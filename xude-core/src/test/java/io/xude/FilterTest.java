package io.xude;

import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.Arrays;
import java.util.List;

import static io.xude.util.Publishers.just;
import static io.xude.util.Publishers.range;
import static io.xude.util.Publishers.toList;
import static org.junit.Assert.assertTrue;

public class FilterTest {
    @Test
    public void testBasicFilter() throws InterruptedException {
        Filter<Integer, Integer, String, String> square =
            Filters.fromInputFunction(x -> x * x);
        Filter<Integer, Integer, String, String> plusOne =
            Filters.fromInputFunction(x -> x + 1);
        Service<Integer, String> toStringService = Services.fromFunction(Object::toString);

        Filter<Integer, Integer, String, String> xSquarePlusOne = square.andThen(plusOne);
        Service<Integer, String> operation = xSquarePlusOne.andThen(toStringService);

        Publisher<String> stringsPublisher =
            operation.apply(range(1, 2, 3, 4, 5));
        List<String> strings = toList(stringsPublisher);
        System.out.println(strings);
        assertTrue(strings.equals(Arrays.asList("2", "5", "10", "17", "26")));
    }

    @Test
    public void testServiceFactoryFilter() throws InterruptedException {
        ServiceFactory<Integer, String> factory = ServiceFactories.fromFunctions(
            () -> just(Services.fromFunction(Object::toString)),
            () -> Subscriber::onComplete
        );
        Filter<Integer, Integer, String, String> square =
            Filters.fromInputFunction(x -> x * x);

        ServiceFactory<Integer, String> squareToStringFactory = square.andThen(factory);
        Service<Integer, String> squareToString =
            new FactoryToService<>(squareToStringFactory);

        Publisher<String> stringPublisher2 =
            squareToString.apply(range(1, 2, 3, 4, 5));
        List<String> strings = toList(stringPublisher2);
        System.out.println(strings);
        assertTrue(strings.equals(Arrays.asList("1", "4", "9", "16", "25")));
    }
}
