# ElastiCORE Environment Setup Guide (env.yml)

`env.yml` is the per-domain environment file of ElastiCORE. It defines the code generation mode, the framework, namespaces, annotations and more.
It lives in each domain directory (`src/main/resources/blueprint/{domain}/`). You describe the environment in it before you model with the DSL.

---

## 1. Basic structure

```yaml
elasticore:
  enable: true
  config:
    domainName: myDomain        # domain name (used for the generated package and file names)
    framework: springboot3      # framework (springboot3)
    mode: jpa                   # generation mode: jpa, mybatis, mongo, uml, dsl (several allowed, comma separated)
    j2ee: 17                    # Jakarta EE version (17 or higher)
  namespace:
    entity: com.example.domain.entity
    dto: com.example.domain.dto
    repository: com.example.domain.repository
    service: com.example.domain.service
    control: com.example.domain.controller
```

**Required settings:**
- `domainName`: the domain identifier. It is used for the package name of the generated code and for the API path
- `framework`: the target framework (`springboot3`)
- `mode`: the code generation mode
- `j2ee`: the Jakarta EE version (`17` or higher; javax is deprecated)

---

## 2. Generation modes in detail

### 2.1 JPA mode (default)

```yaml
config:
  mode: jpa
```

**Generated:** Entity, DTO, SearchDTO, Repository, Service, Controller, Mapper

**table setting:**
```yaml
config:
  table:
    prefix: "tbl_"        # table name prefix (@Table(name="tbl_xxx"))
    autoJoin: true         # automatic JOIN FETCH (default: false)
  tableSchema: "schema_a"   # DB schema (@Table(schema="schema_a")) - Since: 2.1.260409
```

- `prefix`: the table name prefix applied to the `@Table` annotation of the entity
- `tableSchema`: applies `@Table(schema="...")` to every entity in the domain. The `@schema` annotation on an entity takes precedence
- `autoJoin`:
  - `true`: applies JOIN FETCH to every entity reference field in a paged search (`findBySearch`) (prevents N+1)
  - `false` (default): applies JOIN FETCH only to fields with an explicit `@fetchjoin` annotation

**`@fetchjoin` vs `autoJoin`:**
- `@fetchjoin` is set on individual fields
- `autoJoin: true` applies to the whole domain at once
- You can use both together. With `autoJoin: true`, fields without `@fetchjoin` are JOIN FETCH targets too

**Additional JPA settings:**
```yaml
config:
  jpa:
    useQueryMethod: true   # generate custom query methods in the Repository
```

### 2.2 Multi-Mode

```yaml
config:
  mode: jpa,uml    # generate JPA code and a Mermaid UML diagram together
```

Separate modes with commas to run several at once. The output of every mode is generated.

> **The 3.0 rule engine (`elcore3` - the plugin default) writes `jpa` and `mybatis` code.** For a domain with neither
> (`mode: mongo` and so on), `elcore3` refuses and writes no source, so such a project uses the 2.x generator with
> `elasticore { output = 'v2' }` (the build calls `elcore`). `uml` and `dsl` listed with them are not written by
> `elcore3`; it only prints a warning - build that output with `./gradlew elcore`. If you list `jpa` and `mybatis` together,
> it writes JPA code (one domain has one storage).

### 2.3 MyBatis mode (3.0)

A domain that stores with MyBatis instead of JPA. **The model (DSL) stays the same; you change only the one word `mode`.** Since: 3.0.261030-rc5

```yaml
config:
  mode: mybatis
```

**What is generated** - DTOs, search DTOs, controllers, access checks and the mapper (`<Domain>Mapper`) are the same as in JPA. Only the storage side differs.

| File | Content |
|---|---|
| `<Entity>.java` | The entity - a plain class without JPA annotations. A to-one relation is the target class itself (`Customer customer`) |
| `<Entity>Repository.java` | The MyBatis mapper interface (`@Mapper`). The service calls it as `helper.getOrder()` |
| `<Entity>Repository.xml` | Its SQL - under `src/main/resources` at the same package path (where MyBatis finds it by itself) |
| `<Entity>CoreService.java` | A service with the **same method names and signatures** as the JPA version. The controller and the extension service are the same as the JPA version |
| `db/<domain>-schema.sql` | Tables, foreign keys and `@Index` - for H2. If the project DDL (section 9-1) is on, `db/schema-{db}.sql` from it replaces this file |

**Schema per DB** - the schema and migrations for PostgreSQL, Oracle and SQL Server are written by the project DDL (section 9-1, `elasticore { ddl {} }`).
The types are the same as the ones Hibernate picks for that DB. So the tables can be read together with tables made by JPA.
The generated SQL does not quote names, so a table or column name that is a reserved word in any DB (`level`, `date`, `user`, `order` ...)
produces a warning at generation - give another name with `@table` or `@column`.

| Model | H2 | PostgreSQL | Oracle (12c+) | SQL Server (2016+) |
|---|---|---|---|---|
| generated key (`@sequence`) | `GENERATED BY DEFAULT AS IDENTITY` | same | `GENERATED BY DEFAULT ON NULL AS IDENTITY` | `IDENTITY(1,1)` |
| `string(n)` | `VARCHAR(n)` | `VARCHAR(n)` | `VARCHAR2(n CHAR)` (`CLOB` above 4000) | `NVARCHAR(n)` (`NVARCHAR(MAX)` above 4000) |
| `text`, `clob` / `blob` | `CLOB` / `BLOB` | `TEXT` / `BYTEA` | `CLOB` / `BLOB` | `NVARCHAR(MAX)` / `VARBINARY(MAX)` |
| `long` / `int` | `BIGINT` / `INTEGER` | same | `NUMBER(19)` / `NUMBER(10)` | `BIGINT` / `INT` |
| `boolean` | `BOOLEAN` | `BOOLEAN` | `NUMBER(1)` | `BIT` |
| `decimal(p.s)`, `money(p.s)` | `DECIMAL(p,s)` | `NUMERIC(p,s)` | `NUMBER(p,s)` | `DECIMAL(p,s)` |
| `double` / `float` | `DOUBLE PRECISION` / `REAL` | same | `BINARY_DOUBLE` / `BINARY_FLOAT` | `FLOAT(53)` / `REAL` |
| `datetime`, `timestamp` / `time` | `TIMESTAMP` / `TIME` | `TIMESTAMP(6)` / `TIME` | `TIMESTAMP(6)` / `DATE` | `DATETIME2(6)` / `TIME` |
| enum (`@db(code)`) | `VARCHAR(code length)` | same | `VARCHAR2(code length CHAR)` | `VARCHAR(code length)` |

A SQL Server `UNIQUE` accepts only one NULL, so a `@unique` column that may be empty uses a unique index with `WHERE ... IS NOT NULL`
(other DBs keep plain `UNIQUE`).

**What the XML does**

- **One row + to-one relations**: every relation that has a key (`@ManyToOne` and so on) is read in one statement with a LEFT JOIN (one level). There is no
  lazy loading, so N+1 does not occur - a paged search is two statements, a count and the body. A joined row's relations come as keys only.
- **Search**: one `<if>` line per condition (`@search`) of the search DTO. It applies only when there is a value, and an empty string is not a condition (same as JPA).
  It uses `eq`, `like` (case-insensitive, partial match), `between` (From/To), `>`, `>=`, `<`, `<=`, `!=`, `isnull`, `_%` (prefix match) and `%_` (suffix match).
  `in` on a single-valued field is an equality comparison, as in JPA.
- **Sort**: only entity properties work (`amount`, `customer.name`). Any other value gives 400 - the characters of the request never enter the SQL.
  Without a sort, the order is the key order.
- **Paging**: H2, MySQL, MariaDB and PostgreSQL use `LIMIT/OFFSET`; Oracle (12c+), Tibero, SQL Server and DB2 use `OFFSET ... FETCH`.
  The runtime library tells MyBatis the DB type (`DatabaseIdProvider`) - there is nothing to set.
  (The `OFFSET ... FETCH` side was checked in H2's Oracle mode - the tests of `elasticore-template-mybatis`.)
  This DB type also applies to mappers the app writes itself: a statement with a `databaseId` is used on that DB. If you do not want that, set
  `elasticore.mybatis.database-id=false` - the generated mappers then page with `LIMIT/OFFSET`.
- **Write**: insert (the DB-generated key is returned), update of all columns (except `@updatable(false)` columns - they keep the first value), and
  delete by key. With `@jpa:Version`, the same UPDATE compares the version and adds 1 - a stale version gives 409.
- **enum**: stored with the same value as JPA (the code `N` with `@db(code)`, otherwise the name). Both ways can read the same table.
- **Money, Quantity**: `money(10.2)` is an amount (DECIMAL) and `quantity` is a count (INTEGER), one column each - the same values as the JPA converter.
  The XML uses `MoneyTypeHandler` and `QuantityTypeHandler` of the runtime library by name.
- **Value object (`@embeddable` + `@Embedded`)**: its fields are columns of the row (each with its own column name as in JPA; with `@embedded(work)` the name is prefixed,
  as in `work_street`). It is read from the row and from joined rows, and when all columns are empty the value is null. Sort and search
  are written like `homeAddress.city`. If the value is optional (no `!`), all its columns allow NULL.
  **The DTO carries the value object as it is** (`CustomerDTO.homeAddress`) - so that an address can be sent and received. (The JPA layered DTO does not
  carry it.)
- **Collection relation** (`lines: List<OrderLine> @OneToMany(mappedBy="order")`): it is read through the relation field of the target (`order`).
  - **Read**: for one row or one page, each collection is read with **one statement**, `IN (...)` over the keys of those rows, and distributed (500 keys at a time -
    the IN limit of Oracle). One page of orders plus their lines is three statements. The order is the key order of the target.
  - **Members of an aggregate** (an entity that is not `@root`, such as `OrderLine` with no service or API) are written together with the root: the root DTO
    carries the lines as `@dtype(List<OrderLineDTO>)` (`@Valid` - the `@min(1)` of a line is checked too), they are inserted together on save, an update request that
    holds lines deletes the old lines and writes the new ones, and deleting the root deletes the lines first (the cascade and orphanRemoval of JPA). A member gets
    only a mapper (`OrderLineRepository`), no service. An update that changes lines writes them anew, so a DB-generated line key is a new value each time
    (if another table points to the line key, that update gives 409). `lines: []` deletes all the lines (when validation allows).
    A collection inside a member (a sub-list of a line) is refused - a root works down to one level.
  - A collection of another root (`Category.products`) is read only - those rows are written by their own service. Even inside an aggregate,
    an entity that has its own service (`@expose`, `@service`) is written only through that service.
  - A large value (`text`, `clob`, `blob`, a string over 4000 characters) is not in the sort list, and a search on it allows only `like`, prefix and suffix match
    (Oracle cannot compare or sort a CLOB).
  - A collection without `mappedBy` (the join table of JPA), `@ManyToMany` and a value collection are refused.
- **Relation by id** (`@joinBy`): as in `customerId: long` and `customer: Customer @ManyToOne @joinBy(customerId)`,
  the row holds the id as a value and the relation only joins through that column. What you write is the id. This is the shape when you use `@owner(customerId)` in layered
  (hexagonal creates the two by itself).

- **The other side of a 1:1 relation** (`customer: Customer @OneToOne(mappedBy="vipProfile")`): the key is only on the other row (`Customer.vipProfile`),
  and this side joins through that column and only reads (one column, as in JPA).
- **`@abstract` parent (`@extend(AuditEntity)`)**: the parent's fields are columns of the row (the `@MappedSuperclass` of JPA). A parent that
  collects audit fields has this shape. A parent that is not `@abstract` (table inheritance) is refused. Since: 3.0.261030-rc6

**The service does what JPA did for you**: making the `@genid` key, stamping the time for `@jpa:CreatedDate` and `@jpa:LastModifiedDate`,
and putting the writer into `@jpa:CreatedBy` and `@jpa:LastModifiedBy` - the person the app's `AuditorAware` bean returns (the bean JPA auditing asks.
Null if there is none). Since: 3.0.261030-rc6

**Queries in repository.yml** - the mapper interface gets the names and signatures exactly as the DSL writes them, and the XML gets their statements.
Since: 3.0.261030-rc6
- **Queries read by name** (no `query`): the name is read like Spring Data - `find`, `count`, `exists`, `delete` + `By`, `First/TopN`,
  `Distinct`, `And`, `Or`, `Is`, `Not`, `LessThan(Equal)`, `GreaterThan(Equal)`, `Before`, `After`, `Between`, `IsNull`, `IsNotNull`, `Like`, `NotLike`,
  `StartingWith`, `EndingWith`, `Containing`, `NotContaining`, `In`, `NotIn`, `True`, `False`, `IgnoreCase`, `AllIgnoreCase`, `OrderBy...Asc/Desc`.
  A property is one of the row, the value object or a joined row (`findByCustomerName` is `customer.name`). With `pageable: true`, a `Page` is a count and one page.
  You may write only an order with no condition, as in `findAllByOrderBySkuDesc`. `Containing`, `StartingWith` and `EndingWith` search for `%` and `_` in the value
  as literal characters (as in JPA); `In` with an empty list returns no rows and `NotIn` returns all rows. `Stream` is refused (use `List`).
  In the root of a domain rule (DDD, hexagonal), `deleteBy...` reads the rows and deletes them one by one (table below).
- **SQL with `nativeQuery: true`**: it runs as written, and binds `:name` (or `?1`) to that parameter. If it returns an entity, the entity is
  read again by key and filled up to the joined rows (in the order the query gave), and if it returns a DTO, it is filled by column alias (`AS customerId`).
  `UPDATE`, `DELETE` and `INSERT` return the number of changed rows.
- **JPQL** (a `query` without `nativeQuery`) is refused - MyBatis runs SQL. Use `nativeQuery: true` and the SQL of the DB.

**dbms port** (`port: ... meta: dbms`) - in a MyBatis domain, ports are generated with MyBatis too: a mapper that holds the port's SQL
(`<Port>Mapper` + XML) and an adapter that implements the port (`<Port>Adapter`, a bean) are created in `infrastructure.persistence` (the repository package if there is none),
instead of a runtime proxy. The SQL is exactly what the port DSL says, `:name` becomes `#{}`, a trailing `/* if:name */` becomes `<if>` (when there is a value;
an empty string counts as none), and the block `/* if:name */ ... /* if-end */` works the same way. The result is converted by the same rules as the runtime (column aliases to
a DTO in camelCase, the first column for a single value - null if NULL, a row whose columns are all NULL becomes a DTO whose fields are all null). A port that uses
an expression (JEXL - `input.x > 3`) in `/* if: */` is refused - use a single name. A method with no answer (`void`) only runs the statement.
A page is a count and one page. The count removes the last `ORDER BY` of the SQL (SQL Server does not accept an `ORDER BY` inside a derived table) - if
`LIMIT`, `OFFSET` or `FETCH` follows, it is left as it is. A page of SQL without an order is cut with `ORDER BY (SELECT NULL)` on SQL Server
(it does not cut a page without an order) - so do not put the `ORDER BY` of a page inside an `/* if: */` line or block
(if the condition is false it fails on SQL Server). Putting a block inside a block (nesting) is
not supported, as in the runtime - the inner `/* if-end */` closes the outer block. Sorting uses `sortCode` and
`sortColumn` of the input, as in the runtime - the `Sort` of a `Pageable` is not read. Since: 3.0.261030-rc6

In an app without JPA (an app that uses only MyBatis), a dbms port that remains a runtime proxy runs with JDBC (`JdbcDbmsGateway` - the runtime
library registers it by itself). The JPQL of such a port is refused.

**Table and column names are the same as in JPA** - `@table`, `table.prefix`, `@column`; otherwise the name in snake case, and
a relation column is `field_targetKey` (`customer_customer_id`). It reads the tables you used with JPA as they are.

**Build** - put MyBatis and the Spring Data page types instead of JPA.

```gradle
dependencies {
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.5'   // Spring Boot 4: 4.0.0
    implementation 'org.springframework.data:spring-data-commons'                // Page, Pageable, Sort
    // spring-boot-starter-data-jpa is not needed
}
```

```properties
# create the tables from the generated schema (embedded DBs such as H2 run it by default; otherwise spring.sql.init.mode=always)
spring.sql.init.schema-locations=classpath:db/shop-schema.sql      # with the project DDL: classpath:db/schema-h2.sql
# for PostgreSQL / Oracle / SQL Server, the file of that DB written by the project DDL (section 9-1), or a Flyway migration
# spring.sql.init.schema-locations=classpath:db/schema-oracle.sql
```

The DB is verified by the tests of `elasticore-sample-petstore-mybatis` (the whole pet store) and `elasticore-template-mybatis` (every search condition and every column type)
on H2 and on real Oracle 23 Free, PostgreSQL 16 and SQL Server 2022 (the "Oracle, PostgreSQL and
SQL Server" job of CI).

**Queries you write yourself**: you may edit the statements of the generated XML (hints, conditions, sort ...) - your edits stay when the model changes (9-2
generated file merge). Write a **new statement** by creating one more mapper interface and XML (`OrderQueryMapper` + `OrderQueryMapper.xml`) and using it in the extension
service (`OrderService`) - adding statements and methods to the generated XML and mapper interface also survives the merge, but then
every model change has to merge in the same place as the generated code, so there are more places to conflict.

**What is still refused** (the build stops; nothing is dropped silently):
- a collection without `mappedBy`, `@ManyToMany`, a value collection (`List<String>`, `string[]`), a collection of an entity of another domain
- a value object inside a value object, or a relation inside one, two or more keys (`@IdClass`), a parent that is not `@abstract` (table inheritance),
  a type with no JDBC type (`UUID` and so on), a `@joinBy` that does not point to a value field of the row, a `@OneToOne(mappedBy)` whose other side does not hold the key
- a key made by a sequence or UUID (`@sequence(SEQUENCE)` and so on - IDENTITY only), a `@sequence` that is not on the key
- a `@jpa:CreatedDate` that is not a time type, `@audited` (the history of a row belongs to Hibernate Envers - MyBatis writes leave no history)
- a search path farther than the joined row (`@ref(shop.region.name)`) - because without it a conditional update or delete would reach all rows
- JPQL in repository.yml, a method whose name does not read as a query, a method that returns `Stream`, `deleteBy...` on a root that has members in
  layered (it would leave the members - domain rules delete them one by one), a `deleteBy` or `existsBy` with no condition
- in layered, a member without a service that the root holds by a to-one relation (`@OneToOne` or `@ManyToOne` + `@cascade`) - the layered service does not
  write it. Give that entity a service, or use domain rules (`architecture = 'ddd'` or `'hexagonal'`)
- a `publisher.rules` that picks the rule sets of the domain rules by hand and leaves out `mybatis-ddd`
- an entity of a JPA domain that points to an entity of a MyBatis domain (the reverse works), a project that also has a domain that keeps 2.x code,
  Kotlin (MyBatis code is Java)

#### DDD and hexagonal (`architecture = 'ddd'`, `'hexagonal'` + `mode: mybatis`)

The domain model and hexagonal are generated with MyBatis too (bundles `ddd-mybatis-springboot` and `hexagonal-mybatis-springboot` - chosen by itself from the build's
`architecture` and `mode`. If the domain env.yml has `ddd-springboot` or `hexagonal-springboot` in `publisher.rules`,
it is read as the MyBatis version). Since: 3.0.261030-rc6

The model is the same as the JPA version, and so is what is generated - the root and `<Root>Base`, members, value objects, domain events, invariants, factories,
specifications, commands (`commands:`), lifecycle (`states:`), `@snapshot` and `@compute`, domain services and process managers, ports
(`<Root>RepositoryPort`, `<Root>QueryPort`, `<Root>UseCase`), the outbox, architecture tests. The domain (`domain.model`) has no JPA or Spring
Data name at all (the JPA version gets this with `mapping: xml`; here you get it as it is).

The difference is the **repository adapter** (`<Root>RepositoryAdapter`). The adapter does, line by line, what JPA did by itself:

| What JPA did | The MyBatis adapter |
|---|---|
| `save()` decides whether the row is new | INSERT if there is no version (or no key), otherwise UPDATE |
| Dirty checking, `@Version` | UPDATE of all columns, with the version compared and increased in the same statement (409 if stale), and the in-memory version raised too - for the root and for members |
| Member cascade, orphanRemoval | Compares with the stored member rows - DELETE the missing, UPDATE the remaining, INSERT the new (member keys are kept) |
| To-one member cascade (`@OneToOne @cascade` and so on) | INSERT/UPDATE before the root (the root holds the key), DELETE the old member that changed or is gone (reads the stored row after clearing the session cache, to compare) |
| `delete()` | 409 before anything is deleted if the version is stale (locks the row), then DELETE in the order member collections -> row -> to-one members (the stored one and the in-memory one), and publishes the events raised in between |
| Derived `deleteBy...` | Reads the rows of the condition and deletes them one by one like `delete()` (delete guard, members) - like the derived delete of Spring Data JPA |
| `@PrePersist`, `@PreUpdate` (invariants), `@PreRemove`, `@PostLoad`, `@PostPersist` | The adapter calls the same moments of `io.elasticore.springboot3.domain.Lifecycle`, which the root implements |
| `@DomainEvents` publishing | Publishes with `ApplicationEventPublisher` after save or delete and clears them (once) |
| Auditing such as `@CreatedDate`, `@CreatedBy` | Puts the time and the person of `AuditorAware` on save - for members too (even if only the member has audit fields) |
| Persistence context (the same object within one transaction) | The MyBatis session returns the same object for the same query while there are no writes (a new object after a write) - the adapter fills the collections only of a row it sees first in the transaction, and calls `afterLoad` (it does not overwrite in-memory changes) |
| `EntityManager.find` (reference check, `@snapshot`) | The adapter is a `Lookup.Source` - the `Lookup` bean that the runtime registers finds it by type (the root of a JPA domain goes through the app's `EntityManager`) |
| Lazy loading | None - a row is read together with its joined rows, and collections are read one statement at a time per page |

The application service takes only ports (`<Root>RepositoryPort`, `<Root>QueryPort`, `Lookup`) - the constructor of the extension service
is those three too. The search port takes a search DTO instead of a `Specification` (`findAll(OrderSrchDTO, owner, Pageable)`).
A member of an aggregate has no service of its own even if you write `@service`, and the root uses it - both when you write it with `@aggregate(Order)` and when the model
makes it a member (held with `@cascade`, or held by an `@OneToMany(mappedBy=...)` of a `@root`). Only the mapper (`<Member>Repository`) is
generated, and only the root's adapter calls it.

If you pick rule sets by hand with `publisher.rules`, you must write `mybatis-ddd` together with `ddd-*` and `hex-*` (the rule set that
moves the JPA callbacks to `Lifecycle`) - without it the build stops and says why. If you write a bundle (`ddd-springboot`, `hexagonal-springboot`), it is added by itself.

The outbox (`outbox: true`) is written with a MyBatis mapper, and the relay takes the next row with a lock that depends on the DB - PostgreSQL and Oracle
`FOR UPDATE SKIP LOCKED`, SQL Server `UPDLOCK, READPAST`, H2 `FOR UPDATE`. The table goes into the project DDL.

`elasticore-template-ddd-mybatis` runs the model and tests of `elasticore-template-ddd` on MyBatis (H2, and in CI
Oracle, PostgreSQL and SQL Server).

A `@formula` field stays in the entity, but MyBatis does not read it, so its value is null, and it is left out of the search conditions (warning).

### 2.4 MongoDB mode

```yaml
config:
  mode: mongo
```

**Generated:** MongoDB Document entities, Spring Data MongoDB Repository, service classes

### 2.5 UML mode

```yaml
config:
  mode: uml
```

**Generated:** Mermaid diagrams, an entity relationship diagram as an HTML document

### 2.6 DSL mode

```yaml
config:
  mode: dsl
```

**Generated:** DSL-only code output (when you use only the model definition, without JPA)

---

## 3. supportAI setting

> **Deprecated** - `supportAI` will be removed in a later release, and a generation that uses it prints a warning. The tools are still generated as below. Give an agent the REST API (OpenAPI) instead: the tools repeat that API, and they put deletes by condition in a model's hands.

```yaml
config:
  supportAI: "true"    # use the AI-support service template (default: "false")
```

- With `"true"`, the extension service class is generated from `service_extend_ai.tmpl`
- With `"false"` (default), `service_extend.tmpl` is used
- It provides extension points for AI integration, and AI-related method templates are added to the service layer
- Spring AI `@Tool`/`@ToolParam` are put on the methods of the generated `XxxService`. Spring AI 1.x and 2.0 use the same annotations.
- A `@readonly` entity (or a method left out with `@restExclude`) gets read tools only (3.0 rule engine). A service with no write methods gets no write tools.
- For Spring Boot 4 + Spring AI 2.0, see 3-1 below.

---

## 3-1. Spring Boot 4 / Spring AI 2.0 (ElastiCORE 3.0)

ElastiCORE **3.0** supports both Spring Boot 3 and 4. 2.2 supports only Spring Boot 3.
Verified versions: Spring Boot 4.0.8, Spring AI 2.0.1, Hibernate 7.2, Spring Data JPA 4.0, Jackson 3.1.

### What to change in the project

```gradle
plugins {
    id 'org.springframework.boot' version '4.0.8'          // 4.x
    id 'io.spring.dependency-management' version '1.1.7'
    id 'io.elasticore' version '3.0.x'
}

dependencies {
    implementation 'io.elasticore.springboot4:elasticore-springboot4:3.0.x'   // instead of springboot3
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'      // Boot 4: the new name of starter-web
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3'  // 3.x for Boot 4

    // when you use supportAI
    implementation platform('org.springframework.ai:spring-ai-bom:2.0.1')
    implementation 'org.springframework.ai:spring-ai-model'

    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'  // MockMvc tests
}
```

- Gradle **8.14 or higher** (required by the Spring Boot 4 Gradle plugin)
- Kotlin **2.2 or higher**; the Jackson Kotlin module is `tools.jackson.module:jackson-module-kotlin`
- The Java package of `elasticore-springboot4` is still `io.elasticore.springboot3`. The imports of generated code do not change, so moving from Boot 3 to 4 is one dependency line.

### What ElastiCORE does for you

The plugin reads the version of the Spring Boot Gradle plugin applied to the build (declaring it with `apply false` only at the root is enough). With Boot 4, the generated code changes.

| Generated | Spring Boot 3 | Spring Boot 4 |
|---|---|---|
| enum (`@json`) | Jackson 2 `CustomSerializer`/`CustomDeserializer` | `@JsonValue` + `@JsonCreator` (`jackson-annotations`, common to Jackson 2 and 3) |
| outbox recorder (DDD) | Jackson 2 `ObjectMapper` | Jackson 3 `JsonMapper` (the one the app registered) |
| Kotlin (`language = 'kotlin'`) | unchanged | `T : Any` on the `Specification` type parameter, `!!` on `Page.map` (JSpecify declarations of Spring Data 4) |
| HTTP port client (runtime) | Jackson 2 codec | Jackson 3 codec (`elasticore-springboot4`) |
| Field rejected in a request body (`RequestShapeAdvice`, `typeMismatch` of the API error) | the path from Jackson 2 | the path from Jackson 3 (`elasticore-springboot4`; an app that put the web layer back to Jackson 2 gets the path from Jackson 2) - the response is the same |
| HTTP port (optional: `httpPort: spring`) | ElastiCORE runtime | an `@HttpExchange` interface that Spring implements + `@ImportHttpServices` (`elasticore-dsl-port-guide.md` 2.7) |
| entity, repository, service, controller | unchanged | unchanged (compiled and run on Hibernate 7 and Spring Data 4) |

**Why the enum has to change**: the web layer of Spring Boot 4 writes JSON with Jackson 3. Jackson 3 does not see the `@JsonSerialize`/`@JsonDeserialize` of Jackson 2, so an old enum goes out as the name (`"DRAFT"`) instead of the code (`"DRF"`), **with no error**. It compiles and starts, so you can tell that the API response changed only by a test.

The log prints the decided version at the top when you generate. **If it is a Boot 4 project and you see `Spring Boot: 3`**, enums go out as names, so set it by hand in one of the ways below:

```
[ElastiCORE] Spring Boot: 4.0.8 (build) - enums and the outbox are written for Jackson 3
```

| Where | How to write it | When |
|---|---|---|
| `build.gradle` | `elasticore { springBoot = '4' }` | You use the plugin but it is not detected (for example, only the BOM is used, without the Boot plugin) |
| `env.yml` | `elasticore: config: springBoot: 4` | When you generate without the plugin - the annotation processor (`./gradlew build`), a JavaExec task you made |
| JavaExec | `systemProperty 'elasticore.springBoot', '4'` | A generation task you made |

The build-side values (the first and third lines) take precedence over `env.yml`. A value that cannot be read as a number (`boot4`) prints a warning and generates for 3.

### What to fix yourself (project code)

For code that ElastiCORE did not generate, follow the Spring Boot 4 migration guide. What was actually fixed in the example project:

| Symptom | Fix |
|---|---|
| `package org.springframework.boot.test.autoconfigure.web.servlet does not exist` | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` + `spring-boot-starter-webmvc-test` |
| `No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper'` | `tools.jackson.databind.ObjectMapper` (Boot 4 registers a Jackson 3 mapper) |
| `JsonNode.asText()` warning | Jackson 3: `asString()` |

Even if you turn on `supportAI` later, `@Tool` is added to the developer-owned `XxxService` that was already generated: a file you did not touch is generated again, and a file you edited is merged if your edits do not overlap the changed lines (`MERGED(your edits kept)`, 9-2). If it stayed as `KEPT(yours ...)` because of an overlap, or you edited a file made by a version before the merge and it stayed as `SKIP(user-owned)`, add it by hand.

### Writing tools with Spring AI 2.0

Register the `@Tool` methods of the generated service:

```java
@Bean
ToolCallbackProvider erpTools(PartnerService partners, ItemService items) {
    return MethodToolCallbackProvider.builder().toolObjects(partners, items).build();
}
```

The arguments of a tool are read with Jackson 3, so with the enum change above, a tool call and the REST API use the same value (`"DRF"`).

---

## 4. Annotation settings

```yaml
config:
  annotations:
    # ID generator
    genid: "genid(io.elasticore.springboot3.util.IdUtils.newId)"

    # applied when generating the Entity code (@entity: prefix)
    stringarray: "@entity:Convert(converter = io.elasticore.springboot3.util.StringArrayConverter.class)"
    integerarray: "@entity:Convert(converter = io.elasticore.springboot3.util.IntegerArrayConverter.class)"

    # applied when generating the DTO code (@dto: prefix)
    email: "@dto:Email(message = 'Invalid email format')"
    phone: "@dto:Pattern(regexp = '^[0-9-]+$', message = 'Invalid phone format')"
```

**Scope prefixes of an annotation:**
- `@entity:` - applied only when generating the Entity class
- `@dto:` - applied only when generating the DTO class
- no prefix - applied to all generated code

**Use in a model file:**
```yaml
entity:
  User:
    fields:
      id: string @genid                  # generate the ID automatically
      tags: string[] @env:stringarray     # apply StringArrayConverter
      email: string @env:email            # Email validation annotation
```

The `@env:` prefix refers to an annotation defined in env.yml. It is replaced with the actual annotation code at build time.

**genid setting:**
- The `genid` key is handled specially: when an entity field uses `@genid`, the given ID generator method is registered in `@PrePersist`
- Form: `genid(package.Class.method)`

---

## 5. jsonview setting

```yaml
config:
  jsonview: auto  # false (default) | true | auto
```

- **Purpose**: control the visibility of DTO fields with Jackson @JsonView
- **Default**: `false`
- **Allowed values**: `false`, `true`, `auto`
- **Description**:
  - `false`: disabled (default). It is enabled only when you set `@jsonview` on an individual entity/dto
  - `true`: manual mode. DTO fields need the `@view` annotation
  - `auto`: automatic mode. Visibility is inferred from the existing annotations (@id, @genid, @jpa:CreatedDate and so on)

**Example**:
```yaml
elasticore:
  config:
    jsonview: auto
```

For details, see section 4.6 of `elasticore-annotation-guide.md`.

---

## 6. Template and test code settings

### 6.1 Custom templates

```yaml
config:
  template:
    entity: "custom/entity.tmpl"
    dto: "custom/dto.tmpl"
    service: "custom/service.tmpl"
    repository: "custom/repository.tmpl"
    port: "custom/port.tmpl"
    mapper: "custom/mapper.tmpl"
    enum: "custom/enum.tmpl"
    serviceExt: "custom/service_extend.tmpl"
    serviceTest: "custom/service_test.tmpl"
    templateHelper: "custom/repository_helper.tmpl"
```

You can set the path of a custom `.tmpl` file for each generation target. If you do not set it, the built-in template is used.

### 6.1.1 Transactional outbox

```yaml
config:
  outbox: true          # send domain events through an Outbox (default: false)
```

- In the DDD/hexagonal bundles, when there is a root that declares `@event`, each domain gets
  `<Domain>OutboxMessage` / `OutboxRepository` / `OutboxRecorder` / `OutboxRelay`
- The event is recorded in the **same transaction** as the change, and after the commit it is sent through the application's `OutboxPublisher` bean
- Send interval: `elasticore.outbox.relay-delay-ms` (default 5000; the app needs `@EnableScheduling`)
- Use cases: event integration between microservices (Kafka/RabbitMQ), webhooks to external systems, collecting audit logs

### 6.1.2 Removing JPA from the domain (`mapping: xml`)

```yaml
config:
  mapping: xml          # JPA mapping in META-INF/orm.xml (default: annotations)
```

- It keeps the hexagonal rule "the inside knows no technology": it **removes all** `jakarta.persistence` and Hibernate annotations from the domain classes (entity, Base, behavior class, embeddable,
  invariant listener), and writes the same mapping to
  `src/main/resources/META-INF/orm.xml` (a file JPA reads automatically - no setting needed).
- Dirty checking, `@Version`, lazy loading and lifecycle callbacks stay as they are (standard JPA XML).
- Also moved: the DB converter of an enum -> `XxxConverter` in the `persistence` namespace, the query meta class
  `Q` -> the `persistence` namespace. The proxy unwrapping in the member delete check uses the runtime `Proxies.real()`.
- It also removes `@Entity`/`@Table`/`@DynamicUpdate` from the developer-owned behavior class (`SalesOrder`) (the rest of the code stays).
- Hibernate-only features: `@DynamicUpdate` has no XML form, so it is dropped (UPDATE of all columns). `@Formula`, Envers
  (`@Audited`) and the like stay on the class, with a warning.
- **Remaining dependencies** (metadata level): the auditing annotations of Spring Data (`@CreatedDate` and so on), publishing of domain events
  (`@DomainEvents`), and the JSON code conversion of an enum (Jackson). The domain package has no `jakarta.persistence` or `org.hibernate`.
- **Kotlin**: the all-open/no-arg plugins that open classes work by looking at `@Entity`, so in this mode you must register the
  `@io.elasticore.springboot3.entity.PersistentModel` that is put on domain classes instead (otherwise the classes become
  final and lazy loading proxies do not work).
  ```groovy
  allOpen { annotation 'io.elasticore.springboot3.entity.PersistentModel' }
  noArg   { annotation 'io.elasticore.springboot3.entity.PersistentModel' }
  ```
- A domain that uses Envers (`@Audited`) keeps that annotation, so the generated architecture test allows `org.hibernate.envers`.
- If a `META-INF/orm.xml` made by the user exists, it is not overwritten; the mapping is written to `META-INF/elasticore-orm.xml` and
  the `spring.jpa.mapping-resources` setting is shown.
- Use cases: unit tests of the domain model without JPA, enforcing hexagonal rules with an architecture test (`archTest`), layer separation audits in regulated industries

### 6.1.3 Architecture test (`archTest: true`)

```yaml
config:
  archTest: true        # test the hexagonal boundaries with ArchUnit (default: false)
```

- It generates `src/test/java/<base package>/<Domain>ArchitectureTest.java` (`src/test/kotlin` for Kotlin) **once, the first time**.
  It reads the layers from the namespaces: domain (`entity`, `enumeration`, `event`, `domainService`, `domainRepository`),
  application (`service`, `dto`, `inboundPort`, `queryPort`), adapter (`control`), persistence (`persistence`, `repository`).
- Rules: the domain does not depend on outer layers / the application does not depend on adapter or persistence classes (only through ports) /
  the domain does not know the web / (with `mapping: xml`) the domain does not know `jakarta.persistence` or `org.hibernate`.
- It needs a test dependency: `testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'`
- Use cases: block layer violations in the build without code review, convey the boundaries to a new team member as code

### 6.1.4 Bounded context (`context:`, 3.0)

One blueprint domain is one bounded context. A domain with `context:` declares **what it takes from other contexts
(`uses`)** and **what other contexts may use (`publishes`)**, and the build guards that boundary.

```yaml
# blueprint/catalog/env.yml
config:
  context:
    publishes: [ProductDTO, Unit]   # what other contexts may use as a field type (DTO, enum, @embeddable)

# blueprint/shop/env.yml
config:
  context:
    uses: catalog                   # the upstream contexts this context relies on
    publishes: [OrderPlaced]        # events can be published too (root name + event name)
```

| Case | Result |
|---|---|
| A field of `shop` uses `ProductDTO` of `catalog` (both uses and publishes are present) | Passes |
| A field of `shop` uses the entity `Product` of `catalog` (`product: Product @ManyToOne`) | **Build fails**: an entity of another context cannot be a field type - hold only the identifier with `productId: long`, or use a DTO that `catalog` publishes |
| It uses a type of a context that is not in `uses` | **Build fails** (`... does not declare it (config.context.uses: catalog)`) |
| The other side declared `publishes` and the type is not in it (`publishes: []` publishes nothing) | **Build fails** (`... which catalog does not publish`) |
| The other side has `context:` but does not declare `publishes` | Passes if it is in `uses` |
| A process manager of another context receives an event with `on: [shop:OrderPlaced]` | The same check as for types: `shop` in the receiver's `uses`, and if `shop` declared `publishes`, `OrderPlaced` in it |
| `publishes` lists an entity or a name that does not exist | DSL warning (event names of that context are fine) |
| Neither side has `context:` | Same as before (names are looked up in all domains, and the DDD bundle only warns) |

- What is checked: the types of entity and DTO fields (including the X in `List<X>`, and those with a domain name such as `catalog:Product`), the DTO's
  `@template(...)`, the entity's `@extend(...)`, and the process manager's `on: [shop:OrderPlaced]` ("process manager" in
  `elasticore-dsl-guide.md`). If two domains have the same name, it cannot tell which one is meant, so it does not check
  (the loader warns separately). The 3.0 rule engine (`elcore3`) checks before generation, and on failure it reports all problems at once and then stops the build.
  The 2.x generator (`elcore`) does not read `context:`.
- **Context map**: if any domain has `context:`, `blueprint/context-map.md` is created (a Mermaid diagram -
  GitHub shows it as it is). Arrows go from the side that is used (upstream) to the side that uses it, labeled with the type and event names that cross.
  `catalog`, `shop` and `billing` of `elasticore-template-ddd` are the example (`billing` receives `shop:OrderPlaced`).
- Use cases:
  - Stop domains inside a monolith from tying entities together directly and breaking the boundary, in the build (less cost when you split out a service later)
  - Refuse at once the mistake of an AI pulling in an entity of another context when it edits the DSL
  - Connect contexts only by events (when an order comes in, the billing context issues a receipt) - what is published is
    left in the one `publishes` line
  - Match contexts to team or organization boundaries, and document the dependencies with `context-map.md`

### 6.2 Test code generation

```yaml
config:
  testCode: "true"      # generate test code automatically (default: "false")
```

- With `"true"`, JUnit 5 + Mockito service unit tests are generated
- Composite Key entities are handled correctly too
- Sample data is generated for every field type
- Complete test coverage for CRUD and search (List/Page) methods

The generated test files are placed under `src/test/java/` at the path of the service package.

---

## 6-2. Reference column name and admin role (3.0)

```yaml
config:
  referenceColumn: short   # column for a reference to another aggregate: customer_id (default: the 2.x style customer_customer_id)
  adminRole: ADMIN         # role name that the admin level of @access checks (default ADMIN)
```

- `referenceColumn: short` - makes the column name used by an identifier reference (`customerId`) of the DDD bundle the field name itself
  (`customer_id`). It is **for new projects**: the column names of an existing DB change, so a project
  moving from 2.x leaves it as it is or turns it on together with a migration.
- `adminRole` - if the roles put in at sign-in, as in `SessionCurrentUser.signIn(request, id, "ADMIN")`, include this name,
  the user is an admin. The `signin` generated by `@signin` on an entity line puts that value in when the `role` field (or the `@role` field) of that entity
  is an enum or string with `@access(admin)`.

## 7. apiPath setting

```yaml
config:
  apiPath: "/api/v1"    # Base API path of the controller (default: "/api")
```

It applies to the `@RequestMapping` path of the generated controller:
```java
@RequestMapping("/api/v1/{domainName}/{entityName}")
```

**Use cases:**
```yaml
# API versioning
apiPath: "/api/v1"
# Result: /api/v1/user/profile

# Separate domains
apiPath: "/api/admin"
# Result: /api/admin/user/role

# Microservice path
apiPath: "/services/order-api"
# Result: /services/order-api/order/create
```

**Notes:**
- If not set, the default `/api` applies
- It must start with a slash (`/`)
- It does not affect controllers that already exist (it applies on regeneration)

---

## 7-1. publisher.rules setting (3.0)

It decides how the same model is read. **If you do not write it, the behavior is the same as 2.x.**

```yaml
elasticore:
  config:
    publisher:
      rules: [ddd-springboot]
```

### Available bundles

| Bundle | Content |
|---|---|
| `jpa-springboot` | The existing layered structure (same as not writing it) |
| `ddd-springboot` | Aggregate boundaries, invariants, domain events, value objects |
| `hexagonal-springboot` | The above + inbound ports, hexagonal package layout |
| `jpa-springboot-kt` / `ddd-springboot-kt` / `hexagonal-springboot-kt` | The Kotlin versions of the three above |
| `ts-model` | Browser DTOs, enums, a typed REST client |
| `mybatis-springboot` | Layered Java on MyBatis (a domain with `mode: mybatis` gets it automatically - section 2.3) |
| `ddd-mybatis-springboot` / `hexagonal-mybatis-springboot` | The domain model / hexagonal on MyBatis (a domain whose `architecture` is ddd or hexagonal and which has `mode: mybatis` gets it automatically - section 2.3) |

A bundle is a list of names of individual rule sets, so you can also combine them yourself:

```yaml
      rules: [jpa-springboot-kt, ddd-aggregate, ddd-reference, ddd-value-object-kt]
```

### Priority (4 levels)

```
1. Domain    publisher.rules of this env.yml       <- when only this domain is an exception
2. Project   -Delasticore.publisher.rules=...      <- usually set once here
3. Built-in default jpa-springboot                 <- if you write nothing, this applies
```

A domain declares it only when it departs from the project default. Even with 20 domains,
you do not need to edit 20 `env.yml` files.

`publisher.suppress` behaves differently - it accumulates. The domain-level entries are added to the
project-level suppression list.

**To add your team's rules**, use the build setting that **adds** to the list above instead of replacing it.
Whichever bundle was chosen above, they are appended after it (not for TypeScript generation):

```gradle
elasticore {
    architecture = 'hexagonal'
    rules = ['com.acme.rules.AcmeDddRules']   // -Delasticore.publisher.rules.add=...
}
dependencies { elasticore project(':acme-rules') }
```

See the rules that are running now, and their order, with `./gradlew elcore3 -PlistRules`. For details, see `elasticore-ddd-customize-guide.md`.

### Warning: the rule engine reads this setting

**`publisher.rules` is not read by the `@ElastiCore` annotation processor.**
It is read by the rule engine, a separate entry point. If you only write the bundle, the file looks
configured, the build ignores it, and no warning appears.

For how to run it, see "Running the rule engine in your project" in
`elasticore-rule-guide.md`.

### The language is a separate switch

A bundle name only decides *which rules to use*. *Which tree to write to and with which extension*
is decided by the runner's system property:

| Language | Switch |
|---|---|
| Java (clean-up mode - the default from 3.0.261028-rc3) | none, or `elasticore.rules.v3` (to state that you chose it) |
| Java (compatibility - 2.x code) | `elasticore.rules.v2` (in the plugin, `output = 'v2'`) |
| Kotlin | `elasticore.rules.kt` |
| TypeScript | `elasticore.rules.ts` |

If you write only `rules: [jpa-springboot-kt]` and do not turn the switch on, no `.kt` files are
produced. The domain init wizard sets both together, so they do not drift apart.

The TypeScript client (`src/main/ts`) is a second output used together with the server code, so the Gradle plugin's `elcore3ts` task
writes it. `build` does not call that task, so to produce it with every build, write `elasticore { typescriptClient = true }`
(`elasticore-rule-guide.md` "TypeScript client"). If you did not write it but a generated client exists, `elcore3` warns.

---

## 8. Namespace setting

```yaml
namespace:
  entity: io.example.domain.entity           # JPA entity package
  dto: io.example.domain.dto                 # DTO class package
  enumeration: io.example.domain.enums       # Enum class package
  repository: io.example.domain.repository   # Repository package
  service: io.example.domain.service         # Service package
  control: io.example.domain.controller      # Controller package
  port: io.example.domain.port              # Transaction port package
  proto: io.example.proto                    # Protocol Buffer package
  event: io.example.domain.event             # Domain event package (ddd-event rule)
  domainService: io.example.domain.service   # Domain service package (ddd-domain-service rule)
  inboundPort: io.example.application.port.in          # Inbound port (UseCase)
  domainRepository: io.example.domain.repository       # Repository port (owned by the domain)
  queryPort: io.example.application.port.out           # Query port (application-layer search, default service)
  persistence: io.example.infrastructure.persistence   # Adapter + Spring Data
```

`event` is used only when there is an aggregate root that declares `@event(...)`. If you do not
declare one, the event classes are placed in the `entity` package.

`domainService` is used only in a domain that declares a `domainService:` section. If you do not
declare one, it is placed in the `entity` package. **Do not put it in the same package as `service` (the
application service)** - a domain service belongs to the domain and an application service is a layer above it, so putting
them in the same place breaks the dependency direction.

It is used in the `package` declaration and the `import` statements of each generated class.

**Recommended naming pattern:** `{base package}.{domain}.{type}`
```
io.company.rental.entity
io.company.rental.dto
io.company.rental.repository
```

### 8.0 In one line: `namespace: base`

```yaml
namespace:
  base: com.acme.erp
```

It fills in the hexagonal layout below under `com.acme.erp`. A key written together with it takes priority.

| Key | Package |
|---|---|
| `entity`, `enumeration` | `base.domain.model` |
| `event` / `domainService` / `domainRepository` | `base.domain.event` / `.domain.service` / `.domain.repository` |
| `service` / `dto` | `base.application` / `.application.dto` |
| `inboundPort` / `queryPort`, `port` | `base.application.port.in` / `.application.port.out` |
| `control` | `base.adapter.in.web` |
| `repository`, `persistence` | `base.infrastructure.persistence` |

### 8.1 Namespace presets by architecture style

A namespace only decides the package layout, so an architecture style (layered / DDD / hexagonal)
can be expressed **by setting alone, with no rule change**.

#### DDD layered (same as the elasticore-template-ddd sample)

```yaml
namespace:
  entity:      com.acme.shop.domain.model          # Domain model (entity, enum)
  enumeration: com.acme.shop.domain.model
  repository:  com.acme.shop.domain.repository     # Repository the domain requires
  service:     com.acme.shop.application           # Application service (transaction boundary)
  dto:         com.acme.shop.application.dto       # Input/output shapes of the application layer
  control:     com.acme.shop.interfaces.rest       # Inbound adapter
  port:        com.acme.shop.application.port.out  # Outbound port (see below)
  event:       com.acme.shop.domain.event          # Domain event (belongs to the domain)
  domainService: com.acme.shop.domain.service      # Domain service (also belongs to the domain)
  domainRepository: com.acme.shop.domain.repository        # Port owned by the domain
  queryPort:   com.acme.shop.application.port.out          # Search port of the application service
  repository:  com.acme.shop.infrastructure.persistence    # Spring Data goes in infrastructure
  persistence: com.acme.shop.infrastructure.persistence    # The adapter too
```

#### Hexagonal (ports & adapters)

```yaml
namespace:
  entity:           com.acme.shop.domain.model
  enumeration:      com.acme.shop.domain.model
  event:            com.acme.shop.domain.event
  domainService:    com.acme.shop.domain.service
  domainRepository: com.acme.shop.domain.repository            # <Root>RepositoryPort (owned by the domain)
  repository:       com.acme.shop.infrastructure.persistence   # Spring Data goes in infrastructure
  persistence:      com.acme.shop.infrastructure.persistence   # Adapter that implements the two ports
  service:          com.acme.shop.application
  dto:              com.acme.shop.application.dto
  inboundPort:      com.acme.shop.application.port.in          # <Root>UseCase
  queryPort:        com.acme.shop.application.port.out         # <Root>QueryPort (search/paging)
  port:             com.acme.shop.application.port.out         # driven port (port DSL)
  control:          com.acme.shop.adapter.in.web               # driving adapter
```

> This preset works with only `elasticore { architecture = 'hexagonal' }` in build.gradle -
> the `hexagonal-springboot` bundle applies even if you do not write `publisher.rules` in env.yml.
> If you leave out `domainRepository`, the domain port is placed in the same package as Spring Data
> and the domain comes to depend on infrastructure, so always separate it.


#### Why the port is placed on the application side

What ElastiCORE's `port:` generates is **the interface only**. The implementation (adapter) is made by a
runtime proxy (`HttpServiceProxyFactory`, `DbmsServiceProxyFactory`) and
there is no generated code. In hexagonal, the application owns the port interface and the adapter
belongs to infrastructure, so the interface, the only generated file,
belongs in `application.port.out`, not `infrastructure.port`.

#### Inbound ports are generated

If outbound ports are the `port:` DSL, inbound ports are made by the `hexagonal-springboot`
bundle: for each exposed aggregate root a `<Entity>UseCase` interface is created,
the extension service implements it, and the controller is injected with it instead of the implementation class.
The interface is placed in the `service` namespace, so in the preset above
`application` / `application.service` is that place.

Combined with the DDD rule set (`rules: [ddd-springboot]`), aggregate boundaries are
enforced as well, and `hexagonal-springboot` adds inbound ports on top. Kotlin uses
`ddd-springboot-kt` / `hexagonal-springboot-kt`. For details, see
`elasticore-template-ddd/README.md` and `elasticore-rule-guide.md`.

### 8.2 Cross-Module Namespace Redirect (multi-module only)

In a multi-module project, within one domain (env.yml) you can generate code into **different modules** per namespace.
Specify it by adding a prefix in the form `moduleName://packageName`.

```yaml
# Example: blueprint/account/env.yml of the core-domain module
namespace:
  # Generated in the current module (core-domain)
  entity:      com.acme.shop.domain.account.entity
  dto:         com.acme.shop.domain.account.dto
  repository:  com.acme.shop.domain.account.repository
  enumeration: com.acme.shop.domain.account.enums

  # Generated in the app-admin module (cross-module redirect)
  service:     app-admin://com.acme.shop.domain.account.service
  control:     app-admin://com.acme.shop.domain.account.control
  port:        app-admin://com.acme.shop.domain.account.port
```

**How it works:**
- A namespace without a prefix -> generated in `src/main/java/...` of the current module (the module where the blueprint is located)
- A namespace with a `moduleName://` prefix -> generated in `src/main/java/...` of that module
- The `package` declaration of the generated Java file uses only the plain package name (the prefix is removed)

**Conditions:**
- It works only in **multi-module mode**, where the `-Delasticore.modules=...` system property is set
  when you run `./gradlew elcore`
- `moduleName` must match the last directory name of a path passed in `elasticore.modules`
  (e.g. `C:/.../app-admin` -> module name `app-admin`)
- In a single-module project, the code is generated in the current module even with a prefix (backward compatible)

**Scenarios:**
- Keep the domain model (entity/DTO/repository) in the shared module (`core-domain`),
  and spread the business logic (service/controller) across application modules (`app-admin`, `app-batch`)
- In multi-tenant SaaS, deploy the shared schema in core and each application's API in its own module

**Gradle setting example (root build.gradle):**
```groovy
task elcore(type: JavaExec) {
    classpath = project(':core-domain').sourceSets.main.runtimeClasspath
    mainClass = 'io.elasticore.base.extract.ModelExtractor'

    def modules = ['core-domain', 'app-admin', 'app-batch']
    systemProperty 'elasticore.modules', modules.collect { "$projectDir/$it" }.join(',')
    systemProperty 'elasticore.base.path', "$projectDir"

    if (project.hasProperty('mode')) {
        systemProperty 'mode', project.property('mode')
    }
    standardInput = System.in
}
```

---

## 9. Datasource setting

```yaml
config:
  datasource:
    name: "orderEntityManagerFactory"   # EntityManagerFactory bean name
```

- In a multi-datasource environment, it specifies the particular EntityManagerFactory the domain uses
- Both the full name (`orderEntityManagerFactory`) and the short name without the suffix (`order`) work
- When `DbTransactionGateway` maps the bean name, it removes the `EntityManagerFactory` suffix automatically, so it recognizes the short name too

```yaml
# Using the full name
datasource:
  name: "orderEntityManagerFactory"

# Using the short name (works the same)
datasource:
  name: "order"
```

In a single-datasource environment, if you do not set it, the default `primary` EntityManagerFactory is used.

---

## 9-1. DDL - per-DB schema and migration (3.0)

A production DB is not created with `ddl-auto`. ElastiCORE writes **all tables of the project** (both JPA and MyBatis domains) as DDL
for each DB, and when the model changes it writes **only what changed as a Flyway migration**. Since: 3.0.261030-rc5

**Turn it on - once per project** (`build.gradle`, not per domain):

```gradle
elasticore {
    ddl {
        databases = ['postgresql', 'oracle']   // h2, postgresql, oracle, sqlserver (postgres, mssql also work)
        migration = 'flyway'                   // omitted or none: full schema only
    }
}
```

**What is produced** (`src/main/resources/`)

| File | Content |
|---|---|
| `db/schema-{db}.sql` | All tables, foreign keys, indexes, sequences. Regenerated on every build. Run it on an empty DB with `spring.sql.init.schema-locations` or hand it to the DBA |
| `db/migration/{db}/V{timestamp}__init.sql` | The first migration of that DB's history (the full schema at that time) |
| `db/migration/{db}/V{timestamp}__{what}.sql` | As much as the model changed - `add_customer_email`, `create_visit` ... |
| `db/migration/schema-state.json` | The schema the migrations produced. The next change is compared with this |

**Commit the migration files and the state file** - they are the history of the DB. Once a migration is written, it is not written again (Flyway checks
the checksum). You may edit it by hand - from then on it is the developer's file. `db/schema-*.sql` is generated, so you do not have to commit it.

```yaml
# application.yml - Spring Boot picks the folder by DB type ({vendor}: h2, postgresql, oracle, sqlserver)
spring:
  flyway:
    locations: classpath:db/migration/{vendor}
```
A project that already has a hand-written history in Spring Boot's default location (directly under `db/migration`) and has one DB does not
change `locations` - Flyway also reads folders below `classpath:db/migration`, so the migrations in `db/migration/{db}/` run
after that history. If you change it to `{vendor}`, Flyway cannot find the history it has already run (table below).

Dependencies: `spring-boot-starter-flyway` (Boot 4; for Boot 3 it is `org.flywaydb:flyway-core`) and the per-DB modules
(`flyway-database-postgresql`, `flyway-database-oracle`, `flyway-sqlserver`).

**What the migration writes**

| Change in the model | Migration |
|---|---|
| Entity added | `CREATE TABLE` + foreign keys + indexes (and the schema, if it is the first table of another schema) |
| Field added (optional) | `ADD COLUMN` |
| Field added (required `!`) | `ADD COLUMN` left empty; `NOT NULL` goes to **review** (give the existing rows a value first) |
| Length or precision increased | `ALTER COLUMN` - the syntax differs per DB (Oracle `MODIFY`; SQL Server drops the index briefly and recreates it) |
| Required -> optional | `DROP NOT NULL` |
| Relation, `@unique`, `@Index` added/removed | Foreign key or index added/removed |
| Field or entity removed, type changed or narrowed, optional -> required, key changed | **Review**: as a comment at the end of the file - what can lose data or fail on existing rows |

A change that needs review also appears in the build log as `REVIEW`. Uncomment it (or add SQL that moves the data) and commit.

**A build that would break the history fails** - it does not write that history's migration and state file, and it states the reason (`db/schema-*.sql` is regenerated as usual).

| Situation | What to do |
|---|---|
| There are migrations but no `schema-state.json` | Restore the state file from version control |
| You turn it on for the first time in a project that already has a hand-written Flyway history | Check that the DB equals the current model, then run `./gradlew elcore -PddlBaseline` once - it writes only the state with the current model, without a first migration. It is the same whether the history is in `db/migration/{db}/` or directly under Spring Boot's default location `db/migration` - if you turn it on without a baseline, the build stops (because a first migration written in a folder below it would recreate all tables) |
| More than one DB in the history in the default location | The build stops - `classpath:db/migration` reads all folders below it and runs the migrations of other DBs on one DB. Move the history to `db/migration/{db}/` for each DB, set `locations` to `classpath:db/migration/{vendor}`, then `-PddlBaseline` |
| `schema-state.json` cannot be read (merge conflict markers) | Two branches each wrote migrations. Keep both sets of migrations, take the later one's state, and build again |
| You put back a DB that you had removed from `databases` | Bring that DB folder's history up to the current model by hand, then add that DB to `databases` in the state file |
| A file with this build's version (`V{timestamp}__`) already exists | Build again (the timestamp changes) |

For a DB removed from `databases`, only a warning appears, and that DB's migrations stop there.

**Names taken over by `-PddlBaseline`** - the names of foreign keys and indexes in the state are the names the model assigns (`fk_{table}_{field}` etc.).
A DB built from a hand-written history may have other names - Hibernate names foreign keys with a hash (`FK` and a hash)
and treats `@Index(unique)` as a unique constraint. So the state that baseline wrote records those names as `takenOver`, and a statement that drops such a name, or
drops it and recreates it with the same name, is written as **review** - change it to the real name in the DB and uncomment it. In SQL Server, dropping and recreating that name while
changing a column is review only in the SQL Server migration; in other DBs the column change runs as it is.
Foreign keys and indexes that a migration creates after the takeover have the right names, so they run as they are. A DB added to `databases` after baseline
is also created by `init` with the model's names, so that DB's migrations run as they are (`takenOverOn` in the state lists the DBs that were taken over). A state started with `-PddlBaseline` before this feature (up to 3.0.261030-rc6)
has no taken-over names - when the DB equals the current model, delete `schema-state.json` and run
`./gradlew elcore -PddlBaseline` once more, and they are recorded.

**Different per domain** - override only what you need in that domain's `env.yml`.

```yaml
config:
  ddl: false                 # This domain is left out of the DDL (when it only reads tables managed by another system)
```
```yaml
config:
  ddl:
    separate: true           # Separate DB: db/{domain}/schema-{db}.sql, db/{domain}/migration/... separately
    databases: [oracle]      # The kind of that DB
    migration: none          # That domain gets the schema only
```
If it is not `separate`, all domains go into the project's one DB (one file, one history). A relation pointing to a table in another DB
cannot get a foreign key, so it is left out and a warning is given. You can also turn on only one domain with `ddl.databases`, without a global setting.

**The tables of a JPA domain** are read from the generated entity source (and the `orm.xml` of `mapping: xml`) - exactly as Hibernate reads them, so
entities edited by the developer and hand-written entities (outbox etc.) are included. Names follow Spring Boot's rules (snake case, relation column
`field_targetKey`, join table `ownerTable_property`), types are Hibernate's choice, and length and NOT NULL
reflect `@Size` and `@NotNull` too. It also writes the audit tables of Envers (`@Audited`), `revinfo` and `revinfo_seq`, and reads settings such as
`audit_table_suffix` in `application.yml`. In a project that keeps 2.x code (`output = 'v2'`), the `elcoreDdl` task writes after `elcore`.

Verification: CI checks every time that the four JPA samples of ElastiCORE (2.x, hexagonal, Kotlin, pet store) start with Hibernate
`ddl-auto=validate` on the tables created by this DDL (H2; the pet store also on PostgreSQL, Oracle and SQL Server), that the MyBatis pet store runs with Flyway on
the three DBs, and that the change migrations (ALTER) run on the three DBs.

**Limits**: Oracle has no `TEXT`, so for JPA `columnDefinition = "TEXT"` (DSL `text`) becomes CLOB on Oracle and Hibernate
validate refuses it (warning) - on Oracle, use `clob` or a length. Names are not quoted, so
a name that is a reserved word of the DB cannot be used (a MyBatis domain gets a warning).

---

## 9-2. Generated-file merge - follow the DSL while keeping your edits (3.0)

You may edit a generated file. The next build **keeps your edits and also applies the DSL change** - the way git merges two branches.
It does not matter whether you added a comment, added a statement, or changed a line. Since: 3.0.261030-rc5

**Scope**: all generated files - Java and Kotlin sources, MyBatis mapper XML, `orm.xml` (`mapping: xml`), `db/schema-{db}.sql`,
`{domain}-schema.sql`, `*-http-services.properties`, `i18n/*-messages.properties`, gRPC `.proto`, the TypeScript client (`.ts`).
The first-line marker (`//ecd:`) is not added - `<!DOCTYPE mapper ...>` or `package` stays as the first line. A Flyway migration is a history that is not written again
once written (9-1), and `blueprint/schema.yml` is rewritten every time. In a multi-module project, if a module is
outside the project folder, that module's files are handled by the first-line marker (`elasticore-rule-guide.md` 7.5).

**Java and Kotlin are merged as source**: imports are merged as a group, not by line - the imports the DSL added and the imports the developer added both
remain, and an import the developer removed stays removed. In a class, when both sides **only added** lines in the same place (a DSL field, a developer
method), both remain, the generated lines first and then the developer's lines; when one side changed a line and the other side only added next to it, the added line is placed
next to the changed line. Only when both sides each changed the same line is it a conflict.

**Generate-once files** (marked `// Initially generated by ElastiCORE;` - extension services, controllers, `@behavior` entities ...) are
the developer's code. Until you edit them, they follow the DSL; after you edit them, a DSL change comes in **only when it merges cleanly**
(`MERGED`) - e.g. if you add `@readonly`, the untouched write endpoints also disappear from the developer's controller, and endpoints the developer added
remain. If the DSL also changes a line the developer edited, it **leaves no conflict markers** and keeps the file as it is (`KEPT(yours ...)`), and
warns what to move by hand - developer code never breaks during a build. Leave the marker line as it is.

**Turn it on**: it is on by default for 3.0 code. To turn it off, `elasticore { merge = false }` - from then on a file without a marker 
is treated as the developer's own and skipped (if you delete it, it is generated anew with the marker). `output = 'v2'` (2.x code) is off by default - exactly as 2.x used it.

**`.elasticore/cache` - commit it together with the generated files.** It is one file (an uncompressed zip) holding, per file, exactly what the generator wrote last (before merging).
It is the base of the merge, so for a file edited by someone who cloned (even with `--depth 1`) to be merged, it must be in the repository. If the content is
the same, the bytes are the same, so the repository grows only as much as the generated output changed. The `.elasticore/.gitattributes` next to it marks it binary.
**The cache and the generated files travel together** - commit both, or, in a project that keeps generated files out of git, leave out `.elasticore/` too.
If you commit only the cache, after a pull the old local generated file looks like an "edited file" and is not rewritten.

| Case | What the build does |
|---|---|
| First generation | Writes it and puts it in the cache |
| Unedited file, DSL changed | Writes it anew |
| Edited file, DSL unchanged | Leaves it as it is - `UNCHANGED(your edits kept)` |
| Edited file, DSL changed | Merges - `MERGED(your edits kept)` |
| The DSL also changed a line you edited | **Conflict** - leaves `<<<<<<< yours` / `=======` / `>>>>>>> elasticore` in the file and the build fails. It keeps failing until you clean up the markers, and what you cleaned up becomes the base from then on |
| You deleted the file | Generated again - the way to get it back as generated |
| The DSL no longer produces it | If unedited, it is deleted; if edited, it is kept with a warning (from then on it is the developer's file). Even if unedited, a class that remaining sources still use (and the mapper XML with the same name) is kept and the file that uses it is reported (`still uses it - kept`) - it is deleted in the build after nothing uses it any more |
| A different file where the cache has none | Not overwritten, reported (if the cache file is missing, the summary tells you to fetch it from git) |
| A file generated with a first-line marker (older version, 2.x) | "Projects that had markers" below |

**Projects that had markers** (files that an earlier version wrote with the first line `//ecd:` - including projects upgraded from 2.x): there is no separate command to migrate.
When the merging build handles such a file for the first time, it removes the marker and puts the file in the cache **file by file** - but only when that file's original generated version (the base)
**can be confirmed by the marker's hash**. If it merged with a guessed base, unrelated changes would be mixed in silently.

| File with a marker | First merging build |
|---|---|
| Not edited (the hash equals the content) | That content is the base. Removes the marker and writes per the DSL. The user block `// !----` is left out of the merge like the hash and goes back to where 2.x puts it when it rewrites (the import block below the `package` line, the others before the closing line) - from the next build on it is the same as an edit. If the DSL did not change this file, only the marker is removed and the block stays where it is |
| Edited - what this build produces equals the hash (model and generator unchanged) | That is the base. Removes the marker and keeps your edits |
| Edited - the first build that upgrades a 2.x project to `output = 'v3'` | The build produces the 2.x code once more in memory only (`[elcore3] upgrade : ...`), takes the one whose hash matches as the base, and merges your edits into the 3.0 code. This build handles all domains at once (it stops with `-Pdomain`) |
| Edited - the base cannot be confirmed | Leaves the marker and skips it as before (`SKIP(user-modified)`; a generate-once file gets `SKIP(user-owned)` and a warning). If you delete it, it is generated anew |

If the DSL changes an edited file (the third row above), that user block is also left out of the merge and goes to 2.x's place - only the edited lines are merged.
A file the DSL did not change (the second row) stays as it is, blocks included. However, for a file where the block
cannot be put back in that place intact - more than one import block, or a code block above the import block - the block is merged where it is, with
your edits (a conflict may occur, and nothing is lost). If the merge conflicts, that file too gets the conflict markers with the block left
where it is - so that the block falls inside one side of the conflict markers and is not deleted when you resolve it.

So for a while, files handled by the cache and files that still have markers can be mixed, and each file follows its own rule. The per-file
`UNCHANGED(your edits kept)`, `MERGED`, `KEPT` and `SKIP` and the one-line summary say where things are:
`=== merge: 3 merged with your edits / 1 kept as yours / 0 conflict(s) / 2 still stamped (edited, base not confirmed) ===`
and below it the list of files that still have markers.

- `-Pforce` does not apply to merged files. A setting that is always on in the build must not erase your edits.
- **If a git merge conflicts** (when two branches each changed the model and generated):
  1. For `.elasticore/cache`, pick one side - `git checkout --ours .elasticore/cache`.
  2. For a conflicted generated file, pick **the same side as the cache** - `git checkout --ours <file>`. When you build, the other side's DSL changes come in again. If you pick
     the side different from the cache, the DSL changes of the cache's branch look like "things the developer deleted" and may silently drop out.
  3. **If both branches edited the same generated file by hand**, merge that file yourself - keep the generated lines and the edited lines of both sides. If you
     pick only one side, what the other side edited is lost.
- If the cache cannot be read (corrupt), nothing is written and the build fails. Fetch it again from git. If you delete only the `cache` file, the next build reports, without touching them, all files that are not
  in the cache and differ from what is generated now - delete the files you want to get as generated. If you delete the whole `.elasticore/`
  folder, it is treated as a project merging for the first time, and `orm.xml` and the message files, which were always overwritten, are written anew as before.
- The merge is line by line, so it counts as a conflict even when both sides edit **directly adjacent lines** (same as git).

---

## 10. Resource setting

```yaml
config:
  resource:
    - "https://example.com/shared-models.yml"
    - "https://example.com/common-entities.yml"
```

- Downloads YAML model files from an external URL and includes them in the domain model
- The downloaded file is cached locally in the form `_resource/model-{N}.yml`
- By a byte comparison, if the content is identical the file write is skipped (avoids unnecessary I/O)
- HTTP redirects (301, 302, 307, 308) are handled automatically
- Useful when several domains share a common model

---

## 11. A complete env.yml example

```yaml
elasticore:
  enable: true
  config:
    domainName: rental
    framework: springboot3
    mode: jpa
    j2ee: 17
    apiPath: "/api/v1"
    supportAI: "false"
    testCode: "true"

    table:
      prefix: rental_
      autoJoin: false

    jpa:
      useQueryMethod: true

    annotations:
      genid: "genid(io.example.util.IdGenerator.newId)"
      stringarray: "@entity:Convert(converter = io.example.util.StringArrayConverter.class)"
      audit: "@entity:EntityListeners(AuditingEntityListener.class)"

    datasource:
      name: "primaryEntityManagerFactory"

    resource:
      - "https://shared.example.com/common-models.yml"

  namespace:
    entity: io.example.rental.entity
    dto: io.example.rental.dto
    enumeration: io.example.rental.enums
    repository: io.example.rental.repository
    service: io.example.rental.service
    control: io.example.rental.controller
    port: io.example.rental.port
```

---

## 12. Domain-scoped code generation (`-Pdomain`)

This feature lets you update the DSL and regenerate Java sources for selected domains only.
In a large project, it lets you regenerate quickly when you changed only one domain,
or refresh remote DSL URLs for only specific domains.

### 12.1 Required Gradle setup

You must pass the `-Pdomain` option in the `elcore` task of your project's `build.gradle`.
Add the following 3 lines, the same pattern as the existing `-Pmode` forwarding:

```groovy
task elcore(type: JavaExec) {
    // ... existing settings ...
    
    if (project.hasProperty('domain')) {
        systemProperty 'elasticore.domain', project.property('domain')
    }
}
```

Without this setting, the `-Pdomain` option is ignored and all domains are processed.

### 12.2 Examples

**Process a single domain:**
```bash
./gradlew elcore -Pdomain=hr
```
Only the `hr` domain loads its `env.yml`, updates its DSL, and regenerates its Java sources.

**Process several domains (comma separated):**
```bash
./gradlew elcore -Pdomain=hr,bbs
```
Only the `hr` and `bbs` domains are processed.

**Combined with validation mode:**
```bash
./gradlew elcore -Pmode=valid -Pdomain=hr
```
Only the `hr` domain runs DSL validation, and the build fails on a validation error.

### 12.3 Details

`-Pdomain` works as a **two-stage filter**. The DSL itself loads all domains so that cross-domain references (`@extend`, `@audited`, shared templates, etc.) do not break.

#### Stage 1: load the DSL (all domains)
- The `env.yml` and DSL of all domains are loaded
- The global type reference validation (`validateGlobalTypeReferences`) runs on the whole model
- `schema.yml` is also regenerated normally from all entities and enums

#### Stage 2: apply the filter
- **Publishing (source generation)**: only the specified domains have their Java sources regenerated. The earlier output of the other domains is preserved as it is.
- **Remote URL refresh**: only the `config.resource` HTTP(S) URIs of the specified domains are downloaded anew. The other domains reuse the existing `_resource/model-N.yml` cache (zero network access).

#### Domains without a cache
If a domain excluded by the filter has never been run before and its `_resource/` cache is empty, that remote resource is not loaded and the following warning is printed:
```
[DomainFilter] Skipping remote resource (no cache): <url>
```
If needed, run a full elcore once without `-Pdomain` to initialize the cache, then switch back to filter mode.

### 12.4 schema.yml behavior

Whether or not a filter is used, `schema.yml` is **regenerated from all domains**. This is because all domain DSLs are loaded and `SchemaCodePublisher` aggregates the complete set of entities.

### 12.5 Use cases

**A fast development cycle in a large project:**
- Among several domains, modify only the `hr` domain and add an entity
- Generate quickly with `./gradlew elcore -Pdomain=hr`
- The code of the other domains is not touched

**Selective refresh of remote DSL:**
- The project references several remote `config.resource` URLs
- Use `-Pdomain` when you want to refresh only the URLs of a specific domain
- Saves unnecessary network I/O and time

**Multi-module project:**
- Process only specific domains with `./gradlew elcore -Pdomain=account,order`
- As a result, code may be generated in several modules
- The domains of the other modules are skipped completely

---

## 12-1. The guides' language (`guides`)

Every generation copies these guides into `src/main/resources/blueprint/`, in English. A team that reads Korean
better asks for the Korean ones; the files keep their names, and the index beside them follows:

```gradle
elasticore {
    guides = 'ko'                 // en (default) | ko - -Delasticore.guides=ko without the plugin
}
```

Up to 3.0.261031-rc7 the guides were in Korean only. A language they are not written in gives a warning and the
English guides.

---

## 13. Best Practices

**Namespace naming rules:**
- Keep an independent package structure for each domain
- Use the `{base package}.{domain}.{type}` pattern consistently
- Always include the domain name to prevent package collisions between domains

**Cautions when using Multi-Mode:**
- When you use several modes such as `jpa,uml`, all outputs are generated at the same time
- In production, specify only the modes you need to avoid generating unnecessary files
- Use the `uml` mode to check entity relations during development, and excluding it from production builds is recommended

**autoJoin performance impact:**
- `autoJoin: true` applies JOIN FETCH to all associated entities, so queries can become complex for entities with many associations
- In most cases, leave `autoJoin: false` (the default) and apply `@fetchjoin` individually only to the fields that need it, which is better for performance
- In a domain with many simple reads, `autoJoin: true` effectively prevents the N+1 problem

**Test code generation:**
- After you set `testCode: "true"`, test files are generated in the first build
- The generated tests are useful for basic CRUD checks; writing business logic tests separately is recommended
- Correct test code is also generated automatically for composite-key entities
