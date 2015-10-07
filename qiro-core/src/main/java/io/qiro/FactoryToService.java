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
            public void subscribe(Subscriber<? super Resp> responseSubscriber) {
                Publisher<Service<Req, Resp>> servicePublisher = factory.apply();
                servicePublisher.subscribe(new Subscriber<Service<Req, Resp>>() {

                    @Override
                    public void onSubscribe(Subscription subscription) {
                        // request only one service
                        subscription.request(1L);
                    }

                    @Override
                    public void onNext(Service<Req, Resp> service) {
                        Publisher<Resp> responses = service.apply(inputs);
                        responses.subscribe(new Subscriber<Resp>() {
                            @Override
                            public void onSubscribe(Subscription s) {
                                responseSubscriber.onSubscribe(s);
                            }

                            @Override
                            public void onNext(Resp response) {
                                responseSubscriber.onNext(response);
                            }

                            @Override
                            public void onError(Throwable responseFailure) {
                                service.close().subscribe(new EmptySubscriber<>());
                                responseSubscriber.onError(responseFailure);
                            }

                            @Override
                            public void onComplete() {
                                service.close().subscribe(new EmptySubscriber<>());
                                responseSubscriber.onComplete();
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable serviceCreationFailure) {
                        responseSubscriber.onError(serviceCreationFailure);
                    }

                    @Override
                    public void onComplete() {}
                });
            }
        };
    }

    @Override
    public double availability() {
        return factory.availability();
    }

    @Override
    public Publisher<Void> close() {
        return factory.close();
    }
}
