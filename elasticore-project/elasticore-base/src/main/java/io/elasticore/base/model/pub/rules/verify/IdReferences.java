package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.entity.BaseFieldType;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.module.AbstractModelLoader;
import io.elasticore.base.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Another aggregate is referenced by its identity.
 *
 * <pre>
 *   partner: Partner @ManyToOne            (the DSL, as written)
 *
 *   partnerId: long @db(partner_partner_id)                  the reference: written, sealed with the root
 *   partner: Partner @ManyToOne @joinBy(partnerId)           read-only: for queries and display
 * </pre>
 *
 * The aggregate holds the other root's identity and nothing it could write
 * through: the id is the field that is set, compared and saved, and the
 * relation stays only as a read-only join on the same column - no setter -
 * so a query can still say {@code o.partner.name} and a DTO can still show
 * the partner's name, while no code in this aggregate can change which
 * partner it points at except by its id, or change the partner at all.
 *
 * The column is the one the relation always had, so the table does not
 * change. The rewrite is of the model, before any spec is built, so every
 * artifact - entity, DTO, mapper, query class - sees the same two fields.
 *
 * With the DDD rule sets only (the ones that decide references), for a
 * to-one reference to another root of the same domain with a single key.
 * {@code @objectref} keeps a reference as an object; {@code @joinBy} written
 * by hand is left as it is.
 */
final class IdReferences {

    private IdReferences() {
    }

    private static final class Parser extends AbstractModelLoader {
        Field parse(String name, String line, Entity owner) {
            return super.parseFieldLine(name, line, owner.getMetaInfo());
        }
    }

    /** Rewrites every qualifying reference of the model; a second call finds nothing left to do. */
    static void apply(ECoreModel model) {
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        List<Entity> entities = new ArrayList<Entity>();
        for (int i = 0; i < all.size(); i++) entities.add(all.get(i));
        Parser parser = new Parser();
        // config: referenceColumn: short - customer_id rather than 2.x's
        // customer_customer_id. Opt-in: renaming a column is a migration.
        boolean shortColumns = "short".equalsIgnoreCase(String.valueOf(model.getConfig("referenceColumn")).trim());

        for (Entity entity : entities) {
            if (entity.getMetaInfo().hasMetaAnnotation("embeddable")
                    || entity.getMetaInfo().hasMetaAnnotation("abstract")) continue;
            if (entity.getItems() == null) continue;
            String name = entity.getIdentity().getName();
            String ownRoot = rootOf(name, model);

            List<Field> own = new ArrayList<Field>();
            ModelComponentItems<Field> items = entity.getItems();
            for (int i = 0; i < items.size(); i++) own.add(items.get(i));

            for (Field f : own) {
                Entity target = target(f, model);
                if (target == null) continue;
                if (f.hasAnnotation("cascade") || f.hasAnnotation("embedded") || f.hasAnnotation("joinby")
                        || f.hasAnnotation("joinfield") || f.hasAnnotation("objectref")) continue;
                if (f.hasAnnotation("onetomany") || f.hasAnnotation("manytomany")) continue;
                String mappedBy = f.getAnnotation("onetoone") == null ? null
                        : f.getAnnotation("onetoone").getProperties() == null ? null
                        : f.getAnnotation("onetoone").getProperties().getProperty("mappedBy");
                if (mappedBy != null) continue;

                String targetName = target.getIdentity().getName();
                // A root of its own; not this entity's own root, which a
                // member points back at.
                if (SpecFactory.aggregateOfModel(targetName, model) != null) continue;
                if (targetName.equals(ownRoot)) continue;

                Field pk = singleKey(target);
                if (pk == null) continue;
                String type = dslType(pk);
                if (type == null) continue;

                String idName = pk.getName().startsWith(f.getName())
                        ? pk.getName()
                        : f.getName() + Character.toUpperCase(pk.getName().charAt(0)) + pk.getName().substring(1);
                if (hasField(entity, idName)) {
                    io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                            "[%s] %s.%s refers to another aggregate, and %s is already a field - the reference"
                                    + " stays an object. Rename the field, or mark it @objectref.",
                            name, name, f.getName(), idName));
                    continue;
                }

                String column = f.getAnnotationValue("column", "col", "db");
                if (column == null || column.isEmpty()) {
                    if (shortColumns) {
                        // The column is named after the field it is: customer_id.
                        column = StringUtils.camelToSnake(idName);
                    } else {
                        // 2.x's join column, so a database made from 2.x keeps fitting.
                        String pkColumn = pk.getAnnotationValue("column", "col", "db");
                        if (pkColumn == null || pkColumn.isEmpty()) pkColumn = StringUtils.camelToSnake(pk.getName());
                        column = StringUtils.camelToSnake(f.getName()) + "_" + pkColumn;
                    }
                }

                boolean required = f.hasAnnotation("notnull");
                StringBuilder line = new StringBuilder(type).append(required ? "!" : "");
                line.append(" @db(").append(column).append(")");
                line.append(" @idof(").append(f.getName()).append(")");
                String search = f.getAnnotationValue("search", "s", "srch", "filter");
                if (f.hasAnnotation("search") && search != null) line.append(" @search(").append(search).append(")");
                // "a review stays on its item": the identity is what is written, so it carries the relation's
                // @updatable(false) - the column is not updated and the mapper writes it only on create.
                if ("false".equalsIgnoreCase(String.valueOf(f.getAnnotationValue("updatable")).trim())) {
                    line.append(" @updatable(false)");
                }
                Field id = parser.parse(idName, line.toString(), entity);
                entity.addDerivedField(id);

                f.getAnnotationMap().put("joinby", Annotation.create("joinby", idName));
                f.getAnnotationMap().put("idref", Annotation.create("idref", idName));
            }
        }
    }

    private static Entity target(Field f, ECoreModel model) {
        if (f.getTypeInfo() == null || f.getTypeInfo().isList() || f.getTypeInfo().isBaseType()) return null;
        String type = f.getTypeInfo().getBaseTypeName();
        if (type == null) return null;
        Entity target = model.getEntityModels().findByName(type);
        if (target == null) return null;
        if (target.getMetaInfo().hasMetaAnnotation("embeddable")
                || target.getMetaInfo().hasMetaAnnotation("abstract")) return null;
        return target;
    }

    private static String rootOf(String name, ECoreModel model) {
        String holder = SpecFactory.aggregateOfModel(name, model);
        return holder == null ? name : holder;
    }

    private static Field singleKey(Entity target) {
        Field key = null;
        io.elasticore.base.model.core.ListMap<String, Field> fields = target.getAllFieldListMap();
        for (Field f : fields.values()) {
            if (!f.isPrimaryKey() && !f.hasAnnotation("pk")) continue;
            if (key != null) return null;
            key = f;
        }
        return key;
    }

    /** The key's type as the DSL writes it, with its length; null for a key this cannot mirror. */
    private static String dslType(Field pk) {
        BaseFieldType t = pk.getTypeInfo().getBaseFieldType();
        if (t == null) return null;
        switch (t) {
            case STRING:
                int length = pk.getLength() > 0 ? pk.getLength() : pk.getMaxLength();
                return length > 0 ? "string(" + length + ")" : "string";
            case P_LONG:
            case LONG:
                return "long";
            case P_INT:
            case INTEGER:
                return "int";
            default:
                return null;
        }
    }

    private static boolean hasField(Entity entity, String name) {
        io.elasticore.base.model.core.ListMap<String, Field> fields = entity.getAllFieldListMap();
        return fields.get(name) != null;
    }
}
