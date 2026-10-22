package io.elasticore.base.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes one line per section of the guides: where it starts, where it ends,
 * and the words it is about.
 *
 * The guides are long - the rule guide alone is nearly two thousand lines -
 * and the reader that opens them most is a coding agent with a context window
 * to spend. Searching a guide for a word gives a line number and no idea of
 * where the section around it ends, so the agent reads too much or reads again.
 * A line of this index answers both: search it for the word, then read exactly
 * {@code first-last} of the file it names.
 *
 * The index is written from the guides as they were copied, on every
 * generation, so its line numbers are the numbers of the files beside it and
 * cannot go stale the way a hand-kept table of contents would.
 */
public final class GuideIndex {

    /** The file name in the blueprint root, beside the guides. */
    public static final String FILE_NAME = "elasticore-guide-index.md";

    /**
     * Characters of words per section; past this a line stops being a line.
     * A budget rather than a count, because an API table names a dozen short
     * methods and a tutorial step names three long annotations.
     */
    static final int TERM_BUDGET = 160;

    /**
     * The deepest heading indexed. The rule and annotation guides keep whole
     * topics under level-four headings; stopping at three sent a reader to a
     * two-hundred-line section for one of them.
     */
    private static final String HASHES = "####";

    private static final Set<String> NOT_WORDS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "if", "null", "void", "import", "true", "false", "return", "new"));

    private static final Pattern CODE_SPAN = Pattern.compile("`([^`]+)`");
    private static final Pattern DSL_ANNOTATION = Pattern.compile("(?<![\\w.@])@[A-Za-z][A-Za-z0-9_]*");

    /**
     * What a reader searches for: a name, an annotation or a key, with short
     * arguments at most - {@code @search(like)}, {@code publisher.rules},
     * {@code ctx(e).excludeMethod}. Commands, paths, JSON and whole lines of
     * code are examples; listing them made the first index an eighth of the
     * size of the guides it was meant to spare.
     */
    private static final Pattern TERM = Pattern.compile("@?[A-Za-z_][A-Za-z0-9_.]*(\\([^()]{0,24}\\))?(\\.[A-Za-z_][A-Za-z0-9_]*(\\([^()]{0,24}\\))?)*:?");

    /** File names name the example, not the topic. */
    private static final Pattern FILE_NAME_TERM = Pattern.compile(".*\\.(java|kt|ts|md|yml|yaml|gradle|properties|json|xml|tmpl)");

    private GuideIndex() {
    }

    /**
     * Writes the index of the guides found in {@code blueprint}, in the order
     * given. A guide that is missing is left out rather than failing the rest.
     */
    public static void writeTo(Path blueprint, String[] guides) throws IOException {
        List<String> out = new ArrayList<String>();
        out.add("# ElastiCORE guide index");
        out.add("");
        out.add("Written with the guides on every generation - do not edit. One line per section:");
        out.add("`<guide>:<first line>-<last line>  <heading>  [words it is about]`.");
        out.add("Search this file for a word - an annotation such as `@access`, a key such as");
        out.add("`publisher.rules`, a Korean term - then read only those lines of that guide,");
        out.add("not the whole guide.");
        for (String guide : guides) {
            Path file = blueprint.resolve(guide);
            if (!Files.isRegularFile(file)) continue;
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            out.add("");
            out.add("## " + guide);
            out.add("");
            out.add("```text");
            out.addAll(index(guide, lines));
            out.add("```");
        }
        // "\n" on every machine: the guides beside it are copied byte for byte,
        // and the same jar should write the same index on Windows and Linux.
        StringBuilder text = new StringBuilder();
        for (String line : out) text.append(line).append('\n');
        Files.write(blueprint.resolve(FILE_NAME), text.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** The index lines of one guide: the whole file first, then every section. */
    static List<String> index(String guide, List<String> lines) {
        List<Heading> headings = headings(lines);
        List<String> out = new ArrayList<String>();

        String title = guide;
        for (Heading h : headings) {
            if (h.level == 1) {
                title = h.title;
                break;
            }
        }
        out.add(guide + ":1-" + lines.size() + "  # " + title);

        for (int i = 0; i < headings.size(); i++) {
            Heading h = headings.get(i);
            if (h.level == 1) continue;

            int end = lines.size();
            int ownEnd = lines.size();
            for (int j = i + 1; j < headings.size(); j++) {
                Heading next = headings.get(j);
                if (ownEnd == lines.size()) ownEnd = next.line - 1;
                if (next.level <= h.level) {
                    end = next.line - 1;
                    break;
                }
            }
            end = trimBlank(lines, h.line, end);

            StringBuilder row = new StringBuilder();
            row.append(guide).append(':').append(h.line).append('-').append(end).append("  ")
                    .append(HASHES.substring(0, h.level)).append(' ').append(h.title);
            Set<String> terms = terms(lines, h.line, Math.min(ownEnd, end));
            if (!terms.isEmpty()) {
                row.append("  [").append(join(terms)).append(']');
            }
            out.add(row.toString());
        }
        return out;
    }

    /**
     * The headings outside code blocks, levels 1 to 4. A "## " inside a fence
     * is a line of an example - the guides show YAML and Markdown in fences -
     * and counting it would cut a section in half.
     */
    static List<Heading> headings(List<String> lines) {
        List<Heading> found = new ArrayList<Heading>();
        Fence fence = new Fence();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (fence.step(line) || fence.open()) continue;
            int level = 0;
            while (level < line.length() && line.charAt(level) == '#') level++;
            if (level < 1 || level > HASHES.length() || level >= line.length() || line.charAt(level) != ' ') continue;
            String title = line.substring(level + 1).trim();
            if (title.isEmpty()) continue;
            found.add(new Heading(i + 1, level, title));
        }
        return found;
    }

    /**
     * The words a section is about: its code spans, and the DSL annotations
     * its examples use. Taken from the section's own lines only - for a
     * section with subsections, the text before the first one - so a word
     * points at the section that explains it rather than at every parent.
     */
    static Set<String> terms(List<String> lines, int headingLine, int last) {
        Set<String> terms = new LinkedHashSet<String>();
        String heading = lines.get(headingLine - 1);
        int[] budget = {TERM_BUDGET};
        Fence fence = new Fence();
        for (int n = headingLine + 1; n <= last && budget[0] > 0; n++) {
            String line = lines.get(n - 1);
            if (fence.step(line)) continue;
            boolean inFence = fence.open();
            if (!inFence) {
                Matcher span = CODE_SPAN.matcher(line);
                while (span.find()) {
                    String term = span.group(1).trim();
                    if (isTerm(term)) add(terms, word(term), heading, budget);
                }
            }
            Matcher anno = DSL_ANNOTATION.matcher(line);
            while (anno.find()) {
                String term = anno.group();
                // In an example a capitalised annotation is Java (@Override, @Bean),
                // not a word of the DSL, whose annotations are lower case.
                if (inFence && Character.isUpperCase(term.charAt(1))) continue;
                if (!containsWord(terms, term)) add(terms, term, heading, budget);
            }
        }
        return terms;
    }

    private static void add(Set<String> terms, String term, String heading, int[] budget) {
        if (term.length() < 2 || NOT_WORDS.contains(term) || inHeading(heading, term) || terms.contains(term)) return;
        int cost = term.length() + 2;
        if (cost > budget[0]) {
            budget[0] = 0;
            return;
        }
        budget[0] -= cost;
        terms.add(term);
    }

    /**
     * The searchable part of a code span. A DSL annotation keeps its
     * arguments - {@code @search(like)} is a different thing from
     * {@code @search(eq)} - and a call loses them, since nobody searches for
     * {@code hasMeta("x")} but for {@code hasMeta}.
     */
    static String word(String term) {
        if (term.startsWith("@")) return term;
        return term.replaceAll("\\([^()]*\\)", "");
    }

    static boolean isTerm(String s) {
        return s.length() >= 2 && TERM.matcher(s).matches() && !FILE_NAME_TERM.matcher(s).matches();
    }

    /**
     * True when the heading already names the term as a word of its own - a
     * heading "@access / @owner" makes listing @access again pointless, while
     * "user" inside "username" is a different word.
     */
    static boolean inHeading(String heading, String term) {
        return Pattern.compile("(?<![\\w@.])" + Pattern.quote(term) + "(?![\\w(])").matcher(heading).find();
    }

    /** True when a code span already listed starts with this annotation, e.g. @access(read=public). */
    private static boolean containsWord(Set<String> terms, String annotation) {
        for (String t : terms) {
            if (t.equals(annotation) || t.startsWith(annotation + "(")) return true;
        }
        return false;
    }

    private static int trimBlank(List<String> lines, int first, int last) {
        while (last > first && lines.get(last - 1).trim().isEmpty()) last--;
        return last;
    }

    private static String join(Set<String> terms) {
        StringBuilder b = new StringBuilder();
        for (String t : terms) {
            if (b.length() > 0) b.append(", ");
            b.append(t);
        }
        return b.toString();
    }

    /**
     * Where the code blocks are. A block opened with three backticks closes
     * only at a line of at least three backticks and nothing else, one opened
     * with tildes only at tildes - so a guide that shows a Markdown example
     * containing a fence, inside a longer or other fence, keeps its sections.
     */
    static final class Fence {
        private char marker;
        private int length;

        /** Reads one line; true when it opens or closes a block. */
        boolean step(String line) {
            String t = line.trim();
            if (t.isEmpty() || (t.charAt(0) != '`' && t.charAt(0) != '~')) return false;
            char c = t.charAt(0);
            int run = 0;
            while (run < t.length() && t.charAt(run) == c) run++;
            if (run < 3) return false;
            if (length == 0) {
                marker = c;
                length = run;
                return true;
            }
            if (c == marker && run >= length && t.substring(run).trim().isEmpty()) {
                length = 0;
                return true;
            }
            return false;
        }

        boolean open() {
            return length > 0;
        }
    }

    static final class Heading {
        final int line;
        final int level;
        final String title;

        Heading(int line, int level, String title) {
            this.line = line;
            this.level = level;
            this.title = title;
        }
    }
}
