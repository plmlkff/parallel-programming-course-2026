package ru.itmo.parallelprogramming.lab1.step1;

import ru.itmo.parallelprogramming.lab1.Benchmark;
import ru.itmo.parallelprogramming.lab1.generator.SeriesGenerator;

public class TestNaive {
    static void main() {
        var collector = new EmptyLockSynchronousCollector();
//        var collector = new SynchronousCollector();
        var bench = new Benchmark();
        var values = SeriesGenerator.generateValues();

        bench.measure(collector, values, 12);
    }
}
