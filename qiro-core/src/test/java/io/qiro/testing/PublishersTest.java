package io.qiro.testing;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static io.qiro.util.Publishers.concat;
import static io.qiro.util.Publishers.just;
import static io.qiro.util.Publishers.toList;
import static org.junit.Assert.assertEquals;

public class PublishersTest {
    @Test(timeout = 10_000L)
    public void testPublishersConcat() throws Exception {
        List<Integer> integers = toList(concat(just(1), just(2)));
        assertEquals(Arrays.asList(1,2), integers);
    }
}
