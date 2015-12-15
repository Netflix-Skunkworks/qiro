package io.qiro.failures;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.util.Backoff;
import io.qiro.util.EmptySubscriber;
import io.qiro.util.Publishers;
import io.qiro.util.Timer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class FailFastFactory<Req, Resp> implements ServiceFactory<Req, Resp> {
    private interface State {}
    private static class Ok implements State {}
    private static class Retrying implements State {
        private Backoff backoff = null;

        long nextBackoff() {
            if (backoff == null) {
                backoff = new Backoff(0.5, 2, 1<<15);
                return 0;
            } else {
                return backoff.nextBackoff(); // 1, 2, 4, 8, 16 ... 32768, 32768, 32768 ...
            }
        }
    }
    private static Ok OK = new Ok();

    private State state;
    private final Timer timer;
    private ServiceFactory<Req, Resp> underlying;

    public FailFastFactory(ServiceFactory<Req, Resp> underlying, Timer timer) {
        this.state = OK;
        this.timer = timer;
        this.underlying = underlying;
    }

    @Override
    public Publisher<Service<Req, Resp>> apply() {
        return subscriber -> Publishers.transform(
            underlying.apply(),
            svc -> svc,
            exc -> {
                // failure to create the Service toggle the state
                synchronized (FailFastFactory.this) {
                    if (state == OK) {
                        state = new Retrying();
                        update();
                    }
                }
                return exc;
            }
        ).subscribe(subscriber);
    }

    @Override
    public double availability() {
        return state == OK ? underlying.availability() : 0.0;
    }

    @Override
    public Publisher<Void> close() {
        // TODO: discard the task?
        return underlying.close();
    }

    private synchronized void update() {
        if (state != OK) {
            long nextBackoffMs = ((Retrying) state).nextBackoff();
            timer.schedule(() -> {
                underlying.apply().subscribe(new Subscriber<Service<Req, Resp>>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(1L);
                    }

                    @Override
                    public void onNext(Service<Req, Resp> service) {
                        synchronized (FailFastFactory.this) {
                            state = OK;
                        }
                        service.close().subscribe(EmptySubscriber.INSTANCE);
                    }

                    @Override
                    public void onError(Throwable t) {
                        update();
                    }

                    @Override
                    public void onComplete() {}
                });
            }, nextBackoffMs
            );
        }
    }
}
