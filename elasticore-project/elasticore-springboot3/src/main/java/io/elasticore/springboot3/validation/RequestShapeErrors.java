package io.elasticore.springboot3.validation;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * What the generated {@code <Domain>ApiErrors} imports
 * ({@code @Import(RequestShapeErrors.class)}) so that a request Spring MVC
 * refuses before any controller method runs - a body that is not JSON, a
 * value its type refuses, a parameter missing, a method, media type or path
 * the application does not serve - is answered in the API's problem body
 * with a code. The answers themselves are {@link RequestShapeAdvice}.
 *
 * It registers that advice once, however many domains import it. Nothing
 * else does: the advice is not picked up by a component scan of
 * io.elasticore.springboot3 (the old guide told 2.x applications to add one)
 * and no auto-configuration registers it, so an application that does not
 * import this class answers as before.
 */
public class RequestShapeErrors implements ImportBeanDefinitionRegistrar {

    /** The advice's bean name. */
    static final String ADVICE = RequestShapeAdvice.class.getName();

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importing, BeanDefinitionRegistry registry) {
        // Every domain's ApiErrors imports it: the first stands.
        if (registry.containsBeanDefinition(ADVICE)) return;
        RootBeanDefinition advice = new RootBeanDefinition(RequestShapeAdvice.class);
        advice.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
        registry.registerBeanDefinition(ADVICE, advice);
    }
}
