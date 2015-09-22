package io.qiro.pool;

import io.qiro.Service;
import io.qiro.ServiceFactories;
import io.qiro.ServiceFactory;
import io.qiro.testing.LoggerSubscriber;
import io.qiro.testing.TestingService;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.qiro.util.Publishers.just;
import static junit.framework.TestCase.assertTrue;

public class SingletonPoolTest {
    @Test(timeout = 100_000L)
    public void testSingletonPool() throws InterruptedException {
        TestingService<Integer, String> testingService = new TestingService<>(Object::toString);
        AtomicInteger serviceCreated = new AtomicInteger(0);
        ServiceFactory<Integer, String> factory = ServiceFactories.fromFunctions(
            () -> {
                if (serviceCreated.getAndIncrement() == 1) {
                    assertTrue("Shouldn't create more than one service!", false);
                }
                return testingService;
            },
            () -> null
        );
        Service<Integer, String> service = new SingletonPool<>(factory).toService();

        service.apply(just(0)).subscribe(new LoggerSubscriber<>("request 0"));
        service.apply(just(1)).subscribe(new LoggerSubscriber<>("request 1"));
        testingService.respond();
        testingService.complete();
    }

}
