package io.qiro.reactivesocket;

import io.qiro.Service;
import io.qiro.codec.Codec;
import io.qiro.util.Publishers;
import io.reactivesocket.Payload;
import io.reactivesocket.ReactiveSocket;
import org.reactivestreams.Publisher;

import java.nio.ByteBuffer;

public class ReactiveSocketService<Req, Resp> implements Service<Req, Resp> {
    private static ByteBuffer EMPTY = ByteBuffer.allocate(0);

    private ReactiveSocket reactiveSocket;
    private Codec<Req, Resp> codec;

    public ReactiveSocketService(ReactiveSocket reactiveSocket, Codec<Req, Resp> codec) {
        this.reactiveSocket = reactiveSocket;
        this.codec = codec;
    }

    @Override
    public Publisher<Resp> apply(Publisher<Req> inputs) {
        Publisher<Payload> payloadInputs = Publishers.map(inputs,
            req -> new Payload() {
                @Override
                public ByteBuffer getData() {
                    return codec.encode(req);
                }

                @Override
                public ByteBuffer getMetadata() {
                    return EMPTY;
                }
            });

        Publisher<Payload> payloadOutput =
            reactiveSocket.requestChannel(payloadInputs);

        return Publishers.map(payloadOutput,
            payload -> codec.decode(payload.getData()));

    }

    @Override
    public double availability() {
        return reactiveSocket.availability();
    }

    @Override
    public Publisher<Void> close() {
        return s -> {
            try {
                reactiveSocket.close();
                s.onComplete();
            } catch (Exception e) {
                s.onError(e);
            }
        };
    }
}
