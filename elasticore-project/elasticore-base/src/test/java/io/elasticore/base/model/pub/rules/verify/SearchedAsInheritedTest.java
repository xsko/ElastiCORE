package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.core.BaseECoreModelContext;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule engine's half of issue 376, on a model loaded in this JVM: SpecFactory marks the fields a search DTO
 * that is not @expose searches as inherited ones, and a generation run collects the @expose search DTOs' fields
 * once - per model context, and forgotten when the run ends.
 */
class SearchedAsInheritedTest {

    private static final String ENV = String.join(
            "\n",
            "elasticore:",
            "  enable: true",
            "  config:",
            "    domainName: %s",
            "    framework: springboot",
            "    mode: jpa",
            "    j2ee: jakarta",
            "  namespace:",
            "    base: com.acme.%s",
            "");

    /** Notice (@service) and Post (@expose) extend Audit. */
    private static final String EXPOSED = String.join(
            "\n",
            "entity:",
            "  %1$sAudit:",
            "    meta: entity @abstract",
            "    fields:",
            "      createdBy:  string(50)",
            "      createDate: datetime",
            "  %1$sNotice:",
            "    meta: entity @service @extend(%1$sAudit)",
            "    fields:",
            "      noticeId: long @id @sequence",
            "      memo:     string(100)",
            "      body:     string(100) @search(like)",
            "  %1$sPost:",
            "    meta: entity @expose @extend(%1$sAudit)",
            "    fields:",
            "      postId: long @id @sequence",
            "      title:  string(100)",
            "");

    /** The same without the @expose entity: no search DTO has the parent's fields. */
    private static final String SERVICE_ONLY = String.join(
            "\n",
            "entity:",
            "  %1$sAudit:",
            "    meta: entity @abstract",
            "    fields:",
            "      createdBy:  string(50)",
            "      createDate: datetime",
            "  %1$sNotice:",
            "    meta: entity @service @extend(%1$sAudit)",
            "    fields:",
            "      noticeId: long @id @sequence",
            "      memo:     string(100)",
            "      body:     string(100) @search(like)",
            "");

    private static final String[] CONTEXT = {"STATIC_DOMAIN_MAP", "context", "tmpCtx"};

    private Object[] saved;

    @BeforeEach
    void save() throws Exception {
        saved = new Object[CONTEXT.length];
        for (int i = 0; i < CONTEXT.length; i++) {
            java.lang.reflect.Field f = BaseECoreModelContext.class.getDeclaredField(CONTEXT[i]);
            f.setAccessible(true);
            Object value = f.get(null);
            saved[i] = value instanceof Map ? new java.util.HashMap<Object, Object>((Map<?, ?>) value) : value;
        }
    }

    @AfterEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void restore() throws Exception {
        SpecFactory.exposedSearchPerRun(false);
        for (int i = 0; i < CONTEXT.length; i++) {
            java.lang.reflect.Field f = BaseECoreModelContext.class.getDeclaredField(CONTEXT[i]);
            f.setAccessible(true);
            if (saved[i] instanceof Map) {
                Map map = (Map) f.get(null);
                map.clear();
                map.putAll((Map) saved[i]);
            } else {
                f.set(null, saved[i]);
            }
        }
    }

    @Test
    void theInheritedFieldsAnExposeSearchDtoHasAreMarked(@TempDir Path tmp) throws Exception {
        ECoreModel model = load(tmp, "sai", EXPOSED);

        EntitySpec notice = SpecFactory.fromDto(dto(model, "SaiNoticeSrchDTO"), model, "sai");
        assertTrue(field(notice, "createdBy").searchedAsInherited());
        assertTrue(field(notice, "createDate").searchedAsInherited());
        assertFalse(field(notice, "memo").searchedAsInherited(), "its own field");
        assertFalse(field(notice, "body").searchedAsInherited(), "a field with its own @search");

        // the @expose search DTO searches every field anyway: nothing is marked there
        EntitySpec post = SpecFactory.fromDto(dto(model, "SaiPostSrchDTO"), model, "sai");
        assertFalse(field(post, "createdBy").searchedAsInherited());
    }

    @Test
    void withoutAnExposeSearchDtoNothingIsMarked(@TempDir Path tmp) throws Exception {
        ECoreModel model = load(tmp, "sao", SERVICE_ONLY);
        EntitySpec notice = SpecFactory.fromDto(dto(model, "SaoNoticeSrchDTO"), model, "sao");
        assertFalse(field(notice, "createdBy").searchedAsInherited());
        assertFalse(field(notice, "createDate").searchedAsInherited());
    }

    /**
     * Within a run the collection is made once and reused - but only for the context it was made from: a model
     * loaded afterwards is another context and is collected again, not answered from the first.
     */
    @Test
    void aRunCollectsOncePerContext(@TempDir Path tmp) throws Exception {
        SpecFactory.exposedSearchPerRun(true);

        ECoreModel serviceOnly = load(tmp.resolve("a"), "sca", SERVICE_ONLY);
        assertFalse(field(SpecFactory.fromDto(dto(serviceOnly, "ScaNoticeSrchDTO"), serviceOnly, "sca"), "createdBy")
                .searchedAsInherited());

        ECoreModel exposed = load(tmp.resolve("b"), "scb", EXPOSED);
        EntitySpec notice = SpecFactory.fromDto(dto(exposed, "ScbNoticeSrchDTO"), exposed, "scb");
        assertTrue(field(notice, "createdBy").searchedAsInherited(), "collected again for the new context");
        // and reused for the same one
        EntitySpec again = SpecFactory.fromDto(dto(exposed, "ScbNoticeSrchDTO"), exposed, "scb");
        assertTrue(field(again, "createdBy").searchedAsInherited());

        SpecFactory.exposedSearchPerRun(false);
        EntitySpec after = SpecFactory.fromDto(dto(exposed, "ScbNoticeSrchDTO"), exposed, "scb");
        assertTrue(field(after, "createdBy").searchedAsInherited(), "outside a run, collected each time");
    }

    /** Once per run: the same context gets the same collection; outside a run nothing is kept. */
    @Test
    void theCollectionIsMadeOnceInARunAndKeptByNothingOutsideOne(@TempDir Path tmp) throws Exception {
        load(tmp, "sak", EXPOSED);
        io.elasticore.base.ECoreModelContext ctx = BaseECoreModelContext.getContext();
        java.lang.reflect.Method collect = SpecFactory.class.getDeclaredMethod(
                "exposedSearchAnnotationMaps", io.elasticore.base.ECoreModelContext.class);
        collect.setAccessible(true);
        java.lang.reflect.Field kept = SpecFactory.class.getDeclaredField("exposedSearch");
        kept.setAccessible(true);

        SpecFactory.exposedSearchPerRun(true);
        Object first = collect.invoke(null, ctx);
        assertSame(first, collect.invoke(null, ctx), "collected once for the run");
        SpecFactory.exposedSearchPerRun(false);
        assertNull(kept.get(null), "the run's end forgets it");

        Object outside = collect.invoke(null, ctx);
        org.junit.jupiter.api.Assertions.assertNotSame(first, outside, "outside a run, collected again");
        assertNull(kept.get(null), "and not kept");
    }

    /** The two marks a rule reads: set fluently, as the spec's other setters are. */
    @Test
    void theMarksAreSetFluently(@TempDir Path tmp) throws Exception {
        ECoreModel model = load(tmp, "sam", EXPOSED);
        FieldSpec memo = field(SpecFactory.fromDto(dto(model, "SamNoticeSrchDTO"), model, "sam"), "memo");
        assertNull(memo.derivedSearch());
        assertSame(memo, memo.searchedAsInherited(true));
        assertTrue(memo.searchedAsInherited());
        assertSame(memo, memo.derivedSearch("eq"));
        assertEquals("eq", memo.derivedSearch());
    }

    // ------------------------------------------------------------------

    private static ECoreModel load(Path tmp, String domain, String model) throws Exception {
        String prefix = Character.toUpperCase(domain.charAt(0)) + domain.substring(1);
        Path dir = tmp.resolve("blueprint/" + domain);
        Files.createDirectories(dir.resolve("model"));
        Files.write(dir.resolve("env.yml"), String.format(ENV, domain, domain).getBytes(StandardCharsets.UTF_8));
        Files.write(
                dir.resolve("model/entity.yml"), String.format(model, prefix).getBytes(StandardCharsets.UTF_8));
        BlueprintLoader loader = BlueprintLoader.load(tmp.resolve("blueprint").toString());
        assertEquals(1, loader.allModels().size());
        return loader.allModels().get(0);
    }

    private static DataTransfer dto(ECoreModel model, String name) {
        DataTransfer d = model.getDataTransferModels().findByName(name);
        assertNotNull(d, name);
        return d;
    }

    private static FieldSpec field(EntitySpec spec, String name) {
        for (FieldSpec f : spec.fields()) {
            if (f.name().equals(name)) return f;
        }
        throw new AssertionError(name + " is not a field of " + spec.getName());
    }
}
