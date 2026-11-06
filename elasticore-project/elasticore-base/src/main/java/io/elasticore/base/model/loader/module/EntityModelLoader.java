package io.elasticore.base.model.loader.module;

import io.elasticore.base.model.ConstantParam;
import io.elasticore.base.model.MetaInfo;
import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.core.Items;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.FileSource;
import io.elasticore.base.model.loader.ModelLoader;
import io.elasticore.base.model.loader.ModelLoaderContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class EntityModelLoader extends AbstractModelLoader implements ConstantParam, ModelLoader<Entity> {

    // YAML block name for fixed values injected into every converted row at runtime.
    // Stored as a synthetic "staticvalues" meta annotation (TYPE_KEY_VALUE) accessible via
    // entity.getMetaInfo().getMetaAnnotation("staticvalues").getProperties().
    private static final String PROPERTY_STATIC_VALUES = "staticValues";

    // YAML block of business commands: "commands: { confirm: , ship: { warehouse: string(20)! } }".
    // Each command's parameters are written like fields.
    public static final String PROPERTY_COMMANDS = "commands";

    /** The DTO a command with parameters is sent as: SalesOrder + ship -> SalesOrderShipCommand. */
    public static String commandDtoName(String entityName, String command) {
        return entityName + Character.toUpperCase(command.charAt(0)) + command.substring(1) + "Command";
    }

    public boolean loadModel(ModelLoaderContext ctx, FileSource source) {
        return loadModel(ctx, source.getInfoMap());
    }

    public boolean loadModel(ModelLoaderContext ctx, Map<String, Map> map) {
        if (map.containsKey(ConstantParam.KEYNAME_ENTITY)) {
            Map entityMap = map.get(ConstantParam.KEYNAME_ENTITY);

            loadModel(ctx, ctx.getEntityItems(), entityMap);
            return true;
        }
        return false;
    }


    public void loadModel(ModelLoaderContext ctx, Items<Entity> items, Map<String, LinkedHashMap> entityMap) {

        this.setModelLoaderContext(ctx);

        entityMap.forEach((entityNm, value) -> {

            //System.err.println(entityNm + " parsed");


            Entity entity = loadEntity(ctx, entityNm, value);
            items.addItem(entity);
        });
    }


    protected Entity loadEntity(ModelLoaderContext ctx, String entityNm, Map<String, Object> entityMap) {

        MetaInfo metaInfo = parseMetaInfoObject(entityMap , ConstantParam.KEYNAME_ENTITY, entityNm);

        // Inject staticValues block (if present) as a synthetic meta annotation so
        // runtime EntityConverter can retrieve it via metaInfo.getMetaAnnotation("staticvalues").
        injectStaticValues(metaInfo, entityMap);

        Map fields = (Map) entityMap.get(PROPERTY_FIELDS);
        Items<Field> fieldItems = null;
        if (fields != null)
            fieldItems = parseField(fields, entityNm ,metaInfo);

        // When a row was deleted (@deleted) is the delete's to write, never a request's.
        if (fieldItems != null) {
            for (Field f : fieldItems.getItemList()) {
                if (f.hasAnnotation("deleted") && !f.hasAnnotation("readonly")) {
                    f.getAnnotationMap().put("readonly", Annotation.create("readonly"));
                }
            }
        }
        Entity entity = Entity.create(ctx.getDomainId(), entityNm, metaInfo, fieldItems);
        Map<String, Items<Field>> commands = parseCommands(entityNm, entityMap.get(PROPERTY_COMMANDS), metaInfo);
        Map<String, Map<String, String>> states = parseStates(entityMap.get(PROPERTY_STATES));
        for (Map.Entry<String, Map<String, String>> machine : states.entrySet()) {
            // The state is the lifecycle's to change: its commands, never a request.
            Field holder = fieldItems == null ? null : fieldItems.findByName(machine.getKey());
            if (holder != null) holder.getAnnotationMap().put("statefield", Annotation.create("statefield"));
            // A move names its command; one the commands block left out is declared here.
            for (String key : machine.getValue().keySet()) {
                // "delete" names the states a row may be deleted in; deleting is no command.
                if ("delete".equals(key)) continue;
                if (!"initial".equals(key) && !commands.containsKey(key)) commands.put(key, Items.create(Field.class));
            }
        }
        entity.setCommands(commands);
        entity.setStates(states);
        return entity;
    }

    // "states: { status: { initial: PLACED, ship: PLACED -> SHIPPED } }": a
    // field's lifecycle, one move per command.
    public static final String PROPERTY_STATES = "states";

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> parseStates(Object raw) {
        Map<String, Map<String, String>> out = new LinkedHashMap<String, Map<String, String>>();
        if (!(raw instanceof Map)) return out;
        for (Map.Entry<String, Object> machine : ((Map<String, Object>) raw).entrySet()) {
            if (machine.getKey() == null || !(machine.getValue() instanceof Map)) continue;
            Map<String, String> moves = new LinkedHashMap<String, String>();
            for (Map.Entry<String, Object> move : ((Map<String, Object>) machine.getValue()).entrySet()) {
                if (move.getKey() == null || move.getValue() == null) continue;
                Object v = move.getValue();
                if (v instanceof java.util.List) {
                    // initial may be written as a one-item list by habit; take it as text.
                    StringBuilder sb = new StringBuilder();
                    for (Object o : (java.util.List<Object>) v) sb.append(sb.length() > 0 ? ", " : "").append(o);
                    v = sb.toString();
                }
                moves.put(move.getKey().trim(), String.valueOf(v).trim());
            }
            out.put(machine.getKey().trim(), moves);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Items<Field>> parseCommands(String entityNm, Object raw, MetaInfo metaInfo) {
        Map<String, Items<Field>> out = new LinkedHashMap<String, Items<Field>>();
        if (!(raw instanceof Map)) return out;
        for (Map.Entry<String, Object> e : ((Map<String, Object>) raw).entrySet()) {
            String name = e.getKey() == null ? "" : e.getKey().trim();
            if (name.isEmpty()) continue;
            Items<Field> params = null;
            if (e.getValue() instanceof Map && !((Map<?, ?>) e.getValue()).isEmpty()) {
                params = parseField((Map<String, String>) e.getValue(), entityNm, metaInfo);
            }
            out.put(name, params == null ? Items.create(Field.class) : params);
        }
        return out;

    }

    @SuppressWarnings("unchecked")
    private void injectStaticValues(MetaInfo metaInfo, Map<String, Object> entityMap) {
        if (metaInfo == null) return;
        Object raw = entityMap.get(PROPERTY_STATIC_VALUES);
        if (!(raw instanceof Map)) return;

        Map<String, Object> svMap = (Map<String, Object>) raw;
        if (svMap.isEmpty()) return;

        Properties props = new Properties();
        for (Map.Entry<String, Object> e : svMap.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            props.setProperty(e.getKey(), String.valueOf(e.getValue()));
        }

        Annotation sv = Annotation.create("staticvalues", null, props);

        Map<String, Annotation> metaMap = metaInfo.getMetaAnnotationMap();
        if (metaMap != null) {
            metaMap.put("staticvalues", sv);
        }
    }

}
