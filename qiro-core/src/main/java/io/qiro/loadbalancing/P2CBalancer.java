package io.qiro.loadbalancing;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.loadbalancing.loadestimator.LoadEstimator;
import io.qiro.loadbalancing.loadestimator.PeakEwmaLoadEstimator;
import io.qiro.loadbalancing.selector.P2C;
import io.qiro.loadbalancing.selector.Selector;
import org.reactivestreams.Publisher;

import java.util.*;
import java.util.function.Supplier;

public class P2CBalancer<Req, Resp> implements ServiceFactory<Req, Resp> {
    private final BalancerFactory<Req,Resp> balancer;

    public P2CBalancer(Publisher<Set<ServiceFactory<Req, Resp>>> factories) {
        Selector<Req, Resp> p2c = new P2C<>();
        Supplier<LoadEstimator> peakEwma = PeakEwmaLoadEstimator::new;
        balancer = new BalancerFactory<>(factories, p2c, peakEwma);
    }

    @Override
    public Publisher<Service<Req, Resp>> apply() {
        return balancer.apply();
    }

    @Override
    public synchronized double availability() {
        return balancer.availability();
    }

    @Override
    public Publisher<Void> close() {
        return balancer.close();
    }
}
