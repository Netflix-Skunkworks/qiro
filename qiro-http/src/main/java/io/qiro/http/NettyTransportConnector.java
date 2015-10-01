package io.qiro.http;

import io.netty.handler.codec.http.*;
import io.qiro.Service;
import io.qiro.resolver.TransportConnector;
import org.reactivestreams.Publisher;

import java.net.SocketAddress;

public class NettyTransportConnector implements TransportConnector<HttpRequest, HttpResponse> {
    private final int maxConnections;

    public NettyTransportConnector(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    @Override
    public Publisher<Service<HttpRequest, HttpResponse>> apply(SocketAddress address) {
        return subscriber -> {
            Service<HttpRequest, HttpResponse> service =
                new RxNettyService(address, maxConnections, subscriber);
            subscriber.onNext(service);
        };
    }
}
