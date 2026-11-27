package io.elasticore.base.model.pub.jpa;

import io.elasticore.base.CodePublisher;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.model.*;
import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.core.ListMap;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.dto.DataTransferAnnotation;
import io.elasticore.base.model.entity.*;
import io.elasticore.base.model.enums.EnumModel;
import io.elasticore.base.model.shadow.SourceShadowModel;
import io.elasticore.base.util.CodeTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.elasticore.base.util.StringUtils.splitConditionText;

/**
 * Handles the publishing of source files for entities.
 * This class is responsible for generating Java source code
 * based on entity definitions and their annotations.
 */
public class SearchDtoSrcFilePublisher extends SrcFilePublisher {

    private CodeTemplate javaClassTmpl;

    private CodePublisher publisher;

    private String packageName;

    private String enumPackageName;

    // the generation mode from env.yml (config.mode)
    private String publishMode;

    private CodeTemplate.Paragraph paragraphForEntity;


    /**
     * Initializes a new instance of the EntitySrcFilePublisher class.
     * Sets up the template path and packages based on the provided publisher.
     *
     * @param publisher The JPACodePublisher instance used for publishing.
     */
    public SearchDtoSrcFilePublisher(CodePublisher publisher) {
        super(publisher);

        this.publisher = publisher;

        this.publishMode = this.publisher.getECoreModelContext().getDomain().getModel().getConfig("mode");

        String templatePath = this.publisher.getECoreModelContext().getDomain().getModel().getConfig("template.dto");
        if (templatePath == null)
            templatePath = "elasticore-template/dto.tmpl";
        else
            templatePath = "resource://" + templatePath;

        this.javaClassTmpl = CodeTemplate.newInstance(templatePath);

        ECoreModel model = publisher.getECoreModelContext().getDomain().getModel();
        this.packageName = model.getNamespace(ConstantParam.KEYNAME_DTO);

        if (model.getEnumModels().getItems().size() > 0)
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

        if(entity.getMetaInfo().hasMetaAnnotation("dto"))
            return "";

        Annotation annotation = entity.getMetaInfo().getMetaAnnotation("extend");
        if (annotation != null)
            return "extends " + annotation.getValue();

        return "";
    }


    private String getAbstractInfo(MetaInfoModel entity) {
        if (entity.getMetaInfo().hasMetaAnnotation("abstract")) {
            return "abstract";
        }
        return "";
    }


    /**
     * Generates and publishes Java source code for the specified dtoModel.
     * This method orchestrates the creation of dtoModel classes based on
     * dtoModel definition and annotations.
     *
     * @param domain   The model domain to which the dtoModel belongs.
     * @param dtoModel The dtoModel for which source code is to be published.
     */
    public void publish(ModelDomain domain, DataTransfer dtoModel) {
        if (this.packageName == null) return;

        MetaInfo metaInfo = dtoModel.getMetaInfo();

        StringBuilder implInfos = new StringBuilder();
        implInfos.append("implements java.io.Serializable");
        
        // Get custom sortable interface from config, fallback to default
        ECoreModel model = domain.getModel();
        String sortableInterface = model.getConfig("interface.sortable");
        if (sortableInterface == null) {
            sortableInterface = "SortableObject";
        }
        implInfos.append(", ").append(extractSimpleClassName(sortableInterface));

        Annotation typeAnnotation = metaInfo.getMetaAnnotation("type");
        if (typeAnnotation != null) {
            if ("template".equals(typeAnnotation.getValue()))
                return;
        }

        if (!metaInfo.hasMetaAnnotation(DataTransferAnnotation.META_SEARCHABLE)) return;


        CodeTemplate.Parameters p = CodeTemplate.newParameters();
        String classNm = dtoModel.getIdentity().getName();

        this.paragraphForEntity = CodeTemplate.newParagraph();

        setNativeAnnotation(dtoModel, this.paragraphForEntity);

        // must call 'getFieldInfo' first
        CodeTemplate.Paragraph pr = getFieldInfo(dtoModel);



        String pageSize = metaInfo.getMetaAnnotationValue(DataTransferAnnotation.META_SEARCHABLE_PAGESIZE);
        if(pageSize==null) {
            String pageable = metaInfo.getMetaAnnotationValue(DataTransferAnnotation.META_SEARCHABLE_PAGEABLE);
            if("true".equals(pageable)) {
                pageSize = "100";
            }
        }

        pr.add("");
        pr.add("private String sortCode;");

        if (pageSize != null) {
            int defaultPageSize = 100;
            try {
                defaultPageSize = Integer.parseInt(pageSize);
            } catch (RuntimeException re) {
            }

            // Get custom pageable interface from config, fallback to default
            String pageableInterface = model.getConfig("interface.pageable");
            if (pageableInterface == null) {
                pageableInterface = "PageableObject";
            }
            implInfos.append(", ").append(extractSimpleClassName(pageableInterface));
            pr.add("");
            pr.add("@Builder.Default");
            pr.add("private int pageNumber=0;");
            pr.add("");
            pr.add("@Builder.Default");
            pr.add("private int pageSize=%s;", defaultPageSize);
        }

        CodeTemplate.Paragraph importList = CodeTemplate.newParagraph();

        ShadowModel shadowModel = domain.getModel().getShadowModel(classNm);
        Set<String> namespaceSet = shadowModel.getNamespaceSet();
        for(String ns: namespaceSet) {
            importList.add(ns);
        }
        
        // Add custom interface imports if they are fully qualified class names
        if (sortableInterface != null && sortableInterface.contains(".")) {
            importList.add(sortableInterface);
        }
        
        if (pageSize != null) {
            String pageableInterface = model.getConfig("interface.pageable");
            if (pageableInterface != null && pageableInterface.contains(".")) {
                importList.add(pageableInterface);
            }
        }

        boolean isSearchDTO = true;

        p
                .set("isSearchDTO",isSearchDTO)
                .set("packageName", packageName)
                .set("j2eePkgName",getPersistentPackageName(domain))
                .set("importList", importList)
                .set("enumPackageName", enumPackageName)
                .set("abstract", getAbstractInfo(dtoModel))
                .set("classAnnotationList", this.paragraphForEntity)
                .set("extendInfo", getExtendInfo(dtoModel))
                .set("implementInfo", implInfos.toString())
                .set("className", classNm);


        p.set("fieldList", pr);

        String qualifiedClassName = packageName + "." + classNm;
        String code = javaClassTmpl.toString(p);

        this.writeSrcCode(this.publisher, dtoModel, qualifiedClassName, code);
    }


    private CodeTemplate.Paragraph getFieldInfo(DataModelComponent dataModel) {
        CodeTemplate.Paragraph p = CodeTemplate.newParagraph();

        loadFieldInfo(dataModel, p);
        return p;
    }


    private void loadFieldInfo(DataModelComponent dto, CodeTemplate.Paragraph p) {

        DataTransfer dataTransfer = null;

        boolean isEntity = false;
        if (dto instanceof Entity) {
            isEntity = true;
        }else if (dto instanceof DataTransfer) {
            dataTransfer = (DataTransfer) dto;
        }

        ModelComponentItems<Field> items = dto.getItems();
        while(items.hasNext()) {
            Field f = items.next();
            processDeferredField((DataTransfer) dto, f);
        }

        ListMap<String, Field> fields = dto.getAllFieldListMap();
        List<Field> fieldList = fields.getList();


        Set<String> fieldNmSet = new HashSet<>();

        SourceShadowModel shadowModel = new SourceShadowModel(dto.getIdentity().getName());

        for (Field f : fieldList) {

            if(isDisableField(f))
                continue;


            if(f.hasAnnotation(DataTransferAnnotation.META_SEARCHABLE_BYPASS))
                continue;

            boolean enableSearch = f.hasAnnotation(EntityAnnotation.SEARCH);

            // eq,=,%5,in ....etc
            String conditionCode = f.getAnnotationValue(EntityAnnotation.SEARCH);

            // auto,between,eq when the field is searched without its own @search (see MapperSrcPublisher).
            // Decided here, not written into the field: an @extend parent's field is shared (issue 376).
            String autoConditionCode = getAutoSearchCondition(dto, f);

            if (f.getTypeInfo().isList() && (enableSearch || autoConditionCode != null)) {
                if(f.getTypeInfo().getCoreItemBaseFieldType()==null) {
                    String coreTypeNm = f.getTypeInfo().getCoreItemType();
                    EnumModel byName = this.publisher.getECoreModelContext().findModelComponent(coreTypeNm, EnumModel.class);
                    if (byName == null)
                        continue;
                }

            }

            /*
            if(f.getTypeInfo().isList() && enableSearch)
                continue;

             */

            boolean isForceDefineField = false;
            if(dataTransfer!=null && dataTransfer.findFieldName(f.getName())!=null) {
                isForceDefineField = true;
            }



            if(isSkipSearchField(dto, f))
                continue;

            if (autoConditionCode != null) {
                enableSearch = true;
                conditionCode = autoConditionCode;
            }


            if (!isForceDefineField
                    //&& !f.hasAnnotation("id")
                    //&& !isEnableSearchDTO
                    && !enableSearch
                    &&  ( dto.getItems().findByName(f.getName()) == null )

            )
                continue;


            if (isEntity) {
                if (!f.getTypeInfo().isBaseType())
                    continue;
            }

            String fieldNm = f.getName();

            String[] conditionItems = splitConditionText(conditionCode);
            if(conditionItems!=null && conditionItems.length==2) {
                fieldNm = conditionItems[1];
            }

            // A search name already taken (@s(like$keyword) on two fields): skip this field only.
            // A return here ended the field loop and publishing then failed with an NPE.
            if (fieldNmSet.contains(fieldNm)) continue;

            fieldNmSet.add(fieldNm);


            setFieldDesc(f, p);
            setFieldDocumentation(f,p ,conditionItems ,enableSearch);
            //setFieldValidation(f,p);
            if(setJsonInfo(f, p, false) ) {
                shadowModel.setImportPackageName("com.fasterxml.jackson.annotation.*");
            }
            setFormatAnnotation(f,p);

            setNativeAnnotation(f, p);

            //String defaultValDefined = getDefaultValueSetup(f);
            String defaultValDefined = "";


            String typeName = f.getTypeInfo().getDefaultTypeName();
            if ("between".equals(conditionCode) || "~".equals(conditionCode)) {
                p.add("%s %s %s%s;", "private", typeName, fieldNm + "From", defaultValDefined);
                p.add("");

                setFormatAnnotation(f,p);
                p.add("%s %s %s%s;", "private", typeName, fieldNm + "To", defaultValDefined);
            } else {

                if(f.getTypeInfo().isList()) {
                    if("eq".equals(conditionCode) || "=".equals(conditionCode)) {
                        typeName = f.getTypeInfo().getCoreItemType();
                        fieldNm = fieldNm +"Item";
                    }
                }

                p.add("%s %s %s%s;", "private", typeName, fieldNm, defaultValDefined);
            }

            p.add("");

            setFunctionInfo(f, p);

            shadowModel.addField(f);

        }

        this.publisher.getECoreModelContext().getDomain().getModel().setShadowModel(shadowModel);
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

        String prefix = this.publishMode + ":";
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
     * Extracts the simple class name from a fully qualified class name.
     * If the input contains a dot, returns the part after the last dot.
     * Otherwise, returns the input as is.
     *
     * @param className the fully qualified class name or simple class name
     * @return the simple class name
     */
    private String extractSimpleClassName(String className) {
        if (className == null) {
            return null;
        }
        int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < className.length() - 1) {
            return className.substring(lastDotIndex + 1);
        }
        return className;
    }
}
