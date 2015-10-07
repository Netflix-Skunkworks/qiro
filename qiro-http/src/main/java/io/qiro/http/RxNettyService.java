package io.qiro.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.qiro.Service;
import io.reactivex.netty.client.ConnectionProvider;
import io.reactivex.netty.client.pool.PooledConnectionProvider;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.net.ConnectException;
import java.net.SocketAddress;
import java.net.SocketException;


class RxNettyService implements Service<HttpRequest, HttpResponse> {
    private final SocketAddress address;
    private final Subscriber<? super Service<HttpRequest, HttpResponse>> subscriber;
    private HttpClient<ByteBuf, ByteBuf> client;
    private double availability;

    public RxNettyService(
        SocketAddress address,
        Subscriber<? super Service<HttpRequest, HttpResponse>> subscriber
    ) {
        this.address = address;
        this.subscriber = subscriber;

        // this should be done outside, but AFAIK establishing a connection is lazy in RxNetty
        ConnectionProvider<ByteBuf, ByteBuf> provider =
            PooledConnectionProvider.createUnbounded(address);
        client = HttpClient.newClient(provider);
        this.availability = 1.0;
    }

    @Override
    public Publisher<HttpResponse> apply(Publisher<HttpRequest> inputs) {
        return new Publisher<HttpResponse>() {
            @Override
            public void subscribe(Subscriber<? super HttpResponse> respSubcriber) {
                inputs.subscribe(new Subscriber<HttpRequest>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        respSubcriber.onSubscribe(s);
                    }

                    @Override
                    public void onNext(HttpRequest request) {
                        synchronized (RxNettyService.this) {
                            // Hack to trigger reconnection
                            if (client == null) {
                                ConnectionProvider<ByteBuf, ByteBuf> provider =
                                    PooledConnectionProvider.createUnbounded(address);
                                client = HttpClient.newClient(provider);
                            }
                        }
                        HttpClientRequest<ByteBuf, ByteBuf> rxNettyRequest = client.createRequest(
                            request.protocolVersion(), request.method(), request.uri());

                        rxNettyRequest.subscribe(new rx.Subscriber<HttpClientResponse<ByteBuf>>() {
                            @Override
                            public void onCompleted() {
                                respSubcriber.onComplete();
                            }

                            @Override
                            public void onError(Throwable requestFailure) {
                                // Hack to trigger reconnection
                                if (requestFailure instanceof ConnectException
                                    || requestFailure instanceof SocketException) {
                                    synchronized (RxNettyService.this) {
                                        client = null;
                                    }
                                }
                                respSubcriber.onError(requestFailure);
                            }

                            @Override
                            public void onNext(HttpClientResponse<ByteBuf> response) {
                                HttpResponse httpResponse = RxNettyResponse.wrap(response);
                                respSubcriber.onNext(httpResponse);
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable inputFailure) {
                        respSubcriber.onError(inputFailure);
                    }

                    @Override
                    public void onComplete() {
                        // inputs complete
                    }
                });
            }
        };
    }

    @Override
    public double availability() {
        // AFAIK: No current way to know the state of the client
        return availability;
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
