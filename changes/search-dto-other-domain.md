### Bug Fixes (search - a search DTO of another domain's entity)

#### A `@searchable` DTO of an entity in another domain is reported: it is searched by nothing
- Why: `search-in-value-list.md` said such a DTO "still joins in the 3.0 code". Run, it does not join - neither
  generator writes a `toSpec` for it at all: the mapper is written in the entity's domain, from that domain's search
  DTOs. elcore3 writes the DTO alone (and leaves out a field whose enum is the other domain's); the 2.x generator
  writes the DTO with `import null.*;`, which does not compile. Nothing said so.
- Now: elcore3 warns `[search] OrderSearchDTO @searchable(entity=PurchaseOrder): PurchaseOrder is declared in domain
  'shop', and a search DTO is searched only in its entity's domain - no toSpec is written for it here. Declare
  OrderSearchDTO in domain 'shop'.`, and for a name no domain declares: `... no domain declares an entity Purchase, so
  nothing is searched by ... (no toSpec is written).` A warning only; generated code does not change. Searching
  across domains is not added: 2.x never had it.
- Checked: `SearchInValueListTest` - both warnings, no `toSpec` written (layered v3, hexagonal); fail without the change.
- Files: `pub/rules/verify/RulesGenRunner.java`, `pub/rules/verify/SpecFactory.java`, `SearchInValueListTest.java`.