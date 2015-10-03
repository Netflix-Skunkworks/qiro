package io.qiro.http;

import io.netty.handler.codec.http.*;
import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.filter.RetryFilter;
import io.qiro.filter.TimeoutFilter;
import io.qiro.loadbalancing.P2CBalancer;
import io.qiro.pool.WatermarkPool;
import org.junit.Ignore;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static io.qiro.util.Publishers.*;
import static junit.framework.TestCase.assertEquals;

public class SimpleHttpClientTest {

    @Ignore
    @Test(timeout = 30_000L)
    public void testSimpleHttpClient() throws InterruptedException {
        NettyTransportConnector connector = new NettyTransportConnector(11);

        Set<ServiceFactory<HttpRequest, HttpResponse>> factories = new HashSet<>();
//        factories.add(connector.toFactory(new InetSocketAddress(8080)));
//        factories.add(connector.toFactory(new InetSocketAddress(8081)));

        factories.add(
            new WatermarkPool<>(1, 10, 128,
                connector.toFactory(new InetSocketAddress(8080))
            )
        );
        factories.add(
            new WatermarkPool<>(1, 10, 128,
                connector.toFactory(new InetSocketAddress(8081))
            )
        );

        Service<HttpRequest, HttpResponse> service =
            new RetryFilter<HttpRequest, HttpResponse>(3)
                .andThen(new TimeoutFilter<>(5000))
                .andThen(new P2CBalancer<>(from(factories))
                .toService());

        int n = 128;
        CountDownLatch latch = new CountDownLatch(n);

        int i = 0;
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failure = new AtomicInteger(0);
        while (i < n) {
            service.apply(just(createGetRequest("/", "127.0.0.1")))
                .subscribe(new Subscriber<HttpResponse>() {
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(HttpResponse httpResponse) {
                        System.out.println(httpResponse.status());
                    }

                    @Override
                    public void onError(Throwable t) {
                        failure.incrementAndGet();
                        System.err.println("Exception " + t);
                        latch.countDown();
                        System.out.println(latch.getCount());
                    }

                    @Override
                    public void onComplete() {
                        success.incrementAndGet();
                        latch.countDown();
                        System.out.println(latch.getCount());
                    }
                });
            i += 1;
        }
        latch.await();
        System.out.println("### FINITO ###");
        System.out.println("successes: " + success.get() + "  failures: " + failure.get());
        assertEquals(0, failure.get());
        assertEquals(n, success.get());
    }

    private HttpRequest createGetRequest(String path, String host) {
        HttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, path);
        request.headers().set(HttpHeaderNames.HOST, host);
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);

        return request;
    }
}
