package io.qiro.failures;

public class Exceptions {
    public static boolean isRetryable(Throwable t) {
        // TODO: to be extended
        if (t instanceof Retryable) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isApplicativeError(Throwable t) {
        // TODO: to be extended
        return false;
    }
}
