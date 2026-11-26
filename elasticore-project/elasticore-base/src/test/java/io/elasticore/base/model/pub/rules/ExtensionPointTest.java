package io.elasticore.base.model.pub.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The artifact extension point, asked the questions its consumers asked it.
 *
 * Every defect this mechanism has had arrived the same way: a second consumer
 * did something the first had not, and an assumption nobody had written down
 * turned out to be load-bearing. Four times.
 *
 * <ol>
 *   <li>a set contributing more than one artifact of a target could not tell
 *       which it was writing - the entity path had no artifact name</li>
 *   <li>the same, on the domain path, when domain services arrived</li>
 *   <li>a contributed artifact was placed with a hard-coded Java target, so
 *       selecting the Kotlin bundle wrote a .java file</li>
 *   <li>contributed artifacts were skipped entirely when a project named no
 *       rules of its own, because the walk read the configured list and a
 *       null meant "return"</li>
 * </ol>
 *
 * The pattern is that each was found by a consumer rather than by a test, so
 * these are the axes written down as tests instead: how many artifacts, which
 * subject, which namespace, which language, and whether anything was
 * configured. A fifth axis nobody has exercised is worth more as a question
 * here than as a surprise later.
 */
public class ExtensionPointTest {

    // ---- a rule set that exists only here -------------------------------

    /**
     * Two artifacts of one target, so "which one am I writing" is a question
     * with a wrong answer available.
     */
    public static class TwoArtifactRules implements CodeRules, ArtifactContributor {

        @Override
        public List<ContributedArtifact> artifactsFor(EntitySpec spec) {
            if (!spec.hasMeta("probe")) return new ArrayList<ContributedArtifact>();

            return Arrays.asList(
                    ContributedArtifact.of("probe", "probe", spec.getName() + "First")
                            .orNamespace("entity"),
                    ContributedArtifact.of("probe", "probe", spec.getName() + "Second")
                            .orNamespace("entity"));
        }

        @Rule(value = "probe-artifact", target = "probe", order = 10)
        void write(EntitySpec e) {
            ctx(e).var("written", ctx(e).artifactName());
        }

        private SourceCode ctx(EntitySpec e) {
            return Ctx.ctx(e);
        }
    }

    private EntitySpec probe() {
        return new EntitySpec("Order")
                .domain("shop", "Shop")
                .meta("probe", "")
                .pkg("entity", "io.acme.shop.entity");
    }

    // ---- the axes -------------------------------------------------------

    @Test
    @DisplayName("a set can contribute several artifacts of one target")
    public void severalOfOneTarget() {
        List<ContributedArtifact> declared = new TwoArtifactRules().artifactsFor(probe());

        assertEquals(2, declared.size());
        assertEquals("OrderFirst", declared.get(0).className());
        assertEquals("OrderSecond", declared.get(1).className());
        assertEquals("probe", declared.get(0).target(),
                "both are the same kind of file, which is what makes the name necessary");
    }

    /**
     * The name reaches the rules, which is what the first defect was about.
     *
     * Without it every artifact of a target renders identically, so the
     * second overwrites the first and the run reports two files written.
     */
    @Test
    @DisplayName("the engine tells a rule which artifact it is writing")
    public void theRuleKnowsWhichOne() {
        assertEquals("OrderSecond", rendered("OrderSecond"));
        assertEquals("OrderFirst", rendered("OrderFirst"));
    }

    private String rendered(String artifactName) {
        RuleEngine engine = new RuleEngine();
        engine.register(new TwoArtifactRules());

        SourceCode code = engine.artifactName(artifactName).run(probe(), "probe");
        return code.renderTemplate("${written}");
    }

    /** A set with nothing to add for this entity says so with an empty list. */
    @Test
    @DisplayName("a set that does not apply contributes nothing")
    public void nothingWhenItDoesNotApply() {
        EntitySpec plain = new EntitySpec("Customer").domain("shop", "Shop");

        assertTrue(new TwoArtifactRules().artifactsFor(plain).isEmpty(),
                "conditions are code, so declining is an empty answer rather than a flag");
    }

    /**
     * A new artifact arrives with a new place to put it, and every existing
     * env.yml was written before it existed.
     */
    @Test
    @DisplayName("an artifact names a fallback for projects that never heard of it")
    public void namespaceFallback() {
        ContributedArtifact a = ContributedArtifact.of("event", "event", "OrderPlacedEvent")
                .orNamespace("entity");

        assertEquals("event", a.namespaceKey());
        assertEquals("entity", a.fallbackNamespaceKey());

        assertEquals(null,
                ContributedArtifact.of("event", "event", "X").fallbackNamespaceKey(),
                "an artifact that names no fallback gets none invented for it");
    }

    /** A domain has its own contributors, on the same terms as an entity: nothing to say, no file. */
    @Test
    @DisplayName("a domain with nothing to contribute contributes none")
    public void anEmptyDomainContributesNothing() {
        DomainSpec empty = new DomainSpec("shop", "Shop");
        assertTrue(new io.elasticore.base.model.pub.rules.base.ApiErrorRules()
                .artifactsFor(empty)
                .isEmpty());
        assertTrue(new io.elasticore.base.model.pub.rules.ddd.DddOutboxRules()
                .artifactsFor(empty)
                .isEmpty());
    }

    /**
     * The runner must not know a rule set by name.
     *
     * This is the whole premise - "a new kind of file lives entirely inside
     * the rule set that wants it, the runner never learns its name" - and it
     * had quietly stopped being true: the domain-service loop imported the
     * rule class, called its static helpers and named the set in a string,
     * while the entity half stayed clean. A premise worth stating in an
     * interface is worth a check.
     */
    @Test
    @DisplayName("the runner names no rule set of its own")
    public void theRunnerKnowsNoRuleSet() throws IOException {
        String runner = readRunnerSource();
        List<String> named = new ArrayList<String>();

        for (String forbidden : new String[]{"rules.ddd.", "rules.hex."}) {
            if (runner.contains(forbidden)) named.add(forbidden);
        }

        assertTrue(named.isEmpty(),
                "declare the artifact from the rule set through ArtifactContributor or"
                        + " DomainArtifactContributor instead; the runner referenced: " + named);
    }

    /**
     * And the bundles it does name are the defaults, not domain readings.
     *
     * The runner has to know the fallback bundle for a run with nothing
     * configured. It has no business knowing ddd-springboot exists.
     */
    @Test
    @DisplayName("the runner names no domain bundle either")
    public void theRunnerKnowsNoDomainBundle() throws IOException {
        String runner = readRunnerSource();

        assertFalse(runner.contains("ddd-springboot"), "a domain reading is a project's choice");
        assertFalse(runner.contains("hexagonal-springboot"), "so is this one");
    }

    private String readRunnerSource() throws IOException {
        File dir = new File(ExtensionPointTest.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath().replace("%20", " "));
        while (dir != null && !new File(dir, "src/main/java").isDirectory()) {
            dir = dir.getParentFile();
        }
        assertTrue(dir != null, "could not find the module root from the test's own location");

        File runner = new File(dir,
                "src/main/java/io/elasticore/base/model/pub/rules/verify/RulesGenRunner.java");
        assertTrue(runner.isFile(), "the runner should be where it has always been");

        return new String(Files.readAllBytes(runner.toPath()), StandardCharsets.UTF_8);
    }
}
