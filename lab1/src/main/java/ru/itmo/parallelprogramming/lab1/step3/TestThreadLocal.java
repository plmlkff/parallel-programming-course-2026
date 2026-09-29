package ru.itmo.parallelprogramming.lab1.step3;

import ru.itmo.parallelprogramming.lab1.Benchmark;
import ru.itmo.parallelprogramming.lab1.generator.SeriesGenerator;

public class TestThreadLocal {
    static void main() {
        var collector = new ThreadLocalSynchronousCollector();
        var bench = new Benchmark();
        var values = SeriesGenerator.generateValues();

        bench.measure(collector, values, 12);
    }
}
