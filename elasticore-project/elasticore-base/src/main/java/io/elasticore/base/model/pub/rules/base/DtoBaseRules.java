package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.Syntax;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Base DTO field emission, ported 1:1 from SrcFilePublisher
 * (setSchemaInfo / setFieldValidation / setJsonInfo) and
 * DtoSrcFilePublisher, including the irregular spacing of the 2.x
 * @Schema line, which is reproduced verbatim through raw annotations.
 *
 * Rule order mirrors the publisher call order:
 *   @Schema (10) -> date-time format pair (15) -> validation (20)
 *   -> json (25) -> @Builder.Default and initializer (30)
 *
 * Two 2.x branches are deliberately not ported because they cannot fire:
 * see dtoValidation below.
 */
public class DtoBaseRules implements CodeRules {

    private static final String DT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final String VALIDATION = "jakarta.validation.constraints.%s";


    /**
     * A @primitive field is declared as the primitive rather than the boxed
     * type: 2.x swaps in the type's init form just before the declaration.
     */
    @Rule(value = "dto-primitive-type", target = "dto", order = 2)
    void primitiveType(EntitySpec e, FieldSpec f) {
        if (!f.hasDslAny("primitive", "prim")) return;
        String init = f.initTypeName();
        if (init != null && !init.isEmpty()) ctx(e).field(f).type(init);
    }

    /** Annotations the DSL hands to the DTO as written. */
    @Rule(value = "dto-native-annotation", target = "dto", order = 55)
    void nativeAnnotation(EntitySpec e, FieldSpec f) {
        for (String line : f.dtoNativeAnnotationLines()) {
            ctx(e).field(f).ensure(AnnoCode.raw(String.format("native:%s", line), "%s", line));
        }
    }

    /**
     * customerCustomerId beside customerId: the same key twice, once through
     * the relation and once as the identity a DDD reference wrote. The DTO
     * keeps the identity; with both, a request could set one and clear the
     * other, and which won depended on the order the mapper wrote them in.
     */
    @Rule(value = "dto-reference-once", target = "dto", order = 0)
    void referenceOnce(EntitySpec e, FieldSpec f) {
        if (SearchDtoBaseRules.flattensAReferenceById(e, f)) ctx(e).removeField(f);
    }

    /**
     * A field that names its DTO type is declared with that type: a relation
     * appears as the counterpart's DTO, not as the entity.
     */
    @Rule(value = "dto-type", target = "dto", order = 1)
    void dtoType(EntitySpec e, FieldSpec f) {
        String declared = f.dtoTypeName();
        if (declared != null && !declared.isEmpty()) {
            ctx(e).field(f).type(declared);
        }
    }

    /** Mirrors the 2.x @Schema line including its irregular spacing. */
    @Rule(value = "dto-schema", target = "dto", order = 10)
    void schema(EntitySpec e, FieldSpec f) {
        // 2.x renders "REQUIRED )" with a space before the closing paren:
        // the format is  "desc" <required><access> <example>)  and the empty
        // attributes leave their separators behind.
        String req = f.schemaRequired() ? ", requiredMode=Schema.RequiredMode.REQUIRED" : "";

        // The comment block above the declaration says what the field calls
        // itself; the schema attribute below reads a different alias order,
        // so a "-- label | text" comment gives the two different values.
        String label = f.label();
        if (label != null) {
            ctx(e).field(f)
                  .before("/*")
                  .before("  %s", label)
                  .before("*/");
        }

        // 2.x reads the DESCRIPTION alias list here, which puts @desc ahead of
        // @label. A "-- label | text" comment is parsed into @label(label) and
        // @desc(text), and @desc's value stops at the first ")", so a comment
        // like "-- cell | e.g.) A, F" documents the field as "e.g.". That is
        // what 2.x emits, and the compatibility mode emits it too.
        // See DtoGapCoverageTest#schemaDescription_prefersDescOverLabel.
        String desc = f.dslDescription();
        if (desc == null) desc = f.name();

        // A relation DTO added for the response is set through its flattened
        // id instead, so the request schema leaves it out. So does a field
        // the server sets (@readonly / @noinput), in the clean output.
        // springdoc already reads READ_ONLY off @JsonProperty; the schema
        // annotation says it too, for a reader that looks at @Schema alone.
        boolean readOnly = f.hasDsl("autoRelation")
                || (!ctx(e).compatible() && f.hasDslAny("readonly", "noinput"));
        String access = readOnly ? ", accessMode = Schema.AccessMode.READ_ONLY" : "";

        String line = String.format("@Schema(description = %s %s%s %s)",
                RuleText.quote(desc), req, access, FieldDoc.exampleAttribute(e, f));
        ctx(e).field(f).ensure(
                AnnoCode.raw("io.swagger.v3.oas.annotations.media.Schema", line));
    }

    /**
     * Date-time fields get the Spring/Jackson format pair with FQCN names.
     *
     * Ordered after validation and JsonView because that is the sequence
     * DtoSrcFilePublisher emits in: setFieldValidation, setJsonInfo,
     * setJsonViewInfo, then setFormatAnnotation.
     */
    @Rule(value = "dto-datetime-format", target = "dto", order = 28)
    void dateTimeFormat(EntitySpec e, FieldSpec f) {
        String pattern = FieldDoc.formatPattern(f);
        if (pattern == null) return;
        ctx(e).field(f)
                .ensure(AnnoCode.of("org.springframework.format.annotation.DateTimeFormat")
                        .qualified().val("pattern", pattern))
                .ensure(AnnoCode.of("com.fasterxml.jackson.annotation.JsonFormat")
                        .qualified().val("pattern", pattern));
    }

    /**
     * One numeric bound. BigDecimal and BigInteger carry it as a decimal
     * annotation whose value is a string, since a literal cannot express
     * their range; every other numeric type takes the plain one.
     */
    private void bound(EntitySpec e, FieldSpec f, String kind, String declared) {
        boolean arbitraryPrecision = f.isBigDecimal() || f.isMoney() || "BIGINTEGER".equals(f.baseTypeName());
        String[] bound = Bound.split(declared);
        String value = bound[0];
        String message = bound[1];
        AnnoCode a;
        if (message == null) {
            // As 2.x writes it: the bare value.
            a = arbitraryPrecision
                    ? AnnoCode.of(VALIDATION, String.format("Decimal%s", kind)).qualified().value(value)
                    : AnnoCode.of(VALIDATION, kind).qualified().valueRaw(value);
        } else {
            a = arbitraryPrecision
                    ? AnnoCode.of(VALIDATION, String.format("Decimal%s", kind)).qualified().val("value", value)
                    : AnnoCode.of(VALIDATION, kind).qualified().valRaw("value", value);
            a.val("message", message);
        }
        ctx(e).field(f).ensure(a);
    }

    /**
     * A numeric bound as the DSL writes it: the value, and optionally the
     * sentence to say when it is not met - @min(0.01, 'unit price must be above 0').
     * The message is separated at the first comma outside quotes.
     */
    public static final class Bound {
        private Bound() {
        }

        /** {value, message-or-null}. */
        public static String[] split(String declared) {
            if (declared == null) return new String[]{null, null};
            String t = declared.trim();
            int comma = -1;
            boolean quoted = false;
            for (int i = 0; i < t.length(); i++) {
                char ch = t.charAt(i);
                if (ch == '\'' || ch == '"') quoted = !quoted;
                if (ch == ',' && !quoted) { comma = i; break; }
            }
            if (comma < 0) return new String[]{t, null};
            String value = t.substring(0, comma).trim();
            String message = t.substring(comma + 1).trim();
            if (message.length() >= 2 && (message.charAt(0) == '\'' || message.charAt(0) == '"')
                    && message.charAt(message.length() - 1) == message.charAt(0)) {
                message = message.substring(1, message.length() - 1);
            }
            return new String[]{value, message.isEmpty() ? null : message.replace("\"", "'")};
        }
    }

    /**
     * Mirrors setFieldValidation: @NotNull, @NotBlank, @Pattern, @Size,
     * @Min/@Max and the date/text constraints.
     *
     * A bound on an arbitrary-precision number is a DecimalMin/DecimalMax
     * taking its value as a string; everything else takes @Min/@Max with a
     * bare number, which is what the annotations themselves accept.
     */
    @Rule(value = "dto-validation", target = "dto", order = 20)
    void validation(EntitySpec e, FieldSpec f) {
        if (f.required() && !f.hasDsl("dto:jakarta.validation.constraints.NotNull")) {
            ctx(e).field(f).ensure(AnnoCode.of(VALIDATION, "NotNull"));
        }
        if (f.hasDsl("notblank")) {
            ctx(e).field(f).ensure(AnnoCode.of(VALIDATION, "NotBlank"));
        }

        pattern(e, f);
        size(e, f);

        String minValue = f.dslValueAny("min", "minval", "minvalue");
        if (minValue != null) bound(e, f, "Min", minValue);
        String maxValue = f.dslValueAny("max", "maxval", "maxvalue");
        if (maxValue != null) bound(e, f, "Max", maxValue);

        dateConstraint(e, f, "email", "Email", "Email format invalid");
        dateConstraint(e, f, "past", "Past", "Date must be past");
        dateConstraint(e, f, "future", "Future", "Date must be future");
        dateConstraint(e, f, "pastorpresent", "PastOrPresent", "Date must be past or present");
        dateConstraint(e, f, "futureorpresent", "FutureOrPresent", "Date must be future or present");
    }

    /**
     * @pattern becomes @Pattern(regexp = "...", message = "...").
     *
     * The compatible output keeps what 2.x writes: a four-line annotation
     * whose message attribute is spelled msg, written only when both the
     * named regexp and message are given. The clean output reads the named
     * or the positional form, and says what the TypeScript client says when
     * no message is given.
     */
    private void pattern(EntitySpec e, FieldSpec f) {
        if (!f.hasDsl("pattern")) return;
        if (f.hasDsl("dto:jakarta.validation.constraints.Pattern")) return;

        if (!ctx(e).compatible()) {
            // Jakarta's @Pattern checks text only; on a date, @pattern is the
            // format it reads and writes (FieldDoc.formatPattern).
            if (!f.isStringType()) return;
            String regexp = f.patternRegexp();
            if (regexp == null) return;
            String message = f.patternMessage();
            if (message == null) message = notInTheExpectedForm(f);
            boolean kotlin = ctx(e).syntax() == Syntax.KOTLIN;
            ctx(e).field(f).ensure(AnnoCode.of(VALIDATION, "Pattern")
                    .val("regexp", kotlin ? kotlinLiteral(regexp) : regexp)
                    .val("message", kotlin ? kotlinLiteral(message) : message));
            return;
        }

        String regexp = f.dslValueAny("pattern.regexp");
        if (regexp == null || regexp.isEmpty()) return;
        String msg = f.dslValueAny("pattern.message");
        if (msg == null || msg.isEmpty()) return;

        ctx(e).field(f).ensure(AnnoCode.of(VALIDATION, "Pattern")
                .qualified().compact().block(" ")
                .val("regexp", regexp)
                .val("msg", msg));
    }

    /**
     * What a value that does not match is told when the DSL gives no message.
     * The same sentence as the TypeScript client's validate(), so the form and
     * the server say one thing.
     */
    public static String notInTheExpectedForm(FieldSpec f) {
        String label = f.label();
        if (label == null || label.trim().isEmpty()) label = f.name();
        return String.format("%s is not in the expected form", label.trim().replace("\"", "'"));
    }

    /**
     * Text for a Kotlin string literal: a dollar that would start a template -
     * before a letter, an underscore, a backtick or a brace - is written \$.
     * Any other dollar, such as the end anchor of a regex, and one already
     * escaped, stay as they are.
     */
    static String kotlinLiteral(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 4);
        int backslashes = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '$' && i + 1 < text.length() && backslashes % 2 == 0) {
                char next = text.charAt(i + 1);
                if (Character.isLetter(next) || next == '_' || next == '`' || next == '{') sb.append('\\');
            }
            backslashes = ch == '\\' ? backslashes + 1 : 0;
            sb.append(ch);
        }
        return sb.toString();
    }

    /** Whether a declared lower bound (@minsize) asks for one member or more. */
    private static boolean atLeastOne(String bound) {
        try {
            return bound != null && Integer.parseInt(bound.trim()) >= 1;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /** @Size carries min and max together, separated by " , " as in 2.x. */
    private void size(EntitySpec e, FieldSpec f) {
        int minLength = f.minLength();
        int maxLength = f.validationMaxLength();

        // A collection's bounds with a message: @minsize(1, '...'), @maxsize(10, '...').
        String minSize = f.dslValueAny("minsize");
        String maxSize = f.dslValueAny("maxsize");
        String[] minBound = minSize == null ? null : Bound.split(minSize);
        String[] maxBound = maxSize == null ? null : Bound.split(maxSize);
        // A collection that must hold a member is required as well: @Size lets a missing list
        // through, and the request then reached the entity's invariant (422) instead of 400.
        if (!ctx(e).compatible() && f.isList() && !f.required() && minBound != null && atLeastOne(minBound[0])) {
            ctx(e).field(f).ensure(minBound[1] == null
                    ? AnnoCode.of(VALIDATION, "NotNull")
                    : AnnoCode.raw(String.format(VALIDATION, "NotNull"), "@NotNull(message=%s)", RuleText.quote(minBound[1])));
        }
        // Clean output only: the compatible one keeps 2.x's reading of the DSL.
        if (!ctx(e).compatible() && ((minBound != null && minBound[1] != null) || maxBound != null)) {
            StringBuilder sb = new StringBuilder();
            String message = null;
            if (minBound != null) {
                sb.append("min=").append(minBound[0].trim());
                message = minBound[1];
            } else if (minLength >= 0) {
                sb.append("min=").append(minLength);
            }
            if (maxBound != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("max=").append(maxBound[0].trim());
                if (message == null) message = maxBound[1];
            } else if (maxLength >= 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("max=").append(maxLength);
            }
            if (message != null) sb.append(", message=").append(RuleText.quote(message));
            ctx(e).field(f).ensure(AnnoCode.raw(String.format(VALIDATION, "Size"), "@Size(%s)", sb.toString()));
            return;
        }

        if (minLength < 0) {
            String legacy = f.dslValueAny("minsize");
            if (legacy != null) {
                try {
                    minLength = Integer.parseInt(legacy);
                } catch (NumberFormatException ignore) {
                    // keep it undefined
                }
            }
        }
        if (minLength < 0 && maxLength < 0) return;

        StringBuilder sb = new StringBuilder();
        if (minLength >= 0) {
            sb.append(String.format("min=%d", minLength));
        }
        if (maxLength >= 0) {
            if (sb.length() > 0) sb.append(" , ");
            sb.append(String.format("max=%d", maxLength));
        }
        ctx(e).field(f).ensure(AnnoCode.raw(String.format(VALIDATION, "Size"),
                "@Size(%s)", sb.toString()));
    }

    private void dateConstraint(EntitySpec e, FieldSpec f,
                                String dslName, String annoName, String message) {
        if (!f.hasDsl(dslName)) return;
        ctx(e).field(f).ensure(AnnoCode.of(VALIDATION, annoName)
                .qualified().val("message", message));
    }

    /** Mirrors setJsonInfo: @JsonProperty, @JsonIgnore, @JsonSerialize. */
    @Rule(value = "dto-json", target = "dto", order = 25)
    void json(EntitySpec e, FieldSpec f) {
        String jsonName = f.dslValueAny("jsonproperty", "json");
        boolean readOnly = f.hasDslAny("readonly", "noinput");
        boolean writeOnly = f.hasDslAny("writeonly", "nooutput")
                // A password is written and never read back - clean output only.
                || (!ctx(e).compatible() && f.hasDsl("password"));

        if (jsonName != null || readOnly || writeOnly) {
            String line;
            if (!readOnly && !writeOnly) {
                line = String.format("@JsonProperty(%s)", RuleText.quote(jsonName));
            } else {
                String access = readOnly
                        ? "access=JsonProperty.Access.READ_ONLY"
                        : "access=JsonProperty.Access.WRITE_ONLY";
                if (jsonName != null && !jsonName.isEmpty()) {
                    access = String.format("value=%s, %s", RuleText.quote(jsonName), access);
                }
                line = String.format("@JsonProperty(%s)", access);
            }
            ctx(e).field(f).ensure(AnnoCode.raw(
                    "com.fasterxml.jackson.annotation.JsonProperty", line));
        }

        if (f.hasDslAny("jsonignore", "ignore")) {
            ctx(e).field(f).ensure(AnnoCode.of("com.fasterxml.jackson.annotation.JsonIgnore"));
        }

        // The @JsonSerialize pair uses the 2.x constants verbatim, which means
        // it never fires: JSON_SERIALIZE_USE is {"jsonSerialize.using"} while
        // the annotation keys are stored lowercase, so the lookup misses. Kept
        // in this shape so the compatibility mode emits exactly what 2.x emits.
        // See DtoGapCoverageTest#jsonSerialize_isUnreachableIn2x.
        String serialize = f.dslValueAny("jsonSerialize.using");
        if (serialize != null) {
            ctx(e).field(f).add(AnnoCode.of("com.fasterxml.jackson.databind.annotation.JsonSerialize")
                    .qualified().valRaw("using", serialize));
        }
        // 2.x emits JsonSerialize for the deserialize setting too, and that
        // lookup misses for the same reason.
        String deserialize = f.dslValueAny("jsonDeserialize.using");
        if (deserialize != null) {
            ctx(e).field(f).add(AnnoCode.of("com.fasterxml.jackson.databind.annotation.JsonSerialize")
                    .qualified().valRaw("using", deserialize));
        }
    }

    // ---- JsonView (setJsonViewInfo) ------------------------------------

    /**
     * Per-field @JsonView, ported from setJsonViewInfo.
     *
     * The DTO's effective mode decides the source of truth: "false" emits
     * nothing at all, an explicit @view always wins, "auto" infers the views
     * from the field's other annotations, and manual mode without a @view
     * leaves the field visible in every view.
     */
    @Rule(value = "dto-jsonview", target = "dto", order = 27)
    void jsonView(EntitySpec e, FieldSpec f) {
        // A JsonView names a marker class, and a language without class
        // literals has nothing to name. Asked as a capability, not as "which
        // language is this" - the seam answers, the rule does not choose.
        if (!ctx(e).syntax().supportsAnnotations()) return;
        String mode = e.jsonViewMode();
        if ("false".equals(mode)) return;

        String viewValue = f.dslValue("view");
        if (viewValue != null && !viewValue.trim().isEmpty()) {
            explicitViews(e, f, viewValue);
            return;
        }

        if ("auto".equals(mode)) {
            inferredViews(e, f);
        }
        // Manual mode without @view: visible everywhere, so no annotation.
    }

    /**
     * Renders an explicit @view. Both the single-char form @view(cru) and the
     * comma-separated form @view(create,update,read) are accepted; the views
     * keep the order they were written in.
     */
    private void explicitViews(EntitySpec e, FieldSpec f, String viewValue) {
        java.util.Set<String> views = new java.util.LinkedHashSet<String>();
        if (viewValue.indexOf(',') >= 0) {
            for (String view : viewValue.split("\\s*,\\s*")) {
                mapViewName(view.trim().toLowerCase(), views);
            }
        } else {
            for (char ch : viewValue.trim().toLowerCase().toCharArray()) {
                switch (ch) {
                    case 'c': views.add("Views.Create"); break;
                    case 'r': views.add("Views.Read"); break;
                    case 'u': views.add("Views.Update"); break;
                    default: break;
                }
            }
        }
        emitViews(e, f, new java.util.ArrayList<String>(views));
    }

    private void mapViewName(String name, java.util.Set<String> views) {
        if ("create".equals(name) || "c".equals(name)) views.add("Views.Create");
        else if ("update".equals(name) || "u".equals(name)) views.add("Views.Update");
        else if ("read".equals(name) || "r".equals(name)) views.add("Views.Read");
    }

    /**
     * The @jsonview(auto) inference: every field starts visible everywhere,
     * then each annotation that implies the server owns the value takes a view
     * away. A field left visible everywhere - or nowhere - gets no annotation.
     */
    /**
     * Whether the json rule will put a @JsonProperty on this field, which is
     * what makes the jackson wildcard import appear in the 2.x shadow model.
     */
    public static boolean jsonPropertyFires(FieldSpec f) {
        return f.dslValueAny("jsonproperty", "json") != null
                || f.hasDslAny("readonly", "noinput")
                || f.hasDslAny("writeonly", "nooutput");
    }

    /**
     * Whether the jsonview rule will put a @JsonView on this field, which is
     * what makes the JsonView import appear in the 2.x shadow model. Mirrors
     * jsonView()/inferredViews above - keep the three in step.
     */
    public static boolean viewAnnotationFires(String mode, FieldSpec f) {
        if ("false".equals(mode)) return false;
        String viewValue = f.dslValue("view");
        if (viewValue != null && !viewValue.trim().isEmpty()) return true;
        if (!"auto".equals(mode)) return false;

        boolean create = true, update = true, read = true;
        if (f.hasDsl("id") && (f.hasDsl("genid") || f.hasDsl("sequence"))) create = false;
        if ("false".equals(f.dslValue("updatable"))) update = false;
        if (f.hasDsl("jpa:createddate") || f.hasDsl("jpa:lastmodifieddate")
                || f.hasDsl("jpa:createdby") || f.hasDsl("jpa:lastmodifiedby")
                || f.hasDsl("jpa:version") || f.hasDsl("formula")
                || f.hasDslAny("readonly", "noinput")) {
            create = false;
            update = false;
        }
        if (f.hasDslAny("writeonly", "nooutput")) read = false;

        if (create && update && read) return false;
        if (!create && !update && !read) return false;
        return true;
    }

    private void inferredViews(EntitySpec e, FieldSpec f) {
        boolean isId = f.hasDsl("id");
        boolean isGenId = f.hasDsl("genid");
        boolean isSequence = f.hasDsl("sequence");
        boolean isUpdatableFalse = "false".equals(f.dslValue("updatable"));

        boolean serverManaged = f.hasDsl("jpa:createddate")
                || f.hasDsl("jpa:lastmodifieddate")
                || f.hasDsl("jpa:createdby")
                || f.hasDsl("jpa:lastmodifiedby")
                || f.hasDsl("jpa:version")
                || f.hasDsl("formula")
                || f.hasDslAny("readonly", "noinput");
        boolean isWriteonly = f.hasDslAny("writeonly", "nooutput");

        boolean create = true, update = true, read = true;

        // An auto-generated id is supplied by the server, but still identifies
        // the row on update.
        if (isId && (isGenId || isSequence)) create = false;
        if (isUpdatableFalse) update = false;
        if (serverManaged) {
            create = false;
            update = false;
        }
        if (isWriteonly) read = false;

        // Visible everywhere is the default, and visible nowhere is an edge
        // case 2.x declines to express.
        if (create && update && read) return;
        if (!create && !update && !read) return;

        java.util.List<String> views = new java.util.ArrayList<String>();
        if (create) views.add("Views.Create");
        if (update) views.add("Views.Update");
        if (read) views.add("Views.Read");
        emitViews(e, f, views);
    }

    /** One view renders bare, several render as an array. */
    /**
     * The views are held as type names and named as class literals here, so
     * the one statement of how a language writes a class literal serves them.
     */
    private void emitViews(EntitySpec e, FieldSpec f, java.util.List<String> views) {
        if (views.isEmpty()) return;
        Syntax syntax = ctx(e).syntax();
        java.util.List<String> literals = new java.util.ArrayList<String>();
        for (String v : views) literals.add(syntax.classLiteral(v));

        if (literals.size() == 1) {
            ctx(e).field(f).ensure(AnnoCode.raw(JSON_VIEW, "@JsonView(%s)", literals.get(0)));
        } else {
            // The value parameter is positional, which Kotlin passes as a
            // vararg - an array literal would be one argument too many.
            String open = syntax == Syntax.KOTLIN ? "" : "{";
            String close = syntax == Syntax.KOTLIN ? "" : "}";
            ctx(e).field(f).ensure(AnnoCode.raw(JSON_VIEW, "@JsonView(%s%s%s)",
                    open, joinViews(literals), close));
        }
    }

    private static String joinViews(java.util.List<String> views) {
        StringBuilder sb = new StringBuilder();
        for (String v : views) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(v);
        }
        return sb.toString();
    }

    private static final String JSON_VIEW = "com.fasterxml.jackson.annotation.JsonView";

    /** @default(x) becomes @Builder.Default plus a field initializer. */
    @Rule(value = "dto-builder-default", target = "dto", order = 30)
    void builderDefault(EntitySpec e, FieldSpec f) {
        if (!f.hasDsl("default")) return;
        String val = f.dslValue("default");
        if (val == null) return;
        // The DTO's own conversion: temporal defaults are dropped, and only
        // a field that keeps an initializer gets its @Builder.Default.
        String literal = DefaultValueLiteral.forDto(e, f, val);
        if (literal == null) return;

        // Lombok's builder needs telling that an initialized field keeps its
        // value; a Kotlin property states its default in the declaration and
        // has no builder to tell, so the annotation is Java's alone.
        if (ctx(e).syntax() == Syntax.JAVA) {
            // The identity used for tracking is the OUTER class: the rendered
            // text "@Builder.Default" resolves through Builder, so that is what
            // a clean-mode import list needs - lombok.Builder.Default would
            // import the nested type and leave Builder itself unresolved.
            ctx(e).field(f).ensure(AnnoCode.raw("lombok.Builder", "@Builder.Default"));
        }
        ctx(e).field(f).initial("%s", literal);
    }
}
