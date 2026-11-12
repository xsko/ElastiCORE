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
                    line(new String(current.toByteArray(), StandardCharsets.UTF_8).replace("\r", ""));
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
            return new java.io.PrintStream(lines, true, "UTF-8");
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

    /** A source the run would merge: the imports of the developer's file the merge does not keep. */
    void merged(String key, String yours, String merged) {
        Set<String> kept = imports(merged);
        List<String> dropped = new ArrayList<String>();
        for (String imp : imports(yours)) {
            if (!kept.contains(imp)) dropped.add(imp);
        }
        if (!dropped.isEmpty()) droppedImports.put(key, dropped);
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
        b.append("- merged files that would lose an import: ")
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

        b.append("\n## Imports a merge would take out of your files\n\n");
        if (droppedImports.isEmpty()) {
            b.append("None.\n");
        } else {
            b.append(
                            "An import the generated code no longer needs goes; code of yours that used it does not compile.\n")
                    .append("Add it back in the file after the build, or keep it in a preserved block.\n\n");
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
