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
    public Publisher<Set<SocketAddress>> resolve(URL url);

    default <Req, Resp>
    Publisher<Set<ServiceFactory<Req, Resp>>> resolveFactory(
        URL url,
        Function<SocketAddress, Service<Req, Resp>> fn
    ) {
        return Resolvers.resolveFactory(resolve(url), fn);
    }
}
