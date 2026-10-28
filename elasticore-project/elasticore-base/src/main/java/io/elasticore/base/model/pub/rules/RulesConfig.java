package io.elasticore.base.model.pub.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves which rule sets apply, from three levels of configuration:
 *
 *   1. domain level   - env.yml  publisher.rules / publisher.suppress
 *   2. project level  - system properties, set once in build.gradle:
 *                         systemProperty 'elasticore.publisher.rules', '...'
 *                         systemProperty 'elasticore.publisher.suppress', '...'
 *                       (or gradle.properties + pass-through, same channel
 *                        the elcore task already uses for mode/domain)
 *   3. built-in       - the "jpa-springboot" bundle
 *
 * A domain only declares rules when it deviates from the project default;
 * most projects configure level 2 once and leave env.yml untouched.
 */
public final class RulesConfig {

    public static final String PROP_RULES = "elasticore.publisher.rules";
    public static final String PROP_SUPPRESS = "elasticore.publisher.suppress";

    /**
     * Rule sets a project adds to whatever its domains select - its own
     * CodeRules classes, by class name. Added rather than substituted, so a
     * project keeps the bundle its architecture and language chose and does
     * not have to restate it (and cannot restate it for the wrong language).
     * The build plugin's rules list arrives here.
     */
    public static final String PROP_RULES_ADD = "elasticore.publisher.rules.add";

    /** The rule sets the project adds, in order; empty when it adds none. */
    public static List<String> addedRules() {
        String prop = System.getProperty(PROP_RULES_ADD);
        List<String> out = new ArrayList<String>();
        if (prop == null) return out;
        for (String s : splitCsv(prop)) out.add(s);
        return out;
    }

    public static final String DEFAULT_BUNDLE = "jpa-springboot";

    /**
     * The clean-mode counterpart: file-rendering rule sets swapped for their
     * used-imports-only versions. Everything mode-agnostic (field rules,
     * spec-level artifacts) is the same set as {@link #DEFAULT_BUNDLE}.
     */
    public static final String DEFAULT_BUNDLE_V3 = "jpa-springboot-v3";

    /**
     * The Kotlin bundle. Smaller than the other two on purpose: an artifact
     * joins it when its rules emit Kotlin, and until then is absent rather
     * than falling back to a Java shell that would compile as neither.
     */
    public static final String DEFAULT_BUNDLE_KT = "jpa-springboot-kt";

    /**
     * How the generated code is shaped. The wizard asks this; a build declares it.
     */
    public enum Architecture {
        LAYERED, DOMAIN_MODEL, HEXAGONAL
    }

    /** What it is written in. */
    public enum Language {
        JAVA, KOTLIN, TYPESCRIPT
    }

    /**
     * Where the generated code keeps its data: the third choice beside the
     * architecture and the language. A domain says it in env.yml as its mode
     * (config.mode, "jpa" when unsaid) - the key 2.x already reads for jpa and
     * mongo - so that one word, not two, names it.
     *
     * The rule engine writes JPA and MyBatis code. MongoDB is known so that a
     * domain naming it is told it is the 2.x generator's, rather than refused
     * as an unknown word.
     */
    public enum Persistence {
        JPA, MYBATIS, MONGO;

        /** Whether the rule engine writes code for it. */
        public boolean generated() {
            return this != MONGO;
        }
    }

    /** The MyBatis bundle: layered Java, the JPA bundle's DTOs and API over MyBatis mappers. */
    public static final String MYBATIS_BUNDLE = "mybatis-springboot";

    /** Whether a domain whose env.yml mode is this one is generated as MyBatis code. */
    public static boolean mybatis(String mode) {
        return persistenceOfMode(mode) == Persistence.MYBATIS;
    }

    /**
     * Why an architecture and language cannot be generated as MyBatis code, or
     * null when they can. MyBatis code is layered Java: the domain model and
     * the hexagon rest on JPA's change tracking and lifecycle callbacks, and
     * the Kotlin service is the JPA one.
     */
    public static String mybatisRefusal(Architecture architecture, Language language) {
        if (language == Language.TYPESCRIPT) return null;
        if (language == Language.KOTLIN) {
            return "MyBatis code is written in Java; language = 'kotlin' is generated for JPA only";
        }
        if (architecture == Architecture.DOMAIN_MODEL || architecture == Architecture.HEXAGONAL) {
            return "MyBatis code is layered (architecture = 'layered'); architecture = '"
                    + (architecture == Architecture.DOMAIN_MODEL ? "ddd" : "hexagonal")
                    + "' is generated for JPA only";
        }
        return null;
    }

    /**
     * The persistence the rule engine writes for a domain's mode, or null
     * when the mode names none.
     *
     * The mode is a list - "jpa,uml" writes JPA code and a UML document; uml
     * and dsl are documents. 2.x writes every persistence a mode names, so
     * "jpa,mongo" is a project 2.x already serves; here the first in the
     * order of the enum is the one written, JPA before the others, and the
     * caller says what is left to the 2.x generator. Unsaid is JPA, as it
     * always was.
     */
    public static Persistence persistenceOfMode(String mode) {
        if (mode == null || mode.trim().isEmpty()) return Persistence.JPA;
        Persistence found = null;
        for (String part : mode.split(",")) {
            Persistence p = persistenceNamed(part);
            if (p != null && (found == null || p.ordinal() < found.ordinal())) found = p;
        }
        return found;
    }

    /** One word of a mode as a persistence, or null when it is not one. */
    public static Persistence persistenceNamed(String name) {
        if (name == null) return null;
        String key = name.trim().toLowerCase();
        if (key.equals("jpa")) return Persistence.JPA;
        if (key.equals("mybatis")) return Persistence.MYBATIS;
        if (key.equals("mongo") || key.equals("mongodb")) return Persistence.MONGO;
        return null;
    }

    /**
     * The bundle three choices name. A combination with no bundle is refused
     * here as well as by the runner, so a caller cannot get a JPA bundle for
     * a domain that asked for something else. The browser's model is the same
     * whatever the server keeps its data in.
     */
    public static String bundleFor(Architecture architecture, Language language, Persistence persistence) {
        if (persistence == null || persistence == Persistence.JPA || language == Language.TYPESCRIPT) {
            return bundleFor(architecture, language);
        }
        if (persistence == Persistence.MYBATIS) {
            String refusal = mybatisRefusal(architecture, language);
            if (refusal != null) throw new IllegalArgumentException(refusal + ".");
            return MYBATIS_BUNDLE;
        }
        throw new IllegalArgumentException("No rule bundle writes " + persistence.name().toLowerCase()
                + " code; the 2.x generator does.");
    }

    /**
     * The bundle two choices name, so that they cannot be named separately -
     * for JPA, the persistence every bundle here writes.
     *
     * A bundle decides which rules run; a system property decides which tree
     * and which extension. They are two switches for one decision, and the
     * rule guide warns about the pairing that follows -
     * <em>publisher.rules: [jpa-springboot-kt] alone does not produce .kt
     * files</em> - which is a warning that exists because people hit it.
     * SourceCode.requireSyntax throws on the same mistake later, from further
     * away.
     *
     * Composing the name from the pair removes the mistake rather than
     * diagnosing it: there is no way to spell a Kotlin architecture in Java.
     * This table was written twice before it was written here, once in the
     * initialization wizard and once as the runner's defaults, so it is the
     * one both now read.
     *
     * TypeScript takes no architecture. It is a client model - DTOs, enums, a
     * typed REST client - and an aggregate boundary is a server-side statement
     * about writes, so there is one TypeScript bundle rather than three.
     */
    public static String bundleFor(Architecture architecture, Language language) {
        if (language == Language.TYPESCRIPT) return "ts-model";

        boolean kotlin = language == Language.KOTLIN;
        switch (architecture) {
            case DOMAIN_MODEL:
                return kotlin ? "ddd-springboot-kt" : "ddd-springboot";
            case HEXAGONAL:
                return kotlin ? "hexagonal-springboot-kt" : "hexagonal-springboot";
            case LAYERED:
            default:
                // Layered Java is the v3 bundle since 3.0.261028-rc3; the code 2.x
                // generates (DEFAULT_BUNDLE) is chosen, not defaulted to.
                return kotlin ? DEFAULT_BUNDLE_KT : DEFAULT_BUNDLE_V3;
        }
    }

    public static final String PROP_ARCHITECTURE = "elasticore.architecture";
    public static final String PROP_LANGUAGE = "elasticore.language";

    /**
     * The architecture a build declared, or null when it declared nothing.
     *
     * Null is not "layered": it is a project that made no choice, which the
     * generator treats differently from one that did - a project that said
     * nothing and holds the code 2.x generated is stopped rather than
     * rewritten (RulesGenRunner.twoXGenerated).
     */
    public static Architecture declaredArchitecture() {
        return architectureNamed(System.getProperty(PROP_ARCHITECTURE));
    }

    /** As above, for the language. Null when unspoken. */
    public static Language declaredLanguage() {
        return languageNamed(System.getProperty(PROP_LANGUAGE));
    }

    /**
     * The Spring Boot generation the generated code runs on: "4" or "4.0.8"
     * from the build (the plugin reads it off the Spring Boot plugin, or the
     * elasticore block declares it), 3 when nothing says otherwise.
     */
    public static final String PROP_SPRING_BOOT = "elasticore.springBoot";

    public static int springBootMajor() {
        String v = System.getProperty(PROP_SPRING_BOOT);
        if (v == null || v.trim().isEmpty()) return 3;
        try {
            return Integer.parseInt(v.trim().split("\\.")[0]);
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    /**
     * Decides the Spring Boot generation once, before anything is generated,
     * and says it in the log - a Boot 4 project generated as Boot 3 writes
     * enums Jackson 3 sends by name, which nothing else would reveal.
     *
     * The build's word wins (the plugin, or a JavaExec systemProperty);
     * otherwise a domain's env.yml "config: springBoot: 4" - which also
     * reaches the annotation processor, where no build property does.
     */
    public static void settleSpringBoot(java.util.Collection<io.elasticore.base.ECoreModelContext> contexts) {
        java.util.Map<String, String> declared = new java.util.LinkedHashMap<String, String>();
        if (contexts != null) {
            for (io.elasticore.base.ECoreModelContext ctx : contexts) {
                String[] names = ctx.getAllDomainNames();
                if (names == null) continue;
                for (String name : names) {
                    io.elasticore.base.ModelDomain d = ctx.getDomain(name);
                    String value = d == null || d.getModel() == null ? null : d.getModel().getConfig("springBoot");
                    if (value != null && !value.trim().isEmpty() && !declared.containsKey(name)) {
                        declared.put(name, value.trim());
                    }
                }
            }
        }
        settleSpringBoot(declared);
    }

    /** The value an earlier run took from its own env.yml, to forget at the next. */
    private static String adoptedSpringBoot;

    /**
     * As above, from what each domain's env.yml declares (domain name to
     * value) - the first one wins.
     */
    public static synchronized void settleSpringBoot(java.util.Map<String, String> declaredByDomain) {
        // A value an earlier run took from its env.yml is that project's, not
        // this one's: a compiler daemon runs the processor for many builds.
        if (adoptedSpringBoot != null && adoptedSpringBoot.equals(System.getProperty(PROP_SPRING_BOOT))) {
            System.clearProperty(PROP_SPRING_BOOT);
        }
        adoptedSpringBoot = null;
        String from = "build";
        String v = System.getProperty(PROP_SPRING_BOOT);
        if ((v == null || v.trim().isEmpty()) && declaredByDomain != null && !declaredByDomain.isEmpty()) {
            java.util.Map.Entry<String, String> first = declaredByDomain.entrySet().iterator().next();
            v = first.getValue();
            from = "env.yml of " + first.getKey();
            System.setProperty(PROP_SPRING_BOOT, v);
            adoptedSpringBoot = v;
        }
        if (v == null || v.trim().isEmpty()) {
            io.elasticore.base.util.ConsoleLog.print("[ElastiCORE] Spring Boot: 3 (none declared)");
            return;
        }
        try {
            Integer.parseInt(v.trim().split("\\.")[0]);
        } catch (NumberFormatException e) {
            io.elasticore.base.util.ConsoleLog.printWarn(String.format(
                    "[ElastiCORE] Spring Boot version '%s' (%s) is not a version; generating for Spring Boot 3."
                            + " Write it as 3 or 4.", v, from));
            return;
        }
        io.elasticore.base.util.ConsoleLog.print(String.format("[ElastiCORE] Spring Boot: %s (%s)%s",
                v, from, jackson3() ? " - enums and the outbox are written for Jackson 3" : ""));
    }

    /**
     * Whether the generated code reads JSON with Jackson 3 - Spring Boot 4,
     * whose web layer no longer looks at Jackson 2's databind annotations.
     */
    public static boolean jackson3() {
        return springBootMajor() >= 4;
    }

    /**
     * Reads the word a build script wrote.
     *
     * Both spellings are taken - "ddd" as a build would write it, DOMAIN_MODEL
     * as the enum spells it - because a user writing 'ddd' in a Gradle file
     * and getting an enum-name error would be right to be annoyed.
     */
    public static Architecture architectureNamed(String name) {
        if (name == null) return null;
        String key = name.trim().toLowerCase().replace('-', '_');
        if (key.isEmpty()) return null;

        if (key.equals("ddd") || key.equals("domain_model")) return Architecture.DOMAIN_MODEL;
        if (key.equals("hexagonal") || key.equals("hex")) return Architecture.HEXAGONAL;
        if (key.equals("layered") || key.equals("jpa")) return Architecture.LAYERED;
        throw new IllegalArgumentException(
                "Unknown architecture '" + name + "'. Use layered, ddd or hexagonal.");
    }

    /** As above, for the language. */
    public static Language languageNamed(String name) {
        if (name == null) return null;
        String key = name.trim().toLowerCase();
        if (key.isEmpty()) return null;

        if (key.equals("kotlin") || key.equals("kt")) return Language.KOTLIN;
        if (key.equals("typescript") || key.equals("ts")) return Language.TYPESCRIPT;
        if (key.equals("java")) return Language.JAVA;
        throw new IllegalArgumentException(
                "Unknown language '" + name + "'. Use java, kotlin or typescript.");
    }

    /** What each architecture is, in the words the wizard offers it in. */
    public static String describe(Architecture architecture) {
        switch (architecture) {
            case DOMAIN_MODEL:
                return "Domain model - aggregate boundaries, invariants, domain events";
            case HEXAGONAL:
                return "Hexagonal    - the domain model, entered through inbound ports";
            case LAYERED:
            default:
                return "Layered      - entity / dto / repository / service / controller";
        }
    }

    /**
     * The system property that makes a runner write this language.
     *
     * Returned beside the bundle rather than left to the caller, because a
     * bundle and a switch that disagree is the failure this table exists to
     * prevent. Null for Java in compatible mode: nothing to set.
     */
    public static String languageSwitch(Language language) {
        switch (language) {
            case KOTLIN:
                return "elasticore.rules.kt";
            case TYPESCRIPT:
                return "elasticore.rules.ts";
            case JAVA:
            default:
                return null;
        }
    }

    private RulesConfig() {
    }

    /**
     * Builds an engine for one domain.
     *
     * @param catalog        the rule set catalog
     * @param domainRules    env.yml publisher.rules for this domain, or null
     * @param domainSuppress env.yml publisher.suppress for this domain, or null
     */
    public static RuleEngine engineFor(RuleCatalog catalog,
                                       List<String> domainRules,
                                       List<String> domainSuppress) {
        return engineFor(catalog, domainRules, domainSuppress, DEFAULT_BUNDLE);
    }

    /** As above, with the default bundle named explicitly - the v3 caller's way in. */
    public static RuleEngine engineFor(RuleCatalog catalog,
                                       List<String> domainRules,
                                       List<String> domainSuppress,
                                       String defaultBundle) {
        RuleEngine engine = catalog.engineFor(resolveRules(domainRules, defaultBundle));

        for (String id : resolveSuppress(domainSuppress)) {
            engine.suppress(id);
        }
        return engine;
    }

    /**
     * A rule list as env.yml carries it.
     *
     * The config map hands back whatever YAML parsed, stringified - so a
     * list arrives as "[a, b]" and a single value as itself. Both are
     * accepted rather than making a project remember which one this reader
     * wanted; null when nothing was configured, which is what tells the
     * resolution to fall through to the next level.
     */
    public static List<String> configuredList(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        List<String> out = new ArrayList<String>();
        for (String part : text.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) out.add(name);
        }
        return out.isEmpty() ? null : out;
    }

    /** Domain rules win; otherwise the project property; otherwise the default bundle. */
    static String[] resolveRules(List<String> domainRules, String defaultBundle) {
        if (domainRules != null && !domainRules.isEmpty()) {
            return domainRules.toArray(new String[0]);
        }
        String prop = System.getProperty(PROP_RULES);
        if (prop != null && !prop.trim().isEmpty()) {
            return splitCsv(prop);
        }
        return new String[]{defaultBundle};
    }

    /** Suppress lists accumulate: project-level suppressions plus domain-level ones. */
    public static List<String> resolveSuppress(List<String> domainSuppress) {
        List<String> result = new ArrayList<String>();
        String prop = System.getProperty(PROP_SUPPRESS);
        if (prop != null && !prop.trim().isEmpty()) {
            for (String s : splitCsv(prop)) result.add(s);
        }
        if (domainSuppress != null) {
            result.addAll(domainSuppress);
        }
        return result;
    }

    private static String[] splitCsv(String value) {
        String[] parts = value.split(",");
        List<String> out = new ArrayList<String>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out.toArray(new String[0]);
    }
}
