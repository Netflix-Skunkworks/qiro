package io.qiro;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static io.qiro.util.Publishers.just;
import static io.qiro.util.Publishers.never;

public interface Filter<FilterReq, Req, Resp, FilterResp> {
    default Publisher<Void> fireAndForget(
        FilterReq input,
        Service<Req, Resp> service
    ) {
        return s -> {
            requestResponse(input, service);
            s.onComplete();
        };
    }

    default Publisher<FilterResp> requestResponse(
        FilterReq input,
        Service<Req, Resp> service
    ) {
        return requestStream(input, service);
    }

    default Publisher<FilterResp> requestStream(
        FilterReq input,
        Service<Req, Resp> service
    ) {
        return requestSubscription(input, service);
    }

    default Publisher<FilterResp> requestSubscription(
        FilterReq input,
        Service<Req, Resp> service
    ) {
        return requestChannel(just(input), service);
    }

    public Publisher<FilterResp> requestChannel(
        Publisher<FilterReq> inputs,
        Service<Req, Resp> service
    );

    //   FilterReq  --> Req  -->   FilterReq2
    //                  this         other
    //   FilterResp <-- Resp <--   FilterResp2
    //
    default <FilterReq2, FilterResp2> Filter<FilterReq, FilterReq2, FilterResp2, FilterResp> andThen(
        Filter<Req, FilterReq2, FilterResp2, Resp> other
    ) {
        return new Filter<FilterReq, FilterReq2, FilterResp2, FilterResp>() {
            @Override
            public Publisher<Void> fireAndForget(
                FilterReq input,
                Service<FilterReq2, FilterResp2> service
            ) {
                return filteredService(other, service).fireAndForget(input);
            }

            @Override
            public Publisher<FilterResp> requestResponse(
                FilterReq input,
                Service<FilterReq2, FilterResp2> service
            ) {
                return filteredService(other, service).requestResponse(input);
            }

            @Override
            public Publisher<FilterResp> requestStream(
                FilterReq input,
                Service<FilterReq2, FilterResp2> service
            ) {
                return filteredService(other, service).requestStream(input);
            }

            @Override
            public Publisher<FilterResp> requestSubscription(
                FilterReq input,
                Service<FilterReq2, FilterResp2> service
            ) {
                return filteredService(other, service).requestSubscription(input);
            }

            @Override
            public Publisher<FilterResp> requestChannel(
                Publisher<FilterReq> inputs,
                Service<FilterReq2, FilterResp2> service
            ) {
                return filteredService(other, service).requestChannel(inputs);
            }

            private Service<FilterReq, FilterResp> filteredService(
                Filter<Req, FilterReq2, FilterResp2, Resp> other,
                Service<FilterReq2, FilterResp2> service
            ) {
                return Filter.this.andThen(other.andThen(service));
            }
        };
    }

    default Service<FilterReq, FilterResp> andThen(Service<Req, Resp> service) {
        return new Service<FilterReq, FilterResp>() {
            @Override
            public Publisher<Void> fireAndForget(FilterReq filterReq) {
                return Filter.this.fireAndForget(filterReq, service);
            }

            @Override
            public Publisher<FilterResp> requestResponse(FilterReq filterReq) {
                return Filter.this.requestResponse(filterReq, service);
            }

            @Override
            public Publisher<FilterResp> requestStream(FilterReq filterReq) {
                return Filter.this.requestStream(filterReq, service);
            }

            @Override
            public Publisher<FilterResp> requestSubscription(FilterReq filterReq) {
                return Filter.this.requestSubscription(filterReq, service);
            }

            @Override
            public Publisher<FilterResp> requestChannel(Publisher<FilterReq> inputs) {
                return Filter.this.requestChannel(inputs, service);
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

    default ServiceFactory<FilterReq, FilterResp> andThen(ServiceFactory<Req, Resp> factory) {
        return new ServiceFactory<FilterReq, FilterResp>() {
            @Override
            public Publisher<Service<FilterReq, FilterResp>> apply() {
                return svcSubscriber ->
                    factory.apply().subscribe(new Subscriber<Service<Req, Resp>>() {
                        @Override
                        public void onSubscribe(Subscription subscription) {
                            svcSubscriber.onSubscribe(subscription);
                        }

                        @Override
                        public void onNext(Service<Req, Resp> service) {
                            final Service<FilterReq, FilterResp> filterService =
                                Filter.this.andThen(service);
                            svcSubscriber.onNext(filterService);
                        }

                        @Override
                        public void onError(Throwable t) {
                            svcSubscriber.onError(t);
                        }

                        @Override
                        public void onComplete() {
                            svcSubscriber.onComplete();
                        }
                    });
            }

            @Override
            public double availability() {
                return factory.availability();
            }

            @Override
            public Publisher<Void> close() {
                return factory.close();
            }
        };
    }
}
