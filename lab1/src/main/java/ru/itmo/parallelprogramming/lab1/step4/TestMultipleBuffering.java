package ru.itmo.parallelprogramming.lab1.step4;

import ru.itmo.parallelprogramming.lab1.Benchmark;
import ru.itmo.parallelprogramming.lab1.generator.SeriesGenerator;
import ru.itmo.parallelprogramming.lab1.step3.ThreadLocalSynchronousCollector;

public class TestMultipleBuffering {
    static void main() {
        var collector = new MultipleBufferingCollector();
        var bench = new Benchmark();
        var values = SeriesGenerator.generateValues();

        bench.measure(collector, values, 12);
    }
}
