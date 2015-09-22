package io.qiro.failures;

public class Exception extends Throwable {
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
