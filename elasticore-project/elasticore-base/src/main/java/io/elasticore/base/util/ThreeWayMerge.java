package io.elasticore.base.util;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A line merge of three texts, as diff3 and git do it: what the generator
 * wrote last time (base), the file as the developer left it (yours), and what
 * the generator writes now (generated).
 *
 * It does not ask what kind of change either side made - a comment, a new
 * statement, an edited line are all lines. A stretch only one side changed
 * takes that side; one both changed alike takes it once; one both changed
 * differently is a conflict, written between git's marks so the developer
 * resolves it where they would resolve any other.
 */
public final class ThreeWayMerge {

    public static final String YOURS = "<<<<<<< yours";
    public static final String BETWEEN = "=======";
    public static final String GENERATED = ">>>>>>> elasticore";

    private ThreeWayMerge() {
    }

    /** The merged text and how many of its stretches are conflicts. */
    public static final class Result {
        private final String text;
        private final int conflicts;

        Result(String text, int conflicts) {
            this.text = text;
            this.conflicts = conflicts;
        }

        public String text() {
            return text;
        }

        public int conflicts() {
            return conflicts;
        }
    }

    /** Texts with LF line ends; a CR before one is taken as part of the end. */
    public static Result merge(String base, String yours, String generated) {
        List<String> out = new ArrayList<String>();
        int conflicts = mergeLines(lines(base), lines(yours), lines(generated), out, false);
        return new Result(String.join("\n", out), conflicts);
    }

    /**
     * The merge of three lists of lines, added to out; the number of conflicts.
     * bothAdded (a class body): lines both sides only added at one place are
     * kept, the generated ones first - a field the DSL added and a method the
     * developer added before the same closing brace; and lines one side only
     * added next to lines the other changed are kept beside the change.
     */
    private static int mergeLines(List<String> o, List<String> a, List<String> b, List<String> out,
                                  boolean bothAdded) {
        int[] ma = matches(o, a);
        int[] mb = matches(o, b);
        int conflicts = 0;
        int io = 0;
        int ia = 0;
        int ib = 0;
        while (true) {
            // Lines all three still share, in the same place.
            while (io < o.size() && ma[io] == ia && mb[io] == ib) {
                out.add(o.get(io));
                io++;
                ia++;
                ib++;
            }
            if (io == o.size() && ia == a.size() && ib == b.size()) break;
            // The next base line both sides kept ends the stretch that changed.
            int next = io;
            while (next < o.size() && (ma[next] < 0 || mb[next] < 0)) next++;
            int ea = next < o.size() ? ma[next] : a.size();
            int eb = next < o.size() ? mb[next] : b.size();
            List<String> oc = o.subList(io, next);
            List<String> ac = a.subList(ia, ea);
            List<String> bc = b.subList(ib, eb);
            if (ac.equals(oc)) {
                out.addAll(bc);
            } else if (bc.equals(oc) || ac.equals(bc)) {
                out.addAll(ac);
            } else if (bothAdded && oc.isEmpty()) {
                out.addAll(bc);
                out.addAll(ac);
            } else if (bothAdded && startsWith(ac, oc)) {
                // The developer only added after lines the DSL changed: the
                // DSL's lines, then theirs - a method after the last field.
                out.addAll(bc);
                out.addAll(ac.subList(oc.size(), ac.size()));
            } else if (bothAdded && endsWith(ac, oc)) {
                out.addAll(ac.subList(0, ac.size() - oc.size()));
                out.addAll(bc);
            } else if (bothAdded && startsWith(bc, oc)) {
                // And the other way round: the DSL only added after lines the developer changed.
                out.addAll(ac);
                out.addAll(bc.subList(oc.size(), bc.size()));
            } else if (bothAdded && endsWith(bc, oc)) {
                out.addAll(bc.subList(0, bc.size() - oc.size()));
                out.addAll(ac);
            } else {
                conflicts++;
                out.add(YOURS);
                out.addAll(ac);
                out.add(BETWEEN);
                out.addAll(bc);
                out.add(GENERATED);
            }
            io = next;
            ia = ea;
            ib = eb;
        }
        return conflicts;
    }

    /**
     * A Java or Kotlin source: the import block merged as a set, the rest by
     * lines. Line by line, an import the DSL adds next to one the developer
     * added is a conflict over nothing - imports have no order that matters.
     * The generated block is kept as it is laid out, without the imports the
     * developer deleted, and the developer's own follow it.
     */
    public static Result mergeSource(String base, String yours, String generated) {
        Parts o = Parts.of(base);
        Parts a = Parts.of(yours);
        Parts b = Parts.of(generated);
        java.util.Set<String> baseImports = imports(o.imports);
        java.util.Set<String> yourImports = imports(a.imports);
        java.util.Set<String> generatedImports = imports(b.imports);
        List<String> block = new ArrayList<String>();
        for (String line : b.imports) {
            String key = importKey(line);
            if (key != null && baseImports.contains(key) && !yourImports.contains(key)) continue;
            block.add(line);
        }
        java.util.Set<String> added = new java.util.LinkedHashSet<String>();
        for (String line : a.imports) {
            String key = importKey(line);
            if (key != null && !baseImports.contains(key) && !generatedImports.contains(key) && added.add(key)) {
                block.add(line);
            }
        }
        List<String> out = new ArrayList<String>();
        int conflicts = mergeLines(o.head, a.head, b.head, out, false);
        out.addAll(block);
        conflicts += mergeLines(o.body, a.body, b.body, out, true);
        return new Result(String.join("\n", out), conflicts);
    }

    /** A source cut where its imports are: before them, the block itself, after it. */
    private static final class Parts {
        final List<String> head;
        final List<String> imports;
        final List<String> body;

        private Parts(List<String> head, List<String> imports, List<String> body) {
            this.head = head;
            this.imports = imports;
            this.body = body;
        }

        /**
         * The block is the imports at the top and the blank lines between
         * them, nothing else: it ends at the first other line, and the lines
         * after it - a comment among imports, the imports below it - are
         * merged as lines, so nothing of the developer's is dropped. An
         * "import" past the start of the code (a text block) or inside a
         * comment is not one.
         */
        static Parts of(String text) {
            List<String> l = lines(text);
            int first = -1;
            int last = -1;
            int pkg = -1;
            boolean inComment = false;
            for (int i = 0; i < l.size(); i++) {
                String t = l.get(i).trim();
                if (inComment) {
                    if (t.contains("*/")) inComment = false;
                    if (first >= 0) break;
                    continue;
                }
                if (pkg < 0 && first < 0 && t.startsWith("package ")) {
                    pkg = i;
                    continue;
                }
                if (importKey(l.get(i)) != null) {
                    if (first < 0) first = i;
                    last = i;
                    continue;
                }
                if (t.isEmpty()) continue;
                if (first >= 0) break;
                if (t.startsWith("/*")) {
                    inComment = !t.contains("*/");
                    continue;
                }
                if (t.startsWith("//") || t.startsWith("@file:")) continue;
                break;
            }
            // No imports: the block is empty, right after the package line.
            if (first < 0) {
                first = pkg + 1;
                last = pkg;
            }
            return new Parts(l.subList(0, first), l.subList(first, last + 1), l.subList(last + 1, l.size()));
        }
    }

    /** "import a.b.C;" / "import a.b.C" / "import static ..." as written, without the ';'; null for any other line. */
    private static String importKey(String line) {
        String t = line.trim();
        if (!t.startsWith("import ")) return null;
        if (t.endsWith(";")) t = t.substring(0, t.length() - 1).trim();
        return t.replaceAll("\\s+", " ");
    }

    private static java.util.Set<String> imports(List<String> block) {
        java.util.Set<String> keys = new java.util.HashSet<String>();
        for (String line : block) {
            String key = importKey(line);
            if (key != null) keys.add(key);
        }
        return keys;
    }

    private static boolean startsWith(List<String> whole, List<String> part) {
        return whole.size() > part.size() && whole.subList(0, part.size()).equals(part);
    }

    private static boolean endsWith(List<String> whole, List<String> part) {
        return whole.size() > part.size() && whole.subList(whole.size() - part.size(), whole.size()).equals(part);
    }

    /** Whether a text still holds the marks of a conflict nobody resolved. */
    public static boolean hasConflictMarks(String text) {
        for (String line : lines(text)) {
            if (line.startsWith(YOURS) || line.startsWith(GENERATED)) return true;
        }
        return false;
    }

    private static List<String> lines(String text) {
        return Arrays.asList(text.replace("\r\n", "\n").split("\n", -1));
    }

    /** For each base line, the line of other it stayed as, or -1 where it changed. */
    private static int[] matches(List<String> base, List<String> other) {
        int[] m = new int[base.size()];
        Arrays.fill(m, -1);
        Patch<String> patch = DiffUtils.diff(base, other);
        int io = 0;
        int ix = 0;
        for (AbstractDelta<String> d : patch.getDeltas()) {
            int so = d.getSource().getPosition();
            while (io < so) m[io++] = ix++;
            io = so + d.getSource().size();
            ix = d.getTarget().getPosition() + d.getTarget().size();
        }
        while (io < base.size()) m[io++] = ix++;
        return m;
    }
}
