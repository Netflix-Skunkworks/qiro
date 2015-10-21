package io.qiro.resolver;

import io.qiro.Service;
import io.qiro.ServiceFactory;

import org.reactivestreams.Publisher;

import java.net.SocketAddress;
import java.net.URL;
import java.util.Set;
import java.util.function.Function;

/**
 * A Resolver is responsible for converting an abstract name into a more meaningful
 * list of socket addresses.
 *
 * This process is asynchronous and the resolution may change over time.
 */
public interface Resolver {
    public Publisher<Set<SocketAddress>> resolve(String url);

    default <Req, Resp>
    Publisher<Set<ServiceFactory<Req, Resp>>> resolveFactory(
        String url,
        TransportConnector<Req, Resp> connector
    ) {
        return Resolvers.resolveFactory(resolve(url), connector);
    }
}
