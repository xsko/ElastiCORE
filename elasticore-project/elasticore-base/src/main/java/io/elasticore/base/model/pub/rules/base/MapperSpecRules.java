package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.SourceCode;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * JPA Specification generation for the domain mapper, ported 1:1 from
 * MapperSrcPublisher#makeSpecificationCode / makeSpecificationFieldCode.
 *
 * Produces the "spec" line block:
 *
 *   public static Specification&lt;E&gt; toSpec(ESrchDTO searchDTO){...}
 *   public static Specification&lt;E&gt; toSpec(ESrchDTO searchDTO, Specification&lt;E&gt; sp){...}
 *
 * The spec fed to this rule set is the SEARCH DTO: only fields carrying a
 * search annotation (@search), or a condition the search DTO rule derived
 * for them (FieldSpec.derivedSearch), take part, and that value selects the
 * condition. Deriving it from the entity is the search-DTO rule's job,
 * upstream of emission.
 *
 * Conditions, exactly as 2.x resolves them:
 *   (no value) auto   sp=setSpec(sp, "f", searchDTO.getF())
 *   >= <= &gt; &lt;      greaterThanOrEqualTo / lessThanOrEqualTo / greaterThan / lessThan
 *   like %_% %%       like with "%" + trim(v) + "%"
 *   %__%              like, both sides lower-cased
 *   __= =__           equal, both sides lower-cased
 *   %xxx  xxx%        prefix / suffix like, with __ marking ignore-case
 *   in                r.get("f").in(v), or a join for a collection field
 *   between ~         From/To pair, three branches, +1 day for LocalDate
 *   isnull            Boolean xEmpty: true isNull, false isNotNull (not in
 *                     the 2.x-compatible output, which reads it as equal)
 *   (anything else)   equal
 *
 * A collection field with an eq condition joins on a single item instead.
 */
public class MapperSpecRules implements CodeRules {

    /** The block the shell rules open and close around these conditions. */
    public static final String SPEC_BLOCK = "spec";

    private static final String BLOCK = "spec";

    private static final String I2 = "        ";       // method body
    private static final String I3 = "            ";   // inside an if block

    @Rule(value = "mapper-spec-field", target = "mapper", order = 110)
    void field(EntitySpec e, FieldSpec f) {
        // A condition the search DTO rule derived counts as declared.
        String derived = f.derivedSearch();
        if (!f.hasDsl("search") && !f.hasDslAny("searchable", "s", "srch", "filter") && derived == null) return;

        SourceCode c = ctx(e);
        // The search DTO dropped it; the identity field carries the condition.
        if (SearchDtoBaseRules.searchedByItsFlattenedId(f)) return;
        if (SearchDtoBaseRules.flattensAReferenceById(e, f)) return;

        String type = f.typeName();
        String fieldNm = f.name();

        java.util.List<String> words = SearchDtoBaseRules.wordsOf(f);
        if (words != null) {
            words(c, e, f, words);
            return;
        }

        String raw = f.dslValueAny("searchable", "search", "s", "srch", "filter");
        if (raw == null && derived != null) raw = derived;
        String condition;
        if (raw == null) {
            condition = "auto";
        } else {
            String[] items = raw.split(",");
            condition = items[0].trim();
            if (items.length > 1 && !items[1].trim().isEmpty()) {
                fieldNm = items[1].trim();
            }
        }
        condition = SearchDtoBaseRules.effectiveCondition(c, f, condition);
        if (!c.compatible() && "%".equals(condition)) {
            // A lone % reads as a leading one: the value ends the column.
            io.elasticore.base.model.pub.rules.SourceCode.dslWarnOnce(String.format(
                    "[%s] %s.%s @search(%%) searches by the end of the value (like '%%' + value) - say %%_ for"
                            + " that, _%% for the start or %%_%% for any part.",
                    e.domainClassName(), e.getName(), f.name()));
        } else if (!c.compatible() && !knownCondition(condition)) {
            io.elasticore.base.model.pub.rules.SourceCode.dslWarnOnce(String.format(
                    "[%s] %s.%s @search(%s) is not a search condition, so it searches by equality - say eq, like,"
                            + " %%_%%, %%__%%, __=, _%%, %%_, >, >=, <, <=, != (or gt, gte/ge, lt, lte/le, ne/neq), in, between or isnull.",
                    e.domainClassName(), e.getName(), f.name(), condition));
        }

        String capFieldNm = capitalize(fieldNm);
        String entityFieldNm = fieldNm;

        // @ref(a.b) searches through a relation: r.get("a").get("b")
        String childField = "";
        boolean isListEntityField = f.isList();
        String ref = f.dslValue("ref");
        if (ref != null) {
            String[] parts = ref.split("\\.");
            if (parts.length == 2) {
                entityFieldNm = parts[0];
                childField = String.format(".get(%s)", RuleText.quote(parts[1]));
            } else {
                entityFieldNm = ref;
            }
            isListEntityField = false;
        } else if (isListEntityField) {
            // A list of values searched against one column of the entity - an
            // enum, a code - is that column's in(...); only a collection of the
            // entity is joined.
            FieldSpec column = entityField(e, entityFieldNm);
            // A to-one relation (customer: Customer) is not a column of values: it joins as before.
            if (column != null && !column.isList() && !column.isEntityRef()) isListEntityField = false;
        }

        // An encrypted column is compared by its blind index (EncryptRules).
        FieldSpec encrypted =
                c.compatible() || !childField.isEmpty() ? null : EncryptRules.encryptedTarget(e, f, entityFieldNm);
        if (encrypted != null) {
            byIndex(c, e, f, encrypted, condition, fieldNm, type);
            return;
        }

        // Clean output only, as the search DTO declares it: the 2.x
        // publisher reads isnull as an equality.
        if (SearchDtoBaseRules.isNullCheck(condition) && !c.compatible()) {
            emitNullCheck(c, e.getName(), entityFieldNm, childField, fieldNm, type);
            return;
        }

        boolean isBetween = "between".equals(condition) || "~".equals(condition);
        boolean isEq = "eq".equals(condition) || "=".equals(condition);
        boolean joinSingle = f.isList() && isEq;

        String joinItemNm = null;
        if (joinSingle) {
            joinItemNm = String.format("%sItem", fieldNm);
            c.line(BLOCK, "%s%s", I2, c.syntax().localVar(f.coreItemType(), joinItemNm,
                            c.syntax().getter("searchDTO", String.format("%sItem", fieldNm))))
             .line(BLOCK, "%sif(hasValue(%s)){", I2, joinItemNm);
        }

        boolean isAuto = "auto".equals(condition);
        if (isAuto) {
            c.line(BLOCK, "%s%s", I2, c.syntax().statement(String.format(
                    "sp=setSpec(sp, %s, %s)", RuleText.quote(entityFieldNm),
                    c.syntax().getter("searchDTO", fieldNm))));
        } else if (!joinSingle && !isBetween) {
            c.line(BLOCK, "%s%s", I2, c.syntax().localVar(type, fieldNm,
                            c.syntax().getter("searchDTO", fieldNm)))
             .line(BLOCK, "%sif(hasValue(%s)){", I2, fieldNm);
        }

        if (isAuto) {
            return;
        }

        emitCondition(c, e, f, condition, entityFieldNm, childField, fieldNm,
                type, isListEntityField, joinSingle, joinItemNm);

        if (!isBetween) {
            c.line(BLOCK, "%s}", I2);
        }
    }

    /**
     * {@code @search(words: title, body, author.nickname)}: every word of the text, each in any of the
     * fields - the runtime's Words, a contains ignoring case with the word escaped, a relation joined
     * LEFT. Not in the 2.x-compatible output, whose mapper has no runtime to call (issue 182).
     */
    private void words(SourceCode c, EntitySpec e, FieldSpec f, java.util.List<String> paths) {
        if (c.compatible()) {
            SourceCode.dslWarnOnce(String.format(
                    "[%s] %s.%s: @search(words: ...) is not searched in the"
                            + " 2.x-compatible output - the 3.0 code (output = 'v3') searches by it.",
                    e.domainClassName(), e.getName(), f.name()));
            return;
        }
        if (paths.isEmpty()) return;
        StringBuilder quoted = new StringBuilder();
        for (String path : paths) quoted.append(", ").append(RuleText.quote(path));
        String name = f.name();
        c.line(
                        BLOCK,
                        "%s%s",
                        I2,
                        c.syntax().localVar(f.typeName(), name, c.syntax().getter("searchDTO", name)))
                .line(BLOCK, "%sif(hasValue(%s)){", I2, name);
        and(c, e.getName(), "io.elasticore.springboot3.mapper.Words.match(r, q, c, %s%s)", nn(c, name), quoted);
        c.line(BLOCK, "%s}", I2);
    }

    /**
     * An encrypted column searched by equality: its blind index compared with the index of the value - the column
     * itself holds a different text for the same value each time. Any other condition, or a field without an index,
     * is refused: a search that compared the encrypted column would find nothing.
     */
    private void byIndex(
            SourceCode c,
            EntitySpec e,
            FieldSpec f,
            FieldSpec encrypted,
            String condition,
            String fieldNm,
            String type) {
        // The entity's own @search was checked with the model (SpecFactory.prepareEncrypt).
        boolean own = f.hasDsl(EncryptRules.ENCRYPT);
        String at = String.format("[%s] %s.%s", e.domainClassName(), e.searchDtoClassName(), f.name());
        if (!EncryptRules.equality(condition)) {
            if (!own) {
                String said = f.dslValueAny("searchable", "search", "s", "srch", "filter");
                io.elasticore.base.model.pub.rules.ModelCheck.fail(at + ": @search(" + (said == null ? condition : said)
                        + ") on "
                        + encrypted.name() + ", which is encrypted - an encrypted column is searched by equality only"
                        + " (@search(eq))");
            }
            return;
        }
        FieldSpec index = EncryptRules.indexOf(e, encrypted.name());
        if (index == null) {
            if (!own) {
                io.elasticore.base.model.pub.rules.ModelCheck.fail(at + ": " + encrypted.name() + " is encrypted and"
                        + " has no blind index to search by - mark it @search(eq) on the entity");
            }
            return;
        }
        c.line(BLOCK, "%s%s", I2, c.syntax().localVar(type, fieldNm, c.syntax().getter("searchDTO", fieldNm)))
                .line(BLOCK, "%sif(hasValue(%s)){", I2, fieldNm);
        and(
                c,
                e.getName(),
                "c.equal(%s,%s(%s))",
                path(c.syntax(), index.name(), "", "String"),
                EncryptRules.bidxCall(index),
                nn(c, fieldNm));
        c.line(BLOCK, "%s}", I2);
    }

    // ------------------------------------------------------------------

    private void emitCondition(SourceCode c, EntitySpec e, FieldSpec f, String condition,
                               String entityFieldNm, String childField, String fieldNm,
                               String type, boolean isListEntityField,
                               boolean joinSingle, String joinItemNm) {

        String path = path(c.syntax(), entityFieldNm, childField, type);

        if (">=".equals(condition)) {
            and(c, e.getName(), "c.greaterThanOrEqualTo(%s,%s)", path, nn(c, fieldNm));
        } else if ("<=".equals(condition)) {
            and(c, e.getName(), "c.lessThanOrEqualTo(%s,%s)", path, nn(c, fieldNm));
        } else if (">".equals(condition)) {
            and(c, e.getName(), "c.greaterThan(%s,%s)", path, nn(c, fieldNm));
        } else if ("<".equals(condition)) {
            and(c, e.getName(), "c.lessThan(%s,%s)", path, nn(c, fieldNm));
        } else if ("!=".equals(condition) && !c.compatible()) {
            // Clean output only: 2.x read != as an equality.
            and(c, e.getName(), "c.notEqual(%s,%s)", path, nn(c, fieldNm));
        } else if ("like".equals(condition) || "%_%".equals(condition) || "%%".equals(condition)) {
            and(c, e.getName(), "c.like(%s,\"%%\" +%s+ \"%%\"%s)", path, text(c, "trim(" + fieldNm + ")"), esc(c));
        } else if ("%__%".equals(condition)) {
            and(
                    c,
                    e.getName(),
                    "c.like(c.lower(%s),\"%%\" +%s+ \"%%\"%s)",
                    path,
                    text(c, lowered(c, fieldNm, true)),
                    esc(c));
        } else if ("__=".equals(condition) || "=__".equals(condition)) {
            and(c, e.getName(), "c.equal(c.lower(%s),%s)", path, lowered(c, fieldNm, false));
        } else if (condition.startsWith("%")) {
            if (condition.endsWith("__")) {
                and(
                        c,
                        e.getName(),
                        "c.like(c.lower(%s),\"%%\" +%s%s)",
                        path,
                        text(c, lowered(c, fieldNm, true)),
                        esc(c));
            } else {
                and(c, e.getName(), "c.like(%s,\"%%\" +%s%s)", path, text(c, "trim(" + fieldNm + ")"), esc(c));
            }
        } else if (condition.endsWith("%")) {
            if (condition.startsWith("__")) {
                and(
                        c,
                        e.getName(),
                        "c.like(c.lower(%s),%s+ \"%%\"%s)",
                        path,
                        text(c, lowered(c, fieldNm, true)),
                        esc(c));
            } else {
                and(c, e.getName(), "c.like(%s,%s+ \"%%\"%s)", path, text(c, "trim(" + fieldNm + ")"), esc(c));
            }
        } else if ("in".equals(condition)) {
            emitIn(c, e, f, entityFieldNm, childField, fieldNm, isListEntityField);
        } else if ("between".equals(condition) || "~".equals(condition)) {
            emitBetween(c, e.getName(), entityFieldNm, childField, fieldNm, type);
        } else if (joinSingle && !isListEntityField && childField.isEmpty() && f.dslValue("ref") == null) {
            // One value searched against one column of the entity - an enum, a
            // code - is that column's equality; only a collection is joined.
            and(c, e.getName(), "c.equal(%s,%s)", path(c.syntax(), entityFieldNm, "", "Any"), nn(c, joinItemNm));
        } else if (joinSingle) {
            Syntax sx = c.syntax();
            String sp = String.format("Specification<%s>", e.getName());
            c.line(BLOCK, "%ssp = sp.and(%s", I3, sx.samBlockOpen(sp, "root, query, criteriaBuilder"))
                    .line(
                            BLOCK,
                            "%s  %s",
                            I3,
                            sx.localVar(
                                    String.format("Join<%s, %s>", e.getName(), f.coreItemType()),
                                    "join",
                                    sx.genericCall("root", "join", f.coreItemType(), RuleText.quote(entityFieldNm))))
                    .line(
                            BLOCK,
                            "%s  %s",
                            I3,
                            sx.lambdaReturn(String.format("criteriaBuilder.equal(join, %s)", joinItemNm)))
                    .line(BLOCK, "%s%s", I3, sx.samBlockClose());
        } else {
            and(c, e.getName(), "c.equal(%s,%s)", path, nn(c, fieldNm));
        }
    }

    /** The entity's own field of that name - the search DTO's spec carries them as allFields - or null. */
    private static FieldSpec entityField(EntitySpec e, String name) {
        for (FieldSpec column : e.allFields()) {
            if (name.equals(column.name())) return column;
        }
        return null;
    }

    /**
     * The Criteria path a condition reads: the root's own column, or the one
     * reached through a relation when the search field flattens one.
     */
    private static String path(String entityFieldNm, String childField) {
        return String.format("r.get(%s)%s", RuleText.quote(entityFieldNm), childField);
    }

    /**
     * The same path with its type stated. Java reads the type off the method
     * it is handed to; Kotlin infers only from the arguments, and a path names
     * its column with a string.
     */
    private static String path(Syntax syntax, String entityFieldNm,
                               String childField, String leafType) {
        if (childField.isEmpty()) {
            return syntax.genericCall("r", "get", leafType, RuleText.quote(entityFieldNm));
        }
        String parent = syntax.genericCall("r", "get", "Any", RuleText.quote(entityFieldNm));
        String child = childField.substring(childField.indexOf('(') + 1, childField.length() - 1);
        return syntax.genericCall(parent, "get", leafType, child);
    }

    private void emitIn(SourceCode c, EntitySpec e, FieldSpec f, String entityFieldNm,
                        String childField, String fieldNm, boolean isListEntityField) {
        Syntax syntax = c.syntax();
        String spec = String.format("Specification<%s>", e.getName());
        if (isListEntityField) {
            c.line(BLOCK, "%ssp = sp.and(%s", I3, syntax.samBlockOpen(spec, "root, query, criteriaBuilder"))
                    .line(
                            BLOCK,
                            "%s    %s",
                            I3,
                            syntax.localVar(
                                    String.format("Join<%s, %s>", e.getName(), f.coreItemType()),
                                    "join",
                                    syntax.genericCall(
                                            "root", "join", f.coreItemType(), RuleText.quote(entityFieldNm))))
                    .line(
                            BLOCK,
                            "%s    %s",
                            I3,
                            syntax.lambdaReturn(String.format("join.%s(%s)", syntax.escapeName("in"), fieldNm)))
                    .line(BLOCK, "%s%s", I3, syntax.samBlockClose());
        } else if (!childField.isEmpty()) {
            c.line(BLOCK, "%s%s", I3, syntax.statement(String.format("sp = sp.and(%s)",
                    syntax.sam(spec, "r,q,c", String.format("%s.%s(%s)",
                            path(syntax, entityFieldNm, childField, "Any"),
                            syntax.escapeName("in"), fieldNm)))));
        } else {
            // The spacing of this lambda differs from the one above, as in 2.x.
            c.line(BLOCK, "%s%s", I3, syntax.statement(String.format("sp = sp.and(%s)",
                    syntax.sam(spec, "r, q, c", String.format("%s.%s(%s)",
                            path(syntax, entityFieldNm, "", "Any"),
                            syntax.escapeName("in"), fieldNm)))));
        }
    }

    /**
     * An emptiness check reads the three-state xEmpty property: absent
     * searches by nothing, true finds an empty column, false a set one.
     */
    private void emitNullCheck(SourceCode c, String entityName, String entityFieldNm,
                               String childField, String fieldNm, String type) {
        String nm = String.format("%sEmpty", fieldNm);
        Syntax syntax = c.syntax();
        String path = path(syntax, entityFieldNm, childField, type);

        c.line(BLOCK, "%s%s", I2, syntax.localVar("Boolean", nm, syntax.getter("searchDTO", nm)))
         .line(BLOCK, "%sif(hasValue(%s)){", I2, nm)
         .line(BLOCK, "%sif(%s){", I3, nn(syntax, nm))
         .line(BLOCK, "%s    %s", I3, and(syntax, entityName, "c.isNull(%s)", path))
         .line(BLOCK, "%s}else{", I3)
         .line(BLOCK, "%s    %s", I3, and(syntax, entityName, "c.isNotNull(%s)", path))
         .line(BLOCK, "%s}", I3)
         .line(BLOCK, "%s}", I2);
    }

    /** From/To pair with three branches; LocalDate shifts the upper bound a day. */
    private void emitBetween(SourceCode c, String entityName, String entityFieldNm,
                             String childField, String fieldNm, String type) {
        String fromNm = String.format("%sFrom", fieldNm);
        String toNm = String.format("%sTo", fieldNm);
        boolean isLocalDate = "java.time.LocalDate".equals(type);

        Syntax syntax = c.syntax();
        c.line(BLOCK, "%s%s", I2, syntax.localVar(type, fromNm, syntax.getter("searchDTO", fromNm)))
         .line(BLOCK, "%s%s", I2, syntax.localVar(type, toNm, syntax.getter("searchDTO", toNm)))
         .line(BLOCK, "%sif(hasValue(%s) && hasValue(%s)){", I2, fromNm, toNm);
        String path = path(syntax, entityFieldNm, childField, type);

        if (isLocalDate) {
            c.line(BLOCK, "%s%s", I3, syntax.localVar(type, String.format("%s_2", toNm),
                            String.format("%s.plusDays(1)", nn(syntax, toNm))))
             .line(BLOCK, "%s%s", I3, and(syntax, entityName, "c.between(%s,%s,%s_2)", path, nn(syntax, fromNm), toNm));
        } else {
            c.line(BLOCK, "%s%s", I3, and(syntax, entityName, "c.between(%s,%s,%s)", path, nn(syntax, fromNm), nn(syntax, toNm)));
        }
        c.line(BLOCK, "%s}", I2)
         .line(BLOCK, "%selse if(hasValue(%s)){", I2, fromNm)
         .line(BLOCK, "%s%s", I3, and(syntax, entityName, "c.greaterThanOrEqualTo(%s,%s)", path, nn(syntax, fromNm)))
         .line(BLOCK, "%s}", I2)
         .line(BLOCK, "%selse if(hasValue(%s)){", I2, toNm);
        if (isLocalDate) {
            c.line(BLOCK, "%s%s", I3, syntax.localVar(type, String.format("%s_2", toNm),
                            String.format("%s.plusDays(1)", nn(syntax, toNm))))
             .line(BLOCK, "%s%s", I3, and(syntax, entityName, "c.lessThan(%s,%s_2)", path, toNm));
        } else {
            c.line(BLOCK, "%s%s", I3, and(syntax, entityName, "c.lessThanOrEqualTo(%s,%s)", path, nn(syntax, toNm)));
        }
        c.line(BLOCK, "%s}", I2);
    }

    private void and(SourceCode c, String entityName, String predicate, Object... args) {
        c.line(BLOCK, "%s%s", I3, and(c.syntax(), entityName, predicate, args));
    }

    /**
     * With {@code @deleted}, every search leaves the deleted rows out, whatever it is
     * given - the last condition of toSpec. Null when the rows are deleted
     * outright, and in the 2.x-compatible output.
     */
    public static String softDeleteCondition(SourceCode c, EntitySpec e) {
        String field = e.softDeleteField();
        if (field == null || c.compatible()) return null;
        return and(c.syntax(), e.getName(), "c.isNull(%s)", path(c.syntax(), field, "", "Any"));
    }

    /**
     * One accumulated condition. Java writes a bare lambda and reads its type
     * off Specification.and; Kotlin converts a lambda to a Java interface only
     * where it can name it, so the interface is named.
     */
    private static String and(Syntax syntax, String entityName, String predicate, Object... args) {
        return syntax.statement(String.format("sp = sp.and(%s)",
                syntax.sam(String.format("Specification<%s>", entityName),
                        "r,q,c", String.format(predicate, args))));
    }

    /**
     * The value an ignore-case condition compares, in lower case. 2.x's text
     * as it was; the clean output lowers through the language (Kotlin's
     * toLowerCase() does not compile from Kotlin 2.1 on), in every locale
     * the same, and trims as like does.
     */
    private static String lowered(SourceCode c, String value, boolean trim) {
        if (c.compatible()) return nn(c, value) + ".toLowerCase()";
        return c.syntax().lowerCase(nn(c, trim ? "trim(" + value + ")" : value));
    }

    /**
     * The searched value of a like: matched as written - its own %, _ and the
     * escape character escaped (the mapper's likeText) - so a contains search
     * for "100%" finds "100%", not every row with 100 in it. 2.x's text as it
     * was: the value as a pattern. auto keeps the value as a pattern too: it
     * is a like only when the value holds a % (issue 159).
     */
    private static String text(SourceCode c, String value) {
        return c.compatible() ? value : "likeText(" + value + ")";
    }

    /** The escape character the clean output's like passes with likeText's value; 2.x passes none. */
    private static String esc(SourceCode c) {
        return c.compatible() ? "" : ",'!'";
    }

    /** The conditions emitCondition reads; anything else falls through to an equality. */
    static boolean knownCondition(String condition) {
        if (condition == null) return false;
        String[] known = {"", "auto", "eq", "=", "like", "%_%", "%%", "%__%", "__=", "=__", ">", ">=", "<", "<=",
                "!=", "in", "between", "~"};
        for (String k : known) {
            if (k.equals(condition)) return true;
        }
        if (SearchDtoBaseRules.isNullCheck(condition)) return true;
        return condition.length() > 1 && (condition.startsWith("%") || condition.endsWith("%"));
    }

    /** A value the surrounding hasValue guard has already established. */
    private static String nn(SourceCode c, String expression) {
        return c.syntax().notNull(expression);
    }

    private static String nn(Syntax syntax, String expression) {
        return syntax.notNull(expression);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
