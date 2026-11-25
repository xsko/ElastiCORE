package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.core.ListMap;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.EntityAnnotation;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.pub.rules.base.AccessExpression;
import io.elasticore.base.model.pub.rules.base.EncryptRules;
import io.elasticore.base.model.pub.rules.base.MaskedFields;
import io.elasticore.base.model.pub.rules.base.ServiceAccessRules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a search of an entity may sort by: the paths of the values its response shows. A sort on any
 * other value would give that value's order away to whoever may search - the order of a password's
 * hash, a {@code @writeonly} card number, a masked e-mail or a joined row's e-mail the response
 * leaves out - and, with rows of one's own to sort among, the value itself.
 *
 * <p>The rule, path by path (an entity path, as a sort names it - {@code name}, {@code address.city},
 * {@code customer.customerId}):
 * <ul>
 *   <li>a value of the entity the search's response DTO shows under its own name;</li>
 *   <li>a part of an embedded value the DTO carries whole ({@code address.city}) that it shows;</li>
 *   <li>a DTO field's {@code @ref} path - a reference's key ({@code customer.customerId}) or a joined
 *       value ({@code product.name}) the DTO copies into the response;</li>
 *   <li>the same, again, of a joined row whose DTO the response carries ({@code customer.name} where
 *       the response nests CustomerDTO - only for an entity everyone who may read the holder may
 *       read, as the DTO nests no other), up to three relations deep;</li>
 *   <li>the entity's key, which an export puts after the order, and the default order ({@code @sort}),
 *       which every search without a sort already answers in.</li>
 * </ul>
 * A value is not shown when it is write-only ({@code @writeonly}, {@code @nooutput}, {@code @password}),
 * left out of the JSON ({@code @jsonignore}), written masked (the runtime's {@code @Mask}), or kept out
 * of the read view ({@code @view} without r). A list is never sorted by. {@code @sensitive} and
 * {@code @encrypt} values are shown, so they sort (an encrypted column sorts by its ciphertext).
 */
final class SortablePaths {

    /** Relations followed through nested DTOs: deep enough for any response, short of a cycle. */
    private static final int DEPTH = 3;

    private static final java.util.regex.Pattern COMMAS = java.util.regex.Pattern.compile(",");

    private static final java.util.regex.Pattern DOTS = java.util.regex.Pattern.compile("\\.");

    private SortablePaths() {}

    /**
     * The paths a search of the entity may sort by, the key first; null when the model has no DTO the
     * search answers with (then nothing is checked, as before).
     *
     * @param defaultSort the search DTO's default order as a sort code ("name+,id-"), or null
     */
    // An empty term names nothing and is skipped, trailing ones included: split as it is.
    @SuppressWarnings("StringSplitter")
    static List<String> of(Entity entity, ECoreModel model, String defaultSort) {
        DataTransfer result = resultDto(entity, model);
        if (result == null) {
            // Nothing to tell what the response shows by: said, rather than left unchecked without a word.
            io.elasticore.base.model.pub.rules.SourceCode.dslWarnOnce(String.format(
                    "[%s] the search of %s answers with no DTO of this model (%s), so its sort is not kept to what the"
                            + " response shows - any property sorts. Declare the DTO here to have it checked.",
                    entity.getIdentity().getDomainId(), entity.getIdentity().getName(), resultName(entity, model)));
            return null;
        }
        Set<String> out = new LinkedHashSet<String>();
        ListMap<String, Field> own = entity.getAllFieldListMap();
        if (own != null) {
            for (Field f : own.getList()) {
                if (f.hasAnnotation("id", "pk")) out.add(f.getName());
            }
        }
        shown(result, entity, model, "", 0, out);
        if (defaultSort != null) {
            for (String term : COMMAS.split(defaultSort)) {
                String t = term.trim();
                if (t.endsWith("+") || t.endsWith("-"))
                    t = t.substring(0, t.length() - 1).trim();
                if (t.isEmpty()) continue;
                // Every search without a sort answers in this order already, so a sort may name it;
                // on a value no response shows, that order is said - with a warning.
                if (!out.contains(t) && own != null && own.get(t) != null && hidden(own.get(t), "on")) {
                    io.elasticore.base.model.pub.rules.SourceCode.dslWarnOnce(String.format(
                            "[%s] @sort(%s) of %s orders by %s, which no response shows: every search without a"
                                    + " sort answers in its order, and a sort may name it. Order by a shown value.",
                            entity.getIdentity().getDomainId(),
                            defaultSort,
                            entity.getIdentity().getName(),
                            t));
                }
                out.add(t);
            }
        }
        return new ArrayList<String>(out);
    }

    /** The DTO a search answers with: its @searchResult DTO, else the entity's own. */
    private static DataTransfer resultDto(Entity entity, ECoreModel model) {
        if (model.getDataTransferModels() == null) return null;
        return model.getDataTransferModels().getItems().findByName(resultName(entity, model));
    }

    private static String resultName(Entity entity, ECoreModel model) {
        String name = model.getDataTransferModels() == null ? null : SpecFactory.searchResultDtoOf(entity, model);
        return name != null ? name : entity.getIdentity().getName() + "DTO";
    }

    /** The paths the DTO shows of the entity's values, under the prefix of the relations followed to it. */
    private static void shown(
            DataTransfer dto, Entity entity, ECoreModel model, String prefix, int depth, Set<String> out) {
        ListMap<String, Field> fields = dto.getAllFieldListMap();
        if (fields == null) return;
        String mode = viewMode(dto, model);
        ListMap<String, Field> entityFields = entity == null ? null : entity.getAllFieldListMap();
        for (Field d : fields.getList()) {
            if (d.hasAnnotation(EncryptRules.INDEX_OF) || d.getTypeInfo().isList() || hidden(d, mode)) continue;
            if (d.hasAnnotation(EntityAnnotation.META_DEFERRED) && deferred(d, entity, model, prefix, depth, out))
                continue;
            String ref = d.getAnnotationValue("ref");
            if (ref != null && !ref.trim().isEmpty()) {
                // A @ref that reads a @password is left out of the DTO (access-dto-ref-password).
                if (!readsAPassword(entity, ref.trim(), model)) out.add(prefix + ref.trim());
                continue;
            }
            Field stored = entityFields == null ? null : entityFields.get(d.getName());
            if (stored == null || stored.getTypeInfo().isList() || hidden(stored, mode)) continue;
            Entity target = entityNamed(stored.getTypeInfo().getDefaultTypeName(), model);
            if (target == null) {
                out.add(prefix + d.getName());
            } else if (target.getMetaInfo().hasMetaAnnotation("embeddable")) {
                // Carried whole only where the run puts value objects in DTOs; left out otherwise.
                if (!SpecFactory.valueObjectsInDto() || !sameType(d, stored)) continue;
                ListMap<String, Field> parts = target.getAllFieldListMap();
                if (parts == null) continue;
                for (Field p : parts.getList()) {
                    if (!p.getTypeInfo().isList() && !hidden(p, mode))
                        out.add(prefix + d.getName() + "." + p.getName());
                }
            } else if (depth < DEPTH && carried(entity, target, model)) {
                // A relation is shown as far as the DTO it is carried as shows it - and that DTO is
                // carried only when everyone who may read the holder may read the row too
                // (access-dto-nested); else its key alone.
                DataTransfer nested = dtoNamed(dtoTypeOf(d), model);
                if (nested != null) shown(nested, target, model, prefix + d.getName() + ".", depth + 1, out);
            }
        }
    }

    /**
     * A relation the DSL deferred, read as SpecFactory.resolveDeferredFields writes it into the DTO - without
     * writing it, so the model is left as the run finds it: the related row's key flattened beside it
     * ({@code customer.customerId}), and the row's own DTO nested (CustomerDTO) where the model has one and
     * everyone who may read the holder may read the row. False for a field that is no such relation (an embedded value is no relation).
     */
    private static boolean deferred(
            Field d, Entity holder, ECoreModel model, String prefix, int depth, Set<String> out) {
        Entity target = entityNamed(d.getTypeInfo().getDefaultTypeName(), model);
        if (target == null || target.getMetaInfo().hasMetaAnnotation("embeddable")) return false;
        for (String key : keysOf(target, model, 0)) out.add(prefix + d.getName() + "." + key);
        if (depth < DEPTH && carried(holder, target, model)) {
            DataTransfer nested = dtoNamed(target.getIdentity().getName() + "DTO", model);
            if (nested != null) shown(nested, target, model, prefix + d.getName() + ".", depth + 1, out);
        }
        return true;
    }

    /** The key fields of an entity, its @extend parents' first, as flattenIdentity finds them. */
    private static List<String> keysOf(Entity entity, ECoreModel model, int depth) {
        List<String> out = new ArrayList<String>();
        String parent = entity.getMetaInfo().getMetaAnnotationValue(EntityAnnotation.META_EXTEND);
        if (parent != null && depth < DEPTH) {
            Entity p = entityNamed(parent, model);
            if (p != null) out.addAll(keysOf(p, model, depth + 1));
        }
        ModelComponentItems<Field> fields = entity.getItems();
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).hasAnnotation(EntityAnnotation.META_ID))
                out.add(fields.get(i).getName());
        }
        return out;
    }

    /**
     * Whether a DTO of the holder carries a whole row of the target, as the mapper fills it
     * (ServiceAccessRules.nestedIsVisible): the target says no @access, or everyone who may read the
     * holder may read the target too - or, under config: nestedRows: public, anyone may read it.
     */
    private static boolean carried(Entity holder, Entity target, ECoreModel model) {
        String referenced = readLevelOf(target);
        if (referenced == null) return true;
        if (!ServiceAccessRules.readersCarry(model.getConfig(ServiceAccessRules.NESTED_ROWS))) {
            AccessExpression read = AccessExpression.parse(referenced);
            return read != null && read.isPublic();
        }
        return ServiceAccessRules.readImplies(holder == null ? null : readLevelOf(holder), referenced);
    }

    /** The level the entity is read at, or null when it says no @access. */
    private static String readLevelOf(Entity entity) {
        Map<String, Annotation> meta = entity.getMetaInfo().getMetaAnnotationMap();
        Annotation access = meta == null ? null : meta.get("access");
        if (access == null) return null;
        String text = access.getValue() != null ? access.getValue() : SpecFactory.keyValues(access);
        return ServiceAccessRules.readLevel(text);
    }

    /** Whether a @ref path, followed through the entity's relations, ends at a @password. */
    // An empty segment finds no field, and the path reads no password: split as it is.
    @SuppressWarnings("StringSplitter")
    private static boolean readsAPassword(Entity entity, String ref, ECoreModel model) {
        String[] path = DOTS.split(ref);
        Entity at = entity;
        for (int i = 0; i < path.length && at != null; i++) {
            ListMap<String, Field> fields = at.getAllFieldListMap();
            Field f = fields == null ? null : fields.get(path[i].trim());
            if (f == null) return false;
            if (i == path.length - 1) return f.hasAnnotation("password");
            at = entityNamed(
                    f.getTypeInfo().isList()
                            ? f.getTypeInfo().getCoreItemType()
                            : f.getTypeInfo().getDefaultTypeName(),
                    model);
        }
        return false;
    }

    /**
     * Whether a response leaves the value out or writes it masked: write-only, ignored by JSON, the
     * runtime's mask, or a @view without the read view.
     */
    static boolean hidden(Field f, String viewMode) {
        if (f.hasAnnotation("writeonly", "nooutput", "password", "jsonignore", "ignore")) return true;
        Map<String, Annotation> map = f.getAnnotationMap();
        if (map != null) {
            for (Map.Entry<String, Annotation> en : map.entrySet()) {
                if (!en.getKey().contains("dto:") || en.getKey().contains("jpa:")) continue;
                String line = String.valueOf(en.getValue());
                // The runtime's mask, or Jackson told natively to leave the value out of the JSON.
                if (MaskedFields.namesMask(line) || line.contains("JsonIgnore") || line.contains("WRITE_ONLY"))
                    return true;
            }
        }
        String view = f.getAnnotationValue("view");
        return view != null && !view.trim().isEmpty() && !"false".equals(viewMode) && !readView(view);
    }

    /** Whether a @view value names the read view: @view(cr), @view(create,read). */
    // An empty name is no view: split as it is.
    @SuppressWarnings("StringSplitter")
    private static boolean readView(String view) {
        String v = view.trim().toLowerCase(Locale.ROOT);
        if (v.indexOf(',') >= 0) {
            for (String name : COMMAS.split(v)) {
                String n = name.trim();
                if ("read".equals(n) || "r".equals(n)) return true;
            }
            return false;
        }
        return v.indexOf('r') >= 0;
    }

    /** The DTO's JsonView mode, as EntitySpec.jsonViewMode reads it: its own @jsonview, else env.yml's. */
    private static String viewMode(DataTransfer dto, ECoreModel model) {
        if (dto.getMetaInfo().hasMetaAnnotation("jsonview")) {
            String v = dto.getMetaInfo().getMetaAnnotationValue("jsonview");
            return v != null && "false".equalsIgnoreCase(v.trim()) ? "false" : "on";
        }
        String config = model.getConfig("jsonview", "false");
        return config == null || "false".equalsIgnoreCase(config.trim()) ? "false" : "on";
    }

    /** The DTO type a field is declared with: its @dtype, else its type. */
    private static String dtoTypeOf(Field f) {
        for (String alias : new String[] {"dtotype", "dtype", "dto"}) {
            String declared = f.getAnnotationValue(alias);
            if (declared != null && !declared.trim().isEmpty()) return declared.trim();
        }
        return f.getTypeInfo().getDefaultTypeName();
    }

    /** Whether the DTO field is the stored value itself, not a DTO of it. */
    private static boolean sameType(Field d, Field stored) {
        String a = d.getTypeInfo().getDefaultTypeName();
        return a != null && a.equals(stored.getTypeInfo().getDefaultTypeName());
    }

    private static Entity entityNamed(String name, ECoreModel model) {
        if (name == null || name.isEmpty()) return null;
        Entity e = model.getEntityModels().findByName(name);
        return e != null ? e : SpecFactory.globalEntity(name);
    }

    private static DataTransfer dtoNamed(String name, ECoreModel model) {
        if (name == null || name.isEmpty() || model.getDataTransferModels() == null) return null;
        return model.getDataTransferModels().getItems().findByName(name);
    }
}
