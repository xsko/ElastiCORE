package io.elasticore.base.model.pub.rules.verify;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * What the generator last wrote, file by file, as it wrote it: the base a
 * developer's edits are merged against (elasticore.merge).
 *
 * One file, .elasticore/generated-base.zip, committed with the project -
 * whoever clones it has the base of every generated file, edited or not. A
 * zip so that it is one file; stored, not compressed, and the same bytes for
 * the same content (sorted entries, no times), so that git keeps only what
 * changed between two versions of it. Paths are relative to the project,
 * with '/'.
 *
 * It used to be named .elasticore/cache, a name that read as disposable:
 * deleted or ignored, the edits could no longer be merged. A
 * project that has only the old file is read from it, and the first save
 * writes the new one - the same bytes - and only then removes the old one
 * (renaming()). A project that has both reads the new one and leaves the
 * old one where it is (oldLeftBeside()): a branch may have merged it back,
 * and what is committed is the developer's to delete.
 */
final class GeneratedCache {

    static final String DIR = ".elasticore";
    static final String NAME = "generated-base.zip";

    /** The name the base had before (see above): read when it is the only one. */
    static final String OLD_NAME = "cache";

    /** The base as the project names it, for messages: .elasticore/generated-base.zip. */
    static final String PATH = DIR + "/" + NAME;

    /** The old base as the project names it: .elasticore/cache. */
    static final String OLD_PATH = DIR + "/" + OLD_NAME;

    static final String ATTRIBUTES = ".gitattributes";

    /**
     * What the attributes beside the base say of it: binary - two branches
     * that both changed it conflict as a whole - and no filter, so that a
     * project's "*.zip filter=lfs" does not turn it into a pointer file.
     */
    static final String ATTRIBUTES_LINE = NAME + " binary -filter\n";

    /**
     * A .gitignore beside the base that takes it back in: many projects ignore
     * *.zip (the common Java .gitignore does), and an ignored base is not
     * committed - the edits of whoever clones can no longer be merged.
     */
    static final String IGNORE = ".gitignore";

    static final String IGNORE_LINE = "!" + NAME + "\n";

    /**
     * The entry that says, for each file, the runtimePackages mapping its text
     * was renamed with: one line "path TAB from=to,..." per file that had one.
     * Written in the same save as the texts, so a base is never paired with
     * another mapping than its own - whatever part of a project a run wrote,
     * or wherever it stopped. Absent when no file had a mapping: the cache of
     * a project without the setting is as it always was. No generated file
     * is named so: they are all under src/.
     */
    static final String MAPPINGS = ".runtime-packages";

    private final File file;
    private final TreeMap<String, String> entries;
    private final TreeMap<String, String> mappings;
    private final boolean existed;
    private boolean changed;

    /** The old base the entries were read from, until a save has written the new one and removed it. */
    private File renameFrom;

    /** The old base, still there beside the new one - read by nothing. */
    private File oldLeft;

    /** Why the old base was not removed after the new one was written; null: it was, or nothing was tried. */
    private String oldKeptBecause;

    /** For a test: what happens between the new base being written and read back, the old one still there. */
    private Runnable afterNewWritten;

    /** The mapping of this run, recorded with every text put. Empty: none. */
    private String putMapping = "";

    private GeneratedCache(File file, TreeMap<String, String> entries, boolean existed) {
        this.file = file;
        this.existed = existed;
        this.mappings = new TreeMap<String, String>();
        String said = entries.remove(MAPPINGS);
        if (said != null) {
            for (String line : said.split("\n", -1)) {
                int tab = line.indexOf('\t');
                if (tab > 0)
                    mappings.put(line.substring(0, tab), line.substring(tab + 1).trim());
            }
        }
        this.entries = entries;
    }

    /**
     * The project's base, empty when it has none yet; an unreadable one throws.
     * Read from the old name when only that one is there - nothing is moved
     * until a save.
     */
    static GeneratedCache load(File projectRoot) throws IOException {
        File dir = new File(projectRoot.getAbsoluteFile(), DIR);
        File file = new File(dir, NAME);
        File old = new File(dir, OLD_NAME);
        boolean hasNew = file.isFile();
        boolean hasOld = old.isFile();
        TreeMap<String, String> entries = new TreeMap<String, String>();
        if (hasNew || hasOld) entries.putAll(read(Files.readAllBytes((hasNew ? file : old).toPath())));
        // The attributes are written with the first base and left alone: a
        // base deleted from beside them was there before.
        boolean existed = hasNew || hasOld || new File(dir, ATTRIBUTES).isFile();
        GeneratedCache cache = new GeneratedCache(file, entries, existed);
        if (hasNew && hasOld) cache.oldLeft = old;
        else if (hasOld) cache.renameFrom = old;
        return cache;
    }

    /** The base a project has, as it names it - the old name when that is the only one: for a message. */
    static String pathIn(File projectRoot) {
        File dir = new File(projectRoot.getAbsoluteFile(), DIR);
        return !new File(dir, NAME).isFile() && new File(dir, OLD_NAME).isFile() ? OLD_PATH : PATH;
    }

    /** The base as the project names it now, for a message: the old name until a save has moved it. */
    String path() {
        return renameFrom != null ? OLD_PATH : PATH;
    }

    /** Whether the entries were read from the old name, and no save has written the new one yet. */
    boolean renaming() {
        return renameFrom != null;
    }

    /** The old base beside the new one, read by nothing; null when there is none. */
    File oldLeftBeside() {
        return oldLeft;
    }

    /** Why the save that wrote the new base did not remove the old one; null when it did. */
    String oldKeptBecause() {
        return oldKeptBecause;
    }

    /** For a test: run between writing the new base and reading it back, before the old one is removed. */
    void afterNewWritten(Runnable step) {
        this.afterNewWritten = step;
    }

    /** The mapping this run's texts are renamed with (RuntimePackages.spec()), recorded with each put. */
    void putMapping(String spec) {
        this.putMapping = spec == null ? "" : spec.trim();
    }

    /** The mapping the cached text of a file was renamed with, as recorded; empty: none, or no such file. */
    String mappingOf(String path) {
        String m = entries.containsKey(path) ? mappings.get(path) : null;
        return m == null ? "" : m;
    }

    /** Every mapping a cached text was renamed with, as recorded - not the empty one. */
    Set<String> mappingsHeld() {
        Set<String> held = new java.util.TreeSet<String>();
        for (Map.Entry<String, String> en : mappings.entrySet()) {
            if (entries.containsKey(en.getKey()) && !en.getValue().isEmpty()) held.add(en.getValue());
        }
        return held;
    }

    /** For a test: the recorded mapping of a file, as a hand-edited cache would say it. */
    void recordMapping(String path, String spec) {
        mappings.put(path, spec);
        changed = true;
    }

    File file() {
        return file;
    }

    /**
     * Whether the project had a cache when the run began. Without one, this
     * is its first merging run; with one, a file it does not hold was not
     * written by a merging run - or the cache was replaced or deleted - and
     * what is in that file is not known to be the generator's.
     */
    boolean existed() {
        return existed;
    }

    String get(String path) {
        return entries.get(path);
    }

    void put(String path, String content) {
        String old = entries.put(path, content);
        if (!content.equals(old)) changed = true;
        String was = putMapping.isEmpty() ? mappings.remove(path) : mappings.put(path, putMapping);
        if (!putMapping.equals(was == null ? "" : was)) changed = true;
    }

    void remove(String path) {
        if (entries.remove(path) != null) changed = true;
        mappings.remove(path);
    }

    Set<String> paths() {
        return Collections.unmodifiableSet(new java.util.TreeSet<String>(entries.keySet()));
    }

    /**
     * Written when something changed, or when it was read from the old name:
     * to a file beside it, then moved over it, so that a run that stops half
     * way leaves the last whole one. The attributes beside it tell git it is
     * binary - two branches that both changed it conflict as a whole, and
     * either side may be taken.
     *
     * Read from the old name, the new file is written first, read back and
     * compared, the attributes are made to name it, and only then is the old
     * file removed: a run that stops anywhere before leaves the old one, and
     * the next run reads whichever is whole.
     */
    void save() throws IOException {
        if (!changed && renameFrom == null) return;
        File dir = file.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File attributes = new File(dir, ATTRIBUTES);
        if (!attributes.exists()) Files.write(attributes.toPath(), ATTRIBUTES_LINE.getBytes(StandardCharsets.UTF_8));
        File ignore = new File(dir, IGNORE);
        if (!ignore.exists()) Files.write(ignore.toPath(), IGNORE_LINE.getBytes(StandardCharsets.UTF_8));
        byte[] bytes = write(dir);
        if (renameFrom != null) {
            if (afterNewWritten != null) afterNewWritten.run();
            if (!Arrays.equals(bytes, Files.readAllBytes(file.toPath()))) {
                // Taken back, so that the next run reads the old one again rather than this.
                removeExactly(file);
                throw new IOException(PATH + " does not read back as it was written - " + OLD_PATH + " is kept");
            }
            renameAttributes(attributes);
            oldKeptBecause = removeExactly(renameFrom);
            if (oldKeptBecause != null) oldLeft = renameFrom;
            renameFrom = null;
        }
        changed = false;
    }

    /**
     * Removes one file the base is kept in: exactly that file, and only when
     * it is a regular file. Null when it is gone; otherwise why it is still there.
     */
    private static String removeExactly(File one) {
        java.nio.file.Path path = one.toPath();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return "it is not a regular file";
        try {
            Files.delete(path);
            return null;
        } catch (IOException e) {
            return String.valueOf(e.getMessage());
        }
    }

    /** The attributes beside the base, the old name's line made to name the new one; the rest as they were. */
    private static void renameAttributes(File attributes) throws IOException {
        if (!attributes.isFile()) return;
        String text = new String(Files.readAllBytes(attributes.toPath()), StandardCharsets.UTF_8);
        String renamed = renamedAttributes(text);
        if (!renamed.equals(text)) Files.write(attributes.toPath(), renamed.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A .gitattributes with each line whose pattern is the old name
     * ("cache" or "/cache") naming the new one, its attributes and every other
     * line - the developer's own, comments, line ends - as they were. As it
     * was when a line names the new name already, or none names the old one.
     */
    static String renamedAttributes(String text) {
        String[] lines = text.split("\n", -1);
        boolean named = false;
        for (String line : lines) {
            String pattern = patternOf(line);
            if (pattern.equals(NAME) || pattern.equals("/" + NAME)) named = true;
        }
        if (named) return text;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String pattern = patternOf(line);
            if (line.trim().equals(OLD_NAME + " binary")) {
                // The line the old name was written with becomes the one the new name is written with.
                line = ATTRIBUTES_LINE.trim() + (line.endsWith("\r") ? "\r" : "");
            } else if (pattern.equals(OLD_NAME) || pattern.equals("/" + OLD_NAME)) {
                int at = line.indexOf(pattern);
                line = line.substring(0, at) + pattern.replace(OLD_NAME, NAME) + line.substring(at + pattern.length());
            }
            out.append(line);
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    /** The pattern of a .gitattributes line: its first word; empty for a blank line or a comment. */
    private static String patternOf(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return "";
        int end = 0;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) end++;
        return trimmed.substring(0, end);
    }

    /** The zip of the entries and their mappings, written beside the base and moved over it; its bytes. */
    private byte[] write(File dir) throws IOException {
        File tmp = new File(dir, NAME + ".tmp");
        TreeMap<String, String> all = new TreeMap<String, String>(entries);
        StringBuilder said = new StringBuilder();
        for (Map.Entry<String, String> en : mappings.entrySet()) {
            if (entries.containsKey(en.getKey()) && !en.getValue().isEmpty()) {
                said.append(en.getKey()).append('\t').append(en.getValue()).append('\n');
            }
        }
        if (said.length() > 0) all.put(MAPPINGS, said.toString());
        byte[] bytes = zip(all);
        Files.write(tmp.toPath(), bytes);
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return bytes;
    }

    /**
     * The entries of a cache. One that is cut short - its end record missing,
     * or fewer entries than the record counts - or that names a path outside
     * the project is refused: a run would merge against part of it, and its
     * stale files are deleted by those paths.
     */
    static Map<String, String> read(byte[] bytes) throws IOException {
        int end = bytes.length - 22;
        if (end < 0 || int4(bytes, end) != 0x06054b50L) throw new IOException("not a whole zip");
        int count = (bytes[end + 10] & 0xff) | (bytes[end + 11] & 0xff) << 8;
        Map<String, String> found = new TreeMap<String, String>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = in.getNextEntry()) != null) {
                if (!inside(e.getName())) throw new IOException("an entry outside the project: " + e.getName());
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                int n;
                while ((n = in.read(buf)) > 0) content.write(buf, 0, n);
                found.put(e.getName(), new String(content.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        if (found.size() != count) throw new IOException(found.size() + " entries of " + count);
        return found;
    }

    /** A relative path with '/', none of its parts "..". */
    private static boolean inside(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.indexOf('\\') >= 0 || path.indexOf(':') >= 0) return false;
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals("..") || part.equals(".")) return false;
        }
        return true;
    }

    private static long int4(byte[] b, int at) {
        return (b[at] & 0xffL) | (b[at + 1] & 0xffL) << 8 | (b[at + 2] & 0xffL) << 16 | (b[at + 3] & 0xffL) << 24;
    }

    /**
     * A stored zip, written by hand: java.util.zip puts the local time in each
     * entry, which differs from machine to machine and run to run. Here every
     * entry carries 1980-01-01 00:00, the zip format's first day.
     */
    static byte[] zip(SortedMap<String, String> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream central = new ByteArrayOutputStream();
        int count = 0;
        for (Map.Entry<String, String> en : entries.entrySet()) {
            byte[] name = en.getKey().getBytes(StandardCharsets.UTF_8);
            byte[] data = en.getValue().getBytes(StandardCharsets.UTF_8);
            CRC32 crc = new CRC32();
            crc.update(data);
            long offset = out.size();

            int4(out, 0x04034b50);
            header(out, name.length, data.length, crc.getValue());
            out.write(name);
            out.write(data);

            int4(central, 0x02014b50);
            int2(central, 10);
            header(central, name.length, data.length, crc.getValue());
            int2(central, 0);              // comment
            int2(central, 0);              // disk
            int2(central, 0);              // internal attributes
            int4(central, 0);              // external attributes
            int4(central, offset);
            central.write(name);
            count++;
        }
        long centralAt = out.size();
        central.writeTo(out);
        int4(out, 0x06054b50);
        int2(out, 0);
        int2(out, 0);
        int2(out, count);
        int2(out, count);
        int4(out, central.size());
        int4(out, centralAt);
        int2(out, 0);
        return out.toByteArray();
    }

    /** From "version needed" to "extra length" - the same in both headers. */
    private static void header(ByteArrayOutputStream out, int nameLength, int size, long crc) {
        int2(out, 10);                     // version needed: 1.0, stored
        int2(out, 0x0800);                 // names in UTF-8
        int2(out, 0);                      // stored
        int2(out, 0);                      // 00:00
        int2(out, (0 << 9) | (1 << 5) | 1); // 1980-01-01
        int4(out, crc);
        int4(out, size);
        int4(out, size);
        int2(out, nameLength);
        int2(out, 0);
    }

    private static void int2(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >>> 8) & 0xff);
    }

    private static void int4(ByteArrayOutputStream out, long v) {
        out.write((int) (v & 0xff));
        out.write((int) ((v >>> 8) & 0xff));
        out.write((int) ((v >>> 16) & 0xff));
        out.write((int) ((v >>> 24) & 0xff));
    }
}
