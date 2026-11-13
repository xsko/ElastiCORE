### New Features (references - `@onDelete`)

#### `@onDelete(cascade | restrict | setNull)`: what becomes of the rows that point to a deleted row
- Why (issue 180): deleting a post that comments point to failed on the foreign key (409 `referenced`), or, without
  one, left comments that point to nothing. The cascade - and a comment's replies - was written by hand in the
  service, before the delete, in the same transaction.
- What: written on the side that points (`post: Post @onDelete(cascade)` on Comment). The CoreService of the deleted
  row carries it out before each delete it makes - by its key, a batch, and each row a search deletes, as it deletes
  it - in the same transaction. Per row: its `restrict`s (409 `"code": "restricted"`, naming what still points),
  its `setNull`s, its `cascade`s. A cascaded row's own `@onDelete` references come first, then it is removed through
  JPA (its soft delete and delete guards apply). Each row is visited once: rows that point to each other are refused
  with 409 `"code": "cycle"`. Any refusal undoes the whole delete.
- `setNull` is one JPQL update - a domain entity does not let its reference be set from outside; a `@Version` moves
  (`update versioned`; a row the same delete then removes is read again first), audit fields and Envers revisions do not. Soft-deleted rows are left out of `restrict` and
  `cascade`; they still hold their foreign key.
- How: in the deleted root's CoreService, `onDeletePost(em, row)` and a method per entity on the cascade's way
  (static in Java; members in Kotlin, beside an `@access` companion object), JPQL over the reference's id
  (`x.postId`) or the relation in layered code (`x.post.postId`). No service is handed another: the constructors are
  unchanged. The templates' delete-by-search line takes an inline slot (`deleteEachOpen` / `deleteEachClose`), empty
  unless `@onDelete` fills it. A project that keeps its own copy of `v3/service.tmpl` or `kt/service.tmpl` needs the
  two slots in its delete-by-search line, or its delete by search runs no `@onDelete`.
- Misuse stops the build: another value; a field that is no single reference to an entity of the domain; `setNull`
  on a required reference; the `mappedBy` side of a one-to-one; a composite-key target; a target that is a member of
  an aggregate, or a `cascade` from one; MyBatis and the 2.x-compatible output. A cascade into a soft-deleting entity
  from a target deleted outright is a warning.
- Generated Kotlin service tests: an entity that others point to with `@onDelete` gets its mocked entity manager's
  `createQuery` stubbed with a query that finds nothing. A test you edited keeps your version and needs that stub.
- Not done: the cascaded rows' domain events; `ON DELETE` in the DDL.
- Unchanged: a model without `@onDelete` generates the same.
- Samples: `elasticore-template-ddd` has a `board` domain (Post, Comment, Report, Bookmark - hexagonal; its DDL
  files gain the `brd_` tables, and the Ossie model's name becomes `billing_board_catalog_shop`), and its catalog's
  `Book.author` says `@onDelete(setNull)`. `elasticore-template-kt`: the content domain cascades an article's
  comments and their replies and clears its bookmarks; `Ticket.agent` says `@onDelete(restrict)` on the `@access`
  entity Agent.
- Checked:
  - `OnDeleteTest` (generator): Java, Kotlin and layered output, the order, soft-delete conditions, `update
    versioned`, the cycle refusal, the Kotlin members beside the `@access` companion, the generated test's stub, the
    MyBatis and 2.x-compatible refusals, the misuses, the aggregate-member refusals, the soft-delete warning.
  - `BoardOnDeleteTest` (template-ddd, HTTP on H2): a search deleting a versioned post and the post quoting it;
    comments and replies deleted and the bookmark cleared; a
    reported post refused with 409 `restricted`; a reported comment on the cascade's way refusing the post with
    nothing deleted; a cycle of replies refused with 409 `cycle`, deleted once broken.
  - `CatalogApiTest.onDeleteSetNull` (template-ddd, layered): an author deleted by key and by search, the books kept
    without it, the book's version moved.
  - template-kt (Kotlin on the generated H2 schema): `OnDeleteTest` - an article's comments, their replies and its
    bookmark; replies two deep; the whole module's 179 tests, the generated service tests among them.
- Files: `OnDeleteRules.java` (new), `EntitySpec.java`, `SpecFactory.java`, `RuleCatalog.java`,
  `ServiceAccessRules.java` (the inline slots), `v3/service.tmpl`, `kt/service.tmpl`, the tests above, the samples'
  blueprints, `DddSampleApp.java`, the template-ddd DDL files, `OssieExportTest.java`,
  `elasticore-annotation-guide.md` (en, ko).
