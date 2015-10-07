package io.qiro.filter;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.ServiceProxy;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static io.qiro.util.Publishers.map;

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
            public void subscribe(Subscriber<? super Service<Request, Response>> svcSubscriber) {
                map(underlying.apply(), service -> new FailureAccrualService<>(service))
                    .subscribe(new Subscriber<Service<Request, Response>>() {
                        @Override
                        public void onSubscribe(Subscription s) {
                            svcSubscriber.onSubscribe(s);
                        }

                        @Override
                        public void onNext(Service<Request, Response> svc) {
                            svcSubscriber.onNext(svc);
                        }

                        @Override
                        public void onError(Throwable t) {
                            svcSubscriber.onError(t);
                            markFailure();  // ServiceFactory failures also trigger markFailure
                        }

                        @Override
                        public void onComplete() {
                            svcSubscriber.onComplete();
                        }
                    });
            }
        };
    }

    private synchronized void markFailure() {
//        System.out.println(toString() + " FailureAccrualDetector.markFailure");
        consecutiveFailures += 1;
        if (consecutiveFailures >= consecutiveErrorThreshold && deadSince < 0L) {
            System.out.println(toString() + " FailureAccrualDetector markDEAD!");
            deadSince = System.currentTimeMillis();
        }
    }

    private synchronized void markSuccess() {
//        System.out.println(toString() + " FailureAccrualDetector.markSuccess");
        consecutiveFailures = 0;
        deadSince = -1L;
    }

    @Override
    public synchronized double availability() {
        if (deadSince > 0L) {
            long elapse = System.currentTimeMillis() - deadSince;
            if (elapse < markDeadForMs) {
//                System.out.println(toString() + " FailureAccrualDetector return 0.0 availability");
                return 0.0;
            } else {
                markSuccess(); // revive
                return underlying.availability();
            }
        } else {
            return underlying.availability();
        }
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
