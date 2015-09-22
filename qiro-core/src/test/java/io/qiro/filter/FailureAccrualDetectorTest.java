package io.qiro.filter;

import io.qiro.*;
import io.qiro.util.EmptySubscriber;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.qiro.util.Publishers.just;
import static io.qiro.util.Publishers.toSingle;
import static org.junit.Assert.assertTrue;

public class FailureAccrualDetectorTest {
    @Test(timeout = 100000L)
    public void testFailureDetector() throws InterruptedException {

        AtomicInteger i = new AtomicInteger(0);
        ServiceFactory<Integer, String> factory = ServiceFactories.fromFunctions(
            () -> Services.<Integer, String>fromFunction(x -> {
                if (i.getAndIncrement() < 2) {
                    throw new io.qiro.failures.Exception();
                } else {
                    return "OK: " + x;
                }
            }),
            () -> null
        );
        Service<Integer, String> service =
            new FailureAccrualDetector<>(factory, 2, 5_000).toService();

        // One failing message doesn't change availability
        service.apply(just(1)).subscribe(new EmptySubscriber<>());
        Double availability = toSingle(service.availability());
        assertTrue(availability == 1.0);

        // Two consecutive failures change the availability
        service.apply(just(2)).subscribe(new EmptySubscriber<>());
        availability = toSingle(service.availability());
        assertTrue(availability == 0.0);

        // One success and you are back to the swing of things
        service.apply(just(3)).subscribe(new EmptySubscriber<>());
        availability = toSingle(service.availability());
        assertTrue(availability == 1.0);
    }
}
