package io.elasticore.base.model.dto;

import io.elasticore.base.ECoreModelContext;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.model.DataModelComponent;
import io.elasticore.base.model.MetaInfo;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.EntityAnnotation;
import io.elasticore.base.model.entity.Field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a DTO's @template says, and the one place that reads its value.
 *
 * <pre>
 *   dto @template(Order[note, deliveryDate], Customer, shop:Article[aid])
 * </pre>
 *
 * A model copies all its fields, or with brackets the fields they list; a
 * field added to the model later is then not in the DTO until someone lists
 * it. Its brackets are only the DTO's business, so what asks which models a
 * DTO is made from asks {@link #namesOf(MetaInfo)}. A value without brackets
 * comes back exactly as it was written, so that nothing about a DTO that does
 * not use them changes; one with brackets comes back as its plain names,
 * "Order,Customer,shop:Article".
 */
public final class TemplateList {

    /** One model of the value. */
    public static final class Entry {
        /** As written, with its domain when it says one: shop:Order. */
        public final String model;
        /** The names in its brackets, as written; null without brackets: all its fields. */
        public final List<String> fields;

        Entry(String model, List<String> fields) {
            this.model = model;
            this.fields = fields;
        }

        String written() {
            return fields == null ? model : model + "[" + String.join(", ", fields) + "]";
        }
    }

    private final String value;
    private final List<Entry> entries = new ArrayList<Entry>();
    private boolean hasLists;
    private boolean readable = true;

    private TemplateList(String value) {
        this.value = value;
        if (value == null || (value.indexOf('[') < 0 && value.indexOf(']') < 0)) return;
        hasLists = true;
        read(value);
    }

    public static TemplateList of(MetaInfo meta) {
        return new TemplateList(meta.getMetaAnnotationValue(DataTransferAnnotation.META_TEMPLATE));
    }

    public static TemplateList of(String value) {
        return new TemplateList(value);
    }

    /** The models a DTO is made from: the @template value, without its brackets. Null without @template. */
    public static String namesOf(MetaInfo meta) {
        return of(meta).names();
    }

    /** The value itself without brackets; the plain names, joined, with them. */
    public String names() {
        if (!hasLists) return value;
        List<String> names = new ArrayList<String>();
        for (Entry e : entries) names.add(e.model);
        return String.join(",", names);
    }

    public boolean hasLists() {
        return hasLists;
    }

    /** The fields a model takes, for a name of {@link #names()}; null when it takes them all. */
    public List<String> fieldsOf(String model) {
        for (Entry e : entries) {
            if (e.model.equals(model.trim())) return e.fields;
        }
        return null;
    }

    /** One character at a time: a comma in brackets separates fields, and one outside separates models. */
    private void read(String text) {
        StringBuilder name = new StringBuilder();
        StringBuilder field = null;
        List<String> fields = null;
        boolean closed = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (field != null) {
                if (c == ']') {
                    fields.add(field.toString().trim());
                    field = null;
                    closed = true;
                } else if (c == ',') {
                    fields.add(field.toString().trim());
                    field = new StringBuilder();
                } else {
                    if (c == '[') readable = false;
                    field.append(c);
                }
            } else if (c == '[') {
                if (closed) readable = false;
                fields = new ArrayList<String>();
                field = new StringBuilder();
            } else if (c == ',') {
                add(name, fields);
                name = new StringBuilder();
                fields = null;
                closed = false;
            } else if (c == ']' || (closed && !Character.isWhitespace(c))) {
                readable = false;
            } else if (!closed) {
                name.append(c);
            }
        }
        if (field != null) {
            readable = false;
            fields.add(field.toString().trim());
        }
        add(name, fields);
    }

    private void add(StringBuilder name, List<String> fields) {
        String model = name.toString().trim();
        if (model.isEmpty()) {
            if (fields != null) readable = false;
            return;
        }
        entries.add(new Entry(model, fields));
    }

    /**
     * What is wrong with a DTO's lists, one sentence each; empty for a DTO
     * without any, which stays as free as it always was. Asked once every
     * domain is loaded, so a template of another domain is found.
     */
    public static List<String> problemsOf(DataTransfer dto, List<ECoreModelContext> contexts) {
        List<String> problems = new ArrayList<String>();
        String name = dto.getIdentity().getName();
        // Brackets here would make a name that finds no model, without a word.
        for (String[] search : new String[][] {
            DataTransferAnnotation.META_SEARCHABLE_NAME, DataTransferAnnotation.META_SEARCH_RESULT_NAME
        }) {
            String value = dto.getMetaInfo().getMetaAnnotationValue(search);
            if (value != null && value.indexOf('[') >= 0) {
                problems.add(name + ": @" + search[1] + "(" + value.trim()
                        + ") takes no list: a search DTO takes the fields with a search condition");
            }
        }
        TemplateList list = of(dto.getMetaInfo());
        if (!list.hasLists) return problems;
        String at = name + ": @template(" + list.value.trim() + ")";
        if (!list.readable) {
            problems.add(at + " cannot be read: a model is Name or Name[field, field]");
            return problems;
        }
        if (dto.getMetaInfo().hasMetaAnnotation(DataTransferAnnotation.META_EXTEND)) {
            problems.add(at + " cannot go with @extend, whose fields the DTO always has");
        }
        String home = dto.getIdentity().getDomainId();
        Map<Object, Entry> seen = new HashMap<Object, Entry>();
        Set<String> listed = new LinkedHashSet<String>();
        for (Entry e : list.entries) {
            // The copy takes the first domain with the name, and a check that looks elsewhere would pass a
            // field the copy does not have. Refused here, so that a name finds one model for both.
            List<String> domains = domainsHaving(contexts, e.model);
            if (domains.size() > 1) {
                problems.add(at + ": " + String.join(" and ", domains) + " both have " + e.model + ", so say which: "
                        + domains.get(0) + ":" + e.model);
                continue;
            }
            DataModelComponent model = ForcedRefCheck.modelNamed(contexts, e.model, home);
            Entry before = seen.put(model != null ? model : e.model, e);
            if (before != null) {
                problems.add(at + " names " + e.model + " twice: list what it takes once");
            }
            if (e.fields == null) continue;
            listed.addAll(e.fields);
            addProblems(problems, at, e, model);
        }
        ModelComponentItems<Field> own = dto.getItems();
        for (int i = 0; i < own.size(); i++) {
            Field f = own.get(i);
            if (f.hasAnnotation("disable") && listed.contains(f.getName())) {
                problems.add(name + "." + f.getName() + " is removed with -- and listed by @template("
                        + list.value.trim() + "): say one of them");
            }
        }
        return problems;
    }

    private static void addProblems(List<String> problems, String at, Entry e, DataModelComponent model) {
        if (e.fields.size() == 1 && e.fields.get(0).isEmpty()) {
            problems.add(at + " lists no field of " + e.model);
            return;
        }
        if (model == null) {
            problems.add(at + ": no domain has a model " + e.model);
            return;
        }
        Set<String> known = new HashSet<String>();
        // A DDD reference keeps its relation beside its id field, so the relation's name is here too.
        for (Object each : model.getAllFieldListMap().getList()) known.add(((Field) each).getName());
        Set<String> once = new HashSet<String>();
        for (String field : e.fields) {
            if (field.isEmpty()) problems.add(at + " has an empty name in the list of " + e.model);
            else if (!known.contains(field)) problems.add(at + ": " + e.model + " has no field " + field);
            else if (!once.add(field)) problems.add(at + " lists " + field + " twice");
        }
    }

    /**
     * Whether a DTO lists its fields and leaves the key out. An entity's service and
     * controller are written with the first DTO made from it, and this one could not
     * name a row, so it is passed over.
     */
    public static boolean leavesOutTheKey(DataTransfer dto) {
        if (!of(dto.getMetaInfo()).hasLists) return false;
        for (Object each : dto.getAllFieldListMap().getList()) {
            if (((Field) each).isPrimaryKey()) return false;
        }
        return true;
    }

    /** The domains with a model of this name, when the name does not say its domain. */
    private static List<String> domainsHaving(List<ECoreModelContext> contexts, String name) {
        List<String> domains = new ArrayList<String>();
        if (name.indexOf(':') >= 0) return domains;
        for (ECoreModelContext ctx : contexts) {
            String[] domainNames = ctx.getInternalDomainNames();
            if (domainNames == null) continue;
            for (String domainName : domainNames) {
                ModelDomain d = ctx.getDomain(domainName);
                if (d != null && d.getModel() != null && d.getModel().findModelByName(name) != null) {
                    domains.add(domainName);
                }
            }
        }
        return domains;
    }

    /**
     * The listed names that would give the DTO no field, one sentence each: a
     * DTO copies neither a relation nor, in most outputs, a value object, only a
     * field that holds a relation's id. That field exists once the references
     * are rewritten, so this is asked after that, where {@link #problemsOf} is
     * asked before. A search DTO keeps its relations, and is left alone.
     *
     * @param valueObjects whether a DTO of this domain carries a value object whole
     */
    public static List<String> emptyNamesOf(DataTransfer dto, List<ECoreModelContext> contexts, boolean valueObjects) {
        List<String> problems = new ArrayList<String>();
        TemplateList list = of(dto.getMetaInfo());
        if (!list.hasLists || !list.readable) return problems;
        if (dto.getMetaInfo().hasMetaAnnotation(DataTransferAnnotation.META_SEARCHABLE)) return problems;
        String at = dto.getIdentity().getName() + ": @template(" + list.value.trim() + "): ";
        for (Entry e : list.entries) {
            if (e.fields == null) continue;
            DataModelComponent model = ForcedRefCheck.modelNamed(
                    contexts, e.model, dto.getIdentity().getDomainId());
            if (model == null) continue;
            for (String name : e.fields) {
                String reason = whyEmpty(name, model, contexts, valueObjects);
                if (reason != null) problems.add(at + reason);
            }
        }
        return problems;
    }

    /** Why a listed name gives no field, or null when it gives one. */
    private static String whyEmpty(
            String name, DataModelComponent model, List<ECoreModelContext> contexts, boolean valueObjects) {
        Field field = null;
        for (Object each : model.getAllFieldListMap().getList()) {
            Field f = (Field) each;
            if (name.equals(f.getAnnotationValue("idof"))) return null;
            if (name.equals(f.getName())) field = f;
        }
        if (field == null || field.getTypeInfo().isBaseType() || field.hasAnnotation(EntityAnnotation.DTO_TYPE)) {
            return null;
        }
        boolean many = field.getTypeInfo().isList();
        String type = many
                ? field.getTypeInfo().getCoreItemType()
                : field.getTypeInfo().getDefaultTypeName();
        DataModelComponent target =
                ForcedRefCheck.modelNamed(contexts, type, model.getIdentity().getDomainId());
        if (!(target instanceof Entity)) return null;
        if (target.getMetaInfo().hasMetaAnnotation(EntityAnnotation.META_EMBEDDABLE)) {
            if (valueObjects && !many) return null;
            return name + " is a value object, which this DTO does not carry: declare what it needs of it";
        }
        String key = many ? null : ForcedRefCheck.singleKeyOf((Entity) target);
        return name + " is a relation, which a DTO does not copy: "
                + (key == null ? "take it out of the list" : "declare its id with @ref(" + name + "." + key + ")");
    }
}
