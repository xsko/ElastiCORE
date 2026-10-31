# ElastiCORE Annotation Guide

> **Version notation**: An annotation marked `Since: x.x` is available only from that version on. An annotation without a mark is available from ElastiCORE 1.0.

## 1. Entity Meta Annotations

Annotations used in the entity declaration (`meta:`). They can be combined.

### @expose
Generates DTO, Repository, Service, Controller and SearchDTO automatically. By default, string fields use auto search (like if the value contains `%`, otherwise exact match) and datetime fields use between search.
```yaml
Product:
  meta: entity @expose(30)  # pageSize=30, generates full CRUD including the Controller
```

### @service
Same as `@expose`, but no Controller is generated. For internal services.
```yaml
InternalData:
  meta: entity @service(20)
```

### @readonly (entity)
> **Since: 3.0** (3.0 rule engine. The 2.x generator does not read it)

An entity that is only read. The Controller gets only the read endpoints (`read`, `search`, `schemaInfo`); create, update, batch update and delete are left out.
The v3 Java Service, the TypeScript client and the tools of the AI extension service also keep only reads.
Not the same as the field `@readonly` (a field the server decides, §2.2).
```yaml
CommonCode:
  meta: entity @expose @readonly
```

### @restExclude(method, ...)
> **Since: 3.0** (3.0 rule engine. The 2.x generator does not read it)

Excludes only the methods you name. The names are Service methods (`save`, `update`, `batchUpdate`, `delete`, `deleteById`, `updateBySpecification`) and
Controller endpoints (`create`, `read`, `search`, `update`, `batchUpdate`, `deleteBatch`, `deleteById`, `schemaInfo`). In v3 output, the
methods that call an excluded method are left out too (leave out `update` and `batchUpdate` goes with it). For the pairs that drop out and the difference from the 2.x-compatible bundle, see
the `@restExclude` description in `elasticore-rule-guide.md`.
```yaml
Notice:
  meta: entity @expose @restExclude(batchUpdate, deleteById)
```

> The entity `@readonly` and `@restExclude` are read by the 3.0 rule engine (`elcore3`). The 2.x generator (`elcore` with `output = 'v2'`) does not
> read them and generates every method.

### @audited
Adds the JPA Envers `@Audited` annotation. For tracking data change history (a dependency setting is required).
```yaml
Order:
  meta: entity @audited
```

### @pageable
Supports paging. Default pageSize=20.
```yaml
Article:
  meta: entity @pageable(50)
```

### @abstract
Abstract entity. No table is created; for inheritance only.
```yaml
AuditEntity:
  meta: entity @abstract
```

### @extend(ParentEntity)
Inherits a parent entity. Generates `extends ParentEntity`.
```yaml
User:
  meta: entity @extend(AuditEntity)
```

### @embeddable
JPA `@Embeddable` value object. Used for addresses, coordinates and so on.
```yaml
Address:
  meta: entity @embeddable
```

In v3 (DDD, hexagonal), the DTO holds the value object **as a class**, so the rules that apply to a DTO field
apply to the fields of the value object (`jpa-value-object-boundary`):

```yaml
CreditCard:
  meta: entity @embeddable
  fields:
    creditCardNumber: string(30)! @writeonly   # not sent in the response; 400 if missing from the request
    creditCardExpDate: string(5)!
```

```java
@NotNull
@Size(max=30)
@JsonProperty(access=JsonProperty.Access.WRITE_ONLY)
@Column(name = "credit_card_number", nullable = false, length = 30)
private String creditCardNumber;

// PurchaseOrderDTO
@Valid
private CreditCard creditCard;
```

The columns of a value object are created in the table of the entity that holds it. So when an entity holds a value object **optionally**
(`shipTo: Address @Embedded`, no `!`), the `!` fields of that value object do not get `nullable = false` on the column
(from 3.0.261022-rc1, v3). If a value arrives, the DTO checks the required fields (400); a row without a value is saved as it is.
Before, an order sent without a shipping address was not saved, failing with `NULL not allowed for column "ZIPCODE"` (400 `required`).
- The same applies when the holding entity is in a **different domain** (an order in `shop` holds `Address` from `common` optionally).
- A value object **inside** an optionally held value object (`place: Place! @Embedded` in `contact: Contact @Embedded`) is optional too -
  if the outer value is missing, the inner columns are empty as well.
- The marks for holding it as required are `!`, `@required` and `@nn`. A value object that is always held as required (`card: Card! @Embedded`,
  `card: Card @Embedded @required`) is NOT NULL as before.

> **Note**: A value object is replaced as a whole. If you read a value object that has a `@writeonly` field that is not in the response and
> save it back unchanged, that field comes back empty - 400 if it is required, saved as null if it is optional.
> Use it for **values that are received only on creation**, such as a card, and if you need to change it, receive the whole value again.

The v3 mapper does not copy `@writeonly` values from entity -> DTO (same as `@password`). Even if server code passes a DTO
from `findById` back to `update`, the stored hash does not go in as the "new password".

### @table(name) / @tbl(name)
Sets the table name explicitly. If not set, the entity name is converted to snake_case.
```yaml
UserProfile:
  meta: entity @table(user_profile_tbl)
```

### @schema(name)
> **Since: 2.1.260409**

Sets the DB schema. Generates `@Table(schema="name")`. You can set it per entity, or for the whole domain at once with `tableSchema` in env.yml.

**Per entity** (higher priority):
```yaml
Customer:
  meta: entity @schema(schema_a)
  fields:
    id: long @id @sequence
    name: string(100)
# generates: @Table(name="customer", schema="schema_a")
```

**Whole domain** (env.yml):
```yaml
tableSchema: schema_a
# applies schema="schema_a" to all entities of that domain
```

Priority: entity `@schema` > env.yml `tableSchema`

### @Index(name, columnList, unique)  / @idx
Defines a database index. Generates `@Table(indexes=...)`.
```yaml
Message:
  meta: entity @Index(name="idx_msg_date", columnList="create_date,status")
```

### @rollup
Merges columns into the parent table. Creates no table of its own. Use it with `@extend` of a parent entity that has an `@id`.
```yaml
Item:
  meta: entity
  fields:
    id: long @id @sequence
    name: string
    dtype: string @formula("dtype")

Album:
  meta: entity @extend(Item) @rollup
  fields:
    artist: string
```
The discriminator value (`@DiscriminatorValue`) is `@rollup(discriminator=ALB)` (or `value=`, `type=`); if absent, `@rollup(target=...)` (or
`entity=`); if that is absent too, the entity name (`"Album"`). A value written without a name, as in `@rollup(Item)`, is not the discriminator value - the 2.x and
all 3.0 bundles behave the same.

### @terms(term, synonym, ...)
The names the business uses for this entity, and their synonyms. Also used on fields and on the metrics in `metrics:`. It has no effect on code generation;
it goes out as `ai_context.synonyms` in the Apache Ossie semantic model (`ossie-export`) (DSL guide "Business metrics and business terms").
```yaml
PurchaseOrder:
  meta: entity @root @terms(order, purchase, buy) -- order
Item:
  meta: entity @root @terms(pet, companion animal) -- item for sale
  fields:
    unitCost: money(10.2)! @terms(price, cost)
```

---

## 2. Field Annotations

### 2.1 Primary key

#### @id
JPA Primary Key. Generates `@Id`.
```yaml
userId: long @id
```

#### @sequence
Auto increment. Generates `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
```yaml
id: long @id @sequence
```
id
#### @genid
Just before saving (`@PrePersist`), if the key is empty, it creates a value and puts it in. What it creates depends on the length.

| DSL | Generated code | Value |
|---|---|---|
| `string @id @genid` | `java.util.UUID.randomUUID().toString()` | UUID (36 characters) |
| `string(n) @id @genid` | `IdUtils.newId(n)` | n characters (n >= 4): 3 characters taken from the full entity class name (including the package) + n-3 random alphanumeric characters (not a UUID) |
| `string @id @genid(com.acme.Ids.next)` | `com.acme.Ids.next()` | a static method you write yourself |

```yaml
orderId: string(36) @id @genid   # 36-character ElastiCORE ID
id:      string @id @genid       # UUID
```

### 2.2 Data constraints

#### @length(n) / @len(n) / @size(n)
Limits the string length. Generates `@Column(length=n)` + `@Size`. Prefer the type-parenthesis form.
```yaml
name: string(100)                    # recommended: set it directly on the type
title: string @size(min=5, max=200)  # combined: set min and max together
password: string(255)! @length(min=8)   # type length + minimum length -> @Size(min=8 , max=255)
```
When you use the type length (`string(255)`) together with `@length(min=..)`, the maximum is the type length and the minimum is the value of `@length`.
(In 2.x the minimum length was silently dropped in this combination.)

#### @default(value)
Field default value. Smart quote handling: double quotes are kept, single quotes are converted to double quotes, and quotes are added if missing.
```yaml
status: string @default("ACTIVE")        # kept as it is
category: string @default('GENERAL')     # converted to double quotes
isActive: boolean @default(true)
processType: ProcessType @default(ProcessType.HANG)
createdAt: datetime @default(now)        # LocalDateTime.now() - date, time and timestamp(Instant) also take now
```

**A creation time decided by the server** is written together with `@readonly` - you do not need to hand-write `beforeSave`:

```yaml
orderedAt: datetime @readonly @default(now)   # the time it was created; the request value is ignored; stays as it is on update
joinedOn:  date @readonly @default(now)       # today
```

It is the initial value of the entity field (`= LocalDateTime.now()`), so it is set when the row is created. Because of `@readonly`, a value sent by the request is
not used, and on update the value read from the DB stays as it is. The DTO gets no default (date defaults are
left out of the DTO). The default of a `timestamp`, whether `now` or another value (`@default(java.time.Instant.EPOCH)`), is put only on the entity (from 3.0.261022-rc1
- before, the DTO also got `@Builder.Default ... = java.time.Instant.EPOCH`). For a value that
**changes every time**, such as the last modified time, use `@jpa:org.springframework.data.annotation.LastModifiedDate` and `@EnableJpaAuditing`.

#### @notnull / @notblank / @required
NOT NULL constraint. Generates `@Column(nullable = false)`. Not needed together with `@id`.
```yaml
email: string(100) @notnull
```

#### @updatable(false)
A field that cannot be changed. Generates `@Column(updatable = false)`.
```yaml
createdDate: datetime @updatable(false)
item:        Item @ManyToOne @updatable(false)   # a review stays with the item it was first set to
```
In the DDD and hexagonal bundles, a reference to another root is written as an identifier field (`itemId`), so that identifier field inherits
`@updatable(false)`: it is `@Column(name = "item_item_id", updatable = false)`, and the mapper writes it only when the row has no value (creation) - even if an update
request sends a different `itemId`, the stored value and the response stay with the first item.

#### @readonly / @noinput - a field the server decides
A field that goes out in the response and is **not received from the request**. Use it for fields whose value server code decides, such as a join date, a grade or a processing number.
`@noinput` is an alias with the same meaning.

```yaml
Member:
  meta: entity @root @editable @expose(20)
  fields:
    memberId: long @id @sequence
    name: string(50)!
    joinedAt: date @readonly          # filled in by the service's beforeSave
    grade: string(10)! @noinput       # a required column, but the request does not send it
```

| Output | 2.x compatible (`jpa-springboot`, `output = 'v2'`) | v3 bundles (`jpa-springboot-v3` - the default of layered Java, DDD, hexagonal, Kotlin) |
|---|---|---|
| DTO `@JsonProperty(access=READ_ONLY)` | yes | yes |
| DTO `@Schema(... accessMode = READ_ONLY)` | no | yes |
| DTO `@NotNull` / `@NotBlank` (`!`, `@notblank`) | kept | **dropped** - because the request cannot send the value |
| Mapper entity -> DTO (response) | writes | writes |
| Mapper DTO -> entity (`toEntity`, `mapping(dto, entity, ...)`, including the EntityManager version) | writes | **does not write** |
| Entity column (`nullable = false` and so on) | as it is | as it is - the server must fill it |

v3 generated result (Java, `Member` above):

```java
// MemberDTO.java
@Schema(description = "grade" , requiredMode=Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.READ_ONLY )
@Size(max=10)
@JsonProperty(access=JsonProperty.Access.READ_ONLY)
private String grade;

// LibraryMapper.java - mapping(MemberDTO from, Member to, boolean isSkipNull, MappingContext c)
setVal(from.getMemberId(), to::setMemberId, isSkipNull);
setVal(from.getName(), to::setName, isSkipNull);
// joinedAt and grade are not there. They are in the opposite direction, mapping(Member from, MemberDTO to, ...).
```

Server code puts the value in. The `beforeSave` of the extension service runs on all four paths: create, update, batch update and conditional update.

```java
@Override
protected void beforeSave(Member entity) {
    if (entity.getJoinedAt() == null) entity.setJoinedAt(LocalDate.now());
}
```

In v3, a POST where the client sends a value for a `string(10)! @readonly` field returns 200, and the response carries the value that `beforeSave`
put in. If `@NotNull` stayed on the DTO, the same request would return 400 (`must not be null`) - because Jackson
checks after it has discarded the value.

Notes:
- In v3, **the mapper also discards a value that server code put in the DTO.** Code that did `dto.setGrade("A")` and then `service.save(dto)`
  no longer saves the value. Put it in the entity (`beforeSave`, a method of the root).
- `@readonly` on an `@id` (`@id @genid @readonly`) blocks JSON input only. The mapper keeps writing the identifier.
- Put on an entity field, it applies to all DTOs of that entity. When a DTO in `dto.yml` re-declares a template field and adds
  `@readonly`, **only the mapping of that DTO** does not write this field.
- Persistence fields such as `@jpa:CreatedDate` and `@jpa:Version` are not written by the v3 mapper even without `@readonly` (§2.8).
- springdoc emits `readOnly: true` in OpenAPI from `@JsonProperty(access=READ_ONLY)` alone. The v3
  `@Schema(accessMode = READ_ONLY)` records the same fact for tools that read only `@Schema`.

In the DDD bundle, a `@readonly` field of a split root (`XxxBase`) is still a locked field: the setter is `protected` and
`creation()` has the setter. Server code puts the value in a new root with `entity.creation().setLoanDate(v)`, and after that
changes it through a method of the root. For a **server value that changes after creation** (return date, status), `@behavior` is the right choice - it is the same in that the request does not
receive it, and it is not in `creation()` either, so only a method of the root changes it ([@behavior](#behavior---entity-behavior-ddd-rule-set)).

| | `@readonly` | `@behavior` (field) |
|---|---|---|
| Entities it applies to | all entities, v3 bundles | only split roots of the DDD bundle (only a warning on an `@editable` root or member) |
| Request (JSON, mapper) | not received | not received |
| `creation()` of a split root | present - server code puts the value in a new root | absent |
| Who changes it | server code (`beforeSave`, a method of the root) | a method of the root |

`creation().setX(null)` does nothing on a new root (a device so that a value the request did not send does not overwrite `@default`).
It is not a way to clear a value; to clear it, call the `protected` setter from a method of the root.

#### @min(value) / @max(value) / @min(value, 'message')
Numeric range validation. Generates Bean Validation `@Min`/`@Max` (`@DecimalMin`/`@DecimalMax` for BigDecimal).
You can give a message as the second argument.
```yaml
age: int @min(18) @max(65)
unitPrice: bigdecimal(12.2)! @min(0.01, 'The unit price must be greater than 0')
onHand: int! @min(0, 'Stock cannot be negative')
```

**In the DDD bundle, it is also a domain rule of the root.** The DTO checks it when the request comes in (400, a message per field),
and the invariant listener of the root entity checks the same condition **on every save** (422). A change that does not go through a request, such as a domain service
making the stock negative, is blocked too. So for a rule about a single field, write it on the field instead of `@invariant`. Use `@invariant` only for
**rules that compare two or more fields**.

#### @minsize(n, 'message') / @maxsize(n, 'message') - number of collection elements
The number of members a collection holds. On the DTO it becomes `@Size(min=n, message=...)` (and `@NotNull` too if n is 1 or more - 400 even if the list is not sent at all), and on the DDD root a check at save time (422).
A minimum count is violated also when the collection is absent (null). If n is 1 or more, the `validate...()` of the TypeScript client
also checks first with the same message - `if (!partial && (value.lines == null || value.lines.length < 1)) errors.lines = "..."`
(not asked on a partial update, `partial`). The collection does not go into the `...Fields` constants.
```yaml
lines: List<SalesOrderLine> @OneToMany(mappedBy="order") @minsize(1, 'A sales order must have at least one item')
```
If you declare the DTO directly in `dto.yml` and re-declare the same field, that field follows the DSL declaration, so if you want the request-stage (400)
check, write it on the DTO field too. The save-time check (422) always applies from the entity's declaration alone.

#### @pattern('regex', 'message') / @pattern(regexp="...", message="...")
A rule that the string must match the regex **in full**. The DTO gets Bean Validation `@Pattern` (request check, 400),
and the `validate...()` of the TypeScript client checks with the same regex and message.
As with `@min`, write the message after the value (the regex). The named form works the same way.
```yaml
isbn:  string(13)! @id @pattern('^[0-9]{13}$', 'ISBN must be 13 digits') -- ISBN, 13 digits
code:  string(10) @pattern('^[A-Z]{3}[0-9]+$') -- Shelf code          # message omitted
range: string(13) @pattern(regexp="^[0-9]{10,13}$", message="10 to 13 digits")
email: string @pattern(regexp="^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message="Invalid email")
```
Generated DTO (v3 / Kotlin bundles):
```java
	@Schema(description = "ISBN, 13 digits" , requiredMode=Schema.RequiredMode.REQUIRED )
	@NotNull
	@Pattern(regexp = "^[0-9]{13}$", message = "ISBN must be 13 digits")
	@Size(max=13)
	private String isbn;

	@Schema(description = "Shelf code"  )
	@Pattern(regexp = "^[A-Z]{3}[0-9]+$", message = "Shelf code is not in the expected form")
	@Size(max=10)
	private String code;
```
TypeScript client:
```ts
else if (value.isbn && !new RegExp("^(?:^[0-9]{13}$)$").test(String(value.isbn))) errors.isbn = "ISBN must be 13 digits";
```
- The **message** can be omitted. Then both server and client say `"<label> is not in the expected form"`
  (the label is the `--` comment, or the field name if there is none).
- Write **backslashes twice**. The regex goes in as the content of a Java string literal as it is: `\\d`, `\\.`.
  A character class such as `[0-9]` is easier. If you write only one, as in `\d`, Java does not compile it (`illegal escape character`)
  and TypeScript reads it as `d`, so the DSL gives a warning at generation time:
  `Book.digits: @pattern has \d, which a string literal does not read as a backslash. Write it doubled (\\d) or use a class such as [0-9].`
- In **Kotlin**, a `$` that would become a string template (a `$` before a letter, `_` or `{`) is written as `\$`. In the DSL, write it exactly as in Java.
  `@pattern('^\\$USD[0-9]+$', 'write it as $USD100')` -> `@Pattern(regexp = "^\\\$USD[0-9]+$", message = "write it as \$USD100")`.
  A `$` at the end of the regex stays as it is.
- A **comma** inside the regex is fine (`{10,13}`). The message is what follows the first comma outside quotes and parentheses.
  If the regex contains `=`, use the named form and wrap its value in double quotes (a single-quoted value is cut at a space).
- It applies to **string fields only**. `@pattern(yyyyMMdd)` on a date field is, as before, a date format (`@DateTimeFormat` / `@JsonFormat`).
- It goes into **the DTO and the TS client only**. It is not on the entity (only `@Column`) or the search DTO, and unlike `@min` it is not a save-time check (422) of the DDD root either.
  The regex is not used as the Swagger `example` either (it is not an example value).
- The 2.x-compatible output (`jpa-springboot`) is unchanged: only when a named form also has a message does it use a multi-line
  `@jakarta.validation.constraints.Pattern( regexp="...", msg="..." )`, and `msg` is not an attribute of Jakarta `@Pattern`,
  so it does not compile. This fix is in the output of the v3 / Kotlin / TypeScript bundles.

### 2.3 Search annotations
`@search(condition)` - described in detail in Section 3.

### 2.4 References and types

#### @ref(fieldPath)
References a field of another entity in the DTO. Generates a nested get.
```yaml
boardName: string @ref(board.name)           # r.get("board").get("name")
boardTypeName: string @ref(board.boardType.name)
```

On update (DTO -> entity), the value is written to the row beyond the relation (`to.getBoard().setName(from.getBoardName())`). In the v3 bundles of the 3.0 rule
engine (`jpa-springboot-v3` and `jpa-springboot-kt`, which contain `jpa-service-access`), a `@ref` to an **entity that decides who may write**
(an entity with `@access`, `@owner`, `@password` or a field `@access(admin)`) is **read-only** -
because someone who may edit a ticket could change the assignee's login or password and skip that entity's checks. A `@ref(assignee.memberId)`
that points to the key of the relation still changes the row the relation points to, as before. A `@ref` that reads a `@password` is left out of the DTO and
the DSL gives a warning (`... is left out: @ref(assignee.password) reads a @password ...`). The DDD and hexagonal bundles already keep every `@ref` to
another aggregate read-only. The 2.x-compatible bundle (`jpa-springboot`) is unchanged.

#### @fk(Target) / @fk(Target.field) / @fk(Target.field, groupName)

**Foreign Key metadata** - declares an FK relation on a primitive field. It does not generate JPA `@ManyToOne` and
keeps a plain `@Column`, so it suits **legacy DB migration, analysis, and avoiding
N+1 / lazy loading**.

**Aliases**: `@fk`, `@foreignkey`

**Field type requirement**: it must be a primitive type (long, int, string and so on). For an entity reference
type, use the existing relation annotations such as `@manytoone`.

##### Single FK - referencing the target entity
```yaml
Article:
  fields:
    id:       long @id @genid
    boardId:  long! @fk(Board)        # automatically references the @id field of Board
    title:    string(200)!
```

##### Single FK - naming Target Entity.field
```yaml
Article:
  fields:
    id:       long @id @genid
    boardId:  long! @fk(Board.id)     # explicitly references the id field of Board
    title:    string(200)!
```

##### Composite FK - grouping several fields into one logical FK by group name
```yaml
ArticleAudit:
  fields:
    id:       long @id @genid
    tenantId: long! @fk(Board.tenantId, boardFk)
    boardSeq: long! @fk(Board.seq, boardFk)
    # the two fields are grouped as 'boardFk' and recognized as one composite FK
```

##### Several FKs to the same target (told apart by group)
```yaml
Article:
  fields:
    id:               long @id @genid
    # author FK (writer group)
    writerTenantId:   long! @fk(User.tenantId, writer)
    writerId:         long! @fk(User.id, writer)
    # last modifier FK (modifier group)
    modifierTenantId: long! @fk(User.tenantId, modifier)
    modifierId:       long! @fk(User.id, modifier)
```

##### Generated Java code
`@fk` is metadata, so the generated Java code is the same as a plain `@Column`:
```java
@Column(name = "board_id", nullable = false)
private Long boardId;
```

##### schema.yml output
schema.yml contains the FK metadata:
```yaml
Article:
  fields:
    boardId:
      type: long
      annotations: '@Column(name="board_id") @NotNull @fk(Board.id)'
```

##### DSL Validation
- If the target entity does not exist, a `DSL_WARN` warning is given
- If `Target.field` is written and that field does not exist, a `DSL_WARN` warning is given
- With `./gradlew elcore -Pmode=valid`, it is reported as a build failure

##### @fk vs @manytoone comparison

| Item | `board: Board! @manytoone` | `boardId: long! @fk(Board.id)` |
|------|--------------------------|-------------------------------|
| Field type | Entity reference (Board) | Primitive (Long) |
| JPA annotation | `@ManyToOne @JoinColumn` | `@Column` |
| Lazy loading | managed by the JPA proxy | none (explicit lookup needed) |
| N+1 risk | yes | no |
| Use case | new object-oriented design | legacy DB migration / analysis |

#### @dtype(ClassName)
Names the type to use when converting to a DTO.
```yaml
articles: List<Article> @dtype(List<ArticleDTO>)
```

#### @fetchjoin
JOIN FETCH to avoid N+1. Applied in the Service's `withJoinFetches()` - used by the page search (`findBySearch`). Automatically excluded from the count query.
Both 2.x code and the v3 bundles (from 3.0.261029-rc4).
```yaml
postMan: PostMan @fetchjoin
category: Category @fetchjoin
```

#### @formula(expression)
Read-only SQL expression mapping. A computed value, not a DB column.
```yaml
dtype: string @formula("dtype")
orderCount: int @formula("(select count(*) from orders o where o.customer_id = id)")
createdByNm: string(100) @formula("coalesce((select u.name from system_user u where u.user_id = created_by), created_by)")
```
- Wrap the expression in **double quotes**. Inside the quotes, `=`, `,`, parentheses and the SQL string `'Y'` are used
  as part of the expression as they are (from 2.2.261012 and 3.0). You cannot use a double quote (`"`) inside the expression.
  Example: `activeCount: long @formula("(select count(*) from member m where m.team_id = team_id and m.use_yn = 'Y')")`
- The DB computes the value when it reads, so the Mapper does not put the value in the DTO->Entity direction (`toEntity`, `mapping(dto, entity)`).
  It is included like a normal field in the Entity->DTO direction and in search conditions. In the TypeScript client's
  `XxxFields` it is `readOnly: true` and `validateXxx` does not check it.
- If you declare it on an `@abstract` parent entity, it is generated in the parent class, and an inheriting entity inherits the field.
- The Entity gets a field but no table column. The field gets `@Formula` and
  not `@Column` or `@Comment` (`-- description` stays as a Java comment). This is because Hibernate ignores
  `@Formula` and maps to a real column if `@Column` is present.
- Earlier versions put `@Column` next to `@Formula`, and so created a column, as soon as any `@Column` attribute arose:
  a length (`string(100)`), a length guessed from the name (a `string` field without a length whose name contains `Nm`, `Name`, `Code`, `Type`, `Status`,
  `Addr`, `Date`, `Time`, `Zip` or `Tel`),
  `!`, `@unique`, `@updatable(false)`, `@text`. `ddl-auto: update` does not drop that column, so drop it in the DB yourself
  (if `!` made it a NOT NULL column, INSERT fails after the upgrade).
- The expression runs as a subquery inside the SELECT every time that Entity is read (including list reads).
  It is not run separately only when converting to a DTO.
- In `schema.yml` it is shown as `@Formula` instead of `@Column`.

### 2.5 JPA relations

An Entity-typed field gets **`@ManyToOne` applied automatically by default**. You only need to specify it explicitly when you use `@OneToOne` or `@ManyToMany`.

```yaml
# @ManyToOne (applied automatically - the two forms below are the same)
board: Board                    # @ManyToOne can be omitted
board: Board @ManyToOne         # explicit (same result)

# @OneToMany
articles: List<Article> @OneToMany(mappedBy="board")

# @ManyToMany + cascade
roles: List<Role> @ManyToMany @cascade(ALL)

# @OneToOne
profile: UserProfile @OneToOne @cascade(ALL)

# @Embedded
address: Address @Embedded

# @relation (set mappedBy)
address: Address @relation(mappedBy="market_post_id")
```
cascade types: `ALL`, `PERSIST`, `MERGE`, `REMOVE`

When a relation with cascade is carried whole in the DTO (`member: QmMember @OneToOne @cascade(merge) @dtype(QmMemberDTO)`, a `List<...> @OneToMany @cascade` of an entity in another
domain), on update (DTO -> entity) the row the request sent is attached to the relation, and the save writes that row too through the cascade.
In the v3 bundles of the 3.0 rule engine (`jpa-springboot-v3`, `jpa-springboot-kt`, DDD and hexagonal, which contain `jpa-service-access`), a cascade relation to an
**entity that decides who may write** (an entity with `@access`, `@owner`, `@password` or a field `@access(admin)`, including a `@signin` entity) is not written from the DTO - someone who could edit the profile
could change the member's login or password (by building the hash directly).
Edit that row through its own service (its own API); the key the DTO sends along (`memberMemberId`) still
points to the relation, as before. The response carries it as it is. The build points out the field:
`[qmshop] QmProfile.member is not written from the DTO: QmMember says who may write it (@access, @owner, @password), and the
cascade would save the row past those checks. ...`. Create (`POST`) is the same, so the row carried in the request is not saved.
A cascade to an entity with no such checks, a relation without cascade (a hand-written cascade such as `@jpa:OneToOne(cascade = ...)` counts as a
cascade), and the 2.x-compatible bundle (`jpa-springboot`) are unchanged.

In the DDD and hexagonal bundles, a **member of the same aggregate** comes together with the root by design, so it is outside the rule above and is written through the root.
But a member has no service of its own, and the root's service cannot hash or check for it, so the `@password` and `@access(admin)` fields of a member are
not written from the DTO, and the build tells you (`QdAccount.secret is not written from a DTO: QdAccount is a member of QdCustomer's
aggregate and arrives with it, and the root's service hashes a @password of only its own fields. Declare it on QdCustomer.`).
Put such a field on the root.

#### FK column name (@column / @col)

Using `@column` (or `@col`) on an Entity reference field generates `@JoinColumn(name=...)`. If not set, the JPA default rule (field name + `_id`) applies.

```yaml
# FK column name not set - the JPA default rule applies (comment_id)
comment: Comment

# FK column name set - generates @JoinColumn(name="parent_comment_id")
comment: Comment @column(parent_comment_id)
comment: Comment @col(parent_comment_id)

# NOT NULL + FK column name set - generates @JoinColumn(name="parent_comment_id", nullable=false)
comment: Comment! @col(parent_comment_id)
```

#### Automatic DTO rendering of relationship fields (nested DTO + FK field)
> **Since: 2.2.260703**

In the main DTO that an `@expose`/`@service` entity generates automatically, a `@ManyToOne`/`@OneToOne` relationship field is
**rendered as two fields**:

```yaml
# entity
ScheduleInfo:
  meta: entity @expose(20)
  fields:
    scheduleId: string(20) @id
    batch: BatchInfo @ManyToOne @fetchjoin
```
```java
// Generated ScheduleInfoDTO
@Schema(description = "batch" , accessMode = Schema.AccessMode.READ_ONLY )
private BatchInfoDTO batch;          // for query responses (nested detail)

@Schema(description = "batchBatchId"  )
private String batchBatchId;         // for save/update input (FK id)
```

- **Nested DTO field (`batch`)**: It is marked `accessMode = READ_ONLY` in the OpenAPI schema, so it is
  **excluded from the save/update request body in Swagger** (it stays in query responses).
  This is a documentation (spec) level mark; runtime serialization/deserialization does not change. A request that sends the nested object
  does not fail, but the mapper does not read the nested object on save/update - the relationship is linked only through the FK id field.
- **FK id field (`batchBatchId`)**: The input field that sets the relationship on save/update
  (named `field name + referenced entity @id field name`, linked internally with `em.getReference()`).
  If the key of the referenced entity is `id`, the name is `batchId`.
- To exclude the nested DTO from generation, declare the DTO yourself in `dto.yml` with `@template`
  (relationship fields are excluded automatically; declare only the fields you need, flattened with `@ref(relationField.targetField)` -
  see `EmployeeDTO` in blueprint `hr/model/dto.yml`).

##### Required relationship (`!`): the save request sends the FK id field
> **Since: 3.0.261022-rc1** (v3, Kotlin and TypeScript generation. 2.x and the `jpa-springboot` compatible output stay as they are)

If you add `!` to a relationship, the column becomes NOT NULL (`@JoinColumn(nullable = false)`), and in the automatically generated DTO the
**FK id field is required** and the nested DTO is optional.

```yaml
Post:
  meta: entity @root @expose(20) -- post
  fields:
    postId:    long @id @sequence
Comment:
  meta: entity @root @expose(20) -- comment
  fields:
    commentId: long @id @sequence
    post:      Post! -- parent post
```
```java
// Generated CommentDTO (v3; Kotlin has the same annotations)
@Schema(description = "parent post" , accessMode = Schema.AccessMode.READ_ONLY )
private PostDTO post;                // for responses - optional in requests

@Schema(description = "postPostId" , requiredMode=Schema.RequiredMode.REQUIRED )
@NotNull
private Long postPostId;             // required in the save request
```
```ts
// TypeScript client CommentDTO.ts
post?: PostDTO;
postPostId: number;
```

- The save request (`POST`, `@Valid @RequestBody`) **sends the FK id field**, as in `{"postPostId": 1, ...}`.
  Without `postPostId`, `@Valid` validation refuses the request. You do not need to send the nested `post`.
- The 2.x (`build`/`elcore`) and `jpa-springboot` compatible output is the same as before: the nested `post` gets `@NotNull` and
  `requiredMode=REQUIRED`, and `postPostId` does not. In this output `post` is needed to pass validation and `postPostId` is needed to
  save the relationship, so the request must send both.
- **What changed in v3 output before 3.0.261022-rc1**: Before, v3 also required the nested `post`, as 2.x does. Now a request that sends only `postPostId`
  passes, and a request that sends only the nested `post` is refused by `@Valid` validation (that request could not link the relationship before either -
  the mapper reads only `postPostId`).
- `postPostId` in the search DTO (`CommentSrchDTO`) is a search condition, so it is not required. The nested `post` is not in the search DTO.
- In DDD and hexagonal bundles that refer to another aggregate by ID, the FK id field is the entity's ID field (`memberId`),
  and by the same rule that field is required - see `elasticore-rule-guide.md` "Refer to another aggregate by ID".
- This does not apply to a DTO you declare yourself in `dto.yml`. In a DTO you declare yourself, write `!` on the
  field, as in `postId: long! @ref(post.postId)`.

### 2.6 JPA column mapping

#### @Column / @col / @db
Database column mapping. If not set, the field name is converted to snake_case automatically.
```yaml
postNo: string(5) @Column(name="post_no", length=5)
username: string @col(unique=true)
```
Parameters: `name`, `length`, `unique`, `nullable`

#### @columnDef('xxx') / @jdbcType('XXX')
> **Since: 2.2.260421-4**
> **Requirement: Hibernate 6.2 or later**
> **First-stage scope: based on the `string` type (BaseFieldType.STRING). Map / POJO / List<POJO> come in a later stage.**

Set any DB column definition (JSON / JSONB / XML and so on) and the Hibernate JdbcTypeCode directly in the DSL.
The DB column definition is **emitted together as the `columnDefinition` attribute inside a single `@Column`**; no separate `@Column` annotation is added.

| DSL | Mapped JPA / Hibernate output |
|---|---|
| `@columnDef('xxx')` | merged as the `@Column(columnDefinition="xxx")` attribute |
| `@jdbcType('XXX')`  | `@JdbcTypeCode(org.hibernate.type.SqlTypes.XXX)` |

**Automatic inference rule** (when `@jdbcType` is not set, it is inferred from the `@columnDef` value):

| `@columnDef` value (case-insensitive) | `@jdbcType` applied automatically |
|---|---|
| `json`, `jsonb` | `JSON` |
| `xml` | `SQLXML` |
| (others) | no inference - emitted only when `@jdbcType` is set |

If `@jdbcType` is set, **the explicit value wins** over the automatic inference.

```yaml
fields:
  # automatic inference
  metadataRaw: string @columnDef('jsonb')                              # @JdbcTypeCode(SqlTypes.JSON)
  oldPayload:  string @columnDef('json')                               # @JdbcTypeCode(SqlTypes.JSON)
  customXml:   string @columnDef('xml')                                # @JdbcTypeCode(SqlTypes.SQLXML)

  # explicit override - use LONG32VARCHAR instead of the inferred JSON
  forceLong:   string @columnDef('jsonb') @jdbcType('LONG32VARCHAR')

  # future extension (any SqlTypes constant can be emitted without a DSL-side code change)
  geomCol:     string @columnDef('geometry') @jdbcType('GEOMETRY')
```

**Generated code example**:
```java
@Column(name = "metadata_raw", columnDefinition = "jsonb")
@JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
private String metadataRaw;

@Column(name = "old_payload", columnDefinition = "json")
@JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
private String oldPayload;

@Column(name = "custom_xml", columnDefinition = "xml")
@JdbcTypeCode(org.hibernate.type.SqlTypes.SQLXML)
private String customXml;

@Column(name = "force_long", columnDefinition = "jsonb")
@JdbcTypeCode(org.hibernate.type.SqlTypes.LONG32VARCHAR)
private String forceLong;
```

**Notes**:
- `@JdbcTypeCode` is resolved by the `org.hibernate.annotations.*` wildcard import (no extra import needed).
- `org.hibernate.type.SqlTypes` is emitted inline as an FQCN (`org.hibernate.type.SqlTypes.XXX`), so no extra import is needed.
- The first stage is verified **only for the `string` type**. Mapping of `Map<String,Object>`, POJO and `List<POJO>` is planned for a separate stage (enabling BaseFieldType.MAP, adding TypeInfo / relationship branch handling).
- On the DB side, PostgreSQL `jsonb` / `json` columns are the most common use, and Hibernate 6.2+ handles the mapping without a separate library.

### 2.7 Custom functions

```yaml
fullName: string @function.get("getFullName")       # custom getter
password: string @function.set("setEncryptedPassword")  # custom setter
```

### 2.8 Spring Data JPA auditing

```yaml
createDate: datetime @jpa:org.springframework.data.annotation.CreatedDate
createdBy: string(20) @jpa:org.springframework.data.annotation.CreatedBy
lastModifiedDate: datetime @jpa:org.springframework.data.annotation.LastModifiedDate
lastModifiedBy: string(20) @jpa:org.springframework.data.annotation.LastModifiedBy
```

In v3 bundles, the mapper does not write these four fields and `@jpa:Version` from the DTO to the entity, and the DTO's `@NotNull` is
dropped too (`jpa-write-guard`). They still go out in the response. To also mark them read-only in the DTO and OpenAPI, add
`@readonly` (§2.2 `@readonly / @noinput`):

```yaml
createdBy: string(20) @jpa:org.springframework.data.annotation.CreatedBy @readonly
```

### 2.9 Others

```yaml
temporaryData: string @notaudited          # excluded from Envers auditing
coordinates: int[] @env:integerarray       # see env.yml annotations
```

---

## 3. Search Conditions - Full Reference

### 3.1 Overview

`@search(condition)`, `@s(condition)`, `@srch(condition)` and `@filter(condition)` are all the same.
The separator between the condition and the mapped field is `,` (the generator splits on the comma).
```yaml
customName: string @search(like,comName)   # like search on the comName field
```

### 3.2 Full list of implemented operators (18)

| DSL syntax | JPA Criteria | Ignore case | Description |
|----------|-------------|:---:|------|
| `eq`, `=` | `equal` | No | exact match |
| `like` | `like` (%v%) | **v3: Yes** / 2.x compatible: No | contains search. In v3 mode (layered Java, the plugin default; hexagonal and DDD; all Kotlin builds) it is the same as `%__%` on a string field - it finds part of a name regardless of case. Layered Java that keeps 2.x code (`output = 'v2'`) still distinguishes case |
| `%_%`, `%%` | `like` (%v%) | No | case-sensitive contains search (all modes) |
| `%__%` | `like` (%v%) | Yes | case-insensitive contains search |
| `__=`, `=__` | `equal` | Yes | case-insensitive exact match |
| `%_` | `like` (%v) | No | suffix search (ends-with) |
| `_%` | `like` (v%) | No | prefix search (starts-with) |
| `%__` | `like` (%v) | Yes | case-insensitive suffix search |
| `__%` | `like` (v%) | Yes | case-insensitive prefix search |
| `>` (`gt`) | `greaterThan` | No | greater than |
| `>=` (`gte`, `ge`) | `greaterThanOrEqualTo` | No | greater than or equal |
| `<` (`lt`) | `lessThan` | No | less than |
| `<=` (`lte`, `le`) | `lessThanOrEqualTo` | No | less than or equal |
| `!=` (`ne`, `neq`) | `notEqual` | No | not equal (v3 bundles only) |
| `in` | `.in()` / `Join.in()` | No | contains any of several values |
| `between`, `~` | `between` | No | range search |
| `isnull` | `isNull` / `isNotNull` | No | checks whether the value is empty. Search property `<field>Empty: Boolean` (3.4.1, v3 bundles only) |
| `auto`, (default - `@search` without a condition) | decided at runtime | No | String only; like if it contains %, otherwise eq |

- In v3 mode, the case-insensitive conditions (`like`, `%__%`, `%__`, `__%`) trim the leading and trailing spaces of the value (as `like` does)
  and lower-case it regardless of locale (Java `toLowerCase(Locale.ROOT)`, Kotlin `lowercase()` - in Kotlin 2.x
  `toLowerCase()` is a compile error). The column is compared with `lower(...)`.
- A `%` or `_` that the caller puts in the value is still a wildcard (`{"name":"%Puppy%"}`).
- Index: a contains search (`%v%`) cannot use a B-tree range on any DB, so `lower()` adds no cost. To find the prefix
  `__%` and the exact match `__=` through an index, you need a `lower(col)` expression index. The default CI collation of MySQL and SQL Server
  already ignores case.

**Comparison keywords and `!=`:**
- In v3 mode (layered Java, the plugin default; hexagonal and DDD; all Kotlin builds) the keywords are the same as the symbols: `gt` = `>`, `gte`/`ge` = `>=`,
  `lt` = `<`, `lte`/`le` = `<=`, `ne`/`neq` = `!=` (case-insensitive). The generated code is the same as when you write the symbol (from 3.0.261027-rc2).
  `!=` is SQL `<>`, so rows where the column is empty (NULL) are not in the result - to find empty values too, add an `isnull` field.
- In layered Java (2.x compatible) and in 2.x, the keywords and `!=` work as an **exact match** (no error and no warning). In this mode,
  use the symbols `>`, `>=`, `<`, `<=`; there is no not-equal search.
- There is no `ilike` - for a case-insensitive contains search use `%__%` (string `like` in v3 mode does the same). An unknown condition becomes an exact match,
  and in v3 mode the build prints the DSL warning `... @search(ilike) is not a search condition, so it searches by equality ...`
- `@search(%)` with a single `%` is an end match (`like '%' + value`, the same as `%_`). In v3 mode the build prints the
  `... @search(%) searches by the end of the value (like '%' + value) - say %_ for that, _% for the start or %_% for any part.`
  DSL warning.

### 3.3 auto condition behavior

In an `@expose` entity, a field without `@search` gets a type-based automatic assignment:
- **String** -> `auto` (at runtime, like if the value contains `%`, otherwise eq)
- **LocalDate / LocalDateTime / DATE / DATETIME / LocalTime** -> `between`
- **Other types** -> `eq`

The runtime logic of `auto` (the generated `setSpec` method):
```java
if(value.startsWith("%") || value.endsWith("%"))
    return sp.and((r,q,c) -> c.like(r.get(fieldName), value));
else
    return sp.and((r,q,c) -> c.equal(r.get(fieldName), value));
```

### 3.4 between special behavior

Two fields, `fieldNameFrom` and `fieldNameTo`, are generated in the SearchDTO automatically.

**LocalDate special handling:**
- If both From and To are set: apply **+1 day** to To, then `between(from, to+1)` - includes the whole last day
- If only From is set: `greaterThanOrEqualTo(from)`
- If only To is set: apply **+1 day** to To, then `lessThan(to+1)` - includes the whole last day

**Types other than LocalDate:**
- If both From and To are set: `between(from, to)`
- If only From is set: `greaterThanOrEqualTo(from)`
- If only To is set: `lessThanOrEqualTo(to)`

```yaml
registDate: localDate @search(between)
# -> SearchDTO: registDateFrom, registDateTo fields are generated
# -> it is a LocalDate, so To+1 day is applied automatically
```

### 3.4.1 isnull - search by empty value

Use it to search by **whether a value is empty**, as in "loans not yet returned" or "products never received into stock".
The search DTO gets one `Boolean` property named `<field>Empty` instead of a field value, and that value decides the condition.

| `<field>Empty` in the request | Condition |
|---|---|
| not sent or `null` | no condition (all) |
| `true` | `IS NULL` - only empty rows |
| `false` | `IS NOT NULL` - only rows with a value |

```yaml
entity:
  Product:
    meta: entity @expose(20)
    fields:
      lastRestocked: datetime @search(isnull) -- last restocked, empty until the first restock
```

The generated `ProductSrchDTO` (no date format annotation and no `example` - the value you send is not a date):
```java
/*
  last restocked, empty until the first restock
*/
@Schema(description = "last restocked, empty until the first restock Field is empty (true) or set (false). field:lastRestocked"  )
private Boolean lastRestockedEmpty;
```

The generated Mapper `toSpec(ProductSrchDTO searchDTO, Specification<Product> sp)`:
```java
Boolean lastRestockedEmpty = searchDTO.getLastRestockedEmpty();
if(hasValue(lastRestockedEmpty)){
    if(lastRestockedEmpty){
        sp = sp.and((r,q,c) -> c.isNull(r.get("lastRestocked")));
    }else{
        sp = sp.and((r,q,c) -> c.isNotNull(r.get("lastRestocked")));
    }
}
```

Call: `POST /api/v1/shop/product/search {"name":"kettle","lastRestockedEmpty":true}` -> only products whose name contains kettle and
that have never been received into stock. It is combined with the other search conditions by AND.

Kotlin and TypeScript come from the same rule:
```kotlin
var endDateEmpty: Boolean? = null                    // ProjectSrchDTO.kt

val endDateEmpty = searchDTO.endDateEmpty            // HrMapper.kt toSpec
if(hasValue(endDateEmpty)){
    if(endDateEmpty!!){
        sp = sp.and(Specification<Project> { r,q,c -> c.isNull(r.get<java.time.LocalDate>("endDate")) })
    }else{
        sp = sp.and(Specification<Project> { r,q,c -> c.isNotNull(r.get<java.time.LocalDate>("endDate")) })
    }
}
```
```typescript
endDateEmpty?: boolean;                              // ProjectSrchDTO.ts
```

- **One condition per field.** A field marked `isnull` does not get the From/To of `between`. If you also need a period search,
  for now add the condition yourself in the Service with `Q`/`Op` (dev-guide "Op enum").
- The type-based automatic assignment of `@expose` (3.3, date -> `between`) stays as it is. Only the field marked `@search(isnull)` changes.
- Used with `@ref(order.shippedDate)`, it checks the column across the relationship: `c.isNull(r.get("order").get("shippedDate"))`.
- The second value (after the comma) is the **entity field name**, as in the other conditions: `@search(isnull,returnedDate)` ->
  property `returnedDateEmpty`, condition `r.get("returnedDate")`.
- It makes no sense on a `!` / `@notnull` field (`true` always gives an empty result). There is no warning yet.
- It works in the v3 bundles of the 3.0 rule engine (`elcore3`) (`jpa-springboot-v3`, `ddd-springboot`, `hexagonal-springboot`, the Kotlin versions,
  `ts-model`). The 2.x generator (`elcore`) and the 2.x compatible bundle (`jpa-springboot`) treat `isnull` as an unknown value
  and produce the same-value comparison (`c.equal`) on the field type as before - 2.x output does not change.

### 3.5 List field + eq = Join generation

When you apply `@search(eq)` to a `List<Entity>` type field:
- The SearchDTO gets a `fieldNameItem` field (the list element type)
- JPA `root.join("field")` + `equal(join, value)` are generated automatically

```yaml
drivers: List<RentDriver> @search(eq)
# -> SearchDTO: driversItem (RentDriver type)
# -> Join<Entity, RentDriver> join = root.join("drivers");
#    return criteriaBuilder.equal(join, driversItem);
```

### 3.6 The in operator and List fields

**Plain field:**
```java
sp.and((r,q,c) -> r.get("status").in(status));
```

**List<Entity> field:**
```java
Join<Entity, ItemType> join = root.join("items");
return join.in(items);
```

### 3.7 @ref combined with a search condition

With `@ref(parentEntity.childField)`, a nested get is generated:
```yaml
companyName: string @search(like) @ref(company.name)
# -> r.get("company").get("name")
```

### 3.8 Practical example

```yaml
entity:
  RentCompany:
    "@": "@expose @pageable @audited"
    fields:
      comId: string @id @search(eq)
      comName: string @search(like) @length(100)
      email: string @search(%__%)
      status: string @search(in)
      registDate: localDate @search(between)
      closeDate: localDate @search(isnull)
      score: int @search(>=)
      drivers: List<RentDriver> @search(eq)
```

Generated SearchDTO fields:
- `comId: String` (eq)
- `comName: String` (like)
- `email: String` (%__% case-insensitive)
- `status: String` (in)
- `registDateFrom: LocalDate`, `registDateTo: LocalDate` (between)
- `closeDateEmpty: Boolean` (isnull - `true` in business, `false` closed, omitted all. v3 bundles only)
- `score: Integer` (>=)
- `driversItem: RentDriver` (Join eq)
- `sortCode: String`, `pageNumber: int`, `pageSize: int` (generated automatically)
- When the request does not send `sortCode`, the order is set by `@sort` on the entity (or the search DTO) (below)

---

## 4. DTO Annotations

### 4.1 @template(EntityName)
Generates a DTO based on an entity. Fields are copied automatically. You can combine several DTOs/entities.
```yaml
dto:
  UserDTO:
    meta: dto @template(User)
    fields:
      password: --              # exclude field
      createDate: --            # exclude field

  CarProfileInfoDTO:
    meta: dto @template(CarProfileDTO,CarInfoDTO)  # multiple templates
```

With `@template`, the `string(n)` length of the entity is applied to the DTO as `@Size(max=n)` automatically.

### 4.2 @searchable
Generates a DTO only for search. Parameters: `entity` (required), `pageSize`, `pageable`.
```yaml
dto:
  UserSearchDTO:
    meta: dto @searchable(entity=User, pageSize=25)
```
With pageSize set, it implements the `PageableObject` interface. Generated fields: `pageNumber` (default 0), `pageSize` (the set value), `sortCode`.

### 4.2.1 @sort - default order
The order when the request does not send `sortCode`. Written in the entity meta, it applies to all search DTOs of that entity; written in a search DTO meta,
it applies only to that DTO (the search DTO side wins). The format is the same as `sortCode`: property + direction (`+` ascending, `-` descending,
ascending if omitted), several separated by commas.
```yaml
entity:
  Product:
    meta: entity @root @expose(20) @sort(name+)
  PurchaseOrder:
    meta: entity @root @expose(20) @sort(orderDate-, orderId-)
```
Generated: `@Builder.Default private String sortCode="name+";` (Kotlin `override var sortCode: String? = "name+"`).
If the request sends `sortCode`, that wins. `ShopFlowTest` checks that the Pet Store product search comes out in name order.
- If you write a property that the entity does not have, a DSL warning (`@sort(...): x is not a field of Product`) is printed and only that property is dropped (this prevents a sort 400).
- Without `@sort`, the query runs with no ordering, as before.
- It is a 3.0 rule engine (`elcore3`) feature. The 2.x generator (`elcore`) does not read `@sort`.

### 4.3 @searchBypass
When a search DTO is generated, excludes the field from the search conditions.
```yaml
internalId: string @searchBypass
```

### 4.4 JSON annotations

```yaml
userName: string @json("user_name")            # @JsonProperty("user_name")
amount: BigDecimal @jsonSerialize(using=com.example.MoneySerializer)
optionalField: string @jsoninclude             # @JsonInclude
```
Aliases: `@json` = `@jsonproperty`, `@jsoninclude` = `@includejson`

### 4.5 Bean Validation

```yaml
email: string(100) @email                       # @Email
isbn: string(13) @pattern('^[0-9]{13}$', 'ISBN must be 13 digits')   # @Pattern(regexp = ..., message = ...) - see 2.2
birthDate: date @past                           # @Past
eventDate: datetime @future                     # @Future
orderDate: datetime @pastorpresent              # @PastOrPresent
checkInDate: date @futureorpresent              # @FutureOrPresent
address: AddressDTO                             # DTO type -> @Valid is added automatically
```

Message patterns: simple English such as `"{FieldName} required"`, `"{FieldName} max {n}"`, `"Email format invalid"`, `"Date must be past"`.
`@pattern` can take a message directly; if omitted, it is `"<label> is not in the expected form"` (the same sentence as the TS client).

---

## 4.6 @jsonview - DTO field visibility control (JsonView)

> **Since: 2.1**

### Overview
It uses Jackson `@JsonView` to control DTO field visibility per REST API operation (Create/Update/Read).
One DTO class can expose different fields in Swagger for the Create, Update and Read operations.

### DTO meta annotation: @jsonview

| Value | Description |
|---|---|
| `@jsonview` | Manual mode. You must set `@view` on fields |
| `@jsonview(auto)` | Automatic mode. Visibility is inferred from existing annotations |
| `@jsonview(false)` | Explicitly disabled |

```yaml
dto:
  ProductDTO:
    meta: dto @template(Product) @jsonview(auto)
```

It can also be applied to a DTO that is generated automatically from an entity:
```yaml
entity:
  Product:
    meta: entity @expose @jsonview(auto)
```

### Field annotation: @view

> **Since: 2.1**

Two syntaxes are supported:

**Short syntax** (recommended):
```yaml
fields:
  id: long @id @view(r)          # Read only
  name: string @view(cru)        # Create, Read and Update
  status: string @view(ur)       # Update and Read only
```

**Full name syntax**:
```yaml
fields:
  id: long @id @view(read)
  name: string @view(create,update,read)
```

| Character | Full name | Meaning | Controller mapping |
|------|----------|------|----------------|
| `c` | `create` | shown in the create operation | `@PostMapping` |
| `u` | `update` | shown in the update operation | `@PutMapping` |
| `r` | `read` | shown in query responses | `@GetMapping`, all responses |

**Rules**:
- A field without `@view`: shown in all Views (default behavior)
- A DTO without `@jsonview`: generates code 100% identical to before

### Automatic inference rules (@jsonview(auto))

| Existing annotation | Create | Update | Read | Basis of inference |
|----------------|:------:|:------:|:----:|----------|
| `@id @genid` | ❌ | ✅ | ✅ | auto-generated ID |
| `@id @sequence` | ❌ | ✅ | ✅ | DB sequence ID |
| `@id` (manual) | ✅ | ✅ | ✅ | user-assigned ID |
| `@updatable(false)` | ✅ | ❌ | ✅ | non-updatable field |
| `@jpa:CreatedDate` | ❌ | ❌ | ✅ | auto-injected field |
| `@jpa:LastModifiedDate` | ❌ | ❌ | ✅ | auto-injected field |
| `@jpa:Version` | ❌ | ❌ | ✅ | optimistic locking |
| `@formula(...)` | ❌ | ❌ | ✅ | calculated field |
| `@readonly` / `@noinput` | ❌ | ❌ | ✅ | field set by the server (§2.2) |
| `@writeonly` | ✅ | ✅ | ❌ | write only |
| plain field | ✅ | ✅ | ✅ | default |

### env.yml setting

> **Since: 2.1**

```yaml
elasticore:
  config:
    jsonview: auto  # false(default) | true | auto
```

- `false`: default. Disabled until you set `@jsonview` on an individual entity/dto
- `true`: manual mode. You must set `@view`
- `auto`: automatic mode for everything. Automatic inference is applied to all DTOs

### Generated code example

**DSL input**:
```yaml
dto:
  ProductDTO:
    meta: dto @template(Product) @jsonview(auto)
    fields:
      id: long @id @view(r)
      name: string(100) @view(cru)
      status: string(20) @view(ur)
      createdAt: datetime @view(r)
```

**Generated result (DTO)**:
```java
@JsonView(Views.Read.class)
private Long id;

@JsonView({Views.Create.class, Views.Update.class, Views.Read.class})
private String name;

@JsonView({Views.Update.class, Views.Read.class})
private String status;

@JsonView(Views.Read.class)
private LocalDateTime createdAt;

// a field without @view gets no @JsonView → shown in all Views
private BigDecimal price;
```

**Generated result (Controller)**:
```java
@JsonView(Views.Read.class)
@PostMapping
public ProductDTO create(@JsonView(Views.Create.class) @Valid @RequestBody ProductDTO inputDto) { ... }

@JsonView(Views.Read.class)
@PutMapping
public ResponseEntity<ProductDTO> update(@JsonView(Views.Update.class) @RequestBody ProductDTO updateDto) { ... }

@JsonView(Views.Read.class)
@GetMapping("/{id}")
public ResponseEntity<ProductDTO> findById(@PathVariable Long id) { ... }
```

### Notes
- **ElastiCORE 2.1 or later** is required (this covers `@jsonview`, `@view` and the env.yml `jsonview` setting)
- **You must set `spring.jackson.mapper.default-view-inclusion=true` explicitly in application.yml**
  - ⚠️ The Spring Boot default is **false** (Spring's `Jackson2ObjectMapperBuilder`
    disables `MapperFeature.DEFAULT_VIEW_INCLUSION`)
  - When this is false and `@JsonView` is applied to a controller, every field without `@JsonView` is
    **dropped entirely** from the request/response, so this setting is required when you use the jsonview feature
  ```yaml
  # application.yml (required when you use the jsonview feature)
  spring:
    jackson:
      mapper:
        default-view-inclusion: true
  ```
- `@view` and the existing `@readonly`/`@writeonly` work independently
- springdoc-openapi v2.0+ (Spring Boot 3) or v1.7+ (Spring Boot 2) is required

---

## 5. Enumeration Annotations

### @db(field) / @json(field)
Sets the field stored in the DB and the field used for JSON serialization.
```yaml
enumeration:
  BoardType:
    meta: enum @db(code) @json(code)
    fields:
      code: string(2)
      name: string(20)
    values:
      NOTICE: { code: "NT", name: "Notice" }
      FREE: { code: "FR", name: "Free" }
```

---

## 6. Port Annotations

For details, see `elasticore-dsl-port-guide.md`.

### HTTP Port
```yaml
port:
  ExternalApi:
    meta: http @url("https://api.example.com") @auth(API-KEY)
    methods:
      getUser:
        meta: method @get("/users/{userId}")
      createUser:
        meta: method @post("/users")
```
Supported methods: `@get`, `@post`, `@put`, `@delete`, `@patch`

### DBMS Port
```yaml
port:
  BoardService:
    meta: dbms @datasource("main")
    methods:
      findByStatus:
        meta: method @query("SELECT b FROM Board b WHERE b.status = :status") @pageable
      deleteOld:
        meta: method @native("DELETE FROM board WHERE created < :date")
```

---

## 7. Best Practices

### Performance
- `@fetchjoin`: Use it on related entities that you always load together. It is excluded from count queries automatically.
- `table.autoJoin=true` (env.yml): Applies fetchjoin to every entity reference field automatically. No per-field setting is needed.
- `@cascade(ALL)`: Use it only for entities with the same lifecycle. For large collections, consider the performance impact.
- `@pageable` / `@expose` pageSize: An excessive value increases memory use.

### Choosing a search condition
- **Exact code/ID search**: `eq` or `=` (default)
- **Partial text search**: `like` (v3 mode ignores case, layered Java is case-sensitive), `%_%` (always case-sensitive), `%__%` (always case-insensitive)
- **Date range**: `between` (LocalDate automatically includes +1 day)
- **Multi-select filter**: `in`
- **Number comparison**: `>=`, `<=`, `>`, `<` (in v3 mode, `gte`, `lte`, `gt`, `lt` mean the same. 2.x compatibility mode accepts the symbols only)
- **Not equal**: `!=` or `ne` (v3 mode only)
- **Flexible search UI**: `auto` (like if the user types %, otherwise eq)

### Using DTO template
- With `@template(Entity)`, the entity's `string(n)` length is applied automatically as `@Size(max=n)`.
- An explicit `@size` takes precedence.
- Use `--` to exclude unneeded fields.
- You can combine several DTOs/entities to build a composite DTO.

### Indexes and search
- Set an `@Index` on fields that use `@search`.
- Only a prefix `like` (`_%`) can use an index. `%_` and `%_%` do a full scan.
- If a column used in `between` search has an index, the range scan is optimized.

### Using defaults
- `@table`: If not set, the entity name is converted to snake_case.
- `@Column`: If not set, the field name is converted to snake_case.
- `@search`: On an `@expose` entity, it is assigned automatically by type if not set.
- `@pageable`: Default pageSize=20.

---

## 8. Known Limitations & Workarounds

### 8.1 @min/@max annotation restrictions

The Jakarta Bean Validation annotations `@Min` and `@Max` accept only `long` arguments. A decimal value (0.0, 5.0, and so on) causes a compile error.

#### The problem
```yaml
# WRONG - decimal values cause compile errors
fields:
  rating: double @min(0.0) @max(5.0)   # Generates @Min(0.0) @Max(5.0) - compile error
  temperature: float @min(-273.15)     # Generates @Min(-273.15) - compile error
  price: bigdecimal @min(0.01)         # Generates @Min(0.01) - compile error
```

Generated Java code:
```java
@Min(0.0)  // Error: incompatible types: possible lossy conversion from double to long
@Max(5.0)  // Error: incompatible types: possible lossy conversion from double to long
private Double rating;
```

#### Correct usage
```yaml
# CORRECT - use integer values only
fields:
  rating: double @min(0) @max(5)        # Generates @Min(0) @Max(5) - valid
  temperature: float @min(-273)         # Generates @Min(-273) - valid
  price: bigdecimal @min(0)             # Generates @Min(0) - valid
```

Generated Java code:
```java
@Min(0)
@Max(5)
private Double rating;
```

#### Workarounds
1. **Use integer values**: Convert decimal values to integers (0.0 -> 0, 5.0 -> 5, -273.15 -> -273)
2. **Custom validation**: If you need a precise decimal range check, implement a custom Bean Validation annotation
3. **Service layer validation**: Validate complex business rules in the service layer

### 8.2 Words that are not DSL types (Invalid Field Types)

For a field type, use only the types in the "Value types at a glance" table of the DSL guide. A word that is not in the table
becomes a Java type name as it is (`private uuid id;`) and does not compile.

| What you want | How to write it | Note |
|-----------|--------|-----|
| UUID key | `string @id @genid` | `uuid` is not a type. If you give a length (`string(36)`), an ElastiCORE ID is made, not a UUID (see the `@genid` section above) |
| JSON document | `json` | `String` + `columnDefinition = "json"` |
| Large string | `clob` | `String` + `@Lob` (3.0 rule engine). The 2.x (`build`/`elcore`) does not add `@Lob`, so use `text` |
| Binary | `blob` | `byte[]` + `@Lob`, base64 in JSON (3.0 rule engine only) |

`json`, `blob` and `clob` are supported from 3.0.260925. In earlier versions, `private json metadata;` was generated
with the DSL word as it is and did not compile, so `text` was used instead. Only the 3.0 rule engine (`elcore3`) adds `@Lob`.
The generated result for each type follows the value types table in the DSL guide.

```yaml
entity:
  Product:
    fields:
      productId:  string @id @genid      # UUID
      metadata:   json
      attachment: blob
```

### 8.3 @ManyToOne @id fields and SQL reserved words

When a `@ManyToOne` field is used as an `@id` in a composite key, the field name becomes the SQL column name (`field_name_id`). If you use an SQL reserved word as the field name, table creation fails.

#### Problem reserved words
`order`, `group`, `select`, `from`, `where`, `table`, `index`, `key`, `value`, `user`, `date`, `time`, `timestamp`, `status`

#### The problem
```yaml
# WRONG - 'order' is a SQL reserved keyword
entity:
  OrderItem:
    meta: entity
    fields:
      order: Order @id @ManyToOne       # Generates column: order_id - reserved keyword
      itemSeq: int @id                  # SQL: CREATE TABLE fails

# Database error:
# Syntax error near 'order_id' - reserved keyword
```

#### Solution 1: Use a non-reserved field name (recommended)
```yaml
# RECOMMENDED - use non-reserved field names
entity:
  OrderItem:
    meta: entity
    fields:
      orderRef: Order @id @ManyToOne    # Generates column: order_ref_id - safe
      itemSeq: int @id

  UserProfile:
    meta: entity
    fields:
      userRef: User @id @ManyToOne      # Not 'user'
      profileSeq: int @id
```

#### Solution 2: Edit by hand after generation
```yaml
# If you must use reserved keyword, manually add @Column annotation after generation
entity:
  OrderItem:
    meta: entity
    fields:
      order: Order @id @ManyToOne       # Generate first
      itemSeq: int @id
```

Edit the generated Java code by hand:
```java
// Add quotes to escape reserved keyword
@Id
@ManyToOne
@Column(name = "\"order_id\"")  // Add this manually
private Order order;
```

### 8.4 @audited entities and relationships

When an `@audited` (Hibernate Envers) entity has a relationship with an entity that is not audited, the `@NotAudited` annotation is needed.

#### Automatic handling
The code generator adds `@NotAudited` automatically in these cases:
- `@ManyToOne`, `@OneToMany` and `@ManyToMany` fields of an `@audited` entity
- The referenced entity is not `@audited`

```yaml
entity:
  Order:
    meta: entity @audited
    fields:
      orderId: long @id @sequence
      customer: Customer @ManyToOne      # Customer is NOT @audited
      items: List<OrderItem> @OneToMany(mappedBy="order")  # OrderItem is NOT @audited

# Generated Java code (automatic):
# @NotAudited
# @ManyToOne
# private Customer customer;
#
# @NotAudited
# @OneToMany(mappedBy = "order")
# private List<OrderItem> items;
```

#### Manual declaration
If the automatic generation misses it, declare it in the YAML:
```yaml
fields:
  customer: Customer @ManyToOne @notaudited
  items: List<OrderItem> @OneToMany(mappedBy="order") @notaudited
```

#### What to verify
1. Check that the generated Java code compiles
2. Review all relationship fields of the `@audited` entity
3. Check that `@NotAudited` is present when a non-audited entity is referenced
4. If one is missing, regenerate from the YAML or edit the Java code by hand

### 8.5 Comparison keywords and not equal (depend on the mode)

**Only** the operators in section 3.2 are available. The comparison keywords and `!=` behave differently depending on the mode.

| What you wrote | v3 mode (layered Java by default, hexagonal, DDD, Kotlin) | Layered Java (`output = 'v2'`), 2.x |
|---|---|---|
| `gt`, `gte`/`ge`, `lt`, `lte`/`le` | Same as `>`, `>=`, `<`, `<=` (from 3.0.261027-rc2) | **Exact match** - no warning |
| `!=`, `ne`, `neq` | Not equal (`notEqual`) | **Exact match** - no warning |

To search for empty values (IS NULL / IS NOT NULL), use `@search(isnull)` alone (3.4.1).

```yaml
fields:
  price: int @search(>)      # all modes
  age: int @search(<=)       # all modes
  stock: int @search(gte)    # v3 mode: same as >=. 2.x compatibility mode: exact match
  code: string(10) @search(ne)  # v3 mode only
```

If you want the same result in every mode, use the symbols.

### 8.6 between search and the LocalDate +1 day behavior

In `@search(between)`, **only the LocalDate type** is handled specially. It adds +1 day to the To value automatically, so the whole last day is included.

```yaml
entity:
  Order:
    fields:
      orderDate: date @search(between)       # LocalDate - automatic +1 day to 'To'
      createDateTime: datetime @search(between)  # LocalDateTime - no +1 day
```

Generated Specification logic:
```java
// LocalDate: To+1 applied
if(orderDateFrom != null && orderDateTo != null) {
    LocalDate adjustedTo = orderDateTo.plusDays(1);  // +1 day
    return sp.and((r,q,c) -> c.between(r.get("orderDate"), orderDateFrom, adjustedTo));
}

// LocalDateTime: No adjustment
if(createDateTimeFrom != null && createDateTimeTo != null) {
    return sp.and((r,q,c) -> c.between(r.get("createDateTime"), createDateTimeFrom, createDateTimeTo));
}
```

#### Notes
- **LocalDate type**: A search from 2024-01-01 to 2024-01-31 automatically includes up to 2024-02-01 00:00:00
- **LocalDateTime type**: The input value is used as it is, with no +1 day
- **Number/other types**: The input value is used as it is

### 8.7 Summary of recommendations

1. **@min/@max**: Use integer values only. For decimal validation, use a custom annotation or the service layer
2. **DSL types**: Use only the types in the DSL guide's value types table. `uuid` is not a type (a UUID key is `string @id @genid`)
3. **Field names**: Avoid SQL reserved words, especially for composite key `@id` fields
4. **Search operators**: Use only the operators in 3.2. The symbols (`>`, `<=`) mean the same in every mode (the keywords and `!=` work in v3 mode only, 8.5)
5. **between + LocalDate**: Know that +1 day is applied automatically
6. **@audited**: After generation, you must verify `@NotAudited` on relationship fields
7. **Verify after generation**: Always compile and check for errors

## @root / @aggregate - aggregate boundary (DDD rule set)

In a project that uses the `ddd-aggregate` rule, you state whether an entity is an **aggregate root or a member**.
If you add neither, it is inferred from the model.

| Form | Meaning |
|---|---|
| `@root` | This entity is an **aggregate root** |
| `@aggregate(Order)` | It is a **member** of the `Order` aggregate |
| `@aggregate` (no value) | Same as `@root` |
| (none) | A member of the `@root` entity that holds it as a `@OneToMany` list or through `@cascade`; a root if nobody holds it |

**Most of the time you need to write nothing.** There are three rules.
1. If `@root` is present, it is a root.
2. An `X` that a `@root` entity holds as `List<X> @OneToMany(...)` is a member of that root (`@aggregate` is not needed).
3. An entity that nobody holds in a list is a root that stands alone.

Where `@root` is **required**: an entity that another root holds in a list but that is also a root itself
(`Order` in `Customer.orders: List<Order>`). Where `@aggregate(X)` is required: when two roots hold the same entity in a
list and the ownership is ambiguous.

Each annotation **says only one thing.** You can use them together -
as in `@root @aggregate(Order)`, you can write what the entity is and which aggregate it leads
at the same time, and the two do not contradict each other (`@root` wins).

### Why inference alone is not enough

`@cascade` is a **persistence decision**, not a domain decision. It usually matches
the aggregate boundary, but sometimes it does not.

```yaml
  # Two aggregates saved together, but each is its own root
  CustomerProfile:
    meta: entity @service @root           # a root even if held by cascade

  # A member whose lifecycle is managed in code, without JPA cascade
  OrderNote:
    meta: entity @service @aggregate(Order)
```

### Effect on generated output

A member **does not get a repository / service / controller / service_test.**
An aggregate is loaded and saved through its root, and you reach a member by navigating from the root.
`RepositoryHelper` also injects only the repositories of roots.

A value object has no separate annotation - **`@embeddable` already means that**
(it has no identifier of its own and is stored inline in its owner). For details,
see `elasticore-rule-guide.md`.

## domainService - domain service (DDD rule set)

**This is a DSL section, not an annotation.** The operation belongs to no entity,
so there is no place for it on an entity's meta line.

```yaml
# blueprint/<domain>/model/domainservice.yml
domainService:

  Pricing:
    desc: What an order costs once the products it names are priced
    uses: [Order, Product]
```

| Key | Required | Description |
|---|---|---|
| `uses` | Yes | The aggregates this operation spans. They are injected as domain ports into the regenerated Base |
| `desc` | No | Class comment |

If `uses` is empty or has only one entry, a `DSL_WARN` is raised (the build continues). An operation on a single aggregate
is usually a method of that aggregate.

### Generated output

```java
public abstract class PricingDomainServiceBase {     // regenerated every time - follows changes to uses

	@Autowired
	protected OrderRepositoryPort orderRepositoryPort;

	@Autowired
	protected ProductRepositoryPort productRepositoryPort;
}

@Service
public class PricingDomainService extends PricingDomainServiceBase {
	// Put the operations here. It is generate-once, so this file is yours and your edits stay.
}
```

If you add an aggregate to `uses`, the Base gets a port, and you use it without editing your class.

`namespace.domainService` in `env.yml` sets the location. If omitted, it is the same
package as the entities.

## @behavior - entity behavior (DDD rule set)

Creates **a place for business methods** on an entity. The `ddd-behavior` rule (included in the `ddd-springboot` /
`hexagonal-springboot` and Kotlin bundles) handles it. In the DDD bundles, **a root is split by default
without writing anything** - use `@behavior` in meta to split an entity that is not a root or to make the intent explicit.

| Where | Parameters | Default | Effect |
|---|---|---|---|
| Entity meta | none | A root is split; with `@editable` it is not split | `XxxBase` (@MappedSuperclass, regenerated) + `Xxx` (@Entity, generate-once) |
| Field | none | Locked (accepted only at creation) | setter `protected`, the mapper does not use it, DTO `READ_ONLY` |

A field `@behavior` has an effect **only on a root that is split**. On an entity that is not split, such as an `@editable` root or a member,
it leaves only a DSL warning and the field can still be used as is in a request. To get a "server value that is not accepted from a request"
on any entity, use `@readonly` ([§2.2 @readonly / @noinput](#readonly--noinput---a-field-the-server-decides)).

## @editable - fields and roots edited by a request (DDD rule set)

In the DDD bundles, the fields of a root are **locked** by default: a request gives values only when it creates the root, and after that
the root's methods change them (a PUT that tries to change a locked value gets 422). `@editable` opens this.

| Where | Parameters | Effect |
|---|---|---|
| Field | none | Public setter, can be changed by PUT |
| Entity meta | none | The whole root is plain data - not split, and every field gets a public setter |

```yaml
Item:                                   # master data: field-level editing is its natural form
  meta: entity @root @editable @expose(20)

SalesOrder:
  meta: entity @root @expose(20)
  fields:
    orderNo: string(20)!                # locked: only at creation, then by methods
    memo: string(500) @editable         # by PUT at any time
```

Use cases:
- **Master data**: items, customers, codes - meta `@editable`
- **Supplementary information such as memos and contacts on a business root** - field `@editable`
- **Draft editing**: order lines changed by requests until confirmed - collection `@editable` (+ a state check in the service)

```yaml
SalesOrder:
  meta: entity @root @behavior @event(Confirmed, Cancelled)
  fields:
    status: OrderStatus @behavior
```

Generated result (Java):

```java
// SalesOrderBase.java - regenerated
@MappedSuperclass
@EntityListeners({AuditingEntityListener.class, SalesOrderInvariants.class})
public abstract class SalesOrderBase implements java.io.Serializable {
    @Setter(AccessLevel.PROTECTED)
    private OrderStatus status;
    public void raiseConfirmed() { ... }
}

// SalesOrder.java - owned by the developer after the first generation
@Entity
@Table(name = "erp_sales_order")
@DynamicUpdate
public class SalesOrder extends SalesOrderBase {
    public void confirm() { ... }
}
```

Use cases:
- **A root with state transitions**, such as orders, approvals and deliveries: put the transition rules in entity methods (`order.confirm()`)
- **Calculated fields**: the root keeps its own total up to date with `recalculate()`
- **A slimmer service**: the application service only reads, saves and handles transactions, and the rules live in the entity

## @invariant - invariant (DDD rule set)

> **Where to use it?** Write a rule about a single field on the field - `unitPrice: money(12.2)! @min(0.01, 'unit price must be greater than 0')`,
> and a collection size as `lines: ... @minsize(1, '...')`. In the DDD bundles, the field rules of a root become invariants that are also checked at save time.
> You need `@invariant` only for a **rule that looks at several fields together**:
> - Comparing two fields: `startDate <= endDate`, `discount <= totalAmount`
> - Required depending on another field's value: if the status is shipped, the ship date is required
> - A rule that looks at the members as a whole: the sum of line amounts = the total, no duplicate items
>
> Put a rule that the condition syntax does not support (such as comparing two fields) in a method of the behavior class or in a domain service.

Declares what the aggregate root has **promised to keep always true**.
It works in a project that has the `ddd-invariant` rule (included in the `ddd-springboot` / `hexagonal-springboot`
bundles).

```yaml
  Order:
    meta: entity @root @expose(30) @invariant(items.size > 0, 'an order has at least one line')
```

You can write it several times - each declaration is a separate promise.

| Parameter | Required | Description |
|---|---|---|
| Condition | Yes | The condition syntax below |
| Message | No | The sentence used when it is violated. Single quotes. If omitted, the condition is used as it is |

### Generated output

An `OrderInvariants` listener class is created and registered in the root's `@EntityListeners`.

```java
@EntityListeners({AuditingEntityListener.class, OrderInvariants.class})
public class Order extends AuditEntity { ... }
```

```java
public class OrderInvariants {
    @PrePersist
    @PreUpdate
    void check(Order order) {
        if (order == null) return;
        if (order.getItems() == null || order.getItems().size() <= 0) {
            throw new IllegalStateException("an order has at least one line");
        }
    }
}
```

**Why not a method of the root**: JPA allows only one lifecycle callback per class,
and a root that uses `@genid` already uses `@PrePersist`. With two,
it is not a warning - the persistence unit does not start at all.

**Deleting a member is checked too**: If you delete only the last member, the root's row does not change, but the `OrderItemRemovalCheck` listener attached to the member
checks the root's invariants against the remaining state when the member row is deleted (flush) -
a save that deletes the last item is refused. Replacing everything with `replaceItems` is
allowed because it is already filled at flush time, and deleting the root itself is also allowed. If the member has no field that points to the root (a one-way relationship), this check
cannot be made and the build raises a DSL warning - map the relationship as two-way (`mappedBy`).

## @factory - factory (DDD rule set)

Declares **the place that creates** an aggregate. A root with invariants
cannot be created by setting its fields one by one.

```yaml
  Order:
    meta: entity @root @invariant(items.size > 0, '...') @factory(place)
```

### Generated output

An `OrderFactory.place(...)` stub is created, and the file is **generate-once** -
the body belongs to the developer, so your edits stay. Until you write the body, the signature follows the root's required fields; if the required fields
change after you have written it, the file is left as it is and you are told to move the change by hand (`KEPT(yours ...)`).

```java
public static Order place(LocalDateTime orderDate) {
    throw new UnsupportedOperationException(
            "OrderFactory.place is where a valid Order is made; write it.");
}
```

The signature holds the root's required fields. The generated identifier (`@genid`) and collections are left out -
a factory that is handed the identifier before creating anything has created nothing, and members go in
through the root's add method.

## @specification - specification (DDD rule set)

Declares a domain rule worth a name.

```yaml
  Order:
    meta: entity @root @specification(highValue, totalAmount > 100) @specification(hasLines, items notEmpty)
```

| Parameter | Required | Description |
|---|---|---|
| Name | Yes | The name of the generated method. Before the first comma |
| Condition | Yes | The condition syntax below |

### Generated output

```java
OrderSpecifications.highValue()                          // Predicate<Order>
OrderSpecifications.highValue().and(OrderSpecifications.hasLines())
```

It is a `Predicate`, not a boolean, because **combining is the point of this pattern**.
You select from what has already been read, not by a query.

### In TypeScript

With the `ts-model` bundle, the same declaration also produces a file for the browser.

```ts
// OrderSpecifications.ts
export function highValue(order: OrderDTO | null | undefined): boolean {
    return order != null && (order.totalAmount != null
            && Number(order.totalAmount) > 100);
}

// OrderInvariants.ts - a pre-check, not enforcement
export function violations(order: OrderDTO | null | undefined): string[]
export function isValid(order: OrderDTO | null | undefined): boolean
```

`Number(...)` compares correctly whether the server sends a decimal as a JSON number (the Jackson default) or as a string.
A hand-written `order.totalAmount > 100` is false regardless of the amount on a server that sends strings.
The TS type declaration is `number`, to match the default serialization.

## Condition syntax (common to `@invariant` and `@specification`)

| Form | Meaning | Example |
|---|---|---|
| `field.size > n` | Number of to-many members | `items.size > 0` |
| `field > n` / `field < n` | Number comparison (`Integer`/`Long`/`Double`/`BigDecimal` and so on) | `totalAmount > 100` |
| `field notNull` | Is set | `notes notNull` |
| `field notEmpty` | A to-many is not empty | `items notEmpty` |

It accepts only fields, comparisons and literals. The intent is not to build an arbitrary expression parser.

> **`>=`, `<=` and `==` are not available.** The DSL annotation parser reads an `=` inside an argument
> as the start of a key/value list. `:` is not available either - the meta line is a YAML scalar, so
> `name: ` is read as the start of a mapping and the file fails to load.

A condition that cannot be read is **dropped silently** (the build continues). This is so that
one typo does not block the generation of the whole domain - if the generated file lacks the check,
check your condition.

## @access / @owner - who can do what (v3)

In the entity meta, write the allowed level per operation and the field that holds the owner.

```yaml
PurchaseOrder:
  meta: entity @root @expose @owner(customerId)
        @access(read=owner, create=user, cancel=owner)   # ship and delete are not listed -> admin
```

| Level | Who |
|---|---|
| `public` | Anyone (no sign-in needed) |
| `user` | A signed-in user |
| `owner` | The owner of the row (`@owner` field = login ID) and the admin |
| `admin` | The admin (role `ADMIN`, change it with `config: adminRole:` in `env.yml`) |
| `none` | Nobody (the application itself can) |

Operation names: `read` (get and search), `create`, `update`, `delete`, and command names (`ship`, `cancel`...).
**An operation that is not listed is `admin`** (set a default with `*=user`). Without `@access`, nothing is checked.

Generated behavior (CoreService):

| Situation | Result |
|---|---|
| Not signed in + `user`/`owner`/`admin` | 401 `Sign in first` |
| No permission | 403 |
| Someone else's row in an `owner` get, update, delete or command | 404 (does not reveal that the row exists) |
| `owner` search | Adds an **equality condition** (`=`) on the owner field to the search - only your own rows come back (regardless of the field's `@search` mode) |
| `create` | Overwrites the owner field with the login ID - a value sent by the visitor is erased too (the admin's request value is kept as it is). If the owner is the primary key and it is not generated, the primary key becomes the login ID. If the owner field is `@readonly` or `@noinput` (a value the request cannot send), the signed-in person is the owner, the admin included - inside `runAsSystem` it is left alone |
| `update` | The owner field and `@access(admin)` fields take the **row's current value** (the sent value is ignored, and they are not erased even with `@dynamicupdate(false)`). A `@readonly` owner stays as it is even when the admin updates |
| `findAll`, conditional delete and conditional update at `owner` | Admin only (rows cannot be checked one by one) |
| `owner` level but `@owner` does not resolve | Build warning (DSL_WARN) + admin only at run time (search is 403) |
| A call that is not signed in (including outside a request, WebFlux, gRPC, `@Async`) | **Visitor** - fail-closed |
| Inside `CurrentUser.runAsSystem(() -> ...)` | The application itself - everything is allowed (start-up data, batches) |

```java
// Create the admin account at start-up - state explicitly that it runs as the application itself
CurrentUser.runAsSystem(() -> customers.save(admin));
```

The **whole row** of another entity (a nested DTO) is carried in a DTO only when that entity's read level is `public`.
If an order DTO carried a whole customer with `read=owner`, the customer data would be visible to whoever reads the order, so the build
drops that field and warns (the identifier `customerId` stays).

The build reports `@access` syntax errors (`read=owners`, `@access(admin)` on an entity) as warnings - if you leave them,
the application does not start. In the rule engine (`elcore3`), with a bundle that has no `jpa-service-access` (such as the 2.x-compatible bundle),
using `@access`, `@owner` or `@password` checks and hashes nothing, so the build warns.
**The 2.x generator (`elcore`, the annotation processor) does not handle these annotations** - use the 3.0 rule engine.

The generated service test (`testCode: true`) adds `RunAsSystemExtension` to an entity that has access rules, so it runs
as the application itself. A test that checks the permissions themselves signs in as a user and does not use this extension.

Put `@access(admin)` on a field and it is a **field only the admin writes**. A value sent by anyone else is replaced by the entity
default (`@default`) on create and by the row's current value on update. This field has no default in the DTO, so when the admin edits only
other fields, a default does not come along and overwrite it. An owner field cannot be changed by an update either.

```yaml
Customer:
  meta: entity @root @editable @expose @owner(customerId) @access(read=owner, create=public, update=owner)
  fields:
    role: UserRole @default(UserRole.USER) @access(admin)   # a new user cannot make themselves admin
```

The sign-in API is **generated when you write `@signin` on the entity line** (session by default, from 3.0.261022-rc1). The entity has `@login` and
`@password` and is `@expose`d, and **the password and login of that row must be changeable only by the person themselves and the admin**
(`update=owner` with an `@owner` that points to the key, or `update=admin` or `none` - see "Update level" below):

```yaml
Customer:
  meta: entity @root @editable @expose(20) @signin @owner(customerId) @access(read=owner, create=public, update=owner)
```

| Request | Response |
|---|---|
| `POST /api/shop/customer/signin` `{"login": "...", "password": "..."}` (the keys are the names of the two fields) | 200 the customer (without the password), the session starts. If wrong, 401 `Wrong login or password` |
| `POST /api/shop/customer/signout` | 204, the session ends |
| `GET /api/shop/customer/me` | 200 the signed-in customer, 401 for a visitor. If the key is a string, a row whose key is `me` is answered by this endpoint (the signed-in person), not by `GET /{id}` |

`me()` is added to the CoreService and the use case, and `signIn(login, password)`, `signOut()` and `me()` (null for a
visitor) are added to the TypeScript client. It is a cookie session, so it works when the screen and the API have the same origin. Without `@signin` there is no `me()` either,
only `authenticate` (see "@password / @login" below).

The session holds the row's key and the **role**. The role is the value of the field marked `@role`, or, if there is none, of the field named `role`,
and **only an enum or string field with `@access(admin)`** is used (for an enum it is the constant name - `ADMIN` for `UserRole.ADMIN`, compared with
`adminRole`). If it is not an admin-only field, a new user could send `"role":"ADMIN"`, sign in and become the admin,
so the build gives a DSL warning and signs in without a role. Without a role field, everyone is an ordinary user.

- **Update level.** Every request that writes a row (`PUT`, `PUT /batch`, conditional update) asks for the `update` level - `update`, else `*`,
  else `admin`, and `public` if there is no `@access` at all. The sign-in API is generated only when this is `admin` or `none`, or `owner` with `@owner` being the key.
  If it is `public` or `user`, or `owner` on a field that is not the key, someone else could change the admin's password and sign in as the admin,
  so it is not generated, and a DSL warning gives the reason and the sentence to fix it (if there is no `@access`: `@owner(customerId)
  @access(read=owner, create=public, update=owner)`). An operation that is not listed in `@access` is `admin`, so
  when you first write `@access`, write sign-up (`create=public`) and read as well. `*` may be used together with other entries
  (the update in `@access(read=public, create=public, *=owner)` is `owner`). Even if another entity's DTO reads this entity with `@ref(member.login)` or
  `@ref(member.password)`, it cannot write through that path (see "@ref" above - the password is not even read).
- **If the request chooses the key, delete is also for the person themselves and the admin only.** If the key is not generated (not `@genid` or `@sequence`), someone could delete the `alice` row and
  sign up again as `alice`, and so own every row that `alice` owned. So it is generated only when the `delete` level (else `*`, else `admin`)
  is `admin` or `none`, or `owner` with an `@owner` that points to the key; otherwise it gives the `delete is user and its key handle is one
  the request chooses ...` DSL warning. You can also make the key generated.
- **The login is unique.** If the entity line has `@signin`, `unique = true` is put on the `@login` column - it is put on even when the conditions above stop the sign-in
  API from being generated, so fixing `@access` does not change the table (if it is already `@unique`, or the key, or
  has a unique `@Index` on that one column alone, it stays as it is). Signing up with an existing login or changing the login gives
  409, like any other constraint violation. If two rows have the same login, `authenticate` refuses both, so the database prevents
  someone from creating a row with another person's login to lock that person out. For a database already in operation, add the unique constraint with a migration (clean up duplicates first).
- **Only one `@signin` entity per application.** The session holds only the bare key, so with two, seller 7 would be read as customer 7. Within one
  generation, entities whose controllers are created in the same application are counted together: in a single-module project all domains are one
  application, and in multi-module (`elasticore.modules`) it is one per module where controllers are created (the module that the `module-name://` of `control` points to,
  else the module that holds the blueprint). If there are two or more, it is not generated anywhere in that application and a DSL warning is given.
  The generator cannot know whether two modules run together as one application, so for such modules write `@signin` on only one of them.
  If two or more modules each have a `@signin` entity, the build reports once `@signin is counted per module: Staff and Customer each sign in
  through their own module - if these modules run as one application, only one of them may say @signin.`
- **Another `@login`/`@password` entity in the same application.** If you sign in that entity by hand with `SessionCurrentUser.signIn`, its key is also
  the same session key, so `me()` and every owner check read it as the row with the same key in the `@signin` entity (staff 7 = customer 7). The build
  reports it with the `Customer: the session the generated sign-in starts holds only a bare Customer key, and Staff has @login and @password
  too ...` DSL warning. That entity signs in with its own `CurrentUser` bean (Spring Security etc.), or sign-in is done
  only with the `@signin` entity.
- If there is more than one key, or no `@expose`, or `@login` or `@password` is missing, it is not generated and a DSL warning is given.
- If the key is a string that the request chooses (not `@genid` or `@sequence`), a row whose key is `me` cannot be read with `GET /{id}` (`GET /me`
  answers with the signed-in person), so the build warns. Refuse `me` in `beforeSave`, or make the key generated.
- Sign-in rules such as how many wrong passwords to allow and whether to block a suspended account are written by overriding
  `authenticate(login, password)` in `CustomerService`, which the developer owns - the generated `signin` calls it.
- The controller is developer-owned, so **it is not added to a controller you edited.** When the build reports `... is yours and does not serve POST /signin,
  POST /signout, GET /me ...` as a DSL warning, delete that file and build (move your edits aside) to see the generated one, or
  copy in the three methods. This comparison reads the path variable name (`/{id}` and `/{customerId}`), a trailing `/`, the Kotlin array form
  (`@GetMapping(value = ["/me"])`), and `@RequestMapping(value = "/me", method = RequestMethod.GET)` as the same endpoint,
  and if there is a mapping it could not read, such as a path written as a constant (`@GetMapping(ME)`), it does not ask about the endpoints of that HTTP method.
  The 2.x-compatible bundle (`jpa-springboot`) has no TypeScript client, so it does not give this warning.
  Conversely, if an edited controller still has `POST /signin` or `GET /me`, which the DSL no longer generates (you removed `@signin`
  or it was refused for the reasons above), or puts a role into the session that is not from an admin-only field, the `... is yours and still
  serves POST /signin, GET /me ...` / `... still puts a role in the session ...` warning appears - because that sign-in keeps creating a session that the generated checks
  trust. Delete those methods or remove the role argument.

When you handle the session by hand (such as signing in right after sign-up):

```java
SessionCurrentUser.signIn(request, customer.getCustomerId(), customer.getRole().name());
SessionCurrentUser.signOut(request);
```

If you use Spring Security, register an `io.elasticore.springboot3.security.CurrentUser` bean and do not use `@signin`
(`authenticate` stays).

## @password / @login - credentials (v3)

```yaml
login:    string(10)! @login @unique
password: string(255)! @password      # long enough for the hash (PBKDF2 is about 100 characters)
```

- A password received in a request is stored **only as a hash, on every write path** (PBKDF2-SHA256 by default, replace it with a `PasswordHasher` bean).
- It does not appear in responses, searches or DTO copies (same as `@writeonly`), and is optional in TypeScript.
- If `@login` and `@password` are together, `authenticate(login, password)` is generated in the CoreService and the use case - it returns the row, or
  empty if there is no such login, two or more rows, or the password is wrong. A login that does not exist pays the same hash cost, so the response time does not reveal whether an account
  exists.
- The login must be unique: if two rows have the same login, `authenticate` refuses both, so if someone creates a row with another person's login,
  that person cannot sign in. If the login of an entity used without `@signin` is not unique (not `@unique`, the key, or a
  unique `@Index` on that one column alone), a DSL warning is given.
- If the entity line also has `@signin`, you get `me()`, the controller's `signin`, `signout` and `me` (the sign-in table in "@access / @owner" above), and
  the unique constraint on the login column (the unique constraint is added by `@signin` alone, even if the sign-in API is refused).

## @snapshot / @compute - derived values (DDD rule set)

```yaml
OrderLine:
  fields:
    item:     Item @ManyToOne
    quantity: quantity! @min(1)
    unitCost: money(10.2) @snapshot(item.unitCost)     # copy the price at the time of the order, once
    subTotal: money(12.2) @compute(unitCost * quantity)
PurchaseOrder:
  fields:
    totalWithoutVat: money(12.2) @compute(sum(lines.subTotal))
```

- Both are fields that a request cannot write (read-only in the DTO, the mapper does not write them - even in an `@editable` entity).
- If a `@compute` uses another `@compute`, declare that field first (the other way round gives a build warning).
- `@snapshot` fills the value only when it is empty - even if the product price changes later, the order line stays as it is.
  If the referenced row does not exist, 404 (`Item 99 does not exist`, code `reference.OrderLine.item`).
- `@compute` is recalculated on every save. If one of the operands is null, the result is null too.
- **It is different from `@formula`**: `@formula("...")` is a Hibernate SQL expression that the database calculates.

## @event - domain events (DDD rule set)

It declares **what can happen** to an aggregate root. It works in a project that has the `ddd-event` rule
(included in the `ddd-springboot` / `hexagonal-springboot` bundles).

```yaml
  Order:
    meta: entity @root @expose(30) @event(Placed, Cancelled)
```

### What is generated

| Generated | Content |
|---|---|
| `OrderPlacedEvent`, `OrderCancelledEvent` | An immutable value that holds the root's identifier + the declared content + `occurredOn` (the time it happened) (Java: equals/hashCode/toString, Kotlin: data class) |
| `order.raisePlaced()`, `order.raiseCancelled()` | Raise methods added to the root |
| `@DomainEvents` / `@AfterDomainEventPublication` | Accessors that Spring Data reads at save time |

```java
// In the extension service that the developer owns
order.raiseCancelled();
orderRepository.save(order);   // it is published here
```

- It is published **when saved**. If the save fails, nothing is published.
- It is cleared after publishing, so it is **not published again** on the next save.
- An event holds the **identifier**, not the aggregate. If you passed the object, you could write another aggregate through a transaction that has
  already ended.
- The event object is created at publish time. This is because a newly inserted row has no
  identifier yet when the event is raised.

### Event content (payload)

Write **fields of the root** in the parentheses after the event name, and the event carries those values.

```yaml
  SalesOrder:
    meta: entity @root @event(Confirmed(totalAmount), Shipped(orderNo, shipWarehouse, totalAmount), Cancelled)
```

- The value is the value at the time you **called** `raiseShipped()` (not at save time). Only the identifier is filled at publish time.
- The receiver (a listener, an Outbox consumer) does not need to read the sales order again.
- What it can carry: columns, enums, value objects. References to other aggregates and collections are dropped after a DSL warning
  (carry another aggregate by its identifier column).

### Sending outside - Outbox

If you put `outbox: true` in `env.yml`, the event is written to the Outbox table in the same transaction,
and the generated Relay sends it after commit to the application's `OutboxPublisher` (Kafka etc.).
For details, see `ddd-outbox` in `elasticore-rule-guide.md`.

### When it happens is not generated

That an order can be cancelled is in the model, but **what counts as a cancellation** is not in the model.
A raise call put in by guesswork would fire even on a row that was simply edited. So the root
provides `raiseCancelled()`, and the domain decides where to call it.

### Constraints

| Item | Content |
|---|---|
| Target | Aggregate **roots** only. Declarations on members are ignored |
| Language | Java, Kotlin |
| Location | Set with `namespace.event`. If absent, the same package as the entity |
