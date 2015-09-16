package io.xude;

import org.reactivestreams.Publisher;
import java.util.function.Supplier;

public class ServiceFactories {

    public static <Request, Response> ServiceFactory<Request, Response>
    fromFunctions(
        Supplier<Service<Request, Response>> createFn,
        Supplier<Void> closeFn
    ) {
        return fromFunctions(createFn, closeFn, () -> 1.0);
    }

    public static <Request, Response> ServiceFactory<Request, Response>
    fromFunctions(
        Supplier<Service<Request, Response>> createFn,
        Supplier<Void> closeFn,
        Supplier<Double> availabilityFn
    ) {
        return new ServiceFactory<Request, Response>() {
            @Override
            public Publisher<Service<Request, Response>> apply() {
                return s -> {
                    Service<Request, Response> service = createFn.get();
                    s.onNext(service);
                    s.onComplete();
                };
            }

            @Override
            public Publisher<Double> availability() {
                return s -> {
                    double availability = availabilityFn.get();
                    s.onNext(availability);
                    s.onComplete();
                };
            }

            @Override
            public Publisher<Void> close() {
                return s -> {
                    s.onNext(closeFn.get());
                    s.onComplete();
                };
            }
        };
    }
}
