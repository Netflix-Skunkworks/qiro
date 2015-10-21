package io.qiro.failures;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.ServiceProxy;
import io.qiro.util.Clock;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.TimeUnit;

public class FailureAccrualDetector<Req, Resp> implements ServiceFactory<Req, Resp> {
    private static class TimestampRPC {
        private long ts;
        private boolean success;

        TimestampRPC(long ts, boolean success) {
            this.ts = ts;
            this.success = success;
        }

        public long ts() {
            return ts;
        }

        public boolean isSuccess() {
            return success;
        }

        public static TimestampRPC zero() {
            return new TimestampRPC(Long.MIN_VALUE, true);
        }

        public static TimestampRPC success(long ts) {
            return new TimestampRPC(ts, true);
        }

        public static TimestampRPC failure(long ts) {
            return new TimestampRPC(ts, false);
        }
    }

    private final ServiceFactory<Req, Resp> underlying;
    private final TimestampRPC[] history;
    private final long expiration;
    private final Clock clock;
    private int historyIndex;

    public FailureAccrualDetector(
        ServiceFactory<Req, Resp> underlying,
        int requestHistory,
        long expiration,
        TimeUnit unit,
        Clock clock
    ) {
        this.underlying = underlying;
        this.history = new TimestampRPC[requestHistory];
        for (int i = 0; i < history.length; i++) {
            history[i] = TimestampRPC.zero();
        }
        this.historyIndex = 0;
        this.expiration = TimeUnit.MILLISECONDS.convert(expiration, unit);
        this.clock = clock;
    }

    public FailureAccrualDetector(
        ServiceFactory<Req, Resp> underlying,
        int history,
        long expiration,
        TimeUnit unit
    ) {
        this(underlying, history, expiration, unit, Clock.SYSTEM_CLOCK);
    }

    public FailureAccrualDetector(ServiceFactory<Req, Resp> underlying) {
        this(underlying, 5, 30, TimeUnit.SECONDS);
    }

    @Override
    public Publisher<Service<Req, Resp>> apply() {
        return svcSubscriber ->
            underlying.apply().subscribe(new Subscriber<Service<Req, Resp>>() {
                @Override
                public void onSubscribe(Subscription s) {
                    svcSubscriber.onSubscribe(s);
                }

                @Override
                public void onNext(Service<Req, Resp> service) {
                    markSuccess();
                    FailureAccrualService<Req, Resp> accrualService =
                        new FailureAccrualService<>(service);
                    svcSubscriber.onNext(accrualService);
                }

                @Override
                public void onError(Throwable serviceCreationFailure) {
                    markFailure();
                    svcSubscriber.onError(serviceCreationFailure);
                }

                @Override
                public void onComplete() {
                    svcSubscriber.onComplete();
                }
            });
    }

    private synchronized void markFailure() {
        history[historyIndex++ % history.length] = TimestampRPC.failure(clock.nowMs());
    }

    private synchronized void markSuccess() {
        history[historyIndex++ % history.length] = TimestampRPC.success(clock.nowMs());
    }

    @Override
    public synchronized double availability() {
        long now = clock.nowMs();
        int successes = 0;
        int failures = 0;
        for (TimestampRPC rpc: history) {
            if (rpc.ts() >= now - expiration) {
                if (rpc.isSuccess())
                    successes += 1;
                else
                    failures += 1;
            }
        }

        if (failures == 0) {
            return underlying.availability();
        } else {
            double ratio = (double) successes / (successes + failures);
            return ratio * underlying.availability();
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
            return respSubscriber ->
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
                    public void onError(Throwable responseFailure) {
                        // TODO: do not count applicative failure
                        markFailure();
                        respSubscriber.onError(responseFailure);
                    }

                    @Override
                    public void onComplete() {
                        markSuccess();
                        respSubscriber.onComplete();
                    }
                });
        }
    }
}
