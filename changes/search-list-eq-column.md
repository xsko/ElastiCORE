### Bug Fixes (search - a list of values against one column)

#### A search DTO's `List<Enum> @search(eq)` compares the column, `status = statusItem`
- Why: the same failure as `@search(in)` on a list of values (`search-in-value-list.md`), for `eq`. A search DTO's
  `status: List<OrderStatus> @search(eq)`, where the entity's `status` is one enum, gets the one value it searches by,
  `statusItem`, and every JPA mapper joined the column with it: `root.join("status")` + `criteriaBuilder.equal(join,
  statusItem)`. It compiled and failed when it ran - `Attribute 'io.elasticore.blueprint.domain.shop.entity.Order#
  paymentMethod(BASIC)' is not joinable` on H2 (2.x template, `@DataJpaTest`).
- Now: a list field is joined only when the entity's field of that name is a collection; against a single column the
  item is the column's equality, `sp = sp.and((r,q,c) -> c.equal(r.get("status"),statusItem));` (Kotlin
  `c.equal(r.get<Any>("status"),statusItem!!)`). The search DTO keeps `statusItem`. The 2.x generator changes too,
  because its output failed at run time; a list field that is a collection of the entity, one reached through `@ref`,
  and every other condition generate as before.
- Checked: `SearchInValueListTest` (v3 layered and hexagonal Java, Kotlin, the 2.x generator, and the 2.x-compatible
  rule-engine output equal to the 2.x generator's `toSpec`) - the new cases fail without the fix. Run on H2, probes not
  committed: `paymentMethod: List<PaymentMethod> @search(eq)` added to `OrderSrchDTO` - the 2.x template
  (`output = 'v2'`) found 1 of 3 orders through `OrderRepository.findAll(ShopMapper.toSpec(dto))` (before the fix: the
  "is not joinable" error above); `elasticore-template-ddd` (hexagonal, v3) found 1 of 3 the same way.
  `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `pub/jpa/MapperSrcPublisher.java`, `pub/rules/base/MapperSpecRules.java`,
  `elasticore-guide/elasticore-annotation-guide.md` and `ko/` (3.5), `SearchInValueListTest.java`.

#### Kotlin: a list of values in a search DTO compiles and searches
- Checked by running, not only as text (follow-up of `search-in-value-list.md`): in `elasticore-template-kt` (probe not
  committed) `OrderSrchDTO` got `paymentMethod: List<PaymentMethod> @search(in)` and
  `status: List<OrderStatus> @search(eq)`; the Kotlin code compiled, the application booted, three orders were created
  over HTTP and `POST /api/v1/shop/order/search {"paymentMethod":["CA","PP"]}` answered 2 of 3;
  `{"statusItem":"PEN"}` ran and answered 0 (the orders' status is not set on create). No code change.
