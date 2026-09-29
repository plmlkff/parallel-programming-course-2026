package ru.itmo.parallelprogramming.lab1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import ru.itmo.parallelprogramming.lab1.step1.SynchronousCollector;
import ru.itmo.parallelprogramming.lab1.step4.MultipleBufferingCollector;

class ConsistencyTestTest {
    @Test
    void synchronizedCollectorHasConsistentSnapshotsAndFinalCount() throws Exception {
        var result = ConsistencyTest.run(new MultipleBufferingCollector());

        assertEquals(10_000, result.snapshots());
        assertEquals(0, result.inconsistentSnapshots());
        assertEquals(result.operations(), result.finalCount());
    }
}
