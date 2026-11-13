package io.elasticore.springboot3.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.elasticore.springboot3.mapper.words.WordsMember;
import io.elasticore.springboot3.mapper.words.WordsPost;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/** {@code @search(words: ...)} against H2: every word, each in any field, matched as written. */
@DisplayName("Words: one search box over several fields")
class WordsTest {

    private static LocalContainerEntityManagerFactoryBean factory;
    private static EntityManagerFactory emf;

    @BeforeAll
    static void startDatabase() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:words;DB_CLOSE_DELAY=-1", "sa", "");
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceProvider(new HibernatePersistenceProvider());
        factory.setPackagesToScan("io.elasticore.springboot3.mapper.words");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        factory.afterPropertiesSet();
        emf = factory.getObject();

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        WordsMember kim = member(em, 1L, "Kim", null);
        WordsMember lee = member(em, 2L, "Lee", kim);
        post(em, 1L, "Spring tips", "about JPA", kim);
        post(em, 2L, "Kotlin news", "spring and more", lee);
        post(em, 3L, "100% done", "no author here", null);
        post(em, 4L, "100 items", "snake_case", null);
        WordsMember park = member(em, 3L, "Park", lee);
        post(em, 5L, "Gamma alpha", "x", park);
        post(em, 6L, "Delta beta", "y", park);
        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void stopDatabase() {
        factory.destroy();
    }

    private static WordsMember member(EntityManager em, Long id, String nickname, WordsMember mentor) {
        WordsMember m = new WordsMember();
        m.id = id;
        m.nickname = nickname;
        m.mentor = mentor;
        em.persist(m);
        return m;
    }

    private static void post(EntityManager em, Long id, String title, String body, WordsMember author) {
        WordsPost p = new WordsPost();
        p.id = id;
        p.title = title;
        p.body = body;
        p.author = author;
        em.persist(p);
    }

    private static List<Long> posts(String text, String... paths) {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<WordsPost> q = cb.createQuery(WordsPost.class);
            Root<WordsPost> r = q.from(WordsPost.class);
            q.where(Words.match(r, q, cb, text, paths)).orderBy(cb.asc(r.get("id")));
            return em.createQuery(q).getResultList().stream().map(p -> p.id).collect(Collectors.toList());
        } finally {
            em.close();
        }
    }

    private static List<Long> members(String text, String... paths) {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<WordsMember> q = cb.createQuery(WordsMember.class);
            Root<WordsMember> r = q.from(WordsMember.class);
            q.where(Words.match(r, q, cb, text, paths)).orderBy(cb.asc(r.get("id")));
            return em.createQuery(q).getResultList().stream().map(m -> m.id).collect(Collectors.toList());
        } finally {
            em.close();
        }
    }

    @Test
    @DisplayName("each word may be in any field, ignoring case; a row without the relation is still found")
    void anyField() {
        assertThat(posts("spring", "title", "body", "author.nickname")).containsExactly(1L, 2L);
        assertThat(posts("LEE", "title", "body", "author.nickname")).containsExactly(2L);
        assertThat(posts("100", "title", "body", "author.nickname")).containsExactly(3L, 4L);
    }

    @Test
    @DisplayName("every word has to be found")
    void everyWord() {
        assertThat(posts("spring kim", "title", "body", "author.nickname")).containsExactly(1L);
        assertThat(posts("  spring   lee ", "title", "body", "author.nickname")).containsExactly(2L);
        assertThat(posts("spring nobody", "title", "body", "author.nickname")).isEmpty();
    }

    @Test
    @DisplayName("% and _ are characters, not patterns")
    void escaped() {
        assertThat(posts("100%", "title")).containsExactly(3L);
        assertThat(posts("e_c", "body")).containsExactly(4L);
        assertThat(posts("s_c", "body")).isEmpty();
    }

    @Test
    @DisplayName("a text without a word searches by nothing")
    void noWord() {
        assertThat(posts("   ", "title")).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(posts(null, "title")).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    @DisplayName("a path through two relations: each joined once")
    void twoRelations() {
        assertThat(posts("kim", "author.mentor.nickname")).containsExactly(2L);
        assertThat(posts("lee", "author.mentor.nickname", "author.nickname")).containsExactly(2L, 5L, 6L);
    }

    @Test
    @DisplayName("through a collection: each word may be in another member, and the row comes once")
    void throughACollection() {
        assertThat(members("spring", "posts.title", "posts.body")).containsExactly(1L, 2L);
        // both of Park's posts have an a: Park once
        assertThat(members("a", "posts.title")).containsExactly(3L);
        // gamma in one post, delta in the other
        assertThat(members("gamma delta", "posts.title")).containsExactly(3L);
        assertThat(members("gamma delta", "posts.title", "nickname")).containsExactly(3L);
        assertThat(members("park gamma", "posts.title", "nickname")).containsExactly(3L);
        assertThat(members("gamma zzz", "posts.title")).isEmpty();
    }

    @Test
    @DisplayName("through a collection, sorted by a related field: no DISTINCT for the ORDER BY to fall out of")
    void throughACollectionSortedByARelation() {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<WordsMember> q = cb.createQuery(WordsMember.class);
            Root<WordsMember> r = q.from(WordsMember.class);
            q.where(Words.match(r, q, cb, "a", "posts.title", "posts.body"))
                    .orderBy(cb.desc(r.join("mentor", jakarta.persistence.criteria.JoinType.LEFT)
                            .get("nickname")));
            List<Long> ids =
                    em.createQuery(q).getResultList().stream().map(m -> m.id).collect(Collectors.toList());
            // every member has an a in a post; Park's mentor Lee, Lee's Kim, Kim none (last)
            assertThat(ids).containsExactly(3L, 2L, 1L);
        } finally {
            em.close();
        }
    }

    @Test
    @DisplayName("the words: split on white space, each once, the first five")
    void theWords() {
        assertThat(Words.of(" a  b\ta c d e f g ")).containsExactly("a", "b", "c", "d", "e");
        assertThat(Words.of(null)).isEmpty();
        assertThat(Words.of("단어" + (char) 0x3000 + "둘")).containsExactly("단어", "둘");
        assertThat(Words.escaped("a!b%c_d")).isEqualTo("a!!b!%c!_d");
    }
}
