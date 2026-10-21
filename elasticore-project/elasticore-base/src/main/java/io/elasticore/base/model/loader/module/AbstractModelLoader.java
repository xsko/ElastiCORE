package io.elasticore.base.model.loader.module;

import io.elasticore.base.model.ConstantParam;
import io.elasticore.base.model.MetaInfo;
import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.core.Items;
import io.elasticore.base.model.entity.EntityAnnotation;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.ModelLoaderContext;
import io.elasticore.base.util.ConsoleLog;
import io.elasticore.base.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Abstract base class for model loaders.
 * Provides common functionalities to parse field information, annotations, and meta-information from entity maps.
 */
public class AbstractModelLoader implements ConstantParam {


    protected ModelLoaderContext modelLoaderContext;

    protected Pattern fieldNamePattern;

    protected void setModelLoaderContext(ModelLoaderContext modelLoaderContext) {
        this.modelLoaderContext = modelLoaderContext;
        String fieldRegx = modelLoaderContext.getConfig("fieldRegx", "^[a-zA-Z_$][a-zA-Z\\d_$]*$");
        fieldNamePattern = Pattern.compile(fieldRegx);
    }


    protected boolean isValidFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return false;
        }
        return fieldNamePattern.matcher(fieldName).matches();
    }

    /**
     * Parses field information from a map where the key is the field name and the value is the field's properties.
     *
     * @param fieldInfo Map containing field names as keys and field properties as values.
     * @return Items containing Field objects parsed from the input map.
     */
    protected Items<Field> parseField(Map<String, String> fieldInfo, String entityName) {
        return parseField(fieldInfo, entityName, null);
    }
    /**
     * Parses field information from a map where the key is the field name and the value is the field's properties.
     *
     * @param fieldInfo Map containing field names as keys and field properties as values.
     * @param entityName String
     * @param metaInfo MetaInfo
     * @return Items containing Field objects parsed from the input map.
     */
    protected Items<Field> parseField(Map<String, String> fieldInfo, String entityName, MetaInfo metaInfo) {
        Items<Field> items = Items.create(Field.class);

        fieldInfo.forEach((fieldNm, fieldPropery) -> {
            Field f = parseFieldLine(fieldNm, fieldPropery ,metaInfo);

            if(entityName!= null && !isValidFieldName(f.getName())) {
                ConsoleLog.storeLog("FIELD_NM_ERR" , entityName+"."+fieldNm);
            }

            // DSL validation: check for common field definition mistakes
            if (entityName != null && fieldPropery != null) {
                validateFieldDsl(entityName, fieldNm, fieldPropery, f);
            }

            items.addItem(f);
        });

        return items;
    }

    /**
     * Validate field DSL for common mistakes.
     */
    private void validateFieldDsl(String entityName, String fieldNm, String fieldLine, Field f) {
        // Check for trailing '!' on annotation (e.g., "@onetomany!" instead of type!)
        if (fieldLine.matches(".*@\\w+!.*")) {
            ConsoleLog.storeLog("DSL_WARN",
                String.format("%s.%s: invalid '!' after annotation in '%s'. Use '!' after type name (e.g., 'TypeName! @annotation')",
                    entityName, fieldNm, fieldLine.trim()));
        }

        // string(255) @length(max=64): the type's length is the one used, so a
        // different max beside it is dropped - said rather than lost.
        String typeLength = null;
        String explicitMax = null;
        for (String name : EntityAnnotation.LENGTH) {
            Annotation first = f.getAnnotation(name);
            if (first == null) continue;
            for (Annotation a : first.getSiblings()) {
                if ("length".equals(name) && typeLength == null && a.getType() == Annotation.TYPE_SINGLE_VALUE) {
                    typeLength = a.getValue();
                } else if (a.getType() == Annotation.TYPE_KEY_VALUE && a.getProperties() != null
                        && explicitMax == null && a.getProperties().getProperty("max") != null) {
                    explicitMax = a.getProperties().getProperty("max");
                }
            }
        }
        // Said by the 3.0 rule engine only: the 2.x extractor and annotation
        // processor fail the build on any DSL warning, and this DSL built there.
        if (io.elasticore.base.model.entity.BaseFieldType.ruleEngineTypes()
                && typeLength != null && explicitMax != null && !typeLength.trim().equals(explicitMax.trim())) {
            ConsoleLog.storeLog("DSL_WARN",
                String.format("%s.%s: the type says length %s and @length says max=%s; %s is used. Write the length once.",
                    entityName, fieldNm, typeLength.trim(), explicitMax.trim(), typeLength.trim()));
        }

        // @pattern('^\d{13}$'): the regex is the content of a string literal,
        // where \d is not a backslash and d - Java rejects it, TypeScript reads
        // a d. Said by the rule engine only, as above.
        if (io.elasticore.base.model.entity.BaseFieldType.ruleEngineTypes()) {
            String escape = loneEscape(f.getAnnotation("pattern"));
            if (escape != null) {
                ConsoleLog.storeLog("DSL_WARN",
                    String.format("%s.%s: @pattern has %s, which a string literal does not read as a backslash. Write it doubled (\\%s) or use a class such as [0-9].",
                        entityName, fieldNm, escape, escape));
            }
        }

        // Check for relationship annotations used with primitive types
        if (f.getTypeInfo() != null && f.getTypeInfo().isBaseType()) {
            String[] relationAnnotations = {"onetomany", "manytoone", "onetoone", "manytomany"};
            for (String rel : relationAnnotations) {
                if (f.hasAnnotation(rel)) {
                    ConsoleLog.storeLog("DSL_WARN",
                        String.format("%s.%s: relationship annotation '@%s' used with primitive type '%s'. Relationship annotations require entity type reference.",
                            entityName, fieldNm, rel, f.getTypeInfo().getBaseTypeName()));
                }
            }
        }
    }

    protected Items<Field> parseField(Map<String, String> fieldInfo) {
        return parseField(fieldInfo, null);
    }

    /**
     * The first single backslash in a @pattern regex that is not one of the
     * escapes a regex needs in a string literal - a doubled backslash, a
     * quote, or a unicode escape - e.g. "\d";
     * null when there is none or no @pattern.
     */
    static String loneEscape(Annotation pattern) {
        if (pattern == null) return null;
        String text = pattern.getProperties() != null
                ? pattern.getProperties().getProperty("regexp") : pattern.value;
        if (text == null) return null;
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) != '\\') continue;
            char next = text.charAt(i + 1);
            if (next == '\\' || next == '"' || next == '\'' || next == 'u') {
                i++;
                continue;
            }
            return "\\" + next;
        }
        return null;
    }


    /**
     * Whether the parameters are a single string in double or single quotes,
     * e.g. "a = b" or 'a = b', with no further quote of the same kind inside.
     */
    static boolean isOneQuotedValue(String parameters) {
        String s = parameters.trim();
        if (s.length() < 2) return false;
        char q = s.charAt(0);
        if (q != '"' && q != '\'') return false;
        return s.charAt(s.length() - 1) == q && s.indexOf(q, 1) == s.length() - 1;
    }

    /** key=value, key="value" or a single value - the 2.x reading of annotation parameters. */
    private static final Pattern KV =
            Pattern.compile("(\\w+)\\s*=\\s*(?:\"([^\"]*)\"|([^,\\s]+))|([^,\\s]+)");

    /** The same for @access, whose key may also be '*', the default level. */
    private static final Pattern KV_ACCESS =
            Pattern.compile("(\\w+|\\*)\\s*=\\s*(?:\"([^\"]*)\"|([^,\\s]+))|([^,\\s]+)");

    /**
     * Parses annotation parameters from a string representation.
     *
     * @param annotationName The name of the annotation.
     * @param parameters The string containing all parameters for the annotation.
     * @return An Annotation object populated with the parsed data.
     */
    protected Annotation parseParameters(String annotationName, String parameters) {

        String singleValue = null;
        Properties properties = new Properties();

        if(parameters.indexOf("=")<0) {
            singleValue = parameters;
        }else if(isOneQuotedValue(parameters)) {
            // One quoted string is one value, whatever it contains: an SQL
            // expression such as @formula("(select ... where u.id = created_by)")
            // must not be split into key=value pairs at its '='. Trimmed, so
            // the quotes are the ends of the value and are stripped as usual.
            singleValue = parameters.trim();
        }else {
            //Pattern kvPattern = Pattern.compile("(\\w+)\\s*=\\s*([^,]+)|([^,\\s]+)");
            //Pattern kvPattern = Pattern.compile("(\\w+)\\s*=\\s*\"([^\"]*)\"|(\\w+)\\s*=\\s*([^,\\s]+)");
            // A key is a word - or, in @access only, '*' alone:
            // @access(create=public, *=owner) lost its default to the
            // single-value branch, which another entry then discarded. Every
            // other annotation, a "--" comment's @label included, keeps the
            // 2.x pattern, so what 2.x reads is parsed as it always was.
            Matcher kvMatcher = ("access".equals(annotationName) ? KV_ACCESS : KV).matcher(parameters);

            /*
            while (kvMatcher.find()) {
                if (kvMatcher.group(1) != null) {
                    String key = kvMatcher.group(1);
                    String value = kvMatcher.group(2);
                    value = StringUtils.removeQuotes(value);
                    value = StringUtils.removeDoubleQuotes(value);
                    properties.setProperty(key, value);
                } else if (kvMatcher.group(3) != null) {
                    singleValue = kvMatcher.group(3);
                }
            }
             */

            while (kvMatcher.find()) {
                if (kvMatcher.group(1) != null) {
                    String key = kvMatcher.group(1);
                    String value = kvMatcher.group(2) != null ? kvMatcher.group(2) : kvMatcher.group(3);
                    properties.setProperty(key, value);
                } else if (kvMatcher.group(4) != null) {
                    singleValue = kvMatcher.group(4);
                }
            }
        }


        return Annotation.create(annotationName, singleValue, properties);
    }




    /**
     * Parses meta-information about an entity from a map representation.
     *
     * @param entityMap
     * @param type
     * @param mainClassName
     * @return
     */
    protected MetaInfo parseMetaInfoObject(Map<String, Object> entityMap, String type, String mainClassName) {
        if(entityMap ==null)
            entityMap = new HashMap<>();

        Object infoObj = entityMap.get(PROPERTY_INFO);
        Map<String, Annotation> infoAnnotation = getAnnotationObj(infoObj);


        Object metaInfoObj = entityMap.get(PROPERTY_META);
        Map<String, Annotation> metaAnnotation = null;
        if(metaInfoObj==null) {
            metaAnnotation = Annotation.createAnnotationMap();
            if(type!=null)
                metaAnnotation.put("type", Annotation.create("type",type) );
        }else{
            metaAnnotation = getAnnotationObj(metaInfoObj);
        }

        if(mainClassName!=null) {
            metaAnnotation.put("name", Annotation.create("name",mainClassName) );
        }

        String desc = (String) entityMap.get(PROPERTY_DESC);


        return MetaInfo.creat(infoAnnotation, metaAnnotation ,desc);
    }

    /**
     * Extracts annotation objects from various types of inputs.
     *
     * @param metaInfoObj The object containing meta-information which could be a String or a Map.
     * @return A map of annotation names to Annotation objects.
     */
    protected Map<String, Annotation> getAnnotationObj(Object metaInfoObj) {
        Map<String, Annotation> annotationMap = null;
        if(metaInfoObj instanceof String) {
            String line = metaInfoObj.toString();

            if(line!=null && line.trim().length()>0) {
                annotationMap = loadAnnotationMap(line);

                if(line.indexOf("@")!=0) {
                    String[] parts = line.split(" ", 2);

                    annotationMap.put("type", Annotation.create("type", parts[0],null) );
                }
            }



        }
        else if(metaInfoObj instanceof Map) {
            Map<String, String>  propertyMap = (Map) metaInfoObj;
            annotationMap = new HashMap<>();
            for (Map.Entry<String, String> entry : propertyMap.entrySet()) {
                String nm = entry.getKey().toLowerCase(Locale.ROOT);
                try {

                    Annotation annotation = Annotation.create(nm, entry.getValue(), null);
                    annotationMap.put(nm, annotation);
                }catch (ClassCastException cce) {
                    cce.printStackTrace();
                }
            }
        }

        if(annotationMap!=null)
            return annotationMap;

        return null;
    }

    private String replaceEnvVariables(String input) {

        if(modelLoaderContext==null)
            return input;


        StringBuilder result = new StringBuilder();
        String[] tokens = input.split(" ");

        for (String token : tokens) {
            if (token.startsWith("@env:")) {
                String key = token.substring(5);
                String val = modelLoaderContext.getConfig("annotations."+key);
                if (val!=null) {
                    result.append(" @").append(val);
                } else {
                    result.append(" ").append(token);
                }
            } else {
                result.append(" ").append(token);
            }
        }

        return result.toString().trim();
    }

    /**
     * Loads a map of annotations from a string that represents multiple annotations.
     *
     * @param fieldLine The string containing annotations.
     * @return A map of annotation names to Annotation objects.
     */
    protected Map<String, Annotation> loadAnnotationMap(String fieldLine) {

        fieldLine = replaceEnvVariables(fieldLine);

        boolean chk = false;
        int p0 = fieldLine.indexOf(" --");
        if(p0<0) {
            p0 = fieldLine.indexOf("--");
            if(p0!=0) {
                p0 = -1;
            }else{
                chk = true;
            }

        }
        if(p0>=0) {
            String comment =null;
            if(chk)
                comment = fieldLine.substring(p0+2).trim();
            else
                comment = fieldLine.substring(p0+3).trim();
            if(!comment.isEmpty() && comment.indexOf("@")<0) {
                fieldLine = fieldLine.substring(0,p0);

                String label = comment;
                String desc = null;
                if(comment.indexOf(" | ")>0) {
                    String[] items = comment.split(" \\| ");
                    if(items.length==2) {
                        label = items[0];
                        desc = items[1];
                    }
                }
                fieldLine = fieldLine +" @label("+label+")";
                if(desc!=null)
                    fieldLine = fieldLine +" @desc("+desc+")";
            }
        }

        //Pattern pattern = Pattern.compile("@(\\w+)(?:\\((.*?)\\))?");
        //Pattern pattern = Pattern.compile("@(\\w+(?::\\w+)?)(?:\\((.*?)\\))?");
        //Pattern pattern = Pattern.compile("@(\\w+(?::\\w+)?)(?:\\('([^']*?)'\\))?");
        //Pattern pattern = Pattern.compile("@(\\w+(?::\\w+)?)(?:\\(('([^']*?)'|[^()]*?)\\))?");
        //Pattern pattern = Pattern.compile("@(\\w+(?::\\w+)?)(?:\\(([^()]*(?:\\([^)]*\\))?[^()]*)\\))?");

        //Pattern pattern = Pattern.compile("@([\\w:.]+(?::\\w+)?)(?:\\(([^()]*(?:\\([^)]*\\))?[^()]*)\\))?");
        //Pattern pattern = Pattern.compile("@([\\w:.]+)\\s*\\((.*)\\)", Pattern.DOTALL);

        //Pattern pattern = Pattern.compile("@([\\w:.]+(?:[:]\\w+)?)(?:\\((([^()]|\"[^\"]*\")*?)\\))?",Pattern.DOTALL);
        Pattern pattern = Pattern.compile("@([\\w:.]+(?:[:]\\w+)?)(?:\\((([^()]*|\\([^()]*\\)|\"[^\"]*\")*?)\\))?", Pattern.DOTALL);









        Matcher matcher = pattern.matcher(fieldLine);

        Map<String, Annotation> annotationMap = Annotation.createAnnotationMap();
        while (matcher.find()) {
            String annotationName = matcher.group(1);
            String attributeParameters = matcher.group(2);
            int p = annotationName.indexOf(":");
            if(p<0) {
                annotationName = annotationName.toLowerCase(Locale.ROOT);
            }

            Annotation annotation = null;
            if(p>0) {
                String orginAnnotationName = annotationName.substring(p+1);
                String annotationFullTxt = "@"+orginAnnotationName;
                if(attributeParameters!=null && attributeParameters.length()>0) {
                    annotationFullTxt= "@"+orginAnnotationName+"("+attributeParameters+")";

                }
                annotation = Annotation.create(annotationName, annotationFullTxt, null);
            }
            else if (attributeParameters != null && !attributeParameters.isEmpty()) {
                annotation = parseParameters(annotationName, attributeParameters);
            } else {
                annotation = Annotation.create(annotationName);

            }
            Annotation preAnnot = annotationMap.get(annotation.getName());
            if(preAnnot!=null) {
                preAnnot.getSiblings().add(annotation);
            }else {
                annotationMap.put(annotation.getName(), annotation);
            }

        }
        return annotationMap;
    }

    /**
     * Parses a single field line to extract field information and annotations.
     *
     * @param fieldNm The name of the field.
     * @param fieldLine The string representation of the field and its annotations.
     * @return A Field object populated with the parsed information.
     */
    protected Field parseFieldLine(String fieldNm, String fieldLine) {
        return parseFieldLine(fieldNm, fieldLine, null);
    }

    /**
     * Parses a single field line to extract field information and annotations.
     *
     * @param fieldNm The name of the field.
     * @param fieldLine The string representation of the field and its annotations.
     * @param metaInfo MetaInfo
     * @return A Field object populated with the parsed information.
     */
    protected Field parseFieldLine(String fieldNm, String fieldLine ,MetaInfo metaInfo) {
        if("--".equals(fieldLine)) {
            fieldLine = "undefined @disable";
        }

        // for comment
        /*
        int p0 = fieldLine.indexOf(" --");
        if(p0>0) {
            String comment = fieldLine.substring(p0+3).trim();
            if(!comment.isEmpty()) {
                fieldLine = fieldLine.substring(0,p0);
                fieldLine = fieldLine +" @label("+comment+")";
            }
        }

         */


        String[] parts = fieldLine.split(" ", 2);
        String type = parts[0];
        fieldLine = parts.length > 1 ? parts[1] : "";



        // for require
        int p =type.indexOf("!");
        if(p>0) {
            type = type.replace("!", "");
            fieldLine = "@notnull "+ fieldLine;
        }
        // for length and BigDecimal precision/scale
        int p2 =type.indexOf("(");
        if(p2>0) {
            String lenInfo = type.substring(p2);
            String baseType = type.substring(0,p2);
            
            // Handle BigDecimal/decimal types differently - preserve full type string
            if ("bigdecimal".equalsIgnoreCase(baseType) || "decimal".equalsIgnoreCase(baseType) || "biginteger".equalsIgnoreCase(baseType)
                    || "money".equalsIgnoreCase(baseType)) {
                // Keep the full type string with precision/scale for BigDecimal types
                // Don't split it into separate parts
                // type remains as "bigdecimal(15,2)" or "decimal(15,2)"
            } else {
                // For other types, use the original length logic
                type = baseType;
                fieldLine = "@length"+lenInfo +" "+fieldLine;
            }
        }

        if("longtext".equals(type)) {
            type="string";
            fieldLine = "@longtext "+fieldLine;
        }
        else if("text".equals(type)) {
            type="string";
            fieldLine = "@text "+fieldLine;
        }
        // Large objects and JSON: a string or bytes, with what the column is.
        else if("clob".equals(type)) {
            type="string";
            fieldLine = "@lob "+fieldLine;
        }
        else if("blob".equals(type)) {
            type="byte[]";
            fieldLine = "@lob "+fieldLine;
        }
        else if("json".equals(type)) {
            type="string";
            fieldLine = "@columndef(json) "+fieldLine;
        }
        else if("HashMap".equals(type)) {
            type="java.util.HashMap";
        }
        else if("Object".equals(type)) {
            type="java.lang.Object";
        }




        Map<String, Annotation> annotationMap = loadAnnotationMap(fieldLine);


        // Automatically adds the "search" annotation if the "dto" annotation is defined in the entity
        if(metaInfo!=null && metaInfo.hasMetaAnnotation(EntityAnnotation.META_DTO)) {

            //if( !annotationMap.containsKey("s") && !annotationMap.containsKey("searchable")) {
            if(!StringUtils.hasKey(annotationMap, EntityAnnotation.SEARCH)) {

                annotationMap.put(EntityAnnotation.AUTOSEARCH, Annotation.create(EntityAnnotation.AUTOSEARCH));
                String typeNm = type.toLowerCase();

                if(metaInfo.hasMetaAnnotation(EntityAnnotation.SEARCH)) {

                }
                else if("string".equals(typeNm)) {
                    annotationMap.put("searchable", Annotation.create("searchable"));
                }
                else if("localdate".equals(typeNm) || "localtime".equals(typeNm)  || "localdatetime".equals(typeNm)
                        || "datetime".equals(typeNm)   || "date".equals(typeNm)
                ) {
                    annotationMap.put("searchable", Annotation.create("searchable","between"));
                }
                else {
                    annotationMap.put("searchable", Annotation.create("searchable", "eq"));
                }
            }
        }

        // Populate Field.description from parsed "--" comment annotations so that
        // field.getDescription() returns the comment text uniformly in build-time and runtime
        // loading paths. loadAnnotationMap() converts "-- label" into @label(label) and
        // "-- label | desc" into @label(label) + @desc(desc). Match SchemaCodePublisher's
        // resolution order: prefer @label (always present for "--" comments), fall back to @desc.
        String parsedDesc = null;
        Annotation labelAnno = annotationMap.get("label");
        if (labelAnno != null) parsedDesc = labelAnno.getValue();
        if (parsedDesc == null) {
            Annotation descAnno = annotationMap.get("desc");
            if (descAnno != null) parsedDesc = descAnno.getValue();
        }

        return Field.builder().name(fieldNm)
                .type(type)
                .parentMetaInfo(metaInfo)
                .isPrimaryKey(annotationMap.containsKey("id"))
                .unique(annotationMap.containsKey("unique"))
                .description(parsedDesc)
                .annotationMap(annotationMap).build();

    }

    /**
     * Placeholder method for completing the load process.
     * Override this method in subclasses to implement specific finalization steps if needed.
     */
    public void completeLoad() {
        // This method can be overridden to perform final steps after loading is complete
    }

}
