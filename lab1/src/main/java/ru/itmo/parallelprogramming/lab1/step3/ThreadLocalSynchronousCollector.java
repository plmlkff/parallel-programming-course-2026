package ru.itmo.parallelprogramming.lab1.step3;

import ru.itmo.parallelprogramming.lab1.domain.MetricsCollector;
import ru.itmo.parallelprogramming.lab1.domain.Snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;

public class ThreadLocalSynchronousCollector implements MetricsCollector {
    private static final int BUCKETS_COUNT = 256;
    private static final long BUCKET_STEP_MS = 4;

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
        state.get().count.setRelease(state.get().count.getPlain() + 1);
        var bucket = bucket(value);
        state.get().buckets.setRelease(bucket, state.get().buckets.getPlain(bucket) + 1);
        state.get().sum.setRelease(state.get().sum.getPlain() + value);
        state.get().min.setRelease(Math.min(state.get().min.getPlain(), value));
        state.get().max.setRelease(Math.max(state.get().max.getPlain(), value));
    }

    @Override
    public void reset() {
        // Работает корректно только после остановки всех продюсеров
        synchronized (listLock) {
            allStates.clear();
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

    private long percentile(int percentile, long[] buckets, long invocationsCount) {
        var threshold = invocationsCount * percentile / 100.0;
        var acc = 0L;

        for (int i = 0; i < buckets.length; i++) {
            acc += buckets[i];
            if (acc >= threshold) return i * BUCKET_STEP_MS;
        }

        return BUCKETS_COUNT * BUCKET_STEP_MS;
    }

    private int bucket(long value) {
        return Math.toIntExact(Math.min(value / 4, 255));
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
        final AtomicLong max = new AtomicLong(Long.MIN_VALUE);
    }
}
