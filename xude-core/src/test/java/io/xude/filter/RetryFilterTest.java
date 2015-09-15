package io.xude.filter;

import io.xude.Filter;
import io.xude.Filters;
import io.xude.Service;
import io.xude.Services;
import io.xude.failures.*;
import io.xude.failures.Exception;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.xude.util.Publishers.range;
import static io.xude.util.Publishers.toList;
import static org.junit.Assert.assertTrue;

public class RetryFilterTest {

    @Test(timeout = 10_000L)
    public void testRetryFilterOnRetryable()
        throws InterruptedException {
        testRetryFilter(new Retryable(), true);
    }

    @Test(timeout = 10_000L)
    public void testRetryFilterOnNonRetryable()
        throws InterruptedException {
        testRetryFilter(new Exception(), false);
    }

    private void testRetryFilter(io.xude.failures.Exception ex, boolean expectResponse)
        throws InterruptedException {
        AtomicInteger i = new AtomicInteger(0);
        Filter<Integer, Integer, String, String> failFirstFilter =
            Filters.<Integer, String, String>fromOutputFunction(x -> {
                if (i.getAndIncrement() == 0) {
                    throw ex;
                } else {
                    return "OK: " + x;
                }
            });

        Service<Integer, String> service = new RetryFilter<Integer, String>(1)
            .andThen(failFirstFilter)
            .andThen(Services.fromFunction(Object::toString));

        List<String> strings = toList(service.apply(range(1,2,3)));

        if (expectResponse) {
            assertTrue(strings.size() == 3);
            assertTrue(strings.get(0).equals("OK: 1"));
        } else {
            assertTrue(strings.size() == 2);
            assertTrue(strings.get(0).equals("OK: 2"));
        }
    }
}
