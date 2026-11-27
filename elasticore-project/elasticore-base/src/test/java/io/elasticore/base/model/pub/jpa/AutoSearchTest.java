package io.elasticore.base.model.pub.jpa;

import io.elasticore.base.CodePublisher;
import io.elasticore.base.ECoreModelContext;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.SourceFileAccessFactory;
import io.elasticore.base.model.ModelComponent;
import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.core.BaseECoreModelContext;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.pub.rules.verify.BlueprintLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AutoSearch, the decision the 2.x publishers and the rule engine share (issue 376), on a model loaded in this
 * JVM: which condition a field's type gives it, which fields an entity inherits, the entity a search DTO
 * searches, which fields the @expose search DTOs have, and the condition SrcFilePublisher gives a field.
 */
class AutoSearchTest {

    static final String ENV = String.join(
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

    /** AsPost is @expose, AsNotice @service, both extend AsAudit; three DTOs written in the DSL. */
    static final String MODEL = String.join(
            "\n",
            "entity:",
            "  AsAudit:",
            "    meta: entity @abstract",
            "    fields:",
            "      createdBy:  string(50)",
            "      createDate: datetime",
            "",
            "  AsPost:",
            "    meta: entity @expose @extend(AsAudit)",
            "    fields:",
            "      postId:    long @id @sequence",
            "      title:     string(100)",
            "      code:      string(10) @search(eq)",
            "      hitCount:  int",
            "      active:    boolean",
            "      visitDay:  date",
            "      visitTime: time",
            "      stampAt:   LocalDateTime",
            "      onDay:     LocalDate",
            "      atTime:    LocalTime",
            "",
            "  AsNotice:",
            "    meta: entity @service @extend(AsAudit)",
            "    fields:",
            "      noticeId: long @id @sequence",
            "      memo:     string(100)",
            "      body:     string(100) @search(like)",
            "",
            "dto:",
            "  AsPostNameSrchDTO:",
            "    meta: dto @searchable(entity=AsPost)",
            "    fields:",
            "      keyword: string @search(like) @ref(title)",
            "",
            "  AsReportDTO:",
            "    meta: dto @expose",
            "    fields:",
            "      note: string(20)",
            "",
            "  AsTplSrchDTO:",
            "    meta: template @searchable(entity=AsPost) @expose",
            "    fields:",
            "      tplOnly: string(10)",
            "");

    private Object[] saved;
    private ECoreModelContext ctx;

    @BeforeEach
    void load(@TempDir Path tmp) throws Exception {
        saved = ModelContexts.save();
        ctx = load(tmp, "asrch", MODEL);
    }

    @AfterEach
    void restore() throws Exception {
        ModelContexts.restore(saved);
    }

    /** The blueprint written and loaded in this JVM; the model context it made. */
    static ECoreModelContext load(Path tmp, String domain, String model) throws Exception {
        Path dir = tmp.resolve("blueprint/" + domain);
        Files.createDirectories(dir.resolve("model"));
        Files.write(dir.resolve("env.yml"), String.format(ENV, domain, domain).getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("model/entity.yml"), model.getBytes(StandardCharsets.UTF_8));
        BlueprintLoader.load(tmp.resolve("blueprint").toString());
        ECoreModelContext loaded = BaseECoreModelContext.getContext();
        assertNotNull(loaded);
        return loaded;
    }

    @Test
    void theTypeGivesTheCondition() {
        Entity post = entity("AsPost");
        assertEquals("auto", AutoSearch.conditionByType(own(post, "title")), "text, by pattern");
        assertEquals("eq", AutoSearch.conditionByType(own(post, "hitCount")), "a number, by equality");
        assertEquals("eq", AutoSearch.conditionByType(own(post, "active")), "a yes or no, by equality");
        assertEquals("between", AutoSearch.conditionByType(own(entity("AsAudit"), "createDate")), "datetime");
        assertEquals("between", AutoSearch.conditionByType(own(post, "visitDay")), "date");
        assertEquals("between", AutoSearch.conditionByType(own(post, "stampAt")), "LocalDateTime");
        assertEquals("between", AutoSearch.conditionByType(own(post, "onDay")), "LocalDate");
        assertEquals("between", AutoSearch.conditionByType(own(post, "atTime")), "LocalTime");
        // time is not one of the range types 2.x lists: searched by equality, as 2.x does
        assertEquals("eq", AutoSearch.conditionByType(own(post, "visitTime")), "time");
    }

    @Test
    void aFieldIsInheritedWhenTheEntityDoesNotDeclareIt() {
        Entity post = entity("AsPost");
        Field createdBy = own(entity("AsAudit"), "createdBy");
        assertTrue(AutoSearch.isInherited(post, createdBy));
        assertFalse(AutoSearch.isInherited(post, own(post, "title")));
        assertFalse(AutoSearch.isInherited(null, createdBy), "no entity, nothing inherited");
    }

    @Test
    void theSearchedEntityIsTheOneSearchableNames() {
        assertSame(
                entity("AsNotice"),
                AutoSearch.searchedEntity(ctx, dto("AsNoticeSrchDTO").getMetaInfo()));
        assertSame(
                entity("AsPost"),
                AutoSearch.searchedEntity(ctx, dto("AsPostNameSrchDTO").getMetaInfo()));
        assertNull(AutoSearch.searchedEntity(ctx, dto("AsReportDTO").getMetaInfo()), "not a search DTO");
        assertNull(AutoSearch.searchedEntity(null, dto("AsNoticeSrchDTO").getMetaInfo()), "no context");
    }

    @Test
    void theExposedSearchDtosFieldsAreTheExposeSearchDtosOnly() {
        assertEquals("template", dto("AsTplSrchDTO").getMetaInfo().getMetaAnnotationValue("type"));

        Set<Map<String, Annotation>> maps = AutoSearch.exposedSearchAnnotationMaps(ctx);
        // AsPostSrchDTO (@expose): its own fields and the inherited ones, which share the entity fields' maps
        assertTrue(maps.contains(own(entity("AsPost"), "title").getAnnotationMap()));
        assertTrue(maps.contains(own(entity("AsAudit"), "createdBy").getAnnotationMap()));
        // AsNoticeSrchDTO is searchable, not @expose
        assertFalse(maps.contains(own(entity("AsNotice"), "memo").getAnnotationMap()));
        // a DSL search DTO without @expose
        assertFalse(maps.contains(own(dto("AsPostNameSrchDTO"), "keyword").getAnnotationMap()));
        // @expose on a DTO written in the DSL makes it a search DTO of its own (the loader adds @searchable)
        assertTrue(dto("AsReportDTO").getMetaInfo().hasMetaAnnotation("searchable"));
        assertTrue(maps.contains(own(dto("AsReportDTO"), "note").getAnnotationMap()));
        // a template, @searchable and @expose as it is
        assertFalse(maps.contains(own(dto("AsTplSrchDTO"), "tplOnly").getAnnotationMap()));

        assertTrue(AutoSearch.exposedSearchAnnotationMaps(null).isEmpty(), "no context, no fields");
    }

    @Test
    void thePublisherGivesTheConditionOfTheFieldsASearchDtoSearchesWithoutTheirOwn() {
        SrcFilePublisher publisher = new SrcFilePublisher(new ContextOnly(ctx));

        // @expose: every field, by its type; one with its own @search keeps it (null here)
        DataTransfer post = dto("AsPostSrchDTO");
        assertEquals("auto", publisher.getAutoSearchCondition(post, field(post, "title")));
        assertEquals("eq", publisher.getAutoSearchCondition(post, field(post, "hitCount")));
        assertEquals("between", publisher.getAutoSearchCondition(post, field(post, "createDate")));
        assertNull(publisher.getAutoSearchCondition(post, field(post, "code")));

        // @service: the inherited fields an @expose search DTO has; its own only by their own @search
        DataTransfer notice = dto("AsNoticeSrchDTO");
        assertEquals("auto", publisher.getAutoSearchCondition(notice, field(notice, "createdBy")));
        assertEquals("between", publisher.getAutoSearchCondition(notice, field(notice, "createDate")));
        assertNull(publisher.getAutoSearchCondition(notice, field(notice, "memo")));
        assertNull(publisher.getAutoSearchCondition(notice, field(notice, "body")));

        // written in the DSL: the same - the entity's own title is not searched by it
        DataTransfer byName = dto("AsPostNameSrchDTO");
        assertEquals("auto", publisher.getAutoSearchCondition(byName, field(byName, "createdBy")));
        assertNull(publisher.getAutoSearchCondition(byName, field(byName, "title")));
        assertNull(publisher.getAutoSearchCondition(byName, field(byName, "keyword")));
    }

    // ------------------------------------------------------------------

    private Entity entity(String name) {
        Entity e = ctx.findModelComponent(name, Entity.class);
        assertNotNull(e, name);
        return e;
    }

    private DataTransfer dto(String name) {
        DataTransfer d = ctx.findModelComponent(name, DataTransfer.class);
        assertNotNull(d, name);
        return d;
    }

    private static Field own(io.elasticore.base.model.DataModelComponent c, String name) {
        Field f = (Field) c.getItems().findByName(name);
        assertNotNull(f, name + " of " + c.getIdentity().getName());
        return f;
    }

    private static Field field(DataTransfer dto, String name) {
        io.elasticore.base.model.core.ListMap<String, Field> fields = dto.getAllFieldListMap();
        Field f = fields.get(name);
        assertNotNull(f, name + " of " + dto.getIdentity().getName());
        return f;
    }

    /** A publisher that only hands out the model context, which is all getAutoSearchCondition asks of it. */
    private static final class ContextOnly implements CodePublisher {
        private final ECoreModelContext ctx;

        ContextOnly(ECoreModelContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void setSrcCodeWriterFactory(SourceFileAccessFactory sourceFileAccessFactory) {}

        @Override
        public SourceFileAccessFactory getSrcFileAccessFactory() {
            return null;
        }

        @Override
        public ECoreModelContext getECoreModelContext() {
            return ctx;
        }

        @Override
        public void publish(ECoreModelContext c, ModelDomain domain) {}

        @Override
        public void errorOnPublish(ModelComponent modelComponent, Throwable e) {}

        @Override
        public boolean deleteSource(ModelDomain domain, String rootDir) {
            return false;
        }
    }

    /** The model context is static: what a test loads is put back as it was afterwards. */
    static final class ModelContexts {
        private static final String[] FIELDS = {"STATIC_DOMAIN_MAP", "context", "tmpCtx"};

        private ModelContexts() {}

        static Object[] save() throws Exception {
            Object[] saved = new Object[FIELDS.length];
            for (int i = 0; i < FIELDS.length; i++) {
                java.lang.reflect.Field f = BaseECoreModelContext.class.getDeclaredField(FIELDS[i]);
                f.setAccessible(true);
                Object value = f.get(null);
                saved[i] = value instanceof Map ? new java.util.HashMap<Object, Object>((Map<?, ?>) value) : value;
            }
            return saved;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        static void restore(Object[] saved) throws Exception {
            for (int i = 0; i < FIELDS.length; i++) {
                java.lang.reflect.Field f = BaseECoreModelContext.class.getDeclaredField(FIELDS[i]);
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
    }
}
