package io.qiro.testing;

import io.qiro.Filter;
import io.qiro.Service;
import io.qiro.util.Timer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DelayFilter<Req, Resp> implements Filter<Req, Req, Resp, Resp> {
    private long delayMs;
    private Timer timer;

    public DelayFilter(long delayMs, Timer timer) {
        this.delayMs = delayMs;
        this.timer = timer;
    }

    private void doLater(Runnable runnable) {
        timer.schedule(runnable, delayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public Publisher<Resp> requestChannel(Publisher<Req> inputs, Service<Req, Resp> service) {
        Publisher<Req> delayedInputs = subscriber ->
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

        return service.requestChannel(delayedInputs);
    }
}
