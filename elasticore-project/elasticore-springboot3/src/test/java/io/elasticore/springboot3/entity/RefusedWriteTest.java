package io.elasticore.springboot3.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.elasticore.springboot3.entity.refused.RfLine;
import io.elasticore.springboot3.entity.refused.RfOrder;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A refused write leaves no trace in what its transaction reads (issue 344): the row the request was mapped
 * onto is read again, with the members it cascades to - or let go when it cannot be. Hibernate on H2, under
 * Spring transactions.
 */
@DisplayName("RefusedWrite: the row a refused write changed is put back as stored")
class RefusedWriteTest {

    private static LocalContainerEntityManagerFactoryBean factory;
    private static EntityManager em;
    private static TransactionTemplate tx;
    private static long nextId = 100;

    @BeforeAll
    static void start() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:refusedwrite;DB_CLOSE_DELAY=-1", "sa", "");
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceProvider(new HibernatePersistenceProvider());
        factory.setPackagesToScan("io.elasticore.springboot3.entity.refused");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        factory.afterPropertiesSet();
        EntityManagerFactory emf = factory.getObject();
        em = SharedEntityManagerCreator.createSharedEntityManager(emf);
        tx = new TransactionTemplate(new JpaTransactionManager(emf));
    }

    @AfterAll
    static void stop() {
        factory.destroy();
    }

    /** An order "stored" with one line of quantity 1, committed. */
    private static long order() {
        long id = nextId++;
        long lineId = nextId++;
        tx.executeWithoutResult(s -> {
            RfOrder o = new RfOrder();
            o.id = id;
            o.memo = "stored";
            RfLine line = new RfLine();
            line.id = lineId;
            line.qty = 1;
            line.order = o;
            o.lines.add(line);
            em.persist(o);
        });
        return id;
    }

    private static String memoInTheTable(long id) {
        return em.createQuery("select o.memo from RfOrder o where o.id = :id", String.class)
                .setParameter("id", id)
                .getSingleResult();
    }

    @Test
    @DisplayName("without it, the transaction reads the refused value - and its next query writes it")
    void withoutItTheRefusedValueStays() {
        long id = order();
        tx.executeWithoutResult(s -> {
            RfOrder o = em.find(RfOrder.class, id);
            o.memo = "REFUSED";
            // The refusal: nothing is saved, the change stays in the managed entity.
            assertThat(em.find(RfOrder.class, id).memo).isEqualTo("REFUSED");
            assertThat(memoInTheTable(id)).as("AUTO flush").isEqualTo("REFUSED");
            s.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("the row and the member it changed are read again: the same object, as stored")
    void theRowIsReadAgain() {
        long id = order();
        tx.executeWithoutResult(s -> {
            RfOrder o = em.find(RfOrder.class, id);
            o.memo = "REFUSED";
            o.lines.get(0).qty = 9;
            RuntimeException refused = new IllegalStateException("refused");

            assertThat(RefusedWrite.discard(em, o, refused)).isSameAs(refused);

            RfOrder again = em.find(RfOrder.class, id);
            assertThat(again).isSameAs(o);
            assertThat(again.memo).isEqualTo("stored");
            assertThat(again.lines).hasSize(1);
            assertThat(again.lines.get(0).qty).isEqualTo(1);
            assertThat(memoInTheTable(id)).isEqualTo("stored");
            s.setRollbackOnly();
        });
        tx.executeWithoutResult(
                s -> assertThat(em.find(RfOrder.class, id).version).isEqualTo(0L));
    }

    @Test
    @DisplayName("a member the request added, never written: the row is let go, and read afresh")
    void aRowThatCannotBeReadAgainIsLetGo() {
        long id = order();
        tx.executeWithoutResult(s -> {
            RfOrder o = em.find(RfOrder.class, id);
            o.memo = "REFUSED";
            RfLine added = new RfLine();
            added.id = nextId++;
            added.qty = 5;
            added.order = o;
            o.lines.add(added);

            RefusedWrite.discard(em, o, new IllegalStateException("refused"));

            assertThat(em.contains(o)).isFalse();
            RfOrder again = em.find(RfOrder.class, id);
            assertThat(again.memo).isEqualTo("stored");
            assertThat(again.lines).hasSize(1);
            assertThat(memoInTheTable(id)).isEqualTo("stored");
            s.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("nothing to do without an entity manager, a row, or for a row never saved")
    void nothingToDo() {
        RuntimeException refused = new IllegalStateException("refused");
        assertThat(RefusedWrite.discard(null, new RfOrder(), refused)).isSameAs(refused);
        tx.executeWithoutResult(s -> {
            assertThat(RefusedWrite.discard(em, null, refused)).isSameAs(refused);
            RfOrder fresh = new RfOrder();
            fresh.id = nextId++;
            assertThat(RefusedWrite.discard(em, fresh, refused)).isSameAs(refused);
            assertThat(em.contains(fresh)).isFalse();
        });
    }
}
