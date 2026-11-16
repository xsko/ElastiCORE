package io.elasticore.springboot3.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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

    private Html() {}

    /**
     * The markup with only what the safelist allows: every other tag,
     * attribute and link protocol is removed, and the text of a removed
     * script or style with it. What is kept is not reformatted.
     */
    public static String clean(String html, Safelist safelist) {
        if (html == null) return null;
        return Jsoup.clean(html, "", safelist, new Document.OutputSettings().prettyPrint(false));
    }

    /** The text without tags, entities read and white space collapsed - for search, previews and summaries. */
    public static String plain(String html) {
        if (html == null) return null;
        return Jsoup.parse(html).text();
    }
}
