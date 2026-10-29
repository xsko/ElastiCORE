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
        List<String> o = lines(base);
        List<String> a = lines(yours);
        List<String> b = lines(generated);
        int[] ma = matches(o, a);
        int[] mb = matches(o, b);

        List<String> out = new ArrayList<String>();
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
        return new Result(String.join("\n", out), conflicts);
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
