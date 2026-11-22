package io.elasticore.springboot3.validation;

import org.springframework.context.MessageSourceResolvable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A value of a refusal that names something of the model - a field, an
 * entity, a state - by its label, in the language the response is said in.
 *
 * The generated refusals pass these as their values ({0} the field, {1} the
 * entity of an {@code invariant.*} sentence). {@link ErrorMessages} says each
 * by the domain's label bundle, {@code i18n/<domain>-labels_<language>.properties}:
 * the file of the response's language itself ({@code _en} for English), never
 * the default file; without one, the name. Anywhere else - a log, a
 * MessageFormat, another MessageSource - it is its name ({@link #toString()}).
 *
 * Where no label is found the value reads exactly as the refusal's own
 * values did before labels: a field or an entity by its name, a state as
 * {@code String.valueOf(state)}, a list of states framed as the code list it
 * stands beside ({@code [PLACED, SHIPPED]}, or {@code PLACED or SHIPPED}).
 *
 * Keys as the label bundle has them: {@code Account} for an entity,
 * {@code Account.annualRevenue} for a field, {@code Stage.WON} for a constant.
 */
public final class Label implements MessageSourceResolvable, Serializable {

    private static final long serialVersionUID = 2L;

    /** The label bundle's key of each part; null for a part with no label (a missing state). */
    private final List<String> keys;

    /** The name of each part: what is said where no label is found. */
    private final List<String> names;

    /** What comes before the parts, between two of them, and after them. */
    private final String prefix;

    private final String separator;

    private final String suffix;

    private Label(List<String> keys, List<String> names, String prefix, String separator, String suffix) {
        this.keys = Collections.unmodifiableList(keys);
        this.names = Collections.unmodifiableList(names);
        this.prefix = prefix;
        this.separator = separator;
        this.suffix = suffix;
    }

    /**
     * A type or a field by its key: {@code Account}, {@code Account.annualRevenue}.
     * Its name is what follows the last dot.
     */
    public static Label of(String key) {
        List<String> keys = new ArrayList<String>();
        List<String> names = new ArrayList<String>();
        keys.add(key);
        names.add(nameOf(key));
        return new Label(keys, names, "", "", "");
    }

    /** A constant of an enumeration: key {@code Stage.WON}, named as String.valueOf says it; null is "null". */
    public static Label of(String type, Enum<?> constant) {
        return parts(type, new Enum<?>[] {constant}, "", "", "");
    }

    /**
     * Several constants of one enumeration, framed as {@code java.util.Arrays.toString}
     * frames them: {@code [PLACED, SHIPPED]} - the states a step needs, beside that list as a code.
     */
    public static Label listOf(String type, Enum<?>... constants) {
        return parts(type, constants, "[", ", ", "]");
    }

    /**
     * Several constants of one enumeration joined with " or ", as Kotlin's
     * {@code joinToString(" or ")} joins them: {@code PLACED or SHIPPED}.
     */
    public static Label anyOf(String type, Enum<?>... constants) {
        return parts(type, constants, "", " or ", "");
    }

    private static Label parts(String type, Enum<?>[] constants, String prefix, String separator, String suffix) {
        List<String> keys = new ArrayList<String>();
        List<String> names = new ArrayList<String>();
        if (constants != null) {
            for (Enum<?> c : constants) {
                keys.add(c == null ? null : type + "." + c.name());
                names.add(String.valueOf(c));
            }
        }
        return new Label(keys, names, prefix, separator, suffix);
    }

    private static String nameOf(String key) {
        if (key == null) return "null";
        int dot = key.lastIndexOf('.');
        return dot < 0 ? key : key.substring(dot + 1);
    }

    /** The label bundle's key of each part, in order; an entry is null where the part has none. */
    public List<String> keys() {
        return keys;
    }

    /** The name of each part, in order: what is said where no label is found. */
    public List<String> names() {
        return names;
    }

    /** The parts' texts, one per key, framed as this value frames its parts. */
    public String framed(List<String> texts) {
        return prefix + String.join(separator, texts) + suffix;
    }

    /** One part's key as a code (for another MessageSource); none for a list. */
    @Override
    public String[] getCodes() {
        if (keys.size() != 1 || keys.get(0) == null || !prefix.isEmpty()) return null;
        return new String[] {keys.get(0)};
    }

    @Override
    public Object[] getArguments() {
        return null;
    }

    @Override
    public String getDefaultMessage() {
        return toString();
    }

    /** The names, framed: how the value reads where no label bundle is asked. */
    @Override
    public String toString() {
        return framed(names);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Label label
                && keys.equals(label.keys)
                && names.equals(label.names)
                && prefix.equals(label.prefix)
                && separator.equals(label.separator)
                && suffix.equals(label.suffix);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * keys.hashCode() + names.hashCode()) + (prefix + separator + suffix).hashCode();
    }
}
