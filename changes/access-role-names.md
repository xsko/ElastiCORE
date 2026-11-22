### New Features (v3 service - `@access` names roles)

#### `@access` takes role names and joins words with `|` (either) and `&` (both)
- Why (issue 269): a level was one of `public`, `user`, `owner`, `admin`, `none`. A shop whose managers read every
  order, or whose representatives change only their own rows, had no way to say it: `read=owner` let the owner and
  the administrator alone, and a role between the two was hand-written around the generated service.
- Now: a level entry may name the session's roles - the constants of the `@signin` entity's role field (`@role`, or
  the field named `role`, an enum or string with `@access(admin)`) - and join words with `|` and `&`; `&` binds
  tighter than `|`, there are no parentheses, spaces around `|` and `&` are allowed: `read=owner|MANAGER` (the owner,
  or a MANAGER on every row - a MANAGER's search is not narrowed, anyone else's has their own rows),
  `update=REP&owner` (a REP on a row of their own; an owner who is no REP is refused 403),
  `close=REP&owner|MANAGER`. A word that is a level is the level, in any case; any other word is a role name, starts
  with a capital letter and is compared with `CurrentUser.hasRole` as written. The administrator passes every entry
  but `none`, as it passes `owner`. `findAll` and a conditional delete or update of an entry naming `owner` are for
  whoever passes it on every row, and the administrator. The runtime's `AccessPolicy` reads the entry (the generated
  services hand it the declaration text, as before), so the checks are those of every mode, language and
  architecture: the service checks of create, read, update, delete, search and the command operations, the per-row
  read, and the search filter of JPA (the `Specification`), MyBatis (the mapper's owner argument) and MongoDB (the
  `Criteria`). `AccessPolicy.level(op)` answers null for an entry that names a role or joins words.
- Build checks: where the application has a role enum - the `@signin` entity's role field is an enum, in this domain
  or another domain of the application (counted per application, as `@signin` is) - a name that is neither a level
  nor one of its constants stops the build, naming the entity, the entry and the name: `[Shop] Ticket
  @access(create=REP|MANGER): MANGER is neither a level (public, user, owner, admin, none) nor a constant of the role
  enum UserRole (USER, MANAGER, REP, ADMIN).` Without one (no `@signin`, a string role, two `@signin` entities) a name
  is not checked. DSL warnings: a role constant named `PUBLIC`, `USER`, `OWNER` or `NONE`, once an entry names a role
  or writes the word as the constant (`read=USER`) - `UserRole.USER cannot be named in @access` (`ADMIN` is the
  administrator anyway); `&` with `user` or `public` (adds nothing) or with `none` (lets nobody in). The sign-in is
  still generated only when the `@signin` entity's update (and a chosen key's delete) lets in only the row's own user
  and the administrator: every term of the entry holds `admin`, `none`, or `owner` on the key - `update=owner|MANAGER`
  is refused with the reason, `update=REP&owner` is not. The Ossie export writes a joined entry as the runtime reads
  it (`owner|MANAGER`).
- Unchanged: an entry of one level generates and behaves as before - a model that names no role generates the same
  bytes, and the runtime's refusals keep their messages (401 for a visitor, "is for the administrator", "is not
  allowed"); the 2.x-compatible output; a field's `@access(admin)` (a field takes only `admin`); the TypeScript
  client, which exposes no access levels. The loader reads `@access` values with `|` and `&`, spaced or not, as one
  value; other annotations keep the 2.x reading. Changed for a wrong entry: the runtime's parse error and the build's
  "is not operation=level" warning name role names too, and where the application has a role enum a lower-case word
  that is no level (`read=owners`) now stops the build instead of only warning.
- Checked: `AccessPolicyTest` (springboot3 and springboot4 share it) - parsing, either, both, `&` before `|`, the
  administrator passing all but `none`, a visitor's 401, the search filter of `owner|MANAGER` and `REP&owner`,
  `requireAll`, a lower-case non-level word refused. `AccessRolesTest` - the entry in the generated service of JPA
  hexagonal Java and Kotlin, MyBatis layered Java and MongoDB Java and Kotlin with the owner filter; the service of a
  role entry equal to that of a level entry but for the declaration text; the build error for an unknown name, also
  when the role enum is in another domain; no check without a role enum; the warnings (`&` with user, none and
  public; the hidden `USER` said where an entry names a role or writes `USER`, not in a model that names none); the
  refused sign-in of `update=owner|MANAGER`, and of `update=REP&owner` when `@owner` is not the key, the kept sign-in
  of `update=REP&owner` on the key. `StarParameterTest` - the loader keeps a spaced entry whole. The pet store (Boot 4,
  hexagonal JPA, over MockMvc): `UserRole` has `MANAGER`, `PurchaseOrder` says `read=owner|MANAGER` and
  `delete=MANAGER&owner`; `ShopFlowTest` - a customer the administrator made a MANAGER reads and searches another's
  order, another customer gets 404 and an empty search; the owner who is no MANAGER deletes nothing (403), a MANAGER
  deletes a cancelled order of their own and not another's (404). Its build says `UserRole.USER cannot be named in
  @access`. `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing /
  0 extra.
- Files: `AccessPolicy` (springboot3), `AccessExpression` (new), `ServiceAccessRules`, `EntitySpec`, `SpecFactory`,
  `RulesGenRunner`, `AbstractModelLoader`, `OssieSemanticModelExporter`, `AccessPolicyTest`, `AccessRolesTest` (new), `StarParameterTest`, the
  annotation and DSL guides (en, ko), the pet store (`enum.yml`, `entity.yml`, `semantic_model.yml`, `ShopFlowTest`,
  `guide/build_guide.py`, `static/dsl-guide.html`).
