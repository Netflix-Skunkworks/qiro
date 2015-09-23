package io.qiro;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public interface Filter<FilterRequest, Request, Response, FilterResponse> {
    public
    Publisher<FilterResponse>
    apply(
        Publisher<FilterRequest> inputs,
        Service<Request, Response> service
    );

    //   FilterRequest  --> Request  -->   FilterRequest2
    //                  this         other
    //   FilterResponse <-- Response <--   FilterResponse2
    //
    default <FilterRequest2, FilterResponse2>
    Filter<FilterRequest, FilterRequest2, FilterResponse2, FilterResponse>
    andThen(
        Filter<Request, FilterRequest2, FilterResponse2, Response> other
    ) {
        return new Filter<FilterRequest, FilterRequest2, FilterResponse2, FilterResponse>() {
            @Override
            public Publisher<FilterResponse>
            apply(
                Publisher<FilterRequest> inputs,
                Service<FilterRequest2, FilterResponse2> service
            ) {
                Service<Request, Response> innerService = other.andThen(service);
                Service<FilterRequest, FilterResponse> outerService =
                    Filter.this.andThen(innerService);

                return outerService.apply(inputs);
            }
        };
    }

    default
    Service<FilterRequest, FilterResponse>
    andThen(
        Service<Request, Response> service
    ) {
        return new Service<FilterRequest, FilterResponse>() {
            @Override
            public Publisher<FilterResponse> apply(Publisher<FilterRequest> requests) {
                return Filter.this.apply(requests, service);
            }

            @Override
            public double availability() {
                return service.availability();
            }

            @Override
            public Publisher<Void> close() {
                return service.close();
            }
        };
    }

    default
    ServiceFactory<FilterRequest, FilterResponse>
    andThen(
        ServiceFactory<Request, Response> other
    ) {
        return new ServiceFactory<FilterRequest, FilterResponse>() {
            @Override
            public Publisher<Service<FilterRequest, FilterResponse>> apply() {
                return new Publisher<Service<FilterRequest, FilterResponse>>() {
                    @Override
                    public void subscribe(Subscriber<? super Service<FilterRequest, FilterResponse>> s) {
                        other.apply().subscribe(new Subscriber<Service<Request, Response>>() {
                            @Override
                            public void onSubscribe(Subscription subscription) {
                                s.onSubscribe(subscription);
                            }

                            @Override
                            public void onNext(Service<Request, Response> service) {
                                final Service<FilterRequest, FilterResponse> filterService =
                                    Filter.this.andThen(service);
                                s.onNext(filterService);
                            }

                            @Override
                            public void onError(Throwable t) {
                                s.onError(t);
                            }

                            @Override
                            public void onComplete() {
                                s.onComplete();
                            }
                        });
                    }
                };
            }

            @Override
            public double availability() {
                return other.availability();
            }

            @Override
            public Publisher<Void> close() {
                return other.close();
            }
        };
    }
}
