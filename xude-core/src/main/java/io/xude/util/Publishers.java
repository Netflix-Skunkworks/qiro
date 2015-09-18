package io.xude.util;

import io.xude.ThrowableBiFunction;
import io.xude.ThrowableFunction;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Publishers {
    public static <T> Publisher<T> never() {
        return s -> {};
    }

    public static <T> Publisher<T> just(T singleValue) {
        return from(singleValue);
    }

    @SafeVarargs
    public static <T> Publisher<T> from(T... values) {
        return new Publisher<T>() {
            private boolean interrupted = false;

            @Override
            public void subscribe(Subscriber<? super T> s) {
                s.onSubscribe(new Subscription() {
                    private int index = 0;

                    @Override
                    public void request(long n) {
                        for (int i = index; i < values.length && i < n + index; i++) {
                            T value = values[i];
                            System.out.println("Publishers.from -> " + value);
                            s.onNext(value);
                            if (interrupted) {
                                return;
                            }
                        }
                        s.onComplete();
                    }

                    @Override
                    public void cancel() {
                        interrupted = true;
                    }
                });
            }
        };
    }

    public static <Input, Output> Publisher<Output> map(
        Publisher<Input> inputs,
        ThrowableFunction<Input, Output> f
    ) {
        return new Publisher<Output>() {
            @Override
            public void subscribe(Subscriber<? super Output> subscriber) {
                inputs.subscribe(new Subscriber<Input>() {
                    @Override
                    public void onSubscribe(Subscription subscription) {
                        subscriber.onSubscribe(subscription);
                    }

                    @Override
                    public void onNext(Input input) {
                        try {
                            Output output = f.apply(input);
                            subscriber.onNext(output);
                        } catch (Throwable ex) {
                            subscriber.onError(ex);
                        }
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

    public static <T> T toSingle(Publisher<T> publisher) throws InterruptedException {
        List<T> data = toList(publisher);
        if (data.size() != 1) {
            throw new RuntimeException("Not a single value");
        }
        return data.get(0);
    }

    public static <T> List<T> toList(Publisher<T> publisher) throws InterruptedException {
        List<T> result = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        publisher.subscribe(new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T t) {
                result.add(t);
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("toList received error " + t);
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
