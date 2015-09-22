package io.xude;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class ServiceFactories {

    private static List<Service<Object, Object>> services;

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

    @SafeVarargs
    public static <Request, Response> ServiceFactory<Request, Response>
    roundRobin(Service<Request, Response>... services) {
        return roundRobin(Arrays.asList(services));
    }

    public static <Request, Response> ServiceFactory<Request, Response>
    roundRobin(List<Service<Request, Response>> services) {
        return new ServiceFactory<Request, Response>() {
            private int i = 0;

            @Override
            public Publisher<Service<Request, Response>> apply() {
                return s -> {
                    s.onNext(services.get(i % services.size()));
                    s.onComplete();
                    i += 1;
                };
            }

            @Override
            public Publisher<Double> availability() {
                return null;
            }

            @Override
            public Publisher<Void> close() {
                return null;
            }
        };
    }
}
