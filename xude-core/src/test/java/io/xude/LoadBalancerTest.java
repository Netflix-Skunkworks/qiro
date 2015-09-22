package io.xude;

import io.xude.loadbalancing.LeastLoadedBalancer;
import io.xude.loadbalancing.P2CBalancer;
import io.xude.loadbalancing.RoundRobinBalancer;
import io.xude.testing.TestingService;
import io.xude.testing.LoggerSubscriber;
import org.junit.Test;
import org.reactivestreams.Publisher;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static io.xude.util.Publishers.just;
import static io.xude.util.Publishers.from;
import static io.xude.util.Publishers.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoadBalancerTest {
    @Test(timeout = 10_000L)
    public void testRoundRobinBalancer() throws InterruptedException {
        AtomicInteger c0 = new AtomicInteger(0);
        ServiceFactory<Integer, String> factory0 = createFactory("0", c0);
        AtomicInteger c1 = new AtomicInteger(0);
        ServiceFactory<Integer, String> factory1 = createFactory("1", c1);
        Set<ServiceFactory<Integer, String>> factories = new HashSet<>();
        factories.add(factory0);
        factories.add(factory1);

        Service<Integer, String> service =
            new RoundRobinBalancer<>(from(factories)).toService();

        service.availability().subscribe(new LoggerSubscriber<>("availability"));

        List<String> strings1 = toList(service.apply(from(1, 2)));
        List<String> strings2 = toList(service.apply(from(3, 4)));
        System.out.println(strings1);
        System.out.println(strings2);

        assertEquals(2, c0.get());
        assertEquals(2, c1.get());
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

    @Test(timeout = 10_000L)
    public void testAsynchronousRoundRobin() throws InterruptedException {
        testFairBalancing(RoundRobinBalancer::new);
    }

    @Test(timeout = 10_000L)
    public void testAsynchronousLeastLoadedBalancer() throws InterruptedException {
        testFairBalancing(LeastLoadedBalancer::new);
    }

    private void testFairBalancing(
        Function<Publisher<Set<ServiceFactory<Integer, String>>>, ServiceFactory<Integer, String>> balancerFactory
    ) throws InterruptedException {
        TestingService<Integer, String> service0 =
            new TestingService<>(i -> i.toString() + " from service0");
        TestingService<Integer, String> service1 =
            new TestingService<>(i -> i.toString() + " from service1");

        ServiceFactory<Integer, String> factory0 = ServiceFactories.fromFunctions(
            () -> service0,
            () -> null
        );
        ServiceFactory<Integer, String> factory1 = ServiceFactories.fromFunctions(
            () -> service1,
            () -> null
        );
        Set<ServiceFactory<Integer, String>> factories = new HashSet<>();
        factories.add(factory0);
        factories.add(factory1);

        ServiceFactory<Integer, String> balancer = balancerFactory.apply(from(factories));
        Service<Integer, String> service = balancer.toService();

        service.apply(just(0)).subscribe(new LoggerSubscriber<>("request 0"));
        service.apply(just(1)).subscribe(new LoggerSubscriber<>("request 1"));
        service.apply(just(2)).subscribe(new LoggerSubscriber<>("request 2"));
        service.apply(just(3)).subscribe(new LoggerSubscriber<>("request 3"));
        assertEquals("Fair balancing", service0.queueSize(), service1.queueSize());

        service0.respond();
        service0.complete();
        assertEquals("Service0 load is null", service0.queueSize(), 0);

        service1.respond();
        service1.complete();
        assertEquals("Service1 load is null", service1.queueSize(), 0);
    }

    @Test(timeout = 10_000L)
    public void testLeastLoadedLoadBalancer() throws InterruptedException {
        testMoreFairBalancing(LeastLoadedBalancer::new);
    }

    @Test(timeout = 10_000L)
    public void testP2CBalancer() throws InterruptedException {
        // when the number of factories is 2, the P2C should behave exactly like LeastLoaded
        testMoreFairBalancing(P2CBalancer::new);
    }

    private void testMoreFairBalancing(
        Function<Publisher<Set<ServiceFactory<Integer, String>>>, ServiceFactory<Integer, String>> balancerFactory
    ) throws InterruptedException {
        // The goal of this test is to ensure that the load balancer always select
        // the least loaded ServiceFactory (or one of the least loaded when
        // there're more than one with the minimum load)

        TestingService<Integer, String> service0 =
            new TestingService<Integer, String>(i -> i.toString() + " from service0") {
                @Override
                public Publisher<Void> close() {
                    // allow reuse of the same service for testing purposes
                    return s -> s.onComplete();
                }
            };
        TestingService<Integer, String> service1 =
            new TestingService<Integer, String>(i -> i.toString() + " from service1") {
                @Override
                public Publisher<Void> close() {
                    // allow reuse of the same service for testing purposes
                    return s -> s.onComplete();
                }
            };

        ServiceFactory<Integer, String> factory0 = ServiceFactories.fromFunctions(
            () -> service0,
            () -> null
        );
        ServiceFactory<Integer, String> factory1 = ServiceFactories.fromFunctions(
            () -> service1,
            () -> null
        );
        Set<ServiceFactory<Integer, String>> factories = new HashSet<>();
        factories.add(factory0);
        factories.add(factory1);

        Service<Integer, String> service =
            balancerFactory.apply(from(factories)).toService();

        service.apply(just(0)).subscribe(new LoggerSubscriber<>("request 0"));
        service.apply(just(1)).subscribe(new LoggerSubscriber<>("request 1"));
        service.apply(just(2)).subscribe(new LoggerSubscriber<>("request 2"));
        service.apply(just(3)).subscribe(new LoggerSubscriber<>("request 3"));

        // loads: [svc0: 2, svc1: 2]
        assertEquals("Fair balancing", service0.queueSize(), service1.queueSize());

        service0.respond();
        service0.complete();
        // loads: [svc0: 0, svc1: 2]
        assertEquals(0, service0.queueSize());
        assertEquals(2, service1.queueSize());

        // loads is [svc0: 0, svc1: 2]
        // next call will chose service0
        service.apply(just(4)).subscribe(new LoggerSubscriber<>("request 4"));

        // now loads are [svc0: 1, svc1: 2]
        assertEquals(1, service0.queueSize());
        assertEquals(2, service1.queueSize());

        service1.respond();
        service1.complete();
        // loads: [svc0: 1, svc1: 0]
        assertEquals(1, service0.queueSize());
        assertEquals(0, service1.queueSize());
    }
}
