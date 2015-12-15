package io.qiro.loadbalancing.selector;

import io.qiro.ServiceFactory;
import io.qiro.loadbalancing.WeightedServiceFactory;

import java.util.List;

public class LinearMin<Req, Resp> implements Selector<Req, Resp> {
    @Override
    public ServiceFactory<Req, Resp> apply(List<WeightedServiceFactory<Req, Resp>> factories) {
        int n = factories.size();
        if (n == 0) {
            throw new IllegalStateException("Empty Factory List");
        } else {
            WeightedServiceFactory<Req, Resp> leastLoaded = factories.get(0);
            for(WeightedServiceFactory<Req, Resp> factory: factories) {
                if (factory.getLoad() < leastLoaded.getLoad()) {
                    leastLoaded = factory;
                }
            }
            System.out.println("Selecting SF " + leastLoaded + " load=" + leastLoaded.getLoad());
            return leastLoaded;
        }
    }
}
