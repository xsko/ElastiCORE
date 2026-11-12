### Changes (v3 API errors - their order beside an application's catch-all)

#### A generated `XxxApiErrors` comes before an application's advice without `@Order`; a tie is warned about
- Why: a project moving to 3.0 had a common `@RestControllerAdvice` with an `@ExceptionHandler(Throwable.class)` and no
  `@Order`. The generated `XxxApiErrors` was `@Order(Ordered.LOWEST_PRECEDENCE)`, the same as an advice without one:
  Spring consults advice beans of the same order in no set order and the first with a matching handler answers, so a
  generated controller's 404 could go out as the catch-all's 500 - found by reading the code, not by a test.
- What: a new `XxxApiErrors` (Java and Kotlin, v3) is `@Order(Ordered.LOWEST_PRECEDENCE - 1)`: an advice the
  application orders itself still comes first, one without an order comes after. An `XxxApiErrors` never edited is
  regenerated with it; for one that was edited, or any tie, `elcore3` and `elcoreMigrationCheck` warn and name both
  files: an advice of the application's that answers `Throwable`, `Exception` or `RuntimeException`, has no `@Order` or
  the lowest one, beside an `XxxApiErrors` that is last in line too. A project with no generated advice hears nothing.
- Checked: `AdviceOrderTest` (the tie with `Throwable`, `{Exception.class}`, `value = RuntimeException.class` and an
  explicit lowest order is said; an `XxxApiErrors` next to last, an ordered advice, one without a catch-all, or no
  generated advice says nothing). Rule guide (en, ko), API errors.

### Bug Fixes (migration check - preserved blocks)

#### The check and the build say the imports a preserved block may lose
- Why: in a repository 2.x generated, methods written in a preserved block (`// !---------- ... ------------!`) used
  `java.util.*` and `...dto.*`. The 3.0 code imports each class by name; the block was put back as it was, and the file
  failed to compile with 5 errors - which `elcoreMigrationCheck` did not list: it looked only at merged files, and a
  file whose edits are all in blocks is rewritten, not merged. Its report also listed only "merged" files.
- What: for a file whose code of the developer's is in preserved blocks, the imports it had that the new text does not,
  and that a block's line may need (one it names, or a wildcard it may rely on), are in the check's report ("Imports the
  build would take out of your files") and are a build warning that says to write them in the preserved block under
  `package`. A merged file with blocks is read with its blocks too. The count is "files that would lose an import your
  code may need".
- Checked: `MigrationCheckTest` - the 2.2 project's `PurchaseOrderRepository` gets a block that uses `List` and
  `Optional`: the report names it with `import java.util.*`, the build prints the warning, keeps the block and drops
  the wildcard. `MigrationPreviewTest`. Migration guide (en, ko).

- Files: `RulesGenRunner.java` (`checkAdviceOrder`, `adviceOrderClashes`, `blockImports`), `MigrationPreview.java`
  (`dropped`, the report's wording), `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `AdviceOrderTest.java`,
  `MigrationCheckTest.java`, `MigrationPreviewTest.java`, the rule and migration guides (en, ko)
