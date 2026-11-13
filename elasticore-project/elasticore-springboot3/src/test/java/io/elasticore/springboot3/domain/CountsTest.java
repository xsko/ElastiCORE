package io.elasticore.springboot3.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.elasticore.springboot3.domain.counts.CtrItem;
import io.elasticore.springboot3.domain.counts.CtrPost;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** {@code Counts} against H2 under Spring transactions: counted once, before the commit, wherever touched. */
@DisplayName("Counts: a count counted again before the commit")
class CountsTest {

    private static final String LOCK = "select x from CtrPost x where x.id = :id";
    private static final String COUNT =
            "update CtrPost x set x.items = (select count(c) from CtrItem c where c.postId = x.id) where x.id = :id";

    private static LocalContainerEntityManagerFactoryBean factory;
    private static EntityManager em;
    private static TransactionTemplate tx;
    private static TransactionTemplate newTx;
    private static long nextId = 100;

    @BeforeAll
    static void start() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:counts;DB_CLOSE_DELAY=-1", "sa", "");
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceProvider(new HibernatePersistenceProvider());
        factory.setPackagesToScan("io.elasticore.springboot3.domain.counts");
        factory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto",
                "create-drop",
                "hibernate.session_factory.statement_inspector",
                io.elasticore.springboot3.domain.counts.SqlSeen.class.getName()));
        factory.afterPropertiesSet();
        EntityManagerFactory emf = factory.getObject();
        em = SharedEntityManagerCreator.createSharedEntityManager(emf);
        JpaTransactionManager manager = new JpaTransactionManager(emf);
        tx = new TransactionTemplate(manager);
        newTx = new TransactionTemplate(manager);
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @AfterAll
    static void stop() {
        factory.destroy();
    }

    private static long post() {
        long id = nextId++;
        tx.executeWithoutResult(s -> {
            CtrPost p = new CtrPost();
            p.id = id;
            em.persist(p);
        });
        return id;
    }

    private static void item(long postId) {
        CtrItem i = new CtrItem();
        i.id = nextId++;
        i.postId = postId;
        em.persist(i);
        Counts.touch(em, LOCK, COUNT, postId);
    }

    private static long items(long postId) {
        return tx.execute(s -> em.find(CtrPost.class, postId).items);
    }

    @Test
    @DisplayName("touched twice in a transaction, counted once at the commit: what is there")
    void countedAtTheCommit() {
        long post = post();
        tx.executeWithoutResult(s -> {
            item(post);
            item(post);
        });
        assertThat(items(post)).isEqualTo(2);
    }

    @Test
    @DisplayName("the counting row is locked (for update) before it is counted, by a statement of its own")
    void lockedThenCounted() {
        long post = post();
        io.elasticore.springboot3.domain.counts.SqlSeen.SQL.clear();
        tx.executeWithoutResult(s -> item(post));
        java.util.List<String> sql =
                new java.util.ArrayList<String>(io.elasticore.springboot3.domain.counts.SqlSeen.SQL);
        int lock = -1;
        int count = -1;
        for (int i = 0; i < sql.size(); i++) {
            String each = sql.get(i).toLowerCase(java.util.Locale.ROOT);
            if (lock < 0 && each.contains("ctrpost") && each.contains("for update")) lock = i;
            if (count < 0 && each.startsWith("update ctrpost")) count = i;
        }
        assertThat(lock).as(sql.toString()).isGreaterThanOrEqualTo(0);
        assertThat(count).as(sql.toString()).isGreaterThan(lock);
        assertThat(items(post)).isEqualTo(1);
    }

    @Test
    @DisplayName("an after-commit handler that writes in a REQUIRES_NEW transaction counts what it wrote")
    void afterCommitRequiresNew() {
        long first = post();
        long second = post();
        tx.executeWithoutResult(s -> {
            item(first);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    newTx.executeWithoutResult(s2 -> item(second));
                }
            });
        });
        assertThat(items(first)).isEqualTo(1);
        assertThat(items(second)).isEqualTo(1);
    }

    @Test
    @DisplayName("a REQUIRES_NEW transaction inside one that touched a count counts its own")
    void requiresNew() {
        long outer = post();
        long inner = post();
        tx.executeWithoutResult(s -> {
            item(outer);
            newTx.executeWithoutResult(s2 -> item(inner));
            // committed already, before the outer one
            Long committed = newTx.execute(s3 -> em.find(CtrPost.class, inner).items);
            assertThat(committed).isEqualTo(1L);
        });
        assertThat(items(outer)).isEqualTo(1);
        assertThat(items(inner)).isEqualTo(1);
    }

    @Test
    @DisplayName("a row written by a before-commit handler after the counts ran is counted too")
    void touchedWhileCounting() {
        long post = post();
        tx.executeWithoutResult(s -> {
            item(post);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    item(post);
                }
            });
        });
        assertThat(items(post)).isEqualTo(2);
    }

    @Test
    @DisplayName("a row read at the commit: its reference as it is then")
    void readAtTheCommit() {
        long first = post();
        long second = post();
        tx.executeWithoutResult(s -> {
            CtrItem i = new CtrItem();
            i.id = nextId++;
            i.postId = first;
            em.persist(i);
            Counts.touchLater(em, LOCK, COUNT, i, r -> r.postId);
            i.postId = second; // moved by a listener, a beforeSave, after the touch
        });
        assertThat(items(first)).isZero();
        assertThat(items(second)).isEqualTo(1);
    }

    @Test
    @DisplayName("outside a transaction: counted at once")
    void outsideATransaction() {
        long post = post();
        tx.executeWithoutResult(s -> {
            CtrItem i = new CtrItem();
            i.id = nextId++;
            i.postId = post;
            em.persist(i);
        });
        tx.executeWithoutResult(s -> Counts.touch(em, LOCK, COUNT, null)); // nothing to count
        tx.executeWithoutResult(
                s -> em.createQuery(COUNT).setParameter("id", post).executeUpdate());
        assertThat(items(post)).isEqualTo(1);
    }
}
