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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
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
 * the call stack. An interface found in both is registered once. The
 * auto-configuration's registrar looks in the application's auto-configuration
 * packages instead of its own (ElastiCoreAutoConfiguration.ApplicationPorts).</p>
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

    private static final Log LOG = LogFactory.getLog(PortServiceRegistrar.class);

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

    /** The bean factory the registrar was handed, for a subclass that reads the application's packages from it. */
    protected BeanFactory getBeanFactory() {
        return beanFactory;
    }

    /** The port interfaces found in some packages: HTTP ({@code @ExternalService}) and DBMS ({@code @DbmsService}). */
    public static final class Ports {
        final Set<Class<?>> http;
        final Set<Class<?>> dbms;
        /** Simple names two or more of these ports share: those ports go under their full names. */
        final Set<String> sharedNames;

        Ports(Set<Class<?>> http, Set<Class<?>> dbms) {
            this.http = Collections.unmodifiableSet(http);
            this.dbms = Collections.unmodifiableSet(dbms);
            Set<String> seen = new HashSet<String>();
            Set<String> shared = new TreeSet<String>();
            for (Class<?> port : http) if (!seen.add(port.getSimpleName())) shared.add(port.getSimpleName());
            for (Class<?> port : dbms) if (!seen.add(port.getSimpleName())) shared.add(port.getSimpleName());
            this.sharedNames = Collections.unmodifiableSet(shared);
        }

        public boolean isEmpty() {
            return http.isEmpty() && dbms.isEmpty();
        }

        @Override
        public String toString() {
            return http.size() + " HTTP and " + dbms.size() + " DBMS ports";
        }
    }

    /*
     * The last scan, so that the auto-configuration's condition and the
     * registrar right after it scan once. One slot, emptied when a registrar
     * has used it: a cache that outlived the refresh would hold the port
     * classes - and through them their class loader - after a restart.
     */
    private static final Object SCAN_LOCK = new Object();
    private static ClassLoader scannedLoader;
    private static Set<String> scannedPackages;
    private static Ports scanned;

    /** The port interfaces in these packages and below them. */
    public static Ports findPorts(Set<String> packages) {
        ClassLoader loader = ClassUtils.getDefaultClassLoader();
        Set<String> key = new LinkedHashSet<String>(packages);
        synchronized (SCAN_LOCK) {
            if (scanned != null && scannedLoader == loader && key.equals(scannedPackages)) return scanned;
        }
        Ports found = scan(key);
        synchronized (SCAN_LOCK) {
            scannedLoader = loader;
            scannedPackages = key;
            scanned = found;
        }
        return found;
    }

    private static void forgetScan() {
        synchronized (SCAN_LOCK) {
            scannedLoader = null;
            scannedPackages = null;
            scanned = null;
        }
    }

    private static Ports scan(Set<String> packages) {
        // By name, so that registration - which port gets which bean name - does
        // not depend on the order the class path happens to be read in.
        Set<Class<?>> http = new TreeSet<Class<?>>(BY_NAME);
        Set<Class<?>> dbms = new TreeSet<Class<?>>(BY_NAME);
        for (String basePackage : packages) {
            Reflections reflections = new Reflections(basePackage);
            // Reflections also answers the subtypes of an annotated type - a
            // class implementing the port - which cannot be proxied.
            for (Class<?> type : reflections.getTypesAnnotatedWith(ExternalService.class)) {
                if (type.isInterface() && type.isAnnotationPresent(ExternalService.class)) http.add(type);
            }
            for (Class<?> type : reflections.getTypesAnnotatedWith(DbmsService.class)) {
                if (type.isInterface() && type.isAnnotationPresent(DbmsService.class)) dbms.add(type);
            }
        }
        return new Ports(http, dbms);
    }

    private static final Comparator<Class<?>> BY_NAME = new Comparator<Class<?>>() {
        @Override
        public int compare(Class<?> a, Class<?> b) {
            return a.getName().compareTo(b.getName());
        }
    };

    /** The package of the @SpringBootApplication class on the call stack, or null. */
    protected String detectMainPackage() {
        return mainApplicationPackage();
    }

    /** The package of the @SpringBootApplication class on the call stack, or null. */
    public static String mainApplicationPackage() {
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

    /**
     * The packages to look for ports in: the importing class's (the
     * {@code @EnableElastiCore} class) and the started application's.
     */
    protected Set<String> packagesToScan(AnnotationMetadata metadata) {
        Set<String> packages = new LinkedHashSet<String>();
        String importing = ClassUtils.getPackageName(metadata.getClassName());
        if (!importing.isEmpty()) packages.add(importing);
        String main = detectMainPackage();
        if (main != null && !main.isEmpty()) packages.add(main);
        return packages;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Ports ports = findPorts(packagesToScan(metadata));
        forgetScan();
        Set<Class<?>> extPortServices = ports.http;
        Set<Class<?>> dbmsServices = ports.dbms;
        for (String shared : ports.sharedNames) {
            LOG.warn("Two or more ports are named " + shared + "; each is registered under its full name"
                    + " (inject it by type, or @Qualifier(\"<package>." + shared + "\")).");
        }

        final BeanFactory factory = this.beanFactory;
        final Environment env = this.environment;
        // port
        for (final Class<?> serviceClass : extPortServices) {
            register(registry, serviceClass, ports, new Supplier<Object>() {
                @Override
                public Object get() {
                    ExternalService externalService = serviceClass.getAnnotation(ExternalService.class);
                    return HttpServiceProxyFactory.createService(serviceClass, externalService, factory, env);
                }
            });
        }
        // dbms
        for (final Class<?> serviceClass : dbmsServices) {
            register(registry, serviceClass, ports, new Supplier<Object>() {
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

    private static boolean registered(BeanDefinitionRegistry registry, String name, Class<?> port) {
        return registry.containsBeanDefinition(name)
                && port.equals(registry.getBeanDefinition(name).getAttribute(PORT_ATTRIBUTE));
    }

    private static void register(BeanDefinitionRegistry registry, Class<?> serviceClass, Ports ports, Supplier<Object> proxy) {
        String simple = serviceClass.getSimpleName();
        String full = serviceClass.getName();
        // Imported twice (two configuration classes carry @EnableElastiCore, or one
        // does and the auto-configuration runs too): the first stands.
        if (registered(registry, simple, serviceClass) || registered(registry, full, serviceClass)) return;

        String name = ports.sharedNames.contains(simple) ? full : simple;
        if (registry.containsBeanDefinition(name)) {
            // The name is another bean's. Overriding it would fail the start (Spring
            // Boot forbids it): this one goes under its full name.
            if (name.equals(full)) {
                LOG.warn("The bean name " + full + " is taken; the port is not registered - declare it as a @Bean.");
                return;
            }
            LOG.warn("The bean name " + simple + " is taken; the port " + full + " is registered under its full name"
                    + " (inject it by type, or @Qualifier(\"" + full + "\")).");
            name = full;
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
