package io.xude.loadbalancing;

import io.xude.Service;
import io.xude.ServiceFactory;
import io.xude.ServiceFactoryProxy;
import io.xude.ServiceProxy;
import io.xude.util.EmptySubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LeastLoadedBalancer<Req, Resp> implements ServiceFactory<Req,Resp> {

    final private List<WeightedServiceFactory> buffer;

    /**
     * Add a load/weight to a ServiceFactory
     * The load/weight is the number of outstanding messages multiply by the availability.
     */
    private class WeightedServiceFactory extends ServiceFactoryProxy<Req, Resp> {

        private double load = 0.0;
        private double availabilityValue = 0.0;

        WeightedServiceFactory(ServiceFactory<Req, Resp> underlying) {
            super(underlying);
        }

        @Override
        public Publisher<Service<Req, Resp>> apply() {
            return new Publisher<Service<Req, Resp>>() {
                @Override
                public void subscribe(Subscriber<? super Service<Req, Resp>> subscriber) {
                    underlying.apply().subscribe(new Subscriber<Service<Req, Resp>>() {
                        @Override
                        public void onSubscribe(Subscription s) {
                            subscriber.onSubscribe(s);
                        }

                        @Override
                        public void onNext(Service<Req, Resp> service) {
                            ServiceProxy<Req, Resp> proxy = new ServiceProxy<Req, Resp>(service) {
                                @Override
                                public Publisher<Void> close() {
                                    return s -> {
                                        decrement();
                                        underlying.close().subscribe(s);
                                    };
                                }
                            };
                            subscriber.onNext(proxy);
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
            };
        }

        synchronized void increment() {
            System.out.println("LeastLoadedBalancer: load +1 on svc_" + hashCode());
            load += 1;
        }

        synchronized void decrement() {
            System.out.println("LeastLoadedBalancer: load -1 on svc_" + hashCode());
            load -= 1;
        }

        synchronized double getLoad() {
            // Expect that the subscription is synchronous
            availability().subscribe(new EmptySubscriber<Double>() {
                @Override
                public void onNext(Double x) {
                    availabilityValue = x;
                }
            });

            // in case all availabilities are zeros, it nicely degrades to a normal
            // least loaded loadbalancer.
            double penaltyFactor = (double) Integer.MAX_VALUE;
            if (availabilityValue != 0.0) {
                penaltyFactor = 1.0 / availabilityValue;
            }

            return penaltyFactor * load;
        }
    }

    public LeastLoadedBalancer(Publisher<ServiceFactory<Req, Resp>> factories) {
        this.buffer = new ArrayList<>();
        factories.subscribe(new EmptySubscriber<ServiceFactory<Req, Resp>>() {
            @Override
            public void onNext(ServiceFactory<Req, Resp> factory) {
                factory.apply().subscribe(new EmptySubscriber<Service<Req, Resp>>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(1L);
                    }

                    @Override
                    public void onNext(Service<Req, Resp> service) {
                        System.out.println("LeastLoadedBalancer: Storing ServiceFactory");
                        synchronized (LeastLoadedBalancer.this) {
                            buffer.add(new WeightedServiceFactory(factory));
                        }
                    }
                });
            }
        });
    }

    private WeightedServiceFactory findLeastLoaded() {
        // buffer is garanteed to be non empty here
        List<WeightedServiceFactory> leastLoadeds = new ArrayList<>();
        leastLoadeds.add(buffer.get(0));
        double minLoad = buffer.get(0).getLoad();
        for (int i=1; i < buffer.size(); i++) {
            WeightedServiceFactory factory = buffer.get(i);
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
                    for (WeightedServiceFactory factory: buffer) {
                        message += "sf_" + factory.hashCode() + " load=" + factory.load + ", ";
                    }
                    message += "]";
                    System.out.println(message);

                    WeightedServiceFactory factory = findLeastLoaded();
                    System.out.println("LeastLoadedBalancer: choosing sf_" + factory.hashCode());
                    factory.increment();
                    message = "LeastLoadedBalancer[";
                    for (WeightedServiceFactory factory0: buffer) {
                        message += "sf_" + factory0.hashCode() + " load=" + factory0.load + ", ";
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
