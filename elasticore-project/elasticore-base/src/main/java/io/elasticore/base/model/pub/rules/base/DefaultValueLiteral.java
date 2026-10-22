package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;

import java.util.Locale;

/**
 * Renders an @default value as a literal of the field's own type, ported from
 * getDefaultValueSetup. Entity and DTO emission share it: 2.x applies the same
 * conversion in both, which is why a long default reads "0L" in the DTO too.
 */
public final class DefaultValueLiteral {

    private DefaultValueLiteral() {
    }

    /** Whether the loaded context knows the type as an enumeration. */
    private static boolean isGlobalEnum(String typeName) {
        if (typeName == null) return false;
        try {
            io.elasticore.base.ECoreModelContext ctx =
                    io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            return ctx != null && ctx.findModelComponent(typeName,
                    io.elasticore.base.model.enums.EnumModel.class) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The DTO variant, ported from DtoSrcFilePublisher.getDefaultValueSetup:
     * temporal defaults are dropped - a DTO date stays null - and BigDecimal
     * literals keep their fully-qualified names. Null means "no initializer",
     * and without one the DTO gets no @Builder.Default either.
     */
    public static String forDto(EntitySpec e, FieldSpec f, String val) {
        if (val == null) return null;

        if (e.isKnownEnum(f.coreItemType()) || isGlobalEnum(f.coreItemType())) {
            if (val.contains(".")) return val;
            return String.format("%s.%s", f.coreItemType(), val);
        }

        String base = f.baseTypeName();
        if ("DATE".equals(base) || "DATETIME".equals(base) || "TIME".equals(base) || "INSTANT".equals(base)) {
            return null;
        }

        if (f.isMoney() || f.isQuantity()) return valueType(f, val);
        if (f.isBigDecimal()) {
            if ("0".equals(val) || "0.0".equals(val) || "0.00".equals(val)) return "java.math.BigDecimal.ZERO";
            if ("1".equals(val) || "1.0".equals(val) || "1.00".equals(val)) return "java.math.BigDecimal.ONE";
            if ("10".equals(val) || "10.0".equals(val) || "10.00".equals(val)) return "java.math.BigDecimal.TEN";
            return String.format("new java.math.BigDecimal(%s)", RuleText.quote(val));
        }

        return of(e, f, val);
    }

    /** Money.of("0.00") / Quantity.of(0): written in full, the same in Java and Kotlin. */
    private static String valueType(FieldSpec f, String val) {
        if (f.isMoney()) return String.format("io.elasticore.springboot3.domain.Money.of(%s)", RuleText.quote(val));
        return String.format("io.elasticore.springboot3.domain.Quantity.of(%s)", val);
    }

    public static String of(EntitySpec e, FieldSpec f, String val) {
        if (val == null) return null;

        // Enumeration constants get their type prefix. 2.x resolves the type
        // through the global context, so another domain's enum counts too.
        if (e.isKnownEnum(f.coreItemType()) || isGlobalEnum(f.coreItemType())) {
            if (val.contains(".")) return val;
            return String.format("%s.%s", f.coreItemType(), val);
        }

        String base = f.baseTypeName();

        if ("now".equalsIgnoreCase(val)) {
            if ("DATE".equals(base)) return "LocalDate.now()";
            if ("DATETIME".equals(base)) return "LocalDateTime.now()";
            if ("TIME".equals(base)) return "LocalTime.now()";
            // timestamp (an instant): a rule-engine type, written in full - 2.x has no such field.
            if ("INSTANT".equals(base)) return "java.time.Instant.now()";
        }
        if ("DATE".equals(base) || "DATETIME".equals(base) || "TIME".equals(base) || "INSTANT".equals(base)) {
            return val;
        }

        if (f.isStringType()) {
            if (val.startsWith("\"") && val.endsWith("\"")) return val;
            if (val.startsWith("'") && val.endsWith("'")) {
                return RuleText.quote(val.substring(1, val.length() - 1));
            }
            return RuleText.quote(val);
        }
        if (f.isMoney() || f.isQuantity()) return valueType(f, val);
        if (f.isBigDecimal()) {
            if ("0".equals(val) || "0.0".equals(val) || "0.00".equals(val)) return "BigDecimal.ZERO";
            if ("1".equals(val) || "1.0".equals(val) || "1.00".equals(val)) return "BigDecimal.ONE";
            if ("10".equals(val) || "10.0".equals(val) || "10.00".equals(val)) return "BigDecimal.TEN";
            return String.format("new BigDecimal(%s)", RuleText.quote(val));
        }
        if ("LONG".equals(base) || "P_LONG".equals(base)) {
            return val.toUpperCase(Locale.ROOT).endsWith("L") ? val : String.format("%sL", val);
        }
        if ("FLOAT".equals(base) || "P_FLOAT".equals(base)) {
            return val.toUpperCase(Locale.ROOT).endsWith("F") ? val : String.format("%sF", val);
        }
        if ("DOUBLE".equals(base) || "P_DOUBLE".equals(base)) {
            boolean needsPoint = !val.toUpperCase(Locale.ROOT).endsWith("D") && !val.contains(".");
            return needsPoint ? String.format("%s.0", val) : val;
        }
        return val;
    }
}
