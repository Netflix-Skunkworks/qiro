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

public class RetryFilter<Request, Response> implements Filter<Request, Request, Response, Response> {
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
    public Publisher<Response> apply(
        Publisher<Request> inputs, Service<Request, Response> service
    ) {
        return apply(inputs, service, limit);
    }

    public Publisher<Response> apply(
        Publisher<Request> inputs, Service<Request, Response> service,
        int retryBudget
    ) {
        return new Publisher<Response>() {
            private boolean started = false;
            private boolean canceled = false;

            @Override
            public void subscribe(Subscriber<? super Response> subscriber) {
                RestartablePublisher<Request> restartablePublisher = new RestartablePublisher<>(inputs);
                service.apply(restartablePublisher).subscribe(new Subscriber<Response>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        subscriber.onSubscribe(s);
                    }

                    @Override
                    public void onNext(Response response) {
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
                            Publisher<Response> newResponses =
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
