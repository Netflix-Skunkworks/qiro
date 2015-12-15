package io.qiro.loadbalancing.loadestimator;

public interface LoadEstimator {
    double load(long now);
    long start();
    void end(long ts);

    default double load() {
        return load(System.nanoTime());
    }
}
