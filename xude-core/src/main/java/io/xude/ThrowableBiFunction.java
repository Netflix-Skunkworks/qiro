package io.xude;

public interface ThrowableBiFunction<T, U, V> {
    V apply(T t, U u) throws io.xude.failures.Exception;
}
