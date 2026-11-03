package io.elasticore.blueprint.domain.content;

import io.elasticore.blueprint.domain.content.dto.AttachmentDTO;
import io.elasticore.blueprint.domain.content.dto.ContentMapper;
import io.elasticore.blueprint.domain.content.entity.Article;
import io.elasticore.blueprint.domain.content.entity.Attachment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AttachmentDTO's articleId is @ref(article.articleId) @forceupdate: a full
 * update that sends no articleId - null or empty - takes the attachment off
 * its article, a PATCH (isSkipNull) leaves it where it is, and an id attaches
 * that article. Run on the mapper the 2.x generator wrote, against H2.
 */
@DataJpaTest
class AttachmentArticleForceUpdateTest {

    @Autowired
    TestEntityManager em;

    @Test
    void aFullUpdateWithoutAnArticleIdTakesTheArticleAway() {
        Attachment attachment = attachedTo(article("first"));
        AttachmentDTO dto = ContentMapper.toDTO(attachment);
        dto.setArticleId(null);

        ContentMapper.mapping(dto, attachment, false, em.getEntityManager());

        assertThat(reread(attachment).getArticle()).isNull();
    }

    @Test
    void anEmptyArticleIdIsNoneToo() {
        Attachment attachment = attachedTo(article("first"));
        AttachmentDTO dto = ContentMapper.toDTO(attachment);
        dto.setArticleId("");

        ContentMapper.mapping(dto, attachment, false);

        assertThat(reread(attachment).getArticle()).isNull();
    }

    @Test
    void aPatchWithoutAnArticleIdKeepsIt() {
        Article first = article("first");
        Attachment attachment = attachedTo(first);
        AttachmentDTO dto = ContentMapper.toDTO(attachment);
        dto.setArticleId(null);

        ContentMapper.mapping(dto, attachment, true, em.getEntityManager());

        assertThat(reread(attachment).getArticle().getArticleId()).isEqualTo(first.getArticleId());
    }

    @Test
    void anArticleIdAttachesThatArticle() {
        Attachment attachment = attachedTo(article("first"));
        Article second = article("second");
        AttachmentDTO dto = ContentMapper.toDTO(attachment);
        dto.setArticleId(second.getArticleId());

        ContentMapper.mapping(dto, attachment, false, em.getEntityManager());

        assertThat(reread(attachment).getArticle().getArticleId()).isEqualTo(second.getArticleId());
    }

    private Article article(String slug) {
        Article article = new Article();
        article.setTitle(slug);
        article.setSlug(slug);
        return em.persistAndFlush(article);
    }

    private Attachment attachedTo(Article article) {
        Attachment attachment = new Attachment();
        attachment.setFileName("notes.txt");
        attachment.setArticle(article);
        return em.persistAndFlush(attachment);
    }

    private Attachment reread(Attachment attachment) {
        em.flush();
        em.clear();
        return em.find(Attachment.class, attachment.getAttachId());
    }
}
