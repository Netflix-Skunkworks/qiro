package io.qiro.pool;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.util.Availabilities;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;

public class SingletonPool<Req, Resp> implements ServiceFactory<Req,Resp> {
    private final ServiceFactory<Req, Resp> underlying;
    private volatile Service<Req, Resp> singleton;
    private AtomicBoolean isCreated;

    public SingletonPool(ServiceFactory<Req, Resp> underlying) {
        this.underlying = underlying;
        this.isCreated = new AtomicBoolean(false);
    }

    @Override
    public Publisher<Service<Req, Resp>> apply() {
        return new Publisher<Service<Req, Resp>>() {
            @Override
            public void subscribe(Subscriber<? super Service<Req, Resp>> subscriber) {
                if (isCreated.compareAndSet(false, true)) {
                    System.out.println("SingletonPool: creating the singleton");
                    underlying.apply().subscribe(new Subscriber<Service<Req, Resp>>() {
                        @Override
                        public void onSubscribe(Subscription s) {
                            subscriber.onSubscribe(s);
                        }

                        @Override
                        public void onNext(Service<Req, Resp> service) {
                            if (singleton != null) {
                                throw new RuntimeException("Singleton has already been created");
                            }
                            System.out.println("SingletonPool: singleton created");
                            singleton = service;
                            subscriber.onNext(service);
                        }

                        @Override
                        public void onError(Throwable t) {
                            subscriber.onError(t);
                        }

                        @Override
                        public void onComplete() {
                            subscriber.onComplete();
                        }
                    });
                } else {
                    System.out.println("SingletonPool: reusing singleton " + singleton);
                    // TODO: handle the race properly
                    while (singleton == null) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    subscriber.onNext(singleton);
                    subscriber.onComplete();
                }
            }
        };
    }

    @Override
    public Publisher<Double> availability() {
        if (singleton == null) {
            return Availabilities.UNAVAILABLE;
        } else {
            return singleton.availability();
        }
    }

    @Override
    public Publisher<Void> close() {
        return s -> {
            if (isCreated.compareAndSet(true, false)) {
                singleton = null;
            }
            s.onNext(null);
            s.onComplete();
        };
    }
}
