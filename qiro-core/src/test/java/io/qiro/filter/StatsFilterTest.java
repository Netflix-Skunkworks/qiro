package io.qiro.filter;

import io.qiro.Service;
import io.qiro.Services;
import org.junit.Test;

import java.util.List;

import static io.qiro.util.Publishers.from;
import static io.qiro.util.Publishers.toList;

public class StatsFilterTest {

    @Test(timeout = 1000L)
    public void testStatsFilter() throws InterruptedException {
        StatsFilter<Integer, String> statsFilter = new StatsFilter<>();
        Service<Integer, String> toStringService =
            statsFilter.andThen(Services.fromFunction(Object::toString));

        List<String> strings = toList(toStringService.requestChannel(from(1, 2, 3)));
    }
}
