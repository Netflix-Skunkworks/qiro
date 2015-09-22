package io.qiro;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class FactoryToServiceTest {
    @Test(timeout = 10_000L)
    public void testBasicServiceFactory() throws InterruptedException {

        ServiceFactory<Integer, String> factory = ServiceFactories.fromFunctions(
            () -> Services.fromFunction(Object::toString),
            () -> {
                assertTrue("You shouldn't close the ServiceFactory", false);
                return null;
            }
        );

        ServiceTest.testService(factory.toService());
    }
}
