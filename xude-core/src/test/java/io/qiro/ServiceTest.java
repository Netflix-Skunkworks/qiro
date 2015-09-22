package io.qiro;

import org.junit.Test;
import org.reactivestreams.Publisher;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static io.qiro.util.Publishers.*;

public class ServiceTest {
    @Test
    public void testBasicService() throws InterruptedException {
        Service<Integer, String> toStringService = Services.fromFunction(Object::toString);
        testService(toStringService);
    }

    static void testService(Service<Integer, String> toStringService) throws InterruptedException {
        Publisher<String> stringPublisher = toStringService.apply(just(1));
        List<String> singleString = toList(stringPublisher);
        assertTrue(singleString.size() == 1);
        assertTrue(singleString.get(0).equals("1"));

        Publisher<String> stringPublisher2 =
            toStringService.apply(from(1, 2, 3, 4, 5));
        List<String> strings = toList(stringPublisher2);
        assertTrue(strings.equals(Arrays.asList("1", "2", "3", "4", "5")));

        Publisher<Double> doubles = from(1.0, 2.0, 3.0, 4.0, 5.0);
        Filter<Double, Integer, String, String> filter =
            Filters.fromFunction(x -> (int) (2 * x), str -> "'" + str + "'");
        Publisher<String> apply = filter.apply(doubles, toStringService);
        List<String> strings2 = toList(apply);
        assertTrue(strings2.equals(Arrays.asList("'2'", "'4'", "'6'", "'8'", "'10'")));
    }
}
