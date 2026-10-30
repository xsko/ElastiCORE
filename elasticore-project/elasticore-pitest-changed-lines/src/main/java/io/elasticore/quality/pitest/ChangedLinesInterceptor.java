package io.elasticore.quality.pitest;

import org.pitest.bytecode.analysis.ClassTree;
import org.pitest.mutationtest.build.InterceptorType;
import org.pitest.mutationtest.build.MutationInterceptor;
import org.pitest.mutationtest.engine.Mutater;
import org.pitest.mutationtest.engine.MutationDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Drops every mutation that is not on a changed line (see {@link ChangedLinesInterceptorFactory}). */
class ChangedLinesInterceptor implements MutationInterceptor {

    private final Set<String> changed;

    ChangedLinesInterceptor(Set<String> changed) {
        this.changed = changed;
    }

    @Override
    public InterceptorType type() {
        return InterceptorType.FILTER;
    }

    @Override
    public void begin(ClassTree clazz) {
    }

    @Override
    public Collection<MutationDetails> intercept(Collection<MutationDetails> mutations, Mutater mutater) {
        List<MutationDetails> kept = new ArrayList<>();
        for (MutationDetails mutation : mutations) {
            if (changed.contains(key(mutation.getClassName().asJavaName(), mutation.getFilename(), mutation.getLineNumber()))) {
                kept.add(mutation);
            }
        }
        return kept;
    }

    @Override
    public void end() {
    }

    /** io.elasticore.base.Foo$Bar, Foo.java, 12 -> io/elasticore/base/Foo.java:12 */
    static String key(String className, String fileName, int line) {
        int dot = className.lastIndexOf('.');
        String dir = dot < 0 ? "" : className.substring(0, dot).replace('.', '/') + "/";
        return dir + fileName + ":" + line;
    }
}
