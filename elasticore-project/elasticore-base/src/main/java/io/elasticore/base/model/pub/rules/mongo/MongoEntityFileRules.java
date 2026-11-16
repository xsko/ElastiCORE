package io.elasticore.base.model.pub.rules.mongo;

import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.mybatis.MyBatisSql;

import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The entity of a MongoDB domain: the class the JPA rules describe - its
 * fields, their types and initial values - mapped by Spring Data MongoDB
 * instead (MongoDocs says where each field is kept).
 *
 * The field rules (jpa-entity) run as for JPA; this set takes off every JPA,
 * Hibernate and Spring Data annotation they wrote, with the lifecycle
 * callbacks those would have called, and writes Spring Data MongoDB's on the
 * v3 entity skeleton: {@code @Document} on an entity with a collection of
 * its own, {@code @Id}, {@code @Version}, the auditing annotations,
 * {@code @Indexed} for {@code @unique} and {@code @CompoundIndex} for
 * {@code @index}, {@code @Field} where the DSL names a column, and
 * {@code @DocumentReference} for a relation to another collection's document.
 */
public class MongoEntityFileRules implements CodeRules {

    static final String MAPPING = "org.springframework.data.mongodb.core.mapping.";
    static final String INDEX = "org.springframework.data.mongodb.core.index.";
    static final String DATA = "org.springframework.data.annotation.";

    private static final java.util.regex.Pattern COMMA = java.util.regex.Pattern.compile(",");
    private static final java.util.regex.Pattern SPACES = java.util.regex.Pattern.compile("\\s+");

    @Rule(value = "mongo-lombok", target = "entity", order = 6)
    void lombok(EntitySpec e) {
        ctx(e).ensureClassAnnotation(AnnoCode.of("lombok.Getter"))
                .ensureClassAnnotation(AnnoCode.of("lombok.Setter"))
                .ensureClassAnnotation(AnnoCode.of("lombok.NoArgsConstructor"));
    }

    /** Whether a type belongs to a persistence API the JPA rules wrote for. */
    static boolean persistenceOnly(String fqcn) {
        return fqcn.startsWith("jakarta.persistence.")
                || fqcn.startsWith("javax.persistence.")
                || fqcn.startsWith("org.hibernate.")
                || fqcn.startsWith("org.springframework.data.")
                // a native annotation the DSL handed through as written (@jpa:...)
                || fqcn.startsWith("native:");
    }

    @Rule(value = "mongo-entity-plain", target = "entity", order = 80, phase = Rule.Phase.TAIL)
    void plain(EntitySpec e) {
        ctx(e).removeAnnotations(new java.util.function.Predicate<String>() {
            @Override
            public boolean test(String fqcn) {
                return persistenceOnly(fqcn);
            }
        });
    }

    @Rule(value = "mongo-entity-mapping", target = "entity", order = 85, phase = Rule.Phase.TAIL)
    void mapping(EntitySpec e) {
        SourceCode c = ctx(e);
        boolean document = !e.hasMeta("embeddable") && !e.isMappedSuperclass() && MongoDocs.ownCollection(e.getName());
        if (document) {
            c.ensureClassAnnotation(AnnoCode.of(MAPPING + "Document").val("collection", MongoDocs.collection(e)));
            List<EntitySpec.IndexSpec> indexes = e.indexes();
            for (int i = 0; i < indexes.size(); i++) {
                EntitySpec.IndexSpec idx = indexes.get(i);
                boolean unique = idx.unique() != null
                        && "true".equalsIgnoreCase(idx.unique().trim());
                String name = idx.name() == null || idx.name().isEmpty()
                        ? MongoDocs.collection(e) + "_ix" + (i + 1)
                        : idx.name();
                c.ensureClassAnnotation(AnnoCode.of(INDEX + "CompoundIndex")
                        .val("name", name)
                        .val("def", indexDef(e, idx.columnList()))
                        .valIf(unique, "unique", true));
            }
        }
        FieldSpec pk = e.pkField();
        for (FieldSpec f : e.fields()) {
            if (MongoDocs.isTransient(e, f)) {
                c.field(f).ensure(AnnoCode.of(DATA + "Transient"));
                continue;
            }
            if (f == pk) c.field(f).ensure(AnnoCode.of(DATA + "Id"));
            if (f.isVersion()) c.field(f).ensure(AnnoCode.of(DATA + "Version"));
            if (MyBatisSql.createdDate(f)) c.field(f).ensure(AnnoCode.of(DATA + "CreatedDate"));
            if (MyBatisSql.lastModifiedDate(f)) c.field(f).ensure(AnnoCode.of(DATA + "LastModifiedDate"));
            if (MyBatisSql.createdBy(f)) c.field(f).ensure(AnnoCode.of(DATA + "CreatedBy"));
            if (MyBatisSql.lastModifiedBy(f)) c.field(f).ensure(AnnoCode.of(DATA + "LastModifiedBy"));
            String named = f == pk ? null : MongoDocs.explicitName(f);
            boolean decimal = MongoDocs.decimal(f);
            if (named != null || decimal) {
                AnnoCode field = AnnoCode.of(MAPPING + "Field");
                if (named != null) field.val("value", named);
                if (decimal) {
                    field.valRaw("targetType", "%s.DECIMAL128", MAPPING + "FieldType");
                }
                c.field(f).ensure(field);
            }
            if (f.hasDsl("unique") && f != pk && document) {
                c.field(f)
                        .ensure(AnnoCode.of(INDEX + "Indexed")
                                .val("unique", true)
                                .val("sparse", true));
            }
            if (MongoDocs.converted(e, f)) {
                c.field(f)
                        .ensure(AnnoCode.of("org.springframework.data.convert.ValueConverter")
                                .valRaw("value", "%s.class", MongoDocs.VALUE_CONVERTER));
            }
            if (MongoDocs.isReference(e, f)) {
                c.field(f).ensure(AnnoCode.of(MAPPING + "DocumentReference").val("lazy", true));
            }
        }
    }

    /**
     * An @index's columns as a compound index's definition: each named by the
     * field it is stored under - a column the DSL derives is found by its
     * field - descending where it says DESC.
     */
    // Empty parts, trailing ones included, are skipped or dropped anyway: split as it is.
    @SuppressWarnings("StringSplitter")
    static String indexDef(EntitySpec e, String columnList) {
        StringBuilder sb = new StringBuilder("{");
        if (columnList != null) {
            for (String part : COMMA.split(columnList)) {
                String[] words = SPACES.split(part.trim());
                if (words.length == 0 || words[0].isEmpty()) continue;
                String name = words[0];
                for (FieldSpec f : MyBatisSql.fields(e)) {
                    if (f.name().equals(name) || MyBatisSql.column(f).equalsIgnoreCase(name)) {
                        String explicit = MongoDocs.explicitName(f);
                        name = f == e.pkField() ? "_id" : explicit != null ? explicit : f.name();
                        break;
                    }
                }
                boolean desc = words.length > 1 && "desc".equalsIgnoreCase(words[1]);
                if (sb.length() > 1) sb.append(", ");
                sb.append('\'').append(name).append("': ").append(desc ? -1 : 1);
            }
        }
        return sb.append('}').toString();
    }

    @Rule(value = "mongo-entity-file", target = "entity", order = 90, phase = Rule.Phase.TAIL)
    void fileVars(EntitySpec e) {
        SourceCode c = ctx(e);
        for (FieldSpec f : e.fields()) {
            RuleText.registerFieldTypeImport(c, e, f, e.pkg("entity"));
        }
        String modifier = e.hasMeta("abstract") ? "public abstract" : "public";
        String parent = e.parentName() == null ? "" : e.parentName().trim();
        String extendsClause = parent.isEmpty() || parent.equals(e.getName()) ? "" : " extends " + parent;
        c.var("packageName", e.pkg("entity"))
                .var("className", e.getName())
                .var("classAnnotationList", c.renderClassAnnotations())
                .var(
                        "classDeclaration",
                        "%s class %s%s implements java.io.Serializable {",
                        modifier,
                        e.getName(),
                        extendsClause)
                .var("importList", RuleText.stripTrailing(c.renderImports()))
                .varLines("fieldList", RuleText.linesOf(RuleText.stripTrailing(c.renderFieldBlock())));
    }
}
