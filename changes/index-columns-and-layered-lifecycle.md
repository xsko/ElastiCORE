### Bug Fixes (v3 - build-time warnings)

#### An `@Index` naming a column its table does not have is said at build time (#323)
- Why: `@Index(columnList = "product_code, ...")` on an entity whose reference `product` is the column
  `product_product_code` in layered passed generation and compilation; Hibernate's schema creation then failed on
  it and the table was missing at run time - every request on the entity failed. The column a reference gets depends
  on the architecture, so the same model was right in one project and wrong in another.
- Now: after generation the tables are read off the generated entity sources - Java and Kotlin, every architecture,
  references, embedded values and parents included, the way the DDL reads them - and an index column the table does
  not have is a DSL warning that names the columns there are, with the reference's column when one ends with the name:
  `[product] Coverage @Index(uk_coverage_product_code): coverage has no column product_code (perhaps
  product_product_code) - creating the table fails on it; name the columns as the table has them: coverage_id,
  product_product_code, coverage_code`. It runs whether or not the project writes a DDL, and only when an entity
  declares `@Index`. A MyBatis domain, whose indexes exist only in the DDL it writes, is checked when it writes one.
  MongoDB is not checked: an index there may name a path into an embedded document. Nothing generated changes; the
  build does not fail.
- Checked: `IndexColumnsTest` - the reported model in layered Java (no DDL, and with a DDL: said once), right columns
  by column or field name (nothing said), hexagonal Java and layered Kotlin, MyBatis with and without a DDL.
- Files: `DdlRun` (`checkIndexes`, `unknownIndexColumns`; the entity sources read once for the DDL and the check),
  `RulesGenRunner` (`recordDdl`, `writeDdl`).

#### `states:` in layered: the warning says what adding `ddd-state` gives, and a lifecycle left unwritten is said (#320)
- Why: a layered project declaring `states:` was told "Declare architecture = 'ddd' or 'hexagonal', or add the rule
  set." Adding `ddd-state` to the layered bundle sets only the initial state - the commands, the moves they check,
  `@stamp` and the `delete:` guard are written for a root with a behaviour class - and the missing commands were then
  said nowhere. In `ddd` and `hexagonal` too, an `@editable` root has no behaviour class, which no warning named.
- Now: for `ddd-state` in a layered bundle the warning ends "...adding it to this bundle sets the initial state only:
  the commands, the moves they check, @stamp and the delete: guard are written for a root with a behaviour class - a
  root that is not @editable, with architecture = 'ddd' or 'hexagonal'." (the other rule sets keep "or add the rule
  set", which works - checked for `ddd-derive`). An entity whose lifecycle has moves but no behaviour class gets
  `[Product] Device states.status: the commands approve, reject, revoke are written only for a root with a behaviour
  class (a root that is not @editable, with architecture = 'ddd' or 'hexagonal'); Device gets its initial state only.`,
  and the `@stamp` and `delete:` warnings name the same condition instead of "(@root in a DDD bundle)". Generated code
  does not change. Writing the lifecycle in layered itself is the feature proposed in #320, under review.
- Checked: `UnenforcedWarningTest` (the layered advice for `ddd-state` and for `ddd-derive`, adding `ddd-derive` to a
  layered bundle writes the value), `IndexColumnsTest` (the reported `Device` with `ddd-state` added and without),
  `StateStampTest`.
- Files: `RulesGenRunner.unenforcedWarning`, `DddStateRules` (`warnUnmoved`, `SPLIT_ROOT`).
