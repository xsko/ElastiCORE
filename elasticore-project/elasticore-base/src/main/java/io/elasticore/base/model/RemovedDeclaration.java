package io.elasticore.base.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A declaration of a DSL section that 3.1 no longer reads (issue 370): a
 * domainService: or a processManager: entry. The loader keeps only what the
 * notice names - the section, the name, and its uses or on list - so that a
 * project that still has one is told what to write instead, and its build
 * does not fail on it.
 */
public final class RemovedDeclaration {

    /** domainService: an operation of no single aggregate. */
    public static final String DOMAIN_SERVICE = "domainService";

    /** processManager: what reacts to which events. */
    public static final String PROCESS_MANAGER = "processManager";

    private final String section;
    private final String name;
    private final List<String> names;

    public RemovedDeclaration(String section, String name, List<String> names) {
        this.section = section;
        this.name = name;
        this.names = names == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(names));
    }

    /** DOMAIN_SERVICE or PROCESS_MANAGER. */
    public String section() {
        return section;
    }

    /** The name the declaration gave (Pricing, Loyalty). */
    public String name() {
        return name;
    }

    /** What it listed: a domain service's uses, a process manager's on. */
    public List<String> names() {
        return names;
    }
}
