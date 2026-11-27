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

    /**
     * A search value is no longer than the column it is compared with: the
     * length declared (string(200), or the DTO field's own string(50)) is its
     * {@code @Size}, as in the DTO; the search body is validated already (@Valid).
     * After every other rule - a project's own @Size (rule dev guide, a like
     * needs 2 or more characters) stays as it wrote it - and only on a field
     * still a String of its own. Clean output only (issue 169).
     */
    @Rule(value = "srchdto-size", target = "srchdto", order = 1, phase = Rule.Phase.TAIL)
    void size(EntitySpec e) {
        if (ctx(e).compatible()) return;
        for (FieldSpec f : e.fields()) {
            if (f.isList()) {
                listSize(e, f);
                continue;
            }
            if (!f.isStringType() || f.maxLength() <= 0) continue;
            io.elasticore.base.model.pub.rules.FieldCode code = ctx(e).field(f);
            if (code == null || code.isRemoved()) continue;
            // A field another rule turned into something else - a yes or no for isnull, a list - is no text.
            String declared = code.declaredType();
            if (declared != null && !"String".equals(declared) && !"java.lang.String".equals(declared)) continue;
            code.ensure(AnnoCode.raw("jakarta.validation.constraints.Size", "@Size(max=%d)", f.maxLength()));
        }
    }

    /**
     * A list of values searched for (@search(in)) holds what @minsize and @maxsize allow, as the DTO's list does
     * (issue 324): @Size, with the declared message. Without it one request could bind any number of values into
     * one in (...). A list another rule turned into one element (an equality on a list) is no list any more.
     */
    private void listSize(EntitySpec e, FieldSpec f) {
        String min = f.dslValueAny("minsize");
        String max = f.dslValueAny("maxsize");
        if (min == null && max == null) return;
        FieldCode code = ctx(e).field(f);
        if (code == null || code.isRemoved()) return;
        String declared = code.declaredType();
        if (declared != null && !declared.contains("List")) return;
        String[] minBound = min == null ? null : DtoBaseRules.Bound.split(min);
        String[] maxBound = max == null ? null : DtoBaseRules.Bound.split(max);
        StringBuilder sb = new StringBuilder();
        String message = null;
        if (minBound != null) {
            sb.append("min=").append(minBound[0].trim());
            message = minBound[1];
        }
        if (maxBound != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("max=").append(maxBound[0].trim());
            if (message == null) message = maxBound[1];
        }
        if (message != null) sb.append(", message=").append(RuleText.quote(message));
        code.ensure(AnnoCode.raw("jakarta.validation.constraints.Size", "@Size(%s)", sb.toString()));
    }

    /**
     * One property of the search DTO: what it is called, the field it
     * searches, the condition it searches by and, for a range, which end it
     * is. The search DTO is rendered from these, so whatever else describes a
     * search (issue 373) reads the same decisions rather than making them again.
     */
    public static final class SearchProperty {

        /** What a property is: how the search DTO declares it. */
        public enum Kind {
            /** A field the DTO declares that searches by nothing: a plain property, op null. */
            PLAIN,
            /** One value compared by the condition, under the field's name or its alias. */
            VALUE,
            /** One end of a range, xFrom or xTo (between, ~). */
            RANGE,
            /** One element searched for in a list (an equality on a list), xItem. */
            ITEM,
            /** Whether the column is empty (isnull, clean output): a yes or no, xEmpty. */
            EMPTY,
            /** The words of one search box, looked for in other fields ({@code @search(words: a, b)}). */
            WORDS
        }

        /** The end of a range a RANGE property is. */
        public static final String FROM = "from";

        public static final String TO = "to";

        private final String name;
        private final FieldSpec field;
        private final String op;
        private final Kind kind;
        private final String end;
        private final String alias;
        private final java.util.List<String> words;

        SearchProperty(
                String name,
                FieldSpec field,
                String op,
                Kind kind,
                String end,
                String alias,
                java.util.List<String> words) {
            this.name = name;
            this.field = field;
            this.op = op;
            this.kind = kind;
            this.end = end;
            this.alias = alias;
            this.words = words;
        }

        /** The property's name, as a request sends it: priceFrom, statusItem, openEmpty, customerName. */
        public String name() {
            return name;
        }

        /** The field it searches. */
        public FieldSpec field() {
            return field;
        }

        /**
         * The condition, as the search applies it (effectiveCondition): eq, %__%, between, >=, isnull, in,
         * auto; "words" for WORDS; null for PLAIN.
         */
        public String op() {
            return op;
        }

        public Kind kind() {
            return kind;
        }

        /** FROM or TO for a RANGE property; null for every other one. */
        public String end() {
            return end;
        }

        /** The name the condition gives the property ({@code @search(like,customerName)}), or null. */
        public String alias() {
            return alias;
        }

        /** The fields a WORDS property looks for each word in; null for every other one. */
        public java.util.List<String> words() {
            return words;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SearchProperty)) return false;
            SearchProperty p = (SearchProperty) o;
            return name.equals(p.name)
                    && field == p.field
                    && java.util.Objects.equals(op, p.op)
                    && kind == p.kind
                    && java.util.Objects.equals(end, p.end)
                    && java.util.Objects.equals(alias, p.alias)
                    && java.util.Objects.equals(words, p.words);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, field.name(), op, kind, end, alias, words);
        }

        @Override
        public String toString() {
            return String.format(
                    "%s(%s %s %s%s%s)",
                    name, field.name(), kind, op, end == null ? "" : " " + end, words == null ? "" : " " + words);
        }
    }

    /**
     * The properties of a search DTO, in the order of its fields: what its
     * srchdto-field rule decided for each - the same decision it renders
     * from, made again from the spec. It writes nothing (not even
     * FieldSpec.derivedSearch), so it may be read before or after the rule.
     */
    public static java.util.List<SearchProperty> properties(EntitySpec e) {
        SourceCode c = ctx(e);
        java.util.List<SearchProperty> out = new java.util.ArrayList<SearchProperty>();
        for (FieldSpec f : e.fields()) out.addAll(decide(c, e, f).properties);
        return java.util.Collections.unmodifiableList(out);
    }

    /** What the rule decides for one field: its properties, and a condition derived for it, or null. */
    private static final class Decision {
        final java.util.List<SearchProperty> properties;
        final String derived;

        Decision(String derived, SearchProperty... properties) {
            this.derived = derived;
            this.properties = java.util.Arrays.asList(properties);
        }
    }

    /** Decides one field's properties. Writes nothing: the rule writes what it decides. */
    private static Decision decide(SourceCode c, EntitySpec e, FieldSpec f) {
        if (searchedByItsFlattenedId(f)) return new Decision(null);

        // The same relation searched twice: customerCustomerId through the
        // join and customerId on the root's own column. The identity the DDD
        // reference wrote is kept and the flattening dropped. Only a DDD run
        // writes @idof, so the 2.x output never reaches this.
        if (flattensAReferenceById(e, f)) return new Decision(null);

        // Nor is a collection of them. A list is searchable when its items
        // are values - a base type, or an enum of the model - and a list of
        // entities is a relation the other side owns.
        if (f.isList()
                && !f.isBaseItemType()
                && !e.isKnownEnum(f.coreItemType())) {
            return new Decision(null);
        }

        String declared = declaredCondition(f);
        String derived = declared == null ? derivedCondition(e, f, c.compatible()) : null;
        String condition = declared != null ? declared : derived;
        if (condition == null) {
            // A field the DTO's own body declares stays, as a plain property
            // without a condition; one the referenced entity carried in was
            // only worth keeping for its condition.
            return f.declaredInDto()
                    ? new Decision(
                            null, new SearchProperty(f.name(), f, null, SearchProperty.Kind.PLAIN, null, null, null))
                    : new Decision(null);
        }

        // "@search(words: a, b)": the text of one search box, looked for in those fields.
        java.util.List<String> words = wordsOf(f);
        if (words != null) {
            // Not searched in the 2.x-compatible output (the mapper says so): a property a client
            // could send to no effect is left out.
            if (c.compatible()) return new Decision(derived);
            return new Decision(
                    derived, new SearchProperty(f.name(), f, "words", SearchProperty.Kind.WORDS, null, null, words));
        }

        // "@search(like,alias)" renames the property it produces. A derived condition has neither.
        String[] parts = splitCondition(f.searchValue());
        String alias = parts.length == 2 ? parts[1] : null;
        String name = alias != null ? alias : f.name();
        String op = effectiveCondition(c, f, parts[0].isEmpty() ? condition : parts[0]);

        // "@search(isnull)" asks whether the column is empty, so the caller
        // sends a yes or no rather than a value of the field's own type.
        // Clean output only: the 2.x publisher reads it as an equality.
        if (isNullCheck(op) && !c.compatible()) {
            return new Decision(
                    derived, new SearchProperty(name + "Empty", f, op, SearchProperty.Kind.EMPTY, null, alias, null));
        }
        if (isRange(op)) {
            return new Decision(
                    derived,
                    new SearchProperty(
                            name + "From", f, op, SearchProperty.Kind.RANGE, SearchProperty.FROM, alias, null),
                    new SearchProperty(name + "To", f, op, SearchProperty.Kind.RANGE, SearchProperty.TO, alias, null));
        }
        if (f.isList() && isEquality(op)) {
            // One element of the list is what the caller searches for.
            return new Decision(
                    derived, new SearchProperty(name + "Item", f, op, SearchProperty.Kind.ITEM, null, alias, null));
        }
        return new Decision(derived, new SearchProperty(name, f, op, SearchProperty.Kind.VALUE, null, alias, null));
    }

    @Rule(value = "srchdto-field", target = "srchdto", order = 10)
    void field(EntitySpec e, FieldSpec f) {
        Decision decision = decide(ctx(e), e, f);

        // Kept for the mapper's toSpec and MyBatis's search, not written into
        // the field: inherited fields are objects every extending entity
        // shares, and a condition written there reached only the search DTOs
        // that came after this one (issue 376). Which inherited fields a
        // search DTO that is not @expose takes is shared with the 2.x
        // publisher (AutoSearch, marked by SpecFactory); the condition itself
        // is derived here, from this rule's own type tests (derivedCondition),
        // not from AutoSearch.conditionByType.
        if (decision.derived != null) f.derivedSearch(decision.derived);

        java.util.List<SearchProperty> properties = decision.properties;
        if (properties.isEmpty()) {
            ctx(e).removeField(f);
            return;
        }
        SearchProperty p = properties.get(0);
        if (p.kind() == SearchProperty.Kind.PLAIN) {
            plainProperty(e, f);
            return;
        }
        if (p.kind() == SearchProperty.Kind.WORDS) {
            ctx(e).field(f)
                    .ensure(AnnoCode.raw(
                            "io.swagger.v3.oas.annotations.media.Schema",
                            String.format(
                                    "@Schema(description = %s  )",
                                    RuleText.quote(conditionText(
                                            f,
                                            "every word (the first five) in any of "
                                                    + String.join(", ", p.words()))))));
            return;
        }
        if (p.kind() == SearchProperty.Kind.EMPTY) {
            nullCheckProperty(e, f, p.name());
            return;
        }

        ctx(e).field(f).ensure(AnnoCode.raw("io.swagger.v3.oas.annotations.media.Schema", schemaLine(e, f, p.op())));

        String pattern = FieldDoc.formatPattern(f);
        if (pattern != null) {
            ctx(e).field(f)
                    .ensure(AnnoCode.of("org.springframework.format.annotation.DateTimeFormat")
                            .qualified().val("pattern", pattern))
                    .ensure(AnnoCode.of("com.fasterxml.jackson.annotation.JsonFormat")
                            .qualified().val("pattern", pattern));
        }

        if (p.kind() == SearchProperty.Kind.RANGE) {
            rangeProperty(e, f, p.name(), properties.get(1).name(), pattern);
            return;
        }
        if (p.kind() == SearchProperty.Kind.ITEM) {
            ctx(e).field(f).type(f.coreItemType()).rename(p.name());
            return;
        }
        if (!p.name().equals(f.name())) ctx(e).field(f).rename(p.name());
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
    private void rangeProperty(EntitySpec e, FieldSpec f, String fromName, String toName, String pattern) {
        FieldCode from = ctx(e).field(f).rename(fromName);

        if (pattern != null) {
            from.after("@org.springframework.format.annotation.DateTimeFormat(pattern = %s)", RuleText.quote(pattern))
                .after("@com.fasterxml.jackson.annotation.JsonFormat(pattern = %s)", RuleText.quote(pattern));
        }
        // The upper end is a second property this rule writes itself rather
        // than one the model declared, so it goes through the language seam -
        // written as Java text it lands verbatim in a Kotlin file.
        Syntax syntax = ctx(e).syntax();
        from.after("%s", syntax.declaration(syntax.fieldModifier(), f.typeName(), toName, "", false));
    }

    /**
     * The three states of an emptiness check: absent searches by nothing,
     * true finds the rows whose column is empty, false the ones where it is
     * set. The field's own type, its date format and its example describe a
     * value the caller no longer sends, so none of them is carried over.
     */
    private void nullCheckProperty(EntitySpec e, FieldSpec f, String name) {
        ctx(e).field(f)
                .ensure(AnnoCode.raw(
                        "io.swagger.v3.oas.annotations.media.Schema",
                        String.format(
                                "@Schema(description = %s  )",
                                RuleText.quote(conditionText(f, StringUtils.getOperatorDescription("isnull", true))))))
                .type("Boolean")
                .rename(name);
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
     * Whether a field is a relation searched by the identity flattened onto
     * the search DTO - parentCategoryCatId rather than parentCategory - so the
     * relation itself is not a search property. Its flattened field carries
     * the condition, and was added alongside it.
     *
     * Shared by the search DTO, the mapper and MyBatis's search, which must
     * agree: a condition on a property the search DTO does not have reads a
     * getter that is not there.
     */
    public static boolean searchedByItsFlattenedId(FieldSpec f) {
        return f.hasDsl("deferred") && f.isEntityRef();
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
        return identityOfReference(e, f) != null;
    }

    /** The identity field that holds the key a field flattens (see flattensAReferenceById), or null. */
    public static FieldSpec identityOfReference(EntitySpec e, FieldSpec f) {
        String ref = f.dslValue("ref");
        if (ref == null || ref.indexOf('.') < 0) return null;
        String relation = ref.substring(0, ref.indexOf('.'));
        String child = ref.substring(ref.indexOf('.') + 1);
        if (child.isEmpty() || child.indexOf('.') >= 0) return null;
        String flattened = relation + Character.toUpperCase(child.charAt(0)) + child.substring(1);
        for (FieldSpec other : e.fields()) {
            if (other == f || !relation.equals(other.dslValue("idof"))) continue;
            // The identity field is named for the key it holds.
            if (other.name().equals(child) || other.name().equals(flattened)) return other;
        }
        return null;
    }

    /**
     * The condition a field declares (@search), "auto" for an @search without
     * one, or null when it declares none.
     */
    private static String declaredCondition(FieldSpec f) {
        String declared = f.searchValue();
        if (declared != null && !declared.isEmpty()) return declared;
        if (f.hasSearch()) return "auto";
        return null;
    }

    /**
     * The condition a search DTO derives for a field without a @search of its own, or null when it
     * derives none: every field of an @expose search DTO, and the inherited fields another search DTO
     * takes (FieldSpec.searchedAsInherited).
     *
     * Which fields reach this rule is decided when the search DTO's model is
     * built, not here: an @searchable(entity=X) DTO carries the fields worth
     * searching plus whatever it declares itself. What is decided here is how
     * each of them searches - text matches by pattern, temporal by range, and
     * everything else by equality.
     */
    private static String derivedCondition(EntitySpec searchDto, FieldSpec f, boolean compatible) {
        if (f.hasSearch()) return null;
        if (!searchDto.hasMeta("expose") && !f.searchedAsInherited()) return null;

        // A value no reply shows is not one to search by either: a derived
        // "like" on a password let anyone read its hash one prefix at a time.
        // An explicit @search above still wins. Clean output only.
        if (!compatible && f.hasDslAny("writeonly", "nooutput", "password")) return null;
        // When a row was deleted: a search never finds a deleted row (SoftDelete).
        if (!compatible && f.hasDsl(SoftDelete.DELETED)) return null;

        if (f.isStringType()) return "auto";
        if (f.isDate() || f.isDateTime()) return "between";
        return "eq";
    }

    /**
     * The fields {@code @search(words: title, body, author.nickname)} looks for each word in, or null when
     * the field searches some other way (issue 182). Shared by the search DTO, the mapper and MyBatis's
     * search, which must agree.
     */
    public static java.util.List<String> wordsOf(FieldSpec f) {
        String declared = f.searchValue();
        if (declared == null) return null;
        String text = declared.trim();
        if (!text.regionMatches(true, 0, "words:", 0, 6)) return null;
        java.util.List<String> paths = new java.util.ArrayList<String>();
        for (String path : text.substring(6).split(",", -1)) {
            if (!path.trim().isEmpty()) paths.add(path.trim());
        }
        return paths;
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
