package io.elasticore.base.model.pub.jpa;

import io.elasticore.base.CodePublisher;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.model.*;
import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.core.ListMap;
import io.elasticore.base.model.core.RelationshipManager;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.dto.DataTransferAnnotation;
import io.elasticore.base.model.entity.*;
import io.elasticore.base.model.shadow.SourceShadowModel;
import io.elasticore.base.util.CodeTemplate;
import io.elasticore.base.util.StringUtils;

import java.util.*;
import java.util.function.Predicate;

/**
 * Handles the publishing of source files for entities.
 * This class is responsible for generating Java source code
 * based on entity definitions and their annotations.
 */
public class DtoSrcFilePublisher extends SrcFilePublisher {

    private CodeTemplate javaClassTmpl;

    private CodePublisher publisher;

    private String packageName;

    private String enumPackageName;

    // the generation mode from env.yml (config.mode)
    private String publishMode;

    private CodeTemplate.Paragraph paragraphForEntity;

    private ECoreModel eCoreModel;


    /**
     * Initializes a new instance of the EntitySrcFilePublisher class.
     * Sets up the template path and packages based on the provided publisher.
     *
     * @param publisher The JPACodePublisher instance used for publishing.
     */
    public DtoSrcFilePublisher(CodePublisher publisher) {
        super(publisher);

        this.publisher = publisher;
        this.eCoreModel = this.publisher.getECoreModelContext().getDomain().getModel();

        this.publishMode = this.eCoreModel.getConfig("mode");

        String templatePath = this.publisher.getECoreModelContext().getDomain().getModel().getConfig("template.dto");
        if (templatePath == null)
            templatePath = "elasticore-template/dto.tmpl";
        else
            templatePath = "resource://"+templatePath;

        this.javaClassTmpl = CodeTemplate.newInstance(templatePath);

        ECoreModel model = publisher.getECoreModelContext().getDomain().getModel();
        this.packageName = model.getNamespace(ConstantParam.KEYNAME_DTO);

        if(model.getEnumModels().getItems().size()>0)
            this.enumPackageName = model.getNamespace(ConstantParam.KEYNAME_ENUMERATION);
        else
            this.enumPackageName = null;

    }

    /**
     * Retrieves the extension information for an entity.
     * This method checks if the entity has an "extend" meta-annotation and
     * returns the extension clause (e.g., "extends BaseClass") for the entity.
     * If the "extend" meta-annotation is not present, an empty string is returned.
     *
     * @param entity The entity for which to retrieve the extension information.
     * @return A string containing the extension clause, if applicable; otherwise, an empty string.
     */
    private String getExtendInfo(MetaInfoModel entity) {

        // Check if the entity has a "dto" meta-annotation, which means it's an
        // entity DTO and should not be extended from any other class.
        if(entity.getMetaInfo().hasMetaAnnotation(DataTransferAnnotation.META_DTO))
            return "";

        Annotation annotation = entity.getMetaInfo().getMetaAnnotation(DataTransferAnnotation.META_EXTEND);
        if (annotation != null)
            return "extends " + annotation.getValue();

        return "";
    }


    private String getAbstractInfo(MetaInfoModel entity) {
        if (entity.getMetaInfo().hasMetaAnnotation(DataTransferAnnotation.META_ABSTRACT)) {
            return "abstract";
        }
        return "";
    }


    /**
     * Generates and publishes Java source code for the specified entity.
     * This method orchestrates the creation of entity classes based on
     * entity definition and annotations.
     *
     * @param domain The model domain to which the entity belongs.
     * @param dto The entity for which source code is to be published.
     */
    public void publish(ModelDomain domain, DataTransfer dto) {
        if (this.packageName == null) return;

        MetaInfo metaInfo = dto.getMetaInfo();
        Annotation typeAnnotation = metaInfo.getMetaAnnotation("type");
        if (typeAnnotation != null) {
            if ("template".equals(typeAnnotation.getValue()))
                return;
        }

        if(metaInfo.hasMetaAnnotation(DataTransferAnnotation.META_PROTO)){
            return;
        }

        String implementInfo = metaInfo.getMetaAnnotationValue(DataTransferAnnotation.META_IMPLEMENTS);
        if(implementInfo!=null && implementInfo.length()>0)
            implementInfo = ","+implementInfo;
        else
            implementInfo="";


        if(metaInfo.hasMetaAnnotation(DataTransferAnnotation.META_SEARCHABLE)) return;


        CodeTemplate.Parameters p = CodeTemplate.newParameters();
        String classNm = dto.getIdentity().getName();


        this.paragraphForEntity = CodeTemplate.newParagraph();

        setNativeAnnotation(dto, this.paragraphForEntity);
        setDtoAnnotation(dto, this.paragraphForEntity);

        boolean isSearchDTO = false;


        // must call 'getFieldInfo' first
        CodeTemplate.Paragraph pr = getFieldInfo(dto);


        CodeTemplate.Paragraph importList = CodeTemplate.newParagraph();

        ShadowModel shadowModel = domain.getModel().getShadowModel(classNm);
        Set<String> namespaceSet = shadowModel.getNamespaceSet();

        for(String ns: namespaceSet) {
            importList.add(ns);
        }

        // Add JsonView import when jsonview mode is enabled
        String jsonViewMode = resolveJsonViewMode(dto);
        if (!"false".equals(jsonViewMode)) {
            importList.add("com.fasterxml.jackson.annotation.JsonView");
        }

        if(!metaInfo.hasMetaAnnotation("includenull")) {
            importList.add("com.fasterxml.jackson.annotation.JsonInclude");
            importList.add("com.fasterxml.jackson.annotation.JsonInclude.Include");
            this.paragraphForEntity.add("@JsonInclude(Include.NON_NULL)");
        }

        p
                .set("packageName", packageName)
                .set("j2eePkgName",getPersistentPackageName(domain))
                .set("importList", importList)
                .set("enumPackageName", enumPackageName)
                .set("abstract", getAbstractInfo(dto))
                .set("classAnnotationList", this.paragraphForEntity)
                .set("extendInfo", getExtendInfo(dto))
                .set("isSearchDTO", isSearchDTO)
                .set("implementInfo", "implements java.io.Serializable"+implementInfo)
                .set("className", classNm);


        p.set("fieldList", pr);

        String qualifiedClassName = packageName + "." + classNm;
        String code = javaClassTmpl.toString(p);

        this.writeSrcCode(this.publisher, dto, qualifiedClassName, code);

        String templateName = io.elasticore.base.model.dto.TemplateList.namesOf(dto.getMeta());
        if(templateName!=null && !templateName.isEmpty() && templateName.indexOf(",")<0) {

            // generate DTO which has key fields
            CodeTemplate.Paragraph pkFields = getPkFieldInfo(dto);
            if(pkFields.size()>0) {
                classNm = templateName + "Key" + "DTO";
                qualifiedClassName = packageName + "." + classNm;
                p.set("fieldList", pkFields);
                p.set("className", classNm);

                String pkDtoCode = javaClassTmpl.toString(p);

                this.writeSrcCode(this.publisher, dto, qualifiedClassName, pkDtoCode);
            }
        }



    }


    private CodeTemplate.Paragraph getMultiplePkField(Entity entity) {
        ModelComponentItems<Field> fields = entity.getItems();
        CodeTemplate.Paragraph p = CodeTemplate.newParagraph();
        while (fields.hasNext()) {
            Field f = fields.next();

            if (!f.isPrimaryKey())
                continue;
            ;

            setFieldDesc(f, p);
            //setFieldPkInfo(f, p);


            /*
            BaseFieldType ft = f.getTypeInfo().getBaseFieldType();
            if (ft == BaseFieldType.DATETIME)
                p.add("@Temporal(TemporalType.TIMESTAMP)");
            else if (ft == BaseFieldType.DATE)
                p.add("@Temporal(TemporalType.DATE)");
            else if (ft == BaseFieldType.TIME)
                p.add("@Temporal(TemporalType.TIME)");

             */

            String code = String.format("%s %s %s;", "private", f.getTypeInfo().getDefaultTypeName(), f.getName());
            p.add(code);
            p.add("");
        }
        return p;
    }

    private CodeTemplate.Paragraph getFieldInfo(DataModelComponent dto) {
        CodeTemplate.Paragraph p = CodeTemplate.newParagraph();

        SourceShadowModel shadowModel = new SourceShadowModel(dto.getIdentity().getName());
        loadFieldInfo(dto, p,null, shadowModel);
        this.eCoreModel.setShadowModel(shadowModel);

        return p;

    }

    private CodeTemplate.Paragraph getPkFieldInfo(DataModelComponent dto) {
        CodeTemplate.Paragraph p = CodeTemplate.newParagraph();

        Predicate<Field> condition = f-> f.isPrimaryKey();
        loadFieldInfo(dto, p,condition,null);

        return p;
    }



    private void loadFieldInfo(DataModelComponent dto, CodeTemplate.Paragraph p, Predicate<Field> condition, SourceShadowModel shadowModel ) {

        // if the dto is a convert from entity, we do not need to generate any field annotation
        boolean isConvertFromEntity = dto.getMetaInfo().hasMetaAnnotation(EntityAnnotation.META_DTO);

        ModelComponentItems<Field> items = dto.getItems();

        int fieldCount = items.size();
        for(int i=0;i<fieldCount;i++) {
        //while(items.hasNext()) {
            Field f = items.get(i);
            processDeferredField((DataTransfer) dto, f);
        }

        ListMap<String, Field> fields = dto.getAllFieldListMap();

        List<Field> fieldList = fields.getList();


        RelationshipManager relMgr = RelationshipManager.getInstance(dto.getIdentity().getDomainId());

        String parentName = dto.getMetaInfo().getMetaAnnotationValue(EntityAnnotation.META_EXTEND);
        if(parentName!=null && !parentName.isEmpty()) {

            shadowModel.setNamespaceInfo(parentName);

            fieldList = new ArrayList<>();
            ModelComponentItems<Field> items1 = dto.getItems();
            while (items1.hasNext()) {
                Field next = items1.next();
                fieldList.add(next);
            }
        }


        for(Field f: fieldList) {

            if(condition!=null && !condition.test(f))
                continue;

            if(this.isDisableField(f))
                continue;


            String typeName = f.getAnnotationValue(EntityAnnotation.DTO_TYPE);
            boolean hasDtoType = true;
            if(!StringUtils.hasValue(typeName)) {
                typeName = f.getTypeInfo().getDefaultTypeName();
                hasDtoType = false;
            }
            else {

            }



            if(!hasDtoType && ! f.getTypeInfo().isBaseType() && typeName.indexOf(".")<0) {

                String checkTypeName = typeName;

                if(f.getTypeInfo().isList()) {
                    String coreType = f.getTypeInfo().getCoreItemType();
                    checkTypeName = coreType;

                    /***********************
                    if(this.findEntityByName(checkTypeName) !=null) {
                        String dtoName = checkTypeName+ComponentType.DTO.getSuffix();
                        if(this.findDTO(dtoName)!=null) {
                            checkTypeName = dtoName;
                            typeName = "List<"+checkTypeName+">";
                        }
                    }
                     *********************/
                }else {
                }

                boolean hasRef = f.hasAnnotation(DataTransferAnnotation.REFERENCE);

                if(!hasRef && !this.isEnableInDTO(this.eCoreModel, dto.getMetaInfo(), f, checkTypeName ,dto.getIdentity())) {
                    // disable check
                    //f.getAnnotationMap().put("disable", Annotation.create("disable"));
                    continue;
                }

            }

            boolean isEntityReturnType = isEntityReturnType(f, eCoreModel);

            if(!hasDtoType && isEntityReturnType) {
                if(!isConvertFromEntity)
                    continue;

                /***********************
                try {
                    List<ModelRelationship> byFromNameAndType = relMgr.findByFromNameAndType(typeName, RelationType.TEMPLATE_TO);
                    if (byFromNameAndType != null && byFromNameAndType.size() == 1) {
                        typeName = byFromNameAndType.get(0).getToName();
                    } else {
                        continue;
                    }
                }catch (Throwable e){
                    e.printStackTrace();
                }
                 *************************/


            }


            setFieldDesc(f, p);
            setFieldDocumentation(f,p);

            // Auto-map Entity column size to DTO @Size validation when using @template
            applyEntitySizeToDTO(dto, f);

            // Add @Valid for nested DTO fields
            applyNestedValidation(f, p, shadowModel);

            setFieldValidation(f,p);
            if(setJsonInfo(f, p) && shadowModel!=null) {
                shadowModel.setImportPackageName("com.fasterxml.jackson.annotation.*");
            }
            // Generate @JsonView annotation for JsonView-enabled DTOs
            if(setJsonViewInfo(f, p, dto) && shadowModel!=null) {
                shadowModel.setImportPackageName("com.fasterxml.jackson.annotation.JsonView");
            }
            setFormatAnnotation(f,p);




            setNativeAnnotation(f, p);

            String defaultValDefined = getDefaultValueSetup(f);
            if(defaultValDefined!=null && !defaultValDefined.isEmpty()) {
                p.add("@Builder.Default");
            }

            /*
            BaseFieldType ft = f.getTypeInfo().getBaseFieldType();
            if (ft == BaseFieldType.DATETIME)
                p.add("@Temporal(TemporalType.TIMESTAMP)");
            else if (ft == BaseFieldType.DATE)
                p.add("@Temporal(TemporalType.DATE)");
            else if (ft == BaseFieldType.TIME)
                p.add("@Temporal(TemporalType.TIME)");

             */

            String printFieldType = typeName;
            if(f.hasAnnotation(DataTransferAnnotation.PRIMITIVE)) {
                printFieldType = f.getTypeInfo().getInitTypeInfo();
            }


            p.add("%s %s %s%s;", "private", printFieldType, f.getName(), defaultValDefined);
            p.add("");

            setFunctionInfo(f, p);


            if(shadowModel!=null) {
                if(hasDtoType) {
                    f = Field.builder()
                            .name(f.getName())
                            .type(typeName)
                            .annotationMap(f.getAnnotationMap())
                            .description(f.getDescription())
                            .build();
                }
                shadowModel.addField(f);
            }
        }


    }



    private String getDefaultValueSetup(Field f) {
        if (!f.hasAnnotation("default")) return "";
        String val = f.getAnnotation("default").getValue();

        BaseFieldType fieldType = f.getTypeInfo().getBaseFieldType();

        // Check if field type is Enumeration
        String typeName = f.getTypeInfo().getDefaultTypeName();
        io.elasticore.base.model.enums.EnumModel enumModel = this.publisher.getECoreModelContext()
                .findModelComponent(typeName, io.elasticore.base.model.enums.EnumModel.class);
        if (enumModel != null) {
            // Enumeration type
            if (!val.contains(".")) {
                // Add type prefix: Y -> Indicator.Y
                return " = " + typeName + "." + val;
            }
            // Already has dot notation: Type.CUSTOM -> Type.CUSTOM
            return " = " + val;
        }

        // Handle date/time types - ignore "now" default value in DTOs
        // DTOs should not have default values for date fields, they should be null-able
        if (fieldType == BaseFieldType.DATE || fieldType == BaseFieldType.DATETIME || fieldType == BaseFieldType.TIME) {
            if ("now".equalsIgnoreCase(val)) {
                return ""; // Ignore default value, allow null
            }
            // For other date values, also ignore in DTOs as they should be transferred as-is
            return "";
        }

        if (f.getTypeInfo().isStringType()) {
            // Smart quote handling to prevent double quotes
            if (val.startsWith("\"") && val.endsWith("\"")) {
                // Already properly quoted: @default("COMPLETED") -> "COMPLETED"
                return " = " + val;
            }
            if (val.startsWith("'") && val.endsWith("'")) {
                // Convert single quotes to double quotes: @default('COMPLETED') -> "COMPLETED"
                return " = \"" + val.substring(1, val.length()-1) + "\"";
            }
            // Add quotes for unquoted values: @default(COMPLETED) -> "COMPLETED"
            return " = \"" + val + "\"";
        } else if (f.getTypeInfo().isBigDecimal()) {
            // BigDecimal default value handling
            if ("0".equals(val) || "0.0".equals(val) || "0.00".equals(val)) {
                return " = java.math.BigDecimal.ZERO";
            } else if ("1".equals(val) || "1.0".equals(val) || "1.00".equals(val)) {
                return " = java.math.BigDecimal.ONE";
            } else if ("10".equals(val) || "10.0".equals(val) || "10.00".equals(val)) {
                return " = java.math.BigDecimal.TEN";
            } else {
                return " = new java.math.BigDecimal(\"" + val + "\")";
            }
        } else if (f.getTypeInfo().isBigInteger()) {
            // BigDecimal default value handling
            if ("0".equals(val) || "0.0".equals(val) || "0.00".equals(val)) {
                return " = java.math.BigInteger.ZERO";
            } else if ("1".equals(val) || "1.0".equals(val) || "1.00".equals(val)) {
                return " = java.math.BigInteger.ONE";
            } else if ("10".equals(val) || "10.0".equals(val) || "10.00".equals(val)) {
                return " = java.math.BigInteger.TEN";
            } else {
                return " = new java.math.BigDecimal(\"" + val + "\")";
            }
        } else if (fieldType == BaseFieldType.P_LONG || fieldType == BaseFieldType.LONG) {
            // Check if user already added L postfix
            if (!val.toUpperCase().endsWith("L")) {
                val = val + "L";
            }
            return " = " + val;
        } else if (fieldType == BaseFieldType.P_FLOAT || fieldType == BaseFieldType.FLOAT) {
            // Check if user already added F postfix
            if (!val.toUpperCase().endsWith("F")) {
                val = val + "F";
            }
            return " = " + val;
        } else if (fieldType == BaseFieldType.P_DOUBLE || fieldType == BaseFieldType.DOUBLE) {
            // Check if user already added D postfix (D is optional for double)
            if (!val.toUpperCase().endsWith("D") && !val.contains(".")) {
                val = val + ".0"; // Add decimal point for integer values
            }
            return " = " + val;
        } else {
            return " = " + val;
        }
    }



    private void setFieldPkInfo(Field field, CodeTemplate.Paragraph paragraph) {

        if (field.hasAnnotation("id") || field.hasAnnotation("pk") || field.isPrimaryKey()) {
            paragraph.add("@Id");

            if (field.hasAnnotation("sequence")) {
                paragraph.add("@GeneratedValue(strategy = GenerationType.IDENTITY)");
            }
        }


        if (field.getGenerationType() != null) {
            paragraph.add("@GeneratedValue(strategy = GenerationType." + field.getGenerationType());
        }
    }



    private void setDtoAnnotation(MetaInfoModel entity, CodeTemplate.Paragraph paragraph) {

        String val = entity.getMetaInfo().getMetaAnnotationValue( DataTransferAnnotation.META_JSON_INCLUDE);
        if(val!=null) {
            paragraph.add("@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.%s)", val.toUpperCase());
        }
    }


    private void setNativeAnnotation(MetaInfoModel entity, CodeTemplate.Paragraph paragraph) {
        setNativeAnnotation(entity.getMetaInfo().getMetaAnnotationMap(), paragraph);
    }

    private void setNativeAnnotation(Field field, CodeTemplate.Paragraph paragraph) {
        setNativeAnnotation(field.getAnnotationMap(), paragraph);
    }

    private void setNativeAnnotation(Map<String, Annotation> annotationMap, CodeTemplate.Paragraph paragraph) {

        if (annotationMap == null)
            return;

        if (this.publishMode == null) return;

        String prefix = "dto:";
        annotationMap.entrySet().stream()
                .filter(entry -> entry.getKey().contains(prefix))
                .filter(entry -> !entry.getKey().contains("jpa:")) // Exclude JPA annotations
                .map(Map.Entry::getValue)
                .forEach(paragraph::add);


    }


    private void setSqlDeleteAnnotation(Field field, CodeTemplate.Paragraph paragraph) {
        if (field.hasAnnotation("chkdelete")) {

        }
    }

    /**
     * Auto-map Entity column size to DTO @Size validation when using @template annotation.
     * If the DTO uses @template(EntityName), this method retrieves the Entity field
     * and applies the column length as @Size(max=X) validation to the DTO field.
     *
     * @param dto DTO component being processed
     * @param dtoField DTO field to apply size validation
     */
    private void applyEntitySizeToDTO(DataModelComponent dto, Field dtoField) {
        // Check if DTO uses @template annotation
        if (!dto.getMetaInfo().hasMetaAnnotation(DataTransferAnnotation.META_TEMPLATE)) {
            return;
        }

        // Skip if field already has explicit @size annotation
        if (dtoField.hasAnnotation("size") || dtoField.hasAnnotation("length")) {
            return;
        }

        // Get Entity name from @template annotation
        String entityName = io.elasticore.base.model.dto.TemplateList.namesOf(dto.getMetaInfo());
        if (entityName == null || entityName.isEmpty()) {
            return;
        }

        // Find the Entity by name
        try {
            Entity entity = this.publisher.getECoreModelContext().findModelComponent(entityName, Entity.class);
            if (entity == null) {
                return;
            }

            // Find matching field in Entity
            String dtoFieldName = dtoField.getName();
            ListMap<String, Field> entityFields = entity.getAllFieldListMap();
            Field entityField = null;

            for (Field ef : entityFields.getList()) {
                if (ef.getName().equals(dtoFieldName)) {
                    entityField = ef;
                    break;
                }
            }

            if (entityField == null) {
                return;
            }

            // Check if Entity field is string type with length
            String entityFieldType = entityField.getTypeInfo().getDefaultTypeName();
            if (entityFieldType == null || !entityFieldType.equals("String")) {
                return;
            }

            // Extract length from field
            int entityFieldLength = entityField.getLength();
            if (entityFieldLength > 0) {
                // Add @length annotation to DTO field so setFieldValidation will generate @Size
                // Use the same format as the existing length annotation processing
                String lengthAnnotation = String.format("max=%d", entityFieldLength);
                dtoField.getAnnotationMap().put("length",
                    Annotation.create("length", lengthAnnotation));
            }
        } catch (Exception e) {
            // Silently ignore if entity cannot be found
            return;
        }
    }

    /**
     * Add @Valid annotation for nested DTO fields to enable cascading validation.
     * This method checks if a field's type is another DTO and automatically adds
     * @Valid annotation for nested object validation.
     *
     * @param field Field to check for nested DTO validation
     * @param paragraph Paragraph to add @Valid annotation
     * @param shadowModel Shadow model for tracking imports
     */
    private void applyNestedValidation(Field field, CodeTemplate.Paragraph paragraph, SourceShadowModel shadowModel) {
        String fieldType = field.getTypeInfo().getDefaultTypeName();

        // Check if field type ends with "DTO" suffix or is a defined DTO type
        /*
        if (fieldType.endsWith("DTO") || isDefinedDtoType(fieldType)) {
            String persistentPackageName = getPersistentPackageName(null);
            paragraph.add("@" + persistentPackageName + ".validation.Valid");
            if (shadowModel != null) {
                shadowModel.setImportPackageName(persistentPackageName + ".validation.Valid");
            }
        }
        */
    }

    /**
     * Check if the given type is a defined DTO in the model context.
     *
     * @param typeName Type name to check
     * @return true if type is a defined DTO
     */
    private boolean isDefinedDtoType(String typeName) {
        try {
            DataModelComponent component = this.publisher.getECoreModelContext()
                .findModelComponent(typeName, DataTransfer.class);
            return component != null;
        } catch (Exception e) {
            return false;
        }
    }


}
