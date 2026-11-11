### Bug Fixes (MyBatis - database ports and the aggregate's delete)

#### A database port's conditional blocks nest, under MyBatis and in the port runtime
- Why: the first `/* if-end */` closed the outer block. The lines after an inner block were always kept, and an inline
  `/* if:name */` inside a block was kept whatever its value. The guide listed nesting as not supported.
- What: each `/* if-end */` closes the innermost open block; an inner block is read only when the outer one is kept,
  and an inline condition inside a block is read as one outside it is. The generated mapper XML nests `<if>`, and the
  runtime (`SqlPortGateway`, JPA and JDBC) reads the SQL the same way. A line kept by an inline condition is cut at the
  condition, not one character before the first `/*`.
- Checked: `MyBatisGenerationTest` (nested `<if>` in the XML), `MyBatisPortSqlTest`, `JdbcDbmsGatewayTest.blocksInsideBlocks`
  (H2: four inputs, one of which the old reading answered wrong), `DomainOnDatabase.theDbmsPortsSql` in
  `elasticore-template-ddd-mybatis` (H2 locally; Oracle, PostgreSQL and SQL Server in CI).

#### A port method may take a `Pageable`, whose page and `Sort` are the call's
- Why: the port interface did not import `Pageable`, so a method that took one did not compile; under MyBatis a
  parameter named `pageable` clashed with the adapter's own variable. The runtime proxy read page 0 of 50 when the
  `Pageable` came with other parameters, and neither read its `Sort`.
- What: the v3 and Kotlin port interfaces import `Pageable` and `Sort` when a method takes one (the 2.x-compatible
  output is unchanged). The MyBatis adapter's own names start with `port` (`portPage`, `portTotal`, `portRows`,
  `portRow`). The page is the `Pageable` parameter's, in the runtime too. The order is the input's sort code, else the
  `Sort` of a `Pageable` or `Sort` parameter - its properties are column names or labels, checked term by term as a sort
  code is (`PortRows.orderBy`, `SqlPortGateway.sortOf`).
- Checked: `PortModelImportsTest` (Java and Kotlin import it, 2.x does not), `MyBatisGenerationTest`,
  `DbmsPortInputTest.aPageableParameterIsThePage`, `JdbcDbmsGatewayTest.aPageablesSortOrdersTheRows` (an injected
  property is refused), `DomainOnDatabase.theDbmsPortsSql` (`customersByPage` with `Sort.by(DESC, "firstName")`).

#### Under MyBatis the call's sort goes before the SQL's own `ORDER BY`, and an `ORDER BY` inside `/* if: */` pages on SQL Server
- Why: the mapper appended the call's sort only to SQL without an `ORDER BY`; the runtime puts it first and keeps the
  SQL's terms as tie breakers. An `ORDER BY` inside an `/* if: */` line or block was taken as always there, so when its
  condition was false a page reached SQL Server with no order, which it does not page. The guide said not to do it.
- What: the sort goes first in the SQL's own `ORDER BY`, and becomes the `ORDER BY` where the SQL has none or its
  conditions leave it out; there, SQL Server gets `ORDER BY (SELECT NULL)` when the call names no sort either. Every
  read of a port that carries a sort takes it.
- Checked: `MyBatisGenerationTest` (the XML of an own and a conditional `ORDER BY`), `DomainOnDatabase.theDbmsPortsSql`
  (`customersMaybeOrdered` with and without its condition).

#### A port's `Map` row has every column under MyBatis, a NULL one as null
- Why: MyBatis leaves a NULL column out of a map it reads, and answers a row whose columns are all NULL as null; the port
  runtime has every column. A `List<Map<String,Object>>` method also did not compile (`Map.class` against
  `Map<String,Object>`).
- What: the port's statements answer `io.elasticore.springboot3.bean.PortRow`, and the runtime library's MyBatis
  interceptor `PortRowColumns` (registered by `ElastiCoreMyBatisAutoConfiguration`, applied by the MyBatis Spring Boot
  starter) reads the column labels as MyBatis opens the result set and puts the missing columns in, in the order the
  SQL selects them. No other statement is touched. A method that answers a generic type reads it by its class
  (`PortRows.listOf` / `firstOf`), a page of one too.
- Checked: `PortRowColumnsTest` (H2, plain MyBatis, behind another plugin: a NULL column, a row of NULLs, a statement
  left as MyBatis reads it), `DomainOnDatabase.theDbmsPortsSql` (`customerRows`).

#### The aggregate's delete locks the row with a read, not an UPDATE
- Why: `lockVersion` was `UPDATE t SET v = v WHERE id = ? AND v = ?`. MySQL and MariaDB with `useAffectedRows=true`
  count changed rows, so they answer 0 and the adapter refused every delete of a current copy as stale (409).
- What: `SELECT 1 FROM t WHERE id = ? AND v = ? FOR UPDATE` (`WITH (UPDLOCK, ROWLOCK)` on SQL Server); the mapper answers
  `Integer`, null when the row has another version. The row stays locked to the end of the transaction, as before.
- Checked: `MyBatisGenerationTest`, `DomainOnDatabase.aStaleDeleteIsRefused` (H2 locally; Oracle, PostgreSQL and SQL
  Server in CI).

- Files: `MyBatisPortRules.java`, `MyBatisMapperRules.java`, `DddMyBatisPorts.java`, `PortBaseRules.java`,
  `SqlPortGateway.java`, `DbmsServiceProxyFactory.java`, `PortRows.java`, `PortRow.java`, `PortRowColumns.java`,
  `ElastiCoreMyBatisAutoConfiguration.java`, the tests above, `elasticore-template-ddd-mybatis` (`report_port.yml`,
  `dto.yml`, `DomainOnDatabase.java`), `elasticore-dsl-port-guide.md` (3.4, 3.6, 3.11, 3.13) and
  `elasticore-env-guide.md` (2.3), en and ko
