### Bug Fixes (v3 service - a refused write, issue 344)

#### A refused update leaves no trace in what its transaction reads
- Why: the generated CoreService reads the row, maps the request onto that same object, then runs what may refuse
  the write - `beforeSave`, a reference to a row that does not exist, an invariant the write checks, a command's
  rule. Nothing was committed, but the change stayed in the object the transaction holds: under MyBatis the row in
  the session's local cache (`localCacheScope` SESSION, its default), under JPA the managed entity - whose change the
  next query's AUTO flush even wrote into the table inside the transaction. A later read of the row in the same
  transaction (a batch, a caller that catches the refusal) answered the refused value.
- Before and after: update, update by condition and the commands (`commands:`, `states:` moves) catch a
  `RuntimeException` thrown between the mapping and the write, discard the row, and throw the refusal unchanged.
  The discarding is the runtime's (`elasticore-springboot3` and `-springboot4`), one line of generated code:
  - JPA: `io.elasticore.springboot3.entity.RefusedWrite.discard(em, entity, refused)` reads the entity again
    (`refresh`), with the members it cascades to (REFRESH, ALL). Read again rather than detached: the entity stays the
    one object the persistence context, a parent's collection and the caller share, so none of them keeps the refused
    value. Where it cannot be read again - a member the request added that was never written, a row no longer there -
    it is detached (with the members its cascade reaches), and the next read loads it afresh.
  - MyBatis: `io.elasticore.springboot3.mybatis.RefusedWrite.discard(refused)` clears the local cache of each MyBatis
    session the transaction holds - what an UPDATE would have done - so the next read reads the database.
  - MongoDB is not changed: Spring Data MongoDB reads a new object each time, so a refused change is not read back.
  - A write that is not refused runs exactly as before; the checks before the mapping (validation, the access check
    of the row, the version) change nothing and stay outside. A create's row is new - nothing else holds it - and a
    delete changes no field of its row before it is refused, so neither is changed. What an `@onDelete` statement or
    an earlier row of a batch wrote stays as written until the transaction rolls back, as before.
  - The dev guide (en, ko) says so under `beforeSave`, with the same call for a method of your own that reads a row,
    changes it and then refuses.
- Checked: reproduced first, each failing without the fix ("expected original but was REFUSED CHANGE"): a
  `@Transactional` MockMvc test - refused PUT, then GET of the row in the same transaction - on
  `elasticore-template-mybatis` (layered MyBatis, a `beforeSave` that refuses), `elasticore-template-ddd` (hexagonal
  JPA) and `elasticore-template-ddd-mybatis` (hexagonal MyBatis), both refused by the reference check after the mapping
  (404 `reference.Category.parentCategory`); all pass with it, with the modules' other tests, and so does the same test in
  Kotlin on `elasticore-template-kt` (hexagonal JPA; run with the fix only). `RefusedWriteTest` in the
  runtime (Hibernate on H2: the row and a cascaded member read again, an added member detached, the AUTO flush no
  longer writing the refused value; MyBatis-Spring on H2: the session cache cleared), on Boot 3 and Boot 4.
  `RefusedWriteGenerationTest`: update, update by condition and a command in layered and hexagonal JPA (Java, Kotlin)
  and MyBatis. `elcoreRulesCheck -Pstrict` and `elcoreTreeCheck` unchanged (the 2.x output is not touched).
- Files: `v3/service.tmpl`, `kt/service.tmpl`, `v3/mybatis_service.tmpl`, `v3/mybatis_port_service.tmpl`,
  `MyBatisServiceFileRules.java`, `DddCommandRules.java`, `DddRepositoryPortRules.java` (the entity manager's comment),
  `RefusedWrites.java`; runtime `entity/RefusedWrite.java`, `mybatis/RefusedWrite.java` (MyBatis-Spring compile-only);
  `elasticore-dev-guide.md` and `ko/elasticore-dev-guide.md`.
