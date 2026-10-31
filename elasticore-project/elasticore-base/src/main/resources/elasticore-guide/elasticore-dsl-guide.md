# ElastiCORE YAML DSL Guide

## 1. YAML DSL Overview

The ElastiCORE DSL is a YAML-based domain model definition language.
You declare Entity, DTO, Enumeration, Repository, Transaction Port and Domain Service,
and Java or Kotlin code is generated at build time.

| Top-level key | Meaning | Chapter in this document |
|---|---|---|
| `entity:` | Entity | Chapter 2 |
| `dto:` | DTO | Chapter 4 |
| `enumeration:` | Enum | Chapter 5 |
| `repository:` | Repository query | Chapter 6 |
| `port:` | Transaction port | Chapter 7 |
| `domainService:` | An operation that belongs to no aggregate | **Chapter 8** |
| `processManager:` | Receives events and drives the next step (including events from other contexts) | Chapter 8 |
| `metrics:` | Business metrics (revenue = total excluding cancelled ...) | Chapter 2, "Business metrics" |

Inside an entity, `commands:` (business commands) and `states:` (lifecycle) sit at the same indentation as `fields:` (Chapter 2).

### 1.1 The syntax is six principles, and nothing else

It is a format that developers write by hand and reviewers read. When a new feature arrives, no shape outside the principles below is introduced.

| # | Principle | Example |
|---|---|---|
| 1 | **One line is one field** - that line decides storage, validation, search and exposure | `name: string(30)! @search(like)` |
| 2 | **Common things are symbols next to the type** | `(30)` length, `!` required, `(10.2)` precision |
| 3 | **Entity-level rules are one `meta:` line** | `meta: entity @root @editable @expose(20) @access(read=public)` |
| 4 | **There are defaults** - you write only what differs | `@expose(20)` alone gives create, read, search, paging and REST (update only on `@editable` roots and fields in the DDD bundle) |
| 5 | **A YAML section is a concept** | `entity`, `dto`, `enumeration`, `states`, `metrics`, `port` |
| 6 | **Text after `--` is a description** - the model is the documentation | `-- Sales unit (Large, Male Puppy ...)` |

Taking one line apart (`Item` of the pet store):

```yaml
unitCost:  money(10.2)!  @min(0.01, 'price must be positive')  @terms(price, 가격)
```

| Piece | Meaning |
|---|---|
| `unitCost` | Name |
| `money(10.2)` | Type and precision (10 digits in total, 2 after the decimal point) |
| `!` | Required |
| `@min(0.01, 'price must be positive')` | A rule and its message |
| `@terms(price, 가격)` | The words people use for this value (synonyms that BI and AI look for) |

What this one line generated (the actual generated output):

| Where | Generated |
|---|---|
| JPA entity | `@Column(name = "unit_cost", nullable = false, precision = 10, scale = 2)` + `Money` converter |
| Request DTO (400 validation) | `@NotNull` `@DecimalMin(value = "0.01", message = "price must be positive")` |
| TypeScript validation | `else if (value.unitCost != null && Number(value.unitCost) < 0.01) errors.unitCost = "price must be positive";` |
| Semantic model (Apache Ossie) | `unit_cost` field, `synonyms: ["price", "가격"]` |

Written by hand, the same fact is written four times in four places, and the build succeeds even if you miss one. Here, changing one line changes all four places together.

**New features have the same shape.** The syntax you have to learn does not grow with each feature.

| Feature | Shape | Example (pet store) |
|---|---|---|
| Lifecycle | One line `command: from -> to` | `ship: PLACED -> SHIPPED` |
| A state that can be deleted | One line in the same `states:` | `delete: CANCELLED` |
| Business metric | One line `name: expression @mark -- description` | `revenue: sum(PurchaseOrder.total) where status != CANCELLED @terms(sales, 매출액) -- Revenue ...` |
| Access and ownership | A mark on the `meta:` line | `@owner(customerId) @access(read=owner, create=user, cancel=owner)` |
| Type or event of another context | `context:Name` | `catalog:Product`, `on: [shop:OrderPlaced]` |

When the DSL is wrong, the generator says **in one sentence** what is wrong and how to fix it
(`... names SHIPED, which OrderStatus does not have (it has PLACED, SHIPPED, CANCELLED)`). A person or an AI reads that sentence
and fixes it right away.

- File location: `src/main/resources/blueprint/{domain}/`
- File extension: `.yml`
- File layout: splitting by model type is recommended (entity.yml, dto.yml and so on)
- Build command: `./gradlew build` or `./gradlew elcore`
- Settings: `env.yml` (see elasticore-env-guide.md for details)

---

## 2. Defining an Entity

### Basic structure
```yaml
entity:
  EntityName:
    meta: entity [@annotations]
    fields:
      fieldName: fieldType [@annotations] -- comment
      fieldName: fieldType [@annotations] -- label | description  # extended form: "|" separates label and description
```

**How the `--` comment parser works**
- Simple form (`-- text`) -> generates an `@label(text)` annotation
- Extended form (`-- label | desc`) -> generates two annotations, `@label(label)` + `@desc(desc)`

**Reading the comment string at runtime**
- `field.getDescription()` -> returns the `--` comment text (label first, falls back to desc if there is no label). Supported from `v2.2.260421-2+`
- `field.getAnnotationValue("label")`, `field.getAnnotationValue("desc")` -> read each annotation value directly (the existing way, still valid)

### Meta annotations
| Annotation | Description |
|-----------|------|
| `@abstract` | Abstract entity (for inheritance only, no table is created) |
| `@extend(Parent)` | Inherits a parent entity |
| `@expose(n)` | Generates REST Controller + Service + SearchDTO. n = page size |
| `@service(n)` | Generates Service + SearchDTO (no Controller). n = page size |
| `@pageable` | Paging support |
| `@audited` | Subject to JPA Envers auditing |
| `@dto` | Marks that a DTO can be generated |
| `@embeddable` | JPA Embeddable type |
| `@table(name)` | Sets the table name directly |
| `@schema(name)` | Sets the DB schema. Generates `@Table(schema="name")`. Since: 2.1.260409 |
| `@java(fullClass)` | Sets the FQCN of the generated Java class |
| `@Index(name="idx", columnList="col1,col2")` | Defines a table index |

#### Meta annotations of the DDD rule sets

The annotations below work only in projects that select a `ddd-springboot` / `hexagonal-springboot` family bundle.
For parameters and examples of generated output, see `elasticore-annotation-guide.md`.

| Annotation | One-line meaning |
|-----------|---------|
| `@root` | This entity is an aggregate root (required only on a root that is held in the list of another root) |
| `@aggregate(Root)` | This entity is a member of that root (usually unnecessary - inferred from the `@OneToMany` list of the `@root`) |
| `@event(A, B)` | What can happen to the root. Generates the event classes + the methods that raise them |
| `@invariant(condition, 'message')` | What the root always keeps true. Checked when a row is written |
| `@factory(name)` | The place that creates a valid aggregate (generate-once) |
| `@behavior` | On a field, it cannot be sent in a request and changes only through business methods. (A root is split into `XxxBase` + `Xxx` even if you do not write it. On an `@editable` root or member that is not split, it only warns - use `@readonly` there) |
| `@specification(name, condition)` | A domain rule worth giving a name. Generated as a `Predicate` |

The condition syntax of `@invariant` and `@specification` is limited to **fields, comparisons and literals**
(`items.size > 0`, `totalAmount > 100`, `notes notNull`, `items notEmpty`).
You cannot use `>=` and `:` - this is not an omission, because the DSL annotation parser and
YAML read each of them differently. The annotation guide explains the reasons in detail.

### Field annotations
| Annotation | Description |
|-----------|------|
| `@id` | Primary key |
| `@sequence` | Auto-increment sequence |
| `@genid` | Custom ID generator |
| `@GeneratedValue` | JPA generated value |
| `@length(n)` | Maximum string length |
| `@default(value)` | Default value |
| `@search(like\|eq\|between\|in\|isnull)` | Search condition (see annotation-guide). `isnull` searches for empty values - search property `<field>Empty: Boolean` (v3 bundles) |
| `@updatable(false)` | Field that cannot be updated |
| `@readonly` / `@noinput` | Field decided by the server: it goes out in the response and is not accepted from the request. v3 bundles also do not write it from DTO -> entity in the mapper and drop `@NotNull`/`@NotBlank` from the DTO (annotation-guide §2.2) |
| `@ref(path)` | Refers to another field |
| `@dtype(type)` | Sets the DTO conversion type |
| `@cascade(ALL\|PERSIST\|MERGE\|REMOVE)` | Relation cascade |
| `@relation(mappedBy="field")` | Relation mapping |
| `@fetchjoin` | Applies JOIN FETCH automatically (prevents N+1) |
| `@notaudited` | Excluded from auditing |
| `@Column(name="col", length=n)` | JPA column mapping |
| `@env:annotationName` | Refers to an annotation defined in env.yml |

### Relation annotations

A field of an Entity type gets **`@ManyToOne` applied automatically by default**. You specify `@OneToOne` and `@ManyToMany` explicitly only when you use them.

```yaml
fields:
  parent: ParentEntity                          # @ManyToOne applied automatically (can be omitted)
  parent: ParentEntity @ManyToOne               # same as above (explicit)
  children: List<Child> @OneToMany(mappedBy="parent")
  profile: Profile @OneToOne
  tags: List<Tag> @ManyToMany
```

#### Setting the FK column name

Using `@column` (or `@col`) on an entity reference field generates `@JoinColumn(name=...)`. If you do not set it, the JPA default rule (field name + `_id`) applies.

```yaml
fields:
  comment: Comment                              # JPA default: comment_id
  comment: Comment @column(parent_comment_id)   # @JoinColumn(name="parent_comment_id")
  comment: Comment! @col(parent_comment_id)     # @JoinColumn(name="parent_comment_id", nullable=false)
```

For a relation marked `!`, the **FK id field is required** in the automatically generated DTOs (`@expose`/`@service`). A save request sends this field.
The name is `field name + target @id` (`post: Post!`, key `postId` -> `postPostId`; if the key is `id`, `topic: Topic!` -> `topicId`).
The nested DTO for responses (`post`) is optional in the request. This applies to v3, Kotlin and TypeScript output (from 3.0.261022-rc1); 2.x and
the `jpa-springboot` compatible output still put `@NotNull` on the nested DTO as before -
`elasticore-annotation-guide.md` "Required relation (`!`): a save request sends the FK id field".

### Entity example
```yaml
entity:
  AuditEntity:
    meta: entity @abstract
    fields:
      createdBy: string(20) @updatable(false) @jpa:org.springframework.data.annotation.CreatedBy
      createDate: datetime @updatable(false) @jpa:org.springframework.data.annotation.CreatedDate
      lastModifiedBy: string(20) @jpa:org.springframework.data.annotation.LastModifiedBy
      lastModifiedDate: datetime @jpa:org.springframework.data.annotation.LastModifiedDate

  Board:
    meta: entity @expose(50) @extend(AuditEntity) @Index(name="idx_board_type", columnList="board_type")
    fields:
      bid: long @id @sequence -- Board ID
      name: string(100)! @search(like) -- Board name
      boardType: BoardType -- Board type enum
      articles: List<Article> @dtype(List<ArticleDTO>) @OneToMany(mappedBy="board")

  Article:
    meta: entity @expose(50) @extend(AuditEntity)
    fields:
      aid: string @id @genid -- Article ID
      title: string(200)! @search(like) -- Title
      content: text -- Body
      price: bigdecimal(10.2) @search(between) -- Price (precision 10, scale 2)
      board: Board @fetchjoin -- Parent board (@ManyToOne applied automatically)
```

### Business commands (`commands:`) - DDD bundle

CRUD is "change these fields"; a domain is asked "confirm this sales order". On an entity,
if you write `commands:`, each command is generated as **one path, from the endpoint to the rule**.

```yaml
SalesOrder:
  meta: entity @root @expose(20)
  fields:
    orderId: string(36) @id @genid
    status: OrderStatus @behavior
  commands:
    confirm:                     # no parameters
    ship:                        # parameters are written like fields (validation annotations too)
      warehouse: string(20)!
    cancel:
```

| Generated | Content | Kind of file |
|---|---|---|
| `POST /api/erp/salesOrder/{orderId}/ship` | Controller method (a camelCase command becomes a kebab-case path: `shipPartially` -> `/ship-partially`) | Generated in a new controller |
| `SalesOrderShipCommand` | Request DTO of a command that has parameters (`@NotNull`, `@Size` and so on) | Regenerated |
| `SalesOrderUseCase.ship(orderId, command)` | Inbound port | Regenerated |
| `SalesOrderCoreService.ship(...)` | Load -> `entity.ship(warehouse)` -> save, transaction | Regenerated |
| `SalesOrderBase`: `abstract void ship(String warehouse)` | The promise - if the behavior class already exists, a **compile error** until you write the rule | Regenerated |
| `SalesOrder.ship(String)` | The rule (generated once as a skeleton that throws an exception) | Developer-owned |

- The rule is not generated. You write the state check, the value change and the event raising directly in `SalesOrder`.
- If a command needs something outside the aggregate (a domain service or another port, such as deducting stock), override `ship(orderId, command)` in the extension service
  (`SalesOrderService`).
- Parameters are **values**. Take another aggregate by its identifier (`itemCode: string(20)`) and load it in the service
  - an entity-type parameter is a DSL warning.
- A command is generated only on a root that has a behavior class (= a single-key root that is not `@editable`). Anywhere else it is ignored after a DSL warning.
- A **root with no `@editable` field at all** does not get PUT (`update`, `batchUpdate`): its API is
  create, read, delete and commands.

### Lifecycle (`states:`) - DDD bundle

A **rule that a state changes only from
here to there** - "an order starts as PLACED, becomes SHIPPED when shipped and CANCELLED when cancelled" - is written in the DSL instead of in code. The bodies of the command methods (`ship()`, `cancel()`) are generated too, so
there is nothing to write by hand in the behavior class.

#### 1. How the three places connect

```yaml
enumeration:
  OrderStatus:                         # (1) list of states = the constant names of the enum
    meta: enum @db(code) @json(code)
    fields:
      code:  string(3) @code
      label: string(20)
    enum:
      PLACED:    PLC, Placed
      SHIPPED:   SHP, Shipped
      CANCELLED: CAN, Cancelled

entity:
  PurchaseOrder:
    meta: entity @root @expose(20) @event(Placed(total), Shipped, Cancelled)
    fields:
      orderId: string(36) @id @genid
      status:  OrderStatus             # (2) the field that holds the state. Its type is the enum of (1)
    states:
      status:                          # (3) key = the "field name" of (2)
        initial: PLACED                #     the starting state of a new order (reserved word initial)
        ship:    PLACED -> SHIPPED     #     command name: start state -> end state
        cancel:  PLACED -> CANCELLED
```

- **Which field's lifecycle**: the key directly under `states:` (`status`) is the **field name**.
  It is not the type name (`OrderStatus`). So there is no confusion even when two fields use the same enum.
- **Where state names come from**: they are the **constant names** (`PLACED`) of the enum that is the type of that field.
  They are not the DB/JSON code values (`PLC`). If you use a name the enum does not have, the build warns and the whole lifecycle is dropped
  (see the "Build warnings" table below).
- **Every key other than `initial` is a command.** If you write `ship:`, you get a `ship()` command and
  `POST /api/.../{id}/ship`. You do not need to list it in `commands:`
  (write the same name in `commands:` only when the command needs parameters).

#### 2. As a picture

```
          ship
  PLACED ------> SHIPPED      (end)
     |
     |  cancel
     +---------> CANCELLED    (end)
```

- **A state with no outgoing arrow is an end (final) state.** In the example above, `SHIPPED` and `CANCELLED` accept
  no command. For example, sending `cancel` to a shipped order returns 409 (conflict with the current state):
  `PurchaseOrder cannot cancel when status is SHIPPED; it must be [PLACED]` (Kotlin: `... it must be PLACED`)
- If you need a step after SHIPPED, add a state to the enum and one more command line:

```yaml
    enum:
      PLACED:    PLC, Placed
      SHIPPED:   SHP, Shipped
      DELIVERED: DLV, Delivered      # added
      CANCELLED: CAN, Cancelled
  ...
    states:
      status:
        initial: PLACED
        ship:    PLACED -> SHIPPED
        deliver: SHIPPED -> DELIVERED          # the way out of SHIPPED
        cancel:  PLACED, SHIPPED -> CANCELLED  # several start states are separated by commas
```

- The comment of the generated `XxxBase` class keeps this picture as text too (so you can read it without opening the DSL):

```java
/**
 * Moves status to the next state, or refuses: the command was given in a state it does not start in.
 *
 * Lifecycle of status (OrderStatus), from the DSL's states:
 *   start:  PLACED
 *   ship:   PLACED -> SHIPPED
 *   cancel: PLACED -> CANCELLED
 *   end:    SHIPPED, CANCELLED - no command leaves them
 */
```

  If the enum has a state that no command can reach, an `unused: ... - no command moves a row there`
  line is added (check whether it is a state that is entered only by, for example, a data migration).

#### 3. What is generated

| Generated | Content |
|---|---|
| `private OrderStatus status = OrderStatus.PLACED` | The start state. Without `initial`, the state of a new row is empty (null) and no command can pass |
| `PurchaseOrderBase.ship()` | Check the current state -> move -> `raiseShipped()` (when there is an `@event` with the same name as the end state). If the state is not a start state, **409** |
| `PurchaseOrderBase.canShip()` | Whether this command can be done now (`status == PLACED`) |
| `commands:` entry, endpoint, use case, service | Same as `commands:` (`POST /{id}/ship`) |
| TypeScript `PurchaseOrderApi.canShip(dto)` | To decide whether the screen shows a "Ship" button |
| `status` of the DTO | **Read-only**. Putting `"status": "SHP"` in a create or update request is ignored - the state changes only through commands |

#### 4. When the move needs more work

The generated code does the move itself; you override extra work (such as recording a shipping number) in the behavior class:

```java
public class PurchaseOrder extends PurchaseOrderBase {
    @Override
    public void ship() {
        // checks to make before the move go here
        super.ship();               // state check + move + event
        // work to do after the move
    }
}
```

#### 5. When there are several state fields (one command changes several lifecycles)

When there are **two or more enum fields**, such as an order state and a payment state, write a separate lifecycle for each field under `states:`.
Each lifecycle moves independently, and **if the same command name is in two lifecycles, that command changes both together.**

```yaml
enumeration:
  ShipmentStatus:
    enum:
      READY:     RDY, Ready
      SENT:      SNT, Sent
      CANCELLED: CAN, Cancelled
  FeeStatus:
    enum:
      DUE:      DUE, Due
      PAID:     PAD, Paid
      REFUNDED: REF, Refunded

entity:
  Shipment:
    meta: entity @root @expose(20) @event(Sent, Cancelled, Refunded)
    fields:
      shipmentId: string(36) @id @genid
      status:     ShipmentStatus
      fee:        FeeStatus
    states:
      status:                       # lifecycle 1: shipment
        initial: READY
        send:    READY -> SENT
        cancel:  READY -> CANCELLED
      fee:                          # lifecycle 2: shipping fee
        initial: DUE
        pay:     DUE -> PAID
        cancel:  PAID -> REFUNDED   # the same command cancel: changes both lifecycles together
```

| Command | Fields it changes | When it can be done (`canX()`) |
|---|---|---|
| `send()` | `status` only | `status == READY` |
| `pay()` | `fee` only | `fee == DUE` |
| `cancel()` | `status` **and** `fee` | `status == READY` **and** `fee == PAID` |

The generated `cancel()`:

```java
public void cancel() {
    requireStatus("cancel", ShipmentStatus.READY);   // 1) check all lifecycles first
    requireFee("cancel", FeeStatus.PAID);
    this.status = ShipmentStatus.CANCELLED;          // 2) move together only if all pass
    this.fee = FeeStatus.REFUNDED;
    raiseCancelled();                                // 3) publish the @event with the same name for each end state
    raiseRefunded();
}
```

- **All or nothing**: if any one is not in a start state, the command is refused (409) and **no field changes.**
  Example: cancelling a shipment whose fee is unpaid (`DUE`) gives `Shipment cannot cancel when fee is DUE; it must be [PAID]`,
  and `status` stays `READY`.
- TypeScript `ShipmentApi.canCancel(dto)` also checks both conditions.
- The lifecycle comment of the generated class marks the fields that change together, as in `cancel: READY -> CANCELLED  (with fee)`.
- If either of the two lifecycles is wrong (for example, a state the enum does not have), that command is not generated and stays an abstract method.

**What this syntax cannot do** (write it as an override in the behavior class):
- A case where **the end state differs by state within one lifecycle**, such as "refund if the fee was paid, leave it as is and cancel if not"
  (`PAID -> REFUNDED` and `DUE -> DUE` in one command). In this case, remove `cancel` from the `fee` side, override
  `cancel()` in `Shipment`, and handle `fee` yourself after `super.cancel()`.
- Changing the state of another aggregate together (for example, restoring stock when an order is cancelled) - you write only the rules inside one aggregate.
  Handle such flows by receiving the domain event (`@event`).

#### 6. Deletable states (`delete:`)

If you write `delete:` in a lifecycle, a row can be deleted **only in that state**. `delete` is not a command but a list of states
(no endpoint such as `POST /{id}/delete` is created).

```yaml
    states:
      status:
        initial: PLACED
        ship:    PLACED -> SHIPPED
        cancel:  PLACED -> CANCELLED
        delete:  CANCELLED          # only a cancelled order can be deleted (for several: CANCELLED, LOST or [CANCELLED, LOST])
```

| Generated | Content |
|---|---|
| `PurchaseOrderBase.canDelete()` | Whether it can be deleted now (`status == CANCELLED`) |
| `PurchaseOrderBase.checkDeletable()` | In any other state, **409** (`state.PurchaseOrder.status.delete`), and the row stays. It is called by JPA `@PreRemove`, so it applies to **every delete path**: delete by id, conditional delete, cascade and so on |
| TypeScript `canDelete(dto)` | For showing the delete button, same condition |

In the Pet Store (`ShopFlowTest.onlyACancelledOrderIsDeleted`):

```http
DELETE /api/petstore/purchaseOrder/{shipped order}
HTTP/1.1 409
{ "code": "state.PurchaseOrder.status.delete",
  "detail": "PurchaseOrder cannot be deleted when status is SHIPPED; it must be [CANCELLED]" }
```

- With several state fields, the row is deleted only if **all** of them are in an allowed state (`canDelete()` is an AND). Example: a ticket only when `status: CLOSED` and
  `billing: UNBILLED, VOID` (Kotlin sample `ticket.yml`).
- Without `delete:`, deletion is unrelated to the lifecycle (as before). `delete: []` means it cannot be deleted in any state.
  Who may delete is decided by `@access(delete=admin)`.
- `delete` is now a reserved word of the lifecycle (a model that used `delete: A -> B` as a command before must rename it - a warning is shown).
- It is generated only on a root that has a behavior class (a split root). If it is not split, like an `@editable` root, there is a DSL warning and no check.
- Because it is a JPA callback, it is not applied to JPQL bulk delete (`DELETE FROM ...`), native SQL or the DB's `ON DELETE CASCADE`.
- If the root has invariants (`@invariant`, `@min` and so on), JPA allows only one `@PreRemove` per class, so the check is
  called first inside the invariant side's `markBeingRemoved()`. The behavior is the same.

#### 7. Build warnings (when a lifecycle is dropped)

| Warning | Cause | Result |
|---|---|---|
| `states.status names SHIPED, which OrderStatus does not have (it has PLACED, SHIPPED, CANCELLED)` | A state name the enum does not have (a typo, using the code value `SHP` and so on) | Lifecycle omitted. The command stays an abstract method |
| `states names status, which PurchaseOrder has no field for` | A key under `states:` is not a field name (such as when you wrote the type name) | Lifecycle omitted |
| `states names status, which is not an enum of the model` | The field type is not an enum (`string` and so on) | Lifecycle omitted |
| `states.status.ship: '...' is not FROM -> TO` | There is no `->` | Only that move is omitted |
| `states.status.delete: '...' is not a list of states` | `delete:` has `->` or is empty | Delete check omitted |
| `has @default and states.status.initial` | The state field also has `@default` | `initial` wins |
| `still has the stub that throws` | A behavior class made earlier still has the skeleton that throws an exception, and it overrides the move | Delete the skeleton |

Warnings are collected under `[elcore3] DSL validation warnings:` at the end of the `elcore3` output. The build does not stop, so
be sure to check this list after generation. If a lifecycle is dropped, the command goes back to an abstract method, and if the behavior class has no implementation, it leads to a
compile error such as `PurchaseOrder is not abstract and does not override ship()`.

---

### Business metrics (`metrics:`) and business terms (`@terms`)

"What is revenue?" and "Do cancelled orders count in the order count?" are core domain information, but if code, SQL and BI tools each define them separately, you get different
numbers. You write them in the DSL **once**, and when you export them as the `metrics` of the [Apache Ossie](https://github.com/apache/ossie) semantic model (`ossie-export`),
BI and AI tools use the same definition. No application code is generated (there is no query API).

```yaml
# model/metrics.yml (anywhere in the model files, top-level metrics:)
metrics:
  revenue:         sum(PurchaseOrder.total) where status != CANCELLED @terms(sales, 매출액) -- Revenue (excluding cancelled)
  orderCount:      count(PurchaseOrder) where status != CANCELLED -- Order count
  averageOrder:    revenue / orderCount @terms(AOV, 객단가) -- Average amount per order
  shippedOrders:   count(PurchaseOrder) where status = SHIPPED -- Shipped order count
  customerCount:   count(Customer) where role = USER -- Customer count (administrators excluded)
```

| How to write | Meaning | Ossie SQL |
|---|---|---|
| `sum(Entity.field)` / `avg` / `min` / `max` | Aggregate a field of one entity | `SUM(PurchaseOrder.total)` |
| `count(Entity)` / `count(Entity.field)` | Number of rows / number of rows with a value | `COUNT(PurchaseOrder.order_id)` (the key column - so that BI tools know which dataset is counted) / `COUNT(PurchaseOrder.total)` |
| `… where field != value and field >= value` | Condition (`=`, `!=`, `<`, `<=`, `>`, `>=`, `and`) | `SUM(CASE WHEN … THEN … END)` |
| Value | A number, `'text'` (a `'` inside is `''`), `true`/`false`, **an enum constant name** (converted to the stored value: the `@db` code `'CAN'`, the name if `@jpa:Enumerated(STRING)`, the ordinal `2` if neither) | |
| `metricA / metricB` (`+ - * /`) | Calculate between metrics declared earlier. Division never divides by 0, and count / count gives a decimal | `(…) / NULLIF((…), 0)` |
| `@terms(sales, 매출액)` | Business terms and synonyms | `ai_context.synonyms` |
| `-- Revenue` | Description | `description` |

- Data types: `count` is `Integer`, `sum`, `min` and `max` take the type of that field, `avg` and division are `Decimal` (`Float` for a `Float` field),
  `+ - *` is `Integer` if both are `Integer`, otherwise `Decimal`. `sum` and `avg` work only on numeric fields.
- If a line contains ` #` or `: `, YAML cuts the line or cannot read the file, so wrap the whole line in quotes:
  `ratio: "shippedOrders / orderCount -- Shipped ratio: shipped / total"`.
- A line that cannot be read (an entity or field that does not exist, a constant the enum does not have, a metric declared later, an unquoted value on a field that is not an enum) is dropped at generation
  with the warning `[ossie] [domain] metric name: … ; it is left out.` - it does not guess and export.
- It counts **within one entity only**. A metric that needs a condition on another entity, such as "the quantity sold in orders that are not cancelled" (order line -> order status), cannot be
  written yet - if you force it, cancelled lines would be counted too, so do not add it.
- A period (monthly revenue) is not put in a metric. The time axis is split by the BI tool on a date field (`orderDate`, the time dimension of Ossie).

`@terms` goes on an entity meta, a field or a metric, and lists the names and synonyms used in the business. It has no effect on code generation and goes out as Ossie's
`ai_context.synonyms` - so that when asked "last month's purchase amount", the AI finds `PurchaseOrder` and `revenue`:

```yaml
PurchaseOrder:
  meta: entity @root @expose(20) @terms(order, purchase, 구매) -- Order
Item:
  meta: entity @root @editable @terms(pet, 반려동물) -- Sales item
  fields:
    unitCost: money(10.2)! @terms(price, 가격)
```

Verification: the Pet Store `MetricsTest` places, ships and cancels orders, and then checks that the values from running the exported metric SQL on a real DB equal the values calculated directly from the entities
(revenue, order count, average order, shipped, cancelled, customer count).

### Access, ownership, credentials and derived values - annotation summary

| Where | Annotation | Meaning |
|---|---|---|
| Entity meta | `@access(read=owner, create=user, ship=admin)` | Allowed level per operation (public/user/owner/admin/none, admin if not written) |
| Entity meta | `@owner(customerId)` | The field that holds the owner of the row |
| Entity meta | `@signin` | Sign-in API: `me()`, the controller's `signin`, `signout` and `me`, TypeScript `signIn`, `signOut` and `me`. Needs `@login`, `@password` and `@expose`, one key, update by the owner or an administrator only (`update=owner` + `@owner` on the key, or `update=admin`), delete also by the owner or an administrator only if the request decides the key, only when there is one in the application - otherwise a DSL warning. The login column is unique with `@signin` alone (even if rejected) |
| Field | `@access(admin)` | A field only administrators use |
| Field | `@password` | Stored only as a hash, absent from responses and search |
| Field | `@login` | With `@password`, generates `authenticate(login, password)`. Must be unique (automatic with `@signin`, otherwise `@unique` - a DSL warning if missing) |
| Field | `@role` | The role that the sign-in of `@signin` puts in the session (the `role` field if absent). Only an enum or string with `@access(admin)` - otherwise it signs in without a role and gives a DSL warning |
| Field | `@snapshot(item.unitCost)` | Copies the value of the referenced row once |
| Field | `@compute(unitCost * quantity)`, `@compute(sum(lines.subTotal))` | Calculated on every save |

Details: "@access / @owner", "@password / @login" and "@snapshot / @compute" in `elasticore-annotation-guide.md`.

## 3. Field Types

### Value types at a glance (DSL -> Java -> JSON -> TypeScript -> Apache Ossie)

These are all the value types you can use as a field type. For each type, the generated Java field type, the shape in request and response JSON,
the TypeScript type, and the **DataType** when exporting to the [Apache Ossie](https://github.com/apache/ossie/blob/main/core-spec/spec.md) semantic model are fixed
(`ossie-export`, see the rule guide).
Ossie has only 10 DataTypes: `String`, `Integer`, `Decimal`, `Float`, `Boolean`, `Date`, `Time`,
`DateTime` (no time zone), `DateTimeTz` (one instant - with time zone/offset) and `Opaque` (outside the vocabulary).

| DSL type | Java type | JSON | TS | Ossie DataType | Note |
|---|---|---|---|---|---|
| `string` / `string(n)` | `String` | `"text"` | `string` | `String` | n = maximum length, `!` = NOT NULL |
| `text` / `longtext` | `String` | `"text"` | `string` | `String` | `columnDefinition = "TEXT"` / `"LONGTEXT"` |
| `clob` | `String` | `"text"` | `string` | `String` | `@Lob` (large string) |
| `json` | `String` | `"{...}"` (string) | `string` | `Opaque` | `columnDefinition = "json"` + `@JdbcTypeCode(SqlTypes.JSON)` |
| `int` / `Integer` | `Integer` | `42` | `number` | `Integer` | |
| `long` / `Long` | `Long` | `42` | `number` | `Integer` | |
| `biginteger` | `BigInteger` | `123456789012345678901` | `number` | `Integer` | |
| `quantity` | `Quantity` | `5` | `number` | `Integer` | 3.0 value type: a count that is never negative (below) |
| `decimal` / `decimal(p.s)` | `BigDecimal` | `12.34` | `number` | `Decimal` | Same as `bigdecimal`. `(p.s)` = precision, scale |
| `bigdecimal` / `bigdecimal(p.s)` | `BigDecimal` | `12.34` | `number` | `Decimal` | |
| `money` / `money(p.s)` | `Money` | `99.95` | `number` | `Decimal` | 3.0 value type: an amount (below), default (19,2) |
| `float` / `double` | `Float` / `Double` | `1.5` | `number` | `Float` | Approximate value - do not use for amounts |
| `boolean` | `Boolean` | `true` | `boolean` | `Boolean` | |
| `date` / `LocalDate` | `LocalDate` | `"2026-10-03"` | `string` | `Date` | `@JsonFormat("yyyy-MM-dd")` on the DTO |
| `time` / `LocalTime` | `LocalTime` | `"09:30:00"` | `string` | `Time` | |
| `datetime` / `LocalDateTime` | `LocalDateTime` | `"2026-10-03 09:30:00"` | `string` | `DateTime` | No time zone. `@JsonFormat("yyyy-MM-dd HH:mm:ss")` on the DTO |
| `timestamp` | `java.time.Instant` | `"2026-10-03T00:30:00Z"` | `string` | `DateTimeTz` | One instant. Even if sent with `+09:00`, it is stored as the same instant and returned in UTC |
| `blob` | `byte[]` | `"AQID/w=="` (base64) | `string` | `Opaque` | `@Lob` |
| `EnumName` | That enum | Code (`"PLC"`) | enum | `String` | The value of `@db(code)` is stored. In Ossie, the code and the label go in `ai_context` |
| `EntityName` | Entity reference | (identifier field) | - | Relationship | `@ManyToOne` automatically. Goes to Ossie `relationships` |

- **Which time type to use**: for dates and times that people look at (business day, reservation time), use `date` and `datetime`; for "when did it happen"
  (payment approval time, event occurrence, audit), use `timestamp`. Even if the time zones of the server and the DB differ, a `timestamp` is the same instant.
- The JSON of `datetime` is `yyyy-MM-dd HH:mm:ss`, not the ISO format (`T`) (the ElastiCORE DTO convention).
- **Do not use types that are not in the table.** A type in the table already says the same thing: `short` and `byte` are `int`, `char` is `string(1)`,
  `OffsetDateTime` and `ZonedDateTime` are `timestamp`, and a UUID key is `string @id @genid` (without a length it is `UUID.randomUUID()`).
- Every type in the table above is verified by generation, storage and an HTTP round trip in `catalog/model/reading.yml` of `elasticore-template-ddd`
  (`TypeRoundTripTest`). In earlier versions, `timestamp`, `clob`, `json`, `blob` and `decimal` (without a size) were
  written into the entity as the DSL word itself (`private timestamp at;`) and did not compile - fixed in 3.0.260925.
- The table is based on the 3.0 rule engine (`elcore3`). The output of the 2.x generator (`elcore`) has not changed
  (`timestamp` is `Instant` in 2.x too; `@Lob` is 3.0 only).

### Value types (Value) - 3.0

You use **value objects** for amounts and quantities instead of primitive types. The column and the JSON shape stay the same (decimal and integer, a number),
and the code in between handles `Money` and `Quantity`. They are in the runtime `io.elasticore.springboot3.domain`.

| DSL type | Java type | Column | Note |
|---------|----------|------|------|
| `money` / `money(p.s)` | `Money` | `decimal(p,s)` (default 19,2) | `plus`, `minus`, `times(Quantity/long/BigDecimal)`, `rounded`, `isNegative` … 10 and 10.00 are the same amount |
| `quantity` | `Quantity` | `integer` | Never negative. If `minus` runs short, 422 (`not enough`). `plus`, `covers` |

```yaml
SalesOrderLine:
  fields:
    qty: quantity! @min(1)
    unitPrice: money(12.2)! @min(0.01, 'unit price must be greater than 0')
    amount: money(14.2)
```

```java
// in the behavior class
line.setAmount(line.getUnitPrice().times(line.getQty()));
total = total.plus(line.getAmount());
stock.setOnHand(stock.getOnHand().minus(qty));   // 422 if it runs short
```

- The entity gets `@Convert(converter = Money.JpaConverter.class)` and `@Column(precision, scale)`.
- `@min`/`@max` are put on the DTO as the same `@DecimalMin`/`@Min` as for BigDecimal, and the runtime registers the validator for value types
  as a service loader (no extra setting). On a DDD root, the check at save time is the same.
- `@default(0)` becomes `Money.of("0")` / `Quantity.of(0)`.
- A signed change (+ for receiving, - for shipping out) is not a quantity, so keep it as `int`.
- It is only for the 3.0 rule engine (elcore3). The 2.x generator (elcore) does not attach the converters.

### Collection/array types

| DSL type | Java type | Note |
|---------|----------|------|
| `List<T>` | `List<T>` | Collection |
| `Set<T>` | `Set<T>` | Collection |
| `Map<K,V>` | `Map<K,V>` | Map |
| `HashMap` | `java.util.HashMap` | Use HashMap directly |
| `int[]` / `string[]` | Array | Array type |

### Reference and other types

| DSL type | Java type | Note |
|---------|----------|------|
| `Object` | `Object` | General object type (Ossie: `Opaque`) |
| `EntityName` | Entity reference | `@ManyToOne` is applied automatically (can be omitted) |
| `EnumName` | Enum reference | Enum reference |

---

## 4. DTO definition

### Basic structure
```yaml
dto:
  EntityNameDTO:
    meta: dto @template(EntityName)
    fields:
      additionalField: string -- extra field
      excludedField: -- -- removes from template

  EntityNameSrchDTO:
    meta: dto @searchable(entity=EntityName, pageSize=20)
    fields:
      name: string @search(like)
      status: List<Status> @search(in)

  CustomDTO:
    meta: dto
    fields:
      field1: string
      field2: int
```

### Meta annotations
| Annotation | Description |
|-----------|------|
| `@template(Entity)` | Inherits the entity's fields. Several are allowed: `@template(A,B)` |
| `@searchable(entity=E, pageSize=n)` | Generates a search DTO (converted to a Specification automatically) |
| `@jsonview` / `@jsonview(auto)` | Turns on JsonView-based field visibility control |

### Field annotations
| Annotation | Description |
|-----------|------|
| `@ref(field.path)` | References a field of a nested entity |
| `@view(cru)` | Sets JsonView field visibility (c=create, u=update, r=read) |
| `--` (value) | Excludes the field from the template |

### DTO example
```yaml
dto:
  ArticleDTO:
    meta: dto @template(Article)
    fields:
      boardName: string @ref(board.name) -- Board name via ref
      boardType: BoardType @ref(board.boardType) -- Board type via ref
      createDate: -- -- Excluded from template

  ArticleSrchDTO:
    meta: dto @searchable(entity=Article, pageSize=50)
    fields:
      title: string @search(like)
      boardType: List<BoardType> @search(in) @ref(board.boardType)
      createDateFrom: date @search(between)
      createDateTo: date @search(between)
```

---

## 5. Enumeration definition

### Basic structure
```yaml
enumeration:
  EnumName:
    meta: enum @db(field) @json(field)
    fields:
      field1: type [@annotations]
      field2: type
    enum:
      VALUE1: val1,val2
      VALUE2: val1,val2
```

### Meta annotations
| Annotation | Description |
|-----------|------|
| `@db(field)` | The field used when saving to the DB |
| `@json(field)` | The field used for JSON serialization |

### Field annotations
| Annotation | Description |
|-----------|------|
| `@code` | A field used for both DB storage and JSON serialization (sets db and json at once) |
| `@length(n)` | String length |

### Enumeration example
```yaml
enumeration:
  BoardType:
    meta: enum @db(code) @json(code)
    fields:
      code: string(2) @length(3)
      name: string(50)
    enum:
      PUBLIC: PB,Public
      PRIVATE: PV,Private
      NOTICE: NT,Notice

  UserStatus:
    meta: enum
    fields:
      status: string(1) @code
      description: string(20)
    enum:
      ACTIVE: A,Active
      INACTIVE: I,Inactive
      SUSPENDED: S,Suspended
```

---

## 6. Repository definition

Adds query methods to an entity's Spring Data repository. **The key is the entity name**, and
you write the methods as a `methods` **list**.

### Basic structure
```yaml
repository:
  EntityName:                 # entity name (not EntityNameRepository)
    methods:
      - id: methodName        # method name
        params:               # parameters (name: DSL type)
          paramName: string
        return: ReturnType    # List<Entity>, Optional<Entity>, Entity, Page<Entity>, long ...
        query: "JPQL"         # if omitted, a Spring Data derived query (read from the method name)
        nativeQuery: false    # true for native SQL
        pageable: false       # true adds Pageable as the last parameter
```

> **Warning**: An entry without a `methods` list (for example, the form that writes methods as keys under `ProductRepository:`)
> is not read. The generator prints a warning and skips that entry.

### Repository example
```yaml
repository:
  Stock:
    methods:
      # derived query - by name only, no query
      - id: findByItemItemCodeAndWarehouse
        params:
          itemCode: string
          warehouse: string
        return: Optional<Stock>

      # JPQL
      - id: findLowStock
        params:
          threshold: int
        return: List<Stock>
        query: "SELECT s FROM Stock s WHERE s.onHand < :threshold ORDER BY s.onHand"

      # paging
      - id: findByWarehouse
        params:
          warehouse: string
        return: Page<Stock>
        pageable: true
```

### In DDD / hexagonal bundles (exposed as ports)

In a bundle that has `ddd-repository-port`, the methods you declare also appear on the port,
and the adapter delegates them to the Spring Data methods.

| Declaration | Goes to | Why |
|---|---|---|
| A lookup that returns `Root` / `Optional<Root>` / `List<Root>` and is not paged | `RootRepositoryPort` (domain) | The question a domain service asks to find the aggregate |
| Anything else (`Page`, DTO projection, count, update query) | `RootQueryPort` (application) | For screens and lookups, or a bulk write that does not go through the aggregate |

```java
// StockRepositoryPort (generated)
Optional<Stock> findByItemItemCodeAndWarehouse(String itemCode, String warehouse);

// ShippingDomainService (developer)
Stock stock = stockRepositoryPort.findByItemItemCodeAndWarehouse(code, "MAIN").orElseThrow(...);
```

---

## 7. Port definition overview

A transaction port is an interface definition for integrating with an external system.

### Basic structure
```yaml
port:
  ServiceName:
    methodName:
      "@": "@native @pageable"
      param1: type
      "->": ReturnType
      "--": |
        SQL or URL definition
```

### Port types
| Type | Description |
|------|------|
| `dbms` | Direct database integration (native SQL) |
| `http` | HTTP REST API integration |
| `grpc` | gRPC service integration |

For the detailed syntax and examples, see `elasticore-dsl-port-guide.md`.

---

## 8. Domain service definition

An operation that belongs to no aggregate. The point of this section is that you **declare it on the domain, not on an entity** -
every other annotation goes on an entity, but a domain service is defined as "belonging to no one".
The moment you pick one root and hang it there,
it contradicts that definition.

```yaml
# blueprint/{domain}/model/domainservice.yml
domainService:

  Pricing:
    desc: What an order costs once the products it names are priced
    uses: [Order, Product]
```

| Key | Required | Description |
|---|------|------|
| `uses` | Yes | The aggregates this operation spans. They are injected as domain ports (`XxxRepositoryPort`) |
| `desc` | No | Comment on the generated class |

### Generated code - two files

Two files are created in the `namespace.domainService` package. It is the same split as an entity's `XxxBase` + `Xxx`.

| File | Kind | Content |
|---|---|---|
| `PricingDomainServiceBase` | **Regenerated every time** | The ports from `uses` (injected fields) |
| `PricingDomainService` | Generated once, then owned by the developer | The place to write the operation |

```java
public abstract class PricingDomainServiceBase {       // regenerated - do not edit

	@Autowired
	protected OrderRepositoryPort orderRepositoryPort;

	@Autowired
	protected ProductRepositoryPort productRepositoryPort;
}

@Service
public class PricingDomainService extends PricingDomainServiceBase {   // developer
	// The operation goes here. ...
}
```

**When you change the DSL, the source follows.** If you add an aggregate to `uses`, the regenerated `PricingDomainServiceBase` gets a port
field, and you use it in the developer class right away without editing that class. This is why the ports are injected as fields, not through the constructor -
with a constructor, you would have to change the developer class's constructor too.

The generator does not generate the operation itself, for the same reason as `@factory`: the model declares data, so what it can generate is
not the behavior but the place to write the behavior and its wiring. If you have not yet edited the developer file, it is regenerated to follow the DSL too.

### Warning

- If `uses` is empty or has only one entry, a `DSL_WARN` is raised. An operation on a single aggregate
  is usually a method of that aggregate.
- Do not put `namespace.domainService` in the same package as `namespace.service` (the application service).
  A domain service belongs to the domain and an application service is a layer above it, so putting them in the same
  place breaks the dependency direction.
- It works only in bundles that have the `ddd-domain-service` rule.

---

### Process manager (`processManager:`)

You declare it in the same file as the domain service. It is the place that reacts to events and moves on to the next step.

```yaml
processManager:
  Billing:
    desc: Creates one invoice for each shipped sales order
    on: [SalesOrderShipped]
    uses: [Invoice]
```

The generated code is two files, like a domain service.

| File | Kind | Content |
|---|---|---|
| `BillingProcessManagerBase` | **Regenerated every time** | The ports from `uses`, a listener for each event in `on` (after commit, in a separate transaction) and an abstract method `handle(...)` |
| `BillingProcessManager` | Generated once, then owned by the developer | A `handle(...)` for each event - the place to write the next step |

**When you change the DSL, the source follows.**
- If you add an event to `on`, the regenerated Base gets a listener and an abstract `handle(new event)`. Until you write that step in the developer class, the compiler tells you exactly: `LoyaltyProcessManager is not abstract and does not override abstract
  method handle(OrderCancelledEvent)` (the message actually seen in `elasticore-template-ddd`). It works the same way as business commands (`commands:`),
  so a step is never
  silently missing.
- If you add an aggregate to `uses`, the Base gets a port field, and you do not edit the developer class.
- If you remove an event from `on`, the developer class's `@Override handle(that event)` has nothing to override and fails to compile -
  the compiler points at the code to delete.
- If you have not yet edited the developer file, it is regenerated to follow the DSL too.
- A file that was created before this split and already edited is left as it is, and only the Base is newly created. The generator warns
  (`... does not extend XxxBase, which is regenerated from the DSL ...`): add `extends XxxBase`, change the listener to
  `protected void handle(...)` (remove the annotation) and delete the constructor. If you delete the file, it is generated anew.
- `handle` runs inside the transaction (`REQUIRES_NEW`) that the Base's listener opens. It is a call within the same object, so a `@Transactional` put on `handle`
  does not apply - and is not needed.
- The ports are injected fields, so to unit test without Spring you fill the fields directly (in Java, assign to the `protected`
  field from the same package; in Kotlin, assign to the `lateinit var` - using it without filling it gives `UninitializedPropertyAccessException`).

For details, see the process manager section of `elasticore-rule-guide.md`.

**An event from another context** is written as `context:RootEvent`, just as you write another domain model in a field type as `catalog:Product`.

```yaml
# blueprint/billing/model/domainservice.yml
processManager:
  Receipting:
    desc: Creates one receipt when an order comes in
    on: [shop:OrderPlaced]       # the Placed event of the Order root in the shop context (folder name or domainName)
    uses: [Receipt]
```

- The generated code is the same two files, and only the event class is `shop`'s (imported from the `event` namespace of `shop`).
  The listener `on(OrderPlacedEvent event)` in `ReceiptingProcessManagerBase` receives it and passes it to the developer class's
  `handle(OrderPlacedEvent event)`, where you read the values carried by the event (`@event(Placed(totalAmount))`) as they are.
- If either side declares `context:`, the boundary check applies: if `billing` declared it, `shop` must be in its `uses`; if `shop`
  declared `publishes`, `OrderPlaced` must be in it (`elasticore-env-guide.md` 6.1.4). If not, the build fails before generation.
- You may also write the class name, such as `OrderPlacedEvent` (the same holds for `publishes`).
- A name that cannot be received gives a DSL warning, and only that handler is dropped:
  - You wrote only `OrderPlaced` without a context, but it is an event of another context - `... OrderPlaced is an event of shop - write shop:OrderPlaced`
  - An event that does not exist (`shop declares no event OrderShiped`), a context that does not exist (`there is no context nowhere`)
  - The other context's rules do not create the event class (for example, a context that uses only `jpa-springboot-v3`, or a context where `publisher.suppress`
    turned off `ddd-event` - it must be a ddd or hexagonal bundle that contains `ddd-event`)
  - An `@event` on a member entity (one that belongs to another root) or on an entity without a key creates no event, so it cannot be received.
- If you write the same event twice, only one handler is created, with a DSL warning. A context with no entities generates nothing
  (DSL warning), so the receiving side needs at least one entity of its own.
- Delivery uses Spring application events, so it happens **within the same application** (modular monolith). If you deploy the contexts separately,
  the sending side sends to the broker through the Outbox (`outbox: true`), and the receiving side must subscribe to that message itself - this
  part is not generated.
- Example: `billing` in `elasticore-template-ddd` (`CrossContextEventTest` checks that billing receives the order when shop commits it,
  and does not receive it when shop rolls back).

## 9. Advanced features

### Composite key
When you define several `@id` fields, a KeyDTO class is generated automatically.
```yaml
entity:
  OrderItem:
    meta: entity
    fields:
      orderId: string @id
      itemSeq: int @id
      quantity: int
```

### Embedded entity
```yaml
entity:
  Address:
    meta: entity @embeddable
    fields:
      postNo: string(5) @Column(name="post_no", length=5)
      baseAddr: string(200) @Column(name="base_addr", length=200)

  User:
    meta: entity
    fields:
      userId: long @id @sequence
      address: Address -- Embedded address
```

### Inheritance
```yaml
entity:
  Vehicle:
    meta: entity @abstract
    fields:
      id: string @id
      brand: string(50)

  Car:
    meta: entity @extend(Vehicle)
    fields:
      doors: int
      fuelType: FuelType
```

### Multiple template inheritance
```yaml
dto:
  MergedDTO:
    meta: dto @template(EntityA, EntityB)
    fields:
      extraField: string
```

---

## 10. Known limitations and workarounds

### 10.1 Invalid DSL types

Use only the field types in the table in "3. Field types". A word that is not in the table is written into the entity as it is (`private uuid id;`) and does not compile.

| Invalid type | Use instead |
|-----------|------|
| `uuid` | `string(36)` - for a key, `string @id @genid` (generates a UUID) |
| `short`, `byte` | `int` |
| `char` | `string(1)` |
| `OffsetDateTime`, `ZonedDateTime` | `timestamp` |

From 3.0.260925, `json`, `clob`, `blob`, `timestamp` and `decimal` without a size are generated as the table says (before that they did not compile).

```yaml
entity:
  Product:
    fields:
      productId: string @id @genid       # UUID key (UUID.randomUUID())
      metadata:  json                    # String + JSON column
      document:  clob                    # String + @Lob
      createdAt: timestamp               # Instant (a single moment)
```

### 10.2 @min/@max and decimal values

The Jakarta Bean Validation annotations `@Min` and `@Max` accept only the `long` type. Decimal values (0.0, 5.0 and so on) cause a compile error.

#### Example
```yaml
# WRONG - decimal values cause compile errors
fields:
  rating: double @min(0.0) @max(5.0)   # Generates @Min(0.0) - compile error
  temperature: float @min(-273.15)     # Generates @Min(-273.15) - compile error

# CORRECT - use integer values
fields:
  rating: double @min(0) @max(5)        # Generates @Min(0) @Max(5) - valid
  temperature: float @min(-273)         # Generates @Min(-273) - valid
```

#### Workaround
- Use integer values only (0.0 -> 0, 5.0 -> 5)
- If you need to validate a decimal range, a custom Bean Validation annotation is recommended

### 10.3 SQL reserved words in a @ManyToOne @id field

When a `@ManyToOne` field is used as an `@id` (composite key), the field name becomes the SQL column name as it is. If you use an SQL reserved word (order, group, select and so on), table creation fails.

#### Example
```yaml
# WRONG - 'order' is a SQL reserved keyword
entity:
  OrderItem:
    meta: entity
    fields:
      order: Order @id @ManyToOne       # SQL: order_id - reserved keyword error
      itemSeq: int @id

# SOLUTION 1 - Use non-reserved field name
entity:
  OrderItem:
    meta: entity
    fields:
      orderRef: Order @id @ManyToOne    # SQL: order_ref_id - safe
      itemSeq: int @id

# SOLUTION 2 - Manually add @Column annotation after code generation
# Generated Java code after build:
# @Column(name = "\"order_id\"")  // Add quotes manually for reserved keywords
```

#### Common SQL reserved words
`order`, `group`, `select`, `from`, `where`, `table`, `index`, `key`, `value`, `user`, `date`, `time`, `timestamp`

#### Workaround
1. **Recommended**: Use a field name that is not an SQL reserved word (order -> orderRef, user -> userRef)
2. Add `@Column(name = "\"field_id\"")` by hand in the generated code (wrap the name in quotes)

### 10.4 @audited and relationships (Relationships with Audited Entities)

When an entity is `@audited` (Hibernate Envers), every relationship field to a non-audited entity needs the `@org.hibernate.envers.NotAudited` annotation.

#### Example
```yaml
# Entity with @audited
entity:
  Order:
    meta: entity @audited
    fields:
      orderId: long @id @sequence
      customer: Customer @ManyToOne      # Customer is NOT @audited
      orderItems: List<OrderItem> @OneToMany(mappedBy="order")  # OrderItem is NOT @audited

# Code generator automatically adds @NotAudited for relationships to non-audited entities
# Generated Java code:
# @NotAudited
# @ManyToOne
# private Customer customer;
#
# @NotAudited
# @OneToMany(mappedBy = "order")
# private List<OrderItem> orderItems;
```

#### Notes
- In most cases the code generator adds `@NotAudited` automatically
- Check after generation: confirm `@NotAudited` on relationships to non-audited entities
- If it is missing, add it by hand or state `@notaudited` in the YAML

```yaml
# Explicitly mark in YAML if needed
fields:
  customer: Customer @ManyToOne @notaudited
```

### 10.5 Summary of recommendations

1. **Choose types**: Use only the types in the "3. Field types" table. Use `string(36)` instead of `uuid`, and `string @id @genid` for a UUID key
2. **Validation range**: Use only integer values in `@min/@max`. For decimal validation, consider a custom annotation
3. **Field names**: Avoid SQL reserved words. This matters most for `@id` composite key fields
4. **Audit setting**: After generation, check the relationship fields of `@audited` entities for `@NotAudited`
5. **Code review**: Compile the generated Java code and check for errors
