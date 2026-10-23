package io.elasticore.springboot3.bean;

import io.elasticore.base.Version;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;

/**
 * Prints the ElastiCORE version banner at Spring Boot startup.
 * Uses System.out (like Spring Boot's own banner) for guaranteed visibility
 * regardless of logging configuration.
 * Registered by the auto-configuration and by @EnableElastiCore;
 * {@code elasticore.banner.enabled=false} keeps it quiet.
 */
public class ElastiCoreBannerPrinter implements ApplicationListener<ContextRefreshedEvent> {

    static final String ENABLED = "elasticore.banner.enabled";

    private static boolean printed = false;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!printed && enabled(event.getApplicationContext().getEnvironment())) {
            printed = true;
            System.out.println(Version.getBanner());
        }
    }

    /** Only "false" silences it: a value the banner cannot read must not fail the start. */
    static boolean enabled(Environment environment) {
        String value = environment == null ? null : environment.getProperty(ENABLED);
        return value == null || !"false".equalsIgnoreCase(value.trim());
    }
}
