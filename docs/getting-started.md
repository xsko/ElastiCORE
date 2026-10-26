<p align="right"><b>English</b> · <a href="getting-started.ko.md">한국어</a></p>

# Getting started

The [README](../README.md#into-your-project-in-five-steps) has the five steps; this page has what they leave out.

## What you need

**Java 17 or later** for your project, and with Spring Boot 4 **Gradle 8.14 or later** (what Boot 4's Gradle plugin
requires). ElastiCORE is on Maven Central, the Gradle plugin included. The README's `build.gradle` names Maven Central
for dependencies; name it for plugins too, in your project's `settings.gradle`:

```gradle
// settings.gradle
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

## Starting from Spring Initializr

Choose Spring Web, Spring Data JPA, Validation, Lombok and H2 (its default Boot 4.1.1 works too). Keep its
`build.gradle`, whose test lines its generated test needs, and add what it lacks: the `io.elasticore` plugin line, the
`elasticore` block, the two ElastiCORE dependencies and springdoc.

## On Spring Boot 3

Checked with 3.5.16. In the README's `build.gradle`:

| Spring Boot 4 | Spring Boot 3 |
|---|---|
| Boot plugin `4.0.8` | `3.5.16` |
| `io.elasticore.springboot4:elasticore-springboot4` | `io.elasticore.springboot3:elasticore-springboot3` |
| `spring-boot-starter-webmvc` | `spring-boot-starter-web` |
| springdoc `3.0.3` | `2.8.17` |

The generated code follows the Boot plugin's version by itself.

## Your part, at length

- **An application class**: `@SpringBootApplication` in `com.acme.shop` or a package above it, so that Spring finds the
  generated classes. Spring Initializr writes one in the package of its group and artifact (`com.acme` and `shop` give
  `com.acme.shop`).
- **Your business logic**, in `ProductService.java`. Like the controller it is generated once: it follows the model
  until you edit it, and from then on generating again leaves it alone (`SKIP(user-owned)` in the log).
- **Signing in.** Changing a product needs the `ADMIN` role (`@access(read=public)`: anyone reads, everything else is
  for the administrator). Your sign-in calls `SessionCurrentUser.signIn(request, userId, "ADMIN")` (package
  `io.elasticore.springboot3.security`, on Boot 4 too); with Spring Security, register a `CurrentUser` bean instead.
  Or let the model do it: `@signin` on the line of an entity with a `@login` and a `@password` field generates
  sign-in, sign-out and "who am I" — the pet store sample does it that way. What `@signin` asks of the entity (it is
  exposed, one per application, its password and login changed only by their owner or an administrator) is in the
  [annotation guide](../elasticore-project/elasticore-base/src/main/resources/elasticore-guide/elasticore-annotation-guide.md).

## Running it

`./gradlew bootRun`. `POST /api/shop/product/search` and `GET /api/shop/product/{id}` answer anyone; creating a product
without signing in answers 401 "Sign in to create Product", and a price of 0 answers 400 "price must be positive".
From here on a change starts in the model: `./gradlew build` and `bootRun` run `elcore3` before compiling, so the code
never lags behind the model.

## Building ElastiCORE from source

Run `./gradlew publishToMavenLocal` in a clone of this repository — on a JDK 17 to 22; the build downloads the JDK 8
and 17 toolchains it compiles with if the machine lacks them — and put `mavenLocal()` first in both repository lists
of your project.
