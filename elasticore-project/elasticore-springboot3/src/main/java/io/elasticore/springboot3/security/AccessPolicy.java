package io.elasticore.springboot3.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Who may do what to one kind of entity: the DSL's
 * {@code @access(read=public, create=user, cancel=owner)} at run time.
 *
 * <pre>
 *   public  anyone
 *   user    anyone signed in
 *   owner   the signed-in owner of the row (@owner) - and the administrator
 *   admin   the administrator only
 *   none    nobody; the application itself still may
 * </pre>
 *
 * An entry may also name the roles of the session (the @signin entity's role
 * field) and combine words with | (either) and &amp; (both); &amp; binds
 * tighter than |, and there are no parentheses:
 *
 * <pre>
 *   read=owner|MANAGER     the row's owner, or a MANAGER (every row)
 *   delete=REP&amp;owner       a REP, and only on a row of their own
 * </pre>
 *
 * A word that is a level is the level, in any case; any other word is a role
 * name, which starts with a capital letter and is compared with the
 * session's role as it is written. The administrator passes every term that
 * does not say none - owner|MANAGER, REP&amp;owner, but not none.
 *
 * An operation the declaration does not name takes "*" when it is given,
 * and admin otherwise: what was not opened stays closed. With no declaration
 * at all everything is public - the generated code then never asks.
 *
 * The application itself - inside {@link CurrentUser#runAsSystem} - passes every check.
 */
public final class AccessPolicy {

    public enum Level { PUBLIC, USER, OWNER, ADMIN, NONE }

    /** A role name: a word that starts with a capital letter. */
    private static final Pattern ROLE = Pattern.compile("[A-Z][A-Za-z0-9_]*");

    /**
     * One entry of the declaration: either of its terms, each a list of words
     * that must all hold - a Level, or a role name.
     */
    private static final class Rule {
        final String text;
        final Level single;
        final List<List<Object>> any;
        final boolean owner;

        Rule(String text, Level single, List<List<Object>> any) {
            this.text = text;
            this.single = single;
            this.any = any;
            boolean mentionsOwner = false;
            for (List<Object> all : any) mentionsOwner |= all.contains(Level.OWNER);
            this.owner = mentionsOwner;
        }

        static Rule of(Level level) {
            List<Object> one = Collections.<Object>singletonList(level);
            return new Rule(level.name().toLowerCase(Locale.ROOT), level, Collections.singletonList(one));
        }
    }

    private final String entity;
    private final Map<String, Rule> rules;
    private final Rule fallback;
    private final String adminRole;

    private AccessPolicy(String entity, Map<String, Rule> rules, Rule fallback, String adminRole) {
        this.entity = entity;
        this.rules = rules;
        this.fallback = fallback;
        this.adminRole = adminRole == null || adminRole.trim().isEmpty() ? "ADMIN" : adminRole.trim();
    }

    /**
     * "read=public, create=user, *=admin" - or "read=owner|MANAGER". Null or
     * empty is no declaration: everything public.
     */
    public static AccessPolicy parse(String entity, String declaration, String adminRole) {
        Map<String, Rule> rules = new LinkedHashMap<String, Rule>();
        if (declaration == null || declaration.trim().isEmpty()) {
            return new AccessPolicy(entity, rules, Rule.of(Level.PUBLIC), adminRole);
        }
        Rule fallback = Rule.of(Level.ADMIN);
        for (String part : declaration.split(",")) {
            String p = part.replace("'", "").replace("\"", "").trim();
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            if (eq <= 0) throw new IllegalArgumentException(entity + " @access: '" + p + "' is not operation=level");
            String operation = p.substring(0, eq).trim();
            Rule rule = rule(entity, p.substring(eq + 1).trim());
            if ("*".equals(operation)) fallback = rule;
            else rules.put(operation, rule);
        }
        return new AccessPolicy(entity, rules, fallback, adminRole);
    }

    private static Rule rule(String entity, String text) {
        List<List<Object>> any = new ArrayList<List<Object>>();
        StringBuilder normal = new StringBuilder();
        for (String term : text.split("\\|", -1)) {
            List<Object> all = new ArrayList<Object>();
            if (normal.length() > 0) normal.append('|');
            boolean first = true;
            for (String word : term.split("&", -1)) {
                Object w = word(entity, text, word.trim());
                all.add(w);
                if (!first) normal.append('&');
                normal.append(w instanceof Level level ? level.name().toLowerCase(Locale.ROOT) : w);
                first = false;
            }
            any.add(all);
        }
        Level single =
                any.size() == 1 && any.get(0).size() == 1 && any.get(0).get(0) instanceof Level level ? level : null;
        return new Rule(normal.toString(), single, any);
    }

    private static Object word(String entity, String text, String word) {
        try {
            return Level.valueOf(word.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            if (ROLE.matcher(word).matches()) return word;
            // A word that cannot be a role name (no capital first) is refused as it always was.
            throw new IllegalArgumentException(
                    entity + " @access: '" + text + "' is not one of public, user, owner, admin, none");
        }
    }

    private Rule rule(String operation) {
        Rule rule = rules.get(operation);
        return rule == null ? fallback : rule;
    }

    /** The level of the operation; null when its entry names a role or combines words (read=owner|MANAGER). */
    public Level level(String operation) {
        return rule(operation).single;
    }

    public boolean isAdmin(CurrentUser user) {
        return user.system() || (user.signedIn() && user.hasRole(adminRole));
    }

    /** Whether every word of the term may hold for the caller - owner taken as signed in, the row not known yet. */
    private boolean mayHold(CurrentUser user, List<Object> all, boolean admin) {
        for (Object word : all) {
            if (word == Level.NONE) return false;
            if (admin || word == Level.PUBLIC) continue;
            if (!user.signedIn()) return false;
            if (word == Level.ADMIN) return false;
            if (word instanceof String role && !user.hasRole(role)) return false;
        }
        return true;
    }

    /** Whether the caller passes the rule on every row: a term without owner holds, or they are the administrator. */
    private boolean seesAll(CurrentUser user, Rule rule) {
        boolean admin = isAdmin(user);
        if (admin) return true;
        for (List<Object> all : rule.any) {
            if (!all.contains(Level.OWNER) && mayHold(user, all, false)) return true;
        }
        return false;
    }

    /** Refuses the caller who may not do this at all: 401 for a visitor, 403 for the rest. */
    public void require(CurrentUser user, String operation) {
        if (user.system()) return;
        Rule rule = rule(operation);
        boolean admin = isAdmin(user);
        boolean signingInHelps = false;
        for (List<Object> all : rule.any) {
            if (mayHold(user, all, admin)) return;
            if (!all.contains(Level.NONE)) signingInHelps = true;
        }
        if (!user.signedIn() && signingInHelps) {
            throw new NotSignedInException("Sign in to " + operation + " " + entity);
        }
        if (rule.single == Level.ADMIN)
            throw new ForbiddenException(operation + " " + entity + " is for the administrator");
        if (rule.single == Level.NONE) throw new ForbiddenException(operation + " " + entity + " is not allowed");
        throw new ForbiddenException(operation + " " + entity + " is for " + rule.text);
    }

    /**
     * The same, for an operation over many rows at once - findAll, delete by
     * condition. An owner-level operation is not offered that way to anyone
     * who reaches only their own rows, since the rows are not checked one by
     * one: read=owner|MANAGER lets a MANAGER, and the administrator.
     */
    public void requireAll(CurrentUser user, String operation) {
        require(user, operation);
        Rule rule = rule(operation);
        if (rule.owner && !seesAll(user, rule)) {
            throw new ForbiddenException(operation + " " + entity + " is done one at a time, on your own");
        }
    }

    /** Whether the caller may do this to the row owned by ownerValue. */
    public boolean owns(CurrentUser user, String operation, Object ownerValue) {
        Rule rule = rule(operation);
        if (!rule.owner || isAdmin(user)) return true;
        Object id = user.id();
        boolean mine = id != null && ownerValue != null && String.valueOf(id).equals(String.valueOf(ownerValue));
        for (List<Object> all : rule.any) {
            if (mayHold(user, all, false) && (mine || !all.contains(Level.OWNER))) return true;
        }
        return false;
    }

    /**
     * The owner a search must be narrowed to, or null when the caller sees
     * every row: read=owner|MANAGER narrows everyone's but a MANAGER's.
     */
    public Object ownerFilter(CurrentUser user, String operation) {
        Rule rule = rule(operation);
        if (!rule.owner || seesAll(user, rule)) return null;
        return user.id();
    }

    /** The owner a new row is created for: the signed-in caller, unless that is the administrator. */
    public Object ownerToClaim(CurrentUser user) {
        if (isAdmin(user) || !user.signedIn()) return null;
        return user.id();
    }

    /** An identity, as the owner field's type holds it. */
    @SuppressWarnings("unchecked")
    public static <T> T as(Object id, Class<T> type) {
        if (id == null || type.isInstance(id)) return (T) id;
        String text = String.valueOf(id);
        if (type == Long.class) return (T) Long.valueOf(text);
        if (type == Integer.class) return (T) Integer.valueOf(text);
        if (type == String.class) return (T) text;
        if (type == java.util.UUID.class) return (T) java.util.UUID.fromString(text);
        throw new IllegalArgumentException("An identity of " + id.getClass().getName() + " is not a " + type.getName());
    }
}
