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

    private ForcedRefCheck() {}

    public static Findings of(DataTransfer dto, List<ECoreModelContext> contexts) {
        Findings out = new Findings();
        String templates = dto.getMetaInfo().getMetaAnnotationValue(DataTransferAnnotation.META_TEMPLATE);
        if (templates == null) return out;
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
            Field relation = relationOf(templates, path[0], contexts);
            if (relation == null) continue;
            String key = singleKeyOf(relation.getTypeInfo().getBaseTypeName(), contexts);
            if (path.length < 2 || key == null || !key.equals(path[1])) {
                out.refused.add(at + ": only a relation's own key is cleared, and " + ref + " is not "
                        + (key == null ? "a single key of " + path[0] : path[0] + "." + key));
                continue;
            }
            if (relation.hasAnnotation("notnull")) {
                out.refused.add(at + ": " + path[0] + " is required, so it cannot be cleared");
                continue;
            }
            if (relation.hasAnnotation("cascade")) {
                out.warned.add(at + " does nothing: " + path[0] + " cascades, so the mapper writes it whole");
            }
        }
        return out;
    }

    /** The template's field the path starts at, if it is a relation to an entity. */
    private static Field relationOf(String templates, String name, List<ECoreModelContext> contexts) {
        for (String template : templates.split(",", -1)) {
            DataModelComponent model = modelNamed(contexts, template.trim());
            if (model == null) continue;
            Field relation = (Field) model.getAllFieldListMap().get(name);
            if (relation != null && modelNamed(contexts, relation.getTypeInfo().getBaseTypeName()) instanceof Entity) {
                return relation;
            }
        }
        return null;
    }

    /** The name of an entity's only key field, or null when it has none or several. */
    private static String singleKeyOf(String entity, List<ECoreModelContext> contexts) {
        DataModelComponent model = modelNamed(contexts, entity);
        String key = null;
        for (Object each : model.getAllFieldListMap().values()) {
            Field f = (Field) each;
            if (!f.isPrimaryKey()) continue;
            if (key != null) return null;
            key = f.getName();
        }
        return key;
    }

    /** A model by its name (crm:Order is read as Order) from the domains this run loaded. */
    private static DataModelComponent modelNamed(List<ECoreModelContext> contexts, String name) {
        String model = name.substring(name.indexOf(':') + 1);
        for (ECoreModelContext ctx : contexts) {
            String[] domainNames = ctx.getInternalDomainNames();
            if (domainNames == null) continue;
            for (String domainName : domainNames) {
                ModelDomain d = ctx.getDomain(domainName);
                DataModelComponent found =
                        d == null || d.getModel() == null ? null : d.getModel().findModelByName(model);
                if (found != null) return found;
            }
        }
        return null;
    }
}
