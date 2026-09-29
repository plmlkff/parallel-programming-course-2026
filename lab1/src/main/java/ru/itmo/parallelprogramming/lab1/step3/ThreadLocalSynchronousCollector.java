package ru.itmo.parallelprogramming.lab1.step3;

import ru.itmo.parallelprogramming.lab1.domain.AbstractMetricsCollector;
import ru.itmo.parallelprogramming.lab1.domain.Snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;

public class ThreadLocalSynchronousCollector extends AbstractMetricsCollector {
    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();

    private final ThreadLocal<ThreadState> state = ThreadLocal.withInitial(() -> {
        ThreadState s = new ThreadState();
        synchronized (listLock) {
            allStates.add(s);
        }
        return s;
    });


    @Override
    public void record(long value) {
        var state = this.state.get();
        state.count.setRelease(state.count.getPlain() + 1);
        var bucket = bucket(value);
        state.buckets.setRelease(bucket, state.buckets.getPlain(bucket) + 1);
        state.sum.setRelease(state.sum.getPlain() + value);
        state.min.setRelease(Math.min(state.min.getPlain(), value));
        state.max.setRelease(Math.max(state.max.getPlain(), value));
    }

    @Override
    public void reset() {
        // Работает корректно только после остановки всех продюсеров
        synchronized (listLock) {
            allStates.forEach(ThreadState::reset);
        }
        state.remove();
    }

    @Override
    public Snapshot snapshot() {
        var bucketsCopy = new AtomicLongArray(BUCKETS_COUNT);
        var invocationsCount = 0L;
        var sum = 0L;
        var min = Long.MAX_VALUE;
        var max = Long.MIN_VALUE;
        synchronized (listLock) {
            for (ThreadState state : allStates) {
                mergeBuckets(bucketsCopy, state.buckets);
                invocationsCount += state.count.getAcquire();
                sum += state.sum.getAcquire();
                min = Math.min(min, state.min.getAcquire());
                max = Math.max(max, state.max.getAcquire());
            }
        }

        var primitivesBucketsCopy = IntStream.range(0, bucketsCopy.length())
            .mapToLong(bucketsCopy::get)
            .toArray();

        return new Snapshot(
            primitivesBucketsCopy,
            invocationsCount, sum,
            min, max,
            percentile(50, primitivesBucketsCopy, invocationsCount), percentile(99, primitivesBucketsCopy, invocationsCount)
        );
    }

    private void mergeBuckets(AtomicLongArray buckets, AtomicLongArray toMerge) {
        for (int i = 0; i < buckets.length(); i++) {
            buckets.addAndGet(i, toMerge.getAcquire(i));
        }
    }

    static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(256);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);

        public void reset() {
            IntStream.range(0, buckets.length()).forEach(i -> buckets.set(i, 0));
            count.set(0);
            sum.set(0);
            min.set(Long.MAX_VALUE);
            max.set(0);
        }
    }
}
