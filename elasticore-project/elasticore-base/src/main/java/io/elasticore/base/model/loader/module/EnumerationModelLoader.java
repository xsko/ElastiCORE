package io.elasticore.base.model.loader.module;

import io.elasticore.base.model.ConstantParam;
import io.elasticore.base.model.MetaInfo;
import io.elasticore.base.model.core.Items;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.enums.EnumConstant;
import io.elasticore.base.model.enums.EnumModel;
import io.elasticore.base.model.loader.FileSource;
import io.elasticore.base.model.loader.ModelLoader;
import io.elasticore.base.model.loader.ModelLoaderContext;
import io.elasticore.base.util.ConsoleLog;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnumerationModelLoader extends AbstractModelLoader implements ConstantParam, ModelLoader<EnumModel> {


    public boolean loadModel(ModelLoaderContext ctx, FileSource source) {
        return loadModel(ctx, source.getInfoMap());
    }

    public boolean loadModel(ModelLoaderContext ctx, Map<String, Map> map) {
        if (map.containsKey(ConstantParam.KEYNAME_ENUMERATION)) {
            Map entityMap = map.get(ConstantParam.KEYNAME_ENUMERATION);

            loadModel(ctx, ctx.getEnumModelItems(), entityMap);
            return true;
        }
        return false;
    }


    public void loadModel(ModelLoaderContext ctx,Items<EnumModel> items, Map<String, LinkedHashMap> enumMap) {

        this.setModelLoaderContext(ctx);

        if(enumMap==null)
            return;

        enumMap.forEach((enumNm, value) -> {

            //System.err.println(enumNm + " parsed");

            if(value!=null) {
                EnumModel enumModel = loadEnumModel(ctx, enumNm, value);

                ctx.getEnumModelItems().addItem(enumModel);

                items.addItem(enumModel);
            }
        });
    }


    protected EnumModel loadEnumModel(ModelLoaderContext ctx,String enumName, Map<String, Object> entityMap) {

        Map fields = (Map) entityMap.get(PROPERTY_FIELDS);
        Items<Field> fieldItems = null;
        if (fields != null)
            fieldItems = parseField(fields);

        // Parse meta info with @code annotation enhancement
        MetaInfo metaInfo = parseMetaInfoObjectWithCodeAnnotation(entityMap, ConstantParam.KEYNAME_ENUM, enumName, fieldItems);

        Map enumMap = (Map) entityMap.get(PROPERTY_ENUM);
        Items<EnumConstant> enumItems = null;
        if (enumMap != null) {
            int fieldCount = (fieldItems != null) ? fieldItems.getItemList().size() : 0;
            enumItems = parseEnum(ctx, enumMap, fieldCount);
        }

        // DSL validation: check enum constant values against field definitions
        if (fieldItems != null && enumItems != null) {
            validateEnumValues(enumName, fieldItems, enumItems);
        }

        //(String name, MetaInfo meta, Items<Field> items, Items<EnumConstant> enumConstantItems)
        return EnumModel.create(ctx.getDomainId(), enumName, metaInfo, fieldItems, enumItems);
    }

    /**
     * Validate enum constant values against field type/length definitions.
     * Reports warnings when values exceed declared field lengths.
     */
    private void validateEnumValues(String enumName, Items<Field> fieldItems, Items<EnumConstant> enumItems) {
        List<Field> fields = fieldItems.getItemList();
        for (EnumConstant ec : enumItems.getItemList()) {
            List<EnumConstant.ConstructParam> params = ec.getConstructParamList();
            for (int i = 0; i < Math.min(params.size(), fields.size()); i++) {
                Field field = fields.get(i);
                EnumConstant.ConstructParam param = params.get(i);
                int maxLen = field.getLength();
                if (maxLen > 0 && param.getName() != null) {
                    String value = param.getName();
                    // Strip surrounding quotes if present
                    if (value.startsWith("'") && value.endsWith("'")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    if (value.length() > maxLen) {
                        String found = String.format(
                                "enum %s.%s: field '%s' value '%s' (length %d) exceeds max length %d",
                                enumName, ec.getIdentity().getName(), field.getName(), value, value.length(), maxLen);
                        if (io.elasticore.base.model.entity.BaseFieldType.ruleEngineTypes()) {
                            // The 3.0 rule engine stops on it, as the 2.x generator
                            // does: the column is that long, so the row would
                            // fail when it is inserted.
                            io.elasticore.base.model.pub.rules.ModelCheck.fail(String.format(
                                    "[elcore3] %s - the column holds %d characters. Widen the field (%s: string(%d))"
                                            + " or shorten the value.",
                                    found, maxLen, field.getName(), value.length()));
                        } else {
                            ConsoleLog.storeLog("DSL_WARN", found);
                        }
                    }
                }
            }
        }
    }

    protected Items<EnumConstant> parseEnum(ModelLoaderContext ctx,Map<String, String> fieldInfo, int fieldCount) {
        Items<EnumConstant> enumConstantItems = Items.create(EnumConstant.class);

        fieldInfo.forEach((fieldNm, fieldPropery) -> {
            //System.out.println("* ENUM :" + fieldNm + " >>  " + fieldPropery);

            EnumConstant f = parseEnumLine(fieldNm, fieldPropery, fieldCount);
            enumConstantItems.addItem(f);
        });

        return enumConstantItems;
    }


    private static EnumConstant parseEnumLine(String enumName, String valueLine, int fieldCount) {
        // Handle null or empty value lines
        if (valueLine == null || valueLine.trim().isEmpty()) {
            // If no values provided, use enum name for all fields
            List<EnumConstant.ConstructParam> paramList = new ArrayList<>();
            for (int i = 0; i < Math.max(1, fieldCount); i++) {
                paramList.add(EnumConstant.ConstructParam.create(enumName));
            }
            return EnumConstant.builder().name(enumName).constructParamList(paramList).build();
        }

        //String[] items = valueLine.trim().split("\\s*,\\s*");

        String regex = "'(?:[^']|'')*'|[^,]+";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(valueLine);

        // List to store results
        ArrayList<String> results = new ArrayList<>();

        // Add matched groups from regex to list
        while (matcher.find()) {
            results.add(matcher.group().trim());
        }

        String[] items = results.toArray(new String[0]);

        //System.err.println(enumName + " > " + items.length);
        List<EnumConstant.ConstructParam> paramList = new ArrayList<>();
        
        if (items.length == 0) {
            // If no items parsed, use enum name for all fields
            for (int i = 0; i < Math.max(1, fieldCount); i++) {
                paramList.add(EnumConstant.ConstructParam.create(enumName));
            }
        } else if (items.length == 1 && fieldCount == 1) {
            // If only one item and one field, use it directly
            String value = items[0];
            paramList.add(EnumConstant.ConstructParam.create(value));
        } else if (items.length == 1 && fieldCount > 1) {
            // If only one item but multiple fields, use it as the first field and enum name for the rest
            String value = items[0];
            paramList.add(EnumConstant.ConstructParam.create(value));
            for (int i = 1; i < fieldCount; i++) {
                paramList.add(EnumConstant.ConstructParam.create(enumName));
            }
        } else {
            // If multiple items, use them as provided (matching field count)
            for (int i = 0; i < Math.min(items.length, fieldCount); i++) {
                paramList.add(EnumConstant.ConstructParam.create(items[i]));
            }
            // If we have fewer items than fields, fill with enum name
            for (int i = items.length; i < fieldCount; i++) {
                paramList.add(EnumConstant.ConstructParam.create(enumName));
            }
        }

        EnumConstant enumConstant = EnumConstant.builder().name(enumName).constructParamList(paramList).build();

        return enumConstant;
    }

    /**
     * Enhanced parseMetaInfoObject that auto-generates @db and @json annotations 
     * when @code annotation is found in fields
     */
    protected MetaInfo parseMetaInfoObjectWithCodeAnnotation(Map<String, Object> entityMap, String type, String mainClassName, Items<Field> fieldItems) {
        if(entityMap == null)
            entityMap = new HashMap<>();

        Object infoObj = entityMap.get(PROPERTY_INFO);
        Map<String, io.elasticore.base.model.core.Annotation> infoAnnotation = getAnnotationObj(infoObj);

        Object metaInfoObj = entityMap.get(PROPERTY_META);
        Map<String, io.elasticore.base.model.core.Annotation> metaAnnotation = null;
        if(metaInfoObj == null) {
            metaAnnotation = io.elasticore.base.model.core.Annotation.createAnnotationMap();
            if(type != null)
                metaAnnotation.put("type", io.elasticore.base.model.core.Annotation.create("type", type));
        } else {
            metaAnnotation = getAnnotationObj(metaInfoObj);
        }

        if(mainClassName != null) {
            metaAnnotation.put("name", io.elasticore.base.model.core.Annotation.create("name", mainClassName));
        }

        // Auto-generate @db and @json annotations if @code annotation is found
        if (fieldItems != null && !fieldItems.getItemList().isEmpty()) {
            // Check if @db or @json is already explicitly defined
            boolean hasDbAnnotation = metaAnnotation.containsKey("db");
            boolean hasJsonAnnotation = metaAnnotation.containsKey("json");

            // Find field with @code annotation
            String codeFieldName = null;
            for (Field field : fieldItems.getItemList()) {
                if (field.getAnnotation("code") != null) {
                    codeFieldName = field.getName();
                    break;
                }
            }

            // Auto-generate missing annotations
            if (codeFieldName != null) {
                if (!hasDbAnnotation) {
                    metaAnnotation.put("db", io.elasticore.base.model.core.Annotation.create("db", codeFieldName));
                }
                if (!hasJsonAnnotation) {
                    metaAnnotation.put("json", io.elasticore.base.model.core.Annotation.create("json", codeFieldName));
                }
            }
        }

        String desc = (String)  entityMap.get(PROPERTY_DESC);
        return MetaInfo.creat(infoAnnotation, metaAnnotation, desc);
    }

}
