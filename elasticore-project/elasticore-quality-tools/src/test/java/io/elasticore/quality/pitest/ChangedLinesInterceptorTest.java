package io.elasticore.quality.pitest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangedLinesInterceptorTest {

    @Test
    void aMutationIsKnownByItsSourceFileAndLine() {
        assertEquals(
                "io/elasticore/base/Foo.java:12",
                ChangedLinesInterceptor.key("io.elasticore.base.Foo", "Foo.java", 12));
        // An inner or anonymous class is in its outer class's file.
        assertEquals(
                "io/elasticore/base/Foo.java:7",
                ChangedLinesInterceptor.key("io.elasticore.base.Foo$1", "Foo.java", 7));
        assertEquals("Top.java:3", ChangedLinesInterceptor.key("Top", "Top.java", 3));
    }

    @Test
    void aLineKeepsAtMostTheMutationsAskedForTurningWithTheLine() {
        java.util.List<String> four = Arrays.asList("a", "b", "c", "d");
        assertEquals(four, ChangedLinesInterceptor.pick(four, 0, 9));
        assertEquals(four, ChangedLinesInterceptor.pick(four, 4, 9));
        assertEquals(Arrays.asList("b"), ChangedLinesInterceptor.pick(four, 1, 9));
        assertEquals(Arrays.asList("c"), ChangedLinesInterceptor.pick(four, 1, 10));
        assertEquals(Arrays.asList("d", "a"), ChangedLinesInterceptor.pick(four, 2, 7));
    }

    @Test
    void theChangedLinesAreReadOnePerLineBlankLinesAside(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("lines.txt");
        Files.write(file, Arrays.asList("io/a/A.java:1", "", "  io/a/A.java:2  "), StandardCharsets.UTF_8);
        assertEquals(
                new java.util.HashSet<>(Arrays.asList("io/a/A.java:1", "io/a/A.java:2")),
                ChangedLinesInterceptorFactory.read(file.toString()));
    }
}
