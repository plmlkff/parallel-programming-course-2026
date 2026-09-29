package ru.itmo.parallelprogramming.lab1.domain;

public interface MetricsCollector {
    void record(long value);

    void reset();

    Snapshot snapshot();
}
