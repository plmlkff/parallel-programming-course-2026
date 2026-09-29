package ru.itmo.parallelprogramming.lab1.step2;

import ru.itmo.parallelprogramming.lab1.Benchmark;
import ru.itmo.parallelprogramming.lab1.generator.SeriesGenerator;

public class TestSharded {
    static void main() {
        var collector = new ShardedSynchronousCollector();
        var bench = new Benchmark();
        var values = SeriesGenerator.generateValues();

        bench.measure(collector, values, 4);
    }
}
