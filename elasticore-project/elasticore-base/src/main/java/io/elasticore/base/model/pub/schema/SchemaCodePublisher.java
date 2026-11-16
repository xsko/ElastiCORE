package io.elasticore.base.model.pub.schema;

import io.elasticore.base.CodePublisher;
import io.elasticore.base.ECoreModelContext;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.SourceFileAccessFactory;
import io.elasticore.base.model.ModelComponent;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.entity.EntityModels;
import io.elasticore.base.model.entity.TypeInfo;
import io.elasticore.base.model.enums.EnumModel;
import io.elasticore.base.model.enums.EnumModels;
import io.elasticore.base.model.enums.EnumConstant;
import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.MetaInfo;
import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.util.ConsoleLog;
import io.elasticore.base.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Schema Code Publisher for generating unified schema.yml file
 * containing all domain entity models with database table and column information
 */
public class SchemaCodePublisher implements CodePublisher {

    private String outputPath;
    private List<EntitySchemaInfo> allEntities = new ArrayList<>();
    private List<EnumSchemaInfo> allEnums = new ArrayList<>();
    private SourceFileAccessFactory sourceFileAccessFactory;
    private ECoreModelContext ctx;

    public SchemaCodePublisher() {
        this.outputPath = "src/main/resources/blueprint/schema.yml";
    }

    @Override
    public void setSrcCodeWriterFactory(SourceFileAccessFactory sourceFileAccessFactory) {
        this.sourceFileAccessFactory = sourceFileAccessFactory;
    }

    @Override
    public SourceFileAccessFactory getSrcFileAccessFactory() {
        return this.sourceFileAccessFactory;
    }

    @Override
    public ECoreModelContext getECoreModelContext() {
        return this.ctx;
    }

    @Override
    public void publish(ECoreModelContext ctx, ModelDomain domain) {
        this.ctx = ctx;
        collectEntityInfo(ctx, domain);
    }

    @Override
    public void errorOnPublish(ModelComponent modelComponent, Throwable e) {
        ConsoleLog.printWarn("Error publishing schema for component: " + modelComponent.getClass().getSimpleName() + " - " + e.getMessage());
    }

    @Override
    public boolean deleteSource(ModelDomain domain, String rootDir) {
        // Not applicable for schema generation
        return true;
    }

    /**
     * Collect entity and enum information from the domain
     */
    private void collectEntityInfo(ECoreModelContext ctx, ModelDomain domain) {
        String domainName = domain.getName();
        //ConsoleLog.print("[SchemaPublisher] Processing domain: " + domainName);

        ECoreModel model = domain.getModel();
        if (model != null) {
            // Collect entities
            EntityModels entityModels = model.getEntityModels();
            if (entityModels != null) {
                // Iterate through entities using index-based access
                ModelComponentItems<Entity> items = entityModels.getItems();
                for (int i = 0; i < items.size(); i++) {
                    Entity entity = items.get(i);
                    // Skip abstract entities
                    if (isAbstractEntity(entity)) {
                        //ConsoleLog.print("[SchemaPublisher] Skipping abstract entity: " + entity.getIdentity().getName());
                        continue;
                    }
                    EntitySchemaInfo schemaInfo = createEntitySchemaInfo(entity, domainName);
                    allEntities.add(schemaInfo);
                    //ConsoleLog.print("[SchemaPublisher] Added entity: " + entity.getIdentity().getName());
                }
            }
            
            // Collect enums
            EnumModels enumModels = model.getEnumModels();
            if (enumModels != null) {
                ModelComponentItems<EnumModel> enumItems = enumModels.getItems();
                //ConsoleLog.print("[SchemaPublisher] Found " + enumItems.size() + " enum models in domain: " + domainName);
                for (int i = 0; i < enumItems.size(); i++) {
                    EnumModel enumModel = enumItems.get(i);
                    EnumSchemaInfo enumInfo = createEnumSchemaInfo(enumModel, domainName);
                    allEnums.add(enumInfo);
                    //ConsoleLog.print("[SchemaPublisher] Added enum: " + enumModel.getIdentity().getName());
                }
            } else {
                ConsoleLog.print("[SchemaPublisher] No enum models found in domain: " + domainName);
            }
        }
    }

    /**
     * Create schema information for an entity
     */
    private EntitySchemaInfo createEntitySchemaInfo(Entity entity, String domainName) {
        EntitySchemaInfo info = new EntitySchemaInfo();
        info.name = entity.getIdentity().getName();
        info.domainName = domainName;
        
        // Get table prefix from config and apply it to table name
        String tableName = entity.getTableName();
        String tablePrefix = null;
        if (ctx != null && ctx.getDomain() != null && ctx.getDomain().getModel() != null) {
            tablePrefix = ctx.getDomain().getModel().getConfig("table.prefix");
        }
        
        if (tablePrefix != null && !tablePrefix.isEmpty()) {
            tableName = tablePrefix + tableName;
        }
        
        info.tableName = tableName;
        info.javaClassName = entity.getFullName();
        
        // Get meta annotations
        MetaInfo metaInfo = entity.getMetaInfo();
        List<String> metaAnnotations = new ArrayList<>();
        metaAnnotations.add("entity");
        
        if (metaInfo != null) {
            if (metaInfo.hasMetaAnnotation("embeddable")) {
                metaAnnotations.add("@embeddable");
            }
            if (metaInfo.hasMetaAnnotation("dto")) {
                metaAnnotations.add("@dto");
            }
            // Handle @extend annotation
            if (metaInfo.hasMetaAnnotation("extend")) {
                String parentName = metaInfo.getMetaAnnotationValue("extend");
                metaAnnotations.add("@extend(" + parentName + ")");
            }
            info.label = metaInfo.getLabel();
        }
        info.description = metaInfo.getDescription();
        
        info.metaAnnotations = String.join(" ", metaAnnotations);
        
        // Process fields with inheritance
        info.fields = new ArrayList<>();
        
        // Get all fields including inherited ones
        Map<String, Field> allFields = getAllInheritedFields(entity);
        for (Map.Entry<String, Field> entry : allFields.entrySet()) {
            Field field = entry.getValue();
            FieldSchemaInfo fieldInfo = createFieldSchemaInfo(field, entity);
            info.fields.add(fieldInfo);
        }
        
        return info;
    }
    
    /**
     * Create schema information for a field
     */
    private FieldSchemaInfo createFieldSchemaInfo(Field field, Entity entity) {
        FieldSchemaInfo info = new FieldSchemaInfo();
        info.name = field.getName();
        
        TypeInfo typeInfo = field.getTypeInfo();
        String originalType = typeInfo.getDefaultTypeName();
        
        // Get column name - for enum types, generate from field name if null
        info.columnName = field.getDbColumnName();
        if (info.columnName == null || "null".equals(info.columnName)) {
            info.columnName = camelToSnakeCase(field.getName());
        }
        
        // Check if field is enum type
        EnumModel enumModel = findEnumModel(originalType);
        boolean isEnumType = enumModel != null;

        // Only a type that names an entity of the model is a relation. A value type
        // the field resolves to - java.math.BigDecimal, java.time.Instant, Money,
        // byte[] - is a column of this table, like a string.
        boolean isList = typeInfo.isList();
        Entity target = (entity == null || isEnumType) ? null : findReferencedEntity(field);
        boolean embeddedValue = target != null
                && (field.hasAnnotation("embedded") || isEmbeddableEntity(target));
        boolean relation = target != null && !embeddedValue;
        // A list of enum codes or of an embeddable value is an element collection:
        // rows of a table of its own, no column of this one.
        boolean elementCollection = entity != null && isList
                && (embeddedValue || findEnumModel(typeInfo.getCoreItemType()) != null);

        // Set field type and handle enum types
        if (isEnumType) {
            // For enum types, use the enum's code field type and length
            info.type = getEnumCodeType(enumModel);
        } else if (elementCollection) {
            info.type = originalType;
        } else if (relation && isList) {
            info.type = originalType + " " + generateCollectionAnnotation(field, originalType);
        } else if (relation) {
            info.type = originalType + " " + generateReferenceAnnotation(field, entity, target);
        } else if (embeddedValue) {
            info.type = originalType;
        } else {
            // Map Java types to schema types
            info.type = mapJavaTypeToSchemaType(originalType, field.getLength());
        }

        // Build field annotations (only for primitive types or enum types)
        List<String> annotations = new ArrayList<>();

        // @Column for every field stored in a column of this table: not a relation,
        // not an embedded value (its own columns), not an element collection, and not
        // a formula, which is computed on read and has no column in the table.
        if (field.hasAnnotation("formula")) {
            annotations.add("@Formula");
        } else if (elementCollection) {
            annotations.add("@ElementCollection");
        } else if (isEnumType || (target == null && !field.hasAnnotation("embedded"))) {
            StringBuilder columnAnnotation = new StringBuilder("@Column(name = \"" + info.columnName + "\"");
            
            Integer length = field.getLength();
            if (isEnumType) {
                // For enum types, use the length from enum definition
                length = getEnumCodeLength(enumModel);
            }
            
            if (length != null && length > 0) {
                columnAnnotation.append(", length = ").append(length);
            }
            columnAnnotation.append(")");
            annotations.add(columnAnnotation.toString());
        } else if (embeddedValue) {
            annotations.add("@Embedded");
        }
        
        // Add other annotations
        if (field.isPrimaryKey()) {
            annotations.add("@Id");
        }
        if (!field.isNullable()) {
            annotations.add("@NotNull");
        }
        if (field.isUnique()) {
            annotations.add("@Unique");
        }
        
        // Check for custom annotations
        Map<String, Annotation> annotationMap = field.getAnnotationMap();
        if (annotationMap != null) {
            for (Map.Entry<String, Annotation> entry : annotationMap.entrySet()) {
                String annName = entry.getKey();

                if ("sequence".equals(annName) || "generated".equals(annName)) {
                    annotations.add("@GeneratedValue");
                } else if ("embedded".equals(annName)) {
                    if (!elementCollection && !annotations.contains("@Embedded")) annotations.add("@Embedded");
                } else if ("cascade".equals(annName)) {
                    annotations.add("@Cascade");
                } else if ("fk".equals(annName) || "foreignkey".equals(annName)) {
                    // Preserve @fk annotation in schema output for FK metadata tracking
                    String fkValue = entry.getValue().getValue();
                    if (fkValue != null) {
                        annotations.add("@fk(" + fkValue + ")");
                    }
                }
            }
        }
        
        info.annotations = String.join(" ", annotations);
        
        // Get field description from @label annotation (YAML comment)
        String description = field.getAnnotationValue("label");
        if (description == null) {
            description = field.getAnnotationValue("desc");
        }
        info.description = description;
        
        // Add enum values description for enum types
        if (isEnumType) {
            String enumValuesDesc = getEnumValuesDescription(enumModel);
            if (enumValuesDesc != null) {
                if (info.description != null && !info.description.trim().isEmpty()) {
                    info.description += " " + enumValuesDesc;
                } else {
                    info.description = enumValuesDesc;
                }
            }
        }
        
        return info;
    }

    /**
     * Create schema information for an enum
     */
    private EnumSchemaInfo createEnumSchemaInfo(EnumModel enumModel, String domainName) {
        EnumSchemaInfo info = new EnumSchemaInfo();
        info.name = enumModel.getIdentity().getName();
        info.domainName = domainName;
        
        // Get meta annotations
        MetaInfo metaInfo = enumModel.getMetaInfo();
        List<String> metaAnnotations = new ArrayList<>();
        metaAnnotations.add("enum");
        
        if (metaInfo != null) {
            // Check for @db and @json annotations
            if (metaInfo.hasInfoAnnotation("db")) {
                String dbValue = metaInfo.getInfoAnnotationValue("db");
                metaAnnotations.add("@db(" + dbValue + ")");
            }
            if (metaInfo.hasInfoAnnotation("json")) {
                String jsonValue = metaInfo.getInfoAnnotationValue("json");
                metaAnnotations.add("@json(" + jsonValue + ")");
            }
            info.description = metaInfo.getLabel();
        }
        
        info.metaAnnotations = String.join(" ", metaAnnotations);
        
        // Process fields
        info.fields = new ArrayList<>();
        ModelComponentItems<Field> fieldItems = enumModel.getItems();
        for (int i = 0; i < fieldItems.size(); i++) {
            Field field = fieldItems.get(i);
            FieldSchemaInfo fieldInfo = createFieldSchemaInfo(field, null);
            info.fields.add(fieldInfo);
        }
        
        // Process enum constants
        info.enumValues = new ArrayList<>();
        try {
            List<EnumConstant> constants = enumModel.getEnumConstantItems().getItemList();
            for (EnumConstant constant : constants) {
                EnumValueInfo valueInfo = new EnumValueInfo();
                valueInfo.name = constant.getIdentity().getName();
                List<EnumConstant.ConstructParam> params = constant.getConstructParamList();
                
                if (params != null && !params.isEmpty()) {
                    // Get first parameter for code
                    EnumConstant.ConstructParam firstParam = params.get(0);
                    if (firstParam != null && firstParam.getName() != null) {
                        valueInfo.code = firstParam.getName();
                    } else {
                        // If first param is null, use the enum constant name as code
                        valueInfo.code = constant.getIdentity().getName();
                    }
                    
                    // Get second parameter for display name, or use first if only one param
                    if (params.size() >= 2) {
                        EnumConstant.ConstructParam secondParam = params.get(1);
                        if (secondParam != null && secondParam.getName() != null) {
                            valueInfo.displayName = secondParam.getName();
                        } else {
                            // If second param is null, use code as display name
                            valueInfo.displayName = valueInfo.code;
                        }
                    } else {
                        // If only one parameter, use it as both code and display name
                        valueInfo.displayName = valueInfo.code;
                    }
                } else {
                    // If no parameters, use enum constant name for both
                    valueInfo.code = constant.getIdentity().getName();
                    valueInfo.displayName = constant.getIdentity().getName();
                }
                
                info.enumValues.add(valueInfo);
            }
        } catch (Exception e) {
            ConsoleLog.printWarn("Failed to get enum constants for " + info.name + ": " + e.getMessage());
        }
        
        return info;
    }

    /**
     * Generate the final schema.yml file after all domains are processed
     */
    public void generateSchemaFile(String basePath) throws IOException {
        writeSchemaFile(new File(basePath + "/" + outputPath));
    }

    /**
     * Writes the schema beside the blueprint directory the model was actually loaded from.
     *
     * The basePath overload resolves against a project root, which the annotation
     * processor does not know: it is handed the model path, while the root falls back
     * to the JVM working directory - the Gradle root project during compilation. A
     * multi-module build therefore wrote one module's schema into the repository root.
     * The blueprint directory is known on both entry paths, so it is what the file
     * follows.
     */
    public void generateSchemaFileInBlueprintDir(String blueprintDirPath) throws IOException {
        writeSchemaFile(new File(blueprintDirPath, "schema.yml"));
    }

    private void writeSchemaFile(File outputFile) throws IOException {
        if (allEntities.isEmpty() && allEnums.isEmpty()) {
            ConsoleLog.printWarn("[SchemaPublisher] No entities or enums found to generate schema");
            return;
        }

        String fullPath = outputFile.getPath();
        outputFile.getParentFile().mkdirs();

        // UTF-8 explicitly: a platform-default writer stores the Korean labels the DSL
        // carries in the console codepage, which is not what reads them back.
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            writer.write("# Generated Schema File\n");
            writer.write("# Contains all entity models with database table and column information\n");
            writer.write("# Generated by ElastiCORE SchemaCodePublisher\n\n");
            
            // Write entities section
            if (!allEntities.isEmpty()) {
                writer.write("entities:\n\n");
                
                // Sort entities by name for consistent output
                allEntities.sort(Comparator.comparing(e -> e.name));
                
                for (EntitySchemaInfo entity : allEntities) {
                    writeEntityToSchema(writer, entity);
                    writer.write("\n");
                }
                writer.write("\n");
            }
            
            // Write enums section
            if (!allEnums.isEmpty()) {
                writer.write("enumeration:\n\n");
                
                // Sort enums by name for consistent output
                allEnums.sort(Comparator.comparing(e -> e.name));
                
                for (EnumSchemaInfo enumInfo : allEnums) {
                    writeEnumToSchema(writer, enumInfo);
                    writer.write("\n");
                }
            }
        }
        
        ConsoleLog.printInfo("[SchemaPublisher] Generated schema file: " + fullPath);
        ConsoleLog.printInfo("[SchemaPublisher] Total entities: " + allEntities.size());
        ConsoleLog.printInfo("[SchemaPublisher] Total enums: " + allEnums.size());
    }
    
    /**
     * Write a single entity to the schema file
     */
    private void writeEntityToSchema(Writer writer, EntitySchemaInfo entity) throws IOException {
        writer.write("  " + entity.name + ":\n");
        
        // Meta line with @table and @java annotations
        StringBuilder metaLine = new StringBuilder("    meta: " + entity.metaAnnotations);
        //metaLine.append(" @table(").append(entity.tableName).append(")");
        metaLine.append(" @java(").append(entity.javaClassName).append(")");
        if (entity.label != null && !entity.label.trim().isEmpty()) {
            metaLine.append(" -- ").append(entity.label);
        }
        writer.write(metaLine.toString() + "\n");

        String desc = entity.description;
        if(desc!=null && desc.trim().length()>0){
            writer.write("    description: |\n");
            String newDesc = StringUtils.addPrefixToEachLine(desc,"      ");
            writer.write(newDesc + "\n");
        }
        
        // Table name
        writer.write("    table: " + entity.tableName + "\n");
        
        // Java class name
        writer.write("    java: " + entity.javaClassName + "\n");
        
        // Fields
        writer.write("    fields:\n");
        for (FieldSchemaInfo field : entity.fields) {
            writeFieldToSchema(writer, field);
        }
    }
    
    /**
     * Write a single field to the schema file
     */
    private void writeFieldToSchema(Writer writer, FieldSchemaInfo field) throws IOException {
        StringBuilder fieldLine = new StringBuilder("      " + field.name + ": " + field.type);
        
        if (field.annotations != null && !field.annotations.trim().isEmpty()) {
            fieldLine.append(" ").append(field.annotations);
        }
        
        if (field.description != null && !field.description.trim().isEmpty()) {
            fieldLine.append(" -- ").append(field.description);
        }
        
        writer.write(fieldLine.toString() + "\n");
    }
    
    /**
     * Check if a field type is enumeration
     */
    private EnumModel findEnumModel(String typeName) {
        if (ctx == null) return null;
        return ctx.findModelComponent(typeName, EnumModel.class);
    }
    
    /**
     * Check if type is primitive or enum
     */
    public boolean isPrimitiveOrEnumType(String typeName, boolean isEnum) {
        if (isEnum) return true;
        
        // Check if it's a primitive type
        String lowerType = typeName.toLowerCase();
        return lowerType.contains("string") || 
               lowerType.contains("int") || 
               lowerType.contains("long") || 
               lowerType.contains("double") || 
               lowerType.contains("float") || 
               lowerType.contains("boolean") ||
               lowerType.contains("date") ||
               lowerType.equals("integer") ||
               lowerType.equals("bigdecimal");
    }
    
    /**
     * Map Java types to schema types
     */
    public String mapJavaTypeToSchemaType(String javaType, Integer length) {
        String lowerType = javaType.toLowerCase();
        
        if (lowerType.contains("string")) {
            return length != null && length > 0 ? "string(" + length + ")" : "string";
        } else if (lowerType.equals("long") || lowerType.equals("java.lang.long")) {
            return "Long";
        } else if (lowerType.equals("int") || lowerType.equals("integer") || lowerType.equals("java.lang.integer")) {
            return "Integer";
        } else if (lowerType.contains("double")) {
            return "Double";
        } else if (lowerType.contains("float")) {
            return "Float";
        } else if (lowerType.contains("boolean")) {
            return "Boolean";
        } else if (lowerType.contains("date")) {
            return "Date";
        } else if (lowerType.contains("bigdecimal")) {
            return "BigDecimal";
        }
        
        return javaType; // Return original if no mapping found
    }
    
    /**
     * Get enum code field type
     */
    private String getEnumCodeType(EnumModel enumModel) {
        // Find the 'code' field in enum definition
        ModelComponentItems<Field> fields = enumModel.getItems();
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            if ("code".equals(field.getName())) {
                String codeType = field.getTypeInfo().getDefaultTypeName();
                Integer length = field.getLength();
                return mapJavaTypeToSchemaType(codeType, length);
            }
        }
        return "string(2)"; // Default fallback
    }
    
    /**
     * Get enum code field length
     */
    private Integer getEnumCodeLength(EnumModel enumModel) {
        ModelComponentItems<Field> fields = enumModel.getItems();
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            if ("code".equals(field.getName())) {
                return field.getLength();
            }
        }
        return 2; // Default fallback
    }
    
    /**
     * Get enum values description
     */
    private String getEnumValuesDescription(EnumModel enumModel) {
        try {
            List<EnumConstant> constants = enumModel.getEnumConstantItems().getItemList();
            if (constants.isEmpty()) {
                return null;
            }
            
            StringBuilder description = new StringBuilder("\ud6c4\ubcf4\uac12(");
            for (int i = 0; i < constants.size(); i++) {
                EnumConstant constant = constants.get(i);
                List<EnumConstant.ConstructParam> params = constant.getConstructParamList();
                
                if (params.size() >= 2) {
                    String code = params.get(0).getName();    // PB
                    String display = params.get(1).getName(); // display name
                    description.append(" ").append(code).append("=").append(display);
                    
                    if (i < constants.size() - 1) {
                        description.append(",");
                    }
                }
            }
            description.append(")");
            
            return description.toString();
        } catch (Exception e) {
            ConsoleLog.printWarn("Failed to get enum values description: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if entity is abstract
     */
    private boolean isAbstractEntity(Entity entity) {
        MetaInfo metaInfo = entity.getMetaInfo();
        return metaInfo != null && metaInfo.hasMetaAnnotation("abstract");
    }
    
    /**
     * Get all fields including inherited ones from parent entities
     */
    private Map<String, Field> getAllInheritedFields(Entity entity) {
        Map<String, Field> allFields = new LinkedHashMap<>();
        
        // First, collect fields from parent entities (inheritance chain)
        collectParentFields(entity, allFields);
        
        // Then, add this entity's own fields (override parent fields if same name)
        ModelComponentItems<Field> fieldItems = entity.getItems();
        for (int i = 0; i < fieldItems.size(); i++) {
            Field field = fieldItems.get(i);
            // The id column of a reference by identity is the relation's own
            // column, which the relation already describes.
            if (field.hasAnnotation("idof")) continue;
            allFields.put(field.getName(), field);
        }

        return allFields;
    }
    
    /**
     * Recursively collect fields from parent entities
     */
    private void collectParentFields(Entity entity, Map<String, Field> allFields) {
        MetaInfo metaInfo = entity.getMetaInfo();
        if (metaInfo != null && metaInfo.hasMetaAnnotation("extend")) {
            String parentName = metaInfo.getMetaAnnotationValue("extend");
            if (parentName != null && ctx != null) {
                Entity parentEntity = ctx.findModelComponent(parentName, Entity.class);
                if (parentEntity != null) {
                    // Recursively collect parent's fields first
                    collectParentFields(parentEntity, allFields);
                    
                    // Add parent's own fields
                    ModelComponentItems<Field> parentFieldItems = parentEntity.getItems();
                    for (int i = 0; i < parentFieldItems.size(); i++) {
                        Field field = parentFieldItems.get(i);
                        allFields.put(field.getName(), field);
                    }
                }
            }
        }
    }
    
    /** The entity of the model a field refers to, by its type or its list's item type; null for a value type. */
    private Entity findReferencedEntity(Field field) {
        if (ctx == null || field.getTypeInfo() == null) return null;
        String type = field.getTypeInfo().getCoreItemType();
        if (type == null || type.isEmpty()) return null;
        return ctx.findModelComponent(type, Entity.class);
    }

    private static boolean isEmbeddableEntity(Entity entity) {
        MetaInfo meta = entity.getMetaInfo();
        return meta != null && meta.hasMetaAnnotation("embeddable");
    }

    /**
     * A to-one reference. It owns a column of this table, the one the entity's
     * {@code @JoinColumn} names, which {@code @foreignKey} gives. It has no other
     * side to name with {@code mappedBy} - except a one-to-one written
     * {@code @OneToOne(mappedBy=...)}, which is that other side and has no column.
     */
    private String generateReferenceAnnotation(Field field, Entity entity, Entity target) {
        Annotation oneToOne = field.getAnnotation("onetoone");
        String mappedBy = oneToOne == null || oneToOne.getProperties() == null ? null
                : oneToOne.getProperties().getProperty("mappedBy");
        String targetName = field.getTypeInfo().getDefaultTypeName();
        if (mappedBy != null && !mappedBy.isEmpty()) {
            return "@relation(mappedBy=\"" + mappedBy + "\") -- " + targetName + " entity relationship";
        }
        return "@foreignKey(" + joinColumnName(field, entity, target) + ") -- " + targetName + " entity relationship";
    }

    /**
     * The column the generated entity joins on, by the rules its {@code @JoinColumn} follows:
     * the column of the field {@code @joinBy} names (a reference by id, with any
     * {@code referenceColumn} setting), else {@code @column}, else JPA's default -
     * the field's name and the target key's column: {@code customer_customer_id}.
     */
    private String joinColumnName(Field field, Entity entity, Entity target) {
        // A native @jpa:JoinColumn(name=...) is the one the entity carries.
        Annotation nativeJoin = field.getAnnotation("jpa:joincolumn");
        if (nativeJoin != null && nativeJoin.getProperties() != null) {
            String name = nativeJoin.getProperties().getProperty("name");
            if (name != null && !name.trim().isEmpty()) return name.trim().replace("\"", "");
        }
        String joinBy = field.getAnnotationValue("joinby", "joinfield");
        if (joinBy != null && !joinBy.isEmpty()) {
            Field idField = (Field) entity.getAllFieldListMap().get(joinBy);
            if (idField != null) {
                String column = idField.getDbColumnName();
                if (column != null && !column.isEmpty() && !"null".equals(column)) return column;
            }
        }
        String column = field.getAnnotationValue("column", "col", "db");
        if (column != null && !column.isEmpty()) return column;

        Field pk = findPrimaryKey(target);
        if (pk == null) return physicalName(field.getName() + "_id");
        String pkColumn = pk.getAnnotationValue("column", "col", "db");
        if (pkColumn == null || pkColumn.isEmpty()) pkColumn = pk.getName();
        return physicalName(field.getName() + "_" + pkColumn);
    }

    /**
     * A collection of entities has no column in this table. With
     * {@code @OneToMany(mappedBy=...)} the other side's field holds the key, and
     * {@code mappedBy} names that field.
     */
    private static String generateCollectionAnnotation(Field field, String targetType) {
        String mappedBy = null;
        for (String kind : new String[] {"onetomany", "manytomany"}) {
            Annotation a = field.getAnnotation(kind);
            if (a != null && a.getProperties() != null && a.getProperties().getProperty("mappedBy") != null) {
                mappedBy = a.getProperties().getProperty("mappedBy").trim().replace("\"", "");
            }
        }
        String comment = "-- " + targetType + " entity relationship";
        return mappedBy == null || mappedBy.isEmpty() ? comment : "@relation(mappedBy=\"" + mappedBy + "\") " + comment;
    }

    /**
     * The physical name Spring Boot's naming strategy gives a logical one: an
     * underscore between a lower-case letter and an upper-case one followed by a
     * lower-case one, then all lower case - what the DDL of the entity holds.
     */
    private static String physicalName(String name) {
        StringBuilder sb = new StringBuilder(name.replace('.', '_'));
        for (int i = 1; i < sb.length() - 1; i++) {
            if (Character.isLowerCase(sb.charAt(i - 1)) && Character.isUpperCase(sb.charAt(i))
                    && Character.isLowerCase(sb.charAt(i + 1))) {
                sb.insert(i++, '_');
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private Field findPrimaryKey(Entity entity) {
        String name = findPrimaryKeyField(entity);
        if (name == null) return null;
        Map<String, Field> fields = getAllInheritedFields(entity);
        Field pk = fields.get(name);
        return pk != null ? pk : (Field) entity.getAllFieldListMap().get(name);
    }

    /**
     * Find primary key field name in entity
     */
    private String findPrimaryKeyField(Entity entity) {
        // Check entity's own fields first
        ModelComponentItems<Field> fieldItems = entity.getItems();
        for (int i = 0; i < fieldItems.size(); i++) {
            Field field = fieldItems.get(i);
            if (field.isPrimaryKey()) {
                return field.getName();
            }
        }
        
        // Check inherited fields if not found
        Map<String, Field> allFields = getAllInheritedFields(entity);
        for (Map.Entry<String, Field> entry : allFields.entrySet()) {
            Field field = entry.getValue();
            if (field.isPrimaryKey()) {
                return field.getName();
            }
        }
        
        return null; // No primary key found
    }
    
    /**
     * Convert camelCase to snake_case
     */
    private String camelToSnakeCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append("_");
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
    
    /**
     * Write a single enum to the schema file
     */
    private void writeEnumToSchema(Writer writer, EnumSchemaInfo enumInfo) throws IOException {
        writer.write("  " + enumInfo.name + ":\n");
        
        // Meta line
        StringBuilder metaLine = new StringBuilder("    meta: " + enumInfo.metaAnnotations);
        if (enumInfo.description != null && !enumInfo.description.trim().isEmpty()) {
            metaLine.append(" -- ").append(enumInfo.description);
        }
        writer.write(metaLine.toString() + "\n");
        
        // Fields
        writer.write("    fields:\n");
        for (FieldSchemaInfo field : enumInfo.fields) {
            writeFieldToSchema(writer, field);
        }
        
        // Enum values
        writer.write("    enum:\n");
        for (EnumValueInfo enumValue : enumInfo.enumValues) {
            writer.write("      " + enumValue.name + ": " + enumValue.code + "," + enumValue.displayName + "\n");
        }
    }
    
    /**
     * Reset collected entities for new generation
     */
    public void reset() {
        allEntities.clear();
        allEnums.clear();
    }
    
    /**
     * Entity schema information holder
     */
    private static class EntitySchemaInfo {
        String name;
        String domainName;
        String tableName;
        String javaClassName;
        String metaAnnotations;
        String label;
        String description;
        List<FieldSchemaInfo> fields;
    }
    
    /**
     * Field schema information holder
     */
    private static class FieldSchemaInfo {
        String name;
        String type;
        String columnName;
        String annotations;
        String description;
    }
    
    /**
     * Enum schema information holder
     */
    private static class EnumSchemaInfo {
        String name;
        String domainName;
        String metaAnnotations;
        String description;
        List<FieldSchemaInfo> fields;
        List<EnumValueInfo> enumValues;
    }
    
    /**
     * Enum value information holder
     */
    private static class EnumValueInfo {
        String name;
        String code;
        String displayName;
    }
}