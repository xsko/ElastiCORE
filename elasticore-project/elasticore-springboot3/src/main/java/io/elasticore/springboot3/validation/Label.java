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
 * Keys as the label bundle has them: {@code Account} for an entity,
 * {@code Account.annualRevenue} for a field, {@code Stage.WON} for a constant.
 */
public final class Label implements MessageSourceResolvable, Serializable {

    private static final long serialVersionUID = 1L;

    /** What several states are joined with: no word of any language. */
    static final String SEPARATOR = ", ";

    /** The label bundle's key of each part; null for a part with no label (a missing state). */
    private final List<String> keys;

    /** The name of each part: what is said where no label is found. */
    private final List<String> names;

    private Label(List<String> keys, List<String> names) {
        this.keys = Collections.unmodifiableList(keys);
        this.names = Collections.unmodifiableList(names);
    }

    /**
     * A type or a field by its key: {@code Account}, {@code Account.annualRevenue}.
     * Its name is what follows the last dot.
     */
    public static Label of(String key) {
        List<String> keys = new ArrayList<String>();
        List<String> names = new ArrayList<String>();
        add(keys, names, key, nameOf(key));
        return new Label(keys, names);
    }

    /** A constant of an enumeration: {@code Stage.WON}, named WON; a null constant is "null". */
    public static Label of(String type, Enum<?> constant) {
        return anyOf(type, constant);
    }

    /** Several constants of one enumeration, joined with ", " - the states a step needs. */
    public static Label anyOf(String type, Enum<?>... constants) {
        List<String> keys = new ArrayList<String>();
        List<String> names = new ArrayList<String>();
        if (constants != null) {
            for (Enum<?> c : constants) {
                if (c == null) add(keys, names, null, "null");
                else add(keys, names, type + "." + c.name(), c.name());
            }
        }
        return new Label(keys, names);
    }

    private static void add(List<String> keys, List<String> names, String key, String name) {
        keys.add(key);
        names.add(name);
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

    /** One part's key as a code (for another MessageSource); none for several parts. */
    @Override
    public String[] getCodes() {
        if (keys.size() != 1 || keys.get(0) == null) return null;
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

    /** The names, joined: how the value reads where no label bundle is asked. */
    @Override
    public String toString() {
        return String.join(SEPARATOR, names);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Label label && keys.equals(label.keys) && names.equals(label.names);
    }

    @Override
    public int hashCode() {
        return 31 * keys.hashCode() + names.hashCode();
    }
}
