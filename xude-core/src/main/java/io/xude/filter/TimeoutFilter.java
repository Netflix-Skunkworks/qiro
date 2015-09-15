package io.xude.filter;

import io.xude.Filter;
import io.xude.Service;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.*;

public class TimeoutFilter<Request, Response>
    implements Filter<Request, Request, Response, Response>
{
    private final static ScheduledExecutorService EXECUTOR =
        Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "Timer-Thread");
            thread.setDaemon(true);
            return thread;
        });

    private final long maxDurationMs;

    public TimeoutFilter(long maxDurationMs) {
        this.maxDurationMs = maxDurationMs;
    }

    @Override
    public Publisher<Response> apply(Publisher<Request> inputs, Service<Request, Response> service) {
        return new Publisher<Response>() {
            private ScheduledFuture<?> schedule = null;
            private volatile Subscription respSubscription = null;

            @Override
            public void subscribe(Subscriber<? super Response> subscriber) {
                service.apply(inputs).subscribe(new Subscriber<Response>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        respSubscription = s;
                        subscriber.onSubscribe(s);
                        armTimer();
                    }

                    @Override
                    public void onNext(Response response) {
                        subscriber.onNext(response);
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
                        if (schedule != null) {
                            schedule.cancel(false);
                        }
                    }

                    private void armTimer() {
                        schedule = EXECUTOR.schedule(() -> {
                            subscriber.onError(new Exception("timeout"));
                            respSubscription.cancel();
                        }, maxDurationMs, TimeUnit.MILLISECONDS);
                    }
                });
            }
        };
    }
}
