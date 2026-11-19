### Bug Fixes (search - `@search(in)` on a list of values)

#### A search DTO's `List<Enum> @search(in)` searches the column, `status in (...)`
- Why: the DSL guide's own search DTO example (4. DTO definition) declares `status: List<Status> @search(in)`.
  With an entity whose `status` is one enum, every JPA mapper (not MyBatis, not MongoDB) read the list as a collection of the entity and joined it:
  `Join<PurchaseOrder, OrderStatus> join = root.join("status"); return join.in(status);`. The 2.x generator
  (`elcore`, `output = 'v2'`) and the Kotlin code compiled, and the search failed when it ran -
  `Attribute 'Customer#customerType(BASIC)' is not joinable` on H2. The 3.0 Java code (layered, ddd, hexagonal)
  did not even compile: the rule engine wrapped the lambda's parameters twice, `((root, query, criteriaBuilder)) ->`,
  for every join it writes - the `in` join on a collection and the `eq` join on a single item too.
- Now: a list field is joined only when the entity's field of that name is a collection; a list of values against a
  single column is that column's `in`: `sp = sp.and((r, q, c) -> r.get("status").in(status));` (Kotlin
  ``r.get<Any>("status").`in`(status)``). The join lambdas open with one pair of parentheses, as 2.x writes them.
  The 2.x generator changes too, because its output failed at run time: a model that searches a single column with a
  list now gets the column's `in` instead of the failing join. Nothing else in 2.x output moves - a list field that is
  a collection of the entity, and every other condition, generate as before.
- Checked: `SearchInValueListTest` - the v3 layered, hexagonal and Kotlin mappers and the 2.x generator write the
  column's `in` for the guide's model (enum `@db(code)`, `@searchable(entity=PurchaseOrder)`), and the
  2.x-compatible rule-engine output writes the 2.x generator's `toSpec`; all fail without the fix. `MapperSpecTest`:
  the single-column case, a collection of the entity still joining, one pair of parentheses on both joins. Run on
  H2 (not committed): a `List<CustomerType> @search(in)` search DTO added to the 2.x template filtered three customers
  to the two asked for through `CustomerRepository.findAll(ShopMapper.toSpec(dto))` - and failed with "is not
  joinable" before the fix; the same in the hexagonal sample (`elasticore-template-ddd`, v3), customers created over
  HTTP. `elcoreRulesCheck -Pstrict`, `elcoreTreeCheck`.
- Follow-ups (`search-list-eq-column.md`): `@search(eq)` on a list of values against a single column, which joined as
  `in` did, compares the column now; the Kotlin output was compiled and run; a `@searchable` DTO of an entity in
  another domain gets no `toSpec` in either generator, and elcore3 now says so.
- Files: `pub/jpa/MapperSrcPublisher.java`, `pub/rules/base/MapperSpecRules.java`,
  `elasticore-guide/elasticore-annotation-guide.md` and `ko/` (3.6), `MapperSpecTest.java`,
  `SearchInValueListTest.java`.
