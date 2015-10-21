package io.qiro.reactivesocket;

import io.qiro.Service;
import io.qiro.codec.Codec;
import io.qiro.resolver.TransportConnector;
import io.reactivesocket.ConnectionSetupPayload;
import io.reactivesocket.ReactiveSocket;
import io.reactivesocket.rx.Completable;
import io.reactivesocket.websocket.rxnetty.WebSocketDuplexConnection;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.ws.WebSocketConnection;
import io.reactivex.netty.protocol.http.ws.client.WebSocketResponse;
import org.reactivestreams.Publisher;
import rx.Observable;

import java.net.SocketAddress;

public class ReactiveSocketTransportConnector<Req, Resp> implements TransportConnector<Req, Resp> {
    private Codec<Req, Resp> codec;

    public ReactiveSocketTransportConnector(Codec<Req, Resp> codec) {
        this.codec = codec;
    }

    @Override
    public Publisher<Service<Req, Resp>> apply(SocketAddress address) {
        return svcSubscriber -> {
            Observable<WebSocketConnection> wsConnection =
                HttpClient.newClient(address)
                    .createGet("/rs")
                    .requestWebSocketUpgrade()
                    .flatMap(WebSocketResponse::getWebSocketConnection);

            wsConnection.subscribe(new rx.Subscriber<WebSocketConnection>() {
                @Override
                public void onCompleted() {
                    svcSubscriber.onComplete();
                }

                @Override
                public void onError(Throwable e) {
                    svcSubscriber.onError(e);
                }

                @Override
                public void onNext(WebSocketConnection webSocket) {
                    WebSocketDuplexConnection wsDuplexConnection =
                        WebSocketDuplexConnection.create(webSocket);
                    ReactiveSocket reactiveSocket = ReactiveSocket.fromClientConnection(
                        wsDuplexConnection, ConnectionSetupPayload.create("UTF-8", "UTF-8"));

                    reactiveSocket.start(new Completable() {
                        @Override
                        public void success() {
                            svcSubscriber.onNext(
                                new ReactiveSocketService<>(reactiveSocket, codec));
                        }

                        @Override
                        public void error(Throwable e) {
                            svcSubscriber.onError(e);
                        }
                    });
                }
            });
        };
    }
}
