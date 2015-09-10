package io.xude.util;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class Publishers {
    public static <T> Publisher<T> just(T singleValue) {
        return range(singleValue);
    }

    public static <T> Publisher<T> range(T... values) {
        return new Publisher<T>() {
            @Override
            public void subscribe(Subscriber<? super T> s) {
                s.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        for (T value: values) {
                            s.onNext(value);
                        }
                        s.onComplete();
                    }

                    @Override
                    public void cancel() {
                    }
                });
            }
        };
    }

    public static <T, U> Publisher<T> map(Publisher<U> inputs, Function<U, T> f) {
        return new Publisher<T>() {
            @Override
            public void subscribe(Subscriber<? super T> subscriber) {
                inputs.subscribe(new Subscriber<U>() {
                    @Override
                    public void onSubscribe(Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(U filterReq) {
                        final T req = f.apply(filterReq);
                        subscriber.onNext(req);
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
            }
        };
    }

    public static <T> List<T> toList(Publisher<T> publisher) throws InterruptedException {
        List<T> result = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        publisher.subscribe(new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription s) {}

            @Override
            public void onNext(T t) {
                result.add(t);
            }

            @Override
            public void onError(Throwable t) {
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        latch.await(5, TimeUnit.SECONDS);
        return result;
    }
}
