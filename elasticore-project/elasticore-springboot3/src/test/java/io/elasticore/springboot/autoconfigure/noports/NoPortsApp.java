package io.elasticore.springboot.autoconfigure.noports;

import jakarta.persistence.EntityManagerFactory;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** An application with a database and no ports in its package. */
@Configuration
@AutoConfigurationPackage
public class NoPortsApp {
    @Bean
    EntityManagerFactory entityManagerFactory() {
        return Mockito.mock(EntityManagerFactory.class);
    }
}
