package io.qiro.loadbalancing;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.loadbalancing.selector.RoundRobin;
import org.reactivestreams.Publisher;

import java.util.Set;

public class RoundRobinBalancer<Req, Resp> implements ServiceFactory<Req,Resp> {
    private final BalancerFactory<Req,Resp> balancer;

    public RoundRobinBalancer(Publisher<Set<ServiceFactory<Req, Resp>>> factorySet) {
        RoundRobin<Req, Resp> selector = new RoundRobin<>();
        balancer = new BalancerFactory<>(factorySet, selector);
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
