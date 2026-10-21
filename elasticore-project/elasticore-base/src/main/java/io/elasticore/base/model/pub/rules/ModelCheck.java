package io.elasticore.base.model.pub.rules;

import io.elasticore.base.util.ConsoleLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a rule says about the model rather than writes into a file.
 *
 * A rule runs once per artifact of an entity - its entity class, its DTO, its
 * service - so the same finding would be said many times; each message is
 * said once per run. A warning is only said. A failure is said, generation
 * goes on so every finding is reported in one run, and the run then ends
 * non-zero, which fails the build that asked for the sources.
 *
 * Throwing from a rule is not the way to fail: it loses only the one file it
 * was writing and says nothing about why the model is wrong.
 */
public final class ModelCheck {

    private static final Set<String> SAID = Collections.synchronizedSet(new LinkedHashSet<String>());
    private static final List<String> FAILURES = Collections.synchronizedList(new ArrayList<String>());

    private ModelCheck() {
    }

    /** Says something worth fixing that does not stop the build. */
    public static void warn(String message) {
        if (SAID.add("warn:" + message)) ConsoleLog.printWarn(message);
    }

    /** Says something the build must not go on with; the run ends non-zero after generating. */
    public static void fail(String message) {
        if (SAID.add("fail:" + message)) {
            FAILURES.add(message);
            ConsoleLog.printWarn(message);
        }
    }

    /**
     * A failure already said where it happened - a model file the loader could
     * not parse, printed with the parser's own message: counted here, so the
     * rule engine's run ends non-zero, and not said again.
     */
    public static void failSaid(String message) {
        if (SAID.add("fail:" + message)) FAILURES.add(message);
    }

    /** The failures said so far, in order. */
    public static List<String> failures() {
        synchronized (FAILURES) {
            return new ArrayList<String>(FAILURES);
        }
    }

    /** Forgets what was said - for a test, or a second run in one process. */
    public static void reset() {
        SAID.clear();
        FAILURES.clear();
    }
}
