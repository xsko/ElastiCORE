### Changes (v3 merge - the base of the merge)

#### `.elasticore/cache` is now `.elasticore/generated-base.zip`, and an existing project is moved by its next build
- Why: the file that holds what the generator last wrote - the base every edit of a generated file is merged against - was
  named `.elasticore/cache`. The name read as disposable: people deleted it or put it in `.gitignore`, and after that the
  build could no longer merge their edits (it leaves every edited file alone as "not in the cache"). The file has to be
  committed with the project; its name now says what it is.
- Now the base is `.elasticore/generated-base.zip` - the same stored zip, the same bytes for the same content. Beside it,
  written when absent as `.gitattributes` always was: `.elasticore/.gitattributes` says `generated-base.zip binary -filter`
  (`-filter`: a project's `*.zip filter=lfs` does not turn the base into an LFS pointer), and `.elasticore/.gitignore` says
  `!generated-base.zip`, which takes the base back in where a project ignores `*.zip` (the common Java `.gitignore` does).
  Both checked with git: `git check-ignore` under a root `*.zip`, `git check-attr` under a root LFS rule.
- What a project sees:
  - Only the old file there: the build reads it as the base, as before. The first merging run that writes (`elcore3`,
    also with `-Pdomain`) writes the new file, reads it back, makes `.elasticore/.gitattributes` name it (the line
    `cache binary` becomes `generated-base.zip binary -filter`, another line whose pattern is `cache` gets the new name;
    every other line is kept as it is), and only then deletes the old file. It prints one line:
    `[elcore3] .elasticore/cache was renamed to .elasticore/generated-base.zip - commit both changes (the delete and the new file)`.
    Commit the delete and the new files; nothing else is needed. The edits of generated files are merged across the rename.
  - Both files there (a branch that had not moved yet brought the old one back): the new one is read; the old one is not
    deleted - it is a committed file, and which branch's base it holds is not known - and every run warns:
    `[elcore3] .elasticore/cache is no longer read - .elasticore/generated-base.zip is the base of the merge; delete .elasticore/cache and commit the delete`.
    The env guide (9-2, git merge conflicts) says how to merge such a branch.
  - Neither: a first merging run, as before; it writes only the new name.
  - A run that stops before or while saving leaves the old file: it is deleted only after the new one was written and read
    back whole. A new file that does not read back as written is taken away again and the build fails, as a base it
    cannot write always did. An old file that cannot be deleted is said, and warned about as above from then on.
  - A run that writes nothing - `elcoreValidate`, `elcoreMigrationCheck` (also when refused for `-Pdomain`), `-PlistRules`
    - moves nothing. An old file that cannot be read is named as `.elasticore/cache` in the message that stops the build.
- Unchanged: the merge itself, the summary's words `the cache is missing` and the status `SKIP(not in the cache)`, which
  tools read; a project that does not merge (the 2.x-compatible output, `merge = false`) has no base and sees nothing of
  this. No file in this repository held a base (the samples regenerate their trees and keep `.elasticore/` out of git).
- Checked: `GeneratedBaseRenameTest` - a new project writes only the new name and the two files beside it; the old file
  alone is renamed with the same bytes and entries and the line printed once; an edited mapper is merged across the rename
  (end to end through `RulesGenRunner`), with the developer's own `.gitattributes` lines (CRLF) kept; a `-Pdomain` run
  renames, a validation or migration check refused for `-Pdomain` does not; both files: the new one read, the old one left
  as it was, the warning; a run stopped after the new file is written leaves the old one and the next run reads the new; a
  new file that does not read back is taken away; an old name that is no longer a regular file is not deleted; a listing,
  a validation and a migration check move nothing; an unreadable old file is named; the `.gitattributes` rewrite line by
  line. `GeneratedMergeTest`, `LanguageSwitchTest`, `RuntimePackagesProjectTest`, `SoftDeleteHierarchyTest` follow the
  new name. The guide's steps for merging a branch from before the rename were run in a scratch repository.
- Guides: env guide (en, ko) 9-2 - the new name, the files beside it, the rename, both files, merging an older branch;
  rule, migration and annotation guides (en, ko), README, the tour and getting started name the new file. The samples'
  `.gitignore` comments.
- Files: `GeneratedCache.java`, `RulesGenRunner.java`, `ElastiCoreExtension.java` (javadoc).
