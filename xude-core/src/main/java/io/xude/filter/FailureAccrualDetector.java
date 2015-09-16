package io.xude.filter;

import io.xude.Service;
import io.xude.ServiceFactory;
import io.xude.ServiceProxy;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static io.xude.util.Publishers.map;

public class FailureAccrualDetector<Request, Response> implements ServiceFactory<Request, Response> {
    private final ServiceFactory<Request, Response> underlying;
    private final int consecutiveErrorThreshold;
    private final long markDeadForMs;

    private int consecutiveFailures;
    private long deadSince;

    public FailureAccrualDetector(
        ServiceFactory<Request, Response> underlying,
        int consecutiveErrorThreshold,
        long markDeadForMs
    ) {
        this.underlying = underlying;
        this.consecutiveErrorThreshold = consecutiveErrorThreshold;
        this.markDeadForMs = markDeadForMs;
        this.consecutiveFailures = 0;
        this.deadSince = -1L;
    }

    public FailureAccrualDetector(ServiceFactory<Request, Response> underlying) {
        this(underlying, 5, 5000);
    }

    @Override
    public Publisher<Service<Request, Response>> apply() {
        return new Publisher<Service<Request, Response>>() {
            @Override
            public void subscribe(Subscriber<? super Service<Request, Response>> subscriber) {
                map(underlying.apply(), service -> new FailureAccrualService<>(service))
                    .subscribe(new Subscriber<Service<Request, Response>>() {
                        @Override
                        public void onSubscribe(Subscription s) {
                            subscriber.onSubscribe(s);
                        }

                        @Override
                        public void onNext(Service<Request, Response> svc) {
                            subscriber.onNext(svc);
                        }

                        @Override
                        public void onError(Throwable t) {
                            subscriber.onError(t);
                            markFailure();  // ServiceFactory failures also trigger markFailure
                        }

                        @Override
                        public void onComplete() {
                            subscriber.onComplete();
                        }
                    });
            }
        };
    }

    private void markFailure() {
        System.out.println("FailureAccrualDetector.markFailure");
        consecutiveFailures += 1;
        if (consecutiveFailures >= consecutiveErrorThreshold) {
            deadSince = System.currentTimeMillis();
        }
    }

    private void markSuccess() {
        System.out.println("FailureAccrualDetector.markSuccess");
        consecutiveFailures = 0;
        deadSince = -1L;
    }

    @Override
    public Publisher<Double> availability() {
        return new Publisher<Double>() {
            @Override
            public void subscribe(Subscriber<? super Double> availabilitySubscriber) {
                if (deadSince > 0L) {
                    long elapse = System.currentTimeMillis() - deadSince;
                    if (elapse < markDeadForMs) {
                        System.out.println("FailureAccrualDetector return 0.0 availability");
                        availabilitySubscriber.onNext(0.0);
                        availabilitySubscriber.onComplete();
                    } else {
                        markSuccess(); // revive
                        underlying.availability().subscribe(availabilitySubscriber);
                    }
                } else {
                    underlying.availability().subscribe(availabilitySubscriber);
                }
            }
        };
    }

    @Override
    public Publisher<Void> close() {
        return underlying.close();
    }

    /**
     * Measure the number of Success/Error and update counters via markFailure/markSuccess
     */
    private class FailureAccrualService<Request, Response> extends ServiceProxy<Request, Response> {
        public FailureAccrualService(Service<Request, Response> service) {
            super(service);
        }

        @Override
        public Publisher<Response> apply(Publisher<Request> requests) {
            return new Publisher<Response>() {
                @Override
                public void subscribe(Subscriber<? super Response> respSubscriber) {
                    underlying.apply(requests).subscribe(new Subscriber<Response>() {
                        @Override
                        public void onSubscribe(Subscription s) {
                            respSubscriber.onSubscribe(s);
                        }

                        @Override
                        public void onNext(Response response) {
                            respSubscriber.onNext(response);
                        }

                        @Override
                        public void onError(Throwable t) {
                            respSubscriber.onError(t);
                            markFailure();
                        }

                        @Override
                        public void onComplete() {
                            respSubscriber.onComplete();
                            markSuccess();
                        }
                    });
                }
            };
        }
    }
}
