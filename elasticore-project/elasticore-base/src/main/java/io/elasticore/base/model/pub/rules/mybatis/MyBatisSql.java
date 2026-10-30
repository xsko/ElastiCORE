package io.elasticore.base.model.pub.rules.mybatis;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.RulesConfig;
import io.elasticore.base.model.pub.rules.base.RuleText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How an entity of the model lies in its table, for the MyBatis code: the
 * table, each field's column, the relations a row joins, and the JDBC type a
 * value is bound as.
 *
 * The names are the ones the JPA code uses, so that a table can be read by
 * either persistence: the table @table names or the entity's name in snake
 * case, a column @column names or the field's, and a relation's key column
 * the one JPA derives - field, underscore, the key's column
 * (customer_customer_id). Spring Boot names a JPA column that is not named by
 * splitting a lower-case letter from the upper-case one that starts the next
 * word (CamelCaseToUnderscoresNamingStrategy); so does this.
 */
public final class MyBatisSql {

    private MyBatisSql() {
    }

    /** The alias of the root table in every statement. */
    public static final String ROOT = "t";

    /** Whether a domain's env.yml mode is MyBatis: what every rule of this package asks first. */
    public static boolean applies(EntitySpec e) {
        return e != null && RulesConfig.mybatis(e.config("mode"));
    }

    /** As above, for a domain-level artifact. */
    public static boolean applies(io.elasticore.base.model.pub.rules.DomainSpec d) {
        return d != null && RulesConfig.mybatis(d.config("mode"));
    }

    /**
     * The fields whose columns lie in the entity's row: those of the @abstract
     * class it extends (@extend, a mapped superclass - audit columns, say),
     * and that one's in turn, then its own. A field it declares again under
     * the same name replaces the inherited one.
     *
     * The parent is the entity's related spec of that name, which the runner
     * hands over; without it only the entity's own fields are known.
     */
    public static List<FieldSpec> fields(EntitySpec e) {
        EntitySpec parent = abstractParent(e);
        if (parent == null) return e.fields();
        List<FieldSpec> out = new ArrayList<FieldSpec>();
        java.util.Set<String> own = new java.util.HashSet<String>();
        for (FieldSpec f : e.fields()) own.add(f.name());
        for (FieldSpec f : fields(parent)) {
            if (!own.contains(f.name())) out.add(f);
        }
        out.addAll(e.fields());
        return out;
    }

    /** The @abstract class the entity extends, when the run knows it; null for none or a concrete one. */
    public static EntitySpec abstractParent(EntitySpec e) {
        String name = e.parentName();
        if (name == null || name.trim().isEmpty() || name.trim().equals(e.getName())) return null;
        EntitySpec parent = e.related(name.trim());
        return parent != null && parent.hasMeta("abstract") ? parent : null;
    }

    /** The table, with its schema when the entity names one. */
    public static String table(EntitySpec e) {
        String explicit = e.metaValue("table");
        String name = explicit != null && !explicit.isEmpty()
                ? explicit
                : (e.tablePrefix() == null ? "" : e.tablePrefix()) + springName(e.getName());
        name = springName(name);
        String schema = e.schema();
        return schema != null && !schema.isEmpty() ? springName(schema) + "." + name : name;
    }

    /** A column name as Spring Boot's JPA naming writes it from a property name. */
    public static String springName(String name) {
        StringBuilder sb = new StringBuilder(name.replace('.', '_'));
        for (int i = 1; i < sb.length() - 1; i++) {
            if (Character.isLowerCase(sb.charAt(i - 1)) && Character.isUpperCase(sb.charAt(i))
                    && Character.isLowerCase(sb.charAt(i + 1))) {
                sb.insert(i++, '_');
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** The column of a field that holds a value (not a relation). */
    public static String column(FieldSpec f) {
        // A named column goes through Spring's naming too, which writes it in lower case.
        String named = f.dbColumnName();
        if (named != null && !named.isEmpty()) return springName(named);
        named = f.dslValueAny("column", "col");
        if (named != null && !named.isEmpty()) return springName(named);
        return springName(f.name());
    }

    /**
     * The key column a to-one relation is stored in: the one @column / @db
     * names, or the field's name and the target key's, as JPA derives it.
     */
    public static String foreignKey(FieldSpec f, EntitySpec target) {
        String named = f.dslValueAny("column", "col", "db");
        if (named != null && !named.isEmpty()) return springName(named);
        FieldSpec pk = target == null ? null : target.pkField();
        String key = pk == null ? "id" : column(pk);
        return springName(f.name()) + "_" + key;
    }

    /** A field whose value lies in one column of the row: neither a collection, a relation nor an embedded value. */
    public static boolean isValue(EntitySpec e, FieldSpec f) {
        return !f.isList() && !f.isEntityRef() && !f.hasDsl("formula") && !f.hasDsl("transient")
                && !isEmbedded(e, f);
    }

    /** A to-one relation the row holds the key of. */
    public static boolean isToOne(FieldSpec f) {
        return f.isEntityRef() && !f.isList() && !f.hasDsl("embedded")
                && f.dslValueAny("joinby", "joinfield") == null && mappedBy(f) == null;
    }

    /** The mappedBy of a one-to-one: the other row holds the key, this one is read through it. */
    private static String mappedBy(FieldSpec f) {
        String mappedBy = f.dslProperty("onetoone", "mappedBy");
        return mappedBy == null || mappedBy.trim().isEmpty() ? null : mappedBy.trim();
    }

    /**
     * The field of the other row that holds this row's key, for a one-to-one
     * this row does not hold (@OneToOne(mappedBy="...")): read by joining the
     * other row on it, written by the other row only - as JPA keeps one
     * column for the pair. Null for any other field.
     */
    public static FieldSpec inverseOf(EntitySpec e, FieldSpec f) {
        if (!f.isEntityRef() || f.isList() || isEmbedded(e, f)) return null;
        String mappedBy = mappedBy(f);
        if (mappedBy == null) return null;
        EntitySpec target = e.related(f.coreItemType());
        if (target == null || e.pkField() == null) return null;
        for (FieldSpec b : fields(target)) {
            if (b.name().equals(mappedBy) && isToOne(target, b) && e.getName().equals(b.coreItemType())) return b;
        }
        return null;
    }

    /** A to-one relation the row holds the key of, in an entity that may embed values. */
    public static boolean isToOne(EntitySpec e, FieldSpec f) {
        return isToOne(f) && !isEmbedded(e, f);
    }

    /**
     * The field a to-one relation is read through - @joinBy(customerId), the
     * reference by identity: the row holds the id as a value of its own,
     * which is what is written, and the relation is only joined on it. Null
     * for any other field, and for a @joinBy naming no value of the row.
     */
    public static FieldSpec joinedBy(EntitySpec e, FieldSpec f) {
        if (!f.isEntityRef() || f.isList() || isEmbedded(e, f)) return null;
        String by = f.dslValueAny("joinby", "joinfield");
        if (by == null || by.trim().isEmpty()) return null;
        for (FieldSpec o : fields(e)) {
            if (o.name().equals(by.trim()) && isValue(e, o)) return o;
        }
        return null;
    }

    /**
     * A to-one relation read with the row: one whose key column it holds, one
     * read through an id field, or the inverse of a one-to-one the other row holds.
     */
    public static boolean isJoined(EntitySpec e, FieldSpec f) {
        return isToOne(e, f) || joinedBy(e, f) != null || inverseOf(e, f) != null;
    }

    /** A to-one relation whose key the row itself holds: its own column, or an id field's. */
    public static boolean holdsKey(EntitySpec e, FieldSpec f) {
        return isToOne(e, f) || joinedBy(e, f) != null;
    }

    /**
     * A to-one relation to a member of this root's aggregate whose key the
     * row holds - written with the root, as JPA's cascade writes it: inserted
     * before the root takes its key, updated in place, deleted when the root
     * lets it go. Only where the member has no service of its own to write it.
     */
    public static boolean isMemberReference(EntitySpec e, FieldSpec f) {
        if (!isToOne(e, f)) return false;
        String target = f.coreItemType();
        return target != null && !target.equals(e.getName()) && isMemberOf(e, target);
    }

    /** The column of the row that holds a relation's key: the id field's for @joinBy, the relation's own otherwise. */
    public static String keyColumn(EntitySpec e, FieldSpec f, EntitySpec target) {
        FieldSpec by = joinedBy(e, f);
        return by != null ? column(by) : foreignKey(f, target);
    }

    // ---- embedded values ------------------------------------------------------

    /** An @embeddable value kept in the columns of the row that holds it. */
    public static boolean isEmbedded(EntitySpec e, FieldSpec f) {
        return !f.isList() && (f.hasDsl("embedded") || e.isEmbeddableType(f.coreItemType()));
    }

    /** One column of an embedded value: the value's field and the column it lies in. */
    public static final class Part {
        public final FieldSpec field;
        public final String column;

        Part(FieldSpec field, String column) {
            this.field = field;
            this.column = column;
        }
    }

    /**
     * The columns of an embedded value, in the order its type declares them:
     * each field's own column, as JPA maps an @Embedded, or prefix_field where
     * the relation says @embedded(prefix) - the @AttributeOverrides the JPA
     * code writes for it. Empty when the type is not known to the run.
     */
    public static List<Part> parts(EntitySpec e, FieldSpec f) {
        List<Part> out = new ArrayList<Part>();
        EntitySpec type = e.related(f.coreItemType());
        if (type == null) return out;
        String prefix = f.dslValueAny("embedded.prefix", "embedded");
        for (FieldSpec p : type.fields()) {
            if (p.hasDsl("formula") || p.hasDsl("transient")) continue;
            String column = prefix != null && !prefix.isEmpty()
                    ? springName(prefix + "_" + io.elasticore.base.util.StringUtils.camelToSnake(p.name()))
                    : column(p);
            out.add(new Part(p, column));
        }
        return out;
    }

    /** What an embedded value holds that its columns cannot: another embedded value, a relation, a collection. */
    static List<String> unsupportedParts(EntitySpec e, FieldSpec f) {
        List<String> out = new ArrayList<String>();
        EntitySpec type = e.related(f.coreItemType());
        if (type == null) {
            out.add(f.name() + ": the embedded type " + f.coreItemType() + " is not one the run knows");
            return out;
        }
        for (FieldSpec p : type.fields()) {
            if (p.hasDsl("formula") || p.hasDsl("transient")) continue;
            if (p.isList() || isEmbedded(e, p) || p.isEntityRef()) {
                out.add(f.name() + "." + p.name() + ": an embedded value holds only values");
            } else if (!isEnum(e, p) && "OTHER".equals(jdbcType(e, p))) {
                out.add(f.name() + "." + p.name() + ": type " + p.typeName());
            }
        }
        return out;
    }

    // ---- collections ----------------------------------------------------------

    /**
     * The field of the target that holds this collection's rows - the
     * collection's mappedBy, naming a to-one back to this entity - or null
     * when the collection is not one the MyBatis code reads: one without
     * mappedBy (a join table) or a many-to-many.
     */
    public static FieldSpec backField(EntitySpec e, FieldSpec f) {
        if (!f.isList() || !f.isEntityRef() || f.hasDsl("manytomany")) return null;
        String mappedBy = f.dslProperty("onetomany", "mappedBy");
        if (mappedBy == null || mappedBy.isEmpty()) return null;
        EntitySpec target = e.related(f.coreItemType());
        if (target == null || target.pkField() == null) return null;
        for (FieldSpec b : fields(target)) {
            if (b.name().equals(mappedBy) && holdsKey(target, b) && e.getName().equals(b.coreItemType())) return b;
        }
        return null;
    }

    /**
     * Whether a collection holds members of this entity's aggregate - rows
     * that are written, replaced and deleted with it, as JPA's cascade and
     * orphan removal do. A collection of another aggregate's roots is read
     * only: those rows are written through their own service.
     */
    public static boolean isMemberCollection(EntitySpec e, FieldSpec f) {
        return backField(e, f) != null && isMemberOf(e, f.coreItemType());
    }

    /**
     * Whether an entity is one of this root's members that it writes: in its
     * aggregate, held by it, and without a service of its own - one with a
     * service is written there, and writing it with the root as well would
     * write it twice.
     */
    public static boolean isMemberOf(EntitySpec root, String target) {
        return root.knowsAggregateOf(target) && root.sameAggregate(target)
                && root.getName().equals(root.aggregateOf(target)) && !hasOwnService(target);
    }

    /**
     * The members of the domain being written when the domain rules bind it
     * into aggregates (ddd-aggregate), or null when they do not.
     */
    private static volatile java.util.Set<String> boundMembers;

    /**
     * Says, per domain, which entities the domain rules make members - by
     * @aggregate(X), a cascade or a root's @OneToMany - or null when they bind
     * no aggregates: a member then has no service, controller or search of its
     * own whatever it declares (ddd-aggregate takes them away), and its root
     * writes it.
     */
    public static void aggregateBoundaries(java.util.Set<String> members) {
        boundMembers = members == null ? null : java.util.Collections.unmodifiableSet(new java.util.HashSet<String>(members));
    }

    /** Whether the domain being written is bound into aggregates by the domain rules. */
    public static boolean aggregatesBound() {
        return boundMembers != null;
    }

    /**
     * Whether an entity of the run has a service of its own (@expose or
     * @service) - none for a member where the domain rules bind aggregates.
     */
    public static boolean hasOwnService(String entityName) {
        try {
            io.elasticore.base.ECoreModelContext ctx = io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            io.elasticore.base.model.entity.Entity entity = ctx == null ? null
                    : ctx.findModelComponent(entityName, io.elasticore.base.model.entity.Entity.class);
            if (entity == null) return false;
            java.util.Set<String> members = boundMembers;
            if (members != null && members.contains(entityName)) return false;
            return entity.getMetaInfo().hasMetaAnnotation("expose")
                    || entity.getMetaInfo().hasMetaAnnotation("service");
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Whether an UPDATE has a column to set: a value, an embedded value, a
     * relation's key or the version that can change. An entity of a key and
     * collections alone has none, and the mapper writes no update for it.
     */
    public static boolean hasColumnsToUpdate(EntitySpec e) {
        FieldSpec pk = e.pkField();
        for (FieldSpec f : fields(e)) {
            if (f == pk || !updatable(f)) continue;
            if (isValue(e, f) || isEmbedded(e, f) || isToOne(e, f)) return true;
        }
        return false;
    }

    /** The collections of an entity the MyBatis code reads with it. */
    public static List<FieldSpec> collections(EntitySpec e) {
        List<FieldSpec> out = new ArrayList<FieldSpec>();
        for (FieldSpec f : fields(e)) {
            if (backField(e, f) != null) out.add(f);
        }
        return out;
    }

    /** The money and quantity types of the runtime library, with the handlers that store them. */
    public static final String MONEY_HANDLER = "io.elasticore.springboot3.mybatis.MoneyTypeHandler";
    public static final String QUANTITY_HANDLER = "io.elasticore.springboot3.mybatis.QuantityTypeHandler";

    /** The handler a value column is bound with, or null for one MyBatis binds by itself. */
    public static String typeHandler(EntitySpec e, FieldSpec f) {
        if (isEnum(e, f)) return ENUM_HANDLER;
        if (f.isMoney()) return MONEY_HANDLER;
        if (f.isQuantity()) return QUANTITY_HANDLER;
        return null;
    }

    /** Whether the key is generated by the database, which a create therefore does not send. */
    public static boolean generatedKey(FieldSpec pk) {
        return pk != null && (pk.hasDsl("sequence") || pk.hasDsl("identity") || pk.hasDsl("autoincrement")
                || pk.generationType() != null);
    }

    /**
     * What a key marked @genid is set to before a row is written: the
     * generator @genid names, or one for its type - a short id for a string
     * of a length, a UUID otherwise. JPA sets it in @PrePersist; MyBatis has
     * no such moment, so the service does it.
     */
    public static String genIdExpression(FieldSpec pk) {
        if (pk == null || !pk.hasDsl("genid")) return null;
        String generator = pk.dslValue("genid");
        if (generator == null || generator.isEmpty()) {
            if (!pk.isStringType()) return null;
            int size = pk.rawLength();
            return size > 1
                    ? String.format("io.elasticore.springboot3.util.IdUtils.newId(%d)", size)
                    : "java.util.UUID.randomUUID().toString()";
        }
        return generator.lastIndexOf("()") < 0 ? generator + "()" : generator;
    }

    /** The version field when the entity declares a numeric one; null otherwise. */
    public static FieldSpec version(EntitySpec e) {
        // The entity's own fields, the same instances the mapper walks.
        for (FieldSpec f : fields(e)) {
            if (!f.isVersion()) continue;
            String t = RuleText.simpleName(f.typeName()).toLowerCase(Locale.ROOT);
            if (t.equals("long") || t.equals("integer") || t.equals("int") || t.equals("short")) return f;
        }
        return null;
    }

    /** A field stamped with the time a row is created, as JPA's auditing does for @CreatedDate. */
    public static boolean createdDate(FieldSpec f) {
        return nativeNames(f, "CreatedDate");
    }

    /** A field stamped with the time a row is last written (@LastModifiedDate). */
    public static boolean lastModifiedDate(FieldSpec f) {
        return nativeNames(f, "LastModifiedDate");
    }

    /** A field stamped with who created the row (@CreatedBy), from the application's AuditorAware. */
    public static boolean createdBy(FieldSpec f) {
        return nativeNames(f, "CreatedBy");
    }

    /** A field stamped with who last wrote the row (@LastModifiedBy). */
    public static boolean lastModifiedBy(FieldSpec f) {
        return nativeNames(f, "LastModifiedBy");
    }

    /**
     * Whether writing the entity - or the members its service writes with it -
     * records who wrote: the service then asks the application's AuditorAware.
     */
    public static boolean recordsAuditor(EntitySpec e) {
        if (recordsAuditorItself(e)) return true;
        for (FieldSpec f : collections(e)) {
            EntitySpec target = e.related(f.coreItemType());
            if (target != null && isMemberCollection(e, f) && recordsAuditorItself(target)) return true;
        }
        for (FieldSpec f : fields(e)) {
            EntitySpec target = e.related(f.coreItemType());
            if (target != null && isMemberReference(e, f) && recordsAuditorItself(target)) return true;
        }
        return false;
    }

    private static boolean recordsAuditorItself(EntitySpec e) {
        for (FieldSpec f : fields(e)) {
            if (isValue(e, f) && (createdBy(f) || lastModifiedBy(f))) return true;
        }
        return false;
    }

    private static boolean nativeNames(FieldSpec f, String annotation) {
        for (String line : f.nativeAnnotationLines()) {
            if (line.contains(annotation)) return true;
        }
        java.util.Map<String, io.elasticore.base.model.core.Annotation> map = f.raw().getAnnotationMap();
        if (map != null) {
            for (String key : map.keySet()) {
                if (key.toLowerCase(Locale.ROOT).endsWith(annotation.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }

    /** Whether a field holds an enum of the model, which the enum handler binds. */
    public static boolean isEnum(EntitySpec e, FieldSpec f) {
        return !f.isList() && e.isKnownEnum(RuleText.simpleName(f.typeName()));
    }

    /** The handler every enum column is bound with. */
    public static final String ENUM_HANDLER = "io.elasticore.springboot3.mybatis.ConvertedEnumTypeHandler";

    /**
     * The JDBC type a value is bound as. Named on every parameter, because a
     * null bound without one is refused by Oracle's driver ("invalid column
     * type: 1111").
     */
    public static String jdbcType(EntitySpec e, FieldSpec f) {
        if (isEnum(e, f)) return "VARCHAR";
        if (f.isMoney()) return "DECIMAL";
        if (f.isQuantity()) return "INTEGER";
        String t = RuleText.simpleName(f.typeName());
        switch (t) {
            case "String": return f.hasDsl("lob") || f.hasDsl("text") || f.hasDsl("longtext") ? "CLOB" : "VARCHAR";
            case "Long": case "long": return "BIGINT";
            case "Integer": case "int": return "INTEGER";
            case "Short": case "short": return "SMALLINT";
            case "Byte": case "byte": return "TINYINT";
            case "Boolean": case "boolean": return "BOOLEAN";
            case "Double": case "double": return "DOUBLE";
            case "Float": case "float": return "REAL";
            case "BigDecimal": return "DECIMAL";
            // A whole number past BIGINT: JPA keeps it as NUMERIC(38,0).
            case "BigInteger": return "NUMERIC";
            case "LocalDate": return "DATE";
            case "LocalDateTime": case "Instant": case "Date": return "TIMESTAMP";
            case "OffsetDateTime": case "ZonedDateTime": return "TIMESTAMP_WITH_TIMEZONE";
            case "LocalTime": return "TIME";
            case "byte[]": return "BLOB";
            default: return "OTHER";
        }
    }

    /**
     * The JDBC type a parameter names, which a driver reads when the value
     * is null. A large text or binary value is named VARCHAR / VARBINARY
     * rather than CLOB / BLOB: PostgreSQL keeps them as TEXT / BYTEA and
     * refuses a null bound as a large object, which the other databases take
     * either way.
     */
    public static String paramJdbcType(EntitySpec e, FieldSpec f) {
        String jdbc = jdbcType(e, f);
        if ("CLOB".equals(jdbc)) return "VARCHAR";
        if ("BLOB".equals(jdbc)) return "VARBINARY";
        return jdbc;
    }

    /**
     * A large value - a CLOB or BLOB, or text longer than Oracle keeps
     * inline (which is a CLOB there): Oracle neither sorts nor compares one
     * (ORA-00932), so it is not in the sort list and is searched only by
     * LIKE.
     */
    public static boolean isLob(EntitySpec e, FieldSpec f) {
        String jdbc = jdbcType(e, f);
        return "CLOB".equals(jdbc) || "BLOB".equals(jdbc) || ("VARCHAR".equals(jdbc) && f.rawLength() > 4000);
    }

    /**
     * What the MyBatis code does not write yet, one sentence each - the run
     * refuses the entity rather than write code that loses data quietly.
     */
    public static List<String> unsupported(EntitySpec e) {
        List<String> out = new ArrayList<String>();
        if (e.pkCount() > 1) out.add("a key of " + e.pkCount() + " fields (@IdClass)");
        if (e.parentName() != null && !e.parentName().trim().isEmpty() && abstractParent(e) == null) {
            // An @abstract parent is a mapped superclass: its columns are the
            // row's. A concrete one is table inheritance, which is JPA's.
            out.add("extends " + e.parentName() + ", which is not @abstract (table inheritance)");
        }
        if (e.hasMeta("audited")) {
            out.add("@audited (the row's history is Hibernate Envers'; a MyBatis write keeps none - drop it, or record"
                    + " the history in the extension service)");
        }
        FieldSpec pk = e.pkField();
        for (FieldSpec f : fields(e)) {
            if (!f.isList() && f.isEntityRef() && !isEmbedded(e, f) && mappedBy(f) != null
                    && e.related(f.coreItemType()) != null && inverseOf(e, f) == null) {
                out.add(f.name() + ": @OneToOne(mappedBy=\"" + mappedBy(f) + "\") names no relation of "
                        + f.coreItemType() + " back to " + e.getName() + " that holds the key");
            }
            if (boundMembers == null && isMemberReference(e, f)) {
                out.add(f.name() + ": a member of " + e.getName() + "'s aggregate held by a to-one relation, which"
                        + " the layered service does not write - give " + f.coreItemType() + " a service of its own,"
                        + " or let the domain rules write the aggregate (architecture = 'ddd' or 'hexagonal')");
            }
            if (f.isList() && f.isEntityRef()) {
                if (!e.getName().equals(e.ownAggregate()) && !hasOwnService(e.getName())) {
                    // Its rows would arrive with the root's request and be
                    // stored by nothing: the root reads and writes one level.
                    out.add(f.name() + ": a collection on a member of " + e.ownAggregate()
                            + "'s aggregate (the root reads and writes its members, one level deep)");
                } else if (f.hasDsl("manytomany")) {
                    out.add(f.name() + ": a many-to-many relation (map its join table as an entity)");
                } else if (backField(e, f) == null) {
                    out.add(f.name() + ": a collection relation without mappedBy naming a "
                            + f.coreItemType() + " field that points back here (@OneToMany(mappedBy=\"...\"))");
                }
            } else if (f.isList()) {
                out.add(f.name() + ": a collection of values");
            } else if (isEmbedded(e, f)) {
                out.addAll(unsupportedParts(e, f));
            } else if (f.hasDsl("discriminator")) {
                out.add(f.name() + ": a discriminator (inheritance)");
            } else if (f.isEntityRef() && f.dslValueAny("joinby", "joinfield") != null && joinedBy(e, f) == null) {
                out.add(f.name() + ": @joinBy(" + f.dslValueAny("joinby", "joinfield")
                        + ") names no value field of " + e.getName() + " that holds the key");
            } else if (!f.isEntityRef() && !isEnum(e, f) && !f.hasDsl("formula") && "OTHER".equals(jdbcType(e, f))) {
                out.add(f.name() + ": type " + f.typeName());
            } else if ((createdDate(f) || lastModifiedDate(f)) && nowType(f) == null) {
                out.add(f.name() + ": a time stamp of type " + f.typeName());
            } else if (f != pk && (f.hasDsl("sequence") || f.generationType() != null)) {
                out.add(f.name() + ": a generated value that is not the key (@sequence)");
            } else if (f == pk && !identityKey(pk)) {
                out.add(f.name() + ": a key generated otherwise than by an identity column (@sequence("
                        + generation(pk) + "))");
            }
        }
        return out;
    }

    /** How a generated key is generated, as @sequence or a native @GeneratedValue names it; "" for the default. */
    static String generation(FieldSpec pk) {
        String declared = pk.dslValue("sequence");
        if (declared == null || declared.isEmpty()) declared = pk.generationType();
        return declared == null ? "" : declared.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Whether the key is the database's identity column, or is not generated
     * at all - what the MyBatis insert reads back. A sequence, a table or a
     * UUID generator is Hibernate's, and a row written without it would get
     * no key.
     */
    static boolean identityKey(FieldSpec pk) {
        if (!generatedKey(pk)) return true;
        String g = generation(pk);
        return g.isEmpty() || g.equals("IDENTITY");
    }

    /** Whether a column keeps its value once written: @updatable(false), as JPA's @Column(updatable = false). */
    public static boolean updatable(FieldSpec f) {
        if ("false".equals(f.dslValue("updatable"))) return false;
        for (String line : f.nativeAnnotationLines()) {
            if (line.replace(" ", "").contains("updatable=false")) return false;
        }
        return true;
    }

    /** The "now" of a time stamp field's type, or null for a type there is none for. */
    public static String nowType(FieldSpec f) {
        String t = RuleText.simpleName(f.typeName());
        switch (t) {
            case "LocalDateTime": return "java.time.LocalDateTime.now()";
            case "LocalDate": return "java.time.LocalDate.now()";
            case "Instant": return "java.time.Instant.now()";
            case "OffsetDateTime": return "java.time.OffsetDateTime.now()";
            case "ZonedDateTime": return "java.time.ZonedDateTime.now()";
            case "Date": return "new java.util.Date()";
            default: return null;
        }
    }
}
