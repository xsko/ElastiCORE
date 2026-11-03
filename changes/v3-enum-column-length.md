### Bug Fixes (v3 entity - an enum's column length)

#### The column of a required, `@unique` or not updatable enum field is as long as its `@db` code
- Why: an enum's column takes the length of the enum's `@db` field (`code: string(3)` -> `@Column(length = 3)`).
  2.x keeps that length apart from the column's other attributes and writes it only when there are none,
  and the rule engine took it over as it was. A field marked required (`boardType: BoardType!`), `@unique` or
  `@updatable(false)` got `@Column(nullable = false)` and so on without a length: Hibernate and the project DDL
  made the column `VARCHAR(255)`. In a real 3.0 project, twelve required enum columns of the PostgreSQL schema were
  `VARCHAR(255)`.
- What: the v3 output adds the length to the column's other attributes -
  `@Column(nullable = false, length = 3)`, `@Column(length = 3, unique = true)`,
  `@Column(updatable = false, length = 3)`; the schema has `VARCHAR(3)`. A column with nothing else said is
  `@Column(length = 3)` as before. Java and Kotlin. MyBatis's schema already took the code's length.
- The 2.x generator and the 2.x-compatible output (`output = 'v2'`) are unchanged: the 2.2 generator, run on the
  same model, writes `@Column(nullable = false)`, `@Column(unique = true)` and `@Column(updatable = false)`, as the
  compatible output does.
- For a database made from the v3 output before, the column is narrower now: a schema migration
  (`ddl { migration }`) writes the change for review.
- Checked: `EnumColumnLengthTest` - the v3 entity in Java and Kotlin, the PostgreSQL schema without
  `VARCHAR(255)`, the compatible output as 2.x writes it.
- Files: `JpaEntityBaseRules.java`, `EnumColumnLengthTest.java`, `elasticore-annotation-guide.md` 5 (en, ko)
