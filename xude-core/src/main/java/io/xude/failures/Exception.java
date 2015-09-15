package io.xude.failures;

public class Exception extends Throwable {
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
