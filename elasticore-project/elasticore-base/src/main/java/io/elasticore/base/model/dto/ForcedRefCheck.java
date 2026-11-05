package io.elasticore.base.model.dto;

import io.elasticore.base.ECoreModelContext;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.model.DataModelComponent;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.Field;

import java.util.ArrayList;
import java.util.List;

/**
 * A DTO field that names a relation by its key with forceupdate, as in
 * {@code assigneeId: string @ref(assignee.id) @forceupdate}, takes the
 * relation away when it comes without a value on a full update. What that
 * cannot mean is refused, and what makes it do nothing is said - read once
 * every domain is loaded, by both generators.
 */
public final class ForcedRefCheck {

    /** What stops the run, and what is only said; one sentence each. */
    public static final class Findings {
        public final List<String> refused = new ArrayList<String>();
        public final List<String> warned = new ArrayList<String>();
    }

    /** A template's relation to an entity, with that entity. */
    private static final class Relation {
        final Field field;
        final Entity target;

        Relation(Field field, Entity target) {
            this.field = field;
            this.target = target;
        }
    }

    private ForcedRefCheck() {}

    public static Findings of(DataTransfer dto, List<ECoreModelContext> contexts) {
        Findings out = new Findings();
        String templates = TemplateList.namesOf(dto.getMetaInfo());
        if (templates == null) return out;
        String home = dto.getIdentity().getDomainId();
        ModelComponentItems<Field> items = dto.getItems();
        for (int i = 0; i < items.size(); i++) {
            Field f = items.get(i);
            String ref = f.getAnnotationValue("ref");
            if (!f.hasAnnotation("forceupdate") || ref == null) continue;
            String at = dto.getIdentity().getName() + "." + f.getName() + " @ref(" + ref + ") @forceupdate";
            String[] path = ref.split("\\.", -1);
            if (path.length > 2) {
                out.refused.add(at + ": only a relation's own key is cleared, and " + ref + " goes further");
                continue;
            }
            Relation relation = relationOf(templates, path[0], contexts, home);
            if (relation == null) {
                out.warned.add(
                        at + " does nothing: " + path[0] + " is no relation of " + templates.trim() + " to an entity");
                continue;
            }
            String key = singleKeyOf(relation.target);
            if (path.length < 2 || key == null || !key.equals(path[1])) {
                out.refused.add(at + ": only a relation's own key is cleared, and " + ref + " is not "
                        + (key == null ? "a single key of " + path[0] : path[0] + "." + key));
                continue;
            }
            Field rel = relation.field;
            if (rel.hasAnnotation("notnull")) {
                out.refused.add(at + ": " + path[0] + " is required, so it cannot be cleared");
                continue;
            }
            String owner = rel.getAnnotationValue("joinby", "joinfield");
            if (rel.hasAnnotation("cascade")) {
                out.warned.add(at + " does nothing: " + path[0] + " cascades, so the mapper writes it whole");
            } else if (owner != null && !owner.trim().isEmpty()) {
                // Mapped insertable = false, updatable = false: cleared in memory, kept by the row.
                out.warned.add(at + " does nothing: " + path[0] + " is joined read-only on the column of "
                        + owner.trim() + ", so the row keeps its value - send " + owner.trim() + " instead");
            } else if ("false"
                    .equalsIgnoreCase(
                            String.valueOf(rel.getAnnotationValue("updatable")).trim())) {
                out.warned.add(at + " does nothing on an update: " + path[0] + " is @updatable(false), so the row"
                        + " keeps the relation it was created with");
            }
        }
        return out;
    }

    /** The template's field the path starts at, if it is a relation to an entity. */
    private static Relation relationOf(String templates, String name, List<ECoreModelContext> contexts, String home) {
        for (String template : templates.split(",", -1)) {
            DataModelComponent model = modelNamed(contexts, template.trim(), home);
            if (model == null) continue;
            Field field = (Field) model.getAllFieldListMap().get(name);
            if (field == null) continue;
            DataModelComponent target = modelNamed(
                    contexts,
                    field.getTypeInfo().getBaseTypeName(),
                    model.getIdentity().getDomainId());
            if (target instanceof Entity) return new Relation(field, (Entity) target);
        }
        return null;
    }

    /** The name of an entity's only key field, or null when it has none or several. */
    static String singleKeyOf(Entity entity) {
        String key = null;
        for (Object each : entity.getAllFieldListMap().values()) {
            Field f = (Field) each;
            if (!f.isPrimaryKey()) continue;
            if (key != null) return null;
            key = f.getName();
        }
        return key;
    }

    /**
     * A model by its name from the domains this run loaded: in the domain a
     * name says (crm:Order), or else in this one first, as a model looks up
     * its own; then in any other.
     */
    static DataModelComponent modelNamed(List<ECoreModelContext> contexts, String name, String home) {
        String domain = name.indexOf(':') > 0 ? name.substring(0, name.indexOf(':')) : null;
        String model = name.substring(name.indexOf(':') + 1);
        DataModelComponent elsewhere = null;
        for (ECoreModelContext ctx : contexts) {
            String[] domainNames = ctx.getInternalDomainNames();
            if (domainNames == null) continue;
            for (String domainName : domainNames) {
                if (domain != null && !domain.equals(domainName)) continue;
                ModelDomain d = ctx.getDomain(domainName);
                DataModelComponent found =
                        d == null || d.getModel() == null ? null : d.getModel().findModelByName(model);
                if (found == null) continue;
                if (domain != null || domainName.equals(home)) return found;
                if (elsewhere == null) elsewhere = found;
            }
        }
        return elsewhere;
    }
}
