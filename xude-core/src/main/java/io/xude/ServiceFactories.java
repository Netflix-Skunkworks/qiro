package io.xude;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.function.Supplier;

public class ServiceFactories {
    public static <Request, Response> ServiceFactory<Request, Response>
    fromFunctions(
        Supplier<Service<Request, Response>> createFn,
        Supplier<Void> closeFn
    ) {
        return new ServiceFactory<Request, Response>() {
            private int count = 0;

            @Override
            public Publisher<Service<Request, Response>> apply() {
                return new Publisher<Service<Request, Response>>() {
                    @Override
                    public void subscribe(Subscriber<? super Service<Request, Response>> s) {
                        Service<Request, Response> service = createFn.get();
                        s.onNext(service);
                        s.onComplete();
                    }
                };
            }

            @Override
            public Publisher<Void> close() {
                return s -> closeFn.get();
            }
        };
    }
}
