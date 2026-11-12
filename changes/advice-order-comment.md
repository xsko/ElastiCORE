### Bug Fixes (API errors order check)

#### The check no longer warns about a generated `XxxApiErrors` that is already next to last
- Why: on rc14, `elcore3` and `elcoreMigrationCheck` warned on every build that a catch-all advice of the
  application's tied with the generated `XxxApiErrors`, even when it was already
  `@Order(Ordered.LOWEST_PRECEDENCE - 1)`. The check took the first " class " in the file as the declaration;
  the generated Javadoc says "Handle one of those exceptions in this class", so it never saw the `@Order` below
  and read the file as having none.
- Before: the warning on every build, also after the file was regenerated. After: comments are left out before
  the annotations are read and the declaration is found as `class Name` / `object Name`; the generated Java and
  Kotlin files are not flagged, the same files at `LOWEST_PRECEDENCE` still are.
- Checked: `AdviceOrderTest` runs the check over the real `v3/apiErrors.tmpl` and `kt/apiErrors.tmpl`.
- Files: `RulesGenRunner.java`, `AdviceOrderTest.java`.
