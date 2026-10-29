package io.elasticore.base.model.pub.rules.verify;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
 * One file, .elasticore/cache, committed with the project - whoever clones it
 * has the base of every generated file, edited or not. A zip so that it is
 * one file; stored, not compressed, and the same bytes for the same content
 * (sorted entries, no times), so that git keeps only what changed between
 * two versions of it. Paths are relative to the project, with '/'.
 */
final class GeneratedCache {

    static final String DIR = ".elasticore";
    static final String NAME = "cache";

    private final File file;
    private final TreeMap<String, String> entries;
    private boolean changed;

    private GeneratedCache(File file, TreeMap<String, String> entries) {
        this.file = file;
        this.entries = entries;
    }

    /** The project's cache, empty when it has none yet; an unreadable one throws. */
    static GeneratedCache load(File projectRoot) throws IOException {
        File file = new File(new File(projectRoot, DIR), NAME);
        TreeMap<String, String> entries = new TreeMap<String, String>();
        if (file.isFile()) entries.putAll(read(Files.readAllBytes(file.toPath())));
        return new GeneratedCache(file, entries);
    }

    File file() {
        return file;
    }

    String get(String path) {
        return entries.get(path);
    }

    void put(String path, String content) {
        String old = entries.put(path, content);
        if (!content.equals(old)) changed = true;
    }

    void remove(String path) {
        if (entries.remove(path) != null) changed = true;
    }

    Set<String> paths() {
        return Collections.unmodifiableSet(new java.util.TreeSet<String>(entries.keySet()));
    }

    /**
     * Written when something changed: to a file beside it, then moved over it,
     * so that a run that stops half way leaves the last whole one. The
     * attributes beside it tell git it is binary - two branches that both
     * changed it conflict as a whole, and either side may be taken.
     */
    void save() throws IOException {
        File dir = file.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File attributes = new File(dir, ".gitattributes");
        if (!attributes.exists()) {
            Files.write(attributes.toPath(), (NAME + " binary\n").getBytes(StandardCharsets.UTF_8));
        }
        if (!changed && file.isFile()) return;
        File tmp = new File(dir, NAME + ".tmp");
        Files.write(tmp.toPath(), zip(entries));
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        changed = false;
    }

    static Map<String, String> read(byte[] bytes) throws IOException {
        Map<String, String> found = new TreeMap<String, String>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = in.getNextEntry()) != null) {
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                int n;
                while ((n = in.read(buf)) > 0) content.write(buf, 0, n);
                found.put(e.getName(), new String(content.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        if (bytes.length > 0 && found.isEmpty() && !isEmptyZip(bytes)) {
            throw new IOException("not a zip");
        }
        return found;
    }

    private static boolean isEmptyZip(byte[] bytes) {
        return bytes.length == 22 && bytes[0] == 0x50 && bytes[1] == 0x4b && bytes[2] == 5 && bytes[3] == 6;
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
