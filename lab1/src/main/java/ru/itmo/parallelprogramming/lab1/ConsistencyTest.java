package ru.itmo.parallelprogramming.lab1;

import ru.itmo.parallelprogramming.lab1.domain.MetricsCollector;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConsistencyTest {
    private static final int WRITERS = 4;
    private static final int SNAPSHOTS = 10_000;

    public static Result run(MetricsCollector collector) throws InterruptedException, ExecutionException {
        collector.reset();

        var stop = new AtomicBoolean();
        var ready = new CountDownLatch(WRITERS);
        var start = new CountDownLatch(1);
        var firstRecord = new CountDownLatch(WRITERS);

        try (var executor = Executors.newFixedThreadPool(WRITERS)) {
            var writers = new ArrayList<Future<Long>>();
            for (int writer = 0; writer < WRITERS; writer++) {
                long value = writer * 100L + 1;
                writers.add(executor.submit(() -> {
                    long operations = 0;
                    ready.countDown();
                    start.await();
                    try {
                        collector.record(value);
                        operations++;
                    } finally {
                        firstRecord.countDown();
                    }
                    while (!stop.get()) {
                        collector.record(value);
                        operations++;
                    }
                    return operations;
                }));
            }

            int bucketsBelowCount = 0;
            int bucketsAboveCount = 0;
            try {
                ready.await();
                start.countDown();
                firstRecord.await();
                for (int i = 0; i < SNAPSHOTS; i++) {
                    var snapshot = collector.snapshot();
                    long bucketTotal = 0;
                    for (long bucket : snapshot.buckets()) {
                        bucketTotal += bucket;
                    }
                    if (bucketTotal < snapshot.count()) {
                        bucketsBelowCount++;
                    } else if (bucketTotal > snapshot.count()) {
                        bucketsAboveCount++;
                    }
                }
            } finally {
                stop.set(true);
                start.countDown();
            }

            long operations = 0;
            for (var writer : writers) {
                operations += writer.get();
            }
            long finalCount = collector.snapshot().count();
            return new Result(SNAPSHOTS, bucketsBelowCount, bucketsAboveCount, operations, finalCount);
        }
    }

    public record Result(
        int snapshots,
        int bucketsBelowCount,
        int bucketsAboveCount,
        long operations,
        long finalCount
    ) {
        public int inconsistentSnapshots() {
            return bucketsBelowCount + bucketsAboveCount;
        }

        public double inconsistentPercent() {
            return 100.0 * inconsistentSnapshots() / snapshots;
        }
    }
}
