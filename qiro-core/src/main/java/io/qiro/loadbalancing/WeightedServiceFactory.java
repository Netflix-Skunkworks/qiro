package io.qiro.loadbalancing;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.ServiceFactoryProxy;
import io.qiro.ServiceProxy;
import io.qiro.loadbalancing.loadestimator.LoadEstimator;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Add a load/weight to a ServiceFactory
 * The load/weight is the number of outstanding messages multiply by the availability.
 */
public class WeightedServiceFactory<Req, Resp> extends ServiceFactoryProxy<Req, Resp> {

    private static final double PENALTY_CONSTANT = 1_000_000;
    private final LoadEstimator estimator;

    WeightedServiceFactory(ServiceFactory<Req, Resp> underlying, LoadEstimator estimator) {
        super(underlying);
        this.estimator = estimator;
    }

    @Override
    public Publisher<Service<Req, Resp>> apply() {
        return subscriber -> underlying.apply().subscribe(new Subscriber<Service<Req, Resp>>() {
            @Override
            public void onSubscribe(Subscription s) {
                subscriber.onSubscribe(s);
            }

            @Override
            public void onNext(Service<Req, Resp> service) {
                ServiceProxy<Req, Resp> proxy = new ServiceProxy<Req, Resp>(service) {
                    private long t0 = estimator.start();

                    @Override
                    public Publisher<Void> close() {
                        return s -> {
                            estimator.end(t0);
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

    public synchronized double getLoad() {
        double availabilityValue = availability();

        // in case all availabilities are zeros, it nicely degrades to a normal
        // least loaded loadbalancer.
        long now = System.nanoTime();
        if (availabilityValue > 0.0) {
            return estimator.load(now) / availabilityValue;
        } else {
            return estimator.load(now) + PENALTY_CONSTANT;
        }
    }
}
