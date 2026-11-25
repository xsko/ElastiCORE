### Changes (v3 service - `@access`: a nested DTO is carried for the holder's readers by default)

#### A DTO carries the whole row of a referenced entity for whoever may read the holder (#321) - responses gain the field
- Why: a nested DTO was dropped whenever the referenced entity was not public - also where it was safe, a `Coverage`
  and its `Product` both read at `read=user` or `read=MANAGER`. 3.0 has it as an opt-in (`config: nestedRows: readers`);
  3.1 turns it on by default, as agreed on #321.
- Before and after: the row is carried when the holder's read level implies the referenced one (`AccessExpression.implies`,
  the runtime `AccessPolicy` reading: a signed-in word implies `user`, anything implies `public`, nothing implies `owner`).
  **Responses change**: such a DTO, its TypeScript type and the sortable paths gain the field, and the mapper fills it -
  in what a read answers (read one, search, export). Create, update, a command, `me()` and sign-in check other levels and
  answer it as `null` (a generated `withoutCarriedRows` of the CoreService), so with `Coverage @access(read=MANAGER,
  create=user)` and `Product @access(read=MANAGER)` a plain user who creates a coverage gets no product back. The
  identifier stays. A nested DTO sent in a create or update never writes the referenced row, cascade included.
  `config: nestedRows: public` (env.yml, domain-wide) keeps 3.0's default; a 3.0 project that said `nestedRows: readers`
  keeps working and may drop the line.
- Checked: `NestedRowsOffTest` (unset and `readers` write the same bytes in ten readings; `public` writes, with a
  baseline jar of main before the change, the bytes main wrote), `AccessExpressionTest`, `ServiceAccessRulesTest` (the
  level table, the default and `public`), `NestedGuardedRowTest`, `CarriedRowTest` (JPA, MyBatis and MongoDB; Java and
  Kotlin; layered, ddd and hexagonal; the TypeScript type), template-ddd `AwardCarriedRowTest` over HTTP,
  `elcoreRulesCheck -Pstrict`, `elcoreTreeCheck`.
- Files: `AccessExpression.java`, `ServiceAccessRules.java`, `MapperBaseRules.java`, `DddCommandRules.java`,
  `SortablePaths.java`, `v3/service.tmpl`, `v3/mybatis_service.tmpl`, `v3/mybatis_port_service.tmpl`,
  `v3/mongo_service.tmpl`, `kt/service.tmpl`, `kt/mongo_service.tmpl`, `elasticore-annotation-guide.md` and
  `elasticore-env-guide.md` (en, ko), template-ddd forum model.
