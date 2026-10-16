<p align="right"><b>English</b> · <a href="README.ko.md">한국어</a></p>

# ElastiCORE

[![Maven Central](https://img.shields.io/maven-central/v/io.elasticore/elasticore-base.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.elasticore/elasticore-base)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x%20%7C%204.x-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**For business systems that have to last: the business rules in a few lines of YAML, the Spring Boot code generated
from them — the same code every time.**

ElastiCORE takes your domain model and business rules — who may do what, how an order moves along, how a value is
decided — as a short DSL, and generates the Spring Boot application: JPA entities, DTOs, services, REST APIs, access
checks, state transitions, and a TypeScript client for the screens. The rules stay in one place, which a developer or
an AI assistant changes and a reviewer reads; the build carries them into every layer of the server, and
`./gradlew elcore3ts` into the TypeScript client. Generation is **assembled from rulesets**, so you can switch the
language, the architecture or the Spring Boot version, or plug in your team's own rules — and the model stays as it is.

```
  blueprint/shop/model/*.yml  ──►  Java  · Kotlin  · TypeScript
     (business rules, ~150 lines)  layered · DDD · hexagonal
                                   Spring Boot 3 · Spring Boot 4
                                   + your team's rules (ruleset)
```

**Built for** business systems that live for years and pass through many hands — orders, billing, contracts, back
offices — where access rules and lifecycles must hold at every entry point, and every developer and every AI
assistant must produce the same structure. **Not for** a UI-first application, a system whose persistence is not
relational or not JPA, or logic that does not fit a declarative model ([how it compares](docs/comparison.md)). What is
checked, and what is still pre-release: [Output you can trust](#output-you-can-trust).

---

## Into your project in five steps

**① `build.gradle`** — a Spring Boot project, the plugin with its two settings, and what the generated code uses

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.8'      // 3.x works too - the version is read and followed
    id 'io.spring.dependency-management' version '1.1.7'
    id 'io.elasticore' version '3.0.261015-alpha1'
}

repositories {
    mavenCentral()
}

elasticore {
    architecture = 'hexagonal'    // layered | ddd | hexagonal
    language     = 'java'         // java | kotlin
}

dependencies {
    implementation 'io.elasticore:elasticore-base:3.0.261015-alpha1'
    implementation 'io.elasticore.springboot4:elasticore-springboot4:3.0.261015-alpha1'

    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    runtimeOnly 'com.h2database:h2'               // an in-memory database to try it with
}
```

Starting from Spring Initializr (Spring Web, Spring Data JPA, Validation, Lombok, H2; its default Boot 4.1.1 works
too)? Keep its `build.gradle`, whose test lines its generated test needs, and add what it lacks: the `io.elasticore`
plugin line, the `elasticore` block, the two ElastiCORE dependencies and springdoc. On Spring Boot 3 (checked with 3.5.16): the Boot
plugin `3.5.16`, `io.elasticore.springboot3:elasticore-springboot3` in place of `…springboot4`,
`spring-boot-starter-web` in place of `-webmvc`, and springdoc `2.8.17` in place of `3.0.3`.

**② `src/main/resources/blueprint/shop/`** — the domain's settings and its business

```yaml
# env.yml
elasticore:
  enable: true
  config:
    domainName: shop
    framework: springboot
    mode: jpa
    j2ee: jakarta
    apiPath: "/api"               # REST paths: /api/shop/...
  namespace:
    base: com.acme.shop           # the whole package layout from this one line
```

```yaml
# model/entity.yml
entity:
  Product:
    meta: entity @root @editable @expose(20) @access(read=public) @sort(name+) -- product
    fields:
      productId: long @id @sequence
      name:      string(100)! @search(like)
      price:     money(10.2)! @min(0.01, 'price must be positive')
```

**③ Generate**

```bash
./gradlew elcore3
```

The code appears under `src/main/java/com/acme/shop/`:

```
domain/model/Product.java               JPA entity
application/ProductCoreService.java     CRUD, search, access checks (regenerated)
application/ProductService.java         where your code goes (generated once, yours afterwards)
application/dto/ProductDTO.java         request and response (+ 400 validation)
application/dto/ProductSrchDTO.java     search: name LIKE, paging, sorting (by name unless asked otherwise)
adapter/in/web/ProductController.java   REST API + OpenAPI docs (generated once too)
domain/repository/...                   repository port
infrastructure/persistence/...          its JPA adapter
```

**④ Your part** — what the model does not say

- **An application class**: `@SpringBootApplication` in `com.acme.shop` or a package above it, so that Spring finds the
  generated classes. Spring Initializr writes one in the package of its group and artifact (`com.acme` and `shop` give
  `com.acme.shop`).
- **Your business logic**, in `ProductService.java`. Like the controller it is generated once: it follows the model
  until you edit it, and from then on generating again leaves it alone (`SKIP(user-owned)` in the log).
- **Signing in.** Changing a product needs the `ADMIN` role (`@access(read=public)`: anyone reads, everything else is
  for the administrator). Your sign-in calls `SessionCurrentUser.signIn(request, userId, "ADMIN")` (package
  `io.elasticore.springboot3.security`, on Boot 4 too); with Spring Security, register a `CurrentUser` bean instead.

**⑤ Run**

```bash
./gradlew bootRun
```

`POST /api/shop/product/search` and `GET /api/shop/product/{id}` answer anyone; creating a product without signing in
answers 401 "Sign in to create Product", and a price of 0 answers 400 "price must be positive". From here on a change
starts in the model: `./gradlew build` and `bootRun` run `elcore3` before compiling, so the code never lags behind the
model. New here? Run [`elasticore-sample-petstore`](elasticore-project/elasticore-sample-petstore/) and open
`/dsl-guide.html` to see, business area by business area, the DSL next to the code it generated.

---

## Reading the DSL: one line, every layer

The whole grammar is six habits, so a developer writes it by hand and a reviewer reads it at a glance:

| | Principle | Example |
|---|---|---|
| 1 | **One line is one field** — it decides storage, validation, search and exposure | `name: string(30)! @search(like)` |
| 2 | **Common things are symbols beside the type** | `(30)` length, `!` required, `(10.2)` precision |
| 3 | **Entity-level rules go on the one `meta:` line** | `meta: entity @root @editable @expose(20) @access(read=public)` |
| 4 | **Defaults** — write only what differs | `@expose(20)` alone gives create, read, search, paging and REST (and update — in the DDD bundles for `@editable` roots or fields) |
| 5 | **YAML sections are the concepts** | `entity`, `dto`, `enumeration`, `states`, `metrics`, `port` |
| 6 | **The description follows `--`** — the model documents itself | `-- unit of sale (Large, Male Puppy ...)` |

One line of the pet store's `Item`, and what it became (copied from the generated files):

```
unitCost:  money(10.2)!  @min(0.01, 'price must be positive')  @terms(price, 가격)
```

| Where | Generated |
|---|---|
| JPA entity | `@Column(name = "unit_cost", nullable = false, precision = 10, scale = 2)` + a `Money` converter |
| Request DTO (400) | `@NotNull` `@DecimalMin(value = "0.01", message = "price must be positive")` |
| TypeScript validation | `else if (value.unitCost != null && Number(value.unitCost) < 0.01) errors.unitCost = "price must be positive";` |
| Semantic model (Ossie) | field `unit_cost`, `synonyms: ["price", "가격"]` |

Written by hand that is one fact in four places, and forgetting one still compiles. Business rules keep the same
shapes — a lifecycle step is `ship: PLACED -> SHIPPED`, a business measure is
`revenue: sum(PurchaseOrder.total) where status != CANCELLED`, another context's event is `shop:OrderPlaced` — so
there is no new grammar per feature. The [DSL guide](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-dsl-guide.md)
opens with these rules.

## What sets it apart

Compared with JHipster, Context Mapper, Sculptor, Spring Modulith, Amplication, Jmix, Wasp and Bootify
([details and sources](docs/comparison.md), checked 2026-10):

- **Business rules generate their enforcement** (3.0 rule bundles; lifecycles and invariants in the DDD and hexagonal
  ones). Lifecycles, per-operation access, row ownership, invariants and derived values are lines in the model,
  enforced in regenerated code — a request that breaks one gets a defined HTTP answer. JDL stops at field
  validations; Context Mapper can model lifecycles, but only as documentation.
- **Generation never stops.** What the model owns and what you own are separate files, so regenerating is how a change
  is made — not a risk to your edits.
- **Bounded contexts are enforced, not only drawn.** A type or event crossing a boundary nobody opened fails
  generation; JHipster's generated ArchUnit test checks technical layers, Spring Modulith checks existing code at test
  time.
- **One model, several readings** — layered, DDD or hexagonal; Java, Kotlin or TypeScript; Spring Boot 3 or 4.
- **The data's meaning goes out too** — an Apache Ossie semantic model, business measures as SQL, for BI tools and AI
  agents.
- **Same DSL, same code** — and the generator is rules your team can extend.

Your business rules outlive the language and the framework: the same DSL generates Java or Kotlin, for Boot 3 or
Boot 4, and survived a complete rewrite of the generator (2.x → 3.0): of the 229 files 2.x generates for the
reference project, 224 are identical, one differs only in import order, and four differ by a 2.x bug fix the
history lists.

---

## One model, the code you want

Generation is **a bundle of rulesets**. One setting changes how the same model is read.

| To change | Setting | Result |
|---|---|---|
| Language | `language = 'kotlin'` | the same model as Kotlin entities, services, controllers |
| Screen code | `./gradlew elcore3ts` | TypeScript DTOs, enum labels, a typed API client, validation functions |
| Architecture | `architecture = 'ddd'` / `'hexagonal'` | aggregate boundaries, invariants, domain events, ports-and-adapters packages |
| Spring Boot | the Boot plugin's version (automatic) | on Boot 4: Jackson 3 enums, Kotlin fitted to Spring Data 4, … |
| Modules | `service: app-admin://com.acme.shop.service` in `env.yml` | only services and controllers go to another module |
| Only some rules | `publisher: rules: [jpa-springboot-kt, ddd-aggregate, …]` in `env.yml` | pick and combine the rulesets you need |

Server and screen code from the same `Item` model (verbatim from the sample):

```java
// Java - domain/model/Item.java
@Column(name = "unit_cost", nullable = false, precision = 10, scale = 2)
@Convert(converter = Money.JpaConverter.class)
private Money unitCost;
```

```ts
// TypeScript - src/main/ts/ItemDTO.ts
export interface ItemDTO { name: string; unitCost: number; productName?: string; /* … */ }
export function validateItem(value: Partial<ItemDTO>, partial = false): Record<string, string>
//   → { unitCost: "price must be positive" }   the same words as the server's 400
```

Because server and screen come **from the same DSL**, change a field and the screen code tells you with a compile error.

---

## Your team's rules (rulesets)

"Our annotation on every root entity", "personal data never in a response", "a command without an event fails the
build" — **team rules as code**, plugged into generation. Write the rule once instead of hand-editing generated files,
and every entity gets it on every regeneration.

```java
public class AcmeRules implements CodeRules {

    /** A field marked @secret in the DSL is accepted in requests but never sent back. */
    @Rule(value = "acme-secret", target = "dto")
    void secret(EntitySpec entity, FieldSpec field) {
        if (field.hasDsl("secret")) {
            ctx(entity).field(field).ensure(AnnoCode.of("com.fasterxml.jackson.annotation.JsonProperty")
                    .valRaw("access", "JsonProperty.Access.WRITE_ONLY"));
        }
    }

    /** A root that takes commands must declare its events - otherwise the build fails. */
    @Rule("acme-commands-need-events")
    void commandsNeedEvents(EntitySpec entity) {
        if (!entity.commands().isEmpty() && entity.events().isEmpty()) {
            ModelCheck.fail(entity.getName() + " has commands but no @event.");
        }
    }
}
```

```gradle
elasticore { rules = ['com.acme.rules.AcmeRules'] }
dependencies { elasticore project(':acme-rules') }
```

```yaml
      bizNo: string(12)! @secret      # a marker of your own in the DSL
```

ElastiCORE's own Java, Kotlin, TypeScript, DDD and hexagonal generation are rules of exactly this kind. The
[rule development guide](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-rule-dev-guide.md)
shows the result of every step for someone writing their first rule.

---

## The environment changes; the model does not

- **Spring Boot 3 → 4**: raise the Boot plugin's version and the generated code follows — including the places that
  would otherwise change a response without an error, such as Jackson 3's enum serialization. Boot 3 and Boot 4
  sample projects are both generated, compiled and tested.
- **2.x → 3.0**: change nothing and you get **the same code** as 2.x — compared on every pull request over the 229
  files 2.x generates for the reference project — apart from bug fixes the
  [history](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-history.md) lists.
  New capabilities switch on only when you use them.
- **Your edits are kept**: regenerated files and generate-once files (service extensions, controllers, …) are kept
  apart, and a file you edited is recognised by its content hash and never overwritten.

---

## In the age of AI coding

AI coding tools write code fast, and for a prototype that is enough. For a business system that will be changed for
years, the problem comes next — who reviews thousands of lines, and how do you make the next change keep the same rules?

| AI alone | ElastiCORE + AI |
|---|---|
| AI writes thousands of lines; a person reviews them all | AI writes **tens of lines of DSL**; the person reviews only that ([measured](docs/benchmarks/change-amplification.md)) |
| A different result for the same request | **Same DSL → same code.** Regenerating is safe |
| An access check missing on one endpoint | Access, ownership, passwords — **the same code at every entry point** |
| A PR of hundreds of lines | The PR shows **the business rule that changed** |
| Wrong, and it still builds | A wrong DSL is **explained in a sentence** → the AI fixes it right away |
| A domain boundary crossed by a convenient import | The **build refuses** a reference across a bounded context nobody opened |
| Agents and BI that don't know what the data means | An [Apache Ossie](https://github.com/apache/ossie) semantic model from the same DSL — joins, what a code means, what "revenue" is |

The DSL guides are copied into the project's `blueprint/` on every build, so an AI reads the grammar of exactly the
version in use.

```
In blueprint/petstore/model/entity.yml, add a "delivered" step after shipping.
Add DELIVERED to OrderStatus and a deliver command to states. Don't edit generated code;
run ./gradlew elcore3 and check there are no warnings.
```

---

## Business rules in the DSL, too

Not only structure: **who may do what, how an order moves along, how a value is decided.**

```yaml
PurchaseOrder:
  meta: entity @root @expose(20) @event(Placed(total), Shipped, Cancelled) @owner(customerId)
        @access(read=owner, create=user, cancel=owner) -- order
  fields:
    status:   OrderStatus
    total:    money(12.2) @compute(sum(lines.subTotal))
    lines:    List<OrderLine> @OneToMany(mappedBy="order")
  states:
    status:
      initial: PLACED
      ship:    PLACED -> SHIPPED
      cancel:  PLACED -> CANCELLED
      delete:  CANCELLED          # only a cancelled order may be deleted

OrderLine:
  fields:
    unitCost: money(10.2) @snapshot(item.unitCost)    # the price when ordered
    subTotal: money(12.2) @compute(unitCost * quantity)
```

Real requests to the sample application, and its answers:

```
order (not signed in)                      → 401 "Sign in to create PurchaseOrder"
order (sends unit cost 0.01, status SHP)   → 200 unit cost 10.00, status PLC - the server decides
ship (as a customer)                       → 403 "ship PurchaseOrder is for the administrator"
cancel (a shipped order)                   → 422 "PurchaseOrder cannot cancel when status is SHIPPED; it must be [PLACED]"
cancel (shipped, Accept-Language: ko)      → 422 the same in Korean,  code: state.PurchaseOrder.status.cancel
delete (a shipped order)                   → 422 "PurchaseOrder cannot be deleted when status is SHIPPED; it must be [CANCELLED]"
someone else's order                       → 404
```

| Business information | DSL |
|---|---|
| Access and ownership | `@access(read=owner, create=user)`, `@owner(customerId)` |
| Sign-in and passwords | `@login`, `@password` (PBKDF2 hash; never in a response or a search) |
| Lifecycle | `states:` — state checks, moves, events, which states may be deleted (`delete:`), `canShip(dto)` / `canDelete(dto)` for screens |
| Derived values | `@snapshot`, `@compute` |
| Validation | `!`, `@min(0.01, '...')`, `@email`, `@past` — TS validation with the same words as the server's 400 |
| Business measures and terms | `metrics:` (revenue = total excluding cancelled), `@terms` |
| Default order | `@sort(name+)` — when a request names none |
| Error messages | a `code` on every error, words in the request's language (add only a `<domain>-messages_ko.properties`) |
| Calls outside | `port:` — HTTP, SQL, gRPC declared as interfaces |

---

## Domain boundaries, too (bounded contexts)

Say, for each domain (blueprint folder), what it uses from others and what it lets out — and **the build refuses a
reference across the boundary**. An AI editing the DSL that pulls in another domain's entity is stopped on the spot.

```yaml
# blueprint/catalog/env.yml                  # blueprint/shop/env.yml
config:                                      config:
  context:                                     context:
    publishes: [ProductDTO]                      uses: catalog
```

```
[elcore3] context: [shop] Order.product is catalog's entity Product: another context's entity is no field type
          - hold its id (productId: long), or use a DTO catalog publishes.
```

Events cross the same way. A process manager in one context names another context's event as `context:RootEvent`,
and runs after that context's transaction commits. It is checked like a type: the reacting context lists the other in
`uses`, and a context that declares `publishes` lists the events it lets out:

```yaml
# blueprint/billing/model/domainservice.yml  # blueprint/shop/env.yml
processManager:                              config:
  Receipting:                                  context:
    on: [shop:OrderPlaced]                       publishes: [OrderPlaced]
    uses: [Receipt]
```

The declarations also produce `blueprint/context-map.md`, a Mermaid context map. Declare nothing and nothing changes.

---

## Output you can trust

- **More than 1,400 automated tests** across the modules run on every build. The sample projects are generated, compiled and run.
- **Checked on every change**: on each pull request CI builds every module, which regenerates the reference project
  with 2.x's generator and must leave it exactly as committed. Then it compares the rule engine's output with 2.x's —
  per artifact (`150 MATCH / 0 DIFF / 2 ACCEPTED`, an entity by its fields) and over all 229 files 2.x generates for
  the reference project (`0 differ / 0 missing / 0 extra`: the four files a 2.x bug fix changes are accepted by name,
  one more differs only in import order). These two comparisons leave out each file's first line (its content hash
  and time), the wording of the generated-once marker, line endings and trailing blanks.
- **Checked by running**: access answers over HTTP, the SQL of business measures against a real database, every column
  of the semantic model against database metadata.
- **Measured**: adding a searchable field is one DSL line and 39 changed lines of generated code in 9 files; generated
  twice from the same DSL, 0 of 130 files differ (each file's first line, its content hash and time, aside) —
  [change amplification benchmark](docs/benchmarks/change-amplification.md).
- **Signed releases** on Maven Central, under the Apache License 2.0.
- **The generated code is in the security scope**: an access check that lets a user read someone else's rows, or a
  password in a response, is a vulnerability to report like one in the generator ([SECURITY.md](SECURITY.md)).

### Status

- **3.0 is in pre-release.** What 2.x generated stays as it was, apart from the bug fixes the history lists and, on
  Spring Boot 4, enums written for Jackson 3: a project whose `elasticore` block declares no `architecture`,
  `language` or `rules` runs 2.x's generator, whose output for the reference project is held to the committed tree,
  and the rule engine's `jpa-springboot` bundle (layered Java) is compared with it — both on every pull request.
- The v3 bundles — `jpa-springboot-v3` (layered, `-Pv3`), DDD, hexagonal, Kotlin, TypeScript — may still change
  between pre-releases; each change is listed with its reason in the
  [history](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-history.md) (in Korean).
- **3.0 is supported for three years from its first stable release**: bug and security fixes to the generator, the
  Gradle plugin and both runtime libraries, released from the `release/3.0` branch as `3.0.YYMMDD` versions. They are
  tested on a Spring Boot 4 version the Spring team still supports as open source at the time, and the Spring
  Boot 3 runtime library on 3.5.16, the last open-source Spring Boot 3 release ([SECURITY.md](SECURITY.md),
  [BRANCHING.md](BRANCHING.md)). Until that first stable release, fixes come in the next pre-release. How a release is
  made: [RELEASING.md](RELEASING.md).

## Getting started (install)

**Java 17 or later** for your project, and with Spring Boot 4 **Gradle 8.14 or later** (what Boot 4's Gradle plugin
requires). ElastiCORE is on Maven Central, the Gradle plugin included. The `build.gradle` above names Maven Central for
dependencies; name it for plugins too, in your project's `settings.gradle`:

```gradle
// settings.gradle
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Then follow the five steps above. To build it from source instead, run `./gradlew publishToMavenLocal` in a clone of
this repository — on a JDK 17 to 22; the build downloads the JDK 8 and 17 toolchains it compiles with if the machine
lacks them — and put `mavenLocal()` first in both lists.

## Documentation

| Document | Covers |
|---|---|
| [How it compares](docs/comparison.md) | ElastiCORE next to JHipster, Context Mapper, Sculptor, Spring Modulith, Amplication, Jmix, Wasp, Bootify, and an AI writing Spring code directly |
| [DSL](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-dsl-guide.md) | entities, DTOs, enums, lifecycles, business measures, value types |
| [Annotations](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-annotation-guide.md) | every annotation and what it generates |
| [Settings](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-env-guide.md) | `env.yml`, bounded contexts, multi-module, Spring Boot 3/4 |
| [Rule engine](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-rule-guide.md) | choosing an architecture, rule bundles, error codes and languages, Ossie export |
| [Rule development](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-rule-dev-guide.md) | writing and testing your team's rules |
| [Ports](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-dsl-port-guide.md) | HTTP, DB and gRPC ports |

The guides are copied into the project's `blueprint/` on every build. They are written in Korean today; English
editions are in progress — the code examples and generated code in them read the same in any language.

## Contributing · License

[CONTRIBUTING.md](CONTRIBUTING.md) · [Code of conduct](CODE_OF_CONDUCT.md) · [Reporting a vulnerability](SECURITY.md) · Apache License 2.0 ([LICENSE](LICENSE))
