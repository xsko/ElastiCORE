package io.elasticore.base.model.entity;


import io.elasticore.base.ECoreModelContext;
import io.elasticore.base.model.DataModelComponent;
import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.core.BaseECoreModelContext;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */


public class TypeInfo {

    private BaseFieldType baseFieldType;

    private String initTypeInfo;

    private String baseTypeName;
    private String typeParameterName;

    private boolean isGenericType = false;

    private Map<String, Annotation> annotationMap;

    private Set<String> types;
    
    // BigDecimal precision and scale support
    private int precision = -1;
    private int scale = -1;

    public TypeInfo(String typeInfo) {
        if (typeInfo == null || typeInfo.length() < 2)
            throw new IllegalArgumentException("type info is not normal. [" + typeInfo + "]");

        this.initTypeInfo = simpleNamed(typeInfo);
        this.types = extractTypes(this.initTypeInfo);
        loadInfo();
    }

    TypeInfo(String typeInfo, Map<String, Annotation> annotationMap) {
        this.annotationMap = annotationMap;
        if (typeInfo == null || typeInfo.length() < 2)
            throw new IllegalArgumentException("type info is not normal. [" + typeInfo + "]");

        this.initTypeInfo = simpleNamed(typeInfo);
        this.types = extractTypes(this.initTypeInfo);
        loadInfo();

    }

    /** A domain written before a type name: common:Grade, List&lt;common:ContactDTO&gt;. */
    private static final Pattern DOMAIN_PREFIX = Pattern.compile("(?<![\\w.$])[A-Za-z_][\\w-]*:(?=\\s*[A-Za-z_])");

    /**
     * A type of another domain is named by its simple name: a type name is unique across
     * domains, and the rule engine imports it from the domain that declares it (issue 331).
     * The domain written before it (common:Grade), which 2.x accepted, is dropped there, so every
     * rule reads Grade. The 2.x generator, which writes such a type by its full class name, reads
     * it as written: the rule engine turns this on while it runs (BaseFieldType.ruleEngineTypes).
     */
    public static String simpleNamed(String typeInfo) {
        if (typeInfo == null || !BaseFieldType.ruleEngineTypes() || typeInfo.indexOf(':') < 0) return typeInfo;
        return DOMAIN_PREFIX.matcher(typeInfo).replaceAll("");
    }

    public Set<String> getTypes() {
        return this.types;
    }

    private static Set<String> extractTypes(String input) {
        Set<String> result = new HashSet<>();

        // Regular expression to match fully qualified class names and generic types
        String regex = "\\b[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*\\b";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            result.add(matcher.group());
        }

        return result;
    }

    /**
     *
     */
    private void loadInfo() {

        // Check for BigDecimal with precision and scale: bigdecimal(15.2) or decimal(15,2)
        Pattern bigDecimalPattern = Pattern.compile("^(decimal|bigdecimal)\\s*\\(\\s*(\\d+)\\s*[.,]\\s*(\\d+)\\s*\\)$", Pattern.CASE_INSENSITIVE);
        Matcher bigDecimalMatcher = bigDecimalPattern.matcher(initTypeInfo.trim());
        
        if (bigDecimalMatcher.matches()) {
            baseTypeName = "BigDecimal";
            precision = Integer.parseInt(bigDecimalMatcher.group(2));
            scale = Integer.parseInt(bigDecimalMatcher.group(3));
            baseFieldType = BaseFieldType.BIGDECIMAL;
            return;
        }

        // money or money(14.2): an amount, stored as a decimal of that size.
        Matcher money = Pattern.compile("^money\\s*(?:\\(\\s*(\\d+)\\s*[.,]\\s*(\\d+)\\s*\\))?$",
                Pattern.CASE_INSENSITIVE).matcher(initTypeInfo.trim());
        if (money.matches()) {
            baseTypeName = "Money";
            precision = money.group(1) == null ? 19 : Integer.parseInt(money.group(1));
            scale = money.group(2) == null ? 2 : Integer.parseInt(money.group(2));
            baseFieldType = BaseFieldType.MONEY;
            return;
        }

        int p = initTypeInfo.indexOf("<");
        if (p > 0) {
            isGenericType = true;
            baseTypeName = initTypeInfo.substring(0, p);
            typeParameterName = initTypeInfo.substring(p + 1, initTypeInfo.length() - 1);

        } else
            baseTypeName = initTypeInfo;

        // Words the DSL documents for a type it knows by another name.
        if (!isGenericType) {
            String alias = ALIASES.get(baseTypeName.toLowerCase());
            if (alias != null) baseTypeName = alias;
        }

        baseFieldType = BaseFieldType.findByName(baseTypeName);
    }

    /**
     * decimal without a size is BigDecimal, as decimal(p.s) is; timestamp is
     * an instant - a point on the time line, whatever the zone - which is
     * what a column called a timestamp holds.
     */
    private static final Map<String, String> ALIASES = new java.util.HashMap<String, String>();

    static {
        ALIASES.put("decimal", "BigDecimal");
        ALIASES.put("timestamp", "Instant");
    }

    public static String getParameterTypeName(String initTypeInfo) {
        int p = initTypeInfo.indexOf("<");
        if (p > 0) {
            return initTypeInfo.substring(p + 1, initTypeInfo.length() - 1);
        }
        return initTypeInfo;
    }

    public static boolean isList(String initTypeInfo ) {
        if( initTypeInfo.indexOf("List<") ==0 )
            return true;

        return false;
    }

    public boolean equals(BaseFieldType type) {
        if(baseFieldType!=null && baseFieldType == type)
            return true;

        return false;
    }

    public boolean isStringType() {
        if(baseFieldType==BaseFieldType.STRING)
            return true;

        return false;
    }

    public boolean isList() {
        if (baseFieldType == BaseFieldType.LIST)
            return true;
        return false;
    }

    public String getCoreItemType() {
        if(isGenericType()) {
            return getTypeParameterName();
        }

        return getDefaultTypeName();
    }

    public BaseFieldType getCoreItemBaseFieldType() {

        String coreType =getCoreItemType();
        BaseFieldType byName = BaseFieldType.findByName(coreType);
        if(byName!=null && byName != BaseFieldType.UNKNOWN) {
            return byName;
        }

        return null;
    }


    public String getDefaultTypeName() {
        if(isBaseType() && !isList()) {

            if(annotationMap!=null && annotationMap.containsKey("id")) {
                return this.baseFieldType.getWrapperClassName();
            }

            return this.baseFieldType.getWrapperClassName();
        }

        //if(this.initTypeInfo.indexOf(":")>0)
        /*
        {
            // external domain type
            ECoreModelContext context = BaseECoreModelContext.getContext();
            if(context!=null) {
                DataModelComponent modelComponent = context.findModelComponent(this.initTypeInfo);
                if(modelComponent!=null) {
                    return modelComponent.getFullName();
                }
            }
        }*/

        return this.initTypeInfo;
    }

    public boolean isBaseType() {
        if (baseFieldType == BaseFieldType.UNKNOWN) {
            return false;
        }
        if (baseFieldType == BaseFieldType.LIST) {
            return false;
        }
        return true;
    }

    public BaseFieldType getBaseFieldType() {
        return this.baseFieldType;
    }


    public String getInitTypeInfo() {
        return initTypeInfo;
    }

    public String getBaseTypeName() {
        return baseTypeName;
    }

    public String getTypeParameterName() {
        return typeParameterName;
    }

    public boolean isTypeParameterBaseType() {
        if(typeParameterName==null || typeParameterName.isEmpty())
            return false;
        String typeNm = typeParameterName.toLowerCase();
        if("string".equals(typeNm) || "integer".equals(typeNm) || "long".equals(typeNm)) {
            return true;
        }
        return false;
    }

    public boolean isGenericType() {
        return isGenericType;
    }
    
    public boolean isMoney() {
        return baseFieldType == BaseFieldType.MONEY;
    }

    public boolean isQuantity() {
        return baseFieldType == BaseFieldType.QUANTITY;
    }

    public boolean isBigDecimal() {
        return baseFieldType == BaseFieldType.BIGDECIMAL;
    }
    public boolean isBigInteger() {
        return baseFieldType == BaseFieldType.BIGINTEGER;
    }
    
    public int getPrecision() {
        return precision;
    }
    
    public int getScale() {
        return scale;
    }
    
    public boolean hasPrecisionScale() {
        return precision > 0 && scale >= 0;
    }
}
