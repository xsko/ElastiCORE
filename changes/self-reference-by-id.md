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
- The column stays `parent_comment_id`, the name the relation's join column always had (and the name
  `referenceColumn: short` gives it too), and its foreign key stays, so the table does not change and no migration
  is needed; that is why this output can change. Code written by hand that set the parent object on create
  (`creation().setParent(...)`) sets its id instead. A member's reference to its own kind, a member's back reference
  to its root, the output without `ddd-reference`, 2.x and the 2.x-compatible output are unchanged.
- In the templates:
  - `Category.parentCategory` of the ddd templates is now referenced by its id. The regenerated `orm.xml` of
    `elasticore-template-ddd` maps `parentCategoryCatId` on `parent_category_cat_id`, with the relation read-only on
    it. In both, the column's entry in `semantic_model.yml` loses its `parentCategory -> Category` description, as
    the columns of other id references have none; the relationship `Category_parentCategory` stays.
  - `elasticore-template-kt` names `ddd-reference`, so `Employee.manager`, `Comment.parentComment`,
    `ContentCategory.parent` and `Category.parentCategory` are referenced by their ids too. Its four
    `db/schema-*.sql` do not change.
- Checked: `SelfReferenceByIdTest` - hexagonal and ddd (Java), hexagonal Kotlin, the declared DTO, the mapper, the
  PostgreSQL schema with one `parent_comment_id` and its foreign key in all three, a member's back reference that
  stays an object, and the layered output; three of its six tests fail without the change. The ddd, ddd-mybatis and
  Kotlin templates build with their tests.
- Files: `IdReferences.java`, `SelfReferenceByIdTest.java`, `elasticore-template-ddd` `orm.xml` and
  `semantic_model.yml`, `elasticore-template-ddd-mybatis` `semantic_model.yml`, `elasticore-rule-guide.md`
  "Refer to another aggregate by ID" (en, ko)
