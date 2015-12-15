package io.qiro.testing;

import io.qiro.util.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class FakeTimer implements Timer {
    private TreeMap<Long, List<Runnable>> entries = new TreeMap<>();
    private long time = 0L;

    public synchronized void advance(long delta, TimeUnit unit) {
        time += TimeUnit.NANOSECONDS.convert(delta, unit);
        Map.Entry<Long, List<Runnable>> entry = entries.firstEntry();
        while (entry != null && entry.getKey() < time) {
            entry.getValue().forEach(Runnable::run);
            entries.pollFirstEntry();
            entry = entries.firstEntry();
        }
    }

    public synchronized void advance() {
        Map.Entry<Long, List<Runnable>> entry = entries.firstEntry();
        if (entry != null) {
            time += entry.getKey();
            entry.getValue().forEach(Runnable::run);
        }
    }

    @Override
    public synchronized TimerTask schedule(Runnable task, long delay, TimeUnit unit) {
        long t = time + TimeUnit.NANOSECONDS.convert(delay, unit);
        List<Runnable> runnables = entries.get(t);
        if (runnables == null) {
            runnables = new ArrayList<>();
        }
        runnables.add(task);
        entries.put(t, runnables);
        return new TimerTask() {
            @Override
            public void cancel() {
                if (t < time) {
                    entries.remove(t);
                }
            }

            @Override
            public boolean isCancel() {
                return false;
            }
        };
    }
}
