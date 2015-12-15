package io.qiro.loadbalancing.selector;

import io.qiro.ServiceFactory;
import io.qiro.loadbalancing.WeightedServiceFactory;

import java.util.List;

public class RoundRobin<Req, Resp> implements Selector<Req, Resp> {
    private int i = 0;

    @Override
    public ServiceFactory<Req, Resp> apply(List<WeightedServiceFactory<Req, Resp>> factories) {
        int n = factories.size();
        if (n == 0) {
            throw new IllegalStateException("Empty Factory List");
        } else {
            ServiceFactory<Req, Resp> factory = factories.get(i % n);
            i += 1;
            return factory;
        }
    }
}
