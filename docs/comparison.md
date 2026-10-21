<p align="right"><b>English</b> · <a href="comparison.ko.md">한국어</a></p>

# How ElastiCORE compares

ElastiCORE is a DSL and a rule engine that keep generating a Spring Boot back end - and a TypeScript client - from a
model that carries the business rules. It is asked about next to code generators (JHipster, Amplication, Bootify,
Sculptor, OpenAPI Generator), DDD modelling tools (Context Mapper), structure checkers (Spring Modulith), application
platforms (Jmix, Wasp), and simply letting an AI assistant write the code. This page says what each is for, where
ElastiCORE differs, and where the others are the better choice.

Every statement about another product was checked against its own documentation or repository on **2026-10-06**; the
sources are at the end. Products change - if one of these is out of date, please open an issue.

## The short version

What ElastiCORE puts together, and no single tool below does:

1. **Business rules are declarations that generate their enforcement** (the 3.0 rule bundles; lifecycles,
   invariants and derived values in the DDD and hexagonal ones). A lifecycle (`ship: PLACED -> SHIPPED`),
   per-operation access and row ownership (`@access`, `@owner`), invariants, values fixed at order time
   (`@snapshot`) or derived (`@compute`), which states may be deleted - each one line, each enforced in regenerated
   code. A request that breaks a rule gets a defined HTTP answer (401, 403, 404, 409, 422) and a translatable error code;
   a value the server derives is simply not taken from the request. Screens get the same checks (`canShip(dto)`).
2. **Generation never stops.** The model stays the single statement after the first day: regenerated files and files
   you own are kept apart (base class + your class, generate-once files, edited files detected by content hash), and
   a generated file the model no longer produces is removed (an edited one is reported instead).
3. **Strategic DDD is enforced, not only drawn.** Bounded contexts declare what they use and publish; a type or an
   event taken across a boundary nobody opened fails generation, before anything is compiled. The context map is
   drawn from the same declarations.
4. **One model, several readings.** Layered, DDD or hexagonal; Java or Kotlin; Spring Boot 3 or 4; plus TypeScript -
   chosen by a setting, from the same DSL.
5. **The generator is made of rules your team can add to**, in plain Java - including checks that fail the build
   ("a root with commands must declare its events").
6. **The meaning of the data is exported too.** An [Apache Ossie](https://github.com/apache/ossie) semantic model -
   joins, what a code means, what "revenue" is as SQL - so BI tools and AI agents read the business definitions
   instead of guessing them.
7. **Same DSL, same code.** Generation is deterministic and measured: 2.x and 3.0 output compared on every pull request, and
   a [benchmark](benchmarks/change-amplification.md) of how much code one DSL line moves.

## In one table

| | Business rules in the model | Keeps your code on regeneration | Domain boundaries | What it generates | Status (2026-10) |
|---|---|---|---|---|---|
| **ElastiCORE** | Lifecycles, access per operation, row ownership, invariants, derived values, delete guards, business measures - enforced | Yes: base/own split, generate-once, hash detection, stale removal | Contexts checked at generation (types and events); aggregate members get no endpoint | Spring Boot back end (Java/Kotlin), TypeScript client, Ossie semantic model | Active, Apache-2.0 |
| JHipster (JDL) | Field validations only | Overwrites, asking first; "side-by-side" customisation as the workaround | Generated ArchUnit test checks technical layers; JDL has no bounded contexts | Full stack: Spring Boot + Angular/React/Vue, Liquibase, tests, deployments | Active (9.x), Apache-2.0 |
| Context Mapper (CML) | Lifecycle states and transitions - as documentation, not enforced | Not applicable (no code) | The richest model of contexts and relationship patterns; nothing enforced | Diagrams, service contracts (MDSL), JHipster JDL | Last release Aug 2024, Apache-2.0 |
| Sculptor | Bean-validation constraints | Yes: generation gap (base + gap class) | Modules (no cycles), aggregates | Java, JPA, Spring | Last release 2015 |
| Spring Modulith | Not its concern | Not applicable (no code) | Verifies module dependencies in tests; event publication registry | Documentation (C4/UML) | Active (2.1), Apache-2.0 |
| Amplication | Per-entity, per-action role permissions; logic by hand | Yes: base classes overwritten, subclasses kept | Several services; no bounded-context model | NestJS/Prisma or .NET services, admin UI | Site redirects to another product; repo not archived |
| Jmix | Bean validation; row-level security roles | Not applicable (edits your source) | None found in its documentation | Entities and CRUD views; the rest is the runtime platform | Active (3.0), Apache-2.0 + paid Studio features |
| Wasp | None declarative (checks written in operations) | Not applicable (compiler builds the glue) | Single app | React/Node wiring: auth, RPC, CRUD, jobs | 0.x; DSL retired for a TypeScript spec, MIT |
| Bootify | Field validation | Overwrites an export branch you merge | Spring Modulith modules with a generated test | Spring Boot code, migrations, front end | Commercial (free plan) |
| AI writes Spring directly | Whatever the prompt says, written anew each time | Not applicable | By review | Anything | - |

## JHipster and JDL

JHipster is the mature, widely used application generator for Spring Boot. JDL describes applications, deployments,
entities, fields with validations (`required`, `min`, `max`, `pattern` ...), relationships, enums, and options such
as DTOs, service classes, pagination and filtering. It generates complete Angular, React or Vue front ends and
Liquibase changelogs, which ElastiCORE does not, and a `TechnicalStructureTest` (ArchUnit) for its technical layers.

Where ElastiCORE differs:

- **The DSL carries business rules, not only data.** JDL's keywords cover applications, entities, relationships and
  options; there is no construct for a lifecycle, a per-operation permission, row ownership, an invariant or a
  derived value. In ElastiCORE each is a line that generates its enforcement and its HTTP answer.
- **Generation never stops.** Re-running JHipster's entity generator warns that custom code will be overwritten, and
  its documentation recommends a "side-by-side" arrangement (extend generated classes, separate controllers) to keep
  customisations. ElastiCORE splits each artifact into what the model owns and what you own, so regenerating is the
  normal way to make a change.
- **Architecture and language are readings of the same model** - layered, DDD or hexagonal, Java or Kotlin, Boot 3
  or 4.
- **Boundaries are domain boundaries.** JHipster's ArchUnit test checks technical layers (web, service,
  persistence); ElastiCORE checks bounded contexts and aggregates.

Choose JHipster when you want a complete full-stack application, including the UI and the deployment, generated
quickly and then developed by hand.

## Context Mapper

Context Mapper is the reference DSL for modelling strategic DDD: context maps, bounded contexts, subdomains and the
relationship patterns between them (Customer/Supplier, Open Host Service, Anticorruption Layer ...), plus a tactical
part based on the Sculptor DSL, and flows of commands and events. It can declare lifecycle states and the transitions of
operations. It produces diagrams, service contracts and JHipster JDL - not runnable code - so what it models is not
enforced anywhere.

ElastiCORE's context declarations (`config.context`) are deliberately smaller - what a context uses and what it
publishes - and are **enforced**: a type or event crossing undeclared fails generation, and the context map is drawn
from what the code actually does. Its lifecycles are enforced in the generated aggregate. Use Context Mapper to
explore and discuss a context map; ElastiCORE does not model relationship patterns.

## Sculptor

Sculptor was a DDD DSL on Xtext that generated Java, JPA and Spring code. It used the generation-gap pattern - a
generated base class and a hand-written subclass - which ElastiCORE also uses for aggregate roots (`XxxBase` +
`Xxx`). Its last release on Maven Central is from 2015. ElastiCORE carries the idea forward with business rules in
the model, current Spring Boot, Kotlin, and a rule engine you can extend.

## Spring Modulith

Spring Modulith structures one Spring Boot application into application modules, verifies that modules depend on each
other only as allowed (`ApplicationModules.verify()`), records event publications so failed listeners can be retried,
and documents the structure. It works on code that already exists and generates none.

ElastiCORE states the same kind of boundary one level earlier - in the model - and refuses a crossing at generation
time. The two address one concern at different moments and could be used together, but ElastiCORE does not generate
Modulith's configuration, and its package layout per domain is not arranged for Modulith's conventions (Modulith
treats a module's sub-packages as internal unless they are declared as named interfaces) - using both today means
aligning the packages by hand. ElastiCORE's cross-context events are Spring application events within one
application; it has no publication registry of its own (its transactional outbox covers events that leave the
service).

Choose Spring Modulith when the code base already exists and you want its module boundaries verified and documented.

## Amplication, Bootify and other generators

- **Amplication** generates NestJS/Prisma (or .NET) services from entities defined in a web UI, with role permissions
  per entity and action, and keeps custom code in subclasses of overwritten base classes. Its business logic is
  written by hand. As of October 2026 its website redirects to a different product; the repository is not archived
  and had a release in April 2026.
- **Bootify** generates a Spring Boot project from a visual builder, including Spring Modulith modules; its Git
  export overwrites a branch that you merge yourself.
- **OpenAPI Generator** starts from the API contract rather than the domain, and keeps hand-written implementations
  apart through interface or delegate patterns.

Unlike Amplication and Bootify, ElastiCORE's model is a text file in your repository, reviewed like code (as an
OpenAPI spec is). Unlike all three, it carries the business rules - lifecycles, ownership, invariants, derived values
- beyond Amplication's role permissions.

## Jmix and Wasp

These are application platforms rather than generators. **Jmix** is code-first: its Studio edits your Java entities
directly, and the platform provides UI and security at runtime, including row-level security roles - a strength
ElastiCORE matches only partly (`@owner`). **Wasp** declares the wiring of a React/Node application (pages, routes,
operations, auth) - since version 0.24 in a TypeScript spec rather than its own DSL - and leaves the logic, including
authorization checks, to the functions you write.

## Letting an AI write the Spring code directly

An assistant can write anything, including what no DSL can express - and that freedom is its cost:

- **Review.** Every generated line is a line to review. With ElastiCORE the reviewed change is the DSL; the
  [change amplification benchmark](benchmarks/change-amplification.md) measures how much generated code one line of
  DSL moves.
- **Consistency.** An access check written by hand on one endpoint and forgotten on another is a common defect;
  generated access checks are the same code at every entry point.
- **Determinism.** Asked the same thing twice, a model may write different code. An empirical study of ChatGPT on 829
  coding problems reports that "the ratio of coding tasks with zero equal test output across different requests is
  75.76%, 51.00%, and 47.56%" on its three benchmarks, and that "setting the temperature to 0 does not guarantee
  determinism in code generation" (Ouyang et al., ACM TOSEM). The same DSL gives the same code, so a regeneration is
  a safe operation and a diff shows only what the model changed (apart from the `//ecd:` header line of a rewritten
  file).
- **Feedback.** A wrong DSL is refused, or warned about, with a sentence that says what is wrong - a short loop an
  agent can close by itself.

The two are not exclusive: the intended workflow is an AI that edits the DSL, reads the build's answer, and leaves the
generated code alone - with hand-written code where the DSL ends (behaviour classes, service extensions, ports).

## Where ElastiCORE is not the right tool

- A UI-first application where the front end is most of the work (JHipster, Jmix).
- Exploring and discussing a context map with its relationship patterns (Context Mapper).
- A system whose persistence is not relational or not JPA (MongoDB support exists but is narrower).
- Logic that does not fit a declarative model - complex calculations, workflows across many aggregates. ElastiCORE
  leaves explicit places for that code, but it is code you write.

## Sources (checked 2026-10-06)

- JHipster: [JDL](https://www.jhipster.tech/jdl/intro), [options](https://www.jhipster.tech/jdl/options),
  [fields and validations](https://www.jhipster.tech/jdl/entities-fields),
  [creating an entity](https://www.jhipster.tech/creating-an-entity/),
  [combining generation and custom code](https://www.jhipster.tech/tips/035_tip_combine_generation_and_custom_code.html),
  [generator repository](https://github.com/jhipster/generator-jhipster) (JDL lexer; `TechnicalStructureTest` template)
- Context Mapper: [language reference](https://contextmapper.org/docs/language-reference/),
  [application and process layer](https://contextmapper.org/docs/application-and-process-layer/),
  [aggregates and lifecycles](https://contextmapper.org/docs/aggregate/),
  [generators](https://contextmapper.org/docs/generators/),
  [JHipster generation](https://contextmapper.org/docs/jhipster-microservice-generation/),
  [releases](https://github.com/ContextMapper/context-mapper-dsl/releases)
- Sculptor: [repository](https://github.com/sculptor/sculptor),
  [advanced tutorial](https://sculptor.github.io/documentation/advanced-tutorial),
  [Maven Central](https://repo1.maven.org/maven2/org/sculptorgenerator/sculptor-maven-plugin/maven-metadata.xml)
- Spring Modulith: [verification](https://docs.spring.io/spring-modulith/reference/verification.html),
  [events](https://docs.spring.io/spring-modulith/reference/events.html),
  [documentation](https://docs.spring.io/spring-modulith/reference/documentation.html),
  [Maven Central](https://repo1.maven.org/maven2/org/springframework/modulith/spring-modulith-core/maven-metadata.xml)
- Amplication: [repository](https://github.com/amplication/amplication), [docs repository](https://github.com/amplication/docs)
- Jmix: [entity designer](https://docs.jmix.io/jmix/studio/entity-designer.html),
  [row-level roles](https://docs.jmix.io/jmix/security/row-level-roles.html),
  [database migration](https://docs.jmix.io/jmix/data-model/db-migration.html),
  [release 3.0](https://docs.jmix.io/jmix/whats-new/release-3.0.html)
- Wasp: [docs](https://wasp.sh/docs), [the Wasp spec](https://wasp.sh/docs/general/wasp-ts-config),
  [actions](https://wasp.sh/docs/data-model/operations/actions)
- Bootify: [entities](https://bootify.io/docs/entities-tab.html), [modules](https://bootify.io/docs/modules-tab.html),
  [Git integration](https://bootify.io/docs/git-integration.html)
- OpenAPI Generator: [customization](https://openapi-generator.tech/docs/customization/)
- Ouyang, Zhang, Harman, Wang, *An Empirical Study of the Non-determinism of ChatGPT in Code Generation*, ACM TOSEM -
  [arXiv:2308.02828](https://arxiv.org/abs/2308.02828)
