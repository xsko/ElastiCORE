### Documentation (guides - methods of your own in the owned `<Entity>Service`, issue 360)

#### The guides say that the owned service is read-only, which code prefix gives which status, and that a test transaction hides it
- Why: a method added to the owned `<Entity>Service` without `@Transactional` ran in the core service's class-level
  `readOnly = true` transaction; PostgreSQL refused its insert (SQLSTATE 25006, a 500 to the client). The project's tests
  passed, in a test transaction that is not read-only. And a refusal coded `rule.` answered 422 where 409 was expected; the
  mapping was written only in the generated advice's Javadoc.
- Added: dev guide, after `beforeSave` (English and Korean), "Methods of your own in `*Service`": (1) the class is read-only,
  a method that writes needs `@Transactional`; (2) the table of `<Domain>ApiErrors`: `state.` 409 "Not allowed in this
  state", `reference.` 404 "Not found", any other code or none 422 "Refused by the domain", the body's `code` being the code
  thrown, `ConflictException` for a 409 of your own; (3) a test transaction hides (1): test without one
  (`@Transactional(propagation = NOT_SUPPORTED)`) or over HTTP, on the database you deploy to. Env guide 6.2 points to it.
- Where it holds, from the templates and the generated samples: the class-level `@Transactional(readOnly = true)` is in the
  JPA CoreService (`v3/service.tmpl`, `kt/service.tmpl`), the MyBatis ones (`v3/mybatis_service.tmpl`,
  `v3/mybatis_port_service.tmpl`; MyBatis code is Java) and the 2.x `service.tmpl`. Generated with `elcore3`: every CoreService
  of `elasticore-template-mybatis` (layered MyBatis), `elasticore-template-ddd` (layered, ddd and hexagonal JPA),
  `elasticore-template-ddd-mybatis` (layered, ddd and hexagonal MyBatis) and `elasticore-template-kt` (Kotlin JPA, layered and
  hexagonal) has it; none of `elasticore-template-mongo` and `elasticore-template-kt-mongo` has (`v3/mongo_service.tmpl`
  and `kt/mongo_service.tmpl` carry no `@Transactional`, the 2.x `service_mongo.tmpl` one on `findById`, not read-only),
  so the guide marks MongoDB "not in". The prefix mapping is copied from `v3/apiErrors.tmpl` and
  `kt/apiErrors.tmpl` (`refused(InvariantViolationException)`).
- Checked by running, with a throwaway test not committed: an owned-service subclass with a method without `@Transactional`
  that calls the inherited `save` - in `elasticore-template-mybatis` (MyBatis) and `elasticore-template-ddd` (JPA, catalog), on
  H2 - runs in an active transaction that `TransactionSynchronizationManager` reports read-only, and H2 writes the row anyway
  (0 -> 1 rows); with `@Transactional` it is not read-only. That is why the guide says H2 does not show it. The PostgreSQL
  refusal (25006) is the issue's report; no PostgreSQL was at hand to run it again.
- Corrected from the issue: its table put `reference.` with 422; the generated advice answers it 404.
- No code, template or generated header changed (the owned service's header is the owner's decision). The guide tests
  (`--tests "*Guide*"`).
- Files: `elasticore-dev-guide.md`, `elasticore-env-guide.md` and their `ko/` copies.
