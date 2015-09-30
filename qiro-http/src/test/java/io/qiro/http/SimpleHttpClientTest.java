package io.qiro.http;

import io.netty.handler.codec.http.*;
import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.filter.RetryFilter;
import io.qiro.filter.TimeoutFilter;
import io.qiro.loadbalancing.P2CBalancer;
import io.qiro.testing.LoggerSubscriber;
import io.qiro.util.Publishers;
import org.junit.Test;
import org.reactivestreams.Publisher;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.qiro.util.Publishers.*;

public class SimpleHttpClientTest {
    @Test(timeout = 200_000L)
    public void testSimpleHttpClient() throws InterruptedException {
        NettyTransportConnector connector = new NettyTransportConnector();

        Set<ServiceFactory<HttpRequest, HttpResponse>> factories = new HashSet<>();
        factories.add(connector.toFactory(new InetSocketAddress(8080)));
        factories.add(connector.toFactory(new InetSocketAddress(8081)));

//        factories.add(
//            new WatermarkPool<>(1,10,10,
//                connector.toFactory(new InetSocketAddress(8080))
//            )
//        );
//        factories.add(
//            new WatermarkPool<>(1,10,10,
//                connector.toFactory(new InetSocketAddress(8081))
//            )
//        );

        Service<HttpRequest, HttpResponse> service =
            new RetryFilter<HttpRequest, HttpResponse>(3)
                .andThen(new TimeoutFilter<>(1000))
                .andThen(new P2CBalancer<>(from(factories)).toService());

        ExecutorService executor = Executors.newFixedThreadPool(64);
        int n = 1;
        CountDownLatch latch = new CountDownLatch(n);

        int i = 0;
        while (i < n) {
            executor.submit( () -> {
                try {
                    HttpResponse httpResponse =
                        toSingle(service.apply(just(createGetRequest("/", "127.0.0.1"))));
                    System.out.println(httpResponse.status());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
                System.out.println(latch.getCount());
            });
            i += 1;
        }
        latch.await();
        Thread.sleep(100);
    }

    private HttpRequest createGetRequest(String path, String host) {
        HttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, path);
        request.headers().set(HttpHeaderNames.HOST, host);
//        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);

        return request;
    }
}
