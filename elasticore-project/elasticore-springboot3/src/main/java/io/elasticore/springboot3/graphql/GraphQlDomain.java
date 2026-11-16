package io.elasticore.springboot3.graphql;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A GraphQL controller ElastiCORE generated, and the domain it serves.
 *
 * The schema writer ({@link GraphQlSchemaWriter}) reads the compiled
 * controllers that carry it and writes {@code graphql/<domain>.graphqls} from
 * their operations and the classes those operations take and answer. A
 * controller written by hand does not carry it; its schema is the
 * developer's own file.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GraphQlDomain {

    /** The domain (blueprint folder) whose schema file this controller's operations go to. */
    String value();
}
