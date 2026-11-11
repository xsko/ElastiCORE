### Bug Fixes (v3 DTO - a fraction in `@min` / `@max` on a `double` or `float`)

#### A bound with a fraction on a `double` or a `float` is a `@DecimalMin` / `@DecimalMax`, so the DTO compiles
- Why: `@Min` and `@Max` take a `long`. `rating: double @min(0.5) @max(5.0)` was written as `@Min(0.5)` / `@Max(5.0)`,
  and the DTO did not compile (`incompatible types: possible lossy conversion from double to long`) - in every v3
  bundle (layered, hexagonal, ddd) and in Kotlin. The guides told the reader to write whole numbers only, and also
  said that `bigdecimal @min(0.01)` failed, which it never did: `bigdecimal` and `money` were already a
  `@DecimalMin` / `@DecimalMax`.
- What: `DtoBaseRules.bound` writes a bound with a fraction on a `double` or a `float` (boxed or not) as
  `@DecimalMin("0.5")` / `@DecimalMax("5.0")`, with its message when one is given. Hibernate Validator checks these
  on any `Number`. A whole bound (`@min(0)`, `@max(-10)`) stays `@Min` / `@Max`, so a project that compiled before
  generates what it did. The 2.x-compatible output (`output = 'v2'`) keeps what 2.x writes, `@Min(0.5)`.
- Checked: `FieldBoundTest` - on a `double`, a `float`, a `Double` and a `Float`, a fraction is a decimal bound, with
  and without a message; a whole bound on a `double` stays `@Min(0)` / `@Max(-10)`; the compatible output keeps
  `Min(0.5)`. In `elasticore-template-ddd` the catalog's `Book` has `rating: double @min(0.5) @max(5.0)`:
  `CatalogApiTest.fractionalBoundOnADouble` sends 5.5 and 0.4 (400) and 4.5 and 5.0 (200, stored). Before the change
  the module did not compile. The module's `db/schema-*.sql`, `schema.yml` and `semantic_model.yml` are regenerated
  with the column.
- Files: `DtoBaseRules.java`, `FieldBoundTest.java`, `elasticore-template-ddd` (`catalog/model/entity.yml`,
  `CatalogApiTest.java`, generated schemas), `elasticore-dsl-guide.md` 10.2 and 10.5, `elasticore-annotation-guide.md`
  8.1 and 8.7 (en, ko)
