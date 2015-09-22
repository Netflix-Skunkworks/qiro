package io.qiro;

import org.reactivestreams.Publisher;

public interface Service<Request, Response> {

    public Publisher<Response> apply(Publisher<Request> reqStream);

    // 5 Interaction models
    //    public Observable<Void> fireAndForget(final Request request);
    //    public Observable<Response> requestResponse(final Request request);
    //    public Observable<Response> requestStream(final Request request);
    //    public Observable<Response> requestSubscription(final Request request);
    //    public Observable<Response> requestChannel(final Observable<Request> requests);

    public Publisher<Double> availability();
    public Publisher<Void> close();
}
