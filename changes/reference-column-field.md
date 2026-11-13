### New Features (DDD references - `referenceColumn: field`)

#### `referenceColumn: field` names a reference's column for the field: `author: Member` -> `author_id`
- Why (issue 171): a reference named for its role (`author: Member`, `editor: Member`) got `author_member_id` under
  `referenceColumn: short`, and a root's reference to its own kind kept `parent_post_id`. A hand-written schema
  usually says `author_id`, `parent_id`; the only way there was a `@db(...)` on each field of each entity.
- What: a third value of `config: referenceColumn`. Each reference by id (ddd, hexagonal) is stored in the column
  `<field>_id`, a root's reference to its own kind included. A field's own `@db` / `@column` still wins. The id field
  keeps its name (`authorMemberId`): only the table changes.
- Unchanged: the default and `short` mean what they meant; a project that does not write `field` generates the same.
- Checked: `SelfReferenceByIdTest.referenceColumnFieldNamesTheColumnForTheField` generates a hexagonal project with
  `author` (required), `editor`, `reviewer @db(checked_by)` and `parent` and reads the PostgreSQL DDL and the entity:
  `author_id BIGINT NOT NULL`, the foreign keys on `author_id`, `editor_id`, `checked_by` and `parent_id`, no
  `author_member_id`, and the id fields `authorMemberId` / `parentPostId`.
- Files: `IdReferences.java`, `SelfReferenceByIdTest.java`, `elasticore-env-guide.md` (en, ko).
