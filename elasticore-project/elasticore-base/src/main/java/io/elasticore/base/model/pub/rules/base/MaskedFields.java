package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.verify.RuntimePackages;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A DTO field the response writes masked - the runtime's {@code @Mask(MaskType.X)}, named in env.yml as a
 * {@code @dto:} annotation - keeps its stored value on an update when the text sent is the stored value masked.
 *
 * <p>A client that reads a row and sends it back unchanged sends {@code 010-****-5678} for a phone it never saw.
 * Written as sent, that text replaced the number for good (issue 270). The update now compares the text with the
 * stored value masked by the runtime's own {@code MaskType.apply} - exactly what the response showed - and, when they
 * are equal, puts the stored value back on the DTO before the mapper writes it. Any other text is written as before;
 * a create has no stored value and writes what it is sent.
 *
 * <p>The stored value is the entity's, as read: an {@code @encrypt} field is decrypted by the persistence layer
 * (the JPA converter, the MyBatis type handler), so the comparison is with the plain value.
 *
 * <p>The mask is not a DSL word; the field is found by the annotation's class name -
 * {@code io.elasticore.springboot3.mask.Mask}, or the name {@code elasticore { runtimePackages }} renames it to when
 * the annotation is written with the project's package. The {@code MaskType} the code names is the one in the
 * package of the annotation as written, so the generated comparison follows the annotation through the rename.
 *
 * <p>Every v3 update (PUT and PUT /batch, which calls it), over JPA, MyBatis and MongoDB, Java and Kotlin; the
 * 2.x-compatible service is not touched. A model without a masked field gets no line.
 */
public final class MaskedFields {

    /** The runtime's annotation. */
    public static final String MASK = "io.elasticore.springboot3.mask.Mask";

    /** {@code @a.b.Mask(...)}: the class name and what is in the parentheses. */
    private static final Pattern ANNOTATION =
            Pattern.compile("^@?\\s*([A-Za-z_$][A-Za-z0-9_$.]*)\\s*(?:\\((.*)\\))?\\s*$", Pattern.DOTALL);

    /** The argument: {@code [value =] [a.b.]MaskType.CONSTANT}. */
    private static final Pattern ARGUMENT = Pattern.compile(
            "^\\s*(?:value\\s*=\\s*)?(?:[A-Za-z_$][A-Za-z0-9_$]*\\.)*MaskType\\.([A-Za-z_$][A-Za-z0-9_$]*)\\s*$");

    private MaskedFields() {}

    /** A masked field: its name, and the MaskType constant that masks it, qualified. */
    public static final class Masked {
        private final String field;
        private final String maskType;

        Masked(String field, String maskType) {
            this.field = field;
            this.maskType = maskType;
        }

        public String field() {
            return field;
        }

        /** e.g. {@code io.elasticore.springboot3.mask.MaskType.PHONE}. */
        public String maskType() {
            return maskType;
        }
    }

    /**
     * The fields of the entity's DTO that are written masked and stored as text in the entity under the same name.
     * A field the server sets (@readonly) is left out: the mapper never writes it from a DTO.
     */
    public static List<Masked> of(EntitySpec e) {
        List<Masked> out = new ArrayList<Masked>();
        List<String> names = maskClassNames();
        for (FieldSpec f : e.dtoFields()) {
            String maskType = null;
            for (String line : f.dtoNativeAnnotationLines()) {
                String found = maskType(line, names, e, f);
                if (found != null) {
                    maskType = found;
                    break;
                }
            }
            if (maskType == null) continue;
            FieldSpec stored = entityField(e, f.name());
            if (stored == null || !isText(stored) || !isText(f)) continue;
            if (WriteGuardRules.serverSet(f) || WriteGuardRules.serverSet(stored)) continue;
            out.add(new Masked(f.name(), maskType));
        }
        return out;
    }

    /**
     * The statements that keep the stored value, each on a line of its own after the line they follow - empty for
     * an entity without a masked field, so its update is what it was.
     */
    public static String keepStatements(Syntax syntax, EntitySpec e, String indent) {
        List<Masked> masked = of(e);
        if (masked.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append('\n')
                .append(indent)
                .append("// A masked field sent back as the response showed it keeps the stored value.");
        for (Masked m : masked) sb.append('\n').append(indent).append(keep(syntax, m));
        return sb.toString();
    }

    /** One field's statement. */
    static String keep(Syntax syntax, Masked m) {
        String stored = syntax.getter("entity", m.field());
        String sent = syntax.getter("dto", m.field());
        if (syntax == Syntax.KOTLIN) {
            // Every generated property is nullable: the stored value is taken once, and only when there is one.
            return String.format(
                    "%s?.let { if (%s.apply(it) == %s) %s }",
                    stored, m.maskType(), sent, syntax.setter("dto", m.field(), "it"));
        }
        return String.format(
                "if (%s != null && %s.apply(%s).equals(%s)) %s",
                stored, m.maskType(), stored, sent, syntax.statement(syntax.setter("dto", m.field(), stored)));
    }

    /** Whether an annotation line the DSL hands to the DTO is the runtime's mask - the response writes the field masked. */
    public static boolean namesMask(String line) {
        if (line == null) return false;
        Matcher a = ANNOTATION.matcher(line.trim());
        return a.matches() && maskClassNames().contains(a.group(1));
    }

    /** The runtime's class name, and the one the project's runtimePackages renames it to. */
    static List<String> maskClassNames() {
        List<String> names = new ArrayList<String>();
        names.add(MASK);
        RuntimePackages packages;
        try {
            packages = RuntimePackages.fromSystemProperties();
        } catch (IllegalArgumentException ex) {
            // The run itself refuses the mapping before it gets here.
            packages = null;
        }
        if (packages != null) {
            String renamed = packages.rewrite(MASK);
            if (!renamed.equals(MASK)) names.add(renamed);
        }
        return names;
    }

    /**
     * The qualified MaskType constant of an annotation line naming the mask, or null for any other annotation.
     * A mask whose argument is not a MaskType constant is said: its field is written as sent.
     */
    static String maskType(String line, List<String> maskNames, EntitySpec e, FieldSpec f) {
        Matcher a = ANNOTATION.matcher(line.trim());
        if (!a.matches()) return null;
        String name = a.group(1);
        if (!maskNames.contains(name)) return null;
        String pkg = name.substring(0, name.lastIndexOf('.'));
        Matcher arg = ARGUMENT.matcher(a.group(2) == null ? "" : a.group(2));
        if (!arg.matches()) {
            SourceCode.dslWarnOnce(String.format(
                    "[%s] %s.%s: %s names no MaskType constant - an update writes the"
                            + " field as sent, masked text included",
                    e.getName(), e.getName(), f.name(), line.trim()));
            return null;
        }
        return pkg + ".MaskType." + arg.group(1);
    }

    private static FieldSpec entityField(EntitySpec e, String name) {
        for (FieldSpec s : e.allFields()) {
            if (s.name().equals(name)) return s;
        }
        return null;
    }

    private static boolean isText(FieldSpec f) {
        String type = f.typeName();
        return type != null && "String".equals(RuleText.simpleName(type.trim()));
    }
}
