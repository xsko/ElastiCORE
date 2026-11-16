package io.elasticore.springboot.autoconfigure;

import io.elasticore.springboot3.graphql.ElastiCoreGraphQlConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Import;

/**
 * The scalars and errors of the GraphQL schema the build wrote
 * (graphql/elasticore.graphqls), wherever that schema is served - also by an
 * application that scans none of the generated GraphQL controllers, which
 * import the same configuration themselves. Spring for GraphQL reads every
 * schema file on the classpath, and a declared scalar nobody wires stops it
 * from starting.
 *
 * Only where the generated schema is there: an application without it is
 * not changed.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.graphql.execution.RuntimeWiringConfigurer")
@ConditionalOnResource(resources = "classpath:graphql/elasticore.graphqls")
@Import(ElastiCoreGraphQlConfiguration.class)
public class ElastiCoreGraphQlAutoConfiguration {}
