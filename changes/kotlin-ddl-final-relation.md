### Bug Fixes (project DDL - a Kotlin relation read through its id)

#### The project DDL of a Kotlin entity has the foreign key of a relation referenced by its id
- Why: the project DDL (`ddl { databases }`) reads the generated entity sources, Java with JavaParser and Kotlin
  line by line (`JpaSchemaReader`). Under the DDD reference rules a relation to another root is read through its
  id, and the Kotlin rules write that relation as `final var department: Department? = null`. The Kotlin line
  pattern took `private`, `open`, `override` and the like before `var`, but not `final`, so the relation was not
  read at all. Its column was still there, from the id field (`departmentDeptId`), but the foreign key was not:
  in `elasticore-template-kt` none of the nine relations read through an id had one, while the Java output
  writes them (`fk_shop_products_category` in `elasticore-template-ddd`). Hibernate, mapping the same entity,
  makes the constraint.
- What: `final` is one of the modifiers the pattern takes. A Kotlin relation read through its id gets its
  foreign key, and its column stands where the relation is declared, as in the Java output; the columns
  themselves are unchanged.
- In `elasticore-template-kt` the four `db/schema-*.sql` gain nine foreign keys (`fk_hr_employee_department`,
  `fk_shop_order_customer`, `fk_shop_products_category` and six more) and those columns move. A project that
  writes migrations (`ddl { migration }`) gets one that adds the foreign keys, which runs as written, as a new
  foreign key does; the order of a table's columns is not compared. Java output, and Kotlin output without such
  a relation, are unchanged.
- Checked: `JpaSchemaReaderTest.aKotlinRelationReadThroughItsIdKeepsItsForeignKey` - an entity written as the
  Kotlin rules write it; it fails without the change. `elasticore-template-kt` builds with its tests
  (`SchemaValidateTest` among them) on the regenerated schemas.
- Files: `JpaSchemaReader.java`, `JpaSchemaReaderTest.java`, `elasticore-template-kt` `db/schema-*.sql`
