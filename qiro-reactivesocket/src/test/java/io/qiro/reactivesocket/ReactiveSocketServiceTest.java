package io.qiro.reactivesocket;

import io.qiro.Server;
import io.qiro.Service;
import io.qiro.codec.UTF8Codec;
import io.qiro.util.EmptySubscriber;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;

import static io.qiro.util.Publishers.from;

public class ReactiveSocketServiceTest {

    @Test(timeout = 1_000_000L)
    public void testReactiveSocketPingPong() throws Exception {
        Server server = ServerBuilder.<String, String>get()
            .listen(8888)
            .codec(UTF8Codec.INSTANCE)
            .build(new Service<String, String>() {
                @Override
                public Publisher<String> apply(Publisher<String> inputs) {
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

        CountDownLatch latch = new CountDownLatch(1);
        client.apply(from("ping", "blabla")).subscribe(new Subscriber<String>() {
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
        server.close().subscribe(EmptySubscriber.INSTANCE);

        System.out.println("### FINITO ###");
    }
}
