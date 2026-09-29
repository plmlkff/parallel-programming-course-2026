package ru.itmo.parallelprogramming.lab1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import ru.itmo.parallelprogramming.lab1.domain.MetricsCollector;
import ru.itmo.parallelprogramming.lab1.step2.ShardedSynchronousCollector;
import ru.itmo.parallelprogramming.lab1.step3.ThreadLocalSynchronousCollector;
import ru.itmo.parallelprogramming.lab1.step4.MultipleBufferingCollector;

class ConsistencyTestTest {
    @Test
    void shardedCollectorPreservesFinalCount() throws Exception {
        checkFinalCount("sharded", new ShardedSynchronousCollector());
    }

    @Test
    void threadLocalCollectorPreservesFinalCount() throws Exception {
        checkFinalCount("thread-local", new ThreadLocalSynchronousCollector());
    }

    @Test
    void doubleBufferedCollectorHasConsistentSnapshotsAndFinalCount() throws Exception {
        var result = checkFinalCount("double-buffered", new MultipleBufferingCollector());

        assertEquals(0, result.inconsistentSnapshots());
    }

    private ConsistencyTest.Result checkFinalCount(String name, MetricsCollector collector) throws Exception {
        var result = ConsistencyTest.run(collector);
        System.out.printf(
            "STRESS_RESULT %s: broken=%d/%d (%.2f%%), buckets<count=%d, buckets>count=%d, final_count_delta=%d%n",
            name, result.inconsistentSnapshots(), result.snapshots(), result.inconsistentPercent(),
            result.bucketsBelowCount(), result.bucketsAboveCount(), result.finalCount() - result.operations()
        );
        assertEquals(10_000, result.snapshots());
        assertEquals(result.operations(), result.finalCount());
        return result;
    }

    @Test
    void simulatesSnapshotsStayConsistent() throws Exception {
        var collector = new MultipleBufferingCollector();
        var stop = new AtomicBoolean();

        try (var executor = Executors.newFixedThreadPool(4)) {
            Runnable write = () -> {
                while (!stop.get()) {
                    collector.record(1);
                }
            };
            var firstWriter = executor.submit(write);
            var secondWriter = executor.submit(write);

            Runnable checkSnapshots = () -> {
                for (int i = 0; i < 1_000; i++) {
                    var snapshot = collector.snapshot();
                    assertEquals(snapshot.count(), Arrays.stream(snapshot.buckets()).sum());
                }
            };
            var firstReader = executor.submit(checkSnapshots);
            var secondReader = executor.submit(checkSnapshots);

            try {
                firstReader.get();
                secondReader.get();
            } finally {
                stop.set(true);
            }
            firstWriter.get();
            secondWriter.get();
        }
    }
}
