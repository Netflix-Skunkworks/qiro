package io.xude.filter;

import io.xude.Service;
import io.xude.Services;
import org.junit.Test;

import java.util.List;

import static io.xude.util.Publishers.range;
import static io.xude.util.Publishers.toList;

public class StatsFilterTest {

    @Test(timeout = 1000L)
    public void testStatsFilter() throws InterruptedException {
        StatsFilter<Integer, String> statsFilter = new StatsFilter<>();
        Service<Integer, String> toStringService =
            statsFilter.andThen(Services.fromFunction(Object::toString));

        List<String> strings = toList(toStringService.apply(range(1, 2, 3)));
    }
}
