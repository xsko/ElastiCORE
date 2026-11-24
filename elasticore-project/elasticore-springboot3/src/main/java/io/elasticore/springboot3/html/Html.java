package io.elasticore.springboot3.html;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * What an {@code html} field holds: markup from a rich-text editor, kept to an
 * allow-list before it is saved, and its text without tags for {@code @plain}.
 *
 * <pre>
 * entity.setContent(Html.clean(entity.getContent(), Safelist.basic()));
 * entity.setContentText(Html.plain(entity.getContent()));
 * </pre>
 *
 * This library is only compiled against jsoup. The generated code names the
 * {@link Safelist}, so a project with an html field and without jsoup stops
 * where that line is compiled.
 */
public final class Html {

    /**
     * The base a relative link is read against, so the safelist's protocol
     * check can run on it. It never reaches the output: a relative link is
     * kept as it was written.
     */
    private static final String PLACEHOLDER_BASE = "https://site.invalid/";

    /** The attributes the basic and relaxed safelists check the protocol of. */
    private static final String[] LINK_ATTRIBUTES = {"href", "src", "cite"};

    private Html() {}

    /**
     * The markup with only what the safelist allows: every other tag,
     * attribute and link protocol is removed, and the text of a removed
     * script or style with it. What is kept is not reformatted.
     *
     * A relative link ({@code /api/files/3}, {@code #top}) is kept as written,
     * and so is a protocol-relative one ({@code //host/x}), whose protocol is
     * the page's. One a browser would read as another host's while it reads as
     * a path of this site - with a backslash, or {@code https:host} without the
     * slashes - is removed. The safelist given is not changed.
     */
    public static String clean(String html, Safelist safelist) {
        if (html == null) return null;
        Document dirty = Jsoup.parseBodyFragment(html, PLACEHOLDER_BASE);
        for (Element el : dirty.body().getAllElements()) {
            for (String key : LINK_ATTRIBUTES) {
                if (el.hasAttr(key) && readAsAnotherHost(el.attr(key))) el.removeAttr(key);
            }
        }
        Document clean = new Cleaner(new Safelist(safelist).preserveRelativeLinks(true)).clean(dirty);
        clean.outputSettings(new Document.OutputSettings().prettyPrint(false));
        return clean.body().html();
    }

    /**
     * Whether a browser could read the link as another host's where jsoup reads
     * it as a path of the base: a link without a scheme that holds a backslash
     * or a control character (a browser reads a backslash as a slash and drops
     * a tab or a line break), or an http(s) link without the two slashes.
     */
    static boolean readAsAnotherHost(String value) {
        String v = value;
        int start = 0;
        while (start < v.length() && v.charAt(start) <= ' ') start++;
        v = v.substring(start);
        int colon = v.indexOf(':');
        String scheme = colon > 0 ? v.substring(0, colon) : null;
        if (scheme != null && scheme.matches("[A-Za-z][A-Za-z0-9+.\\-]*")) {
            String lower = scheme.toLowerCase(java.util.Locale.ROOT);
            return ("http".equals(lower) || "https".equals(lower)) && !v.startsWith("//", colon + 1);
        }
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\\' || c < ' ' || c == 0x7f) return true;
        }
        return false;
    }

    /** The text without tags, entities read and white space collapsed - for search, previews and summaries. */
    public static String plain(String html) {
        if (html == null) return null;
        return Jsoup.parse(html).text();
    }

    /** The elements a line ends after in {@link #plainLines}. */
    private static final Set<String> LINE_ENDS = new HashSet<String>(
            Arrays.asList("p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "pre", "div", "tr", "br", "hr"));

    /**
     * The text without tags, one line per paragraph - for a preview or a
     * summary that shows the first lines. A line ends after a {@code p},
     * {@code h1}-{@code h6}, {@code li}, {@code blockquote}, {@code pre},
     * {@code div}, {@code tr}, {@code br} and {@code hr}; a list item gets no
     * marker. Blocks inside each other end one line, not several:
     * {@code <blockquote><p>a</p></blockquote><ul><li><p>b</p></li></ul>} is
     * {@code a\nb}. Two {@code br} in a row leave one blank line, and never
     * more than one. Within a line white space is collapsed as in
     * {@link #plain}, spaces before a line's end are dropped, and so is white
     * space at both ends. The lines of a {@code pre} are kept.
     */
    public static String plainLines(String html) {
        if (html == null) return null;
        Lines lines = new Lines();
        NodeTraversor.traverse(lines, Jsoup.parseBodyFragment(html).body());
        return lines.text();
    }

    /** Writes the text of the nodes it visits, a line at a time. */
    private static final class Lines implements NodeVisitor {
        private final StringBuilder out = new StringBuilder();
        private boolean space;

        @Override
        public void head(Node node, int depth) {
            if (node instanceof TextNode text) {
                if (insidePre(text)) verbatim(text.getWholeText());
                else words(text.getWholeText());
            } else if (node instanceof Element el) {
                if ("br".equals(el.normalName())) {
                    out.append('\n');
                    space = false;
                } else if (LINE_ENDS.contains(el.normalName())) {
                    endLine();
                } else if (el.isBlock()) {
                    space = true;
                }
            }
        }

        @Override
        public void tail(Node node, int depth) {
            if (!(node instanceof Element el)) return;
            if ("br".equals(el.normalName())) return;
            if (LINE_ENDS.contains(el.normalName())) endLine();
            else if (el.isBlock()) space = true;
        }

        /** Ends the line written so far, unless nothing is written on it. */
        private void endLine() {
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
            space = false;
        }

        /** Text with its white space collapsed to one space, none at the start of a line. */
        private void words(String text) {
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (isSpace(ch)) {
                    space = true;
                    continue;
                }
                if (space && out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append(' ');
                space = false;
                out.append(ch);
            }
        }

        /** A pre's text: its line breaks kept, a carriage return dropped. */
        private void verbatim(String text) {
            if (space && out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append(' ');
            space = false;
            out.append(text.replace("\r", ""));
        }

        private static boolean insidePre(TextNode text) {
            for (Node p = text.parentNode(); p instanceof Element el; p = p.parentNode()) {
                if ("pre".equals(el.normalName())) return true;
            }
            return false;
        }

        /** Spaces before a line's end dropped, at most one blank line, both ends stripped. */
        String text() {
            String[] rows = out.toString().split("\n", -1);
            StringBuilder sb = new StringBuilder();
            int blank = 0;
            for (String row : rows) {
                String line = stripTrailing(row);
                if (line.isEmpty()) {
                    blank++;
                    continue;
                }
                if (sb.length() > 0) sb.append(blank > 0 ? "\n\n" : "\n");
                blank = 0;
                sb.append(line);
            }
            return stripLeading(sb.toString());
        }

        private static String stripTrailing(String s) {
            int end = s.length();
            while (end > 0 && isSpace(s.charAt(end - 1))) end--;
            return s.substring(0, end);
        }

        private static String stripLeading(String s) {
            int start = 0;
            while (start < s.length() && isSpace(s.charAt(start))) start++;
            return s.substring(start);
        }

        private static boolean isSpace(char ch) {
            return Character.isWhitespace(ch) || Character.isSpaceChar(ch);
        }
    }
}
