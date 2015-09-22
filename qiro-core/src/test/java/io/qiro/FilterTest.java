package io.qiro;

import org.junit.Test;
import org.reactivestreams.Publisher;

import java.util.Arrays;
import java.util.List;

import static io.qiro.util.Publishers.from;
import static io.qiro.util.Publishers.toList;
import static org.junit.Assert.assertTrue;

public class FilterTest {
    @Test(timeout = 10_000L)
    public void testBasicFilter() throws InterruptedException {
        Filter<Integer, Integer, String, String> square =
            Filters.fromInputFunction(x -> x * x);
        Filter<Integer, Integer, String, String> plusOne =
            Filters.fromInputFunction(x -> x + 1);
        Service<Integer, String> toStringService = Services.fromFunction(Object::toString);

        Filter<Integer, Integer, String, String> xSquarePlusOne = square.andThen(plusOne);
        Filter<Integer, Integer, String, String> xPlusOneSquare = plusOne.andThen(square);
        Service<Integer, String> operation = xSquarePlusOne.andThen(toStringService);
        Service<Integer, String> operation2 = xPlusOneSquare.andThen(toStringService);


        Publisher<Integer> inputs = from(1, 2, 3, 4, 5);
        Publisher<String> outputs = operation.apply(inputs);
        Publisher<String> outputs2 = operation2.apply(inputs);

        List<String> strings = toList(outputs);
        List<String> strings2 = toList(outputs2);
        System.out.println(strings);
        System.out.println(strings2);
        assertTrue(strings.equals(Arrays.asList("2", "5", "10", "17", "26")));
        assertTrue(strings2.equals(Arrays.asList("4", "9", "16", "25", "36")));
    }

    @Test(timeout = 10_000L)
    public void testServiceFactoryFilter() throws InterruptedException {
        ServiceFactory<Integer, String> factory = ServiceFactories.fromFunctions(
            () -> Services.<Integer, String>fromFunction(Object::toString),
            () -> null
        );
        Filter<Integer, Integer, String, String> square =
            Filters.fromInputFunction(x -> x * x);

        Service<Integer, String> squareToString =
            square
                .andThen(factory)
                .toService();

        Publisher<String> stringPublisher2 =
            squareToString.apply(from(1, 2, 3, 4, 5));
        List<String> strings = toList(stringPublisher2);
        System.out.println(strings);
        assertTrue(strings.equals(Arrays.asList("1", "4", "9", "16", "25")));
    }
}
