### Bug Fixes (runtime - DBMS ports)

#### A port method whose only parameter is a list of values binds it by name
- Why (issue 164): `findByIds(ids: list<long>)` with `IN (:ids)` found nothing and `deleteByIds` deleted nothing.
  With one parameter, the proxy bound it by name only when it was a single value; a `List` was taken for an object,
  read by its getters (`getIds()` on an `ArrayList`), and `:ids` bound as null - with a stack trace and no error.
  With a second parameter it worked.
- After: a lone collection or array of simple values (`List<Long>`, `Set<String>`, `long[]`) is bound by its name, as
  one value is. A list of DTOs or maps stays the input of a batch.
- Checked: `DbmsPortInputTest` (through the proxy: a list, a `String[]`, a `long[]`; a delete by ids is one statement,
  a list of rows still a batch); `JdbcDbmsGatewayTest` (`IN (:ids)` on H2 answers 1 and 3).

#### An enum field of a port's result row is read as the enum
- Why (issue 165): `SqlPortGateway.convertValue` knew numbers, booleans, dates and text, and threw for an enum, so a
  result DTO with an enum field failed every call (`Failed to map tuple to DTO`), whether the column held the `@db`
  code or the constant's name. The JPA, JDBC and MyBatis port paths all map rows through it.
- After: a number is the ordinal; text is what the enum's `fromCode(String)` answers (the generated enums have it; a
  Kotlin enum's companion too), otherwise the constant of that name; anything else is an error naming the value. A
  port method answering `List<SomeEnum>` reads the first column as one value.
- Checked: `JdbcDbmsGatewayTest` - an `@db` code, a name, an ordinal, a list of enum values, on H2.
- Files: `DbmsServiceProxyFactory`, `SqlPortGateway`, the port guide (en, ko).
