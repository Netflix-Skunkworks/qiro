package io.qiro.failures;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.Services;
import io.qiro.testing.FakeTimer;
import io.qiro.util.EmptySubscriber;
import org.junit.Test;
import org.reactivestreams.Publisher;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class FailFastFactoryTest {

    @Test(timeout = 10_000L)
    public void testFailFastFactory() throws InterruptedException {

        AtomicBoolean factoryUp = new AtomicBoolean(false);

        ServiceFactory<Integer, String> factory = new ServiceFactory<Integer, String>() {
            @Override
            public Publisher<Service<Integer, String>> apply() {
                return subscriber -> {
                    if (factoryUp.get()) {
                        Service<Integer, String> service = Services.fromFunction(Object::toString);
                        subscriber.onNext(service);
                    } else {
                        subscriber.onError(new Exception());
                    }
                };
            }

            @Override
            public double availability() {
                return 1.0;
            }

            @Override
            public Publisher<Void> close() {
                return s -> {
                    s.onNext(null);
                    s.onComplete();
                };
            }
        };
        FakeTimer timer = new FakeTimer();

        Service<Integer, String> service = new FailFastFactory<>(factory, timer).toService();
        Double availability = service.availability();
        assertTrue(availability == 1.0);

        service.requestResponse(1).subscribe(EmptySubscriber.INSTANCE);
        availability = service.availability();
        assertTrue(availability == 0.0);

        service.requestResponse(1).subscribe(EmptySubscriber.INSTANCE);
        availability = service.availability();
        assertTrue(availability == 0.0);

        factoryUp.set(true);
        timer.advance();

        availability = service.availability();
        assertTrue(availability == 1.0);
    }
}
