package io.elasticore.springboot3.mask;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A response field shown masked: a String the application's JSON writes through {@link MaskType#apply}. Reading a
 * request is not changed. It names no Jackson type, so the same DTO works on Spring Boot 3 (Jackson 2) and 4
 * (Jackson 3); the runtime's auto-configuration registers the module that reads it.
 *
 * <p>In the DSL, through an env.yml annotation:
 *
 * <pre>
 * config:
 *   annotations:
 *     maskphone: "@dto:io.elasticore.springboot3.mask.Mask(io.elasticore.springboot3.mask.MaskType.PHONE)"
 * ...
 *     phone: string(20) @env:maskphone
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Mask {

    MaskType value();
}
