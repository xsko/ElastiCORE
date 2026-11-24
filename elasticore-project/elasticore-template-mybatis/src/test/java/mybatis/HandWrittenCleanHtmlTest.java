package mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import io.elasticore.sample.mybatis.shop.domain.model.Gadget;
import org.junit.jupiter.api.Test;

/**
 * The entity keeps cleanHtml() for the developer's own code (issue 217): a hand-written call compiles and cleans with
 * the generated defaults - Gadget.page to jsoup's relaxed safelist, its text without tags in pageText - though the
 * generated service now cleans through the CoreService pair instead.
 */
class HandWrittenCleanHtmlTest {

    @Test
    void aHandWrittenCallStillCleansWithTheDefaults() {
        Gadget gadget = new Gadget();
        gadget.setPage("<h2 onclick=\"steal()\">Specs</h2><script>steal()</script><p>light <b>and</b> small</p>");
        gadget.cleanHtml();
        assertThat(gadget.getPage()).isEqualTo("<h2>Specs</h2><p>light <b>and</b> small</p>");
        assertThat(gadget.getPageText()).isEqualTo("Specs light and small");

        // A field set to null leaves its copy null.
        gadget.setPage(null);
        gadget.cleanHtml();
        assertThat(gadget.getPage()).isNull();
        assertThat(gadget.getPageText()).isNull();
    }
}
