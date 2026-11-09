### Bug Fixes (project DDL - `elcoreDdl` before the code is generated)

#### `elcoreDdl` run before generation stops, naming the entities it could not read, instead of writing tables without columns
- Why: a JPA domain's tables are read off its generated entity sources. In a checkout where those have not been
  generated yet (the pet store's are git-ignored), `elcoreDdl` wrote `schema-*.sql` from whatever was there and said
  `WROTE`: the pet store got `ps_order_line` and `ps_purchase_order` with no column - from the two behaviour classes
  that are committed, without their generated bases - and none of its other five tables. That is not valid SQL on any
  of the four databases, and nothing said it was incomplete (#107).
- What: `DdlRun` checks each JPA domain before it writes. An entity of the model with no source in its entity package
  (`<Name>.java` or `.kt`, under any source root of the run), and a table read with no column, stop the run, and
  nothing is written for that schema:
  `[elcore3] ddl: the tables are read off the generated entity sources, and no source for dgshop.Customer,
  dgshop.PurchaseOrder - run elcore3 first; nothing was written for the project`
  (or `no column read for purchase_order (io.acme.dgshop.entity.PurchaseOrder)` where only the class written once is there). After
  generation the same task writes what it did. `DdlRun.Part` carries the model's entity names for it.
  Whether `elcoreDdl` should depend on the generation is left open; the refusal closes the issue.
- Checked: `GeneratedMergeTest.theDdlAloneBeforeTheCodeIsRefused` - a hexagonal JPA project: `elcoreDdl` alone first
  exits 1 with the message above and writes no `schema-h2.sql`; after `elcore3`, with `PurchaseOrderBase.java` and the
  schema deleted, it exits 1 naming `purchase_order` and writes nothing; generated again, it writes the schema
  `elcore3` wrote, byte for byte. Without the change the first and second runs exit 0.
- Files: `DdlRun.java`, `RulesGenRunner.java`, `GeneratedMergeTest.java`, `elasticore-env-guide.md` (en, ko)
