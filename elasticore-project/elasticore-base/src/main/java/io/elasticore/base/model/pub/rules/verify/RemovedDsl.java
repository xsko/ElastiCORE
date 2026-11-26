package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.RemovedDeclaration;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.util.ConsoleLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The DSL 3.1 no longer reads (issue 370): domainService:, processManager:
 * and @specification, and the two rule sets that generated them when a
 * selection still names one. Each is said once per declaration, with the
 * domain, the name and the code to write instead.
 *
 * A notice, not a DSL warning. A warning (DSL_WARN) is counted by
 * elcoreValidate -Pstrict, so a project that built yesterday would fail its
 * CI on a declaration that now does nothing. The notices are kept under their
 * own key: printed after the DSL warnings and listed in the validation
 * report, never counted.
 */
final class RemovedDsl {

    /** The ConsoleLog key of the notices; -Pstrict counts DSL_WARN only. */
    static final String NOTICE = "DSL_NOTICE";

    /** The heading the notices are printed under. */
    static final String HEADER =
            "[elcore3] DSL no longer read since 3.1 - remove it (a notice: -Pstrict does not count it):";

    /** What every notice says first, after the declaration. */
    static final String NO_LONGER_READ = "is no longer read since 3.1 - remove it;";

    private RemovedDsl() {}

    /** The notices of a model: its removed sections' entries and its entities' @specification. */
    static List<String> of(String domain, ECoreModel model, List<EntitySpec> specs) {
        List<String> out = new ArrayList<String>();
        if (model != null && model.getRemovedDeclarations() != null) {
            for (RemovedDeclaration r : model.getRemovedDeclarations()) {
                out.add(
                        RemovedDeclaration.PROCESS_MANAGER.equals(r.section())
                                ? processManager(domain, r.name(), r.names())
                                : domainService(domain, r.name(), r.names()));
            }
        }
        if (specs != null) {
            for (EntitySpec e : specs) {
                if (!e.hasMeta("specification")) continue;
                for (String value : e.metaValues("specification")) {
                    out.add(specification(domain, e.getName(), value == null ? "" : value.trim()));
                }
            }
        }
        return out;
    }

    /** The notices of rule set names a selection still lists. */
    static List<String> ofRuleSets(String domain, List<String> selected) {
        List<String> out = new ArrayList<String>();
        if (selected == null) return out;
        for (String name : selected) {
            if (io.elasticore.base.model.pub.rules.RuleCatalog.removed(name)) out.add(ruleSet(domain, name));
        }
        return out;
    }

    /** Stores each notice the run has not said yet. */
    static void record(List<String> notices, Set<String> said) {
        for (String n : notices) {
            if (said.add(n)) ConsoleLog.storeLog(NOTICE, n);
        }
    }

    /** Prints the stored notices under their heading and clears them. */
    static int print() {
        return ConsoleLog.printRemainingAsOrphans(NOTICE, HEADER, "  ", false);
    }

    static String domainService(String domain, String name, List<String> uses) {
        String ports = uses == null || uses.isEmpty()
                ? "the repository ports it needs"
                : "the repository ports of " + String.join(", ", uses);
        return String.format(
                "[%s] domainService%s %s write a @Service class in the domain.service package that takes %s in"
                        + " its constructor.",
                domain, name == null ? ":" : " " + name, NO_LONGER_READ, ports);
    }

    static String processManager(String domain, String name, List<String> on) {
        String events = on == null || on.isEmpty() ? "each event" : "each event of 'on' (" + String.join(", ", on) + ")";
        return String.format(
                "[%s] processManager%s %s write a @Component with a method for %s annotated"
                        + " @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true) and"
                        + " @Transactional(propagation = REQUIRES_NEW), or a plain @EventListener when it must run in"
                        + " the caller's transaction.",
                domain, name == null ? ":" : " " + name, NO_LONGER_READ, events);
    }

    static String specification(String domain, String entity, String declared) {
        int comma = declared.indexOf(',');
        String name = (comma < 0 ? declared : declared.substring(0, comma)).trim();
        String method = name.isEmpty() ? "rule" : name;
        return String.format(
                "[%s] %s @specification(%s) %s write a static Predicate<%s> %s() method in a class of your own,"
                        + " plus the TypeScript function if screens need it.",
                domain, entity, name, NO_LONGER_READ, entity, method);
    }

    static String ruleSet(String domain, String name) {
        return String.format(
                "[%s] the rule set %s %s it generated domainService:, processManager: or @specification,"
                        + " which are written by hand now; the selection runs without it.",
                domain, name, NO_LONGER_READ);
    }
}
