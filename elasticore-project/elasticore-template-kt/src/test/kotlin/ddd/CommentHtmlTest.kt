package ddd

import io.elasticore.blueprint.domain.content.dto.CommentDTO
import io.elasticore.blueprint.domain.content.service.CommentService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * An html field in Kotlin, on the generated schema (issue 183): Comment.content is kept to jsoup's basic safelist
 * by the generated CommentCoreService on a save and on an update, and Comment.contentText holds its text without
 * tags - whatever the caller put there.
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
}
