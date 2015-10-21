package io.qiro.loadbalancing;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.util.Availabilities;
import io.qiro.util.EmptySubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class P2CBalancer<Req, Resp> implements ServiceFactory<Req, Resp> {
    final private List<WeightedServiceFactory<Req, Resp>> buffer;

    public P2CBalancer(Publisher<Set<ServiceFactory<Req, Resp>>> factories) {
        this.buffer = new ArrayList<>();
        factories.subscribe(new EmptySubscriber<Set<ServiceFactory<Req, Resp>>>() {
            @Override
            public void onNext(Set<ServiceFactory<Req, Resp>> set) {
                System.out.println("P2CBalancer: Storing ServiceFactory");
                synchronized (P2CBalancer.this) {
                    Set<ServiceFactory<Req, Resp>> current = new HashSet<>(buffer);
                    buffer.clear();
                    for (ServiceFactory<Req, Resp> factory: current) {
                        if (!set.contains(factory)) {
                            factory.close().subscribe(EmptySubscriber.INSTANCE);
                        } else {
                            buffer.add(new WeightedServiceFactory<>(factory));
                        }
                    }
                    for (ServiceFactory<Req, Resp> factory: set) {
                        if (!current.contains(factory)) {
                            buffer.add(new WeightedServiceFactory<>(factory));
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
                Random rng = ThreadLocalRandom.current();
                WeightedServiceFactory<Req, Resp> selectedFactory = null;
                synchronized (P2CBalancer.this) {
                    if (buffer.isEmpty()) {
                        System.out.println("P2CBalancer: buffer is empty");
                        subscriber.onError(new Exception("No Server available in the Loadbalancer!"));
                    } else if (buffer.size() == 1) {
                        System.out.println("P2CBalancer: buffer contains only 1 element");
                        selectedFactory = buffer.get(0);
                    } else {
                        int n = buffer.size();
                        int i = 0;
                        int a = 0;
                        int b = 0;
                        while (i < 10) {
                            a = rng.nextInt(n);
                            b = rng.nextInt(n - 1);
                            if (b >= a) {
                                b = b + 1;
                            }
                            if (buffer.get(a).availability() != 0.0
                                && buffer.get(b).availability() != 0.0) {
                                break;
                            }
                            i += 1;
                        }
                        System.out.println("P2CBalancer: choosing between "
                            + "svc(i:" + a + " load:" + buffer.get(a).getLoad()
                            + ") and "
                            + "svc(i:" + b + " load:" + buffer.get(b).getLoad()
                            + ")");
                        if (buffer.get(a).getLoad() < buffer.get(b).getLoad()) {
                            selectedFactory = buffer.get(a);
                        } else {
                            selectedFactory = buffer.get(b);
                        }
                    }
                }
                if (selectedFactory != null) {
                    selectedFactory.increment();
                    selectedFactory.apply().subscribe(subscriber);
                }
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
            synchronized (P2CBalancer.this) {
                buffer.forEach(svc ->
                        svc.close().subscribe(new EmptySubscriber<>())
                );
            }
        };
    }
}
