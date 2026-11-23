### Bug Fixes (v3 generated-file merge)

#### A hand-written member's Javadoc stays with it when the DSL adds a member at the same place (#295)
- Why: a generated-once file the developer added to - a contract test class, a behaviour class, a service - is merged with
  what the generator writes now. Reported on a contract test class: the developer's test, with its own Javadoc, sat right
  after the generated `nameIsRequired`; on regeneration the new generated `nameNotBlank` went between that Javadoc and the
  developer's method. The comment then sat on the generated test, and the developer's had none. The cause: the added member
  repeats the first lines of the member after it (`@Test`, a blank line), the line diff took the existing `@Test` as the added
  member's, and the developer's lines became two insertions - the Javadoc before that `@Test`, the rest after it - with the
  generated member landing between them.
- Now, in the body of a Java or Kotlin source (`ThreeWayMerge.mergeSource`), a run of kept lines between two pure insertions
  of one side is kept at the end of the second (or the start of the first) when the lines there are the same: the same lines
  kept, one insertion. The developer's member - comments, annotations, method - is merged whole, and the generated one is put
  beside it. The run does not move next to a line that side deleted or replaced, nor where the other side added the same
  lines on either side of it (the merge takes those once). Other merges (YAML and the other text files) are unchanged.
- A file the developer did not edit is still the generated file byte for byte, and one only the developer changed is theirs:
  the merge takes one side whole there, whatever the alignment.
- Checked: `ThreeWayMergeTest` - the reported class (fails before), a Javadoc of several lines with a line comment and an
  annotation more, the Kotlin case with a KDoc, the test the developer wrote as the DSL now writes it (there once), a member
  both added alike, a line both replaced alike (no conflict), the untouched and developer-only files, and the re-alignment
  itself case by case; `GeneratedMergeTest`, `ContractTestsTest`, `MigrationPreviewTest`, `SignInAndReferencesTest`,
  `RuntimePackagesProjectTest`; `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 / 0 / 0.
- Files: `ThreeWayMerge.java`.
