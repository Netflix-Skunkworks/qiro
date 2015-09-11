package io.xude;

import io.xude.loadbalancing.RoundRobinBalancer;
import org.junit.Test;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.xude.util.Publishers.range;
import static io.xude.util.Publishers.toList;
import static org.junit.Assert.assertTrue;

public class LoadBalancerTest {
    @Test
    public void testHeapBalancer() throws InterruptedException {
        AtomicInteger c0 = new AtomicInteger(0);
        ServiceFactory<Integer, String> factory0 = createFactory("0", c0);
        AtomicInteger c1 = new AtomicInteger(0);
        ServiceFactory<Integer, String> factory1 = createFactory("1", c1);

        ServiceFactory<Integer, String> balancer =
            new RoundRobinBalancer<>(range(factory0, factory1));
        Service<Integer, String> service = new FactoryToService<>(balancer);

        List<String> strings1 = toList(service.apply(range(1, 2)));
        List<String> strings2 = toList(service.apply(range(3, 4)));
        System.out.println(strings1);
        System.out.println(strings2);

        assertTrue(c0.get() == 2);
        assertTrue(c1.get() == 2);
    }

    private ServiceFactory<Integer, String> createFactory(String name, AtomicInteger counter) {
        return ServiceFactories.fromFunctions(
            () -> Services.fromFunction(x -> {
                counter.incrementAndGet();
                System.out.println("Service["+name+"].apply("+x+")");
                return x.toString();
            }),
            () -> {
                assertTrue("Service["+name+"] shouldn't be closed!", false);
                return null;
            }
        );
    }
}
