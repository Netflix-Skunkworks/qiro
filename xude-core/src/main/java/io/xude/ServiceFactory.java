package io.xude;

import org.reactivestreams.Publisher;

public interface ServiceFactory<Request, Response> {
    // TODO: add a client connection ?
    Publisher<Service<Request, Response>> apply();
    Publisher<Void> close();
}
