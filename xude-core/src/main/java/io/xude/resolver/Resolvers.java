package io.xude.resolver;

import io.xude.Service;
import io.xude.ServiceFactory;
import io.xude.util.Availabilities;
import io.xude.util.EmptySubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class Resolvers {
    public static <Req, Resp> Publisher<Set<ServiceFactory<Req, Resp>>> resolveFactory(
        Publisher<Set<SocketAddress>> addresses,
        Function<SocketAddress, Service<Req, Resp>> fn
    ) {
        return subscriber -> addresses.subscribe(new Subscriber<Set<SocketAddress>>() {
            @Override
            public void onSubscribe(Subscription s) {
                subscriber.onSubscribe(s);
            }

            @Override
            public void onNext(Set<SocketAddress> socketAddresses) {
                Set<ServiceFactory<Req, Resp>> factories = new HashSet<>();
                for (SocketAddress addr : socketAddresses) {
                    ServiceFactory<Req, Resp> factory =
                        new InetServiceFactory<>(fn, addr);
                    factories.add(factory);
                }
                subscriber.onNext(factories);
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

    private static class InetServiceFactory<Req, Resp> implements ServiceFactory<Req, Resp> {
        private final List<Service<Req, Resp>> services;
        private final Function<SocketAddress, Service<Req, Resp>> fn;
        private final SocketAddress addr;

        public InetServiceFactory(Function<SocketAddress, Service<Req, Resp>> fn, SocketAddress addr) {
            this.fn = fn;
            this.addr = addr;
            services = new ArrayList<>();
        }

        @Override
        public Publisher<Service<Req, Resp>> apply() {
            return s -> {
                Service<Req, Resp> service = fn.apply(addr);
                synchronized (services) {
                    services.add(service);
                }
                s.onNext(service);
                s.onComplete();
            };
        }

        @Override
        public Publisher<Double> availability() {
            return Availabilities.avgOfServices(services, services);
        }

        @Override
        public Publisher<Void> close() {
            return s -> {
                synchronized (services) {
                    services.forEach(svc ->
                        svc.close().subscribe(new EmptySubscriber<>())
                    );
                }
            };
        }
    }
}
