package io.elasticore.springboot.autoconfigure;

import io.elasticore.springboot3.domain.Lookup;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;

import java.util.stream.Collectors;

/**
 * The Lookup a service hands an aggregate to check and read what it refers to
 * by identity, where there is no EntityManager to find a row: the generated
 * MyBatis repository adapters are each a Lookup.Source of their root, and
 * this asks the one of the type it is given. A type none of them serves - a
 * root of a domain kept on JPA - is found by the EntityManager, where the
 * application has one.
 *
 * Registered only where there are sources, and only where the application
 * has no Lookup of its own.
 */
@AutoConfiguration
@ConditionalOnBean(Lookup.Source.class)
public class ElastiCoreLookupAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Lookup.class)
    public Lookup elastiCoreLookup(ObjectProvider<Lookup.Source> sources, ListableBeanFactory beans) {
        // Read when first asked, not when built: the adapters are created after this bean.
        return new Lookup() {
            private volatile Lookup built;

            @Override
            public Object find(Class<?> type, Object id) {
                Lookup l = built;
                if (l == null) {
                    built = l = Lookup.over(sources.orderedStream().collect(Collectors.toList()), jpa(beans));
                }
                return l.find(type, id);
            }
        };
    }

    /** The EntityManager's find, where JPA is there; null otherwise. */
    private static Lookup jpa(ListableBeanFactory beans) {
        ClassLoader loader = ElastiCoreLookupAutoConfiguration.class.getClassLoader();
        if (!ClassUtils.isPresent("jakarta.persistence.EntityManagerFactory", loader)
                || !ClassUtils.isPresent("org.springframework.orm.jpa.SharedEntityManagerCreator", loader)) return null;
        return JpaFind.of(beans);
    }

    /** Apart, so a MyBatis-only application never loads a JPA type. */
    private static final class JpaFind {

        static Lookup of(ListableBeanFactory beans) {
            jakarta.persistence.EntityManagerFactory emf =
                    beans.getBeanProvider(jakarta.persistence.EntityManagerFactory.class).getIfUnique();
            if (emf == null) return null;
            jakarta.persistence.EntityManager em =
                    org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(emf);
            return (type, id) -> em.find(type, id);
        }
    }
}
