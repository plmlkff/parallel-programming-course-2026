package ru.itmo.parallelprogramming.lab1.step0;

import ru.itmo.parallelprogramming.lab1.Benchmark;
import ru.itmo.parallelprogramming.lab1.generator.SeriesGenerator;

public class TestUnsafe {
    static void main() {
        var collector = new UnsafeCollector();
        var bench = new Benchmark();
        var values = SeriesGenerator.generateValues();

        bench.measure(collector, values, 1);
    }
}
