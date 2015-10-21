package io.qiro.builder;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.codec.Codec;
import io.qiro.failures.FailFastFactory;
import io.qiro.failures.FailureAccrualDetector;
import io.qiro.loadbalancing.P2CBalancer;
import io.qiro.pool.WatermarkPool;
import io.qiro.resolver.DnsResolver;
import io.qiro.resolver.Resolvers;
import io.qiro.resolver.TransportConnector;
import io.qiro.util.Clock;
import io.qiro.util.HashwheelTimer;
import io.qiro.util.Timer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public abstract class ClientBuilder<Req, Resp> {
    private static class ConnectionPoolOptions {
        private int connectionPoolMin;
        private int connectionPoolMax;
        private int connectionPoolMaxWaiters;

        ConnectionPoolOptions(int connectionPoolMin, int connectionPoolMax, int connectionPoolMaxWaiters) {
            this.connectionPoolMin = connectionPoolMin;
            this.connectionPoolMax = connectionPoolMax;
            this.connectionPoolMaxWaiters = connectionPoolMaxWaiters;
        }

        public int min() {
            return connectionPoolMin;
        }

        public int max() {
            return connectionPoolMax;
        }

        public int maxWaiters() {
            return connectionPoolMaxWaiters;
        }
    }

    private static class FailureAccrualOptions {
        private int requestHistory;
        private long expiration;
        private TimeUnit unit;

        private FailureAccrualOptions(int requestHistory, long expiration, TimeUnit unit) {
            this.requestHistory = requestHistory;
            this.expiration = expiration;
            this.unit = unit;
        }

        public int history() {
            return requestHistory;
        }

        public long expiration() {
            return expiration;
        }

        public TimeUnit expirationUnit() {
            return unit;
        }
    }

    private String remote = null;
    private Timer timer = HashwheelTimer.INSTANCE;
    private Clock clock = Clock.SYSTEM_CLOCK;
    private ConnectionPoolOptions poolOptions = null;
    private FailureAccrualOptions accrualOptions = null;
    private Codec<Req, Resp> codec = null;

    public ClientBuilder<Req, Resp> destination(String remote) {
        this.remote = remote;
        return this;
    }

    public ClientBuilder<Req, Resp> clock(Clock clock) {
        this.clock = clock;
        return this;
    }

    public ClientBuilder<Req, Resp> timer(Timer timer) {
        this.timer = timer;
        return this;
    }

    public ClientBuilder<Req, Resp> codec(Codec<Req, Resp> codec) {
        this.codec = codec;
        return this;
    }

    public ClientBuilder<Req, Resp> connectionPool(int min, int max, int maxWaiters) {
        if (min == 0 && max == 0 && maxWaiters == 0) {
            this.poolOptions = null;
        } else {
            this.poolOptions = new ConnectionPoolOptions(min, max, maxWaiters);
        }
        return this;
    }

    public ClientBuilder<Req, Resp> failureAccrual(
        int requestHistory,
        long expiration,
        TimeUnit unit
    ) {
        this.accrualOptions = new FailureAccrualOptions(requestHistory, expiration, unit);
        return this;
    }

    public abstract TransportConnector<Req, Resp> connector(Codec<Req, Resp> codec);

    public Service<Req, Resp> build() {
        ServiceFactory<Req, Resp> factory = buildFactory();
        return factory.toService();
    }

    public ServiceFactory<Req, Resp> buildFactory() {
        if (remote == null) {
            throw new IllegalStateException("`destination` is uninitialized!\n"
                + "please use `ClientBuilder.destination`\n"
                + "e.g. ClientBuilder.destination(\"dns://www.netflix.com:80\")");
        }
        if (codec == null) {
            throw new IllegalStateException("`codec` is uninitialized!\n"
                + "please use `ClientBuilder.codec`\n"
                + "e.g. ClientBuilder.codec(UTF8Codec.INSTANCE)");
        }

        DnsResolver resolver = new DnsResolver();
        Publisher<Set<SocketAddress>> addresses = resolver.resolve(remote);
        Publisher<Set<ServiceFactory<Req, Resp>>> factories =
            Resolvers.resolveFactory(addresses, connector(codec));

        factories = decorateWithConnectionPool(factories);
        factories = decoracteWithFailureDetection(factories);
        factories = decoracteWithFailFast(factories);

        ServiceFactory<Req, Resp> balancedFactory = new P2CBalancer<>(factories);
        return balancedFactory;
    }

    private Publisher<Set<ServiceFactory<Req, Resp>>> decoracteWithFailFast(
        Publisher<Set<ServiceFactory<Req, Resp>>> factories
    ) {
        if (accrualOptions == null) {
            return factories;
        } else {
            return decorate(factories, factory ->
                new FailFastFactory<>(factory, timer)
            );
        }
    }


    private Publisher<Set<ServiceFactory<Req, Resp>>> decoracteWithFailureDetection(
        Publisher<Set<ServiceFactory<Req, Resp>>> factories
    ) {
        if (accrualOptions == null) {
            return factories;
        } else {
            return decorate(factories, factory ->
                new FailureAccrualDetector<>(factory, accrualOptions.history(),
                    accrualOptions.expiration(), accrualOptions.expirationUnit(), clock)
            );
        }
    }

    private Publisher<Set<ServiceFactory<Req, Resp>>> decorateWithConnectionPool(
        Publisher<Set<ServiceFactory<Req, Resp>>> factories
    ) {
        if (poolOptions == null) {
            return factories;
        } else {
            return decorate(factories, factory ->
                new WatermarkPool<>(
                    poolOptions.min(),
                    poolOptions.max(),
                    poolOptions.maxWaiters(),
                    factory
                ));
        }
    }

    protected Publisher<Set<ServiceFactory<Req, Resp>>> decorate(
        Publisher<Set<ServiceFactory<Req, Resp>>> factories,
        Function<ServiceFactory<Req, Resp>, ServiceFactory<Req, Resp>> function
    ) {
        return subscriber -> factories.subscribe(new Subscriber<Set<ServiceFactory<Req, Resp>>>() {
            @Override
            public void onSubscribe(Subscription s) {
                subscriber.onSubscribe(s);
            }

            @Override
            public void onNext(Set<ServiceFactory<Req, Resp>> serviceFactories) {
                Set<ServiceFactory<Req, Resp>> newSet = new HashSet<>();
                for (ServiceFactory<Req, Resp> factory: serviceFactories) {
                    newSet.add(function.apply(factory));
                }
                subscriber.onNext(newSet);
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
}
