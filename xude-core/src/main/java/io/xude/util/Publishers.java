package io.xude.util;

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
                        for (; index < values.length && index < n; index++) {
                            T value = values[index];
                            System.out.println("Publishers.from -> " + value);
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

    public static <T> Publisher<List<T>> concat(List<Publisher<T>> inputs) {
        return new Publisher<List<T>>() {
            private List<T> buffer = new ArrayList<>();

            @Override
            public void subscribe(Subscriber<? super List<T>> subscriber) {
                for (int i = 0; i < inputs.size(); i++) {
                    Publisher<T> pub = inputs.get(i);
                    final boolean last = i == inputs.size() - 1;
                    pub.subscribe(new Subscriber<T>() {
                        @Override
                        public void onSubscribe(Subscription s) {
                            subscriber.onSubscribe(s);
                        }

                        @Override
                        public void onNext(T t) {
                            buffer.add(t);
                        }

                        @Override
                        public void onError(Throwable t) {
                            subscriber.onError(t);
                            // TODO: Should break the for loop
                        }

                        @Override
                        public void onComplete() {
                            if (last) {
                                subscriber.onNext(buffer);
                                subscriber.onComplete();
                            }
                        }
                    });
                }
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
                counter += 1;
                if (counter > batchSize / 2) {
                    counter = 0;
                    subscribtion.request(batchSize / 2);
                }
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
