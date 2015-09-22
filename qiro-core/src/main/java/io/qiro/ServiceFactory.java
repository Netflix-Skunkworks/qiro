package io.qiro;

import org.reactivestreams.Publisher;

public interface ServiceFactory<Request, Response> {
    // TODO: add a client connection ?
    Publisher<Service<Request, Response>> apply();
    Publisher<Double> availability();
    Publisher<Void> close();

    default Service<Request, Response> toService() {
        return new FactoryToService<>(this);
    }
}
