package ru.itmo.parallelprogramming.lab1.domain;

public abstract class AbstractMetricsCollector implements MetricsCollector{
    public static final int BUCKETS_COUNT = 256;
    public static final long BUCKET_STEP_MS = 4;

    protected static long percentile(int percentile, long[] buckets, long invocationsCount) {
        var threshold = invocationsCount * percentile / 100.0;
        var acc = 0L;

        for (int i = 0; i < buckets.length; i++) {
            acc += buckets[i];
            if (acc >= threshold) return i * BUCKET_STEP_MS;
        }

        return BUCKETS_COUNT * BUCKET_STEP_MS;
    }

    protected static int bucket(long value) {
        return Math.toIntExact(Math.min(value / 4, 255));
    }
}
