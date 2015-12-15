package io.qiro;

import org.reactivestreams.Publisher;

public class ServiceProxy<Req, Resp> implements Service<Req, Resp> {
    protected Service<Req, Resp> underlying;

    public ServiceProxy(Service<Req, Resp> underlying) {
        this.underlying = underlying;
    }

    @Override
    public Publisher<Void> fireAndForget(Req request) {
        return underlying.fireAndForget(request);
    }

    @Override
    public Publisher<Resp> requestResponse(Req request) {
        return underlying.requestResponse(request);
    }

    @Override
    public Publisher<Resp> requestStream(Req request) {
        return underlying.requestStream(request);
    }

    @Override
    public Publisher<Resp> requestSubscription(Req request) {
        return underlying.requestSubscription(request);
    }

    @Override
    public Publisher<Resp> requestChannel(Publisher<Req> inputs) {
        return underlying.requestChannel(inputs);
    }

    @Override
    public double availability() {
        return underlying.availability();
    }

    @Override
    public Publisher<Void> close() {
        return underlying.close();
    }
}
