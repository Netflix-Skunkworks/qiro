package io.xude;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public abstract class AbstractFilter<FilterRequest, Request, Response, FilterResponse>
    implements Filter<FilterRequest, Request, Response, FilterResponse>
{
    //   FilterRequest2  --> FilterRequest  -->   Request
    //                  this               other
    //   FilterResponse2 <-- FilterResponse <--   Response
    //
    @Override
    public <FilterRequest2, FilterResponse2>
    Filter<FilterRequest, FilterRequest2, FilterResponse2, FilterResponse>
    andThen(
        Filter<Request, FilterRequest2, FilterResponse2, Response> other
    ) {
        return new AbstractFilter<FilterRequest, FilterRequest2, FilterResponse2, FilterResponse>() {
            @Override
            public Publisher<FilterResponse>
            apply(
                Publisher<FilterRequest> inputs,
                Service<FilterRequest2, FilterResponse2> service
            ) {
                final Service<Request, Response> innerService =
                    other.andThen(service);
                final Service<FilterRequest, FilterResponse> outerService =
                    AbstractFilter.this.andThen(innerService);

                return outerService.apply(inputs);
            }
        };
    }

    public
    Service<FilterRequest, FilterResponse>
    andThen(
        Service<Request, Response> service
    ) {
        return new Service<FilterRequest, FilterResponse>() {
            @Override
            public Publisher<FilterResponse> apply(Publisher<FilterRequest> requests) {
                return AbstractFilter.this.apply(requests, service);
            }

            @Override
            public Publisher<Double> availability() {
                return service.availability();
            }
        };
    }

    public
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
                            public void onSubscribe(Subscription s) {
                                s.request(1L);
                            }

                            @Override
                            public void onNext(Service<Request, Response> service) {
                                final Service<FilterRequest, FilterResponse> filterService =
                                    AbstractFilter.this.andThen(service);
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
            public Publisher<Void> close() {
                return other.close();
            }
        };
    }
}
