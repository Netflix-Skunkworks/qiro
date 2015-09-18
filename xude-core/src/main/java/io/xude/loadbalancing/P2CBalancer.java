package io.xude.loadbalancing;

import io.xude.Service;
import io.xude.ServiceFactory;
import io.xude.util.EmptySubscriber;
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
                            factory.close().subscribe(new EmptySubscriber<>());
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
                        int a = rng.nextInt(n);
                        int b = rng.nextInt(n - 1);
                        if (b >= a) {
                            b = b + 1;
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
                    selectedFactory.apply().subscribe(new Subscriber<Service<Req, Resp>>() {
                        @Override
                        public void onSubscribe(Subscription s) {
                            subscriber.onSubscribe(s);
                        }

                        @Override
                        public void onNext(Service<Req, Resp> service) {
                            subscriber.onNext(service);
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
        };
    }

    @Override
    public Publisher<Double> availability() {
        return new Publisher<Double>() {
            private double sum = 0.0;
            private int count = 0;

            @Override
            public void subscribe(Subscriber<? super Double> subscriber) {
                synchronized (P2CBalancer.this) {
                    for (ServiceFactory<Req, Resp> factory : buffer) {
                        factory.availability().subscribe(new EmptySubscriber<Double>() {
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
            synchronized (P2CBalancer.this) {
                buffer.forEach(svc ->
                        svc.close().subscribe(new EmptySubscriber<>())
                );
            }
        };
    }
}
