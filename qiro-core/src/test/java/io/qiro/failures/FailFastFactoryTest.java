package io.qiro.failures;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.Services;
import io.qiro.util.EmptySubscriber;
import io.qiro.util.Timer;
import org.junit.Test;
import org.reactivestreams.Publisher;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.qiro.util.Publishers.just;
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

//        ServiceFactory<Integer, String> factory =
//            ServiceFactories.fromFunctions(
//                () -> Services.<Integer, String>fromFunction(x -> {
//                    if (i.getAndIncrement() < 2) {
//                        throw new io.qiro.failures.Exception();
//                    } else {
//                        return "OK: " + x;
//                    }
//                }),
//                () -> null
//            );


        Timer timer = new Timer() {
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

            @Override
            public TimerTask schedule(Runnable task, long delay, TimeUnit unit) {
                ScheduledFuture<?> future = executor.schedule(task, delay, unit);
                return new TimerTask() {
                    @Override
                    public void cancel() {
                        future.cancel(true);
                    }

                    @Override
                    public boolean isCancel() {
                        return future.isCancelled();
                    }
                };
            }
        };

        Service<Integer, String> service = new FailFastFactory<>(factory, timer).toService();
        Double availability = service.availability();
        assertTrue(availability == 1.0);

        service.apply(just(1)).subscribe(EmptySubscriber.INSTANCE);
        availability = service.availability();
        assertTrue(availability == 0.0);

        service.apply(just(1)).subscribe(EmptySubscriber.INSTANCE);
        availability = service.availability();
        assertTrue(availability == 0.0);

        factoryUp.set(true);
        Thread.sleep(100);

        availability = service.availability();
        assertTrue(availability == 1.0);
    }
}
