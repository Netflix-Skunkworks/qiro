package io.qiro.pool;

import io.qiro.Service;
import io.qiro.ServiceFactories;
import io.qiro.ServiceFactory;
import io.qiro.testing.LoggerSubscriber;
import io.qiro.testing.TestingService;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.qiro.util.Publishers.just;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class WatermarkPoolTest {
    @Test(timeout = 10_000L)
    public void testWatermarkPool() {
        TestingService<Integer, String> testingSvc0 =
            new TestingService<>(Object::toString);
        TestingService<Integer, String> testingSvc1 =
            new TestingService<>(Object::toString);
        List<TestingService<Integer, String>> testingServices = Arrays.asList(
            testingSvc0,
            testingSvc1
        );

        int lowWatermark = 1;
        int highWatermark = 2;

        AtomicInteger serviceCreated = new AtomicInteger(0);
        ServiceFactory<Integer, String> factory = ServiceFactories.fromFunctions(
            () -> {
                System.out.println("Creating a TestingService!");
                if (serviceCreated.getAndIncrement() == highWatermark) {
                    assertTrue("Shouldn't create more than "
                        + highWatermark + " services!", false);
                }
                int index = (serviceCreated.get() - 1) % testingServices.size();
                return testingServices.get(index);
            },
            () -> null
        );
        Service<Integer, String> service =
            new WatermarkPool<>(lowWatermark, highWatermark, 0, factory).toService();

        LoggerSubscriber<Object> subscriber0 = new LoggerSubscriber<>("request 0");
        LoggerSubscriber<Object> subscriber1 = new LoggerSubscriber<>("request 1");
        LoggerSubscriber<Object> subscriber2 = new LoggerSubscriber<>("request 2");
        LoggerSubscriber<Object> subscriber3 = new LoggerSubscriber<>("request 3");

        service.requestResponse(0).subscribe(subscriber0);
        service.requestResponse(1).subscribe(subscriber1);
        service.requestResponse(2).subscribe(subscriber2); // -> error maxCapacity
        assertFalse(subscriber0.isComplete());
        assertFalse(subscriber1.isComplete());
        assertTrue(subscriber2.isError());
        assertTrue(testingSvc0.isOpen());
        assertTrue(testingSvc1.isOpen());

        testingSvc0.respond();
        testingSvc0.complete();
        assertTrue(subscriber0.isComplete());
        assertFalse(subscriber1.isComplete());
        assertTrue(testingSvc0.isClosed()); // > low watermark, svc has been closed
        assertTrue(testingSvc1.isOpen());


        testingSvc1.respond();
        testingSvc1.complete();
        assertTrue(subscriber0.isComplete());
        assertTrue(subscriber1.isComplete());
        assertTrue(testingSvc1.isOpen()); // > low watermark, svc stays open

        service.requestResponse(3).subscribe(subscriber3);
        testingSvc0.respond();
        testingSvc0.complete();
    }

    @Test(timeout = 10_000L)
    public void testWatermarkPoolBuffering() {
        TestingService<Integer, String> testingSvc0 =
            new TestingService<>(Object::toString);
        TestingService<Integer, String> testingSvc1 =
            new TestingService<>(Object::toString);
        List<TestingService<Integer, String>> testingServices = Arrays.asList(
            testingSvc0,
            testingSvc1
        );

        int lowWatermark = 1;
        int highWatermark = 2;
        int buffer = 1;

        AtomicInteger serviceCreated = new AtomicInteger(0);
        ServiceFactory<Integer, String> factory = ServiceFactories.fromFunctions(
            () -> {
                System.out.println("Creating a TestingService!");
                if (serviceCreated.getAndIncrement() == highWatermark) {
                    assertTrue("Shouldn't create more than "
                        + highWatermark + " services!", false);
                }
                int index = (serviceCreated.get() - 1) % testingServices.size();
                return testingServices.get(index);
            },
            () -> {
                assertTrue("Shouldn't destroy a service!", false);
                return null;
            }
        );
        Service<Integer, String> service =
            new WatermarkPool<>(lowWatermark, highWatermark, buffer, factory).toService();

        LoggerSubscriber<Object> subscriber0 = new LoggerSubscriber<>("request 0");
        LoggerSubscriber<Object> subscriber1 = new LoggerSubscriber<>("request 1");
        LoggerSubscriber<Object> subscriber2 = new LoggerSubscriber<>("request 2");
        LoggerSubscriber<Object> subscriber3 = new LoggerSubscriber<>("request 3");

        service.requestResponse(0).subscribe(subscriber0);
        service.requestResponse(1).subscribe(subscriber1);
        service.requestResponse(2).subscribe(subscriber2); // -> svc is buffered
        service.requestResponse(3).subscribe(subscriber3); // -> error max # services
        assertFalse(subscriber0.isComplete());
        assertFalse(subscriber1.isComplete());
        assertFalse(subscriber2.isComplete());
        assertTrue(subscriber3.isError());

        testingSvc0.respond(0);  //only reply to the first req to svc_0
        testingSvc0.complete(0); // this will close the service, and put it back to the WP queue
                                 // without the (0) it will also reply to the 2nd req ("request 2")
        assertTrue(subscriber0.isComplete());
        assertFalse(subscriber1.isComplete());
        assertFalse(subscriber2.isComplete());

        testingSvc1.respond();
        testingSvc1.complete();
        assertTrue(subscriber0.isComplete());
        assertTrue(subscriber1.isComplete());
        assertFalse(subscriber2.isComplete());

        testingSvc0.respond();
        testingSvc0.complete();
        assertFalse(subscriber3.isComplete());
    }
}
