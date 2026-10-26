package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldCode;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.util.StringUtils;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Base rules for the search DTO, ported from SearchDtoSrcFilePublisher.
 *
 * A searchable field becomes one property of the search DTO, documented with
 * what its condition means. A between condition needs two ends, so it becomes
 * a From/To pair; an equality on a list searches for one element, so it
 * becomes a single item of the element's type.
 *
 * A @searchable DTO with no @search annotations searches every field: text by
 * pattern, temporal by range, everything else by equality.
 */
public class SearchDtoBaseRules implements CodeRules {


    /**
     * A field that names its DTO type is declared with that type: a relation
     * appears as the counterpart's DTO, not as the entity.
     */
    @Rule(value = "srchdto-type", target = "srchdto", order = 1)
    void dtoType(EntitySpec e, FieldSpec f) {
        String declared = f.dtoTypeName();
        if (declared != null && !declared.isEmpty()) {
            ctx(e).field(f).type(declared);
        }
    }

    /** The prose above the declaration, as the DTO and entity artifacts do. */
    @Rule(value = "srchdto-field-desc", target = "srchdto", order = 5)
    void fieldDesc(EntitySpec e, FieldSpec f) {
        String desc = f.label();
        if (desc == null) return;
        ctx(e).field(f)
              .before("/*")
              .before("  %s", desc)
              .before("*/");
    }

    @Rule(value = "srchdto-field", target = "srchdto", order = 10)
    void field(EntitySpec e, FieldSpec f) {
        // A relation is searched by the identity flattened onto this DTO -
        // parentCategoryCatId rather than parentCategory - so the relation
        // itself is not a search property. Its flattened field carries the
        // condition, and was added alongside it.
        if (f.hasDsl("deferred") && f.isEntityRef()) {
            ctx(e).removeField(f);
            return;
        }

        // The same relation searched twice: customerCustomerId through the
        // join and customerId on the root's own column. The identity the DDD
        // reference wrote is kept and the flattening dropped. Only a DDD run
        // writes @idof, so the 2.x output never reaches this.
        if (flattensAReferenceById(e, f)) {
            ctx(e).removeField(f);
            return;
        }

        // Nor is a collection of them. A list is searchable when its items
        // are values - a base type, or an enum of the model - and a list of
        // entities is a relation the other side owns.
        if (f.isList()
                && !f.isBaseItemType()
                && !e.isKnownEnum(f.coreItemType())) {
            ctx(e).removeField(f);
            return;
        }

        String condition = conditionOf(e, f);
        if (condition == null) {
            // A field the DTO's own body declares stays, as a plain property
            // without a condition; one the referenced entity carried in was
            // only worth keeping for its condition.
            if (f.declaredInDto()) {
                plainProperty(e, f);
            } else {
                ctx(e).removeField(f);
            }
            return;
        }

        // "@search(like,alias)" renames the property it produces.
        String[] parts = splitCondition(f.searchValue());
        String name = parts.length == 2 ? parts[1] : f.name();
        String conditionCode = effectiveCondition(ctx(e), f, parts[0].isEmpty() ? condition : parts[0]);

        // "@search(isnull)" asks whether the column is empty, so the caller
        // sends a yes or no rather than a value of the field's own type.
        // Clean output only: the 2.x publisher reads it as an equality.
        if (isNullCheck(conditionCode) && !ctx(e).compatible()) {
            nullCheckProperty(e, f, name);
            return;
        }

        ctx(e).field(f).ensure(AnnoCode.raw(
                "io.swagger.v3.oas.annotations.media.Schema", schemaLine(e, f, conditionCode)));

        String pattern = FieldDoc.formatPattern(f);
        if (pattern != null) {
            ctx(e).field(f)
                    .ensure(AnnoCode.of("org.springframework.format.annotation.DateTimeFormat")
                            .qualified().val("pattern", pattern))
                    .ensure(AnnoCode.of("com.fasterxml.jackson.annotation.JsonFormat")
                            .qualified().val("pattern", pattern));
        }

        if (isRange(conditionCode)) {
            rangeProperty(e, f, name, pattern);
            return;
        }

        if (f.isList() && isEquality(conditionCode)) {
            // One element of the list is what the caller searches for.
            ctx(e).field(f).type(f.coreItemType()).rename(String.format("%sItem", name));
            return;
        }

        if (!name.equals(f.name())) ctx(e).field(f).rename(name);
    }

    /**
     * A declared field without a condition: documented like a DTO field -
     * required when not null - and formatted, but searching by nothing.
     */
    private void plainProperty(EntitySpec e, FieldSpec f) {
        String declared = f.dslDescription();
        // 2.x appends the empty condition text after a space, so a labeled
        // field keeps a trailing space inside the quotes.
        String desc = declared == null ? "" : String.format("%s ", declared);
        String req = f.required() ? ", requiredMode=Schema.RequiredMode.REQUIRED " : " ";

        ctx(e).field(f).ensure(AnnoCode.raw(
                "io.swagger.v3.oas.annotations.media.Schema",
                String.format("@Schema(description = %s %s%s)",
                        RuleText.quote(desc), req, FieldDoc.exampleAttribute(e, f))));

        String pattern = FieldDoc.formatPattern(f);
        if (pattern != null) {
            ctx(e).field(f)
                    .ensure(AnnoCode.of("org.springframework.format.annotation.DateTimeFormat")
                            .qualified().val("pattern", pattern))
                    .ensure(AnnoCode.of("com.fasterxml.jackson.annotation.JsonFormat")
                            .qualified().val("pattern", pattern));
        }
    }

    /**
     * The two ends of a range. The second carries the format annotations again
     * but no @Schema, which is how 2.x documents the pair as one thing.
     */
    private void rangeProperty(EntitySpec e, FieldSpec f, String name, String pattern) {
        FieldCode from = ctx(e).field(f).rename(String.format("%sFrom", name));

        if (pattern != null) {
            from.after("@org.springframework.format.annotation.DateTimeFormat(pattern = %s)", RuleText.quote(pattern))
                .after("@com.fasterxml.jackson.annotation.JsonFormat(pattern = %s)", RuleText.quote(pattern));
        }
        // The upper end is a second property this rule writes itself rather
        // than one the model declared, so it goes through the language seam -
        // written as Java text it lands verbatim in a Kotlin file.
        Syntax syntax = ctx(e).syntax();
        from.after("%s", syntax.declaration(syntax.fieldModifier(), f.typeName(),
                String.format("%sTo", name), "", false));
    }

    /**
     * The three states of an emptiness check: absent searches by nothing,
     * true finds the rows whose column is empty, false the ones where it is
     * set. The field's own type, its date format and its example describe a
     * value the caller no longer sends, so none of them is carried over.
     */
    private void nullCheckProperty(EntitySpec e, FieldSpec f, String name) {
        ctx(e).field(f)
              .ensure(AnnoCode.raw("io.swagger.v3.oas.annotations.media.Schema",
                      String.format("@Schema(description = %s  )", RuleText.quote(conditionText(f,
                              StringUtils.getOperatorDescription("isnull", true))))))
              .type("Boolean")
              .rename(String.format("%sEmpty", name));
    }

    /** The @Schema line, whose description says what the condition means. */
    private String schemaLine(EntitySpec e, FieldSpec f, String conditionCode) {
        // A search property is never required: an absent value means "any".
        return String.format("@Schema(description = %s  %s)",
                RuleText.quote(conditionText(f, StringUtils.getOperatorDescription(conditionCode,
                        !ctx(e).compatible()))),
                FieldDoc.exampleAttribute(e, f));
    }

    /** The field's own description followed by what its condition means. */
    private static String conditionText(FieldSpec f, String operatorDescription) {
        String meaning = String.format("%s field:%s", operatorDescription, f.name());

        String declared = f.dslDescription();
        return declared == null ? meaning : String.format("%s %s", declared, meaning);
    }

    /**
     * Whether a field flattens the key of a relation - customerCustomerId,
     * @ref(customer.customerId) - that is also held by its identity, the
     * field a DDD reference wrote and marked @idof(customer). The two carry
     * the same value. A @ref to anything else on the relation - its name -
     * is not this, and stays.
     *
     * Shared by the DTO, the search DTO and the mapper, which must agree.
     */
    public static boolean flattensAReferenceById(EntitySpec e, FieldSpec f) {
        String ref = f.dslValue("ref");
        if (ref == null || ref.indexOf('.') < 0) return false;
        String relation = ref.substring(0, ref.indexOf('.'));
        String child = ref.substring(ref.indexOf('.') + 1);
        if (child.isEmpty() || child.indexOf('.') >= 0) return false;
        String flattened = relation + Character.toUpperCase(child.charAt(0)) + child.substring(1);
        for (FieldSpec other : e.fields()) {
            if (other == f || !relation.equals(other.dslValue("idof"))) continue;
            // The identity field is named for the key it holds.
            if (other.name().equals(child) || other.name().equals(flattened)) return true;
        }
        return false;
    }

    /**
     * The condition a field searches by, or null when it is not searchable.
     *
     * Which fields reach this rule is decided when the search DTO's model is
     * built, not here: an @searchable(entity=X) DTO carries the fields worth
     * searching plus whatever it declares itself. What is decided here is how
     * each of them searches - an @expose DTO derives a condition from the
     * field's type, so text matches by pattern, temporal by range, and
     * everything else by equality.
     */
    private String conditionOf(EntitySpec e, FieldSpec f) {
        String declared = f.searchValue();
        if (declared != null && !declared.isEmpty()) return declared;
        if (f.hasSearch()) return "auto";

        if (!e.hasMeta("expose")) return null;

        // A value no reply shows is not one to search by either: a derived
        // "like" on a password let anyone read its hash one prefix at a time.
        // An explicit @search above still wins. Clean output only.
        if (!ctx(e).compatible() && f.hasDslAny("writeonly", "nooutput", "password")) return null;

        String derived;
        if (f.isStringType()) derived = "auto";
        else if (f.isDate() || f.isDateTime()) derived = "between";
        else derived = "eq";

        // 2.x writes the derived condition into the field's annotation map,
        // and inherited audit fields are shared objects: once one @expose DTO
        // derives their conditions, every later search DTO sees them declared.
        // Reproduced as is - order-dependent, but the order is load order.
        f.raw().getAnnotationMap().put("search",
                io.elasticore.base.model.core.Annotation.create("search", derived));
        return derived;
    }

    private static boolean isRange(String condition) {
        return "between".equals(condition) || "~".equals(condition);
    }

    private static boolean isEquality(String condition) {
        return "eq".equals(condition) || "=".equals(condition);
    }

    /**
     * The condition a field searches by. In the clean output a string's
     * like is contains-ignoring-case, what %__% means: "search by part of
     * the name" found nothing for "gold" on H2, PostgreSQL and Oracle,
     * whose LIKE keeps case. %_% and %% keep case; 2.x keeps its like.
     */
    public static String effectiveCondition(SourceCode c, FieldSpec f, String condition) {
        if (c.compatible()) return condition;
        if (f.isStringType() && "like".equals(condition)) return "%__%";
        return symbolOf(condition);
    }

    /**
     * A comparison keyword as its symbol: gt is >, gte and ge are >=, lt is <,
     * lte and le are <=, ne and neq are !=. Clean output only. The 2.2 site
     * documented the keywords, 2.x read them as an equality, and an agent
     * writes them as often as the symbols - so the clean output reads them as
     * they were meant. The 2.x-compatible output keeps its equality.
     */
    static String symbolOf(String condition) {
        if (condition == null) return null;
        switch (condition.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "gt": return ">";
            case "gte": case "ge": return ">=";
            case "lt": return "<";
            case "lte": case "le": return "<=";
            case "ne": case "neq": return "!=";
            default: return condition;
        }
    }

    /**
     * Whether a condition checks for an empty column. Shared with the mapper,
     * which must read the same property the search DTO declares.
     */
    public static boolean isNullCheck(String condition) {
        return "isnull".equals(condition);
    }

    /** Splits "like,alias" into its condition and the property it names. */
    private static String[] splitCondition(String condition) {
        if (condition == null || condition.isEmpty()) return new String[]{""};
        int comma = condition.indexOf(',');
        if (comma < 0) return new String[]{condition};
        return new String[]{condition.substring(0, comma).trim(),
                condition.substring(comma + 1).trim()};
    }
}
