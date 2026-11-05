//ecd:
package io.elasticore.base.model.pub.jpa;

import io.elasticore.base.CodePublisher;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.SourceFileAccessFactory;
import io.elasticore.base.model.*;
import io.elasticore.base.model.core.*;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.dto.DataTransferAnnotation;
import io.elasticore.base.model.entity.EntityAnnotation;
import io.elasticore.base.model.entity.BaseFieldType;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.enums.EnumConstant;
import io.elasticore.base.model.enums.EnumModel;
import io.elasticore.base.model.relation.ModelRelationship;
import io.elasticore.base.model.relation.RelationType;
import io.elasticore.base.util.CodeTemplate;
import io.elasticore.base.util.ConsoleLog;
import io.elasticore.base.util.HashUtils;
import io.elasticore.base.util.StringUtils;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SrcFilePublisher {


    private CodePublisher publisher;

    protected SrcFilePublisher(CodePublisher publisher) {
        this.publisher = publisher;
    }

    private void log(String msg) {
        System.err.println(msg);
    }

    private static String userHome = System.getProperty("user.dir");

    public String getFilePathInfo(String qualifiedClassName) {
        String path = publisher.getSrcFileAccessFactory().getFilePath(qualifiedClassName);

        Path input = Paths.get(path);
        Path home = Paths.get(userHome);
        if (input.startsWith(home)) {
            Path relativePath = home.relativize(input);
            return "." + File.separator + relativePath.toString();
        } else {
            return path;
        }
    }




    protected void writeSrcCode(CodePublisher publisher, ModelComponent modelComponent, String qualifiedClassName, String content) {
        writeSrcCode(publisher, modelComponent, qualifiedClassName, content, true);
    }

    protected void writeSrcCode(CodePublisher publisher, ModelComponent modelComponent, String qualifiedClassName, String content, boolean checkModified) {
        SourceFileAccessFactory fileAccessFactory = publisher.getSrcFileAccessFactory();


        HashUtils.Response response = null;
        try (Reader reader = fileAccessFactory.getReader(qualifiedClassName)) {
            response = HashUtils.checkContentModified(reader);


            if (response != null && !ConsoleLog.isValidationMode()) {

                if (response.getStatus() == HashUtils.UNTRACKED) {
                    // skip checking code
                    return;
                }

                else if (response.getStatus() == HashUtils.MODIFIED) {

                    if (checkModified && !response.hasCustomizedScopeContent()) {
                        ConsoleLog.storeLog("USER_MODIFIED", getFilePathInfo(qualifiedClassName));
                        return;
                    }
                    ConsoleLog.storeLog("USER_MODIFIED", getFilePathInfo(qualifiedClassName) + " [INGORE]");
                }

            }

        } catch (FileNotFoundException fnfe) {
        } catch (Throwable e) {
            e.printStackTrace();
        }

        // Validation mode: compare without writing
        if (ConsoleLog.isValidationMode()) {
            if (response == null) {
                // File doesn't exist yet - needs generation
                ConsoleLog.storeLog("VALIDATION_NEW", getFilePathInfo(qualifiedClassName));
                return;
            }
            if (response.getStatus() == HashUtils.UNTRACKED || response.getStatus() == HashUtils.NO_ECD) {
                // User-managed or non-ElastiCORE file, skip
                return;
            }
            if (response.getStatus() == HashUtils.MODIFIED) {
                // Hash says MODIFIED, but the actual content may be semantically identical
                // (line-ending, trailing whitespace, BOM, or brace-only formatting differences
                // introduced by IDE save). Treat those as VALID_OK -- the NO_MODIFIED branch
                // below already applies the same guard, so the MODIFIED branch should too.
                if (isSemanticallyIdentical(response.getPreSourceContent(), content)) {
                    ConsoleLog.storeLog("VALID_OK", getFilePathInfo(qualifiedClassName));
                    return;
                }
                // Real user edit -- show diff of what changed
                String diff = generateUnifiedDiff(response.getPreSourceContent(), content, getFilePathInfo(qualifiedClassName));
                ConsoleLog.storeLog("VALID_USER_MODIFIED", diff);
                return;
            }
            // NO_MODIFIED: compare generated content with existing
            boolean contentMatches = StringUtils.equalsWithPureChars(content, response.getPreSourceContent());
            if (contentMatches && !response.hasCustomizedScopeContent()) {
                ConsoleLog.storeLog("VALID_OK", getFilePathInfo(qualifiedClassName));
            } else if (contentMatches && response.hasCustomizedScopeContent()) {
                // Content matches but file has user-added custom code blocks
                ConsoleLog.storeLog("VALID_USER_MODIFIED", getFilePathInfo(qualifiedClassName) + " (custom code block)");
            } else {
                // Content differs - compute semantic set-based diff to confirm real difference
                String diff = generateUnifiedDiff(response.getPreSourceContent(), content, getFilePathInfo(qualifiedClassName));
                if (isSemanticallyIdentical(response.getPreSourceContent(), content)) {
                    // Whitespace/ordering difference only - treat as OK
                    ConsoleLog.storeLog("VALID_OK", getFilePathInfo(qualifiedClassName));
                } else {
                    // DSL changed but code not regenerated - this is a real validation failure
                    ConsoleLog.storeLog("VALIDATION_DIFF", diff);
                }
            }
            return;
        }

        boolean isEqaulsWithPureChars = false;
        if (response != null)
            isEqaulsWithPureChars = StringUtils.equalsWithPureChars(content, response.getPreSourceContent());

        // Fall back to semantic set-based comparison when pure-char check fails
        // (catches cases where only whitespace or line ordering differs)
        if (response != null && !isEqaulsWithPureChars
                && isSemanticallyIdentical(response.getPreSourceContent(), content)) {
            isEqaulsWithPureChars = true;
        }

        //if (response != null && isEqaulsWithPureChars && !response.hasCustomizedScopeContent()) {
        if (response != null && isEqaulsWithPureChars) {
            //ConsoleLog.printInfo("[INFO] PUB_SKIP: " + qualifiedClassName + " NO_MODIFIED");
            ConsoleLog.storeLog("NO_MODIFIED", getFilePathInfo(qualifiedClassName));

            return;
        }


        try (Writer writer = fileAccessFactory.getWriter(qualifiedClassName)) {


            if (response != null) {

                String oldEcdLine = response.getOldEcdLine();

                if (oldEcdLine != null && isEqaulsWithPureChars) {
                    writer.write(oldEcdLine + "\n");

                } else {

                    String hCode = HashUtils.makeEcdCode(content);
                    writer.write(hCode);
                }


                if (!response.hasCustomizedScopeContent()) {
                    writer.write(content);
                } else {
                    // Split content by lines
                    String[] lines = content.split("\n");
                    StringBuilder modifiedContent = new StringBuilder();


                    for (int i = 0; i < lines.length; i++) {

                        if (i == lines.length - 1) {
                            if (response.getCustomizedScopeContent() != null) {
                                writer.write(response.getCustomizedScopeContent());
                                writer.write("\n");
                            }
                        }

                        writer.write(lines[i]);
                        writer.write("\n");

                        if (i == 1) {
                            if (response.getCustomizedScopeContent4Header() != null) {
                                writer.write(response.getCustomizedScopeContent4Header());
                                writer.write("\n");
                            }
                        }
                    }
                }


            } else {
                writer.write(HashUtils.makeEcdCode(content));
                writer.write(content);
            }


            writer.flush();
        } catch (Throwable e) {
            e.printStackTrace();
            if (modelComponent != null)
                publisher.errorOnPublish(modelComponent, e);
            else
                e.printStackTrace();
        }
    }


    protected String getPath(String packageName) {
        return packageName.replace('.', '/');
    }


    public String getPersistentPackageName(ModelDomain domain) {
        if(domain==null) {
            domain = this.publisher.getECoreModelContext().getDomain();
        }

        try {
            if ("jakarta".equals(domain.getModel().getConfig("j2ee")))
                return "jakarta";
        } catch (RuntimeException re) {
        }

        return "javax";
    }


    protected CodeTemplate createCodeTemplate(CodePublisher publisher, String configPath, String defaultFileName) {
        String templatePath = publisher.getECoreModelContext().getDomain().getModel().getConfig(configPath);
        if (templatePath == null)
            templatePath = "elasticore-template/" + defaultFileName;
        else
            templatePath = "resource://" + templatePath;

        return CodeTemplate.newInstance(templatePath);
    }


    protected boolean isEntityReturnType(Field f, ECoreModel eCoreModel) {

        if(f.hasAnnotation(EntityAnnotation.DTO_TYPE))
            return false;

        if (!f.getTypeInfo().isBaseType()) {

            String parameterType = f.getTypeInfo().getDefaultTypeName();
            if (f.getTypeInfo().isGenericType())
                parameterType = f.getTypeInfo().getTypeParameterName();

            //if (eCoreModel.getEntityModels().findByName(parameterType) != null) {

            Entity entityModel = this.publisher.getECoreModelContext().findModelComponent(parameterType, Entity.class);
            if (entityModel != null) {
                if (!entityModel.getMetaInfo().hasMetaAnnotation(EntityAnnotation.META_EMBEDDABLE)) {
                    // skip
                    return true;
                }
            }
        }

        return false;
    }

    protected Field findFieldByTypeName(AbstractDataModel entity, String typeName) {
        if (typeName == null) return null;

        ModelComponentItems<Field> items = entity.getItems();
        while (items.hasNext()) {
            Field field = items.next();

            if (typeName.equals(field.getTypeInfo().getDefaultTypeName())) {
                return field;
            }
        }

        return null;
    }

    protected String findFieldNameByTypeName(AbstractDataModel entity, String typeName) {
        Field f = findFieldByTypeName(entity, typeName);
        if (f != null)
            return f.getName();

        return null;
    }


    protected String findSearchResultDTOName(RelationshipManager rm, Entity entity) {
        List<ModelRelationship> relationshipList = rm.findByFromNameAndType(
                entity.getIdentity().getName(), RelationType.SEARCH_RESULT);

        for (ModelRelationship r : relationshipList) {
            return r.getToName();
        }

        return null;
    }


    protected void setFieldDesc(Field field, CodeTemplate.Paragraph paragraph) {
        if (field.getDescription() != null) {
            paragraph.add("/*");
            paragraph.add("  " + field.getDescription());
            paragraph.add("*/");
        } else {
            String description = field.getAnnotationValue(EntityAnnotation.COMMENT);
            if (description != null) {
                paragraph.add("/*");
                paragraph.add("  " + description);
                paragraph.add("*/");
            }
        }


        String required = field.getAnnotationValue(EntityAnnotation.CALCULATION_REQUIRED);
        if ("true".equals(required))
            paragraph.add("// calcRequired:" + required);

    }


    protected void setFieldDocumentation(Field f, CodeTemplate.Paragraph p) {

        String desc = f.getAnnotationValue(EntityAnnotation.DESCRIPTION);
        if (desc == null) {
            desc = f.getName();
        }
        boolean isNotNull = f.hasAnnotation(EntityAnnotation.NOT_NULL);
        String requireTxt = "";
        if (isNotNull) {
            requireTxt = ", requiredMode=Schema.RequiredMode.REQUIRED";
        }

        // Auto-added nested relation DTO fields are response-only in the API contract:
        // clients set the relation via the flattened FK id field instead. Mark them
        // READ_ONLY so they are excluded from the request body schema in Swagger.
        // This is documentation-only and does not change (de)serialization behavior.
        String accessTxt = "";
        if (f.hasAnnotation(DataTransferAnnotation.META_AUTO_RELATION)) {
            accessTxt = ", accessMode = Schema.AccessMode.READ_ONLY";
        }

        String example = getExample(f);
        p.add("@Schema(description = \"%s\" %s%s %s)", desc, requireTxt, accessTxt, example);
    }

    protected void setFieldDocumentation(Field f, CodeTemplate.Paragraph p, String[] conditions, boolean enableSearch) {

        String conditionTxt = "";
        if (enableSearch) {
            if (conditions != null && conditions.length > 0) {
                conditionTxt = StringUtils.getOperatorDescription(conditions[0]) + " field:" + f.getName();
            } else {
                conditionTxt = StringUtils.getOperatorDescription("") + " field:" + f.getName();
            }
        }


        String desc = f.getAnnotationValue(EntityAnnotation.DESCRIPTION);
        if (desc == null) {
            desc = conditionTxt;
        } else {
            desc = desc + " " + conditionTxt;
        }
        boolean isNotNull = f.hasAnnotation(EntityAnnotation.NOT_NULL);
        String requireTxt = "";
        if (isNotNull && !enableSearch) {
            requireTxt = ", requiredMode=Schema.RequiredMode.REQUIRED";
        }

        String example = getExample(f);
        p.add("@Schema(description = \"%s\" %s %s)", desc, requireTxt, example);
    }

    protected String getExample(Field f) {
        String example = f.getAnnotationValue(EntityAnnotation.EXAMPLE);

        if (example == null) {

            EnumModel enumModel = this.publisher.getECoreModelContext().getDomain()
                    .getModel()
                    .getEnumModels()
                    .findByName(f.getTypeInfo().getCoreItemType());

            // An enum shared from another domain (e.g. a common Indicator)
            // documents itself the same way; the lookup above sees only the
            // domain being generated, so such a field had no example at all.
            if (enumModel == null && f.getTypeInfo().getCoreItemType() != null) {
                enumModel = findEnumModel(f.getTypeInfo().getCoreItemType());
            }

            if (enumModel != null) {
                String fieldNameForJson = enumModel.getMetaInfo().getMetaAnnotationValue("json");

                int jsonIdx = -1;
                int chkIdx = 0;
                for (Field ef : enumModel.getFieldItems().getItemList()) {
                    if (ef.getName().equals(fieldNameForJson)) {
                        jsonIdx = chkIdx;
                        break;
                    }
                    chkIdx++;
                }

                StringBuilder sb = new StringBuilder();

                List<EnumConstant> itemList = enumModel.getEnumConstantItems().getItemList();
                for (EnumConstant e : itemList) {
                    List<EnumConstant.ConstructParam> constructParamList = e.getConstructParamList();
                    if (sb.length() > 0)
                        sb.append(" | ");

                    if (jsonIdx < 0) {
                        sb.append(e.getIdentity().getName());
                    } else {
                        sb.append(constructParamList.get(jsonIdx).getName());
                    }
                    sb.append(":");

                    for (int i = 0; i < constructParamList.size(); i++) {

                        if (i != jsonIdx) {
                            sb.append(" ");
                            sb.append(constructParamList.get(i));
                        }
                    }
                }

                example = sb.toString();
            }

        }

        if (example == null) {
            String format = f.getAnnotationValue(EntityAnnotation.FORMAT);
            if (format == null) {
                if (f.getTypeInfo().getBaseFieldType() == BaseFieldType.DATE
                        || f.getTypeInfo().getBaseFieldType() == BaseFieldType.LocalDate) {
                    if (format == null)
                        format = "yyyy-MM-dd";

                } else if (f.getTypeInfo().getBaseFieldType() == BaseFieldType.DATETIME
                        || f.getTypeInfo().getBaseFieldType() == BaseFieldType.LocalDateTime) {
                    if (format == null)
                        format = "yyyy-MM-dd HH:mm:ss";

                }
            }

            example = format;
        }


        if (example != null) {
            example = ", example=\"" + StringUtils.escapeQuotes(example) + "\"";
        } else {
            example = "";
        }
        return example;
    }


    protected void setFieldValidation(Field f, CodeTemplate.Paragraph p) {

        if (f.hasAnnotation("notnull") && !f.hasAnnotation("dto:jakarta.validation.constraints.NotNull"))
            p.add("@NotNull");

        if (f.hasAnnotation("notblank"))
            p.add("@NotBlank");

        if (f.hasAnnotation(DataTransferAnnotation.PATTERN) && !f.hasAnnotation("dto:jakarta.validation.constraints.Pattern")) {
            String regexp = f.getAnnotationValue(DataTransferAnnotation.PATTERN_REGEX);
            if(regexp != null && regexp.length() > 0) {
                String msg = f.getAnnotationValue(DataTransferAnnotation.PATTERN_MSG);
                if(msg !=null && msg.length()>0) {
                    String persistentPackageName = getPersistentPackageName(null);

                    p.add("@"+persistentPackageName+".validation.constraints.Pattern(");
                    p.add(" regexp=\""+regexp+"\",");
                    p.add(" msg=\""+msg+"\"");
                    p.add(")");
                }
            }

        }


        // Enhanced support for complex length parameters: @length(min=1, max=200)
        int minLength = f.getMinLength();
        int maxLength = f.getMaxLength();

        // Fallback to legacy @minsize annotation if min not found in complex format
        String legacyMinSize = f.getAnnotationValue(EntityAnnotation.MIN_SIZE);
        if (minLength < 0 && legacyMinSize != null) {
            try {
                minLength = Integer.parseInt(legacyMinSize);
            } catch (NumberFormatException e) {
                // Keep minLength = -1 if parsing fails
            }
        }

        if (minLength >= 0 || maxLength >= 0) {
            StringBuilder sb = new StringBuilder();
            if (minLength >= 0)
                sb.append("min=").append(minLength);
            if (maxLength >= 0) {
                if (sb.length() > 0)
                    sb.append(" , ");
                sb.append("max=").append(maxLength);
            }

            p.add("@Size(%s)", sb.toString());
        }

        // Add @Min and @Max annotations for numeric values
        String minValue = f.getAnnotationValue(EntityAnnotation.MIN_VALUE);
        String maxValue = f.getAnnotationValue(EntityAnnotation.MAX_VALUE);
        
        if (minValue != null) {
            String persistentPackageName = getPersistentPackageName(null);
            if(f.getTypeInfo().equals(BaseFieldType.BIGDECIMAL) || f.getTypeInfo().equals(BaseFieldType.BIGINTEGER)) {
                p.add("@" + persistentPackageName + ".validation.constraints.DecimalMin(\"" + minValue + "\")");
            }else {
                p.add("@" + persistentPackageName + ".validation.constraints.Min(" + minValue + ")");
            }

        }
        
        if (maxValue != null) {
            String persistentPackageName = getPersistentPackageName(null);
            if(f.getTypeInfo().equals(BaseFieldType.BIGDECIMAL) || f.getTypeInfo().equals(BaseFieldType.BIGINTEGER)) {
                p.add("@" + persistentPackageName + ".validation.constraints.DecimalMax(\"" + minValue + "\")");
            }else {
                p.add("@" + persistentPackageName + ".validation.constraints.Max(" + maxValue + ")");
            }
        }

        // Email validation
        if (f.hasAnnotation("email")) {
            String persistentPackageName = getPersistentPackageName(null);
            p.add("@" + persistentPackageName + ".validation.constraints.Email(message = \"Email format invalid\")");
        }

        // Past validation
        if (f.hasAnnotation("past")) {
            String persistentPackageName = getPersistentPackageName(null);
            p.add("@" + persistentPackageName + ".validation.constraints.Past(message = \"Date must be past\")");
        }

        // Future validation
        if (f.hasAnnotation("future")) {
            String persistentPackageName = getPersistentPackageName(null);
            p.add("@" + persistentPackageName + ".validation.constraints.Future(message = \"Date must be future\")");
        }

        // PastOrPresent validation
        if (f.hasAnnotation("pastorpresent")) {
            String persistentPackageName = getPersistentPackageName(null);
            p.add("@" + persistentPackageName + ".validation.constraints.PastOrPresent(message = \"Date must be past or present\")");
        }

        // FutureOrPresent validation
        if (f.hasAnnotation("futureorpresent")) {
            String persistentPackageName = getPersistentPackageName(null);
            p.add("@" + persistentPackageName + ".validation.constraints.FutureOrPresent(message = \"Date must be future or present\")");
        }
    }


    protected boolean setJsonInfo(Field f, CodeTemplate.Paragraph p) {
        return setJsonInfo(f, p, true);
    }

    protected boolean setJsonInfo(Field f, CodeTemplate.Paragraph p, boolean checkAccess) {

        boolean isApplied = false;
        String jsonName = f.getAnnotationValue(EntityAnnotation.JSON_NAME);
        boolean readOnly = f.hasAnnotation(DataTransferAnnotation.READ_ONLY);
        boolean writeOnly = f.hasAnnotation(DataTransferAnnotation.WRITE_ONLY);
        if(!checkAccess) {
            readOnly = false;
            writeOnly = false;
        }
        if (jsonName != null || readOnly || writeOnly) {

            String jsonAttr = "";
            if(!readOnly && !writeOnly) {
                p.add("@JsonProperty(\"%s\")", jsonName);
            }else {
                if(readOnly) {
                    jsonAttr = "access=JsonProperty.Access.READ_ONLY";
                }
                else if(writeOnly) {
                    jsonAttr = "access=JsonProperty.Access.WRITE_ONLY";
                }
                if(jsonName!=null && jsonName.length()>0) {
                    jsonAttr = "value=\""+jsonName+"\", "+jsonAttr;
                }
                p.add("@JsonProperty(%s)", jsonAttr);
            }

            isApplied = true;
        }

        if (f.hasAnnotation("jsonignore") || f.hasAnnotation("ignore")) {
            p.add("@JsonIgnore");
            isApplied = true;
        }

        String jsonSerialize = f.getAnnotationValue(EntityAnnotation.JSON_SERIALIZE_USE);
        String jsonDeserialize = f.getAnnotationValue(EntityAnnotation.JSON_DESERIALIZE_USE);

        if (jsonSerialize != null) {
            p.add("@com.fasterxml.jackson.databind.annotation.JsonSerialize(using = %s)", jsonSerialize);

        }

        if (jsonDeserialize != null) {
            p.add("@com.fasterxml.jackson.databind.annotation.JsonSerialize(using = %s)", jsonDeserialize);

        }

        return isApplied;
    }

    /**
     * Resolve effective jsonview mode for a DTO.
     * Priority: DTO @jsonview annotation > env.yml config > default (false)
     *
     * @return "false", "true", or "auto"
     */
    protected String resolveJsonViewMode(DataModelComponent dto) {
        MetaInfo metaInfo = dto.getMetaInfo();

        // 1. Check DTO-level explicit annotation
        if (metaInfo.hasMetaAnnotation("jsonview")) {
            String dtoValue = metaInfo.getMetaAnnotationValue("jsonview");
            if (dtoValue == null || dtoValue.isEmpty()) return "true"; // bare @jsonview = manual mode
            if ("false".equalsIgnoreCase(dtoValue)) return "false";
            if ("auto".equalsIgnoreCase(dtoValue)) return "auto";
            return "true"; // any other value = manual mode
        }

        // 2. Fall back to env.yml config
        ECoreModel model = this.publisher.getECoreModelContext().getDomain().getModel();
        String envConfig = model.getConfig("jsonview", "false");
        if ("auto".equalsIgnoreCase(envConfig)) return "auto";
        if ("true".equalsIgnoreCase(envConfig)) return "true";
        return "false";
    }

    /**
     * Generate @JsonView annotation for a field based on @view DSL annotation.
     * Only applies when the parent DTO has @jsonview meta annotation enabled.
     *
     * @param f     the field to process
     * @param p     the paragraph to add annotation to
     * @param dto   the parent DTO component
     * @return true if @JsonView annotation was added
     */
    protected boolean setJsonViewInfo(Field f, CodeTemplate.Paragraph p, DataModelComponent dto) {
        String mode = resolveJsonViewMode(dto);
        if ("false".equals(mode)) {
            return false;
        }

        // Check for explicit @view annotation first (overrides auto-inference)
        String viewValue = f.getAnnotationValue("view");
        if (viewValue != null && !viewValue.trim().isEmpty()) {
            return generateJsonViewAnnotation(viewValue, p);
        }

        // Auto mode: infer from existing annotations
        if ("auto".equals(mode)) {
            return inferJsonViewFromAnnotations(f, p);
        }

        // Manual mode without @view: no @JsonView generated (visible in all views)
        return false;
    }

    /**
     * Parse @view value and generate @JsonView annotation.
     * Supports both single-char mode (@view(cru)) and comma-separated mode (@view(create,update,read)).
     */
    private boolean generateJsonViewAnnotation(String viewValue, CodeTemplate.Paragraph p) {
        java.util.Set<String> viewClasses = new java.util.LinkedHashSet<>();

        if (viewValue.contains(",")) {
            // Comma-separated full name mode: @view(create,update,read)
            for (String view : viewValue.split("\\s*,\\s*")) {
                mapViewName(view.trim().toLowerCase(), viewClasses);
            }
        } else {
            // Single-character mode: @view(cru)
            String lower = viewValue.trim().toLowerCase();
            for (char ch : lower.toCharArray()) {
                switch (ch) {
                    case 'c': viewClasses.add("Views.Create.class"); break;
                    case 'r': viewClasses.add("Views.Read.class"); break;
                    case 'u': viewClasses.add("Views.Update.class"); break;
                    // 'd' reserved for future Delete view
                }
            }
        }

        if (viewClasses.isEmpty()) return false;

        if (viewClasses.size() == 1) {
            p.add("@JsonView(%s)", viewClasses.iterator().next());
        } else {
            p.add("@JsonView({%s})", String.join(", ", viewClasses));
        }
        return true;
    }

    /**
     * Map view name string to Views class reference.
     */
    private void mapViewName(String name, java.util.Set<String> viewClasses) {
        switch (name) {
            case "create": case "c": viewClasses.add("Views.Create.class"); break;
            case "update": case "u": viewClasses.add("Views.Update.class"); break;
            case "read": case "r": viewClasses.add("Views.Read.class"); break;
        }
    }

    /**
     * Infer JsonView from existing field annotations (@id, @genid, @sequence, @jpa:CreatedDate, etc.)
     * Used in @jsonview(auto) mode.
     */
    private boolean inferJsonViewFromAnnotations(Field f, CodeTemplate.Paragraph p) {
        boolean isId = f.hasAnnotation("id");
        boolean isGenId = f.hasAnnotation("genid");
        boolean isSequence = f.hasAnnotation("sequence");
        boolean isUpdatableFalse = "false".equals(f.getAnnotationValue("updatable"));

        // JPA native annotations - keys are LOWERCASED in annotation map
        boolean isCreatedDate = f.hasAnnotation("jpa:createddate");
        boolean isLastModifiedDate = f.hasAnnotation("jpa:lastmodifieddate");
        boolean isCreatedBy = f.hasAnnotation("jpa:createdby");
        boolean isLastModifiedBy = f.hasAnnotation("jpa:lastmodifiedby");
        boolean isVersion = f.hasAnnotation("jpa:version");

        boolean isFormula = f.hasAnnotation("formula");
        boolean isReadonly = f.hasAnnotation("readonly") || f.hasAnnotation("noinput");
        boolean isWriteonly = f.hasAnnotation("writeonly") || f.hasAnnotation("nooutput");

        // Default: visible in all views
        boolean create = true, update = true, read = true;

        // Auto-generated ID: not in create (server generates), needed in update (identifier)
        if (isId && (isGenId || isSequence)) {
            create = false;
        }

        // Insert-only field: not in update
        if (isUpdatableFalse) {
            update = false;
        }

        // Server-managed fields: read only
        if (isCreatedDate || isLastModifiedDate || isCreatedBy || isLastModifiedBy
                || isVersion || isFormula || isReadonly) {
            create = false;
            update = false;
        }

        // Write-only: not in response
        if (isWriteonly) {
            read = false;
        }

        // All true = default behavior, no @JsonView needed
        if (create && update && read) return false;

        // All false = edge case, skip
        if (!create && !update && !read) return false;

        java.util.List<String> views = new java.util.ArrayList<>();
        if (create) views.add("Views.Create.class");
        if (update) views.add("Views.Update.class");
        if (read) views.add("Views.Read.class");

        if (views.size() == 1) {
            p.add("@JsonView(%s)", views.get(0));
        } else {
            p.add("@JsonView({%s})", String.join(", ", views));
        }
        return true;
    }

    protected boolean isEnumerationType(Field f) {
        String typeName = f.getTypeInfo().getCoreItemType();
        //if (this.publisher.getECoreModelContext().getDomain().getModel().getEnumModels().findByName(typeName) != null)
        if (findEnumModel(typeName) != null)
            return true;

        return false;
    }

    protected Entity findEntityByField(Field f) {
        String typeName = f.getTypeInfo().getCoreItemType();
        return findEntityByName(typeName);
    }

    protected String findFullNameWithNS(String typeName) {
        DataModelComponent modelComponent = this.publisher.getECoreModelContext().findModelComponent(typeName);
        if(modelComponent!=null) {
            String namespaceKey = null;
            ComponentType componentType = modelComponent.getIdentity().getComponentType();
             if(ComponentType.ENTITY == componentType) {
                 namespaceKey = ConstantParam.KEYNAME_ENTITY;
             }
             else if(ComponentType.ENUM == componentType) {
                 namespaceKey = ConstantParam.KEYNAME_ENUMERATION;
             }
             else if(ComponentType.DTO == componentType) {
                 namespaceKey = ConstantParam.KEYNAME_DTO;
             }

             if(namespaceKey!=null) {
                 String namespace = this.publisher.getECoreModelContext().getDomain(modelComponent.getIdentity().getDomainId())
                         .getModel().getNamespace(namespaceKey);

                 if(namespace!=null)
                     return namespace+"."+typeName;
             }
        }
        return null;
    }

    protected Entity findEntityByName(String typeName) {
        if(typeName==null) return null;
        return this.publisher.getECoreModelContext().findModelComponent(typeName, Entity.class);
    }

    protected EnumModel findEnumModel(String enumName) {
        return this.publisher.getECoreModelContext().findModelComponent(enumName, EnumModel.class);
    }


    protected boolean isEntityType(Field f) {
        if (findEntityByField(f) != null)
            return true;
        return false;
    }


    protected void setFunctionInfo(Field f, CodeTemplate.Paragraph p) {
        String getFunc = f.getAnnotationValue(EntityAnnotation.FUNCTION_GET);
        String setFunc = f.getAnnotationValue(EntityAnnotation.FUNCTION_SET);

        String fldNm = f.getName();
        String cFldNm = StringUtils.capitalize(fldNm);
        String type = f.getTypeInfo().getDefaultTypeName();


        if (getFunc != null) {
            getFunc = getFunc.trim();
            if (!"null".equals(getFunc) && getFunc.indexOf("(") < 0) {
                getFunc = getFunc + "(this)";
            }
            if (getFunc.indexOf(";") < 0)
                getFunc = getFunc + ";";
            p.add("public %s get%s() {", type, cFldNm);
            p.add("    return %s", getFunc);
            p.add("}");
            p.add("");
        }

        if (setFunc != null) {
            if (setFunc.indexOf("(") < 0) {
                setFunc = setFunc.trim() + "(this)";
            }
            p.add("public void set%s(%s val) {", cFldNm, type);
            p.add("    this.%s = val;", fldNm);
            p.add("    %s;", setFunc);
            p.add("}");
            p.add("");
        }
    }

    protected void setFormatAnnotation(Field field, CodeTemplate.Paragraph paragraph) {

        String format = field.getAnnotationValue(EntityAnnotation.FORMAT);
        if (field.getTypeInfo().getBaseFieldType() == BaseFieldType.DATE
                || field.getTypeInfo().getBaseFieldType() == BaseFieldType.LocalDate) {
            if (format == null)
                format = "yyyy-MM-dd";
            paragraph.add("@org.springframework.format.annotation.DateTimeFormat(pattern = \"%s\")", format);
            paragraph.add("@com.fasterxml.jackson.annotation.JsonFormat(pattern = \"%s\")", format);
        } else if (field.getTypeInfo().getBaseFieldType() == BaseFieldType.DATETIME
                || field.getTypeInfo().getBaseFieldType() == BaseFieldType.LocalDateTime) {
            if (format == null)
                format = "yyyy-MM-dd HH:mm:ss";
            paragraph.add("@org.springframework.format.annotation.DateTimeFormat(pattern = \"%s\")", format);
            paragraph.add("@com.fasterxml.jackson.annotation.JsonFormat(pattern = \"%s\")", format);
        }

    }


    protected String targetEntityName(Entity targetEntity) {

        String rollupTargetNm = targetEntity.getMetaInfo().getMetaAnnotationValue(EntityAnnotation.META_ROLL_UP_TARGET);
        if (rollupTargetNm == null) {
            return targetEntity.getIdentity().getName();
        }

        Entity rollupEntity = findEntityByName(rollupTargetNm);
        if (rollupEntity == null)
            return null;

        return targetEntityName(rollupEntity);

    }

    /**
     * check entity or dto
     *
     * @param eCoreModel
     * @param typeName
     * @return
     */
    protected boolean isModel(ECoreModel eCoreModel, String typeName) {
        boolean isEntity = findEntityByName(typeName) != null;
        if (isEntity)
            return true;
        //boolean isDTO = eCoreModel.getDataTransferModels().findByName(typeName)!=null;
        boolean isDTO = this.publisher.getECoreModelContext().findModelComponent(typeName, DataTransfer.class) != null;
        if (isDTO)
            return true;


        return false;
    }


    protected boolean isEntityModel(ECoreModel eCoreModel, String typeName) {
        boolean isEntity = this.findEntityByName(typeName) != null;
        if (isEntity)
            return true;

        return false;
    }

    protected boolean isDTOModel(ECoreModel eCoreModel, String typeName) {

        boolean isDTO = eCoreModel.getDataTransferModels().findByName(typeName) != null;
        if (isDTO)
            return true;


        return false;
    }

    protected boolean isEnumModel(String typeName) {
        return this.publisher.getECoreModelContext().findModelComponent(typeName, EnumModel.class) != null;
    }


    protected boolean isExist(ECoreModel eCoreModel, String typeName) {
        boolean isEntity = this.findEntityByName(typeName) != null;
        if (isEntity)
            return true;
        boolean isDTO = eCoreModel.getDataTransferModels().findByName(typeName) != null;
        if (isDTO)
            return true;
        boolean isEnum = eCoreModel.getEnumModels().findByName(typeName) != null;
        if (isEnum)
            return true;

        return false;
    }

    protected boolean isEnableInDTO(ECoreModel eCoreModel, MetaInfo metaInfo, Field f ,String typeName, ComponentIdentity identity) {

        //////boolean isDTO = eCoreModel.getDataTransferModels().findByName(typeName)!=null;
        boolean isDTO = this.publisher.getECoreModelContext().findModelComponent(typeName, DataTransfer.class) != null;

        if (isDTO) {
            String type = metaInfo.getMetaAnnotationValue(DataTransferAnnotation.META_TYPE);
            boolean isSearchResult = metaInfo.hasMetaAnnotation(DataTransferAnnotation.META_SEARCH_RESULT);
            if ("entity".equals(type) && isSearchResult) {
                return false;
            }
            String targetNm = io.elasticore.base.model.dto.TemplateList.namesOf(metaInfo);
            if (targetNm != null) {

                // Cross-referencing can lead to an infinite loop, so it has been excluded.
                RelationshipManager instance = RelationshipManager.getInstance(identity.getDomainId());
                List<ModelRelationship> byToName = instance.findByToNameAndType(targetNm, RelationType.MANY_TO_ONE);
                /*
                if (byToName != null && byToName.size() > 0) {
                    ConsoleLog.printWarn("Cross-referencing can lead to an infinite loop. typeName: " + typeName);
                    //return false;
                }
                 */
            }

            return true;
        }
        //////boolean isEnum = eCoreModel.getEnumModels().findByName(typeName)!=null;
        boolean isEnum = this.publisher.getECoreModelContext().findModelComponent(typeName, EnumModel.class) != null;
        if (isEnum)
            return true;


        return false;
    }


    protected boolean isSkipSearchField(DataModelComponent dto, Field f) {
        if (f.hasAnnotation(EntityAnnotation.AUTOSEARCH) && !dto.getMetaInfo().hasMetaAnnotation(EntityAnnotation.AUTOGENERATED)) {
            return true;
        }

        return false;
    }


    /**
     * Retrieves the full class name if the given className includes a domain prefix.
     * If the className contains a colon (`:`), it attempts to find the corresponding
     * DataModelComponent and return its full name. Otherwise, it returns the input className.
     *
     * @param className the class name, which may include a domain prefix (e.g., "domain:ClassName")
     * @return the full class name if a corresponding DataModelComponent is found; otherwise, the input className
     */
    protected String getClassName(Entity entity, String className) {
        if (className.indexOf(":") > 0) {
            DataModelComponent modelComponent = BaseECoreModelContext.getContext().findModelComponent(className);
            if (modelComponent != null) {
                return modelComponent.getFullName();
            }
        } else {
            String domainId = entity.getIdentity().getDomainId();

            String[] allDomainNames = BaseECoreModelContext.getContext().getAllDomainNames();
            for (String domainNm : allDomainNames) {
                DataModelComponent modelByName = BaseECoreModelContext.getContext().getDomain(domainNm).getModel().findModelByName(className);
                if (modelByName != null) {
                    if (domainId.equals(domainNm)) {
                        return modelByName.getIdentity().getName();
                    } else {
                        return modelByName.getFullName();
                    }
                }
            }

        }
        return className;
    }


    protected void processDeferredField(DataTransfer dto, Field f) {


        if (f.hasAnnotation(DataTransferAnnotation.META_DEFERRED)) {

            String baseFieldNm = f.getName();

            String typeName = f.getTypeInfo().getDefaultTypeName();
            Entity entity = this.publisher.getECoreModelContext().findModelComponent(typeName, Entity.class);

            if (entity == null) {
                //f.removeAnnotation(DataTransferAnnotation.META_DEFERRED);
                return;
            }

            //if(entity !=null)
            {

                Map<String, Annotation> antMp = new HashMap<>();

                boolean isEmbeddable = false;

                if (entity.getMetaInfo().hasMetaAnnotation(EntityAnnotation.META_EMBEDDABLE)) {
                    isEmbeddable = true;
                }

                Annotation att = Annotation.create(DataTransferAnnotation.META_SEARCHABLE_BYPASS);
                antMp.put(att.getName(), att);

                if( f.hasAnnotation(EntityAnnotation.NOT_NULL)) {
                    Annotation notNulAttr = Annotation.create(EntityAnnotation.NOT_NULL[0]);
                    antMp.put(notNulAttr.getName(), notNulAttr);
                }

                Annotation label = f.getAnnotation("label");
                if(label!=null) {
                    antMp.put(label.getName(), label);
                }


                if (!dto.getMetaInfo().hasMetaAnnotation(DataTransferAnnotation.META_SEARCHABLE)) {
                    // Mark as auto-added nested relation field so the OpenAPI schema can
                    // document it as READ_ONLY (input goes through the flattened FK id field).
                    Annotation autoRelAnt = Annotation.create(DataTransferAnnotation.META_AUTO_RELATION);
                    antMp.put(autoRelAnt.getName(), autoRelAnt);

                    Field dtoField = Field.builder()
                            .name(f.getName())
                            .parentMetaInfo(dto.getMetaInfo())
                            .type(typeName + "DTO")
                            .annotationMap(antMp)
                            .build();

                    dto.addField(dtoField);
                }
                //ListMap<String, Field> fields = dto.getAllFieldListMap();
                //System.err.println( fields.size());


                //ModelComponentItems<Field> entityFields = entity.getItems();
                checkDTOIdentity(dto,entity,baseFieldNm);

                //ListMap<String, Field> fields2 = dto.getAllFieldListMap();
                //System.err.println( fields2.size());

            }

        }
    }

    private void checkDTOIdentity(DataTransfer dto, Entity entity, String baseFieldNm) {
        ModelComponentItems<Field> entityFields = entity.getItems();

        String parentEntityNm = entity.getMetaInfo().getMetaAnnotationValue(EntityAnnotation.META_EXTEND);

        if(parentEntityNm!=null) {
            Entity parentEntity = this.findEntityByName(parentEntityNm);
            if(parentEntity!=null) {
                checkDTOIdentity(dto, parentEntity, baseFieldNm);
            }
        }

        while (entityFields.hasNext()) {
            Field entityField = entityFields.next();
            if (entityField.hasAnnotation(EntityAnnotation.META_ID)) {

                String refFieldNm = baseFieldNm + "." + entityField.getName();
                String newFieldNm = baseFieldNm + StringUtils.capitalize(entityField.getName());

                if(dto.getAllFieldListMap().containsKey(newFieldNm))
                    continue;

                // for search
                Annotation srchAnt = Annotation.create(DataTransferAnnotation.META_SEARCHABLE, "eq");
                Annotation annotation = Annotation.create("ref", refFieldNm);
                Map<String, Annotation> antMap = new HashMap<>();
                antMap.put(annotation.getName(), annotation);
                antMap.put(srchAnt.getName(), srchAnt);


                Annotation.create("s", "eq");
                antMap.put(annotation.getName(), annotation);


                Field newRefField = Field.builder()
                        .name(newFieldNm)
                        .parentMetaInfo(dto.getMetaInfo())
                        .type(entityField.getTypeInfo().getDefaultTypeName())
                        .annotationMap(antMap)
                        .build();

                dto.addField(newRefField);

            }
        }
    }

    protected boolean isDisableField(Field f) {
        if (f.hasAnnotation(DataTransferAnnotation.META_DISABLE)) {
            if (this.isEntityType(f)) {
                return true;
            }
        }
        return false;
    }

    protected String getDomainName(String typeName) {
        DataModelComponent modelComponent = this.publisher.getECoreModelContext().findModelComponent(typeName);
        if(modelComponent!=null)
            return modelComponent.getIdentity().getDomainId();

        return null;
    }

    protected DataTransfer findDTO(String name) {
        DataTransfer modelComponent = this.publisher.getECoreModelContext().findModelComponent(name, DataTransfer.class);
        return modelComponent;
    }


    protected DataTransfer findDTO(Entity entity) {

        ModelDomain domain = this.publisher.getECoreModelContext().getDomain();
        ECoreModel model = domain.getModel();
        RelationshipManager relationshipManager = RelationshipManager.getInstance(domain.getName());


        String entityNm = entity.getIdentity().getName();
        List<ModelRelationship> relationshipList = relationshipManager
                .findByToNameAndType(entityNm, RelationType.TEMPLATE);

        for (ModelRelationship r : relationshipList) {
            String dtoName = r.getFromName();


            DataTransfer byName = model.getDataTransferModels().findByName(dtoName);
            if (byName != null)
                return byName;
        }

        return null;
    }

    protected String getNamespace(String typeName) {
        return getNamespace(typeName, null);
    }

    protected String getNamespace(String typeName, String componentType) {
        DataModelComponent modelComponent = BaseECoreModelContext.getContext().findModelComponent(typeName);

        if (modelComponent != null) {


            String domainId = modelComponent.getIdentity().getDomainId();

            if(componentType==null || componentType.isEmpty())
                componentType = modelComponent.getIdentity().getComponentType().getName();

            String ns = BaseECoreModelContext.getContext().getDomain(domainId).getModel()
                    .getNamespace(componentType);
            return ns;
        }
        return null;
    }

    protected void storeRefEntityInfo(String baseEntityName, String typEntityName) {
        if(findEntityByName(typEntityName)==null) return;

        String baseNS = getNamespace(baseEntityName);

        String typeNs = getNamespace(typEntityName);
        if(!baseNS.equals(typeNs)) {
            this.publisher.getECoreModelContext().getDomain().getModel().addExternalEntity(typEntityName);
        }

    }

    protected String getCascadeInfo(Field field) {

        boolean isCascadeField = false;
        String cascade="";
        if(field.hasAnnotation(EntityAnnotation.CASCADE)) {
            String cascadeType = field.getAnnotationValue(EntityAnnotation.CASCADE);
            if(cascadeType==null) {
                cascadeType = "ALL, orphanRemoval = true";
                isCascadeField = true;
            }
            else {
                cascadeType = cascadeType.toUpperCase(Locale.ROOT);
                if("ALL".equals(cascadeType))
                    isCascadeField = true;
            }
            if(isCascadeField) {
                Annotation ant = Annotation.create(EntityAnnotation.CASCADE_FIELD);
                field.getAnnotationMap().put(ant.getName(),ant);
            }

            cascade = "cascade = CascadeType."+cascadeType;
        }
        return cascade;
    }

    /**
     * Check if two source contents are semantically identical (same non-empty, non-brace lines as a set).
     * Returns true if the set-based diff would produce zero additions and zero removals.
     */
    private boolean isSemanticallyIdentical(String existing, String generated) {
        java.util.Set<String> existSet = new java.util.LinkedHashSet<>();
        java.util.Set<String> genSet = new java.util.LinkedHashSet<>();
        if (existing != null) {
            for (String line : existing.split("\n", -1)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.equals("{") && !trimmed.equals("}")) {
                    existSet.add(trimmed);
                }
            }
        }
        if (generated != null) {
            for (String line : generated.split("\n", -1)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.equals("{") && !trimmed.equals("}")) {
                    genSet.add(trimmed);
                }
            }
        }
        return existSet.equals(genSet);
    }

    private String generateUnifiedDiff(String existing, String generated, String filePath) {
        StringBuilder diff = new StringBuilder();
        diff.append(filePath).append("\n");

        // Collect trimmed non-empty lines from each version
        java.util.Set<String> existSet = new java.util.LinkedHashSet<>();
        java.util.Set<String> genSet = new java.util.LinkedHashSet<>();

        if (existing != null) {
            for (String line : existing.split("\n", -1)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.equals("{") && !trimmed.equals("}")) {
                    existSet.add(trimmed);
                }
            }
        }
        if (generated != null) {
            for (String line : generated.split("\n", -1)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.equals("{") && !trimmed.equals("}")) {
                    genSet.add(trimmed);
                }
            }
        }

        // Lines in existing but not in generated (removed/modified by user)
        List<String> removed = new java.util.ArrayList<>();
        for (String line : existSet) {
            if (!genSet.contains(line)) removed.add(line);
        }

        // Lines in generated but not in existing (would be added by regeneration)
        List<String> added = new java.util.ArrayList<>();
        for (String line : genSet) {
            if (!existSet.contains(line)) added.add(line);
        }

        if (!removed.isEmpty()) {
            diff.append("  [current] only in current file:\n");
            for (String line : removed) {
                if (removed.size() <= 15) {
                    diff.append("    > ").append(line).append("\n");
                }
            }
            if (removed.size() > 15) {
                diff.append("    > ... (").append(removed.size()).append(" lines)\n");
            }
        }

        if (!added.isEmpty()) {
            diff.append("  [expected] only in ElastiCORE generated:\n");
            for (String line : added) {
                if (added.size() <= 15) {
                    diff.append("    > ").append(line).append("\n");
                }
            }
            if (added.size() > 15) {
                diff.append("    > ... (").append(added.size()).append(" lines)\n");
            }
        }

        diff.append(String.format("  [diff: %d in current, %d in expected]\n", removed.size(), added.size()));
        return diff.toString();
    }
}
