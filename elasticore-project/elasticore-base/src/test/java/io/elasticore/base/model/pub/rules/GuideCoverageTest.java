package io.elasticore.base.model.pub.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the catalog offers and what the guide says it offers, compared.
 *
 * A rule set nobody can find is not a feature, and the way one becomes
 * unfindable is ordinary: the register() line is written, the code works, the
 * guide is a separate file and the change ships without it. Nothing failed
 * when that happened, because nothing was looking.
 *
 * So this looks. It is a weak check by design - a name appearing somewhere in
 * the guide, not a judgement about whether the paragraph around it is any
 * good - because the strong version cannot be automated and the weak version
 * catches the failure that actually occurs.
 *
 * It reads the English guides; {@link GuideCoverageKoTest} runs every check
 * again on the Korean ones.
 */
public class GuideCoverageTest {

    /** The guides' directory below {@code elasticore-guide/}: the English ones here. */
    protected String directory() {
        return io.elasticore.base.util.GuideDeployer.directoryOf("en");
    }

    private static final String RULE_GUIDE = "/elasticore-guide/elasticore-rule-guide.md";
    private static final String ANNOTATION_GUIDE = "/elasticore-guide/elasticore-annotation-guide.md";
    private static final String ENV_GUIDE = "/elasticore-guide/elasticore-env-guide.md";
    private static final String DSL_GUIDE = "/elasticore-guide/elasticore-dsl-guide.md";
    private static final String PORT_GUIDE = "/elasticore-guide/elasticore-dsl-port-guide.md";

    @Test
    @DisplayName("the env.yml reference documents the key that decides everything else")
    public void theEnvGuideDocumentsPublisherRules() throws IOException {
        String guide = read(ENV_GUIDE);

        // publisher.rules was documented only in the rule guide, so it was
        // findable by a reader who had already found the rules - and absent
        // from the file people open to look up what env.yml accepts. Every
        // bundle in the catalog reaches a project through this one key.
        assertTrue(guide.contains("publisher.rules"),
                "the env.yml reference never mentions publisher.rules");

        // Naming the key without saying who reads it is the trap: the file
        // looks configured and the build ignores it.
        assertTrue(guide.contains("elasticore.rules.kt"),
                "the env guide does not say the language is a separate switch");
    }

    @Test

    @DisplayName("every architecture and language pair names a bundle the catalog has")
    public void theMatrixResolves() {
        java.util.Set<String> known = RuleCatalog.standard().bundleNames();
        java.util.List<String> unknown = new ArrayList<String>();

        // The pair is the only way a bundle should be named, so an unresolved
        // combination is a project that cannot be configured rather than a
        // typo somebody made.
        for (RulesConfig.Architecture a : RulesConfig.Architecture.values()) {
            for (RulesConfig.Language l : RulesConfig.Language.values()) {
                String bundle = RulesConfig.bundleFor(a, l);
                if (!known.contains(bundle)) unknown.add(a + "+" + l + " -> " + bundle);
            }
        }
        assertTrue(unknown.isEmpty(), "combinations naming no bundle: " + unknown);
    }

    @Test
    @DisplayName("the customization guide names the ways in it is written against")
    public void theCustomizeGuideNamesItsWaysIn() throws IOException {
        String guide = read("/elasticore-guide/elasticore-ddd-customize-guide.md");
        // Each of these is a support a project's rule set leans on; renaming
        // one without the guide leaves readers following a path that is gone.
        for (String term : new String[]{"rules        = [", "elasticore project(':acme-rules')", "-PlistRules",
                "DomainArtifactContributor", "e.events()", "e.eventClassName", "e.eventPackage()",
                ".field(\"orderId\"", "isSplitForBehavior", "suppress", "elasticore-template/kt/"}) {
            assertTrue(guide.contains(term), "the customization guide no longer mentions " + term);
        }
    }

    @Test
    @DisplayName("every guide that is deployed exists to be deployed")
    public void theDeployedGuidesExist() {
        java.util.List<String> missing = new ArrayList<String>();

        // The list is what a project receives. A name in it with no file
        // behind it produces a warning during generation and a manual with a
        // hole in it, and nobody reads generation warnings.
        for (String guide : io.elasticore.base.util.GuideDeployer.GUIDE_FILES) {
            if (getClass().getResourceAsStream("/elasticore-guide/" + directory() + guide) == null) {
                missing.add(guide);
            }
        }
        assertTrue(missing.isEmpty(), "deployed but not present: " + missing);
    }

    @Test
    @DisplayName("every bundle a project can select is named in the rule guide")
    public void bundlesAreDocumented() throws IOException {
        String guide = read(RULE_GUIDE);
        List<String> missing = new ArrayList<String>();

        for (String bundle : RuleCatalog.standard().bundleNames()) {
            if (!guide.contains(bundle)) missing.add(bundle);
        }

        assertTrue(missing.isEmpty(),
                "a bundle is the thing users actually write in env.yml; undocumented: " + missing);
    }

    /**
     * The domain-model rule sets specifically.
     *
     * Not every registered set: the base ones are implementation detail that
     * a bundle pulls in, and listing all fifty in a guide would be noise. The
     * ddd- and hex- sets are different - they are selected by name, one at a
     * time, by people composing a bundle of their own.
     */
    @Test
    @DisplayName("every DDD and hexagonal rule set is named in the rule guide")
    public void domainRuleSetsAreDocumented() throws IOException {
        String guide = read(RULE_GUIDE);
        List<String> missing = new ArrayList<String>();

        for (String name : RuleCatalog.standard().setNames()) {
            if (!name.startsWith("ddd-") && !name.startsWith("hex-")) continue;
            if (!guide.contains(name)) missing.add(name);
        }

        assertTrue(missing.isEmpty(),
                "these are selected by name and cannot be guessed; undocumented: " + missing);
    }

    @Test
    @DisplayName("every DSL word the DDD rules read is documented where a reader looks for it")
    public void vocabularyIsDocumented() throws IOException {
        String annotations = read(ANNOTATION_GUIDE);
        List<String> missing = new ArrayList<String>();

        // Read by DddAggregateRules, DddDomainEventRules, DddInvariantRules,
        // DddFactoryRules and DddSpecificationRules respectively.
        for (String word : new String[]{
                "@root", "@aggregate", "@event", "@invariant", "@factory", "@specification"}) {
            if (!annotations.contains(word)) missing.add(word);
        }
        assertTrue(missing.isEmpty(), "undocumented in the annotation guide: " + missing);

        // A section rather than an annotation, so it belongs in the DSL guide.
        assertTrue(read(DSL_GUIDE).contains("domainService"),
                "the domainService section is DSL, and the DSL guide is where its shape belongs");
    }

    @Test
    @DisplayName("every namespace key an artifact is placed by is documented")
    public void namespaceKeysAreDocumented() throws IOException {
        String guide = read(ENV_GUIDE);
        List<String> missing = new ArrayList<String>();

        // Each of these decides where a generated file lands. A project that
        // does not know a key exists gets the fallback and no explanation.
        for (String key : new String[]{
                "entity", "dto", "enumeration", "repository", "service", "control",
                "port", "proto", "event", "domainService",
                "domainRepository", "persistence"}) {
            if (!guide.contains(key)) missing.add(key);
        }

        assertTrue(missing.isEmpty(), "undocumented namespace keys: " + missing);
    }

    /**
     * The port guide teaches where a port goes and how its adapter arrives.
     *
     * Both were wrong in it for a while and neither is the kind of thing the
     * other checks can see. It placed the generated interface under domain -
     * which the hexagonal review had already called an error, and which the
     * sample's inward-dependency test would now refuse, since the interface
     * names application DTOs. And it said the proxy is injected
     * automatically, which is true of an application and not of a context
     * that does not walk the call stack the registrar detects its package
     * from: there the port has no bean and nothing can be given one.
     *
     * Prose cannot be checked for being right. It can be checked for
     * mentioning the two things whose absence made it wrong.
     */
    @Test
    @DisplayName("the port guide says which layer a port belongs to, and that its proxy needs registering")
    public void thePortGuideTeachesPlacementAndRegistration() throws IOException {
        String guide = read(PORT_GUIDE);

        assertTrue(guide.contains("application.port.out"),
                "a reader following this guide has to end up with the interface in the"
                        + " application layer, not the domain");
        assertTrue(guide.contains("No qualifying bean"),
                "the failure a project hits when nothing registers the proxy is worth"
                        + " naming, because the message is what they will search for");
        assertTrue(guide.contains("@EnableElastiCore"),
                "and the thing that normally does the registering");
    }

    private String read(String englishResource) throws IOException {
        String resource = englishResource.replace("/elasticore-guide/", "/elasticore-guide/" + directory());
        InputStream in = GuideCoverageTest.class.getResourceAsStream(resource);
        assertNotNull(in, resource + " is not on the classpath - the guide is shipped, so it should be");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}
