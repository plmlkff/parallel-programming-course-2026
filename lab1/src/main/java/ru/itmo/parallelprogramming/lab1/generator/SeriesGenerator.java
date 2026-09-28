package ru.itmo.parallelprogramming.lab1.generator;

import java.util.Arrays;
import java.util.Random;

public final class SeriesGenerator {
    private static final int VALUES_COUNT = 1 << 20;
    private static final int MAX_VALUE = 1023;
    private static final double ZIPF_EXPONENT = 1.15;
    private static final long SEED = 42L;

    public static long[] generateValues() {
        double[] cumulativeProbabilities = buildCumulativeProbabilities();
        Random random = new Random(SEED);
        long[] values = new long[VALUES_COUNT];

        for (int i = 0; i < values.length; i++) {
            int position = Arrays.binarySearch(cumulativeProbabilities, random.nextDouble());
            int valueIndex = position >= 0 ? position : -position - 1;
            values[i] = valueIndex + 1L;
        }

        return values;
    }

    private static double[] buildCumulativeProbabilities() {
        double[] probabilities = new double[MAX_VALUE];
        double totalWeight = 0.0;

        for (int value = 1; value <= MAX_VALUE; value++) {
            totalWeight += 1.0 / Math.pow(value, ZIPF_EXPONENT);
        }

        double cumulativeWeight = 0.0;
        for (int value = 1; value <= MAX_VALUE; value++) {
            cumulativeWeight += 1.0 / Math.pow(value, ZIPF_EXPONENT);
            probabilities[value - 1] = cumulativeWeight / totalWeight;
        }
        probabilities[MAX_VALUE - 1] = 1.0;

        return probabilities;
    }
}
