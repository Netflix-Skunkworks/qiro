package io.xude.filter;

import io.xude.Filter;
import io.xude.Service;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TimeoutFilter<Request, Response>
    implements Filter<Request, Request, Response, Response>
{
    private final static ScheduledExecutorService EXECUTOR =
        Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "Timer-Thread");
            thread.setDaemon(true);
            return thread;
        });

    private final long maxDurationBetweenResponsesMs;

    public TimeoutFilter(long maxDurationBetweenResponsesMs) {
        this.maxDurationBetweenResponsesMs = maxDurationBetweenResponsesMs;
    }

    @Override
    public Publisher<Response> apply(Publisher<Request> inputs, Service<Request, Response> service) {
        return new Publisher<Response>() {
            private AtomicBoolean interrupted = new AtomicBoolean(false);
            private ScheduledFuture<?> schedule = null;
            private volatile Subscription respSubscription = null;

            @Override
            public void subscribe(Subscriber<? super Response> subscriber) {
                service.apply(inputs).subscribe(new Subscriber<Response>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        respSubscription = s;
                        subscriber.onSubscribe(s);
                        rearmTimer();
                    }

                    @Override
                    public void onNext(Response response) {
                        if (interrupted.get()) {
                            return;
                        }
                        rearmTimer();
                        subscriber.onNext(response);
                    }

                    @Override
                    public void onError(Throwable t) {
                        if (interrupted.get()) {
                            return;
                        }
                        cancelTimer();
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        if (interrupted.get()) {
                            return;
                        }
                        cancelTimer();
                        subscriber.onComplete();
                    }

                    private void cancelTimer() {
                        if (schedule != null) {
                            schedule.cancel(false);
                        }
                    }

                    private void rearmTimer() {
                        cancelTimer();
                        schedule = EXECUTOR.schedule(() -> {
                            if (interrupted.compareAndSet(false, true)) {
                                subscriber.onError(new Exception("timeout"));
                                respSubscription.cancel();
                            }
                        }, maxDurationBetweenResponsesMs, TimeUnit.MILLISECONDS);
                    }
                });
            }
        };
    }
}
