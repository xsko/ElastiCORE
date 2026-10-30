package io.elasticore.quality.pitest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.pitest.bytecode.analysis.ClassTree;
import org.pitest.mutationtest.build.InterceptorType;
import org.pitest.mutationtest.build.MutationInterceptor;
import org.pitest.mutationtest.engine.Mutater;
import org.pitest.mutationtest.engine.MutationDetails;

/**
 * Drops every mutation that is not on a changed line, and keeps at most
 * {@code perLine} of each line's when that is above 0 (see {@link ChangedLinesInterceptorFactory}).
 */
class ChangedLinesInterceptor implements MutationInterceptor {

    private final Set<String> changed;
    private final int perLine;

    ChangedLinesInterceptor(Set<String> changed, int perLine) {
        this.changed = changed;
        this.perLine = perLine;
    }

    @Override
    public InterceptorType type() {
        return InterceptorType.FILTER;
    }

    @Override
    public void begin(ClassTree clazz) {}

    @Override
    public Collection<MutationDetails> intercept(Collection<MutationDetails> mutations, Mutater mutater) {
        Map<String, List<MutationDetails>> byLine = new LinkedHashMap<>();
        for (MutationDetails mutation : mutations) {
            String key = key(mutation.getClassName().asJavaName(), mutation.getFilename(), mutation.getLineNumber());
            if (changed.contains(key)) {
                byLine.computeIfAbsent(key, k -> new ArrayList<>()).add(mutation);
            }
        }
        List<MutationDetails> kept = new ArrayList<>();
        for (List<MutationDetails> line : byLine.values()) {
            kept.addAll(pick(line, perLine, line.get(0).getLineNumber()));
        }
        return kept;
    }

    @Override
    public void end() {}

    /**
     * At most {@code count} of a line's mutations, all of them when count is 0 or
     * more than there are. Which ones turns with the line number, so a change of
     * many lines meets every kind of mutation rather than the first kind on each.
     */
    static <T> List<T> pick(List<T> mutations, int count, int line) {
        if (count <= 0 || mutations.size() <= count) {
            return mutations;
        }
        List<T> picked = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            picked.add(mutations.get((line + i) % mutations.size()));
        }
        return picked;
    }

    /** io.elasticore.base.Foo$Bar, Foo.java, 12 -> io/elasticore/base/Foo.java:12 */
    static String key(String className, String fileName, int line) {
        int dot = className.lastIndexOf('.');
        String dir = dot < 0 ? "" : className.substring(0, dot).replace('.', '/') + "/";
        return dir + fileName + ":" + line;
    }
}
