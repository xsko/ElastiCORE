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

    /**
     * Whether a delete keeps the rows of this entity: an entity (not a plain
     * {@code @abstract} frame), and a @deleted it can honour. The root of an
     * {@code @abstract(entity)} hierarchy is one: its repository reads every subclass's rows.
     */
    public static boolean applies(EntitySpec e) {
        return e != null && !e.isMappedSuperclass() && field(e) != null;
    }

    /** JOINED and TABLE_PER_CLASS, the hierarchies of an @abstract(entity) whose @deleted is honoured. */
    static final String JOINED = "JOINED";

    static final String TABLE_PER_CLASS = "TABLE_PER_CLASS";

    /** Whether the entity is the root of an @abstract(entity) hierarchy or below one. */
    static boolean inHierarchy(EntitySpec e) {
        return e.isAbstractEntity() || e.abstractEntityAbove() != null;
    }

    /** The strategy of the @abstract(entity) hierarchy the entity is in (JOINED, TABLE_PER_CLASS), or null. */
    static String strategy(EntitySpec e) {
        String text = e.isAbstractEntity()
                ? String.join(" ", e.nativeEntityAnnotationLines())
                : e.abstractEntityRootInheritance();
        if (text == null) return null;
        if (text.contains(TABLE_PER_CLASS)) return TABLE_PER_CLASS;
        return text.contains(JOINED) ? JOINED : null;
    }

    /** The key a row is deleted by: the entity's own, or the one a subclass inherits from its root. */
    static FieldSpec key(EntitySpec e) {
        FieldSpec own = e.pkField();
        if (own != null) return own;
        for (FieldSpec f : e.allFields()) {
            if (f.hasDsl("id") || f.hasDsl("pk")) return f;
        }
        return null;
    }

    private static int keyCount(EntitySpec e) {
        if (e.pkField() != null) return e.pkCount();
        int n = 0;
        for (FieldSpec f : e.allFields()) {
            if (f.hasDsl("id") || f.hasDsl("pk")) n++;
        }
        return n;
    }

    /**
     * Why the entity's @deleted cannot be honoured, or null when it can (or
     * there is none): one field, a time, not the key, a single key, a table of
     * its own or a JOINED / TABLE_PER_CLASS @abstract(entity) hierarchy whose root
     * has it. A plain @abstract parent is checked in each entity that extends it.
     */
    public static String problem(EntitySpec e) {
        List<FieldSpec> marked = marked(e);
        if (marked.isEmpty()) return null;
        if (e.isMappedSuperclass()) return null;
        if (inHierarchy(e)) {
            String why = hierarchyProblem(e);
            if (why != null) return why;
        }
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
        FieldSpec key = key(e);
        if (key == null) return " @deleted needs a key of one field to delete a row by";
        if (keyCount(e) > 1)
            return " @deleted: a composite key is not supported yet - give the entity a key of one field";
        // Inherited from an entity with a table of its own (JOINED): the column is in the parent's table.
        // In an @abstract(entity) hierarchy that is the root's, whose delete writes it (jpaDelete).
        if (!inHierarchy(e) && f.isParentField() && f.declaringName() != null && !f.declaringAbstract()) {
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
        if (!MyBatisSql.applies(e) && !versioned && !ServiceAccessRules.keyGenerated(key)) {
            return " @deleted with a key the request gives (" + key.name()
                    + ") needs a version (@jpa:Version) - a create"
                    + " of a deleted row's key would otherwise be written into that row, which stays deleted";
        }
        return null;
    }

    /**
     * Why @deleted in an @abstract(entity) hierarchy cannot be honoured, or null:
     * the root's strategy has a table per subclass (JOINED, TABLE_PER_CLASS - SINGLE_TABLE
     * stops at the root anyway), and the field is the root's, so every row a read
     * through the root finds is kept or removed alike.
     */
    private static String hierarchyProblem(EntitySpec e) {
        if (strategy(e) == null) {
            return " @deleted in an @abstract(entity) hierarchy needs @jpa:Inheritance(strategy ="
                    + " InheritanceType.JOINED) or TABLE_PER_CLASS on its root";
        }
        String root = e.abstractEntityAbove();
        if (root != null && !e.abstractEntityRootDeleted()) {
            return " @deleted on a subclass of the @abstract(entity) " + root + " is not supported - a read through "
                    + root + " would find kept and removed rows alike; declare it on " + root;
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

    /**
     * Hibernate's @SQLDelete: the time written, by key (and version, which Hibernate binds after it);
     * null when the class has no table to write it in.
     *
     * In an @abstract(entity) hierarchy Hibernate deletes a row from each table the class
     * maps to, each with that class's own statement. TABLE_PER_CLASS: the subclass's table
     * has every column, and its statement writes the time there; the root has no table.
     * JOINED: the column is in the root's table, whose statement (the root's) writes it;
     * a subclass's table keeps its row, by a statement that changes nothing and finds the
     * one row Hibernate counts on - in whichever order the two run.
     */
    public static String jpaDelete(EntitySpec e) {
        String strategy = inHierarchy(e) ? strategy(e) : null;
        if (TABLE_PER_CLASS.equals(strategy) && e.isAbstractEntity()) return null;
        String key = MyBatisSql.column(key(e)) + " = ?";
        if (JOINED.equals(strategy) && !e.isAbstractEntity()) {
            return "UPDATE " + e.abstractEntityRootTable() + " SET " + column(e) + " = " + column(e) + " WHERE " + key;
        }
        StringBuilder where = new StringBuilder(key);
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
