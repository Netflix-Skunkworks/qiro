package io.qiro.util;

public class Backoff {
    private final double multiplier;
    private final int max;
    private double backoff;

    public Backoff(double init, double multiplier, int max){
        this.multiplier = multiplier;
        this.max = max;
        this.backoff = init;
    }

    public int nextBackoff() {
        if (backoff >= max) {
            return max;
        } else {
            int result = (int) Math.floor(backoff);
            backoff *= multiplier;
            return result;
        }
    }
}
