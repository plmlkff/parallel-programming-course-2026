package ru.itmo.parallelprogramming.lab1;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ru.itmo.parallelprogramming.lab1.generator.SeriesGenerator;

class SeriesGeneratorTest {
    @Test
    void generatesRequiredNumberOfValuesInRequiredRange() {
        long[] values = SeriesGenerator.generateValues();

        assertEquals(1 << 20, values.length);
        for (long value : values) {
            assertTrue(value >= 1 && value <= 1023);
        }
    }

    @Test
    void usesFixedSeed() {
        assertArrayEquals(SeriesGenerator.generateValues(), SeriesGenerator.generateValues());
    }

    @Test
    void roughlyOneThirdOfValuesBelongsToBucketZero() {
        long[] values = SeriesGenerator.generateValues();
        long bucketZeroCount = 0;

        for (long value : values) {
            if (value <= 3) {
                bucketZeroCount++;
            }
        }

        double bucketZeroShare = (double) bucketZeroCount / values.length;
        assertTrue(bucketZeroShare > 0.30 && bucketZeroShare < 0.40);
    }
}
