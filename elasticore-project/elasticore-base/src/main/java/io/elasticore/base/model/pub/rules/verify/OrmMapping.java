package io.elasticore.base.model.pub.rules.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A domain's JPA mapping, moved out of its classes into META-INF/orm.xml
 * ({@code mapping: xml} in env.yml).
 *
 * The domain classes are generated as always and then read back: every
 * annotation of jakarta.persistence is taken off the class, its fields and
 * its methods, and said instead in the XML JPA has always accepted in its
 * place. What is left is a domain model that names no persistence technology
 * - the hexagon's inside - while the ORM keeps everything it relies on:
 * dirty checking, the version, lazy loading, the lifecycle callbacks.
 *
 * Hibernate's @DynamicUpdate has no XML form and is dropped (every column is
 * written on update). Anything else this cannot express - @Formula, Envers,
 * an element collection - is left on the class and reported, so the output
 * never silently loses a mapping.
 */
final class OrmMapping {

    static final String NS = "https://jakarta.ee/xml/ns/persistence/orm";

    private static final Map<String, String> CALLBACK_TAGS = new LinkedHashMap<String, String>();

    static {
        CALLBACK_TAGS.put("PrePersist", "pre-persist");
        CALLBACK_TAGS.put("PostPersist", "post-persist");
        CALLBACK_TAGS.put("PreRemove", "pre-remove");
        CALLBACK_TAGS.put("PostRemove", "post-remove");
        CALLBACK_TAGS.put("PreUpdate", "pre-update");
        CALLBACK_TAGS.put("PostUpdate", "post-update");
        CALLBACK_TAGS.put("PostLoad", "post-load");
    }

    /** Hibernate annotations with no XML form that are safe to drop. */
    private static final Set<String> DROPPED = new HashSet<String>(Arrays.asList(
            "org.hibernate.annotations.DynamicUpdate", "org.hibernate.annotations.DynamicInsert"));

    private static final Set<String> FIELD_SUPPORTED = new HashSet<String>(Arrays.asList(
            "Id", "GeneratedValue", "SequenceGenerator", "Column", "Convert", "Version", "ManyToOne", "OneToOne",
            "OneToMany", "ManyToMany", "JoinColumn", "JoinTable", "Embedded", "EmbeddedId", "Transient", "Lob",
            "Enumerated", "Basic", "OrderBy", "AttributeOverride", "AttributeOverrides"));

    private static final Set<String> CLASS_SUPPORTED = new HashSet<String>(Arrays.asList(
            "Entity", "MappedSuperclass", "Embeddable", "Table", "EntityListeners", "Inheritance",
            "DiscriminatorColumn", "DiscriminatorValue", "IdClass", "Access"));

    // ---- what was read --------------------------------------------------

    /** One annotation, as written. Values: String (a token), Str (a literal), List, Anno. */
    static final class Anno {
        String simple;
        String fqcn;
        final Map<String, Object> args = new LinkedHashMap<String, Object>();
        String text;

        Object arg(String key) {
            return args.get(key);
        }
    }

    /** A string literal. */
    static final class Str {
        final String value;

        Str(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    static final class Member {
        final String name;
        final List<Anno> annos = new ArrayList<Anno>();

        Member(String name) {
            this.name = name;
        }
    }

    static final class ClassMap {
        String fqcn;
        String pkg;
        String kind;
        final List<Anno> annos = new ArrayList<Anno>();
        final List<Member> fields = new ArrayList<Member>();
        final Map<String, String> callbacks = new LinkedHashMap<String, String>();
        Map<String, String> imports;
    }

    private final Map<String, ClassMap> classes = new LinkedHashMap<String, ClassMap>();
    /** enum FQCN -> the converter class that now stands for its nested one. */
    private final Map<String, String> movedConverters = new HashMap<String, String>();
    private final List<String> warnings = new ArrayList<String>();

    List<String> warnings() {
        return warnings;
    }

    boolean isEmpty() {
        return classes.isEmpty();
    }

    /** The enum's converter now lives elsewhere; a @Convert naming the old one names this. */
    void converterMoved(String enumFqcn, String converterFqcn) {
        movedConverters.put(enumFqcn, converterFqcn);
    }

    /**
     * An enum's nested database converter, cut out: {the enum without it,
     * the converter as a class of its own in the given package}, or null
     * when the enum has none.
     */
    static String[] splitEnumConverter(String source, String enumName, String enumFqcn,
                                       String converterPkg, boolean kotlin) {
        String nl = source.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = source.replace("\r\n", "\n").split("\n", -1);
        int decl = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("class EntityConverter")) {
                decl = i;
                break;
            }
        }
        if (decl < 0) return null;
        int from = decl;
        // The converter keeps what its annotation said: auto-applied, it converts
        // a field of the enum that names no converter of its own.
        String converterAnnotation = "@Converter";
        if (from > 0 && lines[from - 1].trim().startsWith("@Converter")) {
            from--;
            converterAnnotation = lines[from].trim();
        }
        int depth = 0;
        int to = decl;
        boolean opened = false;
        for (int i = decl; i < lines.length; i++) {
            depth += count(lines[i], '{') - count(lines[i], '}');
            if (lines[i].contains("{")) opened = true;
            if (opened && depth <= 0) {
                to = i;
                break;
            }
        }
        String indent = lines[decl].substring(0, lines[decl].indexOf(lines[decl].trim()));
        StringBuilder body = new StringBuilder();
        for (int i = decl; i <= to; i++) {
            String l = lines[i].startsWith(indent) ? lines[i].substring(indent.length()) : lines[i].trim();
            if (i == decl) {
                l = kotlin
                        ? l.replace("class EntityConverter", "class " + enumName + "Converter")
                        : l.replace("public static class EntityConverter", "public class " + enumName + "Converter");
            }
            // Nested in the enum it called the enum's own lookups by their
            // bare names; outside it they are the enum's.
            if (kotlin) l = l.replaceAll("(?<![\\w.])(from\\w+)\\(", enumName + ".$1(");
            body.append(l).append('\n');
        }
        List<String> kept = new ArrayList<String>();
        for (int i = 0; i < lines.length; i++) {
            if (i >= from && i <= to) continue;
            kept.add(lines[i]);
        }
        String enumSource = withoutUnusedImports(kept, kotlin, nl);

        String end = kotlin ? "" : ";";
        StringBuilder conv = new StringBuilder();
        conv.append("package ").append(converterPkg).append(end).append('\n')
            .append('\n')
            .append("import jakarta.persistence.AttributeConverter").append(end).append('\n')
            .append("import jakarta.persistence.Converter").append(end).append('\n')
            .append("import ").append(enumFqcn).append(end).append('\n')
            .append('\n')
            .append("/**\n")
            .append(" * ").append(enumName).append(" in its column. The enum is the domain's and names no\n")
            .append(" * persistence technology; this is its persistence side (mapping: xml).\n")
            .append(" *\n")
            .append(" * Generated by ElastiCORE. Do not edit outside the ElastiCORE guidelines.\n")
            .append(" */\n")
            .append(converterAnnotation).append('\n')
            .append(body);
        return new String[]{enumSource, conv.toString().replace("\n", nl)};
    }

    // ---- reading a class --------------------------------------------------

    private static final Pattern CLASS_DECL = Pattern.compile("\\b(?:class|interface|object)\\s+(\\w+)");
    private static final Pattern KT_FUN = Pattern.compile("\\bfun\\s+(\\w+)\\s*\\(");
    private static final Pattern JAVA_METHOD = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final|synchronized|abstract|default)\\s+)*"
                    + "[\\w<>\\[\\],.? ]+?\\s+(\\w+)\\s*\\([^;]*$");
    private static final Pattern KT_PROPERTY = Pattern.compile("\\b(?:var|val)\\s+(\\w+)");
    // The terminator is optional: an initializer may continue on the next line.
    private static final Pattern JAVA_FIELD = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final|transient|volatile)\\s+)*"
                    + "[\\w<>\\[\\],.? ]+?\\s+(\\w+)\\s*(=.*)?;?\\s*$");

    /**
     * The class without its JPA annotations; what they said is remembered for
     * the XML. The class's own name and package say where it is.
     */
    String extract(String source, String pkg, String className, boolean kotlin) {
        String nl = source.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = source.replace("\r\n", "\n").split("\n", -1);
        Map<String, String> imports = imports(lines, kotlin);

        kotlinSource = kotlin;
        ClassMap cm = new ClassMap();
        cm.fqcn = pkg + "." + className;
        cm.pkg = pkg;
        cm.imports = imports;

        List<String> out = new ArrayList<String>();
        List<Object[]> pending = new ArrayList<Object[]>(); // {outIndex, Anno}
        boolean inComment = false;
        boolean topClassSeen = false;
        int depth = 0;
        int classDepth = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String t = line.trim();

            if (inComment) {
                out.add(line);
                if (t.contains("*/")) inComment = false;
                continue;
            }
            if (t.startsWith("/*")) {
                out.add(line);
                if (!t.contains("*/")) inComment = true;
                continue;
            }
            if (t.startsWith("//") || t.startsWith("import ") || t.startsWith("package ") || t.isEmpty()) {
                out.add(line);
                continue;
            }

            if (t.startsWith("@")) {
                // An annotation, maybe spread over lines until it closes.
                StringBuilder text = new StringBuilder(t);
                while (!balanced(text.toString()) && i + 1 < lines.length) {
                    i++;
                    text.append(' ').append(lines[i].trim());
                }
                List<Anno> annos = new ArrayList<Anno>();
                String rest = parseAnnotations(text.toString(), annos, imports, pkg);
                String indent = line.substring(0, line.indexOf('@'));
                for (Anno a : annos) {
                    out.add(indent + a.text);
                    pending.add(new Object[]{out.size() - 1, a});
                }
                if (rest.trim().isEmpty()) continue;
                // A declaration on the same line as its annotations.
                line = indent + rest.trim();
                t = line.trim();
            }

            // A declaration: whatever is pending belongs to it.
            String member = null;
            boolean isMethod = false;
            Matcher cls = CLASS_DECL.matcher(t);
            if (cls.find() && !t.startsWith("return") && !t.contains("=")) {
                if (!topClassSeen && cls.group(1).equals(className)) {
                    topClassSeen = true;
                    classDepth = depth;
                    applyClass(cm, pending, out);
                }
                pending.clear();
            } else if (!pending.isEmpty()) {
                Matcher m = kotlin ? KT_FUN.matcher(t) : JAVA_METHOD.matcher(t);
                if (m.find()) {
                    member = m.group(1);
                    isMethod = true;
                } else {
                    Matcher f = kotlin ? KT_PROPERTY.matcher(t) : JAVA_FIELD.matcher(t);
                    if (f.find()) member = f.group(1);
                }
                // Only members of the class itself, not of a class nested in it.
                if (member != null && topClassSeen && depth == classDepth + 1) {
                    if (isMethod) applyMethod(cm, member, pending, out);
                    else applyField(cm, member, pending, out);
                }
                pending.clear();
            }

            out.add(line);
            depth += count(line, '{') - count(line, '}');
        }

        if (cm.kind != null || !cm.callbacks.isEmpty()) {
            if (cm.kind == null) cm.kind = "listener";
            classes.put(cm.fqcn, cm);
        }

        // Lines emptied by a removed annotation go, and so do imports left unused.
        List<String> kept = new ArrayList<String>();
        for (String l : out) {
            if (l == null) continue;
            kept.add(l);
        }
        return withoutUnusedImports(kept, kotlin, nl);
    }

    private void applyClass(ClassMap cm, List<Object[]> pending, List<String> out) {
        List<Anno> jpa = new ArrayList<Anno>();
        boolean unsupported = false;
        for (Object[] p : pending) {
            Anno a = (Anno) p[1];
            if (isJpa(a)) {
                jpa.add(a);
                if (!CLASS_SUPPORTED.contains(a.simple)) unsupported = true;
            }
        }
        for (Object[] p : pending) {
            Anno a = (Anno) p[1];
            if (a.fqcn != null && a.fqcn.startsWith("org.hibernate.") && !DROPPED.contains(a.fqcn)) {
                warnings.add(String.format("%s: @%s is Hibernate's and stays on the class", cm.fqcn, a.simple));
            }
        }
        if (unsupported) {
            warnings.add(String.format("%s: @%s has no orm.xml form here - its JPA class annotations stay on the class",
                    cm.fqcn, firstUnsupported(jpa, CLASS_SUPPORTED)));
            dropOnly(pending, out, DROPPED);
            for (Anno a : jpa) {
                if (a.simple.equals("Entity")) cm.kind = null;
            }
            return;
        }
        for (Anno a : jpa) {
            if (a.simple.equals("Entity")) cm.kind = "entity";
            else if (a.simple.equals("MappedSuperclass")) cm.kind = "mapped-superclass";
            else if (a.simple.equals("Embeddable")) cm.kind = "embeddable";
            cm.annos.add(a);
        }
        removeJpaAndDropped(pending, out);
        // Kotlin's compiler plugins open a class by its annotation; with @Entity
        // gone, this is the one they can key on (see PersistentModel).
        if (kotlinSource && cm.kind != null && !pending.isEmpty()) {
            int at = (Integer) pending.get(0)[0];
            String first = ((Anno) pending.get(0)[1]).text;
            String line = out.get(at) != null ? out.get(at) : "";
            String indent = line.isEmpty() ? "" : line.substring(0, Math.max(0, line.indexOf('@')));
            out.set(at, indent + "@io.elasticore.springboot3.entity.PersistentModel"
                    + (out.get(at) != null ? "\n" + line : ""));
            if (first == null) return;
        }
    }

    private boolean kotlinSource;

    private void applyField(ClassMap cm, String name, List<Object[]> pending, List<String> out) {
        Member m = new Member(name);
        boolean unsupported = false;
        for (Object[] p : pending) {
            Anno a = (Anno) p[1];
            if (!isJpa(a)) {
                if (a.fqcn != null && (a.fqcn.startsWith("org.hibernate.")) && !DROPPED.contains(a.fqcn)) {
                    warnings.add(String.format("%s.%s: @%s is Hibernate's and stays on the field", cm.fqcn, name, a.simple));
                }
                continue;
            }
            if (!FIELD_SUPPORTED.contains(a.simple)) unsupported = true;
            m.annos.add(a);
        }
        if (m.annos.isEmpty()) {
            dropOnly(pending, out, DROPPED);
            return;
        }
        if (unsupported) {
            warnings.add(String.format("%s.%s: @%s has no orm.xml form here - its JPA annotations stay on the field",
                    cm.fqcn, name, firstUnsupported(m.annos, FIELD_SUPPORTED)));
            return;
        }
        cm.fields.add(m);
        removeJpaAndDropped(pending, out);
    }

    private void applyMethod(ClassMap cm, String name, List<Object[]> pending, List<String> out) {
        boolean any = false;
        for (Object[] p : pending) {
            Anno a = (Anno) p[1];
            if (isJpa(a) && CALLBACK_TAGS.containsKey(a.simple)) {
                cm.callbacks.put(CALLBACK_TAGS.get(a.simple), name);
                any = true;
            }
        }
        if (any) removeJpaAndDropped(pending, out);
    }

    private void removeJpaAndDropped(List<Object[]> pending, List<String> out) {
        for (Object[] p : pending) {
            Anno a = (Anno) p[1];
            if (isJpa(a) || (a.fqcn != null && DROPPED.contains(a.fqcn))) out.set((Integer) p[0], null);
        }
    }

    private void dropOnly(List<Object[]> pending, List<String> out, Set<String> which) {
        for (Object[] p : pending) {
            Anno a = (Anno) p[1];
            if (a.fqcn != null && which.contains(a.fqcn)) out.set((Integer) p[0], null);
        }
    }

    private static String firstUnsupported(List<Anno> annos, Set<String> supported) {
        for (Anno a : annos) {
            if (!supported.contains(a.simple)) return a.simple;
        }
        return "?";
    }

    private static boolean isJpa(Anno a) {
        return a.fqcn != null && (a.fqcn.startsWith("jakarta.persistence.") || a.fqcn.startsWith("javax.persistence."));
    }

    private static int count(String s, char c) {
        int n = 0;
        boolean quoted = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' && (i == 0 || s.charAt(i - 1) != '\\')) quoted = !quoted;
            if (!quoted && ch == c) n++;
        }
        return n;
    }

    private static boolean balanced(String s) {
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' && (i == 0 || s.charAt(i - 1) != '\\')) quoted = !quoted;
            if (quoted) continue;
            if (ch == '(' || ch == '{' || ch == '[') depth++;
            if (ch == ')' || ch == '}' || ch == ']') depth--;
        }
        return depth <= 0;
    }

    private static Map<String, String> imports(String[] lines, boolean kotlin) {
        Map<String, String> out = new HashMap<String, String>();
        for (String l : lines) {
            String t = l.trim();
            if (!t.startsWith("import ")) continue;
            String name = t.substring(7).replace(";", "").trim();
            if (name.startsWith("static ")) continue;
            int as = name.indexOf(" as ");
            if (as > 0) {
                out.put(name.substring(as + 4).trim(), name.substring(0, as).trim());
                continue;
            }
            if (name.endsWith(".*")) continue;
            out.put(name.substring(name.lastIndexOf('.') + 1), name);
        }
        return out;
    }

    // ---- parsing annotation text -----------------------------------------

    /** Parses the annotations at the start of the text; returns what follows them. */
    static String parseAnnotations(String text, List<Anno> into, Map<String, String> imports, String pkg) {
        int i = 0;
        while (i < text.length()) {
            while (i < text.length() && text.charAt(i) == ' ') i++;
            if (i >= text.length() || text.charAt(i) != '@') break;
            int start = i;
            i++;
            int nameStart = i;
            while (i < text.length() && (Character.isJavaIdentifierPart(text.charAt(i)) || text.charAt(i) == '.' || text.charAt(i) == ':')) i++;
            String name = text.substring(nameStart, i);
            Anno a = new Anno();
            if (i < text.length() && text.charAt(i) == '(') {
                int close = matching(text, i);
                parseArgs(text.substring(i + 1, close), a, imports, pkg);
                i = close + 1;
            }
            a.text = text.substring(start, i);
            a.simple = name.substring(name.lastIndexOf('.') + 1);
            a.fqcn = name.contains(".") && Character.isLowerCase(name.charAt(0)) ? name : imports.get(name);
            into.add(a);
        }
        return text.substring(i);
    }

    private static int matching(String s, int open) {
        int depth = 0;
        boolean quoted = false;
        for (int i = open; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' && s.charAt(i - 1) != '\\') quoted = !quoted;
            if (quoted) continue;
            if (ch == '(' || ch == '{' || ch == '[') depth++;
            if (ch == ')' || ch == '}' || ch == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return s.length() - 1;
    }

    private static List<String> splitTop(String s) {
        List<String> out = new ArrayList<String>();
        int depth = 0;
        boolean quoted = false;
        int from = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' && (i == 0 || s.charAt(i - 1) != '\\')) quoted = !quoted;
            if (quoted) continue;
            if (ch == '(' || ch == '{' || ch == '[') depth++;
            if (ch == ')' || ch == '}' || ch == ']') depth--;
            if (ch == ',' && depth == 0) {
                out.add(s.substring(from, i).trim());
                from = i + 1;
            }
        }
        String last = s.substring(from).trim();
        if (!last.isEmpty()) out.add(last);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void parseArgs(String body, Anno a, Map<String, String> imports, String pkg) {
        for (String part : splitTop(body)) {
            int eq = topLevelEquals(part);
            if (eq < 0) {
                // Kotlin writes an array argument as its elements: (A::class, B::class).
                Object v = value(part, imports, pkg);
                Object had = a.args.get("value");
                if (had == null) {
                    a.args.put("value", v);
                } else {
                    List<Object> l;
                    if (had instanceof List) {
                        l = (List<Object>) had;
                    } else {
                        l = new ArrayList<Object>();
                        l.add(had);
                    }
                    l.add(v);
                    a.args.put("value", l);
                }
            } else {
                a.args.put(part.substring(0, eq).trim(), value(part.substring(eq + 1).trim(), imports, pkg));
            }
        }
    }

    private static int topLevelEquals(String s) {
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' && (i == 0 || s.charAt(i - 1) != '\\')) quoted = !quoted;
            if (quoted) continue;
            if (ch == '(' || ch == '{' || ch == '[') depth++;
            if (ch == ')' || ch == '}' || ch == ']') depth--;
            if (ch == '=' && depth == 0) return i;
        }
        return -1;
    }

    private static Object value(String v, Map<String, String> imports, String pkg) {
        v = v.trim();
        if (v.startsWith("\"")) {
            return new Str(v.substring(1, v.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        if (v.startsWith("{") || v.startsWith("[")) {
            List<Object> list = new ArrayList<Object>();
            for (String item : splitTop(v.substring(1, v.length() - 1))) list.add(value(item, imports, pkg));
            return list;
        }
        if (v.startsWith("@") || v.matches("^[A-Z][\\w.]*\\(.*")) {
            List<Anno> nested = new ArrayList<Anno>();
            parseAnnotations(v.startsWith("@") ? v : "@" + v, nested, imports, pkg);
            return nested.isEmpty() ? v : nested.get(0);
        }
        return v;
    }

    /** X.class, X::class, pkg.X.Inner.class: the class's binary name. */
    static String classOf(Object v, ClassMap cm) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.endsWith("::class.java")) s = s.substring(0, s.length() - "::class.java".length());
        else if (s.endsWith("::class")) s = s.substring(0, s.length() - "::class".length());
        else if (s.endsWith(".class")) s = s.substring(0, s.length() - ".class".length());
        String[] parts = s.split("\\.");
        StringBuilder out = new StringBuilder();
        int i = 0;
        if (Character.isLowerCase(parts[0].charAt(0))) {
            // Written in full: the package runs to the first capitalised part.
            while (i < parts.length && Character.isLowerCase(parts[i].charAt(0))) {
                if (out.length() > 0) out.append('.');
                out.append(parts[i++]);
            }
            out.append('.').append(parts[i++]);
        } else {
            String imported = cm.imports.get(parts[0]);
            out.append(imported != null ? imported : cm.pkg + "." + parts[0]);
            i = 1;
        }
        for (; i < parts.length; i++) out.append('$').append(parts[i]);
        return out.toString();
    }

    private static String enumConstant(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.substring(s.lastIndexOf('.') + 1);
    }

    private static List<Object> list(Object v) {
        if (v == null) return new ArrayList<Object>();
        if (v instanceof List) {
            @SuppressWarnings("unchecked") List<Object> l = (List<Object>) v;
            return l;
        }
        List<Object> one = new ArrayList<Object>();
        one.add(v);
        return one;
    }

    // ---- writing the XML ----------------------------------------------------

    String toXml() {
        return toXml(null, null);
    }

    private static final Pattern CLASS_BLOCK = Pattern.compile(
            "(?ms)^    <(mapped-superclass|entity|embeddable) class=\"([^\"]+)\"[^\\n]*>\\n.*?^    </\\1>\\n");

    /**
     * The XML, keeping in place the classes of the previous one that belong to
     * the other domains. A run over one domain (-Pdomain) maps that domain's
     * classes only, while the module's other domains share the file: written
     * from this mapping alone, theirs would be gone. A previous class is theirs
     * when its package is one of theirs (otherPackages) and this run did not
     * map it - so a class the generated domain no longer has, or has moved,
     * leaves the file. A class new to this run goes after the last class of
     * its package, where a whole run puts it.
     */
    String toXml(String previous, Set<String> otherPackages) {
        // fqcn -> {kind, block}; a block of ours (this run's) is null and written fresh.
        Map<String, List<String[]>> byKind = new LinkedHashMap<String, List<String[]>>();
        for (String kind : new String[]{"mapped-superclass", "entity", "embeddable"}) {
            byKind.put(kind, new ArrayList<String[]>());
        }
        if (previous != null && otherPackages != null) {
            Matcher m = CLASS_BLOCK.matcher(previous.replace("\r\n", "\n"));
            while (m.find()) {
                String fqcn = m.group(2);
                ClassMap cm = classes.get(fqcn);
                if (cm != null) {
                    if (m.group(1).equals(cm.kind)) byKind.get(cm.kind).add(new String[]{fqcn, null});
                } else if (otherPackages.contains(packageOf(fqcn))) {
                    byKind.get(m.group(1)).add(new String[]{fqcn, m.group()});
                }
            }
        }
        for (ClassMap cm : classes.values()) {
            List<String[]> list = byKind.get(cm.kind);
            if (list == null || contains(list, cm.fqcn)) continue;
            int at = list.size();
            for (int i = 0; previous != null && i < list.size(); i++) {
                if (packageOf(list.get(i)[0]).equals(cm.pkg)) at = i + 1;
            }
            list.add(at, new String[]{cm.fqcn, null});
        }
        StringBuilder x = new StringBuilder();
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
         .append("<!-- Generated by ElastiCORE (mapping: xml). The domain classes carry no JPA annotations;\n")
         .append("     their mapping is here. Regenerated on every run: change the DSL, not this file. -->\n")
         .append("<entity-mappings xmlns=\"").append(NS).append("\"\n")
         .append("                 xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
         .append("                 xsi:schemaLocation=\"").append(NS).append(" ").append(NS).append("/orm_3_1.xsd\"\n")
         .append("                 version=\"3.1\">\n");
        for (List<String[]> list : byKind.values()) {
            for (String[] c : list) {
                if (c[1] != null) x.append('\n').append(c[1]);
                else writeClass(x, classes.get(c[0]));
            }
        }
        x.append("</entity-mappings>\n");
        return x.toString();
    }

    private static String packageOf(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot < 0 ? "" : fqcn.substring(0, dot);
    }

    private static boolean contains(List<String[]> list, String fqcn) {
        for (String[] c : list) {
            if (c[0].equals(fqcn)) return true;
        }
        return false;
    }

    private void writeClass(StringBuilder x, ClassMap cm) {
        x.append("\n    <").append(cm.kind).append(" class=\"").append(cm.fqcn).append("\" access=\"FIELD\"");
        Anno entity = find(cm.annos, "Entity");
        if (entity != null && entity.arg("name") != null) attr(x, "name", entity.arg("name"));
        x.append(">\n");

        if ("entity".equals(cm.kind)) {
            Anno table = find(cm.annos, "Table");
            if (table != null) writeTable(x, table);
            Anno idClass = find(cm.annos, "IdClass");
            if (idClass != null) {
                x.append("        <id-class class=\"").append(classOf(idClass.arg("value"), cm)).append("\"/>\n");
            }
            Anno inheritance = find(cm.annos, "Inheritance");
            if (inheritance != null) {
                x.append("        <inheritance");
                if (inheritance.arg("strategy") != null) attr(x, "strategy", enumConstant(inheritance.arg("strategy")));
                x.append("/>\n");
            }
            Anno dv = find(cm.annos, "DiscriminatorValue");
            if (dv != null) x.append("        <discriminator-value>").append(esc(dv.arg("value").toString())).append("</discriminator-value>\n");
            Anno dc = find(cm.annos, "DiscriminatorColumn");
            if (dc != null) {
                x.append("        <discriminator-column");
                if (dc.arg("name") != null) attr(x, "name", dc.arg("name"));
                if (dc.arg("discriminatorType") != null) attr(x, "discriminator-type", enumConstant(dc.arg("discriminatorType")));
                if (dc.arg("length") != null) attr(x, "length", dc.arg("length"));
                x.append("/>\n");
            }
        }
        if (!"embeddable".equals(cm.kind)) {
            Anno listeners = find(cm.annos, "EntityListeners");
            if (listeners != null) {
                x.append("        <entity-listeners>\n");
                for (Object v : list(listeners.arg("value"))) {
                    String cls = classOf(v, cm);
                    ClassMap listener = classes.get(cls);
                    if (listener == null || listener.callbacks.isEmpty()) {
                        x.append("            <entity-listener class=\"").append(cls).append("\"/>\n");
                    } else {
                        x.append("            <entity-listener class=\"").append(cls).append("\">\n");
                        for (String tag : CALLBACK_TAGS.values()) {
                            String method = listener.callbacks.get(tag);
                            if (method != null) {
                                x.append("                <").append(tag).append(" method-name=\"").append(method).append("\"/>\n");
                            }
                        }
                        x.append("            </entity-listener>\n");
                    }
                }
                x.append("        </entity-listeners>\n");
            }
            for (String tag : CALLBACK_TAGS.values()) {
                String method = cm.callbacks.get(tag);
                if (method != null) x.append("        <").append(tag).append(" method-name=\"").append(method).append("\"/>\n");
            }
        }
        writeAttributes(x, cm);
        x.append("    </").append(cm.kind).append(">\n");
    }

    private void writeTable(StringBuilder x, Anno table) {
        x.append("        <table");
        for (String k : new String[]{"name", "schema", "catalog"}) {
            if (table.arg(k) != null) attr(x, k, table.arg(k));
        }
        List<Object> indexes = list(table.arg("indexes"));
        List<Object> uniques = list(table.arg("uniqueConstraints"));
        if (indexes.isEmpty() && uniques.isEmpty()) {
            x.append("/>\n");
            return;
        }
        x.append(">\n");
        for (Object u : uniques) {
            if (!(u instanceof Anno)) continue;
            Anno uc = (Anno) u;
            x.append("            <unique-constraint");
            if (uc.arg("name") != null) attr(x, "name", uc.arg("name"));
            x.append(">\n");
            for (Object col : list(uc.arg("columnNames"))) {
                x.append("                <column-name>").append(esc(col.toString())).append("</column-name>\n");
            }
            x.append("            </unique-constraint>\n");
        }
        for (Object i : indexes) {
            if (!(i instanceof Anno)) continue;
            Anno idx = (Anno) i;
            x.append("            <index");
            if (idx.arg("name") != null) attr(x, "name", idx.arg("name"));
            if (idx.arg("columnList") != null) attr(x, "column-list", idx.arg("columnList"));
            if (idx.arg("unique") != null) attr(x, "unique", idx.arg("unique"));
            x.append("/>\n");
        }
        x.append("        </table>\n");
    }

    private void writeAttributes(StringBuilder x, ClassMap cm) {
        Map<String, List<StringBuilder>> byKind = new LinkedHashMap<String, List<StringBuilder>>();
        for (String k : new String[]{"id", "embedded-id", "basic", "version", "many-to-one", "one-to-many",
                "one-to-one", "many-to-many", "embedded", "transient"}) {
            byKind.put(k, new ArrayList<StringBuilder>());
        }
        for (Member m : cm.fields) {
            String kind = kindOf(m);
            StringBuilder e = new StringBuilder();
            writeMember(e, kind, m, cm);
            byKind.get(kind).add(e);
        }
        boolean any = false;
        for (List<StringBuilder> l : byKind.values()) any |= !l.isEmpty();
        if (!any) return;
        x.append("        <attributes>\n");
        for (List<StringBuilder> l : byKind.values()) {
            for (StringBuilder e : l) x.append(e);
        }
        x.append("        </attributes>\n");
    }

    private static String kindOf(Member m) {
        if (find(m.annos, "Id") != null) return "id";
        if (find(m.annos, "EmbeddedId") != null) return "embedded-id";
        if (find(m.annos, "Version") != null) return "version";
        if (find(m.annos, "ManyToOne") != null) return "many-to-one";
        if (find(m.annos, "OneToMany") != null) return "one-to-many";
        if (find(m.annos, "OneToOne") != null) return "one-to-one";
        if (find(m.annos, "ManyToMany") != null) return "many-to-many";
        if (find(m.annos, "Embedded") != null) return "embedded";
        if (find(m.annos, "Transient") != null) return "transient";
        return "basic";
    }

    private void writeMember(StringBuilder e, String kind, Member m, ClassMap cm) {
        String ind = "            ";
        e.append(ind).append('<').append(kind).append(" name=\"").append(m.name).append('"');
        Anno rel = find(m.annos, relationName(kind));
        if (rel != null) {
            if (rel.arg("fetch") != null) attr(e, "fetch", enumConstant(rel.arg("fetch")));
            if (rel.arg("mappedBy") != null) attr(e, "mapped-by", rel.arg("mappedBy"));
            if (rel.arg("optional") != null) attr(e, "optional", rel.arg("optional"));
            if (rel.arg("orphanRemoval") != null) attr(e, "orphan-removal", rel.arg("orphanRemoval"));
            if (rel.arg("targetEntity") != null) attr(e, "target-entity", classOf(rel.arg("targetEntity"), cm));
        }
        Anno basic = find(m.annos, "Basic");
        if (basic != null && "basic".equals(kind)) {
            if (basic.arg("fetch") != null) attr(e, "fetch", enumConstant(basic.arg("fetch")));
            if (basic.arg("optional") != null) attr(e, "optional", basic.arg("optional"));
        }
        StringBuilder body = new StringBuilder();
        String inner = ind + "    ";

        if ("transient".equals(kind)) {
            e.append("/>\n");
            return;
        }
        if ("id".equals(kind) || "basic".equals(kind) || "version".equals(kind)) {
            Anno col = find(m.annos, "Column");
            if (col != null) column(body, inner, "column", col);
        }
        if ("id".equals(kind)) {
            Anno gen = find(m.annos, "GeneratedValue");
            if (gen != null) {
                body.append(inner).append("<generated-value");
                if (gen.arg("strategy") != null) attr(body, "strategy", enumConstant(gen.arg("strategy")));
                if (gen.arg("generator") != null) attr(body, "generator", gen.arg("generator"));
                body.append("/>\n");
            }
            Anno seq = find(m.annos, "SequenceGenerator");
            if (seq != null) {
                body.append(inner).append("<sequence-generator");
                if (seq.arg("name") != null) attr(body, "name", seq.arg("name"));
                if (seq.arg("sequenceName") != null) attr(body, "sequence-name", seq.arg("sequenceName"));
                if (seq.arg("initialValue") != null) attr(body, "initial-value", seq.arg("initialValue"));
                if (seq.arg("allocationSize") != null) attr(body, "allocation-size", seq.arg("allocationSize"));
                body.append("/>\n");
            }
        }
        if ("basic".equals(kind)) {
            if (find(m.annos, "Lob") != null) body.append(inner).append("<lob/>\n");
            Anno en = find(m.annos, "Enumerated");
            if (en != null) {
                String type = en.arg("value") == null ? "ORDINAL" : enumConstant(en.arg("value"));
                body.append(inner).append("<enumerated>").append(type).append("</enumerated>\n");
            }
            Anno conv = find(m.annos, "Convert");
            if (conv != null && find(m.annos, "Lob") == null && en == null) {
                body.append(inner).append("<convert converter=\"").append(converter(conv, cm)).append("\"/>\n");
            }
        }
        if ("one-to-many".equals(kind) || "many-to-many".equals(kind)) {
            Anno order = find(m.annos, "OrderBy");
            if (order != null) {
                body.append(inner).append("<order-by>")
                    .append(order.arg("value") == null ? "" : esc(order.arg("value").toString())).append("</order-by>\n");
            }
        }
        if (kind.startsWith("many-to-") || kind.startsWith("one-to-")) {
            Anno table = find(m.annos, "JoinTable");
            if (table != null) {
                body.append(inner).append("<join-table");
                if (table.arg("name") != null) attr(body, "name", table.arg("name"));
                body.append(">\n");
                for (Object jc : list(table.arg("joinColumns"))) {
                    if (jc instanceof Anno) column(body, inner + "    ", "join-column", (Anno) jc);
                }
                for (Object jc : list(table.arg("inverseJoinColumns"))) {
                    if (jc instanceof Anno) column(body, inner + "    ", "inverse-join-column", (Anno) jc);
                }
                body.append(inner).append("</join-table>\n");
            }
            for (Anno a : m.annos) {
                if (a.simple.equals("JoinColumn")) column(body, inner, "join-column", a);
            }
            if (rel != null && rel.arg("cascade") != null) {
                body.append(inner).append("<cascade>\n");
                for (Object c : list(rel.arg("cascade"))) {
                    body.append(inner).append("    <cascade-").append(enumConstant(c).toLowerCase()).append("/>\n");
                }
                body.append(inner).append("</cascade>\n");
            }
        }
        if ("embedded".equals(kind) || "embedded-id".equals(kind)) {
            List<Anno> overrides = new ArrayList<Anno>();
            for (Anno a : m.annos) {
                if (a.simple.equals("AttributeOverride")) overrides.add(a);
                if (a.simple.equals("AttributeOverrides")) {
                    for (Object o : list(a.arg("value"))) {
                        if (o instanceof Anno) overrides.add((Anno) o);
                    }
                }
            }
            for (Anno o : overrides) {
                body.append(inner).append("<attribute-override");
                if (o.arg("name") != null) attr(body, "name", o.arg("name"));
                body.append(">\n");
                if (o.arg("column") instanceof Anno) column(body, inner + "    ", "column", (Anno) o.arg("column"));
                body.append(inner).append("</attribute-override>\n");
            }
        }
        if (body.length() == 0) {
            e.append("/>\n");
        } else {
            e.append(">\n").append(body).append(ind).append("</").append(kind).append(">\n");
        }
    }

    private static String relationName(String kind) {
        switch (kind) {
            case "many-to-one": return "ManyToOne";
            case "one-to-many": return "OneToMany";
            case "one-to-one": return "OneToOne";
            case "many-to-many": return "ManyToMany";
            default: return "-";
        }
    }

    private String converter(Anno conv, ClassMap cm) {
        String cls = classOf(conv.arg("converter"), cm);
        int dollar = cls.lastIndexOf('$');
        if (dollar > 0) {
            String moved = movedConverters.get(cls.substring(0, dollar));
            if (moved != null) return moved;
        }
        return cls;
    }

    private static void column(StringBuilder b, String ind, String tag, Anno col) {
        b.append(ind).append('<').append(tag);
        String[][] names = {{"name", "name"}, {"referencedColumnName", "referenced-column-name"},
                {"unique", "unique"}, {"nullable", "nullable"}, {"insertable", "insertable"},
                {"updatable", "updatable"}, {"columnDefinition", "column-definition"}, {"table", "table"},
                {"length", "length"}, {"precision", "precision"}, {"scale", "scale"}};
        for (String[] n : names) {
            if (col.arg(n[0]) != null) attr(b, n[1], col.arg(n[0]));
        }
        b.append("/>\n");
    }

    private static Anno find(List<Anno> annos, String simple) {
        for (Anno a : annos) {
            if (a.simple.equals(simple)) return a;
        }
        return null;
    }

    private static void attr(StringBuilder b, String name, Object value) {
        b.append(' ').append(name).append("=\"").append(esc(value.toString())).append('"');
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ---- imports ------------------------------------------------------------

    /** The source without the persistence imports nothing in it uses any more. */
    static String withoutUnusedPersistenceImports(String source, boolean kotlin) {
        String nl = source.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<String>(Arrays.asList(source.replace("\r\n", "\n").split("\n", -1)));
        return withoutUnusedImports(lines, kotlin, nl);
    }

    private static String withoutUnusedImports(List<String> lines, boolean kotlin, String nl) {
        StringBuilder body = new StringBuilder();
        for (String l : lines) {
            if (!l.trim().startsWith("import ")) body.append(l).append('\n');
        }
        String text = body.toString();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            String t = l.trim();
            if (t.startsWith("import ")) {
                String name = t.substring(7).replace(";", "").trim();
                boolean ours = name.startsWith("jakarta.persistence.") || name.startsWith("javax.persistence.")
                        || name.startsWith("org.hibernate.annotations.Dynamic");
                if (ours && !name.endsWith(".*")) {
                    String simple = name.substring(name.lastIndexOf('.') + 1);
                    // Used by its simple name: kotlin.jvm.Transient is not the import's Transient.
                    if (!Pattern.compile("(?<![\\w.])" + Pattern.quote(simple) + "\\b").matcher(text).find()) continue;
                }
            }
            out.append(l);
            if (i < lines.size() - 1) out.append(nl);
        }
        return out.toString();
    }
}
