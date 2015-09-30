package io.qiro.http;

import io.netty.handler.codec.http.*;
import io.qiro.Service;
import io.qiro.resolver.TransportConnector;
import org.reactivestreams.Publisher;

import java.net.SocketAddress;

public class NettyTransportConnector implements TransportConnector<HttpRequest, HttpResponse> {
    @Override
    public Publisher<Service<HttpRequest, HttpResponse>> apply(SocketAddress address) {
        return subscriber -> {
            Service<HttpRequest, HttpResponse> service = new RxNettyService(address, subscriber);
            subscriber.onNext(service);
        };
    }
}
