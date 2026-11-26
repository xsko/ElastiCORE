package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.domainservice.DomainService;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.util.ConsoleLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The DSL that still generates as before and is removed in 3.1 (issue 370):
 * domainService:, processManager: and @specification. Each is said once per
 * declaration, with the domain, the name and the code to write instead.
 *
 * A notice, not a DSL warning. A warning (DSL_WARN) is counted by
 * elcoreValidate -Pstrict, so a project that builds today would fail its CI
 * for a feature that still works. The notices are kept under their own key:
 * printed after the DSL warnings and listed in the validation report, never
 * counted.
 */
final class DeprecatedDsl {

    /** The ConsoleLog key of the notices; -Pstrict counts DSL_WARN only. */
    static final String NOTICE = "DSL_NOTICE";

    /** The heading the notices are printed under. */
    static final String HEADER =
            "[elcore3] Deprecated DSL - still generated, removed in 3.1 (a notice: -Pstrict does not count it):";

    private DeprecatedDsl() {}

    /** The notices of a model: its domain services, its process managers and its entities' @specification. */
    static List<String> of(String domain, ECoreModel model, List<EntitySpec> specs) {
        List<String> out = new ArrayList<String>();
        if (model != null && model.getDomainServiceModels() != null) {
            // Read off the list, not the shared cursor the rules walk.
            for (DomainService s : model.getDomainServiceModels().getOrgItems().getItemList()) {
                String name = s.getIdentity().getName();
                out.add(
                        s.isProcessManager()
                                ? processManager(domain, name, s.getOn())
                                : domainService(domain, name, s.getUses()));
            }
        }
        if (specs != null) {
            for (EntitySpec e : specs) {
                if (!e.hasMeta("specification")) continue;
                for (String value : e.metaValues("specification")) {
                    if (value == null || value.trim().isEmpty()) continue;
                    out.add(specification(domain, e.getName(), value.trim()));
                }
            }
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
                "[%s] domainService %s is deprecated and is removed in 3.1 (it is still generated as before):"
                        + " write a @Service class in the domain.service package that takes %s in its constructor.",
                domain, name, ports);
    }

    static String processManager(String domain, String name, List<String> on) {
        return String.format(
                "[%s] processManager %s is deprecated and is removed in 3.1 (it is still generated as before):"
                        + " write a @Component with a method for each event of 'on' (%s) annotated"
                        + " @TransactionalEventListener(phase = AFTER_COMMIT) and"
                        + " @Transactional(propagation = REQUIRES_NEW), or a plain @EventListener when it must run"
                        + " in the caller's transaction.",
                domain, name, on == null ? "" : String.join(", ", on));
    }

    static String specification(String domain, String entity, String declared) {
        int comma = declared.indexOf(',');
        String name = (comma < 0 ? declared : declared.substring(0, comma)).trim();
        return String.format(
                "[%s] %s @specification(%s) is deprecated and is removed in 3.1 (it is still generated as before):"
                        + " write a static Predicate<%s> %s() method of your own, plus the TypeScript function if"
                        + " screens need it.",
                domain, entity, name, entity, name);
    }
}
