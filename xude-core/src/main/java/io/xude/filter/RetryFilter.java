package io.xude.filter;

import io.xude.Filter;
import io.xude.Service;
import io.xude.failures.Retryable;
import io.xude.util.RestartablePublisher;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class RetryFilter<Request, Response> implements Filter<Request, Request, Response, Response> {
    private int limit;

    public RetryFilter(int limit) {
        this.limit = limit;
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
                        if (retryBudget > 0 && !started && t instanceof Retryable) {
                            canceled = true;
                            Publisher<Response> newResponses =
                                apply(restartablePublisher.restart(), service, retryBudget - 1);
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
}
