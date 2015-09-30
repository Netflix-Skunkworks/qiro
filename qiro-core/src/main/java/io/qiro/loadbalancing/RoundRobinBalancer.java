package io.qiro.loadbalancing;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.util.Availabilities;
import io.qiro.util.EmptySubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RoundRobinBalancer<Req, Resp> implements ServiceFactory<Req,Resp> {
    private List<ServiceFactory<Req, Resp>> factories;
    private int i;

    public RoundRobinBalancer(Publisher<Set<ServiceFactory<Req, Resp>>> factorySet) {
        this.factories = new ArrayList<>();
        this.i = 0;
        factorySet.subscribe(new EmptySubscriber<Set<ServiceFactory<Req, Resp>>>() {
            @Override
            public void onNext(Set<ServiceFactory<Req, Resp>> set) {
                synchronized (RoundRobinBalancer.this) {
                    System.out.println("RoundRobinBalancer: Adding factories " + set);
                    Set<ServiceFactory<Req, Resp>> current = new HashSet<>(factories);
                    factories.clear();
                    for (ServiceFactory<Req, Resp> factory: current) {
                        if (!set.contains(factory)) {
                            factory.close().subscribe(new EmptySubscriber<>());
                        } else {
                            factories.add(factory);
                        }
                    }
                    for (ServiceFactory<Req, Resp> factory: set) {
                        if (!current.contains(factory)) {
                            factories.add(factory);
                        }
                    }
                }
            }
        });
    }

    @Override
    public Publisher<Service<Req, Resp>> apply() {
        return new Publisher<Service<Req, Resp>>() {
            @Override
            public void subscribe(Subscriber<? super Service<Req, Resp>> subscriber) {
                synchronized (RoundRobinBalancer.this) {
                    if (factories.isEmpty()) {
                        subscriber.onError(new Exception("No Server available in the Loadbalancer!"));
                    } else {
                        i = (i + 1) % factories.size();
                        ServiceFactory<Req, Resp> factory = factories.get(i);
                        factory.apply().subscribe(subscriber);
                    }
                }
            }
        };
    }


    @Override
    public synchronized double availability() {
        return Availabilities.avgOfServiceFactories(factories);
    }

    @Override
    public Publisher<Void> close() {
        return s -> {
            synchronized (RoundRobinBalancer.this) {
                factories.forEach(svc ->
                        svc.close().subscribe(new EmptySubscriber<>())
                );
            }
        };
    }
}
