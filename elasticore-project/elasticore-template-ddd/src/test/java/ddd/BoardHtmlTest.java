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
 * An html field over HTTP (issue 183): Post.content is kept to jsoup's basic safelist before it is saved - on a
 * create and on the publish command, whose parameter sets it - and Post.contentText holds its text without tags,
 * which the search reads and no request writes. (A board post is no @editable root: it has no PUT.)
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
        assertThat(saved.path("contentText").asText()).isEqualTo("Hello board rules");
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
