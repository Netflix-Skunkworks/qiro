package io.qiro;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.function.Supplier;

import static io.qiro.util.Publishers.just;
import static io.qiro.util.Publishers.never;

public class Services {
    public static Supplier<Double> ALWAYS_AVAILABLE = () -> 1.0;
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
        Supplier<Double> availability
    ) {
        return new Service<Req, Resp>() {
            @Override
            public Publisher<Resp> requestResponse(Req req) {
                return requestStream(req);
            }

            @Override
            public Publisher<Resp> requestStream(Req req) {
                return requestSubscription(req);
            }

            @Override
            public Publisher<Resp> requestSubscription(Req req) {
                return requestChannel(just(req));
            }

            @Override
            public Publisher<Resp> requestChannel(Publisher<Req> inputs) {
                return subscriber -> inputs.subscribe(new Subscriber<Req>() {
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

            @Override
            public double availability() {
                return availability.get();
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
