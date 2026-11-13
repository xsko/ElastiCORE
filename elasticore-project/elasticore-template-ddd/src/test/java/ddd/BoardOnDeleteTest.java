package ddd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * @onDelete in the board domain, over HTTP: deleting a post deletes its comments and their replies
 * (cascade), clears a bookmark's post (setNull), and is refused while a report points to the post or to one
 * of the comments it would take with it (restrict) - the whole delete undone, not half of it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BoardOnDeleteTest {

    private static final String BASE = "/api/v1/board/";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private org.springframework.context.ApplicationContext context;

    private long create(String what, String body, String key) throws Exception {
        MockHttpServletResponse res = mvc.perform(post(BASE + what).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        return json.readTree(res.getContentAsString()).path(key).asLong();
    }

    private MockHttpServletResponse read(String what, long id) throws Exception {
        return mvc.perform(get(BASE + what + "/" + id)).andReturn().getResponse();
    }

    private MockHttpServletResponse remove(String what, long id) throws Exception {
        return mvc.perform(delete(BASE + what + "/" + id)).andReturn().getResponse();
    }

    @Test
    @DisplayName("cascade takes the comments and their replies; setNull clears the bookmark")
    void cascadeAndSetNull() throws Exception {
        long post = create("post", "{\"title\":\"first\"}", "postId");
        long comment = create("comment", "{\"text\":\"hi\",\"postId\":" + post + "}", "commentId");
        long reply = create("comment", "{\"text\":\"re\",\"postId\":" + post + ",\"parentCommentId\":" + comment + "}",
                "commentId");
        long bookmark = create("bookmark", "{\"label\":\"later\",\"postId\":" + post + "}", "bookmarkId");

        MockHttpServletResponse deleted = remove("post", post);
        assertThat(deleted.getStatus()).as(deleted.getContentAsString()).isEqualTo(200);

        assertThat(read("post", post).getStatus()).isEqualTo(404);
        assertThat(read("comment", comment).getStatus()).isEqualTo(404);
        assertThat(read("comment", reply).getStatus()).isEqualTo(404);
        MockHttpServletResponse kept = read("bookmark", bookmark);
        assertThat(kept.getStatus()).isEqualTo(200);
        JsonNode mark = json.readTree(kept.getContentAsString());
        assertThat(mark.path("label").asText()).isEqualTo("later");
        assertThat(mark.path("postId").isNull() || mark.path("postId").isMissingNode()).as(mark.toString()).isTrue();
    }

    @Test
    @DisplayName("restrict refuses with 409 restricted, naming what points")
    void restrict() throws Exception {
        long post = create("post", "{\"title\":\"reported\"}", "postId");
        create("report", "{\"reason\":\"spam\",\"postId\":" + post + "}", "reportId");

        MockHttpServletResponse refused = remove("post", post);
        assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(409);
        JsonNode problem = json.readTree(refused.getContentAsString());
        assertThat(problem.path("code").asText()).isEqualTo("restricted");
        assertThat(problem.path("detail").asText())
                .isEqualTo("Post " + post + " is still pointed to by Report rows (Report.post @onDelete(restrict))");
        assertThat(read("post", post).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("a search deletes a post and the post quoting it: the quote cleared (versioned) and both gone")
    void aSearchDeletesAVersionedQuote() throws Exception {
        long quoted = create("post", "{\"title\":\"quote-src\"}", "postId");
        long quoting = create("post", "{\"title\":\"quote-reply\",\"quotedPostId\":" + quoted + "}", "postId");
        create("comment", "{\"text\":\"on the reply\",\"postId\":" + quoting + "}", "commentId");

        io.elasticore.sample.ddd.board.application.PostCoreService posts =
                context.getBean(io.elasticore.sample.ddd.board.application.PostService.class);
        io.elasticore.sample.ddd.board.application.dto.PostSrchDTO byTitle =
                new io.elasticore.sample.ddd.board.application.dto.PostSrchDTO();
        byTitle.setTitle("quote-");
        assertThat(posts.delete(byTitle)).isEqualTo(2);
        assertThat(read("post", quoted).getStatus()).isEqualTo(404);
        assertThat(read("post", quoting).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("a cycle of replies is refused with 409 cycle, not recursed into without end")
    void aCycleIsRefused() throws Exception {
        long post = create("post", "{\"title\":\"cyclic\"}", "postId");
        long first = create("comment", "{\"text\":\"a\",\"postId\":" + post + "}", "commentId");
        long second = create("comment", "{\"text\":\"b\",\"postId\":" + post + ",\"parentCommentId\":" + first + "}",
                "commentId");
        // No request makes a reply the parent of its own parent; the database can hold it.
        jdbc.update("UPDATE brd_comment SET parent_comment_id = ? WHERE comment_id = ?", second, first);

        MockHttpServletResponse refused = remove("post", post);
        assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(409);
        assertThat(json.readTree(refused.getContentAsString()).path("code").asText()).isEqualTo("cycle");
        assertThat(read("post", post).getStatus()).isEqualTo(200);
        assertThat(read("comment", first).getStatus()).isEqualTo(200);
        assertThat(read("comment", second).getStatus()).isEqualTo(200);
        // Broken by hand, it goes.
        jdbc.update("UPDATE brd_comment SET parent_comment_id = NULL WHERE comment_id = ?", first);
        assertThat(remove("post", post).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("a restrict met on the cascade's way undoes the whole delete")
    void restrictOnTheWay() throws Exception {
        long post = create("post", "{\"title\":\"with a reported comment\"}", "postId");
        long comment = create("comment", "{\"text\":\"bad\",\"postId\":" + post + "}", "commentId");
        long other = create("comment", "{\"text\":\"fine\",\"postId\":" + post + "}", "commentId");
        create("report", "{\"reason\":\"rude\",\"commentId\":" + comment + "}", "reportId");

        MockHttpServletResponse refused = remove("post", post);
        assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(409);
        assertThat(json.readTree(refused.getContentAsString()).path("detail").asText())
                .isEqualTo("Comment " + comment + " is still pointed to by Report rows (Report.comment @onDelete(restrict))");
        assertThat(read("post", post).getStatus()).isEqualTo(200);
        assertThat(read("comment", comment).getStatus()).isEqualTo(200);
        assertThat(read("comment", other).getStatus()).isEqualTo(200);
    }
}
