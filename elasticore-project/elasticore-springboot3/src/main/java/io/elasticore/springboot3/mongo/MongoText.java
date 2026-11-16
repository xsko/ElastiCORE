package io.elasticore.springboot3.mongo;

import java.util.regex.Pattern;

/**
 * The text conditions of a generated MongoDB search as regular expressions:
 * the value matched as written - its own regex characters escaped - with
 * anything allowed before or after it as the condition says, as the JPA and
 * MyBatis code match a LIKE with the value's own % and _ escaped.
 */
public final class MongoText {

    private static final String SPECIAL = "\\^$.|?*+()[]{}";

    private MongoText() {}

    /**
     * The value, anything before it when anyBefore, anything after it when
     * anyAfter: contains (true, true), starts with (false, true), ends with
     * (true, false), equals (false, false).
     */
    public static Pattern match(String value, boolean anyBefore, boolean anyAfter, boolean ignoreCase) {
        String text = value == null ? "" : value;
        String regex = (anyBefore ? "" : "^") + quote(text) + (anyAfter ? "" : "$");
        return ignoreCase ? Pattern.compile(regex, Pattern.CASE_INSENSITIVE) : Pattern.compile(regex);
    }

    /**
     * A LIKE pattern as a request writes it - % any text, _ one character -
     * matched against the whole value; without either it is an equality.
     */
    public static Pattern like(String value) {
        String text = value == null ? "" : value;
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '%') sb.append(".*");
            else if (ch == '_') sb.append('.');
            else appendQuoted(sb, ch);
        }
        return Pattern.compile(sb.append('$').toString(), Pattern.DOTALL);
    }

    /** The text with every character a regular expression reads escaped. */
    public static String quote(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) appendQuoted(sb, text.charAt(i));
        return sb.toString();
    }

    private static void appendQuoted(StringBuilder sb, char ch) {
        if (SPECIAL.indexOf(ch) >= 0) sb.append('\\');
        sb.append(ch);
    }
}
