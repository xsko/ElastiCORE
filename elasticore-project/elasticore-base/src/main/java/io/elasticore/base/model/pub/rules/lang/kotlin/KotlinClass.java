package io.elasticore.base.model.pub.rules.lang.kotlin;

/**
 * How Kotlin declares the classes this generator emits.
 *
 * Kotlin names its supertypes after the class rather than before it, and
 * draws no distinction in the list between a superclass and an interface
 * except that the superclass is constructed. Every artifact shell needs the
 * same sentence built, so it is built here once.
 */
public final class KotlinClass {

    private KotlinClass() {
    }

    /**
     * @param name       the class being declared
     * @param modifier   "abstract", "open", or empty - Kotlin classes are
     *                   final unless one of these says otherwise, and a class
     *                   the DSL lets others extend has to say so
     * @param superclass constructed in the supertype list, or null
     * @param interfaces named in the supertype list
     */
    public static String declaration(String name, String modifier,
                                     String superclass, String... interfaces) {
        StringBuilder sb = new StringBuilder();
        if (modifier != null && !modifier.isEmpty()) {
            sb.append(modifier).append(" ");
        }
        sb.append(String.format("class %s", name));

        StringBuilder supertypes = new StringBuilder();
        if (superclass != null && !superclass.isEmpty()) {
            supertypes.append(String.format("%s()", superclass));
        }
        for (String each : interfaces) {
            if (each == null || each.isEmpty()) continue;
            if (supertypes.length() > 0) supertypes.append(", ");
            supertypes.append(each);
        }
        if (supertypes.length() > 0) {
            sb.append(" : ").append(supertypes);
        }
        return sb.append(" {").toString();
    }
}
