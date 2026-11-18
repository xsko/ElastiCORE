package io.elasticore.springboot.autoconfigure;

import io.elasticore.springboot3.crypto.FieldCrypto;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * The keys of the encrypted fields ({@code @encrypt}, or {@code EncryptConverter} named by hand), read from the
 * {@code elasticore.crypto.*} properties into {@link FieldCrypto} before any bean is made - Hibernate and MyBatis make
 * the converter and the type handler themselves, and they read the keys from there.
 *
 * <p>A key that is set but malformed stops the start. A missing {@code elasticore.crypto.key} stops it once every
 * bean is made, where an encrypted field is mapped (the converter or the handler was made) - an application without
 * one starts as before.
 */
@AutoConfiguration
public class ElastiCoreCryptoAutoConfiguration {

    @Bean
    static Keys elastiCoreCryptoKeys() {
        return new Keys();
    }

    /** Installs the keys first, and checks them after every bean is made. */
    static class Keys implements BeanFactoryPostProcessor, EnvironmentAware, SmartInitializingSingleton {

        private Environment environment;

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            FieldCrypto.install(FieldCrypto.of(environment::getProperty));
        }

        @Override
        public void afterSingletonsInstantiated() {
            if (FieldCrypto.used()) FieldCrypto.current().requireKeys();
        }
    }
}
