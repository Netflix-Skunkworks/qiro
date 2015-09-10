package io.xude;

import org.reactivestreams.Publisher;

public class ServiceFactoryProxy<Request, Response> implements ServiceFactory<Request, Response> {
    private ServiceFactory<Request, Response> underlying;

    public ServiceFactoryProxy(ServiceFactory<Request, Response> underlying) {
        this.underlying = underlying;
    }
    @Override
    public Publisher<Service<Request, Response>> apply() {
        return underlying.apply();
    }

    @Override
    public Publisher<Void> close() {
        return underlying.close();
    }
}
