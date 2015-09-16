package io.xude;

import io.xude.loadbalancing.HeapBalancer;
import io.xude.loadbalancing.RoundRobinBalancer;
import io.xude.testing.TestingService;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static io.xude.util.Publishers.just;
import static io.xude.util.Publishers.from;
import static io.xude.util.Publishers.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class LoadBalancerTest {
    @Test(timeout = 10_000L)
    public void testRoundRobinBalancer() throws InterruptedException {
        testBalancer(RoundRobinBalancer::new);
    }

    @Test(timeout = 10_000L)
    public void testHeapBalancer() throws InterruptedException {
        testBalancer(HeapBalancer::new);
    }

    private void testBalancer(
        Function<Publisher<ServiceFactory<Integer, String>>, ServiceFactory<Integer, String>> balancerFactory
    ) throws InterruptedException {
        AtomicInteger c0 = new AtomicInteger(0);
        ServiceFactory<Integer, String> factory0 = createFactory("0", c0);
        AtomicInteger c1 = new AtomicInteger(0);
        ServiceFactory<Integer, String> factory1 = createFactory("1", c1);

        ServiceFactory<Integer, String> balancer =
            balancerFactory.apply(from(factory0, factory1));
        Service<Integer, String> service = new FactoryToService<>(balancer);

        List<String> strings1 = toList(service.apply(from(1, 2)));
        List<String> strings2 = toList(service.apply(from(3, 4)));
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

    @Test(timeout = 10_000L)
    public void testAsynchronousRoundRobin() throws InterruptedException {
        testAsynchronousLoadBalancer(RoundRobinBalancer::new);
    }

    @Test(timeout = 10_000L)
    public void testAsynchronousHeapBalancer() throws InterruptedException {
        testAsynchronousLoadBalancer(HeapBalancer::new);
    }

    private void testAsynchronousLoadBalancer(
        Function<Publisher<ServiceFactory<Integer, String>>, ServiceFactory<Integer, String>> balancerFactory
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

        ServiceFactory<Integer, String> balancer = balancerFactory.apply(from(factory0, factory1));
        Service<Integer, String> service = balancer.toService();

        service.apply(just(0)).subscribe(new LoggerSubscriber<>("request 0"));
        service.apply(just(1)).subscribe(new LoggerSubscriber<>("request 1"));
        service.apply(just(2)).subscribe(new LoggerSubscriber<>("request 2"));

        if (service0.queueSize() > service1.queueSize()) {
            assertEquals("Fair balancing", service0.queueSize(), service1.queueSize() + 1);
        } else {
            assertEquals("Fair balancing", service0.queueSize() + 1, service1.queueSize());
        }

        while(service0.queueSize() > 0) {
            service0.respond();
        }
        service0.complete();
        while(service1.queueSize() > 0) {
            service1.respond();
        }
        service1.complete();
    }

    class LoggerSubscriber<T> implements Subscriber<T> {
        private String name;

        LoggerSubscriber(String name){
            this.name = name;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T t) {
            System.out.println("Received " + t);
        }

        @Override
        public void onError(Throwable t) {
            t.printStackTrace();
        }

        @Override
        public void onComplete() {
            System.out.println(name + " is complete!");
        }
    }
}
