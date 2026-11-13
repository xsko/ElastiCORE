### New Features (references - `@onDelete`)

#### `@onDelete(cascade | restrict | setNull)`: what becomes of the rows that point to a deleted row
- Why (issue 180): deleting a post that comments point to failed on the foreign key (409 `referenced`), or, without
  one, left comments that point to nothing. The cascade - and a comment's replies - was written by hand in the
  service, before the delete, in the same transaction.
- What: written on the side that points (`post: Post @onDelete(cascade)` on Comment). The CoreService of the deleted
  row carries it out before each delete it makes - by its key, a batch, a search - in the same transaction: every
  `restrict` first (409 `"code": "restricted"`, naming what still points), then `setNull`, then `cascade`. A
  cascaded row's own `@onDelete` references are carried out before it is removed through JPA (its soft delete and
  delete guards apply). A restrict met on the cascade's way undoes the whole delete.
- How: static methods in the deleted root's CoreService (`onDeletePost(em, row)`, one per entity on the cascade's
  way; Kotlin in a `companion object`), JPQL over the reference's id (`x.postId`) or through the relation in layered
  code (`x.post.postId`). No service is handed another: the constructors are unchanged.
- Misuse stops the build: another value, a field that is no single reference to an entity of the domain, `setNull`
  on a required reference; and MyBatis and the 2.x-compatible output, which do not carry it out yet.
- Not done: the domain events of the cascaded rows' services; `ON DELETE` in the DDL.
- Unchanged: a model without `@onDelete` generates the same.
- Samples: `elasticore-template-ddd` has a `board` domain (Post, Comment, Report, Bookmark - hexagonal), and its
  catalog's `Book.author` says `@onDelete(setNull)`; `elasticore-template-kt`'s content domain cascades an article's
  comments and their replies and clears its bookmarks.
- Checked:
  - `OnDeleteTest` (generator): the Java and Kotlin call sites and methods, the order, the layered JPQL, the
    MyBatis and 2.x-compatible refusals, the three misuses.
  - `BoardOnDeleteTest` (template-ddd, HTTP on H2): a post's comments and their replies deleted and its bookmark
    cleared; a reported post refused with 409 `restricted` and the detail; a reported comment on the cascade's way
    refusing the post's delete with nothing deleted.
  - `CatalogApiTest.onDeleteSetNull` (template-ddd, layered): an author deleted by key and by search, the books kept
    without it.
  - `OnDeleteTest` (template-kt, Kotlin on the generated H2 schema): an article's comments, their replies and its
    bookmark; a comment's replies two deep.
- Files: `OnDeleteRules.java` (new), `EntitySpec.java`, `SpecFactory.java`, `RuleCatalog.java`, the tests above,
  `elasticore-template-ddd` board blueprint and `DddSampleApp.java`, catalog and content `entity.yml`,
  `elasticore-annotation-guide.md` (en, ko).
