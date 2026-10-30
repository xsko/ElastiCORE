package io.elasticore.springboot.autoconfigure;

import io.elasticore.springboot3.bean.ApplicationContextProvider;
import io.elasticore.springboot3.bean.DbTransactionGateway;
import io.elasticore.springboot3.bean.ElastiCoreBannerPrinter;
import io.elasticore.springboot3.bean.PortServiceRegistrar;
import io.elasticore.springboot3.dbms.DbmsSqlExecutor;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What {@code @EnableElastiCore} sets up, without it: Spring Boot loads this
 * class when the runtime library is on the class path
 * (META-INF/spring/...AutoConfiguration.imports), and it does something only
 * where the application has ports - the {@code @ExternalService} and
 * {@code @DbmsService} interfaces generated from the port DSL - in its
 * packages: the package of its {@code @SpringBootApplication} class and below,
 * where Spring Boot finds entities and repositories too.
 *
 * <p>Then it registers:</p>
 * <ul>
 *   <li>the port proxies;</li>
 *   <li>the executor a DBMS port runs its SQL with ({@link DbTransactionGateway}),
 *       when the application has one EntityManagerFactory (or a primary one);</li>
 *   <li>{@link ApplicationContextProvider}, through which a proxy finds it;</li>
 *   <li>the version banner (off with {@code elasticore.banner.enabled=false}).</li>
 * </ul>
 *
 * <p>Each piece the application already has - from {@code @EnableElastiCore},
 * a component scan of {@code io.elasticore.springboot3} or a {@code @Bean} of
 * its own - is the one used: a port is registered once, and a declared bean
 * replaces the proxy as before. {@code elasticore.autoconfigure.enabled=false}
 * switches all of it off; {@code @EnableElastiCore} still works then.</p>
 *
 * <p>Its package is outside {@code io.elasticore.springboot3} on purpose: a
 * project's {@code @ComponentScan("io.elasticore.springboot3")} would otherwise
 * read it as one of the project's own configurations, before the application's
 * packages are known.</p>
 *
 * <p>Ordered after the JPA auto-configuration - its Spring Boot 3 and its Spring
 * Boot 4 class - so that the EntityManagerFactory condition sees the factory.</p>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        // and the DataSource, which an application without JPA runs its ports on
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"})
@ConditionalOnProperty(prefix = "elasticore.autoconfigure", name = "enabled", havingValue = "true", matchIfMissing = true)
@Conditional(ElastiCoreAutoConfiguration.OnApplicationPorts.class)
@Import(ElastiCoreAutoConfiguration.ApplicationPorts.class)
public class ElastiCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApplicationContextProvider elastiCoreApplicationContextProvider() {
        return new ApplicationContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public ElastiCoreBannerPrinter elastiCoreBannerPrinter() {
        return new ElastiCoreBannerPrinter();
    }

    /** The DBMS ports' executor, where there is a database to run their SQL on. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(EntityManagerFactory.class)
    public static class DbmsPorts {

        @Bean
        @ConditionalOnSingleCandidate(EntityManagerFactory.class)
        @ConditionalOnMissingBean(DbmsSqlExecutor.class)
        public DbTransactionGateway elastiCoreDbTransactionGateway(EntityManagerFactory entityManagerFactory)
                throws IOException {
            return new DbTransactionGateway(entityManagerFactory);
        }
    }

    /**
     * The DBMS ports' executor in an application without JPA - a MyBatis one:
     * their SQL through JDBC, on the application's DataSource.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate")
    @ConditionalOnMissingBean(type = "jakarta.persistence.EntityManagerFactory")
    public static class JdbcDbmsPorts {

        @Bean
        @ConditionalOnSingleCandidate(javax.sql.DataSource.class)
        @ConditionalOnMissingBean(DbmsSqlExecutor.class)
        public io.elasticore.springboot3.bean.JdbcDbmsGateway elastiCoreJdbcDbmsGateway(javax.sql.DataSource dataSource) {
            return new io.elasticore.springboot3.bean.JdbcDbmsGateway(dataSource);
        }
    }

    /** Where the application's ports are looked for: its auto-configuration packages and the started class's. */
    static Set<String> applicationPackages(BeanFactory beanFactory) {
        Set<String> packages = new LinkedHashSet<String>();
        if (beanFactory != null && AutoConfigurationPackages.has(beanFactory)) {
            for (String name : AutoConfigurationPackages.get(beanFactory)) {
                if (!name.isEmpty()) packages.add(name);
            }
        }
        String main = PortServiceRegistrar.mainApplicationPackage();
        if (main != null && !main.isEmpty()) packages.add(main);
        return packages;
    }

    /**
     * True when the application's packages hold a port. Decided when the bean
     * definitions are registered - by then the application class has said
     * which its packages are; the scan is kept for the registrar below.
     */
    public static class OnApplicationPorts extends SpringBootCondition implements ConfigurationCondition {

        @Override
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;
        }

        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Set<String> packages = applicationPackages(context.getBeanFactory());
            if (packages.isEmpty()) {
                return ConditionOutcome.noMatch("no application package to look for ports in");
            }
            PortServiceRegistrar.Ports ports = PortServiceRegistrar.findPorts(packages);
            if (ports.isEmpty()) {
                return ConditionOutcome.noMatch("no @ExternalService or @DbmsService interface in " + packages);
            }
            return ConditionOutcome.match(ports + " in " + packages);
        }
    }

    /**
     * The port registrar, looking where Spring Boot looks for the application's
     * own components rather than in the package of the class that imports it
     * (this one, in the runtime library).
     */
    public static class ApplicationPorts extends PortServiceRegistrar {

        @Override
        protected Set<String> packagesToScan(AnnotationMetadata metadata) {
            return applicationPackages(getBeanFactory());
        }
    }
}
