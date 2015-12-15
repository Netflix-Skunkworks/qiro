package io.qiro.loadbalancing.selector;

import io.qiro.ServiceFactory;
import io.qiro.loadbalancing.WeightedServiceFactory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Random<Req, Resp> implements Selector<Req, Resp>
{
    public static Random<?,?> INSTANCE = new Random<>();

    @Override
    public ServiceFactory<Req, Resp> apply(List<WeightedServiceFactory<Req, Resp>> factories) {
        int n = factories.size();
        if (n == 0) {
            throw new IllegalStateException("Empty Factory List");
        } else {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            return factories.get(rng.nextInt(n));
        }
    }
}
