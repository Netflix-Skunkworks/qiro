package io.xude;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.function.Supplier;

public class Services {
    public static Publisher<Double> ALWAYS_AVAILABLE = s -> {
        s.onNext(1.0);
        s.onComplete();
    };
    public static Supplier<Void> EMPTY_CLOSE_FN = () -> null;

    public static <Req, Resp> Service<Req, Resp> fromFunction(ThrowableFunction<Req, Resp> fn) {
        return fromFunction(fn, EMPTY_CLOSE_FN);
    }

    public static <Req, Resp> Service<Req, Resp> fromFunction(
        ThrowableFunction<Req, Resp> fn,
        Supplier<Void> closeFn
    ) {
        return fromFunction(fn, closeFn, ALWAYS_AVAILABLE);
    }

    public static <Req, Resp> Service<Req, Resp> fromFunction(
        ThrowableFunction<Req, Resp> fn,
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
                            private Subscription subscription;

                            @Override
                            public void onSubscribe(Subscription s) {
                                subscription = s;
                                subscriber.onSubscribe(s);
                            }

                            @Override
                            public void onNext(Req input) {
                                try {
                                    Resp resp = fn.apply(input);
                                    subscriber.onNext(resp);
                                } catch (Throwable ex) {
                                    onError(ex);
                                }
                            }

                            @Override
                            public void onError(Throwable t) {
                                subscriber.onError(t);
                                subscription.cancel();
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
                return s -> {
                    s.onNext(closeFn.get());
                    s.onComplete();
                };
            }
        };
    }
}
