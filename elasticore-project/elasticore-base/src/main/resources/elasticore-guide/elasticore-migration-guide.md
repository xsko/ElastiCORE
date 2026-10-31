# ElastiCORE 2.x → 3.0 Migration Guide

## Summary: there is nothing to fix

Raise the library version and nothing else. Leave `env.yml`, `build.gradle` and the model files
as they are.

```gradle
- implementation      'io.elasticore:elasticore-base:2.2.260703'
+ implementation      'io.elasticore:elasticore-base:3.0.261031-rc7'
```

**The generated source code does not change** - only a few places where a 2.x defect was fixed differ, and the list is in
`elasticore-history.md`. A project that generates with a hand-written `elcore` task or with `@ElastiCore`, as in 2.x, still calls the 2.x generator,
so the new 3.0 features (DDD, hexagonal, Kotlin,
TypeScript) are on only when you declare them.

### If you move to the Gradle plugin (`id 'io.elasticore'`): `output = 'v2'`

From 3.0.261028-rc3, if you declare nothing, the plugin **generates 3.0 code** (the v3 bundle of layered Java).
To keep the 2.x code as it is, write one line:

```gradle
elasticore {
    output = 'v2'      // the same code the 2.x generator wrote. The build calls the 2.x generator (elcore)
}
```

If you build without it, the generator looks for sources that 2.x generated (first line `//ecd:..._V2.x`) or for the record of the last run that ran in 2.x-compatible mode
(`build/elasticore/server.mode`), and **stops without writing any source** - this is so that a version bump alone does not rewrite hundreds of sources and change the behavior of search, refusal responses and access checks, which
is what would happen otherwise. As the message says, one of `output = 'v2'` (keep) or `output = 'v3'` (move to 3.0 code) is
all you need to write. For a single run, `-Pv2` / `-Pv3` also work. If the domain's `env.yml` has
`publisher.rules: [jpa-springboot]` (the value that the init wizard before 3.0.261028-rc3 wrote for layered
Java projects), that project also keeps the 2.x code.

The behavior that changes when you move to 3.0 code is in "If you want to use 3.0 features" below and in `elasticore-history.md`, and the code you need to touch is in
"2.x code: what to fix when you move from it" inside it.

The first build that moves with `output = 'v3'` also merges the files that 2.x generated and you **edited by hand** into the 3.0 code: it rebuilds the 2.x original of those files
in memory (`[elcore3] upgrade : ...`) and keeps your edits relative to the one whose hash matches the first line `//ecd:`.
Files you did not edit are rewritten as 3.0 code, and files whose base cannot be verified are skipped and reported, as before. This first build does all
domains at once (with `-Pdomain` it writes nothing and stops) - commit the
`.elasticore/cache` that this build creates together with the generated files (`elasticore-env-guide.md` 9-2). Generate-once files (extension services and
controllers) belong to the developer, so if a 3.0 change overlaps your edited lines, the generator leaves the file as it is, with no conflict marker, and warns you to move the change by hand.
Generated files that 3.0 no longer produces are deleted, but a file that the remaining sources still use is kept, and the generator reports which file uses it
(`... is no longer generated, but ... still uses it - kept`) - see "Services and repositories" in the table below.
A project that generated with `@ElastiCore` on the application class removes it when it moves (and elasticore-base in `annotationProcessor` too) - if you leave it, every compile
makes the 2.x generator write 2.x code again, which mixes with the 3.0 code, and the merge takes that
as a developer edit.
The v3 bundle of layered Java (`jpa-springboot-v3`) uses more rules than the 2.x bundle - `@access`/`@owner` access checks
(`jpa-service-access`), refusal responses with an error code (`spring-api-errors`), and write protection for fields the server decides
(`jpa-write-guard`). In search, `like` ignores case, and keywords such as `gt` and `gte` become the same as the symbols. `@fetchjoin` associations are fetched with the root in a page
search, as in 2.x (from 3.0.261029-rc4 - the v3 service before that did not fetch them).

### Evidence

We compared the generated sources of the sample projects over the whole 3.0 development period:

```
2.2.260703 → 3.0.260829   generated domain source changes: 0 files
```

A few files show as modified, but all of them carry the `_V2.2` stamp and come from adding
entities to the sample model before 3.0. Also, regenerating with the current code
shows no difference from the committed tree.

---

## What still changes (all bug fixes)

### 1. Location and encoding of `schema.yml`

| | 2.x | 3.0 |
|---|---|---|
| Location | repository root (the module's file was not updated) | next to its own blueprint |
| Encoding | platform default (Korean Windows = MS949) | UTF-8 |

**What to do**: the old `schema.yml` at the repository root is no longer updated, so
delete it. If Korean labels were garbled, they are now correct.

### 2. Generated code goes into the module that holds the model

The problem where, in some setups, generated code leaked into the repository root is fixed.

**What to do**: if the repository root holds a bundle of generated files you cannot identify, that is
this problem. Delete it. It is generated again in the right place.

### 3. `DTOUtils.getMetaInfo(Class)` → `getMetaInfo(Class<T>)`

This was a problem where the IDE showed a raw type warning at the call site in the generated controller.

**What to do**: nothing. The signature descriptor
(`(Ljava/lang/Class;)Ljava/util/Map;`) is the same, so existing
binaries keep working without recompiling.

### 4. The new DSL validation does not react to 2.x models

The `DSL_WARN` that 3.0 adds
applies only to DDD-specific syntax such as `domainService` and `uses:`. A 2.x model does not have that syntax, so a `-Pmode=valid` CI does not
suddenly fail.

---

## 3.0 features, if you want them (optional)

From here on, do it **only when you want to**. With a hand-written 2.x task or with `output = 'v2'`, the behavior stays 2.x.

### What you get

| Bundle | Content |
|---|---|
| `jpa-springboot` | The same code as 2.x (same as `output = 'v2'`) |
| `jpa-springboot-v3` | The 3.0 code of layered Java - the plugin default (from 3.0.261028-rc3) |
| `ddd-springboot` | Aggregate boundaries, invariants, domain events, value objects |
| `hexagonal-springboot` | The above + inbound ports, hexagonal package layout |
| `*-kt` | The Kotlin versions of the three above |
| `ts-model` | DTOs, enums and a typed REST client for the browser |

### How to turn it on

The domain's `env.yml`:

```yaml
elasticore:
  config:
    publisher:
      rules: [ddd-springboot]
```

**Note: the rule engine reads this setting.** The rule engine is a separate entry point from the `@ElastiCore` annotation
processor, and a normal build does not look at this setting. For how to
run it, see "Running the rule engine in your project"
in `elasticore-rule-guide.md`.

**`like` in search ignores case.** In v3 bundles (except layered Java that keeps the 2.x code - the plugin default,
hexagonal and DDD, `-Pv3`, every Kotlin build), `@search(like)` on a string field is a case-insensitive contains search, like `%__%`. To make it
case-sensitive as in 2.x, change it to `@search(%_%)`. The comparison uses `lower(column)`, so if a contains search was
backed by an index such as `pg_trgm`, recreate that index on `lower(column)`.

**`@fetchjoin` and `table.autoJoin` work as in 2.x in v3 bundles too (from 3.0.261029-rc4).** `withJoinFetches()` is generated in the
CoreService, and the page search (`findBySearch`) fetches those associations with the root (LEFT JOIN FETCH, not in the count
query). Code in an extension service that calls `withJoinFetches()` also compiles. In v3 it is generated only for entities that have such an association
(2.x generates it in every CoreService - see the table below), and it does not apply outside the page search (`findAll`, `listBySearch`).
The v3 service up to 3.0.261028-rc3 did not fetch them, so it read the associations row by row (N+1), and only warned at generation time.

### 2.x code: what to fix when you move from it

If a project that used code generated by 2.x (or `output = 'v2'`) moves to the v3 bundle, the differences below
show up at compile or test time. They are what actually came up when moving a project with 9 domains.

| What | 2.x code | v3 code | What to do |
|---|---|---|---|
| Entities that get a service and repository | `XxxRepository` if it has a key; if it has a `@template` or `@searchable` DTO, also `XxxCoreService`, `XxxService` and the controller (even without `@service` or `@expose`) | Only entities that declare `@expose` (up to the controller) or `@service` (without a controller) | Write `@expose` on that entity's line (up to the controller as in 2.x - a controller you did not edit is replaced by the 3.0 one). If you do not, that entity's 2.x service and repository are deleted when nothing uses them any more |
| Entity constructor that takes the key | `new Order(id)` | None (default constructor only) | For a reference to put into an association, use `repository.getReferenceById(id)`; build test data with the default constructor and setters |
| Imports used by code inside a preserved block (`// !----` ~ `// ----!`) | Compiled thanks to `*` imports such as `jakarta.persistence.*` and `java.util.*` | Imports only what the generated code uses | Write the imports the block uses in the preserved block under `package` ("Preserved blocks for user code" in `elasticore-dev-guide.md`) |
| `@fetchjoin`, `table.autoJoin` | Fetched with the root in a page search by `withJoinFetches()` | The same - from 3.0.261029-rc4 (v3 up to 3.0.261028-rc3 did not fetch them, see above) | Nothing. A query you wrote with `JoinSpec.fetchJoin()` instead when moving to v3 up to rc3 keeps working |
| `withJoinFetches()` in CoreService | In every CoreService - an empty Specification that adds no condition when there is no association to fetch | Only in entities that have an association to fetch (`@fetchjoin`, `table.autoJoin`) | An extension service of an entity without such an association fails to compile if it calls it. The call did nothing in 2.x either, so remove `.and(withJoinFetches())` |
| `@writeonly`, `@nooutput` values | Copied entity -> DTO (dropped only from the JSON response) | Not copied | If server code needs the value, read it from the entity |
| CoreService `save(dto)` | If the key already exists, overwrites that row | Bean Validation first (`ConstraintViolationException`); if the key already exists, `DuplicateKeyException` | Change calls for existing rows to `update(dto)` |
| CoreService `update(dto)` | No validation | Bean Validation first (by default only the values sent) - a validation error comes before a missing-key error | |
| CoreService `delete(searchDTO)` | Deletes by condition in one statement (bulk DELETE) | Deletes the found rows one by one - entity listeners and cascade work | |
| CoreService `updateBySpecification` | One bulk UPDATE | Reads each row, changes it with the mapper and saves - version, audit fields and `beforeSave` apply | |

The service tests that 2.x generated (`*ServiceTest`) pin with mocks the repository methods that the 2.x CoreService calls.
The v3 CoreService calls other methods (`deleteById` is `findById` then `delete`, `delete(searchDTO)` is `findAll` then
`delete`), so those tests do not fit - delete them and generate them again (`testCode: "true"` in `env.yml`), or fix them to match the new calls.

### How to roll back

Delete the `publisher.rules` line. For a layered Java project that uses the plugin and wants to go back to 2.x code, write
`elasticore { output = 'v2' }`. There is nothing else to roll back - but with merge on, which is the default, the generated files of 3.0 code have no first line `//ecd:`,
so the 2.x generator overwrites those files (controllers and extension services too) with 2.x code. Move the
edits you made in them back from git.

---

## Moving up to Spring Boot 4 (3.0 only)

ElastiCORE 3.0 supports Spring Boot 4.0 (2.2 supports up to Spring Boot 3). The `supportAI` tools are removed in 3.0 (`elasticore-env-guide.md` chapter 3).
If you change the runtime dependency from `elasticore-springboot3` -> `elasticore-springboot4` and raise the Boot plugin to 4.x,
ElastiCORE reads the Boot version and generates enums and the outbox to fit Jackson 3.
For the detailed steps and what you fix yourself, see chapter 3-1 of `elasticore-env-guide.md`.

---

## Frequently asked questions

**Q. Do I have to change everything at once?**
No. `publisher.rules` is a per-domain setting. You can switch only one domain to DDD
and leave the rest as they are.

**Q. Does it work with multiple modules?**
Yes. The `elasticore.modules` system property is supported by both entry points,
and the `module://` cross-module namespace redirect is unchanged.

**Q. I have files where I edited generated code by hand.**
Your edits stay. `output = 'v2'` is the same as 2.x: it detects edits by content hash and does not touch them,
and a file with the `// Initially generated by ElastiCORE; no longer managed` mark is not rewritten after its first generation.
3.0 code (`output = 'v3'`) keeps edited files following the DSL: it merges DSL changes while keeping your edits, files with the mark
(controllers, extension services ...) are generated again until you touch them, and after you edit them they accept only the changes that do not overlap your edited lines
(if they overlap, the file is left as it is and a warning is given). Files edited under 2.x are taken over by the first build - only files whose original generated version can be
verified by the hash in the first line `//ecd:`, and the rest are skipped as before (see the section "`output = 'v2'`" above, `elasticore-env-guide.md` 9-2).

**Q. How do I roll back?**
A project that has kept 2.x code (`output = 'v2'`, hand-written 2.x task) only needs to put the version back. 3.0 leaves no state
behind - but the old files you deleted in 1 and 2 above are generated again in the wrong place if you go back to 2.x.
A project that moved to 3.0 code (`output = 'v3'`, the plugin default) must not just put the version back. With merge on, which is the default, the files 3.0 wrote
have no first line `//ecd:`, so the 2.x generator does not see them as edited and overwrites them with 2.x code - your edits in them
are lost. The same applies to files with the `// Initially generated by ElastiCORE` mark, such as controllers and extension services (2.x looks for
that mark only below the first line `//ecd:`). Files that only 3.0 created are not deleted and stay, and `.elasticore/cache` is not read by 2.x.
Put the sources back to the commit before the move, then lower the version. (A project that generated with `elasticore { merge = false }` has
`//ecd:` in its files, so 2.x also skips the edited files.)
