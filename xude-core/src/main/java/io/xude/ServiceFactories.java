package io.xude;

import org.reactivestreams.Publisher;
import java.util.function.Supplier;

public class ServiceFactories {
    public static <Request, Response> ServiceFactory<Request, Response>
    fromFunctions(
        Supplier<Publisher<Service<Request, Response>>> createFn,
        Supplier<Publisher<Void>> closeFn
    ) {
        return new ServiceFactory<Request, Response>() {
            private int count = 0;

            @Override
            public Publisher<Service<Request, Response>> apply() {
                return createFn.get();
            }

            @Override
            public Publisher<Void> close() {
                return closeFn.get();
            }
        };
    }
}
