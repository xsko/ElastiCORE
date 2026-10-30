package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.ECoreModelContext;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.extract.FileBasedSourceFileAccessFactory;
import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.pub.rules.ArtifactTarget;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.RuleCatalog;
import io.elasticore.base.model.pub.rules.RuleEngine;
import io.elasticore.base.model.pub.rules.RulesConfig;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.util.FileOrder;
import io.elasticore.base.util.HashUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Source generation through the 3.0 rule pipeline. Driven by the elcore3
 * gradle task, alongside - never replacing - the existing elcore task.
 *
 *   ./gradlew elcore3 [-Pdomain=content] [-Pv3|-Pv2] [-Pforce]
 *
 * Modes (layered Java; DDD, hexagonal and Kotlin are always v3):
 *   default : v3 since 3.0.261028-rc3 - the v3 templates (used-imports only,
 *             rule-emitted class annotations, per-entity method selection);
 *             a project that chose nothing and holds code 2.x generated is
 *             stopped instead (twoXGenerated)
 *   -Pv3    : v3, chosen - also output = 'v3' in the elasticore block
 *   -Pv2    : the 2.x templates, output verified byte-identical by
 *             elcoreRulesCheck - also output = 'v2', or jpa-springboot named
 *             in a domain's env.yml
 *
 * File safety mirrors 2.x writeSrcCode: a file carrying the generate-once
 * marker is never touched, and a file whose //ecd: hash shows developer
 * edits is skipped unless -Pforce is given. Diff-based merging of developer
 * edits is improvement 2 and not wired yet, so skipping is the safe default.
 */
public final class RulesGenRunner {

    private final String projectRoot;
    private final boolean v3;
    private final boolean force;

    /**
     * Kotlin output: the kt bundle, the kt skeletons, and .kt files under
     * src/main/kotlin. Only the artifacts the kt bundle covers are emitted -
     * an artifact whose rules still write Java signatures is skipped rather
     * than written in the wrong language.
     */
    private final boolean kotlin;

    /**
     * TypeScript output: the model a browser sees, as .ts under src/main/ts.
     * Only the data artifacts - a DTO and an enum are the whole surface, since
     * nothing else the DSL declares runs on that side.
     */
    private final boolean typescript;

    /** The house style the build asked for, or none. */
    private final io.elasticore.base.model.pub.rules.CodeStyle style =
            io.elasticore.base.model.pub.rules.CodeStyle.fromSystemProperties();

    /** Module alias -> root path, for a namespace that redirects across modules. */
    private Map<String, String> moduleRoots = java.util.Collections.emptyMap();

    /** Where the domain being generated writes, when the project has modules. */
    private String domainRoot;

    /** The project's contexts, for a process manager that reacts to another context's event. */
    private ContextMap contextMap;

    /** Package prefix -> module alias, as the domain's env.yml declares it. */
    private Map<String, String> packageModuleMap;

    private int wrote = 0;
    private int unchanged = 0;
    private int skipped = 0;

    /**
     * Artifacts that could not be written.
     *
     * Counted apart from a skip because the two mean opposite things: a skip
     * is a decision some rule made about a file, and this is the absence of
     * one. Both used to land in the same number, so a rule set that threw on
     * every entity of a domain ended the run the same way a clean one did -
     * zero exit, a line in the log, and a project missing files nobody was
     * told about.
     */
    private int failed = 0;
    private final List<String> notes = new ArrayList<String>();

    private RulesGenRunner(String projectRoot, boolean v3, boolean force,
                           boolean kotlin, boolean typescript) {
        this.projectRoot = projectRoot;
        this.v3 = v3;
        this.force = force;
        this.kotlin = kotlin;
        this.typescript = typescript;
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        SpecFactory.referenceRequiredByKey(false);
        SpecFactory.compatibleServer(false);
    }

    /**
     * The first domain whose own rule selection exists only in v3, as a line
     * for the log - or null when every domain is content with the 2.x mode.
     */
    static String domainAskingForV3(List<ECoreModel> models) {
        if (models == null) return null;
        for (ECoreModel model : models) {
            java.util.List<String> rules = RulesConfig.configuredList(model.getConfig("publisher.rules"));
            if (rules == null) continue;
            for (String rule : rules) {
                if (needsV3(rule)) {
                    return "domain '" + model.getInternalDomainName() + "' names " + rule;
                }
            }
        }
        return null;
    }

    /**
     * The first domain whose env.yml names the 2.x bundle, jpa-springboot -
     * the initialization wizard wrote it for a layered Java domain before
     * 3.0.261028-rc3 - as a line for the log, or null.
     */
    static String domainKeepingTwoX(List<ECoreModel> models) {
        if (models == null) return null;
        for (ECoreModel model : models) {
            java.util.List<String> rules = RulesConfig.configuredList(model.getConfig("publisher.rules"));
            if (rules == null) continue;
            for (String rule : rules) {
                if (RulesConfig.DEFAULT_BUNDLE.equals(rule.trim())) {
                    return "domain '" + model.getInternalDomainName() + "' names " + RulesConfig.DEFAULT_BUNDLE;
                }
            }
        }
        return null;
    }

    /**
     * A source file a 2.x generator wrote, under any of the roots, or null.
     *
     * Every generated file starts with its hash line, which ends in the
     * version of the generator that last changed it: "//ecd:...H..._V2.1".
     * One from 1.x or 2.x is a project that has run before 3.0. A project
     * that ran a 3.0 release candidate in the 2.x-compatible mode carries
     * "_V3.0" like any other and is not seen here - its env.yml names
     * jpa-springboot when the wizard wrote it (domainKeepingTwoX), and its
     * last run is in build/elasticore/server.mode, which generateAll reads
     * too. A clean build deletes that file: such a project, cleaned and
     * never told, is the one case left that gets the 3.0 code unasked.
     */
    static String twoXGenerated(java.util.Collection<String> roots) {
        for (String root : roots) {
            File sources = new File(root, "src/main/java");
            if (!sources.isDirectory()) continue;
            final String[] found = new String[1];
            try {
                java.nio.file.Files.walkFileTree(sources.toPath(), new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                    @Override
                    public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file,
                            java.nio.file.attribute.BasicFileAttributes attrs) {
                        if (!file.toString().endsWith(".java")) return java.nio.file.FileVisitResult.CONTINUE;
                        String first = firstLine(file.toFile());
                        if (first != null && first.startsWith(String.valueOf((char) 0xFEFF))) first = first.substring(1);
                        if (first != null && first.startsWith("//ecd:")
                                && (first.contains("_V1.") || first.contains("_V2."))) {
                            found[0] = sources.toPath().relativize(file).toString().replace('\\', '/');
                            return java.nio.file.FileVisitResult.TERMINATE;
                        }
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException unreadable) {
                // A tree that cannot be walked holds nothing this can name.
            }
            if (found[0] != null) return found[0];
        }
        return null;
    }

    private static String firstLine(File file) {
        try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            return in.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * A domain rule the DSL states and the selected rules do not carry - an
     * @access on a 2.x bundle, states: without the DDD rules - would be
     * ignored without a word, and a password stored as typed. Said instead.
     */
    private void warnUnenforced(ECoreModel model) {
        boolean access = selectsNamed("ServiceAccessRules");
        boolean states = selectsNamed("DddStateRules");
        boolean derive = selectsNamed("DddDeriveRules");
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        for (int i = 0; i < all.size(); i++) {
            Entity entity = all.get(i);
            String name = entity.getIdentity().getName();
            java.util.Map<String, io.elasticore.base.model.core.Annotation> meta = entity.getMetaInfo().getMetaAnnotationMap();
            boolean wantsAccess = meta != null && (meta.containsKey("access") || meta.containsKey("owner"));
            boolean wantsDerive = false;
            ModelComponentItems<io.elasticore.base.model.entity.Field> fields = entity.getItems();
            for (int j = 0; fields != null && j < fields.size(); j++) {
                io.elasticore.base.model.entity.Field f = fields.get(j);
                if (f.hasAnnotation("password") || f.hasAnnotation("access")) wantsAccess = true;
                if (f.hasAnnotation("snapshot") || f.hasAnnotation("compute")) wantsDerive = true;
            }
            String missing = null;
            if (wantsAccess && !access) missing = "@access, @owner and @password (jpa-service-access)";
            else if (!entity.getStates().isEmpty() && !states) missing = "states: (ddd-state)";
            else if (wantsDerive && !derive) missing = "@snapshot and @compute (ddd-derive)";
            if (missing != null) {
                io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                        "[%s] %s declares %s, which the selected rules do not enforce - nothing is checked, derived"
                                + " or hashed. Declare architecture = 'domain-model' or 'hexagonal', or add the rule set.",
                        model.getInternalDomainName(), name, missing));
            }
        }
    }

    /**
     * A command given a move in states: after its behaviour class was written
     * still has the stub that throws - and the stub overrides the move, so
     * every call is a 500. The class is the developer's; it is said rather
     * than rewritten.
     */
    private void warnStubsOverMoves(List<EntitySpec> specs) {
        for (EntitySpec e : specs) {
            if (e.stateMachines().isEmpty()) continue;
            ArtifactTarget behavior = target(e.pkg("entity"), e.getName());
            File file = new File(rootFor(behavior) + "/" + behavior.relativePath());
            if (!file.isFile()) continue;
            String text;
            try {
                text = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException ex) {
                continue;
            }
            java.util.Set<String> warned = new java.util.HashSet<String>();
            for (EntitySpec.StateMachine m : e.stateMachines()) {
                for (EntitySpec.Move move : m.moves()) {
                    // A lifecycle left out keeps its commands abstract: the stub is then needed.
                    if (!e.lifecycleAccepts(move.command()) || !warned.add(move.command())) continue;
                    if (text.contains(e.getName() + "." + move.command() + ": write the rule")) {
                        io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                                "[%s] %s.%s is a move of states:, but %s still has the stub that throws and"
                                        + " overrides it - delete the stub from %s.",
                                e.domainClassName(), e.getName(), move.command(), e.getName(), behavior.relativePath()));
                    }
                }
            }
        }
    }

    /** Whether the selection includes a rule set of this class name (named, so the runner imports no rule set). */
    private boolean selectsNamed(String simpleName) {
        for (String name : contributingRules()) {
            try {
                for (io.elasticore.base.model.pub.rules.CodeRules rs : RuleCatalog.standard().rulesOf(name)) {
                    if (rs.getClass().getSimpleName().equals(simpleName)) return true;
                }
            } catch (IllegalArgumentException notOnThisClasspath) {
                // a project's own rule set: not one of these
            }
        }
        return false;
    }

    /** Whether a rule name exists only as a v3 reading (the DDD, hexagonal and clean bundles). */
    static boolean needsV3(String rule) {
        if (rule == null) return false;
        String r = rule.trim();
        return r.startsWith("ddd-") || r.startsWith("hexagonal-") || r.startsWith("hex-")
                || r.endsWith("-v3") || r.equals("jpa-write-guard") || r.equals("spring-api-errors")
                || r.equals("jpa-value-object-boundary") || r.equals("jpa-service-access");
    }

    /** Where an artifact of the language being generated goes. */
    private ArtifactTarget target(String packageName, String className) {
        if (typescript) {
            // One flat directory: a module path is a path, and the package
            // a Java type sits in says nothing about where a browser would
            // look for it.
            return ArtifactTarget.of("ts", "src/main/ts", "ts", "", className, false);
        }
        return kotlin
                ? ArtifactTarget.kotlin(packageName, className)
                : ArtifactTarget.java(packageName, className);
    }

    public static void main(String[] args) {
        int status = run(args);
        if (status != 0) System.exit(status);
    }

    /**
     * One generation, and the answer a build acts on: 0, or 1 when the build
     * has to stop. main exits with it. A test calls this instead, so that a
     * model the generator refuses fails the test with the generator's own
     * explanation, rather than ending the JVM every other test runs in.
     */
    static int run(String[] args) {
        // The value types this engine writes and the 2.x generator does not
        // (Instant, UUID, short ...): on for this run, off after it, so a 2.x
        // generation in the same JVM resolves them as it always did.
        io.elasticore.base.model.entity.BaseFieldType.ruleEngineTypes(true);
        try {
            return generateAll(args);
        } finally {
            // A run that ended on an exception has still written what it
            // wrote: the cache follows the files (a no-op when it already has).
            if (merging != null) {
                merging.saveCache();
                merging = null;
            }
            io.elasticore.base.model.entity.BaseFieldType.ruleEngineTypes(false);
            // What this run said of its applications is not the next caller's.
            SpecFactory.applicationSignIns(-1, null);
            // Nor what it said of the server a client talks to.
            SpecFactory.compatibleServer(false);
        }
    }

    private static int generateAll(String[] args) {
        String basePath = System.getProperty("elasticore.base.path");
        if (basePath == null) basePath = System.getProperty("user.dir");

        // What an earlier run in this JVM found is not this run's to report.
        io.elasticore.base.model.pub.rules.ModelCheck.reset();
        io.elasticore.base.model.pub.rules.SourceCode.resetWarnings();
        SpecFactory.applicationSignIns(-1, null);
        io.elasticore.base.model.pub.rules.mybatis.MyBatisTables.forgetOutboxes();
        io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.aggregateBoundaries(null);

        // The generation the build chose: -Pv3 / output = 'v3', or -Pv2 /
        // output = 'v2' for the code 2.x generates. Neither is the default -
        // the 3.0 code since 3.0.261028-rc3 - decided once the model is read.
        boolean v3Asked = Boolean.getBoolean("elasticore.rules.v3");
        boolean v2Asked = Boolean.getBoolean("elasticore.rules.v2");
        if (v3Asked && v2Asked) {
            io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] both generations asked for (-Pv2 and -Pv3, or"
                    + " output and the other switch) - choose one");
            return 1;
        }
        boolean v3 = v3Asked;
        boolean force = Boolean.getBoolean("elasticore.rules.force");
        boolean kotlin = Boolean.getBoolean("elasticore.rules.kt");
        boolean typescript = Boolean.getBoolean("elasticore.rules.ts");

        // A build that declared a language gets the switch derived from it
        // rather than set beside it. The two used to be spelled separately -
        // a bundle naming Kotlin and a property naming the tree - and the
        // pairing that disagrees is what the rule guide warns about and what
        // SourceCode.requireSyntax throws on much later. One word decides it.
        RulesConfig.Language declaredLanguage = RulesConfig.declaredLanguage();
        if (declaredLanguage != null) {
            kotlin = kotlin || declaredLanguage == RulesConfig.Language.KOTLIN;
            typescript = typescript || declaredLanguage == RulesConfig.Language.TYPESCRIPT;
        }
        // The architecture decides the mode the same way the language decides
        // its switch. A domain model and a hexagon exist only as v3 bundles -
        // their skeletons live under v3/ - so declaring either and getting the
        // 2.x mode sent every DDD artifact looking for a template that does
        // not exist. The samples never showed it because their builds set
        // elasticore.rules.v3 by hand; a real project declares two words.
        RulesConfig.Architecture declaredArchitecture = RulesConfig.declaredArchitecture();
        if (declaredArchitecture == RulesConfig.Architecture.DOMAIN_MODEL
                || declaredArchitecture == RulesConfig.Architecture.HEXAGONAL) {
            v3 = true;
        }
        // 2.x's code is layered Java: there is no 2.x domain model, hexagon
        // or Kotlin to keep.
        if (v2Asked && (v3 || kotlin)) {
            io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] -Pv2 / output = 'v2' asks for the code 2.x generates,"
                    + " which is layered Java - not " + (kotlin ? "Kotlin" : "architecture " + declaredArchitecture));
            return 1;
        }
        String domainFilter = System.getProperty("elasticore.domain");

        // A multi-module project keeps its blueprints per module, and names
        // those modules the same way the 2.x extractor is told about them.
        String modulePaths = System.getProperty("elasticore.modules");

        if (typescript || kotlin) {
            System.out.println("[elcore3] mode      : " + (typescript ? "typescript" : "kotlin"));
        }
        System.out.println("[elcore3] project   : " + basePath);

        // The guides go beside the model here as well as in ModelExtractor.
        // A project on a bundle is the one meeting these documents for the
        // first time, and until now it was the only one that never received
        // them - both rule-engine sample modules had an empty blueprint root.
        if (!Boolean.getBoolean("elasticore.rules.list") && captured == null) {
            io.elasticore.base.util.GuideDeployer.deployTo(
                    basePath + "/src/main/resources/blueprint", basePath);
        }
        if (modulePaths != null && !modulePaths.trim().isEmpty()) {
            System.out.println("[elcore3] modules   : " + modulePaths);
        } else {
            System.out.println("[elcore3] blueprint : "
                    + basePath + "/src/main/resources/blueprint");
        }

        io.elasticore.base.model.pub.rules.CodeStyle declared =
                io.elasticore.base.model.pub.rules.CodeStyle.fromSystemProperties();
        System.out.println("[elcore3] style     : "
                + (declared.isAsWritten() ? "as written" : declared.describe()));

        BlueprintLoader loader = (modulePaths != null && !modulePaths.trim().isEmpty())
                ? BlueprintLoader.loadModules(modulePaths)
                : BlueprintLoader.load(basePath + "/src/main/resources/blueprint");
        if (loader.isEmpty()) {
            System.out.println("[elcore3] no blueprint domain found - run ./gradlew elcore to create one,"
                    + " or write src/main/resources/blueprint/<domain>/env.yml and model/ by hand");
            return 1;
        }

        // The checks the other entry point has always run, and this one
        // silently did not. A bundle was a downgrade nobody had announced: a
        // duplicate entity across domains, or a reference to a type that
        // exists nowhere, sailed through here and surfaced as a compile error
        // in generated code - or worse, compiled. Same inputs, same warnings,
        // whichever door the project came in through.
        io.elasticore.base.extract.ModelExtractor.validateGlobalTypeReferences(
                new ArrayList<io.elasticore.base.ECoreModelContext>(loader.contexts().values()));
        RulesConfig.settleSpringBoot(loader.contexts().values());

        // The generation, where the build chose none. A domain that names a
        // v3 bundle in its env.yml gets v3, one that names the 2.x bundle
        // (jpa-springboot) keeps 2.x's code - the rule names say what they
        // need. Otherwise the 3.0 code, the default since 3.0.261028-rc3; but not
        // over a project that holds what 2.x generated: an upgrade that
        // rewrote its sources, and changed how its search and its refusals
        // behave, without the project asking, is stopped and the choice put
        // to it instead.
        // A domain's mode (env.yml config.mode, "jpa" when unsaid) names what the
        // 2.x generator writes for it: jpa code, mongo code, uml and dsl
        // documents. The rule engine writes JPA code only, and read no mode at
        // all: a MongoDB domain got JPA entities and repositories without a
        // word - since 3.0.261028-rc3 the default for a build that says nothing.
        // A domain whose modes leave out jpa is refused; documents beside it
        // are said to be the 2.x generator's.
        // The persistence is one word of that mode (RulesConfig.persistenceOfMode):
        // jpa, or mybatis, which the rule engine writes in Java, in every architecture.
        String mybatisDomain = null;
        for (ECoreModel model : loader.allModels()) {
            String mode = String.valueOf(model.getConfig("mode", "jpa"));
            java.util.List<String> modes = new ArrayList<String>();
            for (String m : mode.split(",")) {
                if (!m.trim().isEmpty()) modes.add(m.trim().toLowerCase());
            }
            RulesConfig.Persistence persistence = RulesConfig.persistenceOfMode(mode);
            if (persistence == null || !persistence.generated()) {
                io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] domain '" + model.getInternalDomainName()
                        + "' is mode: " + String.join(",", modes) + ". The rule engine writes JPA and MyBatis code; "
                        + (modes.contains("mongo") ? "MongoDB code" : "this mode") + " comes from the 2.x generator -"
                        + " elasticore { output = 'v2' }, whose build runs it, or ./gradlew elcore. No source was written.");
                return 1;
            }
            if (persistence == RulesConfig.Persistence.MYBATIS) {
                String refusal = v2Asked
                        ? "the code 2.x generates (-Pv2 / output = 'v2') is JPA code"
                        : RulesConfig.mybatisRefusal(declaredArchitecture,
                                typescript ? RulesConfig.Language.TYPESCRIPT
                                        : kotlin ? RulesConfig.Language.KOTLIN : RulesConfig.Language.JAVA);
                // What the domain's own env.yml names counts as much as the build's word.
                if (refusal == null) {
                    java.util.List<String> named = RulesConfig.configuredList(model.getConfig("publisher.rules"));
                    if (named != null) {
                        // The sets the names stand for, a MyBatis bundle's included.
                        java.util.Set<String> sets = new java.util.HashSet<String>();
                        RuleCatalog catalog = RuleCatalog.standard();
                        for (String rule : named) {
                            String mybatis = RulesConfig.mybatisBundleOf(rule);
                            sets.addAll(catalog.setNamesOf(mybatis != null ? mybatis : rule));
                        }
                        for (String rule : named) {
                            // The architecture bundles are read as their MyBatis ones
                            // (RulesConfig.mybatisBundleOf); the rest are JPA's own.
                            if (RulesConfig.mybatisBundleOf(rule) != null) continue;
                            if (rule.endsWith("-kt") || rule.startsWith("jpa-springboot")) {
                                refusal = "its publisher.rules names " + rule + ", which is JPA code";
                                break;
                            }
                            // The domain rules over MyBatis need what stands in for JPA's callbacks.
                            if ((rule.startsWith("ddd-") || rule.startsWith("hex-")) && !sets.contains("mybatis-ddd")) {
                                refusal = "its publisher.rules names " + rule + " without mybatis-ddd, which turns the"
                                        + " JPA callbacks into the Lifecycle the MyBatis adapter calls"
                                        + " - add mybatis-ddd, or name the architecture's bundle";
                                break;
                            }
                        }
                    }
                }
                if (refusal != null) {
                    io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] domain '" + model.getInternalDomainName()
                            + "' is mode: mybatis, but " + refusal + ". No source was written.");
                    return 1;
                }
                if (mybatisDomain == null) mybatisDomain = model.getInternalDomainName();
            }
            for (String m : modes) {
                if (RulesConfig.persistenceNamed(m) == persistence) continue;
                io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", "[" + model.getInternalDomainName()
                        + "] mode " + m + " is not written by elcore3 - only the "
                        + (persistence == RulesConfig.Persistence.MYBATIS ? "MyBatis" : "JPA") + " code is. "
                        + (RulesConfig.persistenceNamed(m) != null && RulesConfig.persistenceNamed(m).generated()
                                ? "A domain is generated for one persistence."
                                : "The 2.x generator (./gradlew elcore) writes the " + m + " output."));
            }
        }

        // A JPA entity cannot hold a MyBatis one: Hibernate maps a relation only
        // to an @Entity, and the JPA mapper attaches the target as a reference.
        // The other way round - MyBatis holding a JPA entity - reads its key.
        String crossing = jpaReachingMyBatis(loader.allModels());
        if (crossing != null) {
            io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] " + crossing + ". A JPA entity can point only at"
                    + " JPA entities - hold the key as a value (customerId: long), or keep both domains on one"
                    + " persistence. No source was written.");
            return 1;
        }

        String why = v2Asked ? "-Pv2 / output = 'v2'" : v3Asked ? "-Pv3 / output = 'v3'"
                : v3 ? "architecture " + declaredArchitecture : null;
        // 2.x's code is the 2.x bundle on the 2.x templates. A rule that
        // exists only in v3 - named by a domain, or the project's own rule
        // sets - would be rendered on templates it was not written for, or,
        // through the 2.x generator the build then runs, not at all.
        if (v2Asked) {
            String asking = domainAskingForV3(loader.allModels());
            if (asking != null || !RulesConfig.addedRules().isEmpty()) {
                io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] -Pv2 / output = 'v2' asks for the code 2.x"
                        + " generates, but " + (asking != null ? asking : "the build adds rule sets of its own "
                        + RulesConfig.addedRules()) + ", which only the 3.0 code carries - remove one or the other");
                return 1;
            }
        }
        if (!v3 && !v2Asked && !kotlin) {
            String asking = domainAskingForV3(loader.allModels());
            String keeping = asking == null ? domainKeepingTwoX(loader.allModels()) : null;
            java.util.List<String> sourceRoots = new ArrayList<String>(loader.moduleRoots().values());
            if (sourceRoots.isEmpty()) sourceRoots.add(basePath);
            String twoX = asking == null && keeping == null ? twoXGenerated(sourceRoots) : null;
            // A 3.0 pre-release that generated the 2.x-compatible code stamps
            // its files _V3.0 like any other; what its run left in
            // build/elasticore/server.mode is what says so.
            String lastRun = null;
            for (String root : sourceRoots) {
                if ("compatible".equals(readText(serverMode(root)))) lastRun = root;
            }
            boolean listing = Boolean.getBoolean("elasticore.rules.list");
            if (asking != null) {
                v3 = true;
                why = asking;
            } else if (keeping != null) {
                why = keeping;
            } else if ((twoX != null || lastRun != null) && !typescript && !listing) {
                io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] This project holds the code 2.x generates ("
                        + (twoX != null ? twoX : "the last run of elcore3 wrote it: build/elasticore/server.mode")
                        + "). Since 3.0.261028-rc3 the generator writes the 3.0 code unless told otherwise,"
                        + " which would rewrite these sources and change how search, refusals and access behave."
                        + " Choose in build.gradle: elasticore { output = 'v2' } keeps the 2.x code;"
                        + " elasticore { output = 'v3' } moves to the 3.0 code - see elasticore-migration-guide.md."
                        + " No source was written.");
                return 1;
            } else if ((twoX != null || lastRun != null) && typescript) {
                // A client of that server: the server is 2.x's until the project moves.
                why = twoX != null ? "the server holds code 2.x generated" : "the server's last run";
            } else {
                v3 = true;
                why = "the default";
            }
        }
        // MyBatis code is 3.0 code, and a run writes one generation: a domain
        // that keeps 2.x's code (jpa-springboot) beside one that is MyBatis
        // would be rewritten if this run took the 3.0 side for it.
        if (mybatisDomain != null && !v3 && !kotlin && !typescript) {
            io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] domain '" + mybatisDomain + "' is mode: mybatis,"
                    + " which is 3.0 code, but " + (why != null ? why : "the project keeps the code 2.x generates")
                    + " - set elasticore { output = 'v3' } to move the project, or keep the MyBatis domain in a"
                    + " project of its own. No source was written.");
            return 1;
        }
        if (!kotlin) {
            System.out.println("[elcore3] mode      : " + (typescript ? "typescript, for a server in " : "")
                    + (v3 ? "v3" : "2.x-compatible") + (why != null ? " - " + why : ""));
        }

        // The project's own rule sets, checked once before anything is
        // generated: a misspelt class otherwise failed every file in turn.
        for (String name : RulesConfig.addedRules()) {
            try {
                RuleCatalog.standard().rulesOf(name);
            } catch (IllegalArgumentException e) {
                io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] " + e.getMessage());
                return 1;
            } catch (LinkageError e) {
                io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] Rule set '" + name + "' cannot be loaded: " + e
                        + (e instanceof UnsupportedClassVersionError
                                ? " - it is compiled for a newer Java than the generator runs on."
                                : " - a class it uses is not on the generator's classpath; add it to the rules module's dependencies."));
                return 1;
            }
        }

        RulesGenRunner runner = new RulesGenRunner(basePath, v3, force, kotlin, typescript);
        runner.moduleRoots = loader.moduleRoots();
        if (domainFilter != null) {
            // A package a generated domain uses too is not only theirs: its
            // classes this run did not map are stale, not someone else's.
            java.util.Set<String> others = new java.util.HashSet<String>();
            java.util.Set<String> generated = new java.util.HashSet<String>();
            for (String key : loader.contexts().keySet()) {
                boolean left = !domainFilter.contains(loader.domainDirNameOf(key));
                for (ECoreModel m : loader.modelsOf(key)) {
                    for (String ns : new String[]{"entity", "enumeration"}) {
                        String pkg = m.getNamespace(ns);
                        if (pkg != null && !pkg.isEmpty()) (left ? others : generated).add(pkg);
                    }
                }
            }
            others.removeAll(generated);
            runner.otherDomainPackages = others;
        }
        // What was generated last, to merge the developer's edits against. One
        // that cannot be read stops the run - without it an edited file can
        // only be overwritten or left behind.
        if (merges(v3, kotlin)) {
            try {
                runner.cache = GeneratedCache.load(new File(basePath));
                merging = runner;
                // A 2.x project's first merging run: the code 2.x generated is
                // the base of every file the developer edited since.
                java.util.List<String> sourceRoots = new ArrayList<String>(loader.moduleRoots().values());
                if (sourceRoots.isEmpty()) sourceRoots.add(basePath);
                if (!runner.cache.existed() && !kotlin && !typescript && twoXGenerated(sourceRoots) != null) {
                    // Only this run reads them: the cache it leaves says the
                    // project has moved, and the next run would find the other
                    // domains' edited files with no base to merge against.
                    if (domainFilter != null) {
                        io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] The first 3.0 build of a project 2.x"
                                + " generated reads the 2.x code of every domain as the bases of the files edited"
                                + " since - run it without -Pdomain once. No source was written.");
                        merging = null;
                        return 1;
                    }
                    runner.upgradeBases = twoXBases();
                    System.out.println("[elcore3] upgrade   : the code 2.x generates, " + runner.upgradeBases.size()
                            + " file(s), read as the bases of the files edited since");
                    // That run loaded the model into the shared context of its
                    // own, never shaped for 3.0: this one generates from a load
                    // of its own again, as a run without the upgrade would.
                    loader = (modulePaths != null && !modulePaths.trim().isEmpty())
                            ? BlueprintLoader.loadModules(modulePaths)
                            : BlueprintLoader.load(basePath + "/src/main/resources/blueprint");
                    RulesConfig.settleSpringBoot(loader.contexts().values());
                    runner.moduleRoots = loader.moduleRoots();
                }
            } catch (IOException e) {
                io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] " + GeneratedCache.DIR + "/" + GeneratedCache.NAME
                        + " cannot be read (" + e.getMessage() + ") - take it from git again;"
                        + " deleting it keeps every generated file that differs from what is generated now");
                return 1;
            }
        }
        List<ECoreModel> allModels = loader.allModels();
        runner.countSignIns(loader);

        // The bounded contexts the domains declare (config.context), checked
        // before anything is written: a type taken across a boundary nobody
        // opened is the model's problem, said once for the whole project.
        ContextMap contextMap = ContextMap.of(loader);
        for (String w : contextMap.warnings()) io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", w);
        for (String p : contextMap.problems()) io.elasticore.base.model.pub.rules.ModelCheck.fail("[elcore3] context: " + p);
        runner.contextMap = contextMap;
        // After the context map, which reads the model as the DSL wrote it.
        runner.shapeReferences(allModels);

        // The DDL alone (elcoreDdl): for a project whose code another task
        // wrote - the 2.x generator - the tables are read off that code.
        if (Boolean.getBoolean("elasticore.ddl.only")) {
            for (Map.Entry<String, ECoreModelContext> en : loader.contexts().entrySet()) {
                String domainDirName = loader.domainDirNameOf(en.getKey());
                if (domainFilter != null && !domainFilter.contains(domainDirName)) continue;
                runner.domainRoot = loader.moduleRootOf(en.getKey());
                for (ECoreModel m : loader.modelsOf(en.getKey())) {
                    runner.currentModel = m;
                    // A MyBatis domain's tables come from its generated model, which elcore3 builds and writes itself.
                    if (RulesConfig.mybatis(m.getConfig("mode")) && runner.ddlOf(m).enabled()) {
                        io.elasticore.base.model.pub.rules.ModelCheck.fail("[elcore3] ddl: domain " + domainDirName
                                + " is mode: mybatis - its DDL is written by elcore3, not elcoreDdl");
                        continue;
                    }
                    runner.recordDdl(domainDirName, m, new ArrayList<EntitySpec>());
                }
            }
            if (io.elasticore.base.model.pub.rules.ModelCheck.failures().isEmpty()) runner.writeDdl();
            runner.saveCache();
            io.elasticore.base.util.ConsoleLog.printStoredWarnLog("DSL_WARN", "  ");
            for (String f : io.elasticore.base.model.pub.rules.ModelCheck.failures()) {
                io.elasticore.base.util.ConsoleLog.printWarn(f);
            }
            // A schema left with conflict marks, or one that could not be written, is the build's answer too.
            for (String n : runner.notes) System.out.println("  " + n);
            return io.elasticore.base.model.pub.rules.ModelCheck.failures().isEmpty() && runner.succeeded() ? 0 : 1;
        }

        for (Map.Entry<String, ECoreModelContext> en : loader.contexts().entrySet()) {
            String domainDirName = loader.domainDirNameOf(en.getKey());
            if (domainFilter != null && !domainFilter.contains(domainDirName)) continue;
            runner.domainRoot = loader.moduleRootOf(en.getKey());
            runner.generate(domainDirName, loader.modelsOf(en.getKey()), allModels);
        }
        // twoXBases wanted the files' texts only: nothing after this writes for it.
        if (captured != null) return runner.succeeded() ? 0 : 1;
        // A listing wrote nothing, so it must not tidy up either: removeStale
        // would read every generated file as no longer produced.
        if (Boolean.getBoolean("elasticore.rules.list")) return 0;
        runner.writeOrmXml();
        // Nor when the model check failed: a model file that was not read leaves
        // everything it declares unproduced, and those files are not stale - the
        // run stops so the YAML is fixed, and the next one writes them again.
        boolean modelChecked = io.elasticore.base.model.pub.rules.ModelCheck.failures().isEmpty();
        // The DDL reads every domain's tables, and a migration is written once:
        // only from a whole run that succeeded.
        if (domainFilter == null && runner.succeeded() && modelChecked) {
            runner.writeDdl();
            modelChecked = io.elasticore.base.model.pub.rules.ModelCheck.failures().isEmpty();
        }
        if (domainFilter == null && runner.succeeded() && modelChecked) {
            runner.removeStale();
            runner.removeStaleMerged();
            runner.removeUnused();
        }
        // Whatever else happened: the files this run wrote are on disk, and
        // the next run merges against what it wrote into them.
        runner.saveCache();
        if (domainFilter == null && runner.succeeded() && !modelChecked) {
            System.out.println("[elcore3] generated files the DSL no longer produces are kept until the model check passes");
        }
        runner.printSummary();
        java.util.List<String> roots = new ArrayList<String>();
        if (modulePaths != null && !modulePaths.trim().isEmpty()) {
            for (String m : modulePaths.split(",")) if (!m.trim().isEmpty()) roots.add(m.trim());
        } else {
            roots.add(basePath);
        }
        // The client of every domain, made from the model as it is: the
        // server run that follows has nothing to say about it.
        if (typescript && domainFilter == null && runner.succeeded() && modelChecked) {
            for (String root : roots) writeStamp(root);
        }
        // And the server's mode, for a client generated by a run that was not
        // told it: -Pv3 given to elcore3 alone.
        if (!typescript && runner.succeeded()) {
            for (String root : roots) writeText(serverMode(root), v3 || kotlin ? "clean" : "compatible");
        }
        if (!typescript && !Boolean.getBoolean("elasticore.rules.list")
                && !Boolean.getBoolean("elasticore.rules.tsWithBuild")) {
            String stale = staleClient(roots, runner.cachedFiles());
            if (stale != null) io.elasticore.base.util.ConsoleLog.printWarn(stale);
        }

        // What the DSL validation found, said where the person who can fix it
        // is looking. The extractor prints these; a rule-engine project used
        // to get them stored and never shown.
        io.elasticore.base.util.ConsoleLog.printRemainingAsOrphans("DSL_WARN",
                "[elcore3] DSL validation warnings:", "  ", true);

        // The schema every domain contributes to, written beside the model it
        // describes. Every domain is loaded regardless of the filter, so the
        // file reflects the whole project and is safe to regenerate - the same
        // guarantee the extractor gives it.
        writeSchema(loader, basePath, modulePaths);

        // The context map beside it, where a domain declared itself a context.
        if (contextMap.declared()) writeContextMap(contextMap, schemaRootOf(basePath, modulePaths));

        // The Ossie semantic model, where a project asked for it. Beside the
        // schema because it is the same kind of thing: a side-car describing
        // the whole model, regenerated with it, never edited.
        if (io.elasticore.base.model.pub.ossie.OssieSemanticModelExporter.requested(allModels)) {
            io.elasticore.base.model.pub.ossie.OssieSemanticModelExporter.export(
                    new ArrayList<io.elasticore.base.ECoreModelContext>(loader.contexts().values()),
                    schemaRootOf(basePath, modulePaths));
        }

        // A generation error used to be a line in the log the build walked
        // past. It is the build's answer now: the sources a compile is about
        // to run on are not the ones the model describes.
        if (!runner.succeeded()) {
            return 1;
        }
        // A project's model check that refused the model: every file was
        // still generated, so all of its findings were said in this one run.
        java.util.List<String> refused = io.elasticore.base.model.pub.rules.ModelCheck.failures();
        if (!refused.isEmpty()) {
            io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] the model check failed (" + refused.size() + ") - fix these and build again:");
            for (String r : refused) io.elasticore.base.util.ConsoleLog.printWarn("  " + r);
            return 1;
        }
        return 0;
    }

    /**
     * What to say when a project holds a generated TypeScript client made
     * from an older model: src/main/ts is written only by elcore3ts, which a
     * build never ran, so after a model change the client silently lacked
     * the new fields and entities. A client elcore3ts made from the model as
     * it is now (clientStamp) is not stale, whichever task the build runs
     * it from. Null when there is none. Said once.
     */
    static String staleClient(java.util.List<String> roots) {
        return staleClient(roots, java.util.Collections.<String>emptySet());
    }

    /** cached: the files .elasticore/cache holds, by absolute path - a merged client has no stamp. */
    static String staleClient(java.util.List<String> roots, java.util.Set<String> cached) {
        for (String root : roots) {
            if (!holdsGeneratedClient(root, cached)) continue;
            if (modelDigest(root).equals(readStamp(root))) continue;
            return "[elcore3] " + new File(root, "src/main/ts").getPath() + " holds a generated TypeScript client"
                    + " made from an older model - run the elcore3ts task after a model change, or set"
                    + " typescriptClient = true in the elasticore { } block so the build runs it.";
        }
        return null;
    }

    private static boolean holdsGeneratedClient(String root, java.util.Set<String> cached) {
        File[] files = new File(root, "src/main/ts").listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (!f.isFile() || !f.getName().endsWith(".ts")) continue;
            if (cached.contains(absolute(f))) return true;
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                    new java.io.FileInputStream(f), StandardCharsets.UTF_8))) {
                String first = r.readLine();
                if (first != null && first.startsWith("//ecd:")) return true;
            } catch (IOException e) {
                // unreadable: not the generator's
            }
        }
        return false;
    }

    /**
     * Where elcore3ts records the model it made the client from: in the
     * build directory, so nothing the project commits changes, and a clean
     * build forgets it - the next server run then asks for the client once.
     */
    static File clientStamp(String root) {
        return new File(root, "build/elasticore/ts-client.model");
    }

    /** Records that the client under root was made from the model as it is now. */
    static void writeStamp(String root) {
        writeText(clientStamp(root), modelDigest(root));
    }

    private static String readStamp(String root) {
        return readText(clientStamp(root));
    }

    /** Where a server run records whether it rendered the clean or the 2.x-compatible skeletons. */
    static File serverMode(String root) {
        return new File(root, "build/elasticore/server.mode");
    }

    private static void writeText(File file, String text) {
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            java.nio.file.Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // A record for the next run only; without it that run decides as it did before.
        }
    }

    private static String readText(File file) {
        if (file == null || !file.isFile()) return null;
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * A digest of the model the client is made from: every .yml of every
     * domain folder under the blueprint, by path and content (line endings
     * aside). The files at the blueprint root - schema.yml and the guides -
     * are written by the generator and are left out.
     */
    static String modelDigest(String root) {
        File blueprint = new File(root, "src/main/resources/blueprint");
        java.util.List<String> paths = new ArrayList<String>();
        File[] domains = blueprint.listFiles();
        if (domains != null) {
            for (File d : domains) if (d.isDirectory()) collectYaml(d, d.getName(), paths);
        }
        java.util.Collections.sort(paths);
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            // What the build declares beside the model, and the generator itself:
            // a changed architecture, language, added rule set or ElastiCORE
            // version changes the client as a model change does.
            for (String key : new String[]{"elasticore.architecture", "elasticore.language",
                    "elasticore.publisher.rules.add"}) {
                md.update((key + "=" + System.getProperty(key, "")).getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
            }
            md.update(io.elasticore.base.Version.getVersion().getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            for (String p : paths) {
                md.update(p.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
                String text = new String(java.nio.file.Files.readAllBytes(new File(blueprint, p).toPath()),
                        StandardCharsets.UTF_8).replace("\r\n", "\n");
                md.update(text.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static void collectYaml(File dir, String relative, java.util.List<String> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String path = relative + "/" + f.getName();
            if (f.isDirectory()) collectYaml(f, path, out);
            else if (f.getName().endsWith(".yml") || f.getName().endsWith(".yaml")) out.add(path);
        }
    }

    /** context-map.md: the bounded contexts and what crosses between them, drawn in Mermaid. */
    private static void writeContextMap(ContextMap map, String blueprintRoot) {
        File file = new File(blueprintRoot, "context-map.md");
        try {
            String text = map.markdown();
            if (file.isFile()) {
                String existing = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                if (equalsIgnoringLineEndings(existing, text)) return;
            }
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            java.nio.file.Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
            System.out.println("[elcore3] context map: " + file.getPath());
        } catch (IOException e) {
            // A side-car: a stale map is a worse document, not a broken build.
            io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] context-map.md not written: " + e.getMessage());
        }
    }

    /** Where the project-level side-cars go: the same answer for both of them. */
    private static String schemaRootOf(String basePath, String modulePaths) {
        return (modulePaths != null && !modulePaths.trim().isEmpty())
                ? modulePaths.split(",")[0].trim() + "/src/main/resources/blueprint"
                : basePath + "/src/main/resources/blueprint";
    }

    /**
     * schema.yml, from whichever shape the project has.
     *
     * A failure here degrades the schema, not the build: the sources are
     * already on disk and correct, and a stale schema.yml with a warning
     * beats losing the generation over its side-car.
     */
    private static void writeSchema(BlueprintLoader loader, String basePath, String modulePaths) {
        io.elasticore.base.model.pub.schema.SchemaCodePublisher schema =
                new io.elasticore.base.model.pub.schema.SchemaCodePublisher();

        for (io.elasticore.base.ECoreModelContext ctx : loader.contexts().values()) {
            String[] domainNames = ctx.getInternalDomainNames();
            if (domainNames == null) continue;
            for (String domainName : domainNames) {
                io.elasticore.base.ModelDomain domain = ctx.getDomain(domainName);
                if (domain == null) continue;
                try {
                    schema.publish(ctx, domain);
                } catch (Exception e) {
                    io.elasticore.base.util.ConsoleLog.printWarn(
                            "Failed to collect schema: " + domainName + " - " + e.getMessage());
                }
            }
        }

        try {
            if (modulePaths != null && !modulePaths.trim().isEmpty()) {
                schema.generateSchemaFile(modulePaths.split(",")[0].trim());
            } else {
                schema.generateSchemaFileInBlueprintDir(basePath + "/src/main/resources/blueprint");
            }
        } catch (Exception e) {
            io.elasticore.base.util.ConsoleLog.printWarn(
                    "Failed to generate schema.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------

    /** Each model's application: the root its controllers are written to. */
    private final Map<ECoreModel, String> applicationOf = new java.util.IdentityHashMap<ECoreModel, String>();

    /** Application root -> the entities of it that say @signin. */
    private final Map<String, List<String>> signInsByApplication = new java.util.LinkedHashMap<String, List<String>>();

    /** Application root -> its entities with @login and @password that do not say @signin. */
    private final Map<String, List<String>> otherLoginsByApplication = new java.util.LinkedHashMap<String, List<String>>();

    /**
     * Counts @signin per application, once per run. The session belongs to
     * the running application, so one entity of it may sign in through it.
     * A single-module run is one application - every domain together. A
     * multi-module run (elasticore.modules) can generate several, and an
     * application is the module the controllers are written to: the one a
     * redirected control namespace names, else the one holding the
     * blueprint. Two modules that run as one application look like two here;
     * the guides say to give only one of them @signin.
     */
    private void countSignIns(BlueprintLoader loader) {
        for (Map.Entry<String, ECoreModelContext> en : loader.contexts().entrySet()) {
            String moduleRoot = loader.moduleRootOf(en.getKey());
            for (ECoreModel model : loader.modelsOf(en.getKey())) {
                if (applicationOf.containsKey(model)) continue;
                String application = applicationRoot(model, moduleRoot);
                applicationOf.put(model, application);
                listOf(signInsByApplication, application).addAll(SpecFactory.signInEntities(model));
                listOf(otherLoginsByApplication, application).addAll(SpecFactory.otherLoginEntities(model));
            }
        }
        // Each module's sign-in is counted on its own, so two modules that are
        // in fact deployed as one application both sign in - one session with
        // a bare key, Staff 7 read as Customer 7. The generator cannot see the
        // deployment, so it says what it assumed.
        List<String> signingIn = new ArrayList<String>();
        for (List<String> names : signInsByApplication.values()) {
            if (!names.isEmpty()) signingIn.add(String.join(", ", names));
        }
        if (signingIn.size() > 1) {
            io.elasticore.base.model.pub.rules.SourceCode.dslWarnOnce("[elcore3] @signin is counted per module: "
                    + String.join(" and ", signingIn) + " each sign in through their own module - if these modules"
                    + " run as one application, only one of them may say @signin.");
        }
    }

    private String applicationRoot(ECoreModel model, String moduleRoot) {
        String control = model.getNamespace("control");
        Map<String, String> redirect = model.getNamespaceModuleRedirectMap();
        if (control != null && redirect != null) {
            for (Map.Entry<String, String> en : redirect.entrySet()) {
                if (!control.startsWith(en.getKey())) continue;
                String root = moduleRoots.get(en.getValue());
                if (root != null) return root;
            }
        }
        return moduleRoot != null ? moduleRoot : projectRoot;
    }

    private static List<String> listOf(Map<String, List<String>> map, String key) {
        List<String> list = map.get(key);
        if (list == null) {
            list = new ArrayList<String>();
            map.put(key, list);
        }
        return list;
    }

    /**
     * Tells the specs of this model what its application holds: how many
     * entities say @signin, and which others have a @login and a @password -
     * the generated session's bare key would be read as theirs too.
     */
    private void sayApplication(ECoreModel model) {
        String application = applicationOf.get(model);
        if (application == null) {
            SpecFactory.applicationSignIns(-1, null);
            return;
        }
        List<String> signIns = signInsByApplication.get(application);
        List<String> others = otherLoginsByApplication.get(application);
        SpecFactory.applicationSignIns(signIns == null ? 0 : signIns.size(),
                others == null ? java.util.Collections.<String>emptyList() : others);
    }

    /**
     * Another aggregate by its identity, where the rule set that decides
     * references is selected - each model by its own domain's selection, and
     * every model of the run before any domain is generated. A domain reads
     * other domains' models too: an @extend child inherits its parent's
     * fields, and whether the parent checks its references decides whether
     * the child's check overrides it. Rewritten as each domain came up, that
     * answer depended on which domain was generated first, and a -Pdomain run
     * of the child never rewrote the parent: the Kotlin child did not compile
     * and the Java child dropped the parent's check.
     *
     * A TypeScript run takes these from the server's selection: the client
     * describes what the server sends (see serverRules). It is not gated on
     * v3: a TypeScript run never reads the domain's own publisher.rules for
     * that switch, and the server's rules say it. A second rewrite of a model
     * finds nothing left to do.
     */
    private void shapeReferences(List<ECoreModel> models) {
        if (!(v3 || kotlin || typescript)) return;
        ECoreModel saved = currentModel;
        try {
            for (ECoreModel model : models) {
                if (model == null || model.getEntityModels() == null) continue;
                currentModel = model;
                if (shapes(io.elasticore.base.model.pub.rules.ReferencesById.class)) IdReferences.apply(model);
            }
        } finally {
            currentModel = saved;
        }
    }

    /** The value types some entity of the run embeds without requiring them; worked out once, on first use. */
    private java.util.Set<String> optionalEmbeddables;

    /** Models already emitted, by identity: the shared context re-offers them. */
    private final java.util.Set<ECoreModel> generatedModels =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ECoreModel, Boolean>());

    /** The model being generated, for the configuration a rule selection reads. */
    private ECoreModel currentModel;

    private void generate(String domainDirName, List<ECoreModel> models, List<ECoreModel> allModels) {
        if (allModels != null) runModels = allModels;
        System.out.println();
        System.out.println("[" + domainDirName + "]");
        // The refusal codes this domain's rules record as they generate - one
        // catalog for the domain, however many models its blueprint holds.
        io.elasticore.base.model.pub.rules.ErrorCatalog.begin();

        for (ECoreModel model : models) {
            if (!generatedModels.add(model)) continue;
            packageModuleMap = model.getNamespaceModuleRedirectMap();
            currentModel = model;
            // One session per application: @signin is counted over every
            // domain whose controllers land in the same one.
            sayApplication(model);
            if (Boolean.getBoolean("elasticore.rules.list")) {
                listRules(domainDirName);
                continue;
            }
            // Every model's references by identity were rewritten before the
            // first domain was generated (shapeReferences).
            boolean shaped = v3 || kotlin || typescript;
            // Per domain, because the selection is: a value object travels in
            // the DTO only where a rule set makes it immutable - and over
            // MyBatis, whose layered DTO would otherwise leave an embedded
            // value out, so an address could not be sent or read at all.
            SpecFactory.valueObjectsInDto(shaped && (shapes(
                    io.elasticore.base.model.pub.rules.ImmutableValueObjects.class)
                    || (v3 && !kotlin && !typescript && persistence() == RulesConfig.Persistence.MYBATIS)));
            // And a root starts with a class for its behaviour where the
            // rule set that makes one - the one that names entity classes -
            // is selected.
            SpecFactory.rootsSplitByDefault(shaped
                    && shapes(io.elasticore.base.model.pub.rules.EntityClassNamer.class));
            SpecFactory.commandOnlyRoots(shaped
                    && shapes(io.elasticore.base.model.pub.rules.CommandOnlyRoots.class));
            // A client of a 2.x (compatible) server: its controller drops what
            // @restExclude names and nothing more, so the client offers the rest.
            SpecFactory.compatibleServer(typescript && !serverIsClean());
            // A required reference is required through its key in the DTO;
            // 2.x requires the nested object, which only the response fills.
            SpecFactory.referenceRequiredByKey(shaped);
            // Over MyBatis, a member of an aggregate the domain rules bound is
            // written by its root, whatever @service it declares - the rules
            // give it no service, so nothing else would write it.
            // A member is what the model makes one: @aggregate(X), a cascade
            // or a root's @OneToMany - as the domain rules read it.
            java.util.Set<String> boundMembers = null;
            if (v3 && !kotlin && !typescript && persistence() == RulesConfig.Persistence.MYBATIS
                    && selectsNamed("DddAggregateRules")) {
                boundMembers = new java.util.HashSet<String>();
                ModelComponentItems<Entity> all = model.getEntityModels().getItems();
                for (int i = 0; i < all.size(); i++) {
                    String name = all.get(i).getIdentity().getName();
                    String root = SpecFactory.aggregateOfModel(name, model);
                    if (root != null && !root.equals(name)) boundMembers.add(name);
                }
            }
            io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.aggregateBoundaries(boundMembers);

            List<EntitySpec> specs = new ArrayList<EntitySpec>();
            ModelComponentItems<Entity> entities = model.getEntityModels().getItems();
            while (entities.hasNext()) {
                Entity entity = entities.next();
                EntitySpec spec = SpecFactory.fromEntity(entity, model, domainDirName);
                for (ECoreModel m : allModels) {
                    SpecFactory.registerEnums(spec, m);
                }
                SpecFactory.registerAllEmbeddables(spec, allModels);
                // A value type of another domain embedded here optionally, or one
                // of this domain embedded optionally there: the columns are on
                // the value type's class, whichever domain writes it.
                if (optionalEmbeddables == null) optionalEmbeddables = SpecFactory.optionalEmbeddables(allModels);
                SpecFactory.knownOptionalEmbeddables(spec, optionalEmbeddables);
                SpecFactory.registerParentReferencing(spec, model, allModels);
                SpecFactory.collectEntityImports(spec, entity, model);
                specs.add(spec);
                generateEntityArtifacts(entity, spec, model);
            }

            generateDtos(domainDirName, model);
            if (!typescript) {
                warnUnenforced(model);
                warnStubsOverMoves(specs);
                recordDdl(domainDirName, model, specs);
            }

            if (typescript) {
                generateEnums(model);
                emitDomain(SpecFactory.fromDomain(domainDirName, model, specs),
                        "apiSupport", target("", "apiSupport"), "ts-api");
                continue;
            }

            // The rest still write Java from their own rules - EnumBaseRules
            // builds converter bodies, RepositoryBaseRules builds signatures -
            // so a Kotlin run emits only what the kt bundle covers rather than
            // putting Java into a Kotlin source tree. Each joins as its rules
            // learn the language.
            if (kotlin) {
                generateEnums(model);
                generatePorts(model);
                // A .proto is not Java or Kotlin, so it is emitted unchanged.
                generateProtoPorts(model);
                generateKotlinDomainArtifacts(domainDirName, model, specs);
                generateSupportTypes(domainDirName, model, specs);
                continue;
            }

            generateEnums(model);
            generatePorts(model);
            generateProtoPorts(model);
            generateDomainArtifacts(domainDirName, model, specs);
        }
    }

    /**
     * One file per declared DTO. A search DTO is a different artifact with its
     * own template, so which one to write is decided by what the DTO declares
     * itself to be.
     */
    /**
     * Mapper sections of the domain being generated. 2.x walks the entity
     * models first, emitting every forward mapping, then the DTO models,
     * emitting every reverse one, then the searchable pairs - so the halves
     * are kept apart and ordered at assembly.
     */
    private final List<String[]> mapperForwardSections = new ArrayList<String[]>();
    private final List<String> mapperReverseSections = new ArrayList<String>();
    private final List<String> mapperSpecSections = new ArrayList<String>();

    /** A MyBatis domain's search conditions, by search DTO name (MyBatisSearchRules). */
    private final Map<String, String> mybatisSearch = new java.util.HashMap<String, String>();

    /** The entities of a MyBatis domain whose mapper is written with the domain's artifacts. */
    private final List<EntitySpec> mybatisMapped = new ArrayList<EntitySpec>();

    /** Every model of the run, for an entity a relation reaches in another domain. */
    private List<ECoreModel> runModels = new ArrayList<ECoreModel>();

    /** The shadow namespaces of the types the mapper's sections referenced. */
    private final java.util.Set<String> mapperNamespaces = new java.util.HashSet<String>();

    /** How many DTOs of each entity the domain has produced sections for. */
    private final Map<String, Integer> mapperOrdinals = new java.util.HashMap<String, Integer>();

    private void generateDtos(String domainDirName, ECoreModel model) {
        if (model.getDataTransferModels() == null) return;

        ModelComponentItems<io.elasticore.base.model.dto.DataTransfer> dtos =
                model.getDataTransferModels().getItems();
        for (int i = 0; i < dtos.size(); i++) {
            io.elasticore.base.model.dto.DataTransfer dto = dtos.get(i);
            String name = dto.getIdentity().getName();
            EntitySpec spec = SpecFactory.fromDto(dto, model, domainDirName);

            boolean searchable = dto.getMetaInfo().hasMetaAnnotation("searchable");
            ArtifactTarget out = target(model.getNamespace("dto"), name);

            // toSpec takes the search DTO as its parameter, so the mapper's
            // section for it cannot stand where a rule declined to write it.
            boolean searchDtoWritten = false;
            if (searchable) {
                searchDtoWritten = emit(spec, "srchdto", out, "jpa-searchdto",
                        typescript ? "jpa-dto-file-ts"
                                : kotlin ? "jpa-searchdto-file-kt"
                                : v3 ? "jpa-searchdto-file-v3" : "jpa-searchdto-file");
            } else {
                emit(spec, "dto", out, "jpa-dto",
                        typescript ? "jpa-dto-file-ts"
                                : kotlin ? "jpa-dto-file-kt"
                                : v3 ? "jpa-dto-file-v3" : "jpa-dto-file");
            }

            collectMapperSection(spec, dto, model, searchable, searchDtoWritten);

            if (!searchable) generateKeyDto(dto, model, domainDirName);
        }
    }

    /**
     * The key DTO of a templated DTO: the same file with only the key fields.
     * A DTO built from several templates has no single entity to key on, so
     * 2.x skips it - and so does this.
     */
    private void generateKeyDto(io.elasticore.base.model.dto.DataTransfer dto,
                                ECoreModel model, String domainDirName) {
        String template = dto.getMetaInfo().getMetaAnnotationValue("template");
        if (template == null || template.isEmpty() || template.indexOf(',') >= 0) return;

        String keyName = String.format("%sKeyDTO", template);
        EntitySpec keySpec = SpecFactory.fromDto(dto, model, domainDirName);
        keySpec.keyDtoClassName(keyName);

        emit(keySpec, "dto",
                target(model.getNamespace("dto"), keyName),
                "jpa-dto", typescript ? "jpa-dto-file-ts"
                        : kotlin ? "jpa-dto-file-kt"
                        : v3 ? "jpa-dto-file-v3" : "jpa-dto-file");
    }

    private void addMapperNamespace(io.elasticore.base.model.pub.rules.FieldSpec f) {
        if (f.isBaseType()) return;
        String pkg = SpecFactory.packageOfType(f.coreItemType());
        if (pkg != null) mapperNamespaces.add(pkg + ".*");
    }

    /**
     * One mapper section per DTO-entity pair, built the way 2.x builds the
     * domain's mapper: the mapping methods for a templated DTO, the toSpec
     * methods for a searchable one. The sections are collected here and the
     * file is assembled with the domain artifacts.
     */
    /**
     * An engine for one mapper section, carrying the language being written.
     * A section is rendered on its own and then pasted into the domain file,
     * so it needs telling which language it is in - the file it lands in
     * cannot tell it afterwards.
     */
    private RuleEngine sectionEngine(String... ruleSets) {
        RuleEngine engine = new RuleEngine().templateSet(
                kotlin ? SourceCode.TemplateSet.KOTLIN
                        : v3 ? SourceCode.TemplateSet.V3 : SourceCode.TemplateSet.COMPATIBLE);

        // What the domain configured, so a mapper is written by the same
        // selection as everything else it is generated alongside. Without
        // this the mapper was the one artifact no configuration could reach,
        // and a ruleset could state a thing about the model in every file
        // except the one that reads and writes it - the aggregate boundary
        // was declared on the entity and then crossed by the mapper.
        for (io.elasticore.base.model.pub.rules.CodeRules r : configuredSectionRules()) {
            engine.register(r);
        }
        for (String rs : ruleSets) {
            for (io.elasticore.base.model.pub.rules.CodeRules r
                    : RuleCatalog.standard().rulesOf(rs)) {
                engine.register(r);
            }
        }
        // Suppressed here as everywhere else, so -PlistRules and the mapper agree.
        for (String id : RulesConfig.resolveSuppress(domainConfig("publisher.suppress"))) {
            engine.suppress(id);
        }
        return engine;
    }

    /**
     * The configured rule sets a mapper section may take, which is all of
     * them except the ones that decide what a section IS.
     *
     * A section's shell is the caller's to choose - a searchable DTO's
     * section is not the same file text as a templated one's, and the two
     * shells cannot both be registered without writing each other's output.
     * So the shells are filtered out here and named explicitly at the call,
     * exactly as before; everything else the configuration says is honoured.
     *
     * A domain that configures nothing adds nothing, which is what keeps the
     * default output byte-identical.
     */
    private java.util.List<io.elasticore.base.model.pub.rules.CodeRules> configuredSectionRules() {
        java.util.List<io.elasticore.base.model.pub.rules.CodeRules> out =
                new java.util.ArrayList<io.elasticore.base.model.pub.rules.CodeRules>();

        java.util.List<String> names = configuredRules();
        // A domain that names nothing still runs a bundle - the one its build
        // declared, or the v3 / Kotlin default. Reading only env.yml here
        // left the mapper sections without their boundary and write-guard
        // rules whenever the bundle came from the build rather than the
        // domain: members were dropped and refs wrote into other aggregates.
        // The compatible 2.x run stays exactly as it was.
        if (names == null && !typescript
                && (v3 || kotlin || RulesConfig.declaredArchitecture() != null)) {
            names = java.util.Collections.singletonList(defaultBundle());
        }
        // The project's own rule sets reach the mapper too. Only those on a
        // compatible run with nothing configured: adding the whole default
        // bundle there would change what 2.x wrote.
        if (names == null) {
            names = typescript ? null : RulesConfig.addedRules();
            if (names == null || names.isEmpty()) return out;
        } else {
            names = withAddedRules(names);
        }

        for (String name : names) {
            for (io.elasticore.base.model.pub.rules.CodeRules r
                    : RuleCatalog.standard().rulesOf(name)) {
                if (!decidesSectionShape(r)) out.add(r);
            }
        }
        return out;
    }

    /** Whether a rule set is one of the mapper shells the caller picks. */
    private boolean decidesSectionShape(io.elasticore.base.model.pub.rules.CodeRules r) {
        return r instanceof io.elasticore.base.model.pub.rules.base.MapperBaseRules
                || r instanceof io.elasticore.base.model.pub.rules.base.MapperAssembleRules
                || r instanceof io.elasticore.base.model.pub.rules.base.MapperSpecRules
                || r instanceof io.elasticore.base.model.pub.rules.base.MapperSpecShellRules
                || r instanceof io.elasticore.base.model.pub.rules.lang.java.MapperFileRules
                || r instanceof io.elasticore.base.model.pub.rules.lang.kotlin.KotlinMapperAssembleRules
                || r instanceof io.elasticore.base.model.pub.rules.lang.kotlin.KotlinMapperSpecShellRules;
    }

    private void collectMapperSection(EntitySpec spec,
                                      io.elasticore.base.model.dto.DataTransfer dto,
                                      ECoreModel model, boolean searchable,
                                      boolean searchDtoWritten) {
        String entityName = spec.getName();
        if (model.getEntityModels().findByName(entityName) == null) return;

        // Only a DTO that gets reverse list methods takes a name: a search
        // DTO maps through toSpec, so one declared first must not push the
        // entity's own DTO to toBlogPost2List the way it did.
        if (!searchable) {
            Integer seenBefore = mapperOrdinals.get(entityName);
            int ordinal = (seenBefore == null ? 0 : seenBefore) + 1;
            mapperOrdinals.put(entityName, ordinal);
            spec.mapperOrdinal(ordinal);
        }

        // The shadow model of 2.x resolves every referenced type's package.
        for (io.elasticore.base.model.pub.rules.FieldSpec f : spec.dtoFields()) {
            addMapperNamespace(f);
        }
        for (io.elasticore.base.model.pub.rules.FieldSpec f : spec.allFields()) {
            addMapperNamespace(f);
        }

        try {
            if (searchable && persistence() == RulesConfig.Persistence.MYBATIS) {
                // Over MyBatis a search is the mapper XML's WHERE clause: its
                // conditions are kept for the entity's XML, and the mapper
                // class gets no toSpec.
                if (!searchDtoWritten) return;
                SourceCode conditions = sectionEngine("mybatis-search")
                        .run(spec, io.elasticore.base.model.pub.rules.mybatis.MyBatisSearchRules.TARGET);
                mybatisSearch.put(dto.getIdentity().getName(), String.join("\n",
                        conditions.blockLines(io.elasticore.base.model.pub.rules.mybatis.MyBatisSearchRules.BLOCK)));
                return;
            }
            if (searchable) {
                // A search DTO a rule declined has no type to take, so the
                // section that would take it is not written either - and the
                // DTO is still not mapped, since a search DTO never was.
                if (!searchDtoWritten) return;
                SourceCode specCode = sectionEngine("jpa-mapper-spec",
                        kotlin ? "jpa-mapper-spec-shell-kt" : "jpa-mapper-spec-shell")
                        .run(spec, "mapper");
                // A rule may have declined the section - the aggregate reading
                // does, for a DTO of an entity nothing queries on its own.
                String text = specCode.isMake() ? specCode.renderBlock("spec") : "";
                if (!text.isEmpty()) mapperSpecSections.add(text);
            } else {
                SourceCode code = sectionEngine("jpa-mapper",
                        kotlin ? "jpa-mapper-assemble-kt" : "jpa-mapper-assemble")
                        .run(spec, "mapper");
                String fwd = code.renderBlock("forward");
                String rev = code.renderBlock("reverse");
                if (!fwd.isEmpty()) mapperForwardSections.add(new String[]{entityName, fwd});
                if (!rev.isEmpty()) mapperReverseSections.add(rev);
            }
        } catch (RuntimeException e) {
            // A section that threw is mapping code the mapper will not carry,
            // and nothing downstream is missing a file to notice it by.
            failed++;
            notes.add(entityName + " mapper section: " + e);
        }
    }

    /**
     * One interface per declared transaction port. A proto port is a
     * different artifact and its rule set declines to write this one.
     */
    private void generatePorts(ECoreModel model) {
        if (model.getPortModels() == null) return;
        if (model.getNamespace("port") == null) return;

        ModelComponentItems<io.elasticore.base.model.port.Port> ports =
                model.getPortModels().getItems();
        List<io.elasticore.base.model.pub.rules.PortSpec> springPorts =
                new ArrayList<io.elasticore.base.model.pub.rules.PortSpec>();
        for (int i = 0; i < ports.size(); i++) {
            io.elasticore.base.model.port.Port port = ports.get(i);
            String name = port.getIdentity().getName();
            io.elasticore.base.model.pub.rules.PortSpec spec = SpecFactory.fromPort(port, model);
            emitPort(spec, target(model.getNamespace("port"), name));
            if (spec.springHttp() && !spec.operations().isEmpty() && !typescript) springPorts.add(spec);
            // A database port of a MyBatis domain runs on MyBatis: a mapper, its XML and the adapter.
            if (v3 && !kotlin && !typescript
                    && io.elasticore.base.model.pub.rules.mybatis.MyBatisPortRules.applies(spec)) {
                emitMyBatisPort(spec);
            }
        }
        if (!springPorts.isEmpty()) emitSpringHttpServices(model, springPorts);

        // Where a properties file of Spring ports would be, looked at by
        // removeStale whether or not this run wrote one: turning httpPort:
        // spring off again must take the file away with the ports.
        if (ports.size() > 0 && !typescript) {
            String domainName = ports.get(0).getIdentity().getDomainId();
            ArtifactTarget props = ArtifactTarget.of("resource", "src/main/resources", "properties",
                    model.getNamespace("port"),
                    io.elasticore.base.model.pub.rules.base.SpringHttpPorts.propertiesName(domainName), true);
            File dir = new File(rootFor(props) + "/" + props.relativePath()).getParentFile();
            if (dir != null) generatedDirs.add(dir);
        }
    }

    /**
     * What registers a domain's Spring HTTP ports (httpPort: spring): the
     * configuration class beside them and the properties file with each
     * group's base URL and timeouts.
     */
    private void emitSpringHttpServices(ECoreModel model, List<io.elasticore.base.model.pub.rules.PortSpec> ports) {
        io.elasticore.base.model.pub.rules.PortSpec first = ports.get(0);
        String className = io.elasticore.base.model.pub.rules.base.SpringHttpPorts.configClassName(first.domainClassName());
        String domainName = first.id().substring(0, first.id().indexOf('.'));
        String propertiesName = io.elasticore.base.model.pub.rules.base.SpringHttpPorts.propertiesName(domainName);

        for (io.elasticore.base.model.pub.rules.PortSpec p : ports) {
            for (String what : io.elasticore.base.model.pub.rules.base.SpringHttpPorts.unsupported(p)) {
                io.elasticore.base.util.ConsoleLog.printWarn(String.format(
                        "[elcore3] %s: %s has no Spring HTTP client setting and is not applied (httpPort: spring)",
                        p.id(), what));
            }
        }

        ArtifactTarget config = target(model.getNamespace("port"), className);
        try {
            write(String.format("    %-12s %s", "httpServices", config.relativePath()), config,
                    io.elasticore.base.model.pub.rules.base.SpringHttpPorts.configuration(
                            kotlin ? io.elasticore.base.model.pub.rules.Syntax.KOTLIN
                                    : io.elasticore.base.model.pub.rules.Syntax.JAVA,
                            model.getNamespace("port"), className, propertiesName, ports));
        } catch (RuntimeException e) {
            failed(String.format("    %-12s %s", "httpServices", config.relativePath()), config, e);
        }

        // Beside the configuration class's package, which is where its @PropertySource looks.
        ArtifactTarget props = ArtifactTarget.of("resource", "src/main/resources", "properties",
                model.getNamespace("port"), propertiesName, true);
        try {
            write(String.format("    %-12s %s", "httpServices", props.relativePath()), props,
                    io.elasticore.base.model.pub.rules.base.SpringHttpPorts.properties(domainName, ports));
        } catch (RuntimeException e) {
            failed(String.format("    %-12s %s", "httpServices", props.relativePath()), props, e);
        }
    }

    /** A database port of a MyBatis domain: its mapper, the mapper's XML, and the adapter that implements the port. */
    private void emitMyBatisPort(io.elasticore.base.model.pub.rules.PortSpec spec) {
        String pkg = io.elasticore.base.model.pub.rules.mybatis.MyBatisPortRules.packageOf(spec);
        String name = spec.getName();
        emitPortArtifact(spec, io.elasticore.base.model.pub.rules.mybatis.MyBatisPortRules.MAPPER,
                ArtifactTarget.java(pkg, name + "Mapper"));
        emitPortArtifact(spec, io.elasticore.base.model.pub.rules.mybatis.MyBatisPortRules.XML,
                ArtifactTarget.resource("src/main/resources", "xml", pkg, name + "Mapper"));
        emitPortArtifact(spec, io.elasticore.base.model.pub.rules.mybatis.MyBatisPortRules.ADAPTER,
                ArtifactTarget.java(pkg, name + "Adapter"));
    }

    private void emitPortArtifact(io.elasticore.base.model.pub.rules.PortSpec spec, String targetName, ArtifactTarget out) {
        String label = String.format("    %-12s %s", targetName, out.relativePath());
        try {
            SourceCode code = engine("mybatis-port").run(spec, targetName);
            if (!code.isMake()) {
                skipped++;
                System.out.println(label + "  SKIP(dontMake)");
                return;
            }
            write(label, out, code.render());
        } catch (RuntimeException e) {
            failed(label, out, e);
        }
    }

    private void emitPort(io.elasticore.base.model.pub.rules.PortSpec spec, ArtifactTarget out) {
        String label = String.format("    %-12s %s", "port", out.relativePath());
        try {
            SourceCode code = engine("port").run(spec, "port");
            if (!code.isMake()) {
                skipped++;
                System.out.println(label + "  SKIP(dontMake)");
                return;
            }
            write(label, out, code.render());
        } catch (RuntimeException e) {
            failed(label, out, e);
        }
    }

    /**
     * One .proto file per declared gRPC port (meta type=proto). Every other
     * port kind is Java; this one is not, so it bypasses the "port" rule set
     * entirely rather than filling one of its slots.
     */
    private void generateProtoPorts(ECoreModel model) {
        if (model.getPortModels() == null) return;
        if (model.getNamespace("proto") == null) return;

        ModelComponentItems<io.elasticore.base.model.port.Port> ports =
                model.getPortModels().getItems();
        for (int i = 0; i < ports.size(); i++) {
            io.elasticore.base.model.port.Port port = ports.get(i);
            String type = port.getMetaInfo().getMetaAnnotationValue("type");
            if (!"proto".equals(type)) continue;

            io.elasticore.base.model.pub.rules.ProtoServiceSpec spec =
                    SpecFactory.fromProtoService(port, model);
            emitProto(spec, spec.target());
            // The stubs the .proto's service needs on this side of the wire.
            emitProtoStub(spec, "grpc_svc_provider", "Provider");
            emitProtoStub(spec, "grpc_svc_client", "Client");
        }
    }

    /**
     * One gRPC stub of a proto service. The .proto is language-neutral; these
     * are source in the language being generated, so they go to its tree.
     */
    private void emitProtoStub(io.elasticore.base.model.pub.rules.ProtoServiceSpec spec,
                               String artifact, String suffix) {
        ArtifactTarget out = target(spec.javaPackage(), spec.getName() + suffix);
        String label = String.format("    %-12s %s", "grpc", out.relativePath());
        try {
            SourceCode code = engine("grpc-stub").run(spec, artifact);
            if (!code.isMake()) {
                System.out.println(label + "  SKIP(dontMake)");
                return;
            }
            write(label, out, code.render());
        } catch (RuntimeException e) {
            failed(label, out, e);
        }
    }

    private void emitProto(io.elasticore.base.model.pub.rules.ProtoServiceSpec spec, ArtifactTarget out) {
        String label = String.format("    %-12s %s", "proto", out.relativePath());
        try {
            SourceCode code = engine("proto").run(spec, "proto");
            if (!code.isMake()) {
                skipped++;
                System.out.println(label + "  SKIP(dontMake)");
                return;
            }
            write(label, out, code.render());
        } catch (RuntimeException e) {
            failed(label, out, e);
        }
    }

    /** One file per declared enumeration. */
    private void generateEnums(ECoreModel model) {
        if (model.getEnumModels() == null) return;

        ModelComponentItems<io.elasticore.base.model.enums.EnumModel> enums =
                model.getEnumModels().getItems();
        for (int i = 0; i < enums.size(); i++) {
            io.elasticore.base.model.enums.EnumModel em = enums.get(i);
            String name = em.getIdentity().getName();

            emitEnum(SpecFactory.fromEnum(em, model), name,
                    target(model.getNamespace("enumeration"), name));
        }
    }

    /** The artifacts a domain has one of, rather than one per entity. */
    private void generateDomainArtifacts(String domainDirName, ECoreModel model,
                                         List<EntitySpec> specs) {
        if (specs.isEmpty()) {
            warnNothingToHang(domainDirName, model);
            return;
        }

        io.elasticore.base.model.pub.rules.DomainSpec domain =
                SpecFactory.fromDomain(domainDirName, model, specs);

        System.out.println("  [" + domainDirName + " domain artifacts]");

        boolean mybatis = persistence() == RulesConfig.Persistence.MYBATIS;
        // Where a MyBatis run writes its XML and schema, looked at whichever
        // persistence this run writes: a domain moved back to JPA must not
        // leave a mapper behind that MyBatis would still bind. Only stamped,
        // never edited files go (removeStale).
        for (ArtifactTarget where : new ArtifactTarget[]{
                ArtifactTarget.resource("src/main/resources", "xml", model.getNamespace("repository"), "x"),
                ArtifactTarget.resource("src/main/resources", "sql", "db", "x")}) {
            File dir = new File(rootFor(where) + "/" + where.relativePath()).getParentFile();
            if (dir != null && dir.isDirectory()) generatedDirs.add(dir);
        }
        // Q is the JPA Criteria's metadata; a MyBatis domain has none.
        if (!mybatis) emitDomain(domain, "Q", ArtifactTarget.java(queryPackage(model), "Q"), "jpa-q");
        else emitMyBatisMappers(domain, model, specs);

        // The domain's mapper, in 2.x emission order: the forward mappings
        // follow the entity models' order, the reverse ones the DTO models',
        // and the toSpec sections close the file.
        // Sections are separated by an indented blank line on either side:
        // one before the first, two between neighbors, none after the last.
        assembleMapper(domain, model);
        emitDomain(domain, "repositoryHelper",
                ArtifactTarget.java(model.getNamespace("repository"),
                        String.format("%sRepositoryHelper", domain.className())),
                "jpa-repository-helper");
        emitContributedDomain(domain, model);
        generateSupportTypes(domainDirName, model, specs);
        emitArchitectureTest(domain, model);
    }

    /**
     * A MyBatis domain's mappers - the interface beside the service's other
     * dependencies, the XML under the same package in the resources - and the
     * domain's schema. Written after the DTOs, whose search DTO says which
     * conditions the XML's WHERE clause holds.
     */
    private void emitMyBatisMappers(io.elasticore.base.model.pub.rules.DomainSpec domain, ECoreModel model,
                                    List<EntitySpec> specs) {
        for (EntitySpec spec : specs) {
            relateMyBatis(spec, specs);
        }
        // The collections: the entity a collection holds gets the statements
        // that read them for a list of keys and delete them with their root,
        // and a mapper of its own when it has no service - an order's lines.
        Map<EntitySpec, List<String>> inbound = new java.util.LinkedHashMap<EntitySpec, List<String>>();
        for (EntitySpec spec : specs) {
            for (io.elasticore.base.model.pub.rules.FieldSpec f
                    : io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.collections(spec)) {
                io.elasticore.base.model.pub.rules.FieldSpec back =
                        io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.backField(spec, f);
                EntitySpec target = null;
                for (EntitySpec s : specs) {
                    if (s.getName().equals(f.coreItemType())) target = s;
                }
                // A collection of another domain's rows is that domain's mapper to read.
                if (target == null) {
                    io.elasticore.base.model.pub.rules.ModelCheck.fail(String.format(
                            "[elcore3] %s.%s: mode: mybatis reads a collection of the domain's own entities;"
                                    + " %s is not one", spec.domainClassName(), spec.getName() + "." + f.name(),
                            f.coreItemType()));
                    continue;
                }
                List<String> backs = inbound.get(target);
                if (backs == null) inbound.put(target, backs = new ArrayList<String>());
                if (!backs.contains(back.name())) backs.add(back.name());
                if (!mybatisMapped.contains(target)) {
                    target.config(io.elasticore.base.model.pub.rules.mybatis.MyBatisMapperRules.SERVICED_CONFIG, "false");
                    mybatisMapped.add(target);
                }
            }
        }
        // A member a root holds by a to-one relation: its mapper too, for the
        // root's adapter to write it with the root (MyBatisSql.isMemberReference).
        for (EntitySpec spec : specs) {
            for (io.elasticore.base.model.pub.rules.FieldSpec f
                    : io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.fields(spec)) {
                if (!io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.isMemberReference(spec, f)) continue;
                for (EntitySpec s : specs) {
                    if (!s.getName().equals(f.coreItemType()) || mybatisMapped.contains(s)) continue;
                    s.config(io.elasticore.base.model.pub.rules.mybatis.MyBatisMapperRules.SERVICED_CONFIG, "false");
                    mybatisMapped.add(s);
                }
            }
        }
        for (Map.Entry<EntitySpec, List<String>> in : inbound.entrySet()) {
            in.getKey().config(io.elasticore.base.model.pub.rules.mybatis.MyBatisMapperRules.INBOUND_CONFIG,
                    String.join("\n", in.getValue()));
        }
        for (EntitySpec spec : mybatisMapped) {
            spec.config(io.elasticore.base.model.pub.rules.mybatis.MyBatisMapperRules.SEARCH_CONFIG,
                    mybatisSearch.get(spec.searchDtoClassName()));
            String name = spec.getName() + "Repository";
            emit(spec, io.elasticore.base.model.pub.rules.mybatis.MyBatisMapperRules.REPOSITORY,
                    ArtifactTarget.java(model.getNamespace("repository"), name), "mybatis-mapper");
            emit(spec, io.elasticore.base.model.pub.rules.mybatis.MyBatisMapperRules.XML,
                    ArtifactTarget.resource("src/main/resources", "xml",
                            model.getNamespace("repository"), name), "mybatis-mapper");
        }
        mybatisMapped.clear();
        mybatisSearch.clear();
        // The tables to start on, for H2 - unless the project's DDL writes
        // this domain's tables, for every database it names (writeDdl).
        if (!ddlOf(model).enabled()) {
            emitDomain(domain, io.elasticore.base.model.pub.rules.mybatis.MyBatisSchemaRules.TARGET,
                    ArtifactTarget.resource("src/main/resources", "sql", "db", domain.getName() + "-schema"),
                    "mybatis-schema");
        }
    }

    /**
     * Hands an entity the entities its relations point at, and theirs in
     * turn: the mapper reads the joined row's columns and the keys of the
     * rows that one points at, an embedded value's columns and the rows of a
     * collection. One of another domain is built from its model.
     */
    private void relateMyBatis(EntitySpec spec, List<EntitySpec> specs) {
        relateMyBatis(spec, specs, new java.util.HashSet<EntitySpec>());
    }

    private void relateMyBatis(EntitySpec spec, List<EntitySpec> specs, java.util.Set<EntitySpec> seen) {
        // Each spec once: relations may run in a cycle (a department's parent).
        if (!seen.add(spec)) return;
        // The class it extends first: an @abstract one's columns are the
        // row's, and its relations are read with the row like the entity's own.
        String parent = spec.parentName() == null ? null : spec.parentName().trim();
        if (parent != null && !parent.isEmpty() && !parent.equals(spec.getName())) {
            EntitySpec own = null;
            for (EntitySpec s : specs) {
                if (s.getName().equals(parent)) own = s;
            }
            EntitySpec known = spec.related(parent);
            if (known == null || (own != null && known != own)) {
                EntitySpec target = own != null ? own : ownModelSpec(parent);
                if (target == null) target = foreignSpec(parent);
                if (target != null) {
                    spec.knownRelated(parent, target);
                    relateMyBatis(target, specs, seen);
                }
            } else {
                relateMyBatis(known, specs, seen);
            }
        }
        for (io.elasticore.base.model.pub.rules.FieldSpec f
                : io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.fields(spec)) {
            if (!f.isEntityRef() && !io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.isEmbedded(spec, f)) continue;
            String type = f.coreItemType();
            EntitySpec own = null;
            for (EntitySpec s : specs) {
                if (s.getName().equals(type)) own = s;
            }
            // One related while the entity was written (relateMyBatisEarly)
            // was built from the model alone; the domain's own spec, which
            // knows the run's enums, takes its place.
            EntitySpec known = spec.related(type);
            if (known != null && (own == null || known == own)) {
                // Its own relations still have to be known - one related early
                // (relateMyBatisEarly) was not walked further.
                if (known != spec) relateMyBatis(known, specs, seen);
                continue;
            }
            EntitySpec target = own != null ? own : foreignSpec(type);
            if (target == null) continue;
            spec.knownRelated(type, target);
            if (target != spec) relateMyBatis(target, specs, seen);
        }
    }

    /**
     * Hands an entity being written the types of its embedded values and
     * collections, so what MyBatis cannot write of them is refused before
     * its files are: the domain's later entities have no spec yet, so these
     * are built from the model, and relateMyBatis replaces them.
     */
    private void relateMyBatisEarly(EntitySpec spec) {
        // The class it extends, and the one that extends in turn: whether it
        // is @abstract decides whether its columns are this row's.
        String parent = spec.parentName() == null ? null : spec.parentName().trim();
        if (parent != null && !parent.isEmpty() && !parent.equals(spec.getName()) && spec.related(parent) == null) {
            EntitySpec target = ownModelSpec(parent);
            if (target == null) target = foreignSpec(parent);
            if (target != null) {
                spec.knownRelated(parent, target);
                relateMyBatisEarly(target);
            }
        }
        for (io.elasticore.base.model.pub.rules.FieldSpec f
                : io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.fields(spec)) {
            boolean embedded = io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.isEmbedded(spec, f);
            // An embedded type's parts, a collection's rows, and a to-one's
            // target (whether it holds this row's key, mappedBy).
            if (!embedded && !f.isEntityRef()) continue;
            if (spec.related(f.coreItemType()) != null) continue;
            EntitySpec target = ownModelSpec(f.coreItemType());
            if (target == null) target = foreignSpec(f.coreItemType());
            if (target != null) {
                spec.knownRelated(f.coreItemType(), target);
                // A member written with this row has the columns of what it extends too.
                relateParentEarly(target);
            }
        }
    }

    /** The classes a related entity extends, for the columns of its row. */
    private void relateParentEarly(EntitySpec spec) {
        String parent = spec.parentName() == null ? null : spec.parentName().trim();
        if (parent != null && !parent.isEmpty() && !parent.equals(spec.getName()) && spec.related(parent) == null) {
            EntitySpec target = ownModelSpec(parent);
            if (target == null) target = foreignSpec(parent);
            if (target != null) {
                spec.knownRelated(parent, target);
                relateParentEarly(target);
            }
        }
    }

    // ---- the project's DDL ------------------------------------------------------------

    /** The project's DDL setting (the build's elasticore { ddl }), read once. */
    private io.elasticore.base.model.pub.rules.schema.DdlConfig projectDdl;
    private final List<DdlRun.Part> ddlParts = new ArrayList<DdlRun.Part>();

    private io.elasticore.base.model.pub.rules.schema.DdlConfig projectDdl() {
        if (projectDdl == null) {
            try {
                projectDdl = io.elasticore.base.model.pub.rules.schema.DdlConfig.global();
            } catch (IllegalArgumentException e) {
                io.elasticore.base.model.pub.rules.ModelCheck.fail("[elcore3] " + e.getMessage());
                projectDdl = io.elasticore.base.model.pub.rules.schema.DdlConfig.OFF;
            }
        }
        return projectDdl;
    }

    /** A domain's DDL: the project's, with what its env.yml says instead. */
    private io.elasticore.base.model.pub.rules.schema.DdlConfig ddlOf(final ECoreModel model) {
        try {
            return io.elasticore.base.model.pub.rules.schema.DdlConfig.forDomain(projectDdl(), model::getConfig);
        } catch (IllegalArgumentException e) {
            io.elasticore.base.model.pub.rules.ModelCheck.fail("[elcore3] " + model.getInternalDomainName() + ": " + e.getMessage());
            return io.elasticore.base.model.pub.rules.schema.DdlConfig.OFF;
        }
    }

    /** Remembers the domain for the DDL written once every domain is generated. */
    private void recordDdl(String domainDirName, ECoreModel model, List<EntitySpec> specs) {
        // Every domain, the ones the DDL leaves out too: their entities are theirs, not "outside every domain".
        io.elasticore.base.model.pub.rules.schema.DdlConfig ddl = ddlOf(model);
        boolean mybatis = RulesConfig.mybatis(model.getConfig("mode"));
        String root = domainRoot != null ? domainRoot : projectRoot;
        ddlParts.add(new DdlRun.Part(domainDirName, ddl, root, mybatis ? specs : null, model.getNamespace("entity")));
    }

    /** The project's DDL: run when every domain has been generated and the model check passed. */
    private void writeDdl() {
        boolean any = false;
        for (DdlRun.Part p : ddlParts) any |= p.config.enabled();
        if (!any) return;
        java.util.Set<String> sources = new java.util.LinkedHashSet<String>();
        sources.add(projectRoot);
        if (moduleRoots != null) sources.addAll(moduleRoots.values());
        for (DdlRun.Part p : ddlParts) sources.add(p.root);
        final Map<String, String> savedModules = packageModuleMap;
        final String savedRoot = domainRoot;
        DdlRun run = new DdlRun(ddlParts, projectRoot, new ArrayList<String>(sources), (root, dir, name, ext, content) -> {
            // Under the root the domain is written to, whatever module the last one was.
            packageModuleMap = null;
            domainRoot = root;
            ArtifactTarget out = ArtifactTarget.resource("src/main/resources", ext, dir.replace('/', '.'), name);
            File d = new File(root + "/" + out.relativePath()).getParentFile();
            if (d != null) generatedDirs.add(d);
            write(String.format("    %-12s %s", "ddl", out.relativePath()), out, content);
        });
        try {
            run.run(projectDdl());
        } catch (IOException e) {
            io.elasticore.base.model.pub.rules.ModelCheck.fail("[elcore3] ddl: " + e.getMessage());
        } finally {
            packageModuleMap = savedModules;
            domainRoot = savedRoot;
        }
        for (String m : run.messages()) io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", m);
        for (String p : run.problems()) io.elasticore.base.model.pub.rules.ModelCheck.fail(p);
    }

    /** Specs ownModelSpec built, per model and entity name. */
    private final Map<String, EntitySpec> ownModelSpecs = new java.util.HashMap<String, EntitySpec>();

    /**
     * An entity of the model being written, as a spec built from that model -
     * so a type of the same name in another domain of the run is not the one
     * read.
     */
    private EntitySpec ownModelSpec(String entityName) {
        if (currentModel == null || currentModel.getEntityModels() == null) return null;
        String key = currentModel.getInternalDomainName() + "/" + entityName;
        if (ownModelSpecs.containsKey(key)) return ownModelSpecs.get(key);
        Entity entity = currentModel.getEntityModels().findByName(entityName);
        EntitySpec spec = entity == null ? null
                : SpecFactory.fromEntity(entity, currentModel, currentModel.getInternalDomainName());
        ownModelSpecs.put(key, spec);
        return spec;
    }

    /**
     * "domain crm: Order.customer is Customer of MyBatis domain shop" for the
     * first relation of a JPA domain's entity to an entity of a MyBatis one,
     * or null when there is none.
     */
    static String jpaReachingMyBatis(List<ECoreModel> models) {
        if (models == null) return null;
        Map<String, String> mybatisEntities = new java.util.HashMap<String, String>();
        for (ECoreModel m : models) {
            if (m.getEntityModels() == null || !RulesConfig.mybatis(m.getConfig("mode"))) continue;
            ModelComponentItems<Entity> items = m.getEntityModels().getItems();
            for (int i = 0; i < items.size(); i++) {
                mybatisEntities.put(items.get(i).getIdentity().getName(), m.getInternalDomainName());
            }
        }
        if (mybatisEntities.isEmpty()) return null;
        for (ECoreModel m : models) {
            if (m.getEntityModels() == null || RulesConfig.mybatis(m.getConfig("mode"))) continue;
            ModelComponentItems<Entity> items = m.getEntityModels().getItems();
            for (int i = 0; i < items.size(); i++) {
                Entity entity = items.get(i);
                ModelComponentItems<io.elasticore.base.model.entity.Field> fields = entity.getItems();
                for (int j = 0; j < fields.size(); j++) {
                    io.elasticore.base.model.entity.Field f = fields.get(j);
                    if (f.getTypeInfo() == null || f.getTypeInfo().isBaseType()) continue;
                    String target = f.getTypeInfo().getCoreItemType();
                    String domain = mybatisEntities.get(target);
                    if (domain != null) {
                        return "domain " + m.getInternalDomainName() + ": " + entity.getIdentity().getName() + "."
                                + f.getName() + " is " + target + " of the MyBatis domain " + domain;
                    }
                }
            }
        }
        return null;
    }

    /** The specs foreignSpec built, once per entity for the run: a cycle among them ends. */
    private final Map<String, EntitySpec> foreignSpecs = new java.util.HashMap<String, EntitySpec>();

    /** An entity of another domain of the run, as a spec; null when none is called that. */
    private EntitySpec foreignSpec(String entityName) {
        if (foreignSpecs.containsKey(entityName)) return foreignSpecs.get(entityName);
        EntitySpec found = null;
        for (ECoreModel m : runModels) {
            if (m.getEntityModels() == null) continue;
            Entity entity = m.getEntityModels().findByName(entityName);
            if (entity != null) {
                found = SpecFactory.fromEntity(entity, m, m.getInternalDomainName());
                break;
            }
        }
        foreignSpecs.put(entityName, found);
        return found;
    }

    /** archTest: true - the hexagon's boundaries as an ArchUnit test, written once. */
    private void emitArchitectureTest(io.elasticore.base.model.pub.rules.DomainSpec domain, ECoreModel model) {
        if (typescript || !"true".equalsIgnoreCase(model.getConfig("archTest", "false"))) return;
        String pkg = ArchitectureTestSource.basePackage(model);
        if (pkg == null) return;
        String name = domain.className() + "ArchitectureTest";
        ArtifactTarget out = kotlin
                ? ArtifactTarget.of("kotlin", "src/test/kotlin", "kt", pkg, name, true)
                : ArtifactTarget.of("java", "src/test/java", "java", pkg, name, true);
        String label = String.format("    %-12s %s", "archTest", out.relativePath());
        write(label, out, ArchitectureTestSource.render(model, name, pkg, kotlin, xmlMapping()));
    }


    /**
     * The domain's mapper, in 2.x emission order: the forward mappings follow
     * the entity models' order, the reverse ones the DTO models', and the
     * toSpec sections close the file. Sections are separated by an indented
     * blank line on either side: one before the first, two between neighbors,
     * none after the last.
     *
     * Both languages call this: the per-field lines were written through the
     * seam, so the collected sections are the same either way.
     */
    private void assembleMapper(io.elasticore.base.model.pub.rules.DomainSpec domain,
                                ECoreModel model) {
        List<String> ordered = new ArrayList<String>();
        ModelComponentItems<Entity> order = model.getEntityModels().getItems();
        for (int i = 0; i < order.size(); i++) {
            String entityName = order.get(i).getIdentity().getName();
            for (String[] section : mapperForwardSections) {
                if (section[0].equals(entityName)) ordered.add(section[1]);
            }
        }
        ordered.addAll(mapperReverseSections);
        ordered.addAll(mapperSpecSections);
        for (int i = 0; i < ordered.size(); i++) {
            String lead = i == 0 ? "    \n" : "    \n    \n";
            String tail = i == ordered.size() - 1 ? "    " : "";
            domain.mapperSection(lead + ordered.get(i) + tail);
        }
        for (String ns : mapperNamespaces) {
            domain.mapperImport(ns);
        }
        mapperNamespaces.clear();
        mapperOrdinals.clear();
        mapperForwardSections.clear();
        mapperReverseSections.clear();
        mapperSpecSections.clear();
        emitDomain(domain, "mapper",
                target(model.getNamespace("dto"),
                        String.format("%sMapper", domain.className())),
                "jpa-mapper-file");
    }

    /**
     * A domain with no entity gets no domain-level files - including the
     * process managers and domain services it declares, which is said rather
     * than left for the user to find missing.
     */
    private void warnNothingToHang(String domainDirName, ECoreModel model) {
        if (model.getDomainServiceModels() == null) return;
        if (model.getDomainServiceModels().getOrgItems().size() == 0) return;
        io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                "[%s] declares domain services or process managers but no entity, so nothing is generated for them"
                        + " - a context that only reacts needs at least one entity of its own.", domainDirName));
    }

    /** The domain-level artifacts the kt bundle covers so far. */
    private void generateKotlinDomainArtifacts(String domainDirName, ECoreModel model,
                                               List<EntitySpec> specs) {
        if (specs.isEmpty()) {
            warnNothingToHang(domainDirName, model);
            return;
        }
        io.elasticore.base.model.pub.rules.DomainSpec domain =
                SpecFactory.fromDomain(domainDirName, model, specs);
        emitDomain(domain, "Q", target(queryPackage(model), "Q"), "jpa-q-kt");
        assembleMapper(domain, model);
        emitDomain(domain, "repositoryHelper",
                target(model.getNamespace("repository"),
                        String.format("%sRepositoryHelper", domain.className())),
                "jpa-repository-helper");
        emitContributedDomain(domain, model);
        emitArchitectureTest(domain, model);
    }

    /**
     * The types a DTO names but no entity declares: the JsonView markers and
     * the sort/page interfaces a search DTO implements. Language-neutral -
     * the rules only gate and set the package, and the bodies come from the
     * template set - so both languages emit them the same way.
     */
    private void generateSupportTypes(String domainDirName, ECoreModel model,
                                      List<EntitySpec> specs) {
        if (specs.isEmpty()) return;
        io.elasticore.base.model.pub.rules.DomainSpec domain =
                SpecFactory.fromDomain(domainDirName, model, specs);
        emitDomain(domain, "views",
                target(model.getNamespace("dto"), "Views"), "jpa-views");
        emitDomain(domain, "pageableObject",
                target(model.getNamespace("dto"), "PageableObject"),
                "jpa-support-types");
        emitDomain(domain, "sortableObject",
                target(model.getNamespace("dto"), "SortableObject"),
                "jpa-support-types");
    }

    private void emitEnum(io.elasticore.base.model.pub.rules.EnumSpec spec,
                          String name, ArtifactTarget out) {
        String label = String.format("    %-12s %s", "enum", out.relativePath());
        try {
            SourceCode code = engine(typescript ? "jpa-enum-ts"
                    : kotlin ? "jpa-enum-kt" : "jpa-enum").run(spec, "enum");
            if (!code.isMake()) {
                skipped++;
                System.out.println(label + "  SKIP(dontMake)");
                return;
            }
            String rendered = code.render();
            if (xmlMapping()) {
                // The enum's database converter is persistence, so it moves out.
                String[] split = OrmMapping.splitEnumConverter(rendered, name,
                        out.packageName() + "." + name, persistencePackage(), kotlin);
                if (split != null) {
                    rendered = split[0];
                    ArtifactTarget conv = target(persistencePackage(), name + "Converter");
                    write(String.format("    %-12s %s", "converter", conv.relativePath()), conv, split[1]);
                    orm(out).converterMoved(out.packageName() + "." + name,
                            persistencePackage() + "." + name + "Converter");
                }
            }
            write(label, out, rendered);
        } catch (RuntimeException e) {
            failed(label, out, e);
        }
    }

    private void emitDomain(io.elasticore.base.model.pub.rules.DomainSpec spec, String target,
                            ArtifactTarget out, String... ruleSets) {
        String label = String.format("    %-12s %s", target, out.relativePath());
        try {
            SourceCode code = engine(ruleSets)
                    .artifactName(out.fileName())
                    .packageName(out.packageName())
                    .run(spec, target);
            if (!code.isMake()) {
                skipped++;
                System.out.println(label + "  SKIP(dontMake)");
                return;
            }
            write(label, out, code.render());
        } catch (RuntimeException e) {
            failed(label, out, e);
        }
    }

    /**
     * An engine carrying the configured selection plus the rule sets this
     * artifact requires, so a project-level choice cannot silently disable
     * the artifact being generated.
     */
    private RuleEngine engine(String... ruleSets) {
        RuleEngine engine = RulesConfig.engineFor(RuleCatalog.standard(),
                        withAddedRules(configuredRules()), domainConfig("publisher.suppress"),
                        defaultBundle())
                .templateSet(typescript ? SourceCode.TemplateSet.TYPESCRIPT
                        : kotlin ? SourceCode.TemplateSet.KOTLIN
                        : v3 ? SourceCode.TemplateSet.V3 : SourceCode.TemplateSet.COMPATIBLE);
        for (String rs : ruleSets) {
            for (io.elasticore.base.model.pub.rules.CodeRules r
                    : RuleCatalog.standard().rulesOf(rs)) {
                engine.register(r);
            }
        }
        return engine;
    }

    /**
     * The rule selection this run should use, which is not always the one
     * env.yml states.
     *
     * publisher.rules names the rules the server is built from, and Java and
     * Kotlin are both the server - so a Kotlin run honours it, which is how
     * [jpa-springboot-kt, ddd-aggregate] works. TypeScript is a different
     * program with a different set of artifacts, and a server-side selection
     * says nothing about it: naming the Kotlin bundle there would generate
     * Kotlin rules into .ts files, which is what this stops.
     *
     * A project that wants to choose for TypeScript says so under its own
     * key, publisher.rules.ts.
     */
    /**
     * The bundle a run falls back to when a domain names no rules of its own.
     *
     * An architecture declared by the build wins over the language switches,
     * because it says more: the switches only distinguish Java from Kotlin,
     * while the declaration also says whether this project is a domain model.
     * Nothing declared leaves the old answer exactly where it was.
     */
    private String defaultBundle() {
        return defaultBundle(typescript);
    }

    private String defaultBundle(boolean typescript) {
        // Layered Java follows the mode the run settled on: the 2.x bundle
        // when the project keeps 2.x's code, the v3 one otherwise. A bundle
        // and a mode that disagree - the 2.x bundle on the v3 skeletons, or
        // the reverse - is the exact bug class bundleFor exists to eliminate.
        if (!v3 && !kotlin && !typescript) return RulesConfig.DEFAULT_BUNDLE;
        RulesConfig.Architecture architecture = RulesConfig.declaredArchitecture();
        if (architecture == null) architecture = RulesConfig.Architecture.LAYERED;
        RulesConfig.Language language = typescript ? RulesConfig.Language.TYPESCRIPT
                : kotlin ? RulesConfig.Language.KOTLIN
                : RulesConfig.Language.JAVA;
        try {
            return RulesConfig.bundleFor(architecture, language, persistence());
        } catch (IllegalArgumentException noSuchServer) {
            // Asked by a TypeScript run for a server the server's own run
            // refuses (a hexagon over MyBatis): that run says so; the client
            // is shaped as the JPA server would be.
            return RulesConfig.bundleFor(architecture, language);
        }
    }

    /**
     * Where the domain being generated keeps its data, from its mode. Every
     * domain reaching generation writes JPA (generateAll refuses the rest),
     * so this names the axis the bundle is chosen on rather than changing
     * the choice.
     */
    private RulesConfig.Persistence persistence() {
        return currentModel == null ? RulesConfig.Persistence.JPA
                : RulesConfig.persistenceOfMode(currentModel.getConfig("mode", "jpa"));
    }

    /**
     * What the server of this project selects: the choice a Java or Kotlin
     * run of the same build makes.
     *
     * A TypeScript run asks this rather than its own selection when it
     * decides the model's shape. The client describes the JSON the server
     * sends, and the server sends what its own rules made of the model - a
     * reference by its id, a value object inline. Asked of ts-model instead,
     * the answer was always no, and the client described a 2.x server: an
     * order with no customerId, no delivery address and no card.
     */
    private java.util.List<String> serverRules() {
        java.util.List<String> declared = kotlin ? domainConfig("publisher.rules.kt") : null;
        if (declared == null) declared = domainConfig("publisher.rules");
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (declared != null) {
            out.addAll(declared);
        } else {
            String prop = System.getProperty(RulesConfig.PROP_RULES);
            if (prop != null && !prop.trim().isEmpty()) {
                out.addAll(RulesConfig.configuredList(prop));
            } else {
                out.add(defaultBundle(false));
            }
        }
        for (String name : RulesConfig.addedRules()) {
            if (!out.contains(name)) out.add(name);
        }
        return out;
    }

    /**
     * Whether the server of this project renders the clean (v3 or Kotlin)
     * skeletons rather than the 2.x-compatible ones: the mode its own run
     * would take - a v3 switch or architecture, Kotlin, or a rule it names
     * that exists only there.
     */
    private boolean serverIsClean() {
        if (v3 || kotlin) return true;
        // What the server's own last run took: a -Pv3 given to elcore3 alone.
        if ("clean".equals(readText(serverMode(domainRoot != null ? domainRoot : projectRoot)))) return true;
        for (String name : serverRules()) {
            if (needsV3(name) || name.trim().endsWith("-kt")) return true;
        }
        return false;
    }

    /** Whether the rules that shape the model - the server's, for a client - include one of this kind. */
    private boolean shapes(Class<?> kind) {
        if (!typescript) return selects(kind);
        for (String name : serverRules()) {
            try {
                for (io.elasticore.base.model.pub.rules.CodeRules rs : RuleCatalog.standard().rulesOf(name)) {
                    if (kind.isInstance(rs)) return true;
                }
            } catch (IllegalArgumentException notOnThisClasspath) {
                // A project's own rule set is the server's business; it does not shape the client.
            }
        }
        return false;
    }

    /**
     * The rule selection contributed artifacts are asked for.
     *
     * The same one the engine resolves, which configuredRules() alone is not:
     * that answers null when a domain declares nothing, and a null meant
     * emitContributed returned without asking anybody. A project relying on
     * the default bundle therefore got no contributed artifact at all, and
     * silently - the run said nothing because nothing had been asked to
     * decline. It stayed invisible only because the bundles that had
     * contributors were also the ones every sample names explicitly.
     */
    private java.util.List<String> contributingRules() {
        java.util.List<String> declared = configuredRules();
        java.util.List<String> selected = declared != null ? declared
                : java.util.Collections.singletonList(defaultBundle());
        return withAddedRules(selected);
    }

    /**
     * The selection plus the rule sets the project adds (the plugin's rules
     * list). Null stays null when nothing is added, so the engine still falls
     * through to the project property and the default bundle as before.
     * TypeScript is another program and takes none of them: a server rule
     * set would write JVM code into .ts files.
     */
    private java.util.List<String> withAddedRules(java.util.List<String> selected) {
        java.util.List<String> added = RulesConfig.addedRules();
        if (added.isEmpty() || typescript) return selected;
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (selected != null) {
            out.addAll(selected);
        } else {
            String prop = System.getProperty(RulesConfig.PROP_RULES);
            if (prop != null && !prop.trim().isEmpty()) {
                out.addAll(RulesConfig.configuredList(prop));
            } else {
                out.add(defaultBundle());
            }
        }
        for (String name : added) {
            if (!out.contains(name)) out.add(name);
        }
        return out;
    }

    private java.util.List<String> configuredRules() {
        String key = typescript ? "publisher.rules.ts"
                : kotlin ? "publisher.rules.kt" : null;

        if (key != null) {
            java.util.List<String> declared = domainConfig(key);
            if (declared != null) return declared;
        }
        return typescript ? null : domainConfig("publisher.rules");
    }

    /**
     * -PlistRules: the rules this domain's selection runs, by artifact and in
     * the order they run, instead of generating. The ids are what
     * publisher.suppress takes, and the orders are where a project's own rule
     * has to sit to come before or after one.
     */
    private void listRules(String domainDirName) {
        java.util.List<String> names = contributingRules();
        java.util.List<String> off = RulesConfig.resolveSuppress(domainConfig("publisher.suppress"));
        System.out.println("[rules] " + domainDirName + ": " + String.join(" + ", names)
                + (off.isEmpty() ? "" : "   (suppressed: " + String.join(", ", off) + ")"));
        java.util.Map<String, java.util.List<String[]>> byTarget = new java.util.TreeMap<String, java.util.List<String[]>>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (String name : names) {
            for (io.elasticore.base.model.pub.rules.CodeRules rs : RuleCatalog.standard().rulesOf(name)) {
                for (java.lang.reflect.Method m : rs.getClass().getDeclaredMethods()) {
                    io.elasticore.base.model.pub.rules.Rule r = m.getAnnotation(io.elasticore.base.model.pub.rules.Rule.class);
                    if (r == null || !seen.add(r.target() + "/" + r.value() + "/" + rs.getClass().getName())) continue;
                    Class<?>[] params = m.getParameterTypes();
                    // A rule taking a domain, an enum or a port runs once, by order alone.
                    String when = params.length == 0 ? "?"
                            : params.length == 2 ? "field"
                            : params[0] != EntitySpec.class ? "once"
                            : r.phase() == io.elasticore.base.model.pub.rules.Rule.Phase.TAIL ? "tail" : "head";
                    java.util.List<String[]> rows = byTarget.get(r.target());
                    if (rows == null) byTarget.put(r.target(), rows = new java.util.ArrayList<String[]>());
                    rows.add(new String[]{String.format("%5d", r.order()), when, r.value(),
                            rs.getClass().getSimpleName(), off.contains(r.value()) ? "  (suppressed)" : "",
                            String.valueOf(seen.size()),
                            rs.getClass().getName().startsWith("io.elasticore.") ? "0" : "1"});
                }
            }
        }
        for (java.util.Map.Entry<String, java.util.List<String[]>> t : byTarget.entrySet()) {
            java.util.List<String[]> rows = t.getValue();
            // The order they run in: head rules, then field rules, then tail
            // rules, each by order.
            java.util.Collections.sort(rows, new java.util.Comparator<String[]>() {
                public int compare(String[] a, String[] b) {
                    // head, field, tail as the engine runs them; whole-model
                    // rules are their own run and listed after them. Within a
                    // stage by order, then as registered - the engine's ties.
                    int pa = rank(a[1]), pb = rank(b[1]);
                    if (pa != pb) return pa - pb;
                    // The project's rules after ElastiCORE's in the same stage, as the engine runs them.
                    if (!a[6].equals(b[6])) return a[6].compareTo(b[6]);
                    int oa = Integer.parseInt(a[0].trim()), ob = Integer.parseInt(b[0].trim());
                    if (oa != ob) return oa < ob ? -1 : 1;
                    return Integer.parseInt(a[5]) - Integer.parseInt(b[5]);
                }

                private int rank(String when) {
                    return "head".equals(when) ? 0 : "field".equals(when) ? 1 : "tail".equals(when) ? 2 : 3;
                }
            });
            System.out.println("  " + t.getKey());
            for (String[] row : rows) {
                System.out.println(String.format("    %s %-5s %-36s %s%s", row[0], row[1], row[2], row[3], row[4]));
            }
        }
    }

    /** A rule selection this domain declares in its own env.yml, or null. */
    private java.util.List<String> domainConfig(String key) {
        java.util.List<String> named = currentModel == null
                ? null
                : RulesConfig.configuredList(currentModel.getConfig(key));
        // A MyBatis domain naming its architecture's bundle gets the MyBatis
        // one of it: mode says the persistence, the bundle the architecture.
        if (named != null && "publisher.rules".equals(key) && persistence() == RulesConfig.Persistence.MYBATIS) {
            java.util.List<String> out = new ArrayList<String>();
            for (String rule : named) {
                String mybatis = RulesConfig.mybatisBundleOf(rule);
                out.add(mybatis != null ? mybatis : rule);
            }
            return out;
        }
        return named;
    }

    private void generateEntityArtifacts(Entity entity, EntitySpec spec, ECoreModel model) {
        String name = entity.getIdentity().getName();
        boolean exposed = entity.getMetaInfo().hasMetaAnnotation("expose");
        boolean serviced = exposed || entity.getMetaInfo().hasMetaAnnotation("service");
        boolean hasPk = spec.pkField() != null;

        System.out.println("  " + name);

        // Over MyBatis the artifacts a rule set contributes - the repository
        // adapter writing the aggregate's members - read the related entities.
        if (v3 && !kotlin && !typescript && persistence() == RulesConfig.Persistence.MYBATIS) {
            relateMyBatisEarly(spec);
        }

        // What the configured rule sets bring of their own, before the
        // built-in list. The built-ins are what every project gets; these
        // exist only where a selection declared them, so a project that
        // configures nothing reaches this loop zero times and its output
        // is untouched.
        emitContributed(spec, model);

        // Kotlin covers the entity and, through generateDtos, the DTOs. The
        // service, repository, controller and mapper are not in the kt bundle
        // yet - their rules still write Java signatures - so they are left
        // unwritten rather than emitted in the wrong language.
        if (typescript) {
            // The client for what the controller exposes. Only an entity the
            // DSL exposed has endpoints to call.
            if (exposed && hasPk) {
                emit(spec, "api", target("", String.format("%sApi", name)), "ts-api");
            }
            // The shape of a value object the server's DTOs carry inline.
            if (entity.getMetaInfo().hasMetaAnnotation("embeddable") && SpecFactory.valueObjectsInDto()) {
                emit(spec, "valueObject", target("", name), "jpa-dto-file-ts");
            }
            return;
        }

        // The files written once are written from what the core service is.
        // Written while it failed, they keep a constructor for a service that
        // was never generated, and are the developer's from then on - so they
        // wait for a run in which it succeeds.
        boolean coreOk = true;

        if (kotlin) {
            emit(spec, "entity",
                    target(model.getNamespace("entity"), entityClassName(spec)),
                    "jpa-entity", "jpa-entity-annotations", "jpa-entity-file-kt");
            if (serviced && hasPk) {
                emit(spec, "repository",
                        target(model.getNamespace("repository"),
                                String.format("%sRepository", name)),
                        "jpa-repository", "jpa-repository-file-v3");
                coreOk = emitSucceeded(spec, "service",
                        target(model.getNamespace("service"),
                                String.format("%sCoreService", name)),
                        "jpa-service-file");
                if (coreOk) emit(spec, "service_extend",
                        target(model.getNamespace("service"),
                                String.format("%sService", name)),
                        "spring-service-extend");
            }
            if (exposed && hasPk && coreOk) {
                emit(spec, "control",
                        target(model.getNamespace("control"),
                                String.format("%sController", name)),
                        "spring-controller");
            }
            // The generated test sits in the test tree, in the language it
            // exercises. Only a domain that asks for it in env.yml gets one.
            if (serviced && hasPk && coreOk && spec.testCode()) {
                emit(spec, "service_test",
                        ArtifactTarget.of("kotlin", "src/test/kotlin", "kt",
                                model.getNamespace("service"),
                                String.format("%sServiceTest", name), true),
                        "spring-service-test");
            }
            if (spec.pkCount() > 1) {
                // The class the entity's @IdClass names; Java gets it from
                // emitIdentity, in the Kotlin form here.
                emit(spec, "identity",
                        target(model.getNamespace("entity"),
                                String.format("%sIdentity", name)),
                        "jpa-entity", "jpa-identity-kt");
            }
            return;
        }

        if (v3 && persistence() == RulesConfig.Persistence.MYBATIS) {
            // What the MyBatis code does not write yet is refused, not written
            // so that it loses data quietly.
            relateMyBatisEarly(spec);
            java.util.List<String> unsupported =
                    io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.unsupported(spec);
            if (!unsupported.isEmpty() && !spec.hasMeta("embeddable") && !spec.hasMeta("abstract")) {
                io.elasticore.base.model.pub.rules.ModelCheck.fail(String.format(
                        "[elcore3] %s.%s: mode: mybatis does not write %s yet - model it otherwise,"
                                + " or keep this domain on JPA", spec.domainClassName(), name,
                        String.join("; ", unsupported)));
                return;
            }
            // A root the domain rules split (OrderBase + Order) is written as its base.
            emit(spec, "entity",
                    ArtifactTarget.java(model.getNamespace("entity"), entityClassName(spec)),
                    "jpa-entity", "mybatis-entity-file");
            // A member of a bound aggregate is its root's to write (hasOwnService).
            serviced = serviced && io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.hasOwnService(name);
            exposed = exposed && serviced;
            if (serviced && hasPk) {
                coreOk = emitSucceeded(spec, "service",
                        ArtifactTarget.java(model.getNamespace("service"),
                                String.format("%sCoreService", name)),
                        "mybatis-service-file");
                // The mapper and its XML wait for the search DTO's conditions,
                // which the DTO pass reads: they are written with the domain.
                mybatisMapped.add(spec);
                if (coreOk) emit(spec, "service_extend",
                        ArtifactTarget.java(model.getNamespace("service"),
                                String.format("%sService", name)),
                        "spring-service-extend");
            }
            if (exposed && hasPk && coreOk) {
                emit(spec, "control",
                        ArtifactTarget.java(model.getNamespace("control"),
                                String.format("%sController", name)),
                        "spring-controller");
            }
            return;
        }

        if (v3) {
            emit(spec, "entity",
                    ArtifactTarget.java(model.getNamespace("entity"), entityClassName(spec)),
                    "jpa-entity", "jpa-entity-file");
            emitIdentity(spec, model);

            if (serviced && hasPk) {
                coreOk = emitSucceeded(spec, "service",
                        ArtifactTarget.java(model.getNamespace("service"),
                                String.format("%sCoreService", name)),
                        "jpa-service-file");
                emit(spec, "repository",
                        ArtifactTarget.java(model.getNamespace("repository"),
                                String.format("%sRepository", name)),
                        "jpa-repository", "jpa-repository-file-v3");
                if (coreOk) emit(spec, "service_extend",
                        ArtifactTarget.java(model.getNamespace("service"),
                                String.format("%sService", name)),
                        "spring-service-extend");
            }
            if (exposed && hasPk && coreOk) {
                emit(spec, "control",
                        ArtifactTarget.java(model.getNamespace("control"),
                                String.format("%sController", name)),
                        "spring-controller");
            }
            return;
        }

        // Compatibility mode: the artifacts whose whole file the rules render.
        // The entity renders through the compat file rules of the configured
        // selection (jpa-entity / jpa-entity-class / jpa-entity-file-compat).
        emit(spec, "entity", ArtifactTarget.java(model.getNamespace("entity"), name));
        emitIdentity(spec, model);

        // Whether 2.x writes the service pair, and with which DTOs: the ones
        // declared @template / @searchable of the entity, whatever they are
        // called (ServiceSrcPublisher.publish).
        String[] dtoPair = compatDtoPair(entity, model);
        if (dtoPair != null) {
            spec.config(COMPAT_SERVICE_DTO, dtoPair[0]);
            spec.config(COMPAT_SERVICE_SRCH_DTO, dtoPair[1]);
        }
        boolean servicePair = dtoPair != null && compatWritesService(entity, model);

        // Every entity with a key has a repository in 2.x: the loader gives
        // each one a repository model whether repository.yml names it or not.
        if (hasPk) {
            emit(spec, "repository",
                    ArtifactTarget.java(model.getNamespace("repository"),
                            String.format("%sRepository", name)),
                    "jpa-repository");
        }
        // The CoreService in its 2.x form ("jpa-service-compat"), not the v3
        // one "jpa-service-file" writes - a different artifact.
        if (servicePair) {
            emit(spec, "service",
                    ArtifactTarget.java(model.getNamespace("service"),
                            String.format("%sCoreService", name)),
                    "jpa-service-compat");
            emit(spec, "service_extend",
                    ArtifactTarget.java(model.getNamespace("service"),
                            String.format("%sService", name)),
                    "spring-service-extend");

            // The service's test: a skeleton the developer takes over.
            // Maven convention puts it under src/test/java, and like the
            // service it is written once and never regenerated. Only a
            // domain that asks for it in env.yml gets one.
            if (spec.testCode()) emit(spec, "service_test",
                    ArtifactTarget.of("java", "src/test/java", "java",
                            model.getNamespace("service"),
                            String.format("%sServiceTest", name), true),
                    "spring-service-test");
        }
        if (dtoPair != null && compatWritesController(entity, model)) {
            emit(spec, "control",
                    ArtifactTarget.java(model.getNamespace("control"),
                            String.format("%sController", name)),
                    "spring-controller");
        }
    }

    /** Where the compatible service rules find the DTOs 2.x chose for the entity. */
    public static final String COMPAT_SERVICE_DTO = "compat.service.dto";
    public static final String COMPAT_SERVICE_SRCH_DTO = "compat.service.srchdto";

    /**
     * The DTO and search DTO 2.x writes an entity's service and controller
     * with, or null when it writes neither (ServiceSrcPublisher.publish,
     * ControlSrcPublisher.publish): not for a @runtime entity or one without
     * a key; only with a DTO declared @template of the entity and one
     * declared @searchable of it, whatever they are called.
     */
    static String[] compatDtoPair(Entity entity, ECoreModel model) {
        io.elasticore.base.model.MetaInfo meta = entity.getMetaInfo();
        if (meta != null && meta.hasMetaAnnotation("runtime")) return null;
        if (entity.getPkField() == null) return null;

        io.elasticore.base.model.core.RelationshipManager relations =
                io.elasticore.base.model.core.RelationshipManager.getInstance(entity.getIdentity().getDomainId());
        String name = entity.getIdentity().getName();
        String dto = firstDto(relations.findByToNameAndType(name,
                io.elasticore.base.model.relation.RelationType.TEMPLATE), model);
        String srch = firstDto(relations.findByToNameAndType(name,
                io.elasticore.base.model.relation.RelationType.SEARCHABLE), model);
        return dto == null || srch == null ? null : new String[]{dto, srch};
    }

    /**
     * Not when @service(false) / @expose(false) says so, or when
     * defaultGeneration.service is false and the entity asked for neither.
     */
    static boolean compatWritesService(Entity entity, ECoreModel model) {
        io.elasticore.base.model.MetaInfo meta = entity.getMetaInfo();
        boolean asked = meta.hasMetaAnnotation("service") || meta.hasMetaAnnotation("expose");
        String isGen = meta.getInfoAnnotationValue("service");
        if (isGen == null) isGen = meta.getInfoAnnotationValue("expose");
        boolean defaultGen = !"false".equals(model.getConfig("defaultGeneration.service", "true"));
        return !("false".equals(isGen) || (!asked && !defaultGen));
    }

    /**
     * Not for a @service entity, which is used from other code rather than
     * over HTTP; not when @api(false) says so, or when defaultGeneration.api
     * is false and the entity did not ask with @api.
     */
    static boolean compatWritesController(Entity entity, ECoreModel model) {
        io.elasticore.base.model.MetaInfo meta = entity.getMetaInfo();
        if (meta.hasMetaAnnotation("service")) return false;
        boolean asked = meta.hasMetaAnnotation("api");
        String isGen = meta.getInfoAnnotationValue("api");
        boolean defaultGen = !"false".equals(model.getConfig("defaultGeneration.api", "true"));
        return !("false".equals(isGen) || (!asked && !defaultGen));
    }

    private static String firstDto(java.util.List<io.elasticore.base.model.relation.ModelRelationship> relations,
                                   ECoreModel model) {
        for (io.elasticore.base.model.relation.ModelRelationship r : relations) {
            String dtoName = r.getFromName();
            if (model.getDataTransferModels() != null
                    && model.getDataTransferModels().findByName(dtoName) != null) return dtoName;
        }
        return null;
    }

    /**
     * The class a composite key's @IdClass names. Without it a Java project
     * whose entity declares two @id fields did not compile: the entity named
     * a class nothing wrote.
     */
    private void emitIdentity(EntitySpec spec, ECoreModel model) {
        if (spec.pkCount() <= 1) return;
        emit(spec, "identity",
                ArtifactTarget.java(model.getNamespace("entity"),
                        String.format("%sIdentity", spec.getName())),
                "jpa-identity");
    }

    /**
     * The class an entity's regenerated file is written as: its own name, or
     * its base when ddd-behavior splits it and the concrete class - the one
     * with the entity's name - is the developer's (DddBehaviorRules).
     */
    private String entityClassName(EntitySpec spec) {
        for (String name : contributingRules()) {
            for (io.elasticore.base.model.pub.rules.CodeRules rs : RuleCatalog.standard().rulesOf(name)) {
                if (!(rs instanceof io.elasticore.base.model.pub.rules.EntityClassNamer)) continue;
                String named = ((io.elasticore.base.model.pub.rules.EntityClassNamer) rs).entityClassName(spec);
                if (named != null) return named;
            }
        }
        return spec.getName();
    }

    /** Whether a rule set of this kind is in the current domain's selection. */
    private boolean selects(Class<?> kind) {
        for (String name : contributingRules()) {
            for (io.elasticore.base.model.pub.rules.CodeRules rs : RuleCatalog.standard().rulesOf(name)) {
                if (kind.isInstance(rs)) return true;
            }
        }
        return false;
    }

    /** Whether the model declares a DTO of the given name. */
    private static boolean hasDataTransfer(ECoreModel model, String name) {
        if (model.getDataTransferModels() == null) return false;
        return model.getDataTransferModels().getItems().findByName(name) != null;
    }

    /**
     * Renders one artifact and writes it when the file may be touched.
     * The template is resolved from the target name, so no template path
     * appears at the call site.
     */
    /** @return whether the artifact was written. */
    /**
     * The artifacts the configured rule sets declare beyond the built-in
     * list.
     *
     * The runner never learns their names: it asks each configured set what
     * it adds for this entity and emits the answers. Conditions live in the
     * set's own code - an empty answer is the artifact not applying - and
     * the rules that write the content are the set's own, found through the
     * same engine as everything else, so a contributed artifact is declined,
     * styled and ownership-checked exactly like a built-in one.
     */
    /**
     * The artifacts the configured rule sets declare for the domain itself.
     *
     * The same walk as the entity form, over the other interface. Neither
     * mentions a rule set: the runner asks what is configured and emits the
     * answers, so a new kind of domain-level file lives entirely inside the
     * set that wants it.
     */
    private void emitContributedDomain(io.elasticore.base.model.pub.rules.DomainSpec domain,
                                       ECoreModel model) {
        lendEvents(domain);
        // A rule set selected twice - in a bundle and by name - declares its files once.
        java.util.Set<Class<?>> asked = new java.util.HashSet<Class<?>>();
        for (String name : contributingRules()) {
            for (io.elasticore.base.model.pub.rules.CodeRules rs
                    : RuleCatalog.standard().rulesOf(name)) {
                if (!(rs instanceof io.elasticore.base.model.pub.rules.DomainArtifactContributor)) {
                    continue;
                }
                if (!asked.add(rs.getClass())) continue;

                for (io.elasticore.base.model.pub.rules.ContributedArtifact a
                        : ((io.elasticore.base.model.pub.rules.DomainArtifactContributor) rs)
                                .artifactsFor(domain)) {
                    emitDomain(domain, a.target(),
                            target(namespaceFor(a, model), a.className()),
                            name);
                    // The handler says its messages through the domain's bundle.
                    if ("apiErrors".equals(a.target())) {
                        writeMessages(domain.getName(), target(namespaceFor(a, model), a.className()));
                    }
                }
            }
        }
    }

    /**
     * What a process manager needs from the other contexts: the class of each
     * event it names as context:RootEvent, and which context declares a name
     * written without one. The domain alone cannot know either.
     */
    private void lendEvents(io.elasticore.base.model.pub.rules.DomainSpec domain) {
        if (contextMap == null) return;
        for (io.elasticore.base.model.pub.rules.DomainSpec.DomainServiceSpec pm : domain.domainServices()) {
            for (String event : pm.on()) {
                int colon = event.indexOf(':');
                if (colon > 0) {
                    String other = contextMap.contextNamed(event.substring(0, colon));
                    String cls = contextMap.eventClass(event);
                    if (other == null) {
                        domain.foreignEventProblem(event, String.format("there is no context %s (a blueprint folder"
                                + " or domainName)", event.substring(0, colon).trim()));
                    } else if (cls == null && contextMap.whyNoEvent(event) != null) {
                        domain.foreignEventProblem(event, contextMap.whyNoEvent(event));
                    } else if (cls != null && !generatesEvents(contextMap.modelsOf(other))) {
                        // Declared, but the rules that context runs write no event
                        // classes: the import would name a class nobody generates.
                        domain.foreignEventProblem(event, String.format("%s declares it, but its rules generate no"
                                + " event classes - select ddd-event there (a ddd or hexagonal bundle has it)"
                                + " and do not suppress it", other));
                    } else if (cls != null) {
                        domain.foreignEvent(event, cls);
                    }
                } else {
                    String other = contextMap.contextDeclaringEvent(domain.getName(), event);
                    if (other != null) domain.eventElsewhere(event, other);
                }
            }
        }
    }

    /**
     * Whether another domain's rule selection - as this run would resolve it
     * there, suppressions included - has a rule writing the event artifact.
     * Asked of the rules' declarations, as -PlistRules reads them, so the
     * runner still names no rule set.
     */
    private boolean generatesEvents(List<ECoreModel> models) {
        ECoreModel saved = currentModel;
        try {
            for (ECoreModel m : models) {
                currentModel = m;
                java.util.List<String> off = RulesConfig.resolveSuppress(domainConfig("publisher.suppress"));
                for (String name : contributingRules()) {
                    for (io.elasticore.base.model.pub.rules.CodeRules rs : RuleCatalog.standard().rulesOf(name)) {
                        for (java.lang.reflect.Method method : rs.getClass().getDeclaredMethods()) {
                            io.elasticore.base.model.pub.rules.Rule r =
                                    method.getAnnotation(io.elasticore.base.model.pub.rules.Rule.class);
                            if (r != null && "event".equals(r.target()) && !off.contains(r.value())) return true;
                        }
                    }
                }
            }
            return false;
        } finally {
            currentModel = saved;
        }
    }

    /** Where a contributed artifact goes: its own namespace, or its fallback. */
    private String namespaceFor(io.elasticore.base.model.pub.rules.ContributedArtifact a,
                                ECoreModel model) {
        String pkg = model.getNamespace(a.namespaceKey());
        if (pkg == null && a.fallbackNamespaceKey() != null) {
            pkg = model.getNamespace(a.fallbackNamespaceKey());
        }
        return pkg;
    }

    private void emitContributed(EntitySpec spec, ECoreModel model) {
        java.util.List<String> names = contributingRules();
        java.util.Set<Class<?>> asked = new java.util.HashSet<Class<?>>();

        for (String name : names) {
            for (io.elasticore.base.model.pub.rules.CodeRules rs
                    : RuleCatalog.standard().rulesOf(name)) {
                if (!(rs instanceof io.elasticore.base.model.pub.rules.ArtifactContributor)) continue;
                if (!asked.add(rs.getClass())) continue;

                for (io.elasticore.base.model.pub.rules.ContributedArtifact a
                        : ((io.elasticore.base.model.pub.rules.ArtifactContributor) rs).artifactsFor(spec)) {
                    contributedPackage = true;
                    try {
                        emit(spec, a.target(), target(namespaceFor(a, model), a.className()));
                    } finally {
                        contributedPackage = false;
                    }
                }
            }
        }
    }

    /** The test-tree twin of {@link #target}, for a contributed test artifact. */
    private ArtifactTarget testTarget(String packageName, String className) {
        return kotlin
                ? ArtifactTarget.of("kotlin", "src/test/kotlin", "kt", packageName, className, true)
                : ArtifactTarget.of("java", "src/test/java", "java", packageName, className, true);
    }

    /** Whether an artifact did not fail - written, unchanged, owned or declined by a rule. */
    private boolean emitSucceeded(EntitySpec spec, String target,
                                  ArtifactTarget out, String... ruleSets) {
        int before = failed;
        emit(spec, target, out, ruleSets);
        return failed == before;
    }

    /** Whether the artifact being emitted is a contributed one, whose rules are handed its package. */
    private boolean contributedPackage;

    private boolean emit(EntitySpec spec, String target,
                      ArtifactTarget out, String... ruleSets) {
        String label = String.format("    %-12s %s", target, out.relativePath());
        try {
            // Rule sets required by this artifact are added on top of the
            // configured selection so a project-level choice cannot silently
            // disable the artifact being generated.
            SourceCode code = engine(ruleSets)
                    .artifactName(out.fileName())
                    .packageName(contributedPackage ? out.packageName() : null)
                    .run(spec, target);
            if (!code.isMake()) {
                skipped++;
                System.out.println(label + "  SKIP(dontMake)");
                return false;
            }
            write(label, out, code.render());
            return true;
        } catch (RuntimeException e) {
            failed(label, out, e);
            return false;
        }
    }

    /**
     * Records an artifact that could not be generated, and keeps going.
     *
     * Not thrown: one broken rule set should not hide what the others would
     * have said, and a run that stops at the first failure has to be run
     * again to learn the second. The run ends non-zero instead, so a build
     * that asked for these files does not proceed as though it got them.
     */
    private void failed(String label, ArtifactTarget out, Exception e) {
        failed++;
        notes.add(out.relativePath() + ": " + e);
        System.out.println(label + "  ERROR " + e.getMessage());
    }

    /**
     * A user-owned file the generator no longer writes can still have to
     * agree with one it does.
     *
     * The extension service is generate-once and calls its core service's
     * constructor; when a rule changes what the core service is given - the
     * hexagonal bundle handing it ports instead of the repository helper - the
     * owned file keeps the old call and the build fails far from the cause.
     * So the call the generator would write now is compared with the one the
     * file has, and a difference is said here, with the line to write.
     */
    /**
     * An entity class the developer owns - the concrete half of a split
     * entity - carries the table annotations, so a DSL change to the table
     * name, schema or indexes is not applied to it. It is said instead, with
     * the lines the generator would write now.
     */
    /**
     * A file the developer owns that the generator would now write as the
     * subclass of a regenerated base - a process manager or domain service
     * written before they were split - does not extend that base. Kept, it
     * stops following the DSL: an event added to 'on' or a name added to
     * 'uses' reaches only the base nobody extends, and nothing fails. So it
     * is said, with what to do.
     */
    private void warnIfBaseNotExtended(ArtifactTarget out, File file, String wouldWrite) {
        String base = out.fileName() + "Base";
        if (!wouldWrite.contains("extends " + base) && !wouldWrite.contains(": " + base + "()")) return;
        String existing;
        try {
            existing = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return;
        }
        if (existing.contains("extends " + base) || existing.contains(": " + base + "()")) return;
        String message = String.format("[elcore3] %s does not extend %s, which is regenerated from the DSL - so a change"
                + " to the DSL ('on', 'uses') does not reach it. Make it extend %s (Kotlin: ': %s()'), turn each"
                + " listener into 'protected void handle(...)' (Kotlin: 'override fun handle(...)') without its"
                + " annotations, and drop the constructor - the ports are fields of the base. Or delete the file to"
                + " have it generated anew.", out.relativePath(), base, base, base);
        io.elasticore.base.util.ConsoleLog.printWarn(message);
        notes.add(message);
    }

    private void warnIfClassAnnotationsDrifted(ArtifactTarget out, File file, String wouldWrite) {
        String cls = out.fileName();
        // With mapping: xml the class says nothing of its table, so an owned
        // class that still does is brought in line - to no annotations.
        if (!wouldWrite.contains("@Entity") && !(xmlMapping() && inDomainModel(out))) return;
        String existing;
        try {
            existing = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return;
        }
        // @behavior taken away: the generator now writes the whole entity
        // here, but the file is the developer's split half and is kept - so
        // the entity's fields stop following the DSL until they decide.
        String base = cls + "Base";
        boolean ownedSplit = existing.contains("extends " + base) || existing.contains(": " + base + "()");
        boolean splitNow = wouldWrite.contains("extends " + base) || wouldWrite.contains(": " + base + "()");
        if (ownedSplit && !splitNow) {
            String message = String.format("[elcore3] %s extends %s, but the DSL no longer splits the entity"
                    + " (@editable added, @behavior removed, or the rule set changed)."
                    + " The entity is not regenerated while this file is yours: move its methods elsewhere and delete it"
                    + " to generate the whole entity here again, or undo the DSL change.", out.relativePath(), base);
            io.elasticore.base.util.ConsoleLog.printWarn(message);
            notes.add(message);
            return;
        }
        java.util.List<String> would = classAnnotationsOf(wouldWrite, cls);
        java.util.List<String> have = classAnnotationsOf(existing, cls);
        // Only the table's annotations are kept in line. One a rule set adds
        // besides - a project's own, like a DDD role marker - is said once
        // missing and never written into the developer's file: comparing it
        // too made the file differ on every run, and writing it put a second
        // copy beside one the developer had already added.
        java.util.List<String> expected = managedOnly(would);
        // Compared as written: a name in full (@org.jmolecules.ddd.annotation.Entity)
        // is not the same annotation as another of its simple name (@Entity).
        java.util.Set<String> haveNames = new java.util.HashSet<String>();
        for (String h : have) haveNames.add(annotationToken(h));
        java.util.List<String> notCarried = new java.util.ArrayList<String>();
        for (String w : would) {
            if (!MANAGED_CLASS_ANNOTATIONS.contains(annotationName(w)) && !haveNames.contains(annotationToken(w))) {
                notCarried.add(w);
            }
        }
        if (!notCarried.isEmpty()) {
            String message = String.format("[elcore3] %s: the rules also put these on the class, which this file"
                            + " does not have - it is yours, so add them by hand if they apply:%n    %s",
                    out.relativePath(), String.join(System.lineSeparator() + "    ", notCarried));
            System.out.println(message);
            notes.add(message);
        }
        // Compared as sets: the developer may order them differently, and a
        // reordering is not a difference worth rewriting their file for.
        // Compared as sorted lists rather than sets: a repeated line is a
        // difference - an annotation written twice does not compile.
        java.util.List<String> want = new java.util.ArrayList<String>(expected);
        java.util.List<String> got = new java.util.ArrayList<String>(managedOnly(have));
        java.util.Collections.sort(want);
        java.util.Collections.sort(got);
        if (want.equals(got)) return;

        String synced = withClassAnnotations(existing, wouldWrite, cls, expected);
        if (synced != null && xmlMapping()) synced = OrmMapping.withoutUnusedPersistenceImports(synced, kotlin);
        // The file keeps the stamp it had. It holds the developer's code, and
        // a stamp taken of it would make the next run read it as the
        // generator's own, untouched output - and write the template over it.
        int eol = existing.indexOf('\n');
        String stamp = existing.startsWith("//ecd:") && eol > 0
                ? existing.substring(0, eol).replace("\r", "") + "\n" : null;
        if (synced != null && stamp != null && writeWithStamp(file, stamp, synced)) {
            String message = String.format("[elcore3] %s: table annotations brought in line with the DSL"
                    + " - the rest of the file, your own annotations included, is as it was", out.relativePath());
            System.out.println(message);
            return;
        }
        String message = String.format("[elcore3] %s: the class annotations differ from what the DSL now says"
                        + " - this file is yours, so update them by hand:%n    %s",
                out.relativePath(), String.join(System.lineSeparator() + "    ", expected));
        System.out.println(message);
        notes.add(message);
    }

    /**
     * The persistence annotations the generator states for an entity class.
     * Anything else above the class is the developer's and is kept.
     */
    private static final java.util.Set<String> MANAGED_CLASS_ANNOTATIONS = new java.util.HashSet<String>(
            java.util.Arrays.asList("Entity", "Table", "DynamicUpdate", "DynamicInsert", "Immutable",
                    "Audited", "Inheritance", "DiscriminatorColumn", "DiscriminatorValue", "IdClass",
                    "PersistentModel"));

    /** The annotation's name as written, qualified or not: "AggregateRoot", "org.jmolecules.ddd.annotation.Entity". */
    private static String annotationToken(String line) {
        String t = line.trim();
        if (!t.startsWith("@")) return null;
        int end = 1;
        while (end < t.length() && (Character.isJavaIdentifierPart(t.charAt(end)) || t.charAt(end) == '.')) end++;
        return t.substring(1, end);
    }

    private static String annotationName(String line) {
        String t = line.trim();
        if (!t.startsWith("@")) return null;
        int end = 1;
        while (end < t.length() && (Character.isJavaIdentifierPart(t.charAt(end)) || t.charAt(end) == '.')) end++;
        String name = t.substring(1, end);
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static java.util.List<String> managedOnly(java.util.List<String> lines) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (String l : lines) {
            if (MANAGED_CLASS_ANNOTATIONS.contains(annotationName(l))) out.add(l);
        }
        return out;
    }

    /**
     * The owned file with the managed class annotations replaced by the ones
     * the DSL now states, the developer's own kept after them, and any import
     * the new ones need added. Null when the block cannot be read safely - an
     * annotation spread over several lines - and a warning is given instead.
     */
    static String withClassAnnotations(String existing, String wouldWrite, String cls, java.util.List<String> expected) {
        String[] lines = existing.split("\\r?\\n", -1);
        int classLine = -1;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.contains("class " + cls + " ") || t.contains("class " + cls + ":")) { classLine = i; break; }
        }
        if (classLine < 0) return null;
        // The block above the class: everything up to a blank line, a
        // statement, or the end of a comment. Each line of it has to be one
        // whole annotation; a continuation line means an annotation spread
        // over several, which is left for the developer.
        int first = classLine;
        while (first > 0) {
            String t = lines[first - 1].trim();
            if (t.isEmpty() || t.endsWith(";") || t.endsWith("*/") || t.startsWith("//") || t.startsWith("*")) break;
            first--;
        }
        java.util.List<String> kept = new java.util.ArrayList<String>();
        for (int i = first; i < classLine; i++) {
            String t = lines[i].trim();
            int open = t.length() - t.replace("(", "").length();
            int close = t.length() - t.replace(")", "").length();
            if (!t.startsWith("@") || open != close) return null;
            if (!MANAGED_CLASS_ANNOTATIONS.contains(annotationName(t))) kept.add(lines[i]);
        }
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> haveImports = new java.util.HashSet<String>();
        for (String l : lines) if (l.trim().startsWith("import ")) haveImports.add(l.trim());
        java.util.List<String> missing = new java.util.ArrayList<String>();
        for (String l : wouldWrite.split("\\r?\\n")) {
            if (!l.trim().startsWith("import ") || haveImports.contains(l.trim())) continue;
            // Only what the annotations written here name: the file would
            // otherwise gain imports for things it does not use.
            String imported = l.trim().replaceAll(";$", "");
            String simple = imported.substring(imported.lastIndexOf('.') + 1);
            boolean used = false;
            for (String e : expected) {
                if (e.contains("@" + simple) || e.contains(simple + "(") || e.contains(simple + ".")) used = true;
            }
            if (used) missing.add(l.trim());
        }
        // New imports join the existing ones - before the first - or, where
        // there are none, follow the package line.
        int firstImport = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("import ")) { firstImport = i; break; }
        }
        boolean importsAdded = missing.isEmpty();
        for (int i = 0; i < lines.length; i++) {
            // The ecd line is the writer's to put back, not the content's.
            if (i == 0 && lines[i].startsWith("//ecd:")) continue;
            if (!importsAdded && i == firstImport) {
                for (String m : missing) sb.append(m).append('\n');
                importsAdded = true;
            }
            if (i == first) {
                for (String a : expected) sb.append(a).append('\n');
                for (String k : kept) sb.append(k).append('\n');
            }
            if (i >= first && i < classLine) continue;
            sb.append(lines[i]);
            if (i < lines.length - 1) sb.append('\n');
            if (!importsAdded && firstImport < 0 && lines[i].trim().startsWith("package ")) {
                sb.append('\n');
                for (String m : missing) sb.append(m).append('\n');
                importsAdded = true;
            }
        }
        return sb.toString();
    }

    /** The annotation lines directly above a class declaration, trimmed. */
    static java.util.List<String> classAnnotationsOf(String source, String className) {
        java.util.List<String> found = new java.util.ArrayList<String>();
        for (String line : source.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("@")) {
                found.add(t);
            } else if (t.contains("class " + className + " ") || t.contains("class " + className + ":")) {
                return found;
            } else if (!t.isEmpty() && !found.isEmpty() && !t.startsWith("//")) {
                found.clear();
            } else if (t.startsWith("/*") || t.startsWith("*")) {
                found.clear();
            }
        }
        return java.util.Collections.emptyList();
    }

    /**
     * A controller of the developer's that the DSL has since given more to
     * serve - a lifecycle command, the sign-in of @signin. The file is left
     * alone, so the endpoint would not exist while the TypeScript client
     * (always regenerated) calls it. Said as a DSL warning, naming each one.
     *
     * Not in the 2.x-compatible mode: it regenerates no generate-once file and
     * has no TypeScript client, so nothing there is new or called.
     */
    private void warnIfEndpointsMissing(ArtifactTarget out, File file, String wouldWrite) {
        if (!(v3 || kotlin)) return;
        if (out.fileName() == null || !out.fileName().endsWith("Controller")) return;
        String existing;
        try {
            existing = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }
        java.util.List<String> missing = missingEndpoints(existing, wouldWrite, out.fileName());
        if (missing.isEmpty()) return;
        io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", out.relativePath() + " is yours and does not serve "
                + String.join(", ", missing) + ", which the DSL gives it - add them as the generator would write"
                + " them (delete the file and build to see that), or the TypeScript client calls endpoints that do not exist");
    }

    /**
     * A controller of the developer's that still signs in where the DSL no
     * longer would: @signin moved to another entity or refused, or a role no
     * longer one only the administrator writes. The file is left alone, so
     * its sign-in keeps starting sessions every generated check trusts - and
     * the build's own @signin warning would read as if nothing signed in.
     */
    private void warnIfSignInLeft(ArtifactTarget out, File file, String wouldWrite) {
        if (out.fileName() == null || !out.fileName().endsWith("Controller")) return;
        String existing;
        try {
            existing = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }
        if (!existing.contains("SessionCurrentUser")) return;
        Endpoints have = Endpoints.of(existing, out.fileName());
        Endpoints given = Endpoints.of(wouldWrite, out.fileName());
        java.util.List<String> left = new java.util.ArrayList<String>();
        for (String endpoint : new String[]{"POST /signin", "GET /me"}) {
            if (have.serves(endpoint) && !given.serves(endpoint)) left.add(endpoint);
        }
        if (!left.isEmpty()) {
            io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", out.relativePath() + " is yours and still serves "
                    + String.join(", ", left) + ", which the DSL does not generate now (the entity no longer says @signin,"
                    + " or a @signin warning says why): the sessions it starts are trusted by every generated access"
                    + " check. Remove them, or give the entity what @signin needs.");
        } else if (passesRole(existing) && !passesRole(wouldWrite)) {
            io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", out.relativePath() + " is yours and still puts a role in"
                    + " the session (SessionCurrentUser.signIn(request, id, role)), which the DSL does not do now: the role"
                    + " is not a field only the administrator writes (@access(admin)), so a member who sets their own"
                    + " role signs in as the administrator. Drop the role argument, or mark the field @access(admin).");
        }
    }

    /** Whether a SessionCurrentUser.signIn call of the source passes a role - a third argument. */
    static boolean passesRole(String source) {
        String call = "SessionCurrentUser.signIn(";
        for (int at = source.indexOf(call); at >= 0; at = source.indexOf(call, at + 1)) {
            int depth = 0;
            int args = 1;
            char quote = 0;
            for (int i = at + call.length(); i < source.length(); i++) {
                char ch = source.charAt(i);
                if (quote != 0) {
                    if (ch == '\\') i++;
                    else if (ch == quote) quote = 0;
                } else if (ch == '"' || ch == '\'') {
                    quote = ch;
                } else if (ch == '(' || ch == '[' || ch == '{') {
                    depth++;
                } else if (ch == ')' || ch == ']' || ch == '}') {
                    if (depth == 0) break;
                    depth--;
                } else if (ch == ',' && depth == 0) {
                    args++;
                }
            }
            if (args >= 3) return true;
        }
        return false;
    }

    /**
     * What the DSL would have the controller serve and the developer's file
     * does not, as "VERB /path" in the generator's own spelling. A mapping of
     * theirs the reader cannot follow (a constant, a concatenation) answers
     * for its method - better unsaid than said wrongly.
     */
    static java.util.List<String> missingEndpoints(String existing, String wouldWrite, String className) {
        Endpoints have = Endpoints.of(existing, className);
        java.util.List<String> missing = new java.util.ArrayList<String>();
        Endpoints given = Endpoints.of(wouldWrite, className);
        for (java.util.Map.Entry<String, String> en : given.mapped.entrySet()) {
            if (!have.mayServe(en.getKey())) missing.add(en.getValue());
        }
        return missing;
    }

    private static final java.util.regex.Pattern MAPPING = java.util.regex.Pattern.compile(
            "@(?:org\\.springframework\\.web\\.bind\\.annotation\\.)?(Get|Post|Put|Delete|Patch|Request)Mapping\\b");
    private static final java.util.regex.Pattern STRING_LITERAL = java.util.regex.Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final java.util.regex.Pattern REQUEST_METHOD = java.util.regex.Pattern.compile(
            "\\b(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS|TRACE)\\b");

    /**
     * The endpoints a controller source maps, read from its method-level
     * mapping annotations: @GetMapping("/x"), path = "/x", value = ["/x"]
     * (Kotlin), {"/x", "/y"}, a bare @PostMapping, and @RequestMapping with
     * or without method = RequestMethod.X. A path is compared with each
     * {placeholder} as {} - /{id} serves what the generator calls
     * /{customerId} - and without a trailing slash. Comments are not read.
     */
    static final class Endpoints {
        /** "VERB /path" normalised -> as written; VERB "*" for a @RequestMapping that takes any method. */
        final java.util.Map<String, String> mapped = new java.util.LinkedHashMap<String, String>();
        /** Methods ("*" for any) of a mapping that could not be read. */
        final java.util.Set<String> unreadable = new java.util.HashSet<String>();

        static Endpoints of(String source, String className) {
            Endpoints found = new Endpoints();
            String code = withoutComments(source);
            int classAt = -1;
            if (className != null) {
                java.util.regex.Matcher cls = java.util.regex.Pattern.compile(
                        "\\bclass\\s+" + java.util.regex.Pattern.quote(className) + "\\b").matcher(code);
                if (cls.find()) classAt = cls.start();
            }
            java.util.regex.Matcher m = MAPPING.matcher(code);
            while (m.find()) {
                // The class's own @RequestMapping is the base path every endpoint shares.
                if (m.start() < classAt) continue;
                String args = argumentsAfter(code, m.end());
                found.read(m.group(1), args);
            }
            return found;
        }

        private void read(String kind, String args) {
            java.util.List<String> paths = new java.util.ArrayList<String>();
            java.util.List<String> verbs = new java.util.ArrayList<String>();
            boolean pathsRead = true;
            boolean verbsRead = true;
            boolean pathSaid = false;
            boolean methodSaid = false;
            if (args != null) {
                for (String element : topLevel(args, ',')) {
                    String e = element.trim();
                    if (e.isEmpty()) continue;
                    int eq = topLevelEquals(e);
                    String key = eq < 0 ? "value" : e.substring(0, eq).trim();
                    String val = eq < 0 ? e : e.substring(eq + 1).trim();
                    if ("value".equals(key) || "path".equals(key)) {
                        pathSaid = true;
                        java.util.List<String> literals = literalsOnly(val);
                        if (literals == null) pathsRead = false;
                        else paths.addAll(literals);
                    } else if ("method".equals(key) && "Request".equals(kind)) {
                        methodSaid = true;
                        java.util.regex.Matcher rm = REQUEST_METHOD.matcher(val);
                        while (rm.find()) verbs.add(rm.group(1));
                        String rest = val.replaceAll("(?:org\\.springframework\\.web\\.bind\\.annotation\\.)?RequestMethod\\.", "")
                                .replaceAll(REQUEST_METHOD.pattern(), "").replace("arrayOf(", "").replaceAll("[\\s\\[\\]{},()]", "");
                        if (verbs.isEmpty() || !rest.isEmpty()) verbsRead = false;
                    }
                }
            }
            // No path is the class's base path itself; @RequestMapping without a method takes any.
            if (!pathSaid) paths.add("");
            if (!"Request".equals(kind)) verbs.add(kind.toUpperCase(java.util.Locale.ROOT));
            else if (!methodSaid) verbs.add("*");
            if (!verbsRead) {
                unreadable.add("*");
            } else if (!pathsRead) {
                unreadable.addAll(verbs);
            } else {
                for (String verb : verbs) {
                    for (String path : paths) mapped.put(verb + " " + normal(path), verb + " " + (path.isEmpty() ? "(base path)" : path));
                }
            }
        }

        /** Whether the source maps this endpoint, read as written. */
        boolean serves(String endpoint) {
            int space = endpoint.indexOf(' ');
            String path = normal(endpoint.substring(space + 1));
            return mapped.containsKey(endpoint.substring(0, space) + " " + path) || mapped.containsKey("* " + path);
        }

        /** Whether it does, or has a mapping for that method the reader could not follow. */
        boolean mayServe(String normalisedEndpoint) {
            String verb = normalisedEndpoint.substring(0, normalisedEndpoint.indexOf(' '));
            return serves(normalisedEndpoint) || unreadable.contains(verb) || unreadable.contains("*");
        }

        /** {anything} as {}, no trailing slash, a leading one. */
        static String normal(String path) {
            StringBuilder sb = new StringBuilder();
            int depth = 0;
            for (int i = 0; i < path.length(); i++) {
                char ch = path.charAt(i);
                if (ch == '{') {
                    if (depth++ == 0) sb.append("{}");
                } else if (ch == '}') {
                    if (depth > 0) depth--;
                } else if (depth == 0) {
                    sb.append(ch);
                }
            }
            String p = sb.toString().trim();
            while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
            return p.isEmpty() || p.startsWith("/") ? p : "/" + p;
        }

        /** The string literals of a value that is nothing else - "x", {"x", "y"}, ["x"], arrayOf("x"); null otherwise. */
        private static java.util.List<String> literalsOnly(String val) {
            java.util.List<String> literals = new java.util.ArrayList<String>();
            java.util.regex.Matcher lit = STRING_LITERAL.matcher(val);
            StringBuilder rest = new StringBuilder();
            int last = 0;
            while (lit.find()) {
                // A Kotlin template ("$base/x") is not a literal path.
                if (lit.group(1).contains("$")) return null;
                literals.add(lit.group(1));
                rest.append(val, last, lit.start());
                last = lit.end();
            }
            rest.append(val.substring(last));
            String left = rest.toString().replace("arrayOf(", "").replaceAll("[\\s\\[\\]{},()]", "");
            return left.isEmpty() ? literals : null;
        }

        /** The text between the parentheses that follow position at, or null for a bare annotation. */
        private static String argumentsAfter(String code, int at) {
            int i = at;
            while (i < code.length() && Character.isWhitespace(code.charAt(i)) && code.charAt(i) != '\n') i++;
            if (i >= code.length() || code.charAt(i) != '(') return null;
            int depth = 0;
            char quote = 0;
            for (int j = i; j < code.length(); j++) {
                char ch = code.charAt(j);
                if (quote != 0) {
                    if (ch == '\\') j++;
                    else if (ch == quote) quote = 0;
                } else if (ch == '"' || ch == '\'') {
                    quote = ch;
                } else if (ch == '(' || ch == '[' || ch == '{') {
                    depth++;
                } else if (ch == ')' || ch == ']' || ch == '}') {
                    if (--depth == 0) return code.substring(i + 1, j);
                }
            }
            return null;
        }

        /** The parts of s between top-level separators - outside quotes and brackets. */
        private static java.util.List<String> topLevel(String s, char separator) {
            java.util.List<String> parts = new java.util.ArrayList<String>();
            int depth = 0;
            char quote = 0;
            int start = 0;
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (quote != 0) {
                    if (ch == '\\') i++;
                    else if (ch == quote) quote = 0;
                } else if (ch == '"' || ch == '\'') {
                    quote = ch;
                } else if (ch == '(' || ch == '[' || ch == '{') {
                    depth++;
                } else if (ch == ')' || ch == ']' || ch == '}') {
                    depth--;
                } else if (ch == separator && depth == 0) {
                    parts.add(s.substring(start, i));
                    start = i + 1;
                }
            }
            parts.add(s.substring(start));
            return parts;
        }

        /** The index of a named argument's '=', or -1 when the element is positional. */
        private static int topLevelEquals(String element) {
            java.util.regex.Matcher named = java.util.regex.Pattern.compile("^(\\w+)\\s*=").matcher(element);
            return named.find() ? named.end() - 1 : -1;
        }

        /** The source without // and block comments, string literals kept. */
        static String withoutComments(String source) {
            StringBuilder sb = new StringBuilder(source.length());
            char quote = 0;
            for (int i = 0; i < source.length(); i++) {
                char ch = source.charAt(i);
                char next = i + 1 < source.length() ? source.charAt(i + 1) : 0;
                if (quote != 0) {
                    sb.append(ch);
                    if (ch == '\\' && next != 0) {
                        sb.append(next);
                        i++;
                    } else if (ch == quote) {
                        quote = 0;
                    }
                } else if (ch == '"' || ch == '\'') {
                    quote = ch;
                    sb.append(ch);
                } else if (ch == '/' && next == '/') {
                    while (i < source.length() && source.charAt(i) != '\n') i++;
                    sb.append('\n');
                } else if (ch == '/' && next == '*') {
                    int end = source.indexOf("*/", i + 2);
                    i = end < 0 ? source.length() : end + 1;
                    sb.append(' ');
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }
    }

    private void warnIfConstructorDrifted(ArtifactTarget out, File file, String wouldWrite) {
        java.util.List<String> expected = new java.util.ArrayList<String>();
        for (String line : wouldWrite.split("\\r?\\n")) {
            String args = superArguments(line);
            if (args != null) expected.add(args);
        }
        if (expected.isEmpty()) return;

        String existing;
        try {
            existing = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return;
        }
        java.util.Set<String> have = new java.util.HashSet<String>();
        for (String line : existing.split("\\r?\\n")) {
            String args = superArguments(line);
            if (args != null) have.add(args);
        }
        // A file that calls no core-service constructor at all is not one this can judge.
        if (have.isEmpty()) return;

        boolean drifted = false;
        for (String args : expected) {
            if (!have.contains(args)) drifted = true;
        }
        if (drifted && untouchedApartFromWiring(existing, wouldWrite, out.fileName())) {
            // Nothing in it is the developer's yet - the first run that wrote
            // it failed or ran in another mode - so the file is simply written
            // again, still generate-once, rather than left for a hand fix.
            if (rewriteOwned(file, wouldWrite)) {
                String msg = out.relativePath() + ": constructor was out of date and the file was never edited"
                        + " - written again with (" + expected.get(0) + ")";
                System.out.println("[elcore3] " + msg);
            }
            return;
        }
        for (String args : expected) {
            if (have.contains(args)) continue;
            String msg = out.relativePath() + ": constructor out of date with its core service - "
                    + "the generator now calls it with (" + args + "); update this file by hand "
                    + "(it is yours, so it is not rewritten). See the rule guide, repository ports.";
            notes.add(msg);
            io.elasticore.base.util.ConsoleLog.printWarn("[elcore3] " + msg);
        }
    }

    /**
     * Whether an owned file holds nothing of the developer's beyond what the
     * generator would write now, its constructor wiring and imports aside:
     * the code lines outside the constructor are the same.
     */
    static boolean untouchedApartFromWiring(String existing, String wouldWrite, String className) {
        java.util.List<String> mine = codeOutsideConstructor(existing, className);
        return mine != null && mine.equals(codeOutsideConstructor(wouldWrite, className));
    }

    /**
     * The parameters a generated extension's constructor ever takes: the
     * repository helper of the compatible mode, or the ports and the entity
     * manager of the hexagonal one. A constructor naming anything else was
     * given a dependency by the developer and is not the generator's.
     */
    private static final java.util.Set<String> WIRING_PARAMETERS = new java.util.HashSet<String>(
            java.util.Arrays.asList("helper", "repository", "query", "entityManager"));

    /**
     * The file's lines with only the constructor's wiring taken out - its
     * signature and its super(...) call, or Kotlin's constructor header -
     * and null when that wiring names anything but the generator's own
     * parameters. Everything else, comments and the constructor's own body
     * included, has to match for the file to count as untouched.
     */
    private static java.util.List<String> codeOutsideConstructor(String source, String className) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        String[] lines = source.split("\\r?\\n");
        boolean inKotlinHeader = false;
        StringBuilder header = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("//ecd:")) continue;
            if (inKotlinHeader) {
                header.append(t).append(' ');
                if (t.startsWith(")")) {
                    inKotlinHeader = false;
                    if (!onlyWiring(header.toString())) return null;
                }
                continue;
            }
            if (t.startsWith("public " + className + "(")) {
                if (!onlyWiring(t)) return null;
                continue;
            }
            if (t.startsWith("super(")) continue;
            if (t.contains("class " + className + "(")) {
                header.setLength(0);
                header.append(t).append(' ');
                inKotlinHeader = !t.contains(") :") && !t.endsWith(") {");
                if (!inKotlinHeader && !onlyWiring(t)) return null;
                continue;
            }
            if (t.isEmpty() || t.startsWith("import ")) continue;
            out.add(t.replaceAll("\\s+", " "));
        }
        return out;
    }

    /** Whether a constructor's parameter list names only the generator's wiring. */
    private static boolean onlyWiring(String signature) {
        int open = signature.indexOf('(');
        int close = signature.indexOf(')', open + 1);
        if (open < 0) return false;
        String params = close < 0 ? signature.substring(open + 1) : signature.substring(open + 1, close);
        for (String p : params.split(",")) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            // Java "Type name", Kotlin "name: Type" (maybe "private val name: Type")
            String name;
            int colon = t.indexOf(':');
            if (colon >= 0) {
                String[] words = t.substring(0, colon).trim().split("\\s+");
                name = words[words.length - 1];
            } else {
                String[] words = t.split("\\s+");
                name = words[words.length - 1];
            }
            if (!WIRING_PARAMETERS.contains(name)) return false;
        }
        return true;
    }

    /**
     * Whether an owned file is still byte for byte what the generator wrote:
     * the hash on its first line was taken of the content when it was
     * written, and the content still has it. A file without that line, or
     * with custom blocks added, is taken as edited.
     */
    static boolean neverEdited(HashUtils.Response st) {
        if (st == null || st.getEcd() == null || st.getOldEcdLine() == null) return false;
        if (st.hasCustomizedScopeContent()) return false;
        try {
            return HashUtils.checkHashCode(st.getPreSourceContent(), st.getEcd(),
                    HashUtils.getVersion(st.getOldEcdLine()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Writes content under a given first line (the ecd stamp it already had). */
    private boolean writeWithStamp(File file, String stamp, String content) {
        Writer w = null;
        try {
            w = FileBasedSourceFileAccessFactory.newPlatformLineWriter(file);
            w.write(stamp.endsWith("\n") ? stamp : stamp + "\n");
            w.write(content);
            return true;
        } catch (IOException e) {
            notes.add(file + ": " + e);
            return false;
        } finally {
            if (w != null) {
                try { w.close(); } catch (IOException ignore) { }
            }
        }
    }

    /** Writes an owned file again, keeping it owned (the template carries the marker). */
    private boolean rewriteOwned(File file, String content) {
        // A merged file has no stamp to keep: the cache holds what was written.
        String key = cache != null ? cacheKey(file) : null;
        if (key != null && cache.get(key) != null) {
            String text = content.replace("\r\n", "\n");
            if (!writeLf("  " + key, file, text)) return false;
            cache.put(key, text);
            return true;
        }
        Writer w = null;
        try {
            w = FileBasedSourceFileAccessFactory.newPlatformLineWriter(file);
            w.write(HashUtils.makeEcdCode(content));
            w.write(content);
            return true;
        } catch (IOException e) {
            notes.add(file + ": " + e);
            return false;
        } finally {
            if (w != null) {
                try { w.close(); } catch (IOException ignore) { }
            }
        }
    }

    /**
     * The arguments a line hands to the core service's constructor - Java's
     * super(...) or Kotlin's ") : XCoreService(...)" - with the spacing and
     * whatever follows (an implements list, a brace) taken away, so a
     * reformatted or extended line still compares by what it passes.
     */
    static String superArguments(String line) {
        String t = line.trim();
        int start;
        if (t.startsWith("super(")) {
            start = "super(".length();
        } else if (t.startsWith(") : ") && t.contains("CoreService(")) {
            start = t.indexOf("CoreService(") + "CoreService(".length();
        } else {
            return null;
        }
        int depth = 1;
        for (int i = start; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == '(') depth++;
            else if (ch == ')' && --depth == 0) return t.substring(start, i).replaceAll("\\s+", "");
        }
        return null;
    }

    // ---- mapping: xml ------------------------------------------------------

    /** The JPA mapping moved out of the domain classes, per module root. */
    private final Map<String, OrmMapping> ormByRoot = new java.util.LinkedHashMap<String, OrmMapping>();

    /**
     * A run over some domains only (-Pdomain): the domain model packages of the
     * domains it leaves out, whose classes the module's orm.xml keeps. Null for
     * a whole run.
     */
    private java.util.Set<String> otherDomainPackages;

    /** Whether the domain being generated keeps its JPA mapping in orm.xml. */
    private boolean xmlMapping() {
        return currentModel != null && !typescript && (v3 || kotlin)
                && "xml".equalsIgnoreCase(currentModel.getConfig("mapping", ""));
    }

    private OrmMapping orm(ArtifactTarget out) {
        String root = rootFor(out);
        OrmMapping m = ormByRoot.get(root);
        if (m == null) {
            m = new OrmMapping();
            ormByRoot.put(root, m);
        }
        return m;
    }

    /** Where the persistence side of the domain goes: the persistence namespace, else the repository's. */
    private String persistencePackage() {
        String pkg = currentModel.getNamespace("persistence");
        if (pkg == null || pkg.isEmpty()) pkg = currentModel.getNamespace("repository");
        return pkg;
    }

    /**
     * Where the query metadata class Q goes. It is written in JPA's terms
     * (Specification, criteria), so with mapping: xml it leaves the domain
     * model for the persistence side.
     */
    private String queryPackage(ECoreModel model) {
        return xmlMapping() ? persistencePackage() : model.getNamespace("entity");
    }

    /** The Q class, rendered for the entity package, moved to the one it is written to. */
    private String movedQuery(ArtifactTarget out, String content) {
        String entityPkg = currentModel.getNamespace("entity");
        if (!"Q".equals(out.fileName()) || entityPkg == null || entityPkg.equals(out.packageName())) return content;
        String semi = kotlin ? "" : ";";
        String from = "package " + entityPkg + semi;
        int at = content.indexOf(from);
        if (at < 0) return content;
        String imports = "\n\nimport " + entityPkg + ".*" + semi;
        String enumPkg = currentModel.getNamespace("enumeration");
        if (enumPkg != null && !enumPkg.equals(entityPkg)) imports += "\nimport " + enumPkg + ".*" + semi;
        return content.substring(0, at) + "package " + out.packageName() + semi + imports
                + content.substring(at + from.length());
    }

    /** A class of the domain model package: the entity or the enumeration namespace. */
    private boolean inDomainModel(ArtifactTarget out) {
        String pkg = out.packageName();
        if (pkg == null) return false;
        return pkg.equals(currentModel.getNamespace("entity")) || pkg.equals(currentModel.getNamespace("enumeration"));
    }

    /**
     * src/main/resources/i18n/<domain>-messages.properties: every message the
     * domain's API can answer with, by code, in its default wording - beside
     * the error handler that reads it (same module). Regenerated with the model;
     * a translation is a file beside it (..._ko.properties), never this one.
     */
    private void writeMessages(String domainName, ArtifactTarget handler) {
        if (typescript || captured != null) return;
        String text = io.elasticore.base.model.pub.rules.ErrorCatalog.properties(domainName,
                io.elasticore.base.model.pub.rules.ErrorCatalog.shared(),
                io.elasticore.base.model.pub.rules.ErrorCatalog.recorded());
        File file = new File(rootFor(handler) + "/src/main/resources/i18n/" + domainName + "-messages.properties");
        String label = String.format("    %-12s %s", "messages", relative(file));
        String key = cache != null ? cacheKey(file) : null;
        if (key != null) {
            writeMerged(label, file, key, text, true);
            return;
        }
        if (file.isFile()) {
            try {
                String existing = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                if (equalsIgnoringLineEndings(existing, text)) {
                    unchanged++;
                    System.out.println(label + "  UNCHANGED");
                    return;
                }
            } catch (IOException e) {
                // unreadable: write it again
            }
        }
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        try {
            java.nio.file.Files.write(file.toPath(), io.elasticore.base.model.pub.rules.ErrorCatalog.bytes(text));
            wrote++;
            System.out.println(label + "  WROTE");
        } catch (IOException e) {
            failed++;
            notes.add(file + ": " + e.getMessage());
        }
    }

    /** Writes each module's orm.xml. Not ours to overwrite if someone else wrote it. */
    private void writeOrmXml() {
        for (Map.Entry<String, OrmMapping> en : ormByRoot.entrySet()) {
            OrmMapping m = en.getValue();
            for (String w : m.warnings()) {
                String message = "[elcore3] mapping: xml - " + w;
                io.elasticore.base.util.ConsoleLog.printWarn(message);
                notes.add(message);
            }
            if (m.isEmpty()) continue;
            File file = new File(en.getKey() + "/src/main/resources/META-INF/orm.xml");
            String key = cache != null ? cacheKey(file) : null;
            if (key != null) {
                // An orm.xml of the developer's own - not the generator's, not
                // in the cache - keeps its place, and the mapping goes beside it.
                if (file.isFile() && cache.get(key) == null && !ownOrmXml(file)) {
                    file = new File(en.getKey() + "/src/main/resources/META-INF/elasticore-orm.xml");
                    key = cacheKey(file);
                    notes.add("META-INF/orm.xml is yours, so the mapping went to META-INF/elasticore-orm.xml:"
                            + " add spring.jpa.mapping-resources=META-INF/elasticore-orm.xml");
                }
                // The other domains' classes as last generated: the merge then
                // sees no change to them, and the developer's edits stay.
                String previous = null;
                if (otherDomainPackages != null) {
                    previous = cache.get(key);
                    if (previous == null && file.isFile() && ownOrmXml(file)) previous = readLf(file);
                }
                writeMerged("  " + relative(file), file, key, m.toXml(previous, otherDomainPackages), true);
                continue;
            }
            String xml = m.toXml();
            if (file.isFile()) {
                String existing;
                try {
                    existing = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    existing = "";
                }
                if (!existing.contains("Generated by ElastiCORE (mapping: xml)")) {
                    file = new File(en.getKey() + "/src/main/resources/META-INF/elasticore-orm.xml");
                    notes.add("META-INF/orm.xml is yours, so the mapping went to META-INF/elasticore-orm.xml:"
                            + " add spring.jpa.mapping-resources=META-INF/elasticore-orm.xml");
                    if (otherDomainPackages != null && file.isFile() && ownOrmXml(file)) {
                        xml = m.toXml(readLf(file), otherDomainPackages);
                    }
                } else {
                    if (otherDomainPackages != null) xml = m.toXml(existing, otherDomainPackages);
                    if (equalsIgnoringLineEndings(existing, xml)) {
                        unchanged++;
                        System.out.println("  " + relative(file) + "  UNCHANGED");
                        continue;
                    }
                }
            }
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            Writer w = null;
            try {
                w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), StandardCharsets.UTF_8);
                w.write(xml);
                wrote++;
                System.out.println("  " + relative(file) + "  WROTE");
            } catch (IOException e) {
                failed++;
                notes.add(file + ": " + e.getMessage());
            } finally {
                if (w != null) {
                    try { w.close(); } catch (IOException ignore) { }
                }
            }
        }
    }

    /** Whether an orm.xml is one this generator wrote: it says so in its comment. */
    private static boolean ownOrmXml(File file) {
        String text = readLf(file);
        return text != null && text.contains("Generated by ElastiCORE (mapping: xml)");
    }

    /** Every file this run produced or considered, and the directories they are in. */
    private final java.util.Set<String> produced = new java.util.HashSet<String>();
    private final java.util.Set<File> generatedDirs = new java.util.LinkedHashSet<File>();

    private void remember(File file) {
        try {
            produced.add(file.getCanonicalPath());
        } catch (IOException e) {
            produced.add(file.getAbsolutePath());
        }
        if (file.getParentFile() != null) generatedDirs.add(file.getParentFile());
    }

    /**
     * Removes what the generator wrote and no longer would.
     *
     * A file the DSL stopped asking for - the invariants of a root whose last
     * bound moved into a value type, the command DTO of a command renamed -
     * stayed on disk, still compiled against a base that had changed shape,
     * and broke the build on nothing the developer did. Only a file that is
     * provably the generator's is removed: a 3.0 hash line, not handed over
     * ("Initially generated"), and still byte for byte what was written. One
     * that was edited is reported instead.
     *
     * Only after a run over every domain: a run over one cannot tell another
     * domain's file from a stale one.
     */
    private void removeStale() {
        Map<File, String> unedited = new java.util.LinkedHashMap<File, String>();
        for (File dir : generatedDirs) {
            // Only a directory this run is the one generator of: the project's
            // own, or a module the run was told about. A module a namespace
            // redirects into may be generated by another run as well, which
            // this one cannot tell from a stale file.
            if (!ownsDirectory(dir)) continue;
            File[] files = FileOrder.list(dir);
            if (files == null) continue;
            for (File f : files) {
                String name = f.getName();
                if (!f.isFile() || !(name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".ts")
                        || name.endsWith("-http-services.properties")
                        // a MyBatis mapper and the schema beside it; a file of the
                        // developer's has no stamp and is passed over below
                        || name.endsWith(".xml") || name.matches("(.*-)?schema(-[a-z0-9]+)?\\.sql"))) continue;
                String path;
                try {
                    path = f.getCanonicalPath();
                } catch (IOException e) {
                    path = f.getAbsolutePath();
                }
                if (produced.contains(path)) continue;
                // A base a developer's class still extends is not stale: the
                // class is theirs and kept, and removing its parent breaks it.
                // A merging run asks removeUnused instead, which lets a base go
                // with a class that is going too.
                if (cache == null && stillExtended(f)) continue;
                HashUtils.Response st = readStatus(f);
                if (st == null || st.getOldEcdLine() == null || st.getStatus() == HashUtils.UNTRACKED
                        || st.getStatus() == HashUtils.NO_ECD) continue;
                String version;
                try {
                    version = HashUtils.getVersion(st.getOldEcdLine());
                } catch (RuntimeException e) {
                    continue;
                }
                if (version == null || !version.trim().startsWith("3")) continue;
                String rel = relative(f);
                if (neverEdited(st)) {
                    unedited.put(f, rel);
                } else {
                    String message = "[elcore3] " + rel + " was generated, the DSL no longer produces it,"
                            + " and it has been edited - delete it, or move what you wrote.";
                    io.elasticore.base.util.ConsoleLog.printWarn(message);
                    notes.add(message);
                }
            }
        }
        // Removed by removeUnused, together with what removeStaleMerged finds.
        for (Map.Entry<File, String> en : unedited.entrySet()) staleStamped.put(canonical(en.getKey()), en.getValue());
    }

    /** Unedited stale files, by canonical file: stamped ones (removeStale) with their path, merged ones with their cache key. */
    private final Map<File, String> staleStamped = new java.util.LinkedHashMap<File, String>();
    private final Map<File, String> staleMerged = new java.util.LinkedHashMap<File, String>();

    /**
     * The unedited stale files both passes found go - but, in a merging run,
     * not one a kept source still uses, nor a file named like a class kept so
     * (the mapper XML of a kept mapper). It stays, and stays known, and goes
     * on a later run once nothing uses it. The code 2.x generates (no merge)
     * tidies up as it always has.
     */
    private void removeUnused() {
        java.util.Set<File> all = new java.util.LinkedHashSet<File>(staleStamped.keySet());
        all.addAll(staleMerged.keySet());
        Map<File, File> used = cache != null ? stillUsed(all) : new java.util.HashMap<File, File>();
        Map<String, File> keptNames = new java.util.HashMap<String, File>();
        for (Map.Entry<File, File> en : used.entrySet()) keptNames.put(baseName(en.getKey()), en.getValue());
        int removed = 0;
        int removedMerged = 0;
        for (File f : all) {
            boolean merged = staleMerged.containsKey(f);
            String label = merged ? staleMerged.get(f) : staleStamped.get(f);
            File user = used.get(f);
            String n = f.getName();
            if (user == null && !n.endsWith(".java") && !n.endsWith(".kt")) user = keptNames.get(baseName(f));
            if (user != null) {
                keptInUse(label, user);
                continue;
            }
            if (!f.delete()) continue;
            System.out.println("  " + label + "  REMOVED(the DSL no longer produces it)");
            if (merged) {
                cache.remove(label);
                removedMerged++;
            } else {
                removed++;
            }
        }
        if (removed > 0) notes.add(removed + " generated file(s) the DSL no longer produces were removed.");
        if (removedMerged > 0) notes.add(removedMerged + " merged file(s) the DSL no longer produces were removed.");
    }

    private static String baseName(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? n : n.substring(0, dot);
    }

    /**
     * Of the stale generated classes about to be removed, the ones a kept
     * source still uses, each with one such source. Removing those breaks the
     * build: a class of the developer's - or one generated by an older bundle -
     * that names a class the DSL no longer produces. A source counts when it
     * names the class and can see it: same package, an import of it or of its
     * package, or its full name. Sources that are themselves going are not
     * counted, so a dropped entity's files still go together.
     */
    Map<File, File> stillUsed(java.util.Collection<File> stale) {
        Map<File, File> used = new java.util.LinkedHashMap<File, File>();
        java.util.Set<File> going = new java.util.HashSet<File>();
        for (File f : stale) {
            if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")) going.add(canonical(f));
        }
        if (going.isEmpty()) return used;
        Map<File, String> sources = projectSources();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (File f : new ArrayList<File>(going)) {
                String text = sources.get(f);
                if (text == null) text = readLf(f);
                if (text == null) continue;
                String name = f.getName();
                String cls = name.substring(0, name.lastIndexOf('.'));
                String pkg = packageOf(text);
                java.util.regex.Pattern word = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(cls) + "\\b");
                for (Map.Entry<File, String> src : sources.entrySet()) {
                    File s = src.getKey();
                    if (going.contains(s)) continue;
                    String t = src.getValue();
                    if (!word.matcher(t).find()) continue;
                    boolean sees = pkg.equals(packageOf(t))
                            || (!pkg.isEmpty() && (t.contains(pkg + "." + cls) || t.contains("import " + pkg + ".*")));
                    if (!sees) continue;
                    // Kept: and from now on a source that can keep others.
                    used.put(f, s);
                    going.remove(f);
                    changed = true;
                    break;
                }
            }
        }
        Map<File, File> byGiven = new java.util.LinkedHashMap<File, File>();
        for (File f : stale) {
            File user = used.get(canonical(f));
            if (user != null) byGiven.put(f, user);
        }
        return byGiven;
    }

    private void keptInUse(String rel, File user) {
        String message = "[elcore3] " + rel + " is no longer generated, but " + relative(user)
                + " still uses it - kept; it goes on a later run once nothing uses it.";
        io.elasticore.base.util.ConsoleLog.printWarn(message);
        notes.add(message);
    }

    /** The Java and Kotlin sources under src of the project and its modules, by canonical file. */
    private Map<File, String> projectSources() {
        java.util.Set<File> roots = new java.util.LinkedHashSet<File>();
        roots.add(canonical(new File(projectRoot, "src")));
        if (domainRoot != null) roots.add(canonical(new File(domainRoot, "src")));
        for (String r : moduleRoots.values()) roots.add(canonical(new File(r, "src")));
        Map<File, String> sources = new java.util.LinkedHashMap<File, String>();
        for (File root : roots) {
            if (!root.isDirectory()) continue;
            try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(root.toPath())) {
                java.util.Iterator<java.nio.file.Path> it = walk.iterator();
                while (it.hasNext()) {
                    File f = it.next().toFile();
                    String n = f.getName();
                    if (!f.isFile() || !(n.endsWith(".java") || n.endsWith(".kt"))) continue;
                    File c = canonical(f);
                    if (sources.containsKey(c)) continue;
                    String text = readLf(f);
                    if (text != null) sources.put(c, text);
                }
            } catch (IOException | java.io.UncheckedIOException e) {
                // what could be read is used; a source missed only lets a file go
            }
        }
        return sources;
    }

    private static String packageOf(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)").matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private static File canonical(File f) {
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return f.getAbsoluteFile();
        }
    }

    private boolean ownsDirectory(File dir) {
        String d = dir.getAbsolutePath().replace('\\', '/');
        java.util.List<String> roots = new java.util.ArrayList<String>();
        roots.add(new File(projectRoot).getAbsolutePath());
        if (domainRoot != null) roots.add(new File(domainRoot).getAbsolutePath());
        for (String r : moduleRoots.values()) roots.add(new File(r).getAbsolutePath());
        // Without a module list, a redirected module is someone else's too.
        boolean multiModule = !moduleRoots.isEmpty();
        String project = roots.get(0).replace('\\', '/');
        for (String r : roots) {
            String root = r.replace('\\', '/');
            if (!multiModule && !root.equals(project)) continue;
            if (!d.startsWith(root + "/")) continue;
            // A module nested inside the project root is only the project's
            // when the run knows the modules.
            if (!multiModule && root.equals(project)) {
                String rest = d.substring(root.length() + 1);
                if (!rest.startsWith("src/")) return false;
            }
            return true;
        }
        return false;
    }

    /** Whether f is XxxBase and a kept Xxx beside it still extends it. */
    static boolean stillExtended(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String cls = name.substring(0, dot);
        if (!cls.endsWith("Base")) return false;
        File sibling = new File(f.getParentFile(), cls.substring(0, cls.length() - 4) + name.substring(dot));
        if (!sibling.isFile()) return false;
        try {
            String s = new String(java.nio.file.Files.readAllBytes(sibling.toPath()), StandardCharsets.UTF_8);
            return s.contains("extends " + cls) || s.contains(": " + cls + "(");
        } catch (IOException e) {
            return true;
        }
    }

    private String relative(File f) {
        String p = f.getAbsolutePath().replace('\\', '/');
        String root = new File(projectRoot).getAbsolutePath().replace('\\', '/');
        if (p.startsWith(root + "/")) return p.substring(root.length() + 1);
        // A canonical file (stillUsed's) under a root given another way: through
        // a link, /var for /private/var on macOS, an 8.3 short name on Windows.
        String c = canonical(f).getPath().replace('\\', '/');
        String croot = canonical(new File(projectRoot)).getPath().replace('\\', '/');
        return c.startsWith(croot + "/") ? c.substring(croot.length() + 1) : p;
    }

    /** What was generated last (elasticore.merge), or null when the project does not merge. */
    private GeneratedCache cache;

    /** The run of this JVM that holds a cache, for run() to save it whatever ends the run. */
    private static RulesGenRunner merging;

    /** While twoXBases runs: what each file would be, by cache key, instead of writing it. */
    private static Map<String, String> captured;

    /** For a project moving from the code 2.x generates: that code, by cache key - bases to confirm by hash. */
    private Map<String, String> upgradeBases;

    /**
     * The code 2.x generates for this project as its model is now, by cache
     * key: a run of the 2.x bundle inside this run that writes nothing. Its
     * texts are bases only where a stamp's hash says so (confirmedBase) - the
     * code a 2.x project was generated with, before the developer edited it.
     */
    private static Map<String, String> twoXBases() {
        String[] keys = {"elasticore.rules.v2", "elasticore.rules.v3", "elasticore.architecture", "elasticore.language",
                "elasticore.merge", "elasticore.ddl.databases", "elasticore.ddl.migration", "elasticore.ddl.only",
                "elasticore.rules.ts", "elasticore.rules.kt", "elasticore.rules.list"};
        Map<String, String> saved = new java.util.HashMap<String, String>();
        for (String k : keys) saved.put(k, System.getProperty(k));
        Object logs = io.elasticore.base.util.ConsoleLog.snapshot();
        List<String> failures = io.elasticore.base.model.pub.rules.ModelCheck.failures();
        java.io.PrintStream out = System.out;
        java.io.PrintStream err = System.err;
        Map<String, String> found = new java.util.HashMap<String, String>();
        try {
            for (String k : keys) System.clearProperty(k);
            System.setProperty("elasticore.rules.v2", "true");
            System.setProperty("elasticore.merge", "false");
            java.io.PrintStream quiet = new java.io.PrintStream(new java.io.ByteArrayOutputStream());
            System.setOut(quiet);
            System.setErr(quiet);
            captured = found;
            generateAll(new String[0]);
        } catch (RuntimeException e) {
            found.clear();
        } finally {
            captured = null;
            System.setOut(out);
            System.setErr(err);
            for (String k : keys) {
                if (saved.get(k) == null) System.clearProperty(k);
                else System.setProperty(k, saved.get(k));
            }
            io.elasticore.base.util.ConsoleLog.restore(logs);
            io.elasticore.base.model.pub.rules.ModelCheck.reset();
            for (String f : failures) io.elasticore.base.model.pub.rules.ModelCheck.failSaid(f);
            SpecFactory.applicationSignIns(-1, null);
            SpecFactory.compatibleServer(false);
            // Said once per run: what that run said, this one says again.
            io.elasticore.base.model.pub.rules.SourceCode.resetWarnings();
        }
        return found;
    }

    /** The cache entries this run wrote or kept - the rest are stale after a whole run. */
    private final java.util.Set<String> mergedKeys = new java.util.HashSet<String>();

    /** Sources this merging run left to their stamp: edited, and their base not confirmed. */
    private final java.util.Set<String> stampKept = new java.util.TreeSet<String>();

    /** Sources a merging run found without a stamp and outside the cache: no base, so left as they are. */
    private final java.util.Set<String> unbased = new java.util.TreeSet<String>();

    /** How the merge went, for the summary: merged with edits, edits kept as they are, conflicts. */
    private int mergedWithEdits;
    private int keptAsYours;
    private int conflicts;

    /**
     * Whether this run merges: the 3.0 code does unless the build said
     * merge = false (elasticore.merge); the code 2.x generates keeps its
     * stamps, and stays byte for byte what 2.x wrote, unless it was asked.
     */
    static boolean merges(boolean v3, boolean kotlin) {
        String said = System.getProperty("elasticore.merge");
        if (said == null || said.trim().isEmpty()) return v3 || kotlin;
        return Boolean.parseBoolean(said.trim());
    }

    /**
     * Whether a file is merged as text rather than as a source: every file
     * but the Java and Kotlin sources, which mergesSource takes.
     */
    private boolean merges(ArtifactTarget out) {
        return cache != null && !"java".equals(out.extension()) && !"kt".equals(out.extension());
    }

    /** Whether a Java or Kotlin source is merged: by its imports as a set and its lines (ThreeWayMerge.mergeSource). */
    private boolean mergesSource(ArtifactTarget out) {
        return cache != null && ("java".equals(out.extension()) || "kt".equals(out.extension()));
    }

    /** The generate-once marker a skeleton carries: the developer's from the first write. */
    private static final String OWNED_MARKER = "// Initially generated by ElastiCORE;";

    private static boolean owned(String content) {
        int at = content.indexOf(OWNED_MARKER);
        return at >= 0 && at < 300;
    }

    /**
     * A Java or Kotlin source, merged. True when it was dealt with; false
     * leaves it to the stamp's rules (write), which is what happens to a file
     * whose base cannot be known - stamped, edited, and no generation of the
     * generator this run has, or of the code 2.x generates, is what its
     * stamp's hash says was written.
     *
     * A generated file is merged as any other, conflicts failing the build. A
     * skeleton (generate-once) follows the DSL until it is edited, and after
     * that takes the DSL's changes where they merge cleanly; where they do
     * not, the developer's file is left as it is and the warnings a file of
     * theirs has always had say what the DSL now wants - never conflict
     * marks in code that is the developer's own.
     */
    private boolean writeMergedSource(String label, ArtifactTarget out, File file, String key, String content) {
        String generated = content.replace("\r\n", "\n");
        boolean skeleton = owned(generated);
        String base = cache.get(key);
        String disk = null;
        if (file.isFile()) {
            disk = readLf(file);
            if (disk == null) return false;
        }
        if (disk == null) {
            mergedKeys.add(key);
            if (writeLf(label, file, generated)) {
                cache.put(key, generated);
                wrote++;
                System.out.println(label + "  WROTE");
            }
            return true;
        }
        boolean stamped = false;
        if (base == null) {
            HashUtils.Response st = readStatus(file);
            if (st == null || st.getEcd() == null || st.getPreSourceContent() == null) {
                // No stamp: the developer's - unless it is exactly what is generated now.
                if (!disk.equals(generated)) return false;
                base = disk;
            } else {
                int eol = disk.indexOf('\n');
                String stamp = eol < 0 ? disk : disk.substring(0, eol);
                String stripped = disk.substring(eol + 1);
                if (st.getStatus() == HashUtils.NO_MODIFIED
                        || (st.getStatus() == HashUtils.UNTRACKED && neverEdited(st))) {
                    base = st.getPreSourceContent().replace("\r\n", "\n");
                } else {
                    base = confirmedBase(stamp, st.getEcd(), generated,
                            upgradeBases == null ? null : upgradeBases.get(key));
                    if (base == null) return false;
                }
                disk = stripped;
                stamped = true;
            }
        }
        mergedKeys.add(key);
        if (disk.equals(base)) {
            if ((stamped || !disk.equals(generated)) && !writeLf(label, file, generated)) return true;
            cache.put(key, generated);
            if (disk.equals(generated) && !stamped) {
                unchanged++;
                System.out.println(label + "  UNCHANGED");
            } else {
                wrote++;
                System.out.println(label + "  WROTE");
            }
            return true;
        }
        if (io.elasticore.base.util.ThreeWayMerge.hasConflictMarks(disk)) {
            failed++;
            System.out.println(label + "  CONFLICT(unresolved)");
            notes.add("[elcore3] " + key + " still has conflict marks (<<<<<<< yours) - resolve them first");
            return true;
        }
        if (disk.equals(generated) || base.equals(generated)) {
            if (stamped && !writeLf(label, file, disk)) return true;
            cache.put(key, generated);
            unchanged++;
            System.out.println(label + (disk.equals(generated) ? "  UNCHANGED" : "  UNCHANGED(your edits kept)"));
            return true;
        }
        io.elasticore.base.util.ThreeWayMerge.Result merged =
                io.elasticore.base.util.ThreeWayMerge.mergeSource(base, disk, generated);
        if (merged.conflicts() == 0) {
            if (!writeLf(label, file, merged.text())) return true;
            cache.put(key, generated);
            wrote++;
            mergedWithEdits++;
            System.out.println(label + "  MERGED(your edits kept)");
            return true;
        }
        if (skeleton) {
            // The developer's own code: left as it is, against the base it
            // was written from, so the next run tries again.
            if (stamped && !writeLf(label, file, disk)) return true;
            cache.put(key, base);
            skipped++;
            keptAsYours++;
            System.out.println(label + "  KEPT(yours - the DSL's changes to it do not merge)");
            notes.add("[elcore3] " + key + ": the DSL changes this file of yours where you changed it too ("
                    + merged.conflicts() + " place(s)) - it is left as it is; take the changes over by hand");
            warnIfEndpointsMissing(out, file, content);
            if ((v3 || kotlin) && !typescript) warnIfSignInLeft(out, file, content);
            warnIfConstructorDrifted(out, file, content);
            warnIfClassAnnotationsDrifted(out, file, content);
            warnIfBaseNotExtended(out, file, content);
            return true;
        }
        if (!writeLf(label, file, merged.text())) return true;
        cache.put(key, generated);
        failed++;
        conflicts++;
        System.out.println(label + "  CONFLICT");
        notes.add("[elcore3] " + key + ": " + merged.conflicts() + " place(s) where your edits and the DSL's"
                + " change the same lines - marked <<<<<<< yours / >>>>>>> elasticore in the file; resolve them"
                + " and run again");
        return true;
    }

    /**
     * The base of a stamped file that was edited: what was generated into it
     * then, if this run can produce it again - the text this generator writes
     * now, or, for a project moving from the code 2.x generates, that code.
     * Only a text whose hash is the one on the stamp is taken: a guessed base
     * would merge the wrong changes in silently.
     */
    static String confirmedBase(String stampLine, String ecd, String... candidates) {
        String version;
        try {
            version = HashUtils.getVersion(stampLine.trim());
        } catch (RuntimeException e) {
            return null;
        }
        for (String c : candidates) {
            if (c == null) continue;
            String text = c.endsWith("\n") ? c : c + "\n";
            try {
                if (HashUtils.checkHashCode(text, ecd, version)) return text;
            } catch (RuntimeException e) {
                // not this one
            }
        }
        return null;
    }

    /**
     * Whether a cache entry is this run's to call stale: the client run
     * (TypeScript) and the server run write into one project and one cache,
     * and neither produces the other's files.
     */
    private boolean ownsEntry(String key) {
        return key.endsWith(".ts") == typescript;
    }

    /** The files the cache holds, by absolute path; empty when this run does not merge. */
    private java.util.Set<String> cachedFiles() {
        java.util.Set<String> files = new java.util.HashSet<String>();
        if (cache == null) return files;
        for (String key : cache.paths()) files.add(absolute(new File(projectRoot, key)));
        return files;
    }

    private static String absolute(File f) {
        return f.getAbsoluteFile().toPath().normalize().toString().replace('\\', '/');
    }

    /** The file's path in the cache: relative to the project, or null outside it. */
    private String cacheKey(File file) {
        String p = absolute(file);
        String root = absolute(new File(projectRoot));
        return p.startsWith(root + "/") ? p.substring(root.length() + 1) : null;
    }

    /**
     * A generated file without a stamp, kept in step with the DSL and with
     * whatever the developer wrote into it.
     *
     * The base is what this generator wrote into the file last time, from the
     * cache. The developer's edits are what the file has and the base has
     * not; the DSL's are what the new text has and the base has not. Both are
     * kept - whatever kind of edit it was - unless they touch the same lines:
     * then the file gets git's conflict marks and the run fails, as a merge
     * would. The cache takes the new text either way, so the file, resolved,
     * is the developer's against it.
     */
    private void writeMerged(String label, File file, String key, String content) {
        writeMerged(label, file, key, content, false);
    }

    /**
     * overwritten: a file the generator used to write over on every run
     * (orm.xml, the messages) - on the project's first merging run, one it
     * finds is its own last output, not something to leave alone. Once there
     * is a cache, one it does not hold is treated as any other.
     */
    private void writeMerged(String label, File file, String key, String content, boolean overwritten) {
        mergedKeys.add(key);
        String generated = content.replace("\r\n", "\n");
        String base = cache.get(key);
        String disk = null;
        if (file.isFile()) {
            disk = readLf(file);
            if (disk == null) {
                failed++;
                notes.add(key + ": cannot be read");
                System.out.println(label + "  ERROR cannot read " + key);
                return;
            }
        }
        if (disk == null) {
            if (writeLf(label, file, generated)) {
                cache.put(key, generated);
                wrote++;
                System.out.println(label + "  WROTE");
            }
            return;
        }
        boolean stamped = false;
        if (base == null) {
            // Written before the project merged: the stamp says whether it is
            // still what was generated then. That text is the base - custom
            // blocks are left out of the hash, and are the developer's like any
            // other edit - and the stamp goes with this run.
            HashUtils.Response st = readStatus(file);
            if (st != null && st.getStatus() == HashUtils.NO_MODIFIED && st.getPreSourceContent() != null) {
                base = st.getPreSourceContent().replace("\r\n", "\n");
                disk = disk.substring(disk.indexOf('\n') + 1);
                stamped = true;
            } else if (overwritten && !cache.existed()) {
                // The first merging run: the file holds what the last run wrote over it.
                base = disk;
            } else if (!disk.equals(generated)) {
                skipped++;
                System.out.println(label + "  SKIP(not in the cache)");
                notes.add("[elcore3] " + key + ": not in " + GeneratedCache.DIR + "/" + GeneratedCache.NAME
                        + " - edited before merging was turned on, or not written by ElastiCORE - so what to merge"
                        + " it with is not known; left as it is (delete it to have it generated)");
                return;
            }
        }
        // -Pforce does not reach here: builds leave it on to regenerate every
        // run, which merging does anyway, and an edit is never thrown away by
        // a setting. Deleting the file is how to have it generated afresh.
        if (base == null || disk.equals(base)) {
            if ((stamped || !disk.equals(generated)) && !writeLf(label, file, generated)) return;
            cache.put(key, generated);
            if (disk.equals(generated) && !stamped) {
                unchanged++;
                System.out.println(label + "  UNCHANGED");
            } else {
                wrote++;
                System.out.println(label + "  WROTE");
            }
            return;
        }
        if (io.elasticore.base.util.ThreeWayMerge.hasConflictMarks(disk)) {
            failed++;
            String message = "[elcore3] " + key + " still has conflict marks (<<<<<<< yours) - resolve them first";
            System.out.println(label + "  CONFLICT(unresolved)");
            notes.add(message);
            return;
        }
        if (disk.equals(generated) || base.equals(generated)) {
            // The developer's text already is the new one, or the DSL did not
            // change this file: nothing to write - but a stamp to take off.
            if (stamped && !writeLf(label, file, disk)) return;
            cache.put(key, generated);
            unchanged++;
            System.out.println(label + (disk.equals(generated) ? "  UNCHANGED" : "  UNCHANGED(your edits kept)"));
            return;
        }
        io.elasticore.base.util.ThreeWayMerge.Result merged = io.elasticore.base.util.ThreeWayMerge.merge(base, disk, generated);
        if (!writeLf(label, file, merged.text())) return;
        cache.put(key, generated);
        if (merged.conflicts() == 0) {
            wrote++;
            mergedWithEdits++;
            System.out.println(label + "  MERGED(your edits kept)");
        } else {
            failed++;
            String message = "[elcore3] " + key + ": " + merged.conflicts() + " place(s) where your edits and the DSL's"
                    + " change the same lines - marked <<<<<<< yours / >>>>>>> elasticore in the file; resolve them"
                    + " and run again";
            conflicts++;
            System.out.println(label + "  CONFLICT");
            notes.add(message);
        }
    }

    /**
     * After a whole run: a merged file the DSL no longer produces is removed
     * when it is still what was generated, and kept with a warning when the
     * developer edited it - then it is theirs, and leaves the cache.
     */
    private void removeStaleMerged() {
        if (cache == null) return;
        Map<File, String> unedited = new java.util.LinkedHashMap<File, String>();
        for (String key : cache.paths()) {
            if (mergedKeys.contains(key) || !ownsEntry(key)) continue;
            File f = new File(projectRoot, key);
            // A base a developer's class still extends stays, and stays known:
            // removeUnused finds the class using it - unless that is going too.
            String disk = f.isFile() ? readLf(f) : null;
            if (disk != null && disk.equals(cache.get(key))) {
                unedited.put(f, key);
                continue;
            } else if (disk != null) {
                String message = "[elcore3] " + key + " was generated, the DSL no longer produces it,"
                        + " and it has been edited - it is yours now; delete it, or move what you wrote.";
                io.elasticore.base.util.ConsoleLog.printWarn(message);
                notes.add(message);
            }
            cache.remove(key);
        }
        // Removed by removeUnused - one a kept source still uses stays known.
        for (Map.Entry<File, String> en : unedited.entrySet()) staleMerged.put(canonical(en.getKey()), en.getValue());
    }

    private void saveCache() {
        if (cache == null) return;
        try {
            cache.save();
        } catch (IOException e) {
            failed++;
            notes.add(cache.file() + " cannot be written: " + e.getMessage()
                    + " - the next run merges against what it held before");
        }
    }

    /** A file's text with LF line ends, or null when it cannot be read. */
    private static String readLf(File file) {
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        } catch (IOException e) {
            return null;
        }
    }

    /** LF text written with the line ends a stamped file would get, in UTF-8. */
    private boolean writeLf(String label, File file, String text) {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            failed++;
            notes.add(file + ": cannot create " + dir);
            System.out.println(label + "  ERROR cannot create " + dir);
            return false;
        }
        String eol = style.lineEnding() == null ? System.lineSeparator() : style.lineEnding();
        try {
            java.nio.file.Files.write(file.toPath(), text.replace("\n", eol).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            failed++;
            notes.add(file + ": " + e);
            System.out.println(label + "  ERROR " + e.getMessage());
            return false;
        }
    }

    private void write(String label, ArtifactTarget out, String rendered) {
        File file = new File(rootFor(out) + "/" + out.relativePath());
        remember(file);

        // What would actually land on disk, so that a file already carrying
        // the house style is recognised as unchanged - and one that does not
        // is rewritten.
        //
        // 2.x always terminates the file with a newline; templates do not
        // necessarily end with one.
        String content = rendered.endsWith("\n") ? rendered : rendered + "\n";
        content = style.apply(content);
        // mapping: xml - the domain class says nothing of JPA; orm.xml does.
        if (xmlMapping() && inDomainModel(out) && ("java".equals(out.scheme()) || "kotlin".equals(out.scheme()))) {
            content = orm(out).extract(content, out.packageName(), out.fileName(), kotlin);
        }
        if (xmlMapping()) content = movedQuery(out, content);

        // Gathering what the code 2.x generates, for an upgrade's bases: nothing is written.
        if (captured != null) {
            String key = cacheKey(file);
            if (key != null) captured.put(key, content.replace("\r\n", "\n"));
            return;
        }
        if (merges(out)) {
            String key = cacheKey(file);
            if (key != null) {
                writeMerged(label, file, key, content);
                return;
            }
        }
        if (mergesSource(out)) {
            String key = cacheKey(file);
            if (key != null && writeMergedSource(label, out, file, key, content)) return;
            // Left to the stamp below: a file whose base no run could confirm -
            // or, without a stamp, one the cache does not know.
            if (key != null && file.isFile()) {
                String text = readLf(file);
                if (text != null && text.startsWith("//ecd:")) stampKept.add(key);
                else unbased.add(key);
            }
        }

        HashUtils.Response st = null;
        if (file.isFile()) {
            st = readStatus(file);
            if (st != null && st.getStatus() == HashUtils.UNTRACKED && (v3 || kotlin)
                    && neverEdited(st) && !equalsIgnoringLineEndings(st.getPreSourceContent(), content)) {
                // Written once and handed over - but nobody took it: the file
                // is still exactly what the generator wrote, as its own hash
                // says. Left alone it would keep what the DSL said then - a
                // controller calling a save that @readonly has since removed -
                // and break the build on nothing the developer did. So it
                // follows the DSL until the first edit makes it theirs.
                System.out.println(label + "  REGENERATED(owned, never edited)");
            } else if (st != null && st.getStatus() == HashUtils.NO_ECD) {
                // No hash line: not a file ElastiCORE wrote, or one whose
                // header the developer removed. Either way it is theirs - 2.x
                // skips it too - and writing over it would lose their code.
                skipped++;
                System.out.println(label + "  SKIP(no ElastiCORE header - yours)");
                return;
            } else if (st != null && st.getStatus() == HashUtils.UNTRACKED) {
                skipped++;
                System.out.println(label + "  SKIP(user-owned)");
                warnIfEndpointsMissing(out, file, content);
                if ((v3 || kotlin) && !typescript) warnIfSignInLeft(out, file, content);
                warnIfConstructorDrifted(out, file, content);
                warnIfClassAnnotationsDrifted(out, file, content);
                warnIfBaseNotExtended(out, file, content);
                return;
            }
            if (st != null && st.getStatus() == HashUtils.MODIFIED && !force) {
                skipped++;
                System.out.println(label + "  SKIP(user-modified; -Pforce to overwrite)");
                return;
            }
            if (st != null && st.getPreSourceContent() != null
                    && equalsIgnoringLineEndings(st.getPreSourceContent(), content)) {
                unchanged++;
                System.out.println(label + "  UNCHANGED");
                return;
            }
        }

        File dir = file.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            failed++;
            notes.add(out.relativePath() + ": cannot create " + dir);
            System.out.println(label + "  ERROR cannot create " + dir);
            return;
        }
        // UTF-8 always; never FileWriter, which uses the platform charset.
        //
        // Line endings come from the platform unless the build named a house
        // style, in which case the style owns them and the converting writer
        // would undo its choice.
        Writer w = null;
        try {
            w = style.lineEnding() == null
                    ? FileBasedSourceFileAccessFactory.newPlatformLineWriter(file)
                    : new java.io.OutputStreamWriter(
                            new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8);

            // The hash covers the generated text only, so the user's blocks
            // are put back after it is taken - where 2.x puts them.
            w.write(HashUtils.makeEcdCode(content, out.extension()));
            w.write(st != null && st.hasCustomizedScopeContent() ? withCustomBlocks(content, st) : content);
            w.flush();
            wrote++;
            System.out.println(label + "  WROTE");
        } catch (IOException e) {
            failed(label, out, e);
        } finally {
            if (w != null) {
                try { w.close(); } catch (IOException ignore) { }
            }
        }
    }

    /**
     * The generated text with the user's custom blocks (from a line holding
     * "// !----------" to one holding "------------!") put back.
     *
     * The blocks are left out of the hash and the comparison, so a file that
     * carries them reads as unchanged while the DSL is. When the DSL changed
     * the file was rewritten without them, losing code the user had been told
     * was safe there. Placement is 2.x's: a block of imports after the second
     * line, the rest before the closing line.
     */
    static String withCustomBlocks(String content, HashUtils.Response st) {
        String[] lines = content.split("\n", -1);
        int last = lines.length - 1;
        while (last > 0 && lines[last].trim().isEmpty()) last--;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == last && st.getCustomizedScopeContent() != null) {
                out.append(st.getCustomizedScopeContent());
            }
            out.append(lines[i]);
            if (i < lines.length - 1) out.append("\n");
            if (i == 1 && st.getCustomizedScopeContent4Header() != null) {
                out.append(st.getCustomizedScopeContent4Header());
            }
        }
        return out.toString();
    }

    private HashUtils.Response readStatus(File file) {
        Reader r = null;
        try {
            r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            return HashUtils.checkContentModified(r);
        } catch (IOException e) {
            return null;
        } finally {
            if (r != null) {
                try { r.close(); } catch (IOException ignore) { }
            }
        }
    }

    /**
     * Where an artifact is written: the module its namespace redirects to,
     * otherwise the module that owns the domain, otherwise the project root.
     *
     * The redirect is what "app-admin://com.acme.shop.domain.service" in an
     * env.yml namespace asks for; the package it names arrives here clean,
     * so the mapping is kept beside it.
     */
    private String rootFor(ArtifactTarget out) {
        if (packageModuleMap != null && out.packageName() != null) {
            for (Map.Entry<String, String> en : packageModuleMap.entrySet()) {
                if (!out.packageName().startsWith(en.getKey())) continue;
                String moduleRoot = moduleRoots.get(en.getValue());
                if (moduleRoot != null) return moduleRoot;
            }
        }
        return domainRoot != null ? domainRoot : projectRoot;
    }

    private static boolean equalsIgnoringLineEndings(String a, String b) {
        return a.replace("\r\n", "\n").trim().equals(b.replace("\r\n", "\n").trim());
    }

    private void printSummary() {
        System.out.println();
        System.out.println("=== elcore3: " + wrote + " written / "
                + unchanged + " unchanged / " + skipped + " skipped / "
                + failed + " failed ===");
        // Where each generated file stands, now that a stamp no longer says it.
        if (cache != null) {
            System.out.println("=== merge: " + mergedWithEdits + " merged with your edits / " + keptAsYours
                    + " kept as yours / " + conflicts + " conflict(s) / " + stampKept.size()
                    + " still stamped (edited, base not confirmed) ===");
            for (String k : stampKept) System.out.println("  still stamped: " + k);
            if (!unbased.isEmpty()) {
                // Without a stamp and unknown to the cache there is no base: left as it is.
                System.out.println("  " + unbased.size() + " source(s) without a stamp that " + GeneratedCache.DIR + "/"
                        + GeneratedCache.NAME + " does not hold were left as they are"
                        + (cache.existed() ? "" : " - the cache is missing: take it from git and run again"));
            }
        }
        for (String n : notes) {
            System.out.println("  " + n);
        }
        if (failed > 0) {
            System.out.println();
            System.out.println("[elcore3] " + failed
                    + " artifact(s) could not be generated - see the notes above.");
        }
    }

    /** Whether the run produced everything it was asked for. */
    private boolean succeeded() {
        return failed == 0;
    }
}
