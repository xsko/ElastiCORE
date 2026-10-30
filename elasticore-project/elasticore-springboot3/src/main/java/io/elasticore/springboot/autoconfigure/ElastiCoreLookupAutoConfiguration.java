package io.elasticore.springboot.autoconfigure;

import io.elasticore.springboot3.domain.Lookup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.stream.Collectors;

/**
 * The Lookup a service hands an aggregate to check and read what it refers to
 * by identity, where there is no EntityManager to find a row: the generated
 * MyBatis repository adapters are each a Lookup.Source of their root, and
 * this asks the one of the type it is given.
 *
 * Registered only where there are sources, and only where the application
 * has no Lookup of its own.
 */
@AutoConfiguration
@ConditionalOnBean(Lookup.Source.class)
public class ElastiCoreLookupAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Lookup.class)
    public Lookup elastiCoreLookup(ObjectProvider<Lookup.Source> sources) {
        // Read when first asked, not when built: the adapters are created after this bean.
        return new Lookup() {
            private volatile Lookup built;

            @Override
            public Object find(Class<?> type, Object id) {
                Lookup l = built;
                if (l == null) built = l = Lookup.over(sources.orderedStream().collect(Collectors.toList()));
                return l.find(type, id);
            }
        };
    }
}
