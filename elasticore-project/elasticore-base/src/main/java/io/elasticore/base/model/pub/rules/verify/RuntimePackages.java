package io.elasticore.base.model.pub.rules.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code elasticore { runtimePackages = [...] }}: the ElastiCORE runtime's
 * package prefixes in the generated code, rewritten to packages the project
 * owns - so the application compiles and runs against its own copy of those
 * classes, with no ElastiCORE jar.
 *
 * <p>Only the prefix changes: {@code io.elasticore.springboot3.validation.NotFoundException}
 * under {@code 'io.elasticore.springboot3': 'com.acme.rt'} becomes
 * {@code com.acme.rt.validation.NotFoundException}. A prefix matches whole
 * package segments only - {@code io.elasticore.springboot3} is not a prefix of
 * {@code io.elasticore.springboot3x} - and the longest key that matches wins.
 * The text is rewritten wherever it says the name: imports, fully qualified
 * names in code, Java and Kotlin alike.
 *
 * <p>The plugin passes the mapping as {@code elasticore.runtime.packages=from=to,from=to}.
 * Unset, nothing is rewritten and the output is what it always was.
 */
public final class RuntimePackages {

    /** The system property the plugin's {@code runtimePackages} arrives in. */
    public static final String PROPERTY = "elasticore.runtime.packages";

    /** What a key starts with: a package of the runtime. */
    static final String RUNTIME_PREFIX = "io.elasticore.";

    /** The build's own report of the classes the generated code needs, under build/. */
    public static final String REPORT = "build/elasticore/runtime-classes.txt";

    private static final String IDENT = "[A-Za-z_$][A-Za-z0-9_$]*";
    private static final String NOT_BEFORE = "(?<![A-Za-z0-9_$.])";
    private static final String NOT_AFTER = "(?![A-Za-z0-9_$])";

    private static final Pattern PACKAGE_NAME = Pattern.compile(IDENT + "(\\." + IDENT + ")*");

    /** Any name under io.elasticore, with a wildcard import's star. */
    private static final Pattern RUNTIME_REFERENCE =
            Pattern.compile(NOT_BEFORE + "io\\.elasticore(\\." + IDENT + ")+(\\.\\*)?");

    /** The rest of a qualified name after a package: its segments, and a wildcard's star. */
    private static final Pattern REST_OF_NAME = Pattern.compile("(\\." + IDENT + ")*(\\.\\*)?");

    private static final Set<String> KEYWORDS = new HashSet<String>(Arrays.asList(
            "abstract",
            "assert",
            "boolean",
            "break",
            "byte",
            "case",
            "catch",
            "char",
            "class",
            "const",
            "continue",
            "default",
            "do",
            "double",
            "else",
            "enum",
            "extends",
            "final",
            "finally",
            "float",
            "for",
            "goto",
            "if",
            "implements",
            "import",
            "instanceof",
            "int",
            "interface",
            "long",
            "native",
            "new",
            "package",
            "private",
            "protected",
            "public",
            "return",
            "short",
            "static",
            "strictfp",
            "super",
            "switch",
            "synchronized",
            "this",
            "throw",
            "throws",
            "transient",
            "try",
            "void",
            "volatile",
            "while",
            "true",
            "false",
            "null",
            // Kotlin's hard keywords besides Java's: the generated code may be Kotlin.
            "as",
            "fun",
            "in",
            "is",
            "object",
            "typealias",
            "typeof",
            "val",
            "var",
            "when",
            // Not an identifier on its own since Java 9, nor in Kotlin.
            "_"));

    /** From -> to, the longest key first. */
    private final Map<String, String> mapping;

    private final Pattern keys;
    private final Pattern targets;

    private RuntimePackages(Map<String, String> given) {
        Map<String, String> sorted = new LinkedHashMap<String, String>();
        for (String from : longestFirst(given.keySet())) sorted.put(from, given.get(from));
        this.mapping = Collections.unmodifiableMap(sorted);
        this.keys = alternation(sorted.keySet());
        this.targets = alternation(new TreeSet<String>(sorted.values()));
    }

    /** The longest first, then alphabetically: the order a prefix is tried in. */
    private static List<String> longestFirst(Collection<String> prefixes) {
        List<String> out = new ArrayList<String>(prefixes);
        Collections.sort(out, (a, b) -> a.length() != b.length() ? b.length() - a.length() : a.compareTo(b));
        return out;
    }

    private static Pattern alternation(Collection<String> prefixes) {
        List<String> longestFirst = longestFirst(prefixes);
        StringBuilder sb = new StringBuilder();
        for (String p : longestFirst) {
            if (sb.length() > 0) sb.append('|');
            sb.append(Pattern.quote(p));
        }
        return Pattern.compile(NOT_BEFORE + "(" + sb + ")" + NOT_AFTER);
    }

    /** The mapping the build set (elasticore.runtime.packages), or null when it set none. */
    public static RuntimePackages fromSystemProperties() {
        return parse(System.getProperty(PROPERTY));
    }

    /**
     * {@code from=to,from=to}, or null when it holds no pair.
     *
     * @throws IllegalArgumentException a key that is not under io.elasticore,
     *     a value that is not a Java package name, a pair without '=', or a key given twice
     */
    public static RuntimePackages parse(String spec) {
        if (spec == null || spec.trim().isEmpty()) return null;
        Map<String, String> given = new LinkedHashMap<String, String>();
        for (String entry : spec.split(",", -1)) {
            if (entry.trim().isEmpty()) continue;
            int eq = entry.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("runtimePackages: '" + entry.trim()
                        + "' is not a pair - write 'io.elasticore.<package>': '<your package>'");
            }
            String from = entry.substring(0, eq).trim();
            String to = entry.substring(eq + 1).trim();
            check(from, to);
            if (given.containsKey(from)) {
                throw new IllegalArgumentException("runtimePackages: '" + from + "' is mapped twice");
            }
            given.put(from, to);
        }
        if (given.isEmpty()) return null;
        // A value a key covers would be rewritten again by the next pass over the same text - the merge
        // renames the cache's base and the file as well as the new text - so the rewrite must be idempotent.
        for (Map.Entry<String, String> en : given.entrySet()) {
            for (String key : given.keySet()) {
                String to = en.getValue();
                if (to.equals(key) || to.startsWith(key + ".")) {
                    throw new IllegalArgumentException("runtimePackages: the value '" + to + "' of '" + en.getKey()
                            + "' is under the key '" + key + "', so it would be rewritten again - map to a package"
                            + " outside the keys, e.g. 'com.acme.runtime'");
                }
            }
        }
        return new RuntimePackages(given);
    }

    /** A key under io.elasticore and a value that is a package name, or why not. */
    static void check(String from, String to) {
        if (!from.startsWith(RUNTIME_PREFIX) || !isPackageName(from)) {
            throw new IllegalArgumentException("runtimePackages: the key '" + from + "' is not an ElastiCORE runtime"
                    + " package - a key is a package starting with " + RUNTIME_PREFIX
                    + ", e.g. 'io.elasticore.springboot3'");
        }
        if (!isPackageName(to)) {
            throw new IllegalArgumentException("runtimePackages: the value '" + to + "' of '" + from
                    + "' is not a Java package name, or names a Kotlin keyword, e.g. 'com.acme.runtime'");
        }
    }

    /** Dot-separated Java identifiers, none of them a keyword. */
    static boolean isPackageName(String name) {
        if (name == null || !PACKAGE_NAME.matcher(name).matches()) return false;
        for (String segment : name.split("\\.", -1)) {
            if (KEYWORDS.contains(segment)) return false;
        }
        return true;
    }

    /** From -> to, in the order the rewrite tries them: the longest key first. */
    public Map<String, String> mapping() {
        return mapping;
    }

    /** "io.elasticore.springboot3 -> com.acme.rt, ..." for the run's header. */
    public String describe() {
        List<String> pairs = new ArrayList<String>();
        for (Map.Entry<String, String> en : new java.util.TreeMap<String, String>(mapping).entrySet()) {
            pairs.add(en.getKey() + " -> " + en.getValue());
        }
        return String.join(", ", pairs);
    }

    /** The text with every mapped prefix replaced: whole segments, the longest key first. */
    public String rewrite(String text) {
        if (text == null || text.indexOf("io.elasticore.") < 0) return text;
        Matcher m = keys.matcher(text);
        if (!m.find()) return text;
        StringBuffer out = new StringBuffer(text.length() + 64);
        do {
            m.appendReplacement(out, Matcher.quoteReplacement(mapping.get(m.group(1))));
        } while (m.find());
        m.appendTail(out);
        return out.toString();
    }

    /** Whether a key covers the name: the rewrite would have changed it. */
    public boolean covers(String name) {
        return keys.matcher(name).lookingAt();
    }

    /**
     * The packages under io.elasticore the text still names, the project's own
     * left out: a name in one of {@code own} or below it is the project's.
     * {@code com.x.Foo} is named by its package {@code com.x}; a wildcard
     * import by the package it opens.
     */
    public static Set<String> leftovers(String text, Collection<String> own) {
        Set<String> found = new TreeSet<String>();
        if (text == null || text.indexOf("io.elasticore.") < 0) return found;
        Matcher m = RUNTIME_REFERENCE.matcher(text);
        while (m.find()) {
            String name = m.group();
            if (isOwn(name, own)) continue;
            found.add(packageOf(name));
        }
        return found;
    }

    /**
     * The classes under the mapped packages the text names, outermost class
     * by its qualified name ({@code com.acme.rt.http.Foo} for
     * {@code com.acme.rt.http.Foo.Bar}, a static import's method left off),
     * and {@code com.acme.rt.port.*} for a wildcard import. A name under a
     * mapped package is the runtime's even where that package is inside the
     * project's ({@code com.acme.shop.runtime} in a project
     * {@code com.acme.shop}); only a package of {@code own} below the mapped
     * one - a value above the project, {@code com.acme} - is the project's.
     */
    public Set<String> requiredClasses(String text, Collection<String> own) {
        Set<String> found = new TreeSet<String>();
        if (text == null) return found;
        Matcher m = targets.matcher(text);
        while (m.find()) {
            String target = m.group(1);
            // The segments after the package: as far as a name runs, a star last.
            Matcher rest = REST_OF_NAME.matcher(text);
            rest.region(m.end(), text.length());
            String name = rest.lookingAt() ? target + rest.group() : target;
            List<String> below = new ArrayList<String>();
            for (String p : own) {
                if (p.startsWith(target + ".")) below.add(p);
            }
            if (isOwn(name, below)) continue;
            String cls = outermostClass(name);
            if (cls != null) found.add(cls);
        }
        return found;
    }

    /**
     * The name up to its first segment that starts upper case, a wildcard as
     * itself; null for a package alone. A nested class's binary name
     * ({@code Money$JpaConverter}) is its outer class: that is the file.
     */
    static String outermostClass(String name) {
        if (name.endsWith(".*")) return name;
        String[] segments = name.split("\\.", -1);
        StringBuilder sb = new StringBuilder();
        for (String segment : segments) {
            int dollar = segment.indexOf('$');
            String s = dollar > 0 ? segment.substring(0, dollar) : segment;
            if (sb.length() > 0) sb.append('.');
            sb.append(s);
            if (!s.isEmpty() && Character.isUpperCase(s.charAt(0))) return sb.toString();
        }
        return null;
    }

    /** The package a name is in: before its first upper-case segment; a wildcard's package; a package as itself. */
    static String packageOf(String name) {
        if (name.endsWith(".*")) return name.substring(0, name.length() - 2);
        String[] segments = name.split("\\.", -1);
        StringBuilder sb = new StringBuilder();
        for (String s : segments) {
            if (!s.isEmpty() && Character.isUpperCase(s.charAt(0))) break;
            if (sb.length() > 0) sb.append('.');
            sb.append(s);
        }
        return sb.toString();
    }

    private static boolean isOwn(String name, Collection<String> own) {
        for (String p : own) {
            if (name.equals(p) || name.startsWith(p + ".")) return true;
        }
        return false;
    }

    /**
     * The project's own packages, from those the run wrote classes into: each
     * of them, and the root they share when it is a real one - three segments
     * or more ({@code io.elasticore.blueprint.domain}, not {@code io.elasticore}).
     */
    public static Set<String> ownRoots(Collection<String> packages) {
        Set<String> out = new TreeSet<String>();
        String common = null;
        for (String p : packages) {
            if (p == null || p.isEmpty()) continue;
            out.add(p);
            common = common == null ? p : commonPackage(common, p);
        }
        if (common != null && common.split("\\.", -1).length >= 3) out.add(common);
        return out;
    }

    private static String commonPackage(String a, String b) {
        String[] x = a.split("\\.", -1);
        String[] y = b.split("\\.", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(x.length, y.length) && x[i].equals(y[i]); i++) {
            if (sb.length() > 0) sb.append('.');
            sb.append(x[i]);
        }
        return sb.toString();
    }
}
