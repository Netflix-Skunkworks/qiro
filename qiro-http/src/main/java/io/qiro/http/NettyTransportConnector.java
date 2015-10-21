package io.qiro.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.logging.LogLevel;
import io.qiro.Service;
import io.qiro.resolver.TransportConnector;
import io.reactivex.netty.channel.Connection;
import io.reactivex.netty.client.ConnectionProvider;
import io.reactivex.netty.protocol.http.HttpHandlerNames;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientImpl;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.internal.HttpClientToConnectionBridge;
import io.reactivex.netty.protocol.http.client.internal.HttpEventPublisherFactory;
import io.reactivex.netty.protocol.tcp.client.TcpClient;
import io.reactivex.netty.protocol.tcp.client.TcpClientImpl;
import org.reactivestreams.Publisher;
import rx.Subscriber;
import rx.functions.Action1;

import java.net.SocketAddress;

import static io.reactivex.netty.protocol.http.client.internal.HttpClientRequestImpl.NO_REDIRECTS;

public class NettyTransportConnector implements TransportConnector<HttpRequest, HttpResponse> {
    public NettyTransportConnector() {}

    @Override
    public Publisher<Service<HttpRequest, HttpResponse>> apply(SocketAddress address) {
        return subscriber -> {

            HttpClient<ByteBuf, ByteBuf> httpClient = HttpClient.newClient(address);
            httpClient.createHead("/").subscribe(new Subscriber<HttpClientResponse<ByteBuf>>() {
                @Override
                public void onCompleted() {
                    subscriber.onComplete();
                }

                @Override
                public void onError(Throwable e) {
                    subscriber.onError(e);
                }

                @Override
                public void onNext(HttpClientResponse<ByteBuf> ignore) {
                    Service<HttpRequest, HttpResponse> service =
                        new RxNettyService(httpClient, subscriber);
                    subscriber.onNext(service);
                }
            });
        };
    }
}
