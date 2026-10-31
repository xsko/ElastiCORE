# ElastiCORE DDD Rule Set Customization Guide (Advanced)

> **If you are writing rules for the first time, start with `elasticore-rule-dev-guide.md`.** That document alone takes you through adding and removing annotations, your own YAML markers, build checks and tests.
> This document covers the next step (new files, switching things off, team settings, internals).

This is how you **add your team's rules** to the code that the DDD (hexagonal) bundle generates.
Instead of editing generated files one by one, you add generation rules, so they apply the same way to every domain and every entity.

**Before you read**: this guide assumes you have followed the examples of the "ElastiCORE Hexagonal ERP Guide" (Partner, Item, SalesOrder, Stock, Invoice).
Every example here is added to that project, and a project built from only the code in this document was built and tested.
It is written for Java; the differences in Kotlin are collected in chapter 10.

| This document | Other documents |
|---|---|
| How to **create and add** your own rules (example-driven) | Full list of the rule API: `elasticore-rule-guide.md` chapters 3-6 |
| | Choosing a bundle and the default rules: `elasticore-rule-guide.md` chapter 2 |

---

## 0. When you are done

The ERP project has one rule module (`acme-rules`), and every `./gradlew build` does the following automatically.

| Example | What you use in the DSL or settings | Result |
|---|---|---|
| 1. Switch off (chapter 4) | `suppress: [...]` in `env.yml` | An unwanted generated file disappears (method only, not applied to the ERP) |
| 2. Role markers (chapter 5) | None (read from the model) | `@AggregateRoot` on roots, `@Entity` on members, `@ValueObject` on value objects, `@Identity` on identifier fields (jMolecules) |
| 3. New DSL word + new file (chapter 6) | `@topic(erp.sales)` in the root meta | An `ErpEventTopics` class: the message topic name of each event |
| 4. Model check (chapter 7) | `acme.requireTopic: true` in `env.yml` | A warning on a rule violation, or a build failure |

---

## 1. How rules generate code

```
DSL(yml) ──▶ Spec ──▶ rules ──▶ skeleton(.tmpl) ──▶ file
           (model)   (decide)   (file outline)
```

| Term | Meaning | Example |
|---|---|---|
| **Domain** | One folder under `blueprint/` (the `env.yml` and `model/*.yml` inside it). The same word as the DDD "domain", but here it means this folder unit | `blueprint/erp` -> domain `erp` |
| **Spec** | The model that rules read. `EntitySpec` for an entity, `FieldSpec` for a field, `DomainSpec` for a whole domain | `e.aggregateRoot()`, `e.events()` |
| **Rule** | One method with `@Rule`. It reads the model and only decides "what to write" | "If it is a root, put `@AggregateRoot` on it" |
| **Rule set** | A class that collects rule methods (implements `CodeRules`) | `AcmeDddRules` |
| **Bundle** | A group of several rule sets. Chosen by `architecture` and `language` in the build | `hexagonal-springboot` |
| **target** | The name of the **kind of file** a rule contributes to | `entity`, `behavior`, `dto` ... (table below) |
| **Skeleton** | The outline of a file. Rules fill blanks such as `${packageName}` | `elasticore-template/v3/eventTopics.tmpl` |

A rule does not write a file directly. It says "this annotation on this class" or "this value in this blank", and the generator does the import cleanup and the file writing.
So the same rule applies to both Java and Kotlin, and several rules can contribute to one file.

### Kinds of files (targets) - in the hexagonal ERP

The first word of each line in the generation log (`./gradlew elcore3`) is the target: `entity  src/main/java/.../Partner.java  WROTE`.

| target | Example (ERP) | Whose | Rule that draws the class annotations |
|---|---|---|---|
| `entity` | `Partner.java`, and `SalesOrderBase.java` for a split root | Generator | `v3-file-vars` (tail 90) |
| `behavior` | `SalesOrder.java` (the developer class of a split root) | **Developer** | `ddd-behavior-entity` (head 10) |
| `dto`, `srchdto` | `SalesOrderDTO.java`, `PartnerSrchDTO.java` | Generator | `v3-dto-file`, `v3-srchdto-file` (tail 90) |
| `event` | `SalesOrderShippedEvent.java` | Generator | `ddd-event` (head 10) |
| `repositoryPort`, `queryPort`, `usecase` | `PartnerRepositoryPort.java`, `PartnerQueryPort.java`, `PartnerUseCase.java` | Generator | `ddd-repository-port`, `ddd-query-port`, `hex-usecase` (head 10) |
| `domainServiceBase`, `processManagerBase` | `ShippingDomainServiceBase.java`, `BillingProcessManagerBase.java` | Generator (ports, listeners, abstract `handle`) | `ddd-domain-service-base`, `ddd-process-manager-base` (10 - per domain) |
| `domainService`, `processManager` | `ShippingDomainService.java`, `BillingProcessManager.java` | **Developer** | `ddd-domain-service`, `ddd-process-manager` (once 10 - per domain) |
| `service` / `service_extend` | `PartnerCoreService.java` / `PartnerService.java` | Generator / **Developer** | None |
| `control` | `PartnerController.java` | **Developer** | None |
| `invariants`, `removalCheck`, `factory`, `specifications`, `repositoryAdapter` | `ItemInvariants.java`, `PartnerRepositoryAdapter.java` ... | Generator (`factory` is the **developer's**) | `ddd-invariant`, `ddd-invariant-removal`, `ddd-factory`, `ddd-specification`, `ddd-repository-adapter` (head 10) |
| `mapper`, `repository`, `repositoryHelper`, `outbox...` | `ErpMapper.java`, `PartnerRepository.java` ... | Generator | None |

You cannot put a class annotation on a file marked "None" (if you try, nothing happens).
A file marked "once" is created **per domain**, not per entity. Its rule takes `(DomainSpec d)`, and you tell which of several files it is with `ctx(d).artifactName()` (written as `(EntitySpec e)`, it does not run and a warning appears). The full list of targets is in the output of `./gradlew elcore3 -PlistRules` (chapter 3).
In Kotlin, `kt-entity-file`, `kt-dto-file` and `kt-srchdto-file` (tail 90) draw `entity`, `dto` and `srchdto`.

### Roots and members

`e.aggregateRoot()` means "neither a member nor a value object".

| Entity | Decision (the first one that matches, from the top) | ERP example |
|---|---|---|
| Root | It has `@root` (or `@aggregate` without a value) on itself | All five ERP roots (`Partner`, `Item`, `SalesOrder`, `Stock`, `Invoice`) |
| Member | It has `@aggregate(X)` on itself | |
| Member | Another entity holds it in a relation marked `@cascade` | |
| Member | **Exactly one** entity marked `@root` holds it as `List<X> @OneToMany` (with two or more, it does not decide -> set it with `@aggregate(X)`) | `SalesOrderLine` |
| Value object | `@embeddable` | (none) |
| Root | Every other entity (even without `@root`) | |

So even when an entity that is not `@root` has a `List<X> @OneToMany`, `X` does not become a member. If that is not what you want, put `@root` on the holder or `@aggregate(...)` on `X`.

---

## 2. What you change and how

| What you want | How | Example |
|---|---|---|
| Remove some of the default generated files | `suppress` in `env.yml` | Chapter 4 |
| Add something to generated classes and fields | Add a rule to an existing target | Chapter 5 |
| Create your own words in the DSL | A rule reads them with `e.metaValue("word")`, `f.dslValue("word")` | Chapter 6 |
| Create a new file | `ArtifactContributor` (per entity) / `DomainArtifactContributor` (per domain) + a skeleton | Chapter 6 |
| Give team settings to rules | Write them under `config:` in `env.yml` and read with `e.config("key")` | Chapter 7 |
| Check that the model follows the team rules | `ModelCheck.warn` / `ModelCheck.fail` | Chapter 7 |
| Replace a default skeleton (.tmpl) entirely | **Don't.** You would have to follow it every time the version goes up. Switch it off (chapter 4) and create a new file (chapter 6) | - |

What happens when you edit a generated file by hand:

| File | If you edit it by hand |
|---|---|
| The generator's (`entity`, `dto` ...) | The next generation **skips it and tells you** (`-Pforce` overwrites it). The edit stays out of step with the DSL, so use a rule for what you want to change |
| The developer's (`behavior`, `control`, `service_extend` ...) | Left as it is. As long as you have never edited it, it is rewritten to follow the DSL |

---

## 3. Preparation: create the rule module

Put rules in a **separate module**. The generator runs **before** the application is compiled, so it cannot see rules placed in the application sources.

```
erp/
├── settings.gradle
├── build.gradle                 ← connects the rule module to the generator
├── acme-rules/                  ← new module: our rules
│   ├── build.gradle
│   └── src/
│       ├── main/java/com/acme/rules/AcmeDddRules.java
│       ├── main/resources/elasticore-template/v3/eventTopics.tmpl   ← Java skeleton
│       ├── main/resources/elasticore-template/kt/eventTopics.tmpl   ← Kotlin skeleton
│       ├── test/java/com/acme/rules/AcmeDddRulesTest.java
│       ├── test/java/com/acme/rules/NoDynamicUpdateRules.java       ← chapter 4 example
│       ├── test/java/com/acme/rules/PerRootExampleRules.java        ← 6.7 example
│       └── test/resources/elasticore-template/v3/rootEvents.tmpl    ← 6.7 example
└── src/ …                       ← the existing ERP application
```

Add one line to **`settings.gradle`**:

```gradle
include 'acme-rules'
```

**File: `acme-rules/build.gradle`**

```gradle
plugins {
    id 'java-library'
}

java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }

repositories {
    mavenCentral()
}

dependencies {
    // The generator runs these rules; it is not part of the application.
    compileOnly 'io.elasticore:elasticore-base:3.0.261031-rc7'

    testImplementation 'io.elasticore:elasticore-base:3.0.261031-rc7'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.2'
}

tasks.named('test') { useJUnitPlatform() }
```

Write the `elasticore-base` version **the same as** `id 'io.elasticore' version '...'` in the root `build.gradle`. If the API the rules were compiled against differs from the generator's, it cannot find methods at run time.

You fill in the rule set class in chapters 5-7. For now, create only an empty shell, so you can build at each later step.

**File: `acme-rules/src/main/java/com/acme/rules/AcmeDddRules.java`** (empty shell - the finished version is in appendix A)

```java
package com.acme.rules;

import io.elasticore.base.model.pub.rules.CodeRules;

/** ACME's additions to the DDD rules. Filled in chapters 5-7. */
public class AcmeDddRules implements CodeRules {
}
```

Change two places in the **root `build.gradle`**:

```gradle
elasticore {
    architecture = 'hexagonal'
    language     = 'java'
    rules        = ['com.acme.rules.AcmeDddRules']     // our rules (added to the default bundle)
}

dependencies {
    elasticore project(':acme-rules')                     // where the generator finds our rules
    implementation 'org.jmolecules:jmolecules-ddd:1.10.0'  // the annotations chapter 5 adds
    // ... the existing dependencies stay as they are
}
```

| Line | Meaning |
|---|---|
| `rules = [...]` | The full names of the rule set classes. They are **added after** the bundle that the architecture and language chose (you do not need to write the bundle again). Use it together with `architecture` - without it, generation runs in 2.x compatibility mode and neither the DDD rules nor the `v3/` skeletons are used |
| `elasticore project(':acme-rules')` | `elasticore` is **the generator's classpath**. It is not part of running the application. This module is compiled before generation |

> **The difference from `elasticore.config.publisher.rules` in `env.yml`**: writing `rules: [...]` in `env.yml` **replaces** the bundle.
> Then you must list the bundle too, as in `rules: [hexagonal-springboot, com.acme.rules.AcmeDddRules]`, and for Kotlin you must list `hexagonal-springboot-kt`.
> For rules that apply to every domain, `rules` in the build is simpler and leaves no room for mistakes. Use `env.yml` only when the rules apply to a specific domain.

### See which rules run now: `-PlistRules`

```bash
./gradlew elcore3 -PlistRules
```

After you finish chapters 5-7, it looks like this (for now there are no `acme-` lines):

```
[rules] erp: hexagonal-springboot + com.acme.rules.AcmeDddRules
  entity
        3 head  ddd-reference-context                DddReferenceRules
        5 head  v3-class-annotations                 JpaEntityAnnotationRules
       …
        0 head  acme-model-check                     AcmeDddRules
        0 head  acme-role                            AcmeDddRules
        5 field jpa-field-desc                       JpaEntityBaseRules
       …
        0 field acme-identity                        AcmeDddRules
       …
       90 tail  v3-file-vars                         JpaEntityFileRules
```

| Column | Meaning |
|---|---|
| `behavior`, `entity` ... | The target (kind of file) |
| `5`, `10` ... | The `order`. Smaller runs first within the same stage |
| `head` / `field` / `tail` | The stage. For each file, the `head` rules run, then the `field` rules for each field, then the `tail` rules |
| `once` | A rule per domain, enum or port. It runs once, in `order`, with no stage |
| `acme-role` | The rule id. The name you use in `suppress` |
| `AcmeDddRules` | The class that holds the rule |

This command neither creates nor deletes files. For each domain it shows the rules selected now, **in the order they actually run**.
Our rules (`acme-...`) run **after** all the ElastiCORE rules in each stage. So you do not need to write `order`; you use it only when our rules need an order among themselves.

---

## 4. Example 1 - switch off a default generated file

Suppose your company already has a common error handler (`@RestControllerAdvice`), so you do not need the `ErpApiErrors` that is created for each domain.

> **Read this chapter for the method only. Do not apply it to the ERP example.** The ERP has no common error handler, so switching it off turns every 404, 409 and 422 response into a 500.

1. In `./gradlew elcore3 -PlistRules`, find the rule that creates that file (target `apiErrors`):
   ```
     apiErrors
          10 once  spring-api-errors                    ApiErrorRules
   ```
2. Write it under `config:` in `env.yml`:
   ```yaml
   elasticore:
     config:
       publisher:
         suppress: [spring-api-errors]
   ```
3. An `ErpApiErrors.java` that already exists is the developer's, so it is not deleted. Delete it yourself if you do not need it.

To switch it off in every domain, write it once in the build instead of in each domain (it is merged with the `suppress` of each `env.yml`):

```gradle
elasticore {
    suppress = ['spring-api-errors']
}
```

- If you switch off **all** the rules of one kind of file, that file is not created (`SKIP(dontMake)` in the generation log).
- If you switch off only some, only that part is dropped. Example: switching off `jpa-comment` removes only the `@Comment` on entity fields.
- **Behavior changes.** `ErpApiErrors` is the file that decides "404 for a missing target, 409 for a duplicate or a conflict with the current state, 422 for a business rule violation". Switch it off and your company's handler must decide those responses (otherwise 500).
- The refusals that happen before a request reaches a controller (a body that is not JSON, a value of the wrong type, a path or method that does not exist - the same shape with a `code`) also go away. That is because the runtime's `RequestShapeErrors`, which handles those responses, is brought in by `ErpApiErrors` with `@Import`. To use it in your company's handler too, add one line to that handler: `@Import(io.elasticore.springboot3.validation.RequestShapeErrors.class)` (if you give your company's handler an `@Order`, it goes first). The `NotFoundException` that the generated controller's `findById` and `delete` throw for a missing row is `@ResponseStatus(404)`, so it stays 404 even if you switch it off.

> **Do not switch off**: rules that fill in the outline of a file - rules that fill blanks such as `${packageName}` and `${className}` (the rules in the last column of the table in chapter 1, and the `*-vars` and `*-file` rules such as `controller-vars` and `v3-service-vars`). If you switch off only some of them, the blanks stay empty and the file breaks.
> Use `suppress` to switch off a rule that "adds something more", or to remove a file entirely by switching off all the rules of that one file.

### Remove only one of the things a rule does

`suppress` works per rule. When one rule does several things and you want to remove **only one**, our rule removes it afterwards.
Example: remove only the `@DynamicUpdate` that is put on every entity (`v3-class-annotations` puts on several annotations, so you cannot switch it off as a whole):

**File: `acme-rules/src/test/java/com/acme/rules/NoDynamicUpdateRules.java`** (kept in tests only because it is an example. To use it for real, move it to `src/main/java` and add it to the `rules` list in the build)

```java
package com.acme.rules;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/** Takes an annotation the built-in rules put on every entity back off. */
public class NoDynamicUpdateRules implements CodeRules {

    /** Our rules run after ElastiCORE's, so what they take off stays off - on a split root's classes too. */
    @Rule("acme-no-dynamic-update")
    void noDynamicUpdate(EntitySpec e) {
        ctx(e).removeClassAnnotation("org.hibernate.annotations.DynamicUpdate");
    }
}
```

`c.removeClassAnnotation(...)`, `c.removeField(f)` and `c.field(f).removeAnnotation(...)` work the same way. Our rules run after the default rules, so what you removed is not put back, and it is also removed from the developer class of a split root (`SalesOrder.java`).
(You can also remove `@DynamicUpdate` with the DSL: `@dynamicupdate(false)` in the entity meta.)

---

## 5. Example 2 - mark every class with its DDD role (jMolecules)

[jMolecules](https://github.com/xmolecules/jmolecules) is a library that marks DDD roles such as "this class is an aggregate root" with annotations.
Tools such as ArchUnit and IDEs read these markers and find boundary violations.
The ElastiCORE model already knows the roles (`@root`, member, `@embeddable`, `@id`), so nobody needs to add them by hand.

Below is part 1 of `AcmeDddRules.java` (the whole file is in 6.6).

Fill the empty shell from chapter 3 with the following (see appendix A for the imports; the IDE fills them in).

```java
public class AcmeDddRules implements CodeRules {      // DomainArtifactContributor is added in 6.3

    private static final String JMOLECULES = "org.jmolecules.ddd.annotation.%s";

    /** Each entity class says its DDD role. A root split in two gets it on both of its classes. */
    @Rule("acme-role")
    void role(EntitySpec e) {
        if (e.hasMeta("abstract")) return;   // a base class for others, not a role of its own
        SourceCode c = ctx(e);
        if (e.hasMeta("embeddable")) {
            c.ensureClassAnnotation(AnnoCode.of(JMOLECULES, "ValueObject"));
        } else if (e.aggregateRoot()) {
            c.ensureClassAnnotation(AnnoCode.of(JMOLECULES, "AggregateRoot"));
        } else {
            // Same simple name as jakarta.persistence.Entity: written in full.
            c.ensureClassAnnotation(AnnoCode.of(JMOLECULES, "Entity").qualified());
        }
    }

    /** A field rule: called once per field of each entity. */
    @Rule("acme-identity")
    void identity(EntitySpec e, FieldSpec f) {
        if (!f.hasDslAny("id", "pk")) return;
        ctx(e).field(f).ensure(AnnoCode.of(JMOLECULES, "Identity"));
    }
```

| Code | Meaning |
|---|---|
| `@Rule("acme-role")` | Runs when an entity file is made (if you do not write `target`, it is `"entity"`). The text in parentheses is the id - name our rules so they start with `acme-`, so they do not collide with the default rules |
| The method's **parameters** | They decide when it is called. `(EntitySpec e)` is called once per file; `(EntitySpec e, FieldSpec f)` is called once **per field** (`acme-identity`) |
| `ctx(e)` | The file being made now (`SourceCode`) |
| `ensureClassAnnotation(...)` | An annotation on the class. If it is already there, it is not added twice. **Imports are automatic** |
| `ctx(e).field(f).ensure(...)` | An annotation on that field |
| `AnnoCode.of(JMOLECULES, "Entity").qualified()` | The name is the same as `jakarta.persistence.Entity`, so it is written in full -> `@org.jmolecules.ddd.annotation.Entity` |
| `e.aggregateRoot()` | Is it a root (false for a member or a value object) |
| `e.hasMeta("abstract")` | An `@abstract` entity is a frame that other entities inherit, so it has no role |

### A root split into two files

In the DDD bundle, a **root that is not `@editable`** is split into two files. In the ERP, `SalesOrder`, `Stock` and `Invoice` are split, and `Partner` and `Item`, which are `@editable`, are one file.

| File | target | Whose |
|---|---|---|
| `SalesOrderBase.java` (fields) | `entity` | Generator (rewritten every time) |
| `SalesOrder.java` (rule methods) | `behavior` | **Developer** |

Our rule with `target = "entity"` applies to **both files**. When you write a rule, you do not need to care whether it is split.
(When you need to know whether it is split, use `e.isSplitForBehavior(e.getName())`. The condition is "a root that is not `@editable` and has a single identifier, or an entity with `@behavior`", and it is not split if it is `@abstract`, `@embeddable` or `@rollup`, or has a `@discriminator` field.)

### You do not need to care about order

Our rules run **after** all the ElastiCORE rules in each stage (`head` per file -> `field` per field -> `tail`).
So what our rules add or remove stays. Write `order` only when our rules need an order among themselves.

> It is not put on the files marked "None" in the table of chapter 1. You can confirm it right away by rendering in a rule test (chapter 9).

### A file of yours that already exists

Even a file that is the **developer's**, such as `SalesOrder.java`, is reached by a rule you add later if it is an owned file made by the generator (a file with the line `// Initially generated by ElastiCORE; no longer managed` near its first line).
If you have not touched it, it is generated again; if you edited it, it is merged when the part the rule changes does not overlap the lines you edited (`MERGED(your edits kept)`, `elasticore-env-guide.md` 9-2). The same goes for controllers and services.

If they overlap, the file is left as it is (`KEPT(yours ...)`) and the generator tells you how many places overlap (`the DSL changes this file of yours where you changed it too`) - you move that part by hand.
Rules do not reach a file you edited in a project with merging turned off (`merge = false`), or a file made by a version before merging and then edited by you (`SKIP(user-owned)`).

If a file left this way is the **developer class of a split entity**, the class annotations that are missing are reported separately. For example, if it is `Shipment.java` of a new root `Shipment`:

```
[elcore3] src/main/java/com/acme/erp/domain/model/Shipment.java: the rules also put these on the class,
which this file does not have - it is yours, so add them by hand if they apply:
    @AggregateRoot
```

This notice appears only for entity classes. When other developer files such as controllers and services are left, share with your team what is missing.

The `SalesOrder.java`, `Stock.java` and `Invoice.java` that you copied whole from the hexagonal guide do not have that marker line, so there is no notice either. Add the annotation to the three files yourself:

```java
import org.jmolecules.ddd.annotation.AggregateRoot;

@AggregateRoot
@Entity
@Table(name = "erp_stock", …)
public class Stock extends StockBase {
```

A file the generator made, like `Shipment` above, gives you the notice; these three files you edit by hand, without a notice.

---

## 6. Example 3 - create a new file with our own DSL word (`@topic`)

When you send domain events to a message broker such as Kafka, each event needs a **topic name**.
Instead of scattering this as strings through the code, we write it once in the DSL and generate a constants class.

### 6.1 Write it in the DSL

Add `@topic(erp.sales)` to the end of the `SalesOrder` meta in `src/main/resources/blueprint/erp/model/entity.yml`:

```yaml
  SalesOrder:
    meta: entity @root @expose(20) @event(Confirmed(totalAmount), Shipped(orderNo, shipWarehouse, totalAmount), Cancelled) @topic(erp.sales) @Index(name="uk_erp_sales_order_no", columnList="order_no", unique="true")
```

ElastiCORE does not treat an unknown word as an error; it **only keeps** it. Nothing appears in the generated code, and a rule reads `erp.sales` with `e.metaValue("topic")`.
A word put on a field (`bizNo: string(12) @masked`) is read with `f.hasDsl("masked")` and `f.dslValue("masked")`.

> A misspelling (`@topci`) causes no error and is silently ignored. If the word is important, add a check rule as in chapter 7.
> Name it so it does not collide with words ElastiCORE already uses (`@event`, `@search`, `@label` ... `elasticore-annotation-guide.md`). If you are worried, add a team prefix (for example `@acmeTopic`).

### 6.2 The file that will be generated

```java
public final class ErpEventTopics {

    public static final String SALES_ORDER_CONFIRMED = "erp.sales.confirmed";
    public static final String SALES_ORDER_SHIPPED = "erp.sales.shipped";
    public static final String SALES_ORDER_CANCELLED = "erp.sales.cancelled";
    …
    public static String topicOf(String eventType) { … }   // full name of the event class -> topic
}
```

### 6.3 Declare the file - `DomainArtifactContributor`

> The pieces in 6.3-6.4 are for explanation. The helper methods used here (`topicOf`, `className`, `upperSnake`, `kebab`) and `implements … DomainArtifactContributor` are in appendix A,
> so to build it, replace the file with appendix A, as in 6.6.

The generator **does not know in advance** about a new kind of file. The rule set must tell it "this domain has a file like this".

| If the file is | Interface to implement | Method |
|---|---|---|
| One per domain | `DomainArtifactContributor` | `artifactsFor(DomainSpec d)` |
| One per entity | `ArtifactContributor` | `artifactsFor(EntitySpec e)` (6.7) |

```java
    @Override
    public List<ContributedArtifact> artifactsFor(DomainSpec d) {
        for (EntitySpec e : d.entities()) {
            if (topicOf(e) != null && !e.events().isEmpty()) {
                return Collections.singletonList(
                        ContributedArtifact.of("eventTopics", "event", className(d)).orNamespace("entity"));
            }
        }
        return Collections.emptyList();
    }
```

The values of `ContributedArtifact.of("eventTopics", "event", "ErpEventTopics")`:

| Value | Meaning |
|---|---|
| `"eventTopics"` | The **new target name**. It is the `target` of the rule that fills this file, and the skeleton file name (`eventTopics.tmpl`) |
| `"event"` | The **key that finds the package** the file goes in (`namespace` in `env.yml`). With `namespace: base: com.acme.erp`, it is `com.acme.erp.domain.event`. It is not the same thing as the target `event` (the event classes); the same key was used to put them in the same package |
| `"ErpEventTopics"` | The class (file) name |
| `.orNamespace("entity")` | The key to use instead in a project that has no `event` key |

The package key can be any name under `namespace` in `env.yml`. The keys that `namespace: base: com.acme.erp` creates:

| Key | Package |
|---|---|
| `entity`, `enumeration` | `…domain.model` |
| `event` | `…domain.event` |
| `domainService`, `domainRepository` | `…domain.service`, `…domain.repository` |
| `service`, `dto` | `…application`, `…application.dto` |
| `inboundPort` / `queryPort`, `port` | `…application.port.in` / `…application.port.out` |
| `repository`, `persistence` | `…infrastructure.persistence` |
| `control` | `…adapter.in.web` |

If you need a place of its own for your file, write one more line in `namespace` (`messaging: com.acme.erp.infrastructure.messaging`) and use that key. You can use it together with `base`.
If there is nothing to create, return an empty list. If this domain has no `@topic`, there is no file.

### 6.4 Fill the blanks - the rule

```java
    @Rule(value = "acme-event-topics", target = "eventTopics")
    void eventTopics(DomainSpec d) {
        SourceCode c = ctx(d);
        c.requireSyntaxAnyOf("acme-event-topics", Syntax.JAVA, Syntax.KOTLIN);
        boolean kotlin = c.syntax() == Syntax.KOTLIN;

        for (EntitySpec e : d.entities()) {
            String topic = topicOf(e);
            if (topic == null) continue;
            for (String event : e.events()) {
                String constant = upperSnake(e.getName() + event);
                String eventClass = e.eventPackage() + "." + e.eventClassName(event);
                if (kotlin) {
                    c.varLine("constantList", "    const val %s = \"%s.%s\"", constant, topic, kebab(event));
                    c.varLine("entryList", "        \"%s\" to %s,", eventClass, constant);
                } else {
                    c.varLine("constantList", "    public static final String %s = \"%s.%s\";", constant, topic, kebab(event));
                    c.varLine("entryList", "        TOPICS.put(\"%s\", %s);", eventClass, constant);
                }
            }
        }
        // ${packageName} is already the package artifactsFor chose.
        c.var("className", className(d));
    }
```

| Code | Meaning |
|---|---|
| `(DomainSpec d)` | Called once per domain |
| `e.events()` | The `@event` names of this root: `[Confirmed, Shipped, Cancelled]` |
| `e.eventClassName(event)`, `e.eventPackage()` | The name and package of the generated event class: `SalesOrderShippedEvent`, `com.acme.erp.domain.event` |
| `c.var("name", value)` | Fills `${name}` in the skeleton |
| `c.varLine("name", ...)` | Adds one line at a time at the place of `${name}` |
| `requireSyntaxAnyOf(...)` | The languages this rule can be used with. If it is used to generate another language, it records that file as a failure (`ERROR`) instead of silently making a strange file |
| `${packageName}` | The rule does not fill it. The generator puts in the package chosen in `artifactsFor` beforehand, so the file location and the `package` line cannot disagree |

### 6.5 The skeleton

Put the skeleton under `src/main/resources/elasticore-template/` of the rule module, in the **folder for the language**, named by the **target name**.

| Language | Path |
|---|---|
| Java | `elasticore-template/v3/eventTopics.tmpl` |
| Kotlin | `elasticore-template/kt/eventTopics.tmpl` (chapter 10) |

**File: `acme-rules/src/main/resources/elasticore-template/v3/eventTopics.tmpl`**

```java
package ${packageName};

import java.util.HashMap;
import java.util.Map;

/**
 * ${className} - where each domain event of the domain is published.
 *
 * Generated from @topic by the ACME rules. Change the DSL, not this file.
 */
public final class ${className} {

${constantList}

    private static final Map<String, String> TOPICS = new HashMap<>();

    static {
${entryList}
    }

    private ${className}() {
    }

    /** The topic of an event, by its class name as the outbox records it; null when it has none. */
    public static String topicOf(String eventType) {
        return TOPICS.get(eventType);
    }
}
```

If you put `${importList}` or `${classAnnotationList}` **on a line of its own** in the skeleton, what our rules did with `c.addImport(...)` and `c.ensureClassAnnotation(...)` is put in that place automatically
(when all the rules for that file belong to our rule set - one whose package is not `io.elasticore`. Do not name your rule set package `io.elasticore`).
The example above has fixed imports, so they are written out directly.

The first line of the skeleton decides who manages this file.

| First line of the skeleton | The generated file is |
|---|---|
| (no marker) - the example above | **The generator's**. Rewritten when the DSL changes. If you edit it by hand, your edit is kept and the DSL change is merged (if both sides change the same line, it is a conflict and the build stops, `elasticore-env-guide.md` 9-2) |
| `// Initially generated by ElastiCORE; no longer managed - do not remove this line.` | **The developer's**. Until you edit it, it follows the DSL; after you edit it, it accepts only the changes that do not overlap - if they overlap, it is left as it is and you are told (use it for a frame to be filled in, like a controller) |

### 6.6 The whole rule file

The finished `AcmeDddRules.java`, with the pieces of chapters 5-7 put together, is in **appendix A**. Replace the empty shell from chapter 3 with that content.

### 6.7 Variation: one file per entity

To create one file per **root** instead of one per domain (for example `SalesOrderEvents`), three places differ.

| | Per domain (6.3-6.4) | Per entity |
|---|---|---|
| Interface | `DomainArtifactContributor` | `ArtifactContributor` |
| Declaration | `artifactsFor(DomainSpec d)` | `artifactsFor(EntitySpec e)` - called for each entity |
| Rule parameter | `(DomainSpec d)` | `(EntitySpec e)` |

**File: `acme-rules/src/test/java/com/acme/rules/PerRootExampleRules.java`** (used in tests only in this guide)

```java
package com.acme.rules;

import io.elasticore.base.model.pub.rules.ArtifactContributor;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;

import java.util.Collections;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/** One file per aggregate root that declares events: SalesOrderEvents, listing them. */
public class PerRootExampleRules implements CodeRules, ArtifactContributor {

    @Override
    public List<ContributedArtifact> artifactsFor(EntitySpec e) {
        if (!e.aggregateRoot() || e.events().isEmpty()) return Collections.emptyList();
        return Collections.singletonList(
                ContributedArtifact.of("rootEvents", "event", e.getName() + "Events").orNamespace("entity"));
    }

    @Rule(value = "acme-root-events", target = "rootEvents")
    void rootEvents(EntitySpec e) {
        SourceCode c = ctx(e);
        for (String event : e.events()) {
            c.varLine("eventList", "    %s.class,", e.eventClassName(event));
        }
        c.var("className", e.getName() + "Events");
    }
}
```

Put the skeleton the same way in `elasticore-template/v3/rootEvents.tmpl` (9.1).
This example is used only in rule tests. To use it for real, move the class to `src/main/java` and the skeleton to `src/main/resources`, and add `'com.acme.rules.PerRootExampleRules'` to the `rules` list in the build (you may have several rule sets).
To use it in a Kotlin project too, create `kt/rootEvents.tmpl` and put `requireSyntaxAnyOf(...)` in the rule (6.4, chapter 12).

### 6.8 Writing a generated class - sending to a topic through the Outbox

To send events to a broker, turn on two things.

1. In `env.yml`, under `config:`:
   ```yaml
       apiPath: "/api"
       outbox: true                # send events through the Outbox table
   ```
2. `@EnableScheduling` on the main class - the sender (Relay) runs periodically (every 5 seconds by default), so without it messages only pile up in the table.
   ```java
   @SpringBootApplication
   @EnableScheduling                 // the outbox relay sends on a schedule
   @EnableJpaAuditing          // records the author and time automatically
   public class ErpApplication {
   ```

The flow: the event is written to the Outbox table in the **same transaction** as the save, and the generated `ErpOutboxRelay` hands it to the `OutboxPublisher` bean after the commit.
Implement `OutboxPublisher` to choose the topic.

**File: `src/main/java/com/acme/erp/infrastructure/messaging/TopicOutboxPublisher.java`**

```java
package com.acme.erp.infrastructure.messaging;

import com.acme.erp.domain.event.ErpEventTopics;
import io.elasticore.springboot3.outbox.OutboxEnvelope;
import io.elasticore.springboot3.outbox.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Sends each outbox message to the topic its event was given in the DSL (@topic). */
@Component
public class TopicOutboxPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(TopicOutboxPublisher.class);

    @Override
    public void publish(OutboxEnvelope message) {
        String topic = ErpEventTopics.topicOf(message.getEventType());
        if (topic == null) throw new IllegalStateException("No topic for " + message.getEventType());
        // A real broker goes here, e.g. kafkaTemplate.send(topic, message.getAggregateId(), message.getPayload()).
        log.info("-> {} {}", topic, message.getPayload());
    }
}
```

| When `publish` | Result |
|---|---|
| returns normally | The message is marked as published |
| throws an exception | It stays unpublished and is retried in the next cycle. Meanwhile **all later messages (including those of other aggregates) wait** - publishing order is guaranteed |
| fails 10 times (`elasticore.outbox.max-attempts` in `application.yml`) | It is set aside and an error is logged. The messages after it go out again |

An event without a topic is reported with an exception, so it is never dropped silently.

---

## 7. Example 4 - Checking the model (with a team setting)

A rule can also **only check the model**, without writing code. This is part 3 of the full file.

```java
    @Rule("acme-model-check")
    void check(EntitySpec e) {
        if (!e.aggregateRoot()) return;

        if (!e.commands().isEmpty() && e.events().isEmpty()) {
            ModelCheck.warn(String.format(
                    "[acme] %s takes commands but declares no @event: nothing outside the aggregate hears what happened.",
                    e.getName()));
        }

        // A team setting in env.yml: elasticore.config.acme.requireTopic
        if (Boolean.parseBoolean(e.config("acme.requireTopic")) && !e.events().isEmpty() && topicOf(e) == null) {
            ModelCheck.fail(String.format(
                    "[acme] %s declares events but no @topic - add @topic(...) to its meta.", e.getName()));
        }
    }
```

| Code | Meaning |
|---|---|
| `ModelCheck.warn(msg)` | A warning only. The same message is printed once |
| `ModelCheck.fail(msg)` | Generation runs to the end (so all problems show at once), and then the **build fails** |
| `e.config("acme.requireTopic")` | The value of `elasticore.config.acme.requireTopic` in that domain's `env.yml` (`null` if absent). In a domain rule, use `d.config(...)` |
| `e.commands()` | The commands declared under `commands:`. Each has `name()` and `params()` |

Write the team setting under `config:` in `env.yml`:

```yaml
    outbox: true                # send events through the Outbox table
    acme:
      requireTopic: true        # a setting of our rule: a root with events must have @topic
```

Try it: remove `@topic(erp.sales)` from `SalesOrder` for a moment and run `./gradlew elcore3`:

```
[acme] SalesOrder declares events but no @topic - add @topic(...) to its meta.
...
[elcore3] the model check failed (1):
  [acme] SalesOrder declares events but no @topic - add @topic(...) to its meta.
```

The build fails (`elcore3` exits with code 1). Put it back and it passes.
In the ERP model, the root with commands (`SalesOrder`) also declares events, so the `warn` side does not appear. Remove the `@event(...)` of `SalesOrder` and it does.

> **Do not throw exceptions in a rule.** Only the **one file** that rule was writing is left as a failure (`ERROR`); the rest is generated and the run ends with exit code 1.
> It is hard to find which file is missing and why. Report model problems with `ModelCheck.fail`.

---

## 8. Putting it together: the full build

```bash
./gradlew build        # compile the rule module -> generate (elcore3) -> compile the application -> test
```

The generation log shows the new file:

```
    eventTopics  src/main/java/com/acme/erp/domain/event/ErpEventTopics.java  WROTE
```

---

## 9. Testing the rules

### 9.1 Test only the rules (fast, no project)

Build the model inside the test and produce a single file with the bundle plus our rule set.

**File: `acme-rules/src/test/java/com/acme/rules/AcmeDddRulesTest.java`**

```java
package com.acme.rules;

import io.elasticore.base.model.pub.rules.DomainSpec;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.ModelCheck;
import io.elasticore.base.model.pub.rules.RuleCatalog;
import io.elasticore.base.model.pub.rules.RuleEngine;
import io.elasticore.base.model.pub.rules.SourceCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules run on a model built in the test - no project, no generation run. */
class AcmeDddRulesTest {

    @AfterEach
    void forget() {
        ModelCheck.reset();
    }

    private EntitySpec salesOrder() {
        return new EntitySpec("SalesOrder")
                .domain("erp", "Erp")
                .meta("root", "")
                .meta("event", "Confirmed(totalAmount), Shipped")
                .meta("topic", "erp.sales")
                .pkg("entity", "com.acme.erp.domain.model")
                .pkg("event", "com.acme.erp.domain.event")
                .field("orderId", "string(36) @id")
                .field("orderNo", "string(20)!");
    }

    private RuleEngine engine(SourceCode.TemplateSet set) {
        return RuleCatalog.standard().engineFor("hexagonal-springboot")
                .register(new AcmeDddRules())
                .templateSet(set);
    }

    @Test
    void aRootAndItsIdentityAreMarked() {
        String out = engine(SourceCode.TemplateSet.V3).run(salesOrder(), "entity").render();
        assertTrue(out.contains("@AggregateRoot"), out);
        assertTrue(out.contains("import org.jmolecules.ddd.annotation.AggregateRoot;"), out);
        assertTrue(out.contains("@Identity"), out);
    }

    @Test
    void aSplitRootIsMarkedOnTheDevelopersClass() {
        EntitySpec order = salesOrder().entitySplitForBehavior("SalesOrder");
        String base = engine(SourceCode.TemplateSet.V3).run(order, "entity").render();
        String mine = engine(SourceCode.TemplateSet.V3).run(order, "behavior").render();
        assertTrue(base.contains("@AggregateRoot"), base);
        assertTrue(mine.contains("@AggregateRoot"), mine);
    }

    @Test
    void aMemberIsAnEntityNotARoot() {
        EntitySpec line = new EntitySpec("SalesOrderLine").domain("erp", "Erp")
                .containedIn("SalesOrder").pkg("entity", "com.acme.erp.domain.model")
                .field("lineId", "long @id");
        String out = engine(SourceCode.TemplateSet.V3).run(line, "entity").render();
        assertTrue(out.contains("@org.jmolecules.ddd.annotation.Entity"), out);
        assertFalse(out.contains("@AggregateRoot"), out);
    }

    @Test
    void eachEventGetsATopic() {
        DomainSpec erp = new DomainSpec("erp", "Erp").entity(salesOrder());
        // The generator hands the rule the package artifactsFor chose; a test says it.
        SourceCode code = engine(SourceCode.TemplateSet.V3).packageName("com.acme.erp.domain.event")
                .run(erp, "eventTopics");
        String out = code.render();
        assertTrue(out.contains("package com.acme.erp.domain.event;"), out);
        assertTrue(out.contains("SALES_ORDER_SHIPPED = \"erp.sales.shipped\";"), out);
        assertTrue(out.contains("TOPICS.put(\"com.acme.erp.domain.event.SalesOrderShippedEvent\", SALES_ORDER_SHIPPED);"), out);
        // An unfilled ${slot} renders as an empty line, not an error: ask for them.
        assertTrue(code.unfilledSlots().isEmpty(), String.valueOf(code.unfilledSlots()));
    }

    @Test
    void kotlinToo() {
        DomainSpec erp = new DomainSpec("erp", "Erp").entity(salesOrder());
        SourceCode code = engine(SourceCode.TemplateSet.KOTLIN).packageName("com.acme.erp.domain.event")
                .run(erp, "eventTopics");
        String out = code.render();
        assertTrue(out.contains("object ErpEventTopics"), out);
        assertTrue(code.unfilledSlots().isEmpty(), String.valueOf(code.unfilledSlots()));
        assertTrue(out.contains("const val SALES_ORDER_SHIPPED = \"erp.sales.shipped\""), out);
    }

    @Test
    void oneFilePerRoot() {
        RuleEngine perRoot = RuleCatalog.standard().engineFor("hexagonal-springboot")
                .register(new PerRootExampleRules()).templateSet(SourceCode.TemplateSet.V3);
        assertEquals(1, new PerRootExampleRules().artifactsFor(salesOrder()).size());
        String out = perRoot.run(salesOrder(), "rootEvents").render();
        assertTrue(out.contains("public final class SalesOrderEvents"), out);
        assertTrue(out.contains("SalesOrderShippedEvent.class,"), out);
    }

    @Test
    void aBuiltInAnnotationCanBeTakenOff() {
        RuleEngine without = engine(SourceCode.TemplateSet.V3).register(new NoDynamicUpdateRules());
        String out = without.run(salesOrder(), "entity").render();
        assertFalse(out.contains("@DynamicUpdate"), out);
        assertTrue(engine(SourceCode.TemplateSet.V3).run(salesOrder(), "entity").render().contains("@DynamicUpdate"));
        // A root split in two: its developer's class too.
        String mine = without.run(salesOrder().entitySplitForBehavior("SalesOrder"), "behavior").render();
        assertFalse(mine.contains("@DynamicUpdate"), mine);
    }

    @Test
    void eventsWithoutATopicFailWhenTheTeamRequiresOne() {
        EntitySpec noTopic = new EntitySpec("Stock").domain("erp", "Erp").meta("root", "")
                .meta("event", "Received").config("acme.requireTopic", "true")
                .field("stockId", "string(36) @id");
        engine(SourceCode.TemplateSet.V3).run(noTopic, "entity");
        assertEquals(1, ModelCheck.failures().size());
    }
}
```

| Code | Meaning |
|---|---|
| `.meta("root", "")` | The DSL's `meta: entity @root`. `.meta("topic", "erp.sales")` is `@topic(erp.sales)` |
| `.field("orderId", "string(36) @id")` | One field line of the DSL, as it is |
| `.containedIn("SalesOrder")` | A member entity (belongs to that root) |
| `.entitySplitForBehavior("SalesOrder")` | Makes it a root split into two files -> the developer's class (`behavior`) can be tested too |
| `.config("acme.requireTopic", "true")` | Instead of the `env.yml` setting |
| `engineFor("hexagonal-springboot").register(new AcmeDddRules())` | The same combination as the project: bundle + our rule set |
| `run(spec, "entity").render()` | The content (a string) of the file of that target |
| `code.unfilledSlots()` | After `render()`, the names of the `${slots}` that had no value and became an empty line. A slot becomes an empty line without an error, so check it with this (a typo in a name shows up here) |
| `ModelCheck.reset()` | So that check results do not mix between tests |
| `.packageName("…")` | Supplies in the test the `${packageName}` that the generator normally provides |

The variant rule of 6.7 puts `elasticore-template/v3/rootEvents.tmpl` in the **test resources** (`acme-rules/src/test/resources/`):

**File: `acme-rules/src/test/resources/elasticore-template/v3/rootEvents.tmpl`**

```java
package ${packageName};

/** The events ${className} lists - one file per aggregate root. */
public final class ${className} {

    public static final Class<?>[] ALL = {
${eventList}
    };

    private ${className}() {
    }
}
```

```bash
./gradlew :acme-rules:test
```

### 9.2 Check in the application (does the result really work?)

**File: `src/test/java/com/acme/erp/AcmeRulesAppliedTest.java`**

```java
package com.acme.erp;

import com.acme.erp.domain.event.ErpEventTopics;
import com.acme.erp.domain.event.SalesOrderShippedEvent;
import com.acme.erp.domain.model.Invoice;
import com.acme.erp.domain.model.Item;
import com.acme.erp.domain.model.Partner;
import com.acme.erp.domain.model.SalesOrder;
import com.acme.erp.domain.model.SalesOrderLine;
import com.acme.erp.domain.model.Stock;
import com.acme.erp.infrastructure.persistence.ErpOutboxRelay;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What the ACME rules promise, on the application they generated. */
// After its first run at startup the scheduled relay waits an hour, so it cannot race the relay() below.
@SpringBootTest(properties = "elasticore.outbox.relay-delay-ms=3600000")
@AutoConfigureMockMvc
class AcmeRulesAppliedTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ErpOutboxRelay relay;

    @Test
    void everyClassSaysItsRole() {
        for (Class<?> root : new Class<?>[]{Partner.class, Item.class, SalesOrder.class, Stock.class, Invoice.class}) {
            assertTrue(root.isAnnotationPresent(AggregateRoot.class), root.getSimpleName());
        }
        assertTrue(SalesOrderLine.class.isAnnotationPresent(org.jmolecules.ddd.annotation.Entity.class));
    }

    @Test
    void theIdentityIsMarked() throws Exception {
        assertTrue(com.acme.erp.domain.model.SalesOrderBase.class.getDeclaredField("orderId")
                .isAnnotationPresent(org.jmolecules.ddd.annotation.Identity.class));
    }

    @Test
    void eachEventHasItsTopic() {
        assertEquals("erp.sales.shipped", ErpEventTopics.topicOf(SalesOrderShippedEvent.class.getName()));
        assertEquals("erp.sales.shipped", ErpEventTopics.SALES_ORDER_SHIPPED);
    }

    @Test
    void theOutboxSendsToTheTopic() throws Exception {
        String partnerId = json.readTree(post("/api/erp/partner", "{\"name\":\"Topic Trading\",\"bizNo\":\"555-55-55555\"}"))
                .path("partnerId").asText();
        post("/api/erp/item", "{\"itemCode\":\"TPC\",\"name\":\"Topic\",\"unitPrice\":10}");
        post("/api/erp/stock/receive", "{\"itemCode\":\"TPC\",\"warehouse\":\"TOPIC\",\"qty\":5}");
        String orderId = json.readTree(post("/api/erp/salesOrder", "{\"orderNo\":\"SO-TOPIC\",\"partnerId\":" + partnerId
                + ",\"orderDate\":\"2026-09-25\",\"lines\":[{\"itemCode\":\"TPC\",\"qty\":1,\"unitPrice\":10}]}")).path("orderId").asText();
        post("/api/erp/salesOrder/" + orderId + "/confirm", "");
        post("/api/erp/salesOrder/" + orderId + "/ship", "{\"warehouse\":\"TOPIC\"}");

        relay.relay();
        // The publisher refuses an event without a topic, which leaves it pending.
        assertEquals(0, relay.pending());
    }

    private String post(String url, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
```

`theOutboxSendsToTheTopic` ships a sales order, runs the Relay once by hand (`relay.relay()`), and checks that no message is left.
If an event has no topic, `TopicOutboxPublisher` throws and the message stays, so the test fails.
So that the scheduled Relay and the test's direct call do not overlap, the test stretches the period to one hour (`@SpringBootTest(properties = ...)`).

---

## 10. In a Kotlin project

The same rule set works as it is. Only `language = 'kotlin'` in the root `build.gradle` differs.

| What | In Kotlin |
|---|---|
| Class and field annotations | The same. The generator writes them in Kotlin syntax |
| New file | `elasticore-template/kt/<target>.tmpl` **must exist separately**. If it is missing, it does not fall back to the Java skeleton; that file is left as a failure (`ERROR`) (generation exits with code 1) |
| Slots filled line by line | The syntax differs, so branch on `c.syntax() == Syntax.KOTLIN` as in 6.4 (only in the rule of a new file) |
| Your existing file | The same as Java (chapter 5): it is merged, and the notice for a remaining file also appears for the Kotlin file. When you add it by hand, put `@AggregateRoot` above `class` |

**File: `acme-rules/src/main/resources/elasticore-template/kt/eventTopics.tmpl`**

```kotlin
package ${packageName}

/**
 * ${className} - where each domain event of the domain is published.
 *
 * Generated from @topic by the ACME rules. Change the DSL, not this file.
 */
object ${className} {

${constantList}

    private val TOPICS: Map<String, String> = mapOf(
${entryList}
    )

    /** The topic of an event, by its class name as the outbox records it; null when it has none. */
    @JvmStatic
    fun topicOf(eventType: String): String? = TOPICS[eventType]
}
```

---

## 11. Common sticking points

| Symptom | Cause | Fix |
|---|---|---|
| `Rule set class 'com.acme...' is not on the generator's classpath` | A typo in the class name, or `elasticore project(':acme-rules')` is missing | Check the name and the dependency. After the fix, our rule set should show on the first lines of `-PlistRules` |
| `NoSuchMethodError` or similar during generation | The `elasticore-base` version in `acme-rules` differs from the plugin version | Use the same version, as in chapter 3 |
| The rule runs but there is no result | That file has no place for an annotation (chapter 1 table, "none"), or the `target` is different | Render and print it in a rule test (9.1) |
| My file (`SalesOrder.java`) does not change | A developer file left as `KEPT` or `SKIP` in the build log, or a file copied without the marker line | Add it by hand as the generator's notice says (chapter 5) |
| The generated file has an empty line and no value | An unfilled `${slot}` - not an error, it becomes an empty line | Find which slot with `code.unfilledSlots()` as in 9.1 (usually a typo in the name) |
| After writing `rules:` in `env.yml`, the hexagonal files disappeared | `rules` in `env.yml` **replaces** the bundle | Use `rules = [...]` in the build, or list the bundle name too |
| Our rule does not reach the TypeScript client (`elcore3ts`) | Intended. Added rules apply only to the server (Java, Kotlin) | Make a separate one for TypeScript: a rule that uses TypeScript and an `elasticore-template/ts/` skeleton, and list it with `ts-model` in `publisher.rules.ts` in `env.yml` |
| Outbox messages do not go out | No `@EnableScheduling`, or no `OutboxPublisher` bean | 6.8 |
| I changed the rule but the result is the same | The file is not the generator's, or it was edited by hand | Check the reason of `SKIP(...)` in the generation log |

---

## 12. Checklist

When you add a new rule:

- [ ] The rule id starts with the team prefix (`acme-…`)
- [ ] The `target` is right (for an entity it is `entity` - it applies to both files of a split root)
- [ ] Annotations are not built by joining strings (use `AnnoCode`)
- [ ] For a new file: an empty list when the condition does not hold, `orNamespace`, and a skeleton in both `v3/` and `kt/`
- [ ] A rule test (9.1) exists, and you checked that `unfilledSlots()` is empty
- [ ] It throws no exceptions (use `ModelCheck`)
- [ ] You told the team about the changes needed in existing developer files

## 13. Frequently used API

| Read (`EntitySpec e`) | |
|---|---|
| `e.getName()`, `e.domainClassName()` | `SalesOrder`, `Erp` |
| `e.aggregateRoot()`, `e.containedIn()` | Is it a root / for a member, the name of its root |
| `e.isSplitForBehavior(e.getName())` | Is the entity split into two files |
| `e.hasMeta("x")`, `e.metaValue("x")` | Is the meta word present / the value in parentheses |
| `e.events()`, `e.eventClassName(ev)`, `e.eventPackage()` | Domain events |
| `e.commands()` -> `name()`, `params()` | Business commands |
| `e.fields()`, `e.pkField()`, `e.pkg("dto")` | Fields / identifier / namespace package |
| `e.config("a.b")` (same for `d.config`) | `elasticore.config.a.b` in `env.yml` |

| Read (`DomainSpec d`) | |
|---|---|
| `d.getName()`, `d.className()` | `erp`, `Erp` |
| `d.entities()` | All entities of the domain (`EntitySpec`) |
| `d.namespace("dto")`, `d.config("a.b")` | The package of a `namespace` key (the table in 6.3, including keys you wrote yourself) / the `env.yml` setting |

| Read (`FieldSpec f`) | |
|---|---|
| `f.name()`, `f.typeName()`, `f.required()` | Name / type / whether `!` is present |
| `f.hasDsl("x")`, `f.hasDslAny("x", "y")`, `f.dslValue("x")` | Field annotation `@x` / its value |
| `f.isList()`, `f.isEntityRef()` | Is it a collection / a reference to another entity |

| Write (`SourceCode c = ctx(e)`) | |
|---|---|
| `c.ensureClassAnnotation(AnnoCode.of("a.b.C"))` | Class annotation (import is automatic) |
| `c.field(f).ensure(AnnoCode.of(...))` | Field annotation |
| `AnnoCode.of("a.b.C").val("name", "x").valRaw("length", "%d", 10)` | Attributes (`val` adds quotes, `valRaw` writes as is) |
| `c.var("k", v)`, `c.varLine("k", fmt, args)` | Skeleton slots |
| `c.dontMake()` | Do not make this file |
| `ModelCheck.warn(msg)`, `ModelCheck.fail(msg)` | Model check results |

The full list is in chapters 4 and 5 of `elasticore-rule-guide.md`.

---

## Appendix A. The complete `AcmeDddRules.java`

**File: `acme-rules/src/main/java/com/acme/rules/AcmeDddRules.java`**

```java
package com.acme.rules;

import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.DomainArtifactContributor;
import io.elasticore.base.model.pub.rules.DomainSpec;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.ModelCheck;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.Collections;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * ACME's additions to the DDD rules.
 *
 * 1. Marks each class and identity with its DDD role (jMolecules).
 * 2. Reads @topic on a root and writes one EventTopics class per domain.
 * 3. Checks the model: commands without events, events without a topic.
 */
public class AcmeDddRules implements CodeRules, DomainArtifactContributor {

    private static final String JMOLECULES = "org.jmolecules.ddd.annotation.%s";

    // ---- 1. DDD roles --------------------------------------------------------

    /** Each entity class says its DDD role. A root split in two gets it on both of its classes. */
    @Rule("acme-role")
    void role(EntitySpec e) {
        if (e.hasMeta("abstract")) return;   // a base class for others, not a role of its own
        SourceCode c = ctx(e);
        if (e.hasMeta("embeddable")) {
            c.ensureClassAnnotation(AnnoCode.of(JMOLECULES, "ValueObject"));
        } else if (e.aggregateRoot()) {
            c.ensureClassAnnotation(AnnoCode.of(JMOLECULES, "AggregateRoot"));
        } else {
            // Same simple name as jakarta.persistence.Entity: written in full.
            c.ensureClassAnnotation(AnnoCode.of(JMOLECULES, "Entity").qualified());
        }
    }

    /** A field rule: called once per field of each entity. */
    @Rule("acme-identity")
    void identity(EntitySpec e, FieldSpec f) {
        if (!f.hasDslAny("id", "pk")) return;
        ctx(e).field(f).ensure(AnnoCode.of(JMOLECULES, "Identity"));
    }

    // ---- 2. @topic -> <Domain>EventTopics ------------------------------------

    @Override
    public List<ContributedArtifact> artifactsFor(DomainSpec d) {
        for (EntitySpec e : d.entities()) {
            if (topicOf(e) != null && !e.events().isEmpty()) {
                return Collections.singletonList(
                        ContributedArtifact.of("eventTopics", "event", className(d)).orNamespace("entity"));
            }
        }
        return Collections.emptyList();
    }

    @Rule(value = "acme-event-topics", target = "eventTopics")
    void eventTopics(DomainSpec d) {
        SourceCode c = ctx(d);
        c.requireSyntaxAnyOf("acme-event-topics", Syntax.JAVA, Syntax.KOTLIN);
        boolean kotlin = c.syntax() == Syntax.KOTLIN;

        for (EntitySpec e : d.entities()) {
            String topic = topicOf(e);
            if (topic == null) continue;
            for (String event : e.events()) {
                String constant = upperSnake(e.getName() + event);
                String eventClass = e.eventPackage() + "." + e.eventClassName(event);
                if (kotlin) {
                    c.varLine("constantList", "    const val %s = \"%s.%s\"", constant, topic, kebab(event));
                    c.varLine("entryList", "        \"%s\" to %s,", eventClass, constant);
                } else {
                    c.varLine("constantList", "    public static final String %s = \"%s.%s\";", constant, topic, kebab(event));
                    c.varLine("entryList", "        TOPICS.put(\"%s\", %s);", eventClass, constant);
                }
            }
        }
        // ${packageName} is already the package artifactsFor chose.
        c.var("className", className(d));
    }

    // ---- 3. Checks on the model -------------------------------------------------

    @Rule("acme-model-check")
    void check(EntitySpec e) {
        if (!e.aggregateRoot()) return;

        if (!e.commands().isEmpty() && e.events().isEmpty()) {
            ModelCheck.warn(String.format(
                    "[acme] %s takes commands but declares no @event: nothing outside the aggregate hears what happened.",
                    e.getName()));
        }

        // A team setting in env.yml: elasticore.config.acme.requireTopic
        if (Boolean.parseBoolean(e.config("acme.requireTopic")) && !e.events().isEmpty() && topicOf(e) == null) {
            ModelCheck.fail(String.format(
                    "[acme] %s declares events but no @topic - add @topic(...) to its meta.", e.getName()));
        }
    }

    // ---- helpers ---------------------------------------------------------------

    /** @topic(erp.sales), with or without quotes. */
    private static String topicOf(EntitySpec e) {
        String v = e.metaValue("topic");
        if (v == null) return null;
        v = v.replace("\"", "").replace("'", "").trim();
        return v.isEmpty() ? null : v;
    }

    private static String className(DomainSpec d) {
        return d.className() + "EventTopics";
    }

    /** SalesOrderShipped -> SALES_ORDER_SHIPPED */
    private static String upperSnake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
    }

    /** PartiallyShipped -> partially-shipped */
    private static String kebab(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }
}
```
