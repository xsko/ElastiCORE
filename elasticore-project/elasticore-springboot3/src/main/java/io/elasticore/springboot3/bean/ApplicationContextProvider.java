package io.elasticore.springboot3.bean;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Provides static access to the Spring ApplicationContext throughout the application.
 * This component implements ApplicationContextAware to receive the ApplicationContext
 * instance during Spring bean initialization and makes it available for static access.
 *
 * <p>This is useful for accessing Spring beans in classes that are not managed by Spring
 * or in static contexts where dependency injection is not available.</p>
 *
 * <p>When its context closes it lets go of it - unless another context has
 * taken its place since - so that a port proxy never asks a closed context
 * for a bean (a test that starts and closes contexts one after another, a
 * restarted application).</p>
 *
 * @author ElastiCore Framework
 * @since 1.0
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware, DisposableBean {

    private static volatile ApplicationContext applicationContext;

    private ApplicationContext own;

    /**
     * Sets the ApplicationContext that this object runs in.
     * Called by Spring framework during bean initialization.
     *
     * @param context the ApplicationContext object to be used by this object
     */
    @Override
    public void setApplicationContext(ApplicationContext context) {
        own = context;
        applicationContext = context;
    }

    @Override
    public void destroy() {
        synchronized (ApplicationContextProvider.class) {
            if (applicationContext == own) applicationContext = null;
        }
    }

    /**
     * Returns the static ApplicationContext instance.
     * This method provides access to the Spring ApplicationContext from anywhere
     * in the application, including non-Spring managed classes.
     *
     * @return the ApplicationContext instance, or null if not yet initialized
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
