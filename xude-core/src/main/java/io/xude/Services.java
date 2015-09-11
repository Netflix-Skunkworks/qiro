package io.xude;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.function.Function;
import java.util.function.Supplier;

public class Services {
    public static Publisher<Double> ALWAYS_AVAILABLE = s -> s.onNext(1.0);
    public static Supplier<Void> EMPTY_CLOSE_FN = () -> null;

    public static <Req, Resp> Service<Req, Resp> fromFunction(Function<Req, Resp> fn) {
        return fromFunction(fn, EMPTY_CLOSE_FN);
    }

    public static <Req, Resp> Service<Req, Resp> fromFunction(
        Function<Req, Resp> fn,
        Supplier<Void> closeFn
    ) {
        return fromFunction(fn, closeFn, ALWAYS_AVAILABLE);
    }

    public static <Req, Resp> Service<Req, Resp> fromFunction(
        Function<Req, Resp> fn,
        Supplier<Void> closeFn,
        Publisher<Double> availability
    ) {
        return new Service<Req, Resp>() {
            @Override
            public Publisher<Resp> apply(Publisher<Req> inputs) {
                return new Publisher<Resp>() {
                    @Override
                    public void subscribe(Subscriber<? super Resp> subscriber) {
                        inputs.subscribe(new Subscriber<Req>() {
                            @Override
                            public void onSubscribe(Subscription s) {
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(Req input) {
                                final Resp resp = fn.apply(input);
                                subscriber.onNext(resp);
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

            @Override
            public Publisher<Double> availability() {
                return availability;
            }

            @Override
            public Publisher<Void> close() {
                return s -> s.onNext(closeFn.get());
            }
        };
    }
}
