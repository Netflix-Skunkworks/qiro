package io.qiro;

import io.qiro.testing.ThreadedService;
import io.qiro.util.Publishers;
import org.junit.Test;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.qiro.util.Publishers.toList;
import static org.junit.Assert.assertEquals;

public class ConcurrencyTest {

    @Test(timeout = 10_000L)
    public void testConcurrency() throws InterruptedException {

        List<Integer> integers = toList(Publishers.range(0, 200));
        assertEquals(200, integers.size());

        Filter<Integer, Integer, String, String> toLowerCase =
            Filters.<Integer, String, String>fromOutputFunction(String::toLowerCase);

        Service<Integer, String> service = ServiceFactories.fromFunctions(
            () -> toLowerCase.andThen(new ThreadedService<>(Object::toString)),
            () -> null
        ).toService();


        ExecutorService executor = Executors.newFixedThreadPool(64);
        int concurrency = 1000;
        CountDownLatch c = new CountDownLatch(concurrency);
        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                Publisher<Integer> inputs = Publishers.range(0, 1000);
                try {
                    toList(service.apply(inputs));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                c.countDown();
            });
        }
        c.await();
        System.out.println("All done!");
    }
}
