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
 * column {@code phone_bidx}, indexed): the entity's {@code indexEncrypted()} writes it, the service calls it on every
 * write of the row as it cleans an html field (HtmlRules), and the search compares it with {@code Bidx.of(value)}
 * (MapperSpecRules, MyBatisSearchRules). Any other search of an encrypted column is refused there.
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

    private static final String[] SEARCH = {"search", "s", "srch", "filter", "searchable"};

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
        String c = condition == null ? "" : condition.trim();
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
     * The service writes the blind indexes before each write, as it cleans an html field (26): before the commands
     * are written (30) and the values the model derives (40).
     */
    @Rule(value = "service-encrypt", target = "service", order = 27)
    void service(EntitySpec e) {
        if (indexes(e).isEmpty()) return;
        SourceCode c = ctx(e);
        if (c.compatible()) return;
        String call = c.syntax().statement("entity.indexEncrypted()");
        c.varLine("deriveBeforeSave", "%s", call);
        c.var("encryptAfterCommand", "%s", call);
    }

    /** The entity's indexEncrypted(). */
    @Rule(value = "entity-encrypt", target = "entity", order = 99, phase = Rule.Phase.TAIL)
    void entity(EntitySpec e) {
        List<FieldSpec> indexes = indexes(e);
        if (indexes.isEmpty()) return;
        SourceCode c = ctx(e);
        if (c.compatible()) return;
        Syntax s = c.syntax();
        c.varLine("fieldList", "")
                .varLine("fieldList", "/**")
                .varLine(
                        "fieldList",
                        " * Writes the blind index of each encrypted field searched by equality. Called by")
                .varLine("fieldList", " * the service before each write; call it after setting one yourself.")
                .varLine("fieldList", " */");
        c.varLine("fieldList", s == Syntax.KOTLIN ? "fun indexEncrypted() {" : "public void indexEncrypted() {");
        for (FieldSpec index : indexes) {
            String value = String.format("%s.of(%s)", BIDX, s.getter("this", s.escapeName(index.dslValue(INDEX_OF))));
            c.varLine("fieldList", "    %s", s.statement(s.setter("this", s.escapeName(index.name()), value)));
        }
        c.varLine("fieldList", "}");
    }
}
