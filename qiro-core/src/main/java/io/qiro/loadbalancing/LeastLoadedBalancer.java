package io.qiro.loadbalancing;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.loadbalancing.loadestimator.LoadEstimator;
import io.qiro.loadbalancing.loadestimator.MsgCountLoadEstimator;
import io.qiro.loadbalancing.loadestimator.PeakEwmaLoadEstimator;
import io.qiro.loadbalancing.selector.LinearMin;
import io.qiro.loadbalancing.selector.P2C;
import io.qiro.loadbalancing.selector.Selector;
import org.reactivestreams.Publisher;

import java.util.Set;
import java.util.function.Supplier;

public class LeastLoadedBalancer<Req, Resp> implements ServiceFactory<Req, Resp> {
    private final BalancerFactory<Req,Resp> balancer;

    public LeastLoadedBalancer(Publisher<Set<ServiceFactory<Req, Resp>>> factories) {
        Selector<Req, Resp> linearMin = new LinearMin<>();
        Supplier<LoadEstimator> msgCount = MsgCountLoadEstimator::new;
        balancer = new BalancerFactory<>(factories, linearMin, msgCount);
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

