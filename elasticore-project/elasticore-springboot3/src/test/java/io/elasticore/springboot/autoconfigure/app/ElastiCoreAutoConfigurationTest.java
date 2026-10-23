package io.elasticore.springboot.autoconfigure.app;

import io.elasticore.runtime.port.DbmsService;
import io.elasticore.runtime.port.ExternalService;
import io.elasticore.springboot.autoconfigure.ElastiCoreAutoConfiguration;
import io.elasticore.springboot.autoconfigure.noports.NoPortsApp;
import io.elasticore.springboot3.EnableElastiCore;
import io.elasticore.springboot3.bean.ApplicationContextProvider;
import io.elasticore.springboot3.bean.ElastiCoreBannerPrinter;
import io.elasticore.springboot3.dbms.DbmsSqlExecutor;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Spring Boot application gets its ports - HTTP and DBMS - and the runtime
 * pieces they need without @EnableElastiCore: the runtime library's
 * auto-configuration finds them in the application's package, as Spring Boot
 * finds its entities. What an application already sets up itself - the
 * annotation, a component scan of the runtime, a bean of its own - is the one
 * used, once. An application without ports gets nothing new.
 */
public class ElastiCoreAutoConfigurationTest {

    @ExternalService(protocol = "http", id = "test.CourierPort", url = "http://localhost:1")
    public interface CourierPort {
        String ship(String parcel);
    }

    @DbmsService(id = "test.ReportPort", sqlSource = "")
    public interface ReportPort {
        Long count();
    }

    /** An application class: @SpringBootApplication marks its package the same way. */
    @Configuration
    @AutoConfigurationPackage
    public static class App {
    }

    @Configuration
    @AutoConfigurationPackage
    public static class AppWithADatabase {
        @Bean
        EntityManagerFactory entityManagerFactory() {
            return Mockito.mock(EntityManagerFactory.class);
        }
    }

    /** Two datasources, neither primary - each wired to its own repositories. */
    @Configuration
    @AutoConfigurationPackage
    public static class AppWithTwoDatabases {
        @Bean
        EntityManagerFactory orderEntityManagerFactory() {
            return Mockito.mock(EntityManagerFactory.class);
        }

        @Bean
        EntityManagerFactory reportEntityManagerFactory() {
            return Mockito.mock(EntityManagerFactory.class);
        }
    }

    @Configuration
    @AutoConfigurationPackage
    public static class AppWithAPrimaryDatabase {
        @Bean
        @Primary
        EntityManagerFactory orderEntityManagerFactory() {
            return Mockito.mock(EntityManagerFactory.class);
        }

        @Bean
        EntityManagerFactory reportEntityManagerFactory() {
            return Mockito.mock(EntityManagerFactory.class);
        }
    }

    /** As every application had to be written before. */
    @Configuration
    @AutoConfigurationPackage
    @EnableElastiCore
    public static class AnnotatedApp {
        @Bean
        EntityManagerFactory entityManagerFactory() {
            return Mockito.mock(EntityManagerFactory.class);
        }
    }

    /**
     * The other way projects registered the runtime pieces: a component scan of
     * the library, default filters and all. (This test source tree's own
     * configurations under that package are left out.)
     */
    @Configuration
    @AutoConfigurationPackage
    @ComponentScan(basePackages = "io.elasticore.springboot3",
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "io\\.elasticore\\.springboot3\\..*Test(\\$.*)?"))
    public static class ScanningApp {
        @Bean
        EntityManagerFactory entityManagerFactory() {
            return Mockito.mock(EntityManagerFactory.class);
        }
    }

    @Configuration
    @AutoConfigurationPackage
    public static class AppWithItsOwnBeans {
        static final CourierPort HAND = parcel -> "hand:" + parcel;
        static final DbmsSqlExecutor EXECUTOR = Mockito.mock(DbmsSqlExecutor.class);

        @Bean
        CourierPort courierPort() {
            return HAND;
        }

        @Bean
        DbmsSqlExecutor executor() {
            return EXECUTOR;
        }

        @Bean
        EntityManagerFactory entityManagerFactory() {
            return Mockito.mock(EntityManagerFactory.class);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ElastiCoreAutoConfiguration.class));

    private static void started(org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertNull(context.getStartupFailure(), String.valueOf(context.getStartupFailure()));
    }

    @Test
    public void portsAreBeansWithoutTheAnnotation() {
        runner.withUserConfiguration(App.class).run(context -> {
            started(context);
            CourierPort courier = context.getBean(CourierPort.class);
            assertTrue(Proxy.isProxyClass(courier.getClass()), "the port runtime's HTTP proxy");
            assertEquals(1, context.getBeanNamesForType(ReportPort.class).length, "the DBMS port");
            assertEquals(1, context.getBeanNamesForType(ApplicationContextProvider.class).length);
            assertEquals(1, context.getBeanNamesForType(ElastiCoreBannerPrinter.class).length);
        });
    }

    @Test
    public void anApplicationWithoutPortsGetsNothing() {
        runner.withUserConfiguration(NoPortsApp.class).run(context -> {
            started(context);
            assertEquals(0, context.getBeanNamesForType(ApplicationContextProvider.class).length);
            assertEquals(0, context.getBeanNamesForType(ElastiCoreBannerPrinter.class).length);
            assertEquals(0, context.getBeanNamesForType(DbmsSqlExecutor.class).length);
        });
    }

    @Test
    public void theDbmsExecutorNeedsOneDatabase() {
        runner.withUserConfiguration(App.class).run(context -> {
            started(context);
            assertEquals(0, context.getBeanNamesForType(DbmsSqlExecutor.class).length, "no EntityManagerFactory");
        });
        runner.withUserConfiguration(AppWithADatabase.class).run(context -> {
            started(context);
            assertEquals(1, context.getBeanNamesForType(DbmsSqlExecutor.class).length);
        });
        runner.withUserConfiguration(AppWithTwoDatabases.class).run(context -> {
            started(context);
            assertEquals(0, context.getBeanNamesForType(DbmsSqlExecutor.class).length,
                    "two factories, neither primary: which one is not ours to guess - and the start does not fail");
        });
        runner.withUserConfiguration(AppWithAPrimaryDatabase.class).run(context -> {
            started(context);
            assertEquals(1, context.getBeanNamesForType(DbmsSqlExecutor.class).length, "the primary one");
        });
    }

    @Test
    public void theAnnotationAndTheAutoConfigurationTogetherRegisterEachPieceOnce() {
        runner.withUserConfiguration(AnnotatedApp.class).run(context -> {
            started(context);
            assertEquals(1, context.getBeanNamesForType(CourierPort.class).length);
            assertEquals(1, context.getBeanNamesForType(ReportPort.class).length);
            assertEquals(1, context.getBeanNamesForType(DbmsSqlExecutor.class).length);
            assertEquals(1, context.getBeanNamesForType(ApplicationContextProvider.class).length);
            assertEquals(1, context.getBeanNamesForType(ElastiCoreBannerPrinter.class).length);
        });
    }

    @Test
    public void aComponentScanOfTheRuntimeIsKept() {
        runner.withUserConfiguration(ScanningApp.class).run(context -> {
            started(context);
            assertEquals(1, context.getBeanNamesForType(DbmsSqlExecutor.class).length);
            assertEquals(1, context.getBeanNamesForType(ApplicationContextProvider.class).length);
            assertEquals(1, context.getBeanNamesForType(CourierPort.class).length, "the scan never found ports; this does");
            assertEquals(1, context.getBeanNamesForType(ReportPort.class).length);
        });
    }

    @Test
    public void beansTheApplicationDeclaredAreTheOnesUsed() {
        runner.withUserConfiguration(AppWithItsOwnBeans.class).run(context -> {
            started(context);
            assertEquals(1, context.getBeanNamesForType(CourierPort.class).length);
            assertSame(AppWithItsOwnBeans.HAND, context.getBean(CourierPort.class));
            assertEquals(1, context.getBeanNamesForType(DbmsSqlExecutor.class).length);
            assertSame(AppWithItsOwnBeans.EXECUTOR, context.getBean(DbmsSqlExecutor.class));
        });
    }

    /**
     * shop.NotificationPort and billing.NotificationPort: both under their full
     * names - which one would get the short name would depend on scan order.
     */
    @Test
    public void twoPortsWithTheSameSimpleNameBothStart() {
        runner.withUserConfiguration(App.class).run(context -> {
            started(context);
            assertEquals(1, context.getBeanNamesForType(io.elasticore.springboot.autoconfigure.app.shop.NotificationPort.class).length);
            assertEquals(1, context.getBeanNamesForType(io.elasticore.springboot.autoconfigure.app.billing.NotificationPort.class).length);
            assertTrue(context.containsBean(io.elasticore.springboot.autoconfigure.app.shop.NotificationPort.class.getName()));
            assertTrue(context.containsBean(io.elasticore.springboot.autoconfigure.app.billing.NotificationPort.class.getName()));
            assertTrue(!context.containsBean("NotificationPort"), "neither takes the short name");
            assertTrue(context.containsBean("CourierPort"), "a port whose name is its own keeps the short name");
        });
    }

    @Test
    public void itCanBeSwitchedOff() {
        runner.withUserConfiguration(AppWithADatabase.class)
                .withPropertyValues("elasticore.autoconfigure.enabled=false")
                .run(context -> {
                    started(context);
                    assertEquals(0, context.getBeanNamesForType(CourierPort.class).length);
                    assertEquals(0, context.getBeanNamesForType(DbmsSqlExecutor.class).length);
                    assertEquals(0, context.getBeanNamesForType(ApplicationContextProvider.class).length);
                });
        runner.withUserConfiguration(AnnotatedApp.class)
                .withPropertyValues("elasticore.autoconfigure.enabled=false")
                .run(context -> {
                    started(context);
                    assertEquals(1, context.getBeanNamesForType(CourierPort.class).length, "the annotation still works");
                    assertEquals(1, context.getBeanNamesForType(DbmsSqlExecutor.class).length);
                });
    }

    /** A context that closed lets go of the static provider, so a later proxy never asks it for a bean. */
    @Test
    public void aClosedContextIsNotLeftBehind() {
        runner.withUserConfiguration(App.class).run(context -> {
            started(context);
            assertSame(context.getSourceApplicationContext(), ApplicationContextProvider.getApplicationContext());
        });
        assertNull(ApplicationContextProvider.getApplicationContext());
    }

    /** Spring Boot reads the auto-configuration from this file; without the line nothing above happens. */
    @Test
    public void springBootFindsIt() throws Exception {
        String name = ElastiCoreAutoConfiguration.class.getName();
        boolean listed = false;
        Enumeration<URL> files = getClass().getClassLoader()
                .getResources("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        while (files.hasMoreElements()) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(files.nextElement().openStream(),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.trim().equals(name)) listed = true;
                }
            }
        }
        assertTrue(listed, name + " in AutoConfiguration.imports");
    }
}
