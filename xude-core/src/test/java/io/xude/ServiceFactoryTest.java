package io.xude;

import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import static io.xude.util.Publishers.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServiceFactoryTest {
    @Test
    public void testBasicServiceFactory() throws InterruptedException {
        ServiceFactory<Integer, String> factory = new ServiceFactory<Integer, String>() {
            private boolean open = false;

            @Override
            public Publisher<Service<Integer, String>> apply() {
                System.out.println("### creating Service");
                assertFalse(open);
                open = true;

                final Service<Integer, String> toString = Services.fromFunction(Object::toString);
                return just(toString);
            }

            @Override
            public Publisher<Void> close() {
                System.out.println("### closing");
                assertTrue(open);
                open = false;

                return Subscriber::onComplete;
            }
        };

        Service<Integer, String> service = new FactoryToService<>(factory);
        ServiceTest.testService(service);
    }
}
