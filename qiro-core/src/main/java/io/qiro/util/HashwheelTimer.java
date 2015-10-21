package io.qiro.util;

import io.netty.util.Timeout;

import java.util.concurrent.TimeUnit;

public class HashwheelTimer implements Timer {
    public static Timer INSTANCE = new HashwheelTimer();

    private io.netty.util.HashedWheelTimer netty = new io.netty.util.HashedWheelTimer();

    @Override
    public TimerTask schedule(Runnable task, long delay, TimeUnit unit) {
        Timeout timeout = netty.newTimeout(to -> {
            if (!to.isCancelled()) {
                task.run();
            }
        }, delay, unit);

        return new TimerTask() {
            @Override
            public void cancel() {
                timeout.cancel();
            }

            @Override
            public boolean isCancel() {
                return timeout.isCancelled();
            }
        };
    }
}
