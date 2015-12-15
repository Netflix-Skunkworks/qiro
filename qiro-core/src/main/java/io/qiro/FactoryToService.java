package io.qiro;

import io.qiro.util.EmptySubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.function.Consumer;
import java.util.function.Function;

public class FactoryToService<Req, Resp> implements Service<Req, Resp> {
    private ServiceFactory<Req, Resp> factory;

    public FactoryToService(ServiceFactory<Req, Resp> factory) {
        this.factory = factory;
    }

    @Override
    public Publisher<Void> fireAndForget(Req req) {
        return responseSubscriber -> {
            Publisher<Service<Req, Resp>> servicePublisher = factory.apply();
            servicePublisher.subscribe(new Subscriber<Service<Req, Resp>>() {
                private int count = 0;

                @Override
                public void onSubscribe(Subscription subscription) {
                    // request only one service
                    subscription.request(1L);
                }

                @Override
                public void onNext(Service<Req, Resp> service) {
                    if (count > 1) {
                        throw new IllegalStateException("Factory produced more than 1 Service!");
                    }
                    count += 1;

                    Publisher<Void> responses = service.fireAndForget(req);
                    responses.subscribe(new Subscriber<Void>() {
                        @Override
                        public void onSubscribe(Subscription s) {
                            responseSubscriber.onSubscribe(s);
                        }

                        @Override
                        public void onNext(Void nothing) {
                            responseSubscriber.onComplete();
                        }

                        @Override
                        public void onError(Throwable responseFailure) {
                            service.close().subscribe(EmptySubscriber.INSTANCE);
                            responseSubscriber.onError(responseFailure);
                        }

                        @Override
                        public void onComplete() {
                            service.close().subscribe(EmptySubscriber.INSTANCE);
                            responseSubscriber.onComplete();
                        }
                    });
                }

                @Override
                public void onError(Throwable serviceCreationFailure) {
                    responseSubscriber.onError(serviceCreationFailure);
                }

                @Override
                public void onComplete() {
                    if (count != 1) {
                        throw new IllegalStateException("Factory completed with number of " +
                            "produced services equal to " + count);
                    }
                }
            });
        };
    }

    @Override
    public Publisher<Resp> requestResponse(Req req) {
        return applyFn(service -> service.requestResponse(req));
    }

    @Override
    public Publisher<Resp> requestStream(Req req) {
        return applyFn(service -> service.requestStream(req));
    }

    @Override
    public Publisher<Resp> requestSubscription(Req req) {
        return applyFn(service -> service.requestSubscription(req));
    }

    @Override
    public Publisher<Resp> requestChannel(Publisher<Req> inputs) {
        return responseSubscriber -> {
            Publisher<Service<Req, Resp>> servicePublisher = factory.apply();
            servicePublisher.subscribe(new Subscriber<Service<Req, Resp>>() {
                private int count = 0;

                @Override
                public void onSubscribe(Subscription subscription) {
                    // request only one service
                    subscription.request(1L);
                }

                @Override
                public void onNext(Service<Req, Resp> service) {
                    if (count > 1) {
                        throw new IllegalStateException("Factory produced more than 1 Service!");
                    }
                    count += 1;

                    Publisher<Resp> responses = service.requestChannel(inputs);
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
                            service.close().subscribe(EmptySubscriber.INSTANCE);
                            responseSubscriber.onError(responseFailure);
                        }

                        @Override
                        public void onComplete() {
                            service.close().subscribe(EmptySubscriber.INSTANCE);
                            responseSubscriber.onComplete();
                        }
                    });
                }

                @Override
                public void onError(Throwable serviceCreationFailure) {
                    responseSubscriber.onError(serviceCreationFailure);
                }

                @Override
                public void onComplete() {
                    if (count != 1) {
                        throw new IllegalStateException("Factory completed with number of " +
                            "produced services equal to " + count);
                    }
                }
            });
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

    public Publisher<Resp> applyFn(Function<Service<Req, Resp>, Publisher<Resp>> fn) {
        return responseSubscriber -> {
            Publisher<Service<Req, Resp>> servicePublisher = factory.apply();
            servicePublisher.subscribe(new Subscriber<Service<Req, Resp>>() {
                private int count = 0;

                @Override
                public void onSubscribe(Subscription subscription) {
                    // request only one service
                    subscription.request(1L);
                }

                @Override
                public void onNext(Service<Req, Resp> service) {
                    if (count > 1) {
                        throw new IllegalStateException("Factory produced more than 1 Service!");
                    }
                    count += 1;

                    Publisher<Resp> responses = fn.apply(service);
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
                            service.close().subscribe(EmptySubscriber.INSTANCE);
                            responseSubscriber.onError(responseFailure);
                        }

                        @Override
                        public void onComplete() {
                            service.close().subscribe(EmptySubscriber.INSTANCE);
                            responseSubscriber.onComplete();
                        }
                    });
                }

                @Override
                public void onError(Throwable serviceCreationFailure) {
                    responseSubscriber.onError(serviceCreationFailure);
                }

                @Override
                public void onComplete() {
                    if (count != 1) {
                        throw new IllegalStateException("Factory completed with number of " +
                            "produced services equal to " + count);
                    }
                }
            });
        };
    }
}
