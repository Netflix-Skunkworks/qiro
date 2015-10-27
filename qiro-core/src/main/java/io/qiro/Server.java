package io.qiro;

import org.reactivestreams.Publisher;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

public interface Server {

    SocketAddress boundAddress();

    Publisher<Void> await();

    Publisher<Void> close(long gracePeriod, TimeUnit unit);

    default Publisher<Void> close() {
        return close(0, TimeUnit.NANOSECONDS);
    }
}
