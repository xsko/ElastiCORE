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
