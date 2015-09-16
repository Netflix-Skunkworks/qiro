package io.xude;

@FunctionalInterface
public interface ThrowableFunction<T, U> {
    U apply(T t) throws Throwable;
}
