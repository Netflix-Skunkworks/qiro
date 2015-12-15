package io.qiro.loadbalancing;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.loadbalancing.loadestimator.LoadEstimator;
import io.qiro.loadbalancing.loadestimator.NullEstimator;
import io.qiro.loadbalancing.selector.Selector;
import io.qiro.util.Availabilities;
import io.qiro.util.EmptySubscriber;
import org.reactivestreams.Publisher;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class BalancerFactory<Req, Resp> implements ServiceFactory<Req,Resp> {

    private final Function<List<WeightedServiceFactory<Req, Resp>>, ServiceFactory<Req, Resp>> selector;
    private volatile List<WeightedServiceFactory<Req, Resp>> buffer =
        Collections.unmodifiableList(new ArrayList<>());

    public BalancerFactory(
        Publisher<Set<ServiceFactory<Req, Resp>>> factorySet,
        Selector<Req, Resp> selector
    ) {
       this(factorySet, selector, NullEstimator.SUPPLIER);
    }

    public BalancerFactory(
        Publisher<Set<ServiceFactory<Req, Resp>>> factorySet,
        Selector<Req, Resp> selector,
        Supplier<LoadEstimator> estimator
    ) {
        this.selector = selector;
        factorySet.subscribe(new EmptySubscriber<Set<ServiceFactory<Req, Resp>>>() {
            @Override
            public void onNext(Set<ServiceFactory<Req, Resp>> newSet) {
                System.out.println("BalancerFactory: Storing ServiceFactory");

                // Only the update of the server list is synchronized
                synchronized (BalancerFactory.this) {
                    Set<ServiceFactory<Req, Resp>> current = new HashSet<>(buffer);
                    List<WeightedServiceFactory<Req, Resp>> newFactories = new ArrayList<>();

                    for (ServiceFactory<Req, Resp> factory: current) {
                        if (!newSet.contains(factory)) {
                            factory.close().subscribe(EmptySubscriber.INSTANCE);
                        } else {
                            WeightedServiceFactory<Req, Resp> wFactory =
                                new WeightedServiceFactory<>(factory, estimator.get());
                            newFactories.add(wFactory);
                        }
                    }
                    for (ServiceFactory<Req, Resp> factory: newSet) {
                        if (!current.contains(factory)) {
                            WeightedServiceFactory<Req, Resp> wFactory =
                                new WeightedServiceFactory<>(factory, estimator.get());
                            newFactories.add(wFactory);
                        }
                    }
                    buffer = Collections.unmodifiableList(newFactories);
                }
            }
        });
    }

    @Override
    public Publisher<Service<Req, Resp>> apply() {
        return subscriber -> {
            List<WeightedServiceFactory<Req, Resp>> serverList = this.buffer;
            if (serverList.isEmpty()) {
                subscriber.onError(new Exception("No Server available in the Loadbalancer!"));
            } else {
                ServiceFactory<Req, Resp> factory = selector.apply(serverList);
                factory.apply().subscribe(subscriber);
            }
        };
    }

    @Override
    public synchronized double availability() {
        return Availabilities.avgOfServiceFactories(buffer);
    }

    @Override
    public Publisher<Void> close() {
        return s -> {
            synchronized (BalancerFactory.this) {
                buffer.forEach(svc ->
                        svc.close().subscribe(new EmptySubscriber<>())
                );
            }
        };
    }
}
