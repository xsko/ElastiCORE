package io.elasticore.quality.pitest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.pitest.classinfo.ClassName;
import org.pitest.mutationtest.engine.Location;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.MutationIdentifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private static MutationDetails mutation(String className, int line, int index) {
        Location location = Location.location(ClassName.fromString(className), "m", "()V");
        return new MutationDetails(
                new MutationIdentifier(location, index, "M" + index), "A.java", "mutation " + index, line, 0);
    }

    @Test
    void onlyTheMutationsOfChangedLinesAreKept() {
        Set<String> changed = new HashSet<>(Arrays.asList("io/a/A.java:3", "io/a/A.java:5"));
        List<MutationDetails> all = Arrays.asList(
                mutation("io.a.A", 3, 0),
                mutation("io.a.A", 3, 1),
                mutation("io.a.A", 4, 2),
                mutation("io.a.A$1", 5, 3),
                mutation("io.b.A", 3, 4));

        Collection<MutationDetails> kept = new ChangedLinesInterceptor(changed, 0).intercept(all, null);
        assertEquals(Arrays.asList(all.get(0), all.get(1), all.get(3)), new ArrayList<>(kept));

        Collection<MutationDetails> one = new ChangedLinesInterceptor(changed, 1).intercept(all, null);
        assertEquals(Arrays.asList(all.get(1), all.get(3)), new ArrayList<>(one), "line 3 keeps one of its two");
    }

    @Test
    void theFeatureNeedsItsFile() {
        assertThrows(java.io.UncheckedIOException.class, () -> ChangedLinesInterceptorFactory.read("no/such/file.txt"));
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
