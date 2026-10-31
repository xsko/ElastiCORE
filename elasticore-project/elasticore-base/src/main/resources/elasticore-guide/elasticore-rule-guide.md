# ElastiCORE Rule Guide

How to **choose**, **switch off** and **write your own** code generation rules.

If you want to write a model in the DSL, read `elasticore-dsl-guide.md`.
If you want to work with the generated code, read `elasticore-dev-guide.md`.
This document is for **people who want to change the generated code itself**.

---

## 1. What a rule is

The ElastiCORE 3.0 generator has two parts.

| | What it decides | Where it lives |
|---|---|---|
| **Rule** | *What* to emit - does this field get `@Column(length=200)`? | Java class (`CodeRules`) |
| **Skeleton (template)** | *Where* it goes - the frame of the file | `.tmpl` file |

A rule reads the model and only decides. How a string is written - semicolons, the shape of imports,
property declarations - is answered by the **language seam (`Syntax`)**. This separation is why the same rules
can generate both Java and Kotlin.

```
Model (DSL) ──▶ Spec ──▶ rules ──▶ SourceCode ──▶ skeleton ──▶ file
                                      │
                                   Syntax (language)
```

---

## 2. Choosing rules (env.yml)

You choose rules in the domain's `env.yml`. **If you do not use this setting, the default bundle applies,
so most projects do not need to know this section.**

```yaml
elasticore:
  config:
    publisher:
      rules: [jpa-springboot-v3, kr.co.acme.AcmeRules]
      suppress: [jpa-comment]
```

| Key | Meaning |
|---|---|
| `rules` | The rule sets to use. **Replaces the default** |
| `suppress` | The ids of the rules to switch off. **Accumulates** |

The value accepts all three forms: `[a, b]`, `a, b`, `a`.

### Priority

```
env.yml publisher.rules  >  system property elasticore.publisher.rules  >  default bundle
```

### What a name refers to

Each name in `rules` resolves to one of three things.

1. A registered **bundle** - a group of rule sets
2. A registered **rule set** - one `CodeRules` class
3. A **fully qualified class name** - a `CodeRules` implementation you wrote yourself

### Built-in bundles

| Bundle | What it generates |
|---|---|
| `jpa-springboot` | The **same** output as the 2.x pipeline (except the 2.x bug fixes noted in the history - `elcoreRulesCheck` and `elcoreTreeCheck` compare them) |
| `jpa-springboot-v3` | Cleaned-up output - only the imports actually used, class annotations that rules produce |
| `jpa-springboot-kt` | Kotlin output |
| `ddd-springboot` | v3 + aggregate boundaries - repository/service/controller only for roots, immutable value objects, domain events |
| `ddd-springboot-kt` | The Kotlin version of the same reading |
| `hexagonal-springboot` | `ddd-springboot` + an inbound port (`<Entity>UseCase`) |
| `hexagonal-springboot-kt` | The Kotlin version of the same hexagonal design |
| `ts-model` | TypeScript - DTOs, enums, search DTOs + a client that calls the controllers |
| `mybatis-springboot` | Layered Java on MyBatis - taken by domains with `mode: mybatis` (mapper interfaces, XML and schema instead of the JPA entity shell, Repository and Specification; env guide 2.3) |
| `ddd-mybatis-springboot` | `ddd-springboot` on MyBatis - taken by domains with `architecture = 'ddd'` + `mode: mybatis`. What JPA did by itself (member cascade, orphan removal, version, callbacks, publishing domain events) is done by the generated repository adapter (env guide 2.3) |
| `hexagonal-mybatis-springboot` | `ddd-mybatis-springboot` + an inbound port - `architecture = 'hexagonal'` + `mode: mybatis` |

If a domain with `mode: mybatis` writes `ddd-springboot`, `hexagonal-springboot` or `jpa-springboot-v3` in the `publisher.rules` of env.yml,
each is read as its MyBatis bundle - mode says how data is stored, the bundle says the architecture.

#### What the hexagonal bundle adds

For each exposed aggregate root, `hexagonal-springboot` declares one
inbound port.

```
application/port/in/OrderUseCase   <- the face through which the application is called from outside
application/OrderService           <- implements OrderUseCase (extended service)
interfaces/rest/OrderController    <- gets OrderUseCase injected (not the implementation class)
```

`namespace.inboundPort` decides where it goes. If you do not declare it, it goes in the same
package as `service`, so **existing projects see no change at all.** If you want to separate the two directions,
declare `inboundPort` - then `port.in` and `port.out` stand side by side.

The port's method list is a direct copy of the generated service's surface, so the claim "the service
implements the port" is **checked by the compiler every time**. The outbound port is already
what the `port` DSL did - there is just no code to generate because the adapter is a runtime proxy.

The Kotlin version also matches nullability in the signature to the service (`save(dto: OrderDTO): OrderDTO?`).
In that language nullability is part of the signature, so if they do not match it does not compile in the first place.

#### Rules that reach each language differently

| Rule | Java | Kotlin | TS | Note |
|---|---|---|---|---|
| `ddd-aggregate` / `ddd-reference` | O | O | - | The boundary is a fact of the model. In TS it already shows up as the absence of endpoints |
| `ddd-collection` (cascade, orphanRemoval) | O | O | - | It speaks to the ORM, so the browser has no counterpart |
| `ddd-collection` (add/remove/replace) | O | O | - | Declares the Kotlin property as `MutableList` |
| `ddd-collection` (setter sealing) | O | O | - | See below |
| `ddd-mapper-ref` (write blocking + member round trip) | O | O | - | The mapper belongs to the server |
| `ddd-value-object` / `ddd-value-object-kt` | each | each | - | The difference is whether immutability comes from Lombok or from the language |
| `ddd-event` | O | O | - | Tied to the moment of saving |
| `ddd-invariant` | O | O | **O** | In TS it is a pre-check, not enforcement |
| `ddd-specification` | O | O | **O** | |
| `ddd-factory` | O | O | - | Creating the aggregate belongs to the server |
| `ddd-domain-service` | O | O | - | Gets ports injected |
| `ddd-behavior` | O | O | - | Split into `XxxBase` (regenerated) + `Xxx` (owned by the developer) |
| `ddd-command` | O | O | - | `commands:` -> endpoint, command DTO, use case, service, abstract method |
| `ddd-outbox` | O | O | - | When `outbox: true` in `env.yml` |
| `ddd-state` | O | O | **O** | `states:` -> state transition per command, `canX()`, initial value. In TS it is `canShip(dto)` |
| `ddd-derive` | O | O | - | `@snapshot`, `@compute` -> `derive(lookup)`, which the service calls before saving |
| `jpa-service-access` | O | O | - | `@access`, `@owner`, `@password`, `@login` -> CoreService checks, hashing, `authenticate`; with `@signin`, `me()`, the controller's signin, signout, me, and the unique login column (all v3 bundles) |
| `jpa-value-object-boundary` | O | O | - | The DTO's validation and JSON rules on `@embeddable` fields, `@Valid` on the DTO side (all v3 bundles) |
| Process manager (`processManager:` of `ddd-domain-service`) | O | O | - | After the event commits, in a separate transaction |
| `ddd-repository-port` / `-adapter` | O | O | - | The browser has no repository |
| `hex-usecase` | O | O | - | Skeletons exist for both languages |

> **How to seal a setter in Kotlin.** With `private set` alone the compiler
> refuses - *"Private setters are not allowed for open properties"*. Entity
> properties are `open` because of the all-open plugin, and without it Hibernate cannot create
> a proxy. For a while this was recorded as **the ORM's requirement and encapsulation excluding
> each other**.
>
> They do not exclude each other. The plugin **only opens what is final by default; an explicit `final`
> is left alone.** So the generated code looks like this:
>
> ```kotlin
> final var items: MutableList<OrderItem>? = null
>     private set
> ```
>
> The property is sealed and the class stays open - each side really needed
> exactly that. Hibernate writes to the field, not the property, so it is
> not affected.

An item marked X here is not silently dropped. A language-specific rule names the language it uses
(`requireSyntax`), and if it is selected for another language it stops on the spot.
Only a rule that decides by itself "there is nothing to do in my language" and moves on (half of the methods of `ddd-collection`)
passes without a word.

#### Domain events (`ddd-event`)

If an aggregate root declares `@event(Placed, Cancelled)`:

```java
OrderPlacedEvent, OrderCancelledEvent   // classes holding the root's identifier and the time of occurrence
order.raisePlaced()                     // raise method added to the root
```

A raised event is read and published by Spring Data **the next time that root is saved**
(`@DomainEvents` / `@AfterDomainEventPublication`). If the save fails, nothing is
published, and after publishing the events are cleared, so they are not published twice.

**When it is raised is not generated.** The model says an order can be cancelled,
but not what counts as a cancellation - so the call is made in the extended service
owned by the developer.

`namespace.event` decides where the event classes go. If you do not declare it, they go
in the same package as the entity.

#### Invariants (`ddd-invariant`)

An invariant is something the aggregate **promises to keep always true**. If the boundary rules decide "who can
write", the invariant decides "what is allowed to be true after it is written".

```yaml
Order:
  meta: entity @root @invariant(items.size > 0, 'an order has at least one line')
```

You can write it several times. Each declaration is a separate promise.

The generated code is an `OrderInvariants` **listener class**, registered in the root's
`@EntityListeners`. It is not a method on the root because JPA allows only one callback per class, and
an `@genid` root already uses `@PrePersist` - with two, it is not a warning:
**the persistence unit does not start at all.**

The check runs in `@PrePersist` / `@PreUpdate`, that is, **when the row is written**. Whatever
path saves it cannot get past. A violation throws `IllegalStateException` -
this is not the rejection of user input but the domain saying "it became a state it never promised",
so it differs from validation messages (the Bean Validation that `@length` and others produce).

> **A member leaving is also a write to the root.** When you delete the last member, only the member's row is
> deleted and the root's row stays as it is, so the root's listener alone would not run the check.
> So an `OrderItemRemovalCheck` listener is attached to the member, and when the member row is deleted (flush)
> it checks the root's invariants against the remaining state. The check is not in the remove method because
> the replace method empties the collection first to replace everything - by flush time it has already been
> refilled, so a replacement is allowed. While the root is being deleted, the check is skipped.
> If the member has no field pointing to the root, the check cannot be made and the DSL gives a warning.
> Sample tests: `AggregateInvariantTest` (`emptyingAnOrderIsCaught`,
> `replacingEveryLineIsStillAllowed`, `deletingTheRootIsNotRefused`).

#### Factory (`ddd-factory`)

A root with invariants cannot be built by setting fields one at a time - the middle states are not
valid. The factory is the place that takes responsibility for the moment it becomes valid.

```yaml
Order:
  meta: entity @root @invariant(items.size > 0, '...') @factory(place)
```

The generated code is `OrderFactory`, and it carries a **generate-once marker**. Before you write the body, the signature
follows the model; after that, only changes that merge cleanly come in - if you add a required field to the root, the signature is right above the body the developer wrote,
so it does not merge, and the generator tells you to leave the file as it is and move the change by hand (`KEPT(yours ...)`,
`elasticore-env-guide.md` 9-2). The body is a stub that throws - because a stub that quietly
returns something makes it to production.

The signature is the root's required fields (the generated identifier and collections are excluded). It is the most the model can
say; what comes next is the developer's file.

#### Specification (`ddd-specification`)

A domain rule worth giving a name. It stops the same comparison from being retyped in the service, in the report and in the test,
with one of them typed differently.

```yaml
Order:
  meta: entity @root @specification(highValue, totalAmount > 100)
```

The generated code is `OrderSpecifications.highValue()`, and it returns a `Predicate<Order>`.
It is a predicate, not a boolean, because **composition is the point of this pattern**:
`highValue().and(hasLines())` is a rule made of rules.

**It is not a query.** Exporting it as a Spring Data `Specification<Order>` and pushing it down into SQL
would be a better artifact, but only for rules that the criteria API can express.
Some rules running in the DB and some in memory, with **no way to tell which**,
is the worst property a generated artifact can have. It selects from what has already been read.

#### Condition syntax (shared by `@invariant` and `@specification`)

The two patterns only write the same sentence differently, so they share a syntax.

| Form | Meaning |
|---|---|
| `items.size > 0` | The number of members of a to-many is greater than the value |
| `totalAmount > 100` | The number is greater than the value (`<` also works) |
| `notes notNull` | The field is set |
| `items notEmpty` | The to-many holds something |

A field, a comparison and a literal, nothing more. Accepting an arbitrary expression means writing a parser, and
**an almost-right parser is worse than an obviously limited form** - the DSL starts to
look like a place to put logic, and it is not only that.

> You **cannot use** `>=` and `<=`. The DSL annotation parser reads an `=` inside an argument
> as the start of a key/value list. This is not an omission but a limit, and
> you use word forms such as `notNull` / `notEmpty` instead. For the same reason `:` is not used
> either - the meta line is a YAML scalar, so `name: ` is read as the start of a mapping and the file does not
> load at all.

Values are always read **through accessors**. Both generated artifacts live outside the entity, so fields are not
visible, and a lazy-loaded to-many is initialized only when read through the getter.

#### Replacing a value object (`ddd-value-object`)

Giving no setter says "a value object does not change", and it did not answer
**how you get a different one**. Now each field gets a with method:

```java
Address moved = address.withCity("Rotterdam");   // the original stays as it is
```

Before, the all-args constructor was the only way, and it made the call site list the remaining fields again in positional
order, so it was easy to swap two of them.

#### What changes in Kotlin

What the rule emits is the same; only the way each language says it differs.

| | Java | Kotlin |
|---|---|---|
| Event class | Fields + constructor + getters | `val` in the primary constructor |
| Specification class | `private` constructor | `object` |
| Factory | `private` constructor + `static` | `object` + `fun` |
| Port of a domain service / process manager | `@Autowired protected` field of `XxxBase` | `@Autowired protected lateinit var` of `XxxBase` |
| Condition expression | `order.getItems()` | `order.items!!` |
| Invariant listener | `@EntityListeners({A.class, B.class})` | `@EntityListeners(A::class, B::class)` - an array attribute takes a vararg, so brackets do not compile |

#### Entity behavior (`ddd-behavior`) - Generation Gap

The entity file is regenerated every time the DSL changes, so a method like `confirm()` written inside it
disappears. So behavior moves out into the service and you get an anemic domain model. In the DDD bundles
**a root is split in two by default** (you do not need to write `@behavior`).

| File | Nature | Content |
|---|---|---|
| `SalesOrderBase` | Regenerated (`@MappedSuperclass`, abstract) | Fields, event raise methods, collection and invariant plumbing |
| `SalesOrder` | **generate-once** (`@Entity`, `extends SalesOrderBase`) | Business methods the developer writes |

```yaml
SalesOrder:
  meta: entity @root @expose(20) @behavior @event(Confirmed)
  fields:
    status: OrderStatus @behavior        # changes only through behavior
    totalAmount: bigdecimal(14.2) @behavior
```

```java
public class SalesOrder extends SalesOrderBase {
    public void confirm() {
        if (getStatus() != OrderStatus.DRAFT) throw new InvariantViolationException("only a draft can be confirmed");
        setStatus(OrderStatus.CONFIRMED);   // protected setter
        raiseConfirmed();
    }
}
```

- **The concrete class keeps the entity name.** Mappers, repositories, ports and DTOs do not change;
  one superclass is just added (the same split as Core Data mogenerator and Apache Cayenne).
- **Field `@behavior`**: the setter is `protected` (Java: `@Setter(AccessLevel.PROTECTED)`,
  Kotlin: `protected set`), the mapper does not write it, and the DTO takes it as `READ_ONLY`.
  A client cannot send the state and a service cannot change it directly, so only behavior methods change it.
  A root is split even if you write nothing, so you only need the field `@behavior`. On an entity that is not split (an `@editable` root,
  a member), a field `@behavior` gives a DSL warning and is ignored.
- **Table annotations** (`@Entity`, `@Table`, `@DynamicUpdate` ...) are on the concrete class
  that JPA reads. This file is not regenerated, so if you change the table name, schema or indexes in the DSL,
  the generator prints the difference and the new annotations - copy them by hand.
- **Moving an existing project**: add `@behavior` and generate, and `SalesOrder.java`, which the generator managed,
  turns into a thin concrete class and its fields move to `SalesOrderBase.java`.
  If the user modified the file, it is skipped without `-Pforce`.
- Shapes where it does not apply: `@embeddable`, `@abstract`, `@rollup`, discriminator (single table) hierarchies,
  composite keys. JPA handles inheritance differently for these, so they are not split.

##### A root is locked by default (sealed) - open it with `@editable`

In the DDD bundles the fields of a root are **locked by default**. A model with no public setters is the default, and
an anemic model arises only when you **explicitly choose** it with `@editable`.

| Field kind | setter | Request (POST) | Request (PUT) | How to change it |
|---|---|---|---|---|
| Default (locked) | `protected` | Accepted | Passes if the value is the same, **422** if different | A method of the root |
| `@behavior` | `protected` | Not accepted (`READ_ONLY`) | Not accepted | A method of the root |
| `@readonly` / `@noinput` | `protected` (`public` on an `@editable` root) | Not accepted (`READ_ONLY`) | Not accepted | Server code: `creation()` for a new root, a method of the root after that (for an `@editable` root, the setter in `beforeSave`) |
| `@editable` | `public` | Accepted | Accepted | setter, PUT |
| id, `@jpa:Version`, audit fields | As they are | - | - | Persistence layer |

```yaml
SalesOrder:
  meta: entity @root @expose(20) @event(Confirmed)
  fields:
    orderId: string(36) @id @genid
    orderNo: string(20)!          # set at creation, after that only through methods
    status: OrderStatus @behavior # the request cannot even send it
    memo: string(500) @editable   # edited any time
    lines: List<SalesOrderLine> @OneToMany(mappedBy="order") @editable

Item:                             # master data - a root where editing field by field is right
  meta: entity @root @editable @expose(20)
```

- **The way in is `creation()`**: the mapper writes a locked field as `to.creation()::setOrderNo`.
  A new root accepts anything; a saved or loaded root (marked by `@PostLoad`/`@PostPersist`) accepts
  **only the same value as now**. Sending the whole object back in a PUT passes, but trying to
  change a locked value throws `InvariantViolationException` - 422 (`FixedAtCreation`).
  Judging "the same value": `BigDecimal` by `compareTo` (10 and 10.00 are equal), a reference by identifier, a value object by its components.
- **Creating in code** takes the same way: `order.creation().setOrderNo("SO-1")`
  (Kotlin: `order.creation().orderNo = "SO-1"`). On the domain side, a factory on the concrete class is
  better: `Invoice.issueFor(orderId, orderNo, amount)`.
- **Null on a new root**: if the request does not send a value (null), the entity's default (`@default`) is not overwritten.
  The DTO of a locked field and of a `@behavior` field does not carry a default - if it did, a partial
  PUT would send the default along (for example total 0) and become a request to change a locked value.
  For the same reason **`creation().setX(null)` does nothing on a new root** (`if (!settled && value == null) return;`).
  It is not a way to erase a client-sent value in the service's `beforeSave`. A field you do not want to accept from the request
  is declared `@readonly` (the server decides it) or `@behavior` (only the root's methods change it), and the mapper does not write it from the start;
  erasing the value is done by a method of the root with the `protected` setter.
- **Collections**: the root's member collections are locked too (`creation().replaceLines(...)` only on a new root).
  If a request must change it, like editing a draft, add `@editable`.
- **Generated service tests** also build their fixtures with `creation()`.
- **2.x and the plain v3 bundles are not affected.** A root is split by default only in domains where `ddd-behavior` is
  selected (= `ddd-springboot`, `hexagonal-springboot` and the Kotlin bundles).

#### Files the generator no longer produces (elcore3)

Files that something removed from the DSL used to produce - the `XxxInvariants` of a root whose last `@min` moved to a value type, the DTO of a command that was
renamed - used to stay behind, refer to a superclass whose shape had changed, and break the build.
Now, at the end of **a run that generated every domain**, among the files in the generated directory that this run did not produce,
those that meet the following are deleted (`REMOVED(the DSL no longer produces it)`).

- Files whose first line is a 3.0 hash line, that are not handed-over files (`Initially generated ...`), and that are exactly as the generator wrote them.
- A modified file is not deleted, only a warning is given. Nothing is cleaned up when only some domains are generated with `-Pdomain=...`
  or when there is a failed artifact.

#### Business commands (`ddd-command`)

For each command declared in an entity's `commands:`, it builds one path: `POST /{id}/<command>` -> `XxxUseCase` -> `XxxCoreService`
(load, call, save) -> the abstract method of `XxxBase` -> the rule in `Xxx`.
For the DSL syntax and the table of generated code, see "Business commands" in `elasticore-dsl-guide.md`.

- The method is abstract, so if you add a command to an existing behavior class, it does not compile until you write the rule - it cannot be forgotten.
  When the behavior class is first created, a stub that throws comes with it, so calling it in that state gives 500.
- The first time the behavior class is written, a stub for each command goes in (Java: `UnsupportedOperationException`,
  Kotlin: `TODO()`). It is not added to an existing behavior class, so you write it yourself (the compiler tells you).
- The controller is a developer-owned file. A controller you have not touched is regenerated and gets the
  command endpoint; a controller you edited is left as it is.
- A root with no `@editable` field gets no PUT (neither in the controller nor in the use case).

#### Lifecycle (`ddd-state`)

When an entity's `states:` declares the state field and the move for each command, the command is generated as a **concrete method of
`XxxBase`, not an abstract method**. For the syntax, see "Lifecycle" in `elasticore-dsl-guide.md`.

- If the command arrives when the entity is not in a starting state, it throws `InvariantViolationException` - the API returns 409 (conflict with the current state): "PurchaseOrder cannot ship when status is CANCELLED; it must be [PLACED]".
  The code is `state.PurchaseOrder.status.ship`, and it can be translated into the request language (see "Error codes and multiple languages" below).
- If `@event` has an event with the same name as the target state, it is published (`SHIPPED` -> `raiseShipped()`).
- `canShip()` is generated too, and the TypeScript client also gets `canShip(dto)` (for showing buttons).
- If there is more to do, override it in the behavior class and make the move with `super.ship()`.
- The state field cannot be written by a request (treated the same as `@behavior`).
- The keys under `states:` are **field names**, and the state names are the **constant names** of the enum that is that field's type. If a name is not in the enum,
  the DSL gives a warning (`... names SHIPED, which OrderStatus does not have (it has ...)`) and the lifecycle is left out.
- A state with no outgoing move is an end state. The Javadoc of `moveXxx` lists the start, move, end and unreachable states.
- If the same command is in two lifecycles (two enum fields), the command first checks the start state of all lifecycles (`requireXxx`) and then
  moves them together. If any one fails, it is 409 and no field changes. `canX()` is the AND of the conditions.

#### Derived values (`ddd-derive`)

`@snapshot(item.unitCost)` copies the value of the referenced row **once** (404 if the row does not exist),
and `@compute(unitCost * quantity)` and `@compute(sum(lines.subTotal))` calculate every time.
The entity gets `derive(lookup)`, and CoreService calls
`entity.derive(entityManager::find)` before saving on create, update, bulk update, conditional update and after a command. Members are calculated first, so the total adds up the latest lines.
Only the expressions `a * b`, `a + b`, `a - b` (fields or numbers) and `sum(memberList.field)` are supported, and by type they are written as
`Money.times/plus`, `BigDecimal.multiply/add` and integer arithmetic. An expression it cannot read gives a DSL warning and is skipped.

#### Access, owner and password (`jpa-service-access`)

`@access(read=owner, create=user, ship=admin)`, `@owner(customerId)`, and the field-level `@password`, `@login` and
`@access(admin)` are checked in the **regenerated CoreService** - not the developer-owned controller,
so if you fix the DSL, it reaches every entry point in the next build. For the table of annotations, see `elasticore-annotation-guide.md`,
"@access / @owner".

- The template's slots (`${accessCreate}` and so on) are empty when there is no declaration, so the generated code does not change.
- Runtime: `CurrentUser` (default `SessionCurrentUser`) of `io.elasticore.springboot3.security`,
  `AccessPolicy`, `PasswordHasher` (default PBKDF2), and the 401 and 403 exceptions. If you register a bean, it is used.
- The CoreService and use case of an `@login` + `@password` entity get `authenticate`. If the entity line also has `@signin`,
  this rule also writes `me()` and the controller's `signin`, `signout` and `me` endpoints, and the login column becomes unique
  (unique comes from `@signin` alone - even when the conditions below stop the endpoints from being produced).
  The conditions are: only the owner and an admin can change the row's password (`update=owner` with an `@owner` pointing to the key, or
  `update=admin` or `none`); if the request decides the key, delete must be the same (`delete=owner` with an `@owner` on the key, or `admin` or `none`);
  `@expose`; a single key; and one `@signin` in the application - in a single-module project all domains are
  one application, in a multi-module project it is each module where a controller is generated. The reasons it cannot be produced come out as a DSL warning per entity.
  The controller is developer-owned, so nothing goes into a controller you touched, and the generation log reports a missing endpoint, and also a login
  that is still there though the DSL no longer produces it (`POST /signin`, `GET /me`, the role put in the session), as a DSL warning.

#### Transactional Outbox (`ddd-outbox`)

Domain events published by Spring Data are delivered **only inside the same JVM**. Sending from there to a broker
sends a message of a transaction that will roll back, or loses the message when sending fails after the commit. The outbox writes the event
to a table **in the same transaction** and sends it separately after the commit.

```yaml
# env.yml
elasticore:
  config:
    outbox: true
```

For each domain it is generated in the **infrastructure package** (`namespace.persistence`, or `repository` if there is none) - the outbox is an adapter, so
the domain and application do not know about it (all are regenerated, managed files). An app that sets `@EntityScan` itself must include this package:

| File | Role |
|---|---|
| `<Domain>OutboxMessage` | Entity (`<prefix>outbox` table): aggregate kind and ID, event type, JSON, time of occurrence, time of publishing, number of attempts, last error |
| `<Domain>OutboxRepository` | Query that locks and reads 100 unpublished messages in order |
| `<Domain>OutboxRecorder` | Records every event as JSON at `BEFORE_COMMIT` - if the change commits, the message exists; if it rolls back, it does not |
| `<Domain>OutboxRelay` | `relay()`: sends unpublished messages with `OutboxPublisher` and marks them published on success. On failure, the later messages of that aggregate stop (order is kept) and are retried next time. A message that fails `elasticore.outbox.max-attempts` (default 10) times is set aside so it does not block the ones behind it. `@Scheduled(fixedDelayString = "${elasticore.outbox.relay-delay-ms:5000}")` - runs periodically if the app has `@EnableScheduling` |

What the application writes is the transport, one bean:

```java
@Bean
OutboxPublisher kafka(KafkaTemplate<String, String> kafka) {
    return m -> kafka.send("erp-events", m.getAggregateId(), m.getPayload()).get();
}
```

- Delivery is **at least once**. The consumer filters duplicates with `OutboxEnvelope.getId()`.
- If there is no `OutboxPublisher` bean, the relay does nothing and messages pile up in the table.
- You can see the number of waiting messages with `pending()` (monitoring).

#### Process manager (saga) - `processManager:`

A flow where the events of one aggregate call **the next step of another aggregate** (when an order comes in, issue a receipt).
The `billing` of `elasticore-template-ddd` is exactly such an example.

```yaml
# blueprint/billing/model/domainservice.yml
processManager:
  Receipting:
    desc: When an order comes in, create one receipt
    on: [shop:OrderPlaced]       # (context:)root name + event name (without the Event suffix)
    uses: [Receipt]              # domain port of the aggregate to be injected
```

The generated code is two files (in the application package or `namespace.processManager`). It is the same split as `XxxBase` + `Xxx` of a business command,
so if you fix the DSL the wiring is regenerated and the steps the developer wrote stay. This is the real generated code with only the imports and the class
comments removed:

```java
// ReceiptingProcessManagerBase - regenerated every time (rule ddd-process-manager-base)
public abstract class ReceiptingProcessManagerBase {

	@Autowired
	protected ReceiptRepositoryPort receiptRepositoryPort;

	/** After OrderPlacedEvent has committed, in a transaction of its own. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void on(OrderPlacedEvent event) {
	    handle(event);
	}

	/** What shop:OrderPlaced means here - the next step, written in ReceiptingProcessManager. */
	protected abstract void handle(OrderPlacedEvent event);

}

// ReceiptingProcessManager - generated once, then owned by the developer (rule ddd-process-manager)
@Component

public class ReceiptingProcessManager extends ReceiptingProcessManagerBase {

	@Override
	protected void handle(OrderPlacedEvent event) {
	    // The next step goes here.
	}

}
```

- If you add an event to `on`, the Base gets an abstract `handle(new event)`, and it does not compile until you write it in the developer class
  (it cannot be forgotten). An aggregate added to `uses` becomes a field of the Base, so you do not need to edit the developer class - that is why the port is
  injected as a field, not through the constructor. If you have never edited the developer file, it is regenerated too.
- It runs **after the transaction that raised the event has committed, in its own transaction**. If it fails here, the shipment that has already
  happened is not undone - retry and compensation are decided by `handle` (the compensation step of a saga).
- If you put the needed values in the event content (payload), you do not have to read the original aggregate again.
- Write it idempotent, to be ready for duplicate delivery (for example, check with `findByOrderId` whether it was already issued).
- If a declared event is not in the `@event` of any root, the DSL gives a warning.
- You receive an event of another bounded context with `on: [shop:OrderPlaced]`. The generator finds that context's
  event class in the context map and imports it (if two events in `on` have the same simple name, the one written later is written with its full name), and checks with the `uses` and
  `publishes` of `config.context`. For the syntax and the limit (delivery only inside the same application), see
  "Process manager" in `elasticore-dsl-guide.md`.

#### Read model - projection queries

A query for a screen or report is **projected to a DTO**, not an aggregate. In the repository DSL, write the return type as a
DTO, and match the column aliases of the JPQL/SQL to the DTO's property names.

```yaml
repository:
  SalesOrder:
    methods:
      - id: summarizeByPartner
        params: { status: OrderStatus }
        return: List<PartnerSalesDTO>
        query: "SELECT o.partner.partnerId AS partnerId, o.partner.name AS partnerName, COUNT(o) AS orderCount, SUM(o.totalAmount) AS totalAmount FROM SalesOrder o WHERE o.status = :status GROUP BY o.partner.partnerId, o.partner.name"
```

- v3/Kotlin: the repository returns `List<Tuple>`, and the query port returns `List<PartnerSalesDTO>`.
  The adapter fills alias -> property with `TupleMapper` (ignoring case and underscores, including type conversion).
  `List`, `Page`, `Optional` and a single result are all supported. The 2.x compatibility mode stays `List<Object[]>` as before.
- A column without an alias is reported as an error.
- If a DB view already exists, you can also build a read model with an `@immutable(view_name)` entity + a query port.

#### Value object (`@embeddable`)

`@embeddable` is generated as an immutable value object (`ddd-value-object`: no setters, a new value through `withXxx()`), and
in v3/Kotlin it is **carried as it is in the DTO** and exchanged through the API (a nested object in the request JSON). When it changes,
it is replaced as a whole. Wrapping identifier types (a typed PK such as `OrderId`) is not supported - JPA does not allow a converter on
`@Id`, so every layer would have to change to `@EmbeddedId`.

#### Refer to another aggregate by ID (`ddd-reference`)

In the DDD bundles a **to-one reference to another root** is turned into an ID field. You write the DSL as usual.

```yaml
SalesOrder:
  fields:
    partner: Partner @ManyToOne        # as you always write it
```

Generated result:

```java
@Column(name = "partner_partner_id")
@Setter(AccessLevel.PROTECTED)          // locked - set at creation, afterwards through methods
private Long partnerId;                 // the reference is this field

/** Another aggregate, to read. The reference is partnerId; ... */
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "partner_partner_id", insertable = false, updatable = false)
@Setter(AccessLevel.NONE)
private Partner partner;                // read-only join on the same column - no setter
```

- The aggregate holds **only the identifier** of another root, and there is no way to use that object. Changing the reference is
  only changing `partnerId`, and fixing another root is that root's job.
- The relation stays **read-only**, so queries work as before: JPQL `o.partner.name`, the derived query
  `findByPartnerPartnerId`, and the DTO's `partnerName @ref(partner.name)` display.
- The column keeps the name the relation used (`<field>_<target key column>`, or the `@db` name if there is one), so **the table does not change**.
- Field name: if the target key name starts with the reference name, that name (`partner` -> `partnerId`, `item` -> `itemCode`),
  otherwise `reference + key` (`category` -> `categoryCatId`). If a field of the same name already exists, the DSL gives a warning and keeps the object reference.
- What a save request sends is this ID field. For a required reference (`member: Member!`), the ID field is required in the DTO,
  and the nested DTO for the response is optional (from 3.0.261022-rc1. Before that, the nested DTO was also `@NotNull`, so a request that sent only the ID
  was refused by `@Valid` validation). The `LoanDTO` for `member: Member!` and `book: Book!` (Book's key is `isbn`):

  ```java
  @Schema(description = "borrower" , accessMode = Schema.AccessMode.READ_ONLY )
  private MemberDTO member;            // for the response - optional in the request
  ...
  @Schema(description = "memberId" , requiredMode=Schema.RequiredMode.REQUIRED )
  @NotNull
  private Long memberId;               // required in the save request

  @Schema(description = "bookIsbn" , requiredMode=Schema.RequiredMode.REQUIRED )
  @NotNull
  @Size(max=13)
  private String bookIsbn;
  ```
- The mapper writes `@ref(partner.partnerId)` as `partnerId` (it does not write the relation object).
- Applies to: a to-one that points to another root of the same domain with a single key (`cascade`, `@embedded`, the reverse
  `@OneToOne(mappedBy)` and fields that already use `@joinBy` are excluded). A member's back reference to its own root stays as it is.
- To keep an object reference, use `@objectref`.
- In code: `order.creation().setPartnerId(partner.getPartnerId())` (Kotlin: `order.creation().partnerId = partner.partnerId`).

#### Removing JPA from the domain (`mapping: xml`) and the architecture test (`archTest`)

With `mapping: xml` in `env.yml`, the generator creates the domain classes as usual, strips the JPA annotations, and writes the same
content to `META-INF/orm.xml` (`OrmMapping`). The XML element order follows the JPA 3.1 schema
(`id`, `basic`, `version`, `many-to-one`, `one-to-many`, `one-to-one`, `many-to-many`, `embedded`, `transient`).
The callbacks of the entity listener (`XxxInvariants`) are declared in XML too, so no `jakarta.persistence` is left in the domain package.
`archTest: true` generates this and the hexagonal boundary as ArchUnit rules. For the settings, see
`elasticore-env-guide.md` 6.1.2 and 6.1.3.

Never attach two callbacks to one method: XML mapping attaches only one callback to a method, so the lock marker is
two methods, `settleOnLoad()` (`@PostLoad`) and `settleOnPersist()` (`@PostPersist`).

#### Bounded context boundary

If you reference an entity of another domain (blueprint) directly, the DDD bundle warns. The two contexts would share the same table
shape, so keep the other side's identifier as a column, look it up through a port (the `port:` DSL), and
convert it to your own model (Anti-Corruption Layer). The generator does not block it.

#### Domain service (`ddd-domain-service`)

An operation that belongs to no aggregate. **Declare it on the domain, not on an entity** -
every other DDD annotation goes on an entity, but a domain service is defined by "belonging to no one".
Picking one root and hanging it there would be the cheap choice, but then it
stops being this pattern and becomes "a method someone forgot to put in an aggregate".

```yaml
# model/domainservice.yml
domainService:
  Pricing:
    desc: What an order costs once the products it names are priced
    uses: [Order, Product]
```

`uses` is not decoration. It is **what the generator injects**, and it is the sentence
that says why this service exists. If you list only one, the DSL warns - an operation on a single aggregate
is usually a method of that aggregate.

The output is two files. `PricingDomainServiceBase` is **regenerated every time** and has fields that receive the domain ports
of the named aggregates (`OrderRepositoryPort`, `ProductRepositoryPort`) (rule `ddd-domain-service-base`);
`PricingDomainService` is a **generate-once** class that extends it (rule `ddd-domain-service`). When you add an
aggregate to `uses`, the Base gets a port and the developer class is not touched. The operation itself is not generated -
for the same reason as `@factory`: the model declares data, so what can be generated is the place and the wiring, not the behavior.

The place is set by `namespace.domainService`. If you do not declare it, it is the same
package as the entity. **Do not put it in the application namespace** - it is domain code, and next to the use cases
the dependency direction is reversed.

#### What reaches TypeScript (`ts-model`)

What the browser receives is a DTO, not an entity. So not every server rule
comes here - **only what the browser can honestly hold** does.

| Rule | TS | Reason |
|---|---|---|
| `ddd-specification` | Yes | It is a question about data you already have |
| `ddd-invariant` | Yes | Asks in advance what the server will refuse (not enforcement) |
| Aggregate boundary | **Already reached** | Members have no endpoints, so `ts-api` does not create a client |
| `ddd-event` / `ddd-factory` / `ddd-domain-service` | No | They are tied to save time and to the repository, so the browser has no counterpart |

##### Why it is worth generating the specification

In this language a hand-written copy is not just a duplicate - **it is usually wrong.**

```ts
// hand-written
order.totalAmount > 100

// generated
Number(order.totalAmount) > 100
```

An amount is declared as `number` in TS - Jackson by default sends a decimal **as a JSON number**, and
`JSON.parse` gives a number whatever the declaration says. But if the server is set to send decimals
**as strings** for precision (`@JsonFormat(shape = STRING)` and so on),
the first line compares a string with a number, and JavaScript answers false whatever the amount.
The generated `Number(...)` is right in both cases.

For the same reason the TS model takes its shape **from the rules the server chose**. Not the `ts-model` bundle but
the Java/Kotlin choice in the same build decides: if it uses an identifier reference (`customerId`) or a value object inline (`deliveryAddress`),
the TS DTO does too, and each value object gets an interface (`Address.ts`). `XxxApi.ts` does not create
endpoints that the controller does not have (`@readonly`, `@restExclude`, `update` of a root that accepts only commands), and
a `@writeonly` field is optional because it is not in the response. All conditions of a search DTO are optional, and
it has `pageNumber`, `pageSize`, `sortColumn` and `sortAscending`.

For collections too, it is `.length`, not `.size()`, and properties are optional, so
every read is preceded by a `!= null` guard.

##### An invariant is not enforcement

The generated file says so. The server refusing when it writes a row is what makes the promise true, and
the browser is not to be trusted. What you get here is something else -
telling the form, **in the domain's own sentences**, what is wrong before the request is sent.

```ts
violations(order)   // the promises that were broken. The form wants to show them all at once, so it is a list
isValid(order)      // whether none was broken
```

#### The two directions of a port

Hexagonal has two directions, and the output is split in two.

| Direction | What | Namespace | Adapter |
|---|---|---|---|
| **Inbound** | `OrderUseCase` - the door into the application | `inboundPort` (fallback: `service`) | Controller (generated) |
| **Outbound (persistence)** | `OrderRepositoryPort` - owned by the domain | `domainRepository` (fallback: `entity`) | `OrderRepositoryAdapter` (generated) |
| **Outbound (sql/http)** | DSL `port:` declaration | `port` | **Runtime proxy** (nothing generated) |

```yaml
namespace:
  inboundPort: com.acme.shop.application.port.in
  port:        com.acme.shop.application.port.out
```

##### sql/http ports belong to the application

The contract of a `dbms` / `http` port is written in **application DTOs**, and when it pages it uses Spring's
`Page` too. So this is an **outbound port of the application**.

If you list it in the `uses:` of a domain service, it is **ignored with a warning** - because the domain code would depend on the layer
above. The domain receives aggregates (= repository ports), and
the service that calls them uses the sql/http ports.

##### The adapter must be a bean

The output is only an interface, and the adapter is a runtime proxy. But **a proxy that nobody registers
is a port that cannot be injected**:

```
No qualifying bean of type 'ShopReportService'
```

In a Spring Boot application, the auto-configuration of the runtime library handles it by scanning (from 3.0.261027-rc2 - before that
`@EnableElastiCore` was required, and it still works if you leave it on). It searches the package of the `@SpringBootApplication` class
and below, so a `@SpringBootTest` that starts that class also gets the port beans. If a `@Bean` is declared directly for the same port,
that one is used. `DddSampleApp` is a test configuration in a different package (`ddd`) from the generated code (`io.elasticore.sample.ddd`),
so the scan does not reach the port, and one `@Bean` registers it directly - it is also the best place to read exactly where the seam is.
For details, see chapter 5 of the port guide.

#### Repository port (`ddd-repository-port`)

It is split into an interface owned by the domain and an implementation that knows Spring Data.

```java
// domain.repository - no Spring type at all
public interface OrderRepositoryPort {
    Optional<Order> findById(String orderId);
    Order save(Order order);
    void delete(Order order);
    boolean existsById(String orderId);
}

// infrastructure.persistence
@Repository
public class OrderRepositoryAdapter implements OrderRepositoryPort { ... }
```

##### Why now

`hexagonal-review` 2-B judged this "low priority", for the reason that **the benefit of "being able to strip out JPA"
is hardly ever realized in practice**. That reason is still true.

What changed is that there is one more benefit, and this generator made it itself -
when the domain service arrived:

```java
package ...domain.service;
import ...domain.repository.OrderRepository;   // extends JpaRepository
```

**A domain service is domain code by definition, yet it was receiving a persistence mechanism.**
A dependency of the innermost layer pointing outward - the one thing the architecture this bundle is named after says not to do.
It is worth fixing whether or not you strip out JPA.

##### Why the port is narrow

It is easy to imitate the Spring Data repository as it is and call it a port. And it is pointless -
search, paging and specifications are the **query surface**, and if you put them behind an interface owned by the domain,
`Pageable` and `Specification` end up in the domain after all. You did not remove the dependency; you only
renamed it.

A query that crosses aggregates or pages is **the job of a read model** (chapter 17). So search goes to
an **application-layer query port**, not to the domain port.

##### The application service receives only ports

Before, the application service used the Spring Data repository directly for search ("the application layer
may know its own infrastructure"). As a result **every application service imported
`infrastructure.persistence`**, which is exactly the direction hexagonal forbids.
It went unnoticed because the sample's layer test did not check `.infrastructure.`.

Now each root gets one more file, and the application service receives two ports and an `EntityManager`
(used only when the mapper turns an identifier into a reference).

```java
// application.port.out - the application layer's search port. Specification/Pageable appear only here
public interface OrderQueryPort {
    List<Order> findAll(Specification<Order> spec);
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);
    long count(Specification<Order> spec);
    // findAll(), findAll(Sort), findAll(spec, Sort)
}

// application - does not import infrastructure
public class OrderCoreService {
    protected final OrderRepositoryPort repository;   // find, save, delete (domain port)
    protected final OrderQueryPort query;             // search, paging (query port)
    protected final EntityManager entityManager;      // for resolving references only
}

// infrastructure.persistence - one adapter implements both ports
public class OrderRepositoryAdapter implements OrderRepositoryPort, OrderQueryPort { ... }
```

> **A hexagonal/DDD project made with an existing 3.0 snapshot:** `OrderService` is generate-once, so
> it is not regenerated. Change the constructor to `(OrderRepositoryPort repository, OrderQueryPort query,
> EntityManager entityManager)` and fix it to call `super(repository, query, entityManager)`.
> **The generator detects this and tells you** - if the constructor call in an owned file differs from what would be generated now,
> it prints the warning `constructor out of date ... the generator now writes
> super(...)`. A `supportAI: true` domain receives the same ports.

##### Namespaces

| Key | What | Fallback |
|---|---|---|
| `domainRepository` | Port (domain) | `entity` |
| `queryPort` | Query port (application layer) | `service` |
| `persistence` | Adapter + Spring Data (infrastructure) | `repository` |

If you put `repository` under `.domain.`, the domain comes to know `JpaRepository`. The sample
moves `repository` to infrastructure and puts the port in `domainRepository`.

> **Roots with a composite key are not generated.** The identifier is a separate class, so which class it is
> is a decision about the artifact, and this rule set must not make it silently.

#### What the write path guards (common to v3 services)

The generated core services (`jpa-springboot-v3`, `ddd-springboot`, `hexagonal-springboot`, including the Kotlin
versions) check the following before writing. The 2.x-compatible bundle (`jpa-springboot`) does not change.

| Case | Behavior | API response (`spring-api-errors`, in every v3 bundle) |
|---|---|---|
| Create (`save`) with an identifier that already exists | Refuses (`DuplicateKeyException`). Does not overwrite | 409 |
| The `version` sent with an update differs from the row's | Refuses (`ObjectOptimisticLockingFailureException`) | 409 |
| A value sent with an update violates a constraint (`@Size` etc.) | Refuses (`ConstraintViolationException`). Checks only the fields sent | 400 |
| Update of a row that does not exist | `EntityNotFoundException` | 404 |
| The row the request points to does not exist - references another aggregate by id (`loan.memberId`, `itemId` of an order line) | `InvariantViolationException`, code `reference.<Entity>.<relation>` (DDD bundle, `requireReferences` before save) | 404 - `Member 99 does not exist` |
| A command or delete that cannot be done in the current state (`states:`) | `InvariantViolationException`, code `state.…` | 409 |
| Any other invariant violation | `InvariantViolationException` (subclass of `IllegalStateException`) | 422 |
| A hand-written rule refuses (`beforeSave`, root method) | `io.elasticore.springboot3.validation.BadRequestException` (bad request), `NotFoundException` (the row pointed to does not exist), `ConflictException` (conflicts with what is stored - no copies left to lend, already returned), `security.ForbiddenException`, `security.NotSignedInException`, `http.PortCallException` (the other system that was called failed) | 400, 404, 409, 403, 401, 502 - the message thrown goes out as `detail`. If you give a message code, it is translated to the request language (see "Translating a hand-written refusal" below) |
| Delete by condition `delete(srchDTO)` | Reads and deletes one at a time - members and listeners work together | - |
| Update by condition `updateBySpecification` | Reads one at a time and applies through the mapper - version, audit and invariants apply | - |
| Batch update `batchUpdate` (`PUT /batch`) | Calls `update(dto)` for each row in one transaction (from 3.0.261022-rc1) - a rule that overrides `update()` in the extension service also applies to the batch update, and each row gets the same checks, `readBack` and 409 conversion | Same as a single update |

**`@restExclude(...)`** removes methods from the service, controller, use case and TypeScript client. The names are the service methods
(`save`, `update`, `batchUpdate`, `delete` (list delete), `deleteById`, `updateBySpecification`) and the controller endpoints
(`create`, `read`, `search`, `update`, `batchUpdate`, `deleteBatch` (`POST /delete`), `deleteById`, `schemaInfo`).
In v3 output, the calls to a removed method are removed with it, so the compile does not break: `update` takes `batchUpdate` with it, `deleteById` takes
`delete` and `deleteBatch`, `delete` takes `deleteBatch`, and `save` takes `create`. The hexagonal use case
interface does not promise a method that the service removed either (before, `@restExclude(batchUpdate)` did not compile, with `XxxService is not abstract
and does not override abstract method batchUpdate`). The Kotlin service does not remove methods and leaves them as they are,
and only the controller and use case follow. The 2.x-compatible bundle removes only what is written. When a project rule removes a method itself with `ctx(e).excludeMethod("update")`,
v3 output takes the dependents with it too (`update` without `batchUpdate` is not called). The TypeScript client follows the server;
if the server is 2.x-compatible output, it removes only what is written (even with `@restExclude(update)`, `batchUpdate` of the `PUT /batch` that stays on a 2.x server
also stays in the client). The server's mode is known from the settings of `elcore3ts` itself (architecture, language, `output`, `-Pv2`/`-Pv3`, the domain's rules).
If nothing is set, it also treats the server as the default, v3 - but it follows a 2.x server if the project has sources generated by 2.x, or if the `elcore3` that last generated the server
left `build/elasticore/server.mode` saying 2.x-compatible.

- **The version comparison** reads the `version` property of the DTO by name (`VersionCheck`). If the DTO has no version
  or the request does not send it, nothing is checked.
- **Partial-update validation** (`BeanValidation.validatePresent`) looks only at the constraints of properties that have a value.
  The skip-null merge does not touch missing fields, so it does not require `@NotNull`.
  An `@dynamicupdate(false)` entity gets full validation.
- **The core service is no longer a `@Service` bean.** Only the extension service is a bean, so there is one
  instance per type.

On top of this, the mapper of the DDD bundle **does not write `@jpa:Version` and the Spring Data audit fields
(`@jpa:...CreatedBy/CreatedDate/LastModifiedBy/LastModifiedDate`) from the DTO to the entity.**
They still go out in the response (the client has to send the version back), but a request
cannot forge the audit record.

#### API error responses (`spring-api-errors`)

It is included in `jpa-springboot-v3` and `jpa-springboot-kt`, and every v3 bundle (including DDD and hexagonal) receives
it. The 2.x-compatible bundle `jpa-springboot` does not have it. For each domain that has exposed entities, it generates in the controller
package `ShopApiErrors` (`@RestControllerAdvice`, RFC 9457 `ProblemDetail`) once.
It answers with the status codes in the table above, and applies only to the controllers in that package.
It is generate-once, so the response format belongs to the developer. In the constructor it passes the Validator that Spring configured to
`BeanValidation.use(...)`, so service-side validation also uses custom constraints that need injection and
the application's messages. It catches only the two wrappers that carry invariants (`InvalidDataAccessApiUsageException`,
`TransactionSystemException`), so other data-access errors such as a unique conflict go to the user's
handler.

**Refusals before the controller (`RequestShapeErrors`).** The generated `ShopApiErrors` does an `@Import` of the runtime's
`io.elasticore.springboot3.validation.RequestShapeErrors`. For a request that Spring MVC refuses before
it calls the controller method, it answers in the same shape (`ProblemDetail` + `code`) **across the whole application** - including
hand-written controllers in other packages. Before, it was Spring Boot's default body `{timestamp, status, error, path}` with no `code`.
The status codes are the same as before.

| Request | Status | `code` | Example (checked in the Pet Store) |
|---|---|---|---|
| No body, or not JSON | 400 | `unreadable` | body `{"lines":` |
| A value the type does not accept - letters where a number goes, an enum code that does not exist, a negative `quantity`, outside the int range | 400 | `invalid`, `fieldErrors[].code` is `typeMismatch` | `lines[0].quantity: a quantity cannot be negative: -1`, `creditCard.creditCardType: Unknown CreditCardType code: DINERS`, `GET /item/abc` -> `itemId: must be a number` |
| A required query parameter is missing | 400 | `invalid`, `fieldErrors[].code` is `NotNull` | hand-written `@RequestParam` |
| Parameter constraint (`@RequestParam @Min(1) int page`) | 400 | `invalid`, the constraint name (`Min`) | hand-written controller |
| `@Valid` body of a hand-written controller | 400 | `invalid` | (for a generated controller, `ShopApiErrors` answers) |
| A path that does not exist | 404 | `not-found` | `GET /api/petstore/nothing/here` |
| A method that does not exist on that path | 405 (`Allow` header as it is) | `method-not-allowed` | `PUT` on a root that is not `@editable` |
| A response format it cannot give (`Accept: image/png`) | 406 | `not-acceptable` | |
| A body format it cannot read (`Content-Type: text/plain`) | 415 | `unsupported-media-type` | |

- When a browser asks for a page (`Accept` has `text/html`), 404 and 405 show Spring Boot's error page (Whitelabel or
  `error/404.html`) as it is.
- The **404 with an empty body** that `findById` and `delete` of a generated controller used to answer for a row that does not exist now has the same shape
  (`NotFoundException`, `"code": "not-found"`, `"detail": "Item not found"`). `NotFoundException` is `@ResponseStatus(404)`, so
  it is a 404 even if you turn off `spring-api-errors` (the body is Spring Boot's).
- **To answer differently**, handle that exception in `ShopApiErrors` - that file is called first. A company-wide
  `@RestControllerAdvice` also comes before the handler that `RequestShapeErrors` registers (`RequestShapeAdvice`) (that handler is last). To be sure, put `@Order` on the common
  advice.
- Do not turn on `spring.mvc.problemdetails.enabled=true`. Spring Boot's handler stands first (`@Order(0)`) and takes even the field validation
  response (`fieldErrors`).
- It is not an auto-configuration. It exists only when `ShopApiErrors` imports it, so a 2.x application that uses the same runtime library is unchanged.
  If you turn off `spring-api-errors`, it goes away with it. `RequestShapeErrors` is a class that only registers the handler (`RequestShapeAdvice`),
  and the handler is not picked up by `@ComponentScan("io.elasticore.springboot3")` (the scan that the old port guide recommended to 2.x apps).
  Even if the `XxxApiErrors` of several domains all import it, there is one handler.
- **If you have edited an existing `XxxApiErrors`**, it is not regenerated, so add one line to the class
  (if you have not touched it, it is regenerated and gets the line):
  ```java
  @org.springframework.context.annotation.Import(io.elasticore.springboot3.validation.RequestShapeErrors.class)
  ```
  For Kotlin, `@org.springframework.context.annotation.Import(io.elasticore.springboot3.validation.RequestShapeErrors::class)`.
- It needs Spring Framework 6.1 (Spring Boot 3.2) or later (`NoResourceFoundException`, `HandlerMethodValidationException`).
  The samples are checked with Spring Boot 3.3.2 and 4.0.8.
- **A call to another system fails (502)**: when the code that called a port decides that it failed, it throws `io.elasticore.springboot3.http.PortCallException`.
  `ShopApiErrors` answers 502 with `"code": "upstream"`. When to treat what as a failure is in
  `elasticore-dsl-port-guide.md` 2.8.

#### Error codes and languages (Accept-Language)

Every error response has a `code` that a program can branch on, and the text goes out in **one language per response**:

1. Look at the languages in `Accept-Language` in order, and take the first of: a **language the application has translated** (a language that has a `messages_<language>` on the classpath, a `<domain>-messages_<language>` next to
   the domain bundle, or a `ValidationMessages_<language>` file), the default language, English.
2. If there is none (no header, or a language with no translation), the **default language**: Spring Boot's `spring.web.locale` (the same name in Boot 3 and 4).
   If it is not set, it is the bundle's default text (English, or the sentence written in the DSL).
3. The default messages of Bean Validation (`Size`, `NotNull`, `Min` ...) are also restated in the same language, with the numbers filled in
   (`size must be between 0 and 10` / `크기가 0에서 10 사이여야 합니다`). A sentence written in the DSL stays as written.

**The language of the server machine is not used.** A server on a Korean OS also answers a request with no header, `en`, or `sv` in English - checked by starting the Pet Store with
`-Duser.language=ko -Duser.country=KR`, and `ShopFlowTest` checks the same on a Korean JVM.

```yaml
# application.yml - answer requests with no header in Korean (only meaningful if a translation file _ko exists)
spring:
  web:
    locale: ko
```

- English is the bundle's default text, so even with `spring.web.locale: ko`, `Accept-Language: en` gives English.
- Even if Hibernate Validator has French, a `fr` request goes out in the default language unless the application has translated it - so that the DSL sentences and
  the default messages do not get mixed.
- If you use another `LocaleResolver` such as session or cookie (including `spring.web.locale-resolver: fixed`), it follows the language that resolver decided.
  Only a request with no header where the resolver returned the container's language as it is (a request nobody decided for) gets the bundle's default text.

What is translated:

| Response | `title` | `detail` |
|---|---|---|
| 409 (lifecycle), 404 (referenced row missing), 422 (invariant) | Yes | Yes (by the rule's code) |
| Database refusals as 409/400 (`stale`, `required`, `unique`, `referenced`, `conflict`) | Yes | Yes (`error.<code>`) |
| 400 field validation (`@Valid`, and service-side validation) | Yes | Field message: yes (`<constraint>.<field>`, e.g. `Size.lines`). A default message with no translation goes in the request language |
| Refusal before the controller (`RequestShapeErrors`) | Yes | Yes (`error.unreadable`, `error.method-not-allowed` ..., a field is `typeMismatch.<field>`) |
| A hand-written refusal that was given a message code | Yes | Yes (by that code) |
| 404, 409 (already exists), 401, 403, 502 and other 400 thrown without a code | Yes | No - the exception message as it is (English, with the identifier) |

When you cancel a shipped order in the Pet Store (checked with `ShopFlowTest`):

```http
POST /api/petstore/purchaseOrder/{id}/cancel
Accept-Language: ko

HTTP/1.1 409
{ "title": "지금 상태에서는 할 수 없는 요청",
  "detail": "주문 상태가 SHIPPED이면 취소할 수 없습니다 (가능한 상태: [PLACED])",
  "code": "state.PurchaseOrder.status.cancel", "status": 409 }
```

From 3.0.261022-rc1, a lifecycle refusal is 409 and a missing referenced row is 404 (before, a lifecycle refusal was 422, a save that referenced a row that does not exist by id was
409 "Conflicts with stored data" from the foreign key violation, and 422 only when a `@snapshot` could not find the source row). In an existing project, if you have **never edited**
`<Domain>ApiErrors`, it is regenerated (`REGENERATED(owned, never edited)`) and changes to the new responses -
fix any client that branched on 422 too - and if you **have edited** it, it stays as it is, so a lifecycle refusal and a missing reference both
still answer 422. Change the `refused(...)` in that file as if it had been newly generated: `state.` to 409, `reference.` to 404.

With no `Accept-Language`, or a language with no translation, it is
`"detail": "PurchaseOrder cannot cancel when status is SHIPPED; it must be [PLACED]"`.

**The code scheme**

| Code | When | Values (`{0}`, `{1}`) |
|---|---|---|
| `not-found`, `already-exists`, `stale`, `required`, `unique`, `referenced`, `conflict`, `invalid`, `refused`, `state`, `not-signed-in`, `forbidden`, `bad-request`, `no-such-property` | Common to all domains (title `error.<code>.title`, sentence `error.<code>`) | - |
| `state.<Entity>.<field>.<command>` | Lifecycle: a command came when not in a start state (`states:`) | current state, allowed states |
| `invariant.<Entity>.<field>.min` / `.max` / `.minsize` / `.maxsize` | Field range invariant | - |
| `invariant.<Entity>.<condition>` | `@invariant(condition, 'sentence')` - a name made from the condition expression (`items.size > 0` -> `items-size-gt-0`) | - |
| `reference.<Entity>.<relation>` | The row the request pointed to by id does not exist - reference to another aggregate (checked before save), the source of a `@snapshot` (404) | identifier |
| `upstream` | A call to another system failed (`PortCallException`, 502) | - |
| `unreadable`, `method-not-allowed`, `unsupported-media-type`, `not-acceptable` | Refusal before the controller (`RequestShapeErrors`; title `error.<code>.title`, sentence `error.<code>`) | 405 `{0}` method, 415 `{0}` body format, 406 `{0}` formats it can give |
| `error.no-such-path`, `error.missing-value` | The sentence for a path that does not exist (404 `not-found`), the field message for a missing parameter | `{0}` path / - |
| `typeMismatch.<field>` | The field message for a value the type does not accept. `typeMismatch.lines[0].quantity` is that line only, `typeMismatch.lines.quantity` is all lines. If neither exists, the message the type refused with (`Quantity`, `fromCode` of an enum - English), and if that does not exist either, `error.type.number` / `.boolean` / `.text` / `.datetime` / `.enum` / `.other` | `{0}` of `.enum` is the allowed values |
| (hand-written code) | The message code given to a hand-written refusal (below) | the values given |

**How to translate**

1. When you build, `src/main/resources/i18n/<domain>-messages.properties` is created for each domain.
   It holds **every code and default text** that the domain can answer with (generated: do not edit).
2. In the same folder, create `<domain>-messages_ko.properties` and translate only the keys you need
   (you may save it in UTF-8). A missing key goes out as the default text.

```properties
# src/main/resources/i18n/petstore-messages_ko.properties
error.state.title=지금 상태에서는 할 수 없는 요청
state.PurchaseOrder.status.cancel=주문 상태가 {0}이면 취소할 수 없습니다 (가능한 상태: {1})
invariant.PurchaseOrder.lines.minsize=장바구니가 비어 있습니다
Size.lines=장바구니가 비어 있습니다
NotNull.lines=장바구니가 비어 있습니다

# Refusals before the controller - RequestShapeErrors also reads every domain bundle and the translation next to it
error.unreadable=요청 본문이 없거나 JSON 이 아닙니다.
error.method-not-allowed={0} 메서드는 여기서 쓸 수 없습니다.
typeMismatch.lines.quantity=수량은 0 이상의 정수입니다
```

- The values of a field are in Spring's order: `{0}` is the field, then the constraint attributes in name order (for `Size`, `{1}` is max and `{2}` is min).
  `Size.login=로그인은 {1}자까지` goes out with the same values for both `@Valid` and service-side validation.
- **Do not write codes without a field (`Size=`, `NotNull=`).** They override every `@Size` DSL sentence in that language, and the range is received
  only by position (`{1}`, `{2}`). Write it per field (`Size.login`), or do not write it, and the default message goes out in the request language with the numbers.
- **Do not set the language with `ValidationMessages*.properties`.** The default file (`ValidationMessages.properties`) overrides the Hibernate Validator translation of every other
  language, and if you put only a `_<language>` file, a request in a language that has no such file is answered in the language of the server machine
  (the JDK's bundle fallback). The rules above already make one response one language.

Lookup order: `messages_<language>.properties` on the classpath -> the domain bundle and the translation next to it ->
the application's `MessageSource` (for example a bundle changed with `spring.messages.basename`) -> the default text. No setting is needed.
The first two look at **the file of the request language first**: only after looking at `messages_ko` and the `_ko` files of all domains do they look at the default file (English).
So even with several domains (`billing`, `shop`), a translation of `error.unreadable` placed only in `shop-messages_ko` is not hidden by the English of
`billing-messages.properties`, which comes earlier by name.
But a translation in a bundle changed with `spring.messages.basename` is looked up after the domain bundle (the English default text), so put the translations of domain codes in
`messages_<language>.properties` or in the file next to the domain bundle.

- **Single quotes**: a sentence that takes values (`{0}`) (`state.*`, `reference.*`) follows the Java `MessageFormat` rule and writes `'` as
  `''` (`can''t`). A sentence with no values is written as it is.
- If two `@invariant`s of the same entity get the same name, `-2` is added to the second. The `+ - * /` and Korean letters and so on in a condition expression
  are put into the name in a distinguishable form (`a + b > 0` -> `a-plus-b-gt-0`).

**Translating a hand-written refusal**

The runtime exceptions thrown from `beforeSave`, root methods, service overrides and code that calls a port take a **message code**.
The translation is looked up in the same order as for a generated refusal, and if there is none, the second argument (the default text, a `MessageFormat` pattern if it has values) goes out,
and the `code` of the response becomes that code:

```java
throw new ConflictException("loan.no-copy-left", "No copy of {0} is left to lend", isbn);
```

```properties
# src/main/resources/i18n/library-messages_ko.properties (next to the domain bundle, a developer file) or messages_ko.properties
loan.no-copy-left=빌려줄 수 있는 {0} 이(가) 없습니다
```

```http
HTTP/1.1 409                       (Accept-Language: ko)
{ "title": "...", "detail": "빌려줄 수 있는 978-0-00 이(가) 없습니다", "code": "loan.no-copy-left", "status": 409 }

HTTP/1.1 409                       (no header)
{ "title": "Conflicts with stored data", "detail": "No copy of 978-0-00 is left to lend", "code": "loan.no-copy-left", "status": 409 }
```

| Exception (`io.elasticore.springboot3.`) | Status | `code` when thrown without a code |
|---|---|---|
| `validation.BadRequestException(code, message, args...)` | 400 | `bad-request` |
| `security.NotSignedInException(code, message, args...)` | 401 | `not-signed-in` |
| `security.ForbiddenException(code, message, args...)` | 403 | `forbidden` |
| `validation.NotFoundException(code, message, args...)` | 404 | `not-found` |
| `validation.ConflictException(code, message, args...)` | 409 | `conflict` |
| `http.PortCallException(code, message, args...)` | 502 | `upstream` |

- Write the default text in the code. The generated `<domain>-messages.properties` does not know hand-written codes and is rewritten on every build, so
  do not write them there. Put only the translation in the `_ko` file next to it or in `messages_ko.properties`.
- Write `'` as `''` in a sentence that has values (the default text too). The `getMessage()` of the exception is the default text with the values filled in (as it is in the log).
- If you throw without a code (`new ConflictException("...")`), that message goes out as it did before, and `code` is the last column of the table.
- A refusal that picks the state by code, like an invariant, is `InvariantViolationException(code, message, args...)` (`state.` is 409,
  `reference.` is 404, anything else 422).
- Checked in: Pet Store `ShopFlowTest.aRefusalHandCodeThrowsIsSaidByItsCode` (what a test controller threw).
- An `XxxApiErrors` that has been edited is not regenerated, so the handler uses only `ex.getMessage()`. Change it, as in a newly generated file, to
  `coded(problem(..., messages.text(ex.getCode(), ex.getMessage(), ex.getArgs())), ex.getCode())`.

**400 (field validation)** sends `fieldErrors` in addition to `errors` (a list of `"field: message"`, same as before):
`[{ "field": "lines", "code": "Size", "message": "장바구니가 비어 있습니다" }]`. The field message is looked up in the same bundle by Spring's
message codes (`Size.lines`, `NotNull.customerDTO.login`, `NotNull` ...).
The `ApiError` of the generated TypeScript client reads `code` and `fieldErrors` (field -> message).

- Runtime: `io.elasticore.springboot3.validation.ErrorMessages` (`text`, and `violation` for service-side validation),
  `RequestShapeErrors`, `InvariantViolationException(code, message, args...)`.
- An existing `XxxApiErrors` is a developer file (generate-once). If you never edited it, it is regenerated,
  and if you edited it, it is left as it is. To use the new format, delete the file and build again. Even an edited file, if you only upgrade the runtime, sends the default message of `@Valid`
  in the request language (`messages.text(f)`). For service-side validation to be the same, change `messages.text(code + "." + field, v.getMessage())`
  to `messages.violation(code + "." + field, v, v.getMessage())`.
- If there are several `MessageSource` beans and none is primary, the application's is not used (startup still works). The `messages_<language>.properties` on the classpath
  and the domain bundle are still found.
- Whether a translation exists is judged by the files on the classpath (`messages_<language>`, the domain bundle, `ValidationMessages`). A language that exists only in a bundle changed with `spring.messages.basename`
  is not counted as translated - to use that language too, put one of the files above.
- Upgrade the generator and the **runtime library (`elasticore-springboot3`/`4`) to the same version**. The generated entities use the runtime's
  `InvariantViolationException(code, message, args...)` and `ErrorMessages`, and the generated `XxxApiErrors` uses `RequestShapeErrors`
  (an old runtime does not have them, so it does not compile).

#### What the generated code needs to boot in Kotlin

Starting the Kotlin sample for real with Spring Boot (`ShopApiContractTest`) exposed three things, and they are fixed.
It works without the `kotlin-spring` plugin.

- The extension service (`OrderService`) inherits `@Transactional`, so it is an `open class`.
- The repository adapter is the target of the `@Repository` exception-translation proxy, so it is an `open class`.
- Entity collections are `MutableList<X>`. `List<X>` compiles to `List<? extends X>` for an open entity type,
  and Hibernate refuses it as a raw collection.

A Kotlin app that uses Spring needs `org.jetbrains.kotlin:kotlin-reflect`.

#### Aggregate members - write only through the root

- The getter of a **collection member** returns a read-only view - in Java
  `Collections.unmodifiableList`, in Kotlin the property getter is `MemberList.view(field)`.
  `order.getItems().add(x)` throws `UnsupportedOperationException` - before, it silently disappeared at save with the owner
  not set. Add and remove with `addOrderItem` / `removeOrderItem` /
  `replaceItems`.
- **Deleting a member is also a write of the root.** A member of a root that declares invariants gets
  an `OrderItemRemovalCheck` listener, and when the member row is deleted (flush), it checks the root's invariants
  against the remaining state. Deleting the last order item is now refused. A replacement
  (`replaceItems`) is allowed because it is already filled at flush time. For this,
  `removeX()` does not clear the member's reference to the root (the row is deleted in the same flush).
- A **1:1 member** (`@OneToOne` + the same aggregate) travels in the root DTO, and on update it
  **updates the existing member in place** (it does not replace it with a new row). A member has no endpoint of its own, so
  if you remove it from the root DTO with `--`, there is no way to reach it.
- `occurredOn` of a **domain event** is the time `raiseX()` was called. The event object is
  created after the identifier exists (at publish), but the time is fixed at raise.

### A wrong combination fails

A file shell is language-specific by definition. A bundle is only a list of names, so a wrong combination
**goes unnoticed** - a template is text, and Java inside a `.kt` file shows up only when the compiler
runs.

So a shell says which language it writes:

```java
c.requireSyntax(Syntax.KOTLIN, "kt-dto-file");
```

If it does not match, it stops on the spot, saying which rule tried to use which language.

### Choosing rules per language

`publisher.rules` sets **the server's rules**. Java and Kotlin are both servers, so
they use the same key. TypeScript uses **its own key**, because its output is a different program.

| Key | Applies to |
|---|---|
| `publisher.rules` | Java, Kotlin |
| `publisher.rules.kt` | Kotlin (takes precedence over the one above if present) |
| `publisher.rules.ts` | TypeScript |

If you write a Kotlin bundle in `publisher.rules`, TypeScript generation inherits it and
Kotlin rules end up in `.ts` files - so TypeScript does not inherit.

### Combining bundles

You combine bundles by listing names. A language bundle and an architecture rule are **different axes**, so
you write them together.

```yaml
    publisher:
      rules: [jpa-springboot-kt, ddd-aggregate]   # Kotlin + aggregate boundary
```

If a bundle is already pre-combined for a language, it is better to use that - `ddd-springboot` and
`ddd-springboot-kt` are two language versions of the same reading, and the only differences are what the language decides
(whether a value object is made with Lombok or with the language). The table "Rules that reach each language differently" above says
which language each rule reaches.

### Turning off just one rule

```yaml
    publisher:
      suppress: [jpa-comment]   # no longer attach the @Comment annotation
```

The values of `suppress` are the ids written in `@Rule(value = ...)`.

---

## 3. Writing rules

> **If you want to add your team's rules in a project** (a rule module, `elasticore { rules = [...] }`, a new file kind,
> even tests), read the example-centered `elasticore-ddd-customize-guide.md` first.
> This chapter is an API reference for people who modify ElastiCORE's own rules.

### 3.1 Minimal form

```java
package kr.co.acme;

import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

public class AcmeRules implements CodeRules {

    /** Attach the company-standard annotation to @masked fields. */
    @Rule(value = "acme-masked", target = "dto", order = 50)
    void masked(EntitySpec e, FieldSpec f) {
        if (!f.hasDsl("masked")) return;
        ctx(e).field(f).ensure(AnnoCode.of("kr.co.acme.security.Masked"));
    }
}
```

`CodeRules` is a marker interface. It has no method to implement, and
a method with `@Rule` becomes a rule.

### 3.2 `@Rule` attributes

| Attribute | Default | Meaning |
|---|---|---|
| `value` | (required) | The rule id. Used for tracing and `suppress` |
| `target` | `"entity"` | Which artifact it contributes to |
| `order` | `0` | Lower runs first. If equal, registration order |
| `phase` | `HEAD` | `HEAD` is before the field rules, `TAIL` is after |

`target` uses the same words as the namespace keys in env.yml:
`entity`, `dto`, `srchdto`, `enum`, `repository`, `service`, `control`, `mapper`, `port`, `Q` and so on.

### 3.3 The method signature decides when it is called

| Signature | When |
|---|---|
| `(EntitySpec e)` | Once per entity |
| `(EntitySpec e, FieldSpec f)` | Once per field |
| `(DomainSpec d)` | Once per domain (Q, mapper, support types) |
| `(EnumSpec e)` | Once per enum |
| `(ProtoServiceSpec p)` | Once per proto port |

The method returns `void` and needs no access modifier.

### 3.4 Execution order

```
HEAD entity rules  ->  field rules (per field)  ->  TAIL entity rules
```

File shell rules (the ones that fill `importList`, `classDeclaration` and so on) are usually
`phase = TAIL, order = 90` - the import list must be rendered **after** all field rules have registered
their imports.

---

## 4. Reading the model - Spec

### `FieldSpec` - the common ones

```java
f.name()                 // field name
f.capitalName()          // name with a capital first letter, for accessors
f.typeName()             // declared type
f.coreItemType()         // X of List<X>, otherwise its own type
f.required()             // marked with !
f.isList()               // is it List<...>
f.isBaseType()           // primitive / wrapper / string
f.isBaseItemType()       // is the item type one of those
f.isEntityRef()          // is it an entity reference
f.label()                // description for comments

f.hasDsl("id")                     // has @id (case-insensitive)
f.hasDslAny("min", "minval")       // any one of the aliases
f.dslValue("length")               // the 200 of @length(200)
f.dslValueAny("label", "desc")     // the first one that matches
```

### `EntitySpec`

```java
e.getName()              // entity name
e.pkg("dto")             // namespace (key in env.yml)
e.fields()               // own fields
e.allFields()            // including inherited ones
e.dtoFields()            // fields on the DTO side
e.pkFields()             // @id/@pk fields
e.parentName()           // @extend target
e.hasMeta("expose")      // meta annotation
e.metaValue("dynamicupdate")
e.isKnownEnum(type)      // is it an enum the model knows
```

> `Field.getType()` is private. Read the type through `FieldSpec`.

---

## 5. Writing code - `SourceCode` / `FieldCode` / `AnnoCode`

`ctx(spec)` returns the `SourceCode` of that artifact.

### 5.1 Per field - `FieldCode`

```java
ctx(e).field(f)
      .ensure(anno)          // add if missing (recommended)
      .add(anno)             // always add
      .merge(anno)           // merge attributes into the same annotation
      .replace(anno)         // replace
      .removeAnnotation(fqcn)
      .type("String")        // change the declared type
      .rename("nameFrom")    // change the name
      .initial("0")          // initial value
      .before("/* ... */")   // above the declaration
      .after("...")          // below the declaration (a separate member)
      .comment("...");
```

To drop a field entirely, use `ctx(e).removeField(f)`.
To not create the artifact at all, use `ctx(e).dontMake()`.

### 5.2 Annotations - `AnnoCode`

**Do not build annotations by concatenating strings.** `AnnoCode` does that job.

```java
AnnoCode.of("jakarta.persistence.Column")
        .val("name", "user_id")        // name = "user_id"  (quoted)
        .valRaw("length", "%d", 200)   // length = 200      (as is)

AnnoCode.of(VALIDATION, "NotNull")     // format + simple name
AnnoCode.of("...JsonInclude").valueRaw("...Include.NON_NULL")   // value = ...
AnnoCode.of("...").qualified()         // render with the fully qualified name
AnnoCode.of("...").block("  ")         // one attribute per line
```

For a nested list (`indexes = {...}`) use `AnnoCode.AnnoList.of(...)`.
The renderer knows whether the brackets are Java `{}` or Kotlin `[]`.

### 5.3 Template slots - `SourceCode`

```java
c.var("packageName", e.pkg("dto"))          // ${packageName}
c.var("className", "%sDTO", e.getName())    // with a format
c.varLine("methodList", "...")              // add one line to a slot
c.varLines("importList", list)              // several lines
c.line("forward", "...")                    // to a named block (mapper etc.)
c.addImport("%s.%s", pkg, name)             // register an import
c.renderImports()                           // render only what is actually used
c.renderFieldBlock(1)                       // field block (number of blank lines)
c.ensureClassAnnotation(anno)
c.renderClassAnnotations()
c.template("dto")                           // use another skeleton
```

> `var(key, null)` is **ignored** (not stored). To get an empty string, pass `""`.
> A line with an unresolved `${key}` becomes a blank line as a whole - this is not an error.

---

## 6. The language seam - `Syntax`

**This is the most important rule in this architecture.**

> Do not put a language branch (`if (kotlin) ... else ...`) in a rule.
> A rule says only *what* to emit; `Syntax` answers *how* to write it.

The reason: every branch you add to a common rule adds one more place where **fixing Kotlin breaks Java**.
If you go through the seam, that place never exists structurally.

```java
Syntax s = ctx(e).syntax();

s.importLine(fqcn)                  // import x;      /  import x
s.declaration(mod, type, name, init, required)
s.typeName("Integer")               // Integer        /  Int
s.nullableType("String")            // String         /  String?
s.classLiteral("X")                 // X.class        /  X::class
s.javaClassLiteral("X")             // X.class        /  X::class.java
s.statement("return x")             // return x;      /  return x
s.methodSignature(mod, ret, name, params)
s.parameter("String", "x")          // String x       /  x: String
s.getter("dto", "name")             // dto.getName()  /  dto.name
s.setter("to", "name", v)           // to.setName(v)  /  to.name = v
s.setterRef("to", "name")           // to::setName    /  { v -> to.name = v }
s.localVar(type, name, init)        // T x = init;    /  val x = init
s.newInstance("X", "")              // new X()        /  X()
s.lambda("e", body)                 // e->body        /  { e -> body }
s.sam(type, params, body)           // (p) -> body    /  Type { p -> body }
s.notNull("x")                      // x              /  x!!
s.escapeName("in")                  // in             /  `in`
s.cast("short", "1")                // (short) 1      /  1.toShort()
s.overriding()                      // public         /  override
s.injectedProperty(type, name)      // private final T x;  /  val x: T,
s.needsImport(fqcn)                 // excludes types already in scope
```

### The parts of the seam that are JVM-shaped

What we learned when we added TypeScript: **8 of the 27 are JVM concepts**.
`classLiteral`, `javaClassLiteral`, `sam`, `samBlockOpen`, `samBlockClose`,
`lambdaReturn`, `injectedProperty`, `overriding`.

TypeScript **does not answer them - it throws an exception.** If it returned a plausible value,
a rule that assumes the JVM would pass silently and emit code that makes no sense.
In practice `dto-jsonview` hit exactly this, so that rule was fixed to
ask `supportsAnnotations()` first.

`importLine` is **the one that is really broken.** It was built for a language where the fully
qualified name is the import, but a TypeScript import is **a module path + a name**, and the fully
qualified name says nothing about the path.

### What the seam cannot solve

When the **role** differs, not the syntax, the seam cannot solve it. In that case,
**split the rule and let the bundle choose** - not a branch.

Examples:

- Lombok is a Java-only tool. `@Builder.Default` is not something Kotlin "writes differently" - it is
  **a concept that does not exist** there -> split it into a Java-only rule
- Java accumulates conditions by reassigning the `Specification sp` parameter,
  but **Kotlin cannot reassign a parameter** -> split the shell per language

This is why it is divided into `MapperBaseRules` (the shared decisions) and `MapperAssembleRules` /
`KotlinMapperAssembleRules` (the per-language shells).

---

## 7. Skeletons (`.tmpl`)

```
resources/elasticore-template/
    entity.tmpl          # 2.x compatible
    v3/entity.tmpl       # clean mode
    kt/entity.tmpl       # Kotlin
    ts/dto.tmpl          # TypeScript
```

`target`, the template file name and the env.yml namespace key are **the same word**.
There is no separate mapping table, so they cannot drift apart.

A target that has no skeleton of its own in v3 falls back to the compatible skeleton.
**But if the language differs, it does not fall back - it throws an exception.** The fallback is written
in Java, and this stops Java from silently entering a `.kt` file. Only an artifact that is
no language at all, such as `.proto`, is the exception.

---

## 7.5 Ownership of generated files (elcore3)

In 3.0 code, all generated files, Java and Kotlin included, are **merged** without a first-line mark: your edits stay and DSL changes come in too,
and a generate-once file accepts only changes that merge cleanly and warns about the rest (`.elasticore/cache`,
`elasticore-env-guide.md` section 9-2). The table below is the rule for **files that have a first-line mark** - `output = 'v2'`, `merge = false`,
and files in a merging project where the base cannot be confirmed, so the mark is still there (9-2 "projects that had marks"). When marks are written, mapper
XML, SQL, properties, `.proto` and TypeScript use `<!-- //ecd:... -->`, `-- //ecd:...` and `//ecd:...`, and `orm.xml` and message
files are rewritten every time.

| File state | Behavior |
|---|---|
| `//ecd:` hash + managed file, content equals the hash | Rewritten to match the DSL |
| `//ecd:` hash + managed file, the user modified it | Skipped (overwritten with `-Pforce`) |
| `Initially generated ... no longer managed` mark, **never modified** (hash matches) | Rewritten to match the DSL - for example, adding `@readonly` to an entity removes the write endpoints from the controller |
| The same mark, the user modified it | The user's - skipped. But the two things below are still kept in sync |
| No `//ecd:` line | The user's - skipped (same as 2.x) |

What the generator still does for a file that has become user-owned:

- **Table annotations of a `@behavior` entity**: `@Entity`/`@Table`/`@DynamicUpdate`/`@Audited` and so on are
  replaced to match the DSL; other annotations the user added and the body are left alone.
  An annotation split over several lines is not touched, and a warning is given.
- **The constructor of an extension service**: when the core service constructor changed (for example, a switch to hexagonal), the file is
  rewritten if it holds no code other than the constructor; if it does, the new `super(...)` is reported as a warning.
- In a run where the core service generation failed, the extension service and the controller are not created
  (so that no file generated once in a failed shape is left behind).
- Code inside a preserved block (`// !---------- ... ------------!`) is kept even when a managed file is rewritten.

### Packages that contain a Kotlin reserved word

The `in` in the hexagonal convention package `adapter.in.web` is a Kotlin reserved word. In Kotlin output,
reserved-word parts of packages, imports and qualified names are wrapped automatically, like `` `in` `` (string literals excluded).
In a Kotlin file you write yourself, you must also write `package com.acme.adapter.`in`.web`.

## 8. What to keep to

1. **No string concatenation.** Use `AnnoCode` instead of `"@Column(name = \"" + n + "\")"`,
   and `String.format` or `Formatted` for anything else.
   `Formatted.text(fmt, args)` checks the number of format arguments **at the call site**.
2. **Comments in English.** Write **ASCII only** in the Java sources of `elasticore-base` -
   compilation breaks on Korean Windows (MS949). Use `->` instead of `→`.
3. **File I/O in UTF-8.** No `FileWriter`/`FileReader`.
4. **No language branch in a common rule.** See section 6.
5. **A new rule emits a warning but does not throw.** Validation while the model loads is recorded
   as `DSL_WARN`.

---

## 9. Verification

After you change a rule, you **must** pass the following.

```bash
# 1. Unit tests
./gradlew :elasticore-project:elasticore-base:test

# 2. Same as the 2.x output (compatibility mode)
./gradlew elcoreRulesCheck -Pstrict

# 3. Generate -> compile -> run (Kotlin + TypeScript)
./gradlew :elasticore-project:elasticore-template-kt:elcore3Verify

# 4. Run the generated TypeScript with node
./gradlew :elasticore-project:elasticore-template-kt:elcore3tsTest
```

> **There is one reason in this language why a type check alone is not enough.** Decimals
> arrive as strings, so a hand-written `order.totalAmount > 100` is accepted by the compiler and JavaScript
> answers false regardless of the amount - the only defect this language causes is exactly what a type
> check cannot see. That is why the generated specifications and invariants are **run** with node.

> The verification tasks **empty the generated tree first.** A file with a generate-once marker is
> not regenerated when you fix a rule, so a stale file can be checked and reported as passing.
> This happened once in this project.

**Number 2 is the key.** It fails if the compatibility-mode output differs from 2.x by a single character.
This is the guarantee that the generated code of existing users does not change.

Register an intended difference in `RulesVerifyRunner.ACCEPTED_DIFFS` **with its reason**.

### Gradle plugin (recommended)

Instead of writing tasks by hand, apply the plugin.

`settings.gradle` (once per project):

```gradle
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

`build.gradle`:

```gradle
plugins {
    id 'io.elasticore' version '3.0.261031-rc7'
}

elasticore {
    architecture = 'ddd'       // layered | ddd | hexagonal
    language     = 'kotlin'    // java | kotlin | typescript
}
```

The `elcore` and `elcore3` tasks appear, and `compileJava` waits for generation.

**If you do not use the `elasticore { }` block, nothing changes.** No rule
property is passed at all, and the generator takes the path it always took.
"Fixing" the default to `layered` is not the same as passing nothing.

**You cannot set architecture and language separately.** The two words are composed in `RulesConfig` into
one bundle and one language switch, so a bundle and a switch cannot
disagree. The plugin does not hold this table;
it passes the two words on as they are.

#### Multi-module

Apply it at the root, and it finds the submodules that have `src/main/resources/blueprint` automatically.
Unlike the hand-written tasks, no particular module name is hard-coded.

```gradle
elasticore {
    modules = ['core-domain', 'app-admin']   // only when you want to override auto-discovery
}
```

#### Existing switches

`-Pdomain=shop`, `-Pmode=valid`, `-Pforce`, `-Pv3`, `-Pv2`, `-Pkt`, `-Pts` are all passed
through as they are. Adopting the plugin does not change your commands.

#### Which generation of code (`output`)

From 3.0.261028-rc3, the plugin generates **3.0 code** (`jpa-springboot-v3`) for a layered Java project that declares nothing. A project that keeps the code the 2.x generator wrote
decides it with one line:

```gradle
elasticore {
    output = 'v2'      // 2.x code. The build calls the 2.x generator (elcore), and elcore3 also writes 2.x compatible code
}
```

| Setting | Generated code |
|---|---|
| None | 3.0 code. But if there is source generated by 2.x (first line `//ecd:..._V1.x`/`_V2.x`) or the last `elcore3` run was 2.x compatible (`build/elasticore/server.mode`), it **stops without writing any source** and tells you to choose |
| `output = 'v3'` / `-Pv3` | 3.0 code (even if 2.x source exists - the choice to move over) |
| `output = 'v2'` / `-Pv2` | 2.x code. Only layered Java works - it refuses when combined with hexagonal, DDD, Kotlin or project rules, or when the domain `env.yml` lists rules that exist only in v3 (`jpa-springboot-v3`, `ddd-...` and so on). `rules = ['ossie-export']` can be combined - it writes no code, and the 2.x generator also exports the semantic model |
| `publisher.rules: [jpa-springboot]` in the domain `env.yml` | 2.x code (the value the setup wizard before 3.0.261028-rc3 wrote for layered Java) |

`-Pv2` and `-Pv3` decide that one run only. If you forget to write them every time, different code is generated, so write the project's choice
as `output`. The behaviors that change when you move to 3.0 code are in `elasticore-migration-guide.md`.

#### TypeScript client (`elcore3ts`, `typescriptClient`)

The TypeScript client in `src/main/ts` uses only the `elcore3ts` task. `build` and `compileJava` do not call that task,
so if you leave it after a model change, the client lacks the new fields and entities. To make the build use it as well:

```gradle
elasticore {
    architecture     = 'hexagonal'
    typescriptClient = true      // default false. Runs elcore3ts after elcore3, before compile
}
```

Without this setting, if `src/main/ts` holds a generated `.ts` (in `.elasticore/cache`, or with `//ecd:` on the first line) and that client was not made from the current model,
`elcore3` warns once at the end:
`[elcore3] .../src/main/ts holds a generated TypeScript client made from an older model - run the elcore3ts task after a
model change, or set typescriptClient = true in the elasticore { } block so the build runs it.`
`elcore3ts` leaves a fingerprint of the model it used (all `.yml` files of the blueprint domain folders, plus the `architecture`, `language` and `rules` settings and the ElastiCORE
version) in `build/elasticore/ts-client.model`, and `elcore3` says nothing if that fingerprint equals the current one - a project in which another task of the build
script (`webBuild` and so on) calls `elcore3ts` is also quiet if nothing changed. If you delete the `build` folder,
`elcore3` tells you every time until you run `elcore3ts` again.

`-PlistRules` does not generate; for each domain it shows the currently selected rules per file kind (target) in
execution order (including the id used in `suppress`). It creates and deletes no files.

#### Adding project rules

```gradle
elasticore {
    rules = ['com.acme.rules.AcmeDddRules']   // added after the chosen bundle
}
dependencies {
    elasticore project(':acme-rules')          // generator classpath (does not go into the application)
}
```

Whatever you add to the `elasticore` configuration, `elasticore-base` is not removed, and the module you declare here is compiled first, without
waiting for generation. For examples and how to test, see `elasticore-ddd-customize-guide.md`.

### Apache Ossie semantic model export (`ossie-export`)

[Apache Ossie](https://github.com/apache/ossie) (incubating) is the ASF's vendor-neutral standard for exchanging semantic metadata between analytics, BI and AI platforms -
dataset (table), field (column), relationship (join), metric (measure).
The ElastiCORE blueprint already says everything the dataset side requires, so it exports it
without declaring the same content twice. It is based on **core spec `0.2.0.dev0`** (`core-spec/spec.md`, `ossie-schema.json`).

How to turn it on (either one):

```gradle
elasticore {
    rules = ['ossie-export']          // the bundle stays; only the export is added
}
```

A project that keeps 2.x code (`output = 'v2'`, `./gradlew elcore`) turns it on with the same line - this rule writes no code,
so it is not refused together with `output = 'v2'`.

```yaml
    publisher:
      rules: [hexagonal-springboot, ossie-export]   # env.yml: write it together with the bundle
```

Every time you generate, `semantic_model.yml` is rebuilt in the blueprint root (the whole project is **one model** -
so that relationships across domains are not cut).

The model name (`name`) is the domain names joined with `_` (`billing_catalog_shop`). With many domains, BI and AI tools show the model as a
sentence-like name, so you can choose the name - if you do not write it, the name is the same as before:

```gradle
elasticore {
    rules     = ['ossie-export']
    ossieName = 'acme_erp'            // -Delasticore.ossie.name=acme_erp (without the plugin)
}
```

Write the name with letters, digits and `_`. Other characters become `_`, and generation prints the warning `[ossie] model name 'acme-erp' is written as
acme_erp`. Only `name` changes; `description` and the `domains` of `custom_extensions` still list the
domains as they are.

Part of the Pet Store:

```yaml
version: 0.2.0.dev0
name: petstore
datasets:
  - name: PurchaseOrder
    source: ps_purchase_order
    primary_key: [order_id]
    description: "Order"
    fields:
      - name: status
        expression:
          sql: "status"
        datatype: String
        ai_context: "Codes: PLC = Placed, SHP = Shipped, CAN = Cancelled"
      - name: total
        expression: { sql: "total" }
        datatype: Decimal
      - name: city                       # a column of the embedded value (deliveryAddress)
        description: "deliveryAddress.city"
    custom_extensions:
      - vendor_name: ELASTICORE
        data: "{\"owner\": \"customerId\", \"access\": {\"read\": \"owner\", ...},
                \"states\": {\"status\": {\"initial\": \"PLACED\", \"ship\": \"PLACED -> SHIPPED\", ...}},
                \"omitted_columns\": [\"credit_card_number\"]}"
relationships:
  - name: PurchaseOrder_customer
    from: PurchaseOrder
    to: Customer
    from_columns: [customer_id]
    to_columns: [customer_id]
```

**DSL -> Ossie mapping**

| DSL | Ossie | Note |
|---|---|---|
| Entity (except `@abstract` and `@embeddable`) | `datasets[]` | `source` = the table (`@table`, or `table.prefix` + name) |
| `-- label` | `description` | |
| `@id` field (including a composite key) | `primary_key` | |
| `@unique`, `@Index(unique="true")` | `unique_keys` | By the column name of the dataset field - an attribute name in `columnList` (`seatNo`) becomes the column of that field (`seat_no`, or the `@column` name if there is one), a column name belongs to that field regardless of case (`HALL_ID` if `@column(HALL_ID)`), and any other name follows Hibernate (`seqCd` -> `seq_cd`). The same key appears once |
| Value field | `fields[]` + `expression.sql` (column) + `datatype` | The type mapping is the "Value types at a glance" table in the DSL guide |
| `@formula(sql)` | `expression` = that SQL | A computed column. An expression goes in `expression.sql`; if it contains a subquery (SELECT), one `ANSI_SQL` entry in `dialects` - see "Form of the expression" below |
| Fields of `@extend(Parent)` | `fields` of the child dataset | Because the inherited columns are also in that table |
| `@Embedded` value object | Its columns (`deliveryAddress.city`) | `prefix_city` if `@embedded(prefix)` |
| enum field | `datatype: String` + `ai_context: "Codes: PLC = Placed, ..."` | The table holds only codes, so the meaning goes with them |
| Reference to another entity | `relationships[]` + join column field | The actual join column (`customer_id` if `referenceColumn: short`) |
| `@password`, `@writeonly` | **Not exported** | Values the application does not read back either. Recorded in `omitted_columns` |
| `states:`, `@access`, `@owner`, `@event` | `custom_extensions` (`ELASTICORE`) | Ossie has no vocabulary for them |
| `@compute`, `@snapshot`, `money`/`quantity` | `custom_extensions` of the field | Derived rules and value types |
| A metric in `metrics:` | `metrics[]` (`expression.sql`, `datatype`, `description`) | Only what you declare. The syntax is "Business metrics" in the DSL guide |
| `@terms(…)` (entity, field, metric) | `ai_context.synonyms` | Business terms |

**Form of the expression**: The Ossie `expression` has a top-level `sql` and/or a `dialects` list, at least one of them. The top-level `sql` is
the portable form, written in `OSSIE_SQL_2026`, the only dialect whose meaning the spec defines
(`core-spec/expression_language.md` - column references, arithmetic, comparison and logical operators, `CASE WHEN`, aggregates, functions such as `COALESCE`, `NULLIF` and `CAST`).
If both are present, `sql` wins. The dialect name in `dialects` is a free string, so a consumer **may ignore** an entry such as `ANSI_SQL`.
That is why field expressions that are column names and metric expressions built from `metrics:` are all exported as the top-level `sql`.
`@formula` splits by expression:

```yaml
# @formula("unit_price * quantity") - an expression within that language
expression:
  sql: "unit_price * quantity"

# @formula("(select count(*) from ...)") - a subquery
expression:
  dialects:
    - dialect: ANSI_SQL
      expression: "(select count(*) from ...)"
```

A subquery cannot be written in that language (spec "Not Supported in Expressions": SELECT/FROM/JOIN, subqueries, CTE), so only one `ANSI_SQL` entry is exported,
without a top-level `sql` - the reader uses that SQL if it understands it, and skips it if not. A `@formula` field
carries `"formula": "database"` in `custom_extensions` to say that the database computes the column.
Up to 3.0.261027-rc2, every expression was exported as a `dialects` list (a portable expression was `dialect: OSSIE_SQL_2026`). After Ossie fixed the top-level
`sql` as the portable form (apache/ossie #617), it changed to the current form - the content of the expression is the same.

**What is not exported**: metrics you did not declare - inventing a revenue formula from entity fields would be exporting a guess.
`is_time` is not used - the spec decides the default from `datatype` (Date/Time/DateTime/DateTimeTz),
and that answer is this model's answer.

**Verification**: `SemanticModelTest` in `elasticore-sample-petstore` and `elasticore-template-ddd` checks on every build that every exported
table, column, primary key and join column exists in the real DB. Validating the two files with Ossie's `ossie-schema.json`
gave 0 errors (2026-10-03), and after the switch to the top-level `sql`, validating the two files and the
subquery `@formula` form with Ossie's `validation/validate.py` also passed (2026-10-28) - both were run by hand and are not in the build tests. The earlier output (a `semantic_model:` array, no `expression`, `money` -> String, enum and embedded columns
missing, 2.x-style join columns, password columns included) did not match the spec - this was fixed in 3.0.260925.

A failed export degrades only the export, not the build. It is a sidecar of the same kind as schema.yml: it is always
regenerated and you do not edit it by hand. The spec is still a `dev` version, so it may change.

### Running without the plugin (manual configuration)

**`publisher.rules` is read by the rule engine. The rule engine is an entry point separate from the `@ElastiCore` annotation
processor, and a normal build does not look at this setting.** If you only write a bundle
down, the file looks configured, the build ignores it, and no warning
appears - the worst of the three possible states.

**The plugin registers this task automatically, so if you applied the plugin, you do not need
this section.** Configure it manually only in an environment
where the plugin cannot be used (no access to the internal repository, for example):

```gradle
task elcore3(type: JavaExec) {
    group = 'elasticore'
    classpath = configurations.annotationProcessor
    mainClass = 'io.elasticore.base.model.pub.rules.verify.RulesGenRunner'
    systemProperty 'elasticore.base.path', "$projectDir"
    systemProperty 'elasticore.rules.v3', 'true'
}
```

```bash
./gradlew elcore3 && ./gradlew build
```

> **Do not put `@ElastiCore` on an application class.** The two paths generate the same files
> with different rules, so a project that uses a bundle uses only the task.
> The `elasticore-template-ddd` module is exactly this configuration - it has no `@ElastiCore`
> anywhere.

The domain setup wizard prints this task as it is
when you choose an architecture other than layered.

### Try generating (in the sample modules of this repository)

```bash
./gradlew elcore3                 # as the project is configured (3.0 code - clean mode - if nothing is set)
./gradlew elcore3 -Pv3            # clean mode (3.0 code)
./gradlew elcore3 -Pv2            # compatibility mode (same output as 2.x)
./gradlew elcore3 -Pkt            # Kotlin (.kt in src/main/kotlin)
./gradlew elcore3 -Pts            # TypeScript (.ts in src/main/ts) - elcore3ts in the plugin, typescriptClient = true to run with the build
./gradlew elcore3 -Pdomain=shop   # one domain only
./gradlew elcore3 -Pforce         # overwrite modified files too
```

> **Language and rule selection are different switches.**
> `publisher.rules` in env.yml decides only *which rules to use*;
> *which tree and which extension to write to* and *which skeleton to use* are decided by `-Pkt`.
> Writing only `publisher.rules: [jpa-springboot-kt]` does not produce `.kt` files.

> The generated sources of `elasticore-template` are **golden files**. This module has `output = 'v2'`, so
> `elcore3` also writes in compatibility mode. If you overwrote them with `-Pv3`/`-Pforce`, revert them.

---

## 10. Checklist

### When you add a new rule

- [ ] Is the `@Rule(value = ...)` id unique
- [ ] Is the `target` right
- [ ] Are `order`/`phase` the moment you intended (shell rules are `TAIL`)
- [ ] Did you avoid concatenating strings
- [ ] Is there no language branch - if you need one, add it to `Syntax`
- [ ] Did you register it in `RuleCatalog.standard()` and put it in a bundle
- [ ] Does it pass the 3 verifications above

### When you add a new artifact

- [ ] The `.tmpl` skeleton (for each template set that needs it)
- [ ] The rule class - decisions in `base/`, file shells in `lang/<language>/`
- [ ] Register in `RuleCatalog` + bundle
- [ ] Add the emit to `RulesGenRunner` (`ArtifactTarget`)

### When you add a new kind of artifact (extension point)

The runner does not learn the name of a new file kind. The rule set declares it itself.

| Target | Interface | What it answers |
|---|---|---|
| One per entity | `ArtifactContributor` | `artifactsFor(EntitySpec)` |
| One per domain | `DomainArtifactContributor` | `artifactsFor(DomainSpec)` |

```java
public List<ContributedArtifact> artifactsFor(EntitySpec e) {
    if (!e.aggregateRoot()) return Collections.emptyList();   // the condition is code

    return Collections.singletonList(
            ContributedArtifact.of("event", "event", e.getName() + "PlacedEvent")
                    .orNamespace("entity"));                  // fallback namespace
}
```

What to keep to:

- **Give `orNamespace(...)`.** A new artifact comes with a new place, but an existing
  `env.yml` was written before that key existed. Without a fallback, the project must
  fix its namespaces first, or a `null` package comes out.
- **You can declare several with one target.** In that case the rule knows which one it is
  writing now from `SourceCode.artifactName()`. If you do not read it,
  all of them come out with the same content and only the last one remains.
- **The condition is code.** If it does not apply, answer an empty list. There is no condition language -
  it would only make you express twice what the spec can already say.
- **Do not assume a language.** The tree and the extension the file goes to are decided by the run. The rule
  says which languages it can be used with through `requireSyntaxAnyOf(...)`, and the rest is the runner's
  job.
- **A skeleton is needed for each language.** If `v3/<target>.tmpl` and `kt/<target>.tmpl` do not
  exist, it fails in that language - instead of silently using Java.

### When you add a new language

- [ ] Add an entry to `Syntax`
- [ ] Add an entry to `SourceCode.TemplateSet`
- [ ] Skeletons in `resources/elasticore-template/<language>/`
- [ ] File shell rules in `lang/<language>/` (decision rules are shared as they are from `base/`)
- [ ] Register the bundle
- [ ] **A verification module that really compiles the generated output** -
      `elasticore-template-kt` is the example. String comparison alone cannot catch output that
      "looks like that language but does not compile"

---

## 11. Package structure

```
pub/rules/
    Rule.java, CodeRules.java, RuleEngine.java, RuleCatalog.java, RulesConfig.java
    EntitySpec, FieldSpec, DomainSpec, EnumSpec, ProtoServiceSpec
    SourceCode, FieldCode, AnnoCode, Syntax, Formatted
    base/          # language-neutral rules + shared helpers
    lang/java/     # Java file shells
    lang/kotlin/   # Kotlin file shells
    verify/        # elcoreRulesCheck, elcore3 runner
```

**Only the file shells have to be rewritten when you add a new language.**
The rules that decide what goes on which field are shared as they are from `base/`.
