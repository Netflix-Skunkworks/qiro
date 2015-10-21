package io.qiro.reactivesocket;

import io.qiro.codec.Codec;
import io.qiro.resolver.TransportConnector;

public class ClientBuilder<Req, Resp> extends io.qiro.builder.ClientBuilder<Req, Resp> {

    public static <Req, Resp> ClientBuilder<Req, Resp> get() {
        return new ClientBuilder<>();
    }

    @Override
    public TransportConnector<Req, Resp> connector(Codec<Req, Resp> codec) {
        return new ReactiveSocketTransportConnector<>(codec);
    }
}
