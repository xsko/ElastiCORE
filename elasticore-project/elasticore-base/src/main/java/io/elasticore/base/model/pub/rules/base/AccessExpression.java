package io.elasticore.base.model.pub.rules.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One level entry of {@code @access(op=...)} as the generator reads it - the
 * same reading as AccessPolicy at run time.
 *
 * <pre>
 *   read=owner           a level: public, user, owner, admin, none (any case)
 *   read=owner|MANAGER   either: the owner, or a MANAGER
 *   update=REP&amp;owner     both: a REP, on a row of their own
 * </pre>
 *
 * &amp; binds tighter than |; there are no parentheses. A word that is not a
 * level is a role name and starts with a capital letter. The text is kept as
 * the levels in lower case and the role names as written, without spaces:
 * one level alone reads exactly as it always did.
 */
public final class AccessExpression {

    public static final List<String> LEVELS =
            Collections.unmodifiableList(java.util.Arrays.asList("public", "user", "owner", "admin", "none"));

    private static final Pattern ROLE = Pattern.compile("[A-Z][A-Za-z0-9_]*");

    private final List<List<String>> any;
    private final String text;

    private AccessExpression(List<List<String>> any) {
        this.any = any;
        StringBuilder out = new StringBuilder();
        for (List<String> all : any) {
            if (out.length() > 0) out.append('|');
            for (int i = 0; i < all.size(); i++) {
                if (i > 0) out.append('&');
                out.append(all.get(i));
            }
        }
        this.text = out.toString();
    }

    /** The entry read, or null when it is not one: an empty word, a lower-case word that is no level. */
    public static AccessExpression parse(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        List<List<String>> any = new ArrayList<List<String>>();
        for (String term : text.split("\\|", -1)) {
            List<String> all = new ArrayList<String>();
            for (String raw : term.split("&", -1)) {
                String word = raw.trim();
                String lower = word.toLowerCase(Locale.ROOT);
                if (LEVELS.contains(lower)) all.add(lower);
                else if (ROLE.matcher(word).matches()) all.add(word);
                else return null;
            }
            any.add(all);
        }
        return new AccessExpression(any);
    }

    /** The entry as the generator keeps it: owner, or owner|MANAGER. */
    public String text() {
        return text;
    }

    /** The terms of the entry, either of which lets the caller in; each a list of words that must all hold. */
    public List<List<String>> terms() {
        return any;
    }

    /** Whether a level is one of its words. */
    public boolean mentions(String level) {
        for (List<String> all : any) {
            if (all.contains(level)) return true;
        }
        return false;
    }

    /** The role names it holds, in order, once each. */
    public List<String> roles() {
        List<String> out = new ArrayList<String>();
        for (List<String> all : any) {
            for (String word : all) {
                if (!LEVELS.contains(word) && !out.contains(word)) out.add(word);
            }
        }
        return out;
    }

    /** Whether anyone passes it: a term of public words alone. */
    public boolean isPublic() {
        for (List<String> all : any) {
            boolean open = true;
            for (String word : all) open &= "public".equals(word);
            if (open) return true;
        }
        return false;
    }

    /**
     * Whether only the administrator, or the row's own user when the owner is
     * the key, passes it: every term holds admin or none - or owner, and the
     * owner is the key. One level alone: admin, none, or owner on the key.
     */
    public boolean ownerOrAdminOnly(boolean ownerIsKey) {
        for (List<String> all : any) {
            if (all.contains("admin") || all.contains("none")) continue;
            if (ownerIsKey && all.contains("owner")) continue;
            return false;
        }
        return true;
    }
}
