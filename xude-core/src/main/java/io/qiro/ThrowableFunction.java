package io.qiro;

@FunctionalInterface
public interface ThrowableFunction<T, U> {
    U apply(T t) throws Throwable;
}
