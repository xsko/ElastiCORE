package ddd

import io.elasticore.blueprint.domain.content.dto.CommentDTO
import io.elasticore.blueprint.domain.content.service.CommentService
import io.elasticore.springboot3.validation.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * An html field in Kotlin, on the generated schema (issue 183): Comment.content is kept to jsoup's basic safelist
 * by the generated CommentCoreService on a save and on an update, and Comment.contentText holds its text without
 * tags - whatever the caller put there. CommentService overrides the generated pair (issue 217).
 */
@SpringBootTest(
    classes = [KotlinContentApp::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:kt-html;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema-h2.sql",
    ],
)
class CommentHtmlTest {

    @Autowired lateinit var comments: CommentService
    @Autowired lateinit var jdbc: JdbcTemplate

    private fun stored(id: Long?, column: String): String? =
        jdbc.queryForObject("SELECT $column FROM cms_comment WHERE comment_id = ?", String::class.java, id)

    @Test
    fun aCommentIsCleanedOnASaveAndOnAnUpdateAndItsTextFollows() {
        val saved = comments.save(CommentDTO().apply {
            content = "<p onclick=\"steal()\">Nice <b>post</b></p><script>steal()</script>"
            contentText = "written by the caller"
            authorName = "kim"
        })!!
        assertEquals("<p>Nice <b>post</b></p>", saved.content)
        assertEquals("Nice post", saved.contentText)
        assertEquals("<p>Nice <b>post</b></p>", stored(saved.commentId, "content"))
        assertEquals("Nice post", stored(saved.commentId, "content_text"))

        saved.content = "<a href=\"https://example.com/more\">more</a><img src=\"x\" onerror=\"steal()\">"
        saved.contentText = "written by the caller again"
        val changed = comments.update(saved)!!
        assertEquals("<a href=\"https://example.com/more\" rel=\"nofollow\">more</a>", changed.content)
        assertEquals("more", changed.contentText)
        assertEquals("more", stored(saved.commentId, "content_text"))
    }

    /** CommentService overrides the generated pair (issue 217): a line per paragraph, and no comment without words. */
    @Test
    fun theServicesOwnCleaningRunsAndItsRefusalWritesNothing() {
        val saved = comments.save(CommentDTO().apply {
            content = "<p>First</p><ul><li>one</li><li>two</li></ul>"
            authorName = "lee"
        })!!
        assertEquals("First\none\ntwo", stored(saved.commentId, "content_text"))

        val before = jdbc.queryForObject("SELECT COUNT(*) FROM cms_comment", Long::class.java)
        val refused = assertThrows(BadRequestException::class.java) {
            comments.save(CommentDTO().apply {
                content = "<p> </p><script>steal()</script>"
                authorName = "park"
            })
        }
        assertEquals("comment.content.required", refused.code)
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM cms_comment", Long::class.java))

        // An update that leaves the content as it was does not clean it again: the stored text stays.
        jdbc.update("UPDATE cms_comment SET content_text = ? WHERE comment_id = ?", "kept", saved.commentId)
        val unchanged = comments.findById(saved.commentId!!).get().apply { authorName = "lee2" }
        comments.update(unchanged)
        assertEquals("kept", stored(saved.commentId, "content_text"))
    }
}
