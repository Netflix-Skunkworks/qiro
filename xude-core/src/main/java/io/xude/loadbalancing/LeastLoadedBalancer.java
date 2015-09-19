package io.xude.loadbalancing;

import io.xude.Service;
import io.xude.ServiceFactory;
import io.xude.util.EmptySubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.*;

public class LeastLoadedBalancer<Req, Resp> implements ServiceFactory<Req,Resp> {

    final private List<WeightedServiceFactory<Req, Resp>> buffer;

    public LeastLoadedBalancer(Publisher<Set<ServiceFactory<Req, Resp>>> factorySet) {
        this.buffer = new ArrayList<>();
        factorySet.subscribe(new EmptySubscriber<Set<ServiceFactory<Req, Resp>>>() {
            @Override
            public void onNext(Set<ServiceFactory<Req, Resp>> set) {
                System.out.println("LeastLoadedBalancer: Storing ServiceFactory");
                synchronized (LeastLoadedBalancer.this) {
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

    private WeightedServiceFactory<Req, Resp> findLeastLoaded() {
        // buffer is garanteed to be non empty here
        List<WeightedServiceFactory<Req, Resp>> leastLoadeds = new ArrayList<>();
        leastLoadeds.add(buffer.get(0));
        double minLoad = buffer.get(0).getLoad();
        for (int i=1; i < buffer.size(); i++) {
            WeightedServiceFactory<Req, Resp> factory = buffer.get(i);
            double load = factory.getLoad();
            if(load <= minLoad) {
                leastLoadeds.clear();
                leastLoadeds.add(factory);
                minLoad = load;
            }
        }
        int i = new Random().nextInt(leastLoadeds.size());
        return leastLoadeds.get(i);
    }

    @Override
    public Publisher<Service<Req, Resp>> apply() {
        return subscriber -> {
            synchronized (LeastLoadedBalancer.this) {
                if (buffer.isEmpty()) {
                    subscriber.onError(new Exception("No Server available in the Loadbalancer!"));
                } else {
                    String message = "LeastLoadedBalancer[";
                    for (WeightedServiceFactory<Req, Resp> factory: buffer) {
                        message += "sf_" + factory.hashCode() + " load=" + factory.getLoad() + ", ";
                    }
                    message += "]";
                    System.out.println(message);

                    WeightedServiceFactory<Req, Resp> factory = findLeastLoaded();
                    System.out.println("LeastLoadedBalancer: choosing sf_" + factory.hashCode());
                    factory.increment();
                    message = "LeastLoadedBalancer[";
                    for (WeightedServiceFactory<Req, Resp> factory0: buffer) {
                        message += "sf_" + factory0.hashCode() + " load=" + factory0.getLoad() + ", ";
                    }
                    message += "]";
                    System.out.println(message);
                    factory.apply().subscribe(subscriber);
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
                synchronized (LeastLoadedBalancer.this) {
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
            synchronized (LeastLoadedBalancer.this) {
                buffer.forEach(svc ->
                        svc.close().subscribe(new EmptySubscriber<>())
                );
            }
        };
    }
}