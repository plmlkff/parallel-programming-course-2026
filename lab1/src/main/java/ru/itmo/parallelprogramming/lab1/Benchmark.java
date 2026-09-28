package ru.itmo.parallelprogramming.lab1;

import ru.itmo.parallelprogramming.lab1.domain.MetricsCollector;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Benchmark {
    private static final int VALUES_INDEX_STEP_PER_THREAD = 1000;
    private static final int WARMUP_THREADS_COUNT = 1;
    private static final int MEASUREMENT_DURATION_SECONDS = 5;
    private static final int MEASUREMENTS_COUNT = 5;

    public double measure(MetricsCollector collector, long[] values, int threadsCount) {
        System.out.printf(
            "Benchmarking %s with %d thread(s): %d runs, %d seconds each.\n",
            collector.getClass().getSimpleName(), threadsCount,
            MEASUREMENTS_COUNT, MEASUREMENT_DURATION_SECONDS
        );
        System.out.printf(
            "Warming up the collector for %d seconds with %d thread(s)...\n",
            MEASUREMENT_DURATION_SECONDS, WARMUP_THREADS_COUNT
        );
        warmUp(collector, values);
        System.out.println("Collector warm-up is complete.");
        var results = new double[MEASUREMENTS_COUNT];

        System.out.println("Test runs are beginning...");
        for (int i = 0; i < MEASUREMENTS_COUNT; i++) {
            System.out.printf("Run %d/%d started.\n", i + 1, MEASUREMENTS_COUNT);
            results[i] = run(collector, values, threadsCount, Duration.ofSeconds(MEASUREMENT_DURATION_SECONDS));
            System.out.printf(
                "Run %d/%d finished: %,.2f ops/sec.\n",
                i + 1, MEASUREMENTS_COUNT, results[i]
            );
        }
        System.out.println("Test runs are finished.");

        Arrays.sort(results);
        var median = results[results.length / 2];
        var recordedOperations = collector.snapshot().count();
        System.out.printf("Recorded operations: %,d.\n", recordedOperations);
        System.out.printf("Median throughput: %,.2f ops/sec.\n", median);
        return median;
    }

    private void warmUp(MetricsCollector collector, long[] values) {
        var mean = run(collector, values, WARMUP_THREADS_COUNT, Duration.ofSeconds(MEASUREMENT_DURATION_SECONDS));
        System.out.printf("Warm up median throughput: %,.2f ops/sec.\n", mean);
        collector.reset();
    }

    private double run(MetricsCollector collector, long[] values, int threadsCount, Duration duration) {
        try(var executors = Executors.newFixedThreadPool(threadsCount)) {
            var startLatch = new CountDownLatch(threadsCount + 1);
            var readyLatch = new CountDownLatch(threadsCount);
            var stopLatch = new CountDownLatch(threadsCount);
            var perThreadOperationsCount = new long[threadsCount];
            var stopFlag = new AtomicBoolean(false);

            for (int i = 0; i < threadsCount; i++) {
                executors.submit(threadRunnable(startLatch, stopLatch, readyLatch, perThreadOperationsCount, collector, i, values, stopFlag));
            }

            readyLatch.await();

            var start = System.nanoTime();
            startLatch.countDown();
            Thread.sleep(duration);
            stopFlag.set(true);
            var stop = System.nanoTime();

            if(!stopLatch.await(duration.toMillis(), TimeUnit.MILLISECONDS)) {
                executors.shutdownNow();
            }

            return (double) sumOperations(perThreadOperationsCount) / TimeUnit.NANOSECONDS.toSeconds(stop - start);
        } catch (InterruptedException e) {
            System.err.printf("Основной поток %s прерван во время работы\n", Thread.currentThread().getName());
            throw new IllegalStateException(e);
        }
    }

    private long sumOperations(long[] perThreadOperationsCount) {
        return Arrays.stream(perThreadOperationsCount).sum();
    }

    private Runnable threadRunnable(
        CountDownLatch startLatch, CountDownLatch stopLatch, CountDownLatch readyLatch,
        long[] globalOperationsCount, MetricsCollector collector, int threadIndex,
        long[] values, AtomicBoolean isFinished
    ) {
        return () -> {
            readyLatch.countDown();
            startLatch.countDown();
            try {
                startLatch.await();
                int i = threadIndex * VALUES_INDEX_STEP_PER_THREAD;
                long operationsCount = 0;

                while (!isFinished.get()) {
                    collector.record(values[i]);
                    operationsCount++;
                    if (++i == values.length) i = 0;
                }

                globalOperationsCount[threadIndex] = operationsCount;
            } catch (InterruptedException e) {
                System.out.printf("Поток %s прерван во время работы\n", Thread.currentThread().getName());
            } finally {
                stopLatch.countDown();
            }
        };
    }
}
