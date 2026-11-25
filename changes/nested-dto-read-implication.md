### New Features (v3 service - `@access`: `config: nestedRows: readers`, a nested DTO for the holder's readers)

#### A DTO can carry the whole row of a referenced entity for whoever may read the holder - opt-in on 3.0 (#321)
- Why: a nested DTO was dropped whenever the referenced entity was not public - also where it was safe, a `Coverage`
  and its `Product` both read at `read=user` or `read=MANAGER`.
- Before and after: nothing changes for an existing 3.0 project. The new env.yml switch `config: nestedRows` (domain-wide)
  is `public` by default: a row is carried only when anyone may read it, as before, byte for byte; the warning of a row the
  switch would carry now names it. `nestedRows: readers` carries the row when the holder's read level implies the
  referenced one (`AccessExpression.implies`, the runtime `AccessPolicy` reading: a signed-in word implies `user`,
  anything implies `public`, nothing implies `owner`). The DTO, its TypeScript type and the sortable paths gain the
  field and the mapper fills it - in what a read answers (read one, search, export). Create, update, a command, `me()`
  and sign-in check other levels and answer it as `null` (a generated `withoutCarriedRows` of the CoreService), so with
  `Coverage @access(read=MANAGER, create=user)` and `Product @access(read=MANAGER)` a plain user who creates a coverage
  gets no product back. The identifier stays. A nested DTO sent in a create or update never writes the referenced row,
  cascade included. 3.1 turns it on by default.
- Checked: `NestedRowsOffTest` (off: unset and `public` write the same bytes, and - with a baseline jar of release/3.0 -
  the bytes 3.0 wrote, in ten readings), `AccessExpressionTest`, `ServiceAccessRulesTest` (the level table on and off),
  `NestedGuardedRowTest`, `CarriedRowTest` (JPA, MyBatis and MongoDB; Java and Kotlin; layered, ddd and hexagonal; the
  TypeScript type), template-ddd `AwardCarriedRowTest` over HTTP, `elcoreRulesCheck -Pstrict`, `elcoreTreeCheck`.
- Files: `AccessExpression.java`, `ServiceAccessRules.java`, `MapperBaseRules.java`, `DddCommandRules.java`,
  `SortablePaths.java`, `v3/service.tmpl`, `v3/mybatis_service.tmpl`, `v3/mybatis_port_service.tmpl`,
  `v3/mongo_service.tmpl`, `kt/service.tmpl`, `kt/mongo_service.tmpl`, `elasticore-annotation-guide.md` and
  `elasticore-env-guide.md` (en, ko), template-ddd forum `env.yml` and model.
