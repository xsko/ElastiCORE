### Bug Fixes (generated-file merge - a project that changes its language)

#### Switching `language` between java and kotlin removes the old language's generated files
- Why: after `language = 'java'` became `'kotlin'`, `elcore3` kept every unedited `.java` it had generated with
  `...CategoryCoreService.java is no longer generated, but ...CategoryCoreService.kt still uses it - kept; it goes on a
  later run once nothing uses it.`, and `compileKotlin` failed on each class twice (97 Redeclaration errors in the
  audit's pet store repro). A later run kept them again: the check for a kept source that still uses a dropped class
  counted the new Kotlin file of the same class - it names the class it declares - and every other Kotlin file that
  names it.
- Now: a dropped Java or Kotlin file whose class this run wrote in the other language (same class name, same package,
  the new file declaring it) is not kept for its users - the new file serves them, and keeping the old one only breaks
  the build. It goes as an unedited stale file always did: only when it is still what was generated (its
  `.elasticore/cache` base, or its 3.0 hash line). An edited one is kept as before, and its warning now adds
  `...CustomerDTO.kt declares the same class now, so the build has it twice until this one goes.`
- Unchanged: a dropped class with no file of the same class in the other language is kept while a source uses it, as
  before; the code 2.x generates (no merge) tidies up as it did.
- Checked: `LanguageSwitchTest` - java -> kotlin (with a Java class of the developer's naming a generated DTO) and
  kotlin -> java failed before the fix (the old files were kept, `still uses it`) and pass after: no class left in both
  languages, no `.java` left in the cache, and the next run says nothing more; an edited `CustomerDTO.java` stays with
  the warning while the other files go; a dropped Kotlin DTO a Java class still uses is still kept. End to end on the
  pet store seed (Boot 4): generated and compiled as Java, switched to Kotlin - `compileKotlin compileJava` succeeds, 0
  `still uses it`, 0 Redeclaration, only the application class left in `src/main/java`; a second build removes and
  keeps nothing. The `GeneratedMergeTest` and `MigrationCheckTest` cases of kept files pass unchanged.
- Files: `RulesGenRunner` (`stillUsed`, `twinOf`, the edited-file warnings), `LanguageSwitchTest`, env guide (en, ko).
