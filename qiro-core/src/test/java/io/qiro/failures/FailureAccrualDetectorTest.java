package io.qiro.failures;

import io.qiro.*;
import io.qiro.failures.FailureAccrualDetector;
import io.qiro.testing.FakeClock;
import io.qiro.util.EmptySubscriber;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.qiro.util.Publishers.just;
import static io.qiro.util.Publishers.toSingle;
import static org.junit.Assert.assertTrue;

public class FailureAccrualDetectorTest {
    @Test(timeout = 100000L)
    public void testFailureDetector() throws InterruptedException {

        AtomicInteger i = new AtomicInteger(0);
        ServiceFactory<Integer, String> factory =
            ServiceFactories.fromFunctions(
            () -> Services.<Integer, String>fromFunction(x -> {
                if (i.getAndIncrement() < 2) {
                    throw new io.qiro.failures.Exception();
                } else {
                    return "OK: " + x;
                }
            }),
            () -> null
        );

        FakeClock fakeClock = new FakeClock(0L);
        ServiceFactory<Integer, String> myFactory =
            new FailureAccrualDetector<>(factory, 5, 100, TimeUnit.MILLISECONDS, fakeClock);
        Service<Integer, String> service = toSingle(myFactory.apply());

        // 1 success for the service factory application
        // 1 failure for the service application
        // failures: 1, successes: 1  ===>  ratio = 1 / 2 = 0.5
        service.requestResponse(1).subscribe(EmptySubscriber.INSTANCE);
        Double availability = myFactory.availability();
        assertTrue(availability - 0.5 < 0.01);

        // failures: 2, successes: 1  ===>  ratio = 1 / 3 = 0.333
        service.requestResponse(2).subscribe(EmptySubscriber.INSTANCE);
        availability = myFactory.availability();
        assertTrue(availability - 0.333 < 0.01);

        // failures: 2, successes: 2  ===>  ratio = 2 / 4 = 0.5
        service.requestResponse(3).subscribe(EmptySubscriber.INSTANCE);
        availability = myFactory.availability();
        assertTrue(availability - 0.5 < 0.01);

        // old values are expired
        fakeClock.advance(1000);
        availability = myFactory.availability();
        assertTrue(availability == 1.0);
    }
}
