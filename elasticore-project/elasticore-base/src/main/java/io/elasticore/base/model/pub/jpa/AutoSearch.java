package io.elasticore.base.model.pub.jpa;

import io.elasticore.base.ECoreModelContext;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.model.MetaInfo;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.core.ListMap;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.dto.DataTransferAnnotation;
import io.elasticore.base.model.entity.BaseFieldType;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.EntityAnnotation;
import io.elasticore.base.model.entity.Field;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which fields a search DTO searches without their own @search, shared by the 2.x publishers and
 * the rule engine so both decide alike (issue 376, the same decision as 2.2.261026).
 *
 * The search DTO of an @expose entity searches all its fields. Any other search DTO (of a @service
 * entity, or written in the DSL) searches the fields its entity inherits through @extend when
 * some @expose search DTO has the same field: the fields of an @extend parent are one Field object
 * for every entity that extends it.
 *
 * Decided, never written into the field: the condition used to be written into the shared field,
 * so only the search DTOs published after the @expose one saw it, and the output depended on the
 * order of the entities and on which domains were generated. A search DTO now always gets those
 * inherited fields - what it got when it came after the @expose one.
 */
public final class AutoSearch {

    private AutoSearch() {}

    /** The condition a field's type gives it: text by pattern, temporal by range, else equality. */
    public static String conditionByType(Field f) {
        if (f.getTypeInfo().isStringType()) return "auto";

        BaseFieldType baseFieldType = f.getTypeInfo().getBaseFieldType();
        if (baseFieldType == BaseFieldType.LocalDateTime
                || baseFieldType == BaseFieldType.LocalTime
                || baseFieldType == BaseFieldType.LocalDate
                || baseFieldType == BaseFieldType.DATETIME
                || baseFieldType == BaseFieldType.DATE) return "between";

        return "eq";
    }

    /** True when the entity has the field from an @extend parent, not as its own field. */
    public static boolean isInherited(Entity entity, Field f) {
        return entity != null && entity.getItems().findByName(f.getName()) == null;
    }

    /** The entity a search DTO searches, or null. */
    public static Entity searchedEntity(ECoreModelContext ctx, MetaInfo searchDtoMeta) {
        String entityNm = searchDtoMeta.getMetaAnnotationValue(DataTransferAnnotation.META_SEARCHABLE_NAME);
        if (entityNm == null || ctx == null) return null;
        return ctx.findModelComponent(entityNm, Entity.class);
    }

    /**
     * The annotation maps of the fields of every @expose search DTO in every loaded domain - by
     * identity, because a DTO's own copy of a field shares the entity field's annotation map.
     */
    public static Set<Map<String, Annotation>> exposedSearchAnnotationMaps(ECoreModelContext ctx) {
        Set<Map<String, Annotation>> maps = Collections.newSetFromMap(new IdentityHashMap<>());
        if (ctx == null) return maps;

        for (String domainName : ctx.getAllDomainNames()) {
            ModelDomain domain = ctx.getDomain(domainName);
            if (domain == null || domain.getModel() == null || domain.getModel().getDataTransferModels() == null)
                continue;

            ModelComponentItems<DataTransfer> dtoItems =
                    domain.getModel().getDataTransferModels().getItems();
            for (int i = 0; i < dtoItems.size(); i++) {
                DataTransfer dto = dtoItems.get(i);
                MetaInfo metaInfo = dto.getMetaInfo();
                if (!metaInfo.hasMetaAnnotation(DataTransferAnnotation.META_SEARCHABLE)
                        || !metaInfo.hasMetaAnnotation(EntityAnnotation.META_EXPOSE)
                        || "template".equals(metaInfo.getMetaAnnotationValue("type"))) continue;

                ListMap<String, Field> fields = dto.getAllFieldListMap();
                if (fields == null) continue;
                for (Field f : fields.getList()) {
                    if (f.getAnnotationMap() != null) maps.add(f.getAnnotationMap());
                }
            }
        }
        return maps;
    }
}
