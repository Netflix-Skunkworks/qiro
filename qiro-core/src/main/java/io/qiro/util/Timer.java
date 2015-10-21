package io.qiro.util;

import java.util.concurrent.TimeUnit;

public interface Timer {
    public interface TimerTask {
        void cancel();
        boolean isCancel();
    }

    TimerTask schedule(Runnable task, long delay, TimeUnit unit);

    default TimerTask schedule(Runnable task, long delayMs) {
        return schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }
}
