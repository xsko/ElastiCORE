<p align="right"><b>English</b> · <a href="README.ko.md">한국어</a></p>

# ElastiCORE

[![Maven Central](https://img.shields.io/maven-central/v/io.elasticore/elasticore-base.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.elasticore/elasticore-base)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x%20%7C%204.x-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**Write the business rules of a Spring Boot application in YAML — who may do what, how an order moves along, how a
value is decided — and ElastiCORE generates the code that enforces them, at every entry point, the same code every
time.**

![Two lines of YAML add a step to an order's lifecycle; the regenerated server enforces it](docs/images/demo.gif)

<sub>The pet store sample in this repository: its real model, the generator's real output and the server's real
answers, shortened to one line each.</sub>

- **Review the rule, not the code.** The new step above is 2 lines of DSL and 45 changed lines of generated Java in
  7 files. A developer — or an AI assistant — writes the 2; the reviewer reads the 2.
- **Rules hold at every entry point.** Access, ownership, lifecycles, derived values and validation are lines in the
  model; a request that breaks one gets a defined answer (401, 403, 409, 400) with an error code.
- **Regenerating is safe, even after you edit generated code.** Edit any generated file — a DTO, a mapper, a
  controller — and the next build keeps your edit and still brings in the model's changes: it merges them as git
  merges a branch, against what it generated last (`.elasticore/cache`, committed with the code). Only where your
  edit and the model change the same lines does it stop, with the usual `<<<<<<<` marks for you to resolve. So the
  code never drifts from the model, and you are never forced to choose between your edit and the next DSL change.
- **One model, the code you want.** Java or Kotlin; layered, DDD or hexagonal; Spring Boot 3 or 4; plus a typed
  TypeScript client with the server's validation messages.

**Built for** business systems that live for years and pass through many hands — orders, billing, contracts, back
offices. **Not for** a UI-first application, persistence that is not relational (JPA or MyBatis), or logic that does not
fit a declarative model. How it compares with JHipster, Spring Modulith, Context Mapper, Jmix and others:
[comparison](docs/comparison.md).

## See it run

```bash
git clone https://github.com/xsko/ElastiCORE.git && cd ElastiCORE
./gradlew :elasticore-project:elasticore-sample-petstore:run      # a JDK 17 to 22
```

Then open <http://localhost:8080> — a pet store with sign-in (`user`/`user`, `admin`/`admin`), a cart and orders.
Its server is generated from [162 lines of YAML](elasticore-project/elasticore-sample-petstore/src/main/resources/blueprint/petstore/model/);
by hand are placing an order (VAT, discount, date), signing up and the demo accounts — 7 classes, 269 lines with
their comments.
`/dsl-guide.html` shows, business area by business area, each DSL line next to the code it generated.

## Into your project in five steps

**① `build.gradle`** — a Spring Boot project, the plugin with its two settings, and what the generated code uses

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.8'      // 3.x works too - the version is read and followed
    id 'io.spring.dependency-management' version '1.1.7'
    id 'io.elasticore' version '3.0.261030-rc5'
}

repositories {
    mavenCentral()
}

elasticore {
    architecture = 'hexagonal'    // layered | ddd | hexagonal
    language     = 'java'         // java | kotlin
}

dependencies {
    implementation 'io.elasticore:elasticore-base:3.0.261030-rc5'
    implementation 'io.elasticore.springboot4:elasticore-springboot4:3.0.261030-rc5'

    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    runtimeOnly 'com.h2database:h2'               // an in-memory database to try it with
}
```

The plugin is on Maven Central too, so `settings.gradle` names it for plugins:
`pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }`. Java 17 or later; with Spring Boot 4,
Gradle 8.14 or later. Starting from Spring Initializr, or on Spring Boot 3: [getting started](docs/getting-started.md).

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

**③ Generate** — `./gradlew elcore3`. The code appears under `src/main/java/com/acme/shop/`:

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

**④ Your part** — an `@SpringBootApplication` class in `com.acme.shop` or above it; your business logic in
`ProductService.java`; and signing in: changing a product needs the `ADMIN` role, so your sign-in calls
`SessionCurrentUser.signIn(request, userId, "ADMIN")` (package `io.elasticore.springboot3.security`, on Boot 4 too)
— or, with Spring Security, you register a `CurrentUser` bean.

**⑤ Run** — `./gradlew bootRun`. Anyone may search and read products; creating one without signing in answers 401
"Sign in to create Product", a price of 0 answers 400 "price must be positive". `build` and `bootRun` run `elcore3`
first, so from here on a change starts in the model. Code you add to a generated file stays through every
regeneration (the merge above); commit `.elasticore/cache` with the generated files.

## What the model holds

| Business information | DSL |
|---|---|
| Fields: storage, validation, search, exposure | `name: string(100)! @search(like)` — one line is one field |
| Access and ownership | `@access(read=owner, create=user)`, `@owner(customerId)` |
| Sign-in and passwords | `@signin`, `@login`, `@password` (PBKDF2 hash; never in a response or a search) |
| Lifecycle | `states:` — `ship: PLACED -> SHIPPED`, which states may be deleted, `canShip(dto)` for screens |
| Derived values | `@snapshot(item.unitCost)`, `@compute(unitCost * quantity)` |
| Business measures and terms | `metrics:` — `revenue: sum(PurchaseOrder.total) where status != CANCELLED`, `@terms` |
| Domain boundaries | `context: uses / publishes` — the build refuses a reference across a boundary nobody opened |
| Calls outside | `port:` — HTTP, SQL, gRPC declared as interfaces |
| Your team's own rules | a Java class of `@Rule` methods, plugged into generation like the built-in ones |

The same model, read differently by one setting: `language = 'kotlin'`, `architecture = 'ddd'`, the Spring Boot
plugin's version, `./gradlew elcore3ts` for the TypeScript client, or only some rules —
`publisher: rules: [jpa-springboot-kt, ddd-aggregate, …]` in `env.yml`. The data's meaning goes out too, as an
[Apache Ossie](https://github.com/apache/ossie) semantic model for BI tools and AI agents.

**[Take the tour](docs/tour.md)** for each of these at length, with the generated code and real requests.

## Status

**3.0 is in pre-release** ([on Maven Central](https://central.sonatype.com/artifact/io.elasticore/elasticore-base)).
A project on the Gradle plugin gets the 3.0 code unless it keeps 2.x's: `elasticore { output = 'v2' }` generates what 2.x generated, apart
from the bug fixes the history lists and, on Spring Boot 4, enums written for Jackson 3. A project that holds code 2.x
generated and chose neither is stopped before anything is rewritten, with that choice to make
([migration guide](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-migration-guide.md)).
On every pull request the 2.x generator's output for the reference project must stay as committed, and the rule
engine's 2.x-compatible bundle is compared with it file by file ([the details](docs/tour.md#status)). More than 1,400 automated tests run on every build, and the sample projects
are generated, compiled and run. The v3 bundles may still change between pre-releases, each change listed with its
reason in the [history](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-history.md).
3.0 is supported for three years from its first stable release ([SECURITY.md](SECURITY.md),
[BRANCHING.md](BRANCHING.md)).

## Documentation

| Document | Covers |
|---|---|
| [Getting started](docs/getting-started.md) | Spring Initializr, Spring Boot 3, building from source |
| [Tour](docs/tour.md) | the DSL's six habits, business rules, rulesets, bounded contexts, what is checked |
| [How it compares](docs/comparison.md) | JHipster, Context Mapper, Sculptor, Spring Modulith, Amplication, Jmix, Wasp, Bootify, and an AI writing Spring code directly |
| [DSL](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-dsl-guide.md) | entities, DTOs, enums, lifecycles, business measures, value types |
| [Annotations](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-annotation-guide.md) | every annotation and what it generates |
| [Settings](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-env-guide.md) | `env.yml`, bounded contexts, multi-module, Spring Boot 3/4 |
| [Rule engine](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-rule-guide.md) | choosing an architecture, rule bundles, error codes and languages, Ossie export |
| [Rule development](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-rule-dev-guide.md) | writing and testing your team's rules |
| [Ports](elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-dsl-port-guide.md) | HTTP, DB and gRPC ports |

The guides are copied into the project's `blueprint/` on every build, so an AI assistant reads the grammar of exactly
the version in use. They are written in Korean today; English editions are in progress.

## Contributing · License

[CONTRIBUTING.md](CONTRIBUTING.md) · [Code of conduct](CODE_OF_CONDUCT.md) · [Reporting a vulnerability](SECURITY.md) · Apache License 2.0 ([LICENSE](LICENSE))
