package io.xude;

import org.reactivestreams.Publisher;

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
    public <FilterRequest2, FilterResponse2>
    Filter<FilterRequest, FilterRequest2, FilterResponse2, FilterResponse>
    andThen(
        Filter<Request, FilterRequest2, FilterResponse2, Response> other
    );

    public
    Service<FilterRequest, FilterResponse>
    andThen(
        Service<Request, Response> other
    );

    public
    ServiceFactory<FilterRequest, FilterResponse>
    andThen(
        ServiceFactory<Request, Response> other
    );
}
