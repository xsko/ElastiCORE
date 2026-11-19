package io.elasticore.springboot3.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

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
}
