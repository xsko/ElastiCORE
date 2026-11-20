### Bug Fixes (DSL warnings - an unknown `@extend` parent)

#### An `@extend(Parent)` that names no Entity or DTO of any domain is a DSL warning
- Why: an unknown field type is a DSL warning (`unresolved type 'X' - no matching Entity, DTO, or Enum found in any
  domain`), but an unknown `@extend` parent said nothing - the generated class extended a class that exists nowhere, and
  only the compiler said so.
- Now (owner's decision: a warning, not an error - a hand-written class may be the parent on purpose): the same check
  that reads the field types reads each entity's and DTO's `@extend` and stores a DSL warning for a parent that no
  domain declares: `[shop] Order: unresolved @extend parent 'Base' - no matching Entity or DTO found in any domain`. A
  full class name and a domain-qualified one (`other:Parent`) are not looked up, as for fields; a generic parent is
  read by its name (`Base<Long, String>` is `Base`). Being a DSL warning, `elcoreValidate -Pstrict` fails on it as it
  does on the field one. Both generators run the check (`ModelExtractor.validateGlobalTypeReferences`).
- Upgrading: a project whose parent is a class of its own named by its simple name (`@extend(BaseRecord)`, the class
  in the same package) compiled before and now warns - and fails `elcoreValidate -Pstrict`. Naming it by its full class
  name silences the warning; the generated `extends` is the same class.
- Unchanged: generated output - the check only stores a warning. The samples' parents are all declared, so they say
  nothing new.
- Checked: `ExtendParentWarningTest` - the warning for an entity and for a DTO, and the `-Pstrict` validation failing
  on it, failed before the change (no warning, validation passed) and pass after; a known parent, a full class name
  (also a generic one, `com.acme.support.Audited<Long, String>`) and a domain-qualified one give no warning, and
  `Missing<Long, String>` gives one warning, for `Missing`. `elcoreRulesCheck -Pstrict`, `elcoreTreeCheck`.
- Files: `ModelExtractor`, `ExtendParentWarningTest`, annotation guide (en, ko).
