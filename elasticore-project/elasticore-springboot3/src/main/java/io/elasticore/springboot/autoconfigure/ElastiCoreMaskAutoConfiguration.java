package io.elasticore.springboot.autoconfigure;

import io.elasticore.springboot3.mask.MaskJackson;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * The Jackson module that writes a {@code @Mask} response field masked (Jackson 2 on Spring Boot 3, Jackson 3 on
 * Spring Boot 4: each runtime brings its own MaskJackson). It acts only on fields that carry {@code @Mask}: an
 * application without one writes the same JSON as before.
 */
@AutoConfiguration
@Import(MaskJackson.class)
public class ElastiCoreMaskAutoConfiguration {}
