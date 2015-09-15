package io.xude.failures;

public class Retryable extends Throwable {
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
