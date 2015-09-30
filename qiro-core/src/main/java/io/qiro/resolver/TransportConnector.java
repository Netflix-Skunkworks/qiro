package io.qiro.resolver;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import org.reactivestreams.Publisher;

import java.net.SocketAddress;
import java.util.function.Function;

public interface TransportConnector<Req, Resp>
    extends Function<SocketAddress, Publisher<Service<Req, Resp>>> {

    default ServiceFactory<Req, Resp> toFactory(SocketAddress address) {
        return new ServiceFactory<Req, Resp>() {
            @Override
            public Publisher<Service<Req, Resp>> apply() {
                return TransportConnector.this.apply(address);
            }

            @Override
            public double availability() {
                return 1.0;
            }

            @Override
            public Publisher<Void> close() {
                return s -> {
                    s.onNext(null);
                    s.onComplete();
                };
            }
        };
    }
}
