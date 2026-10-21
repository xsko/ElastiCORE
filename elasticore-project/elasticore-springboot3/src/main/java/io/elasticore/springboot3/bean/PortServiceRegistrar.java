package io.elasticore.springboot3.bean;

import io.elasticore.runtime.port.DbmsService;
import io.elasticore.runtime.port.ExternalService;
import io.elasticore.springboot3.dbms.DbmsServiceProxyFactory;
import io.elasticore.springboot3.http.HttpServiceProxyFactory;
import org.reflections.Reflections;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Bean definition registrar that automatically discovers and registers port service interfaces.
 * This registrar scans for interfaces annotated with @ExternalService and @DbmsService
 * and creates proxy beans for dependency injection.
 *
 * <p>The registrar creates dynamic proxies that handle method invocations by:</p>
 * <ul>
 *   <li>Converting @ExternalService interfaces to HTTP client proxies</li>
 *   <li>Converting @DbmsService interfaces to database query proxies</li>
 * </ul>
 *
 * <p>Where it looks: the package of the class that carries @EnableElastiCore -
 * the one a @SpringBootTest starts too - and the package of the class
 * annotated {@code @SpringBootApplication} that started the application, when it is on
 * the call stack. An interface found in both is registered once.</p>
 *
 * <p>A bean the application declares for a port itself (a hand-written @Bean,
 * as tests had to before the registrar looked at the @EnableElastiCore
 * class) is the one used: the registrar's proxy for that interface is dropped -
 * unless one of the application's beans is @Primary (a wrapper around the
 * proxy, say), in which case there is no ambiguity and the proxy stays,
 * under its name.</p>
 *
 * <p>Only the annotated interfaces themselves are ports: a class that
 * implements one (a fake, a wrapper) is not made a proxy.</p>
 *
 * @author ElastiCore Framework
 * @since 1.0
 */
public class PortServiceRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware, BeanFactoryAware {

    /** Marks a definition this registrar made, with the port interface as its value. */
    static final String PORT_ATTRIBUTE = PortServiceRegistrar.class.getName() + ".port";

    private static final String DECLARED_BEANS_WIN = PortServiceRegistrar.class.getName() + ".declaredBeansWin";

    private Environment environment;

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /** The package of the @SpringBootApplication class on the call stack, or null. */
    private String detectMainPackage() {
        ClassLoader loader = ClassUtils.getDefaultClassLoader();
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            try {
                Class<?> cls = Class.forName(element.getClassName(), false, loader);
                if (cls.isAnnotationPresent(SpringBootApplication.class)) {
                    return ClassUtils.getPackageName(cls);
                }
            } catch (ClassNotFoundException | LinkageError notLoadable) {
                // A frame of a class this loader cannot see is not the application's.
            }
        }
        return null;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Set<String> packages = new LinkedHashSet<String>();
        String importing = ClassUtils.getPackageName(metadata.getClassName());
        if (!importing.isEmpty()) packages.add(importing);
        String main = detectMainPackage();
        if (main != null && !main.isEmpty()) packages.add(main);

        Set<Class<?>> extPortServices = new LinkedHashSet<Class<?>>();
        Set<Class<?>> dbmsServices = new LinkedHashSet<Class<?>>();
        for (String basePackage : packages) {
            Reflections reflections = new Reflections(basePackage);
            // Reflections also answers the subtypes of an annotated type - a
            // class implementing the port - which cannot be proxied.
            for (Class<?> type : reflections.getTypesAnnotatedWith(ExternalService.class)) {
                if (type.isInterface() && type.isAnnotationPresent(ExternalService.class)) extPortServices.add(type);
            }
            for (Class<?> type : reflections.getTypesAnnotatedWith(DbmsService.class)) {
                if (type.isInterface() && type.isAnnotationPresent(DbmsService.class)) dbmsServices.add(type);
            }
        }

        final BeanFactory factory = this.beanFactory;
        final Environment env = this.environment;
        // port
        for (final Class<?> serviceClass : extPortServices) {
            register(registry, serviceClass, new Supplier<Object>() {
                @Override
                public Object get() {
                    ExternalService externalService = serviceClass.getAnnotation(ExternalService.class);
                    return HttpServiceProxyFactory.createService(serviceClass, externalService, factory, env);
                }
            });
        }
        // dbms
        for (final Class<?> serviceClass : dbmsServices) {
            register(registry, serviceClass, new Supplier<Object>() {
                @Override
                public Object get() {
                    DbmsService dbmsService = serviceClass.getAnnotation(DbmsService.class);
                    return DbmsServiceProxyFactory.createService(serviceClass, dbmsService, factory, env);
                }
            });
        }

        if ((!extPortServices.isEmpty() || !dbmsServices.isEmpty()) && !registry.containsBeanDefinition(DECLARED_BEANS_WIN)) {
            RootBeanDefinition winner = new RootBeanDefinition(DeclaredBeansWin.class);
            winner.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
            registry.registerBeanDefinition(DECLARED_BEANS_WIN, winner);
        }
    }

    private static void register(BeanDefinitionRegistry registry, Class<?> serviceClass, Supplier<Object> proxy) {
        String name = serviceClass.getSimpleName();
        if (registry.containsBeanDefinition(name)) {
            // Imported twice (two configuration classes carry @EnableElastiCore): the first stands.
            Object marked = registry.getBeanDefinition(name).getAttribute(PORT_ATTRIBUTE);
            if (serviceClass.equals(marked)) return;
        }
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(serviceClass);
        definition.setInstanceSupplier(proxy);
        definition.setAttribute(PORT_ATTRIBUTE, serviceClass);
        registry.registerBeanDefinition(name, definition);
    }

    /**
     * Runs once every configuration class has declared its beans: a port
     * interface the application declared a bean for keeps that bean alone.
     */
    public static final class DeclaredBeansWin implements BeanDefinitionRegistryPostProcessor {

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            if (!(registry instanceof ListableBeanFactory)) return;
            ListableBeanFactory beans = (ListableBeanFactory) registry;
            for (String name : registry.getBeanDefinitionNames()) {
                if (!registry.containsBeanDefinition(name)) continue;
                Object port = registry.getBeanDefinition(name).getAttribute(PORT_ATTRIBUTE);
                if (!(port instanceof Class)) continue;
                boolean declared = false;
                boolean primary = false;
                for (String other : beans.getBeanNamesForType((Class<?>) port, true, false)) {
                    if (other.equals(name)) continue;
                    declared = true;
                    if (registry.containsBeanDefinition(other) && registry.getBeanDefinition(other).isPrimary()) {
                        primary = true;
                    }
                }
                // A @Primary bean of the application's - one that wraps the
                // proxy, typically - already decides injection by type, and
                // may need the proxy itself (@Qualifier("CourierPort")): kept.
                if (declared && !primary) registry.removeBeanDefinition(name);
            }
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        }
    }
}
