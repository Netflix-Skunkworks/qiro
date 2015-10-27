package io.qiro;

import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertTrue;
import static io.qiro.util.Publishers.*;

public class ServiceTest {
    @Test
    public void testBasicService() throws InterruptedException {
        testService(new DummyService());
    }

    static void testService(Service<Integer, String> aService) throws InterruptedException {
        testServiceFnf(aService);
        testServiceRequestResponse(aService);
        testServiceStream(aService);
        testServiceSubscription(aService);
        testServiceChannel(aService);
    }

    static void testServiceFnf(Service<Integer, String> aService) throws InterruptedException {
        Publisher<Void> forget = aService.fireAndForget(1);
        CountDownLatch latch = new CountDownLatch(1);
        forget.subscribe(new Subscriber<Void>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(1L);
            }

            @Override
            public void onNext(Void aVoid) {}

            @Override
            public void onError(Throwable t) {
                throw new RuntimeException("Shouldn't generate an exception");
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        latch.await();
    }


    static void testServiceRequestResponse(Service<Integer, String> aService) throws InterruptedException {
        Publisher<String> stringPublisher = aService.requestResponse(1);
        List<String> strings0 = toList(stringPublisher);
        assertTrue(strings0.equals(Arrays.asList("RESPONSE:1")));
    }

    static void testServiceStream(Service<Integer, String> aService) throws InterruptedException {
        Publisher<String> stringPublisher = aService.requestStream(1);
        List<String> strings0 = toList(stringPublisher);
        assertTrue(strings0.equals(Arrays.asList(
            "STREAM:1", "STREAM(+1):2", "STREAM(*2):2"
        )));
    }

    static void testServiceSubscription(Service<Integer, String> aService) throws InterruptedException {
        Publisher<String> stringPublisher = aService.requestSubscription(1);
        CountDownLatch latch3 = new CountDownLatch(3);
        List<String> strings0 = new ArrayList<>();
        stringPublisher.subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String s) {
                strings0.add(s);
                latch3.countDown();
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onComplete() {
                throw new RuntimeException("Should never complete!");
            }
        });
        assertTrue(strings0.equals(Arrays.asList(
            "SUBSCRIPTION:1", "SUBSCRIPTION(+1):2", "SUBSCRIPTION(*2):2"
        )));
    }

    static void testServiceChannel(Service<Integer, String> aService) throws InterruptedException {
        Publisher<String> stringPublisher = aService.requestChannel(just(1));
        List<String> strings0 = toList(stringPublisher);
        assertTrue(strings0.equals(Arrays.asList(
            "CHANNEL:1", "CHANNEL(+1):2"
        )));

        Publisher<String> stringPublisher2 =
            aService.requestChannel(from(1, 2, 3, 4, 5));
        List<String> strings1 = toList(stringPublisher2);
        assertTrue(strings1.equals(Arrays.asList(
            "CHANNEL:1", "CHANNEL(+1):2",
            "CHANNEL:2", "CHANNEL(+1):3",
            "CHANNEL:3", "CHANNEL(+1):4",
            "CHANNEL:4", "CHANNEL(+1):5",
            "CHANNEL:5", "CHANNEL(+1):6"
        )));

        Publisher<Double> doubles = from(1.0, 2.0, 3.0, 4.0, 5.0);
        Filter<Double, Integer, String, String> filter =
            Filters.fromFunction(x -> (int) (2 * x), str -> "'" + str + "'");
        Publisher<String> apply = filter.apply(doubles, aService);
        List<String> strings2 = toList(apply);
        assertTrue(strings2.equals(Arrays.asList(
            "'CHANNEL:2'", "'CHANNEL(+1):3'",
            "'CHANNEL:4'", "'CHANNEL(+1):5'",
            "'CHANNEL:6'", "'CHANNEL(+1):7'",
            "'CHANNEL:8'", "'CHANNEL(+1):9'",
            "'CHANNEL:10'", "'CHANNEL(+1):11'"
        )));
    }

    static class DummyService implements Service<Integer, String> {
        private boolean closed = false;

        @Override
        public Publisher<String> apply(Publisher<Integer> inputs) {
            if (closed) {
                throw new RuntimeException("Closed service!");
            }
            return subscriber -> {
                inputs.subscribe(new Subscriber<Integer>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        subscriber.onSubscribe(s);
                    }

                    @Override
                    public void onNext(Integer integer) {
                        subscriber.onNext("CHANNEL:" + integer);
                        subscriber.onNext("CHANNEL(+1):" + (integer + 1));
                    }

                    @Override
                    public void onError(Throwable t) {
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        subscriber.onComplete();
                    }
                });
            };
        }

        @Override
        public Publisher<Void> fireAndForget(Integer integer) {
            if (closed) {
                throw new RuntimeException("Closed service!");
            }
            return Subscriber::onComplete;
        }

        @Override
        public Publisher<String> requestResponse(Integer integer) {
            if (closed) {
                throw new RuntimeException("Closed service!");
            }
            return subsriber -> {
                subsriber.onNext("RESPONSE:" + integer);
                subsriber.onComplete();
            };
        }

        @Override
        public Publisher<String> requestStream(Integer integer) {
            if (closed) {
                throw new RuntimeException("Closed service!");
            }
            return subsriber -> {
                subsriber.onNext("STREAM:" + integer);
                subsriber.onNext("STREAM(+1):" + (integer + 1));
                subsriber.onNext("STREAM(*2):" + (integer * 2));
                subsriber.onComplete();
            };
        }

        @Override
        public Publisher<String> requestSubscription(Integer integer) {
            if (closed) {
                throw new RuntimeException("Closed service!");
            }
            return subsriber -> {
                subsriber.onNext("SUBSCRIPTION:" + integer);
                subsriber.onNext("SUBSCRIPTION(+1):" + (integer + 1));
                subsriber.onNext("SUBSCRIPTION(*2):" + (integer * 2));
            };
        }

        @Override
        public Publisher<String> requestChannel(Publisher<Integer> inputs) {
            return apply(inputs);
        }

        @Override
        public double availability() {
            return 1.0;
        }

        @Override
        public Publisher<Void> close() {
            return s -> {
                closed = true;
                s.onComplete();
            };
        }
    }
}
