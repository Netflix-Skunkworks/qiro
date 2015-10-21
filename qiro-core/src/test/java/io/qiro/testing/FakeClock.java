package io.qiro.testing;

import io.qiro.util.Clock;

public class FakeClock implements Clock {
    private long time;

    public FakeClock(long epoch) {
        this.time = epoch;
    }

    public void advance(long howMuch) {
        time += howMuch;
    }

    @Override
    public long nowMs() {
        return time;
    }
}
