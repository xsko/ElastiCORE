package dddmybatis;

import io.elasticore.base.ECoreModelContext;
import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.verify.BlueprintLoader;
import io.elasticore.base.model.pub.rules.verify.SpecFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the rules claim, checked against what they wrote.
 *
 * Every rule in the DDD bundle asserts something that spans more than one
 * file: an entity says a reference is never written through and the mapper is
 * where that gets broken; a root says its members are reached through it and
 * the API is where they go missing. A test that boots the model catches the
 * ones that fail at runtime, and the rest sit in the source looking correct.
 * Four did, and each was found by someone asking rather than by anything here.
 *
 * So this reads the model the way the generator reads it - the same loader,
 * the same {@link SpecFactory}, so the boundary is never decided twice - and
 * then asserts over the generated text. Where a rule and the boundary
 * disagree, the disagreement is the failure.
 *
 * The bar for adding a check here: it would have caught a defect that reached
 * the tree. All five below did.
 */
class BoundaryInvariantTest {

    /** Generated file simple name -> its text. */
    private static final Map<String, String> SOURCES = new LinkedHashMap<String, String>();

    /** Every entity of the blueprint, paired with the spec the rules see. */
    private static final List<EntitySpec> SPECS = new ArrayList<EntitySpec>();

    private static final List<String> ENTITY_NAMES = new ArrayList<String>();

    @BeforeAll
    static void loadModelAndSources() throws IOException {
        String projectDir = System.getProperty("user.dir");

        // The shop domain only: it is the one generated through the DDD
        // bundle. The module also carries a catalog domain read by the plain
        // v3 bundle (CatalogApiTest), whose entities rightly have none of
        // these boundaries.
        Path generated = new File(projectDir, "src/main/java/io/elasticore/sample/dddmybatis/shop").toPath();
        try (Stream<Path> files = Files.walk(generated)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    SOURCES.put(p.getFileName().toString(),
                            new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new IllegalStateException(p.toString(), e);
                }
            });
        }

        BlueprintLoader loader =
                BlueprintLoader.load(new File(projectDir, "src/main/resources/blueprint").getPath());
        for (Map.Entry<String, ECoreModelContext> en : loader.contexts().entrySet()) {
            String domainDir = loader.domainDirNameOf(en.getKey());
            if (!"shop".equals(domainDir)) continue;
            for (ECoreModel model : loader.modelsOf(en.getKey())) {
                ModelComponentItems<Entity> all = model.getEntityModels().getItems();
                while (all.hasNext()) {
                    Entity entity = all.next();
                    String name = entity.getIdentity().getName();
                    if (!SOURCES.containsKey(name + ".java")) continue;
                    SPECS.add(SpecFactory.fromEntity(entity, model, domainDir));
                    ENTITY_NAMES.add(name);
                }
            }
        }

        assertThat(SPECS).as("the blueprint loaded and the tree was generated").isNotEmpty();
    }

    @Test
    @DisplayName("a member of an aggregate has no artifact that reaches it directly")
    void membersHaveNoDirectArtifacts() {
        for (EntitySpec spec : SPECS) {
            if (spec.aggregateRoot()) continue;

            // UseCase is a contributed artifact, not a built-in - which is
            // exactly why it is checked here: the extension point makes it
            // possible for a rule set to hand back the surface the boundary
            // took away, and nothing structural would notice.
            for (String suffix : new String[]{"Controller", "CoreService",
                                              "SrchDTO", "KeyDTO", "UseCase"}) {
                assertThat(SOURCES)
                        .as("%s is inside the %s aggregate, so nothing fetches it on its own",
                                spec.getName(), spec.ownAggregate())
                        .doesNotContainKey(spec.getName() + suffix + ".java");
            }
            // Over MyBatis a member has a mapper - its rows' SQL - and only its
            // root's adapter calls it: nothing outside infrastructure does.
            String mapperCall = "get" + spec.getName() + "()";
            for (Map.Entry<String, String> en : SOURCES.entrySet()) {
                if (en.getValue().contains("package io.elasticore.sample.dddmybatis.shop.infrastructure.")) continue;
                assertThat(en.getValue())
                        .as("%s reaches %s's mapper, which is its root's adapter's to call", en.getKey(), spec.getName())
                        .doesNotContain("helper." + mapperCall);
            }
        }
    }

    @Test
    @DisplayName("a reference across the boundary is read, never written through")
    void crossAggregateReferencesAreSevered() {
        for (EntitySpec spec : SPECS) {
            String source = stateOf(spec.getName());

            for (FieldSpec f : spec.fields()) {
                if (!f.isEntityRef() || f.isList()) continue;
                if (!leavesAggregate(spec, f)) continue;

                // MyBatis loads nothing lazily: the row it points at is read by
                // a join, one level deep, and only its key is ever written.
                String annotations = annotationsOf(source, f.name());
                assertThat(annotations)
                        .as("%s.%s points at another aggregate, so a write must not travel it",
                                spec.getName(), f.name())
                        .doesNotContain("cascade");
            }
        }
    }

    @Test
    @DisplayName("a member collection is sealed and reachable only through the root's methods")
    void memberCollectionsAreEncapsulated() {
        for (EntitySpec spec : SPECS) {
            String source = stateOf(spec.getName());

            for (FieldSpec f : spec.fields()) {
                if (!isMemberCollection(spec, f)) continue;
                String item = f.coreItemType();

                assertThat(annotationsOf(source, f.name()))
                        .as("handing out %s.%s hands out the aggregate", spec.getName(), f.name())
                        .contains("@Setter(AccessLevel.NONE)");

                for (String method : new String[]{"add" + item + "(", "remove" + item + "(",
                                                  "replace" + capital(f.name()) + "("}) {
                    assertThat(source)
                            .as("%s owns %s, so it has to offer the way in: %s",
                                    spec.getName(), f.name(), method)
                            .contains(method);
                }
            }
        }
    }

    @Test
    @DisplayName("adding a member through the root makes the member say whose it is")
    void addingAMemberSetsTheBackReference() {
        for (EntitySpec spec : SPECS) {
            String source = stateOf(spec.getName());

            for (FieldSpec f : spec.fields()) {
                if (!isMemberCollection(spec, f)) continue;

                String mappedBy = f.dslProperty("onetomany", "mappedBy");
                if (mappedBy == null) continue;

                // The column that decides membership is on the member, so a
                // list that only grows leaves rows belonging to nobody. This
                // is the check that reads a compiling, correct-looking method
                // and still says no.
                assertThat(methodBody(source, "add" + f.coreItemType() + "("))
                        .as("%s.add%s must give the member its owner",
                                spec.getName(), f.coreItemType())
                        .containsAnyOf("set" + capital(mappedBy) + "(this)",
                                "set" + capital(mappedBy) + "((" + spec.getName() + ") this)");
            }
        }
    }

    @Test
    @DisplayName("the mapper agrees with the entity about the boundary")
    void theMapperDoesNotContradictTheEntity() {
        for (String mapperName : SOURCES.keySet()) {
            if (!mapperName.endsWith("Mapper.java")) continue;
            String mapper = SOURCES.get(mapperName);
            // The domain's DTO mapper - not a MyBatis mapper interface (a port's SQL).
            if (!mapper.contains("public class ")) continue;

            for (EntitySpec spec : SPECS) {
                for (FieldSpec f : spec.fields()) {
                    // Nothing may be written into another aggregate through a
                    // relation this one merely reads.
                    if (f.isEntityRef() && !f.isList() && leavesAggregate(spec, f)) {
                        assertThat(mapper)
                                .as("%s says %s is never written through; %s must not either",
                                        spec.getName(), f.name(), mapperName)
                                .doesNotContain("to.get" + capital(f.name()) + "().set");
                    }

                    // A member has no endpoint, so the root is the only way in
                    // and the mapper has to take them with it.
                    if (isMemberCollection(spec, f)) {
                        assertThat(mapper)
                                .as("%s can only receive its %s with itself",
                                        spec.getName(), f.name())
                                .contains("replace" + capital(f.name()) + "(");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("a collection of other aggregates cannot be written through either")
    void collectionsOfOtherAggregatesAreSealed() {
        for (EntitySpec spec : SPECS) {
            String source = stateOf(spec.getName());

            for (FieldSpec f : spec.fields()) {
                if (!f.isList() || !leavesAggregate(spec, f)) continue;

                String annotations = annotationsOf(source, f.name());

                // A category tree read as one aggregate is how a parent came
                // to cascade deletes into its children, which the model never
                // said: two instances of the same root are two aggregates.
                assertThat(annotations)
                        .as("%s.%s holds other aggregates, so a write must not travel it",
                                spec.getName(), f.name())
                        .doesNotContain("cascade");
                assertThat(annotations)
                        .as("handing out %s.%s hands out other roots wholesale",
                                spec.getName(), f.name())
                        .contains("@Setter(AccessLevel.NONE)");

                // And it is not mistaken for an aggregate's own members.
                assertThat(source)
                        .as("%s does not own %s, so it offers no way to add one",
                                spec.getName(), f.name())
                        .doesNotContain("public void add" + f.coreItemType() + "(");
            }
        }
    }

    @Test
    @DisplayName("a reference across the boundary can be set by identity")
    void crossAggregateReferencesAreSettableByIdentity() {
        String mapper = mapperSource();

        for (EntitySpec spec : SPECS) {
            for (FieldSpec f : spec.fields()) {
                if (!f.isEntityRef() || f.isList()) continue;
                if (!leavesAggregate(spec, f)) continue;

                // Severing the write path leaves the reference unsettable
                // unless something can still point it somewhere, and pointing
                // it is not writing through it. The DSL's own idiom does this
                // - a DTO field declared @ref(relation.key) - and the mapper
                // attaches the row by key without loading it.
                //
                // Nothing said so when it was missing: the sample created
                // orders that could never be given a customer.
                //
                // With the DDD rule sets the reference is an id field of its
                // own (customerId), and the relation is joined read-only on
                // its column; the mapper sets the id.
                String byId = referenceIdOf(spec.getName(), f.name());
                if (byId != null) {
                    assertThat(mapper)
                            .as("%s.%s is referenced by %s, which the mapper has to set", spec.getName(), f.name(), byId)
                            .contains("::set" + capital(byId) + ",");
                    continue;
                }
                assertThat(mapper)
                        .as("%s.%s cannot be written through, so a DTO has to carry its "
                                + "identity - declare @ref(%s.<key>) on one",
                                spec.getName(), f.name(), f.name())
                        // a row of its key alone, which is what the mapper XML writes
                        .contains("to.set" + capital(f.name()) + "(t);");
            }
        }
    }

    /** The id field an entity's reference is held by, as its generated source says, or null. */
    private String referenceIdOf(String entity, String relation) {
        for (Map.Entry<String, String> en : SOURCES.entrySet()) {
            String file = en.getKey();
            if (!file.equals(entity + "Base.java") && !file.equals(entity + ".java")) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("The reference is (\\w+);[^\\n]*\\n(?:[^\\n]*\\n){0,6}?\\s*private \\w+ " + relation + ";")
                    .matcher(en.getValue());
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private String mapperSource() {
        for (Map.Entry<String, String> en : SOURCES.entrySet()) {
            if (en.getKey().endsWith("Mapper.java")) return en.getValue();
        }
        return "";
    }

    /**
     * The domain names no persistence mechanism.
     *
     * Stronger than the layer check below and separate from it, because the
     * layers were never the whole of it: a domain package that imports
     * JpaRepository or Specification depends on how it is stored, whatever
     * package the storing lives in. Until the repository inversion this was
     * true of the generated repository and of every domain service handed
     * one, so the check could not have been written.
     *
     * Annotations are excluded deliberately, and the line is worth naming.
     * @DomainEvents and AuditingEntityListener are Spring reading something
     * off the aggregate; JpaRepository and Specification are the domain
     * calling the persistence mechanism. Being observed is not the same as
     * depending, and only the second reverses the arrow.
     */
    /**
     * The hexagon has two sides and they are named as two.
     *
     * Until this the inbound port had no namespace of its own and landed
     * among the services that implement it, while an outbound port - a dbms
     * or http declaration - had one and landed in port.out. A hexagon whose
     * two directions are filed differently is one nobody can draw from the
     * package list, and the asymmetry hid something: the outbound port had no
     * bean, so nothing could be given one, which only showed when something
     * tried.
     */
    @Test
    @DisplayName("the two directions are separated, and each is where it says")
    void thePortsAreDirectional() {
        for (Map.Entry<String, String> en : SOURCES.entrySet()) {
            String pkg = packageOf(en.getValue());
            if (pkg == null) continue;

            if (en.getKey().endsWith("UseCase.java")) {
                assertThat(pkg)
                        .as("%s is how the application is entered", en.getKey())
                        .contains(".port.in");
            }
            if (pkg.contains(".port.in")) {
                assertThat(en.getKey())
                        .as("only inbound ports belong in port.in")
                        .endsWith("UseCase.java");
            }
        }

        // The controller depends on the port rather than on the class behind
        // it, which is what makes the inbound side a port at all.
        assertThat(importsOf(SOURCES.get("OrderController.java")))
                .as("the driving adapter is given the port")
                .anyMatch(imp -> imp.contains(".port.in.OrderUseCase"));

        // And the outbound side is an interface whose adapter is not
        // generated - the runtime proxy is the adapter.
        assertThat(SOURCES.get("ShopReportService.java"))
                .as("a dbms port is a declaration, not an implementation")
                .contains("interface ShopReportService");
        assertThat(SOURCES)
                .as("nothing generates an adapter for it; the runtime makes one")
                .doesNotContainKey("ShopReportServiceImpl.java");
    }

    @Test
    @DisplayName("the domain names no persistence mechanism")
    void theDomainDoesNotKnowHowItIsStored() {
        for (Map.Entry<String, String> en : SOURCES.entrySet()) {
            String pkg = packageOf(en.getValue());
            if (pkg == null || !pkg.contains(".domain.")) continue;

            // Q is the one exception and it is not a domain type: it builds
            // the Specification a search turns into, and 2.x places it beside
            // the entities. Moving it is a compatibility break - every
            // generated service imports it from there and the byte-for-byte
            // comparison against 2.x output is what guarantees an existing
            // project sees no change. Named here so the exception is a
            // decision rather than a hole this test happens not to look in.
            if ("Q.java".equals(en.getKey())) continue;

            for (String mechanism : new String[]{
                    "org.springframework.data.jpa.repository.JpaRepository",
                    "org.springframework.data.jpa.domain.Specification",
                    "org.springframework.data.domain.Pageable",
                    "org.springframework.data.domain.Page"}) {
                assertThat(importsOf(en.getValue()))
                        .as("%s is domain and must not know %s", en.getKey(), mechanism)
                        .noneMatch(imp -> imp.contains(mechanism));
            }
        }
    }

    @Test
    @DisplayName("dependencies point inward: the domain knows nothing above it")
    void dependenciesPointInward() {
        // The hexagon's one structural rule. Everything else in this class
        // says what an aggregate may do; this says what the layers may see.
        // The generator places every artifact, so a violation here is the
        // generator's, not a developer's - which is exactly why it is worth
        // a check that reads every file it wrote.
        for (Map.Entry<String, String> en : SOURCES.entrySet()) {
            String pkg = packageOf(en.getValue());
            if (pkg == null) continue;

            if (pkg.contains(".domain.")) {
                for (String above : new String[]{".application", ".interfaces", ".adapter"}) {
                    assertThat(importsOf(en.getValue()))
                            .as("%s is domain and must not look upward at %s", en.getKey(), above)
                            .noneMatch(imp -> imp.contains(above));
                }
            }
            if (pkg.contains(".application")) {
                // Infrastructure included. It was left out, and every
                // application service imported infrastructure.persistence -
                // the Spring Data repositories - for three versions without
                // this test noticing. The application reaches storage through
                // ports (domain.repository for an aggregate's life,
                // application.port.out for searches) and names no adapter.
                assertThat(importsOf(en.getValue()))
                        .as("%s is application and must not know its adapters", en.getKey())
                        .noneMatch(imp -> imp.contains(".interfaces.") || imp.contains(".adapter.")
                                || imp.contains(".infrastructure."));
            }
        }
    }

    private String packageOf(String source) {
        for (String line : source.split("\n")) {
            String t = line.trim();
            if (t.startsWith("package ")) return t;
        }
        return null;
    }

    private java.util.List<String> importsOf(String source) {
        java.util.List<String> imports = new ArrayList<String>();
        for (String line : source.split("\n")) {
            String t = line.trim();
            if (t.startsWith("import ")) imports.add(t);
        }
        return imports;
    }

    // ---- the boundary, asked of the spec rather than re-decided -----------

    private boolean leavesAggregate(EntitySpec spec, FieldSpec f) {
        return f.isEntityRef()
                && spec.knowsAggregateOf(f.coreItemType())
                && !spec.sameAggregate(f.coreItemType());
    }

    private boolean isMemberCollection(EntitySpec spec, FieldSpec f) {
        return f.isList() && f.isEntityRef() && spec.sameAggregate(f.coreItemType());
    }

    // ---- reading the generated text --------------------------------------

    /** The annotation lines immediately above a field declaration. */
    private String annotationsOf(String source, String fieldName) {
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().endsWith(" " + fieldName + ";")) continue;

            StringBuilder block = new StringBuilder();
            for (int j = i - 1; j >= 0; j--) {
                String line = lines[j].trim();
                if (line.isEmpty()) break;
                block.insert(0, line + "\n");
            }
            return block + mappedInOrmXml(source, fieldName);
        }
        return "";
    }

    /**
     * Where the shop maps through META-INF/orm.xml (mapping: xml), a field's
     * relation is the XML element rather than an annotation: the element, with
     * fetch="LAZY" also said as the annotation says it, so one assertion reads
     * either form. Empty when the class is not in orm.xml.
     */
    private String mappedInOrmXml(String source, String fieldName) {
        java.io.File orm = new java.io.File("src/main/resources/META-INF/orm.xml");
        if (!orm.isFile()) return "";
        String xml;
        try {
            xml = new String(java.nio.file.Files.readAllBytes(orm.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "";
        }
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface)\\s+(\\w+)").matcher(source);
        String pkg = packageOf(source);
        if (!type.find() || pkg == null) return "";
        String fqcn = pkg.substring("package ".length(), pkg.length() - 1).trim() + "." + type.group(1);

        int start = xml.indexOf("class=\"" + fqcn + "\"");
        if (start < 0) return "";
        int end = xml.indexOf("</entity>", start);
        int endSuper = xml.indexOf("</mapped-superclass>", start);
        if (end < 0 || (endSuper >= 0 && endSuper < end)) end = endSuper;
        String block = xml.substring(start, end);

        java.util.regex.Matcher element = java.util.regex.Pattern
                .compile("<([a-z-]+) name=\"" + fieldName + "\"[^>]*?(/>|>)").matcher(block);
        if (!element.find()) return "";
        String text = element.group(2).equals("/>") ? element.group()
                : block.substring(element.start(), block.indexOf("</" + element.group(1) + ">", element.start()));
        return text + (text.contains("fetch=\"LAZY\"") ? "\nFetchType.LAZY" : "") + "\n";
    }

    /** A method's text, from its signature to the line that closes it. */
    private String methodBody(String source, String signatureStart) {
        int at = source.indexOf(signatureStart);
        if (at < 0) return "";

        String[] lines = source.substring(at).split("\n", -1);
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            body.append(line).append('\n');
            if (line.trim().equals("}")) break;
        }
        return body.toString();
    }

    /**
     * The file holding an entity's fields: its own, or its regenerated base
     * when @behavior split it and the entity file is the developer's.
     */
    private String stateOf(String entityName) {
        String base = SOURCES.get(entityName + "Base.java");
        return base != null ? base : SOURCES.get(entityName + ".java");
    }

    private String capital(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
