### Improvements (`@deleted` - `mapping: xml` on Spring Boot 4)

#### `@deleted` works with `mapping: xml` on Spring Boot 4 (Hibernate 7)
- Why: `@deleted` with JPA's `mapping: xml` stopped the build. Hibernate 6 parses the `<sql-delete>` and
  `<where>` elements of its extended orm.xml schema but applies them nowhere - in its 6.4, 6.5 and 6.6 jars
  only the JAXB model and the legacy hbm.xml code name them; the row was removed (run on
  `elasticore-template-ddd`, Hibernate 6.5.2). Hibernate 7 processes orm.xml with Hibernate Models, whose
  `ManagedTypeProcessor` and `CommonPluralAttributeProcessing` apply `<sql-delete>` and `<sql-restriction>`.
- After: on Spring Boot 4 the generated orm.xml is written in Hibernate's schema
  (`http://www.hibernate.org/xsd/orm/mapping`, version 7.0) where a class needs it: `<sql-delete>` after the
  entity's table, `<sql-restriction>` in a collection of `@deleted` rows; the domain classes carry neither
  annotation nor import. A Hibernate `@SQLRestriction` / `@SQLDelete` written on a class with `@jpa:` moves into
  orm.xml the same way on Spring Boot 4 (Hibernate 7 applies it there too); on Spring Boot 3 it stays on the
  class, as before. `@deleted` with `mapping: xml` on Spring Boot 3 still stops the build, now saying it needs
  Spring Boot 4.
- Checked: the pet store (Spring Boot 4, Hibernate 7) switched to `mapping: xml` for the run, with
  `PurchaseOrder` and `OrderLine` made `@deleted` as well: `SoftDeleteTest`, `ShopFlowTest`,
  `SchemaValidateTest` and a run that took a line out of an order (kept with its time, and the order read
  back with one line) - 23 of 23; the sample was put back. `SoftDeleteGenerationTest` checks the orm.xml of a
  Spring Boot 4 `mapping: xml` project (schema, `<sql-delete>`, a collection's `<sql-restriction>`, classes
  without Hibernate). Not kept as a sample: the DDL that `elcoreDdl` writes under `mapping: xml` reads no
  `money` / `quantity` converter from orm.xml and writes those columns as text - a limit of its own, older
  than this change.
- Files: `OrmMapping`, `SoftDelete`; the annotation guide (en and ko).
