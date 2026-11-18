### Bug Fixes (layered - a model written for ddd/hexagonal)

#### Layered with `referenceColumn: short`: the join column was checked, and the unused setting is said
- Why: a run of the guide's order model in layered showed `@ManyToOne private Customer customer;` with no
  `@JoinColumn`, while `referenceColumn: short` promises `customer_id`; it was not known which of the entity, the DDL
  and the Ossie export would disagree.
- Checked by running (3.0.261119, layered, Boot 3.5.16, H2, `rules = ['ossie-export']`, `ddl { databases = ['h2'] }`):
  Hibernate created `sh_purchase_order (..., customer_customer_id bigint, ...)` with the foreign key on
  `customer_customer_id`; `db/schema-h2.sql` has `customer_customer_id BIGINT` and its foreign key; `semantic_model.yml`
  has the field `customer_customer_id` and the relationship `from_columns: [customer_customer_id]`. All three agree;
  nothing was wrong. `referenceColumn` is read by ddd and hexagonal only, as the env guide says - but nothing said
  it in the build.
- Now: a layered (v3, Kotlin) build of a domain that sets `referenceColumn: short` or `field` warns
  `[shop] referenceColumn: short is read by architecture = 'ddd' and 'hexagonal' only - the selected rules keep each
  @ManyToOne on JPA's join column (customer_customer_id for customer: Customer), in the entity, the DDL and the Ossie
  export alike.` Generated code does not change. The env guide's marker says "(warning)" for layered.
- Checked: `LayeredReferenceTest` - layered warns, hexagonal does not; fails without the change.
- Files: `pub/rules/verify/RulesGenRunner.java`, `LayeredReferenceTest.java`, `elasticore-guide/elasticore-env-guide.md`
  and `ko/`.

#### Layered with `@owner(customerId)` through `customer: Customer @ManyToOne`: the warning says what to write
- Why: the annotation guide's `@access`/`@owner` works in v3 and Kotlin, any architecture, and its example is an order
  with `@owner(customerId)`. In ddd and hexagonal the reference `customer: Customer` is held as the field
  `customerId`; in layered it stays an object, so `@owner(customerId)` names no field and the build said only
  `... no @owner field resolves (@owner(customerId)) - they are the administrator's alone until @owner names the
  identity that holds the owner.` - the owner's own orders were admin-only (404 to the owner, search 403).
- Now: the same warning goes on `customerId is the identity of customer: Customer, which only architecture = 'ddd'
  and 'hexagonal' hold as a field. Here, declare it on the column the relation joins on - customerId: long
  @db(customer_customer_id) - and join the relation by it: customer: Customer @ManyToOne @joinBy(customerId); then
  @owner(customerId).` (the column read from the target's key). Layered does not hold references by id on its own:
  that is the ddd/hexagonal rule set, and turning it on in layered would rename columns of existing layered tables.
- Checked: `LayeredReferenceTest` - the full warning in layered, none once the two lines it names are written, none in
  hexagonal; fails without the change. Run (3.0.261119, layered, H2) with those two lines: alice created an order
  sending `customerId: 2` and it was stored with her id 1; she read it (200) and found it (1 row); bob got 404 and an
  empty search.
- Guides: the annotation guide's `@access` table (the row "`@owner` does not resolve"), English and Korean.
- Files: `pub/rules/base/ServiceAccessRules.java`, `LayeredReferenceTest.java`,
  `elasticore-guide/elasticore-annotation-guide.md` and `ko/`.