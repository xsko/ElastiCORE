### Bug Fixes (DDD rules - a root's reference to its own kind)

#### A root that refers to its own kind references it by its id, like any other root
- Why: under the `ddd-reference` rule set (`architecture: ddd` or `hexagonal`, or named in a domain's `env.yml`)
  another root is referenced by its id - `post: Post!` becomes `postId`, the written column, with `post` a read-only
  join on it. A root that refers to its own kind (`parent: Comment` on the root `Comment`) points to another
  instance, another aggregate, but stayed an object: the check that keeps a member's reference back to its root
  (`IdReferences.java`) also caught it, since a root's own root is itself. A DTO declared with `@template`, which
  leaves relations out, then had no parent at all, while the post stayed as `postId` (#93).
- What: the check is narrowed to a member's back reference - the target is the entity's own root and not the
  entity itself. A self-referencing root gets `parentCommentId` as the written column, `parent` becomes read-only
  (`insertable = false, updatable = false`, no setter), and the mapper writes and searches `parentCommentId`, as it
  does `postId`. The declared `CommentDTO` keeps `parentCommentId`. Java and Kotlin.
- The table does not change: the id field takes the relation's column, unchanged, `@db(...)` and all, and its foreign
  key stays, so no migration is needed. `referenceColumn: short` does not rename it either (`manager_emp_no` stays,
  for a key `empId @db(emp_no)`); the setting still names the column of a reference to another root.
- What changes for a project with such a root, under `ddd-reference`:
  - **A field a DTO declares under another name is dropped for the id field.** With
    `parentKey: long @ref(parent.commentId)` the DTO has `parentCommentId` instead, so a property of the API is
    renamed, and a client still sending `parentKey` is ignored: the row is saved with no parent and no error. This
    is what a reference to another root already did without a word; the generator now warns, for both. Use the id
    field's name, or mark the relation `@objectref`, which keeps the object and the declared name. A field declared
    under the id field's own name (`parentCommentId: long @ref(parent.commentId)`) stays, and the mapper writes the id.
  - **The row a parent id names is checked before the save.** The entity has `requireReferences`, which the service
    calls first: a parent id that names no row answers 404 `reference.<Entity>.<relation>`, not the foreign key's 409.
  - **The save is flushed and read back**, as for a root that references another root. Until that read-back the
    relation is `null`: domain code that sets `parentCommentId` and then reads `getParent()` before saving gets null.
  - Code written by hand that set the parent object on create (`creation().setParent(...)`) sets its id instead.
- Unchanged: a member's reference to its own kind, a member's back reference to its root, the output without
  `ddd-reference`, 2.x and the 2.x-compatible output.
- In the templates:
  - `Category.parentCategory` of the ddd templates is now referenced by its id. `CategoryDTO` no longer declares
    `parentCatId @ref(parentCategory.catId)`, as that would be dropped: the property of the API is
    `parentCategoryCatId`. The regenerated `orm.xml` of `elasticore-template-ddd` maps `parentCategoryCatId` on
    `parent_category_cat_id`, with the relation read-only on it. In both, the column's entry in `semantic_model.yml`
    loses its `parentCategory -> Category` description, as the columns of other id references have none; the
    relationship `Category_parentCategory` stays.
  - `elasticore-template-kt` names `ddd-reference`, so `Employee.manager`, `Comment.parentComment`,
    `ContentCategory.parent` and `Category.parentCategory` are referenced by their ids too. Its four
    `db/schema-*.sql` do not change.
- Checked: `SelfReferenceByIdTest` - hexagonal and ddd (Java), hexagonal Kotlin with its read-only relation and what
  its mapper writes, the declared DTO, the mapper, the PostgreSQL schema with one `parent_comment_id` and its foreign
  key in all three, a `@ref` declared under another name (dropped and said), under the id's own name (kept),
  `@objectref` keeping a declared name, `referenceColumn: short` with a key that has `@db`, a member's back reference
  and its reference to its own kind, a required reference, two references on one entity, `requireReferences` and the
  read-back of a root with no other reference by id, and the layered output. Nine of its fourteen tests fail without
  the change. The ddd, ddd-mybatis and Kotlin templates build with their tests.
- Files: `IdReferences.java`, `DtoBaseRules.java`, `SearchDtoBaseRules.java`, `SelfReferenceByIdTest.java`, both ddd
  templates' `shop/model/dto.yml` and `semantic_model.yml`, `elasticore-template-ddd` `orm.xml`,
  `elasticore-rule-guide.md` "Refer to another aggregate by ID" (en, ko)
