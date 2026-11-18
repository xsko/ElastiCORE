### Behaviour Changes (elcore3 - DSL validation)

#### An enum value longer than its field stops `elcore3`, as it stops `elcore`
- Why: `CANCELLED: CXL,Cancelled` with `code: string(2)` does not fit its field (for the `@db` field, the column it is stored in). The 2.x generator
  (`elcore`, `output = 'v2'`) fails on it; the rule engine (`elcore3`, the 3.0 code) listed it under
  `[elcore3] DSL validation warnings:` and the build went on, so the mistake surfaced as a failed insert at run time.
- Now (behaviour change, `elcore3` only): the finding is a model-check failure - generation runs to the end, then
  `elcore3` ends non-zero and the build fails with `[elcore3] the model check failed (n) - fix these and build again:`
  and `[elcore3] enum OrderStatus.CANCELLED: field 'code' value 'CXL' (length 3) exceeds max length 2. Make field
  'code' at least 3 characters long or shorten the value.` Only this finding: every other
  `DSL_WARN` stays a warning. The 2.x generator is unchanged (it already failed, with the old message).
- Checked: `EnumCodeLengthTest` - layered Java (`-Pv3`), hexagonal Java and layered Kotlin fail with that message;
  `string(3)` builds; a model with another DSL warning (`string(10) @length(max=20)`) still builds and says it. The
  failing case fails without the change. `elcoreRulesCheck -Pstrict`, `elcoreTreeCheck`; the samples and templates
  generate in CI.
- Guides: the DSL guide (5. Enumeration definition) and the migration guide (item 4), English and Korean.
- Files: `EnumerationModelLoader.java`, `EnumCodeLengthTest.java`, `elasticore-guide/elasticore-dsl-guide.md`,
  `elasticore-guide/elasticore-migration-guide.md` and their `ko/` copies.