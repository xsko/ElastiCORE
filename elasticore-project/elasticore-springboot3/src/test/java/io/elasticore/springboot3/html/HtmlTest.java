package io.elasticore.springboot3.html;

import org.jsoup.safety.Safelist;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void whichLinksABrowserReadsAsAnotherHost() {
        String[] other = {
            "/\\h", "\\h", "a\tb", "a\nb", "a\u007fb", "https:h", "HTTP:h", " https:h", "\thttps:/h", "https:\\\\h"
        };
        for (String url : other) assertTrue(Html.readAsAnotherHost(url), url);
        String[] same = {
            "/a",
            "a/b",
            "#top",
            "?q=1",
            "",
            "  /a  ",
            "https://h/a\\b",
            "HTTPS://h",
            " http://h",
            "mailto:a@b",
            "javascript:x",
            "x:y",
            "1a:b",
            "a b"
        };
        for (String url : same) assertFalse(Html.readAsAnotherHost(url), url);
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
        assertNull(Html.plainLines(null));
    }

    /** One line per paragraph (issue 217): where plain joins them, plainLines breaks. */
    @Test
    void plainLinesKeepsALinePerParagraphAndNoListMarker() {
        String sent = "<p>First</p><p>Second</p><ul><li>one</li><li>two</li></ul>";
        assertEquals("First Second one two", Html.plain(sent));
        assertEquals("First\nSecond\none\ntwo", Html.plainLines(sent));
        assertEquals(
                "Title\nSub\nquoted\ncode\ncell 1\ncell 2\nafter rule\nlast",
                Html.plainLines("<h1>Title</h1><h6>Sub</h6><blockquote>quoted</blockquote><pre>code</pre>"
                        + "<table><tr><td>cell 1</td></tr><tr><td>cell 2</td></tr></table>"
                        + "<div>after rule</div><hr>last"));
    }

    @Test
    void plainLinesEndsOneLineForBlocksInsideEachOther() {
        assertEquals("a\nb", Html.plainLines("<blockquote><p>a</p></blockquote><ul><li><p>b</p></li></ul>"));
        assertEquals("a\nb", Html.plainLines("<div><div><p>a</p></div></div><div><p>b</p></div>"));
        assertEquals("a\nb", Html.plainLines("<p>a</p><p></p><p> </p><p>b</p>"));
        assertEquals("a\nb", Html.plainLines("<p>a<br></p><p>b</p>"));
        // Text outside a block ends its line where one begins, too.
        assertEquals("a\nb\nc", Html.plainLines("a<p>b</p>c"));
    }

    /** An editor's empty paragraph, which a browser shows as an empty line. */
    @Test
    void plainLinesKeepsAnEditorsEmptyParagraphAsABlankLine() {
        assertEquals("a\n\nb", Html.plainLines("<p>a</p><p><br></p><p>b</p>"));
        assertEquals("a\n\nb", Html.plainLines("<p>a</p><p><br></p><p><br></p><p>b</p>"));
    }

    @Test
    void plainLinesBreaksAtABrAndKeepsAtMostOneBlankLine() {
        assertEquals("a\nb", Html.plainLines("a<br>b"));
        assertEquals("a\n\nb", Html.plainLines("a<br><br>b"));
        assertEquals("a\n\nb", Html.plainLines("a<br><br><br><br>b"));
        assertEquals("a\n\nb", Html.plainLines("<p>a<br><br></p><p><br></p><p>b</p>"));
    }

    @Test
    void plainLinesCollapsesSpacesTrimsLineEndsAndStripsBothEnds() {
        assertEquals(
                "Hello board, see\nnext",
                Html.plainLines(
                        "\n  <p>  Hello <strong>board</strong>,\n   <em>see</em>&nbsp; </p>\n\n<p>next \t</p>  "));
        assertEquals("a b", Html.plainLines("a <b>b</b>"));
        assertEquals("ab", Html.plainLines("a<b>b</b>"));
        // An element without a line of its own still parts the words, as plain does.
        assertEquals("1 2", Html.plainLines("<table><tr><td>1</td><td>2</td></tr></table>"));
        assertEquals("", Html.plainLines(""));
        assertEquals("", Html.plainLines("<p></p>"));
    }

    @Test
    void plainLinesReadsEntitiesSkipsScriptsAndKeepsThePresLines() {
        assertEquals("1 < 2 & 3\na", Html.plainLines("<p>1 &lt; 2 &amp; 3</p><script>steal()</script><p>a</p>"));
        assertEquals("x\nif (a) {\n  b();\n}\ny", Html.plainLines("<p>x</p><pre>if (a) {\n  b();\n}</pre><p>y</p>"));
    }

    /** A pre's text is kept as written: its blank lines, its trailing spaces and its first line's indentation. */
    @Test
    void plainLinesKeepsAPreAsWritten() {
        assertEquals(
                "x\n    if (a) {\n\n\n        b();   \n    }\ny",
                Html.plainLines("<p>x</p><pre>    if (a) {\n\n\n        b();   \n    }\n</pre><p>y</p>"));
        // First in the text, its indentation stays; inline markup inside it gives its text.
        assertEquals("  <a> & b\n\tc", Html.plainLines("<pre>  &lt;a&gt; <b>&amp;</b> b\n\tc</pre>"));
        assertEquals("a\nb", Html.plainLines("a<pre>b</pre>"));
    }
}
