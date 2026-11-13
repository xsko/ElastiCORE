package io.elasticore.base.model.pub.ossie;

import io.elasticore.base.ECoreModelContext;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.entity.BaseFieldType;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.EntityModels;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.enums.EnumConstant;
import io.elasticore.base.model.enums.EnumModel;
import io.elasticore.base.util.ConsoleLog;
import io.elasticore.base.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The model, written down as an Apache Ossie semantic model.
 *
 * Ossie (incubating) is the ASF's vendor-neutral format for exchanging
 * semantic metadata - datasets, fields, relationships, metrics - across
 * analytics, BI and AI platforms. An ElastiCORE blueprint already states what
 * the dataset side of that spec asks for: entities with tables, typed fields,
 * keys and declared relations. This walker says it in Ossie's words, so a
 * domain generated here is consumable by the tools that read them, without
 * anyone re-declaring the model in a second file that then drifts.
 *
 * Written to the core spec 0.3.0.dev0 (core-spec/spec.md): one model per
 * document, flat at the root; every field an expression; each value type one
 * of the spec's DataTypes. One document for the whole project, so the
 * relationships that cross domain files stay inside one model. Expressions
 * are the top-level sql, the portable form in OSSIE_SQL_2026, the one dialect
 * the spec gives a meaning; a @formula the Ossie language cannot say keeps its
 * SQL as an ANSI_SQL dialect entry, which a consumer may ignore (OssieSql).
 *
 * Only what the blueprint actually knows is emitted:
 * - columns as the generated entities map them - an enum by its column, an
 *   embedded value by each of its columns, a reference by its join column;
 * - no password and no write-only column: the DSL says those are never read
 *   back, and a semantic layer is a reader;
 * - an enumeration's codes and labels, as the field's ai_context, since the
 *   table holds the code and only the DSL says what PLC means;
 * - what Ossie has no word for (a lifecycle, who may read a row, a value the
 *   model derives) as an ELASTICORE nonportable extension;
 * - the business's own words (@terms) as synonyms, and its measures
 *   (metrics:) as Ossie metrics, one over one entity in that entity's
 *   dataset - only those the DSL declares. A metric invented from entity
 *   fields would be exporting a guess.
 *
 * The export is opt-in - a project asks for it by naming the ossie-export
 * rule set - and a failure degrades the export, never the build.
 */
public final class OssieSemanticModelExporter {

    public static final String FILE_NAME = "semantic_model.yml";

    /** The rule-set name a project opts in with. */
    public static final String RULE_SET = "ossie-export";

    /** The core spec version this writes. */
    public static final String SPEC_VERSION = "0.3.0.dev0";

    /** The vendor name of what Ossie has no word for. */
    public static final String VENDOR = "ELASTICORE";

    /**
     * The system property a project names its model with - the plugin's
     * {@code elasticore { ossieName = '...' }} sets it. Unset, the model is
     * named after its domains, as it always was.
     */
    public static final String NAME_PROPERTY = "elasticore.ossie.name";

    private final StringBuilder out = new StringBuilder();
    private final List<String> relationships = new ArrayList<String>();
    private final Map<Entity, String> datasetNames = new IdentityHashMap<Entity, String>();
    /** The datasets whose rows a filter limits: a metric over one says so. */
    private final Set<String> filtered = new HashSet<String>();

    private final List<String> domains = new ArrayList<String>();
    /** Every loaded domain, also one with no table of its own: its metrics: are still read. */
    private final List<Unit> domainUnits = new ArrayList<Unit>();
    private ECoreModelContext ctx;

    private OssieSemanticModelExporter() {
    }

    /**
     * Whether any loaded domain, or the project, asked for the export.
     *
     * The rule set itself generates nothing - it is the opt-in signal this
     * reads, from the same keys every other rule selection uses.
     */
    public static boolean requested(List<ECoreModel> models) {
        String property = System.getProperty("elasticore.publisher.rules");
        if (property != null && property.contains(RULE_SET)) return true;
        // The build's rules list (elasticore { rules = ['ossie-export'] }).
        if (io.elasticore.base.model.pub.rules.RulesConfig.addedRules().contains(RULE_SET)) return true;

        if (models == null) return false;
        for (ECoreModel model : models) {
            String declared = model.getConfig("publisher.rules");
            if (declared != null && declared.contains(RULE_SET)) return true;
        }
        return false;
    }

    /** Writes {@code semantic_model.yml} into the blueprint root. */
    public static void export(List<ECoreModelContext> contexts, String blueprintRootPath) {
        try {
            OssieSemanticModelExporter exporter = new OssieSemanticModelExporter();
            exporter.build(contexts);
            exporter.write(new File(blueprintRootPath, FILE_NAME));
            ConsoleLog.printInfo("[ossie] semantic model exported: "
                    + blueprintRootPath + "/" + FILE_NAME);
        } catch (Exception e) {
            ConsoleLog.printWarn("[ossie] semantic model export failed: " + e);
        }
    }

    /** The whole document, as text: for a test that reads it without a file. */
    static String render(List<ECoreModelContext> contexts) {
        OssieSemanticModelExporter exporter = new OssieSemanticModelExporter();
        exporter.build(contexts);
        return exporter.out.toString();
    }

    // ---- the document --------------------------------------------------------

    private static final class Unit {
        final ECoreModelContext ctx;
        final String domain;
        final ECoreModel model;
        final Entity entity;

        Unit(ECoreModelContext ctx, String domain, ECoreModel model, Entity entity) {
            this.ctx = ctx;
            this.domain = domain;
            this.model = model;
            this.entity = entity;
        }
    }

    private void build(List<ECoreModelContext> contexts) {
        // Every table of every domain first: a dataset's name is unique in the
        // model, so a name two domains share is qualified with its domain.
        List<Unit> units = new ArrayList<Unit>();
        Map<String, Integer> seen = new LinkedHashMap<String, Integer>();
        for (ECoreModelContext c : contexts) {
            String[] names = c.getInternalDomainNames();
            if (names == null) continue;
            for (String domainName : names) {
                ModelDomain domain = c.getDomain(domainName);
                if (domain == null || domain.getModel() == null) continue;
                domainUnits.add(new Unit(c, domainName, domain.getModel(), null));
                EntityModels entities = domain.getModel().getEntityModels();
                if (entities == null) continue;
                if (!domains.contains(domainName)) domains.add(domainName);
                ModelComponentItems<Entity> items = entities.getItems();
                for (int i = 0; i < items.size(); i++) {
                    Entity entity = items.get(i);
                    if (entity == null || !isTable(entity)) continue;
                    units.add(new Unit(c, domainName, domain.getModel(), entity));
                    String name = entity.getIdentity().getName();
                    seen.put(name, seen.containsKey(name) ? seen.get(name) + 1 : 1);
                }
            }
        }
        for (Unit u : units) {
            String name = u.entity.getIdentity().getName();
            datasetNames.put(u.entity, seen.get(name) > 1 ? u.domain + "_" + name : name);
        }

        out.append("# Apache Ossie (incubating) semantic model - core spec ").append(SPEC_VERSION).append('\n');
        out.append("# Generated by ElastiCORE from the blueprint DSL - do not edit;\n");
        out.append("# regenerate with the ossie-export rule set.\n");
        out.append("version: ").append(SPEC_VERSION).append('\n');
        out.append("name: ").append(modelName()).append('\n');
        out.append("description: ").append(yaml("Domain model of " + join(domains, ", ")
                + ", as its ElastiCORE DSL declares it")).append('\n');
        out.append("ai_context:\n");
        out.append("  instructions: ")
                .append(yaml("Each dataset is a table the generated application owns. "
                        + "An enumeration column holds a code; the field's ai_context lists every code and its label. "
                        + "Amounts (money) are decimals with their scale; a quantity is a count that is never negative. "
                        + "Password and write-only columns are left out on purpose. "
                        + "Lifecycles, access rules and derived values are in the ELASTICORE nonportable extensions."))
                .append('\n');
        out.append("datasets:\n");
        List<Written> written = new ArrayList<Written>();
        for (Unit u : units) {
            ctx = u.ctx;
            written.add(dataset(u));
        }
        // The metrics are read once every dataset is: one of a dataset goes in it, beside its fields.
        Map<String, List<String>> ofDataset = new LinkedHashMap<String, List<String>>();
        List<String> ofModel = new ArrayList<String>();
        metrics(written, ofDataset, ofModel);
        for (Written w : written) {
            out.append(w.head);
            List<String> own = ofDataset.get(w.name);
            if (own != null) {
                out.append("    metrics:\n");
                for (String m : own) out.append(m);
            }
            out.append(w.tail);
        }
        if (!relationships.isEmpty()) {
            out.append("relationships:\n");
            for (String r : relationships) out.append(r);
        }
        if (!ofModel.isEmpty()) {
            out.append("metrics:\n");
            for (String m : ofModel) out.append(m);
        }
        Map<String, Object> ext = new LinkedHashMap<String, Object>();
        ext.put("generator", "ElastiCORE");
        ext.put("domains", domains);
        customExtension("", ext);
    }

    /**
     * The model's name: the one the project gave it, or else its domains
     * joined. Joined, a project of nine domains is named by a sentence; the
     * name is how a BI or AI tool lists the model, so a project may say it.
     * Either way it is written as an identifier, and a given name that had to
     * change to be one is said.
     */
    private String modelName() {
        String given = System.getProperty(NAME_PROPERTY);
        if (given == null || given.trim().isEmpty()) return identifier(join(domains, "_"));
        String name = identifier(given.trim());
        if (!name.equals(given.trim())) {
            ConsoleLog.printWarn("[ossie] model name '" + given.trim() + "' is written as " + name
                    + " - letters, digits and _ only.");
        }
        return name;
    }

    /** An entity with a table of its own: not an abstract parent, not a value embedded in another's. */
    private static boolean isTable(Entity entity) {
        return !entity.getMetaInfo().hasMetaAnnotation("abstract")
                && !entity.getMetaInfo().hasMetaAnnotation("embeddable");
    }

    // ---- a dataset ---------------------------------------------------------------

    /** A dataset as written, up to where its metrics go and from there, and the names of its fields. */
    private static final class Written {
        final String name;
        final String head;
        final List<String> fields;
        final String tail;

        Written(String name, String head, List<String> fields, String tail) {
            this.name = name;
            this.head = head;
            this.fields = fields;
            this.tail = tail;
        }
    }

    private Written dataset(Unit u) {
        Entity entity = u.entity;
        String name = datasetNames.get(entity);
        StringBuilder b = new StringBuilder();

        b.append("  - name: ").append(name).append('\n');
        b.append("    source: ").append(plain(table(entity, u.model))).append('\n');

        List<Field> all = inItsTable(
                entity, new ArrayList<Field>(entity.getAllFieldListMap().values()));
        List<String> pk = new ArrayList<String>();
        for (Field f : all) {
            if (isKey(f) && f.getTypeInfo().isBaseType()) pk.add(f.getDbColumnName());
        }
        if (!pk.isEmpty())
            b.append("    primary_key: [").append(join(plain(pk), ", ")).append("]\n");

        List<List<String>> unique = uniqueKeys(entity, all);
        if (!unique.isEmpty()) {
            b.append("    unique_keys:\n");
            for (List<String> key : unique)
                b.append("      - [").append(join(plain(key), ", ")).append("]\n");
        }

        String label = entity.getMetaInfo().getMetaAnnotationValue("label");
        String description = label != null && !label.trim().isEmpty() ? label.trim() : null;
        String filter = rowFilter(entity);
        warnOfParentFilter(entity);
        if (filter != null) {
            filtered.add(name);
            description = (description == null ? "" : description + " ") + "(rows only where: " + filter + ")";
        }
        if (description != null)
            b.append("    description: ").append(yaml(description)).append('\n');
        List<String> terms = termsOf(entity.getMetaInfo().getMetaAnnotationValue("terms"));
        if (!terms.isEmpty())
            b.append("    ai_context:\n      synonyms: ").append(list(terms)).append('\n');

        // The fields, keyed by column: a column is in a table once.
        Map<String, String> fields = new LinkedHashMap<String, String>();
        List<String> omitted = new ArrayList<String>();
        integerColumns = integerColumnsOf(all);
        for (Field f : all) {
            if (f.getTypeInfo().isList() || f.hasAnnotation("transient")) continue;
            if (sensitive(f)) {
                omitted.add(columnOf(f));
                continue;
            }
            EnumModel en = enumOf(f);
            Entity target = en == null ? entityOf(f) : null;
            if (f.getTypeInfo().isBaseType() || en != null) {
                field(fields, f, columnOf(f), null, en);
            } else if (target != null && target.getMetaInfo().hasMetaAnnotation("embeddable")) {
                embedded(fields, f, target, omitted);
            } else if (target != null && isTable(target)) {
                reference(fields, entity, f, target, all);
            }
        }
        b.append("    fields:\n");
        for (String text : fields.values()) b.append(text);

        Map<String, Object> ext = new LinkedHashMap<String, Object>();
        ext.put("entity", entity.getIdentity().getName());
        ext.put("domain", u.domain);
        if (filter != null) ext.put("row_filter", filter);
        String owner = entity.getMetaInfo().getMetaAnnotationValue("owner");
        if (owner != null) ext.put("owner", owner);
        Map<String, String> access = keyValues(entity.getMetaInfo().getMetaAnnotation("access"));
        if (!access.isEmpty()) {
            access.put("*", access.containsKey("*") ? access.get("*") : "admin");
            ext.put("access", access);
        }
        if (entity.getStates() != null && !entity.getStates().isEmpty()) ext.put("states", entity.getStates());
        String events = entity.getMetaInfo().getMetaAnnotationValue("event");
        if (events != null) ext.put("events", events);
        if (!omitted.isEmpty()) ext.put("omitted_columns", omitted);
        return new Written(name, b.toString(), new ArrayList<String>(fields.keySet()), extension("    ", ext));
    }

    /** Hibernate's Where or SQLRestriction as the class says it, fully qualified or not, and its arguments. */
    private static final Pattern ROW_FILTER = Pattern.compile(
            "@(?:org\\.hibernate\\.annotations\\.)?(Where|SQLRestriction)\\s*\\((.*)\\)", Pattern.DOTALL);

    /** One string literal, named clause or value or not named: the SQL is in it. */
    private static final Pattern LITERAL =
            Pattern.compile("\\s*(?:(?:clause|value)\\s*=\\s*)?\"((?:[^\"\\\\]|\\\\.)*)\"\\s*", Pattern.DOTALL);

    /**
     * The rows of the table the application reads, as the entity's class says:
     * the Hibernate SQLRestriction or Where the DSL hands to it, as in
     * {@code @jpa:Where(clause = "delete_yn = 'N'")}. Read as Hibernate reads
     * it: on the entity's own class (a parent's is warnOfParentFilter's), and
     * SQLRestriction over Where. Null when there is none, or when the clause
     * is not one string literal; that is said.
     */
    private static String rowFilter(Entity entity) {
        Map<String, String> clauses = new TreeMap<String, String>();
        for (Matcher a : rowFilterAnnotations(entity)) {
            Matcher literal = LITERAL.matcher(a.group(2));
            if (!literal.matches()) {
                ConsoleLog.printWarn("[ossie] " + entity.getIdentity().getName() + ": @" + a.group(1) + "(" + a.group(2)
                        + ") is not one string literal - its row filter is left out of the dataset.");
                continue;
            }
            // The SQL the literal holds: a quote or a backslash in it is escaped in Java.
            clauses.put(a.group(1), literal.group(1).replaceAll("\\\\([\"\\\\])", "$1"));
        }
        return clauses.containsKey("SQLRestriction") ? clauses.get("SQLRestriction") : clauses.get("Where");
    }

    /** The Where and SQLRestriction annotations of the entity's class, readable or not. */
    private static List<Matcher> rowFilterAnnotations(Entity entity) {
        List<Matcher> found = new ArrayList<Matcher>();
        Map<String, Annotation> meta = entity.getMetaInfo().getMetaAnnotationMap();
        if (meta == null) return found;
        for (Map.Entry<String, Annotation> e : meta.entrySet()) {
            // The two prefixes the generators write onto the class as given.
            if (!e.getKey().contains("jpa:") && !e.getKey().contains("entity:")) continue;
            Matcher a = ROW_FILTER.matcher(String.valueOf(e.getValue()).trim());
            if (a.matches()) found.add(a);
        }
        return found;
    }

    /**
     * Hibernate reads a parent's filter for its children only when the parent
     * is an entity, and the code generated from this model decides that: 2.x
     * writes an @abstract with a key or a @discriminator as one, and anything
     * else as a MappedSuperclass, whose filter filters nothing. The export does
     * not guess; it says so.
     */
    private void warnOfParentFilter(Entity entity) {
        for (Entity parent = parentOf(entity); parent != null; parent = parentOf(parent)) {
            if (rowFilterAnnotations(parent).isEmpty()) continue;
            String self = entity.getIdentity().getName();
            String of = parent.getIdentity().getName();
            ConsoleLog.printWarn("[ossie] " + self + ": its parent " + of + " has a row filter on its class,"
                    + " which Hibernate applies to " + self + " only if " + of + " is written as an entity,"
                    + " not as a MappedSuperclass - it is not exported for " + self + ".");
            return;
        }
    }

    /**
     * The fields whose columns are in the entity's own table. Under JOINED a subclass's table
     * holds the key and what it declares, with any plain @abstract frame between it and the
     * entity it extends; the other columns are in the tables above it. Elsewhere, all of them.
     */
    private List<Field> inItsTable(Entity entity, List<Field> all) {
        java.util.Set<String> own = new java.util.HashSet<String>();
        Entity above = null;
        boolean first = true;
        for (Entity at = entity; at != null; at = parentOf(at)) {
            if (!first && !isFrame(at)) {
                above = at;
                break;
            }
            first = false;
            io.elasticore.base.model.ModelComponentItems<Field> items = at.getItems();
            for (int i = 0; items != null && i < items.size(); i++)
                own.add(items.get(i).getName());
        }
        if (above == null || !joinedAbove(above)) return all;
        List<Field> out = new ArrayList<Field>();
        for (Field f : all) {
            if (own.contains(f.getName()) || isKey(f)) out.add(f);
        }
        return out;
    }

    /** A plain @abstract: a frame with no table, whose columns are in the table of what extends it. */
    private static boolean isFrame(Entity entity) {
        String kind = entity.getMetaInfo().getMetaAnnotationValue("abstract");
        return entity.getMetaInfo().hasMetaAnnotation("abstract")
                && !(kind != null && "entity".equalsIgnoreCase(kind.trim()));
    }

    /** Whether the hierarchy the entity is in maps JOINED: its topmost entity says so (@jpa:Inheritance). */
    private boolean joinedAbove(Entity entity) {
        Entity root = entity;
        for (Entity at = parentOf(entity); at != null; at = parentOf(at)) {
            if (!isFrame(at)) root = at;
        }
        Map<String, io.elasticore.base.model.core.Annotation> meta =
                root.getMetaInfo().getMetaAnnotationMap();
        if (meta == null) return false;
        for (Map.Entry<String, io.elasticore.base.model.core.Annotation> en : meta.entrySet()) {
            if (en.getKey().contains("jpa:") && String.valueOf(en.getValue()).contains("JOINED")) return true;
        }
        return false;
    }

    /** The entity it extends (@extend), or null. */
    private Entity parentOf(Entity entity) {
        String parent = entity.getMetaInfo().getMetaAnnotationValue("extend");
        return parent == null ? null : ctx.findModelComponent(parent, Entity.class);
    }

    private String table(Entity entity, ECoreModel model) {
        // The prefix comes before the @table name too - the same answer the
        // generated @Table annotation gives (2.x and 3.0).
        String declared = entity.getMetaInfo().getMetaAnnotationValue("table");
        String prefix = model.getConfig("table.prefix");
        String table = (prefix != null && !prefix.isEmpty() ? prefix : "")
                + (declared != null ? declared : entity.getTableName());
        // Entity @schema wins over the env.yml tableSchema config.
        String schema = entity.getMetaInfo().getMetaAnnotationValue("schema");
        if (schema == null || schema.isEmpty()) {
            schema = model.getConfig("tableSchema");
        }
        if (schema == null || schema.isEmpty()) return table;
        // A dotted source cannot hold a part that has a dot itself; such a
        // name keeps the table alone, as before.
        if (schema.contains(".") || table.contains(".")) return table;
        return schema + "." + table;
    }

    private static boolean isKey(Field f) {
        return f.isPrimaryKey() || f.hasAnnotation("id") || f.hasAnnotation("pk");
    }

    /** @password and @writeonly are never read back by the application; a semantic layer is a reader. */
    private static boolean sensitive(Field f) {
        return f.hasAnnotation("password") || f.hasAnnotation("writeonly");
    }

    /** @unique on a field, and @Index(..., unique="true") on the entity. */
    private List<List<String>> uniqueKeys(Entity entity, List<Field> all) {
        List<List<String>> keys = new ArrayList<List<String>>();
        for (Field f : all) {
            if (!f.hasAnnotation("unique") || "false".equals(f.getAnnotationValue("unique"))) continue;
            if (!f.getTypeInfo().isBaseType()) continue;
            List<String> key = new ArrayList<String>();
            key.add(columnOf(f));
            if (!keys.contains(key)) keys.add(key);
        }
        Annotation index = entity.getMetaInfo().getMetaAnnotation("Index");
        if (index != null) {
            for (Annotation each : index.getSiblings()) {
                Properties p = each.getProperties();
                if (p == null || !"true".equals(unquote(p.getProperty("unique")))) continue;
                String columns = unquote(p.getProperty("columnList"));
                if (columns == null || columns.trim().isEmpty()) continue;
                List<String> key = new ArrayList<String>();
                for (String c : columns.split(",")) {
                    // A column list may say a direction after the name.
                    String col = c.trim().split("\\s+")[0];
                    if (!col.isEmpty()) key.add(keyColumn(col, all));
                }
                if (!key.isEmpty() && !keys.contains(key)) keys.add(key);
            }
        }
        return keys;
    }

    /**
     * A name of an @Index column list as the dataset's column, so that the key
     * names fields the dataset has: a property (seatNo) is its field's column; a
     * column (hall_id, or HALL_ID that @column wrote) is the column of the field
     * stored in it, in the case that field has; any other name is read as
     * Hibernate's naming reads it (seqCd is seq_cd).
     */
    private static String keyColumn(String name, List<Field> all) {
        for (Field f : all) {
            if (f.getName().equals(name)) return columnOf(f);
        }
        for (Field f : all) {
            String column = columnOf(f);
            if (column != null && column.equalsIgnoreCase(name)) return column;
        }
        return StringUtils.camelToSnake(name);
    }

    // ---- fields --------------------------------------------------------------------

    /** The column a value field is stored in, as the generated entity maps it. */
    private static String columnOf(Field f) {
        String declared = f.getAnnotationValue("column", "col", "db");
        if (declared != null && !declared.isEmpty()) return declared;
        String db = f.getDbColumnName();
        return db != null ? db : StringUtils.camelToSnake(f.getName());
    }

    private void field(Map<String, String> fields, Field f, String column, String path, EnumModel en) {
        if (fields.containsKey(column)) {
            // Two fields on one column: JPA would refuse the mapping unless one is read-only. Said, not hidden.
            ConsoleLog.printWarn("[ossie] " + (path != null ? path : f.getName()) + " maps to column " + column
                    + ", which is already exported; it is left out.");
            return;
        }
        StringBuilder b = new StringBuilder();
        b.append("      - name: ").append(plain(column)).append('\n');

        // A @formula column is computed by the database: its SQL is the
        // expression. A column name, or a formula the Ossie language can say,
        // is the portable sql; a formula it cannot say - one with a subquery -
        // keeps its SQL under the opaque ANSI_SQL name, which a consumer may
        // ignore, rather than a claim it would read and get wrong.
        String formula = f.getAnnotationValue("formula");
        String expression = formula != null && !formula.isEmpty() ? formula : column;
        // OSSIE_SQL_2026 makes / true division (5 / 2 is 2.5, apache/ossie#687); the
        // database divides integer columns as integers (2). A formula that divides
        // with an integer column means another thing in the portable language, so it
        // keeps its SQL under the opaque name - what the database computes (issue 157).
        boolean integerDivision = formula != null && dividesIntegers(formula, integerColumns);
        b.append(integerDivision ? opaque("        ", expression) : expression("        ", expression));

        EnumStorage stored = en == null ? null : storage(f, en);
        String type = stored != null ? stored.datatype : ossieType(f);
        if (type != null) b.append("        datatype: ").append(type).append('\n');

        String label = f.getAnnotationValue("label", "desc");
        String description = path != null ? path + (label != null && !label.trim().isEmpty() ? " - " + label.trim() : "") : label;
        if (description != null && !description.trim().isEmpty()) {
            b.append("        description: ").append(yaml(description)).append('\n');
        }
        String codes = stored == null ? null : stored.codes();
        List<String> terms = termsOf(f.getAnnotationValue("terms"));
        if (!terms.isEmpty()) {
            b.append("        ai_context:\n");
            if (codes != null) b.append("          instructions: ").append(yaml(codes)).append('\n');
            b.append("          synonyms: ").append(list(terms)).append('\n');
        } else if (codes != null) {
            b.append("        ai_context: ").append(yaml(codes)).append('\n');
        }

        Map<String, Object> ext = new LinkedHashMap<String, Object>();
        if (formula != null && !formula.isEmpty()) ext.put("formula", "database");
        String compute = f.getAnnotationValue("compute");
        if (compute != null) ext.put("compute", compute);
        String snapshot = f.getAnnotationValue("snapshot");
        if (snapshot != null) ext.put("snapshot", snapshot);
        if (f.hasAnnotation("statefield")) ext.put("lifecycle", true);
        String stamp = f.getAnnotationValue("stamp");
        if (stamp != null) ext.put("stamp", stamp);
        if (f.hasAnnotation("login")) ext.put("login", true);
        if (en != null) ext.put("enumeration", en.getIdentity().getName());
        // What each stored value means, as data: ai_context says it as a sentence a
        // program would have to parse (issue 174).
        if (stored != null && !stored.meaning.isEmpty()) ext.put("codes", stored.meaning);
        if (f.getTypeInfo().isMoney()) ext.put("value_type", "money");
        if (f.getTypeInfo().isQuantity()) ext.put("value_type", "quantity");
        if (!ext.isEmpty()) b.append(extension("        ", ext));
        fields.put(column, b.toString());
    }

    /** Each column of an embedded value, named as @embedded(prefix) or the embeddable maps it. */
    private void embedded(Map<String, String> fields, Field owner, Entity value, List<String> omitted) {
        String prefix = owner.getAnnotationValue("embedded.prefix");
        if (prefix == null) {
            String v = owner.getAnnotationValue("embedded");
            if (v != null && !v.isEmpty()) prefix = v;
        }
        for (Field f : new ArrayList<Field>(value.getAllFieldListMap().values())) {
            if (f.getTypeInfo().isList()) continue;
            EnumModel en = enumOf(f);
            if (!f.getTypeInfo().isBaseType() && en == null) continue;
            String column = prefix != null ? prefix + "_" + StringUtils.camelToSnake(f.getName()) : columnOf(f);
            if (sensitive(f)) {
                omitted.add(column);
                continue;
            }
            field(fields, f, column, owner.getName() + "." + f.getName(), en);
        }
    }

    /**
     * A to-one reference: an Ossie relationship on its join column, and that
     * column as a field - the spec's own example keeps customer_id a field of
     * orders, so the join can be grouped and filtered on.
     */
    private void reference(Map<String, String> fields, Entity from, Field f, Entity target, List<Field> all) {
        if (f.hasAnnotation("onetomany") || f.hasAnnotation("manytomany")) return;
        Annotation oneToOne = f.getAnnotation("onetoone");
        if (oneToOne != null && oneToOne.getProperties() != null
                && oneToOne.getProperties().getProperty("mappedBy") != null) return;

        List<Field> keys = new ArrayList<Field>();
        for (Field k : new ArrayList<Field>(target.getAllFieldListMap().values())) {
            if (isKey(k) && k.getTypeInfo().isBaseType()) keys.add(k);
        }
        if (keys.isEmpty()) return;

        List<String> fromColumns = new ArrayList<String>();
        List<String> toColumns = new ArrayList<String>();
        for (Field k : keys) toColumns.add(k.getDbColumnName());

        String declared = f.getAnnotationValue("column", "col", "db");
        String joinBy = f.getAnnotationValue("joinby", "joinfield");
        Field idField = keys.size() == 1 ? idFieldOf(f, keys.get(0), all) : null;
        if (declared != null && !declared.isEmpty() && keys.size() == 1) {
            fromColumns.add(declared);
        } else if (joinBy != null && !joinBy.isEmpty()) {
            for (Field other : all) if (other.getName().equals(joinBy)) fromColumns.add(columnOf(other));
        } else if (idField != null) {
            // The reference held by its identity (customerId): the join column is that field's.
            fromColumns.add(columnOf(idField));
        } else {
            // JPA's own default: the field's name and the key's column.
            for (Field k : keys) fromColumns.add(StringUtils.camelToSnake(f.getName()) + "_" + k.getDbColumnName());
        }
        if (fromColumns.size() != toColumns.size()) return;

        // The join column as a field, where no field already maps it (@joinBy reuses another's).
        if (idField == null) {
            for (int i = 0; i < keys.size(); i++) {
                if (fields.containsKey(fromColumns.get(i))) continue;
                field(fields, keys.get(i), fromColumns.get(i), f.getName() + " -> " + target.getIdentity().getName(), null);
            }
        }

        String to = datasetNames.get(target);
        if (to == null) return;
        StringBuilder r = new StringBuilder();
        r.append("  - name: ").append(datasetNames.get(from)).append('_').append(f.getName()).append('\n');
        r.append("    from: ").append(datasetNames.get(from)).append('\n');
        r.append("    to: ").append(to).append('\n');
        r.append("    from_columns: [").append(join(plain(fromColumns), ", ")).append("]\n");
        r.append("    to_columns: [").append(join(plain(toColumns), ", ")).append("]\n");
        relationships.add(r.toString());
    }

    /** The field that holds the reference by its identity - item's itemId - if the entity has one. */
    private static Field idFieldOf(Field ref, Field targetKey, List<Field> all) {
        String key = targetKey.getName();
        String idName = key.startsWith(ref.getName())
                ? key
                : ref.getName() + Character.toUpperCase(key.charAt(0)) + key.substring(1);
        for (Field f : all) {
            if (f.getName().equals(idName) && f.getTypeInfo().isBaseType()) return f;
        }
        return null;
    }

    private EnumModel enumOf(Field f) {
        if (f.getTypeInfo().isBaseType() || f.getTypeInfo().isList()) return null;
        return ctx.findModelComponent(f.getTypeInfo().getBaseTypeName(), EnumModel.class);
    }

    private Entity entityOf(Field f) {
        return ctx.findModelComponent(f.getTypeInfo().getBaseTypeName(), Entity.class);
    }

    // ---- metrics ----------------------------------------------------------------------

    /**
     * The metrics: of every domain, as Ossie metrics, each in its scope (core
     * spec, "Metric Scoping"). One whose aggregates all read one entity - also
     * arithmetic over that entity's metrics - is its dataset's: the measure
     * the relational interface (Layer 2) exposes on the dataset, written in
     * its metrics on its fields by their own names, and named
     * PurchaseOrder.revenue. Its fields and its metrics share one set of
     * names, so a metric named like one is refused and said, never renamed;
     * no domain is put before it. Only arithmetic over metrics of more than
     * one entity spans datasets and stays at the model root, where a name two
     * domains both use is qualified with its domain, as a dataset name is.
     */
    private void metrics(List<Written> datasets, Map<String, List<String>> ofDataset, List<String> ofModel) {
        Map<String, Written> byName = new LinkedHashMap<String, Written>();
        for (Written w : datasets) byName.put(w.name, w);
        Map<String, Unit> byDomain = new LinkedHashMap<String, Unit>();
        for (Unit u : domainUnits) if (!byDomain.containsKey(u.domain)) byDomain.put(u.domain, u);
        // A dataset's metrics so far, by their name in lower case: the name and its domain.
        Map<String, Map<String, String[]>> named = new LinkedHashMap<String, Map<String, String[]>>();
        List<Unit> rootDomains = new ArrayList<Unit>();
        List<OssieMetrics.Metric> root = new ArrayList<OssieMetrics.Metric>();
        Map<String, Integer> seen = new LinkedHashMap<String, Integer>();
        for (final Unit u : byDomain.values()) {
            Map<String, String> declared = u.model.getMetrics();
            if (declared == null || declared.isEmpty()) continue;
            ctx = u.ctx;
            OssieMetrics reader = new OssieMetrics(modelOf(u));
            for (Map.Entry<String, String> d : declared.entrySet()) {
                OssieMetrics.Metric m = reader.read(d.getKey(), d.getValue());
                if (m == null) continue;
                if (m.datasets.size() != 1) {
                    rootDomains.add(u);
                    root.add(m);
                    seen.put(m.name, seen.containsKey(m.name) ? seen.get(m.name) + 1 : 1);
                    continue;
                }
                String ds = m.datasets.get(0);
                if (!named.containsKey(ds)) named.put(ds, new LinkedHashMap<String, String[]>());
                String clash = clash(byName.get(ds), named.get(ds), m.name);
                if (clash != null) {
                    ConsoleLog.printWarn(
                            "[ossie] [" + u.domain + "] metric " + m.name + ": " + clash + "; it is left out.");
                    continue;
                }
                named.get(ds).put(m.name.toLowerCase(java.util.Locale.ROOT), new String[] {m.name, u.domain});
                if (!ofDataset.containsKey(ds)) ofDataset.put(ds, new ArrayList<String>());
                ofDataset.get(ds).add(metric("      ", m.name, u, m, m.local));
            }
            for (String p : reader.problems()) ConsoleLog.printWarn("[ossie] [" + u.domain + "] " + p);
        }
        for (int i = 0; i < root.size(); i++) {
            OssieMetrics.Metric m = root.get(i);
            Unit u = rootDomains.get(i);
            ofModel.add(metric("  ", seen.get(m.name) > 1 ? u.domain + "_" + m.name : m.name, u, m, m.sql));
        }
    }

    /**
     * Why the dataset cannot take a metric of that name, or null. Its fields and
     * metrics share one set of names, which a query reads in any case.
     */
    private static String clash(Written dataset, Map<String, String[]> metrics, String name) {
        for (String field : dataset.fields) {
            if (field.equalsIgnoreCase(name)) {
                return dataset.name + " has a field " + field
                        + ", and a dataset's fields and metrics share one set of names - rename the metric";
            }
        }
        String[] other = metrics.get(name.toLowerCase(java.util.Locale.ROOT));
        if (other == null) return null;
        return dataset.name + " already has a metric " + other[0] + ", from domain " + other[1]
                + " - rename the metric";
    }

    /** One metric, its entry at the indent of the section it is in: the model root's, or a dataset's. */
    private String metric(String indent, String name, Unit u, OssieMetrics.Metric m, String sql) {
        String in = indent + "  ";
        StringBuilder b = new StringBuilder();
        b.append(indent).append("- name: ").append(plain(name)).append('\n');
        // Aggregates, CASE WHEN, CAST, NULLIF and comparisons: all of
        // it in the Ossie language, so declared as such.
        b.append(expression(in, sql));
        if (m.label != null && !m.label.isEmpty())
            b.append(in).append("description: ").append(yaml(m.label)).append('\n');
        if (m.datatype != null)
            b.append(in).append("datatype: ").append(m.datatype).append('\n');
        if (!m.terms.isEmpty())
            b.append(in)
                    .append("ai_context:\n")
                    .append(in)
                    .append("  synonyms: ")
                    .append(list(m.terms))
                    .append('\n');
        Map<String, Object> ext = new LinkedHashMap<String, Object>();
        ext.put("domain", u.domain);
        ext.put("declared", m.declared);
        // What the application counts and a reader of the table would not: the filter is on the dataset.
        for (String ds : m.datasets) if (filtered.contains(ds)) ext.put("dataset_row_filter", true);
        b.append(extension(in, ext));
        return b.toString();
    }

    /** What a metric line may name, answered from the exported model: the entities of the metric's domain first. */
    private OssieMetrics.Model modelOf(final Unit u) {
        return new OssieMetrics.Model() {
            public String dataset(String entity) {
                Entity e = entity(entity);
                return e == null ? null : datasetNames.get(e);
            }

            public String column(String entity, String field) {
                Field f = field(entity, field);
                if (f == null || f.getTypeInfo().isList()) return null;
                // Only what the dataset exports: no transient, password or write-only column.
                if (f.hasAnnotation("transient") || sensitive(f)) return null;
                if (!f.getTypeInfo().isBaseType() && enumOf(f) == null) return null;
                return columnOf(f);
            }

            public String datatype(String entity, String field) {
                Field f = field(entity, field);
                if (f == null) return null;
                EnumModel en = enumOf(f);
                return en != null ? storage(f, en).datatype : ossieType(f);
            }

            public String enumLiteral(String entity, String field, String constant) {
                Field f = field(entity, field);
                EnumModel en = f == null ? null : enumOf(f);
                return en == null ? null : storage(f, en).literal.get(constant);
            }

            public boolean isEnum(String entity, String field) {
                Field f = field(entity, field);
                return f != null && enumOf(f) != null;
            }

            public String keyColumn(String entity) {
                Entity e = entity(entity);
                if (e == null || e.getAllFieldListMap() == null) return null;
                for (Object each : e.getAllFieldListMap().values()) {
                    Field f = (Field) each;
                    if (f.isPrimaryKey() || f.hasAnnotation("pk")) return column(entity, f.getName());
                }
                return null;
            }

            private Entity entity(String name) {
                Entity other = null;
                for (Entity e : datasetNames.keySet()) {
                    if (!e.getIdentity().getName().equals(name)) continue;
                    if (u.model.getEntityModels() != null && u.model.getEntityModels().findByName(name) == e) return e;
                    other = e;
                }
                return other;
            }

            private Field field(String entity, String field) {
                Entity e = entity(entity);
                return e == null ? null : (Field) e.getAllFieldListMap().get(field);
            }
        };
    }

    /**
     * How an enum field is stored, as the generated entity maps it: by its
     * @db code (a converter), by its name (@jpa:Enumerated(EnumType.STRING)),
     * or - neither said - by its ordinal, JPA's default.
     */
    private static final class EnumStorage {
        String datatype;
        /** constant -> the SQL literal stored for it. */
        final Map<String, String> literal = new LinkedHashMap<String, String>();
        /** stored value -> what it means, for the reader. */
        final Map<String, String> meaning = new LinkedHashMap<String, String>();

        String codes() {
            if (meaning.isEmpty()) return null;
            List<String> parts = new ArrayList<String>();
            for (Map.Entry<String, String> m : meaning.entrySet()) {
                parts.add(m.getKey().equals(m.getValue()) ? m.getKey() : m.getKey() + " = " + m.getValue());
            }
            return "Codes: " + join(parts, ", ");
        }
    }

    private static EnumStorage storage(Field f, EnumModel en) {
        EnumStorage s = new EnumStorage();
        List<Field> enumFields = en.getFieldItems().getItemList();
        String db = en.getMetaInfo().getMetaAnnotationValue("db");
        int codeAt = -1;
        int labelAt = -1;
        for (int i = 0; i < enumFields.size(); i++) {
            String n = enumFields.get(i).getName();
            if (n.equals(db)) codeAt = i;
            else if (labelAt < 0 && (n.equalsIgnoreCase("label") || n.equalsIgnoreCase("name") || n.equalsIgnoreCase("desc"))) labelAt = i;
        }
        String enumerated = f.getAnnotationValue("jpa:Enumerated", "jpa:enumerated");
        boolean byName = codeAt < 0 && enumerated != null && enumerated.toUpperCase().contains("STRING");
        s.datatype = codeAt >= 0 ? ossieType(enumFields.get(codeAt)) : byName ? "String" : "Integer";
        boolean text = "String".equals(s.datatype);
        if (en.getEnumConstantItems() == null) return s;
        int ordinal = 0;
        for (EnumConstant c : en.getEnumConstantItems().getItemList()) {
            List<String> values = new ArrayList<String>();
            for (EnumConstant.ConstructParam p : c.getConstructParamList()) values.add(unquote(String.valueOf(p)).trim());
            String name = c.getIdentity().getName();
            String stored = codeAt >= 0 && codeAt < values.size() ? values.get(codeAt)
                    : byName ? name : String.valueOf(ordinal);
            String label = labelAt >= 0 && labelAt < values.size() ? values.get(labelAt) : name;
            s.literal.put(name, text ? "'" + stored.replace("'", "''") + "'" : stored);
            s.meaning.put(stored, codeAt < 0 && !byName ? name + (label.equals(name) ? "" : " (" + label + ")") : label);
            ordinal++;
        }
        return s;
    }

    /** @terms(order, purchase): the business's words for a thing, as Ossie synonyms. */
    private static List<String> termsOf(String value) {
        return value == null ? new ArrayList<String>() : OssieMetrics.terms("@terms(" + value + ")");
    }

    private static String list(List<String> items) {
        List<String> quoted = new ArrayList<String>();
        for (String s : items) quoted.add(yaml(s));
        return "[" + join(quoted, ", ") + "]";
    }

    // ---- types -----------------------------------------------------------------------

    /**
     * The DSL's value types in Ossie's portable vocabulary (core spec, "Data
     * types"). The table in the DSL guide says the same, type by type.
     */
    static String ossieType(Field field) {
        // A JSON column is text to the database but not a string to anyone
        // reading it; a large binary is nothing the vocabulary has a word for.
        String columnDef = field.getAnnotationValue("columndef");
        if (columnDef != null && columnDef.trim().toLowerCase().startsWith("json")) return "Opaque";

        BaseFieldType t = field.getTypeInfo().getBaseFieldType();
        if (t == null) return "Opaque";
        switch (t) {
            case STRING:
                return "String";
            case P_INT:
            case P_LONG:
            case INTEGER:
            case LONG:
            case BIGINTEGER:
            case QUANTITY:
                return "Integer";
            case BIGDECIMAL:
            case MONEY:
                return "Decimal";
            case P_FLOAT:
            case P_DOUBLE:
            case FLOAT:
            case DOUBLE:
                return "Float";
            case P_BOOLEAN:
            case BOOLEAN:
                return "Boolean";
            case DATE:
            case LocalDate:
                return "Date";
            case TIME:
            case LocalTime:
                return "Time";
            case DATETIME:
            case LocalDateTime:
                return "DateTime";
            case INSTANT:
                return "DateTimeTz";
            default:
                return "Opaque";
        }
    }

    // ---- text ------------------------------------------------------------------------

    private void customExtension(String indent, Map<String, Object> data) {
        out.append(extension(indent, data));
    }

    /**
     * An expression object (core spec, "Expression"): what the Ossie language
     * says is the top-level sql - the portable OSSIE_SQL_2026 form, which needs
     * no dialect named - and only what it cannot say is a dialect entry, under
     * the opaque ANSI_SQL. The text of the expression is written as given.
     */
    private static String expression(String indent, String sql) {
        if (!OssieSql.portable(sql)) return opaque(indent, sql);
        StringBuilder b = new StringBuilder();
        b.append(indent).append("expression:\n");
        b.append(indent).append("  sql: ").append(yaml(sql)).append('\n');
        return b.toString();
    }

    /** The SQL under the opaque dialect name: what the database runs, which a consumer may ignore. */
    private static String opaque(String indent, String sql) {
        StringBuilder b = new StringBuilder();
        b.append(indent).append("expression:\n");
        b.append(indent).append("  dialects:\n");
        b.append(indent).append("    - dialect: ").append(OssieSql.OPAQUE).append('\n');
        b.append(indent).append("      expression: ").append(yaml(sql)).append('\n');
        return b.toString();
    }

    /** The columns of the entity being exported that hold an integer (int, long). */
    private java.util.Set<String> integerColumns = java.util.Collections.emptySet();

    private static java.util.Set<String> integerColumnsOf(List<Field> fields) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        for (Field f : fields) {
            if (f.getTypeInfo() == null || f.getTypeInfo().isList()) continue;
            io.elasticore.base.model.entity.BaseFieldType t = f.getTypeInfo().getBaseFieldType();
            if (t == null) continue;
            switch (t) {
                case P_INT:
                case P_LONG:
                case INTEGER:
                case LONG:
                case BIGINTEGER:
                    out.add(columnOf(f).toLowerCase(java.util.Locale.ROOT));
                    break;
                default:
                    break;
            }
        }
        return out;
    }

    private static final java.util.regex.Pattern IDENTIFIER = java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** Whether the formula divides (a / outside its literals) and names an integer column. */
    static boolean dividesIntegers(String formula, java.util.Set<String> integerColumns) {
        String bare = formula.replaceAll("'(?:[^']|'')*'", " ");
        if (bare.indexOf('/') < 0) return false;
        java.util.regex.Matcher m = IDENTIFIER.matcher(bare);
        while (m.find()) {
            if (integerColumns.contains(m.group().toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }

    /**
     * nonportable_extensions with one ELASTICORE entry. data is an object, as
     * the spec (0.3.0.dev0, since apache/ossie#522) says: the JSON of it, a
     * YAML flow mapping whose every string is double-quoted - so a code such
     * as NO stays a string, not a boolean.
     */
    private static String extension(String indent, Map<String, Object> data) {
        StringBuilder b = new StringBuilder();
        b.append(indent).append("nonportable_extensions:\n");
        b.append(indent).append("  - vendor_name: ").append(VENDOR).append('\n');
        b.append(indent).append("    data: ").append(json(data)).append('\n');
        return b.toString();
    }

    private static Map<String, String> keyValues(Annotation a) {
        Map<String, String> out = new TreeMap<String, String>();
        if (a == null) return out;
        if (a.getProperties() != null) {
            for (String k : a.getProperties().stringPropertyNames()) out.put(k.trim(), unquote(a.getProperties().getProperty(k)).trim());
        } else if (a.getValue() != null) {
            for (String part : a.getValue().split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) out.put(kv[0].trim(), unquote(kv[1]).trim());
                else if (!kv[0].trim().isEmpty()) out.put("*", unquote(kv[0]).trim());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static String json(Object v) {
        if (v == null) return "null";
        if (v instanceof Boolean || v instanceof Number) return String.valueOf(v);
        if (v instanceof Map) {
            StringBuilder b = new StringBuilder("{");
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) v).entrySet()) {
                if (b.length() > 1) b.append(", ");
                b.append(json(String.valueOf(e.getKey()))).append(": ").append(json(e.getValue()));
            }
            return b.append('}').toString();
        }
        if (v instanceof Iterable) {
            StringBuilder b = new StringBuilder("[");
            for (Object o : (Iterable<Object>) v) {
                if (b.length() > 1) b.append(", ");
                b.append(json(o));
            }
            return b.append(']').toString();
        }
        String s = String.valueOf(v);
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    /** A YAML double-quoted scalar: any text, safely. */
    static String yaml(String text) {
        String t = text.trim();
        StringBuilder b = new StringBuilder("\"");
        for (char c : t.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else if (c == '\t') b.append("\\t");
            else if (c == '\r') b.append("\\r");
            else if (c < 0x20) b.append(String.format("\\x%02x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    /** An identifier as is, quoted only when YAML would read it otherwise (a quoted @table, a dash). */
    static String plain(String name) {
        return name != null && name.matches("[A-Za-z_][A-Za-z0-9_.$]*") ? name : yaml(String.valueOf(name));
    }

    private static List<String> plain(List<String> names) {
        List<String> out = new ArrayList<String>();
        for (String n : names) out.add(plain(n));
        return out;
    }

    private static String identifier(String text) {
        String s = text.replaceAll("[^A-Za-z0-9_]", "_");
        return s.isEmpty() ? "model" : s;
    }

    private static String unquote(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() >= 2 && (t.startsWith("\"") && t.endsWith("\"") || t.startsWith("'") && t.endsWith("'"))) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static String join(List<String> items, String sep) {
        StringBuilder b = new StringBuilder();
        for (String s : items) {
            if (b.length() > 0) b.append(sep);
            b.append(s);
        }
        return b.toString();
    }

    private void write(File file) throws IOException {
        // Never FileWriter: the platform charset is MS949 on the machine this
        // framework is developed on, and Korean labels come out corrupted.
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            writer.write(out.toString());
        } finally {
            writer.close();
        }
    }
}
