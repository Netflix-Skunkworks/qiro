package io.qiro.http;

import io.netty.handler.codec.http.*;
import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.failures.FailureAccrualDetector;
import io.qiro.filter.TimeoutFilter;
import io.qiro.loadbalancing.P2CBalancer;
import io.qiro.pool.WatermarkPool;
import io.qiro.util.HashwheelTimer;
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
        NettyTransportConnector connector = new NettyTransportConnector();

        Set<ServiceFactory<HttpRequest, HttpResponse>> factories = new HashSet<>();

        factories.add(
            new FailureAccrualDetector<>(
                new WatermarkPool<>(1, 10, 128,
                    connector.toFactory(new InetSocketAddress(8080))
                )
            )
        );
        factories.add(
            new FailureAccrualDetector<>(
                new WatermarkPool<>(1, 10, 128,
                    connector.toFactory(new InetSocketAddress(8081))
                )
            )
        );

        Service<HttpRequest, HttpResponse> service =
            new TimeoutFilter<HttpRequest, HttpResponse>(5000, HashwheelTimer.INSTANCE)
                .andThen(new P2CBalancer<>(from(factories)))
                .toService();

        // Stack is:
        //
        // FactoryToService        (S)
        // TimeoutFilter           (S)
        // LoadBalancer            (SF)
        // FailureAccrualDetector  (SF)
        // WatermarkPool           (SF)
        // RxNettyService          (S)

        int n = 128;
        CountDownLatch latch = new CountDownLatch(n);

        int i = 0;
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failure = new AtomicInteger(0);
        while (i < n) {
            HttpRequest get = createGetRequest("/", "127.0.0.1");
            service.requestResponse(get)
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
                    }

                    @Override
                    public void onComplete() {
                        success.incrementAndGet();
                        latch.countDown();
                    }
                });
            i += 1;
        }
        latch.await();
        Thread.sleep(10);
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
