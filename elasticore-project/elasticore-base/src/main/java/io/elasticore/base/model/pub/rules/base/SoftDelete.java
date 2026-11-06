package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.mybatis.MyBatisSql;

import java.util.ArrayList;
import java.util.List;

/**
 * A row deleted is kept, with when it was deleted, and left out of every read
 * the generator writes:
 *
 * <pre>
 * Customer:
 *   fields:
 *     deletedAt: timestamp @deleted
 * </pre>
 *
 * The delete itself is where it always was - JPA's remove (so @PreRemove,
 * the lifecycle's delete guard and the invariants still run) or the MyBatis
 * mapper's deleteById - and it writes the time instead of removing the row:
 * Hibernate's @SQLDelete, an UPDATE in the mapper. The reads of the entity
 * leave such rows out: the JPA repository's redeclared finders and the
 * mapper's toSpec, a condition in each statement of the MyBatis mapper, and
 * a collection of them. What names a deleted row by a to-one relation still
 * reads it - an order line keeps the product it was for. The column is the
 * database's to write (CURRENT_TIMESTAMP), never a request's or the entity's.
 */
public final class SoftDelete {

    /** The field annotation. */
    public static final String DELETED = "deleted";

    private SoftDelete() {}

    /** The entity's @deleted fields, its own and those it inherits. */
    static List<FieldSpec> marked(EntitySpec e) {
        List<FieldSpec> out = new ArrayList<FieldSpec>();
        for (FieldSpec f : e.allFields()) {
            if (f.hasDsl(DELETED)) out.add(f);
        }
        return out;
    }

    /** The field that says when a row was deleted, or null when the entity's rows are deleted outright. */
    public static FieldSpec field(EntitySpec e) {
        if (e == null || problem(e) != null) return null;
        List<FieldSpec> marked = marked(e);
        return marked.isEmpty() ? null : marked.get(0);
    }

    /** Whether a delete keeps the rows of this entity: a table of its own, and a @deleted it can honour. */
    public static boolean applies(EntitySpec e) {
        return e != null && !e.hasMeta("abstract") && field(e) != null;
    }

    /**
     * Why the entity's @deleted cannot be honoured, or null when it can (or
     * there is none): one field, a time, not the key, a single key, a table of
     * its own. An abstract parent is checked in each entity that extends it.
     */
    public static String problem(EntitySpec e) {
        List<FieldSpec> marked = marked(e);
        if (marked.isEmpty() || e.hasMeta("abstract")) return null;
        if (e.hasMeta("embeddable"))
            return " @deleted: a value (@embeddable) is kept or removed with its row - mark the entity";
        if (e.hasMeta("rollup")) return " @deleted on a @rollup entity is not supported yet";
        for (FieldSpec f : e.allFields()) {
            if (f.hasDsl("discriminator"))
                return " @deleted on a single-table hierarchy (@discriminator) is not supported yet";
        }
        if (marked.size() > 1) {
            List<String> names = new ArrayList<String>();
            for (FieldSpec f : marked) names.add(f.name());
            return "has @deleted on " + String.join(" and ", names) + " - one field holds when a row was deleted";
        }
        FieldSpec f = marked.get(0);
        String base = f.baseTypeName();
        if (!"DATETIME".equals(base) && !"INSTANT".equals(base)) {
            return "." + f.name() + " @deleted is " + f.typeName()
                    + " - it holds when the row was deleted: datetime or timestamp";
        }
        if (f.hasDslAny("id", "pk")) return "." + f.name() + " @deleted is the key";
        // Hibernate 6.x reads <sql-delete> from orm.xml and applies nothing (only 7.0's
        // Hibernate Models applies it): under Spring Boot 3 the row would be deleted after
        // all. Said, and the build stopped. Spring Boot 4 brings Hibernate 7.
        if ("xml".equalsIgnoreCase(e.config("mapping"))
                && !MyBatisSql.applies(e)
                && io.elasticore.base.model.pub.rules.RulesConfig.springBootMajor() < 4) {
            return " @deleted with mapping: xml needs Spring Boot 4 (Hibernate 7) - Hibernate 6 does not apply the delete"
                    + " that keeps the row (<sql-delete>) from orm.xml; leave the domain's mapping in annotations, or use"
                    + " mode: mybatis";
        }
        if (e.pkField() == null) return " @deleted needs a key of one field to delete a row by";
        if (e.pkCount() > 1)
            return " @deleted: a composite key is not supported yet - give the entity a key of one field";
        // Inherited from an entity with a table of its own (JOINED): the column is in the parent's table.
        if (f.isParentField() && f.declaringName() != null && !f.declaringAbstract()) {
            return " @deleted is inherited from " + f.declaringName()
                    + ", which has a table of its own - not supported yet;"
                    + " declare it on an @abstract parent or on the entity";
        }
        // JPA merges a create whose key a deleted row holds into that row, which stays deleted: refused
        // unless the key is generated or a version tells a new row from a stored one (MyBatis inserts, and refuses).
        boolean versioned = false;
        for (FieldSpec v : e.allFields()) {
            if (v.isVersion()) versioned = true;
        }
        if (!MyBatisSql.applies(e) && !versioned && !ServiceAccessRules.keyGenerated(e.pkField())) {
            return " @deleted with a key the request gives (" + e.pkField().name()
                    + ") needs a version (@jpa:Version) - a create"
                    + " of a deleted row's key would otherwise be written into that row, which stays deleted";
        }
        return null;
    }

    /**
     * What a delete of this entity would remove outright with it: a member
     * of its aggregate, a relation that cascades the delete, that is not
     * {@code @deleted} itself. Kept rows that hold removed ones would be a deletion
     * nobody can undo, so this is refused rather than done.
     */
    public static List<String> removedWithIt(EntitySpec e) {
        List<String> out = new ArrayList<String>();
        if (!applies(e)) return out;
        for (FieldSpec f : e.allFields()) {
            if (!f.isEntityRef()) continue;
            String target = f.coreItemType();
            if (target == null || target.equals(e.getName()) || e.isEmbeddableType(target)) continue;
            // A member of the same aggregate goes with its root - a collection or a
            // to-one member alike; so does what the relation cascades a delete to.
            boolean member = e.knowsAggregateOf(target) && e.sameAggregate(target);
            String cascade = f.dslProperty("onetomany", "cascade") + " " + f.dslProperty("onetoone", "cascade");
            boolean orphans = "true".equals(f.dslProperty("onetomany", "orphanRemoval"))
                    || "true".equals(f.dslProperty("onetoone", "orphanRemoval"));
            // @cascade alone is ALL with orphan removal (JpaEntityBaseRules); @cascade(PERSIST) deletes nothing.
            String said =
                    f.hasDsl("cascade") ? String.valueOf(f.dslValue("cascade")).toUpperCase(java.util.Locale.ROOT) : "";
            boolean cascadeSaid = f.hasDsl("cascade")
                    && (f.dslValue("cascade") == null || said.contains("ALL") || said.contains("REMOVE"));
            boolean cascades = cascadeSaid || orphans || cascade.contains("ALL") || cascade.contains("REMOVE");
            if ((member || cascades) && e.softDeleteColumnOf(target) == null) out.add(f.name() + " (" + target + ")");
        }
        return out;
    }

    /**
     * Says, once per run, why the entity's @deleted is not honoured - the
     * build fails, since a delete would otherwise remove the row after all.
     */
    public static void check(EntitySpec e) {
        String why = problem(e);
        if (why != null) {
            io.elasticore.base.model.pub.rules.ModelCheck.fail(String.format("[elcore3] %s%s.", e.getName(), why));
            return;
        }
        List<String> removed = removedWithIt(e);
        if (!removed.isEmpty()) {
            io.elasticore.base.model.pub.rules.ModelCheck.fail(String.format(
                    "[elcore3] %s @deleted keeps its rows, but a delete would remove %s outright with it - mark %s"
                            + " @deleted too, so they are kept as well.",
                    e.getName(), String.join(", ", removed), removed.size() == 1 ? "that entity" : "those entities"));
        }
    }

    /** Hibernate's @SQLDelete: the time written, by key (and version, which Hibernate binds after it). */
    public static String jpaDelete(EntitySpec e) {
        StringBuilder where = new StringBuilder(MyBatisSql.column(e.pkField()) + " = ?");
        for (FieldSpec f : e.allFields()) {
            if (f.isVersion()) {
                where.append(" AND ").append(MyBatisSql.column(f)).append(" = ?");
                break;
            }
        }
        return update(e, where.toString());
    }

    /** The column of the field. */
    public static String column(EntitySpec e) {
        return MyBatisSql.column(field(e));
    }

    /** The condition a row that is not deleted meets; alias is the row's (or null for none). */
    public static String live(EntitySpec e, String alias) {
        return (alias == null || alias.isEmpty() ? "" : alias + ".") + column(e) + " IS NULL";
    }

    /** The statement that deletes a row of the entity: its time written, where it is not already deleted. */
    public static String update(EntitySpec e, String where) {
        return "UPDATE " + MyBatisSql.table(e) + " SET " + column(e) + " = CURRENT_TIMESTAMP WHERE " + where + " AND "
                + live(e, null);
    }
}
