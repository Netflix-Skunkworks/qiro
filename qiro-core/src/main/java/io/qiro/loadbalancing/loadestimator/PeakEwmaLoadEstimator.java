package io.qiro.loadbalancing.loadestimator;

import java.util.concurrent.TimeUnit;

/**
 * Inspired by Finagle loadbalancer algorithm
 */
public class PeakEwmaLoadEstimator implements LoadEstimator {
    private static final double PENALTY = Integer.MAX_VALUE / 2.0;
    private static final double TAU = (double) TimeUnit.NANOSECONDS.convert(15, TimeUnit.SECONDS);

    private final long epoch = System.nanoTime();
    private long stamp = epoch;  // last timestamp in nanos we observed an rtt
    private int pending = 0;     // instantaneous rate
    private double cost = 0.0;   // ewma of rtt, sensitive to peaks.

    public synchronized double load(long now) {
        // update our view of the decay on `cost`
        observe(0.0);

        // If we don't have any latency history, we penalize the host on
        // the first probe. Otherwise, we factor in our current rate
        // assuming we were to schedule an additional request.
        if (cost == 0.0 && pending != 0) {
            return PENALTY + pending;
        } else {
            return cost * (pending+1);
        }
    }

    // Calculate the exponential weighted moving average of our
    // round trip time. It isn't exactly an ewma, but rather a
    // "peak-ewma", since `cost` is hyper-sensitive to latency peaks.
    // Note, because the frequency of observations represents an
    // unevenly spaced time-series[1], we consider the time between
    // observations when calculating our weight.
    // [1] http://www.eckner.com/papers/ts_alg.pdf
    private void observe(double rtt) {
        long t = System.nanoTime();
        long td = Math.max(t - stamp, 0L);
        double w = Math.exp(-td / TAU);
        if (rtt > cost) {
            cost = rtt;
        } else {
            cost = cost*w + rtt*(1.0 - w);
        }
        stamp = t;
    }

    public synchronized long start() {
        pending += 1;
        return System.nanoTime();
    }

    public synchronized void end(long ts) {
        long rtt = Math.max(System.nanoTime() - ts, 0L);
        pending -= 1;
        observe((double)rtt);
    }
}
