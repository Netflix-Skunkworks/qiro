package io.xude;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.function.Function;

public class Services {
    public static Publisher<Double> ALWAYS_AVAILABLE = s -> s.onNext(1.0);

    public static <Req, Resp> Service<Req, Resp> fromFunction(Function<Req, Resp> fn) {
        return fromFunction(fn, ALWAYS_AVAILABLE);
    }

    public static <Req, Resp> Service<Req, Resp> fromFunction(
        Function<Req, Resp> fn,
        Publisher<Double> availability
    ) {
        return new Service<Req, Resp>() {
            @Override
            public Publisher<Resp> apply(Publisher<Req> inputs) {
                return new Publisher<Resp>() {
                    @Override
                    public void subscribe(Subscriber<? super Resp> stringSubscriber) {
                        inputs.subscribe(new Subscriber<Req>() {
                            @Override
                            public void onSubscribe(Subscription s) {
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(Req input) {
                                final Resp resp = fn.apply(input);
                                stringSubscriber.onNext(resp
                                );
                            }

                            @Override
                            public void onError(Throwable t) {
                                stringSubscriber.onError(t);
                            }

                            @Override
                            public void onComplete() {
                                stringSubscriber.onComplete();
                            }
                        });
                    }
                };
            }

            @Override
            public Publisher<Double> availability() {
                return availability;
            }
        };
    }
}
