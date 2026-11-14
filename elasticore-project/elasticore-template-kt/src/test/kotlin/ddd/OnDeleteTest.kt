package ddd

import io.elasticore.blueprint.domain.content.service.ArticleService
import io.elasticore.blueprint.domain.content.service.CommentService
import io.elasticore.springboot3.bean.ApplicationContextProvider
import io.elasticore.springboot3.bean.DbTransactionGateway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate

/** The application of the content domain alone. */
@SpringBootApplication(proxyBeanMethods = false)
@Import(DbTransactionGateway::class, ApplicationContextProvider::class)
@EnableJpaAuditing
@ComponentScan("io.elasticore.blueprint.domain.content")
@EntityScan("io.elasticore.blueprint.domain.content.entity")
@EnableJpaRepositories("io.elasticore.blueprint.domain.content.repository")
open class KotlinContentApp

/**
 * @onDelete in Kotlin, on the generated schema: deleting an article deletes its comments and their
 * replies (cascade) and clears its bookmarks (setNull) - the companion's onDeleteArticle, run by the
 * generated ArticleCoreService before its delete.
 */
@SpringBootTest(
    classes = [KotlinContentApp::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:kt-ondelete;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema-h2.sql",
    ],
)
class OnDeleteTest {

    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var articles: ArticleService
    @Autowired lateinit var comments: CommentService

    private fun count(sql: String, vararg args: Any): Int = jdbc.queryForObject(sql, Int::class.java, *args)!!

    @Test
    fun deletingAnArticleTakesItsCommentsAndClearsItsBookmarks() {
        jdbc.update("INSERT INTO cms_article (article_id, title, slug) VALUES ('a-1', 'First', 'first')")
        jdbc.update("INSERT INTO cms_comment (comment_id, content, author_name, article_article_id) VALUES (901, 'hi', 'kim', 'a-1')")
        jdbc.update("INSERT INTO cms_comment (comment_id, content, author_name, article_article_id, test_id) VALUES (902, 're', 'lee', 'a-1', 901)")
        jdbc.update("INSERT INTO cms_bookmark (bookmark_id, memo, article_article_id) VALUES (903, 'later', 'a-1')")

        assertTrue(articles.deleteById("a-1"))

        assertEquals(0, count("SELECT COUNT(*) FROM cms_article WHERE article_id = 'a-1'"))
        assertEquals(0, count("SELECT COUNT(*) FROM cms_comment WHERE comment_id IN (901, 902)"))
        assertEquals(1, count("SELECT COUNT(*) FROM cms_bookmark WHERE bookmark_id = 903 AND article_article_id IS NULL"))
        // Article is @audited: its delete is a revision, numbered by revinfo's identity column on H2.
        assertEquals(1, count("SELECT COUNT(*) FROM cms_article_aud WHERE article_id = 'a-1' AND revtype = 2"))
        assertTrue(count("SELECT COUNT(*) FROM revinfo WHERE rev IS NOT NULL") >= 1)
    }

    /** @count(Comment.article, approved == true): counted again in the transaction of each write of a comment. */
    @Test
    fun anArticleCountsItsApprovedComments() {
        jdbc.update("INSERT INTO cms_article (article_id, title, slug, comment_count) VALUES ('a-9', 'Counted', 'counted', 0)")
        val kept = comments.save(io.elasticore.blueprint.domain.content.dto.CommentDTO().apply {
            content = "yes"; authorName = "kim"; approved = true; articleId = "a-9"
        })
        comments.save(io.elasticore.blueprint.domain.content.dto.CommentDTO().apply {
            content = "not yet"; authorName = "lee"; approved = false; articleId = "a-9"
        })
        assertEquals(1, count("SELECT comment_count FROM cms_article WHERE article_id = 'a-9'"))

        assertTrue(comments.deleteById(kept!!.commentId!!))
        assertEquals(0, count("SELECT comment_count FROM cms_article WHERE article_id = 'a-9'"))
    }

    @Test
    fun deletingACommentTakesItsReplies() {
        jdbc.update("INSERT INTO cms_comment (comment_id, content, author_name) VALUES (911, 'top', 'kim')")
        jdbc.update("INSERT INTO cms_comment (comment_id, content, author_name, test_id) VALUES (912, 'reply', 'lee', 911)")
        jdbc.update("INSERT INTO cms_comment (comment_id, content, author_name, test_id) VALUES (913, 'deeper', 'park', 912)")

        assertTrue(comments.deleteById(911L))

        assertEquals(0, count("SELECT COUNT(*) FROM cms_comment WHERE comment_id IN (911, 912, 913)"))
    }
}
