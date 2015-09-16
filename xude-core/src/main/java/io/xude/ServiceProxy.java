package io.xude;

import org.reactivestreams.Publisher;

public class ServiceProxy<Request, Response> implements Service<Request, Response> {
    protected Service<Request, Response> underlying;

    public ServiceProxy(Service<Request, Response> underlying) {
        this.underlying = underlying;
    }

    @Override
    public Publisher<Response> apply(Publisher<Request> inputs) {
        return underlying.apply(inputs);
    }

    @Override
    public Publisher<Double> availability() {
        return underlying.availability();
    }

    @Override
    public Publisher<Void> close() {
        return underlying.close();
    }
}
