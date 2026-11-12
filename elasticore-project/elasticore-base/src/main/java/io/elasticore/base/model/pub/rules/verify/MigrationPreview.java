package io.elasticore.base.model.pub.rules.verify;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the first 3.0 build of a project 2.x generated would do, found by
 * running it with nothing written (elcoreMigrationCheck): each file's status
 * as the build would print it, the imports a merge would take out of a file
 * the developer edited, the files it would remove, and the files 2.x wrote
 * that the 3.0 code does not - with the code of the developer's that uses
 * them. The run is the real one; only the writes, the deletes and the cache
 * are held back, so what it reports is what the build will do.
 */
final class MigrationPreview {

    /** A status line as the run prints it: two spaces, then the status. */
    private static final Pattern STATUS = Pattern.compile(
            "^\\s+\\S+\\s+(\\S.*?)\\s{2}((?:WROTE|UNCHANGED|MERGED|CONFLICT|KEPT|SKIP|REMOVED|REGENERATED|ERROR)\\S*.*)$");

    private final Map<String, String> statuses = new TreeMap<String, String>();
    private final Map<String, List<String>> droppedImports = new TreeMap<String, List<String>>();
    private final List<String> removed = new ArrayList<String>();
    private final List<File> removedFiles = new ArrayList<File>();
    private final Map<String, List<String>> removedUsers = new TreeMap<String, List<String>>();
    private final Map<String, List<String>> leftBehind = new TreeMap<String, List<String>>();
    private final List<String> notes = new ArrayList<String>();
    private final Map<File, String> written = new LinkedHashMap<File, String>();
    private java.io.PrintStream shown;

    /**
     * Where the run prints while it is checked: the console as before, and
     * each line read here for the file's status. The charset by name: this
     * module compiles for Java 8, which has no PrintStream(..., Charset).
     */
    @SuppressWarnings("JdkObsolete")
    java.io.PrintStream tee(final java.io.PrintStream to) {
        this.shown = to;
        java.io.OutputStream lines = new java.io.OutputStream() {
            private final java.io.ByteArrayOutputStream current = new java.io.ByteArrayOutputStream();

            @Override
            public void write(int b) {
                to.write(b);
                if (b == '\n') {
                    line(new String(current.toByteArray(), java.nio.charset.Charset.defaultCharset())
                            .replace("\r", ""));
                    current.reset();
                } else {
                    current.write(b);
                }
            }

            @Override
            public void flush() {
                to.flush();
            }
        };
        try {
            // The console's own charset, as System.out encodes a normal run's output.
            return new java.io.PrintStream(
                    lines, true, java.nio.charset.Charset.defaultCharset().name());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The console the run printed to before the check. */
    java.io.PrintStream shown() {
        return shown;
    }

    /** A file the run would write, and its text. */
    void wrote(File file, String text) {
        written.put(file, text);
    }

    Map<File, String> written() {
        return written;
    }

    /** One line the run printed: a file's status, when it is one. */
    void line(String text) {
        Matcher m = STATUS.matcher(text);
        if (m.find()) statuses.put(m.group(1).trim(), m.group(2).trim());
    }

    /**
     * A source the run would merge: the imports of the developer's file the
     * merge does not keep and the developer's code may need - one they added,
     * or one the generated code had whose class a line they wrote names. An
     * import only the generated code used goes with it and is no loss.
     */
    void merged(String key, String base, String yours, String merged) {
        List<String> dropped = dropped(base, yours, merged);
        if (!dropped.isEmpty()) droppedImports.put(key, dropped);
    }

    /** The imports of yours the written text does not keep and your lines may need (see merged). */
    static List<String> dropped(String base, String yours, String written) {
        Set<String> kept = imports(written);
        Set<String> theirs = imports(base);
        String added = addedLines(base, yours);
        List<String> dropped = new ArrayList<String>();
        for (String imp : imports(yours)) {
            if (kept.contains(imp)) continue;
            if (!theirs.contains(imp) || namedIn(imp, added) || wildcardNeeded(imp, added, kept)) dropped.add(imp);
        }
        return dropped;
    }

    /** Classes everyone uses without an import. */
    private static final Set<String> JAVA_LANG = new java.util.HashSet<String>(java.util.Arrays.asList(
            "String",
            "Object",
            "Integer",
            "Long",
            "Boolean",
            "Double",
            "Float",
            "Short",
            "Byte",
            "Character",
            "Override",
            "Deprecated",
            "SuppressWarnings",
            "Exception",
            "RuntimeException",
            "Math",
            "System",
            "StringBuilder",
            "Iterable",
            "Void",
            "Class",
            "Thread",
            "Number",
            "Enum",
            "Throwable",
            "Error"));

    /**
     * Whether a dropped wildcard the generated code had may be what a line the
     * developer wrote relies on: a class their lines name that no import the
     * merge keeps names, and that is not java.lang's.
     */
    static boolean wildcardNeeded(String imp, String added, Set<String> kept) {
        if (!imp.endsWith(".*")) return false;
        Set<String> named = new java.util.HashSet<String>();
        for (String k : kept) named.add(k.substring(k.lastIndexOf('.') + 1));
        Matcher m = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\b").matcher(added);
        while (m.find()) {
            String c = m.group(1);
            if (!named.contains(c) && !JAVA_LANG.contains(c) && !c.equals(c.toUpperCase(java.util.Locale.ROOT)))
                return true;
        }
        return false;
    }

    /** The code lines of yours the base does not have: imports and comments left out. */
    static String addedLines(String base, String yours) {
        Set<String> had = new java.util.HashSet<String>();
        for (String l : base.replace("\r\n", "\n").split("\n", -1)) had.add(l.trim());
        StringBuilder b = new StringBuilder();
        for (String l : yours.replace("\r\n", "\n").split("\n", -1)) {
            String t = l.trim();
            if (had.contains(t)
                    || t.startsWith("import ")
                    || t.startsWith("//")
                    || t.startsWith("/*")
                    || t.startsWith("*")) continue;
            b.append(t).append('\n');
        }
        return b.toString();
    }

    /** Whether the class a single-type import names appears in the text; never for a wildcard. */
    static boolean namedIn(String imp, String text) {
        String name = imp.substring(imp.lastIndexOf('.') + 1);
        if (name.equals("*")) return false;
        return Pattern.compile("\\b" + Pattern.quote(name) + "\\b")
                .matcher(text)
                .find();
    }

    /** A generated file the run would delete: the DSL no longer produces it. */
    void removed(File file, String label) {
        removed.add(label);
        removedFiles.add(file);
    }

    List<File> removedFiles() {
        return removedFiles;
    }

    /** The developer's sources that use a file the run would delete. */
    void removedUsers(String label, List<String> users) {
        if (!users.isEmpty()) removedUsers.put(label, users);
    }

    Map<String, List<String>> removedUsers() {
        return removedUsers;
    }

    /**
     * A file 2.x generated that the 3.0 code does not write: it stays, and
     * names a class nothing generates any more - with the developer's
     * sources that use it.
     */
    void leftBehind(String path, List<String> users) {
        leftBehind.put(path, users);
    }

    void note(String text) {
        notes.add(text);
    }

    private static Set<String> imports(String source) {
        Set<String> out = new LinkedHashSet<String>();
        for (String l : source.replace("\r\n", "\n").split("\n", -1)) {
            String t = l.trim();
            if (t.startsWith("import "))
                out.add(t.endsWith(";") ? t.substring(0, t.length() - 1).trim() : t);
        }
        return out;
    }

    /** Status counts, by the word that starts each status. */
    Map<String, Integer> counts() {
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        for (String s : statuses.values()) {
            String word = s.replaceAll("[^A-Z].*$", "");
            Integer n = out.get(word);
            out.put(word, n == null ? 1 : n + 1);
        }
        return out;
    }

    List<String> withStatus(String word) {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, String> en : statuses.entrySet()) {
            if (en.getValue().startsWith(word)) out.add(en.getKey());
        }
        return out;
    }

    Map<String, List<String>> droppedImports() {
        return droppedImports;
    }

    Map<String, List<String>> leftBehind() {
        return leftBehind;
    }

    List<String> removed() {
        return removed;
    }

    /** The report, as markdown: what to look at first, then the lists. */
    String report(String basePath) {
        StringBuilder b = new StringBuilder();
        b.append("# ElastiCORE migration check: the first 3.0 build of this project\n\n");
        b.append("Project: `")
                .append(basePath)
                .append("`. Nothing was written; this is what `elcore3` with\n")
                .append("`elasticore { output = 'v3' }` would do now (elasticore-migration-guide.md).\n\n");
        b.append("## Summary\n\n");
        for (Map.Entry<String, Integer> en : counts().entrySet()) {
            b.append("- ")
                    .append(en.getKey())
                    .append(": ")
                    .append(en.getValue())
                    .append('\n');
        }
        b.append("- removed (the 3.0 code does not produce it): ")
                .append(removed.size())
                .append('\n');
        b.append("- removed or no longer written, and used by your code: ")
                .append(usedLeftBehind())
                .append('\n');
        b.append("- files that would lose an import your code may need: ")
                .append(droppedImports.size())
                .append('\n');

        b.append("\n## Generated files the 3.0 build removes or no longer writes, that your code uses\n\n");
        if (usedLeftBehind() == 0) {
            b.append("None.\n");
        } else {
            b.append("Code of yours that names one of these - or calls the domain helper's getter of a repository\n")
                    .append(
                            "(`helper.getCode()`), which goes with it - does not compile after the build. A repository the\n")
                    .append(
                            "3.0 code writes only for `@expose` / `@service`: for an entity your code reads and writes,\n")
                    .append(
                            "write `@repository` on its entity line (no service, no API), or `@service` / `@expose`.\n\n");
            for (Map.Entry<String, List<String>> en : removedUsers.entrySet()) {
                b.append("- `")
                        .append(en.getKey())
                        .append("` (removed) - used by ")
                        .append(String.join(", ", backquoted(en.getValue())))
                        .append('\n');
            }
            for (Map.Entry<String, List<String>> en : leftBehind.entrySet()) {
                if (en.getValue().isEmpty()) continue;
                b.append("- `")
                        .append(en.getKey())
                        .append("` (left as it is, no longer written) - used by ")
                        .append(String.join(", ", backquoted(en.getValue())))
                        .append('\n');
            }
        }

        section(
                b,
                "Conflicts - your edits and the 3.0 code change the same lines",
                "The build stops on these until the marks are resolved.",
                withStatus("CONFLICT"));
        section(
                b,
                "Kept as yours - the 3.0 code's changes to them do not merge",
                "Take the changes over by hand; the build's [elcore3] lines name them.",
                withStatus("KEPT"));
        section(
                b,
                "Left as the 2.x code - edited, and their base cannot be confirmed",
                "The build neither merges nor rewrites these: they keep the 2.x code, and what calls them may"
                        + " no longer fit the 3.0 code around them. Move what you wrote into a file of your own (or the"
                        + " DSL), delete the file, and build again to get the 3.0 one.",
                withStatus("SKIP(user-modified"));

        b.append("\n## Imports the build would take out of your files\n\n");
        if (droppedImports.isEmpty()) {
            b.append("None.\n");
        } else {
            b.append(
                            "An import the generated code no longer needs goes - from a merged file, and from one whose code\n")
                    .append(
                            "of yours is in preserved blocks, which keep their lines and not the imports above them. Code\n")
                    .append("of yours that used it does not compile: write the import in the preserved block under\n")
                    .append("`package` (elasticore-dev-guide.md, preserved blocks).\n\n");
            for (Map.Entry<String, List<String>> en : droppedImports.entrySet()) {
                b.append("- `").append(en.getKey()).append("`: ");
                b.append(String.join(", ", backquoted(en.getValue()))).append('\n');
            }
        }

        b.append("\n## Files an older generator stamped that the 3.0 code does not write\n\n");
        if (leftBehind.isEmpty()) {
            b.append("None.\n");
        } else {
            b.append("They stay on disk as they are; delete one nothing uses.\n\n");
            for (Map.Entry<String, List<String>> en : leftBehind.entrySet()) {
                b.append("- `")
                        .append(en.getKey())
                        .append('`')
                        .append(en.getValue().isEmpty() ? " - nothing of yours uses it" : " - used (above)")
                        .append('\n');
            }
        }

        section(b, "Removed - the 3.0 code does not produce them", "Unedited generated files.", removed);
        if (!notes.isEmpty()) {
            b.append("\n## Also\n\n");
            for (String n : notes) b.append("- ").append(n).append('\n');
        }
        return b.toString();
    }

    int usedLeftBehind() {
        int n = removedUsers.size();
        for (List<String> users : leftBehind.values()) if (!users.isEmpty()) n++;
        return n;
    }

    private static void section(StringBuilder b, String title, String what, List<String> files) {
        b.append("\n## ").append(title).append("\n\n");
        if (files.isEmpty()) {
            b.append("None.\n");
            return;
        }
        b.append(what).append("\n\n");
        for (String f : files) b.append("- `").append(f).append("`\n");
    }

    private static List<String> backquoted(List<String> items) {
        List<String> out = new ArrayList<String>();
        for (String i : items) out.add("`" + i + "`");
        return out;
    }

    /** Writes the report under build/elasticore; false when it cannot. */
    static boolean write(File file, String text) {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) return false;
            java.nio.file.Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }
}
