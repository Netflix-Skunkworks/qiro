package io.qiro.loadbalancing.loadestimator;

public class MsgCountLoadEstimator implements LoadEstimator {
    private int count = 0;

    @Override
    public double load(long now) {
        return (double) count;
    }

    @Override
    public long start() {
        count += 1;
        return 0;
    }

    @Override
    public void end(long ts) {
        count -= 1;
    }
}
