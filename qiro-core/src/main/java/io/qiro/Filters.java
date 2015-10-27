package io.qiro;

import org.reactivestreams.Publisher;

import static io.qiro.util.Publishers.map;

public class Filters {

    public static <FilterRequest, Request, Response>
    Filter<FilterRequest, Request, Response, Response> fromInputFunction(
        ThrowableFunction<FilterRequest, Request> fnIn
    ) {
        return fromFunction(fnIn, x -> x);
    }

    public static <Request, Response, FilterResponse>
    Filter<Request, Request, Response, FilterResponse> fromOutputFunction(
        ThrowableFunction<Response, FilterResponse> fnOut
    ) {
        return fromFunction(x -> x, fnOut);
    }

    public static <FilterReq, Req, Resp, FilterResp>
    Filter<FilterReq, Req, Resp, FilterResp> fromFunction(
        ThrowableFunction<FilterReq, Req> fnIn,
        ThrowableFunction<Resp, FilterResp> fnOut
    ) {
        return (inputs, service) -> {
            Publisher<Req> requests = map(inputs, fnIn);
            Publisher<Resp> outputs = service.apply(requests);
            return map(outputs, fnOut);
        };
    }
}
