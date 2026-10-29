# elasticore-sample-petstore-mybatis

The YAPS pet store kept in MyBatis rather than JPA: the pet store's model (`elasticore-sample-petstore`) with
`mode: mybatis` in `env.yml`, generated as layered Java on Spring Boot 4, compiled and run on every build - on H2,
and in CI on Oracle 23 Free, PostgreSQL 16 and SQL Server 2022.

What it shows the MyBatis code doing:

| Model | What is generated |
|---|---|
| `unitCost: money(10.2)`, `quantity: quantity!` | one DECIMAL / INTEGER column each, through the runtime's `MoneyTypeHandler` / `QuantityTypeHandler` |
| `homeAddress: Address! @Embedded`, `creditCard: CreditCard! @Embedded` | the value's fields as columns of the row, read in the row and in a joined row; the DTO carries the value |
| `lines: List<OrderLine> @OneToMany(mappedBy="order")` | the lines read for a page of orders in one statement; written, replaced and deleted with their order (`OrderLine` has a mapper, no service) |
| `customerId: long` + `customer: Customer @ManyToOne @joinBy(customerId)` | the order holds its customer's id, the relation is joined on it - `@owner(customerId)` on the layered bundle |
| `@Index(..., unique="true")` | a unique index; a taken login is a 409 |

The build's `elasticore { ddl { databases = [...]; migration = 'flyway' } }` writes the tables for each database
(`db/schema-{db}.sql`) and the Flyway migrations that build them (`db/migration/{db}/V..__init.sql`, the next change of
the model a migration of its own), which the application runs at start-up (`spring.flyway.locations:
classpath:db/migration/{vendor}`). The migrations and `db/migration/schema-state.json` are committed: they are the
database's history. `db/catalog/R__catalog.sql` is the catalog, without generated keys, so the identity columns number it
alike on every database.

The pricing - catalog prices, VAT, discount, delivery to the customer's home - is `PurchaseOrderService`'s, the one
hand-written service: the layered bundle does not run the `@compute` and `states:` the hexagonal sample declares.

```bash
./gradlew :elasticore-project:elasticore-sample-petstore-mybatis:test      # H2
./gradlew :elasticore-project:elasticore-sample-petstore-mybatis:run       # http://localhost:8080 - admin/admin, user/user

# On a database: an administrator's connection; each test class makes a schema (user, database) of its own.
ELASTICORE_IT_POSTGRESQL_URL=jdbc:postgresql://localhost:5432/postgres ELASTICORE_IT_POSTGRESQL_USER=postgres \
ELASTICORE_IT_POSTGRESQL_PASSWORD=... ./gradlew :elasticore-project:elasticore-sample-petstore-mybatis:dbTest
```

The same with `ELASTICORE_IT_ORACLE_*` (`jdbc:oracle:thin:@localhost:1521/FREEPDB1`, `system`) and
`ELASTICORE_IT_SQLSERVER_*` (`jdbc:sqlserver://localhost:1433;encrypt=true;trustServerCertificate=true`, `sa`).

The generated trees are not committed (`.gitignore`); the blueprint, the hand-written classes and the tests are.
