### New Features (moving from 2.x - a check before the move)

#### `./gradlew elcoreMigrationCheck`: the first 3.0 build of a project 2.x generated, with nothing written, and a report
- Why: moving a 2.x project to the 3.0 code showed its problems only at compile time, all at once - a repository the
  3.0 code no longer writes and the `helper.getXxx()` calls of hand code (hundreds of errors), an import a merge took out
  of an edited file, edited files left as the 2.x code. What the build would do is known to the generator before it
  writes anything.
- What: a Gradle task, run only when asked, whatever `output` the build keeps. It runs the 3.0 build as `elcore3` with
  `output = 'v3'` would - the 2.x code read as the bases of the edited files, the merge, the removal of what is no longer
  produced - with the writes, the deletes, the cache, the DDL and the guides held back, and writes
  `build/elasticore/migration-check.md`:
  - generated files the build would remove or no longer write that code of the developer's uses - by name, or for a
    repository by the domain helper's getter (`helper.getCode()`) - with `@repository` / `@service` / `@expose` as the
    fix;
  - files that would conflict, be kept as the developer's, or be left as the 2.x code (edited, base not confirmed);
  - the imports a merge would take out of a file the developer wrote more into and their code may need (one they
    added, one naming a class their lines use, or a wildcard their lines may rely on - not one only the generated code
    used);
  - files an older generator stamped that the 3.0 code does not write, and the files it would remove.
  The console prints each file's status as the build would, and a summary. A Kotlin or TypeScript run and `-Pdomain` are
  refused with the reason: the check reads a whole project 2.x generated, which is Java.
- Checked: `MigrationCheckTest` - a project the 2.x generator wrote (stamped as 2.2 stamps), with a class of the
  developer's calling `helper.getCode()`, one calling a `getCode()` of an entity, and an edited DTO and CoreService:
  the check changes no file (byte for byte) and writes no cache; its report names `CodeRepository` (no longer written,
  left on disk) as used by `CodeReader` and nothing else, and both edited files as merged; then the build itself prints
  the same status for every file, merges the hand method in and rewrites the helper without `CodeRepository`.
  `MigrationPreviewTest` (statuses read off the run's lines, which dropped imports are listed and which are not, the
  order of the report). `ElastiCorePluginTest.theTasksAppear`.
- Files: `RulesGenRunner.java` (the check: `PROP_MIGRATION_CHECK`, writes held back, `finishMigrationCheck`,
  `findLeftBehind`, `usersOf`), `MigrationPreview.java`, `ElastiCorePlugin.java`, the tests above,
  `elasticore-migration-guide.md` (en, ko: the move step by step)
