### New Features (GraphQL)

#### A GraphQL API beside REST, its schema written from the compiled classes (`graphql.enable`, #148)
- Why: a screen that wants an order with its lines in one round trip, or a partner that works in GraphQL, had to have
  resolvers written by hand over the generated services - and a schema kept in step with the DTOs by hand.
- After: `env.yml` `config.graphql.enable: true` (with `mutations: false` for queries only, `path` for
  `spring.graphql.path`) generates `<Entity>GraphQlController` for every entity the REST API serves (a root under the
  ddd and hexagonal bundles), in Java and Kotlin, over JPA and MyBatis: `member(memberId)`, `members(search)`,
  `createMember`, `updateMember`, `deleteMember`, and one mutation per command (`shipOrder(orderId, command)`). They
  call the service (the use case in the hexagon) the REST controller calls, so access, validation, invariants,
  `@deleted` and the lifecycle hold as they do for REST; `@readonly`, `@expose(methods=...)` and `@restExclude` take
  away the same operations.
  - The schema is written by the build after compiling: `elcoreGraphQl` (plugin, before `processResources`) reads the
    controllers that carry `@GraphQlDomain` and every class they take and answer, and writes
    `src/main/resources/graphql/<domain>.graphqls` and `elasticore.graphqls` (root types, scalars, shared types). The
    type mapping is one class of the runtime library (`GraphQlTypes`), used by the writer and by the scalars wired at
    run time. A write-only property is in the input only; a value object without setters is sent into its fields, as
    Jackson reads it. Then every `.graphqls` under `graphql/` is built into one schema; a type name two classes take, an
    operation two domains name, a property that cannot be written or a schema that does not build stops the build.
  - Refusals are GraphQL errors with the REST status and code in `extensions` (`NOT_FOUND` 404, `FORBIDDEN` 403,
    `BAD_REQUEST` 400, `CONFLICT` 409, `UNPROCESSABLE` 422, `BAD_GATEWAY` 502).
  - Domains that turn it on share one schema and one endpoint: different paths, or an entity of one name served by two
    of them, stop `elcore3` naming both domains.
- Not changed: a domain without `graphql.enable` generates exactly what it did (the rule set declares no file, and the
  build step is not wired where no `env.yml` says graphql). `jpa-springboot` (2.x-compatible) does not carry it. The
  runtime library only compiles against Spring for GraphQL; its auto-configuration (scalars, errors) applies only where
  the generated `graphql/elasticore.graphqls` is on the classpath.
- Not yet: subscriptions, a cursor (Connection) page, a TypeScript client for the GraphQL operations.
- Checked: `GraphQlGenerationTest` (layered, hexagonal with commands, Kotlin, `mutations: false`, off, two domains
  disagreeing); `GraphQlSchemaWriterTest` (the files, the shared types, the clashes, a schema that does not build, and
  queries and mutations executed through Spring for GraphQL against the written schema, on Boot 3 and Boot 4);
  `elasticore-template-ddd` `GraphQlApiTest` (shop and billing at one `/graphql`, over HTTP: create/read/search, a
  value object without setters, read-only billing, a command refused and validated) and `elasticore-template-kt`
  `ShopGraphQlTest`.
- Files: `GraphQlRules`, `GraphQlSettings`, `v3/graphql.tmpl`, `kt/graphql.tmpl`, `DddAggregateRules`,
  `DddCommandRules`, `HexUseCaseRules`, `RuleCatalog`, `RulesGenRunner`, `ElastiCorePlugin` (`elcoreGraphQl`),
  `elasticore-springboot3` `graphql/` and `ElastiCoreGraphQlAutoConfiguration`, env guide 6.1.5 (en, ko), the
  samples `elasticore-template-ddd` and `elasticore-template-kt`.
