package io.elasticore.springboot3.html;

import org.jsoup.safety.Safelist;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HtmlTest {

    @Test
    void basicKeepsTextMarkupAndLinksAndDropsScriptsHandlersAndImages() {
        String sent = "<p onclick=\"steal()\">Hi <strong>there</strong>, <em>see</em></p>"
                + "<script>steal()</script>"
                + "<ul><li>one</li></ul>"
                + "<a href=\"https://example.com/a\">link</a>"
                + "<a href=\"javascript:steal()\">bad</a>"
                + "<img src=\"https://example.com/i.png\">";
        assertEquals(
                "<p>Hi <strong>there</strong>, <em>see</em></p>"
                        + "<ul><li>one</li></ul>"
                        + "<a href=\"https://example.com/a\" rel=\"nofollow\">link</a>"
                        + "<a rel=\"nofollow\">bad</a>",
                Html.clean(sent, Safelist.basic()));
    }

    @Test
    void relaxedKeepsImagesAndTablesAndAddsNoRel() {
        String sent = "<table><tbody><tr><td>1</td></tr></tbody></table>"
                + "<img src=\"https://example.com/i.png\" onerror=\"steal()\">"
                + "<a href=\"https://example.com/a\">link</a>";
        assertEquals(
                "<table><tbody><tr><td>1</td></tr></tbody></table>"
                        + "<img src=\"https://example.com/i.png\">"
                        + "<a href=\"https://example.com/a\">link</a>",
                Html.clean(sent, Safelist.relaxed()));
    }

    /** The site's own image, written relative, keeps its src (issue 217). */
    @Test
    void relaxedKeepsARelativeLinkAsWritten() {
        assertEquals(
                "<img src=\"/api/files/3\"><a href=\"/posts/7\">post</a><a href=\"#top\">top</a>",
                Html.clean(
                        "<img src=\"/api/files/3\"><a href=\"/posts/7\">post</a><a href=\"#top\">top</a>",
                        Safelist.relaxed()));
        assertEquals("<img src=\"files/3\">", Html.clean("<img src=\"files/3\">", Safelist.relaxed()));
    }

    /**
     * A link to this site has no rel, as jsoup marks only one to another host;
     * a protocol-relative one is such a link, and keeps it.
     */
    @Test
    void basicKeepsARelativeLinkAndMarksOnlyAnotherHost() {
        assertEquals("<a href=\"/posts/7\">post</a>", Html.clean("<a href=\"/posts/7\">post</a>", Safelist.basic()));
        assertEquals(
                "<a href=\"//other.host/x\" rel=\"nofollow\">x</a>",
                Html.clean("<a href=\"//other.host/x\">x</a>", Safelist.basic()));
        assertEquals(
                "<a href=\"https://other.host/x\" rel=\"nofollow\">x</a>",
                Html.clean("<a href=\"https://other.host/x\">x</a>", Safelist.basic()));
    }

    /** No protocol the safelist refuses gets through as a relative link. */
    @Test
    void aRefusedProtocolStaysRefused() {
        String[] refused = {
            "javascript:alert(1)", "JaVaScRiPt:alert(1)", " javascript:alert(1)", "java&#09;script:alert(1)",
            "java&#10;script:alert(1)", "&#x6A;avascript:alert(1)", "vbscript:x", "data:image/png;base64,AA"
        };
        for (String url : refused) {
            assertEquals("<img>", Html.clean("<img src=\"" + url + "\">", Safelist.relaxed()), url);
            assertEquals(
                    "<a rel=\"nofollow\">x</a>", Html.clean("<a href=\"" + url + "\">x</a>", Safelist.basic()), url);
        }
    }

    /**
     * A link a browser reads as another host's, though jsoup reads it as a
     * path of this site: a backslash is a slash to a browser, and https:host
     * is a host on an http page. Removed, as before relative links were kept.
     */
    @Test
    void aLinkABrowserReadsAsAnotherHostIsRemoved() {
        String[] other = {
            "/\\evil.host/x",
            "\\\\evil.host/x",
            "https:evil.host/x",
            "HTTP:evil.host",
            "https:\\\\evil.host",
            "/&#09;/evil.host/x"
        };
        for (String url : other) {
            assertEquals("<img>", Html.clean("<img src=\"" + url + "\">", Safelist.relaxed()), url);
            assertEquals(
                    "<a rel=\"nofollow\">x</a>", Html.clean("<a href=\"" + url + "\">x</a>", Safelist.basic()), url);
        }
    }

    /** The application's safelist is not changed by the clean. */
    @Test
    void theSafelistGivenIsNotChanged() {
        Safelist mine = Safelist.relaxed();
        Html.clean("<img src=\"/api/files/3\">", mine);
        assertEquals(
                "<img>",
                org.jsoup.Jsoup.clean(
                        "<img src=\"/api/files/3\">",
                        "",
                        mine,
                        new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false)));
    }

    @Test
    void whatIsKeptIsNotReformatted() {
        assertEquals("<p>a</p><p>b</p>", Html.clean("<p>a</p><p>b</p>", Safelist.basic()));
        assertEquals("<p>a\n  b</p>", Html.clean("<p>a\n  b</p>", Safelist.basic()));
    }

    @Test
    void plainIsTheTextWithoutTagsAndWithoutAScriptsText() {
        assertEquals(
                "Hi there, see one", Html.plain("<p>Hi <strong>there</strong>, <em>see</em></p><ul><li>one</li></ul>"));
        assertEquals("a", Html.plain("<p>a</p><script>steal()</script>"));
        assertEquals("1 < 2 & 3", Html.plain("<p>1 &lt; 2 &amp; 3</p>"));
        assertEquals("", Html.plain(""));
    }

    @Test
    void nothingStaysNothing() {
        assertNull(Html.clean(null, Safelist.basic()));
        assertNull(Html.plain(null));
    }
}
