package io.elasticore.springboot3.bean;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/**
 * The executor a DBMS port's SQL runs on, for @EnableElastiCore: JPA's
 * ({@link DbTransactionGateway}) in an application with Hibernate - the bean
 * the annotation always registered, under the same name - and JDBC's
 * ({@link JdbcDbmsGateway}) in one without, a MyBatis one.
 */
public class DbmsGatewayRegistrar implements ImportBeanDefinitionRegistrar, BeanClassLoaderAware {

    private ClassLoader classLoader = DbmsGatewayRegistrar.class.getClassLoader();

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /** Whether the application has JPA: Spring's JPA support and Hibernate. */
    static boolean jpa(ClassLoader classLoader) {
        return ClassUtils.isPresent("org.springframework.orm.jpa.EntityManagerFactoryUtils", classLoader)
                && ClassUtils.isPresent("org.hibernate.Session", classLoader);
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Class<?> type = jpa(classLoader) ? DbTransactionGateway.class : JdbcDbmsGateway.class;
        String name = type.getName();
        if (registry.containsBeanDefinition(name)) return;
        RootBeanDefinition definition = new RootBeanDefinition(type);
        definition.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
        registry.registerBeanDefinition(name, definition);
    }
}
