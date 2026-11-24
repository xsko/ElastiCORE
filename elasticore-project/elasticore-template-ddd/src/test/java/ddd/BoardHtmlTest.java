package ddd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * An html field over HTTP (issues 183, 217): Post.content is kept to an allow-list before it is saved - on a create
 * and on the publish command, whose parameter sets it - and Post.contentText holds its text without tags, which the
 * search reads and no request writes. The board's PostService overrides the generated pair: jsoup's basic safelist
 * with the site's own images (/api/files/...), an empty body refused, and a line per paragraph in the text.
 * (A board post is no @editable root: it has no PUT.)
 */
@SpringBootTest
@AutoConfigureMockMvc
class BoardHtmlTest {

    private static final String POSTS = "/api/v1/board/post";

    private static final String SENT = "<p onclick=\"steal()\">Hello <strong>board</strong></p>"
            + "<script>steal()</script>"
            + "<a href=\"https://example.com/rules\">rules</a>";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private JsonNode send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            Object body) throws Exception {
        MockHttpServletResponse res = mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andReturn().getResponse();
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        return json.readTree(res.getContentAsString());
    }

    private JsonNode read(long id) throws Exception {
        return json.readTree(mvc.perform(get(POSTS + "/" + id)).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("a script, a handler and a bare link are cleaned on a create, and the text is kept beside it")
    void aCreateIsCleaned() throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("title", "cleaned on create");
        body.put("content", SENT);
        body.put("contentText", "written by the request");
        long id = send(post(POSTS), body).path("postId").asLong();

        JsonNode saved = read(id);
        assertThat(saved.path("content").asText()).isEqualTo(
                "<p>Hello <strong>board</strong></p><a href=\"https://example.com/rules\" rel=\"nofollow\">rules</a>");
        // PostService.plainContent: a line per paragraph (Html.plainLines).
        assertThat(saved.path("contentText").asText()).isEqualTo("Hello board\nrules");
    }

    /** PostService.cleanContent (issue 217): the board keeps only the files uploaded to it. */
    @Test
    @DisplayName("the service's own cleaning keeps only this site's images, and the text keeps a line per paragraph")
    void theServicesOwnCleaningRuns() throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("title", "own cleaning");
        body.put("content", "<p>First</p><p>Second <img src=\"/api/files/3\"></p>"
                + "<ul><li>one</li><li>two <img src=\"https://other.host/x.png\"></li></ul>"
                + "<img src=\"//other.host/y.png\"><img src=\"javascript:steal()\">");
        long id = send(post(POSTS), body).path("postId").asLong();

        JsonNode saved = read(id);
        assertThat(saved.path("content").asText()).isEqualTo(
                "<p>First</p><p>Second <img src=\"/api/files/3\"></p><ul><li>one</li><li>two </li></ul>");
        assertThat(saved.path("contentText").asText()).isEqualTo("First\nSecond\none\ntwo");
    }

    /**
     * A refusal from cleanContent is answered as one from beforeSave: 400 with its code, and nothing is written -
     * on a create and after a command.
     */
    @Test
    @DisplayName("an empty body is refused with the service's code, and nothing is written")
    void aRefusalFromTheCleaningWritesNothing() throws Exception {
        String title = "refused " + System.nanoTime();
        ObjectNode body = json.createObjectNode();
        body.put("title", title);
        body.put("content", "<p> </p><script>steal()</script>");
        MockHttpServletResponse refused = mvc.perform(post(POSTS).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andReturn().getResponse();
        assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(400);
        assertThat(json.readTree(refused.getContentAsString()).path("code").asText())
                .isEqualTo("post.content.required");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM brd_post WHERE title = ?", Long.class, title))
                .isZero();

        body.put("content", "<p>draft</p>");
        long id = send(post(POSTS), body).path("postId").asLong();
        MockHttpServletResponse command = mvc.perform(post(POSTS + "/" + id + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(json.createObjectNode().put("content", "<p></p>"))))
                .andReturn().getResponse();
        assertThat(command.getStatus()).as(command.getContentAsString()).isEqualTo(400);
        assertThat(json.readTree(command.getContentAsString()).path("code").asText())
                .isEqualTo("post.content.required");
        JsonNode kept = read(id);
        assertThat(kept.path("status").asText()).isEqualTo("DRF");
        assertThat(kept.path("content").asText()).isEqualTo("<p>draft</p>");
    }

    /**
     * The cleaning runs only where the field changed: a command that hands the post the content it already holds
     * does not clean it again. (The stored value is set past the service here, so the skip shows.)
     */
    @Test
    @DisplayName("a command that leaves the content as it was does not clean it again")
    void anUnchangedFieldIsNotCleanedAgain() throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("title", "unchanged");
        body.put("content", "<p>kept</p>");
        long id = send(post(POSTS), body).path("postId").asLong();
        String stored = "<p>kept <img src=\"https://other.host/x.png\"></p>";
        jdbc.update("UPDATE brd_post SET content = ? WHERE post_id = ?", stored, id);

        send(post(POSTS + "/" + id + "/publish"), json.createObjectNode().put("content", stored));

        JsonNode saved = read(id);
        assertThat(saved.path("status").asText()).isEqualTo("PUB");
        assertThat(saved.path("content").asText()).isEqualTo(stored);
        assertThat(saved.path("contentText").asText()).isEqualTo("kept");
    }

    /**
     * The command's parameter is kept by the move inside the entity, past the save path: the service cleans the row
     * after the move, before it is written.
     */
    @Test
    @DisplayName("a command that sets the content is cleaned too, and the text follows the new content")
    void aCommandIsCleaned() throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("title", "cleaned on publish");
        body.put("content", "<p>draft</p>");
        long id = send(post(POSTS), body).path("postId").asLong();
        assertThat(read(id).path("contentText").asText()).isEqualTo("draft");

        send(post(POSTS + "/" + id + "/publish"),
                json.createObjectNode().put("content", "<p>final <img src=\"x\" onerror=\"steal()\"></p>"));

        JsonNode saved = read(id);
        assertThat(saved.path("status").asText()).isEqualTo("PUB");
        assertThat(saved.path("content").asText()).isEqualTo("<p>final </p>");
        assertThat(saved.path("contentText").asText()).isEqualTo("final");
    }

    @Test
    @DisplayName("the search reads the text: words of the post find it, the removed script's do not")
    void theSearchReadsTheText() throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("title", "searched");
        body.put("content", "<p>a <em>marmalade</em> recipe</p><script>steal()</script>");
        long id = send(post(POSTS), body).path("postId").asLong();

        JsonNode found = send(post(POSTS + "/search"), json.createObjectNode().put("contentText", "marmalade"));
        assertThat(found.path("content").findValuesAsText("postId")).contains(String.valueOf(id));
        JsonNode script = send(post(POSTS + "/search"), json.createObjectNode().put("contentText", "steal"));
        assertThat(script.path("content").findValuesAsText("postId")).doesNotContain(String.valueOf(id));
    }
}
