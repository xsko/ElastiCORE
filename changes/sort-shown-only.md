### Bug Fixes (v3 search - security)

#### A search sorts only by what its response shows; a sort on a hidden value is 400 `no-such-property`
- Why: the 3.0 search turned every `sortCode` / `sortColumn` term into a sort unchecked (JPA), or checked it only against
  the columns it can sort by (MyBatis `SORT_COLUMNS`, MongoDB `SORT_FIELDS`), which hold the hidden ones too. A sort on a value
  the response leaves out still tells that value's order. Reproduced on the generated samples over HTTP: in the pet store
  (Boot 4, JPA hexagonal) a MANAGER's `POST /purchaseOrder/search` with `customer.email+`, `creditCard.creditCardNumber+`
  (`@writeonly`) and `customer.password+` answered 200, in the stored e-mails', card numbers' and password hashes' order
  (checked against the H2 file); in the MyBatis pet store a customer's own orders sorted by the card number, and the admin's
  customer search by the hash; in `elasticore-template-kt` (Kotlin JPA) a ticket search by `agent.secret` and the agent search
  by `secret`; in `elasticore-template-mongo` a member search by the `@password` `secret`, and a customer search by the
  masked (`@Mask`) e-mail. With rows of one's own to sort among, the order gives the value itself away.
- Now each 3.0 search DTO (Java and Kotlin, JPA, MyBatis and MongoDB, every architecture) lists what its sort may name -
  `SORTABLE_PROPERTIES` and `sortableProperties()` - and the generated `SortableObject.getSort()` hands each property through
  the runtime's new `io.elasticore.springboot3.dto.SortCheck`, which refuses one not listed with a `BadRequestException`
  (400, code `no-such-property`, "The search cannot be sorted by 'x'; sort by one of [...]", the term cut to 40 characters and
  control characters shown as `?`) before the query is built. `sortColumn` goes the same way, and so does the export
  (`POST .../export`) and every other call of `findBySearch` with a search DTO.
- The rule (`SortablePaths`): a value the search's response DTO shows under its own name; a part of an embedded value the DTO
  carries; a `@ref` path (a flattened key `customer.customerId`, `product.name`); the values of a joined row whose DTO the
  response carries whole - a row anyone may read, as `access-dto-nested` nests no other - up to three relations deep; the key;
  the `@sort` default order. Never `@password`, `@writeonly`/`@nooutput`, `@jsonignore`, a `@Mask` value, a `@view` without
  read, a `@ref` to a password, a list. `@sensitive` and `@encrypt` values are shown, so they sort.
- The answer is the one a property the entity does not have gets, so a hidden property cannot be told from a missing one.
  JPA said `no-such-property` for that before; MyBatis and MongoDB said `bad-request` (`X cannot be sorted by ...`) - their
  unknown sort terms now get `no-such-property` too, and a term reaches their own check (whose message repeats it as sent) only
  once the search DTO lists it.
- v3 output changes by default (a security fix): a request that sorted by a value its response does not show is now 400.
  The existing sample sorts all name shown values and keep working. The 2.x-compatible output (`output = 'v2'`) and the 2.x
  generator are unchanged: `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 / 0 / 0.
- The runtime's `io.elasticore.springboot3.dto.SortableObject` - the `interface.sortable` the init wizard writes into a new
  project's env.yml - gets the same `sortableProperties()` (default null) and hands each term through `SortCheck`, so a 3.0
  search DTO on it is checked too. For code that does not override it - 2.x DTOs, any other implementation - null means any
  property, exactly as before (`SortCheckTest` checks both). A domain naming a sort interface of its own keeps it unchecked,
  with a DSL warning; so does a search whose response DTO is not in the domain's model. `Refusals.shown(...)` is the runtime's shortening of a
  refused value, which `SqlPortGateway` now calls too (no change in what it says).
- Checked: `SortTermGenerationTest` (9 runs: JPA Java hexagonal/layered/ddd, JPA Kotlin hexagonal/layered, MyBatis layered/ddd,
  MongoDB Java/Kotlin, and the 2.x-compatible run), `SearchDtoFileTest`, `KotlinDtoFileTest`, runtime `SortCheckTest` (Boot 3 and
  4); over HTTP: pet store `SortTermTest`, MyBatis pet store `ShopFlow.aSortOnWhatTheResponseDoesNotShowIsRefused` (no SQL is
  prepared), `elasticore-template-kt` `SortTermTest`, `elasticore-template-mongo` `ClubApi` / `ShopApi`,
  `elasticore-template-kt-mongo` `ClubApi` (MongoDB 7).
- Guides: annotation guide (en, ko) 4.2 "What a search may sort by", the export and 3.8 notes.
- Files: `SortablePaths.java`, `SpecFactory.java`, `RulesGenRunner.java`, `RulesVerifyRunner.java`, `EntitySpec.java`,
  `SearchSortList.java`, `MaskedFields.java`, `SearchDtoCleanFileRules.java`, `KotlinSearchDtoFileRules.java`,
  `v3/java_SortableObject.tmpl`, `kt/java_SortableObject.tmpl`; runtime `SortCheck.java`, `SortableObject.java`, `Refusals.java`, `SqlPortGateway.java`.
