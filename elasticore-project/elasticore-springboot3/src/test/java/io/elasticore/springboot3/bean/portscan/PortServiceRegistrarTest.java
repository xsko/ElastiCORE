package io.elasticore.springboot3.bean.portscan;

import io.elasticore.runtime.port.ExternalService;
import io.elasticore.springboot3.bean.PortServiceRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the port registrar looks: the package of the class that imports it
 * (the @EnableElastiCore class, which a @SpringBootTest starts too) - so a
 * test context gets its ports without a hand-written @Bean - once per
 * interface, and never in place of a bean the application declared itself.
 */
public class PortServiceRegistrarTest {

    @ExternalService(protocol = "http", id = "test.CourierPort", url = "http://localhost:1")
    public interface CourierPort {
        String ship(String parcel);
    }

    /** What @EnableElastiCore does for the registrar, in this package. */
    @Configuration
    @Import(PortServiceRegistrar.class)
    public static class App {
    }

    /** A second configuration class importing it too, as two @EnableElastiCore classes would. */
    @Configuration
    @Import(PortServiceRegistrar.class)
    public static class SecondApp {
    }

    /** The bean a test used to declare because the registrar found nothing in a test context. */
    @Configuration
    @Import(PortServiceRegistrar.class)
    public static class AppWithItsOwnBean {
        static final CourierPort HAND = parcel -> "hand:" + parcel;

        @Bean
        CourierPort courierPort() {
            return HAND;
        }
    }

    private static AnnotationConfigApplicationContext start(Class<?>... classes) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        // As Spring Boot runs: a second definition under a taken name fails the start.
        context.setAllowBeanDefinitionOverriding(false);
        context.register(classes);
        context.refresh();
        return context;
    }

    @Test
    public void theImportingClassesPackageIsScanned() {
        try (AnnotationConfigApplicationContext context = start(App.class)) {
            CourierPort port = context.getBean(CourierPort.class);
            assertTrue(Proxy.isProxyClass(port.getClass()), "the port runtime's proxy");
        }
    }

    @Test
    public void anInterfaceFoundTwiceIsRegisteredOnce() {
        try (AnnotationConfigApplicationContext context = start(App.class, SecondApp.class)) {
            assertEquals(1, context.getBeanNamesForType(CourierPort.class).length);
        }
    }

    @Test
    public void aBeanTheApplicationDeclaredIsTheOneUsed() {
        try (AnnotationConfigApplicationContext context = start(AppWithItsOwnBean.class)) {
            assertEquals(1, context.getBeanNamesForType(CourierPort.class).length);
            assertSame(AppWithItsOwnBean.HAND, context.getBean(CourierPort.class));
        }
    }
}
