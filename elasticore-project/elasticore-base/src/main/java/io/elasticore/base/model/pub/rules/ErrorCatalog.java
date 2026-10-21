package io.elasticore.base.model.pub.rules;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The message codes one domain's generated code can answer with, and the
 * default wording of each.
 *
 * Rules that write a refusal (a lifecycle step out of order, a broken
 * invariant, a reference that does not exist) record its code here while they
 * generate; the runner writes what a domain recorded as
 * {@code src/main/resources/i18n/<domain>-messages.properties}. That file is
 * the default (English, or the DSL's own words) and the list a translator
 * copies to {@code <domain>-messages_ko.properties}.
 *
 * The generated API error handler adds the codes every domain shares
 * ({@link #shared()}): not found, conflicts, validation, access.
 */
public final class ErrorCatalog {

    private static final Map<String, String> recorded = new LinkedHashMap<String, String>();

    private ErrorCatalog() {
    }

    /** Starts a domain: forgets what the previous one recorded. */
    public static synchronized void begin() {
        recorded.clear();
    }

    /** A code the generated code may throw, with its default wording (a MessageFormat pattern when it has arguments). */
    public static synchronized void record(String code, String defaultMessage) {
        if (code == null || code.isEmpty()) return;
        if (!recorded.containsKey(code)) recorded.put(code, defaultMessage == null ? "" : defaultMessage);
    }

    /** What the current domain recorded, sorted by code. */
    public static synchronized Map<String, String> recorded() {
        return new TreeMap<String, String>(recorded);
    }

    /**
     * The codes the generated API error handler uses for every domain:
     * {@code error.<code>} for the detail, {@code error.<code>.title} for the title.
     */
    public static Map<String, String> shared() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        put(m, "not-found", "Not found", null);
        put(m, "already-exists", "Already exists", null);
        put(m, "stale", "Changed by someone else", "The row was changed after it was read. Read it again and resend.");
        put(m, "required", "Invalid request", "A required value is missing.");
        put(m, "unique", "Already exists", "A unique value is already taken.");
        put(m, "referenced", "Conflicts with stored data",
                "The row is still referenced by another, or refers to one that does not exist.");
        put(m, "conflict", "Conflicts with stored data",
                "A unique value is already taken, or the row is still referenced by another.");
        put(m, "invalid", "Invalid request", null);
        put(m, "refused", "Refused by the domain", null);
        put(m, "state", "Not allowed in this state", null);
        put(m, "not-signed-in", "Sign in first", null);
        put(m, "forbidden", "Not allowed", null);
        put(m, "bad-request", "Invalid request", null);
        put(m, "no-such-property", "Invalid request", null);
        put(m, "upstream", "A called system failed", null);
        // What the runtime's RequestShapeAdvice (registered by the
        // RequestShapeErrors the generated handler imports) answers when Spring MVC refuses a request before any
        // controller runs. It reads every domain's bundle, so a translation
        // beside this file reaches those answers too.
        put(m, "unreadable", "Invalid request", "The request body is missing or is not valid JSON.");
        put(m, "method-not-allowed", "Method not allowed", "Method {0} is not supported here.");
        put(m, "unsupported-media-type", "Unsupported media type", "Content type {0} is not supported here.");
        put(m, "not-acceptable", "Not acceptable", "The answer can only be given as {0}.");
        detail(m, "no-such-path", "No such path: {0}");
        detail(m, "missing-value", "is required");
        detail(m, "type.number", "must be a number");
        detail(m, "type.boolean", "must be true or false");
        detail(m, "type.text", "must be text");
        detail(m, "type.datetime", "must be a date or time");
        detail(m, "type.enum", "must be one of: {0}");
        detail(m, "type.other", "has the wrong type");
        return m;
    }

    private static void put(Map<String, String> m, String code, String title, String detail) {
        m.put("error." + code + ".title", title);
        if (detail != null) m.put("error." + code, detail);
    }

    /** A sentence with no title of its own: part of another answer (a field's message, a 404's detail). */
    private static void detail(Map<String, String> m, String code, String detail) {
        m.put("error." + code, detail);
    }

    /**
     * The properties file: shared codes first, then the domain's, every
     * non-ASCII character escaped so the file reads the same under any
     * encoding a ResourceBundle assumes.
     */
    public static String properties(String domainName, Map<String, String> shared, Map<String, String> domain) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated by ElastiCORE - the messages the ").append(domainName)
          .append(" API can answer with, in their default wording.\n")
          .append("# Regenerated with the model: do not edit. To translate, copy it to\n")
          .append("# the same name with a locale suffix (e.g. _ko) next to it, or put the\n")
          .append("# keys in the application's messages_<locale>.properties.\n")
          .append("# A message with values ({0}) is a MessageFormat pattern: write ' as ''.\n");
        sb.append("\n# Shared by every domain\n");
        for (Map.Entry<String, String> en : shared.entrySet()) line(sb, en.getKey(), en.getValue());
        if (!domain.isEmpty()) {
            sb.append("\n# This domain's rules ({0}, {1}: the values the refusal carries)\n");
            for (Map.Entry<String, String> en : domain.entrySet()) line(sb, en.getKey(), en.getValue());
        }
        return sb.toString();
    }

    private static void line(StringBuilder sb, String key, String value) {
        sb.append(escape(key, true)).append('=').append(escape(value, false)).append('\n');
    }

    static String escape(String s, boolean key) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (key && (c == ' ' || c == '=' || c == ':' || c == '#' || c == '!')) sb.append('\\').append(c);
            else if (!key && i == 0 && c == ' ') sb.append("\\ ");
            else if (c < 0x20 || c > 0x7e) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.toString();
    }

    /** UTF-8 bytes of the file - it is ASCII by construction. */
    public static byte[] bytes(String properties) {
        return properties.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A code from the condition text of an @invariant: letters and digits
     * kept, the operators spelled out, any other character a dash - and a
     * character outside ASCII by its code, so two conditions that differ only
     * there do not share a code. {@code total >= 0} -> {@code total-ge-0},
     * {@code a + b > 0} -> {@code a-plus-b-gt-0}.
     */
    public static String slug(String condition) {
        String s = condition.trim()
                .replace(">=", " ge ").replace("<=", " le ").replace("!=", " ne ").replace("==", " eq ")
                .replace(">", " gt ").replace("<", " lt ").replace("&&", " and ").replace("||", " or ")
                .replace("!", " not ").replace("+", " plus ").replace("-", " minus ").replace("*", " times ")
                .replace("/", " div ").replace("%", " mod ");
        StringBuilder sb = new StringBuilder();
        boolean dash = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) && c < 0x80) {
                sb.append(c);
                dash = false;
            } else if (c >= 0x80) {
                sb.append(String.format("u%04x", (int) c));
                dash = false;
            } else if (!dash && sb.length() > 0) {
                sb.append('-');
                dash = true;
            }
        }
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == '-') end--;
        return sb.substring(0, end);
    }
}
