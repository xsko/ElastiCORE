### Bug Fixes (v3 generated-file merge)

#### The same change on both sides, apart from blank lines, merges instead of conflicting
- Why: issue 230. A developer took `GET /schema/info` out of a generated controller by hand; `@expose(..., schema=false)`
  then took the same method out but left one blank line more. `ThreeWayMerge` compared the two changed stretches
  line by line, found them unequal and counted a conflict - so the owned file stayed `KEPT(yours - the DSL's changes
  to it do not merge)` on every build and took no further DSL change. Nine of ten such controllers differed only by
  that blank line. The note said `(1 place(s))` without saying where, so finding the line meant generating the file
  again beside it.
- Now: a stretch both sides changed so that it differs only in blank lines and in spaces or tabs at the end of a line
  merges without a conflict, and the developer's text is kept as it is. Indentation and spaces inside a line still
  count - they mean something in YAML, a Kotlin raw string or a string literal - so those stay conflicts, as does
  any stretch whose content differs. In a class body, the same method both sides added (one with a blank line more)
  is taken once instead of twice. The note of a kept file also says where each place starts in it:
  `... where you changed it too (1 place(s), at line(s) 42) - it is left as it is ...`. Files whose stretches
  differ in content merge, conflict and are noted exactly as before.
- Checked: `ThreeWayMergeTest` - the issue's three texts through `merge` and `mergeSource` (0 conflicts, the
  developer's text), both directions, trailing spaces and tabs, indentation and inner spaces still a conflict, a
  real conflict beside a blank line still one, a method added on both sides taken once, the conflict lines; the
  layout cases fail without the fix. `GeneratedMergeTest` - the kept factory's note names a line of the developer's
  file. `elcoreRulesCheck -Pstrict` (158 MATCH / 0 DIFF / 2 ACCEPTED), `elcoreTreeCheck` (0 differ / 0 missing /
  0 extra).
- Files: `util/ThreeWayMerge.java`, `pub/rules/verify/RulesGenRunner.java`, `ThreeWayMergeTest.java`,
  `GeneratedMergeTest.java`, `elasticore-env-guide.md` 9-2 and `elasticore-ddd-customize-guide.md` (en and ko).
