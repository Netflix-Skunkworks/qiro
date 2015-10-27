package io.qiro.util;

import io.qiro.ThrowableFunction;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.Arrays;
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
                        for (; index < values.length && index < n; index++) {
                            T value = values[index];
//                            System.out.println("Publishers.from -> " + value);
                            s.onNext(value);
                            if (interrupted) {
                                return;
                            }
                        }
                        if (index == values.length) {
                            s.onComplete();
                        }
                    }

                    @Override
                    public void cancel() {
                        interrupted = true;
                    }
                });
            }
        };
    }

    public static Publisher<Integer> range(int from, int to) {
        return new Publisher<Integer>() {
            private boolean interrupted = false;

            @Override
            public void subscribe(Subscriber<? super Integer> s) {
                s.onSubscribe(new Subscription() {
                    private int index = from;
                    private int limit = from;
                    private boolean running = false;

                    @Override
                    public void request(long n) {
                        limit += n;
                        if (running) {
                            return;
                        }
                        running = true;
                        for (; index < limit && index < to; index++) {
                            s.onNext(index);
                            if (interrupted) {
                                return;
                            }
                        }
                        if (index == to) {
                            s.onComplete();
                        }
                        running = false;
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

    public static <Input, Output> Publisher<Output> transform(
        Publisher<Input> inputs,
        ThrowableFunction<Input, Output> elementTransformer,
        ThrowableFunction<Throwable, Throwable> errorTransformer
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
                            Output output = elementTransformer.apply(input);
                            subscriber.onNext(output);
                        } catch (Throwable ex) {
                            onError(ex);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        try {
                            subscriber.onError(errorTransformer.apply(t));
                        } catch (Throwable ex) {
                            subscriber.onError(ex);
                        }
                    }

                    @Override
                    public void onComplete() {
                        subscriber.onComplete();
                    }
                });
            }
        };
    }


    public static <T> Publisher<T> concat(Publisher<T>... inputs) {
        return concat(Arrays.asList(inputs));
    }

    public static <T> Publisher<T> concat(List<Publisher<T>> inputs) {
        return new Publisher<T>() {
            private int i = 0;

            @Override
            public void subscribe(Subscriber<? super T> subscriber) {
                Publisher<T> current = inputs.get(i);
                boolean last = i == inputs.size() - 1;
                current.subscribe(new Subscriber<T>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        subscriber.onSubscribe(s);
                    }

                    @Override
                    public void onNext(T t) {
                        subscriber.onNext(t);
                    }

                    @Override
                    public void onError(Throwable t) {
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        i += 1;
                        if (!last) {
                            subscribe(subscriber);
                        } else {
                            subscriber.onComplete();
                        }
                    }
                });
            }
        };
    }

    public static <T> T toSingle(Publisher<T> publisher) throws InterruptedException {
        List<T> data = toList(publisher);
        if (data.size() != 1) {
            throw new RuntimeException("Not a single value " + data);
        }
        return data.get(0);
    }

    public static <T> List<T> toList(Publisher<T> publisher) throws InterruptedException {
        List<T> result = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        publisher.subscribe(new Subscriber<T>() {
            private long batchSize = 128L;
            private Subscription subscribtion;
            private int counter = 0;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscribtion = s;
                s.request(batchSize);
            }

            @Override
            public void onNext(T t) {
//                System.out.println("toList onNext " + t);
                counter += 1;
                if (counter > batchSize / 2) {
                    counter = 0;
                    subscribtion.request(batchSize / 2);
                }
                result.add(t);
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("toList received error " + t);
                latch.countDown();
            }

            @Override
            public void onComplete() {
//                System.out.println("toList onComplete");
                latch.countDown();
            }
        });

        if (!latch.await(5, TimeUnit.MINUTES)) {
            System.err.println("toList timeout!");
        }
        return result;
    }
}
