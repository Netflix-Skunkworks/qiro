package io.qiro.loadbalancing.selector;

import io.qiro.ServiceFactory;
import io.qiro.loadbalancing.WeightedServiceFactory;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class P2C<Req, Resp> implements Selector<Req, Resp> {
    private final int effort;

    public P2C() {
        this(5);
    }

    public P2C(int effort) {
        this.effort = effort;
    }

    @Override
    public ServiceFactory<Req, Resp> apply(List<WeightedServiceFactory<Req, Resp>> factories) {
        int n = factories.size();
        if (n == 0) {
            throw new IllegalStateException("Empty Factory List");
        } else if (n == 1) {
            return factories.get(0);
        } else {
            int i = 0;
            int a = 0;
            int b = 0;
            Random rng = ThreadLocalRandom.current();
            while (i < effort) {
                a = rng.nextInt(n);
                b = rng.nextInt(n - 1);
                if (b >= a) {
                    b = b + 1;
                }
                if (factories.get(a).availability() != 0.0
                    && factories.get(b).availability() != 0.0) {
                    break;
                }
                i += 1;
            }

            if (factories.get(a).getLoad() < factories.get(b).getLoad()) {
                return factories.get(a);
            } else {
                return factories.get(b);
            }
        }
    }
}
