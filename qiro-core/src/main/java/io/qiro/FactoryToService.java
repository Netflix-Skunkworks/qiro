package io.qiro;

import io.qiro.util.EmptySubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class FactoryToService<Req, Resp> implements Service<Req, Resp> {
    private ServiceFactory<Req, Resp> factory;

    public FactoryToService(ServiceFactory<Req, Resp> factory) {
        this.factory = factory;
    }

    @Override
    public Publisher<Resp> apply(Publisher<Req> inputs) {
        return new Publisher<Resp>() {
            @Override
            public void subscribe(Subscriber<? super Resp> subscriber) {
                Publisher<Service<Req, Resp>> servicePublisher = factory.apply();
                servicePublisher.subscribe(new Subscriber<Service<Req, Resp>>() {
                    private Service<Req, Resp> service = null;

                    @Override
                    public void onSubscribe(Subscription subscription) {
                        // request only one service
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(Service<Req, Resp> service) {
                        this.service = service;
                        Publisher<Resp> responses = service.apply(inputs);
                        responses.subscribe(new Subscriber<Resp>() {
                            @Override
                            public void onSubscribe(Subscription s) {
                                subscriber.onSubscribe(s);
                            }

                            @Override
                            public void onNext(Resp resp) {
                                subscriber.onNext(resp);
                            }

                            @Override
                            public void onError(Throwable t) {
                                subscriber.onError(t);
                            }

                            @Override
                            public void onComplete() {
                                service.close().subscribe(new EmptySubscriber<Void>());
                                subscriber.onComplete();
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable t) {
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                    }
                });
            }
        };
    }

    @Override
    public Publisher<Double> availability() {
        return factory.availability();
    }

    @Override
    public Publisher<Void> close() {
        return factory.close();
    }
}
