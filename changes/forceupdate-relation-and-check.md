### Bug Fixes (2.x generator - `@forceupdate` on a relation)

#### An entity's relation or list field with `@forceupdate` gives a mapper that compiles
- Why (issue 166): the 2.x generator's entity-to-DTO line for a field read through a mapper method (a relation, a
  list of DTOs) opened that call - `to.setCustomer(toDTO(from.getCustomer());` - and never closed it: the line that set
  the closing bracket was commented out. The mapper did not compile. The rule engine's 2.x-compatible code wrote
  `setVal(from.getCustomer(), to::setCustomer, isSkipNull, e->toDTO(e,c));` for the same model.
- After: such a field goes through `setVal` in the 2.x generator too (both variants, with and without the
  EntityManager), the line the 2.x-compatible code writes. A value field with `@forceupdate` is assigned straight
  through as before. On the update side (DTO to entity), `@forceupdate` on a relation keeps what it did.
- 2.x: only output that did not compile changes.
- Checked: `ForceUpdateRelationTest` runs the 2.x generator and the 2.x-compatible rules on the same model: the lines
  are the same and balanced; it fails on the generator before the change.

### Bug Fixes (3.0 code - API errors)

#### A CHECK violation is answered 400 `check`
- Why (issue 167): the generated `XxxApiErrors` knew NOT NULL, UNIQUE and FOREIGN KEY. A row breaking a CHECK
  constraint (one a migration or Hibernate wrote) was answered 409 `conflict` - "a unique value is already taken, or
  the row is still referenced" - and on SQL Server 409 `referenced`, as its 547 is the code of a FOREIGN KEY and a
  CHECK alike.
- After: a kind of its own, answered 400 `check` ("A value is outside what the column allows."), as a value breaking
  its constraint is: 23513 (H2), 23514 (PostgreSQL), 23000 with 2290 (Oracle), 3819 (MySQL), and 547 when SQL
  Server's message names a CHECK (it does in every language); any other 547 stays `referenced`. The code is in
  every domain's message bundle (`error.check`). Java and Kotlin.
- `XxxApiErrors` is generated once: a file never edited is written again with it; an edited one keeps its own, and
  takes the case over by hand.
- Checked: the pet store makes each database raise a CHECK, a UNIQUE and a FOREIGN KEY violation and hands Spring's
  exception to the generated `PetstoreApiErrors`: 400 `check`, 409 `unique`, 409 `referenced` - on H2 locally, and in
  CI on Oracle, PostgreSQL and SQL Server (`IntegrityKindCheck`).
- Files: `MapperSrcPublisher`, `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `ErrorCatalog`, the rule guide (en, ko).
