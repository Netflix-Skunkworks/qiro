package io.qiro.testing;

import io.qiro.Filter;
import io.qiro.Service;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DelayFilter<Req, Resp> implements Filter<Req, Req, Resp, Resp> {
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(1);
    private long delayMs;

    public DelayFilter(long delayMs) {
        this.delayMs = delayMs;
    }

    private void doLater(Runnable runnable) {
        EXECUTOR.schedule(runnable, delayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public Publisher<Resp> apply(Publisher<Req> inputs, Service<Req, Resp> service) {
        Publisher<Req> delayedInputs = new Publisher<Req>() {
            @Override
            public void subscribe(Subscriber<? super Req> subscriber) {
                inputs.subscribe(new Subscriber<Req>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        subscriber.onSubscribe(s);
                    }

                    @Override
                    public void onNext(Req req) {
                        doLater(() -> subscriber.onNext(req));
                    }

                    @Override
                    public void onError(Throwable t) {
                        doLater(() -> subscriber.onError(t));
                    }

                    @Override
                    public void onComplete() {
                        doLater(() -> subscriber.onComplete());
                    }
                });
            }
        };

        return service.apply(delayedInputs);
    }
}
