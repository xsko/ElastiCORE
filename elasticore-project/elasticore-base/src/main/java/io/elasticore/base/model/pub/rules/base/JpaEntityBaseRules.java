package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.util.StringUtils;

import java.util.Locale;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Base JPA field emission, ported 1:1 from EntitySrcFilePublisher.
 *
 * Rule order mirrors the call order of loadFieldInfo so the emitted lines
 * appear exactly where 2.x puts them:
 *
 *   setFieldDesc            -> jpa-field-desc        (5)
 *   setFieldPkInfo          -> jpa-id                (10)
 *   setFieldLabel           -> jpa-comment           (15)
 *   setRelationInfo         -> jpa-relation          (20)
 *                              jpa-join-column       (25)
 *   setFieldColumnAnnotation-> jpa-column            (30)
 *   setJdbcTypeAnnotation   -> jpa-jdbc-type         (35)
 *   setEnumListAnnotation   -> jpa-enum-list         (40)
 *   @NotAudited             -> jpa-not-audited       (45)
 *   setJoinColumnAnnotation -> jpa-join-definition   (50)
 *   setNativeAnnotation     -> jpa-native-annotation (55)
 *   setConvertAnnotation    -> jpa-enum-convert      (60)
 *   getDefaultValueSetup    -> jpa-default-value     (70)
 *
 * Emitted text is always built with format arguments, never concatenation.
 */
public class JpaEntityBaseRules implements CodeRules {

    private static final String JPA = "jakarta.persistence.%s";
    private static final String HIB = "org.hibernate.annotations.%s";

    /** The generator a non-PK @sequence field declares and then refers to. */
    private static final String SEQ_GENERATOR = "seq_gen";
    private static final String SEQUENCE_STYLE_GENERATOR =
            "org.hibernate.id.enhanced.SequenceStyleGenerator";

    // ---- 5. setFieldDesc: description block and calcRequired marker ----

    @Rule(value = "jpa-field-desc", order = 5)
    void fieldDesc(EntitySpec e, FieldSpec f) {
        String desc = f.label();
        if (desc != null) {
            ctx(e).field(f)
                  .before("/*")
                  .before("  %s", desc)
                  .before("*/");
        }
        // 2.x also emits a "// calcRequired:" marker here, but that path is
        // unreachable: EntityAnnotation.CALCULATION_REQUIRED is {"calcRequired"}
        // while Field.getAnnotation does a plain map lookup and the annotation
        // keys are stored lowercase, so the name never matches. Reproduced as
        // is - the compatibility mode must not gain a line 2.x never emits.
        // See JpaEntityGapCoverageTest#calcRequired_isUnreachableIn2x.
    }

    // ---- 10. setFieldPkInfo: @Id, @GeneratedValue, @GenericGenerator ----

    @Rule(value = "jpa-id", order = 10)
    void id(EntitySpec e, FieldSpec f) {
        boolean isId = f.hasDsl("id") || f.hasDsl("pk");
        boolean isSequence = f.hasDsl("sequence");

        if (isId) {
            ctx(e).field(f).ensure(AnnoCode.of(JPA, "Id"));

            if (isSequence) {
                String type = f.dslValue("sequence");
                if (type == null || type.isEmpty()) {
                    type = "IDENTITY";
                }
                ctx(e).addImport(JPA, "GenerationType");
                ctx(e).field(f).ensure(AnnoCode.of(JPA, "GeneratedValue")
                        .valRaw("strategy", "GenerationType.%s", type.toUpperCase(Locale.ROOT)));
            }
        } else if (isSequence) {
            // Non-PK @sequence: Hibernate SequenceStyleGenerator, named seq_gen.
            ctx(e).addImport(JPA, "GenerationType");
            ctx(e).addImport(HIB, "Parameter");
            ctx(e).field(f)
                  .ensure(AnnoCode.of(JPA, "GeneratedValue")
                          .valRaw("strategy", "GenerationType.SEQUENCE")
                          .val("generator", SEQ_GENERATOR))
                  .ensure(sequenceGenerator(String.format("%s_%s_seq", e.getName(), f.name())));
        }

        String generationType = f.generationType();
        if (generationType != null) {
            ctx(e).addImport(JPA, "GenerationType");
            // 2.x emits this line without a closing parenthesis; reproduced verbatim.
            ctx(e).field(f).add(AnnoCode.raw(String.format(JPA, "GeneratedValue"),
                    "@GeneratedValue(strategy = GenerationType.%s", generationType));
        }

        if (isId && f.hasDsl("genid")) {
            String generator = resolveIdGenerator(f);
            if (generator != null) {
                // A body, not a declaration - so it goes through the language
                // seam. 2.x's indentation inside the block is reproduced as
                // written, since the compatibility mode is compared byte for
                // byte against it.
                Syntax syntax = ctx(e).syntax();
                ctx(e).addImport(JPA, "PrePersist");
                ctx(e).field(f)
                      .after("@PrePersist")
                      .after("%s {", syntax.methodSignature("public", null, "prePersist", ""))
                      .after("  if (%s == null)", f.name())
                      .after("        %s", syntax.statement(
                              String.format("%s = %s", f.name(), generator)))
                      .after("}");
            }
        }
    }

    /**
     * The @GenericGenerator 2.x writes for a non-PK @sequence field: its
     * attributes one per line, and the parameters as a braced list of nested
     * @Parameter annotations.
     */
    private AnnoCode sequenceGenerator(String sequenceName) {
        return AnnoCode.of(HIB, "GenericGenerator")
                .block("  ")
                .val("name", SEQ_GENERATOR)
                .val("strategy", SEQUENCE_STYLE_GENERATOR)
                .val("parameters", AnnoCode.AnnoList.of(
                                sequenceParameter("sequence_name", sequenceName),
                                sequenceParameter("initial_value", "1"),
                                sequenceParameter("increment_size", "1"))
                        .itemIndent("    "));
    }

    /** One @Parameter(name = ..., value = ...) entry of a @GenericGenerator. */
    private AnnoCode sequenceParameter(String name, String value) {
        return AnnoCode.of(HIB, "Parameter").val("name", name).val("value", value);
    }

    private String resolveIdGenerator(FieldSpec f) {
        String generator = f.dslValue("genid");
        if (generator == null) {
            if (!f.isStringType()) return null;
            int fieldSize = f.rawLength();
            if (fieldSize > 1) {
                return String.format("io.elasticore.springboot3.util.IdUtils.newId(%d)", fieldSize);
            }
            return "java.util.UUID.randomUUID().toString()";
        }
        if (generator.lastIndexOf("()") < 0) {
            return String.format("%s()", generator);
        }
        return generator;
    }

    // ---- 15. setFieldLabel: @Comment ----

    @Rule(value = "jpa-comment", order = 15)
    void comment(EntitySpec e, FieldSpec f) {
        // A formula has no column to comment on; Hibernate fails on @Comment there.
        if (f.hasDsl("formula")) return;
        String label = f.dslLabel();
        if (label != null && !label.isEmpty()) {
            ctx(e).field(f).ensure(AnnoCode.of(HIB, "Comment").value(label));
        }
    }

    // ---- 20. setRelationInfo ----

    @Rule(value = "jpa-relation", order = 20)
    void relation(EntitySpec e, FieldSpec f) {
        if (!f.isEntityRef()) return;

        if (f.hasDsl("embedded") || e.isEmbeddableType(f.coreItemType())) {
            ctx(e).field(f).ensure(AnnoCode.of(JPA, "Embedded"));
            attributeOverrides(e, f);
            return;
        }

        String fetch = f.dslValue("fetch");

        if (f.isList()) {
            String relationType = f.hasDsl("manytomany") ? "ManyToMany" : "OneToMany";
            fetch = (fetch == null) ? "LAZY" : fetch.toUpperCase(Locale.ROOT);

            String ext = "";
            if (f.hasDsl("cascade")) {
                String cascade = f.dslValue("cascade");
                cascade = (cascade == null) ? "ALL, orphanRemoval = true"
                                            : cascade.toUpperCase(Locale.ROOT);
                // Kotlin needs a named array parameter written as an array,
                // where Java accepts the single element.
                ext = ctx(e).syntax() == Syntax.KOTLIN
                        ? String.format(",cascade = [CascadeType.%s]", cascade)
                        : String.format(",cascade = CascadeType.%s", cascade);
                ctx(e).addImport(JPA, "CascadeType");
            }
            String mappedBy = f.dslProperty("onetomany", "mappedBy");
            if (mappedBy == null) mappedBy = f.dslProperty("manytomany", "mappedBy");
            if (mappedBy != null) {
                ext = String.format("%s,mappedBy=%s", ext, RuleText.quote(mappedBy));
            }
            // 2.x emits "@X(fetch = FetchType.%s %s)" including the irregular spacing.
            ctx(e).addImport(JPA, "FetchType");
            ctx(e).field(f).ensure(AnnoCode.raw(String.format(JPA, relationType),
                    "@%s(fetch = FetchType.%s %s)", relationType, fetch, ext));
            return;
        }

        String relationType = "ManyToOne";
        if (f.hasDsl("onetoone")) relationType = "OneToOne";
        else if (f.hasDsl("manytomany")) relationType = "ManyToMany";

        // getCascadeInfo: only OneToOne relations honor @cascade.
        if ("OneToOne".equals(relationType) && f.hasDsl("cascade")) {
            String cascade = f.dslValue("cascade");
            if (cascade == null) cascade = "ALL";
            ctx(e).addImport(JPA, "CascadeType");
            ctx(e).field(f).ensure(AnnoCode.of(JPA, "OneToOne")
                    .valRaw("cascade", ctx(e).syntax() == Syntax.KOTLIN
                                    ? "[CascadeType.%s]" : "CascadeType.%s",
                            cascade.toUpperCase(Locale.ROOT)));
        } else if (fetch != null) {
            ctx(e).addImport(JPA, "FetchType");
            ctx(e).field(f).ensure(AnnoCode.of(JPA, relationType)
                    .valRaw("fetch", "FetchType.%s", fetch.toUpperCase(Locale.ROOT)));
        } else {
            ctx(e).field(f).ensure(AnnoCode.of(JPA, relationType));
        }
    }

    /** @embedded(prefix) renames every column of the embedded type. */
    private void attributeOverrides(EntitySpec e, FieldSpec f) {
        String prefix = f.dslValueAny("embedded.prefix", "embedded");
        if (prefix == null || prefix.isEmpty()) return;

        java.util.List<String> names = e.embeddableFieldNames(f.coreItemType());
        if (names.isEmpty()) return;

        ctx(e).addImport(JPA, "AttributeOverride");
        ctx(e).addImport(JPA, "Column");

        java.util.List<AnnoCode> overrides = new java.util.ArrayList<AnnoCode>();
        for (String name : names) {
            overrides.add(attributeOverride(name, prefix));
        }

        // 2.x writes the separating comma ahead of the next entry rather than
        // after the previous one - a genuine layout quirk, kept as is.
        ctx(e).field(f).ensure(AnnoCode.of(JPA, "AttributeOverrides")
                .value(AnnoCode.AnnoList.of(overrides).itemIndent(" ").commaLeads()));
    }

    /** One @AttributeOverride(name = ..., column = @Column(name = ...)) entry. */
    private AnnoCode attributeOverride(String fieldName, String prefix) {
        return AnnoCode.of(JPA, "AttributeOverride")
                .val("name", fieldName)
                .val("column", AnnoCode.of(JPA, "Column")
                        .val("name", String.format("%s_%s",
                                prefix, StringUtils.camelToSnake(fieldName))));
    }

    // ---- 25. @JoinColumn derived from the relation column name (@db) ----

    @Rule(value = "jpa-join-column", order = 25)
    void joinColumn(EntitySpec e, FieldSpec f) {
        if (!f.isEntityRef() || f.isList() || f.hasDsl("embedded")) return;
        if (isGlobalEmbeddable(f.coreItemType())) return;

        // @column / @col / @db all name the column, as EntityAnnotation says.
        String joinName = f.dslValueAny("column", "col", "db");
        boolean readOnly = false;

        // @joinBy(field): read-only join reusing another field's column.
        String joinBy = f.dslValueAny("joinby", "joinfield");
        if (joinBy != null && !joinBy.isEmpty()) {
            String column = e.columnNameOfField(joinBy);
            if (column != null) {
                joinName = column;
                readOnly = true;
            }
        }

        if (joinName == null || joinName.isEmpty()) {
            // No named column: 2.x still enforces NOT NULL on the join.
            if (f.required()) {
                ctx(e).field(f).ensure(AnnoCode.of(JPA, "JoinColumn").valRaw("nullable", "false"));
            }
            return;
        }

        if (f.required()) {
            AnnoCode join = AnnoCode.of(JPA, "JoinColumn")
                    .val("name", joinName)
                    .valRaw("nullable", "false");
            if (readOnly) join.valRaw("insertable", "false").valRaw("updatable", "false");
            ctx(e).field(f).ensure(join);
        } else {
            // 2.x's own irregular spacing here - "name = \"x\" ," rather than
            // "name = \"x\", " - is a genuine template quirk, not a typo, so
            // this one stays a raw line rather than AnnoCode's uniform ", " join.
            String extra = readOnly ? ", insertable = false, updatable = false" : "";
            ctx(e).field(f).ensure(AnnoCode.raw(String.format(JPA, "JoinColumn"),
                    "@JoinColumn(name = %s %s)", RuleText.quote(joinName), extra));
        }
    }

    // ---- 30. setFieldColumnAnnotation: @Column and @Formula ----

    @Rule(value = "jpa-column", order = 30)
    void column(EntitySpec e, FieldSpec f) {
        if (f.isEntityRef()) return;

        boolean isEnum = !f.isList() && e.isKnownEnum(f.coreItemType());
        String formula = f.dslValueAny("formula");

        // A formula is not a column. Hibernate reads @Column first and then
        // ignores @Formula, so a @Column here (from a length, for example)
        // would map a real column instead of the expression.
        if (formula != null && !formula.isEmpty()) {
            ctx(e).field(f).ensure(AnnoCode.of(HIB, "Formula")
                    .valueRaw(StringUtils.quoteString(formula)));
            if (f.hasDsl("lob")) {
                ctx(e).field(f).ensure(AnnoCode.of(JPA, "Lob"));
            }
            return;
        }

        AnnoCode col = AnnoCode.of(JPA, "Column");

        String dbName = f.dbColumnName();
        if (dbName != null) {
            col.val("name", dbName);
        }

        String columnDef = f.dslValue("columndef");
        if (columnDef != null && !columnDef.isEmpty()) {
            col.val("columnDefinition", columnDef);
        } else {
            col.valIf(f.hasDsl("text"), "columnDefinition", "TEXT")
               .valIf(f.hasDsl("longtext"), "columnDefinition", "LONGTEXT");
        }
        // A value type embedded somewhere without being required keeps its columns
        // nullable: its required fields are checked when the value is sent (the DTO),
        // and a NOT NULL column would refuse every row that has no such value.
        boolean optionalValue = !ctx(e).compatible() && e.hasMeta("embeddable") && e.embeddedOptionally(e.getName());
        col.valIf(f.required() && !optionalValue, "nullable", false)
           .valIf("false".equals(f.dslValue("updatable")), "updatable", false);

        // 2.x keeps the enum's db length in a second bucket, consulted only
        // when nothing else was said about the column.
        String enumLen = null;
        boolean lengthDefined = false;
        if (f.maxLength() > 0 && !f.isBigDecimal() && !f.isMoney()) {
            col.val("length", f.maxLength());
            lengthDefined = true;
        } else if (isEnum) {
            String len = e.enumDbLength(f.coreItemType());
            if (!len.isEmpty()) enumLen = len;
        }
        if ((f.isBigDecimal() || f.isMoney()) && f.hasPrecisionScale()) {
            col.val("precision", f.precision());
            col.val("scale", f.scale());
        } else if (!lengthDefined && !isEnum) {
            String guessed = lengthByName(f);
            if (guessed != null) col.valRaw("length", "%s", guessed);
        }

        String unique = f.dslValue("unique");
        if (f.hasDsl("unique") && !"false".equals(unique)) {
            col.valRaw("unique", "true");
        }
        if (f.hasDsl("discriminator")) {
            col.valRaw("insertable", "false").valRaw("updatable", "false");
        }

        // 2.x emits no @Column at all when it collected no attribute, which is
        // the case for a List<Enum> element collection.
        // The enum's db length only speaks when nothing else did.
        if (!col.isEmpty()) {
            ctx(e).field(f).merge(col);
        } else if (enumLen != null) {
            ctx(e).field(f).merge(AnnoCode.of(JPA, "Column").valRaw("length", "%s", enumLen));
        }

        // clob and blob: a large object, stored apart from the row.
        if (f.hasDsl("lob")) {
            ctx(e).field(f).ensure(AnnoCode.of(JPA, "Lob"));
        }
    }

    /**
     * The 2.x name heuristic for a string column without a declared length:
     * makeLengthInfo guesses one from what the field calls itself.
     */
    private String lengthByName(FieldSpec f) {
        if (!f.isStringType()) return null;
        String name = f.name();
        if (name.contains("Addr")) return "256";
        if (name.contains("Type")) return "10";
        if (name.contains("Name") || name.contains("Nm")) return "64";
        if (name.contains("Code")) return "32";
        if (name.contains("Status")) return "10";
        if (name.contains("Date")) return "10";
        if (name.contains("Time")) return "8";
        if (name.contains("Zip")) return "7";
        if (name.contains("Tel")) return "15";
        return null;
    }

    /** Whether the type is an @embeddable entity anywhere in the context. */
    private static boolean isGlobalEmbeddable(String typeName) {
        if (typeName == null) return false;
        try {
            io.elasticore.base.ECoreModelContext ctx =
                    io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            io.elasticore.base.model.entity.Entity entity = ctx == null ? null
                    : ctx.findModelComponent(typeName, io.elasticore.base.model.entity.Entity.class);
            return entity != null && entity.getMetaInfo().hasMetaAnnotation("embeddable");
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- 35. setJdbcTypeAnnotation ----

    @Rule(value = "jpa-jdbc-type", order = 35)
    void jdbcType(EntitySpec e, FieldSpec f) {
        String resolved = f.dslValue("jdbctype");
        if (resolved == null || resolved.isEmpty()) {
            resolved = inferJdbcType(f.dslValue("columndef"));
        }
        if (resolved != null) {
            ctx(e).field(f).ensure(AnnoCode.of(HIB, "JdbcTypeCode")
                    .valueRaw(String.format("org.hibernate.type.SqlTypes.%s", resolved)));
        }
    }

    private String inferJdbcType(String columnDef) {
        if (columnDef == null) return null;
        String key = columnDef.trim().toLowerCase(Locale.ROOT);
        if ("json".equals(key) || "jsonb".equals(key)) return "JSON";
        if ("xml".equals(key)) return "SQLXML";
        return null;
    }

    // ---- 40. setEnumListAnnotation: List<Enum> as an element collection ----

    @Rule(value = "jpa-enum-list", order = 40)
    void enumList(EntitySpec e, FieldSpec f) {
        if (!f.isList()) return;
        if (!e.isKnownEnum(f.coreItemType())) return;

        String table = String.format("%s_%s",
                StringUtils.camelToSnake(e.getName()), StringUtils.camelToSnake(f.name()));

        ctx(e).addImport(JPA, "FetchType");
        ctx(e).addImport(JPA, "JoinColumn");
        String joinColumn = AnnoCode.of(JPA, "JoinColumn").val("name", "map_seq").render();
        ctx(e).field(f)
              // 2.x's extra space after "(" here is a genuine template quirk;
              // AnnoCode always renders "(attr" with none, so this one line
              // stays raw to keep byte-identical output.
              .ensure(AnnoCode.raw(String.format(JPA, "ElementCollection"),
                      "@ElementCollection( fetch = FetchType.EAGER)"))
              .ensure(AnnoCode.of(JPA, "CollectionTable")
                      .val("name", table)
                      .valRaw("joinColumns", joinColumn))
              .ensure(AnnoCode.of(JPA, "Column").val("name", "code"));
    }

    // ---- 45. @NotAudited ----

    @Rule(value = "jpa-not-audited", order = 45)
    void notAudited(EntitySpec e, FieldSpec f) {
        if (f.hasDsl("notaudited")) {
            ctx(e).field(f).ensure(AnnoCode.of("org.hibernate.envers.NotAudited"));
        }
    }

    // ---- 50. setJoinColumnAnnotation: @join(definition) ----

    @Rule(value = "jpa-join-definition", order = 50)
    void joinDefinition(EntitySpec e, FieldSpec f) {
        if (!f.hasDsl("join")) return;
        String definition = f.dslValue("join");
        if (definition == null) return;
        ctx(e).field(f).add(AnnoCode.of(JPA, "JoinColumn").compact()
                .val("columnDefinition", definition));
    }

    // ---- 55. setNativeAnnotation: @jpa:* and env.yml @entity:* mappings ----

    @Rule(value = "jpa-native-annotation", order = 55)
    void nativeAnnotation(EntitySpec e, FieldSpec f) {
        for (String line : f.nativeAnnotationLines()) {
            importNativeAnnotation(e, line);
            ctx(e).field(f).ensure(AnnoCode.raw(String.format("native:%s", line), "%s", line));
        }
    }

    /**
     * A native line written as "@Name(...)" or "@Name" names the annotation
     * the way a jakarta.persistence one is written - unqualified, relying on
     * 2.x's own wildcard import. One already written with its own package
     * qualifies itself and needs nothing added; usedImports() has no way to
     * tell the difference on its own, since the whole line is an opaque
     * "native:" identity to it.
     */
    private void importNativeAnnotation(EntitySpec e, String line) {
        if (line == null || line.isEmpty() || line.charAt(0) != '@') return;
        int end = 1;
        while (end < line.length()
                && (Character.isJavaIdentifierPart(line.charAt(end)) || line.charAt(end) == '.')) {
            end++;
        }
        String name = line.substring(1, end);
        if (name.isEmpty() || name.indexOf('.') >= 0) return;
        ctx(e).addImport(JPA, name);
    }

    // ---- 60. setConvertAnnotation: enums with @db get their EntityConverter ----

    /** A value type of the runtime is stored through its own converter. */
    @Rule(value = "jpa-value-type-convert", order = 60)
    void valueTypeConvert(EntitySpec e, FieldSpec f) {
        if (f.isList() || !(f.isMoney() || f.isQuantity())) return;
        String type = f.isMoney() ? "io.elasticore.springboot3.domain.Money" : "io.elasticore.springboot3.domain.Quantity";
        ctx(e).addImport(type);
        ctx(e).field(f).ensure(AnnoCode.of(JPA, "Convert")
                .valRaw("converter", "%s", ctx(e).syntax().classLiteral(
                        String.format("%s.JpaConverter", type.substring(type.lastIndexOf('.') + 1)))));
    }

    @Rule(value = "jpa-enum-convert", order = 60)
    void enumConvert(EntitySpec e, FieldSpec f) {
        if (f.isList()) return;
        String type = f.coreItemType();
        if (e.isKnownEnum(type) && e.enumHasDb(type)) {
            ctx(e).field(f).ensure(AnnoCode.of(JPA, "Convert")
                    .valRaw("converter", "%s",
                            ctx(e).syntax().classLiteral(
                                    String.format("%s.EntityConverter", type))));
        }
    }

    // ---- 70. getDefaultValueSetup ----

    @Rule(value = "jpa-default-value", order = 70)
    void defaultValue(EntitySpec e, FieldSpec f) {
        if (!f.hasDsl("default")) return;
        String val = f.dslValue("default");
        if (val == null) return;
        importDefaultValueLiteral(e, f, val);
        ctx(e).field(f).initial("%s", DefaultValueLiteral.of(e, f, val));
    }

    /**
     * DefaultValueLiteral.of() writes "BigDecimal.ZERO" or "LocalDate.now()"
     * unqualified, the way 2.x's own template - which always imports them -
     * can afford to. The clean mode has no such blanket import, so the same
     * field-type decision is made again here, just to know the one import
     * the literal it picks costs.
     */
    private void importDefaultValueLiteral(EntitySpec e, FieldSpec f, String val) {
        if (e.isKnownEnum(f.coreItemType())) return;
        if (f.isBigDecimal()) {
            ctx(e).addImport("java.math.BigDecimal");
            return;
        }
        if (f.isMoney() || f.isQuantity()) return;
        if (!"now".equalsIgnoreCase(val)) return;
        String base = f.baseTypeName();
        if ("DATE".equals(base)) ctx(e).addImport("java.time.LocalDate");
        else if ("DATETIME".equals(base)) ctx(e).addImport("java.time.LocalDateTime");
        else if ("TIME".equals(base)) ctx(e).addImport("java.time.LocalTime");
    }
}
