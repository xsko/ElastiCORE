package io.elasticore.base.model.pub.rules.base;

import java.util.ArrayList;
import java.util.List;

/**
 * The text shaping rule sets keep needing: turning a field name into a getter
 * name, a qualified type into the name a case label spells, a rendered block
 * back into the lines it was built from.
 *
 * Each of these had grown its own copy in three or four rule sets.
 */
public final class RuleText {

    private RuleText() {
    }

    /** Order -> Order, order -> Order. The form a getter or setter uses. */
    public static String capital(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Order -> order. The form a field or bean name uses. */
    public static String uncapital(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * A value as it appears inside emitted code, in double quotes.
     *
     * Rules used to spell the quotes inside the format string, which made a
     * line hard to read and easy to get wrong. Note this is not
     * StringUtils.quoteString: that one leaves an already-quoted value alone,
     * and the values passed here are names that never carry quotes of their
     * own.
     */
    public static String quote(String value) {
        return String.format("\"%s\"", value);
    }

    /** java.time.LocalDate -> LocalDate. What a type is called without its package. */
    public static String simpleName(String typeName) {
        if (typeName == null) return null;
        int dot = typeName.lastIndexOf('.');
        return dot < 0 ? typeName : typeName.substring(dot + 1);
    }

    /**
     * The package a model type lives in, resolved through the global
     * context so a cross-domain (or cross-module) reference lands on its
     * declared namespace. Shared by every clean-mode file rule that needs to
     * decide whether a field's own type needs an import.
     */
    public static String componentPackage(String typeName) {
        if (typeName == null) return null;
        try {
            io.elasticore.base.ECoreModelContext ctx =
                    io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            if (ctx == null) return null;
            io.elasticore.base.model.DataModelComponent c = ctx.findModelComponent(typeName);
            if (c == null) return null;
            String domainId = c.getIdentity().getDomainId();
            return ctx.getDomain(domainId).getModel()
                    .getNamespace(c.getIdentity().getComponentType().getName());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * A field's own declared type wants an import when it names a list, an
     * enum, or a model type of another package. A same-package reference -
     * the common case, two DTOs of one domain - needs none.
     *
     * The component registry is tried first so a cross-domain enum (or
     * entity, or DTO) resolves to the package it actually lives in, not
     * this entity's own domain; isKnownEnum + this domain's enum package is
     * only a fallback for when the registry lookup can't run at all.
     * Shared by every clean-mode file rule that renders a field block.
     */
    public static void registerFieldTypeImport(io.elasticore.base.model.pub.rules.SourceCode c,
                                        io.elasticore.base.model.pub.rules.EntitySpec e,
                                        io.elasticore.base.model.pub.rules.FieldSpec f,
                                        String ownPackage) {
        if (f.isList()) c.addImport("java.util.List");
        if (f.isBaseType()) return;

        String type = f.isList() ? f.coreItemType() : f.typeName();
        if (type == null || type.isEmpty()) return;

        String pkg = componentPackage(type);
        if (pkg == null && e.isKnownEnum(type)) {
            pkg = e.pkg("enumeration");
        }
        if (pkg != null && !pkg.equals(ownPackage)) {
            c.addImport("%s.%s", pkg, type);
        }
    }

    /**
     * A parent the class declaration names by its simple name - a DSL
     * @extend - wants the import a field of that type would get: a parent of
     * another domain lives in another package. A parent of the same package,
     * or one the registry does not know, needs none.
     */
    public static void registerParentImport(io.elasticore.base.model.pub.rules.SourceCode c,
                                            String parent,
                                            String ownPackage) {
        if (parent == null || parent.isEmpty()) return;
        String pkg = componentPackage(parent);
        if (pkg != null && !pkg.equals(ownPackage)) {
            c.addImport("%s.%s", pkg, parent);
        }
    }

    /** Drops the blank tail a rendered block ends with. */
    public static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == ' ')) end--;
        return s.substring(0, end);
    }

    /**
     * A rendered block as the lines it was built from, so a slot can place
     * each of them. The newline that ends the block is not a line of its own.
     */
    public static List<String> linesOf(String block) {
        // Only the terminator of the last line goes: a trailing blank line is
        // content - the slot renders it indented, as 2.x paragraphs do.
        String text = block;
        if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);

        List<String> lines = new ArrayList<String>();
        for (String line : text.split("\n", -1)) lines.add(line);
        return lines;
    }
}
