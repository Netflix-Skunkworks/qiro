package io.qiro.filter;

import io.qiro.Filter;
import io.qiro.Service;
import io.qiro.util.Timer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.*;

public class TimeoutFilter<Req, Resp> implements Filter<Req, Req, Resp, Resp> {
    private final static ScheduledExecutorService EXECUTOR =
        Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "Timer-Thread");
            thread.setDaemon(true);
            return thread;
        });

    private final long maxDurationMs;
    private Timer timer;

    public TimeoutFilter(long maxDurationMs, Timer timer) {
        this.maxDurationMs = maxDurationMs;
        this.timer = timer;
    }

    @Override
    public Publisher<Resp> requestChannel(Publisher<Req> inputs, Service<Req, Resp> service) {
        return new Publisher<Resp>() {
            Timer.TimerTask timerTask = null;
            private volatile Subscription respSubscription = null;

            @Override
            public void subscribe(Subscriber<? super Resp> subscriber) {
                service.requestChannel(inputs).subscribe(new Subscriber<Resp>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        respSubscription = s;
                        subscriber.onSubscribe(s);
                        armTimer();
                    }

                    @Override
                    public void onNext(Resp resp) {
                        subscriber.onNext(resp);
                    }

                    @Override
                    public void onError(Throwable t) {
                        cancelTimer();
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        cancelTimer();
                        subscriber.onComplete();
                    }

                    private void cancelTimer() {
                        if (timerTask != null) {
                            timerTask.cancel();
                        }
                    }

                    private void armTimer() {
                        timerTask = timer.schedule(() -> {
                            respSubscription.cancel();
                            subscriber.onError(new Exception("timeout"));
                        }, maxDurationMs, TimeUnit.MILLISECONDS);
                    }
                });
            }
        };
    }
}
