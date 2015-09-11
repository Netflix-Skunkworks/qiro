package io.xude;

import org.reactivestreams.Publisher;

import java.util.function.Function;
import static io.xude.util.Publishers.map;

public class Filters {

    public static <FilterRequest, Request, Response>
    Filter<FilterRequest, Request, Response, Response> fromInputFunction(
        Function<FilterRequest, Request> fnIn
    ) {
        return fromFunction(fnIn, x -> x);
    }

    public static <Request, Response, FilterResponse>
    Filter<Request, Request, Response, FilterResponse> fromOutputFunction(
        Function<Response, FilterResponse> fnOut
    ) {
        return fromFunction(x -> x, fnOut);
    }

    public static <FilterReq, Req, Resp, FilterResp>
    Filter<FilterReq, Req, Resp, FilterResp> fromFunction(
        Function<FilterReq, Req> fnIn,
        Function<Resp, FilterResp> fnOut
    ) {
        return new Filter<FilterReq, Req, Resp, FilterResp>() {
            @Override
            public Publisher<FilterResp> apply(
                Publisher<FilterReq> inputs,
                Service<Req, Resp> service
            ) {
                final Publisher<Req> requests = map(inputs, fnIn);
                final Publisher<Resp> respPublisher = service.apply(requests);
                return map(respPublisher, fnOut);
            }
        };
    }
}
