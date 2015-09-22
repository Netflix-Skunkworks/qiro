package io.qiro;

public interface ThrowableBiFunction<T, U, V> {
    V apply(T t, U u) throws io.qiro.failures.Exception;
}
