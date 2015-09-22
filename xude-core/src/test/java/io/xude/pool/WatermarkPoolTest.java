package io.xude.pool;

import io.xude.Service;
import io.xude.ServiceFactories;
import io.xude.ServiceFactory;
import io.xude.testing.LoggerSubscriber;
import io.xude.testing.TestingService;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.xude.util.Publishers.just;
import static junit.framework.TestCase.assertTrue;

public class WatermarkPoolTest {
    @Test(timeout = 100_000L)
    public void testWatermarkPool() {
        List<TestingService<Integer, String>> testingServices = Arrays.asList(
            new TestingService<>(Object::toString),
            new TestingService<>(Object::toString)
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
            new WatermarkPool<>(lowWatermark, highWatermark, factory).toService();

        service.apply(just(0)).subscribe(new LoggerSubscriber<>("request 0"));
        service.apply(just(1)).subscribe(new LoggerSubscriber<>("request 1"));
        service.apply(just(2)).subscribe(new LoggerSubscriber<>("request 2"));
        testingServices.get(0).respond();
        testingServices.get(0).complete();
        testingServices.get(1).respond();
        testingServices.get(1).complete();
        service.apply(just(3)).subscribe(new LoggerSubscriber<>("request 3"));
        testingServices.get(1).respond();
        testingServices.get(1).complete();
    }
}
