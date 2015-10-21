package io.qiro.util;

@FunctionalInterface
public interface Clock {
    public static Clock SYSTEM_CLOCK = System::currentTimeMillis;

    long nowMs();
}
