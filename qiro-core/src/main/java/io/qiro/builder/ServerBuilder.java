package io.qiro.builder;
import io.qiro.Server;
import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.codec.Codec;
import io.qiro.util.Clock;
import io.qiro.util.HashwheelTimer;
import io.qiro.util.Timer;
import org.reactivestreams.Publisher;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public abstract class ServerBuilder<Req, Resp> {
    private Timer timer = HashwheelTimer.INSTANCE;
    private Clock clock = Clock.SYSTEM_CLOCK;
    private Codec<Resp, Req> codec = null;
    private SocketAddress bindingAddress;

    public ServerBuilder<Req, Resp> clock(Clock clock) {
        this.clock = clock;
        return this;
    }

    public ServerBuilder<Req, Resp> timer(Timer timer) {
        this.timer = timer;
        return this;
    }

    public ServerBuilder<Req, Resp> codec(Codec<Resp, Req> codec) {
        this.codec = codec;
        return this;
    }

    public ServerBuilder<Req, Resp> listen(SocketAddress address) {
        this.bindingAddress = address;
        return this;
    }

    public ServerBuilder<Req, Resp> listen(int port) {
        return listen(new InetSocketAddress(port));
    }

    public Server build(Service<Req, Resp> service) {
        ServiceFactory<Req, Resp> factory = new ServiceFactory<Req, Resp>() {
            @Override
            public Publisher<Service<Req, Resp>> apply() {
                return s -> {
                    s.onNext(service);
                    s.onComplete();
                };
            }

            @Override
            public double availability() {
                return service.availability();
            }

            @Override
            public Publisher<Void> close() {
                return service.close();
            }
        };
        return build(factory);
    }

    public Server build(ServiceFactory<Req, Resp> factory) {
        if (bindingAddress == null) {
            throw new IllegalStateException("`binding address` is uninitialized!\n"
                + "please use `ServerBuilder.listen`\n"
                + "e.g. ServerBuilder.listen(8080)");
        }
        if (codec == null) {
            throw new IllegalStateException("`codec` is uninitialized!\n"
                + "please use `ServerBuilder.codec`\n"
                + "e.g. ServerBuilder.codec(UTF8Codec.INSTANCE)");
        }

        return start(factory, bindingAddress, codec);
    }

    protected abstract Server start(
        ServiceFactory<Req, Resp> factory,
        SocketAddress address,
        Codec<Resp, Req> codec
    );
}
