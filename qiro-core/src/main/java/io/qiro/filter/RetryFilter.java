package io.qiro.filter;

import io.qiro.Filter;
import io.qiro.Service;
import io.qiro.failures.Retryable;
import io.qiro.util.RestartablePublisher;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.net.SocketException;
import java.util.function.Function;

public class RetryFilter<Req, Resp> implements Filter<Req, Req, Resp, Resp> {
    private int limit;
    private Function<Throwable, Boolean> retryThisThrowable;

    public RetryFilter(int limit, Function<Throwable, Boolean> retryThisThrowable) {
        this.limit = limit;
        this.retryThisThrowable = retryThisThrowable;
    }

    public RetryFilter(int limit) {
        this(limit, t -> false);
    }

    @Override
    public Publisher<Resp> requestChannel(Publisher<Req> inputs, Service<Req, Resp> service) {
        return apply(inputs, service, limit);
    }

    public Publisher<Resp> apply(Publisher<Req> inputs, Service<Req, Resp> service, int retryBudget) {
        return new Publisher<Resp>() {
            private boolean started = false;
            private boolean canceled = false;

            @Override
            public void subscribe(Subscriber<? super Resp> subscriber) {
                RestartablePublisher<Req> restartablePublisher = new RestartablePublisher<>(inputs);
                service.requestChannel(restartablePublisher).subscribe(new Subscriber<Resp>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        subscriber.onSubscribe(s);
                    }

                    @Override
                    public void onNext(Resp response) {
                        if (canceled) {
                            return;
                        }
                        started = true;
                        subscriber.onNext(response);
                    }

                    @Override
                    public void onError(Throwable t) {
                        if (canceled) {
                            return;
                        }
                        // if the exception is Retryable and the stream of responses didn't started,
                        // it's safe to retry the call.
                        if (retryBudget > 0 && !started && isRetryable(t)) {
                            canceled = true;
                            Publisher<Resp> newResponses =
                                apply(restartablePublisher.restart(), service, retryBudget - 1);
                            System.out.println("***** retrying");
                            newResponses.subscribe(subscriber);
                        } else {
                            subscriber.onError(t);
                        }
                    }

                    @Override
                    public void onComplete() {
                        if (!canceled) {
                            subscriber.onComplete();
                        }
                    }
                });
            }
        };
    }

    private boolean isRetryable(Throwable t) {
        if (t instanceof Retryable) {
            return true;
        } else if (t instanceof SocketException) {
            return true;
        } else if (t instanceof IOException) {
            return true;
        } else {
            return retryThisThrowable.apply(t);
        }
    }
}
