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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * @count in the board domain, over HTTP: Post.commentCount counts its visible comments and Post.likeCount its
 * likes, counted again in the transaction of each write - a comment made, hidden, restored, deleted, a like
 * given and taken back, a comment's replies cascaded away - and no request writes them.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BoardCountTest {

    private static final String BASE = "/api/v1/board/";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    private long create(String what, String body, String key) throws Exception {
        MockHttpServletResponse res = mvc.perform(post(BASE + what).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        return json.readTree(res.getContentAsString()).path(key).asLong();
    }

    private JsonNode postOf(long id) throws Exception {
        return json.readTree(mvc.perform(get(BASE + "post/" + id)).andReturn().getResponse().getContentAsString());
    }

    private int status(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("comments and likes are counted on the post as they come and go")
    void countsFollowTheWrites() throws Exception {
        long post = create("post", "{\"title\":\"counted\"}", "postId");
        assertThat(postOf(post).path("commentCount").asLong()).isZero();
        assertThat(postOf(post).path("likeCount").asLong()).isZero();

        long first = create("comment", "{\"text\":\"one\",\"postId\":" + post + "}", "commentId");
        long second = create("comment", "{\"text\":\"two\",\"postId\":" + post + "}", "commentId");
        create("comment", "{\"text\":\"re\",\"postId\":" + post + ",\"parentCommentId\":" + first + "}", "commentId");
        long like = create("postLike", "{\"postId\":" + post + "}", "likeId");
        assertThat(postOf(post).path("commentCount").asLong()).isEqualTo(3);
        assertThat(postOf(post).path("likeCount").asLong()).isEqualTo(1);

        // A hidden comment is not counted (status is VISIBLE); restored, it is again.
        assertThat(status(post(BASE + "comment/" + second + "/hide"))).isEqualTo(200);
        assertThat(postOf(post).path("commentCount").asLong()).isEqualTo(2);
        assertThat(status(post(BASE + "comment/" + second + "/restore"))).isEqualTo(200);
        assertThat(postOf(post).path("commentCount").asLong()).isEqualTo(3);

        // Deleting a comment takes its reply with it (@onDelete(cascade)): both leave the count.
        assertThat(status(delete(BASE + "comment/" + first))).isEqualTo(200);
        assertThat(postOf(post).path("commentCount").asLong()).isEqualTo(1);

        assertThat(status(delete(BASE + "postLike/" + like))).isEqualTo(200);
        assertThat(postOf(post).path("likeCount").asLong()).isZero();
    }

    @Test
    @DisplayName("no request writes a count")
    void aRequestDoesNotWriteACount() throws Exception {
        long post = create("post", "{\"title\":\"not by hand\",\"commentCount\":99,\"likeCount\":7}", "postId");
        assertThat(postOf(post).path("commentCount").asLong()).isZero();
        assertThat(postOf(post).path("likeCount").asLong()).isZero();
        JsonNode current = postOf(post);
        int updated = status(put(BASE + "post").contentType(MediaType.APPLICATION_JSON)
                .content("{\"postId\":" + post + ",\"title\":\"still\",\"commentCount\":42,\"version\":"
                        + current.path("version").asLong() + "}"));
        assertThat(updated).isIn(200, 405);
        assertThat(postOf(post).path("commentCount").asLong()).isZero();
    }
}
