package io.qiro.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.qiro.Service;
import io.reactivex.netty.client.ConnectionProvider;
import io.reactivex.netty.client.pool.PooledConnectionProvider;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientImpl;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.net.SocketAddress;

class RxNettyService implements Service<HttpRequest, HttpResponse> {
    private final Subscriber<? super Service<HttpRequest, HttpResponse>> subscriber;
    HttpClient<ByteBuf, ByteBuf> client;

    public RxNettyService(
        SocketAddress address,
        Subscriber<? super Service<HttpRequest, HttpResponse>> subscriber
    ) {
        this.subscriber = subscriber;
        ConnectionProvider<ByteBuf, ByteBuf> provider =
            PooledConnectionProvider.createBounded(10, address);
//        client = HttpClientImpl.create(provider);
//        client = HttpClient.newClient(provider);
        client = HttpClient.newClient(address);
    }

    @Override
    public Publisher<HttpResponse> apply(Publisher<HttpRequest> inputs) {
        return new Publisher<HttpResponse>() {
            @Override
            public void subscribe(Subscriber<? super HttpResponse> responseSub) {
                inputs.subscribe(new Subscriber<HttpRequest>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        responseSub.onSubscribe(s);
                    }

                    @Override
                    public void onNext(HttpRequest request) {
                        System.out.println("sending req " + request);
//                        HttpClientRequest<ByteBuf, ByteBuf> rxNettyRequest = client.createRequest(
//                            request.protocolVersion(), request.method(), request.uri());
                        HttpClientRequest<ByteBuf, ByteBuf> rxNettyRequest = client.createGet(request.uri());

                        rxNettyRequest.subscribe(new rx.Subscriber<HttpClientResponse<ByteBuf>>() {
                            @Override
                            public void onCompleted() {
                                System.out.println("RxNetty onComplete");
                                responseSub.onComplete();
                            }

                            @Override
                            public void onError(Throwable e) {
                                System.out.println("RxNetty exc: " + e);
                                responseSub.onError(e);
                            }

                            @Override
                            public void onNext(HttpClientResponse<ByteBuf> response) {
                                System.out.println("RxNetty res: " + response);
                                HttpResponse httpResponse = RxNettyResponse.wrap(response);
                                responseSub.onNext(httpResponse);
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable t) {
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        subscriber.onComplete();
                    }
                });
            }
        };
    }

    @Override
    public double availability() {
        // AFAIK: No current way to know the state of the client
        return 1.0;
    }

    @Override
    public Publisher<Void> close() {
        // TODO: How to close a RxNetty client
        return s -> {
            s.onNext(null);
            s.onComplete();
        };
    }
}
