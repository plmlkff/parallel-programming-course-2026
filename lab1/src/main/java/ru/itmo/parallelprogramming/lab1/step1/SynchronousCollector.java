package ru.itmo.parallelprogramming.lab1.step1;

import ru.itmo.parallelprogramming.lab1.domain.AbstractMetricsCollector;
import ru.itmo.parallelprogramming.lab1.domain.MetricsCollector;
import ru.itmo.parallelprogramming.lab1.domain.Snapshot;

import java.util.Arrays;

public class SynchronousCollector extends AbstractMetricsCollector {
    private long[] buckets = new long[BUCKETS_COUNT];
    private long invocationsCount = 0;
    private long sum = 0;
    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;

    @Override
    public synchronized void record(long value) {
        invocationsCount++;
        buckets[bucket(value)]++;
        sum += value;
        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    @Override
    public synchronized void reset() {
        buckets = new long[BUCKETS_COUNT];
        invocationsCount = 0;
        sum = 0;
        min = Long.MAX_VALUE;
        max = Long.MIN_VALUE;
    }

    @Override
    public synchronized Snapshot snapshot() {
        var bucketsCopy = Arrays.copyOf(buckets, buckets.length);
        return new Snapshot(
            bucketsCopy,
            invocationsCount, sum,
            min, max,
            percentile(50, bucketsCopy, invocationsCount), percentile(99, bucketsCopy, invocationsCount)
        );
    }
}
