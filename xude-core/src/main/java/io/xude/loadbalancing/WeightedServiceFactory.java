package io.xude.loadbalancing;

import io.xude.Service;
import io.xude.ServiceFactory;
import io.xude.ServiceFactoryProxy;
import io.xude.ServiceProxy;
import io.xude.util.EmptySubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Add a load/weight to a ServiceFactory
 * The load/weight is the number of outstanding messages multiply by the availability.
 */
class WeightedServiceFactory<Req, Resp> extends ServiceFactoryProxy<Req, Resp> {

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
        System.out.println("WeightedServiceFactory: load +1 on svc_" + hashCode());
        load += 1;
    }

    synchronized void decrement() {
        System.out.println("WeightedServiceFactory: load -1 on svc_" + hashCode());
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
