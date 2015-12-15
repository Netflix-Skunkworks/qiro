package io.qiro.loadbalancing.loadestimator;

import java.util.function.Supplier;

public class NullEstimator implements LoadEstimator {
    public static LoadEstimator INSTANCE = new NullEstimator();
    public static Supplier<LoadEstimator> SUPPLIER = NullEstimator::new;

    @Override
    public double load(long now) {
        return 0.0;
    }

    @Override
    public long start() {
        return 0L;
    }

    @Override
    public void end(long ts) {
    }
}
