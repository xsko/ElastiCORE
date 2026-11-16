package io.elasticore.springboot3.graphql;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.lang.reflect.Method;

/**
 * What the generated GraphQL controllers need from the running application:
 * the scalars the schema files declare, the errors the services' refusals
 * become, and arguments bound the way the REST API binds a body.
 *
 * Every generated controller imports it, so it is there exactly when one is,
 * and once however many domains turn GraphQL on. The endpoint path the
 * domains' env.yml name is in graphql/elasticore-graphql.properties, read
 * below the application's own properties - an application.yml that sets
 * spring.graphql.path still decides.
 */
@Configuration(proxyBeanMethods = false)
@PropertySource(value = "classpath:graphql/elasticore-graphql.properties", ignoreResourceNotFound = true)
public class ElastiCoreGraphQlConfiguration {

    @Bean
    public RuntimeWiringConfigurer elastiCoreGraphQlScalars() {
        return wiring -> GraphQlScalars.all().forEach(wiring::scalar);
    }

    @Bean
    public DataFetcherExceptionResolver elastiCoreGraphQlErrors() {
        return new GraphQlErrors();
    }

    /**
     * A value object with getters and no setters (an embedded Address) is
     * read from JSON into its fields by Jackson; Spring for GraphQL binds an
     * argument through setters unless it is told to fall back on the fields.
     * Told so before the configurer is initialised; a version without the
     * switch is left as it is.
     */
    @Bean
    public static BeanPostProcessor elastiCoreGraphQlFieldBinding() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean.getClass()
                        .getName()
                        .equals(
                                "org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer")) {
                    fallBackOnFields(bean);
                }
                return bean;
            }
        };
    }

    static void fallBackOnFields(Object configurer) {
        try {
            Method set = configurer.getClass().getMethod("setFallBackOnDirectFieldAccess", boolean.class);
            set.invoke(configurer, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // An older Spring for GraphQL binds through setters only.
        }
    }
}
