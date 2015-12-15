package io.qiro.reactivesocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.qiro.Server;
import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.builder.*;
import io.qiro.codec.Codec;
import io.reactivesocket.ConnectionSetupHandler;
import io.reactivesocket.ConnectionSetupPayload;
import io.reactivesocket.Payload;
import io.reactivesocket.RequestHandler;
import io.reactivesocket.exceptions.SetupException;
import io.reactivesocket.websocket.rxnetty.server.ReactiveSocketWebSocketServer;
import io.reactivex.netty.protocol.http.server.HttpServer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static io.qiro.util.Publishers.*;

public class ServerBuilder<Req, Resp> extends io.qiro.builder.ServerBuilder<Req, Resp> {
    private static ByteBuffer EMPTY = ByteBuffer.allocate(0);


    public static <Req, Resp> io.qiro.builder.ServerBuilder<Req, Resp> get() {
        return new ServerBuilder<>();
    }

    @Override
    protected Server start(
        ServiceFactory<Req, Resp> factory,
        SocketAddress address,
        Codec<Resp, Req> codec
    ) {

        ConnectionSetupHandler setupHandler = new ConnectionSetupHandler() {
            @Override
            public RequestHandler apply(ConnectionSetupPayload setupPayload) throws SetupException {
                return new RequestHandler() {
                    @Override
                    public Publisher<Payload> handleRequestResponse(Payload payload) {
                        return handleRequestStream(payload);
                    }

                    @Override
                    public Publisher<Payload> handleRequestStream(Payload payload) {
                        return handleSubscription(payload);
                    }

                    @Override
                    public Publisher<Payload> handleSubscription(Payload payload) {
                        return handleChannel(payload, never());
                    }

                    @Override
                    public Publisher<Void> handleFireAndForget(Payload payload) {
                        return Subscriber::onComplete;
                    }

                    @Override
                    public Publisher<Payload> handleChannel(Payload initial, Publisher<Payload> inputs) {
                        return subscriber -> {
                            Publisher<Service<Req, Resp>> servicePublisher = factory.apply();
                            servicePublisher.subscribe(new Subscriber<Service<Req, Resp>>() {
                                @Override
                                public void onSubscribe(Subscription s) {
                                    s.request(1L);
                                }

                                @Override
                                public void onNext(Service<Req, Resp> service) {
                                    Publisher<Req> requests = map(inputs,
                                        payload -> codec.decode(payload.getData())
                                    );

                                    service.requestChannel(requests).subscribe(new Subscriber<Resp>() {
                                        @Override
                                        public void onSubscribe(Subscription s) {
                                            subscriber.onSubscribe(s);
                                        }

                                        @Override
                                        public void onNext(Resp resp) {
                                            Payload responsePayload = new Payload() {

                                                @Override
                                                public ByteBuffer getData() {
                                                    return codec.encode(resp);
                                                }

                                                @Override
                                                public ByteBuffer getMetadata() {
                                                    return EMPTY;
                                                }
                                            };
                                            subscriber.onNext(responsePayload);
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

                                @Override
                                public void onError(Throwable t) {
                                    subscriber.onError(t);
                                }

                                @Override
                                public void onComplete() {}
                            });
                        };
                    }

                    @Override
                    public Publisher<Void> handleMetadataPush(Payload payload) {
                        return s -> {
                            // TODO: implement
                        };
                    }
                };
            }
        };

        ReactiveSocketWebSocketServer serverHandler =
            ReactiveSocketWebSocketServer.create(setupHandler);

        HttpServer<ByteBuf, ByteBuf> server = HttpServer.newServer(address)
            .clientChannelOption(ChannelOption.AUTO_READ, true)
            .start((req, resp) ->
                resp.acceptWebSocketUpgrade(serverHandler::acceptWebsocket));

        return new Server() {
            @Override
            public SocketAddress boundAddress() {
                return server.getServerAddress();
            }

            @Override
            public Publisher<Void> await() {
                return s -> {
                    server.awaitShutdown();
                    s.onComplete();
                };
            }

            @Override
            public Publisher<Void> close(long gracePeriod, TimeUnit unit) {
                return s -> {
                    server.shutdown();
                    s.onComplete();
                };
            }
        };
    }
}
