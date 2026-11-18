package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.ModelCheck;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.ArrayList;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * An {@code @encrypt} field: kept encrypted in its column - AES-256-GCM, a random IV per write, {@code v1:...} - and
 * read as the value. The runtime does the work (io.elasticore.springboot3.crypto), the keys are Spring properties.
 *
 * <pre>
 * Customer:
 *   fields:
 *     phone: string(20) @encrypt @search(eq)
 * </pre>
 *
 * <p>JPA: the entity field carries {@code @Convert(converter = EncryptConverter.class)}, the line a project without
 * the generator names in env.yml. MyBatis: the mapper binds and reads the column through EncryptTypeHandler
 * (MyBatisSql.typeHandler). Either way the column is as long as the ciphertext of the declared length can be
 * ({@link #columnLength}), in the entity and in the DDL.
 *
 * <p>An equality search compares a blind index instead (SpecFactory.prepareEncrypt adds it: {@code phoneBidx},
 * column {@code phone_bidx}, indexed): the entity's {@code indexEncrypted()} writes it just before the row is written
 * (a JPA listener, the MyBatis root's Lifecycle, or the layered MyBatis service after beforeSave), and the search
 * compares it with {@code Bidx.of(value)} - {@code Bidx.ofDigits} for {@code @encrypt(digits)} - (MapperSpecRules,
 * MyBatisSearchRules). Any other search of an encrypted column is refused there and in SpecFactory.
 *
 * <p>v3 and Kotlin over JPA, v3 Java over MyBatis. MongoDB and the 2.x-compatible output stop the build, and the 2.x
 * generator on the loader's DSL warning.
 */
public class EncryptRules implements CodeRules {

    /** The DSL mark. */
    public static final String ENCRYPT = "encrypt";

    /** The mark of a blind index field, naming the field it indexes. */
    public static final String INDEX_OF = "bidxof";

    /** 16 bytes of HMAC-SHA256 in base64url. */
    public static final int INDEX_LENGTH = 22;

    public static final String CONVERTER = "io.elasticore.springboot3.crypto.EncryptConverter";
    public static final String HANDLER = "io.elasticore.springboot3.crypto.EncryptTypeHandler";
    public static final String BIDX = "io.elasticore.springboot3.crypto.Bidx";
    public static final String LISTENER = "io.elasticore.springboot3.crypto.BlindIndexListener";
    public static final String LISTENER_NAME = "BlindIndexListener";

    /** {@code @encrypt(digits)}: the index of a number is taken of its digits (Bidx.ofDigits). */
    public static final String DIGITS = "digits";

    /** The mark of a blind index field whose field is @encrypt(digits). */
    public static final String INDEX_DIGITS = "bidxdigits";

    private static final String[] SEARCH = {"search", "s", "srch", "filter", "searchable"};

    /** Whether an @encrypt value is one the mark takes: none, or digits. */
    public static boolean knownMode(String value) {
        String v = value == null ? "" : value.trim();
        return v.isEmpty() || DIGITS.equalsIgnoreCase(v);
    }

    /** Whether an @encrypt field's index is taken of its digits. */
    public static boolean digits(Field f) {
        String v = f.getAnnotationValue(ENCRYPT);
        return v != null && DIGITS.equalsIgnoreCase(v.trim());
    }

    /** The runtime call that takes the index a blind index field holds: Bidx.of, or Bidx.ofDigits. */
    public static String bidxCall(FieldSpec index) {
        return BIDX + (index.hasDsl(INDEX_DIGITS) ? ".ofDigits" : ".of");
    }

    /** The blind index field of an encrypted field. */
    public static String indexName(String field) {
        return field + "Bidx";
    }

    /** The condition an entity field is searched by - "" for a bare @search - or null when it is not searched. */
    public static String searchCondition(Field f) {
        boolean searched = false;
        for (String s : SEARCH) searched |= f.hasAnnotation(s);
        if (!searched) return null;
        String raw = f.getAnnotationValue(SEARCH);
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.startsWith("words")) return trimmed;
        int comma = trimmed.indexOf(',');
        return (comma < 0 ? trimmed : trimmed.substring(0, comma)).trim();
    }

    /** Whether a condition is the equality an encrypted column is searched by: eq, = or none said. */
    public static boolean equality(String condition) {
        String c = condition == null ? "" : condition.trim().toLowerCase(java.util.Locale.ROOT);
        return c.isEmpty() || "eq".equals(c) || "=".equals(c) || "auto".equals(c);
    }

    /**
     * The column an encrypted field of the declared length needs: the version prefix ({@code v1:}) and the base64 of
     * a 12-byte IV, the value at four bytes a character (UTF-8 at worst) and the 16-byte tag -
     * {@code ceil((n * 4 + 28) / 3) * 4 + 3}. 255 characters when no length is declared: 1403.
     */
    public static int columnLength(FieldSpec f) {
        return columnLength(f.maxLength() > 0 ? f.maxLength() : 255);
    }

    /** As above, for a declared length. */
    public static int columnLength(int declared) {
        int bytes = declared * 4 + 28;
        return (bytes + 2) / 3 * 4 + 3;
    }

    /** Whether the field is kept as a long text, whose column holds any ciphertext without a length. */
    public static boolean longText(FieldSpec f) {
        return f.hasDsl("text") || f.hasDsl("longtext") || f.hasDsl("lob");
    }

    /** The entity's blind index fields, a parent's included, in the order they are declared. */
    public static List<FieldSpec> indexes(EntitySpec e) {
        List<FieldSpec> out = new ArrayList<FieldSpec>();
        for (FieldSpec f : e.allFields()) {
            if (f.hasDsl(INDEX_OF)) out.add(f);
        }
        return out;
    }

    /** The blind index of an entity field among a spec's fields, or null when it has none. */
    public static FieldSpec indexOf(EntitySpec e, String field) {
        for (FieldSpec f : e.allFields()) {
            if (f.hasDsl(INDEX_OF) && field.equals(f.dslValue(INDEX_OF))) return f;
        }
        return null;
    }

    /** The entity field a search DTO's field searches, when it is encrypted; null otherwise. */
    public static FieldSpec encryptedTarget(EntitySpec e, FieldSpec f, String entityField) {
        if (f.hasDsl(ENCRYPT)) return f;
        for (FieldSpec x : e.allFields()) {
            if (x.name().equals(entityField) && x.hasDsl(ENCRYPT)) return x;
        }
        return null;
    }

    /** The column is kept encrypted: the converter on the JPA field (a MyBatis entity file takes it off). */
    @Rule(value = "jpa-encrypt", order = 31)
    void field(EntitySpec e, FieldSpec f) {
        if (!f.hasDsl(ENCRYPT)) return;
        SourceCode c = ctx(e);
        if (c.compatible()) {
            ModelCheck.fail(String.format(
                    "[%s] %s.%s: an @encrypt field is kept encrypted by the 3.0 code; the 2.x-compatible output would"
                            + " store it as sent - generate the 3.0 code (output v3)",
                    e.domainClassName(), e.getName(), f.name()));
            return;
        }
        if (io.elasticore.base.model.pub.rules.mongo.MongoDocs.applies(e)) {
            ModelCheck.fail(String.format(
                    "[%s] %s.%s: @encrypt is not written for mode: mongodb yet - a document field would be stored as"
                            + " sent; keep it encrypted in the extension service, or use mode: jpa or mybatis",
                    e.domainClassName(), e.getName(), f.name()));
            return;
        }
        String literal = c.syntax() == Syntax.KOTLIN ? CONVERTER + "::class" : CONVERTER + ".class";
        c.field(f)
                .ensure(AnnoCode.raw(
                        "jakarta.persistence.Convert", "@jakarta.persistence.Convert(converter = %s)", literal));
    }

    /**
     * JPA writes the blind indexes when it writes the row: the runtime's BlindIndexListener calls the entity's
     * indexEncrypted() on @PrePersist and @PreUpdate - after the service's beforeSave, the values the model derives
     * and anything the application set. A listener, as JPA allows one callback per event per class. Under the DDD rules
     * over MyBatis the root's Lifecycle calls it instead (MyBatisDddRules); the layered MyBatis service just before its
     * statement ({@link #service}).
     */
    @Rule(value = "jpa-encrypt-listener", order = 8)
    void listener(EntitySpec e) {
        if (indexes(e).isEmpty() || e.hasMeta("embeddable")) return;
        SourceCode c = ctx(e);
        if (c.compatible()) return;
        c.addEntityListener(LISTENER_NAME);
        if (!io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.applies(e)) c.addImport(LISTENER);
    }

    /**
     * The layered MyBatis service writes the blind indexes after beforeSave, just before the insert or the update
     * (mybatis_service.tmpl's encryptBeforeWrite): no listener of MyBatis's would.
     */
    @Rule(value = "service-encrypt", target = "service", order = 27)
    void service(EntitySpec e) {
        if (indexes(e).isEmpty() || !io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.applies(e)) return;
        SourceCode c = ctx(e);
        if (c.compatible()) return;
        c.varLine("encryptBeforeWrite", "%s", c.syntax().statement("entity.indexEncrypted()"));
    }

    /** The entity's indexEncrypted(). */
    @Rule(value = "entity-encrypt", target = "entity", order = 99, phase = Rule.Phase.TAIL)
    void entity(EntitySpec e) {
        List<FieldSpec> indexes = indexes(e);
        if (indexes.isEmpty()) return;
        SourceCode c = ctx(e);
        if (c.compatible()) return;
        Syntax s = c.syntax();
        if (s == Syntax.KOTLIN) {
            // A Kotlin method cannot be written again below: the parent's covers its own fields.
            boolean inherited = false;
            boolean own = false;
            for (FieldSpec index : indexes) {
                if (index.isParentField()) inherited = true;
                else own = true;
            }
            if (inherited && own) {
                ModelCheck.fail(String.format(
                        "[%s] %s: searched encrypted fields both here and in %s - in Kotlin keep them in one class of"
                                + " the hierarchy (indexEncrypted() is written once)",
                        e.domainClassName(), e.getName(), e.parentName()));
                return;
            }
            if (inherited) return;
        }
        c.varLine("fieldList", "")
                .varLine("fieldList", "/**")
                .varLine("fieldList", " * Writes the blind index of each encrypted field searched by equality. Called")
                .varLine("fieldList", " * just before the row is written (BlindIndexListener under JPA).")
                .varLine("fieldList", " */");
        c.varLine("fieldList", s == Syntax.KOTLIN ? "fun indexEncrypted() {" : "public void indexEncrypted() {");
        for (FieldSpec index : indexes) {
            String value =
                    String.format("%s(%s)", bidxCall(index), s.getter("this", s.escapeName(index.dslValue(INDEX_OF))));
            c.varLine("fieldList", "    %s", s.statement(s.setter("this", s.escapeName(index.name()), value)));
        }
        c.varLine("fieldList", "}");
    }
}
