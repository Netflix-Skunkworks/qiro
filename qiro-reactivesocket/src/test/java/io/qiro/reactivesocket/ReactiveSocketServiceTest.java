package io.qiro.reactivesocket;

import io.qiro.Service;
import io.qiro.codec.UTF8Codec;
import org.junit.Ignore;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;

import static io.qiro.util.Publishers.from;

public class ReactiveSocketServiceTest {
    @Ignore // require a running websocket server on port 8888
    @Test(timeout = 10_000L)
    public void testReactiveSocket() throws Exception {

        Service<String, String> client = ClientBuilder.<String, String>get()
            .destination("dns://localhost:8888")
            .codec(UTF8Codec.INSTANCE)
            .build();

        CountDownLatch latch = new CountDownLatch(1);
        client.apply(from("input0", "input1", "input2")).subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String response) {
                System.out.println("Receiving response: '" + response + "'");
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("Receiving an error: " + t);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        latch.await();
    }
}
