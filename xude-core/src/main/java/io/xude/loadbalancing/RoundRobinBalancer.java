package io.xude.loadbalancing;

import io.xude.Service;
import io.xude.ServiceFactory;
import io.xude.util.EmptySubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RoundRobinBalancer<Req, Resp> implements ServiceFactory<Req,Resp> {
    private List<ServiceFactory<Req, Resp>> factories;
    private int i;

    public RoundRobinBalancer(Publisher<Set<ServiceFactory<Req, Resp>>> factorySet) {
        this.factories = new ArrayList<>();
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
    public Publisher<Double> availability() {
        return new Publisher<Double>() {
            private double sum = 0.0;
            private int count = 0;

            @Override
            public void subscribe(Subscriber<? super Double> subscriber) {
                for (ServiceFactory<Req, Resp> service: factories) {
                    service.availability().subscribe(new EmptySubscriber<Double>() {
                        @Override
                        public void onSubscribe(Subscription s) {
                            s.request(1L);
                        }

                        @Override
                        public void onNext(Double aDouble) {
                            sum += aDouble;
                            count += 1;
                        }
                    });
                }
                if (count != 0) {
                    subscriber.onNext(sum / count);
                } else {
                    subscriber.onNext(0.0);
                }
                subscriber.onComplete();
            }
        };
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
