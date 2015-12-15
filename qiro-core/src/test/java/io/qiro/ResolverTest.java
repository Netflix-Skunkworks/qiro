package io.qiro;

import io.qiro.loadbalancing.RoundRobinBalancer;
import io.qiro.resolver.Resolvers;
import io.qiro.resolver.TransportConnector;
import io.qiro.testing.LoggerSubscriber;
import io.qiro.testing.TestingService;
import io.qiro.util.Publishers;
import org.junit.Test;
import org.reactivestreams.Publisher;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Set;

import static io.qiro.util.Publishers.just;
import static junit.framework.TestCase.assertEquals;

public class ResolverTest {

    @Test(timeout = 10_000L)
    public void testResolver() throws InterruptedException {
        TestingService<Integer, String> svc_1 = new TestingService<>(Object::toString);
        TestingService<Integer, String> svc_2 = new TestingService<>(Object::toString);

        Set<SocketAddress> addresses = new HashSet<>();
        addresses.add(new InetSocketAddress(1));
        addresses.add(new InetSocketAddress(2));
        Publishers.from(addresses);

        TransportConnector<Integer, String> connector = new TransportConnector<Integer, String>() {
            @Override
            public Publisher<Service<Integer, String>> apply(SocketAddress address) {
                return s -> {
                    InetSocketAddress addr = (InetSocketAddress) address;
                    if (addr.getPort() == 1) {
                        s.onNext(svc_1);
                    } else {
                        s.onNext(svc_2);
                    }
                };
            }
        };

        Publisher<Set<ServiceFactory<Integer, String>>> factories =
            Resolvers.resolveFactory(Publishers.from(addresses), connector);

        Service<Integer, String> service = new RoundRobinBalancer<>(factories).toService();

        service.requestResponse(1).subscribe(new LoggerSubscriber<>("req 1"));
        assertEquals(1, svc_1.queueSize() + svc_2.queueSize());

        service.requestResponse(2).subscribe(new LoggerSubscriber<>("req 2"));
        assertEquals(1, svc_1.queueSize());
        assertEquals(1, svc_2.queueSize());

        svc_1.respond();
        svc_1.complete();
        assertEquals(1, svc_1.queueSize() + svc_2.queueSize());

        svc_2.respond();
        svc_2.complete();
        assertEquals(0, svc_1.queueSize());
        assertEquals(0, svc_2.queueSize());
    }
}
