package io.elasticore.springboot3.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.elasticore.springboot.autoconfigure.ElastiCoreCryptoAutoConfiguration;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * The converter under Hibernate, made through Spring's bean container as Spring Boot configures it: Hibernate makes
 * it while the EntityManagerFactory is built, so an application with an encrypted field and without a key stops at
 * its start, naming the property - and with the keys a row is written as ciphertext and read as the value.
 */
public class EncryptConverterHibernateTest {

    /** As the generator writes one: @DynamicUpdate, the listener, and indexEncrypted(). */
    @Entity(name = "CryptoPerson")
    @jakarta.persistence.Table(name = "crypto_person")
    @org.hibernate.annotations.DynamicUpdate
    @jakarta.persistence.EntityListeners(BlindIndexListener.class)
    public static class Person {
        @Id
        public Long id;

        @Column(length = 147)
        @Convert(converter = EncryptConverter.class)
        public String phone;

        @Column(name = "phone_bidx", length = 22)
        public String phoneBidx;

        public String note;

        public void indexEncrypted() {
            this.phoneBidx = Bidx.ofDigits(this.phone);
        }
    }

    @Configuration
    @Import(ElastiCoreCryptoAutoConfiguration.class)
    static class Jpa {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:crypto" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(
                DataSource dataSource, ConfigurableListableBeanFactory beanFactory) throws Exception {
            LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
            emf.setDataSource(dataSource);
            emf.setPackagesToScan(Person.class.getPackage().getName());
            emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Map<String, Object> props = new HashMap<String, Object>();
            props.put("hibernate.hbm2ddl.auto", "create");
            props.put("hibernate.resource.beans.container", beanContainer(beanFactory));
            emf.setJpaPropertyMap(props);
            return emf;
        }
    }

    /** Spring's Hibernate bean container - in another package on Spring 7 than on Spring 6. */
    static Object beanContainer(ConfigurableListableBeanFactory beanFactory) throws Exception {
        for (String name : new String[] {
            "org.springframework.orm.hibernate5.SpringBeanContainer",
            "org.springframework.orm.jpa.hibernate.SpringBeanContainer"
        }) {
            try {
                return Class.forName(name)
                        .getConstructor(ConfigurableListableBeanFactory.class)
                        .newInstance(beanFactory);
            } catch (ClassNotFoundException ex) {
                // the other Spring generation
            }
        }
        throw new IllegalStateException("no SpringBeanContainer");
    }

    @AfterEach
    void forget() {
        FieldCrypto.reset();
    }

    private static AnnotationConfigApplicationContext start(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(Jpa.class);
        context.refresh();
        return context;
    }

    @Test
    public void withoutAKeyTheApplicationDoesNotStart() {
        FieldCrypto.reset();
        Exception ex = assertThrows(
                Exception.class, () -> start(new HashMap<String, Object>()).close());
        StringBuilder messages = new StringBuilder();
        for (Throwable t = ex; t != null; t = t.getCause())
            messages.append(t.getMessage()).append('\n');
        assertTrue(messages.toString().contains("elasticore.crypto.key is not set"), messages.toString());
    }

    @Test
    public void withTheKeysARowIsStoredEncryptedAndReadAsTheValue() {
        Map<String, Object> props = new HashMap<String, Object>(FieldCryptoTest.keys());
        try (AnnotationConfigApplicationContext context = start(props)) {
            EntityManager em = context.getBean(EntityManagerFactory.class).createEntityManager();
            em.getTransaction().begin();
            Person p = new Person();
            p.id = 1L;
            p.phone = "010-1234-5678";
            em.persist(p);
            em.getTransaction().commit();
            em.close();

            String stored = new JdbcTemplate(context.getBean(DataSource.class))
                    .queryForObject("select phone from crypto_person where id = 1", String.class);
            assertTrue(stored.startsWith("v1:"), stored);
            EntityManager read = context.getBean(EntityManagerFactory.class).createEntityManager();
            assertEquals("010-1234-5678", read.find(Person.class, 1L).phone);
            read.close();
        }
    }

    /**
     * The listener writes the index when the row is: on a persist, and on an update that changed the value - set
     * after everything the service did, with @DynamicUpdate writing only the changed columns, the index among them.
     * An update of another column leaves the encrypted text as it was.
     */
    @Test
    public void theListenerIndexesOnEveryWriteOfTheRow() {
        Map<String, Object> props = new HashMap<String, Object>(FieldCryptoTest.keys());
        try (AnnotationConfigApplicationContext context = start(props)) {
            EntityManagerFactory emf = context.getBean(EntityManagerFactory.class);
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            EntityManager em = emf.createEntityManager();
            em.getTransaction().begin();
            Person p = new Person();
            p.id = 2L;
            p.phone = "010-1111-2222";
            em.persist(p);
            em.getTransaction().commit();
            em.close();
            assertEquals(
                    Bidx.ofDigits("01011112222"),
                    jdbc.queryForObject("select phone_bidx from crypto_person where id = 2", String.class));

            em = emf.createEntityManager();
            em.getTransaction().begin();
            em.find(Person.class, 2L).phone = "010-3333-4444";
            em.getTransaction().commit();
            em.close();
            assertEquals(
                    Bidx.ofDigits("01033334444"),
                    jdbc.queryForObject("select phone_bidx from crypto_person where id = 2", String.class));

            String text = jdbc.queryForObject("select phone from crypto_person where id = 2", String.class);
            em = emf.createEntityManager();
            em.getTransaction().begin();
            em.find(Person.class, 2L).note = "other column";
            em.getTransaction().commit();
            em.close();
            assertEquals(text, jdbc.queryForObject("select phone from crypto_person where id = 2", String.class));
        }
    }
}
