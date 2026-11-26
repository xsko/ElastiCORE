# ElastiCORE Environment Setup Guide (env.yml)

`env.yml` is the per-domain environment file of ElastiCORE. It defines the code generation mode, the framework, namespaces, annotations and more.
It lives in each domain directory (`src/main/resources/blueprint/{domain}/`). You describe the environment in it before you model with the DSL.
A section marked **Works in:** works only there; **Not in:** says what happens elsewhere - the build stops, a warning, or nothing is generated.

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

> **The 3.0 rule engine (`elcore3` - the plugin default) writes `jpa`, `mybatis` and `mongo` code** (`mongo`: layered
> Java only - section 2.4). For a domain with none of them (`mode: uml` alone and so on), `elcore3` refuses and writes no
> source; such a project uses the 2.x generator with `elasticore { output = 'v2' }` (the build calls `elcore`). `uml` and
> `dsl` listed with them are not written by `elcore3`; it only prints a warning - build that output with `./gradlew elcore`.
> If you list several storages (`jpa,mongo`), it writes the first of jpa, mybatis, mongo (one domain has one storage).

### 2.3 MyBatis mode (3.0)
> **Works in:** v3, Java (layered, ddd, hexagonal) · **Not in:** Kotlin, 2.x-compatible output (output = 'v2') (stops the build)

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
| `text`, `clob` / `blob` | `CLOB` / `BLOB` | `TEXT` / `BYTEA` | `CLOB` / `BLOB` | `NVARCHAR(MAX)` / `VARBINARY(MAX)`; a JPA `clob` is `VARCHAR(MAX)`, as Hibernate maps it - use a UTF-8 collation (2019+) for any language |
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
an empty string counts as none), and the block `/* if:name */ ... /* if-end */` works the same way. A block may hold blocks and
conditional lines: each `/* if-end */` closes the innermost open block, as in the runtime. The result is converted by the same rules as the runtime (column aliases to
a DTO in camelCase, the first column for a single value - null if NULL, a row whose columns are all NULL becomes a DTO whose fields are all null,
a `Map` has every column, a NULL one as null - the runtime library's `PortRowColumns` does this, which the MyBatis Spring Boot starter applies). A port that uses
an expression (JEXL - `input.x > 3`) in `/* if: */` is refused - use a single name. A method with no answer (`void`) only runs the statement.
A page is a count and one page. The count removes the last `ORDER BY` of the SQL (SQL Server does not accept an `ORDER BY` inside a derived table) - if
`LIMIT`, `OFFSET` or `FETCH` follows, it is left as it is. The page is the input's, or that of a `Pageable` parameter
(`customersByPage(String lastName, Pageable pageable)`; the port interface imports it). The call's sort - `sortCode` or `sortColumn` of the input,
else the `Sort` of a `Pageable` (or `Sort`) parameter, checked as a sort code - goes first in the SQL's own `ORDER BY`, whose terms stay as tie
breakers, or becomes the `ORDER BY` where the SQL has none or an `/* if: */` leaves it out. A page with no order at all is cut with
`ORDER BY (SELECT NULL)` on SQL Server (it does not cut a page without an order) - also when the `ORDER BY` stands in an `/* if: */` line or
block whose condition is false. Since: 3.0.261030-rc6 (blocks in blocks, the `Pageable` and its `Sort`, an `ORDER BY` inside `/* if: */`,
a map's NULL column: 3.0.261112-rc12)

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
commands (`commands:`), lifecycle (`states:`), `@snapshot` and `@compute`, ports
(`<Root>RepositoryPort`, `<Root>QueryPort`, `<Root>UseCase`), the outbox, architecture tests. The domain (`domain.model`) has no JPA or Spring
Data name at all (the JPA version gets this with `mapping: xml`; here you get it as it is).

The difference is the **repository adapter** (`<Root>RepositoryAdapter`). The adapter does, line by line, what JPA did by itself:

| What JPA did | The MyBatis adapter |
|---|---|
| `save()` decides whether the row is new | INSERT if there is no version (or no key), otherwise UPDATE |
| Dirty checking, `@Version` | UPDATE of all columns, with the version compared and increased in the same statement (409 if stale), and the in-memory version raised too - for the root and for members |
| Member cascade, orphanRemoval | Compares with the stored member rows - DELETE the missing, UPDATE the remaining, INSERT the new (member keys are kept) |
| To-one member cascade (`@OneToOne @cascade` and so on) | INSERT/UPDATE before the root (the root holds the key), DELETE the old member that changed or is gone (reads the stored row after clearing the session cache, to compare) |
| `delete()` | 409 before anything is deleted if the version is stale (locks the row to the end of the transaction with `SELECT ... FOR UPDATE`, `UPDLOCK` on SQL Server - told apart by MyBatis's database id, as the paging is - so a database that counts changed rows, such as MySQL with `useAffectedRows=true`, reads it alike), then DELETE in the order member collections -> row -> to-one members (the stored one and the in-memory one), and publishes the events raised in between |
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
> **Works in:** v3 layered Java and Kotlin (run on Spring Boot 3), 2.x generator · **Not in:** ddd, hexagonal (stops the build)

A domain kept in MongoDB. **The model (DSL) stays the same; you change only the one word `mode`.** Since: 3.0.261119

```yaml
config:
  mode: mongo
```

**What is generated** - DTOs, search DTOs, controllers, access checks, `@deleted`, the API errors and the mapper are the same
as in JPA. Only the storage side differs; the project declares `spring-boot-starter-data-mongodb` instead of JPA.

| File | Content |
|---|---|
| `<Entity>.java` | `@Document(collection = "<table name>")`, `@Id`, `@Version`, `@CreatedDate`..., `@Indexed(unique = true, sparse = true)` for `@unique`, `@CompoundIndex` for `@index`, `@Field` only for a named `@column` |
| `<Entity>Repository.java` | `extends MongoRepository<Entity, Id>`, and the search: `criteria(searchDTO, owner)`, `hasCondition`, `sort` (only `SORT_FIELDS`) |
| `<Entity>CoreService.java` | The **same method names and signatures** as the JPA version, over the repository and `MongoTemplate` (a search is a `Query` of the Criteria, paged) |

**In Kotlin** (`language = 'kotlin'`, since 3.0.261121) the same files are `.kt`: the entity is a class of `var` properties with the same annotations, the repository's search is its `companion object` (`@JvmStatic`, so `OrderRepository.criteria(...)` reads the same from Java and Kotlin), and the bundle is `mongo-springboot-kt`. A reference is read lazily through a proxy of the referenced class, which Kotlin makes final - open the documents in `build.gradle`, as a Kotlin JPA project opens `@Entity`:

```groovy
plugins { id 'org.jetbrains.kotlin.plugin.allopen' version '<your Kotlin version>' }
allOpen { annotation 'org.springframework.data.mongodb.core.mapping.Document' }
```

**How the model is stored**

| DSL | In the document |
|---|---|
| an entity with `@expose` / `@service` / `@repository` | a collection of its own |
| `customer: Customer @ManyToOne` (Customer has its own collection) | the customer's id, read back lazily (`@DocumentReference(lazy = true)`) |
| `lines: List<OrderLine> @OneToMany(mappedBy=...)` (OrderLine has no service) | the lines inside the order's document; their way back (`order`) is not stored (`@Transient`) |
| the other side of a relation (`mappedBy` to a collection of its own), `@joinBy`, `@formula` | not stored (`@Transient`) |
| an `@embeddable` value | a nested document |
| an enum with `@db(code)`, `money`, `quantity` | the value its JPA converter writes (`AttributeValueConverter`) - the code `"N"`, not `NEW` |
| `decimal` | `Decimal128`, so it compares and sorts as a number |
| `long @sequence` | a number from the `elasticore_sequences` collection (`MongoSequences`, `$inc`), starting at 1 - members too |

Search conditions: `eq`, `!=`, `>` `>=` `<` `<=`, `between`, `in`, `isnull`, text (`like`, prefix, suffix, ignoring case) as
a regular expression with the value's own characters escaped. A search path is a value of the document, of an embedded
value, or a reference's id (`customer.customerId`); one further (`customer.name`) stops the build.

Indexes are created only with `spring.data.mongodb.auto-index-creation=true` (Spring Data's default is off).
A write is one document - there is no transaction around several (a batch update is its documents one by one).

**Not yet** (stops the build, or as said): ddd and hexagonal; a composite key; an `@abstract(entity)` hierarchy;
`@ManyToMany`; a collection of another collection's documents without `mappedBy`; `@count`, `@onDelete`; DDL (none is
written). `elasticore-template-mongo` generates a shop, compiles it and runs its API on a real MongoDB (Testcontainers);
`elasticore-template-kt-mongo` does the same in Kotlin, with the same tests.

### 2.5 UML mode
> **Works in:** 2.x generator · **Not in:** v3 beside `jpa` or `mybatis` (warning); v3 alone (stops the build)

```yaml
config:
  mode: uml
```

**Generated:** Mermaid diagrams, an entity relationship diagram as an HTML document

### 2.6 DSL mode
> **Works in:** 2.x generator · **Not in:** v3 beside `jpa` or `mybatis` (warning); v3 alone (stops the build)

```yaml
config:
  mode: dsl
```

**Generated:** DSL-only code output (when you use only the model definition, without JPA)

---

## 3. supportAI (removed)

`supportAI` wrote the extension service (`XxxService`) as Spring AI tools (`@Tool`). It is removed: the setting is ignored, the service is generated without tools, and a generation that finds `supportAI: true` prints a warning. Take the line out of `env.yml`.

- Why: the tools repeated the REST API that an agent can already call - give it the OpenAPI document instead - added about a dozen tools per entity, and put deletes by condition in a model's hands.
- A service an earlier version wrote with tools, under 3.0 code (`elcore3`): a file you did not touch is generated again without them. In a file you edited, the tool methods are taken out and your edits are kept where they do not overlap them (`MERGED(your edits kept)`, 9-2). The new file has no `@Primary`, as every other extension service.
- Under the 2.x code (`output = 'v2'`, `-Pv2`, `merge = false`) the service is written once and never again, so it keeps its tools; so does one that stays `KEPT(yours ...)` or `SKIP(user-owned)`. Delete the `@Tool` methods by hand (or the file, to have it written again) before you drop the Spring AI dependency - or keep the ones you want, with that dependency, as your own code.

---

## 3-1. Spring Boot 4 (ElastiCORE 3.0)

ElastiCORE **3.0** supports both Spring Boot 3 and 4. 2.2 supports only Spring Boot 3.
Verified versions: Spring Boot 4.0.8, Hibernate 7.2, Spring Data JPA 4.0, Jackson 3.1.

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
| delete by search `delete(SrchDTO)` of the 2.x-compatible output (`output = 'v2'`) | one `delete(Specification)` | the rows are read and removed one by one (`findAll` + `deleteAll`) - Spring Data JPA 4 has no `delete(Specification)`. From 3.0.261113-rc13; before, it did not compile |

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

**Masking personal data in responses** (`elasticore-springboot3` / `elasticore-springboot4`, since 3.0.261121):
```yaml
config:
  annotations:
    maskphone: "@dto:io.elasticore.springboot3.mask.Mask(io.elasticore.springboot3.mask.MaskType.PHONE)"
    maskname:  "@dto:io.elasticore.springboot3.mask.Mask(io.elasticore.springboot3.mask.MaskType.NAME)"
```
```yaml
      telNumber: string(20) @env:maskphone   # 010-1234-5678 -> 010-****-5678
      name:      string(50) @env:maskname    # 홍길동 -> 홍*동
```
- `@dto:` puts `@Mask` on the DTO field only; the entity and the database keep the value as it is.
- The runtime registers the Jackson module by itself (Jackson 2 on Boot 3, Jackson 3 on Boot 4), and the generated DTO is the same on both.
- `MaskType`: `PHONE` (the middle group), `EMAIL` (two letters of the local part), `NAME` (the first and last letter), `PLATE` (the last four digits), `CARD` (all but the first and last four digits), `RRN` (all but the first seven digits).
- Only the response JSON is masked; a request is read as sent. Everyone gets the masked value - the owner and the admin too.
- A client may send a read DTO back unchanged: the 3.0 code's update (`PUT` and `PUT /batch`; JPA, MyBatis, MongoDB, Java and Kotlin) compares a masked field's text with the stored value masked - with `@encrypt`, the decrypted value - and keeps the stored value when they are the same. Any other text is written, and a create writes what it is sent. The field is found by the class name `io.elasticore.springboot3.mask.Mask` (or the name `runtimePackages` gives it). Not kept: an update by condition (`updateBySpecification`), and code of your own that calls the mapper. A `@Mask` whose argument is not a `MaskType` constant is a DSL warning (`... names no MaskType constant - an update writes the field as sent, masked text included`), and the update writes that field as sent.

**Keeping a column encrypted** (`elasticore-springboot3` / `elasticore-springboot4`, since 3.0.261121): the 3.0 code's `@encrypt` (annotation guide) does it, with the blind index for `@search(eq)`. Without the generator, name the runtime's converter:
```yaml
config:
  annotations:
    encrypt: "@entity:jakarta.persistence.Convert(converter=io.elasticore.springboot3.crypto.EncryptConverter.class)"
```
```yaml
      telNumber: string(200) @env:encrypt @env:maskphone   # v1:... in the column; the length must hold the ciphertext
```
- The keys are Spring properties, read by the runtime's auto-configuration; keep them out of the repository (environment variables or a vault):
```yaml
elasticore:
  crypto:
    key: ${CRYPTO_KEY}               # base64 of 32 bytes (openssl rand -base64 32): every write uses it
    index-key: ${CRYPTO_INDEX_KEY}   # another 32 bytes: the blind index of @encrypt @search(eq)
    key-version: v1                  # the prefix of what the key writes; v1 when unsaid
```
- An application with an encrypted field does not start without `elasticore.crypto.key` and `elasticore.crypto.index-key` (the message names the missing one); a key that is no base64 of 32 bytes, an index key equal to the key, or a key version other than v1 to v9 stops any start.
- Rotating the key: make a new key, set it as `elasticore.crypto.key` with `elasticore.crypto.key-version=v2`, and keep the old one as `elasticore.crypto.keys.v1`. A row is read with the key its prefix names, and every write of the column - a new row, a changed value - is `v2:`; a row that is not written keeps its `v1:` text, and so does one whose other columns change under JPA (the generated entity is `@DynamicUpdate`: only the changed columns are written), while MyBatis writes every column, so any update of the row rewrites it as `v2:`. To move every row, run a one-off job as for existing rows (annotation guide, `@encrypt`): `decrypt` each value and write `encrypt(v)` back; once none starts with `v1:`, drop `keys.v1`. The index key is not rotated this way: a new one needs every `_bidx` column written again (`Bidx.of`, `Bidx.ofDigits`).
- The env alias encrypts only: an equality search of that column finds nothing (its text differs each time). Search it through `@encrypt @search(eq)`, which writes the index.

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
> **Works in:** ddd, hexagonal · **Not in:** layered (not generated)

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
> **Works in:** v3, Kotlin, JPA · **Not in:** 2.x-compatible output (output = 'v2'), 2.x generator - the classes keep their annotations and no orm.xml is written (not generated)

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
  With `runtimePackages` in the build (`elasticore-rule-guide.md`), the classes carry the mapped name - for
  `'io.elasticore.springboot3': 'com.acme.runtime'`, `@com.acme.runtime.entity.PersistentModel` - and the two lines name that one.
- A domain that uses Envers (`@Audited`) keeps that annotation, so the generated architecture test allows `org.hibernate.envers`.
- If a `META-INF/orm.xml` made by the user exists, it is not overwritten; the mapping is written to `META-INF/elasticore-orm.xml` and
  the `spring.jpa.mapping-resources` setting is shown.
- Use cases: unit tests of the domain model without JPA, enforcing hexagonal rules with an architecture test (`archTest`), layer separation audits in regulated industries

### 6.1.3 Architecture test (`archTest: true`)

```yaml
config:
  archTest: true        # test the layer boundaries with ArchUnit (default: false)
```

- It first writes `src/test/java/<base package>/<Domain>ArchitectureTest.java` (`src/test/kotlin` for Kotlin) **once**, then keeps it in step like every file generated once (below).
  It reads the layers from the namespaces: domain (`entity`, `enumeration`, `event`, `domainService`, `domainRepository`),
  application (`service`, `dto`, `inboundPort`, `queryPort`), adapter (`control`), persistence (`persistence`, `repository`).
- Where the namespaces name their layers by segment under the base package - `namespace: { base: ... }` lays them out so:
  `domain.model`, `application`, `adapter.in.web`, `infrastructure.persistence` - the packages your own code adds there are
  read too, by their first segment after the base: `domain.*` with the domain, `application.*` with the application, and
  outside both `adapter.*`, `infrastructure.*` and the first segments of `control`, `repository` and `persistence`
  (`interfaces`, say); `adapter.out.*` goes with persistence (the outbound side). A
  hand-written `domain.policy` class that uses `infrastructure.mail` then fails the test. A package it cannot place
  (`config`, say) is a DSL warning, once, and is not checked; the base package itself (the application class) and the test
  sources are left out. A layered layout, whose segments name no layers, is read from the namespaces alone.
- Once written the file is yours, as every file generated once: one you did not edit takes new package lists on the next
  build, and an edited one goes through the three-way merge (9-2) - it keeps your rules where they do not overlap.
  Upgrading to 3.0.261121 or later: a test nobody edited takes the wider lists with no DSL change, so it can now fail where your
  `domain.*` code uses `infrastructure.*` or `adapter.*`, or `application.*` code uses them past a port. There is no switch for the
  old lists: move the dependency behind a port the domain or the application declares.
- Rules for `ddd` and `hexagonal` (the domain's rules include `ddd-aggregate`): the domain does not depend on outer layers / the application does not depend on adapter or persistence classes (only through ports) /
  the domain does not know the web / (with `mapping: xml`) the domain does not know `jakarta.persistence` or `org.hibernate`.
- Rules for `layered` (any other selection): the services (`service`, `dto`) do not depend on the web (`control`) / persistence and the entities do not depend on
  the services or the web - the DTOs (`dto`) and the outbound ports (`port`) are shared and allowed / (with `mapping: xml`) the entities do not know `jakarta.persistence` or `org.hibernate`.
  The layered services call the repositories and the persistence helper directly, which the hexagon's rule forbids.
  A layered project whose test an earlier version wrote with the hexagon's rules gets the layered ones by deleting `<Domain>ArchitectureTest` (move your own rules aside first).
- It needs a test dependency: `testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'`
- Use cases: block layer violations in the build without code review, convey the boundaries to a new team member as code

### 6.1.4 Bounded context (`context:`, 3.0)
> **Works in:** v3 · **Not in:** 2.x generator (not generated)

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
| `publishes` lists an entity or a name that does not exist | DSL warning (event names of that context are fine) |
| Neither side has `context:` | Same as before (names are looked up in all domains, and the DDD bundle only warns) |

- What is checked: the types of entity and DTO fields (including the X in `List<X>`, and those with a domain name such as `catalog:Product`), the DTO's
  `@template(...)` and the entity's `@extend(...)` - not the classes your own code imports (a listener of another context's
  event, `elasticore-dsl-guide.md` 8). If two domains have the same name, it cannot tell which one is meant, so it does not check
  (the loader warns: a type name is unique across domains - rename one). The 3.0 rule engine (`elcore3`) checks before generation, and on failure it reports all problems at once and then stops the build.
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

### 6.1.5 GraphQL API (`graphql:`, 3.0)
> **Works in:** v3 (JPA and MyBatis; Java and Kotlin; Spring Boot 3 and 4) · **Not in:** 2.x generator

```yaml
config:
  graphql:
    enable: true          # a GraphQL controller beside each REST one (default: false)
    mutations: true       # false: queries only
    path: /graphql        # spring.graphql.path; every domain that names one names the same
```

- For each entity the REST API serves (`@expose` with a key; under the ddd and hexagonal bundles, each root) it generates
  `<Entity>GraphQlController` in the `control` package. It calls the same service (the use case, in the hexagon), so access,
  validation, invariants, `@deleted` and the lifecycle hold as they do for REST.
- Operations are named after the entity: `member(memberId)`, `members(search)` (a page when the search DTO pages),
  `createMember(input)`, `updateMember(input)`, `deleteMember(memberId)`, and one mutation per command (`shipOrder(orderId, command)`).
  `@readonly`, `@expose(methods=...)` and `@restExclude` take away the same operations as on REST.
- **The schema is written by the build, from the compiled classes**: after compiling, `elcoreGraphQl` reads the controllers and the
  DTOs they take and answer, and writes `src/main/resources/graphql/<domain>.graphqls` (the domain's `extend type Query/Mutation`
  and the types only it uses) and `elasticore.graphqls` (root types, scalars, types several domains use). A field is in the schema
  because the class has it: a value object without setters, a `@JsonProperty(access = WRITE_ONLY)` password (sent, never answered) and
  the scalars `Long`, `BigDecimal`, `Date`, `DateTime`, `Money` and so on follow the class. Then every `.graphqls` under `graphql/` -
  your own files too - is built into one schema; if it does not build, the build stops with the file and the reason.
- Refusals are GraphQL errors with the REST status and code in `extensions`: `NOT_FOUND` 404, `FORBIDDEN` 403, `BAD_REQUEST` 400,
  `CONFLICT` 409 (a duplicate, a stale version, a state that does not allow the command), `UNPROCESSABLE` 422. A refusal of
  the database or MongoDB says only its kind - code `unique`, `referenced`, `required`, `check`, `conflict` or `stale`, with
  the REST API's sentence - never the driver's text, which names the SQL, the index and the value.
- Several domains share one schema and one endpoint: different `path` values, or an entity of the same name served by two such
  domains, stop the build (`elcore3`) naming both domains. In your own `.graphqls`, write `extend type Query`, not `type Query`.
- Dependencies: `implementation 'org.springframework.boot:spring-boot-starter-graphql'`
  (tests: `testImplementation 'org.springframework.graphql:spring-graphql-test'`).
- Not yet: subscriptions, the cursor (Connection) page, a TypeScript client for GraphQL.
- Use cases: a screen that wants one round trip for a page of orders with their lines; a partner who prefers GraphQL beside the same REST
  API; an AI or BI tool that reads the schema to write its queries.
- Example: `shop` and `billing` of `elasticore-template-ddd` (`GraphQlApiTest`), `shop` of `elasticore-template-kt` (`ShopGraphQlTest`).

### 6.2 Test code generation
> **Works in:** `"true"`: 2.x generator, 2.x-compatible output (output = 'v2'), Kotlin; `"contract"`: v3 Java and Kotlin - every architecture, JPA, MyBatis, MongoDB · **Not in:** `"true"`: v3 Java (layered, ddd, hexagonal), MyBatis (mode: mybatis) (not generated), MongoDB (mode: mongo) in Kotlin (warning, not generated); `"contract"`: 2.x generator (not generated), 2.x-compatible output (warning, not generated)

```yaml
config:
  testCode: "true"      # "true": service unit tests; "contract": the contract tests (6.2.1); default "false"
```

- With `"true"`, JUnit 5 + Mockito service unit tests are generated - for 2.x code (`output = 'v2'`) and for Kotlin; the v3 Java bundles and MyBatis write none
- Composite Key entities are handled correctly too
- Sample data is generated for every field type
- Complete test coverage for CRUD and search (List/Page) methods

The generated test files are placed under `src/test/java/` at the path of the service package.

**Testing a method of your own in `*Service`.** The generated tests do not cover it. A method that writes needs `@Transactional`
of its own (the CoreService is `readOnly = true`, JPA and MyBatis), and a test transaction - `@Transactional` on the test class -
hides a missing one: test it with `@Transactional(propagation = Propagation.NOT_SUPPORTED)` or through the HTTP API, on the
database you deploy to (H2 does not refuse the write). `elasticore-dev-guide.md`, "Methods of your own in `*Service`".

#### 6.2.1 Contract tests (`testCode: "contract"`, 3.0)

Tests of what the DSL declares, over the project's own API. `"contract"` writes these and nothing else - no Mockito, no service
unit test; `"true"` keeps its meaning, and a model without `"contract"` generates exactly what it did.

| The DSL says | The test |
|---|---|
| a move of `states:` | allowed from each state it lists: 2xx, the state moved, a `@stamp` of the state entered set; refused from each other state a row can reach: 409 `state.<Entity>.<field>.<command>`. A new row starts in `initial`. A parameter of the command named as the field that holds a reference's key, of its type (`boardId` of `board: Board`), is sent the key of a row the test makes first, as a row that needs one makes it |
| `!` (required) | sent as null: 400, the field, `NotNull` |
| `string(n)`, `@length` | n characters pass, n + 1 are refused: 400 `Size` (and the least length, where one is given) |
| `@min`, `@max` | the bound passes, one step past it is refused: 400 `Min` / `Max` (`DecimalMin` / `DecimalMax` on a decimal) |
| `@pattern` | a value made from the expression - checked with it - passes, one that does not match is refused: 400 `Pattern`. A password's rule - `^` then lookaheads `(?=.*X)`: a value with a character of each X passes, one that matches the rest but misses one is refused |
| `@notblank`, `@email` | text passes, blank text (`"   "`) is refused: 400 `NotBlank`; an address passes, the address without its `@` is refused: 400 `Email` |
| `@past`, `@future` | on a date, a datetime or a timestamp: a year ago (from now) passes; past now the other way is refused - a date by two days (a midnight during the request leaves it past), a datetime or timestamp by one: 400 `Past` / `Future` (`PastOrPresent`, `FutureOrPresent`), read from the clock when the test runs |
| `@minsize(n)` | n members pass, n - 1 are refused: 400 `Size` |
| `@invariant` | `price > 0`, `items.size > 0`, `notNull`, `notEmpty`: the boundary passes, one past it is refused: 422 `invariant.<Entity>.<rule>` |
| `@access` | each operation the API serves - read, search, create, update, delete, each command - at its entry, else `*`, else `admin`: a caller each term lets in passes (2xx; a search finds the row); a visitor is refused 401 `not-signed-in`, a caller of a role no term lets in 403 `forbidden`, and where `owner` is a word a caller on another's row 404 `not-found` (a search does not find it). `REP&owner` refuses another role on a row of theirs and a REP on another's; `owner\|MANAGER` lets in either. A visitor's create of a row keyed by its owner (`@owner` the key the request chooses) is refused 401, as the service does |
| `@Mask` (an env.yml `@dto:` annotation) | a value of the mask's shape is sent - a phone of three groups, an address, enough digits: the read and the search show what the field's `MaskType` makes of it (its `apply`, as the generated update compares it). The masked text sent back by an update (`PUT`) keeps the stored value, a new value replaces it (issue 270) - read past the application (`JdbcTemplate`, `MongoTemplate`), else found by an `@encrypt @search(eq)` search |
| `@encrypt` | the column holds no plain value and decrypts to the value sent; the API answers the value. With `@search(eq)`: a search by the value finds the row, one by another value does not |
| `export` in `@expose(methods)` | two rows of the test's own, sharing a text the search finds them by (where it takes none, every row the caller may see): the file of `POST .../export` (CSV) holds the rows the search answers with the same body - as many as it matches, in its order, sorted by the key (`sortCode` `<key>-`), which every response shows. With `elasticore.export.max-rows` at that many rows they are written; at one fewer the export is refused 400 `export.too-many-rows` before anything is written - a problem, no file |

- Files: `<Domain>ContractSupport` and a `<Entity>ContractTest` for each entity the API creates, in `src/test/java/<base package>/`
  (`src/test/kotlin` for Kotlin). Written once, then merged when the DSL changes - kept where you changed them - as the architecture test.
- Every request goes through the support class: `request(builder)` is what every request gets added - nothing as generated; a
  CSRF cookie and header go there - and `signIn()` how a test signs in. Where an operation asks who is calling (`@access`), it signs in
  an administrator made for the test as the application itself, through the generated sign-in (`@signin`); without a generated
  sign-in the requests run as the application (`CurrentUser.runAsSystem`), and no test of `@access` is written.
  The support class's comment says why there is none: the run has no `@signin`, or its principal cannot be built - with the
  summary's reason (`Member (signing in): password: ...`, a `@pattern` the generator cannot sample, say).
- The tests of `@access` sign in principals of their own: `signIn(role)` makes one as the application itself, with that role in the
  `@signin` entity's role field (`"M"` for `UserRole.MANAGER` written `@json(code)`), signs it in through the generated sign-in and
  returns a `Caller` - its key, which a row's `@owner` holds, and its session; `sentBy(caller, ...)` sends a request as it, or as a
  visitor for `null`. A sign-in's session is kept with the cookies the sign-in answers with, and both are sent (`perform`,
  `sentBy`): under Spring Session (`spring-session-jdbc`, ...) the signed-in session is the one its `SESSION` cookie names, not the
  container's; where the container keeps the sessions there is no cookie and nothing more is sent.
  A support class an earlier version wrote (before 3.0.261126) takes these lines by the merge (`MERGED(your edits kept)`); one whose
  own lines meet them is `KEPT`, and the run says where - copy the `cookies` lines over from `build/elasticore/contract/` by hand.
  A row of a caller's is made by the administrator - as the application itself (`asTheSystem`) where no
  administrator signs in - with the owner sent, or by the caller where the server sets `@owner`; the `@signin` entity's own row is
  the caller's. Sign in your way in `signIn(role)`. A support class of yours kept by a merge without these members gets no test of
  `@access` (the build never breaks): the summary names the member, and `build/elasticore/contract/` holds the version generated.
- Callers your sign-up makes: write `newSignInRow(role)` in the support class - Java `protected Map<String, Object>
  newSignInRow(String role) throws Exception`, Kotlin `protected fun newSignInRow(role: String?): Map<String, Any?>`, at the end of the
  class. It makes the row of a caller of that role your way (your service, as your sign-up makes one) and answers its login, its plain
  password and its key: `obj("email", email, "password", "Secret-1", "memberId", id)`. `role` is the role field's value as a request
  sends it (`null`: a caller of no role), and it runs as the application itself. Where the run finds it in your support class, on
  disk, `signIn()` and `signIn(role)` sign in the rows it makes through the generated sign-in - instead of rows built from the model,
  even where those could be - and the tests of `@access` are written, also where the API takes no create (`@expose(20,
  methods="read,search,update")`). It is not generated and not an interface: without it the build is as it was, and where no
  principal can be built from the model the summary and the support class's comment say what to write (`Member (signing in): its API
  takes no create, ... - to sign callers in, write protected Map<String, Object> newSignInRow(String role) ...`). The tests still say
  what each entry expects. Another signature is not read as it. `elasticore-template-ddd`'s `forum` domain does so.
- Rows your code makes (issue 358): write `new<Entity>Row()` in the support class - Java `protected Map<String, Object>
  newNoticeRow() throws Exception`, Kotlin `protected fun newNoticeRow(): Map<String, Any?>`, at the end of the class, as
  `newSignInRow`. It makes a row of that entity your way (your service, as your code makes one) and answers it with its key:
  `obj("noticeId", notices.save(notice).getNoticeId())`; it runs as the application itself, so `create=none` lets it in.
  Where the run finds it in your support class, on disk, every row of that entity a test needs is the one it makes, not one a
  `POST` of the model's row makes - the rows of the `@access` tests (read, search, update, delete, commands, the moves of
  `states:`) and the rows other entities point to. Its fields are checked through `PUT`
  (`assertUpdated(put(NOTICE, obj("noticeId", id, "title", text(50))))`, then 51 characters refused 400 `Size`) where the API
  takes an update; `!` (`NotNull`) is not - an update keeps what it does not send. What only a create of the model's row checks:
  a create let in, `@invariant`, where a new row starts, `@Mask`, `@encrypt`, the export - is not written, and counted. Use it for an
  entity whose API takes no create (`create=none`: a log the server writes, a record another entity's command makes) and for one whose
  `beforeSave` refuses the row the model builds (422: a record only made for a parent in a certain state). Without it the tests are
  as they were; where the API takes no create (`create=none`) no test that needs such a row is written - it could only abort - and
  the summary says what to write (`AccessLog: its API takes no create (create=none), so no row of it is made to test its fields
  and @access with - to have them written, write protected Map<String, Object> newAccessLogRow() throws Exception in
  ForumContractSupport: ...`). Only a `protected` one is read - a private helper of that name stays yours. It is not generated
  and not an interface. `elasticore-template-ddd`'s `forum` domain does so (`Notice`, `AccessLog`).
- `create=none`, `update=none`, `delete=none` (any operation whose every term says `none`): a visitor and a caller of another role
  are refused as for any entry, and the administrator too - 403 `forbidden`, as `AccessPolicy` answers it; update and delete on a
  row `new<Entity>Row()` made (or a create made, where it is open).
- Nobody signed in: the rows of an `@owner` entity are made by the application itself, without their owner (the server sets `@owner`
  from who is calling). The summary counts each such entity (`Verification: its rows are made without their owner (@owner(memberId))
  - ...`), and a 5xx answer on one of its rows - a row a test needs, a step, the move - aborts the test with the reason (`withoutOwner`),
  since the application's own code may need the owner; any other answer is checked as ever (a 409 where the move is allowed fails).
- Not tested of `@access`, counted in the summary and listed in the support class's comment: no principal to sign in, a role the
  role field cannot hold, a term of two roles (`REP&MANAGER` - a session holds one), `owner` where `@owner` names no field, an entry
  not read (`read=owners`), a delete or a command no sequence of commands brings a new row to, a row of a caller's that cannot be
  made, a search that takes no key, a composite key (create only), a visitor's create that clears a required `@owner`.
- `@Mask` and `@encrypt`: the support class reads a row back (`read`), searches (`search`), updates (`put`), reads what is stored past
  the application (`stored`: `JdbcTemplate`, after the JPA transaction is written out; `MongoTemplate`) and decrypts it (`decrypted`, with
  the application's key - give the tests test-only keys of their own, made for the run as the samples' `TestCryptoKeys` makes them).
  Not tested, counted and listed in the support class's comment: a composite key, the key, a field a request does not write, one no value
  the generator builds fits with a change the mask shows (`string(2)` masked `EMAIL`) or with a second value (a `@pattern` of one), no read
  (`@expose methods`; the search where it takes neither the key nor the field, or answers with a `@searchResult` DTO), no update of the
  field (a root its commands change; `@editable` opens it to `PUT`), a `@pattern` field sent back (it may refuse the masked text), an inherited
  field, a `@rollup` entity or a name not written plainly (the column is not read), an `@encrypt @search(eq)` field the search does not take.
  A value the mask writes as it is aborts the test. A kept support class without these members gets none of them.
- The export: the support class sends the body (`exportOf`) and the search (`searched`), and sets `elasticore.export.max-rows` for one
  request in the application's `Environment` (`exportAtMost` - the export reads it at each request, so tests run in parallel would
  see it too: run these one at a time); the file's rows are told apart by the key alone (`columns`, `headers`). Not tested, counted
  and listed in the support class's comment: a controller without `POST /export` (a rule set leaves it out, or the controller is yours
  without it), a composite key, a `@searchResult` DTO without the key, the order where the API takes no search. A kept support class
  without these members gets none of them. More rows than the application exports, or rows written meanwhile where nothing narrows
  the search (a second search or export answers otherwise), abort the test rather than fail it.
- The support class carries `@SpringBootTest` + `@AutoConfigureMockMvc` (and `@Transactional`): every test class extends it, so Spring
  starts the application once for the suite, and the JPA and MyBatis tests roll their rows back. Spring finds the application above
  the tests' package; where it would not, the build names the project's one `@SpringBootApplication` - else name it there.
- The JPA and MyBatis tests write an in-memory H2 of their own, whatever datasource the application names: the support class's
  `@SpringBootTest` properties set its URL and driver, and over JPA Hibernate makes the tables (`ddl-auto=create-drop`, Flyway and
  Liquibase off); over MyBatis the schema is the application's (`spring.sql.init`, Flyway). Change them there for another database.
  A table H2 cannot make - a column `@columnDef('jsonb')` or `xml` - is counted as skipped.
- Each test makes its own rows: what the model requires, a row a reference names first (through its own API), and a value of its
  own for each row where one must be unique (`@unique`, a unique index, the key): text, a pattern's value varied, a number in steps.
  It never depends on the seeded data - only the export's tests count rows, compared with the search or the export itself, never with a
  number - and it checks the status and the problem's `code` (and the field) - never the message, so a translated API passes.
- A row the generator cannot build is counted in the build's summary:
  `contract tests: 70 written, 2 skipped (Receipt: its API takes no create (@expose methods), so no row is made to test; ...)`.
  A refusal at run time by a rule of the application's own - a check its code makes, a unique value taken - aborts the test with
  the reason (`Assumptions.abort`, and printed, since a Gradle XML report keeps no reason). A refusal the model declares - a field
  (`invalid`), an invariant, a state - fails it, on a row the test needs as on the one it checks; so does a 5xx.
- `testCode` is read exactly: `"true"`, `"contract"` or `"false"`; another value (`"Contract"`, `"contracts"`) is a DSL warning.
- MongoDB: the tests run on the server `ELASTICORE_IT_MONGODB_URI` names (`mongodb://host:port`, database `elasticore_contract`) and are
  skipped without it; their rows stay, with unique values.
- Covered: the field constraints, `@access`, `@Mask` and `export` in every mode, language and architecture (`@encrypt` where it is:
  not MongoDB); the lifecycles and the invariants where they are written - ddd and hexagonal, JPA and MyBatis, Java and Kotlin. Not
  yet: the `delete:` states of a lifecycle as a test of their own, a member's own constraints. A fractional bound of a float (`@max(0.1)`) is counted as skipped:
  a float holds no exact 0.1, and its check compares it widened to a double. So are a `@past` / `@future` time of day (past or
  future by the hour the test runs at), a lookahead the rest always holds (`@pattern('^(?=.*\\d)\\d+$')`: nothing matches the rest
  and misses it) and any other lookaround - a negative lookahead, a lookbehind, a lookahead further in.
  A mark the DTO does not write is not tested: `@notblank` on a field the server sets (`@readonly`), which a request cannot send.
- Test dependencies, not added for you: `testImplementation 'org.springframework.boot:spring-boot-starter-test'` and, over JPA and
  MyBatis, H2 (`testRuntimeOnly 'com.h2database:h2'`); on Spring Boot 4 also `spring-boot-starter-webmvc-test`.
- Change a test where you changed a rule on purpose, and a row's builder (`newPurchaseOrder()`) where the application needs more than
  the model says: the pet store's `PurchaseOrderContractTest` adds a delivery address, without which its `place` refuses the order.
- Examples: `elasticore-template-ddd` (Java, hexagonal, JPA; `Subscriber` `@encrypt`, `@Mask`), `-ddd-mybatis` (MyBatis; `Customer.phone`
  masked), `-kt` (Kotlin; `cancel=owner|LEAD`, `bill=LEAD&owner`; `Agent.phone` `@encrypt(digits) @s(eq)` masked), `-mongo` and `-kt-mongo`
  (`Customer.email` masked), the pet store (Spring Boot 4, sign-in; `read=owner|MANAGER`, `delete=MANAGER&owner`). Exported (`export`):
  the pet store's `Item` and `PurchaseOrder`, `-kt`'s `Ticket`, `-ddd-mybatis`'s, `-mongo`'s and `-kt-mongo`'s `Customer`.

---

## 6-2. Reference column name and admin role (3.0)
> **Works in:** ddd, hexagonal for `referenceColumn`; v3, Kotlin for `adminRole` · **Not in:** layered for `referenceColumn` (warning); 2.x-compatible output (output = 'v2'), 2.x generator for `adminRole` (not generated)

```yaml
config:
  referenceColumn: short   # column for a reference to a root of another kind: customer_id (default: the 2.x style customer_customer_id)
  adminRole: ADMIN         # role name that the admin level of @access checks (default ADMIN)
```

- `referenceColumn: short` - makes the column name used by an identifier reference (`customerId`) of the DDD bundle the field name itself
  (`customer_id`). A root's reference to its own kind keeps the relation's column. It is **for new projects**: the column names of an existing DB change, so a project
  moving from 2.x leaves it as it is or turns it on together with a migration.
- `referenceColumn: field` - the column is the relation field's name + `_id`, for every reference by id: `author: Member` ->
  `author_id`, `editor: Member` -> `editor_id`, and a root's reference to its own kind too (`parent: Post` -> `parent_id`).
  A field's own `@db(...)` still wins. Only the table changes: the id field keeps its name (`authorMemberId`). Also for new projects.
- In layered, `referenceColumn` is not read, and the build says so: `[shop] referenceColumn: short is read by architecture = 'ddd'
  and 'hexagonal' only - ...` (since 3.0.261120). It is a DSL warning, so `elcoreValidate -Pstrict` fails until you remove the setting.
- `adminRole` - if the roles put in at sign-in, as in `SessionCurrentUser.signIn(request, id, "ADMIN")`, include this name,
  the user is an admin. The `signin` generated by `@signin` on an entity line puts that value in when the `role` field (or the `@role` field) of that entity
  is an enum or string with `@access(admin)`.

## 6-3. The language of the `--` labels (`labelLanguage`, 3.0)
> **Works in:** the label file - the v3 bundles of a domain with an API (`@expose`), JPA, MyBatis, MongoDB, Java, Kotlin, every architecture; the refusals that read it - the invariant and lifecycle refusals, which the ddd and hexagonal bundles write (JPA, MyBatis; Java, Kotlin) · **Not in:** layered and MongoDB (no such refusal), 2.x-compatible output (output = 'v2'), 2.x generator

```yaml
config:
  labelLanguage: ko        # the "--" labels of this domain are Korean (optional; a tag as an @label key: ko, pt-BR)
```

- Write the bare language (`ko`, not `ko-KR`): a request with `Accept-Language: ko` looks for `_ko`, and `ko-KR` finds `_ko` too.
- In a refusal the `--` text becomes the label as it is - a long description reads oddly there; give a short one with `@label(ko=...)`.

- Says which language the DSL's `--` text is in. It applies to the whole domain. The label bundle then gets a file of that
  language (`i18n/<domain>-labels_ko.properties`) with every label - `@label(ko=...)`, else the `--` text, and for an enumeration
  its `label_ko` column, else its label column - and a refusal answered in Korean names the field, the entity and the state by them
  (`elasticore-rule-guide.md`, "Error codes and languages").
- Without it, a language with no `@label(<language>=...)` gets the field or entity name in those sentences, never the `--`
  text; English always takes `@label(en=...)` or the name. A value that is not a language tag (`korean`) is a `DSL_WARN` and ignored.
- Nothing else reads it yet: the `--` text stays the Javadoc, `@Schema(description)`, the column comments and the Ossie description.
  An export's column titles (6-4), and the enumerations its `labels` names, are read from the same label files.

## 6-4. The rows an export writes at most (`elasticore.export.max-rows`, 3.0)
> **Works in:** the export of `@expose(methods="...,export")` (`elasticore-annotation-guide.md`), every mode, language and architecture · **Not in:** 2.x-compatible output (output = 'v2'), 2.x generator (no export)

```yaml
# application.yml - a Spring property of the application, not an env.yml key
elasticore:
  export:
    max-rows: 20000        # default 100000
```

- An export counts the rows of its search first - the first page's total, the same conditions, access check and `@owner`
  filter - and refuses one of more rows with 400 (`code: export.too-many-rows`, the count in the detail) before it writes anything.
- Read on each request; one value for the whole application. An `.xlsx` workbook writes 1,048,575 rows at most whatever it says.
- The body's `labels` (3.0, issue 289) writes the enumeration columns it names - `"*"`: every one - by their label in the
  request's language: the `label_<lang>` column (from the label file of that language: `labelLanguage`, `label_en`), else
  the label column, else the code. It needs no setting; the body is in `elasticore-annotation-guide.md` (export).
- Example: `ExportLimitTest` of the pet store (`max-rows=10`: the 32 items are refused, a narrower search is written).

## 6-5. Nested rows for the holder's readers (`nestedRows`, 3.0)
> **Works in:** the v3 bundles - JPA, MyBatis, MongoDB, Java, Kotlin, layered, ddd, hexagonal, and the TypeScript types · **Not in:** 2.x-compatible output (output = 'v2'), 2.x generator

```yaml
config:
  nestedRows: public       # keep 3.0's default: a nested DTO only of an entity anyone may read (default from 3.1: readers)
```

- `readers` (the default from 3.1; 3.0's opt-in, issue 321) - the whole row of another entity (a nested DTO, `product: Product @dtype(ProductDTO)`) is
  carried when everyone who may read the holder may also read that entity (`read=MANAGER` carries `read=MANAGER`, `read=user` carries `read=user`, nothing carries `owner`).
  It is in what a read answers (read one, search, export) and sorts; create, update, a command, `me()` and sign-in answer it as `null` (the CoreService's
  `withoutCarriedRows`); a row a request sends never writes the referenced one. A 3.0 project that said `nestedRows: readers` keeps it; the line can go.
- `public` (3.0's default) - the row is carried only when anyone may read that entity; otherwise the field is left out with a warning that names the setting.
- It applies to the whole domain. A project coming from 3.0 sees its responses gain those fields: say `public` to keep them as they were.

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

It decides how the same model is read. **If you do not write it, the build's `architecture` and `language` decide** (layered Java: `jpa-springboot-v3` from 3.0.261028-rc3).

```yaml
elasticore:
  config:
    publisher:
      rules: [ddd-springboot]
```

### Available bundles

| Bundle | Content |
|---|---|
| `jpa-springboot` | The 2.x-compatible layered structure (`output = 'v2'`); layered Java without a setting gets `jpa-springboot-v3` |
| `ddd-springboot` | Aggregate boundaries, invariants, domain events, value objects |
| `hexagonal-springboot` | The above + inbound ports, hexagonal package layout |
| `jpa-springboot-kt` / `ddd-springboot-kt` / `hexagonal-springboot-kt` | The Kotlin versions of the three above |
| `ts-model` | Browser DTOs, enums, a typed REST client |
| `mybatis-springboot` | Layered Java on MyBatis (a domain with `mode: mybatis` gets it automatically - section 2.3) |
| `ddd-mybatis-springboot` / `hexagonal-mybatis-springboot` | The domain model / hexagonal on MyBatis (a domain whose `architecture` is ddd or hexagonal and which has `mode: mybatis` gets it automatically - section 2.3) |
| `mongo-springboot` | Layered Java on Spring Data MongoDB (a domain with `mode: mongo` gets it automatically - section 2.4) |
| `mongo-springboot-kt` | The Kotlin version of `mongo-springboot` (a Kotlin domain with `mode: mongo` gets it automatically - section 2.4) |

A bundle is a list of names of individual rule sets, so you can also combine them yourself:

```yaml
      rules: [jpa-springboot-kt, ddd-aggregate, ddd-reference, ddd-value-object-kt]
```

### Priority (4 levels)

```
1. Domain    publisher.rules of this env.yml       <- when only this domain is an exception
2. Project   -Delasticore.publisher.rules=...      <- usually set once here
3. Built-in default by architecture and language   <- if you write nothing (layered Java: jpa-springboot-v3)
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

### When the selected rules leave out what the DSL declares

A domain that declares `@access`, `@owner`, `@password`, `states:`, `@stamp`, `commands:`, `@deleted`, `@snapshot` or `@compute`
while the selected rules do not carry the rule set for it gets a DSL warning, and that part is not generated. For example, a
hexagonal domain with `publisher.rules: [ossie-export]` and an `@access` entity:

```text
[shop] Customer declares @access, @owner and @password (jpa-service-access), which the selected rules do not enforce - nothing is checked, derived or hashed. publisher.rules in env.yml replaces the architecture's bundle rather than adding to it, and its list does not name jpa-service-access. To add rule sets to the bundle, leave publisher.rules out and name them in rules = [...] in the elasticore { } block (-Delasticore.publisher.rules.add=...); to keep the list, name jpa-service-access in it.
```

The end of the warning fits where the selection came from (since 3.0.261124):

| The selection | What the warning tells you to do |
|---|---|
| A `publisher.rules` list in env.yml, or `-Delasticore.publisher.rules` | The list replaces the bundle: name the rule set in the list, or leave the list out and add the rule set with `rules = [...]` |
| A list that names `jpa-springboot` (the wizard wrote it before 3.0.261028-rc3) | Take `jpa-springboot` out of the list, and `output = 'v2'` out of the `elasticore { }` block |
| The 2.x code (`output = 'v2'`) | `elasticore { output = 'v3' }` (`elasticore-migration-guide.md`) |
| The layered bundle (`jpa-springboot-v3`) | `Declare architecture = 'ddd' or 'hexagonal', or add the rule set.` Instead, for `states:`: adding `ddd-state` to this bundle sets the initial state only; for `commands:` (since 3.0.261125): the commands are not served, and the TypeScript client offers none |
| MongoDB, for `states:`, `commands:` and derived values | MongoDB has no domain-model bundle: they are generated for JPA and MyBatis only |

It stays a warning: the build goes on. Before 3.0.261124 the warning always ended with the layered advice, and a list given as
`-Delasticore.publisher.rules` was not checked at all.

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
  domainService: io.example.domain.service   # Domain service package (yours; the architecture test checks it as domain code)
  inboundPort: io.example.application.port.in          # Inbound port (UseCase)
  domainRepository: io.example.domain.repository       # Repository port (owned by the domain)
  queryPort: io.example.application.port.out           # Query port (application-layer search, default service)
  persistence: io.example.infrastructure.persistence   # Adapter + Spring Data
```

`event` is used only when there is an aggregate root that declares `@event(...)`. If you do not
declare one, the event classes are placed in the `entity` package.

`domainService` is the package of the domain services you write: the architecture test checks it as domain code (with
`base:`, `base.domain.service`). **Do not put it in the same package as `service` (the
application service)** - a domain service belongs to the domain and an application service is a layer above it, so putting
them in the same place breaks the dependency direction.
The `domainService:` and `processManager:` sections are no longer read since 3.1 (`elasticore-dsl-guide.md` 8).

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
        comments = true                        // each column's label as its comment (off by default)
        oracleVersion = 23                     // the Oracle the tables are for: a boolean is BOOLEAN from 23 (unset: NUMBER(1))
    }
}
```

**Column comments** (`comments = true`): each column's label - the field's `-- label`, which the entity carries as Hibernate's `@Comment` - is said to the database: `COMMENT ON COLUMN` on H2, PostgreSQL and Oracle, the `MS_Description` property on SQL Server. A domain turns it on or off with `ddl.comments` in its env.yml. A label that changes is a migration that runs as written. Off by default, because a state written without comments gets every label in one migration the first time it is on. A label longer than Oracle keeps (4000 bytes in UTF-8) or SQL Server (7500 bytes) stops the run. Turning it off leaves the comments already in the database; no migration removes them. Columns only: an entity's label is not written.

**The Oracle version** (`oracleVersion = 23`): from Oracle 23 a boolean is a `BOOLEAN` column there, as Hibernate's `OracleDialect` maps it from that version; with `NUMBER(1)` on 23, `ddl-auto=validate` fails (`found [number (Types#NUMERIC)], but expecting [boolean (Types#BOOLEAN)]`). Below 23, and when it is not set - the default - a boolean stays `NUMBER(1)`, so nothing changes for a project that does not say it. Set it to the Oracle the tables run on: below 23 there is no `BOOLEAN` type. It changes only the boolean and only Oracle's DDL, once for the project (a domain's env.yml does not change it), and the schema file then says `for Oracle 23 or later`. The migration state records it, so setting it over Oracle tables already there is a migration of Oracle's alone, for **review**: Oracle changes a column's type only while the column is empty (ORA-01439), so each boolean column is added again as `BOOLEAN`, its values copied (1 is TRUE, 0 FALSE, an empty one stays empty), the old column dropped and the new one given its name; its `NOT NULL`, the indexes it is in and its comment are made again. It runs on the rows already there: check them, then uncomment it. A MyBatis domain binds a boolean as `jdbcType=BOOLEAN`, which the Oracle driver (ojdbc11 23) writes and reads with either column.

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
| An `@Index` name another table now takes (the table renamed, a `table.prefix` added, the index moved) | The old table's index of that name is dropped first (`drop index uk_coverage_product_code of coverage - jp_coverage takes its name`), then made on the new table: PostgreSQL, Oracle and H2 hold an index name once per schema. It drops an index, not rows; the old table's `DROP TABLE` still waits for review (since 3.0.261125) |
| Field or entity removed, type changed or narrowed, optional -> required, key changed | **Review**: as a comment at the end of the file - what can lose data or fail on existing rows |
| `oracleVersion` set to 23 or later (or unset again) over Oracle tables | Oracle only, **review**: each boolean column copied into a new one of the other type, which takes its place (above) |

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
reflect `@Size` and `@NotNull` too. It also writes the audit tables of Envers (`@Audited`) and `revinfo` - its `rev` an identity column on H2 and SQL Server, `revinfo_seq` on PostgreSQL and Oracle, as Hibernate numbers it - and reads settings such as
`audit_table_suffix` in `application.yml`. In a project that keeps 2.x code (`output = 'v2'`), the `elcoreDdl` task writes after `elcore`, and before `processResources` only when the build generates before it compiles (`generateBeforeCompile`, on by default).
Since the tables are read from the generated sources, `elcoreDdl` run before the code is generated (a fresh checkout whose generated
sources are git-ignored) stops the build and names the entities with no source, or with no column read - run `elcore3` (or `elcore`) first.

Verification: CI checks every time that the four JPA samples of ElastiCORE (2.x, hexagonal, Kotlin, pet store) start with Hibernate
`ddl-auto=validate` on the tables created by this DDL (H2; the pet store also on PostgreSQL, Oracle 23 with `oracleVersion = 23`, and SQL Server), that the MyBatis pet store runs with Flyway on
the three DBs, and that the change migrations (ALTER) run on the three DBs.

**Limits**: Oracle has no `TEXT`, so for JPA `columnDefinition = "TEXT"` (DSL `text`) becomes CLOB on Oracle and Hibernate
validate refuses it (a warning when `ddl.databases` names oracle) - on Oracle, use `clob` or a length. Names are not quoted, so
a name that is a reserved word of the DB cannot be used (a MyBatis domain gets a warning).

---

## 9-2. Generated-file merge - follow the DSL while keeping your edits (3.0)

You may edit a generated file. The next build **keeps your edits and also applies the DSL change** - the way git merges two branches.
It does not matter whether you added a comment, added a statement, or changed a line. Since: 3.0.261030-rc5

**Scope**: all generated files - Java and Kotlin sources, MyBatis mapper XML, `orm.xml` (`mapping: xml`), `db/schema-{db}.sql`,
`{domain}-schema.sql`, `*-http-services.properties`, `i18n/*-messages.properties`, `i18n/*-labels*.properties`, gRPC `.proto`, the TypeScript client (`.ts`).
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
warns what to move by hand - developer code never breaks during a build. Leave the marker line as it is. A controller kept this way
that still serves an endpoint the DSL took away (`@expose(methods)`, `@readonly`) is named in a warning, endpoint by endpoint.

**Turn it on**: it is on by default for 3.0 code. To turn it off, `elasticore { merge = false }` - from then on a file without a marker 
is treated as the developer's own and skipped (if you delete it, it is generated anew with the marker). `output = 'v2'` (2.x code) is off by default - exactly as 2.x used it.

**`.elasticore/generated-base.zip` - commit it together with the generated files.** It is one file (an uncompressed zip) holding, per file, exactly what the generator wrote last (before merging).
It is the base of the merge, so for a file edited by someone who cloned (even with `--depth 1`) to be merged, it must be in the repository. If the content is
the same, the bytes are the same, so the repository grows only as much as the generated output changed. The `.elasticore/.gitattributes` next to it marks it binary (and keeps LFS off it), and `.elasticore/.gitignore` takes it back in where a project ignores `*.zip` - commit both.
**The base and the generated files travel together** - commit both, or, in a project that keeps generated files out of git, leave out `.elasticore/` too.
If you commit only the base, after a pull the old local generated file looks like an "edited file" and is not rewritten.
It used to be named `.elasticore/cache`. The first build that writes renames it (`[elcore3] .elasticore/cache was renamed to .elasticore/generated-base.zip - commit both changes ...`) - commit the delete and the new file; nothing else to do. If both are there (a branch brought the old one back), the new one is read and the build warns until you delete the old one.

| Case | What the build does |
|---|---|
| First generation | Writes it and puts it in the cache |
| Unedited file, DSL changed | Writes it anew |
| Edited file, DSL unchanged | Leaves it as it is - `UNCHANGED(your edits kept)` |
| Edited file, DSL changed | Merges - `MERGED(your edits kept)` |
| You and the DSL made the same change, apart from blank lines or spaces at the end of a line | Merges, keeping your layout - not a conflict (indentation and spaces inside a line still count) |
| The DSL also changed a line you edited | **Conflict** - leaves `<<<<<<< yours` / `=======` / `>>>>>>> elasticore` in the file and the build fails. It keeps failing until you clean up the markers, and what you cleaned up becomes the base from then on |
| You deleted the file | Generated again - the way to get it back as generated |
| The DSL no longer produces it | If unedited, it is deleted; if edited, it is kept with a warning (from then on it is the developer's file). Even if unedited, a class that remaining sources still use (and the mapper XML with the same name) is kept and the file that uses it is reported (`still uses it - kept`) - it is deleted in the build after nothing uses it any more |
| You changed `language` (java <-> kotlin) | The files of the old language go as above - an unedited one even while sources name its class, since the new language's file declares the same class. An edited one is kept with a warning that names that file: move what you wrote there and delete the old one (until then the build has the class twice) |
| A different file where the cache has none | Not overwritten, reported (if the cache file is missing, the summary tells you to fetch it from git) |
| A file generated with a first-line marker (older version, 2.x) | "Projects that had markers" below |

**Projects that had markers** (files that an earlier version wrote with the first line `//ecd:` - including projects upgraded from 2.x): there is no separate command to migrate.
When the merging build handles such a file for the first time, it removes the marker and puts the file in the cache **file by file** - but only when that file's original generated version (the base)
**can be confirmed by the marker's hash**. If it merged with a guessed base, unrelated changes would be mixed in silently.

| File with a marker | First merging build |
|---|---|
| Not edited (the hash equals the content) | That content is the base. Removes the marker and writes per the DSL. The user block `// !----` is left out of the merge like the hash and goes back to where 2.x puts it when it rewrites (the import block below the `package` line, the others before the closing line) - from the next build on it is the same as an edit. If the DSL did not change this file, only the marker is removed and the block stays where it is |
| Edited - what this build produces equals the hash (model and generator unchanged) | That is the base. Removes the marker and keeps your edits |
| Edited - the first build that upgrades a 2.x project to `output = 'v3'` | The build produces the 2.x code once more in memory only (`[elcore3] upgrade : ...`), takes the one whose hash matches as the base, and merges your edits into the 3.0 code. This build handles all domains at once (it stops with `-Pdomain`). See beforehand what it would do, with nothing written: `./gradlew elcoreMigrationCheck` (`build/elasticore/migration-check.md`, Java only; `elasticore-migration-guide.md`) |
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
  1. For `.elasticore/generated-base.zip`, pick one side - `git checkout --ours .elasticore/generated-base.zip`.
  2. For a conflicted generated file, pick **the same side as the cache** - `git checkout --ours <file>`. When you build, the other side's DSL changes come in again. If you pick
     the side different from the cache, the DSL changes of the cache's branch look like "things the developer deleted" and may silently drop out.
  3. **If both branches edited the same generated file by hand**, merge that file yourself - keep the generated lines and the edited lines of both sides. If you
     pick only one side, what the other side edited is lost.
  4. **A branch from before the rename** still has `.elasticore/cache` (a modify/delete conflict on it). The base is the side you picked in step 1: if that is the branch's side, `git checkout <branch> -- .elasticore/cache` and `git mv -f .elasticore/cache .elasticore/generated-base.zip`; otherwise `git rm .elasticore/cache`.
- If the base cannot be read (corrupt), nothing is written and the build fails. Fetch it again from git. If you delete only the `generated-base.zip` file, the next build reports, without touching them, all files that are not
  in the base and differ from what is generated now - delete the files you want to get as generated. If you delete the whole `.elasticore/`
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
English guides. Without `guides`, Korean guides already in the blueprint
(5% or more of their characters Hangul) stay Korean, and the build says so (from 3.0.261113-rc13; before, an upgrade replaced them with the English ones silently).

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
