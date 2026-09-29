package ru.itmo.parallelprogramming.lab1.step2;

import ru.itmo.parallelprogramming.lab1.domain.AbstractMetricsCollector;
import ru.itmo.parallelprogramming.lab1.domain.MetricsCollector;
import ru.itmo.parallelprogramming.lab1.domain.Snapshot;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

public class ShardedSynchronousCollector extends AbstractMetricsCollector {
    private static final int SYNCHRONIZATION_SEGMENTS_COUNT = 16;

    private long[] buckets = new long[BUCKETS_COUNT];
    private final Object[] segments = IntStream.range(0, SYNCHRONIZATION_SEGMENTS_COUNT).mapToObj(_ -> new Object()).toArray();
    private AtomicLong invocationsCount = new AtomicLong();
    private AtomicLong sum = new AtomicLong();
    private AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private AtomicLong max = new AtomicLong(Long.MIN_VALUE);

    @Override
    public void record(long value) {
        var bucket = bucket(value);
        synchronized (segments[bucket % SYNCHRONIZATION_SEGMENTS_COUNT]) {
            buckets[bucket]++;
        }
        invocationsCount.incrementAndGet();
        sum.addAndGet(value);
        updateMinAtomic(value);
        updateMaxAtomic(value);
    }

    @Override
    public synchronized void reset() {
        buckets = new long[BUCKETS_COUNT];
        invocationsCount.set(0);
        sum.set(0);
        min.set(Long.MAX_VALUE);
        max.set(Long.MIN_VALUE);
    }

    @Override
    public Snapshot snapshot() {
        var bucketsCopy = getBucketsSnapshot();
        long invocationsCountCopy = invocationsCount.get();
        return new Snapshot(
            bucketsCopy,
            invocationsCountCopy, sum.get(),
            min.get(), max.get(),
            percentile(50, bucketsCopy, invocationsCountCopy), percentile(99, bucketsCopy, invocationsCountCopy)
        );
    }

    private void updateMinAtomic(long value) {
        long current = min.get();

        while (value < current) {
            if (min.weakCompareAndSetVolatile(current, value)) {
                return;
            }
            current = min.get();
        }
    }

    private void updateMaxAtomic(long value) {
        long current = max.get();

        while (value > current) {
            if (max.weakCompareAndSetVolatile(current, value)) {
                return;
            }
            current = max.get();
        }
    }

    private long[] getBucketsSnapshot() {
        var copy = new long[BUCKETS_COUNT];

        for (int segment = 0; segment < segments.length; segment++) {
            synchronized (segments[segment]) {
                for (int bucket = segment; bucket < BUCKETS_COUNT; bucket += segments.length) {
                    copy[bucket] = buckets[bucket];
                }
            }
        }

        return copy;
    }
}
