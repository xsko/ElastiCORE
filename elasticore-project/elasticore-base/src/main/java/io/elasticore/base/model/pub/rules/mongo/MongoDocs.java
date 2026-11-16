package io.elasticore.base.model.pub.rules.mongo;

import io.elasticore.base.model.pub.rules.DomainSpec;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.RulesConfig;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.mybatis.MyBatisSql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How a domain whose env.yml says mode: mongo keeps its entities in MongoDB:
 * what every rule of this package asks.
 *
 * An entity with a repository of its own (@expose, @service, @repository) is
 * a document of its own collection, named as the JPA table is. A to-one
 * relation to such an entity is stored as the other document's id
 * (@DocumentReference, read lazily). An entity without a collection of its
 * own - an order's line, which no service writes by itself - is stored inside
 * the document that holds it: a collection of them as an array, one of them
 * as a nested document. What the other side holds is not stored twice: the
 * inverse of a relation (mappedBy) and a member's way back to its holder are
 * left out of the document (@Transient).
 */
public final class MongoDocs {

    private MongoDocs() {}

    /** The runtime class that stores an enum or a value type as its JPA converter does. */
    public static final String VALUE_CONVERTER = "io.elasticore.springboot3.mongo.AttributeValueConverter";

    /** The runtime class that draws the next number of a @sequence key. */
    public static final String SEQUENCES = "io.elasticore.springboot3.mongo.MongoSequences";

    /** Whether a domain's env.yml mode is MongoDB. */
    public static boolean applies(EntitySpec e) {
        return e != null && RulesConfig.mongo(e.config("mode"));
    }

    /** As above, for a domain-level artifact. */
    public static boolean applies(DomainSpec d) {
        return d != null && RulesConfig.mongo(d.config("mode"));
    }

    /** The collection the entity's documents are kept in: the name its JPA table has. */
    public static String collection(EntitySpec e) {
        return MyBatisSql.table(e);
    }

    /**
     * Whether an entity of the run is kept in a collection of its own: it
     * has a repository - a service of its own, or @repository.
     */
    public static boolean ownCollection(String entityName) {
        if (entityName == null) return false;
        if (MyBatisSql.hasOwnService(entityName)) return true;
        try {
            io.elasticore.base.ECoreModelContext ctx = io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            io.elasticore.base.model.entity.Entity entity = ctx == null
                    ? null
                    : ctx.findModelComponent(entityName, io.elasticore.base.model.entity.Entity.class);
            return entity != null && entity.getMetaInfo().hasMetaAnnotation("repository");
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** The name a field is stored under when the DSL names a column, or null for the property's own. */
    public static String explicitName(FieldSpec f) {
        String named = f.dslValueAny("column", "col");
        if (named == null || named.trim().isEmpty() || named.trim().equals(f.name())) return null;
        return named.trim();
    }

    /** A to-one relation: one entity, not a value the row embeds. */
    private static boolean toOne(EntitySpec e, FieldSpec f) {
        return f.isEntityRef() && !f.isList() && !MyBatisSql.isEmbedded(e, f);
    }

    /** The mappedBy a relation names: the other side holds it. */
    private static String mappedBy(FieldSpec f) {
        String m = f.dslProperty("onetomany", "mappedBy");
        if (m == null || m.trim().isEmpty()) m = f.dslProperty("onetoone", "mappedBy");
        return m == null || m.trim().isEmpty() ? null : m.trim();
    }

    /**
     * A to-one relation stored as the other document's id: the target has a
     * collection of its own, and this side holds the relation.
     */
    public static boolean isReference(EntitySpec e, FieldSpec f) {
        return toOne(e, f)
                && mappedBy(f) == null
                && f.dslValueAny("joinby", "joinfield") == null
                && ownCollection(f.coreItemType())
                && !isBackReference(e, f);
    }

    /**
     * Rows of entities stored in this document: a collection, or one row, of
     * an entity without a collection of its own.
     */
    public static boolean isEmbeddedMember(EntitySpec e, FieldSpec f) {
        if (!f.isEntityRef() || MyBatisSql.isEmbedded(e, f) || f.hasDsl("manytomany")) return false;
        if (!f.isList() && (mappedBy(f) != null || f.dslValueAny("joinby", "joinfield") != null)) return false;
        String target = f.coreItemType();
        return target != null && !target.equals(e.getName()) && !ownCollection(target) && !isBackReference(e, f);
    }

    /**
     * A member's relation back to the document that holds it: an entity
     * without a collection of its own, pointing at one that embeds it. The
     * holder is the way to it, so nothing is stored.
     */
    public static boolean isBackReference(EntitySpec e, FieldSpec f) {
        if (!toOne(e, f) || ownCollection(e.getName())) return false;
        EntitySpec holder = e.related(f.coreItemType());
        if (holder == null) return false;
        for (FieldSpec h : holder.fields()) {
            if (h.isEntityRef() && e.getName().equals(h.coreItemType())) return true;
        }
        return false;
    }

    /**
     * Whether a field is left out of the document: the inverse side of a
     * relation, a member's way back to its holder, a relation read through an
     * id field (@joinBy - the id is what is stored), a collection of another
     * collection's documents, and a @formula, which only JPA's SELECT computes.
     */
    public static boolean isTransient(EntitySpec e, FieldSpec f) {
        if (f.hasDsl("formula")) return true;
        if (!f.isEntityRef() || MyBatisSql.isEmbedded(e, f)) return false;
        return !isEmbeddedMember(e, f) && !isReference(e, f);
    }

    /** The collections of members stored in the document. */
    public static List<FieldSpec> memberLists(EntitySpec e) {
        List<FieldSpec> out = new ArrayList<FieldSpec>();
        for (FieldSpec f : MyBatisSql.fields(e)) {
            if (f.isList() && isEmbeddedMember(e, f)) out.add(f);
        }
        return out;
    }

    /** Whether a value is stored through its JPA converter: an enum with @db, money, quantity. */
    public static boolean converted(EntitySpec e, FieldSpec f) {
        if (f.isList()) return false;
        if (f.isMoney() || f.isQuantity()) return true;
        String type = f.coreItemType();
        return e.isKnownEnum(type) && e.enumHasDb(type);
    }

    /** Whether a value is a decimal, kept as Decimal128 so that it compares and sorts as a number. */
    public static boolean decimal(FieldSpec f) {
        return !f.isList() && "BigDecimal".equals(RuleText.simpleName(f.typeName()));
    }

    /** Whether a key is drawn from the sequences collection: @sequence and its kin, on a number. */
    public static boolean sequenced(FieldSpec pk) {
        return MyBatisSql.generatedKey(pk);
    }

    /** The Java expression of the next key of a sequenced one, or null for any other key. */
    public static String nextKey(FieldSpec pk, String name, String mongoRef) {
        if (!sequenced(pk)) return null;
        String call = String.format("%s.next(%s, %s)", SEQUENCES, mongoRef, RuleText.quote(name));
        String t = RuleText.simpleName(pk.typeName()).toLowerCase(Locale.ROOT);
        if (t.equals("int") || t.equals("integer")) return "Math.toIntExact(" + call + ")";
        return call;
    }

    /**
     * What a domain under mode: mongo does not write yet, one line each -
     * refused before any file of the entity is written.
     */
    public static List<String> unsupported(EntitySpec e) {
        List<String> out = new ArrayList<String>();
        if (e.pkCount() > 1) out.add("a key of more than one field");
        if (e.isAbstractEntity() || e.abstractEntityAbove() != null) out.add("an @abstract(entity) hierarchy");
        FieldSpec pk = e.pkField();
        if (pk != null && sequenced(pk)) {
            String t = RuleText.simpleName(pk.typeName()).toLowerCase(Locale.ROOT);
            if (!t.equals("long") && !t.equals("int") && !t.equals("integer")) {
                out.add(pk.name() + ": a @sequence key that is not a number (" + pk.typeName() + ")");
            }
        }
        for (FieldSpec f : MyBatisSql.fields(e)) {
            if (!f.isEntityRef() || MyBatisSql.isEmbedded(e, f)) continue;
            if (f.hasDsl("manytomany")) {
                out.add(f.name() + ": a many-to-many relation");
            } else if (f.isList() && ownCollection(f.coreItemType()) && mappedBy(f) == null) {
                out.add(f.name() + ": a collection of " + f.coreItemType()
                        + ", which has a collection of its own, without mappedBy");
            }
        }
        return out;
    }
}
