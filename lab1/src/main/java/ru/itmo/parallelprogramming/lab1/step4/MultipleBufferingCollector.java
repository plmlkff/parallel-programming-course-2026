package ru.itmo.parallelprogramming.lab1.step4;

import ru.itmo.parallelprogramming.lab1.domain.MetricsCollector;
import ru.itmo.parallelprogramming.lab1.domain.Snapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MultipleBufferingCollector implements MetricsCollector {
    private static final int BUCKETS_COUNT = 256;
    private static final long BUCKET_STEP_MS = 4;
    private static final int FIRST = 0;
    private static final int SECOND = 1;
    private static final int NO_WHERE = -1;

    private volatile int active = FIRST;

    private final Object listLock = new Object();
    private final List<ThreadBuffers> allBuffers = new ArrayList<>();
    private final ThreadLocal<ThreadBuffers> buffers = ThreadLocal.withInitial(() -> {
        var buffers = new ThreadBuffers();
        synchronized (listLock) {
            allBuffers.add(buffers);
        }
        return buffers;
    });
    private State globalReadOnlyState = new State();

    @Override
    public void record(long value) {
        var buffers = this.buffers.get();
        while (buffers.inside.getAcquire() == NO_WHERE) {
            buffers.inside.set(active);
            if (buffers.inside.get() == active) {
                break;
            }
            buffers.inside.setRelease(NO_WHERE);
        }

        var inside = buffers.inside.get();
        buffers.buckets[inside][bucket(value)]++;
        buffers.count[inside]++;
        buffers.sum[inside] += value;
        buffers.min[inside] = Math.min(buffers.min[inside], value);
        buffers.max[inside] = Math.max(buffers.max[inside], value);

        buffers.inside.setRelease(NO_WHERE);
    }

    @Override
    public void reset() {
        allBuffers.forEach(b -> { b.reset(FIRST); b.reset(SECOND); });
        globalReadOnlyState = new State();
    }

    @Override
    public Snapshot snapshot() {
        synchronized (listLock) {
            var old = active;
            active = 1 - old;

            while (allBuffers.stream().anyMatch(buffers -> buffers.inside.getAcquire() == old)) {
                Thread.onSpinWait();
            }

            var newState = new State(globalReadOnlyState);

            allBuffers.forEach(buffers -> {
                mergeState(newState, buffers, old);
                buffers.reset(old);
            });
            globalReadOnlyState = newState;
        }

        var state = globalReadOnlyState;
        var bucketsCopy = Arrays.copyOf(state.buckets, state.buckets.length);
        return new Snapshot(
            bucketsCopy, state.count,
            state.sum, state.min, state.max,
            percentile(50, bucketsCopy, state.count), percentile(99, bucketsCopy, state.count)
        );
    }

    private long percentile(int percentile, long[] buckets, long invocationsCount) {
        var threshold = invocationsCount * percentile / 100.0;
        var acc = 0L;

        for (int i = 0; i < buckets.length; i++) {
            acc += buckets[i];
            if (acc >= threshold) return i * BUCKET_STEP_MS;
        }

        return BUCKETS_COUNT * BUCKET_STEP_MS;
    }

    private static int bucket(long value) {
        return Math.toIntExact(Math.min(value / BUCKET_STEP_MS, BUCKETS_COUNT - 1));
    }

    private static void mergeState(State globalState, ThreadBuffers buffers, final int active) {
        mergeBuckets(globalState.buckets, buffers.buckets[active]);
        globalState.count += buffers.count[active];
        globalState.sum += buffers.sum[active];
        globalState.min = Math.min(globalState.min, buffers.min[active]);
        globalState.max = Math.max(globalState.max, buffers.max[active]);
    }

    private static void mergeBuckets(long[] buckets, long[] toMerge) {
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] += toMerge[i];
        }
    }

    static final class State {
        long[] buckets = new long[BUCKETS_COUNT];
        long count = 0;
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = 0;

        public State(State oldState) {
            this.buckets = Arrays.copyOf(oldState.buckets, oldState.buckets.length);
            this.count = oldState.count;
            this.sum = oldState.sum;
            this.min = oldState.min;
            this.max = oldState.max;
        }

        public State() {}
    }

    static final class ThreadBuffers {
        // Два буфера: [0] и [1]. Обычные массивы и переменные!
        final long[][] buckets = new long[2][BUCKETS_COUNT];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = {0, 0};

        // Флаг входа: -1 = вне буферов, 0 = запись в буфер 0, 1 = запись в буфер 1
        final AtomicInteger inside = new AtomicInteger(-1);

        public void reset(final int ind) {
            buckets[ind] = new long[BUCKETS_COUNT];
            count[ind] = 0;
            sum[ind] = 0;
            min[ind] = Long.MAX_VALUE;
            max[ind] = 0;
        }
    }
}
