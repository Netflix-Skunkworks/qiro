package io.qiro.reactivesocket;

import io.qiro.Server;
import io.qiro.Service;
import io.qiro.codec.UTF8Codec;
import io.qiro.util.EmptySubscriber;
import org.junit.Ignore;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;

import static io.qiro.util.Publishers.from;

public class ReactiveSocketServiceTest {

    @Ignore
    @Test(timeout = 30_000L)
    public void testReactiveSocketPingPong() throws Exception {
        Server server = ServerBuilder.<String, String>get()
            .listen(8888)
            .codec(UTF8Codec.INSTANCE)
            .build(new Service<String, String>() {
                @Override
                public Publisher<String> requestResponse(String input) {
                    return output -> {
                        output.onNext(input.toLowerCase());
                        output.onComplete();
                    };
                }

                @Override
                public Publisher<String> requestChannel(Publisher<String> inputs) {
                    return outputs ->
                        inputs.subscribe(new Subscriber<String>() {
                            @Override
                            public void onSubscribe(Subscription s) {
                                s.request(128);
                            }

                            @Override
                            public void onNext(String input) {
                                if (input.toLowerCase().equals("ping")) {
                                    outputs.onNext("Pong!");
                                } else {
                                    outputs.onNext("I don't understand " + input);
                                }
                            }

                            @Override
                            public void onError(Throwable t) {
                                outputs.onError(t);
                            }

                            @Override
                            public void onComplete() {
                                outputs.onComplete();
                            }
                        });
                }

                @Override
                public double availability() {
                    return 1.0;
                }

                @Override
                public Publisher<Void> close() {
                    return Subscriber::onComplete;
                }
            });


        Service<String, String> client = ClientBuilder.<String, String>get()
            .destination("dns://127.0.0.1:8888")
            .codec(UTF8Codec.INSTANCE)
            .build();

        long start = System.nanoTime();
        int n = 1000;
        CountDownLatch latch = new CountDownLatch(n);
        while (0 < n) {
//            client.requestChannel(from("ping", "blabla")).subscribe(new Subscriber<String>() {
            client.requestResponse("ping").subscribe(new Subscriber<String>() {
                @Override
                public void onSubscribe(Subscription s) {
                    s.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(String response) {
//                    System.out.println("Receiving response: '" + response + "'");
                }

                @Override
                public void onError(Throwable t) {
//                    System.out.println("Receiving an error: " + t);
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    latch.countDown();
                }
            });
            n -= 1;
        }

        latch.await();
        long elapsed = System.nanoTime() - start;
        server.close().subscribe(EmptySubscriber.INSTANCE);

        System.out.println("### FINITO ###");
        System.out.println("elapsed: " + elapsed / 1_000_000L);
    }
}
