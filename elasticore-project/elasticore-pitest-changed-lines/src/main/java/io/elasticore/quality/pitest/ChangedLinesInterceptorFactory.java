package io.elasticore.quality.pitest;

import org.pitest.mutationtest.build.InterceptorParameters;
import org.pitest.mutationtest.build.MutationInterceptor;
import org.pitest.mutationtest.build.MutationInterceptorFactory;
import org.pitest.plugin.Feature;
import org.pitest.plugin.FeatureParameter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * PIT's feature CHANGEDLINES: only the mutations on the lines a change adds or
 * edits are run. The lines are a file of "package/path/File.java:line" entries,
 * which the build writes from the diff (gradle/quality.gradle, diffMutation).
 *
 * PIT mutates whole classes; a change of three lines in a class of five hundred
 * would otherwise run every mutant of the five hundred, and report survivors
 * the change did not write.
 */
public class ChangedLinesInterceptorFactory implements MutationInterceptorFactory {

    static final FeatureParameter FILE = FeatureParameter.named("file")
            .withDescription("The file of package/path/File.java:line entries to keep the mutations of");

    @Override
    public MutationInterceptor createInterceptor(InterceptorParameters params) {
        String file = params.getString(FILE)
                .orElseThrow(() -> new IllegalArgumentException("CHANGEDLINES needs file[<path of the changed lines>]"));
        return new ChangedLinesInterceptor(read(file));
    }

    @Override
    public Feature provides() {
        return Feature.named("CHANGEDLINES")
                .withOnByDefault(false)
                .withDescription(description())
                .withParameter(FILE);
    }

    @Override
    public String description() {
        return "Keeps only the mutations on the lines a change adds or edits";
    }

    static Set<String> read(String file) {
        try {
            Set<String> lines = new HashSet<>();
            for (String line : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) {
                if (!line.trim().isEmpty()) {
                    lines.add(line.trim());
                }
            }
            return lines;
        } catch (IOException e) {
            throw new UncheckedIOException("CHANGEDLINES cannot read " + file, e);
        }
    }
}
