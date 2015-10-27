package io.qiro;

import org.reactivestreams.Publisher;

import static io.qiro.util.Publishers.just;
import static io.qiro.util.Publishers.map;

public interface Service<Req, Resp> {

    public Publisher<Resp> apply(Publisher<Req> inputs);

    // 5 Interaction models
    default Publisher<Void> fireAndForget(Req request) {
        return subscriber -> map(requestResponse(request), x -> null);
    }

    default Publisher<Resp> requestResponse(Req request) {
        return requestStream(request);
    }

    default Publisher<Resp> requestStream(Req request) {
        return requestSubscription(request);
    }


    default Publisher<Resp> requestSubscription(Req request) {
        return requestChannel(just(request));
    }

    default Publisher<Resp> requestChannel(Publisher<Req> inputs) {
        return apply(inputs);
    }

    public double availability();
    public Publisher<Void> close();
}
