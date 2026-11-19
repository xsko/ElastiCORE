### Bug Fixes (DSL validation - an enum value longer than its field)

#### The finding names the domain, says when the value is missing, and `-PlistRules` ends non-zero on it
- Why (review of `enum-value-length-fails-elcore3.md`): the message named no domain - two domains with the same enum
  and value said it once, and the 2.x generator printed it under "DSL Validation Warnings (unmatched domain)"; a value
  the enum line leaves out is filled with the constant's name, and was reported as if written:
  `value 'CANCELLED' (length 9)`. `-PlistRules` ended 0 on a model the check refused, which the build then refused.
- Now: `[shop] enum OrderStatus.CANCELLED: field 'code' value 'CXL' (length 3) exceeds max length 2` (2.x, a DSL
  warning under its domain, ending the run 1 as before); elcore3 adds `. Make field 'code' at least 3 characters long
  or shorten the value.` and fails the model check. A value left out: `[shop] enum OrderStatus.CANCELLED: field
  'code' has no value on the line, so the constant's name 'CANCELLED' (length 9) stands in and exceeds max length 2`
  (elcore3: `. Give CANCELLED a value for 'code' of at most 2 characters.`). `-PlistRules` prints the model check's
  failures and ends 1. Generated code does not change.
- Checked: `GenerationExitTest` - the 2.x generator in-process says the warning and records no model-check failure,
  and its `main` in a JVM of its own ends 1 with `[ERROR] DSL Validation: 1 warning(s) found.`; the missing-value
  wording in both generators; elcore3 names the domain; `-PlistRules` ends 1 on the refused model and 0 on a good one.
  They fail without the change. `EnumCodeLengthTest` still passes.
- Guides: the DSL guide (5. Enumeration definition), English and Korean.
- Files: `loader/module/EnumerationModelLoader.java`, `enums/EnumConstant.java`, `pub/rules/verify/RulesGenRunner.java`,
  `GenerationExitTest.java`, `elasticore-guide/elasticore-dsl-guide.md` and `ko/`.