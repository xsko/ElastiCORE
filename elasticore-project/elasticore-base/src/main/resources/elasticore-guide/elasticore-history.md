## 3.0.261118-rc18

### Changes (conditions - `==`, `!=`, `>=`, `<=`)

#### A condition compares with `==`, `!=`, `>=` and `<=`
- Why: the annotation parser read any `=` in the arguments as the start of a key=value pair, so `@invariant(total >= 0, ...)`
  lost its condition and `@count` had to say `status is VISIBLE`.
- What: in `@count`, `@invariant` and `@specification` an `=` that is part of `==`, `>=`, `<=` or `!=` is a comparison,
  and the arguments are one value. `@invariant` and `@specification` gain `>=`, `<=`, `==` and `!=` on numbers and on
  `.size` (`@invariant(totalAmount >= 0, '...')`). Kotlin compares for equality with `compareTo` (`total!!.compareTo(7)
  == 0`) except an `Int` against a whole number - `==` between a `Long` and an `Int` literal does not compile -
  TypeScript with `===` / `!==`. `@count` (unreleased) says `status == VISIBLE and pinned == false` instead of `is`.
- Takes effect: an `@invariant` or `@specification` written with `>=`, `<=`, `==` or `!=` was dropped without a word
  (the guide said so); it is now generated - an invariant refuses a write that breaks it (422).
- Unchanged: every other annotation is read as before (`@search(>=)` included); a single `=` is still a key=value pair.
- Not done: a quote escaped inside a condition's message (`'can\'t'`) is not read as one; a `'text'` value of `@count`
  that holds `==`.
- Checked: `HasAssignmentTest` (the parser, `@search` unchanged), `ConditionComparisonTest` (`@invariant` and
  `@specification` in Java, Kotlin - a `Long` and a `Double` with `compareTo`, an `Int` and a count as written - and
  TypeScript), `CountTest`, the samples' `@count` (`BoardCountTest`, template-kt `OnDeleteTest`), the whole
  `elasticore-base` suite, `elcoreRulesCheck -Pstrict`, `elcoreTreeCheck`.
- Files: `AbstractModelLoader.java`, `DddCondition.java`, `SpecFactory.java`, `elasticore-annotation-guide.md`,
  `elasticore-rule-guide.md`, `elasticore-dsl-guide.md` (en, ko), the samples' blueprints.

### New Features (derived values - `@count`)

#### `@count(Comment.post[, status == VISIBLE])`: a count kept on a row of the rows that point to it
- Why (issue 179): a post's `commentCount` and `likeCount`, where a comment and a like are roots of their own, were
  counted again by hand in the same transaction as every change of a comment or a like. `@compute` reaches only the
  aggregate's own members.
- What: on the counting entity, `commentCount: long @count(Comment.post, status == VISIBLE)`. The counted entity's
  CoreService touches the count at each write of a row - a save, an update (the row it pointed to before and the one
  it points to now), a delete by key and by search, an update by condition, a command that moves its state - and a
  row an `@onDelete(cascade)` removes says so too. The runtime's new `io.elasticore.springboot3.domain.Counts`
  counts each touched row again, once, just before the transaction commits:
  after locking the counting row (a pessimistic select), with its own statement, rows in key order:
  `update Post x set x.commentCount = (select count(c) from Comment c where c.postId = x.postId and ...) where
  x.postId = :id`. A row as written is read at the commit, after `beforeSave` and listeners; a touch made while
  counting (a before-commit handler) is counted too; a `REQUIRES_NEW` transaction counts its own. Concurrent
  writes are handled by that design; it is not load-tested.
- The field is the model's: `@readonly`, `@updatable(false)` and `@default(0)` are implied - no request writes it,
  a save of the counting row does not write it back, a new row starts at 0. A soft-deleted row is not counted.
- Form: the condition compares with `==` (`status == VISIBLE and pinned == false`) - see `condition-comparisons.md`.
  A field is compared with an enum constant, `true`, `false`, a number or `'text'`.
- Misuse stops the build: no `Entity.reference`, a reference that does not point to the counting entity, a value
  of the wrong kind for the field, a count that is not a `long` or an `int`, a counting entity without a single key, a
  counted entity written by its aggregate's root or by a cascading collection (count it with `@compute`); MyBatis
  and the 2.x-compatible output.
- Not done: a row written around the generated service is not counted until a write through it touches the count;
  no backfill of existing rows (the guide gives the statement), no index on the counted column, no `DEFAULT 0` in the
  DDL; the condition is equality only; the eventually consistent reading through a domain event, for later.
- Unchanged: a model without `@count` generates the same.
- Samples: `elasticore-template-ddd` board - `Post.commentCount` (visible comments: `Comment` gains a `status`
  with `hide` / `restore`) and `Post.likeCount` (new root `PostLike`); its DDL files gain the columns and the
  table. `elasticore-template-kt` content - `Article.commentCount @count(Comment.article, approved == true)`; its DDL
  files gain the column.
- Checked:
  - `CountsTest` (runtime, H2 + Spring transactions, on Hibernate 6 and 7 - springboot3 and springboot4): the
    counting row selected `for update` before the count statement; counted once at the commit; a `REQUIRES_NEW` transaction
    inside one that touched a count, and one in an after-commit handler; a row written by a before-commit handler; a
    row read at the commit.
  - `CountTest` (generator): the JPQL (enum constant, boolean, soft delete), each hook in the Java CoreService
    (save, update, update by condition, delete by key, delete by search, the command), the Kotlin method and hooks,
    the layered key through the relation, the column `updatable = false` with `0L`, the DTO's read-only property,
    the generated Kotlin test's query stub, the MyBatis and 2.x-compatible refusals, four misuses.
  - `BoardCountTest` (template-ddd, HTTP on H2): three comments and a like counted; a hidden comment leaves the
    count and a restored one comes back; a deleted comment takes its reply out of the count with it (`@onDelete`);
    a like taken back; a delete by search; a request sending `commentCount` writes nothing.
  - template-kt `OnDeleteTest.anArticleCountsItsApprovedComments` (Kotlin on the generated schema): an approved
    comment counted and an unapproved one not; a deleted one leaves the count. The whole module's 180 tests pass.
- Files: `Counts.java` (springboot3, shared with springboot4), `CountRules.java` (new), `EntitySpec.java`,
  `SpecFactory.java` (`countsOf`, `prepareCounts`), `RulesGenRunner.java`, `RuleCatalog.java`,
  `DddCommandRules.java` (the command's hook), `OnDeleteRules.java` (a cascaded row's counts, the shared test stub),
  the tests above, the samples' blueprints and DDL files, `elasticore-annotation-guide.md` (en, ko).

### Documentation (the pet store, README, tour, getting started)

#### The pet store uses `@count` and `@onDelete`; the site guide, README and tour say what is new
- Why: `@count`, `@onDelete`, `@search(words:...)` and `elcoreValidate` were in the guides only; the pet store's DSL
  guide page still listed "a product with items cannot be deleted" as a rule the DSL could not say (the database's
  foreign key) - since `@deleted` such a product is taken off the catalog - and "default order" as missing, which
  `@sort(name+)` already does. Its numbers at the top were typed by hand and stale.
- What: the pet store's `Category.productCount: long @count(Product.category)` (discontinued products not counted;
  `data.sql` counts the seeded rows once) and `Product.category @onDelete(restrict)` (a category a product on sale
  names answers 409 `restricted`; one whose products are all discontinued is still refused, by the foreign key - 409
  `referenced`). `dsl-guide.html` (built by `guide/build_guide.py`): the one search box and the default order in
  "1. 상품 둘러보기", the count and the restrict in "2. 상품 관리" (now all DSL), `@onDelete`/`@count` in "어디서
  동작하나", the review's rows 7, 9 and 10; the numbers at the top are counted by the script (DSL lines, generated
  and hand-written Java, the TypeScript client). README, tour and getting started: `@count`, `@onDelete`, the search
  box, and `elcoreValidate` in CI; the DSL guide's annotation summary gains `@onDelete` and `@count` (en, ko).
- Checked: `ShopFlowTest.anAdministratorKeepsTheCategoriesTidy` (Fish counts 4; a new category 0, a product 1,
  discontinued 0; a request's `productCount` is not written; 409 `restricted` while a product is on sale, 409
  `referenced` after), the pet store's tests, its DDL files written again (`product_count`).
- Files: the pet store's `entity.yml`, `data.sql`, `db/schema-*.sql`, `ShopFlowTest.java`, `guide/build_guide.py`,
  `static/dsl-guide.html`; `README.md`, `docs/tour.md`, `docs/getting-started.md`, `elasticore-dsl-guide.md` (en, ko).

### New Features (build - `elcoreValidate`)

#### `./gradlew elcoreValidate`: the DSL and the generated files checked for CI, nothing written
- Why: 2.x had `elcore -Pmode=valid` for a CI step; a project on the 3.0 rule engine had no way to check that its DSL
  passes and that the generated files in git are what the DSL generates now, short of generating and diffing.
- What: a plugin task that runs the build as configured (architecture, language, modules, DDL, merge) with nothing
  written but `build/elasticore/validate.md`. It fails (exit 1) on a model check failure or a failed generation, a
  generated file whose text the DSL would change (the DDL schema, orm.xml and the messages bundle included), one the
  DSL no longer produces, a Flyway migration the build would write, and a file with a conflict; `-Pstrict` fails on a
  DSL warning too. A file the developer edited and the build leaves alone passes and is listed; so does a file the
  build takes for the developer's (no header, no base in `.elasticore/cache`), as not checked. A file the build would
  write that is not there fails only where the project keeps its generated sources in git (one the generator owns is
  there - a generate-once skeleton does not count); a project that generates them in each build is checked for its DSL
  and the generated files it commits. The DDL is read off the sources as the run would leave them (laid out in a
  temporary directory). A build with `output = 'v2'` runs 2.x's `elcore -Pmode=valid`. Not checked: `schema.yml`, the
  context map, the Ossie model.
- Unchanged: `elcore3`, the build and `elcoreMigrationCheck`; the task runs only when asked for.
- Checked: `ValidateTest` (an up-to-date project passes and nothing is written; a changed DSL fails and names the file;
  an entity taken out fails on the files no longer produced; an edit of the developer's passes and is listed; a model
  check failure; a warning with and without `-Pstrict`; a project without generated files; the DDL read off the
  sources the run would leave, stale after a DSL change; a Flyway migration not written yet fails and neither it nor
  the state is written; orm.xml and the messages bundle without merging; committed skeletons alone; a project without
  its cache), `ElastiCorePluginTest` (`elcoreValidate` registered, not run by the build, the build's settings and
  `-Pstrict` passed, the 2.x check for `output = 'v2'`), `elasticore-template-ddd` (passes; generated sources not in
  git). The DDL and migration tests still pass.
- Files: `RulesGenRunner.java` (`finishValidate`, the DDL overlay, orm.xml and messages in a check), `DdlRun.java` (a
  dry run), `ElastiCorePlugin.java`, `elasticore-rule-guide.md`, `elasticore-dev-guide.md` (en, ko).

### Bug Fixes (DDL - Envers' revision table)

#### `revinfo.rev` numbered as Hibernate numbers it: an identity column on H2 and SQL Server
- Why: an `@audited` entity's write failed on H2 with `NULL not allowed for column "REV"` - Hibernate inserted
  `insert into revinfo (revtstmp,rev) values (?,default)`. Envers' revision number is Hibernate's native key, and
  Hibernate's dialects (6.5, 6.6 and 7 alike) make it an identity column where the database has one - H2, SQL Server -
  and a sequence on PostgreSQL and Oracle. The DDL wrote a plain column with `revinfo_seq` for every database.
  `elasticore-template-kt`'s test turned Envers off around it.
- What: the DDL writes `rev INTEGER GENERATED BY DEFAULT AS IDENTITY` on H2 and `rev INT IDENTITY(1,1)` on SQL Server,
  without `revinfo_seq`; PostgreSQL and Oracle are unchanged (`rev` plus `revinfo_seq`, by 50). Hibernate 6.5 writes the
  same for H2 itself (`rev integer generated by default as identity`). A Flyway history: a new one makes the sequence
  for PostgreSQL and Oracle only; one built before is told, in its H2 and SQL Server migrations only, to review
  `revinfo.rev is now generated by the database` - the table has to be made again by hand there (SQL Server cannot
  alter a column into an identity). The state records the key's kind (`nativeKey`, `nativeOnly`), and a state without
  it reads as before.
- Unchanged: PostgreSQL and Oracle; a project without `@audited`.
- Checked: `JpaSchemaReaderTest` (the four databases' DDL, the first migration's sequence on PostgreSQL and Oracle only,
  the state's round trip, the review change for an older history), `elasticore-template-kt` with Envers on - deleting an
  article writes its revision (`cms_article_aud`, `revinfo`) - and its 180 tests, `elasticore-template-ddd`'s tests,
  the whole `elasticore-base` suite. The three samples' committed `schema-h2.sql` and `schema-sqlserver.sql` were
  written again (`elasticore-template`'s SQL Server file also gains the `VARCHAR(MAX)` it was missing for a JPA long
  text).
- Files: `Column.java`, `Database.java`, `Schema.java`, `SchemaDiff.java`, `SchemaWriter.java`, `JpaSchemaReader.java`,
  `elasticore-env-guide.md` (en, ko), the samples' DDL.

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

### Changes (Ossie export - enum codes)

#### An enum field's stored values and their meanings are data in the export
- Why (issue 174): the codes of an enum column were only a sentence in `ai_context`
  (`"Codes: NONE = Disabled, YYYY, ..."`). A program had to parse it: a code whose label is the same is written alone,
  and a label holding `, ` or ` = ` cannot be read back.
- What: the field's ELASTICORE extension carries them beside the enum's name:
  `data: {"enumeration": "DatePart", "codes": {"NONE": "Disabled", "YYYY": "YYYY"}}`. The keys are what is stored -
  the `@db` code, the name under `@jpa:Enumerated(STRING)`, or the ordinal (`"0": "SMALL (Small)"`). `ai_context`
  stays as it was.
- Changes the output: every exported enum field gets the `codes` key, so `semantic_model.yml` changes wherever there
  is an enum. The samples' committed models are regenerated with it (pet store, `elasticore-template-ddd`,
  `elasticore-template-ddd-mybatis`); nothing else in them moves. Not the 2.x-compatible output: the export is
  `ossie-export` only.
- Checked: `OssieExportTest.anEnumsCodesAreData` - a code enum with a label equal to its code and one holding ` = `,
  and an ordinal enum; the `ai_context` sentence unchanged.
- Files: `OssieSemanticModelExporter.java`, `OssieExportTest.java`, the three `semantic_model.yml`,
  `elasticore-rule-guide.md` (en, ko).

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

### New Features (search - `@search(words:...)`)

#### One search box over several fields: `keyword: string(50) @search(words:title, body, author.nickname)`
- Why (issue 182): a search box that looks in the title, the body and the author's name had to be a hand-written
  `Specification`: split the words, an OR over the fields per word with a LEFT JOIN for the author, an AND over the
  words, the like escaped.
- What: a search DTO field with `@search(words:a, b, c.d)`. Its text is split on white space (the ideographic
  space too); every word has to be found, each in any of the fields - a contains ignoring case, `%` and `_` as
  characters (as `@search(like)`). The first five words are searched. A to-one relation path is joined LEFT, each
  once. A path through a collection is asked per word with EXISTS: each word may be in another member, and the row
  comes once without a DISTINCT. The 3.0 JPA mapper (Java, Kotlin) calls the runtime's new
  `io.elasticore.springboot3.mapper.Words.match`.
- Misuse: a path that is not a text field of the entity is said by a DSL warning and left out; when no path is left
  the build stops - the property would match every row, and a delete or update by the condition would act on all.
- Not in: MyBatis (`mode: mybatis`) says so in a warning and does not search by it. The 2.x-compatible output says
  so and leaves the property out of the search DTO.
- Form: no space after `words:` - YAML reads `: ` as a key (the form the issue sketched does not load).
- Unchanged: a model without the form generates the same.
- Pet store: `ProductSrchDTO` is now declared, with `keyword` beside the conditions `@expose` derived before
  (`productId`, `name`, `description`, `categoryId`) - the same properties, so the web client's `categoryId` search
  stays; their order changed, and with it the order of the Lombok all-args constructor's parameters.
- Checked:
  - `WordsTest` (runtime, H2 + Hibernate): any field, every word, escaping, the ideographic space, LEFT JOIN, a
    two-hop path (`author.mentor.nickname`), a collection path - two matching members give one row, two words in
    two members match - and a collection path sorted by a related field (no DISTINCT to break the ORDER BY).
  - `SearchWordsTest` (generator): the Java and Kotlin mapper lines, the DTO's `@Size(max=50)` and description, the
    three kinds of wrong path, the build stopping when none is left, the MyBatis warning, the 2.x-compatible output
    leaving the property out.
  - Pet store `ProductSrchDTO.keyword` is generated, compiled and searched over HTTP by `WordsSearchCheck` (to-one
    paths) - on H2 in `SchemaValidateTest`, and on PostgreSQL, Oracle and SQL Server in the databases job. The
    collection path is checked on H2 only.
- Files: `Words.java` (springboot3, shared with springboot4), `MapperSpecRules.java`, `SearchDtoBaseRules.java`,
  `MyBatisSearchRules.java`, `SpecFactory.java`, the tests above, pet store `dto.yml`, `SchemaValidateTest.java`,
  `SchemaValidateOnDatabase.java`, `elasticore-annotation-guide.md` and `elasticore-dsl-guide.md` (en, ko).

### New Features (TypeScript client)

#### `configureApi` and a per-call `RequestInit` in the generated TypeScript client
- Why (issue 176): the client could set only a base URL. An API on another origin needs `credentials: 'include'`
  for its session cookie, a token needs an `Authorization` header, a test or a server (SSR) needs its own `fetch`,
  and one call may need an `AbortSignal` - none of these could be passed without editing `apiSupport.ts`.
- What: `apiSupport.ts` gains `configureApi({ baseUrl?, fetch?, init? })`. `init` is a `RequestInit`, or a function
  called for each request (sync or async). Every generated function (`create`, `findById`, `search`, ... the
  lifecycle commands, `signIn`, `signOut`, `me`) takes an optional `init?: RequestInit` last; `request` and
  `requestOrNull` take it too. Headers merge `Content-Type`, then the configured ones, then the call's - the later
  one wins, whatever its case. `setApiBaseUrl` stays.
- Unchanged: a client that calls neither sends the same request as before (method, `Content-Type`, body) to the
  same URL with the global `fetch`. Existing callers compile unchanged: the new parameter is optional and last.
- Checked: `check/apiSupport.test.ts` in `elasticore-template-kt` runs the generated `apiSupport.ts` in node - the
  request without configuration, and a configured base URL, fetch, function `init` read for each request, and a
  per-call `Headers` and `cache` over it (15 tests pass). `elcore3tsVerify -Pstrict` compiles the generated
  clients; the pet store's `webBuild` (`noUnusedLocals`) builds. `TypeScriptModelTest` and
  `SignInAndReferencesTest` assert the generated signatures and calls.
- Files: `ts/apiSupport.tmpl`, `TypeScriptApiRules.java`, `TypeScriptModelTest.java`, `SignInAndReferencesTest.java`,
  `elasticore-template-kt/src/main/ts/check/apiSupport.test.ts`, `elasticore-rule-guide.md` (en, ko).

## 3.0.261117-rc17

### Bug Fixes (DTO - lists of values and arrays)

#### A DTO carries a list of values: `List<Long>`, `List<String>`
- Why (issue 158): a DTO field `tagIds: List<Long>` or `tags: List<String>` was left out of the DTO without a word,
  in Java and in the TypeScript client, though the DSL guide's table says `List<T>` is `List<T>`. The DTO keeps a
  field whose type names a model component; a list's item, `Long` or `String`, names none, so the list was taken
  for a relation whose counterpart has no DTO - in 2.x, and so in the 2.x-compatible output, the same. `long[]`
  was an unresolved type, and dropped with a warning.
- After (3.0 code - Java, Kotlin, TypeScript): a list or set of a value type is carried as that value's Java type,
  `List<Long>`, also for the DSL's words (`List<long>`, `List<string>` are `List<Long>`, `List<String>`);
  TypeScript reads `number[]`, `string[]`. `long[]`, `double[]` and `boolean[]` are arrays beside `int[]`, wrapped
  as it is (`Long[]`), known to the rule engine only, as `Instant` and `byte[]` are. A list of a type nobody knows
  (`List<Strng>`) is a DSL warning naming the field instead of a silent drop.
- 2.x-compatible output: unchanged - the list is left out as 2.x leaves it, and the build now says so. 2.x
  generator unchanged.
- Checked: `ValueListDtoTest` (Java, Kotlin, TypeScript, 2.x-compatible); `ProductDTO` of `elasticore-template-ddd`
  (Java hexagonal) and `elasticore-template-kt` carry `relatedIds: List<Long>` and `keywords: List<string>`,
  generated, compiled and tested.
- Files: `SpecFactory`, `BaseFieldType`, `RulesGenRunner`, `RulesVerifyRunner`, the DSL guide (en, ko).

### Bug Fixes (`@expose` - page size and methods)

#### `@expose(pageSize=50, methods="...")`: the page size is kept beside methods
- Why (issue 160): `@expose(50, methods="create,search,update,delete")` generated `pageSize=20` in the search DTO, with
  no warning; `methods` was applied. The loader turns `@expose` into `@expose @dto @searchable @pageable` and left the
  brackets after the last of them, so `@pageable(50, methods="...")` was not a page size. `@expose(pageSize=50)`
  was not read at all (a warning said `@expose` takes `methods` only).
- After: `@expose(pageSize=N, methods="...")` - the written form, each value named - gives the search DTO page size
  N and the controller the methods listed. `@expose(50)` (2.x) and `@expose(50, methods="...")` page by 50 too;
  `@expose` without a size pages by 20 as before. `pageSize` is no longer warned about.
- 2.x: a model whose `@expose` has no `=` in its brackets loads as before (`elcoreRulesCheck`, `elcoreTreeCheck`).
- Checked: `ExposePageSizeTest` (hexagonal Java) - named, 2.x, positional beside methods, methods alone; it fails on
  the loader before the change.
- Files: `DataTransferModelLoader`, `ControllerBaseRules`, the annotation guide (en, ko).

### Bug Fixes (2.x generator - `@forceupdate` on a relation)

#### An entity's relation or list field with `@forceupdate` gives a mapper that compiles
- Why (issue 166): the 2.x generator's entity-to-DTO line for a field read through a mapper method (a relation, a
  list of DTOs) opened that call - `to.setCustomer(toDTO(from.getCustomer());` - and never closed it: the line that set
  the closing bracket was commented out. The mapper did not compile. The rule engine's 2.x-compatible code wrote
  `setVal(from.getCustomer(), to::setCustomer, isSkipNull, e->toDTO(e,c));` for the same model.
- After: such a field goes through `setVal` in the 2.x generator too (both variants, with and without the
  EntityManager), the line the 2.x-compatible code writes. A value field with `@forceupdate` is assigned straight
  through as before. On the update side (DTO to entity), `@forceupdate` on a relation keeps what it did.
- 2.x: only output that did not compile changes.
- Checked: `ForceUpdateRelationTest` runs the 2.x generator and the 2.x-compatible rules on the same model: the lines
  are the same and balanced; it fails on the generator before the change.

### Bug Fixes (3.0 code - API errors)

#### A CHECK violation is answered 400 `check`
- Why (issue 167): the generated `XxxApiErrors` knew NOT NULL, UNIQUE and FOREIGN KEY. A row breaking a CHECK
  constraint (one a migration or Hibernate wrote) was answered 409 `conflict` - "a unique value is already taken, or
  the row is still referenced" - and on SQL Server 409 `referenced`, as its 547 is the code of a FOREIGN KEY and a
  CHECK alike.
- After: a kind of its own, answered 400 `check` ("A value is outside what the column allows."), as a value breaking
  its constraint is: 23513 (H2), 23514 (PostgreSQL), 23000 with 2290 (Oracle), 3819 (MySQL), and 547 when SQL
  Server's message names a CHECK (it does in every language); any other 547 stays `referenced`. The code is in
  every domain's message bundle (`error.check`). Java and Kotlin.
- `XxxApiErrors` is generated once: a file never edited is written again with it; an edited one keeps its own, and
  takes the case over by hand.
- Checked: the pet store makes each database raise a CHECK, a UNIQUE and a FOREIGN KEY violation and hands Spring's
  exception to the generated `PetstoreApiErrors`: 400 `check`, 409 `unique`, 409 `referenced` - on H2 locally, and in
  CI on Oracle, PostgreSQL and SQL Server (`IntegrityKindCheck`).
- Files: `MapperSrcPublisher`, `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `ErrorCatalog`, the rule guide (en, ko).

### Documentation (dev guide - the third argument of a `Q` condition)

#### The dev guide says what `chkEmpty` does, and where not to use it
- Why: every generated `Q` (2.x, v3 and Kotlin) has `field(Op op, Object value, boolean chkEmpty)` beside the
  two-argument form, and no guide named it. The `true` reads like "ignore case". A blank value then drops the condition
  without a word, which on a condition that must always hold - the tenant a user belongs to - returns every row.
- What: dev guide §5 (en, ko) shows the three-argument form as an optional search condition, says what counts as
  empty (null, a blank string, an empty collection), that `IS_NULL` and `IS_NOT_NULL` always apply (#72), that the
  two-argument form is `chkEmpty = false`, and not to use it for a condition that must hold.
- Checked: `FieldInfo.where(Op, Object, boolean)`; `QBaseRules`, `KotlinQRules` and `EntitySrcFilePublisher` write the
  three-argument method. `GuideTranslationTest`.
- Files: `elasticore-dev-guide.md` (en, ko).

### Bug Fixes (3.0 code - lifecycle commands, checks, search DTO, Ossie, @expose)

#### A lifecycle command keeps a parameter named as a field; any other is said
- Why (issue 170): `reject` with a `reason` the request had to send changed the state and dropped the value -
  answered 200, kept nothing, and the build said nothing.
- After: a command parameter named as a field of the entity, of the same type (`reject: rejectReason: string(500)!`
  beside the field `rejectReason`), is stored by the generated move, after the move (a refused command stores
  nothing). Any other parameter is a warning naming the command and the parameter, with the two ways out: name it
  as the field it fills, or override the command. Java and Kotlin (a parameter that may be null does not fill a
  required field in Kotlin, and is said instead).
- Checked: `CommandParamKeptTest`.

#### The reserved-word warning names the databases the domain writes for
- Why (issue 168): it named Oracle, PostgreSQL and SQL Server whatever `ddl.databases` said, and `comment`, `level`
  and `size` are refused by Oracle alone - a project on H2 and PostgreSQL heard it on every build.
- After: each name with the databases that reserve it (each from that database's own list), of those
  `ddl.databases` writes for; with no DDL, of all four. No new word is looked for. JPA and MyBatis.
- Checked: `EntityChecksTest` (a domain on H2 and PostgreSQL hears nothing of `level`; every word maps to a
  database), `MyBatisGenerationTest`.

#### A search DTO's string field has the declared length as its @Size
- Why (issue 169): the DTO checked `@Size(max=200)`, the search DTO took a string of any length.
- After: `@Size(max=n)` on a string search field, from its declared length, in the 3.0 code (Java, Kotlin); the
  search body is validated already. The 2.x-compatible output is unchanged.
- Checked: `SearchSizeTest`.

#### The Ossie export does not declare an integer division portable
- Why (issue 157): `@formula("quantity / pack_size")` over two int columns was written as the portable `sql`, where
  OSSIE_SQL_2026 makes `/` true division (apache/ossie#687): the document said 2.5 where the database computes 2.
- After: a formula that divides and names an integer column keeps its SQL under the opaque `ANSI_SQL` dialect - what
  the database runs. A division of decimals, and a formula without `/`, stay portable.
- Checked: `OssieExportTest`.

#### @expose(methods): one of each pair, GET /schema/info left out, no unused import
- Why (issue 172): no list served `PUT /` without `PUT /batch` or `DELETE /{id}` without `POST /delete`, nothing left
  out `GET /schema/info`, and a narrowed controller kept the imports of the methods it left out.
- After: `updateOne`, `updateBatch`, `deleteOne`, `deleteBatch` beside `update` and `delete`; `schema=false` on
  `@expose` leaves out `GET /schema/info`. The `@restExclude` deprecation now names the list that serves the same
  endpoints. A narrowed 3.0 controller drops the imports nothing in it uses; other files are written as before.
- Checked: `ExposeMethodsTest`, `ControllerMethodRulesTest`.
- Files: `DddStateRules`, `ReservedWords` (new), `EntityChecks`, `MyBatisSchemaRules`, `RulesGenRunner`,
  `SearchDtoBaseRules`, `OssieSemanticModelExporter`, `ControllerBaseRules`, `SourceCode`; the DSL and annotation
  guides (en, ko).

### Bug Fixes (`@search` - like)

#### A contains, prefix or suffix search looks for the value as written
- Why (issue 159): `@search(like)` and the prefix / suffix forms put the caller's value into the LIKE pattern as it
  was, so its `%` and `_` were wildcards: `100%` found every row with `100` in it, `kim_t` found `kimat` too, and a
  value of `%_%_%_%` was a pattern the database had to work through. The guide calls `like` a contains search.
- After: in the 3.0 code (JPA, Java and Kotlin) and the MyBatis search, the value's own `%`, `_` and the escape
  character are escaped with `!`, and the LIKE says `ESCAPE '!'` - every database reads that escape, and the MyBatis
  repository queries (`Containing`, `StartingWith`) already did so (`Rows.like`). The JPA mapper gets a `likeText`
  helper; the MyBatis search binds through `Rows.like`.
- Unchanged: `auto` (a string `@search` without a condition) is a like only when the value holds a `%`, so there the
  caller's `%` stays a pattern, as documented. The 2.x-compatible output and the 2.x generator are unchanged
  (`elcoreRulesCheck`, `elcoreTreeCheck`).
- Checked: the pet store's `Category.name @search(like)` finds `Like 100% wool` and not `Like 1000 wool`, and `a_b`
  not `axb`, on H2 and - in CI - on Oracle, PostgreSQL and SQL Server (`LikeSearchCheck`); the MyBatis shop's like
  and `_%` searches the same (`ShopApi.everySearchConditionOfTheModelNarrowsTheRows`, H2 and the three databases).
- Files: `MapperSpecRules`, `MyBatisMapperRules`, `v3/mapper.tmpl`, `kt/mapper.tmpl`, the annotation guide (en, ko).

### Bug Fixes (runtime - DBMS ports)

#### A port method whose only parameter is a list of values binds it by name
- Why (issue 164): `findByIds(ids: list<long>)` with `IN (:ids)` found nothing and `deleteByIds` deleted nothing.
  With one parameter, the proxy bound it by name only when it was a single value; a `List` was taken for an object,
  read by its getters (`getIds()` on an `ArrayList`), and `:ids` bound as null - with a stack trace and no error.
  With a second parameter it worked.
- After: a lone collection or array of simple values (`List<Long>`, `Set<String>`, `long[]`) is bound by its name, as
  one value is. A list of DTOs or maps stays the input of a batch.
- Checked: `DbmsPortInputTest` (through the proxy: a list, a `String[]`, a `long[]`; a delete by ids is one statement,
  a list of rows still a batch); `JdbcDbmsGatewayTest` (`IN (:ids)` on H2 answers 1 and 3).

#### An enum field of a port's result row is read as the enum
- Why (issue 165): `SqlPortGateway.convertValue` knew numbers, booleans, dates and text, and threw for an enum, so a
  result DTO with an enum field failed every call (`Failed to map tuple to DTO`), whether the column held the `@db`
  code or the constant's name. The JPA, JDBC and MyBatis port paths all map rows through it.
- After: a number is the ordinal; text is what the enum's `fromCode(String)` answers (the generated enums have it; a
  Kotlin enum's companion too), otherwise the constant of that name; anything else is an error naming the value. A
  port method answering `List<SomeEnum>` reads the first column as one value.
- Checked: `JdbcDbmsGatewayTest` - an `@db` code, a name, an ordinal, a list of enum values, on H2.
- Files: `DbmsServiceProxyFactory`, `SqlPortGateway`, the port guide (en, ko).

### Bug Fixes (3.0 code - table names)

#### The build no longer warns about an `@table` that starts with `table.prefix`
- Why: rc16 warned on every build about `@table(el_mcp_server_def)` with `table.prefix: el_`, which maps to
  `el_el_mcp_server_def` as in 2.x. A project 2.x generated has exactly that table in its database, so the warning
  was wrong there and came back on every build.
- Before: a warning per entity on every `elcore3`. After: the build says nothing; `elcoreMigrationCheck` lists each
  such `@table` with the table it maps to, once, with what to write if the database has the name without the doubled
  prefix.
- Checked: `TablePrefixAndLargeValueTest` - nothing in the build's output, the line in the migration check report.
- Files: `RulesGenRunner`, `EntityChecks`, the migration guide (en, ko).

## 3.0.261116-rc16

### Bug Fixes (3.0 code - table names and large values)

#### The 3.0 code puts table.prefix before an `@table` name, as 2.x does
- Why: with `table.prefix: el_` and `@table(note)`, 2.x generated `@Table(name="el_note")`, and the 3.0 code
  `@Table(name = "note")`. It generated and compiled without a warning and failed at run time
  (`relation "note" does not exist`), for every entity with `@table` in a project moving from 2.x. 2.x put the prefix
  before an `@table` name that already had it too (`@table(el_def)` is `el_el_def`).
- Before: the 3.0 JPA entity, the MyBatis SQL, the reserved-name check and the Ossie `source` used the `@table` name
  alone. After: all of them name the table `table.prefix` + the `@table` name (`EntitySpec.tableName()`), as 2.x
  does. An `@table` that starts with the prefix is warned about with the name it maps to and the line that names the
  table without the doubled prefix. The v3 samples that wrote `@table(shop_products)` beside `prefix: shop_` now write
  `@table(products)`: their tables keep their names.
- 3.0 is not released: a project that rewrote its `@table` names with the prefix for the earlier release candidates
  gets the warning and writes them back.

#### `clob` and `blob` keep the value in the row
- Why: the 3.0 code mapped `clob` to `String` + `@Lob`. On PostgreSQL, Hibernate keeps such a value apart as a large
  object and the column holds only its number (oid): SQL cannot read it, and Hibernate 7's `ddl-auto: validate`
  refuses a `text` column (`found [text], but expecting [oid (Types#CLOB)]`). `blob` the same.
- After: `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)` / `LONG32VARBINARY` - text / bytea on PostgreSQL, clob / blob on
  Oracle, varchar(max) / varbinary(max) on SQL Server. The 2.x-compatible output keeps `@Lob`.
- SQL Server: Hibernate maps a long text there to varchar(max) and refused the NVARCHAR(MAX) the DDL wrote, so a JPA
  long text (`clob`, a `@Lob` String) is now `VARCHAR(MAX)` in the generated DDL - with a UTF-8 collation (SQL Server
  2019+) it keeps any language, as Oracle keeps it in a CLOB of a UTF-8 database (an NCLOB was considered and left:
  not what Korean databases use). MyBatis's clob stays NVARCHAR(MAX). The pet store's SQL Server test database is
  UTF-8. The build and `elcoreMigrationCheck` warn about a `String` with `@Lob` in the project's code (one
  added by hand to a 2.x file), which does the same on PostgreSQL.
- Checked: `TablePrefixAndLargeValueTest` (Java, Kotlin, MyBatis), `StringLobTest`, `OssieExportTest`; the pet store's
  `Category` has a `clob` and a `blob`, and `SchemaValidateOnDatabase.aLargeValueIsKeptInTheRow` writes them through
  Hibernate and reads them back with SQL on Oracle, PostgreSQL and SQL Server (CI).
- Files: `EntitySpec`, `JpaEntityAnnotationRules`, `JpaEntityBaseRules`, `EntityChecks`, `MyBatisSql`,
  `OssieSemanticModelExporter`, `RulesGenRunner`; the DSL, annotation, rule and migration guides (en, ko).

## 3.0.261115-rc15

### Bug Fixes (API errors order check)

#### The check no longer warns about a generated `XxxApiErrors` that is already next to last
- Why: on rc14, `elcore3` and `elcoreMigrationCheck` warned on every build that a catch-all advice of the
  application's tied with the generated `XxxApiErrors`, even when it was already
  `@Order(Ordered.LOWEST_PRECEDENCE - 1)`. The check took the first " class " in the file as the declaration;
  the generated Javadoc says "Handle one of those exceptions in this class", so it never saw the `@Order` below
  and read the file as having none.
- Before: the warning on every build, also after the file was regenerated. After: comments are left out before
  the annotations are read and the declaration is found as `class Name` / `object Name`; the generated Java and
  Kotlin files are not flagged, the same files at `LOWEST_PRECEDENCE` still are.
- Checked: `AdviceOrderTest` runs the check over the real `v3/apiErrors.tmpl` and `kt/apiErrors.tmpl`.
- Files: `RulesGenRunner.java`, `AdviceOrderTest.java`.

## 3.0.261114-rc14

### Changes (v3 API errors - their order beside an application's catch-all)

#### A generated `XxxApiErrors` comes before an application's advice without `@Order`; a tie is warned about
- Why: a project moving to 3.0 had a common `@RestControllerAdvice` with an `@ExceptionHandler(Throwable.class)` and no
  `@Order`. The generated `XxxApiErrors` was `@Order(Ordered.LOWEST_PRECEDENCE)`, the same as an advice without one:
  Spring consults advice beans of the same order in no set order and the first with a matching handler answers, so a
  generated controller's 404 could go out as the catch-all's 500 - found by reading the code, not by a test.
- What: a new `XxxApiErrors` (Java and Kotlin, v3) is `@Order(Ordered.LOWEST_PRECEDENCE - 1)`: an advice the
  application orders itself still comes first, one without an order comes after. An `XxxApiErrors` never edited is
  regenerated with it; for one that was edited, or any tie, `elcore3` and `elcoreMigrationCheck` warn and name both
  files: an advice of the application's that answers `Throwable`, `Exception` or `RuntimeException`, has no `@Order` or
  the lowest one, beside an `XxxApiErrors` that is last in line too. A project with no generated advice hears nothing.
- Checked: `AdviceOrderTest` (the tie with `Throwable`, `{Exception.class}`, `value = RuntimeException.class` and an
  explicit lowest order is said; an `XxxApiErrors` next to last, an ordered advice, one without a catch-all, or no
  generated advice says nothing). Rule guide (en, ko), API errors.

### Bug Fixes (migration check - preserved blocks)

#### The check and the build say the imports a preserved block may lose
- Why: in a repository 2.x generated, methods written in a preserved block (`// !---------- ... ------------!`) used
  `java.util.*` and `...dto.*`. The 3.0 code imports each class by name; the block was put back as it was, and the file
  failed to compile with 5 errors - which `elcoreMigrationCheck` did not list: it looked only at merged files, and a
  file whose edits are all in blocks is rewritten, not merged. Its report also listed only "merged" files.
- What: for a file whose code of the developer's is in preserved blocks, the imports it had that the new text does not,
  and that a block's line may need (one it names, or a wildcard it may rely on), are in the check's report ("Imports the
  build would take out of your files") and are a build warning that says to write them in the preserved block under
  `package`. A merged file with blocks is read with its blocks too. The count is "files that would lose an import your
  code may need".
- Checked: `MigrationCheckTest` - the 2.2 project's `PurchaseOrderRepository` gets a block that uses `List` and
  `Optional`: the report names it with `import java.util.*`, the build prints the warning, keeps the block and drops
  the wildcard. `MigrationPreviewTest`. Migration guide (en, ko).

- Files: `RulesGenRunner.java` (`checkAdviceOrder`, `adviceOrderClashes`, `blockImports`), `MigrationPreview.java`
  (`dropped`, the report's wording), `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `AdviceOrderTest.java`,
  `MigrationCheckTest.java`, `MigrationPreviewTest.java`, the rule and migration guides (en, ko)

### Bug Fixes (v3 search - `@searchResult`)

#### The 3.0 search answers with the `@searchResult(Entity)` DTO, as 2.x does
- Why: a DTO declared `@searchResult(Entity)` (with `--` to leave a large column out of a list) was written by the rule
  engine, but nothing used it: `findBySearch` still returned `Page<EntityDTO>` through `toDTO`, the mapper had no
  `toEntityResultDTO`, and the list carried every field of the entity. Reported by a user moving a 2.x project to 3.0.
  The spec field every consumer reads (`EntitySpec.searchResultDtoName`) was never set.
- What: `SpecFactory` sets it from the `SEARCH_RESULT` relationship, as 2.x `findSearchResultDTOName` finds it, when
  the model has that DTO (the entity's own `EntityDTO` is not taken: the search already answers with it). The service
  (layered, ddd, hexagonal, MyBatis, Kotlin), the controller, the hexagonal use case and the TypeScript client then use the
  result DTO. A result DTO without `@template` gets its entity-to-DTO mapping
  (`toEntityResultDTO`, one way, as 2.x writes it for the relationship); one with `@template` already had it. The v3
  controller and the hexagonal use case import the result DTO (`${searchResultImport}`, an empty slot without one).
  An entity without `@searchResult` generates what it did. The 2.x-compatible output now matches the 2.x generator
  for such a model too (service and mapper); the golden template has none, so its checks are unchanged.
- Checked: `SearchResultDtoTest` (8 tests, all failing without the change): v3 layered service `Page<XResultDTO>` +
  `Mapper::toXResultDTO`, the mapper method without the removed column, controller import and return type, an entity
  without one unchanged (no added controller line); a `@template` + `@searchResult` DTO mapped once; the compatible
  service and mapping body equal to the 2.x generator's; Kotlin, hexagonal, ddd, MyBatis and the TypeScript client.
  Compiled with a `CategoryResultDTO @searchResult(Category)` added for the run (then removed) to
  elasticore-template-ddd (hexagonal, and with `architecture` switched to `ddd` and `layered`), elasticore-template-kt
  (`compileTestKotlin`, `elcore3tsVerify` type-checks the client), elasticore-template-mybatis and
  elasticore-template-ddd-mybatis (`compileTestJava`). `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED,
  `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `rules/verify/SpecFactory.java`, `rules/verify/RulesGenRunner.java`, `rules/base/ControllerBaseRules.java`,
  `rules/hex/HexUseCaseRules.java`, `elasticore-template/v3/control.tmpl`, `elasticore-template/kt/control.tmpl`,
  `SearchResultDtoTest.java`.

## 3.0.261113-rc13

### New Features (moving from 2.x - a check before the move)

#### `./gradlew elcoreMigrationCheck`: the first 3.0 build of a project 2.x generated, with nothing written, and a report
- Why: moving a 2.x project to the 3.0 code showed its problems only at compile time, all at once - a repository the
  3.0 code no longer writes and the `helper.getXxx()` calls of hand code (hundreds of errors), an import a merge took out
  of an edited file, edited files left as the 2.x code. What the build would do is known to the generator before it
  writes anything.
- What: a Gradle task, run only when asked, whatever `output` the build keeps. It runs the 3.0 build as `elcore3` with
  `output = 'v3'` would - the 2.x code read as the bases of the edited files, the merge, the removal of what is no longer
  produced - with the writes, the deletes, the cache, the DDL and the guides held back, and writes
  `build/elasticore/migration-check.md`:
  - generated files the build would remove or no longer write that code of the developer's uses - by name, or for a
    repository by the domain helper's getter (`helper.getCode()`) - with `@repository` / `@service` / `@expose` as the
    fix;
  - files that would conflict, be kept as the developer's, or be left as the 2.x code (edited, base not confirmed);
  - the imports a merge would take out of a file the developer wrote more into and their code may need (one they
    added, one naming a class their lines use, or a wildcard their lines may rely on - not one only the generated code
    used);
  - files an older generator stamped that the 3.0 code does not write, and the files it would remove.
  The console prints each file's status as the build would, and a summary. A Kotlin or TypeScript run and `-Pdomain` are
  refused with the reason: the check reads a whole project 2.x generated, which is Java.
- Checked: `MigrationCheckTest` - a project the 2.x generator wrote (stamped as 2.2 stamps), with a class of the
  developer's calling `helper.getCode()`, one calling a `getCode()` of an entity, and an edited DTO and CoreService:
  the check changes no file (byte for byte) and writes no cache; its report names `CodeRepository` (no longer written,
  left on disk) as used by `CodeReader` and nothing else, and both edited files as merged; then the build itself prints
  the same status for every file, merges the hand method in and rewrites the helper without `CodeRepository`.
  `MigrationPreviewTest` (statuses read off the run's lines, which dropped imports are listed and which are not, the
  order of the report). `ElastiCorePluginTest.theTasksAppear`.
- Files: `RulesGenRunner.java` (the check: `PROP_MIGRATION_CHECK`, writes held back, `finishMigrationCheck`,
  `findLeftBehind`, `usersOf`), `MigrationPreview.java`, `ElastiCorePlugin.java`, the tests above,
  `elasticore-migration-guide.md` (en, ko: the move step by step)

### New Features (v3 entity - a repository alone)

#### `meta: entity @repository`: the repository and the helper's getter, no service and no API
- Why: moving a 2.x project to 3.0, 36 entities that code of its own reads and writes through
  `RepositoryHelper.getXxx()` had no repository: the 3.0 code writes one only for an entity with `@expose` or `@service`,
  where 2.x wrote one for every entity with a key - about 870 compile errors. `@expose` opens an API nobody wants,
  `@service` writes services nobody calls (up to 72 files).
- What: `@repository` on the entity line writes `<Entity>Repository` and puts it in the domain's `RepositoryHelper`, and
  nothing else - no CoreService, Service, Controller or API. v3 and Kotlin over JPA. Under `mode: mybatis` it is a DSL
  warning and writes nothing yet. `@expose` or `@service` beside it decide as before; an entity without a key gets
  nothing; the 2.x generator and the 2.x-compatible output are unchanged (every keyed entity has a repository there).
- Checked: `RepositoryOnlyTest` (Java and Kotlin: the repository and the helper field, no service or controller files; an
  entity with none of them still gets nothing; MyBatis warns). `elasticore-template-ddd`'s catalog has
  `Supplier @repository`: `CatalogApiTest.aRepositoryWithoutAnApi` saves and reads it through
  `CatalogRepositoryHelper.getSupplier()`, `/api/v1/catalog/supplier/SUP-1` answers 404, and no service or controller
  class exists; its DDL has `cat_supplier`. 105 tests of the module pass.

### Bug Fixes (2.x-compatible output on Spring Boot 4 - the delete by search)

#### `output = 'v2'` on Spring Boot 4 compiles: the delete by search removes the rows it reads
- Why: the 2.x `XxxCoreService.delete(SrchDTO)` calls `JpaSpecificationExecutor.delete(Specification)`, which Spring Data
  JPA 4 does not have (it takes a `PredicateSpecification`), so the 2.x-compatible output did not compile on Spring Boot
  4.0.8 or 4.1.2 (15 files in that project). README and the env guide said only enums differ on Boot 4.
- What: with Spring Boot 4 the compatible service reads the rows (`findAll(specification)`) and removes them
  (`deleteAll`), answering their number - the 3.0 service deletes row by row too. JPA's remove runs for each, so
  `@PreRemove` and cascades run where the bulk delete skipped them. Spring Boot 3 output is unchanged, byte for byte;
  the 2.x generator keeps its line. README (en, ko) and env guide 3-1 (en, ko) say so.
- Checked: `ServiceCompatFileTest.onBoot4TheDeleteBySearchRemovesTheRowsItReads` (Boot 3: the old line; Boot 4: the
  three lines, no template directive left). `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED,
  `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.

### Bug Fixes (generated-file merge - a 2.x wildcard import)

#### The merge keeps a 2.x wildcard import the developer's code relies on
- Why: 2.x writes `import ...dto.*;` and `import ...service.*;`; the 3.0 code names `FolderDTO`, `FolderService` one by
  one. In a file the developer wrote more into (`FolderController`, `MERGED(your edits kept)`), the import block takes the
  generated side where the developer left a base line alone, so the wildcards went - and the developer's code using
  `NoteService` and `ModelInfoService` through them failed with `cannot find symbol` (16 errors in 3 files, reported with
  the base, the developer's file, the merge and the log).
- What: `ThreeWayMerge.mergeSource` keeps a base wildcard the developer kept when they edited the code and the generated
  imports still name a class of that package (so the package is there). A wildcard of a package the generated code no
  longer imports from goes, as does every wildcard of a file the developer did not write into; a single-type import the
  DSL dropped still goes - its class may be gone.
- Checked: `ThreeWayMergeTest.aBaseWildcardTheEditedCodeKeptStaysWhileItsPackageIsImported` and
  `aWildcardIsKeptOnlyForAPackageAClassIsStillImportedFrom`; the reported `FolderController` merged again keeps both
  wildcards. Migration guide (en, ko).

### Changes (guides - their language)

#### Korean guides already in the blueprint stay Korean without `guides = 'ko'`
- Why: after 3.0.261031-rc7 the guides are English by default, and a project that had the Korean ones got the English ones
  on its next build, with nothing but `Copying guide files (en)` in the log.
- What: when `elasticore { guides }` is not set and the guides in the blueprint are Korean (5% or more of their characters
  Hangul - the Korean guides are a fifth and more, the English ones hold 26 Hangul characters of 68,000), the Korean ones
  are copied and the build says so, with `guides = 'en'` to change it. A new project, or one with English guides, gets
  English as before; a set `guides` decides alone.
- Checked: `GuideDeployerTest.koreanGuidesAlreadyThereStayKorean` (Korean kept and said; `en` replaces them; English stay
  English) and `anEmptyBlueprintIsNotKorean`. Env guide 12 (en, ko).

- Files: `RulesGenRunner.java`, `RepositoryHelperBaseRules.java`, `service.tmpl`, `JpaServiceCompatFileRules.java`,
  `ServiceSrcPublisher.java`, `ThreeWayMerge.java`, `GuideDeployer.java`, the tests above, `elasticore-template-ddd`
  (`catalog/model/entity.yml`, `CatalogApiTest.java`, the DDL and `schema.yml` / `semantic_model.yml`), README (en, ko),
  the DSL, annotation, env and migration guides (en, ko)

## 3.0.261112-rc12

### Bug Fixes (2.x generator - `@max` on a `bigdecimal`)

#### The 2.x generator writes `@DecimalMax` with the `@max` value, not the `@min` one
- Why: `SrcFilePublisher.setFieldValidation` wrote the `@min` value into the `@DecimalMax` of a `bigdecimal` or
  `biginteger` DTO field. `price: bigdecimal(10.2) @min(0.01) @max(999.99)` became `@DecimalMin("0.01")
  @DecimalMax("0.01")`, so every request with a price above the minimum was refused (400). The 3.0 code and the
  2.x-compatible output (`output = 'v2'`) already wrote `@DecimalMax("999.99")`; only the 2.x generator (`elcore`, the
  annotation processor) had it.
- What: the `@DecimalMax` carries the `@max` value. A field with `@max` on a `bigdecimal` or `biginteger` now gets the
  bound it declares; nothing else the 2.x generator writes changes.
- Checked: `TwoXBoundTest` runs the 2.x generator (`ModelExtractor`) on a DTO with `price: bigdecimal(10.2) @min(0.01)
  @max(999.99)` and `count: int @min(1) @max(9)`: `@DecimalMin("0.01")`, `@DecimalMax("999.99")`, `@Min(1)`, `@Max(9)`.
  Before the change it wrote `@DecimalMax("0.01")`.
- Files: `SrcFilePublisher.java`, `TwoXBoundTest.java`

### New Features (v3 entity - an abstract root)

#### `@abstract(entity)`: an abstract `@Entity`, the root of a hierarchy read through one repository and one service
- Why: the v3 output writes every `@abstract` as `@MappedSuperclass`, so a hierarchy whose root has a key and is never
  stored as itself, read together through one repository, had no form: a repository for it could not start ("Not a
  managed type"), and an `@Inheritance` it kept did nothing. The 2.x generator writes such a root as an abstract
  `@Entity`, but both mappers wrote `new X()`, so with a service it did not compile. Reading every keyed `@abstract` as
  an entity would silently turn v3's keyed mapped superclasses into a hierarchy, so it is opt-in (#74).
- What: `meta: entity @abstract(entity)` gets `@Entity` and `@Table` on the abstract class, keeps the model's
  `@jpa:Inheritance`, and has a repository, a Q class and a service that reads, searches, updates and deletes. Java and
  Kotlin. Plain `@abstract` stays as it is.
- It has no create: no `save`, no POST (405), no `toEntity`, no `create` in the TypeScript client, no create use case
  under `hexagonal`, and the generated Kotlin service test makes its fixture with a mock and has no save test. A nested
  DTO of it is not written back. A relation to it is written with `em.getReference`, where another entity's would make
  a stub, and the mapper's overloads without an `EntityManager` throw when the DTO names one, rather than leave it unset.
- **The project DDL changes for every project with an abstract `@Entity` under `TABLE_PER_CLASS`, not only one that uses
  this.** `JpaSchemaReader` reads the entity's source, so a hand-written abstract root and the 2.x output of a keyed
  `@abstract` are read the same way: the abstract class has no table, as Hibernate makes none, and a relation to it has
  no foreign key, as its rows are in several tables. Their `schema-*.sql` lose that table and those foreign keys. Those
  foreign keys pointed at a table that stays empty. With a strategy that gives the root a table, the table and the
  foreign key are written.
- Refused: a root without a key; a root whose `@jpa:Inheritance` does not say `TABLE_PER_CLASS` or `JOINED`, as each
  subclass is written with a table of its own, which `SINGLE_TABLE`, the default, does not give it; an identity key under
  `TABLE_PER_CLASS`, which Hibernate refuses at startup, and `@sequence` alone means one (`@sequence(SEQUENCE)` is taken);
  and `mode: mybatis` ("does not write an abstract entity"). (`@deleted` in such a hierarchy was refused here too; the
  same release supports it, #117.) The
  2.x generator and `output = 'v2'` read it as plain
  `@abstract`, as they did; the compatible output is unchanged.
- Limits, said in the guide:
  - A subclass, whose key is inherited, gets no repository or service of its own, as under a keyed `@abstract`; its rows
    go through a repository of the project's. `@expose` or `@service` on one gets a DSL warning.
  - Under `TABLE_PER_CLASS` a key must be unique across all the subclass tables, and nothing in the database enforces it.
  - The Ossie export has no dataset for the root, as for plain `@abstract`, which fits `TABLE_PER_CLASS`; a relation to
    the root is not exported either. Under `JOINED` a subclass's dataset claims the root's columns, so the export is not
    right for that strategy yet.
  - On Oracle 23 a boolean column, of a subclass as of any entity, needs `ddl { oracleVersion = 23 }`
    (`oracle-version.md`): without it the project DDL writes `NUMBER(1)`, Hibernate expects `BOOLEAN` from that
    version, and `ddl-auto=validate` fails. It is not caused by this change.
- Checked: `AbstractEntityTest` - the entity, the repository and Q class, the service, the controller, the mapper, under
  `layered`, `hexagonal` and `ddd`; the hexagonal use case, the Kotlin service test, the TypeScript client, the schema
  under `TABLE_PER_CLASS` and `JOINED`, plain `@abstract` as it was, each refusal, the warning on a subclass and the
  compatible output. `JpaSchemaReaderTest` holds a hand-written abstract root under both strategies. A `TABLE_PER_CLASS`
  sample, `Code` / `StatusCode` / `RegionCode`, is generated, compiled and run in three modules:
  `elasticore-sample-petstore` (hexagonal; a polymorphic `findAll` and the API on H2, and in `SchemaValidateOnDatabase` on
  PostgreSQL, Oracle and SQL Server), `elasticore-template-ddd`'s `catalog` (the plain v3 bundle) and
  `elasticore-template-kt` (Kotlin, with its generated service test). The pet store and the Kotlin template also have a
  `CaseNote` with a relation to `Code`, written with `em.getReference` and refused without an `EntityManager`.
  `elcoreRulesCheck` and `elcoreTreeCheck` are unchanged: `158 MATCH / 0 DIFF / 2 ACCEPTED / 0 SKIP` and `239 generated by 2.x
  | 234 identical | 1 import order only | 4 accepted | 0 differ | 0 missing | 0 extra`.
- The pet store's build sets `ossieName = 'petstore'`, so its semantic model keeps its name beside a second domain.
- Files: `EntitySpec.java`, `SpecFactory.java`, `JpaEntityAnnotationRules.java`, `QBaseRules.java`, `KotlinQRules.java`,
  `ControllerBaseRules.java`, `MapperAssembleRules.java`, `KotlinMapperAssembleRules.java`, `MapperBaseRules.java`,
  `ServiceTestBaseRules.java`, `JpaServiceFileRules.java`, `MyBatisSql.java`, `RulesGenRunner.java`, `SoftDelete.java`,
  `JpaSchemaReader.java`, `kt/service.tmpl`, `kt/service_test.tmpl`, `AbstractEntityTest.java`,
  `SoftDeleteRulesTest.java`, `JpaSchemaReaderTest.java`, the three samples and
  their regenerated schemas, `elasticore-annotation-guide.md` `@abstract`, `elasticore-dsl-guide.md` and
  `elasticore-migration-guide.md` (en, ko)

### Bug Fixes (project DDL - `elcoreDdl` before the code is generated)

#### `elcoreDdl` run before generation stops, naming the entities it could not read, instead of writing tables without columns
- Why: a JPA domain's tables are read off its generated entity sources. In a checkout where those have not been
  generated yet (the pet store's are git-ignored), `elcoreDdl` wrote `schema-*.sql` from whatever was there and said
  `WROTE`: the pet store got `ps_order_line` and `ps_purchase_order` with no column - from the two behaviour classes
  that are committed, without their generated bases - and none of its other five tables. That is not valid SQL on any
  of the four databases, and nothing said it was incomplete (#107).
- What: `DdlRun` checks each JPA domain before it writes. An entity of the model with no source in its entity package
  (`<Name>.java` or `.kt`, under any source root of the run), and a table read with no column, stop the run, and
  nothing is written for that schema:
  `[elcore3] ddl: the tables are read off the generated entity sources, and no source for dgshop.Customer,
  dgshop.PurchaseOrder - run elcore3 first; nothing was written for the project`
  (or `no column read for purchase_order (io.acme.dgshop.entity.PurchaseOrder)` where only the class written once is there). After
  generation the same task writes what it did. `DdlRun.Part` carries the model's entity names for it.
  Whether `elcoreDdl` should depend on the generation is left open; the refusal closes the issue.
- Checked: `GeneratedMergeTest.theDdlAloneBeforeTheCodeIsRefused` - a hexagonal JPA project: `elcoreDdl` alone first
  exits 1 with the message above and writes no `schema-h2.sql`; after `elcore3`, with `PurchaseOrderBase.java` and the
  schema deleted, it exits 1 naming `purchase_order` and writes nothing; generated again, it writes the schema
  `elcore3` wrote, byte for byte. Without the change the first and second runs exit 0.
- Files: `DdlRun.java`, `RulesGenRunner.java`, `GeneratedMergeTest.java`, `elasticore-env-guide.md` (en, ko)

### Bug Fixes (v3 DTO - a fraction in `@min` / `@max` on a `double` or `float`)

#### A bound with a fraction on a `double` or a `float` is a `@DecimalMin` / `@DecimalMax`, so the DTO compiles
- Why: `@Min` and `@Max` take a `long`. `rating: double @min(0.5) @max(5.0)` was written as `@Min(0.5)` / `@Max(5.0)`,
  and the DTO did not compile (`incompatible types: possible lossy conversion from double to long`) - in every v3
  bundle (layered, hexagonal, ddd) and in Kotlin. The guides told the reader to write whole numbers only, and also
  said that `bigdecimal @min(0.01)` failed, which it never did: `bigdecimal` and `money` were already a
  `@DecimalMin` / `@DecimalMax`.
- What: `DtoBaseRules.bound` writes a bound with a fraction on a `double` or a `float` (boxed or not) as
  `@DecimalMin("0.5")` / `@DecimalMax("5.0")`, with its message when one is given. Hibernate Validator checks these
  on any `Number`. A whole bound (`@min(0)`, `@max(-10)`) stays `@Min` / `@Max`, so a project that compiled before
  generates what it did. The 2.x-compatible output (`output = 'v2'`) keeps what 2.x writes, `@Min(0.5)`.
- Checked: `FieldBoundTest` - on a `double`, a `float`, a `Double` and a `Float`, a fraction is a decimal bound, with
  and without a message; a whole bound on a `double` stays `@Min(0)` / `@Max(-10)`; the compatible output keeps
  `Min(0.5)`. In `elasticore-template-ddd` the catalog's `Book` has `rating: double @min(0.5) @max(5.0)`:
  `CatalogApiTest.fractionalBoundOnADouble` sends 5.5 and 0.4 (400) and 4.5 and 5.0 (200, stored). Before the change
  the module did not compile. The module's `db/schema-*.sql`, `schema.yml` and `semantic_model.yml` are regenerated
  with the column.
- Files: `DtoBaseRules.java`, `FieldBoundTest.java`, `elasticore-template-ddd` (`catalog/model/entity.yml`,
  `CatalogApiTest.java`, generated schemas), `elasticore-dsl-guide.md` 10.2 and 10.5, `elasticore-annotation-guide.md`
  8.1 and 8.7 (en, ko)

### New Features (v3 entity - `@deleted` in an `@abstract(entity)` hierarchy)

#### `@deleted` on the root of an `@abstract(entity)` hierarchy keeps the rows of every subclass
- Why: `@deleted` (#112) was written before `@abstract(entity)` (#104) and read every `@abstract` as a mapped
  superclass, so #104 refused the two together. A hierarchy read through one repository is where a delete that keeps
  the row is wanted most - a party who left, a tag taken out of use - and the refusal said "not supported yet" (#117).
- What: the field goes on the root, or on a plain `@abstract` frame the root extends; every subclass inherits it.
  Hibernate deletes a row from each table a class maps to, each with that class's own `@SQLDelete`, so:
  - `JOINED`: the column is in the root's table. The root's `@SQLDelete` writes the time there, by key and version.
    Each subclass's leaves its own row as it is, by a statement that changes nothing and finds the one row Hibernate
    counts on: `UPDATE ref_party SET left_at = left_at WHERE party_id = ?`. It works in whichever order the two run.
    A root of another domain is named by that domain's `table.prefix` and `tableSchema`.
  - `TABLE_PER_CLASS`: the root has no table and no `@SQLDelete`. Each subclass's table has the column, and its
    `@SQLDelete` writes the time there.
  - The root's repository, the one that reads the whole hierarchy, redeclares `findById`, `existsById`, `findAll`
    (plain, sorted, paged) and `count` with `<field> IS NULL`, as a plain entity's does. The mapper's `toSpec` adds the
    condition to the search. A collection of the root's type gets `@SQLRestriction`, and `removedWithIt` sees a
    relation to the root as kept.
  - The inherited key is read from the inherited fields (`SoftDelete.key`): `pkField()` sees only an entity's own
    fields, so a subclass got "needs a key of one field" before.
- Refused, with the reason:
  - `@deleted` on a subclass only (`... is not supported - a read through Code would find kept and removed rows
    alike; declare it on Code.`).
  - A collection of a JOINED subclass (`List<Person>`): Hibernate tests a collection's restriction on the element's
    own table, which has no column - tried on the pet store, it fails with `Column "P1_0.LEFT_AT" not found`. The
    message says to hold `List<Party>`.
  - A subclass file of the developer's (hexagonal and DDD split it) that has no `@SQLDelete` when the generator cannot
    merge it in - no base in `.elasticore/cache`, or no ElastiCORE header: a delete would remove the row, for a JOINED
    subclass its own table's row, leaving the root's kept and unreadable. Before, the first case was a note and the
    second said nothing. With a base, the line is merged in beside the developer's code, as any change is.
  - What holds for any entity holds here: a key the request gives needs a version, a composite key and `mapping: xml`
    on Spring Boot 3 stop the build. MyBatis refuses an `@abstract(entity)` already.
- Opt-in: nothing changes for a model without `@deleted` in such a hierarchy; before, the build stopped. Two changes
  reach models with `@deleted` outside one, and only there: a `@deleted` parent of another domain (`@extend`) is now
  found for the DTO's search condition and a collection's restriction, as the entity's own `@SQLDelete` already found
  it; and the Kotlin service test of a `@deleted` entity matches the key with `(any(Long::class.javaObjectType) ?: 1L)`,
  as the DDD port test does - the redeclared `findById(id: Long)` takes no null, and `any()` gave one ("any(...) must
  not be null").
- Limit: the JOINED subclass's statement needs the database to count the rows it matched, not only those it changed -
  so does Hibernate's check of every update. MySQL and MariaDB with `useAffectedRows=true` count changed rows only.
- Checked: `SoftDeleteRulesTest` and `SoftDeleteHierarchyTest` (the whole run, Java and Kotlin: JOINED with a version,
  TABLE_PER_CLASS from a frame above the root, a collection of the root's type, a field on a subclass only, a
  collection of a JOINED subclass, an owned subclass with and without a merge base), `AbstractEntityTest` (every output,
  a frame above the root, a JOINED root of another domain with its own prefix). The pet store's `refdata` has `Party`
  (JOINED, held by `CaseNote.parties`) and `Tag` (TABLE_PER_CLASS, `@deleted` from the frame `Retirable`, held by
  `CaseNote.tags`): `SoftDeleteHierarchyTest` deletes a person and a tag over HTTP on H2, reads them through the root's
  API, its repository and the note's collections (gone), and finds the rows with the time in the tables;
  `SchemaValidateOnDatabase` runs the same on Oracle, PostgreSQL and SQL Server in CI. `elasticore-template-kt`
  (Kotlin, Spring Boot 3.3, Hibernate 6) does the same through the repositories (`AbstractEntityTest`).
  `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ.
- Files: `SoftDelete`, `JpaEntityAnnotationRules`, `JpaEntityBaseRules` (the collection), `ServiceTestBaseRules`,
  `RulesGenRunner` (`failIfSoftDeleteMissing`), `EntitySpec` (`abstractEntityRoot`, `knownKeptBelowJoined`),
  `SpecFactory` (`teachAbstractEntityRoot`, `modelOf`; `teachSoftDeletes` takes an `@abstract(entity)` root;
  `deletedFieldOf` follows a parent of another domain), the annotation guide (en, ko), the pet store's guide page and
  its and the Kotlin template's `refdata`.

### Bug Fixes (project DDL - a sequence Hibernate names)

#### The project DDL names an unnamed sequence after the root's table, as Hibernate does
- Why: for `@GeneratedValue(strategy = SEQUENCE)` without a generator, the DDL wrote `{entity}_seq`, but Hibernate 6 and
  7 name it `{table}_seq` (`implicit_name_base` is the root's table name, read from `IdentifierGeneratorUtil` 6.6 and
  `GeneratorParameters` 7.2). With a table prefix or `@table` the two differ, and `ddl-auto=validate` failed:
  `missing sequence [ref_tag_seq]` for the pet store's `Tag`, whose DDL said `tag_seq`. The first
  `@sequence(SEQUENCE)` in a sample showed it.
- What: `JpaSchemaReader.sequences` uses the root's own table name. A schema stays out of the sequence's name, as
  before. A table named like its entity gets the same name as before. Any other got a sequence Hibernate did not use,
  and now gets the one it does. A database made from the old DDL has the old sequence and not the new one: create
  `{table}_seq` starting past the table's largest key (Hibernate could not have used the old one, so validate failed there).
- Checked: `JpaSchemaReaderTest.anUnnamedSequenceIsTheTablesName` (a prefix, an `@table` name, a schema); the pet store
  and the Kotlin template validate their schema with the sequence (`ref_tag_seq`, `ref_marker_seq`).
- Files: `JpaSchemaReader`.

### Bug Fixes (Ossie export - a JOINED subclass)

#### A JOINED subclass's dataset holds the columns of its own table
- Why: the dataset of a subclass listed every inherited field, as if its table had them. Under JOINED it has its own
  and the key; the others are in the tables above it. The pet store's `SemanticModelTest` checks every column against
  the table: `ref_person has no column name` (and `version`, `left_at`).
- What: `OssieSemanticModelExporter.inItsTable` keeps, under a hierarchy whose topmost entity says `JOINED`, the key
  and the fields of the entity and of the plain `@abstract` frames between it and the entity it extends. The root of
  an `@abstract(entity)` has no dataset, as before.
- Checked: the pet store's `SemanticModelTest` on `Person` and `Company`.
- Files: `OssieSemanticModelExporter`.

### Improvements (v3 JPA - what cannot compile or cannot be created is said when the code is generated)

#### An exposed composite key that holds a relation stops the build
- Why: `order: Ledger @id @ManyToOne` beside `itemSeq: int @id` in an entity with `@expose` wrote a controller that
  takes each key field as a path variable - `@PathVariable("order") Ledger order` - which does not compile (the type is
  not imported, and a path cannot carry an entity). It did so in every v3 bundle, in Kotlin and in the 2.x-compatible
  output; the guides showed the form as a reserved-word example.
- What: a v3 or Kotlin run over JPA stops (`ModelCheck`, exit 1) before writing such an entity:
  `[elcore3] Probe.LedgerLine: its composite key holds the relation order: Ledger, and an exposed entity's controller
  takes each key field in the path, which a relation cannot be - key it by the related key's value (ledgerId: long
  @id), or do not expose it`. An entity that is not exposed, and a composite key of values, are generated as before.
  The 2.x-compatible output is unchanged.

#### A table or column named with an SQL reserved word is said on JPA, as on MyBatis
- Why: an entity `Order` is the table `order`, and `group: string(10)` the column `group`. Hibernate quotes nothing by
  default, so a database that reserves the word refuses to create them. MyBatis domains were warned
  (`MyBatisSchemaRules.warnReserved`); JPA domains were not.
- What: `EntityChecks.reservedNames` reads the table as `JpaEntityAnnotationRules` writes it (`@table`, or the prefix
  and the snake-cased name) and each column of a value or an enum (`@column`, or the snake-cased name) against the
  same word list, and a v3 or Kotlin JPA run warns once per entity:
  `[Probe] Order: the table order, the column group are reserved by Oracle, PostgreSQL or SQL Server and cannot be
  created there - name it otherwise with @table / @column.` A plain `@abstract` (no table) is checked for its columns
  only; an `@embeddable`, a relation, a collection, Money, a formula and a transient field are not columns of the
  table and are not checked. The generated code does not change.

#### A type the DSL does not have says what to write instead
- Why: `code: uuid` is written into the class as `private uuid code;`, which does not compile; the run warned
  `unresolved type 'uuid'` without saying what the DSL has for it.
- What: the warning (from the loader and from the run) ends with what to write for the words the guides list:
  `- write string(36), or string @id @genid for a generated key instead` for `uuid`; `int` for `short` and `byte`;
  `string(1)` for `char`; `timestamp` for `OffsetDateTime` and `ZonedDateTime` (`BaseFieldType.insteadOf`).

- Checked: `EntityChecksTest` - the relation in a composite key is said with the sentence above, a composite key of
  values or a single key is not; the table and the columns reserved (`@table(app.user)`, `@column(comment)`, an enum
  column `level`) are said, in the plural and the singular; a relation, a collection, a formula, an unknown type,
  Money and an `@embeddable` are not; each type word maps to what the guides say. `ModelChecksRunTest` runs the
  generator: an exposed `LedgerLine` keyed by `order: Ledger @id` exits 1 with the sentence; unexposed (hexagonal) or
  keyed by `ledgerId: long @id`, the run exits 0; `Order` with `group` and `code: uuid` is generated, with the
  reserved names said once and the `uuid` warning ending in what to write.
- Files: `EntityChecks.java` (new), `RulesGenRunner.java`, `BaseFieldType.java`, `ModelExtractor.java`,
  `YamlFileMainModelLoader.java`, `EntityChecksTest.java`, `ModelChecksRunTest.java`, `elasticore-dsl-guide.md` 10.1,
  10.3 and 10.5, `elasticore-annotation-guide.md` 8.2, 8.3 and 8.7 (en, ko)

### Bug Fixes (MyBatis - database ports and the aggregate's delete)

#### A database port's conditional blocks nest, under MyBatis and in the port runtime
- Why: the first `/* if-end */` closed the outer block. The lines after an inner block were always kept, and an inline
  `/* if:name */` inside a block was kept whatever its value. The guide listed nesting as not supported.
- What: each `/* if-end */` closes the innermost open block; an inner block is read only when the outer one is kept,
  and an inline condition inside a block is read as one outside it is. The generated mapper XML nests `<if>`, and the
  runtime (`SqlPortGateway`, JPA and JDBC) reads the SQL the same way. A line kept by an inline condition is cut at the
  condition, not one character before the first `/*`.
- Checked: `MyBatisGenerationTest` (nested `<if>` in the XML), `MyBatisPortSqlTest`, `JdbcDbmsGatewayTest.blocksInsideBlocks`
  (H2: four inputs, one of which the old reading answered wrong), `DomainOnDatabase.theDbmsPortsSql` in
  `elasticore-template-ddd-mybatis` (H2 locally; Oracle, PostgreSQL and SQL Server in CI).

#### A port method may take a `Pageable`, whose page and `Sort` are the call's
- Why: the port interface did not import `Pageable`, so a method that took one did not compile; under MyBatis a
  parameter named `pageable` clashed with the adapter's own variable. The runtime proxy read page 0 of 50 when the
  `Pageable` came with other parameters, and neither read its `Sort`.
- What: the v3 and Kotlin port interfaces import `Pageable` and `Sort` when a method takes one (the 2.x-compatible
  output is unchanged). The MyBatis adapter's own names start with `port` (`portPage`, `portTotal`, `portRows`,
  `portRow`). The page is the `Pageable` parameter's, in the runtime too. The order is the input's sort code, else the
  `Sort` of a `Pageable` or `Sort` parameter - its properties are column names or labels, checked term by term as a sort
  code is (`PortRows.orderBy`, `SqlPortGateway.sortOf`). The runtime appends it, as any sort, only to SQL with
  conditional lines (port guide 3.6); a `Sort` property that is not a column or label now fails there where it was
  ignored. `Pageable.unpaged()` reads every row (`PortRows.paged`).
- Checked: `PortModelImportsTest` (Java and Kotlin import it, 2.x does not), `MyBatisGenerationTest`,
  `DbmsPortInputTest.aPageableParameterIsThePage`, `JdbcDbmsGatewayTest.aPageablesSortOrdersTheRows` (an injected
  property is refused), `DomainOnDatabase.theDbmsPortsSql` (`customersByPage` with `Sort.by(DESC, "firstName")`).

#### Under MyBatis the call's sort goes before the SQL's own `ORDER BY`, and an `ORDER BY` inside `/* if: */` pages on SQL Server
- Why: the mapper appended the call's sort only to SQL without an `ORDER BY`; the runtime puts it first and keeps the
  SQL's terms as tie breakers. An `ORDER BY` inside an `/* if: */` line or block was taken as always there, so when its
  condition was false a page reached SQL Server with no order, which it does not page. The guide said not to do it.
- What: the sort goes first in the SQL's own `ORDER BY`, and becomes the `ORDER BY` where the SQL has none or its
  conditions leave it out; there, SQL Server gets `ORDER BY (SELECT NULL)` when the call names no sort either.
  Alternative orders - several `ORDER BY` lines, each kept by its own condition - each take the sort, and the tail is
  for when none is kept; the count cuts them all. Every read of a port that carries a sort takes it.
- Checked: `MyBatisGenerationTest` (the XML of an own and a conditional `ORDER BY`), `DomainOnDatabase.theDbmsPortsSql`
  (`customersMaybeOrdered` with and without its condition).

#### A port's `Map` row has every column under MyBatis, a NULL one as null
- Why: MyBatis leaves a NULL column out of a map it reads, and answers a row whose columns are all NULL as null; the port
  runtime has every column. A `List<Map<String,Object>>` method also did not compile (`Map.class` against
  `Map<String,Object>`).
- What: the port's statements answer `io.elasticore.springboot3.bean.PortRow`, and the runtime library's MyBatis
  interceptor `PortRowColumns` (registered by `ElastiCoreMyBatisAutoConfiguration`, applied by the MyBatis Spring Boot
  starter) reads the column labels as MyBatis opens the result set and puts the missing columns in, in the order the
  SQL selects them. No other statement is touched. A method that answers a generic type reads it by its class
  (`PortRows.listOf` / `firstOf`), a page of one too.
- Checked: `PortRowColumnsTest` (H2, plain MyBatis, behind another plugin: a NULL column, a row of NULLs, a statement
  left as MyBatis reads it), `DomainOnDatabase.theDbmsPortsSql` (`customerRows`).

#### The aggregate's delete locks the row with a read, not an UPDATE
- Why: `lockVersion` was `UPDATE t SET v = v WHERE id = ? AND v = ?`. MySQL and MariaDB with `useAffectedRows=true`
  count changed rows, so they answer 0 and the adapter refused every delete of a current copy as stale (409).
- What: `SELECT 1 FROM t WHERE id = ? AND v = ? FOR UPDATE` (`WITH (UPDLOCK, ROWLOCK)` on SQL Server); the mapper answers
  `Integer`, null when the row has another version. On H2, Oracle, PostgreSQL and SQL Server the row stays locked to
  the end of the transaction, as before (DB2 is not checked). SQL Server is told apart by MyBatis's database id, as the
  generated paging already is: with `elasticore.mybatis.database-id=false` the lock is `FOR UPDATE`, which SQL Server
  refuses.
- Checked: `MyBatisGenerationTest`, `DomainOnDatabase.aStaleDeleteIsRefused` (H2 locally; Oracle, PostgreSQL and SQL
  Server in CI).

- Files: `MyBatisPortRules.java`, `MyBatisMapperRules.java`, `DddMyBatisPorts.java`, `PortBaseRules.java`,
  `SqlPortGateway.java`, `DbmsServiceProxyFactory.java`, `PortRows.java`, `PortRow.java`, `PortRowColumns.java`,
  `ElastiCoreMyBatisAutoConfiguration.java`, the tests above, `elasticore-template-ddd-mybatis` (`report_port.yml`,
  `dto.yml`, `DomainOnDatabase.java`), `elasticore-dsl-port-guide.md` (3.4, 3.6, 3.11, 3.13) and
  `elasticore-env-guide.md` (2.3), en and ko

### New Features (DDL - the Oracle version the tables are for)

#### `ddl { oracleVersion = 23 }`: a boolean is a `BOOLEAN` column on Oracle 23, as Hibernate maps it there
- Why: the project DDL wrote a boolean as `NUMBER(1)` on Oracle, and Hibernate's `OracleDialect` maps it to `BOOLEAN`
  from version 23, so `ddl-auto=validate` failed there on any entity with a boolean field: `wrong column type
  encountered in column [terminal] in table [ref_status_code]; found [number (Types#NUMERIC)], but expecting [boolean
  (Types#BOOLEAN)]` (#114). `BOOLEAN` is no column type below 23, so the DDL cannot simply write it.
- What: a setting in `elasticore { ddl { } }`, beside `databases`, one value for the project (passed as
  `elasticore.ddl.oracleVersion`). From 23, Oracle's DDL writes a boolean as `BOOLEAN` and says
  `for Oracle 23 or later` in its header; below 23, and when it is not set - the default - it writes `NUMBER(1)`, as
  before, so nothing changes for a project that does not say it. A version below 12, or anything but a number, stops
  the run. Only the boolean changes, and only on Oracle; every other type is the same for 23 as before.
- Migrations: the state (`schema-state.json`) records the version, only when it is set, so a state without it reads
  as before. Setting it over Oracle tables already there is a migration of Oracle's alone; the other databases get
  none. Oracle changes a column's type only while the column is empty (`ORA-01439: column to be modified must be empty
  to change datatype`, tried on Oracle 23 Free with rows), so an `ALTER ... MODIFY` from `NUMBER(1)` to `BOOLEAN` would
  fail on existing data. Instead each boolean column is added again with the new type, its values copied, the old
  column dropped and the new one renamed to it; its `NOT NULL`, the indexes it is in and its comment are made again.
  The values copy as they are: 1 is `TRUE`, 0 `FALSE`, an empty one stays empty. It runs on the rows already there, so
  it waits for review (commented out, `REVIEW .../oracle/V...` in the log). Unsetting it again is the same change
  back to `NUMBER(1)`.
- MyBatis: `template-mybatis` and `template-ddd-mybatis` bind a boolean as `jdbcType=BOOLEAN`. Their Oracle tests
  (`ShopApiOnOracleTest` 10, `DomainOnOracleTest` 14) pass on Oracle 23 Free with ojdbc11 23.7 against `NUMBER(1)`
  columns, as now, and against `BOOLEAN` ones, with the setting on in a local run; nothing to fix. The templates keep
  the default.
- Checked: `OracleVersionTest` (both answers, every other type unchanged, the setting's values, the state, the
  migration both ways, a copy name already taken, an index the model drops); `DdlMigrationTest` (a project turning it
  on: the Oracle migration for review, none for H2, the state, nothing on the next build); `ElastiCorePluginTest`
  (passed when said). The pet store sets `oracleVersion = 23` and its `StatusCode` has `terminal: boolean`:
  `SchemaValidateOnDatabase` passes on Oracle 23 Free, where without the setting it fails with the error above, and
  `AbstractEntityCheck` reads the boolean through Hibernate and binds it in a query on every database.
  `MigrationRun.oracleBooleans` (run by the job on Oracle) makes the tables below 23 with rows, runs the change as
  written and finds `BOOLEAN` columns with their values, nullability and index.
- Files: `Database.java`, `DdlConfig.java`, `Schema.java`, `SchemaWriter.java`, `SchemaDiff.java`, `DdlRun.java`,
  `DdlSpec.java`, `ElastiCorePlugin.java`, `OracleVersionTest.java`, `SchemaDiffTest.java`, `DdlMigrationTest.java`,
  `ElastiCorePluginTest.java`, the pet store's `build.gradle`, `code.yml`, schemas, `semantic_model.yml` and
  `AbstractEntityCheck.java`, `template-mybatis`'s `MigrationRun.java` and `MigrationTest.java`,
  `elasticore-env-guide.md` 9-1 and the `@abstract(entity)` limits of `elasticore-annotation-guide.md` (en, ko),
  `abstract-entity.md`.

### Bug Fixes (project DDL - `mapping: xml`)

#### Under `mapping: xml`, a `money` or `quantity` column is a decimal or an integer again, not text
- Why: `META-INF/orm.xml` names a nested converter by its binary name, as Hibernate loads it:
  `io.elasticore.springboot3.domain.Money$JpaConverter`. The schema reader knew the runtime's two converters as
  `Money.JpaConverter` and `Quantity.JpaConverter`, and the classes it read from source by their dotted names, so the
  `$` form matched neither. Each such column was written as text, with the warning `the converter ... was not read -
  the column is text`, and `ddl-auto=validate` refused the schema (#116). That is the limit the 3.0.261106-rc11 entry
  of #113 states; with the mapping on the classes, the default, nothing was wrong.
- What: `JpaSchemaReader.converterColumn` reads a `$` in the converter's name as `.` before it looks the converter up.
  The runtime's `Money` and `Quantity` converters, and a project's own converter nested in a class (`Outer$Inner`),
  are then found as they are with the annotations. `StringArrayConverter` and `IntegerArrayConverter` are classes of
  their own, named without a `$`, and were not affected. An enum's converter was not affected either, since
  `mapping: xml` moves it out to a class of its own.
- Checked: `JpaSchemaReaderTest.anOrmXmlNamesANestedConverterByItsBinaryName` reads an orm.xml with
  `Money$JpaConverter`, `Quantity$JpaConverter`, a converter nested in an enum and an enum's converter moved out. It
  expects `DECIMAL(14,2)`, `INTEGER`, `INTEGER` and a text column of length 10, with no warning. On `main` the first
  three fail as text, with three warnings.
  - The pet store, generated once with `mapping: xml` in its `petstore` env.yml:
    - `SchemaValidateTest` passes.
    - Its four `schema-*.sql` hold the same tables, columns and constraints as the committed ones, which the
      annotations give; only their order differs.
    - Without the change, the same run warns eight times, and `SchemaValidateTest` fails with `wrong column type
      encountered in column [unit_cost] in table [ps_item]; found [character varying (Types#VARCHAR)], but expecting
      [numeric(10,2) (Types#NUMERIC)]`.
  - The sample was put back. Regenerated as it is, its schemas are unchanged.
- Files: `JpaSchemaReader.java`, `JpaSchemaReaderTest.java`

### Changes (Ossie export - core spec `0.3.0.dev0`)

#### The Ossie export writes `version: 0.3.0.dev0`, the version Ossie's schema now requires
- Why: apache/ossie#691 (merged on 9 November, `c1a3318`) moved the spec's in-development version from `0.2.0.dev0`
  to `0.3.0.dev0`; there will be no 0.2.0 release. `ossie-schema.json` pins the version with `const`, so every
  `semantic_model.yml` the export wrote stopped validating against Ossie's `main`:
  `[Schema] version: '0.3.0.dev0' was expected` (#96).
- What: `OssieSemanticModelExporter.SPEC_VERSION` is `0.3.0.dev0`, which sets the header comment and the `version:`
  line. Nothing else in the document changes. The rule guide's Ossie section (en, ko) says the version, and that
  3.0.261106-rc11 and earlier write `0.2.0.dev0`.
- Upgrade: a project that names the `ossie-export` rule set gets a `semantic_model.yml` with a new `version:` line and
  header the next time it generates. A consumer that checks the document against Ossie's schema needs this one: a
  `0.2.0.dev0` document fails Ossie's `main` from `c1a3318` on, and a `0.3.0.dev0` document fails the schema before it.
  ElastiCORE 3.0.261106-rc11 and earlier write `0.2.0.dev0`; the releases after it write `0.3.0.dev0`.
- Checked: the three committed `semantic_model.yml` (pet store, `elasticore-template-ddd`,
  `elasticore-template-ddd-mybatis`), regenerated, differ from `main` in those two lines only.
  - Against apache/ossie `main` (`d700cde`), with sqlglot, `validation/validate.py` passes all three. Before the
    change, each failed with the error above, and that error alone.
  - Against the schema before #691 (`21d14d7`), the new pet store document fails with
    `version: '0.2.0.dev0' was expected`.
  - `OssieExportTest` and the three `SemanticModelTest` pass. The pet store's guide page (`static/dsl-guide.html`),
    rebuilt with `guide/build_guide.py`, changes in that line alone.
- Files: `OssieSemanticModelExporter.java`, `OssieExportTest.java`, the three `SemanticModelTest.java` and
  `semantic_model.yml`, the rule guide (en, ko), the pet store's `build_guide.py` and `dsl-guide.html`

### Changes (Ossie export - a metric over one entity is its dataset's)

#### A metric over one entity is written in that dataset's `metrics`, where Layer 2 exposes it as a measure
- Why: the export wrote every metric at the model root, on fields named with their dataset:
  `SUM(CASE WHEN PurchaseOrder.status <> 'CAN' THEN PurchaseOrder.total END)`. In the core spec that is a model-scoped
  metric, for aggregations that span datasets ("Metric Scoping"). The relational interface (Layer 2) does not expose a
  model-scoped metric as a measure, so none of the exported metrics was one there. A DSL metric is an aggregate over one
  entity or arithmetic over metrics declared before it, so it spans datasets only when it combines the metrics of two or
  more entities (#97).
- What: a metric whose aggregates all read one entity - also arithmetic over that entity's metrics
  (`averageOrder: revenue / orderCount`) - is written in the dataset's `metrics`, after its fields, on its fields by their
  own names: `SUM(CASE WHEN status <> 'CAN' THEN total END)`. Arithmetic over the metrics of two or more entities stays at
  the model root as before, and a root name two domains use keeps the domain before it. A dataset's fields and metrics
  share one set of names: a metric named like a field of its dataset, in any case, or like a metric another domain put
  there, is left out with `[ossie] [domain] metric total: PurchaseOrder has a field total, and a dataset's fields and
  metrics share one set of names - rename the metric; it is left out.` It is not renamed, and no domain goes before a
  dataset's metric.
- Upgrade: a project that names the `ossie-export` rule set and declares `metrics:` gets a new `semantic_model.yml` the next
  time it generates. A consumer that names a metric of one entity changes: `revenue` is now `PurchaseOrder.revenue`.
  ElastiCORE 3.0.261106-rc11 and earlier write every metric at the model root. A project with no `metrics:` generates the
  same document.
- Checked: `OssieMetricScopeTest` generates projects: a filtered and an unfiltered metric over one entity, a one-entity
  composite, a two-entity composite at the root, names clashing with a field (`Total`, `status`, `name`), and two domains
  with the same metric on one dataset and the same root metric.
  - The pet store's `semantic_model.yml`, regenerated, has its 13 metrics in the `Customer` and `PurchaseOrder` datasets and
    no root `metrics`; its `MetricsTest` runs each one on the database as `SELECT <sql> FROM <table>`, and each equals
    what the test computes from the entities, and `SchemaValidateOnDatabase` runs each one the same way on Oracle,
    PostgreSQL and SQL Server. The two templates declare no metrics and generate the same document.
  - Against apache/ossie `ee89dbd`, with sqlglot, `validation/validate.py` passes the three documents, and a copy of
    the pet store's with a two-entity composite at the root as well. A dataset's metric written with `PurchaseOrder.` fails
    with `[Scope] ... uses qualified reference(s) 'PurchaseOrder'`, and one named `total` with `[Unique] Name 'total' in
    dataset 'PurchaseOrder' is used by both a field and a dataset-scoped metric`.
- Files: `OssieMetrics.java`, `OssieSemanticModelExporter.java`, `OssieMetricsTest.java`, `OssieExportTest.java`,
  `OssieMetricScopeTest.java` (new), the pet store's `semantic_model.yml`, `MetricsTest.java`,
  `SchemaValidateOnDatabase.java`, `build_guide.py` and
  `dsl-guide.html`, the rule guide and the DSL guide (en, ko)

### Changes (Ossie export - `ossie-export`)

#### The ELASTICORE extensions are `nonportable_extensions`, with `data` an object (closes #130)
- Why: apache/ossie#522 (`08d2ad3`, 11 November) changed `0.3.0.dev0` in place: `custom_extensions` is renamed
  `nonportable_extensions`, the old name is not accepted, and `data` is an object, not a JSON-encoded string. The export
  declared `0.3.0.dev0` and wrote the old form, so every document failed the schema that now defines that version - the
  pet store 40 errors, `elasticore-template-ddd` 24, `elasticore-template-ddd-mybatis` 22, one per extension.
- What: each ELASTICORE extension is written as `nonportable_extensions:` and its `data` as the JSON the export already
  built, without the string around it - a YAML flow mapping. Every string in it stays double-quoted, as the spec asks
  under `data`, so a code such as `NO`, `007` or `2026-01-01` is still a string. The model description says
  "nonportable extensions". Nothing else in the document changes.
- Upgrade: only a project that names the `ossie-export` rule set regenerates differently - two lines per extension and
  the description line of its `semantic_model.yml`. A consumer that read `data` as a JSON string now gets an object.
  3.0.261106-rc11 and earlier write `custom_extensions`. `0.3.0.dev0` may change again before `0.3.0-incubating`; the
  export follows Ossie's `main` until that release, and the release after it.
- Checked: the three committed `semantic_model.yml`, regenerated, validate against `ossie-schema.json` at `08d2ad3`
  (the current `main`) with no error; at `ee89dbd`, before #522, they fail on the new name, as expected. `OssieExportTest`
  reads `data` as an object and has no `custom_extensions`, and `dataIsAnObjectWhoseStringsStayStrings` reads back `NO`,
  `yes`, `007`, `2026-01-01`, `a: b` and a quoted text as the strings they were. The 53 Ossie tests and the three samples'
  tests pass.
- Files: `OssieSemanticModelExporter.java`, `OssieExportTest.java`, `OssieFormulaDialectTest.java`,
  `OssieMetricScopeTest.java`, `OssieUniqueKeysTest.java`, `elasticore-rule-guide.md` (en, ko), the pet store's
  `guide/build_guide.py` and `static/dsl-guide.html`, the three `semantic_model.yml`

### Bug Fixes (Ossie 내보내기 - 테이블의 schema)

#### 데이터셋의 `source:` 에 `@schema`·`tableSchema` 의 schema 를 붙인다
- 왜: `OssieSemanticModelExporter.table` 은 `@table` 과 `table.prefix` 만 읽고 DSL 의 schema 를 버렸다. 생성된 JPA 엔티티는
  `@Table(name = "customer", schema = "schema_a")` 인데 내보낸 데이터셋은 `source: customer` 라, 의미 모델을 읽는 쪽은
  기본 schema 의 다른 테이블(또는 없는 테이블)을 읽는다
- 무엇: schema 는 생성된 `@Table(schema=...)` 과 같은 규칙으로 정한다 - 엔티티의 `@schema` 가 있으면 그것, 없으면 도메인
  env.yml 의 `tableSchema`(`EntitySrcFilePublisher`, `SpecFactory` 와 같다). 있으면 `source: schema_a.customer` 처럼 점으로
  잇는다. 점 문자열은 지금 Ossie 릴리스 스펙에서 유효하고, 아직 머지되지 않은 apache/ossie#704 의 문자열 표기로도 같게
  읽힌다(그 PR 의 `kind: table` / `identifier:` 객체형은 쓰지 않는다)
  - schema 나 테이블 이름에 `.` 이 있으면 전처럼 테이블 이름만 쓴다: 점 문자열은 점을 가진 부분을 나타낼 수 없다
  - `@table` 을 쓴 엔티티는 전처럼 `table.prefix` 없이 그 이름에 schema 만 붙는다
  - schema 가 없는 모델의 출력은 바이트 단위로 같다
- 확인: `OssieExportTest` 6개 - `@schema`, `tableSchema`, `@schema` 우선, schema 없음(테이블 이름만, 손대지 않은 데이터셋은
  같은 바이트), 점이 든 이름(`@schema(a.b)`, `tableSchema: a.b`, `@table(legacy.customer)`), `@table` 과 schema·`table.prefix`
  조합. 고치기 전 코드에서는 앞의 셋과 마지막 하나가 실패한다. 펫스토어·`elasticore-template-ddd`·
  `elasticore-template-ddd-mybatis` 를 다시 생성해 커밋된 `semantic_model.yml` 셋과 바이트가 같다(셋 다 schema 를 쓰지 않는다)
- 파일: `OssieSemanticModelExporter.java`, `OssieExportTest.java`

### Improvements (DDD reference by id - a DTO's `@ref` onto the key)

#### A DTO field declaring a referenced root's key under a name of its own is said, not dropped in silence
- Why: under the `ddd-reference` rules another root is referenced by its id (`category: Category` becomes
  `categoryCatId`). A DTO field that also declares the key through the relation, `catId: long @ref(category.catId)`,
  carries the same value, so the DTO keeps the identity and drops the declared field. When the declared name was
  another than the identity's, nothing said so: the API had `categoryCatId`, a client written from the model sent
  `catId`, it was ignored, and the row was saved without the reference (#103).
- What: `DtoBaseRules.referenceOnce` warns once per field where the declared name differs from the identity's,
  naming the DTO, the field, the identity that replaces it and what to do (name the field as the identity, or drop
  it):
  `[Forum] SfCommentKeyedDTO.parentKey @ref(parent.commentId) is the key the reference by id already holds, so the
  DTO has it as parentCommentId and has no parentKey - a request that sends parentKey is not read. Name the field
  parentCommentId, or drop it.`
  The generated code does not change. A field named as the identity, and the relation's flattened key the DTO adds
  for itself (`departmentDepartmentId`), say nothing. The identity is found by `SearchDtoBaseRules.identityOfReference`,
  which `flattensAReferenceById` (the DTO, the search DTO and the mapper) now reads too, so the warning and the
  removal cannot disagree. Only the DDD bundles write the identity (`@idof`), so the 2.x output is untouched.
- Checked: `ReferenceOnceTest` runs the rule on a DTO: a key under a name of its own is left out and said once,
  with the sentence above; under the identity's name, or as the relation's flattened key (`departmentDepartmentId`
  beside `departmentId`), nothing is said; a `@ref` to another field, to the relation itself, two levels deep or to
  a relation with no identity stays as declared.
  `SelfReferenceByIdTest.aRefDeclaredUnderAnotherNameIsSaid` - over the hexagonal run, the warning is printed
  once for `SfCommentKeyedDTO.parentKey`, and not for `SfCommentSameNameDTO.parentCommentId`, for `SfTopicDTO`
  (`@objectref`, no identity) or for the DTOs the model does not declare. Without the change the run prints no
  such line.
- Files: `DtoBaseRules.java`, `SearchDtoBaseRules.java`, `ReferenceOnceTest.java`, `SelfReferenceByIdTest.java`, `elasticore-rule-guide.md`
  "Refer to another aggregate by ID" (en, ko)

## 3.0.261106-rc11

### Documentation (where a DSL feature works)

#### The guides mark the features that work only in some outputs, and say what happens elsewhere
- Why: whether a feature works under the 2.x code, the layered bundle, MyBatis, Kotlin or Spring Boot 3 was written in
  prose far from the feature - mostly in the MyBatis "What is still refused" list of the env guide - or not at all, and
  the heading marks mixed three words for one thing ("(v3)", "(3.0)", "Since: 3.0"). A reader of `@audited`, `@formula`
  or `testCode` found out from the build. Some sentences said the opposite of the code: `testCode` was said to
  generate a service test everywhere, the migration guide told a project moving to v3 to generate its tests again with
  it, `@formula` was said to be searched under MyBatis too, the value object carried whole in the DTO left out layered
  MyBatis, `publisher.rules` left out was said to be 2.x's bundle, and `@jsonSerialize` was shown as if it were written.
- What: a section whose feature is limited starts with one line, `> **Works in:** ... · **Not in:** ... (stops the
  build | warning | not generated)` (ko `> **동작:** ... · **안 됨:** ...`), in the same words everywhere (`v3`,
  `2.x-compatible output (output = 'v2')`, `2.x generator`, `layered`, `ddd`, `hexagonal`, `JPA`,
  `MyBatis (mode: mybatis)`, `Kotlin`, `TypeScript client`, `Spring Boot 3` / `4`, `mapping: xml`), under the heading
  and after its `Since:` line; each of the five guides that has one says once at the top what the line means. Marked:
  the annotation guide (`@expose(methods)`, entity `@readonly`, `@restExclude`, `@audited`, `@extend`, `@embeddable`,
  `@rollup`, `@sequence`, field `@readonly`, `@minsize`/`@maxsize`, `@pattern`, `@fetchjoin`, `@formula`, JPA relations,
  custom functions, auditing, search operators on large values, `between`, `isnull`, `@ref` in a search, `@sort`,
  comparison keywords, the DDD annotations, `@access`, `@password`, `@snapshot`, `@deleted`, `@event`), the DSL guide
  (`commands:`, `states:`, `@stamp`, collection and other types, repository, domain service, process manager,
  composite key, embedded entity, inheritance), the env guide (MyBatis, MongoDB, UML and DSL modes, outbox,
  `mapping: xml`, `context:`, `testCode`, `referenceColumn`/`adminRole`), the port guide (`httpPort: spring`, JEXL
  conditions) and the rule guide (`output = 'v2'`). Corrected in place: `testCode` (env guide 6.2, migration guide,
  annotation guide `@access`, rule guide `ddd-behavior` - a test is written for the 2.x code and Kotlin, not for v3
  Java or MyBatis); `@formula` is not searched under MyBatis; the value object in the DTO includes layered MyBatis;
  `publisher.rules` left out follows the build's architecture and language (`jpa-springboot-v3` for layered Java);
  `@jsonSerialize` is not generated by any output; `@deleted`'s Spring Boot 3.2 is what `@SQLRestriction` needs, and the
  build does not check it. No generated output changes.
- The pet store's guide page has a section "어디서 동작하나": the shop is generated as hexagonal Java JPA on Spring Boot
  4, and a table says where the DSL it uses that is limited (`@access` and the sign-in marks, `@deleted`, `states:`,
  `@compute`, `@snapshot`, `@root`, `@behavior`, `@editable`, `@event`, `referenceColumn: short`) works in layered, in
  `ddd`/`hexagonal`, in the 2.x-compatible output and elsewhere.
- Checked: every marker against the code it rests on (an inventory of 51 limits, each with the lines that make it -
  the bundles in `RuleCatalog`, `RulesGenRunner.warnUnenforced` and its emit paths, `MyBatisSql.unsupported`,
  `SoftDelete.problem`, `OrmMapping`, `SpecFactory`); the guide tests (`GuideTranslationTest`, the coverage tests,
  `RuleGuideExampleTest` - 35/35), the en and ko files of each guide the same length; the pet store page rebuilt with
  `guide/build_guide.py` after a build, adding only the new section.
- Files: `elasticore-annotation-guide.md`, `elasticore-dsl-guide.md`, `elasticore-env-guide.md`,
  `elasticore-dsl-port-guide.md`, `elasticore-rule-guide.md`, `elasticore-migration-guide.md` (en, ko);
  the pet store's `guide/build_guide.py` and `static/dsl-guide.html`

### New Features (metrics - lists, alternatives and distinct counts)

#### A metric's filter takes `in`, `not in`, `or`, parentheses and `not`, and `count(distinct Entity.field)` counts different values
- Why: a filter was `field op value` joined by `and`, so a real project's KPIs needed workarounds (#98). A list of
  statuses had to be written as the statuses left out, and that counts a status added to the enum later without a word.
  An alternative took two metrics and a sum, and a distinct count could not be written at all. All of these are in
  `OSSIE_SQL_2026`, the language the export writes.
- What: `where status in (A, B)` and `not in (A, B)`, `or` with `and` binding tighter as in SQL, parentheses to group,
  and `not` before a parenthesis. `count(distinct Entity.field)`, and with `where`
  `COUNT(DISTINCT CASE WHEN ... THEN Entity.field END)`. Every value of a list is read as a single value is: an enum
  constant by how it is stored, and one the enum does not have drops the line, naming it.
- Refused, as a line that cannot be read always was: the line is dropped with a warning that quotes it. Parentheses that
  do not balance, an empty `in ()` or an empty value in a list, an `or` or `and` with nothing on one side, empty
  parentheses, `distinct` with anything but `count` (`sum(distinct ...)`), and `count(distinct Entity)`, which names no
  field to count the values of.
- A line that uses none of these writes what it did, and a word that only looks like one stays what it was: a constant
  in `region = OR`, a field in `orderNo = 1`. The one thing such a line can see is a different sentence: `qty = 1 and`
  was "cannot read the value 1 and", and now says the `and` has nothing after it. It is dropped either way. The export
  is the only output of `metrics:`, so no code changes for anyone.
- Checked: `OssieMetricsTest` - each form, what binds tighter, the words inside values and names, each refusal, and each
  value of a list checked. `OssieSqlTest` - the new forms stay the portable `sql`. `MetricsTest` in the pet store runs
  the exported SQL of the new metrics on H2 and compares them with the same numbers worked out from the entities, and
  holds the SQL of the six metrics that were there. `SchemaValidateOnDatabase` runs every metric on PostgreSQL, Oracle
  and SQL Server in the CI job. The regenerated `semantic_model.yml` passes Ossie's `validation/validate.py` with
  `sqlglot`, at `172a3e5`.
- The pet store's `dsl-guide.html` is rebuilt by `guide/build_guide.py` for its metrics. The rebuild also takes the
  `discontinuedAt` of #112, which the page had not caught up with.
- Files: `OssieMetrics.java`, `OssieMetricsTest.java`, `OssieSqlTest.java`, the pet store's `metrics.yml`,
  `semantic_model.yml`, `MetricsTest.java`, `SchemaValidateOnDatabase.java`, `build_guide.py` and `dsl-guide.html`,
  `elasticore-dsl-guide.md` "Business metrics" (en, ko)

### Documentation and tests (follow-ups of the reviews of #102, #110 and #115)

#### A metric's not, and what the guides and the counts said
- Why: the reviews of #102, #110 and #115 left small items. The DSL guide said only `!=` and `not in` leave out a row
  whose field is null, though `not (...)` does too, so `unfinished` read as what `finishedOrders` leaves; its table
  showed SQL of models the guide does not declare (`Offer.status`, `Login.user_id`); nothing ran a null status. The
  annotation guide did not say which outputs carry a value object in a DTO. `CONTRIBUTING.md` and the tour still gave
  the counts of the compatibility checks before #102's `EmployeeContactDTO`. Two fragments missed a consequence and a
  `Checked:` line.
- What: the DSL guide (en, ko) says `!=`, `not in` and `not (...)` leave out a null, with the consequence for
  `unfinished`, and its table shows the pet store's SQL (`PurchaseOrder.customer_id`, `PurchaseOrder.status IN ('PLC',
  'SHP')`). `MetricsTest.aNullStatusIsInNeitherAConditionNorItsNot` takes one order's status away in a transaction it
  rolls back and runs the exported SQL of `finishedOrders`, `unfinished`, `otherOrders` and `orderCount`: the order is
  in none of them. The annotation guide (en, ko) names the outputs that carry a value object whole: `ddd`,
  `hexagonal` and layered MyBatis. The counts are `158 MATCH` and 239 / 234 files. `template-field-list.md` says that an
  entity whose only `@template` is a list without the key gets no service or controller; `self-reference-followups.md`
  has its `Checked:` line. No generated output changes.
- Not done: #102's review also asked to ask whether a DTO carries a value object only for a DTO with a list, so that a
  run over one domain is not stopped by another domain's rule sets. Run, that run stops earlier anyway - the references
  are shaped from every domain's rule sets (`shapeReferences`) before the lists are checked - so the change would not
  have helped.
- Checked: `elcoreRulesCheck -Pstrict` `158 MATCH / 0 DIFF / 2 ACCEPTED / 0 SKIP`; `elcoreTreeCheck` `239 generated
  by 2.x | 234 identical | 1 import order only | 4 accepted | 0 differ | 0 missing | 0 extra`; the pet store's
  `MetricsTest`; `GuideTranslationTest`.
- Files: `elasticore-dsl-guide.md` 5 (en, ko), `elasticore-annotation-guide.md` 4.1 (en, ko), `MetricsTest.java`,
  `CONTRIBUTING.md`, `docs/tour.md`, `docs/tour.ko.md`, `changes/template-field-list.md`,
  `changes/self-reference-followups.md`

### Documentation and tests (DDD - a root's reference to its own kind)

#### What a self-reference by id means for a caller is run over HTTP, and the env guide says what `referenceColumn: short` leaves alone
- Why: three follow-ups of the review of #100. The env guide still said `referenceColumn: short` names the column of
  every id reference after its field, the comment in both ddd templates' `dto.yml` read as if it described
  `subCategories`, and the 404 and the `null` relation were checked only in generated text.
- What: `SelfReferenceApiTest` in `elasticore-template-ddd` sends the requests. A `parentCategoryCatId` that names no row
  answers 404 `reference.Category.parentCategory`, one that names a row is written and the relation reads it, and on a
  `Category` in memory the id does not set the relation. No generated output changes: the `dto.yml` comment moves above
  `fields:`, and YAML comments are not read.
- Checked: `SelfReferenceApiTest` over HTTP on `elasticore-template-ddd` (the 404 and its code, a parent written and
  read back in a new transaction, the relation `null` when only the id is set); `GuideTranslationTest` for the env
  guide's 6-2 at the same lines in en and ko; no generated file changed.
- Files: `SelfReferenceApiTest.java`, `elasticore-env-guide.md` 6-2 (en, ko), `shop/model/dto.yml` of both ddd templates

### Improvements (`@deleted` - `mapping: xml` on Spring Boot 4)

#### `@deleted` works with `mapping: xml` on Spring Boot 4 (Hibernate 7)
- Why: `@deleted` with JPA's `mapping: xml` stopped the build. Hibernate 6 parses the `<sql-delete>` and
  `<where>` elements of its extended orm.xml schema but applies them nowhere - in its 6.4, 6.5 and 6.6 jars
  only the JAXB model and the legacy hbm.xml code name them; the row was removed (run on
  `elasticore-template-ddd`, Hibernate 6.5.2). Hibernate 7 processes orm.xml with Hibernate Models, whose
  `ManagedTypeProcessor` and `CommonPluralAttributeProcessing` apply `<sql-delete>` and `<sql-restriction>`.
- After: on Spring Boot 4 the generated orm.xml is written in Hibernate's schema
  (`http://www.hibernate.org/xsd/orm/mapping`, version 7.0) where a class needs it: `<sql-delete>` after the
  entity's table, `<sql-restriction>` in a collection of `@deleted` rows; the domain classes carry neither
  annotation nor import. A Hibernate `@SQLRestriction` / `@SQLDelete` written on a class with `@jpa:` moves into
  orm.xml the same way on Spring Boot 4 (Hibernate 7 applies it there too); on Spring Boot 3 it stays on the
  class, as before. `@deleted` with `mapping: xml` on Spring Boot 3 still stops the build, now saying it needs
  Spring Boot 4.
- Checked: the pet store (Spring Boot 4, Hibernate 7) switched to `mapping: xml` for the run, with
  `PurchaseOrder` and `OrderLine` made `@deleted` as well: `SoftDeleteTest`, `ShopFlowTest`,
  `SchemaValidateTest` and a run that took a line out of an order (kept with its time, and the order read
  back with one line) - 23 of 23; the sample was put back. `SoftDeleteGenerationTest` checks the orm.xml of a
  Spring Boot 4 `mapping: xml` project (schema, `<sql-delete>`, a collection's `<sql-restriction>`, classes
  without Hibernate). Not kept as a sample: the DDL that `elcoreDdl` writes under `mapping: xml` reads no
  `money` / `quantity` converter from orm.xml and writes those columns as text - a limit of its own, older
  than this change.
- Files: `OrmMapping`, `SoftDelete`; the annotation guide (en and ko).

### New Features (v3 - `@deleted`)

#### `deletedAt: timestamp @deleted` - a delete keeps the row, with when, and no read finds it again
- Why: soft delete was hand code - a class `@jpa:SQLRestriction` filtered reads, but the delete still removed
  the row, nothing kept a request from writing the column, MyBatis dropped the restriction, and the rows that
  named a deleted one could not be read any more. A row other rows still name (a product orders were placed
  for) could not be deleted at all.
- After: one field line. JPA: the entity gets Hibernate's `@SQLDelete` (`UPDATE ... SET deleted_at =
  CURRENT_TIMESTAMP WHERE key = ? [AND version = ?] AND deleted_at IS NULL`) - the delete is still JPA's
  remove, so `@PreRemove`, the lifecycle's delete guard and the invariants run as before; on a split root it
  is on the developer's class and kept in line like `@Table`. The repository redeclares `findById`,
  `existsById`, `findAll()`, `findAll(Sort)`, `findAll(Pageable)` and `count()` with a `@Query`, the mapper's
  `toSpec` ends with `isNull(deletedAt)`, and a collection of such rows carries `@SQLRestriction` - a member
  taken out of its aggregate is kept and does not come back. No class-level `@SQLRestriction`: it would also
  hide the row from every row that names it (an item would answer 404 once its product was deleted). MyBatis:
  `deleteById` and `deleteAllBy...` are UPDATEs; `findById`, `findStoredById`, `countById`, `findAll`, the
  search, `findAllByLogin` and `findAllBy...In` read the live rows; `findAnyById` serves the Lookup, so a
  reference check still finds a deleted row and a row that names one can be saved again. The column is
  never written by an insert, an update or a request: read-only in the DTO and in the TypeScript field
  metadata, `insertable = false, updatable = false`, left out of the search DTO.
- Sign-in (`authenticate`, JPA and MyBatis) finds a deleted row no more. The layered MyBatis service replaces
  a collection on update by removing the old live rows of a `@deleted` member outright (`purgeAllBy...`) - kept
  rows would hold the keys the insert writes again; a delete of the root keeps them (`deleteAllBy...`).
- Refused, with the reason - the build stops: a delete that would remove other rows outright with it (members
  of the aggregate, collections and to-one alike, a `@cascade` that removes, `orphanRemoval`) unless they are
  `@deleted` too - `@cascade(PERSIST)` deletes nothing and is not refused; two `@deleted` fields; a type other
  than `datetime` / `timestamp`; the key; a composite key; a key the request gives under JPA without a
  `@jpa:Version` (a create of a deleted row's key would be merged into that row, which stays deleted); a
  `@deleted` inherited from a parent with a table of its own; an `@embeddable`, a `@rollup` entity, a
  single-table hierarchy; JPA with `mapping: xml` (Hibernate 6.5 reads neither `<sql-delete>` nor `<where>`
  from orm.xml - checked on `elasticore-template-ddd`, the row was removed). The 2.x-compatible bundle, which
  writes no delete that keeps the row, names `@deleted` in its unenforced-rule warning. Not generated:
  restoring a deleted row, and the filter in queries the developer declares (`@query`, derived finders, dbms
  ports, native SQL).
- A `@SQLDelete` the developer wrote on their own class (a split root) stays theirs: the class annotations are
  kept in line with the DSL for `@SQLDelete` only where the generator writes one.
- 2.x output: unchanged (rulesCheck -Pstrict 157 MATCH / 0 DIFF / 2 ACCEPTED, treeCheck 0 differ / 0 missing /
  0 extra). A model without `@deleted` generates what it did.
- Samples: pet store `Product.discontinuedAt` (JPA annotations, Hibernate 7), `elasticore-template-mybatis`
  `Category.retiredAt` (layered MyBatis, `datetime`), `elasticore-template-ddd-mybatis` `Shipment.deletedAt`
  (DDD over MyBatis), `elasticore-template-kt` `Ticket` and its member `TicketEntry` (Kotlin, with the
  lifecycle's delete guard); their DDL files are regenerated.
- Checked: `SoftDeleteRulesTest` (each refusal, the members rule - to-one, `@cascade(PERSIST)` - the JPA
  entity, collection and repository in Java and Kotlin, the MyBatis statements, the search DTO);
  `SoftDeleteGenerationTest` (hexagonal Java and Kotlin and the TypeScript client: an inherited `@deleted`, a
  split root, a member collection, the mapper's `toSpec`, sign-in, the refusals, `mapping: xml`; layered
  MyBatis: the members kept on delete and replaced on update); over HTTP and in the table - pet store `SoftDeleteTest` (an item
  still reads its deleted product, a request cannot write the column), `ShopApi` of `elasticore-template-mybatis`
  (a category with products and a child category, on H2 here, Oracle, PostgreSQL and SQL Server in CI),
  `DomainOnDatabase.aSoftDeleteKeepsTheRow` of `elasticore-template-ddd-mybatis` (the four databases),
  `DomainRulesDslTest` of `elasticore-template-kt` (the guard refuses an open ticket; a closed one is kept with
  its entries; an entry taken out is kept and does not come back).
- Files: `SoftDelete` (new), `JpaEntityAnnotationRules`, `JpaEntityBaseRules`, `RepositoryBaseRules`,
  `MapperSpecRules`, `MapperSpecShellRules`, `KotlinMapperSpecShellRules`, `SearchDtoBaseRules`,
  `MyBatisMapperRules`, `MyBatisSql`, `DddMyBatisPorts`, `EntitySpec`, `SpecFactory`, `RulesGenRunner`
  (managed class annotations), `EntityModelLoader`; the annotation and DSL guides (en and ko).

### New Features (lifecycle - `@stamp`)

#### `@stamp(STATE)` records when a lifecycle entered a state
- Why: `states:` writes the move, but "when was it shipped / cancelled / approved" was hand code in
  every behaviour class - the most common addition to a lifecycle, left to the developer.
- After: `shippedAt: timestamp @stamp(SHIPPED)` on a time field. The command that moves the row into
  the state sets the field right after the move, before the event - whichever command it is, and
  again on each entry (the field holds the last one). A command of two lifecycles stamps each state it
  enters, after every check passed; a refused command stamps nothing. `@stamp(fee.REFUNDED)` names the
  lifecycle where two have the state; `@stamp(SENT, CANCELLED)` takes several. `timestamp`, `datetime`
  and `date` take `Instant.now()`, `LocalDateTime.now()` and `LocalDate.now()`. The field is the
  lifecycle's like the state: read-only in the DTO, not mapped from a request, a protected setter,
  `readOnly: true` in the TypeScript field metadata. The lifecycle comment of the base lists it, and
  the Ossie export carries it as `stamp` in the field's extension.
- Where it cannot be written on a split root the model check fails, with the reason: a state no
  lifecycle has, a state two lifecycles have, a state no move enters (the initial one - `@default(now)`
  is for when a row is made), a field that is not a date or time. A warning would not do: the field is
  the lifecycle's, so left out it would be a column nothing ever writes. On an `@editable` root the
  field stays an ordinary writable one, with a DSL warning; with no lifecycle rule set selected (the
  layered bundle) the unenforced-rule warning names `@stamp (ddd-state)`. A `@stamp` on an
  `@abstract` parent is stamped by each split root that extends it. A state named twice is one line.
- JPA and MyBatis alike: the line is in the entity's command and the MyBatis adapter's UPDATE stores
  it. Java and Kotlin.
- 2.x output: unchanged (rulesCheck -Pstrict 157 MATCH / 0 DIFF / 2 ACCEPTED, treeCheck 0 differ /
  0 missing / 0 extra). A model without `@stamp` generates what it did.
- Samples: `Shipment` of `elasticore-template-ddd` and `elasticore-template-ddd-mybatis` has
  `sentAt` (timestamp), `cancelledAt` (datetime) and `refundedOn` (`date @stamp(fee.REFUNDED)`, set by
  the command of two lifecycles); `Ticket` of `elasticore-template-kt` has `closedAt`, set by `close`
  and by `cancel`. Their DDL files are regenerated with the new columns.
- Checked: `StateStampTest` generates Java and Kotlin hexagonal, Java layered and the TypeScript
  client - one lifecycle, two, several states, a state named twice, an inherited field, read-only DTO
  and client field, each refusal failing the run, the `@editable` root left writable, the layered
  warning; `StateStampApiTest` in `elasticore-template-ddd` over HTTP and the table (send
  stamps, cancel stamps both, a refused cancel stamps nothing, a create or update cannot write it);
  `DomainOnDatabase.aLifecycleStampIsStored` in `elasticore-template-ddd-mybatis` (H2 here; Oracle,
  PostgreSQL and SQL Server in CI); `DomainRulesDslTest` in `elasticore-template-kt` (close and cancel
  stamp, a refusal does not, the service stores it and ignores a request's value).
- Files: `DddStateRules`, `DddBehaviorRules` (guarded), `TypeScriptDtoFileRules`, `RulesGenRunner`
  (warning), `OssieSemanticModelExporter`, the DSL and rule guides (en and ko), the three sample
  models and the DDL of the two JPA samples.

### New Features (DTO - the fields of a `@template`)

#### A DTO lists the fields it takes right after the model they come from: `@template(Order[note, deliveryDate], Customer)`
- Why: `@template(Order)` copies every field of the entity, inherited audit fields included, and a smaller DTO was
  made by removing the rest one by one with `field: --`. A DTO taking four fields of thirty listed twenty-six
  removals, and a field added to the entity later appeared in the DTO, and so in every request that binds it, until
  someone added another removal (#79).
- What: a model in `@template` may carry its list in brackets. A model without brackets still copies all its fields,
  and a model of another domain is `shop:Order[note]`. Only the copied fields are filtered, where the model gathers
  them (`getAllFieldListMap`), so the 2.x generator, its compatible output and the 3.0 code take the same fields and
  the mapper needs no change. A field the DTO declares is always kept, and with a listed name it overrides the
  copied one. A field the model inherits through its own `@extend` can be listed, a DTO can be the template, and a
  relation's name keeps the field that holds its id (`customerId @idof(customer)` of a DDD reference). `customerId`
  itself is refused, as the list is checked before the reference is rewritten.
- A name that would give the DTO nothing is refused too, as a misspelt one is: a relation with no field that holds its
  id (the sentence names the way, `@ref(order.orderId)`), a collection, and a value object where the DTO does not carry
  one. This is asked after the references are rewritten, as that is when the id field exists, so a run that does not
  rewrite them (2.x, the compatible output, a layered project) refuses what a `ddd` or `hexagonal` run takes.
- A list without the key is passed over as the DTO of the entity's service and controller, where the first `@template`
  of the entity was taken whatever it held. A list with the key can still be that DTO. So an entity whose only
  `@template` DTO is a list without the key gets no service or controller (in 2.x and the compatible output alike), as
  an entity no DTO names; list the key, or add a DTO of the whole entity, to keep them.
- `AbstractDataModel` keeps its guard against a name that finds the model itself, on the way every copied field now goes.
- One reader: `TemplateList` is now the only place that reads the value. What asks which models a DTO is made from
  gets the plain names: the template's field copy, the DTO-to-entity relationship, the key DTO, the DTO's size
  validation, both mappers, `ForcedRefCheck`, `ContextMap` and `SpecFactory`. A value without brackets comes back
  exactly as it was written, so a DTO without brackets generates what it did, an existing two-template DTO included.
- Refused once every domain is loaded, so a template of another domain is found: a name the model does not have, a
  model no domain has, the same model twice (`Order[note], Order[total]` or `Order, Order[note]`), empty brackets
  (`Order[]`), an empty name in a list, the same field twice (`Order[note, note]`), brackets that cannot be read
  (`Order[note`), `--` on a listed field, a list together with `@extend`, a list on an entity, a list on `@searchable`
  or `@searchResult`, and, beside a list, a name more than one domain has without saying which. The copy takes the
  first domain with a name, so a check that looked elsewhere could pass a field the copy does not have. Both generators
  stop on it (2.x on the DSL warning, the rule engine on the model check).
- Checked: `TemplateFieldListTest` - the reader, a template DTO in the compatible output and the 3.0 code, each model
  with its own list, a model with and without its domain, the mapper's pairing in both outputs, the key DTO, a DTO
  as the template, an inherited field, a relation name with and without an id field, a DDD id field by its own name,
  a value object with and without a DTO that carries it, a model that names itself, a name two domains have, a list
  declared before the entity's DTO, Kotlin, TypeScript, `ddd`, MyBatis, the refusals, a DTO without brackets that is
  not refused, and the 2.x generator in a JVM of its own taking the same fields and refusing the same. The template's
  `hr` model declares `EmployeeContactDTO @template(Employee[...])`, so `elcoreRulesCheck` and `elcoreTreeCheck` compare
  it between 2.x and the rule engine: `158 MATCH / 0 DIFF / 2 ACCEPTED / 0 SKIP` and `239 generated by 2.x | 234 identical
  | 1 import order only | 4 accepted | 0 differ | 0 missing | 0 extra`.
- Files: `TemplateList.java`, `AbstractDataModel.java`, `ForcedRefCheck.java`, `ModelExtractor.java`,
  `DtoSrcFilePublisher.java`, `MapperSrcPublisher.java`, `SrcFilePublisher.java`, `ContextMap.java`,
  `RulesGenRunner.java`, `SpecFactory.java`, `TemplateFieldListTest.java`, the template's `hr/model/dto.yml` and its
  regenerated sources, `elasticore-annotation-guide.md` 4.1 and `elasticore-dsl-guide.md` (en, ko)

## 3.0.261105-rc10

### New Features (v3 controller - `@expose(methods)`)

#### `@expose(20, methods="read,search")` lists the endpoints an entity's controller serves
- Why: every `@expose` entity got the whole CRUD controller, and most roots need part of it - a
  receipt the application writes is only read over HTTP. Taking methods away with `@restExclude`
  named internal section ids (`batchUpdate`, `deleteBatch`, `deleteById`), listed what to drop
  rather than what to keep, and also took the methods off the service.
- After: `methods` names what the controller serves - `create`, `read`, `search`, `update`
  (`PUT /` and `PUT /batch`), `delete` (`DELETE /{id}` and `POST /delete`); the rest is not
  generated. `GET /schema/info`, commands and sign-in stay. The TypeScript client (`XxxApi.ts`)
  calls only those and imports only what its functions read (a client compiled with
  `noUnusedLocals` failed on the key DTO of an entity that deletes nothing); a client with nothing
  to call is not written. The use case (hexagonal, DDD, Java and Kotlin) drops the writes not
  served and keeps the reads. The service keeps every method. Without `methods` the server output
  is unchanged. The list is quoted: unquoted, the loader reads only its first name (an annotation
  with key=value parameters keeps no bare value), which serves fewer endpoints, not more.
- Changed by default (3.0 TypeScript clients, of a 3.0 or a 2.x server): a client whose server
  leaves endpoints out - `@readonly`, `@restExclude` - no longer imports what none of its functions
  read. The `@readonly` client imported the key DTO of a delete
  it does not have, which a client compiled with `noUnusedLocals` refused. A client with every
  endpoint is byte for byte what it was (the pet store's `webBuild` and the template-kt
  `elcore3tsVerify -Pstrict` pass).
- A name it does not know is a DSL warning and serves nothing (`methods="raed,search"` serves
  search) - a typo gives fewer endpoints, never more; `methods=""` serves none. A parameter of
  `@expose` other than `methods`, and a write listed beside `@readonly`, are DSL warnings too.
- `@restExclude` is deprecated: it still applies, and each entity using it gets a DSL warning naming
  the `methods` list with the same endpoints - or, where no list has them (`@restExclude(batchUpdate)`
  keeps `PUT /`; `schemaInfo` stays under every list), the nearest one, which serves fewer, and what
  it changes. It reached the guides in 3.0.261015-alpha1, so it is not removed. Names in `methods`
  and the key itself are read without regard to case.
- A generated controller kept as the developer's because the DSL's change does not merge (an
  endpoint that goes was edited) now gets a DSL warning naming each endpoint the generator wrote
  last, no longer writes, and the file still maps - `@expose(methods)`, `@readonly` and
  `@restExclude` alike. An endpoint the developer added is never named.
- 2.x output: unchanged (rulesCheck -Pstrict 157 MATCH / 0 DIFF / 2 ACCEPTED, treeCheck 0 differ /
  0 missing / 0 extra). The 2.x-compatible bundle does not read `methods`, and a TypeScript client of
  a 2.x server calls every endpoint that server has.
- Samples: `Receipt` of `elasticore-template-ddd` and `elasticore-template-ddd-mybatis` is
  `@expose(20, methods="read,search")` - billing's process manager writes receipts through the
  repository port, and the API reads them.
- Checked: `ExposeMethodsTest` generates Java and Kotlin hexagonal, Java layered, their TypeScript
  clients and a client of a 2.x server (also a `@readonly` client's imports, an inexact
  `@restExclude` suggestion, an unquoted list, capitals); `ControllerMethodRulesTest` (2.x skeleton ignores it, each
  name, the warnings); `GeneratedMergeTest.narrowingTheMethodsTakesTheEndpointsOutOrNamesWhatIsLeft`
  (never edited, edited elsewhere, edited in an endpoint that goes beside an endpoint of the
  developer's own); `ExposeMethodsApiTest` in `elasticore-template-ddd` over HTTP (read 200,
  search finds it, `DELETE /{id}` and `POST /delete` 405, the service still saves).
- Files: `ControllerBaseRules`, `EntitySpec` (`metaProperty`), `SpecFactory`, `TypeScriptApiRules`,
  `RulesGenRunner` (warnings), the annotation, rule, rule-dev and env guides (en and ko), the two
  billing blueprints.

### Bug Fixes (project DDL - a Kotlin relation read through its id)

#### The project DDL of a Kotlin entity has the foreign key of a relation referenced by its id
- Why: the project DDL (`ddl { databases }`) reads the generated entity sources, Java with JavaParser and Kotlin
  line by line (`JpaSchemaReader`). Under the DDD reference rules a relation to another root is read through its
  id, and the Kotlin rules write that relation as `final var department: Department? = null`. The Kotlin line
  pattern took `private`, `open`, `override` and the like before `var`, but not `final`, so the relation was not
  read at all. Its column was still there, from the id field (`departmentDeptId`), but the foreign key was not:
  in `elasticore-template-kt` none of the nine relations read through an id had one, while the Java output
  writes them (`fk_shop_products_category` in `elasticore-template-ddd`). Hibernate, mapping the same entity,
  makes the constraint.
- What: `final` is one of the modifiers the pattern takes. A Kotlin relation read through its id gets its
  foreign key, and its column stands where the relation is declared, as in the Java output; the columns
  themselves are unchanged.
- In `elasticore-template-kt` the four `db/schema-*.sql` gain nine foreign keys (`fk_hr_employee_department`,
  `fk_shop_order_customer`, `fk_shop_products_category` and six more) and those columns move. A project that
  writes migrations (`ddl { migration }`) gets one that adds the foreign keys, which runs as written, as a new
  foreign key does; the order of a table's columns is not compared. Java output, and Kotlin output without such
  a relation, are unchanged.
- Checked: `JpaSchemaReaderTest.aKotlinRelationReadThroughItsIdKeepsItsForeignKey` - an entity written as the
  Kotlin rules write it; it fails without the change. `elasticore-template-kt` builds with its tests
  (`SchemaValidateTest` among them) on the regenerated schemas.
- Files: `JpaSchemaReader.java`, `JpaSchemaReaderTest.java`, `elasticore-template-kt` `db/schema-*.sql`

### Bug Fixes (sample - pet store schema files)

#### The pet store's schema files say the length of `credit_card_type` the generator writes
- Why: since the v3 column of a required enum is as long as its `@db` code (3.0.261104-rc9), the generator writes `credit_card_type VARCHAR(4)`, but the committed `db/schema-*.sql` of the pet store still said `VARCHAR(255)`; every build that generated rewrote them, so a fresh checkout showed four changed files.
- Before / after: `VARCHAR(255)` / `VARCHAR2(255 CHAR)` / `NVARCHAR(255)` -> `VARCHAR(4)` / `VARCHAR2(4 CHAR)` / `NVARCHAR(4)` for H2 and PostgreSQL, Oracle and SQL Server. Sample output only; the generator is unchanged.
- Checked: regenerated with `:elasticore-sample-petstore:elcore3` from `main`; the four files differ in that line only.
- Files: `elasticore-sample-petstore/src/main/resources/db/schema-{h2,postgresql,oracle,sqlserver}.sql`

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
  - **A field a DTO declares under another name comes out under the id field's name**, as it already does for a
    reference to another root. With `parentKey: long @ref(parent.commentId)` the DTO has `parentCommentId`, so a
    property of the API is renamed, and a client still sending `parentKey` is ignored: the row is saved with no parent
    and no error. The ddd templates' `parentCatId` is `parentCategoryCatId` now. Use the id field's name, or mark the
    relation `@objectref`, which keeps the object and the declared name. A field declared under the id field's own
    name (`parentCommentId: long @ref(parent.commentId)`) stays, and the mapper writes the id.
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
  key in all three, a `@ref` declared under another name (under the id's name), under the id's own name (kept),
  `@objectref` keeping a declared name, `referenceColumn: short` with a key that has `@db`, a member's back reference
  and its reference to its own kind, a required reference, two references on one entity, `requireReferences` and the
  read-back of a root with no other reference by id, and the layered output. Nine of its fourteen tests fail without
  the change. The ddd, ddd-mybatis and Kotlin templates build with their tests.
- Files: `IdReferences.java`, `SelfReferenceByIdTest.java`, both ddd
  templates' `shop/model/dto.yml` and `semantic_model.yml`, `elasticore-template-ddd` `orm.xml`,
  `elasticore-rule-guide.md` "Refer to another aggregate by ID" (en, ko)

## 3.0.261104-rc9

New: `ddl { comments = true }` writes each column's label to the database, `@ref(...) @forceupdate` clears a
relation on a full update, and the Ossie export carries a class `@jpa:Where` / `@jpa:SQLRestriction` as the dataset's
row filter. Changed: an HTTP port call that gets an error status it cannot return as a value throws
`HttpPortException` (`HttpApiClient.WebClientException` is removed), and a DBMS port query keeps its own ORDER BY when
a sort value comes. Fixes: the v3 column of a required, `@unique` or not updatable enum is as long as its `@db` code,
the mapper searches a relation by its flattened id when the model declares the entity's DTO, and the TypeScript
client reads a 200 without a body as no value.

### New Features (project DDL - column comments)

#### `ddl { comments = true }` says each column's label to the database
- Why: a field's label (`-- label`) becomes Hibernate's `@Comment` on the entity, and Hibernate's own
  schema export writes it, but the project DDL wrote no comment: a database made from it, or from its
  migrations, had none.
- What: an opt-in `ddl { comments = true }` (the plugin's `DdlSpec.getComments`, passed as
  `elasticore.ddl.comments`; `DdlConfig.comments`), which a domain overrides with `ddl.comments` in its
  env.yml. With it on, a JPA domain's column takes its field's `@Comment` (a relation's goes on its
  join column) and a MyBatis domain's (`MyBatisTables`) its field's label. The schema and a new table
  say it after `CREATE TABLE`: `COMMENT ON COLUMN` on H2, PostgreSQL and Oracle, and on SQL Server the
  `MS_Description` property with `N'...'`. A quote in it is doubled.
- On SQL Server, whether the property is there is the database's to say, not the state's - comments
  turned off and on again, a database Hibernate made, a baseline can each hold one the state does not:
  `IF EXISTS (... sys.extended_properties ...)` updates it, or else adds it, and a drop runs only where
  it is there. The schema is the table's, or the login's default (`SCHEMA_NAME()` through a variable)
  where the table names none.
- A comment added, changed or removed is a change that runs as written, outside `sameStorage`; none
  is `IS NULL`, Oracle's `IS ''` and `sp_dropextendedproperty`. A new column's comment comes in the
  same change. A Kotlin entity's escaped `\$` is a dollar.
- The state keeps a comment only when they are on (`"comment"` of a column), so a state written
  without them reads as it did; off, nothing is compared, written or kept, and the schema is the bytes
  it was; turning them off leaves the comments already in the database. A label longer than Oracle
  keeps (4000 bytes in UTF-8) or SQL Server (7500 bytes, two a character) stops the run, and nothing is
  written for its database group. Columns only: an entity's label is written nowhere yet.
- Checked: `ColumnCommentTest` (each database's words and a quote, a table in a schema, a new table
  and column, added, changed and removed, off is byte-identical and compares nothing, the state with
  and without, bytes counted per database, the project's setting and a domain's), `DdlMigrationTest`
  (JPA and MyBatis: the schema, the first migration and the state; a label changed is
  `comment of note.title` and no review; off says nothing unless the domain asks; a label over
  Oracle's limit stops the run; a Kotlin entity's dollar), `ElastiCorePluginTest.theDdlCommentsArePassedWhenOn`,
  and the template-mybatis `MigrationRun.comments` - a table's comments, their change, then comments
  off and on again and a drop of one the database does not hold, read back from `information_schema`
  (H2, every build), `pg_description`, `user_col_comments` and `sys.extended_properties` (`dbTest`).
- Files: `Column.java`, `Table.java`, `Schema.java`, `Database.java`, `SchemaWriter.java`,
  `SchemaDiff.java`, `DdlConfig.java`, `DdlRun.java`, `JpaSchemaReader.java`, `MyBatisTables.java`,
  `DdlSpec.java`, `ElastiCorePlugin.java`, the tests above, `elasticore-env-guide.md` 9-1 (en, ko)

### Bug Fixes (runtime - HTTP port error status)

#### An HTTP port call that gets an error status throws one exception, HttpPortException, with the status, headers and body
- Why: a service has to tell a 404 from a 409 from a 500 and log what the other side said (issue #75). The runtime threw
  three unrelated types for an error status, and two of them dropped the body: `HttpApiClient.exchange` raised
  `HttpApiClient.WebClientException` with a fixed message ("4xx Client Error"), a `multipart/form-data` port method
  raised Spring's `HttpStatusCodeException` (RestTemplate), and a port method whose error body did not read as its
  return type raised a raw decoding exception. The `WebClientResponseException -> IllegalStateException` mapping in
  `HttpServiceProxyFactory.invoke_old` never ran.
- After: `io.elasticore.springboot3.http.HttpPortException` (unchecked) carries `getStatusCode()`, `getResponseBody()`
  (text), `getResponseHeaders()` / `getResponseHeader(name)`, `getPort()` and `getMethod()`; its message is
  `erp.CreditCheckPort.check: HTTP 409 Conflict - <body, cut at 500 characters>`. `HttpApiClient.exchange`, the
  multipart call and an error answer the proxy cannot read as the return type all throw it; the proxy adds the port and
  method. `HttpApiClient.WebClientException` is removed (no guide, template or generated code named it). Unchanged: an
  error answer with a body that reads as the return type is still the method's return value (port guide 2.8), a call
  that got no answer is still `WebClientRequestException`, and `httpPort: spring` is untouched. No generated file
  changes. Code that caught `HttpApiClient.WebClientException` or `HttpStatusCodeException` from a port catches
  `HttpPortException` now.
- Checked: `HttpPortExceptionTest` (MockWebServer answering 404, 409 and 500 with a JSON or HTML body and a header:
  type, status, body, header, port and method on `HttpApiClient.exchange`, on a port method over WebClient and on a
  multipart port method; a long body cut in the message; no answer stays `WebClientRequestException`),
  `PortFailureContractTest` and `HttpServiceProxyFactoryTest` updated; `elasticore-springboot3` (244) and
  `elasticore-springboot4` (245) tests pass; formatCheck, diffErrorprone, diffCoverage (84.7%) and diffMutation
  (81.4%) pass.
- Files: `elasticore-springboot3/.../http/HttpPortException.java` (new), `HttpApiClient.java`,
  `HttpServiceProxyFactory.java`, their tests; `elasticore-dsl-port-guide.md` (English and Korean) 2.8.

### New Features (Ossie export - a row filter on an entity)

#### A class `@jpa:Where` or `@jpa:SQLRestriction`, such as a soft delete, is in the dataset
- Why: an entity whose class says `@jpa:Where(clause = "delete_yn = 'N'")` is read by the application
  without its deleted rows, but its dataset said only the table. A BI tool or an agent reading the
  semantic model counted the deleted rows too, and so did every metric over that dataset, with nothing
  in the model to say why the numbers differ from the application's.
- What: Ossie has no row filter on a dataset, and a query as `source` would break a consumer that reads
  `source` as a table, so `source` stays the table. The dataset's `ELASTICORE` extension gets
  `row_filter`, the clause as written, and its `description` ends with `(rows only where: <clause>)`.
  A metric over a filtered dataset, also through the metrics it is made of, gets
  `dataset_row_filter: true` in its extension.
- Read as Hibernate reads them: `Where` and `SQLRestriction`, fully qualified or not, with the `@jpa:`
  or `@entity:` prefix that writes them onto the class; `SQLRestriction` over `Where`; only the
  entity's own class. A filter on an `@abstract` parent is a warning and is not exported, because
  Hibernate applies it to the child only when the parent is written as an entity, not as a
  `@MappedSuperclass`, and the export does not know which. A clause that is not one string literal is
  a warning and is not exported. `@Filter` is not read: it is off until a session turns it on.
- An entity without a filter, and a metric over none, are exported byte for byte as before.
- Checked: `OssieExportTest` - `@Where`, a fully qualified `@SQLRestriction` with double and single
  quotes in the clause, both on one class, `@entity:`, a parent's filter, a clause that is no literal,
  metrics over a filtered and an unfiltered dataset, and the same model with no filter giving the same
  bytes for what no filter touches. All but the last fail without the change.
- Files: `OssieSemanticModelExporter.java`, `OssieMetrics.java` (a metric's datasets),
  `OssieExportTest.java`, `elasticore-rule-guide.md` (en, ko - DSL -> Ossie table)

### Improvements (DBMS port runtime - sort)

#### A DBMS port query keeps its own ORDER BY when a sort value comes with the request
- Why: `SqlPortGateway` appended ` ORDER BY <requested>` to the end of the SQL, so a query with an
  ORDER BY of its own, or one ending with LIMIT, OFFSET or FETCH, failed as soon as a sort value
  came (#73). A query had to drop its default order, or the service had to clear the sort values
  of the search DTO, and the user could not sort that list.
- After: where the SQL has an ORDER BY at its top level (outside parentheses, quotes and comments -
  the scan the page count already uses), the requested terms go first and the query's own stay
  after them as tie breakers: `ORDER BY reg_date DESC LIMIT 10` + `amount-` ->
  `ORDER BY amount DESC, reg_date DESC LIMIT 10`. Where it has none, the clause goes before a
  top-level LIMIT, OFFSET or FETCH (not a `:limit` parameter or a `t.offset` column). A query
  without either gets exactly what it got before. The checks of the sort terms are unchanged.
  The top-level scan lower-cases char by char, so a character that lengthens in lower case (a
  dotted capital I in a literal) no longer shifts the positions.
- Generated code: unchanged. `mode: mybatis` is not changed: its port mapper is generated, not run
  through `SqlPortGateway`, and still leaves out the requested sort where the SQL names its own
  ORDER BY (`MyBatisPortRules`).
- Checked: `DbTransactionGatewaySortTest` on H2 - own ORDER BY with a sort (ties broken by the own
  terms, also on a counted page), LIMIT / OFFSET ... FETCH / FETCH FIRST with and without an own
  ORDER BY, an ORDER BY or LIMIT in a subquery, a string literal and comments left alone; the
  test that pinned the failure (`ownOrderByBreaksSortedQuery`) is replaced.
  `:elasticore-springboot3:test` and `:elasticore-springboot4:test` pass.
- Files: `elasticore-springboot3/.../bean/SqlPortGateway.java` (shared by elasticore-springboot4),
  `DbTransactionGatewaySortTest.java`, `elasticore-guide/elasticore-dsl-port-guide.md` and
  `ko/elasticore-dsl-port-guide.md` (3.6 sortCode, Using sortCode).

### New Features (mapper - `@ref` with `@forceupdate`)

#### A `@ref` onto a relation's key with `@forceupdate` takes the relation away when no id comes
- Why: a DTO names a relation by its key, `assigneeId: string(20) @ref(assignee.employeeId)`, and the
  mapper attaches the reference only when the id has a value. A null or empty id left the old relation
  in place, and `@forceupdate`, which writes a plain field even when it is null, was not read for a
  `@ref`. A service that lets a user clear a relation set it to null by hand after the mapper.
- What: with `@forceupdate`, the attaching `if(hasValue(...))` gets
  `else if(!isSkipNull){ to.setAssignee(null); }` - on a full update only, since the mapper serves a
  PATCH too and a PATCH skips what it does not send. `null` and `""` are both no id, as `hasValue`
  reads them. Both generators (2.x `MapperSrcPublisher` and the rule engine's `MapperBaseRules`), both
  overloads (plain and with an `EntityManager`), Java, Kotlin and MyBatis.
- Refused once every domain is loaded (`ForcedRefCheck`), so that both generators stop: a required
  relation (`!` or `@notnull`), a `@ref` that is not the relation's single key, and a path past
  `relation.key`. Said to do nothing, once and for both generators, where the clear does not reach the
  row: a relation that cascades, one joined read-only on another field's column (`@joinBy`, mapped
  insertable = false, updatable = false: 2.x clears it in memory only, the 3.0 code writes that field),
  one with `@updatable(false)`, and a `@ref` whose head is no relation of the template; by the rule
  engine, a DDD reference to another aggregate, held by its id field. A template's relation is
  resolved in the domain a name says (`crm:Employee`), else in the DTO's own first.
- A `@ref` without `@forceupdate` is generated as before.
- Checked: `RefForceUpdateTest` - the else in both overloads in the compatible output and the 3.0 code,
  in Kotlin and in MyBatis (one overload, no `EntityManager`), none for a `@ref` without
  `@forceupdate`, the three refusals, each warning (the read-only join once in every output and in 2.x), a name
  another domain has too, and the 2.x generator in a JVM of its own
  writing the same and refusing the same. The template's `AttachmentDTO.articleId` is now
  `@ref(article.articleId) @forceupdate`; `AttachmentArticleForceUpdateTest` runs the mapper 2.x
  generated against H2: a full update without an id (null, or empty) takes the attachment off its
  article, a PATCH keeps it, an id attaches that article.
- Files: `MapperBaseRules.java`, `MapperSrcPublisher.java`, `ForcedRefCheck.java`, `ModelExtractor.java`,
  `RefForceUpdateTest.java`, the template's `content/model/dto.yml`, its regenerated `ContentMapper.java`
  and `AttachmentArticleForceUpdateTest.java`, `elasticore-annotation-guide.md` 2.4 (en, ko)

### Bug Fixes (rule engine - a searched relation with a declared DTO)

#### The mapper searches a relation by its flattened id alone when the model declares the entity's DTO
- Why: a relation with `@search` (`board: Board! @search(eq)`) is searched through the id flattened onto the
  search DTO, `boardBoardId`; the search DTO derived from the entity leaves the relation itself out. When the model
  also declares the entity's DTO (`PostDTO: meta: dto @template(Post)`), the relation reached the mapper's search
  conditions as it was: `toSpec` read `Board board = searchDTO.getBoard();`, a property the search DTO does not
  have, so the generated project did not compile. Under `mode: mybatis` the run stopped with "board is further than
  that". The rule engine only - in the 2.x-compatible output, the v3 output (Java, Kotlin; layered, DDD,
  hexagonal) and MyBatis. 2.x writes the condition on the flattened id alone: the 2.2 generator, run on the same
  model, reads `searchDTO.getBoardBoardId()` and nothing else.
- What: the search DTO, the mapper and MyBatis's search ask one question, `SearchDtoBaseRules.searchedByItsFlattenedId`,
  and leave out the same relation. A project whose model does not declare such a DTO generates what it did.
- The template's `hr` model now declares it: `Project.lead: Employee @ManyToOne @search(eq)`, with
  `ProjectDTO @template(Project)`. `elcoreTreeCheck` compared `HrMapper.java` as `DIFFER` without the change. The
  regenerated `Project.java`, `ProjectSrchDTO.java`, `HrMapper.java`, `Q.java`, the schemas and `schema.yml` are
  committed: an optional `lead_emp_id` column, and a search by `leadEmpId`.
- Checked: `TemplateDtoSearchRelationTest` - the compatible and v3 output (layered and hexagonal), Kotlin and MyBatis;
  three of its four tests fail without the change. `elcoreTreeCheck` 0 differ.
- Files: `SearchDtoBaseRules.java`, `MapperSpecRules.java`, `MyBatisSearchRules.java`, `TemplateDtoSearchRelationTest.java`,
  the template's `hr/model/entity.yml` and its regenerated files, `elasticore-annotation-guide.md` 2, "Required relationship" (en, ko)

### Bug Fixes (TypeScript client - a 2xx without a body)

#### The TypeScript client reads a 200 without a body as no value
- Why: the generated server answers a void endpoint either way - `POST /signout` with 204, the batch update
  `PUT /batch` with `ResponseEntity.ok().build()`, a 200 with no body. The client's `request` read only a 204 as
  empty and parsed every other 2xx as JSON, so `batchUpdate(inputs)` rejected with
  `SyntaxError: Unexpected end of JSON input` - not an `ApiError` - after the server had saved every row.
  `requestOrNull`, the find by id, did the same on an empty 200.
- What: `apiSupport.ts` reads the body as text first. No text is `undefined` for `request` and `null` for
  `requestOrNull`, whatever the status; a body is parsed as JSON as before. The server's answers do not change.
- Checked: `check/apiSupport.test.ts` in `elasticore-template-kt` runs the generated `apiSupport.ts` in node
  (`elcore3tsTest`) against stubbed responses - a 200 without a body, a 204, a JSON body, a 404 and an empty 200
  for a find, a 400 still an `ApiError`. Without the change, the empty-200 tests fail with that `SyntaxError`.
- Files: `ts/apiSupport.tmpl`, `elasticore-template-kt/src/main/ts/check/apiSupport.test.ts`,
  `elasticore-rule-guide.md` 2, "Error codes and languages" (en, ko)

### Bug Fixes (v3 entity - an enum's column length)

#### The column of a required, `@unique` or not updatable enum field is as long as its `@db` code
- Why: an enum's column takes the length of the enum's `@db` field (`code: string(3)` -> `@Column(length = 3)`).
  2.x keeps that length apart from the column's other attributes and writes it only when there are none,
  and the rule engine took it over as it was. A field marked required (`boardType: BoardType!`), `@unique` or
  `@updatable(false)` got `@Column(nullable = false)` and so on without a length: Hibernate and the project DDL
  made the column `VARCHAR(255)`. In a real 3.0 project, twelve required enum columns of the PostgreSQL schema were
  `VARCHAR(255)`.
- What: the v3 output adds the length to the column's other attributes -
  `@Column(nullable = false, length = 3)`, `@Column(length = 3, unique = true)`,
  `@Column(updatable = false, length = 3)`; the schema has `VARCHAR(3)`. A column with nothing else said is
  `@Column(length = 3)` as before. Java and Kotlin. MyBatis's schema already took the code's length.
- The 2.x generator and the 2.x-compatible output (`output = 'v2'`) are unchanged: the 2.2 generator, run on the
  same model, writes `@Column(nullable = false)`, `@Column(unique = true)` and `@Column(updatable = false)`, as the
  compatible output does.
- For a database made from the v3 output before, the column is narrower now: a schema migration
  (`ddl { migration }`) writes the change for review.
- Checked: `EnumColumnLengthTest` - the v3 entity in Java and Kotlin, the PostgreSQL schema without
  `VARCHAR(255)`, the compatible output as 2.x writes it.
- Files: `JpaEntityBaseRules.java`, `EnumColumnLengthTest.java`, `elasticore-annotation-guide.md` 5 (en, ko)

## 3.0.261102-rc8

The guides a project receives are in English from this release; `guides = 'ko'` gives the Korean ones. `supportAI`
is removed: the setting is ignored with a warning and the service is generated without Spring AI tools. Fixes:
`checkEmpty` keeps `IS_NULL` / `IS_NOT_NULL`, a build with `generateBeforeCompile = false` no longer runs `elcoreDdl`
before its resources, and the warning that Oracle has no `TEXT` is given only for a schema written for Oracle. From
this release the history is written in English.

### Bug Fixes (runtime - `checkEmpty` with `IS_NULL` / `IS_NOT_NULL`)

#### checkEmpty no longer drops a condition that takes no value
- Why: `FieldInfo.where(op, value, checkEmpty)` returns a condition that is always true when
  `checkEmpty` is on and the value is null, blank or an empty collection - a search field left
  empty adds nothing. `IS_NULL` and `IS_NOT_NULL` take no value, so theirs is null every time, and
  `Q.Order.deletedAt(Op.IS_NULL, null, true)` - "deletedAt is null" - added no condition at all and
  the query returned every row, without a word. Generated mappers are not affected: `@search(isnull)`
  writes `isNull`/`isNotNull` itself.
- What: the empty check applies to operators that take a value. `IS_NULL` and `IS_NOT_NULL` always
  write their condition; every other operator is as before.
- Checked: `FieldInfoCheckEmptyTest` - `IS_NULL`/`IS_NOT_NULL` with a null value give `isNull`/
  `isNotNull` (fails without the change: the condition was `conjunction()`), and `EQ`, `LIKE` and `IN`
  with an empty value are still dropped.
- Files: `FieldInfo.java` (`where`), `FieldInfoCheckEmptyTest.java`

### Bug Fixes (project DDL - a warning about Oracle only for Oracle)

#### `columnDefinition "TEXT"` is said to have no Oracle type only when a schema is written for Oracle
- Why: reading a JPA entity for the project DDL, a column with `columnDefinition = "TEXT"` (what the
  DSL's `text` writes) warned `Oracle has no TEXT - its schema makes the column a CLOB, which
  ddl-auto=validate refuses there`, once per column, whatever `ddl.databases` named. A project writing
  its schema for PostgreSQL only got one line for every `text` field (37 on a 9-domain project) about a
  database it does not use.
- What: `DdlRun` tells `JpaSchemaReader` whether any group's databases include Oracle; the warning is said
  only then. A reader nobody told keeps warning, as before. The env guide's limits (9-1) say when, in
  English and Korean.
- Checked: `DdlMigrationTest.whatOnlyOracleRefusesIsSaidOnlyForASchemaForOracle` - a JPA entity with a
  `text` field: no warning with `h2,postgresql`, the warning with `h2,oracle` (fails without the change).
- Files: `JpaSchemaReader.java` (`forOracle`), `DdlRun.java`, `DdlMigrationTest.java`,
  `elasticore-env-guide.md` (en, ko)

### Bug Fixes (Gradle plugin - project DDL and `generateBeforeCompile`)

#### A build that does not generate before compiling no longer runs `elcoreDdl` before its resources
- Why: with `output = 'v2'` and `elasticore { ddl { databases = [...] } }`, the plugin runs `elcoreDdl`
  after `elcore` and also makes `processResources` depend on it - whatever `generateBeforeCompile` says.
  A project turns `generateBeforeCompile` off to generate only when `elcore` is run, yet every `build`,
  `test` or `bootRun` then ran `elcoreDdl`, which loads the model: a domain whose `env.yml` names a remote
  diagram (`resource:`) fetched it again and rewrote `_resource/model-*.yml`, on CI too.
- What: `processResources` waits for `elcoreDdl` only when the build generates before it compiles (the
  default). `elcore` is still finalized by `elcoreDdl`, so the DDL comes whenever the code is generated.
  The env guide (9-1, English and Korean) says so.
- Checked: `ElastiCorePluginTest.theDdlFollowsTheBuildsGeneration` - by default `processResources` and
  `elcore` both bring `:elcoreDdl`; with `generateBeforeCompile = false` only `elcore` does (fails without
  the change).
- Files: `ElastiCorePlugin.java`, `ElastiCorePluginTest.java`, `elasticore-env-guide.md` (en, ko)

### Changes (guides - English by default)

#### The guides a project receives are in English; `guides = 'ko'` gives the Korean ones
- Why: ElastiCORE is an open-source project for developers who mostly do not read Korean, and the guides
  copied into every project's `blueprint/` were in Korean only. Coding agents reading them also wrote the
  code they added by hand in Korean.
- Before: every generation copied the Korean guides.
- After: the nine guides are written in English (`elasticore-guide/`) and in Korean (`elasticore-guide/ko/`),
  line for line. A generation copies the English ones under the same file names, with the index beside them;
  `elasticore { guides = 'ko' }` (`-Delasticore.guides=ko` without the plugin) copies the Korean ones. A
  language the guides are not written in gives a warning and the English guides. The env guide says so in
  12-1; README, CONTRIBUTING and the Korean documents link to the guides of their language. Change fragments
  are written in English from now on (`changes/README.md`); the history before stays in Korean.
- Generated code: unchanged - only the guides copied beside the model differ.
- Checked: `GuideTranslationTest` - each guide has the same lines, headings with their numbers, and code
  blocks of the same length in both languages, and the English prose holds no Korean;
  `GuideCoverageTest` runs on the English guides and, as `GuideCoverageKoTest`, on the Korean ones;
  `GuideIndexTest.theLanguageIsChosen` deploys the default, `ko` and an unknown language;
  `ElastiCorePluginTest.theGuidesLanguageIsPassed`.
- Files: `GuideDeployer`, `GuideIndex`, `ElastiCoreExtension`, `ElastiCorePlugin`,
  `elasticore-guide/*.md`, `elasticore-guide/ko/*.md`, `README.md`, `README.ko.md`, `CONTRIBUTING.md`,
  `changes/README.md`, `docs/*.ko.md`.

### Changes (supportAI removed)

#### `supportAI` is removed; the service is generated without Spring AI tools
- Why: the Spring AI tools it wrote on `XxxService` repeated the REST API that an agent can already call
  (OpenAPI), added about a dozen tools per entity, and opened deletes by condition and bulk changes to a model
  by default. 3.0 is not released yet, so it goes before the release rather than after it.
- Before: `supportAI: true` wrote the extension service from `service_extend_ai.tmpl` (2.x, v3 and Kotlin),
  each core method again with `@Tool` / `@ToolParam`.
- After: the setting is ignored. Every generator writes the plain extension service, and the run that writes
  the services prints, once per domain that still sets it,
  `[ElastiCORE] domain '<name>': supportAI was removed and is ignored - ...` - `elcore3` (also with `-Pv2`)
  and the 2.x template generator (`./gradlew elcore`, which `output = 'v2'` runs). The TypeScript client's
  run and the DDL-only run say nothing. The three templates, `EntitySpec.supportAI` and the tool variables
  of `ServiceExtendBaseRules` are gone; the setup wizard no longer writes `supportAI: false`, and the
  samples no longer set it (the Kotlin sample's `hr` domain set `true`, with a Spring AI milestone
  dependency, both removed).
- Upgrading a project that used it, on 3.0 code (`elcore3`): a `XxxService` the earlier version wrote and
  nobody touched is written again without the tools (and, like every extension service, without
  `@Primary`); an edited one loses the tool methods and keeps the edits where they do not overlap
  (`MERGED(your edits kept)`). On the 2.x code (`output = 'v2'`, `-Pv2`, `merge = false`) the service is
  written once and never again, so it keeps its tools, as does one left `KEPT` or `SKIP(user-owned)`:
  delete the `@Tool` methods (or the file, to have it written again) before dropping the Spring AI
  dependency. A `ToolCallbackProvider` bean the project registered finds no tools in a regenerated service.
  Take `supportAI` out of `env.yml`. The env guide (chapter 3) and the migration guide (item 5) say so.
- Generated code: unchanged for every domain without `supportAI: true`.
- Checked: `SupportAIRemovedTest` - the rule engine (2.x bundle and v3) and the 2.x generator in its own
  JVM write the plain service and warn once; no warning without the setting or in the TypeScript run.
  `GeneratedMergeTest.toolsAnEarlierVersionWroteLeaveAnEditedService` - a service the cache holds with a
  tool, edited by the developer, is merged without the tool and with the edit.
- Files: `ServiceSrcPublisher`, `RulesGenRunner`, `ServiceExtendBaseRules`, `EntitySpec`, `SpecFactory`,
  `DddRepositoryPortRules`, `InitializationHelper`, `service_extend_ai.tmpl` (three, deleted), the sample
  `env.yml` files, `elasticore-template-kt/build.gradle`, `elasticore-guide/elasticore-env-guide.md`,
  `elasticore-rule-guide.md`, `elasticore-migration-guide.md` and their `ko/` versions.

## 3.0.261031-rc7

고친 것의 릴리스다. MyBatis 에서 이미 쓰인 고유값은 JPA 처럼 409 `unique` 로 답한다. 직접 쓴 Flyway 이력 위의
`-PddlBaseline` 은 외래키·인덱스 이름을 넘겨받아 그 이름을 지우는 문장을 검토로 쓰고, Spring Boot 기본 위치(`db/migration`
바로 아래)의 이력도 덮어쓰지 않는다. 2.x 에서 3.0 코드로 옮기는 첫 병합은 사용자 블록 때문에 충돌하지 않는다. Ossie 내보내기는
2.x 코드와 함께 쓸 수 있고 고유 키의 컬럼 이름을 바로 쓴다. 기여자를 위해 머지 전 게이트(바뀐 줄의 포맷·Error Prone·커버리지·
변이 테스트)가 생겼다.

### Bug Fixes (프로젝트 DDL - `-PddlBaseline` 으로 넘겨받은 이름)

#### 넘겨받은 이력의 외래키·인덱스를 그 이름으로 지우는 문장은 검토로 - 그 DB 는 다른 이름일 수 있다
- 왜: `-PddlBaseline` 은 직접 쓴 Flyway 이력이 만든 DB 위에서 상태를 지금 모델로 시작한다. 상태의 외래키·인덱스 이름은 모델이
  붙일 이름(`fk_{table}_{field}`, 30 자 넘으면 해시)인데, 그 DB 를 만든 것은 대개 Hibernate 이고 Hibernate 는 외래키를 해시로
  이름 붙이며(`FK` 와 해시 25 자) `@Index(unique)` 를 유일 제약으로 둔다. 마이그레이션은 외래키·인덱스 삭제를 실행
  문장으로 쓰므로, 넘겨받은 뒤 관계나 `@Index` 를 빼면 `DROP CONSTRAINT fk_...`(없는 이름)·`DROP INDEX ..._u`(제약이 쓰는
  인덱스)가 실패한다 - Flyway 가 실패하면 앱이 뜨지 않는다. 도메인 9개짜리 프로젝트에서 Hibernate 스키마와 프로젝트 DDL 을
  대조하면 테이블·컬럼·타입·외래키 대상은 모두 같고, 외래키 이름 69/69 와 유일 인덱스의 형태 32/32 가 달랐다
- 무엇: `-PddlBaseline` 이 쓴 상태는 그때의 외래키·인덱스 이름을 `takenOver` 로 적는다. 그 이름을 지우는 문장과 지운 뒤
  같은 이름으로 다시 만드는 문장은 검토 주석으로 쓰고 이유를 단다(그 DB 의 이름으로 바꿔 실행)
  - SQL Server 에서 컬럼을 바꾸며 그 이름의 인덱스·외래키를 지웠다 다시 만드는 것은 SQL Server 마이그레이션에서만 검토이고,
    같은 이력의 다른 DB(PostgreSQL 등)에서는 컬럼 변경을 그대로 실행한다(`Change.reviewOn`·`safeOn`)
  - 빌드의 `REVIEW` 줄은 모든 DB 가 기다리면 `*/V...`, 일부만이면 그 DB 의 폴더를 가리킨다
  - 넘겨받은 뒤 마이그레이션이 만든 외래키·인덱스는 그 이름이 맞으므로 전처럼 실행한다. 모델에 그 이름이 남아 있는 동안
    `takenOver` 도 남는다. 이름은 바꾸지 않는다 - 3.0 의 `init` 으로 만든 DB 는 지금 이름을 쓴다
  - `takenOver` 가 없는 상태 파일은 전과 같다(쓰는 내용도 바이트 단위로 같다). 이 변경 전에 `-PddlBaseline` 으로 만든 상태에는 `takenOver` 가 없다 - 가이드(9-1)에 다시
    baseline 하는 법
- 확인: `DdlMigrationTest.aKeyNamedByAHistoryTakenOverIsNotDroppedAsWritten`(자기 이력 위에 baseline → 관계를 빼면
  `-- ALTER TABLE visit DROP CONSTRAINT fk_visit_customer;` 와 이유, 마이그레이션이 만든 `fk_visit_owner` 는 빼면 실행 문장),
  `whatOnlySqlServerDropsAndMakesAgainWaitsThereAlone`, `SchemaDiffTest.anIndexByANameTakenOverIsNotDroppedAndMadeAgainAsWritten`,
  `whatANameTakenOverChangesIsSaidAndWaitsWhereItMatters`.
  위 프로젝트(`db/migration` 의 자기 이력, 기본 위치 이력을 다루는 변경과 함께 돌림)에서 baseline 이 이름 124 개(외래키 69,
  인덱스 55)를 넘겨받고, 생성된 엔티티에서 관계 하나를 빼자 마이그레이션이 `-- ALTER TABLE <테이블> DROP CONSTRAINT fk_<테이블>_<관계>;`
  와 이유를 검토로 썼다 - 그 DB 에서 이 외래키의 이름은 Hibernate 의 해시 이름이다
- 관련 파일: `Schema.java`(`takenOver`), `SchemaDiff.java`, `DdlRun.java`(baseline, 상태 쓰기), `DdlMigrationTest.java`,
  `SchemaDiffTest.java`, `elasticore-env-guide.md`(9-1 절)

### Bug Fixes (프로젝트 DDL - 기본 위치에 있는 Flyway 이력)

#### `db/migration` 바로 아래에 직접 쓴 이력이 있으면 첫 마이그레이션을 쓰지 않는다 - `-PddlBaseline` 도 그 이력에 먹는다
- 왜: 마이그레이션은 `db/migration/{db}/` 에 쓰고, 이미 있는 이력은 그 폴더에서만 찾았다. Spring Boot 의 기본 위치
  `classpath:db/migration` 에 이력을 둔 프로젝트(`db/migration/V1__baseline.sql` ...)는 이력이 없는 것으로 보여, 처음 켜면
  `db/migration/{db}/V{시각}__init.sql`(모든 테이블의 CREATE)을 썼다. Flyway 는 위치 아래의 폴더까지 읽으므로, 다음 기동에서
  그 init 이 기존 이력 뒤에 실행돼 이미 있는 테이블을 만들다 실패한다 - 앱이 뜨지 않는다. 가이드가 이런 프로젝트에 권하는
  `-PddlBaseline` 도 같은 판정이라 효과가 없었다(init 을 또 씀). 마이그레이션 디렉터리를 읽는 다른 도구(Atlas 의 `atlas.sum` 등)도
  그 파일을 이력에 넣는다
- 무엇: `db/migration`(분리 도메인은 `db/{domain}/migration`) 바로 아래에 Flyway 가 읽는 마이그레이션(`V`·`U`·`R`·`B` 로
  시작하고 `__` 가 있는 `.sql`)이 있으면 프로젝트 자기 이력이다. 하위 폴더(Flyway 의 `common/` 과 `{vendor}`)는 세지 않는다 -
  프로젝트의 `locations` 가 이름을 댈 때만 읽히므로 기본 위치를 쓴다는 표시가 아니다
  - DB 가 하나이고 상태 파일이 없음: baseline 없이는 마이그레이션도 상태도 쓰지 않고 실패(이유와 `-PddlBaseline` 을 말함).
    `-PddlBaseline` 이면 상태만 지금 모델로 쓰고 외래키·인덱스 이름을 `takenOver` 로 적는다(`db/migration/{db}/` 의 이력과 같은
    `startFromTheModel` - `ddl-baseline-names`). 다음 변경은 `db/migration/{db}/` 에 - 기본 위치가 그 폴더도 읽으므로
    `locations` 를 바꾸지 않아도 된다
  - DB 가 둘 이상: 상태 파일이 있든 없든 실패 - 기본 위치는 아래 폴더를 모두 읽어 한 DB 에 다른 DB 의 마이그레이션을 실행한다
    (baseline 뒤에 둘째 DB 를 더하면 그 `init` 이 첫 DB 의 Flyway 가 읽는 자리에 생겼다). 이력을 DB 별 폴더로 옮기고
    `locations` 를 `{vendor}` 로 하라고 말한다
  - 이력이 `db/migration/{db}/` 에 있는 경우는 전과 같다
- 확인: `DdlMigrationTest.aHistoryOfTheProjectsOwnInTheDefaultLocationIsNotWrittenOver`(거절 → baseline 으로 상태만 →
  모델 변경이 `migration/h2/` 의 마이그레이션 하나), `aHistoryInTheDefaultLocationIsNotForSeveralDatabases` - 둘 다 고치기 전
  코드에서 실패했다(init 을 쓰고 성공). `aBaselineOverTheDefaultLocationTakesTheNamesOverAndASecondDatabaseIsRefusedLater`(이름을
  넘겨받아 관계를 빼면 검토, baseline 뒤 둘째 DB 는 거절), `aHistoryOfTheProjectsOwnIsAnyMigrationFlywayReadsOutsideTheDatabasesFolders`.
  Flyway 가 위치 아래 폴더를 읽는 것은 Flyway 10.20.1 의 classpath·파일 스캐너로 확인했다. 도메인 9개짜리 프로젝트
  (`db/migration/V1__baseline.sql`~`V19`, `classpath:db/migration`)에서 고치기 전에는 `-PddlBaseline` 을 줘도
  `db/migration/postgresql/V…__init.sql`(테이블 54개)이 생겼고, 고친 뒤에는 `elcore`(→ `elcoreDdl`)가 마이그레이션 없이 멈추고,
  `-PddlBaseline` 이 `schema-state.json` 만 쓰며, 다시 돌리면 아무것도 쓰지 않는다
- 관련 파일: `DdlRun.java`(`write`, `startFromTheModel`, `hasOwnHistory`), `DdlMigrationTest.java`, `elasticore-env-guide.md`(9-1 절 - `locations` 와 이력을 깨는 빌드 표)

### Bug Fixes (프로젝트 DDL - 넘겨받은 이름은 넘겨받은 DB 에만)

#### baseline 뒤에 더한 DB 의 마이그레이션은 넘겨받은 이름 때문에 기다리지 않는다
- 왜: `-PddlBaseline` 이 적는 `takenOver`(`ddl-baseline-names`)는 모든 DB 에 하나였다. baseline 뒤에 `databases` 에 더한 DB 는
  `init` 이 모델의 이름으로 만드므로 이름이 맞는데도, 그 DB 의 마이그레이션에서 그 이름의 외래키·인덱스를 지우는 문장이 검토
  주석이 되었고, 나중에 더한 SQL Server 는 그 이름 위의 컬럼 변경까지 기다렸다 - 주석을 풀지 않으면 그 DB 의 스키마가 모델과
  어긋난 채 남는다
- 무엇: baseline 이 쓴 상태는 이름을 넘겨받은 DB 들을 `takenOverOn` 으로 함께 적고, 그 이름 때문에 기다리는 문장은 그 DB 의
  마이그레이션에서만 검토로 쓴다(나머지 DB 는 실행 문장, 빌드의 `REVIEW` 줄은 그 DB 의 폴더). 모든 DB 의 파일에 붙는 그
  문장의 설명은 어느 DB 에서 넘겨받은 이름인지 말한다(`- on h2, its name was taken over ...`). 모델에 그 이름이 남는 동안
  `takenOverOn` 도 남는다. `takenOverOn` 이 없는 상태는 모든 DB 에서 넘겨받은 것으로 읽고, `takenOver` 가 없는 상태 파일은
  전과 같다
- 함께: `db/migration` 바로 아래의 이력 때문에 여러 DB 빌드를 거절할 때(`ddl-flyway-own-history`) 찾은 파일 이름을 대고, 이력을
  이미 DB 별 폴더로 옮겨 Flyway 가 그 자리에서 더는 읽지 않는 남은 파일이면 옮기라고 말한다
- 확인: `DdlMigrationTest.aDatabaseAddedAfterTheBaselineHasTheModelsNames`(H2 이력 위 baseline 뒤 PostgreSQL 을 더하면 상태에
  `"takenOverOn" : [ "h2" ]`, 관계를 빼면 H2 는 검토이고 PostgreSQL 은 `ALTER TABLE visit DROP CONSTRAINT fk_visit_customer;`),
  `SchemaDiffTest.aNameTakenOverWaitsOnlyOnTheDatabasesItWasTakenOverFrom`(같은 이름으로 다시 만드는 인덱스는 H2 에서만 검토,
  SQL Server 의 컬럼 넓힘은 실행, JSON 을 오가도 같고 `keepTakenOver` 가 이어 받음), 거절 메시지의 파일 이름은
  `aBaselineOverTheDefaultLocationTakesTheNamesOverAndASecondDatabaseIsRefusedLater`
- 관련 파일: `Schema.java`(`takenOverOn`), `SchemaDiff.java`(`byName`, `takenOverOn`), `DdlRun.java`(`startFromTheModel`,
  `ownHistoryFile`), `DdlMigrationTest.java`, `SchemaDiffTest.java`, `elasticore-env-guide.md`(9-1 절)

### Bug Fixes (생성 파일 병합 - DSL 이 바꾸지 않은 파일의 사용자 블록)

#### 표시가 있는 파일을 DSL 이 바꾸지 않으면 첫 병합에서 사용자 블록을 옮기지 않는다
- 왜: 사용자 블록을 2.x 의 자리로 돌려놓는 첫 병합(`merge-stamped-custom-blocks`)은 고치지 않은 파일이면 DSL 이 그 파일을
  바꾸지 않았어도 새로 쓰며 블록을 옮겼다 - 클래스 중간에 둔 블록이 닫는 괄호 앞으로 갔다. 같은 경우의 고친 파일은 블록을 그
  자리에 두었고, 가이드는 어느 쪽도 그렇게 말하지 않았다
- 무엇: DSL 이 바꾸지 않은 파일은 고쳤든 아니든 표시만 떼고 블록을 그 자리에 둔다. 블록을 2.x 의 자리로 옮기는 것은 DSL 이
  바꾸어 새로 쓰는 파일뿐이다. Java·Kotlin 소스와 그 밖의 생성 파일 모두
- 확인: `GeneratedMergeTest.customBlocksOfAStampedFileTheDslDoesNotChangeStayWhereTheyStand`(클래스 중간에 블록을 둔 Java DTO 와
  `</resultMap>` 뒤에 블록을 둔 매퍼 XML - 첫 병합 뒤 파일이 표시 줄만 빠진 그대로)
- 관련 파일: `RulesGenRunner.java`(`writeMergedSource`, `writeMerged`), `GeneratedMergeTest.java`, `elasticore-env-guide.md`(9-2 절 - 표시가 있던 프로젝트의 표)

### Changes (기여 - 머지 전 게이트)

#### 바뀐 줄의 포맷·Error Prone·커버리지·mutation 을 CI 가 막는다
- 왜: 여러 사람과 여러 AI 가 같은 코드를 고친다. CI 는 테스트·2.x 호환은 막았지만, 새 코드가 테스트로 실행되는지, 그
  테스트가 실제로 무엇을 확인하는지, 포맷과 흔한 버그 패턴은 사람의 눈에 맡겼다.
- 무엇이 (`gradle/quality.gradle`, `./gradlew prCheck` 가 넷 다):
  - 모두 **변경이 더하거나 고친 줄**만 본다 - `origin/main` 과의 merge base 부터 작업 트리까지(커밋 전 변경·새 파일 포함).
    그 전 코드는 게이트 밖(전체를 재포맷하면 523 파일 3만 7천 줄이 바뀌고 열린 PR 마다 충돌했다).
  - `formatCheck`·`formatChanged`: 라이브러리·플러그인·도구의 바뀐 Java 줄만 palantir-java-format 으로(git clang-format 방식,
    `elasticore-quality-tools` 의 `FormatChangedLines`).
  - `diffErrorprone`: Error Prone 2.50 이 라이브러리 main 소스를 따로(JDK 21, 배포 클래스는 그대로) 컴파일 - ERROR 는 어디서든
    `build` 실패, WARNING 은 바뀐 줄에서 실패. 억제는 `@SuppressWarnings("Check") // 이유`.
  - `diffCoverage`: JaCoCo - 바뀐 라이브러리 줄의 80% 를 테스트가 실행. 런타임 라이브러리는 샘플 테스트가 실행한 줄도 셈.
  - `diffMutation`: PIT 1.22 - 바뀐 줄의 mutation 만(`elasticore-quality-tools` 의 PIT 기능 `CHANGEDLINES`) 돌려, 테스트가
    실행하는 줄의 mutation 70% 를 테스트가 잡아야 함.
  - 실패하면 PR 의 줄에 annotation, job summary 에 표, HTML 보고서는 artifact.
  - 아무것도 확인하지 않고 통과하는 길을 막음: CI 나 `-PdiffBase` 에서 base 를 못 찾으면 실패(`main` 은 `origin/main` 으로도
    찾음), 포매터가 깨지면(`DONE` 없음·예외) 실패, Error Prone 의 출력 캡처는 일부러 경고를 내는 canary 클래스로 확인,
    git 설정(`diff.noprefix`, `core.quotePath`)과 무관한 diff.
  - 소스 루트별로 셈: springboot3 의 `src/main/java` 는 springboot3·4 어느 테스트가 실행해도, `src/jackson2/java`(3)와
    `src/jackson3/java`(4)는 같은 이름의 파일이 있어 각 모듈 것만. Korean 서버 테스트(`koreanServerTest`)의 실행도 셈.
  - 큰 변경(300줄 초과)은 줄마다 mutation 1개(`perline`) - PR #52 크기(4993줄)로 CI 에서 9.5분. PIT 는 빌드가 병렬
    (`org.gradle.parallel`)이어도 한 번에 하나(build service) - 셋이 함께 돌면 32GB 작업 PC 의 메모리가 바닥났다.
- CI: 새 job `Format and static analysis of the change`·`Mutation testing of the change`, `Build, test and compatibility
  checks` 에 커버리지 단계. CONTRIBUTING.md 에 "The gates on the lines you write", PR 템플릿에 `prCheck`.
- 게이트가 찾은 기존 코드의 결함(ERROR 2건):
  - `DbmsServiceImpl`(런타임)이 `DbmsService` 애노테이션을 구현하면서 `equals`·`hashCode` 가 없었다 - 이제
    `Annotation` 의 계약대로(JVM 이 만든 같은 값의 애노테이션과 같고 해시도 같음), `toString` 도.
  - `ECoreModel.findModelByName(String)` 의 `modelName = modelName;`(자기 대입, 아무 일도 안 함)을 지움 - 동작 그대로.
    `ns:Model` 이름은 지금도 접두어째 찾는다(별도 확인 대상).
- 확인: 각 게이트를 실패시켜 봄 - 이 PR 의 포맷 안 된 줄·`PatternMatchingInstanceof` 를 게이트가 잡았고 고친 뒤 통과;
  PR #52 를 기준으로(수동 실행 `base`) 포맷 32 파일·Error Prone 51건으로 실패, mutation 74.8%(981개, 9.5분),
  로컬 `diffCoverage` 79.8%(샘플 테스트를 세기 전). 없는 base 는 실패. `DbmsServiceImplTest`, `elasticore-quality-tools`
  테스트 14(포매터의 종료 코드·오류, 인터셉터의 줄 필터·`perline`).
- 샘플 `AggregateFactoryTest` 는 JaCoCo 가 더하는 synthetic 메서드를 세지 않음.
- 관련 파일: `gradle/quality.gradle`(새), `build.gradle`, `settings.gradle`, `elasticore-project/elasticore-quality-tools/`(새),
  `.github/workflows/ci.yml`, `.github/PULL_REQUEST_TEMPLATE.md`, `CONTRIBUTING.md`,
  런타임 `dbms/meta/DbmsServiceImpl.java`, `ECoreModel.java`.

### Bug Fixes (생성 파일 병합 - 표시가 있는 파일의 사용자 블록)

#### 표시가 있는 파일의 사용자 블록은 병합하지 않고 2.x 의 자리로 돌려놓는다 - 2.x 에서 3.0 코드로 옮길 때 충돌하지 않는다
- 왜: 사용자 블록(`// !----------` ~ `// ------------!`)은 해시에서 빠진다 - 블록이 어디 있든 파일은 생성된 그대로로
  읽힌다. 병합하는 첫 실행은 그런 파일의 기준을 해시가 확인한 본문(블록 없음)으로, 디스크를 블록이 든 본문으로 잡았다.
  새 생성본에도 블록이 없으니 블록이 개발자 편집이 되어, 그 옆 줄을 DSL 이 바꾸면 같은 자리 충돌이 됐다. 2.x 에서 3.0
  코드로 옮기면 블록 옆(닫는 괄호, import, 빈 줄)이 거의 늘 바뀐다 - 도메인 9개짜리 2.x 프로젝트의 `-Pv3` 이행에서 사용자
  블록이 있는 파일 4개(모두 표시가 생성된 그대로)가 전부 `CONFLICT` 였고 빌드가 멈췄다. 병합하지 않는 실행은 블록을 새
  텍스트에 다시 넣으므로(`withCustomBlocks`) 이 일이 없었다
- 무엇: 표시가 있는 파일을 처음 병합할 때 사용자 블록은 해시처럼 병합에서도 뺀다 - 디스크 쪽도 블록을 뺀 본문으로 병합하고,
  쓸 때 블록을 2.x 가 다시 쓸 때의 자리(import 블록은 `package` 줄 아래, 나머지는 닫는 줄 앞)에 넣는다. 그래서 고치지 않은
  파일은 기준과 디스크가 같아 새 생성본에 블록을 넣은 것이 되고, 고친 파일은 고친 줄만 병합된다. Java·Kotlin 소스와 그 밖의
  생성 파일 모두. 캐시에는 전처럼 블록 없는 생성본을 두므로 다음 실행부터 블록은 다른 편집과 같은 개발자의 줄이다.
  개발자가 블록을 다른 자리(예: 클래스 중간)에 두었다면 2.x 가 다시 쓸 때처럼 그 자리로 옮겨진다
- 리뷰 뒤: `HashUtils` 는 import 블록을 마지막 하나만 기억하고, import 블록 위의 블록을 그 안에 넣는다 - import 블록이 둘이면
  첫 번째가 말없이 사라지고(`WROTE`), 코드 블록이 import 블록 위에 있으면 import 자리로 옮겨져 컴파일되지 않았다. 그래서 블록을
  온전히 돌려놓을 수 있는 파일(`blocksPutBackWhole` - import 블록이 많아야 하나이고 맨 앞, 닫히지 않은 블록이 없음 - 닫는 줄이
  틀린 블록은 `HashUtils` 에게 파일 끝까지라 닫는 중괄호가 블록과 함께 옮겨졌다)만 위처럼 병합하고, 나머지는 전처럼
  블록을 고친 내용으로 그 자리에 둔 채 병합한다. 블록을 뺀 병합이 충돌하면 블록을 둔 채 다시 병합해 그 결과를 쓴다 - 끝 줄을
  덮는 충돌 표시의 한쪽에 블록이 들어가 "yours" 로 해결하면 지워지던 것. `KEPT(yours ...)` 와 `UNCHANGED` 는 파일을 블록이
  있던 그대로 둔다
- 확인: `GeneratedMergeTest.theCustomBlocksOfATwoXFileComeIntoTheThreeZeroCodeWithoutAConflict`(2.x 가 쓴 DTO 의
  `package` 아래 import 블록과 첫 필드 뒤 블록 - 고치기 전 `CONFLICT` 로 실패, 고친 뒤 두 블록이 2.x 의 자리에 남고,
  이어진 모델 변경도 충돌 없이 병합). `customBlocksOfAStampedFileAreKept`(매퍼 XML)는 그대로 통과. 위 프로젝트의 `-Pv3`
  이행: `4 conflict(s)` → `0 conflict(s)`, 4개 파일이 블록 수 그대로 `WROTE`, 다시 돌리면 `0 written / 579 unchanged`.
  `blocksThatWouldNotComeBackWholeAreMergedAsEdits`, `twoBlocksOfImportsOfATwoXFileAreBothKeptOnTheMoveTo30`(import 블록 둘과
  코드 블록 - 셋 다 남고 말없는 `WROTE` 가 아님; 리뷰 전 코드에서는 `WROTE` 로 실패)
- 관련 파일: `RulesGenRunner.java`(`writeMergedSource`, `writeMerged`, `withBlocks`, `blocksPutBackWhole`), `GeneratedMergeTest.java`,
  `elasticore-env-guide.md`(9-2 절 - 표시가 있던 프로젝트의 표)

### Documentation (마이그레이션 가이드 - `withJoinFetches()` 가 생기는 서비스)

#### 2.x 는 모든 CoreService 에 `withJoinFetches()` 를 만든다 - v3 로 옮기면 연관이 없는 엔티티의 호출이 컴파일되지 않는다
- 왜: `@fetchjoin` 항목이 "2.x 와 같이 그 연관이 있는 엔티티에만 생성" 이라고 했다. 2.x 는 연관이 없어도 만든다 - 아무 조건도
  더하지 않는 빈 Specification(`return null`). 그래서 그런 엔티티의 확장 서비스가 `.and(withJoinFetches())` 를 부르는 2.x
  프로젝트는 v3 로 옮기면 컴파일되지 않는데, "2.x 코드에서 옮길 때 손볼 것" 표에 없었다. 개발 가이드(12장)는 이미 바르게 적고
  있었다
- 확인: 2.x 기준 트리 `elasticore-template` 의 CoreService 24 개 모두에 `withJoinFetches()` 가 있고, 연관이 없는
  `CouponCoreService` 는 빈 조건이다. 도메인 9개짜리 2.x 프로젝트(CoreService 53 개, 모두 있음)를 `-Pv3` 로 옮기자 v3
  CoreService 7 개에만 생겼고, 연관이 없는 엔티티의 확장 서비스 2 곳이 컴파일되지 않았다. v3 의 판정은
  `JpaServiceFileRules.joinFetchVars`(가져올 연관이 없으면 메서드를 쓰지 않음)
- `elasticore-migration-guide.md`: `@fetchjoin` 항목의 문장을 바로잡고, "손볼 것" 표에 `withJoinFetches()` 줄을 더했다 -
  2.x 에서도 아무것도 하지 않던 호출이므로 지운다
- 생성물은 그대로다
- 관련 파일: `elasticore-migration-guide.md`

### Bug Fixes (MyBatis - 고유값 충돌의 답)

#### MyBatis 도메인에서 이미 쓰인 고유값이 JPA 와 같은 409 `unique` 로 답한다
- 왜: 같은 모델의 회원가입에서 이미 있는 로그인(유일 인덱스)을 보내면 JPA 는 409 `code: unique`("A unique value is already
  taken."), MyBatis 는 409 `code: already-exists`(드라이버 메시지 그대로)였다. Spring 의 JDBC 변환기가 MyBatis 의 고유 위반을
  `DuplicateKeyException` 으로 바꾸고, 생성된 `<Domain>ApiErrors` 는 그것을 "생성할 때 이미 있는 식별자"(서비스가 던지는 것)로만
  읽었다. 에이전트 킷의 계약 테스트가 shop 레시피를 MyBatis·헥사고날로 돌리다 찾았다.
- 무엇이: `DuplicateKeyException` 의 원인에 DB 의 `SQLException` 이 있으면 `DataIntegrityViolationException` 과 같은 답(SQLSTATE 로
  `unique`, 모르는 종류면 `conflict`) - 드라이버의 글(SQL 이 든)은 나가지 않음. 원인이 없으면(서비스가 던진 것) 그대로
  `already-exists`. 동시에 같은 키로 만들어 DB 의 기본키가 거절한 것은 이제 `unique`. Java·Kotlin 템플릿.
  `<Domain>ApiErrors` 는 한 번만 생성하는 파일 - 고치지 않은 프로젝트는 다음 생성에서 받고, 고친 프로젝트는 병합으로 받는다.
- 확인: `elasticore-sample-petstore-mybatis` 의 `ShopFlow.aTakenLoginIsAConflict`(코드 `unique`, 글에 SQL 없음)(H2, CI 에서 Oracle·PostgreSQL·
  SQL Server) - 고치기 전 `already-exists` 로 실패. 에이전트 킷 계약(shop on MyBatis) S2.
- 관련 파일: `elasticore-template/v3/apiErrors.tmpl`, `elasticore-template/kt/apiErrors.tmpl`, 샘플 `ShopFlow.java`.

### Bug Fixes (2.x 코드 - `ossie-export` 와 함께)

#### 2.x 코드를 지키는 빌드도 `rules = ['ossie-export']` 를 쓸 수 있다
- 왜: 3.0.261028-rc3 부터 `output = 'v2'`(와 `-Pv2`)는 프로젝트 규칙과 함께 쓰면 거절한다 - 그 규칙은 2.x 템플릿에
  그려지지 않기 때문이다. 그런데 `ossie-export` 도 프로젝트 규칙으로 세었다. 이 규칙은 코드를 쓰지 않는 시맨틱 모델 내보내기의
  켜기 신호이고(`RuleCatalog` 의 등록 주석, `OssieExportRules` 는 비어 있음), 2.x 생성기도 같은 신호로 내보낸다
  (`ModelExtractor` 가 `OssieSemanticModelExporter.requested` 를 읽음). 그래서 `./gradlew elcore` 로 2.x 코드와
  `semantic_model.yml` 을 만들던 프로젝트가 `output = 'v2'` 를 적으면 `./gradlew elcore` 가 태스크를 구성하다 실패했고
  (`ddl` 을 켰으면 어느 빌드든), `elcore3 -Pv2` 는 "which only the 3.0 code carries" 라며 멈췄다. 선택을 적지 못하니 `generateBeforeCompile` 을 켜면 빌드가 `elcore3` 에서
  멈추고, `ddl` 의 `elcore` → `elcoreDdl` 연결도 받을 수 없었다
- 무엇: 플러그인의 `output = 'v2'` 검사(`keepsTwoX`)와 규칙 엔진의 `-Pv2` 검사가 `ossie-export` 를 빼고 센다. 다른
  프로젝트 규칙은 전처럼 거절한다(`ossie-export` 와 함께 적어도, 여러 번 적어도). 생성물은 그대로다 - 2.x 호환 번들에
  더해지는 `OssieExportRules` 는 아무것도 쓰지 않는다
- 멀티모듈(`modules`, 또는 하위 프로젝트에 `blueprint/` 가 있는 빌드)의 2.x 생성기(`ModelExtractor.extractMultiModule`)는
  시맨틱 모델을 내보내지 않았다 - 이 변경으로 거절이 풀리면 `semantic_model.yml` 없이, 아무 말 없이 지나갔다(리뷰에서 찾음).
  이제 단일 모듈과 같은 조건(`exportOssieIfRequested`)으로 첫 모듈의 `blueprint/`, 그 `schema.yml` 옆에 쓴다
- 확인: `DefaultGenerationTest.v2KeepsTheOssieExport`(`-Pv2` + `ossie-export` → 2.x 호환 코드와 `semantic_model.yml`),
  `ElastiCorePluginTest.outputV2KeepsTheOssieExport`(`output = 'v2'` + `ossie-export` → `:elcore`, 규칙 전달; 프로젝트
  규칙이 더해지면 거절) - 둘 다 고치기 전 코드에서 실패했다. `v2ExportsTheOssieModelOfAMultiModuleBuild`(모듈 둘, 두 도메인이
  든 `semantic_model.yml` 이 첫 모듈에) - 고치기 전 `extractMultiModule` 에서 실패. `ossieExportIsNoRuleSetOfTheCodeHoweverOftenItIsNamed`. `v2IsNotTheProjectsOwnRules` 는 프로젝트 규칙의 예로
  `ossie-export` 를 쓰고 있어, 규칙 개발 가이드의 예(`com.acme.rules.AcmeRules`)로 바꿨다. 도메인 9개짜리 2.x 프로젝트
  (`rules = ['ossie-export']`)에서 `output = 'v2'` 를 적고 `elcore`·`elcore3 -Pv2` 를 돌려 확인했다
- 관련 파일: `ElastiCorePlugin.java`(`keepsTwoX`), `RulesGenRunner.java`(`addedCodeRules`, `-Pv2` 검사), `ModelExtractor.java`(`exportOssieIfRequested`),
  `DefaultGenerationTest.java`, `ElastiCorePluginTest.java`, `elasticore-rule-guide.md`(어느 세대의 코드인가 표, Ossie 켜는 법)

### Bug Fixes (Ossie 내보내기 - `unique_keys` 의 컬럼 이름)

#### `@Index` 의 `columnList` 에 속성 이름을 적어도 `unique_keys` 는 컬럼 이름으로
- 왜: `@Index(columnList="company_id,seqCd", unique="true")` 처럼 속성 이름을 적은 모델에서 `unique_keys` 가
  `[company_id, seqCd]` 로 나왔다. 데이터셋의 필드는 컬럼 이름(`seq_cd`)이라 키가 없는 필드를 가리켰다. Hibernate 는
  `columnList` 의 이름을 논리 이름으로 읽어 같은 이름 규칙으로 컬럼을 찾고(`seq_cd`), 프로젝트 DDL(`JpaSchemaReader`)도
  그렇게 읽는다 - 내보내기만 글자 그대로 옮겼다
- 무엇: `uniqueKeys` 가 `columnList` 의 이름마다 데이터셋의 필드를 찾는다(`keyColumn`) - 속성 이름이면 그 필드의 컬럼(`columnOf`,
  `@column` 이 있으면 그 이름), 컬럼 이름이면 대소문자와 상관없이 그 컬럼을 가진 필드의 것(`@column(HALL_ID)` 의 `HALL_ID` 를
  `hall_id` 로 낮추지 않음 - 리뷰에서 찾은 회귀), 어느 필드도 아니면 Hibernate 의 이름 규칙(`camelToSnake`). `@unique` 필드도
  데이터셋 필드와 같은 `columnOf` 로 - 그래서 `@unique` 와 같은 컬럼의 유일 `@Index` 는 키 하나
- 확인: `OssieUniqueKeysTest`(`columnList="hall_id,seatNo"` → `[hall_id, seat_no]`, 두 이름이 모두 데이터셋의 필드) - 고치기
  전 코드에서 둘 다 실패했다. `@column(BOX_ROW)`·`@column(BOX_NO) @unique` 와 `columnList="BOX_ROW,boxNo"`·`"box_no"` →
  `[BOX_ROW, BOX_NO]` 와 `[BOX_NO]` 한 번(`aColumnWrittenByColumnKeepsItsCaseAndAKeyIsWrittenOnce`) - 소문자로 바꾸던 코드에서 실패. 커밋된 샘플 둘(petstore·template-ddd)의 `semantic_model.yml` 은 그대로다 - 샘플의 `columnList` 는
  모두 컬럼 이름이다. 도메인 9개짜리 프로젝트에서 `idx_sequence_info_u(company_id,seqCd)` 의 Hibernate DDL 과 프로젝트 DDL 이
  모두 `seq_cd` 인 것을 확인했다
- 관련 파일: `OssieSemanticModelExporter.java`(`uniqueKeys`), `OssieUniqueKeysTest.java`, `elasticore-rule-guide.md`(Ossie 대응 표)

### Bug Fixes (v3 규칙 엔진 - 남은 클래스를 쓰는 파일의 경로)

#### "still uses it" 줄이 개발자 파일을 프로젝트 안의 경로로 말한다 - 링크나 다른 이름으로 받은 프로젝트에서도
- 왜: DSL 이 더 만들지 않는 생성 클래스를 개발자 코드가 쓰면 `... is no longer generated, but <파일> still uses it - kept`
  라고 알린다. 그 파일은 소스를 정규 경로(`getCanonicalFile`)로 읽어 찾는데, 줄에 쓸 때는 정규화하지 않은 프로젝트 경로를
  앞에서 떼어 냈다. 프로젝트 경로가 정규 경로와 다르면 - 링크를 거쳤거나, macOS 의 `/var`(실제 `/private/var`), Windows 의
  8.3 짧은 이름(`C:\Users\DEVELO~1\...`) - 절대 경로가 그대로 찍혔다. 그래서 `GeneratedMergeTest.aClassNoLongerProducedStaysWhileACodeOfTheDevelopersUsesIt`
  는 임시 폴더가 짧은 이름인 Windows 에서 실패했다(리눅스 CI 에서는 같은 경로라 통과, macOS 의 `/var` 는 같은 이유로 보이나
  돌려 보지 않았다)
- 무엇: `relative` 가 그대로 떼어지지 않으면 파일과 프로젝트 경로를 둘 다 정규화해 다시 뗀다. 화면에 찍는 줄만 바뀐다 -
  캐시 키는 이 경로를 쓰지 않는다
- 확인: 새 `GeneratedMergeTest.theCodeThatKeepsAClassIsNamedInTheProjectAlsoThroughALink`(심볼릭 링크로 받은 프로젝트 -
  링크를 만들 수 없는 곳에서는 건너뜀)가 리눅스(`eclipse-temurin:21-jdk`)에서 고치기 전 그 줄의 단정으로 실패하고 고친 뒤
  통과한다. Windows(임시 폴더가 8.3 짧은 이름)에서는 위의 기존 테스트가 고치기 전 실패, 고친 뒤 통과(링크 테스트는 권한이
  없어 건너뜀)
- 관련 파일: `RulesGenRunner.java`(`relative`), `GeneratedMergeTest.java`

## 3.0.261030-rc6

`mode: mybatis` 도메인이 `architecture = 'ddd'`·`'hexagonal'` 로도 생성된다 - JPA 가 애그리거트에 하던 일(새 행 판단, 버전,
멤버 비교, 1:1 멤버, 생명주기 콜백, 이벤트, 감사)을 생성되는 저장소 어댑터가 한다. MyBatis 도메인의 dbms 포트는 MyBatis
매퍼와 어댑터가 되고, repository.yml 쿼리·`@abstract` 부모·쓴 사람도 MyBatis 로 생성된다. 포트 페이지의 count 는 마지막
`ORDER BY` 를 빼고 센다(JPA 프로젝트도).

### New Features (MyBatis - 도메인 모델·헥사고날)

#### `architecture = 'ddd'`·`'hexagonal'` + `mode: mybatis` 를 생성한다
- 왜: rc5 까지 MyBatis 는 레이어드 Java 만 썼고, DDD·헥사고날은 `RulesConfig.mybatisRefusal` 이 빌드를 멈췄다
  ("the domain model and the hexagon rest on JPA's change tracking and lifecycle callbacks"). MyBatis 를 쓰는 팀이
  헥사고날·DDD 구조를 원했다.
- 무엇이: 묶음 `ddd-mybatis-springboot`(= `mybatis-springboot` + DDD 룰셋 + `mybatis-ddd`)와
  `hexagonal-mybatis-springboot`(+ `hex-usecase`). 빌드의 `architecture` 와 도메인의 `mode` 로 저절로 고르고, 도메인
  env.yml 의 `publisher.rules` 에 적은 `ddd-springboot`·`hexagonal-springboot`·`jpa-springboot-v3` 는 MyBatis 판으로 읽는다.
  Kotlin 은 여전히 거절(MyBatis 코드는 Java).
- JPA 가 애그리거트에 스스로 하던 일을 생성되는 저장소 어댑터(`<Root>RepositoryAdapter`, `ddd/DddMyBatisPorts`)가 한다:
  - 새 행 판단(버전 또는 키가 없으면 INSERT), UPDATE 의 버전 비교·증가(낡으면 409, 메모리의 버전도 올림 - 루트도 멤버도,
    새 멤버는 행이 받은 0);
  - 멤버 컬렉션은 저장된 행과 비교 - 빠진 것 DELETE, 남은 것 UPDATE(멤버 키 유지), 새것 INSERT;
  - to-one 멤버(`@OneToOne @cascade`)는 루트보다 먼저 쓰고, 바뀌거나 빠진 이전 멤버를 지움 - 저장된 행은 `findStoredById`
    (`flushCache="true"`)로 읽음: 트랜잭션 안에서 MyBatis 세션 캐시는 저장 중인 바로 그 객체를 돌려줘 이전 멤버를 못 찾았다;
  - `delete()` 는 낡은 버전이면 아무것도 지우기 전에 409(`lockVersion` 이 행을 잠금), 멤버 컬렉션·행·to-one 멤버(저장된
    것과 메모리에 든 것) DELETE 뒤 이벤트 발행.
    도메인 규칙 루트의 파생 `deleteBy...` 는 행을 읽어 하나씩 `delete()`(삭제 가드·멤버 - Spring Data JPA 처럼);
  - 같은 트랜잭션에서 다시 읽은 행(세션이 주는 같은 객체)은 컬렉션을 다시 채우지 않음(런타임 `mybatis/LoadedRows`) -
    메모리의 변경을 덮지 않게;
  - `@PrePersist`·`@PreUpdate`(불변식 리스너)·`@PreRemove`·`@PostLoad`·`@PostPersist` 와 `@DomainEvents` 는 루트가
    구현하는 런타임의 `io.elasticore.springboot3.domain.Lifecycle` 로 옮김(`mybatis/MyBatisDddRules` 가 도메인 규칙이 쓴
    JPA·Spring Data 애노테이션을 지우고 그 메서드를 Lifecycle 에서 부름) - 어댑터가 같은 순간에 부르고, 저장 뒤
    이벤트를 `ApplicationEventPublisher` 로 발행;
  - 감사 필드(시각·`AuditorAware` 의 사람)를 저장 때 넣음 - 멤버가 상속한 것도, 멤버만 가진 것도;
  - `EntityManager.find` 대신 어댑터가 `Lookup.Source` - 런타임의 `ElastiCoreLookupAutoConfiguration` 이 `Lookup` 빈을 만듦
    (참조 검사·`@snapshot`). 어떤 어댑터도 찾지 않는 타입(JPA 도메인의 루트)은 앱의 `EntityManager` 로;
  - 읽기는 조인 한 단계 + 컬렉션은 페이지 단위로 한 문장씩, 읽은 뒤 `afterLoad`.
- 애플리케이션 서비스(`v3/mybatis_port_service.tmpl`)는 포트만 받음 - 도메인 포트, 검색 포트(검색 DTO 를 받음 -
  `findAll(SrchDTO, owner, Pageable)`, `narrows`), `Lookup`. 쓴 뒤에는 포트로 다시 읽어 답함.
- 바운드된 애그리거트의 멤버는 `@service` 를 적어도 자기 서비스·컨트롤러가 없고 루트가 씀 - `@aggregate(X)` 도, 모델이
  멤버로 만든 것(`@cascade`, `@root` 의 `@OneToMany(mappedBy)`)도(`MyBatisSql.aggregateBoundaries` 가 도메인 규칙과 같은
  `SpecFactory.aggregateOfModel` 로 받음). 멤버 매퍼는 헬퍼에 남고 루트의 어댑터만 부름.
- `publisher.rules` 로 `ddd-*`·`hex-*` 룰셋을 직접 고르면서 `mybatis-ddd` 를 빠뜨리면 거절(JPA 콜백이 죽은 애노테이션으로 남아
  불변식·생명주기가 돌지 않았다).
- `Rows` 의 필드 캐시는 `ClassValue` - 클래스 이름으로 잡아 DevTools 재시작 뒤 옛 클래스의 `Field` 를 썼다(모든 읽기·쓰기 실패).
- 아웃박스(`outbox: true`): 메시지 POJO + MyBatis 매퍼(애노테이션 SQL) + 변경을 직접 쓰는 릴레이. 대기 행 잠금은 DB 마다
  - PostgreSQL·Oracle `FOR UPDATE SKIP LOCKED`, SQL Server `UPDLOCK, READPAST`, H2 `FOR UPDATE`. 테이블은 프로젝트 DDL 에.
- 확인: `MyBatisGenerationTest.aHexagonOverMyBatisIsItsPortsAndAnAdapterThatDoesWhatJpaDid`, `PersistenceAxisTest`;
  새 모듈 `elasticore-template-ddd-mybatis` 가 `elasticore-template-ddd` 의 모델(shop 헥사고날, billing ddd, catalog
  레이어드)과 테스트 전부를 MyBatis 로 옮겨 돌림 - 경계, 불변식, 멤버 비교, 낡은 버전, 1:1 멤버, 이벤트·컨텍스트 간 이벤트,
  아웃박스·프로세스 매니저, 선언 쿼리, dbms 포트, 아키텍처 테스트, 모든 값 타입 - 108/108(H2). `DomainOnDatabase` 를
  CI 의 Oracle·PostgreSQL·SQL Server 에서도 돌림(dbTest) - 1:1 멤버 교체·해제, 낡은 삭제, 한 트랜잭션의 두 번 저장,
  파생 쿼리(`%` 그대로, 빈 `In`, `findAllByOrderBy...`, 하나씩 지우는 `deleteBy`), 순서 없는 포트 페이지·NULL 값 포함.
  `MyBatisGenerationTest.membersTheModelMakesAreWrittenByTheirRoot`·`domainRuleSetsWithoutMyBatisDddAreRefused`.
- 관련 파일: `RuleCatalog.java`, `RulesConfig.java`, `RulesGenRunner.java`, `ddd/DddMyBatisPorts.java`(새),
  `ddd/DddRepositoryPortRules.java`, `ddd/DddReferenceRules.java`, `ddd/DddAggregateRules.java`, `ddd/DddOutboxRules.java`,
  `mybatis/MyBatisDddRules.java`(새), `mybatis/MyBatisSql.java`, `SourceCode.java`(`rewriteVarLines`, `entityListeners`),
  `v3/mybatis_port_service.tmpl`·`mybatis_repositoryAdapter.tmpl`·`mybatis_outbox*.tmpl`(새),
  런타임 `domain/Lifecycle.java`·`mybatis/Rows.java`·`ElastiCoreLookupAutoConfiguration.java`(새), `domain/Lookup.java`.

### New Features (MyBatis - 모든 아키텍처)

#### `@abstract` 부모, 쓴 사람(`@CreatedBy`·`@LastModifiedBy`), repository.yml 쿼리, 1:1 의 반대편
- `@extend(AuditEntity)` 처럼 `@abstract` 부모의 필드가 행의 컬럼이 됨(`MyBatisSql.fields`) - 이전엔 상속 전체를 거절.
  `@abstract` 가 아닌 부모(테이블 상속)는 계속 거절.
- `@jpa:CreatedBy`·`@jpa:LastModifiedBy`: 서비스(레이어드)·어댑터(DDD)가 앱의 `AuditorAware` 빈의 사람을 넣음 - 이전엔 거절.
- repository.yml 의 메서드가 MyBatis 매퍼에 생김(`mybatis/MyBatisQueries`) - 이전엔 MyBatis 도메인에서 **말없이 빠졌다**.
  이름으로 읽는 쿼리(Spring Data 의 이름 규칙 - 조건·OrderBy·First/Top·페이지), `nativeQuery: true` SQL(엔티티는 키로 다시
  읽어 조인한 행까지, DTO 는 별칭으로, 쓰기는 바뀐 행 수). JPQL 은 이유를 밝혀 거절.
- `@OneToOne(mappedBy=...)` 쪽은 상대 행의 키 컬럼으로 조인해 읽기만 함 - 이전엔 이쪽에도 키 컬럼을 만들고 null 을 썼다.
- 이름으로 읽는 쿼리: `Containing`·`StartingWith`·`EndingWith` 는 값의 `%`·`_` 를 글자 그대로(`Rows.like` + `ESCAPE '!'`,
  SQL Server 는 `[` 도), 빈 목록의 `In`/`NotIn` 은 `1 = 0`/`1 = 1`(`IN ()` 는 어느 DB 도 읽지 않음), `findAllByOrderByX`
  (By 바로 뒤 OrderBy - 이전엔 빌드가 멈춤), `Stream` 과 조건 없는 `deleteBy`·`existsBy` 는 거절.
- `@audited` 는 거절(행의 이력은 Envers 의 것). 레이어드에서 루트가 to-one 관계로 갖는 서비스 없는 멤버는 거절(이전엔 말없이
  키만 null 로 씀).
- `biginteger` 가 `NUMERIC(38,0)` - 이전엔 BIGINT 라 큰 값이 넘쳐 409.
- 확인: `MyBatisGenerationTest`(추상 부모·쓴 사람, 선언 쿼리, JPQL 거절, 구체 부모 거절), 샘플의 `DeclaredQueryTest`·
  `ApiContractTest`(감사)·`TypeRoundTripTest`.

#### MyBatis 도메인의 dbms 포트를 MyBatis 매퍼와 어댑터로 생성한다
- 왜: 포트는 런타임 프록시가 JPA `EntityManager` 로 SQL 을 실행했다 - JPA 없는 MyBatis 앱에서는 실행기 빈조차 만들 수 없었다.
  MyBatis 를 쓰는 팀은 SQL 을 매퍼 XML 에서 보고 고친다.
- 무엇이: `mybatis/MyBatisPortRules` - 포트 SQL 을 담은 `<Port>Mapper`(+ XML)와 포트를 구현하는 `<Port>Adapter`(빈 - 런타임
  프록시는 비켜섬). SQL 은 DSL 그대로, `:name`→`#{}`, `/* if:name */` 줄·블록→`<if>`, 페이지는 count 와 한 페이지, 정렬
  코드는 런타임과 같이 검사해 ORDER BY 로. 결과는 런타임과 같은 규칙(`PortRows` - 런타임 게이트웨이와 같은 변환).
  조건에 JEXL 식을 쓴 포트는 거절.
- 런타임: `DbTransactionGateway` 를 `SqlPortGateway`(SQL 처리·결과 변환)와 JPA 실행으로 나누고, JPA 없는 앱을 위한
  `JdbcDbmsGateway` 를 더함(`@EnableElastiCore` 는 `DbmsGatewayRegistrar` 로, 자동 구성은 `EntityManagerFactory` 가 없을 때).
  페이지 count 의 `AS X` 를 `X` 로 - Oracle 은 파생 테이블 별칭에 `AS` 를 받지 않는다.
- 페이지 count 는 SQL 의 마지막 `ORDER BY`(괄호·문자열·주석 밖)를 빼고 셈 - SQL Server 는 파생 테이블 안의 `ORDER BY` 를
  거절한다(CI 의 SQL Server 에서 실패). JPA·JDBC 런타임(`SqlPortGateway.withoutOrderBy`)도 같아 JPA 프로젝트의 포트도
  바뀜: 이전엔 `ORDER BY` 째 셌다(SQL Server 에서 실패, 다른 DB 는 같은 수). 뒤에 `LIMIT`·`OFFSET`·`FETCH` 가 있으면 그대로
  둔다(그 행들만 센다). MyBatis 판은 `ORDER BY` 가 `/* if */` 블록 안이면 블록을 닫아 둔다.
- 순서 없는 SQL 의 페이지는 SQL Server 에서 `ORDER BY (SELECT NULL)`, 값 하나씩의 목록·페이지는 그 값 타입으로 읽어 첫 컬럼
  (NULL 은 null - 이전엔 맵으로 읽어 NULL 컬럼이 빠지면 다음 컬럼을 줬다), 모든 컬럼이 NULL 인 행(MyBatis 는 null 로 줌)은
  필드가 모두 null 인 DTO, `void` 메서드는 문장만 실행.
- 확인: `MyBatisGenerationTest.aDatabasePortOfAMyBatisDomainIsAMapperAndAnAdapter`·`aPortConditionThatIsAnExpression...`,
  샘플 `HexagonalPortTest`(조건부 SQL, 페이지, 쓰기, 어댑터가 빈), 런타임 `JdbcDbmsGatewayTest`, 기존 `DbTransactionGateway*Test`
  그대로 통과, JPA 샘플(`elasticore-template-ddd` 93/93, 펫스토어) 그대로.
- 관련 파일: `PortSpec.java`(`sql`, `mode`), `SpecFactory.java`, `mybatis/MyBatisPortRules.java`(새),
  `v3/mybatis_port{Mapper,Xml,Adapter}.tmpl`(새), 런타임 `bean/SqlPortGateway.java`·`JdbcDbmsGateway.java`·
  `DbmsGatewayRegistrar.java`·`PortRows.java`(새), `DbTransactionGateway.java`, `EnableElastiCore.java`,
  `ElastiCoreAutoConfiguration.java`.

- 2.x 출력 그대로: `elcoreRulesCheck -Pstrict` 157/0/2, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.

## 3.0.261030-rc5

3.0 코드의 생성 파일은 이제 첫 줄 `//ecd:` 없이 쓰이고, 고친 내용을 지키면서 DSL 의 변경을 병합한다(`.elasticore/cache`,
`elasticore { merge = false }` 로 끔) - Java·Kotlin 까지, 한 번만 생성하는 파일은 고치기 전까지 DSL 을 따라가고, 2.x 에서
올라오는 프로젝트의 고친 파일도 넘겨받는다. `mode: mybatis` 도메인은 같은 모델을 MyBatis 로 생성하고(펫스토어, Oracle·
PostgreSQL·SQL Server), `elasticore { ddl {} }` 은 프로젝트의 DDL 과 Flyway 마이그레이션을 쓴다. 런타임 라이브러리는
`spring-boot-starter-data-jpa` 를 더는 끌어오지 않는다 - JPA 프로젝트는 스타터를 직접 선언한다. 2.x 생성기의 출력은 그대로다.

### New Features (v3 규칙 엔진 - DDL)

#### `elasticore { ddl {} }` - 프로젝트의 테이블을 DB 별 DDL 로, 모델의 변화를 Flyway 마이그레이션으로
- 왜: 운영 DB 는 `ddl-auto` 로 만들지 않는다. 엔티티를 바꿀 때마다 무엇을 ALTER 해야 하는지 사람이 찾아 DB 마다 다른 문법으로
  써 왔고, MyBatis 도메인은 H2 용 스키마 하나뿐이었다.
- 무엇:
  - 설정은 프로젝트 한 번 - `build.gradle` 의 `elasticore { ddl { databases = [...]; migration = 'flyway' } }`(플러그인이
    `elasticore.ddl.*` 로 넘김). 도메인은 `env.yml` 의 `ddl: false`, `ddl.databases`, `ddl.migration`, `ddl.separate: true`
    로 필요한 것만 덮어쓴다(별도 DB 는 `db/{domain}/` 아래 따로).
  - `db/schema-{h2|postgresql|oracle|sqlserver}.sql`: 모든 도메인의 테이블·외래키·인덱스·시퀀스·스키마, 그 DB 의 Hibernate
    방언이 고르는 타입으로. 매 빌드 다시 생성.
  - `migration = 'flyway'`: `db/migration/{db}/V{시각}__init.sql`(그 DB 이력의 시작)과, 모델이 바뀌면
    `V{시각}__{무엇}.sql`(바뀐 만큼), 그리고 `db/migration/schema-state.json`(마이그레이션이 만든 스키마). 더하거나 넓히는
    것(테이블·선택 컬럼·길이·외래키·인덱스·스키마)은 실행되는 문장으로, 데이터를 잃거나 기존 행에서 실패할 수 있는 것(삭제·
    타입 변경·줄임·NOT NULL·키 변경)은 파일 끝의 **검토** 주석과 빌드 로그의 `REVIEW` 로. 필수 컬럼 추가는 빈 채로 추가하고
    NOT NULL 을 검토로. 마이그레이션은 한 번 쓰면 다시 쓰지 않는다. SQL Server 의 ALTER COLUMN 은 그 컬럼의 인덱스·외래키를
    지웠다 다시 만든다. 이력을 깨는 실행은 그 이력의 마이그레이션과 상태를 쓰지 않고 실패한다: 상태 파일 없이 마이그레이션만
    남은 경우, 상태 파일을 읽을 수 없는 경우(병합 충돌), 목록에서 빠졌던 DB 가 다시 들어온 경우(그 DB 이력을 손으로 맞춘 뒤
    상태에 추가), 이번 버전의 파일이 이미 있는 경우. 직접 쓴 Flyway 이력이 있는 프로젝트는 한 번 `-PddlBaseline` 으로 상태만
    지금 모델로 시작한다. 상태 파일은 모든 마이그레이션을 쓴 뒤 임시 파일에서 옮겨 갱신한다. 목록에서 빠진 DB 는 경고 - 그
    이력은 거기서 멈춘다.
  - JPA 도메인의 테이블은 생성된 엔티티 소스(JavaParser, Kotlin 은 줄 단위)와 `mapping: xml` 의 `orm.xml` 에서 읽는다 -
    Hibernate 가 보는 그대로: Spring Boot 의 이름 규칙, Hibernate 6 의 타입, `@Size`·`@NotNull` 의 DDL 반영, 임베디드·
    `@AttributeOverrides`, `@IdClass`, 다대다·조인 테이블(양방향이면 소유 쪽 컬럼은 반대편 속성 이름, Set 만 기본 키),
    일대일 소유 쪽 조인 컬럼의 유일 인덱스, `@OneToMany @JoinColumn` 의 외래키, `@ElementCollection`(`{엔티티}_{속성}`,
    값 컬럼은 Hibernate 처럼 nullable, 모두 필수인 Set 만 전 컬럼 기본 키, `@OrderColumn`; Map 은 경고 후 생략), 다른
    스키마 엔티티의 조인 테이블은 기본 스키마에, 상속(SINGLE_TABLE·JOINED·TABLE_PER_CLASS), 변환기의 컬럼 타입,
    `@JdbcTypeCode`(JSON·XML·LONG32VARCHAR), Envers 의 감사 테이블(JOINED 하위 클래스는 키·자기 컬럼·`rev` 만, SINGLE_TABLE
    은 루트 감사 테이블에 하위 컬럼과 `dtype`)·`revinfo`·`revinfo_seq`(application.yml 의 `audit_table_suffix` 등 반영),
    도메인 패키지 밖의 엔티티(outbox). `@NotBlank`·`@NotEmpty` 도 NOT NULL - Hibernate 6.6/7 의 Bean Validation DDL 이
    그렇게 한다. 2.x 코드를 지키는 프로젝트는 새 `elcoreDdl` 작업이 `elcore` 뒤에 쓴다.
  - MyBatis 도메인은 모델에서(MyBatisTables). 프로젝트 DDL 이 켜진 도메인은 `{domain}-schema.sql` 대신 프로젝트 파일에 든다.
    PR #44 의 도메인별 `-schema-{db}.sql` 은 이것으로 바뀌었다(미발표). 컬럼 이름이 Oracle·PostgreSQL·SQL Server 의 예약어면 경고.
  - DDL 을 켜지 않은 MyBatis 도메인의 `{domain}-schema.sql`(H2)도 같은 모델로 쓰여 바뀐다(3.0 미발표 출력, PR #43): 크기를
    말하지 않은 소수는 `DECIMAL(19,2)` 대신 Hibernate 와 같은 `DECIMAL(38,2)`, 유일 컬럼은 테이블 안 `UNIQUE (...)` 대신
    `CREATE UNIQUE INDEX uk_...`. 한 프로젝트의 JPA·MyBatis 도메인이 같은 타입을 쓰게 하려는 것이다.
  - 공통 모델(`rules/schema`): `Schema`·`Table`·`Column`·`ColumnType`, `Database`(DB 별 타입과 문장), `SchemaWriter`,
    `SchemaDiff`, `DdlConfig`.
- 확인:
  - JPA 샘플 넷이 생성된 `schema-h2.sql` 로 만든 테이블에서 Hibernate `ddl-auto=validate` 로 기동(`SchemaValidateTest`):
    2.x 템플릿(Hibernate 6.6 - 다대다·복합 키·JSON·`acct` 스키마·`_hist` Envers), template-ddd(`mapping: xml`·Envers·
    `columnDefinition TEXT`·outbox), template-kt(Kotlin), 펫스토어(Boot 4·Hibernate 7). 펫스토어는 CI 의 PostgreSQL·Oracle·
    SQL Server 에서도(`SchemaValidateOn*Test`).
  - `DdlMigrationTest`: 첫 실행·변경 없음·모델 변경의 세 번, DB 별 문장, 검토 주석, 마이그레이션을 차례로 H2 에 실행한 결과가
    마지막 모델의 테이블. 도메인의 `ddl: false`·`separate`. 이력을 깨는 실행의 거부. `JpaSchemaReaderTest`: 이름·타입·
    관계·상속별 Envers·컬렉션 테이블·Kotlin 규칙. `SchemaDiffTest`: 넓힘과 줄임(크기 없는 소수, Oracle 의 CLOB 경계), SQL
    Server 의 인덱스와 NOT NULL.
  - `MigrationTest`(template-mybatis): 넓힘(유일 인덱스가 걸린 컬럼 포함)·선택으로·새 컬럼·필터 유일 인덱스·새 테이블과
    외래키·인덱스 삭제를 H2 와 CI 의 세 DB 에서 실행.
  - MyBatis 펫스토어는 이제 Flyway 로 시작한다(`db/migration/{vendor}` + 반복 마이그레이션 `R__catalog.sql`) - H2 와 세 DB.
- 관련 파일: `rules/schema/*`, `verify/JpaSchemaReader.java`, `verify/DdlRun.java`, `verify/RulesGenRunner.java`,
  `mybatis/MyBatisTables.java`·`MyBatisSchemaRules.java`·`MyBatisSql.java`, `elasticore-gradle-plugin`(`DdlSpec`, `elcoreDdl`),
  `elasticore-env-guide.md`(2.3·9-1 절), 샘플 넷과 MyBatis 둘의 `build.gradle`·테스트, `.github/workflows/ci.yml`

### New Features (v3 규칙 엔진 - 생성 파일 병합)

#### 생성 파일 병합 - 고친 내용을 지키면서 DSL 을 따라간다 (3.0 코드 기본, `elasticore { merge = false }` 로 끔)
- 왜: 생성 파일을 고치면 그 파일은 `SKIP(user-modified)` 로 DSL 을 더 따라가지 않았다 - 고친 내용과 DSL 의 변경 중 하나를
  잃어야 했다. 첫 줄의 `//ecd:` 표시는 형식마다 주석 문법이 필요했고(XML `<!-- -->`, SQL `--`), properties 에서는 주석이
  아니라 `//ecd` 키였으며, 첫 줄이 정해진 형식(`<!DOCTYPE>`)과 맞지 않았다. `orm.xml` 과 `i18n/*-messages.properties` 는
  표시 없이 매번 통째로 덮어써 고친 내용이 사라졌다.
- 무엇:
  - Java·Kotlin 소스를 뺀 생성 파일 - MyBatis 매퍼 XML, `orm.xml`, `db/schema-{db}.sql`, `{domain}-schema.sql`,
    `*-http-services.properties`, `i18n/*-messages.properties`, TypeScript 클라이언트 - 은 표시 없이 쓰이고, 쓴 그대로가
    `.elasticore/cache` 에 남는다(프로젝트와 함께 커밋). 무압축 zip, 항목 순서와 시각 고정 - 같은 내용이면 같은 바이트라
    git 이 바뀐 만큼만 저장한다. 옆의 `.gitattributes` 가 binary 로 둔다.
  - 다음 생성은 파일마다 지난 생성본(캐시)·디스크의 파일·새 생성본을 견준다: 고친 것이 없으면 새로 쓰고, DSL 이 그대로면
    고친 파일을 그대로 두고, 둘 다 바뀌었으면 줄 단위 3-way 병합(git 과 같은 규칙). 무엇을 고쳤는지는 보지 않고 같은 줄을
    양쪽이 바꿨는지만 본다. 같은 줄이면 `<<<<<<< yours` / `>>>>>>> elasticore` 를 남기고 빌드가 실패하며, 표시가 남은 동안
    계속 실패한다. 정리한 파일이 그 뒤의 기준.
  - 지운 파일은 다시 생성. DSL 이 더 만들지 않는 파일은 캐시와 같으면 지우고, 고쳤으면 남기고 경고(캐시에서 빠진다).
    캐시에 없는 자리의 다른 파일은 덮어쓰지 않는다. 이전에 표시와 함께 생성된 파일은 해시가 맞으면 넘겨받고(사용자 블록은
    고친 내용으로 남음), 고쳤으면 그대로 두고 알린다. `orm.xml`·메시지처럼 늘 덮어쓰던 파일은 처음 병합하는 실행(`.elasticore/`
    가 없음)에서만 전처럼 쓰고, 그 뒤로 캐시에 없으면 다른 파일처럼 건드리지 않는다. 캐시를 읽을 수 없으면 - 잘렸거나
    프로젝트 밖 경로를 담았으면 - 아무것도 쓰지 않고 실패한다. 예외로 끝난 실행도 쓴 파일만큼 캐시를 남긴다.
  - `elcoreDdl`(DDL 만)의 충돌도 빌드를 실패시킨다.
  - 병합 파일은 플랫폼 줄바꿈으로 쓴다(Windows 에서 CRLF) - `orm.xml` 과 메시지는 전에 LF 였다.
  - 캐시와 생성 파일은 같이 다닌다(둘 다 커밋하거나 둘 다 git 에서 뺀다) - 캐시만 커밋하면 pull 뒤 로컬의 옛 생성 파일이
    고친 파일로 보인다. 저장소의 샘플은 생성 파일 대부분을 빼고 매 빌드 다시 만들므로 `.elasticore/` 도 뺀다.
  - git 병합 충돌: 캐시는 한쪽, 충돌한 생성 파일은 캐시와 같은 쪽을 고르고 빌드(가이드 9-2).
  - `-Pforce` 는 병합하는 파일에 적용하지 않는다 - 샘플처럼 빌드가 늘 켜 두는 설정이 고친 내용을 지우면 안 된다.
  - 클라이언트 실행(`elcore3ts`)과 서버 실행이 한 캐시를 쓴다: 각자 자기 파일만 오래된 것으로 본다. 오래된 클라이언트 경고는
    캐시의 `.ts` 도 생성된 클라이언트로 본다.
  - 2.x 코드(`output = 'v2'`, `jpa-springboot`)는 끈 채로 - 2.x 가 쓰던 그대로(`elcoreRulesCheck`·`elcoreTreeCheck` 불변).
    3.0 미발표 출력의 기본 변경: 위 파일들의 첫 줄 표시가 빠지고, MyBatis 매퍼 머리 주석이 "고치지 말라" 대신 병합을 말한다.
  - Java·Kotlin 은 그대로 첫 줄 표시 - 한 번만 생성하는 파일, 사용자 블록, 2.x 감지가 그 줄을 읽는다(다음 단계).
- 확인: `GeneratedMergeTest`(위 경우 전부, 형식별 - 매퍼·DDL·메시지·`orm.xml`·TypeScript, 클라이언트와 서버의 공유 캐시와
  오래된 클라이언트 경고, 기본값과 2.x, 얕은 클론, `force`, 잃은 캐시, 한 도메인 실행, 잘린·밖을 가리키는 캐시, DDL 만의 충돌),
  `ThreeWayMergeTest`, 플러그인 `mergeIsPassed`. 펫스토어 MyBatis 모델로 git 저장소를
  만들어 실제로: 수정 후 두 브랜치(각자 모델 변경)를 병합 - 충돌한 생성 파일은 한쪽을 골라 다시 생성하면 양쪽 모델 변경과
  개발자 수정이 모두 남음; `--depth 1` 클론에서 수정과 모델 변경이 병합됨; 모델 변경 30번 커밋에 캐시 34개 버전이 pack 에서
  약 20KB(매퍼 XML 자체와 비슷).
- 관련 파일: `util/ThreeWayMerge.java`, `verify/GeneratedCache.java`, `verify/RulesGenRunner.java`(writeMerged·
  removeStaleMerged·staleClient), `v3/mybatis_xml.tmpl`, `elasticore-gradle-plugin`(`merge`), `elasticore-env-guide.md`(9-2,
  2.3), `elasticore-rule-guide.md`(7.5, TypeScript), 샘플 다섯의 `.gitignore`

### New Features (v3 규칙 엔진 - Java·Kotlin 병합)

#### Java·Kotlin 소스도 병합 - 첫 줄 `//ecd:` 없이, 2.x 에서 올라오는 프로젝트의 고친 파일까지
- 왜: 생성 파일 병합(generated-merge)이 Java·Kotlin 을 빼고 있었다. 고친 Java 파일은 `SKIP(user-modified)` 로 DSL 을 더
  따라가지 않았고, 한 번만 생성하는 파일(확장 서비스·컨트롤러)은 DSL 이 바뀌어도 경고만 했다. 줄 단위 병합만으로는 DSL 이
  넣는 import 와 개발자가 넣은 import, DSL 의 새 필드와 개발자의 새 메서드가 한 자리에서 충돌했다(측정: 날짜 필드 하나에
  DTO 둘이 충돌).
- 무엇:
  - 3.0 코드의 Java·Kotlin 소스도 표시 없이 쓰이고 `.elasticore/cache` 를 기준으로 병합된다. 소스 병합: import 는 묶음으로
    (양쪽이 넣은 것은 모두, 개발자가 지운 것은 지운 채), 클래스 안에서 양쪽이 같은 자리에 더하기만 한 줄은 둘 다(생성된 줄
    먼저), 한쪽이 바꾼 줄 옆에 다른 쪽이 더하기만 한 줄은 그 옆에. 같은 줄을 각자 바꾼 것만 충돌.
  - 한 번만 생성하는 파일: 고치기 전에는 DSL 을 따라가고, 고친 뒤에는 깔끔하게 합쳐지는 DSL 의 변경만 받는다(`MERGED`).
    겹치면 충돌 표시 없이 그대로 두고(`KEPT(yours ...)`) 전부터 있던 경고(엔드포인트, 로그인, 생성자, 클래스 어노테이션,
    베이스 상속)로 옮길 것을 말한다. 예: `@readonly` 는 손대지 않은 쓰기 엔드포인트를 개발자 컨트롤러에서도 지우고,
    `@signin` 을 지우면 손대지 않은 로그인 엔드포인트가 빠진다 - 전에는 경고만 했다.
  - 표시가 있는 파일은 첫 병합 빌드에서 파일마다 넘겨받는다 - 기준을 표시의 해시로 확인할 수 있을 때만: 고치지 않았으면
    그 내용, 고쳤으면 이번 빌드가 만드는 것 또는 - 2.x 프로젝트가 `output = 'v3'` 로 옮기는 첫 빌드에서 - 2.x 번들을
    메모리에서만 한 번 더 돌려 만든 것(`[elcore3] upgrade : ...`). 확인할 수 없으면 표시를 두고 전처럼 건너뛴다.
  - 빌드 요약에 `=== merge: N merged with your edits / N kept as yours / N conflict(s) / N still stamped ... ===` 와 표시가
    남은 파일 목록.
  - 오래된 파일 정리는 개발자 클래스가 아직 상속하는 `...Base` 를 남긴다(표시 방식과 같게). 그리고 DSL 이 더 만들지
    않는 생성 클래스라도 남는 소스가 아직 쓰면(같은 패키지, import, 전체 이름) 지우지 않고 어느 파일이 쓰는지 알린다
    (`... is no longer generated, but ... still uses it - kept`) - 쓰는 곳이 없어진 다음 빌드에서 지워진다. 지워지는
    엔티티의 파일끼리만 서로 쓰는 것은 전처럼 함께 지워진다(엔티티 클래스와 그 `...Base` 도 한 번에). 남는 매퍼 인터페이스는 같은 이름의 XML 도 남긴다. 전에는
    2.x template 을 `output = 'v3'` 로 옮기는 첫 빌드가 2.x 호환 번들이 쓴(3.0 표시) `CouponCoreService` 를 지워, 개발자
    컨트롤러가 컴파일되지 않았다. 병합하는 빌드에서만 - 2.x 코드(`output = 'v2'`)는 전처럼 지운다.
  - 소스의 import 묶음은 맨 위의 import 와 그 사이 빈 줄뿐이다: 그 사이의 주석(주석 처리한 import 포함)부터는 줄로
    병합되어 남고, 코드 안(텍스트 블록)이나 주석 안의 `import` 는 import 가 아니다.
  - 2.x 프로젝트의 첫 3.0 빌드는 `-Pdomain` 으로 하면 멈춘다(다른 도메인의 고친 파일이 기준을 찾을 기회를 잃으므로).
    그 빌드가 2.x 코드를 메모리에서 만든 뒤에는 모델을 다시 읽어, 업그레이드 빌드와 다음 빌드가 같은 코드를 쓴다.
  - 표시도 없고 캐시에도 없는 소스는 그대로 두고 요약에 센다 - 캐시 파일이 없으면 git 에서 가져오라고 한다.
  - 마이그레이션 가이드: 2.x 는 `@service`·`@expose` 없이도 키가 있는 엔티티에 리포지토리를, `@template`·`@searchable`
    DTO 가 있으면 서비스와 컨트롤러까지 썼고 v3 는 `@expose`·`@service` 엔티티만 쓴다는 줄, `@ElastiCore` 로
    생성하던 프로젝트는 옮길 때 그것을 지운다는 줄(남기면 컴파일마다 2.x 코드가 다시 쓰여 병합에 편집으로 들어온다).
  - 3.0 미발표 출력의 기본 변경: Java·Kotlin 소스의 첫 줄 표시가 빠진다. 샘플의 한 번만 생성한 파일 넷(pet store
    `CustomerService`·`PurchaseOrderService`·`OrderLine`, MyBatis pet store `PurchaseOrderService`)이 표시 줄만 빠졌다.
    2.x 코드(`output = 'v2'`)는 그대로(`elcoreRulesCheck` 157/0/2, `elcoreTreeCheck` 0/0/0).
- 확인:
  - `ThreeWayMergeTest`(import 묶음, 같은 자리 추가, 바뀐 줄 옆 추가, Kotlin, import 사이 주석, 텍스트 블록),
    `GeneratedMergeTest`(Java 편집과 날짜 필드, 한 번만 생성하는 파일의 세 경우, 표시 넘겨받기와 확인 못 하는 기준,
    2.x → 3.0 업그레이드와 그 다음 빌드, `-Pdomain` 업그레이드 거절, 개발자 코드가 쓰는 오래된 클래스와 매퍼 XML,
    2.x 코드의 정리), `SignInAndReferencesTest`.
  - 실제 프로젝트 측정: template(2.x) 과 elasticore-petstore 에 모든 엔티티 필드 추가 - 바뀐 생성 파일 119 개 충돌 0;
    template 의 2.x → 3.0 - 바뀐 187 개 충돌 0(줄 단위만이면 1).
  - 샘플 시나리오(작업 트리에서 실제 생성·컴파일·테스트): MyBatis pet store 24/24, JPA pet store 23/23, template-kt
    21/21, template-ddd 23/23, template-mybatis 24/24 - 편집(생성 DTO 의 import·메서드·주석, 서비스, 매퍼 XML, DDL)
    뒤 필드 추가가 병합되고 컴파일·테스트 통과, 이름 바꾸기 충돌이 빌드를 멈추고 풀면 계속, 엔티티를 넣었다 빼면
    고치지 않은 파일은 지워지고 고친 것은 남고 알림. (샘플의 `elcore3clean` 은 생성 전에 소스를 지우므로 `-x
    elcore3clean` 으로.)
  - 2.x template 을 가이드대로 옮기기(`output = 'v3'`, `@ElastiCore` 제거, 서비스를 쓰던 엔티티에 `@expose`) 15/15:
    표시가 있는 2.x 파일 263 개를 기준으로 읽고, 고친 DTO 와 CoreService 는 표시 없이 편집이 남은 채 병합, 고치지 않은
    파일은 3.0 코드, 두 번째 빌드는 그대로, 컴파일 통과.
- 관련 파일: `util/ThreeWayMerge.java`(mergeSource), `verify/RulesGenRunner.java`(writeMergedSource·confirmedBase·
  twoXBases·rewriteOwned·removeStale·removeStaleMerged·removeUnused·stillUsed·printSummary), `util/ConsoleLog.java`(snapshot·restore),
  `elasticore-env-guide.md` 9-2, `elasticore-rule-guide.md` 7.5, `elasticore-migration-guide.md`

### New Features (v3 규칙 엔진 - MyBatis)

#### `mode: mybatis` - 같은 모델을 JPA 대신 MyBatis 로 생성한다
- 왜: v3 는 JPA 코드만 썼다. 공공·금융·SI 현장의 상당수는 MyBatis 를 쓰고, 그런 프로젝트는 ElastiCORE 를 쓸 수 없었다.
- 무엇: env.yml `config.mode` 의 한 단어(2.x 가 이미 읽는 키)로 정한다. 새 DSL 키는 없다. 레이어드 Java, Spring Boot 3·4.
  - DTO·검색 DTO·컨트롤러·접근 검사·도메인 매퍼는 JPA 와 같고, 서비스는 JPA 판과 메서드 이름·시그니처가 같다 - 컨트롤러와
    확장 서비스는 그대로.
  - 엔티티: JPA 규칙이 그리는 클래스에서 JPA·Hibernate·Spring Data 어노테이션과 수명주기 콜백을 걷어낸 평범한 클래스.
  - `<Entity>Repository` (MyBatis `@Mapper`) + 같은 패키지 경로의 `<Entity>Repository.xml`: to-one 관계를 LEFT JOIN 해 한
    문장으로 읽는 resultMap(N+1 없음 - 페이지는 count 와 본문 두 문장), 검색 DTO 조건마다 `<if>`, 엔티티 속성 목록으로만
    하는 정렬(그 밖은 400), DB 별 페이지(`LIMIT/OFFSET`, Oracle·Tibero·SQL Server·DB2 는 `OFFSET ... FETCH`),
    insert(생성 키 회수)·update(버전 비교·증가)·delete.
  - `db/<domain>-schema.sql`: 테이블과 외래키(참조 중인 행 삭제는 JPA 처럼 409). H2 용이며, 다른 DB 는 일부 타입 이름이
    달라 이 파일에서 마이그레이션을 시작한다고 파일 머리에 적는다.
  - JPA 가 하던 `@genid`·`@jpa:CreatedDate`·`@jpa:LastModifiedDate` 는 서비스가 하고, `@updatable(false)` 컬럼은 UPDATE 에서
    뺀다. 이름은 Spring 의 JPA 명명과 같다(테이블·컬럼은 소문자 snake, 관계 컬럼 `field_targetkey`, enum 은 같은 코드 값) -
    한 테이블을 두 방식이 읽는다. `@formula` 필드는 엔티티에 남되 MyBatis 가 읽지 않아 null 이고 검색 조건에서 빠진다(경고).
  - 아직 쓰지 않는 것은 조용히 빼지 않고 빌드가 멈춘다: 컬렉션 관계·값 컬렉션·`@embedded`·복합 키·상속·Money·JDBC 타입이
    없는 타입·`@joinBy`·IDENTITY 가 아닌 키 생성·키가 아닌 `@sequence`·`@jpa:CreatedBy`/`LastModifiedBy`, 조인한 행보다 먼 검색
    경로(빼면 조건부 수정·삭제가 모든 행에 미침), 컬럼이 아닌 `@owner`, DDD·헥사고날·Kotlin(빌드의 선언이든 도메인의
    `publisher.rules` 든).
  - 프로젝트 단위의 거절: `-Pv2` 와 함께(2.x 코드는 JPA), 2.x 코드를 지키는 도메인과 같은 프로젝트(그 도메인을 v3 로
    다시 쓰지 않도록), JPA 엔티티가 MyBatis 엔티티를 가리킴(반대는 됨). `jpa,mongo` 처럼 jpa 와 함께 적힌 모드는 전처럼
    JPA 를 쓴다.
  - 도메인을 `jpa` 로 되돌리면 생성했던 매퍼 XML 과 스키마를 지운다(손대지 않은 것만).
- 기반: 번들을 아키텍처·언어에 더해 **저장 방식**으로도 고른다(`RulesConfig.Persistence`, `bundleFor(아키텍처, 언어, 저장 방식)`).
  XML·SQL 파일의 생성 표시(`//ecd:`)는 그 언어의 주석 안에 쓴다(`<!-- //ecd:... -->`, `-- //ecd:...`).
- 런타임 라이브러리 (`elasticore-springboot3`·`-springboot4`):
  - `ConvertedEnumTypeHandler`(enum 을 JPA 변환기와 같은 값으로), `ElastiCoreMyBatisAutoConfiguration`(MyBatis 에 DB 종류를
    알림). 이 자동 설정은 MyBatis 가 있는 앱에서 기본으로 켜지며, 앱이 직접 쓴 매퍼의 `databaseId` 문장도 그 DB 에서 쓰이게
    한다. 끄려면 `elasticore.mybatis.database-id=false`.
  - **`spring-boot-starter-data-jpa` 를 더는 끌어오지 않는다** (implementation → compileOnly). 끌어오면 MyBatis 앱 안에서
    Hibernate 가 떴다. JPA 프로젝트는 README 의 빌드처럼 data-jpa 스타터를 직접 선언한다 - 저장소의 JPA 샘플은 모두 이미
    그렇다. 증상: 기동 시 EntityManagerFactory 빈이나 Hibernate 를 못 찾으면 `spring-boot-starter-data-jpa` 를 선언한다.
  - `jakarta.persistence-api` 는 `api` 로 (NotFoundException 이 EntityNotFoundException 을 상속하므로 그것을 던지는 코드가
    컴파일되려면 필요).
  - `VersionCheck`: spring-orm 이 없는 앱에서는 `OptimisticLockingFailureException`(같은 409)을 던진다. 전에는 MyBatis 앱에서
    `NoClassDefFoundError` 였다.
- 확인:
  - `MyBatisGenerationTest`(생성물, 거절들, 다른 도메인 자기참조 순환, 2.x 도메인과 함께, JPA→MyBatis 관계, `@updatable(false)`,
    jpa 로 되돌릴 때 정리, 옆에 MyBatis 도메인이 있어도 JPA 도메인 출력이 바이트 단위로 같음), `MyBatisSearchResolutionTest`,
    `PersistenceAxisTest`, `DefaultGenerationTest`. 독립 리뷰의 지적(H1·H2·M1~M6·L1~L8)을 반영.
  - 새 모듈 `elasticore-template-mybatis`: 생성·컴파일 후 H2 위에서 API 를 돈다 - 관계 조인, 모든 검색 조건, 페이지 문장 수 2,
    정렬과 거절, 버전 409, 참조 행 삭제 409, unique 409, 시각 찍기. `OFFSET ... FETCH` 분기는 H2 Oracle 모드에서 SQL 까지 확인.
  - Spring Boot 4.0.8 + mybatis-spring-boot-starter 4.0.0 프로젝트를 만들어 같은 호출을 돌림(Hibernate 없음).
  - JPA 쪽: `elcoreRulesCheck -Pstrict` 157 MATCH / 0 DIFF, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra, base·런타임·
    template·template-ddd·template-kt·petstore 테스트 통과.
- 관련 파일: `RulesConfig.java`, `RuleCatalog.java`, `RulesGenRunner.java`, `rules/mybatis/*`, `v3/mybatis_*.tmpl`,
  `ServiceAccessRules.java`, `MapperAssembleRules.java`, `MapperFileRules.java`, `RepositoryHelperBaseRules.java`, `SourceCode.java`,
  `EntitySpec.java`, `HashUtils.java`, 런타임 `build.gradle`·`VersionCheck.java`·`mybatis/`·`autoconfigure/`,
  `elasticore-env-guide.md`(2.3 절), `elasticore-rule-guide.md`

### New Features (v3 규칙 엔진 - MyBatis)

#### `mode: mybatis` 가 펫스토어를 쓴다 - Money·값 객체·컬렉션·`@joinBy`, Oracle·PostgreSQL·SQL Server
- 왜: 펫스토어 모델을 `mode: mybatis` 로 바꾸자 빌드가 멈췄다. 가격(Money), 주소·카드(값 객체), 주문줄(컬렉션), 수량
  (Quantity) - 쇼핑몰의 핵심이 모두 "아직 쓰지 않는 것" 이었다. 그리고 생성 스키마는 H2 용뿐이라 실제 DB 에서 돌려 본 적이
  없었다.
- 무엇:
  - **Money·Quantity**: 한 컬럼(DECIMAL·INTEGER)에 JPA 변환기와 같은 값. 런타임 라이브러리에 `MoneyTypeHandler`·
    `QuantityTypeHandler` 를 두고 XML 이 결과·파라미터마다 이름으로 쓴다. 검색 DTO 가 다른 타입(BigDecimal)으로 들고 있으면
    그 값은 JDBC 타입으로만 묶는다. Oracle 은 `NUMBER(10,2)` 의 100.00 을 100 으로 돌려주므로(CI 의 Oracle 에서 발견),
    `MoneyTypeHandler` 가 컬럼에 선언된 소수 자릿수로 맞춘다 - 어느 DB 든 같은 금액이 같은 모양(`100.00`)으로.
  - **값 객체**(`@embeddable` + `@Embedded`, `@embedded(prefix)`): 그 필드가 행의 컬럼 - 이름은 JPA 와 같다(각자의 컬럼,
    prefix 면 `prefix_field`). resultMap 의 association 으로 행과 조인한 행 모두에서 읽고, insert·update·정렬(`home.city`)·검색
    경로가 그 컬럼을 쓴다. 선택인 값의 컬럼은 스키마에서 NULL 허용. MyBatis 도메인의 DTO 는 값 객체를 싣는다(레이어드 DTO 는
    빼던 것 - 그러면 주소를 보낼 수 없다). 값 객체 안의 값 객체·관계는 거절.
  - **컬렉션**(`@OneToMany(mappedBy=...)`): 대상 매퍼에 `findAllBy{back}In(keys)`·`deleteAllBy{back}(key)`. 서비스의
    `loaded()` 가 한 행이든 한 페이지든 컬렉션마다 한 문장(키 500 개씩)으로 읽어 나눠 준다 - 주문 페이지와 주문줄이 세 문장.
    **애그리거트의 멤버**(루트가 아닌 대상)는 루트와 함께 쓴다: 매퍼가 DTO 의 줄을 엔티티로 옮기고(`@Valid`), 저장 때 insert
    (`@genid`·생성 시각 포함), 수정 요청이 줄을 보내면 지우고 다시 쓰고, 루트를 지울 때 먼저 지운다. 멤버는 서비스 없이
    매퍼(`*_member` 템플릿)만, 헬퍼에 주입된다. 다른 루트의 컬렉션은 읽기만. `mappedBy` 없는 것·`@ManyToMany` 는 거절.
  - **`@joinBy(idField)`**: 행이 id 를 값으로 갖고 관계는 그 컬럼으로 조인만 - 레이어드에서 `@owner(customerId)` 를 쓰는 모양.
    외래키도 그 컬럼에.
  - **DB 별 스키마**: `{domain}-schema.sql`(H2) 에 더해 `-postgresql`·`-oracle`·`-sqlserver`. 타입은 그 DB 의 Hibernate
    방언이 고르는 것(Oracle `NUMBER(19)`·`VARCHAR2(n CHAR)`·`BINARY_DOUBLE`, SQL Server `NVARCHAR`·`DATETIME2`·`BIT`,
    PostgreSQL `TEXT`·`BYTEA`), 생성 키는 그 DB 의 IDENTITY. `@Index` 를 인덱스로, SQL Server 에서는 NULL 허용 유일 컬럼을
    필터 인덱스로. 어느 DB 의 예약어인 테이블·컬럼 이름은 경고. CLOB·BLOB 파라미터는 VARCHAR·VARBINARY 로 묶는다
    (PostgreSQL 이 대형 객체로 묶인 NULL 을 거절).
  - XML: resultMap 을 DTD 순서(id, result, association)로, 조인한 행의 컬럼 별칭이 PostgreSQL 의 63 자를 넘으면 `jN_` 로.
  - 독립 리뷰가 찾은 것: 멤버 안의 컬렉션(저장할 곳이 없어 조용히 잃던 것)은 거절, 자기 서비스가 있는 엔티티는 루트와 함께
    쓰지 않음(두 번 쓰지 않도록 - 매퍼와 서비스가 같은 판정), `lines: []` 는 줄을 모두 지움, 키와 컬렉션뿐인 엔티티는 UPDATE
    없이 행이 있는지만 확인(전에는 없는 문장을 불러 매번 실패), 큰 값(CLOB·BLOB, 4000 자 넘는 문자열)은 정렬 목록에서 빼고
    LIKE 가 아닌 검색 조건은 경고하고 뺌(Oracle ORA-00932), 같은 이름의 엔티티가 다른 도메인에 있어도 자기 도메인 것을 읽음.
- 새 모듈 `elasticore-sample-petstore-mybatis`: 펫스토어 모델을 레이어드 + MyBatis + Spring Boot 4 로. 가격 계산은 확장
  서비스가 한다(레이어드는 `@compute`·`states:` 를 하지 않는다). 테스트(ShopFlow)는 둘러보기·가입·로그인·주문(값 객체,
  줄, 금액)·소유자 범위·삭제(줄 먼저)·409·400 과, 주문 한 페이지가 세 문장인 것을 본다.
- CI 에 "MyBatis on Oracle, PostgreSQL and SQL Server" 작업: Oracle 23 Free·PostgreSQL 16·SQL Server 2022 서비스
  컨테이너에서 펫스토어 흐름과 template-mybatis 의 API 테스트(모든 검색 조건, 모든 컬럼 타입의 왕복, 버전·외래키·유일 409,
  DB 별 페이지 문법)를 각 DB 의 생성 스키마로 돌린다. 건너뛴 DB 가 있으면 실패.
- 확인: base 의 `MyBatisGenerationTest`(컬렉션·멤버·`@joinBy`·값 객체·DB 별 스키마·필터 인덱스·예약어 경고), H2 의 두 모듈
  테스트, CI 의 세 DB. JPA 쪽 출력은 그대로(`elcoreRulesCheck -Pstrict`, `elcoreTreeCheck`).
- 관련 파일: `rules/mybatis/*`(새 `MyBatisMapperMemberRules`), `v3/mybatis_service.tmpl`·`mybatis_repository*.tmpl`·
  `mybatis_schema.tmpl`, `RulesGenRunner.java`, `RepositoryHelperBaseRules.java`, `RuleCatalog.java`, `DddMapperRules.java`,
  런타임 `mybatis/MoneyTypeHandler.java`·`QuantityTypeHandler.java`, `elasticore-env-guide.md`(2.3 절),
  `elasticore-template-mybatis`, `elasticore-sample-petstore-mybatis`, `.github/workflows/ci.yml`

### Bug Fixes (v3 규칙 엔진 - `mapping: xml`, `-Pdomain`)

#### 도메인 하나만 생성해도 모듈의 orm.xml 이 다른 도메인의 매핑을 잃지 않는다
- 왜: `mapping: xml` 도메인이 둘 이상인 모듈에서 `-Pdomain` 으로 한 도메인만 생성하면, 그 실행은 그 도메인의 클래스만
  읽고 모듈의 `META-INF/orm.xml` 을 그것만으로 다시 썼다. 다른 도메인의 `<entity>` 가 빠져, 다음 실행까지 그 엔티티들은
  JPA 매핑 없이(애너테이션도 없이) 남았다.
- 무엇: `-Pdomain` 실행은 마지막으로 생성한 orm.xml(`.elasticore/cache` 의 기준, 없으면 생성기가 쓴 파일)에서 이번에
  생성하지 않은 도메인의 클래스 - 그 도메인들의 엔티티·enum 패키지에 있는 것 - 를 제자리에 두고, 이번 도메인의 클래스는
  새로 쓴다. 이번 도메인이 더 갖지 않는(지웠거나 패키지를 옮긴) 클래스의 매핑은 빠진다 - 남기면 없는 클래스나 같은 이름의
  두 엔티티를 매핑해 기동이 실패한다. 생성한 도메인도 쓰는 패키지는 다른 도메인의 것으로 보지 않는다(같은 패키지를 쓰는
  도메인끼리는 그 패키지에서 이번에 매핑하지 않은 클래스가 빠진다 - 다음 전체 실행이 다시 쓴다). 새 클래스는 같은 패키지의
  마지막 클래스 뒤에 - 모델에서 더 앞에 적힌 클래스면 다음 전체 실행이 자리를 옮긴다. 병합은 다른
  도메인 쪽에 변경이 없다고 보므로 개발자가 고친 내용도 남는다. 전체 실행은 전과 같다 - 이번 실행의 매핑만 쓴다.
- 확인: `GeneratedMergeTest.aRunOverOneDomainKeepsTheOtherDomainsMappingsInTheOrmXml`(dgshop·billing 두 도메인,
  dgshop 만 바꿔 `-Pdomain` - billing 의 `Invoice` 가 남고 dgshop 의 새 필드가 들어감, 이어진 전체 실행은 `UNCHANGED`),
  `...WithoutTheCache`(`merge = false` 에서도 같음), `aRunOverOneDomainDropsWhatThatDomainNoLongerMapsAndKeepsEditsToTheOthers`
  (dgshop 이 엔티티 하나를 지우고 패키지를 옮김 - 옛 매핑이 빠지고, billing 블록 안의 개발자 주석은 남음),
  `OrmMappingTest.aRunOverOneDomainKeepsOnlyTheOtherDomainsClasses`(CRLF 의 이전 파일, `<attributes>` 없는 블록, 종류가
  바뀐 클래스, 새 클래스의 자리), `aRunOverOneDomainKeepsTheOtherDomainsMappingsBesideTheDevelopersOwnOrmXml`(개발자의
  orm.xml 이 있어 매핑이 `elasticore-orm.xml` 로 갈 때도 같음). 처음 두 테스트는 고치기 전 코드에서 실패했다.
- 관련 파일: `OrmMapping.java`(`toXml(previous, otherPackages)`), `RulesGenRunner.java`(`otherDomainPackages`, `writeOrmXml`),
  `GeneratedMergeTest.java`, `OrmMappingTest.java`.

### Documentation (마이그레이션 가이드 - 롤백)

#### 3.0 코드로 옮긴 프로젝트는 버전만 되돌리면 고친 내용을 잃는다고 적음
- 왜: "Q. 롤백하려면?" 이 "버전을 되돌리면 됩니다. 3.0 이 남긴 상태가 없습니다" 라고 했다. 2.x 코드를 유지한 프로젝트에는
  맞지만, 3.0 코드는 첫 줄 `//ecd:` 없이 쓰이므로 2.x 생성기는 그 파일을 고친 것으로 보지 않고 덮어쓴다.
- 확인: `elasticore-template` 에서 2.x 생성기(`elcore`)를 돌림(확인 뒤 되돌림) - `ArticleKeyDTO.java` 의 `//ecd:` 줄을
  지우고 한 줄을 더하면 2.x 코드로 다시 쓰이고 더한 줄이 사라졌다. 3.0 의 한 번만 생성하는 파일처럼 첫 줄이
  `// Initially generated by ElastiCORE; ...` 인 `CommentController.java` 도 같았다(2.x 는 그 표시를 `//ecd:` 아래에서만 찾는다).
- 무엇: `elasticore-migration-guide.md` "Q. 롤백하려면?" 을 둘로 나눔(2.x 코드 유지 프로젝트는 버전만, 3.0 코드로 옮긴
  프로젝트는 옮기기 전 커밋으로 소스를 되돌린 뒤 버전 - 컨트롤러·확장 서비스도 덮어쓰임, `merge = false` 로 생성해 온
  프로젝트는 표시가 있어 고친 파일이 남음), "되돌리는 법" 의 `output = 'v2'` 에도 같은 주의를 더함.
- 관련 파일: `elasticore-migration-guide.md`.

### Documentation (v3 규칙 엔진 - 한 번만 생성하는 파일)

#### 가이드가 한 번만 생성하는 파일을 "다시 쓰이지 않는다"고 하던 곳을 병합에 맞춤
- 왜: Java·Kotlin 병합(merge-java) 뒤로 한 번만 생성하는 파일(컨트롤러, 확장 서비스, 나뉜 루트의 개발자 클래스, 팩토리)은
  고치기 전에는 DSL 을 따라가고, 고친 뒤에는 깔끔하게 합쳐지는 변경만 받는다. 그런데 가이드 여러 곳이 아직 "처음 한 번만
  생성되고 그 뒤로는 건드리지 않는다", "규칙은 처음 만들어질 때만 적용된다", "Java 파일은 고치면 건너뛴다"고 해, 나중에
  더한 규칙이나 `supportAI` 를 손으로 옮겨야 하는 것처럼 읽혔다.
- 무엇: `elasticore-rule-dev-guide.md` 2장 끝("이미 만들어져 내가 고친 파일에도 붙습니다")·4장 `target` 끝·문제 해결 표,
  `elasticore-ddd-customize-guide.md` "이미 있는 내 파일"·스켈레톤 첫 줄 표·Kotlin 표·문제 해결 표,
  `elasticore-env-guide.md` `supportAI` 를 나중에 켤 때와 MyBatis 직접 쓰는 쿼리, `elasticore-migration-guide.md`
  "손으로 고쳐둔 파일" 질문, `elasticore-rule-guide.md`·`elasticore-annotation-guide.md` 의 `@factory` 와 도메인 서비스 예.
  손으로 붙여야 하는 경우는 그대로 적었다: 겹쳐서 `KEPT`, `merge = false`, 병합 전 버전이 만든 파일을 고친
  `SKIP(user-owned)`, 표시 줄 없이 복사한 파일(`SKIP(no ElastiCORE header - yours)` - ERP 예제의 세 파일). 빠진 클래스
  어노테이션 알림은 여전히 엔티티 클래스에만 나온다. 이전 스냅샷에서 올라오는 프로젝트를 위한 안내(`XxxApiErrors`,
  `OrderService` 생성자)는 그 파일들이 기준을 확인할 수 없어 여전히 맞으므로 두었다.
- 생성물은 그대로다. 팩토리와 도메인 서비스가 생성하는 주석의 "written once" 도 두었다 - 개발자 파일의 생성 줄을 바꾸면
  그 줄을 고치거나 지운 개발자의 파일이 매 빌드 `KEPT` 로 남아 그 뒤의 DSL 변경을 받지 못한다.
- 확인: `GeneratedMergeTest` -
  `aRuleAddedLaterReachesAnEditedSkeleton`(나중에 더한 프로젝트 규칙의 클래스 어노테이션이 고친 컨트롤러에 `MERGED`,
  규칙을 빼면 다시 빠짐), `aRuleAddedLaterReachesTheEditedClassOfASplitRoot`(DDD 루트의 고친 개발자 클래스에
  `@AggregateRoot` 와 import 가 병합, 메서드는 남음, "add them by hand" 알림 없음),
  `supportAiTurnedOnLaterReachesAnEditedService`(`supportAI` 를 나중에 켜면 고친 `CustomerService` 에 `@Tool` 병합),
  `aFactoryWithItsBodyWrittenFollowsTheSignatureOrSaysSo`(손대지 않은 팩토리는 새 필수 필드를 시그니처에 받고, 본문을 쓴
  팩토리는 `KEPT` 와 알림).
- 관련 파일: 위 가이드 6개, `DddFactoryRules`(Javadoc), `GeneratedMergeTest`, `com.acme.probe.AddedLaterRules`

## 3.0.261029-rc4

v3 서비스가 `@fetchjoin` 연관을 2.x 처럼 페이지 검색에서 함께 가져오고, 규칙 엔진은 `mode: mongo` 같은 JPA 가 아닌 도메인을
거절한다. 2.x 기준 샘플이 쓰지 않던 DSL 을 더해 호환성 검사가 비교하는 범위를 넓혔고, 거기서 나온 2.x 호환 컨트롤러의 차이
하나를 고쳤다. 변경 기록은 PR 마다 `changes/` 의 파일로 쓰고 릴리스 때 이 이력에 모은다 - 이 섹션의 뒤쪽 항목들이 처음으로
그렇게 모였다. 2.x 생성기의 출력은 그대로다.

### Build (릴리스 노트 - 영어)

#### GitHub 릴리스의 노트는 영어 파일 `docs/releases/{version}.md`
- 왜: Release 워크플로가 이 이력의 버전 섹션을 그대로 GitHub 릴리스 노트로 옮겨서, 공개 저장소의 릴리스가 모두 한글이었다
- `release.yml` 은 `docs/releases/{version}.md` 를 노트로 쓰고, 이 이력(한글)은 링크로 덧붙인다. 태그의 버전에 그 파일이
  없거나 비어 있으면 업로드하기 전에 멈춘다. `RELEASING.md` 의 2단계에 그 파일을 쓰는 일을 더했다
- 이미 낸 다섯 릴리스(3.0.261015-alpha1 ~ 3.0.261028-rc3)의 영어 노트를 이 이력에서 옮겨 써서 GitHub 릴리스의 본문을
  바꿨다
- 관련 파일: `.github/workflows/release.yml`, `RELEASING.md`, `docs/releases/*.md`

### Bug Fixes (v3 서비스 - `@fetchjoin`)

#### v3·Kotlin CoreService 가 `@fetchjoin`·`table.autoJoin` 연관을 페이지 검색에서 루트와 함께 가져온다
- 왜: 2.x 코드(`output = 'v2'`)의 CoreService 는 `@fetchjoin` 연관(그리고 `table.autoJoin` 이면 엔티티 연관 전부)을
  페이지 검색(`findBySearch`)에서 루트와 함께 가져온다(`withJoinFetches()`). v3 번들(플러그인의 기본값, 헥사고날·DDD,
  `-Pv3`, 모든 Kotlin 빌드)의 CoreService 에는 그 기능이 없어 연관을 행마다 따로 읽었고(N+1), 컴파일도 테스트도 통과하고
  아무 말도 없었다. 확장 서비스에서 `withJoinFetches()` 를 부르던 코드는 컴파일되지 않았다. rc3 부터는 새 레이어드 Java
  프로젝트와 `output = 'v3'` 로 옮긴 프로젝트도 이 서비스를 받는다. 9개 도메인 프로젝트에서 `@fetchjoin` 연관 7개가
  N+1 이 되고 `withJoinFetches()` 호출 2곳이 컴파일되지 않았다
- 먼저 그런 엔티티마다 DSL 경고를 내게 했고(https://github.com/xsko/ElastiCORE/pull/31), 같은 버전에서 실제 JOIN FETCH 로
  바꿨다 - 그 경고는 없어졌다
- 이제 v3 Java·Kotlin CoreService 에 `withJoinFetches()` 가 생성되고(LEFT JOIN FETCH, count 쿼리 제외, `distinct`),
  `findBySearch` 가 그것을 더한다 - 2.x 와 같은 자리다. 대상 연관은 2.x 코드와 같은 기준
  (`JpaServiceCompatFileRules.fetchedRelations`)으로 고른다. 그런 연관이 없는 엔티티의 서비스는 그대로다
- 확인: `elasticore-template-ddd` 의 `JoinFetchTest` - 고객이 다른 주문 5건을 페이지 검색하면, 고객을 따로 읽는 select 가
  수정 전 5번, 수정 후 0번(Hibernate 통계의 fetch count). `FetchJoinTest`(생성된 Java·Kotlin·헥사고날 서비스, 표시 없는
  연관은 그대로, 2.x 호환 서비스도 그대로, 경고 없음). DDD·Kotlin 샘플은 그 서비스로 컴파일·테스트한다
- 관련 파일: `JpaServiceFileRules.java`(`joinFetchVars`), `JpaServiceCompatFileRules.java`(`fetchedRelations`),
  `v3/service.tmpl`, `kt/service.tmpl`, `FetchJoinTest.java`, `JoinFetchTest.java`, `elasticore-migration-guide.md`,
  `elasticore-annotation-guide.md`·`elasticore-env-guide.md`(`autoJoin` 은 `findById` 가 아니라 페이지 검색에서 - 2.x 도
  그렇다)

### Documentation (마이그레이션 가이드 - 2.x 코드에서 옮길 때 손볼 것)

#### v3 번들로 옮기면 컴파일·테스트에서 드러나는 차이를 한 표로
- 왜: rc3 부터 v3 번들이 기본값인데, 가이드의 v3 항목은 `like` 하나였다. 도메인 9개짜리 프로젝트를 `-Pv3` 로 옮기자 본
  코드 5곳과 테스트 138곳이 컴파일되지 않았고, 우회한 뒤에도 테스트 176개가 실패했다 - 가이드에 없는 차이들이었다
- `elasticore-migration-guide.md` 에 "2.x 코드에서 옮길 때 손볼 것" 을 더했다: 키를 받는 엔티티 생성자, 보호 블록이
  기대던 `*` import, `@fetchjoin` 대신 `JoinSpec.fetchJoin`, `@writeonly` 값의 복사, CoreService 의 `save`·`update`
  검증과 키 중복 거절, `delete(searchDTO)`·`updateBySpecification` 이 행마다 도는 것, 2.x 서비스 테스트의 mock
- `like` 항목에 `lower(컬럼)` 비교와 인덱스를 적었다

### Documentation (개발 가이드 - 보호 블록과 JOIN FETCH)

#### 보호 블록은 여러 개 쓸 수 있고, import 는 헤더 블록에
- 왜: 가이드는 "한 소스 파일 내 하나의 블록만 사용 가능" 이라고 했지만 두 생성기 모두 여러 블록을 지킨다. 반대로 적혀
  있지 않던 것이 있었다 - import 줄이 든 블록은 `package` 아래로 가는 헤더 블록이 되고, 헤더 블록이 둘이면 재생성할 때
  첫 번째가 사라진다. v3 코드는 생성 코드가 쓰는 것만 import 하므로, 2.x 코드의 `*` import 에 기대던 블록 코드는 헤더
  블록이 있어야 컴파일된다
- `elasticore-dev-guide.md` 의 "사용자 코드 보존 블록" 을 고쳤다: 여러 블록과 그 자리, 헤더 블록과 예제, 해시에서 빠지는
  것. 2.2 jar 와 v3 로 같은 파일을 다시 생성해 확인했다

#### `withJoinFetches()` 가 어느 서비스에, 어느 검색에 있는지
- 11장·12장이 `withJoinFetches()`·`@fetchjoin`·`autoJoin` 을 버전 구분 없이 적었다. 처음에는 2.x 코드(`output = 'v2'`)의
  CoreService 에만 있다고 고쳤고, 같은 버전에서 v3 서비스도 가져오게 되어(위 "v3 서비스 - `@fetchjoin`") 둘 다의 페이지
  검색(`findBySearch`)이 쓰고, v3 에서는 그런 연관이 있는 엔티티에만 생성된다고 적었다. 그 밖의 쿼리는 `JoinSpec.fetchJoin()`.
  마이그레이션 가이드의 "손볼 것" 표의 `@fetchjoin` 줄도 같이 고쳤다

### Documentation (어노테이션 가이드 - 엔티티의 `@readonly`·`@restExclude`)

#### 메서드를 고르는 두 엔티티 어노테이션을 1장에
- 왜: 엔티티의 `@readonly` 와 `@restExclude(...)` 는 컨트롤러·서비스·TypeScript 클라이언트·AI 도구가 가질 메서드를
  정하는데, 어노테이션 가이드에는 둘 다 없었다 - `@readonly` 는 필드 어노테이션으로만, `@restExclude` 는 규칙 가이드·env
  가이드·이 이력에만 있었다
- `elasticore-annotation-guide.md` 1장에 둘을 더했다: 남는 메서드, `@restExclude` 가 받는 이름, 함께 빠지는 짝은 규칙
  가이드로. 둘 다 3.0 규칙엔진만 읽는다고 적었다 - 2.2 jar 로 생성하면 두 엔티티 모두 엔드포인트 8개와 서비스 메서드를
  전부 받는다

### Build (변경 기록 검사 - CI)

#### 풀 리퀘스트가 변경을 기록하는 방식을 CI 가 검사하고, 어기면 고칠 방법을 메시지로 알린다
- 왜: 변경을 `changes/<주제>.md` 에 적는 방식(같은 버전의 "변경 기록 - PR 마다 한 파일")은 `CONTRIBUTING.md` 와 PR 템플릿에만
  적혀 있어, 읽지 않은 기여자는 충돌을 겪고서야 알았다
- `ci.yml` 에 풀 리퀘스트에서만 도는 작업 "Change record" 를 더했다(`.github/scripts/change-record.sh`). 실패:
  `elasticore-history.md` 를 고친 PR(릴리스 브랜치 `version/*` 는 예외 - 조각을 이력에 모으는 곳), 배포되는 것
  (`elasticore-project/*/src/main/**`, 이력 제외)을 바꾸고 `changes/*.md` 를 더하지 않은 PR, 샘플 `blueprint/` 의 가이드 사본을
  커밋한 PR. 경고: `main` 보다 커밋 10개 넘게 뒤처진 브랜치. 각 메시지는 무엇을 어떻게 하라고 적는다
- 브랜치 이름은 기여자가 정하므로 명령에 끼워 넣지 않고 환경 변수로 스크립트에 넘긴다
- 확인: 로컬에서 경우마다 실행 - 이 브랜치(통과), 이력을 고친 커밋(실패, 오류 하나), 같은 커밋을 `version/` 브랜치로(통과),
  `src/main` 을 바꾸고 조각이 없는 커밋(실패), 조각을 더한 커밋(통과)
- 관련 파일: `.github/workflows/ci.yml`, `.github/scripts/change-record.sh`, `CONTRIBUTING.md`

### Build (변경 기록 - PR 마다 한 파일)

#### 변경은 `changes/<주제>.md` 에 적고, 릴리스할 때 `./gradlew assembleHistory` 가 이 이력으로 옮긴다
- 왜: 모든 풀 리퀘스트가 이 이력의 맨 위(현재 버전 섹션)를 같은 줄에서 고쳐, 하나가 머지되면 다른 것이 모두 충돌했다.
  템플릿 셋의 `blueprint/` 에 사본(이력 사본 하나와 빌드가 복사하는 가이드·가이드 색인)을 커밋해 두어 한 변경이 네 번
  충돌했다. 이틀 사이 외부 기여자의 풀 리퀘스트 여덟(#23~#26, #31, #33~#35)이 모두 이 파일들 때문에 충돌했다
- 이제 변경마다 `changes/` 에 파일 하나를 더한다(형식은 `changes/README.md` - 이 이력과 같은 `###`·`####` 항목, 버전 제목
  없이). 릴리스 커밋에서 `./gradlew assembleHistory` 가 조각을 이름 순으로 그 버전의 섹션(`3.0.261029-rc4` 이면
  `## 3.0.261029-rc4`, 없으면 맨 위에 새로)에 넣고 지운다. Release 워크플로는 `changes/` 에 조각이 남아 있으면 업로드 전에 멈춘다
- 템플릿 셋의 `blueprint/elasticore-*.md`(가이드 9개, 가이드 색인, 이력 사본)는 더 이상 커밋하지 않는다(`.gitignore`).
  가이드와 색인은 빌드가 `elasticore-base` 의 원본에서 다시 쓰고, 이력 사본은 아무도 읽지 않았다. CI 의 `blueprint/` 비교는
  커밋하는 Ossie 모델과 스키마에 그대로 남는다
- `CONTRIBUTING.md`·PR 템플릿: 이력 대신 조각 파일, 최신 `main` 에서 브랜치를 따고 필요할 때만 다른 PR 위에 쌓는다,
  `blueprint/` 의 가이드 사본은 커밋하지 않는다. `RELEASING.md` 2단계에 `assembleHistory`
- 확인: 조각 둘을 이름 순으로 기존 `## 3.0.261029-rc4` 섹션 끝에 넣고 지우는 것, 섹션이 없을 때(`-PelasticoreVersion=
  3.0.261030-SNAPSHOT`) 맨 위에 새 섹션을 만드는 것을 실행해 보았다(시험한 이력은 되돌림)
- 관련 파일: `build.gradle`(`assembleHistory`), `changes/README.md`, `.github/workflows/release.yml`, `.gitignore`,
  `CONTRIBUTING.md`, `RELEASING.md`, `.github/PULL_REQUEST_TEMPLATE.md`, `.github/workflows/ci.yml`(주석)

### Bug Fixes (2.x 호환 - 선언한 검색 DTO 를 받는 컨트롤러)

#### 엔티티에 `@searchable` DTO 를 따로 선언하면, 2.x 호환 컨트롤러의 검색이 그 DTO 를 받는다
- 왜: 2.x 는 엔티티에 `@searchable(entity=Ledger)` DTO(`LedgerQuery`)를 선언하면 서비스와 컨트롤러가 그 DTO 로 검색한다.
  규칙 엔진의 2.x 호환 출력은 서비스는 그렇게 했지만 컨트롤러는 로더가 `@search` 필드에서 만드는 `LedgerSrchDTO` 를 받았다.
  샘플 모델이 이 조합을 쓰지 않아 호환성 검사가 비교한 적이 없었다 - 아래 샘플 확장에서 처음 드러났다
- `ControllerBaseRules` 가 서비스와 같은 선택(`COMPAT_SERVICE_SRCH_DTO`)을 쓴다. `elcoreRulesCheck`(`RulesVerifyRunner`)도
  생성기처럼 그 선택을 기록하고 비교한다 - 전에는 생성기가 쓰지 않는 컨트롤러를 판정했다. v3 출력은 그대로다(v3 는
  컨트롤러·서비스 모두 `LedgerSrchDTO`)
- 관련 파일: `ControllerBaseRules.java`, `RulesVerifyRunner.java`

### Build (호환성 검사 - 비교하는 범위)

#### 2.x 기준 샘플이 쓰지 않던 DSL 과 조합을 더했다 - 호환성 검사가 그것도 비교한다
- 왜: `elcoreRulesCheck`·`elcoreTreeCheck` 는 `elasticore-template` 의 모델이 선언한 것만 비교한다. 9개 도메인의 실제
  프로젝트에서 나온 2.x 차이 넷이 모두 그 밖이었다
- `elasticore-template` 의 shop 도메인에 `model/coverage.yml` 을 더했다: 엔티티 `Ledger` - `@schema`, 클래스의
  `@jpa:Where`(실제 프로젝트에서 v3 가 빠뜨렸던 것), `@pattern`, `@size(min, max)`, `@notnull`, 필드 `@readonly`, 필수와
  선택 `@ManyToOne` 검색 - 와 선언한 검색 DTO `LedgerQuery`(필수 연관, `@searchBypass`). 2.x 생성기로 다시 생성한 기준
  트리를 커밋했다(새 파일 10개와 `ShopMapper`·`Q`·`ShopRepositoryHelper`·`schema.yml`)
- 결과: 위의 컨트롤러 차이 하나가 나왔고, 고친 뒤 `elcoreRulesCheck -Pstrict` 157 MATCH / 0 DIFF / 2 ACCEPTED(전 150),
  `elcoreTreeCheck` 238 generated by 2.x / 233 identical / 0 differ / 0 missing / 0 extra(전 229). 문서의 기대값도 바꿨다
- `@rollup`, `supportAI`(Spring AI 의존성이 템플릿에 없음), `string(n)` 과 `@length(min=..)` 의 조합(2.x 결함을 고친 곳이라
  일부러 다름)은 넣지 않았다. AI 도구 설명은 `AiToolDescriptionTest` 가, 그 조합은 이력의 3.0.261015-alpha1 이 다룬다
- 확인: `elasticore-template` 테스트 289개(생성된 `LedgerServiceTest` 포함), `elasticore-base` 934개.
  `RepositoryHelperTest` 의 shop 엔티티 목록에 `Ledger` 를 더했다
- 관련 파일: `elasticore-template/src/main/resources/blueprint/shop/model/coverage.yml` 과 생성된 기준 트리,
  `RepositoryHelperTest.java`, `CONTRIBUTING.md`·`docs/tour*.md`(기대값, 그리고 "아무것도 바꾸지 않으면 2.x 와 같은 코드" 를
  `output = 'v2'` 로)

### Bug Fixes (규칙 엔진 - JPA 가 아닌 모드)

#### `mode: mongo` 도메인을 JPA 코드로 쓰던 것 - 이제 `elcore3` 가 거절한다
- 왜: env.yml 의 `config.mode`(`jpa`, `mongo`, `uml`, `dsl`, 쉼표로 여럿)는 2.x 생성기가 읽어 그에 맞는 코드나 문서를 쓴다.
  규칙 엔진은 모드를 읽지 않아, `mode: mongo` 도메인에 JPA 엔티티·리포지토리·서비스를 아무 말 없이 썼다(실행해서 확인 -
  `Note.java` 등 15개 파일, 종료 코드 0). 3.0.261028-rc3 부터 플러그인의 기본값이 `elcore3` 이므로, 선언 없이 시작한 MongoDB
  프로젝트가 그대로 받게 된다
- 이제 모드에 `jpa` 가 없는 도메인이 있으면 `elcore3` 가 아무 소스도 쓰지 않고 1 로 끝나며, MongoDB 코드는 2.x 생성기의
  것이니 `elasticore { output = 'v2' }`(빌드가 `elcore` 를 부름)나 `./gradlew elcore` 를 쓰라고 알린다. `jpa` 와 함께 적은
  `uml`·`dsl` 은 쓰지 않는다고 DSL 경고로 알리고 JPA 코드는 쓴다
- 확인: `DefaultGenerationTest` - `mode: mongo` 는 1 과 그 메시지, JPA 파일 없음(수정 전에는 0 과 JPA 파일 15개로 실패),
  `mode: jpa,uml` 은 경고와 함께 JPA 코드. 샘플의 도메인은 모두 `mode: jpa`
- 관련 파일: `RulesGenRunner.java`, `DefaultGenerationTest.java`, `elasticore-env-guide.md`(모드 절)

## 3.0.261028-rc3

**플러그인의 기본값이 3.0 코드가 된다**: 아무것도 선언하지 않은 레이어드 Java 프로젝트는 `jpa-springboot-v3` 로
생성되고, 2.x 의 코드는 `output = 'v2'` 로 고른다. 2.x 가 생성한 코드를 가진 프로젝트가 아무것도 고르지 않으면 소스를
다시 쓰지 않고 멈춘다.

rc2 를 도메인 9개짜리 프로젝트에 `elcore3`·`-Pv3` 로 적용하고, 같은 모델로 2.x 출력과 비교하며 찾은 차이를 고쳤다.
2.x 생성기의 출력은 그대로다. `jpa-springboot` 번들은 2.x 와 달랐던 두 곳(선언한 검색 DTO 의 필수 연관, AI 도구 설명)이 2.x 와
같아졌다. 네 차이 모두 호환성 검사가 비교하는 템플릿 모델 밖이었으므로, CI 가 보지 않던 것을 더 찾아 막았다(생성된
TypeScript 의 컴파일, 빌드가 쓰는 `blueprint/` 파일). Ossie 시맨틱 모델의 식은 Apache Ossie 가 새로 정한 형태로
내보낸다 - `ossie-export` 를 켠 프로젝트의 `semantic_model.yml` 만 바뀐다.

### Bug Fixes (엔티티 - DSL 이 클래스에 준 어노테이션)

#### `-Pv3`·Kotlin 엔티티가 클래스의 `@jpa:` 어노테이션을 버리던 것 - soft-delete 필터가 사라졌다

```yaml
LifecycleEntity:
  meta: entity @abstract @jpa:Where(clause = "delete_yn = 'N'")
```

- 엔티티 `meta` 에 적은 `@jpa:...`(그리고 env.yml `annotations` 의 `@entity:...` 매핑)는 클래스에 적은 그대로 붙는다.
  호환 출력은 2.x 처럼 그렇게 썼지만 `-Pv3`·DDD·헥사고날과 Kotlin 엔티티에서는 경고 없이 빠졌다. 컴파일도 테스트도
  통과하는데, `@Where` 가 빠진 soft-delete 엔티티는 지운 행을 모든 조회에 다시 돌려준다.
- 이제 v3 와 Kotlin 엔티티도 규칙이 만든 클래스 어노테이션 뒤에 그 줄을 쓴다.
- 그 줄은 2.x 엔티티 템플릿이 on-demand 로 import 하는 `jakarta.persistence`·`org.hibernate.annotations` 를 기대고 쓴
  글자 그대로라, 어느 이름을 쓰는지 알 수 없다. 그래서 그런 줄이 있는 엔티티는 두 패키지를 `*` 로 import 한다. 그런 줄이
  없는 엔티티의 출력은 그대로다.
- Kotlin 은 필드의 `@jpa:` 처럼 줄을 그대로 쓴다. `X.class` 같은 Java 문법은 Kotlin 문법(`X::class`)으로 적어야 한다.

### Bug Fixes (검색 DTO - 선언한 검색 DTO 의 필수 연관)

#### 선언한 검색 DTO 가 필수(`!`) 연관의 검색 프로퍼티를 버리던 것 - 그 연관으로 하던 검색의 조건이 사라졌다

```yaml
Sale:
  fields:
    insurer: Company! @search(eq)
SaleSrchDTO:
  meta: dto @searchable(entity=Sale, pageable=true)
```

- `dto:` 로 선언한 검색 DTO 는 엔티티의 `@search(eq)` 연관을 같음 조건 프로퍼티(`private Company insurer;`)와 `toSpec`
  조건으로 갖는다. 2.x 는 필수 여부·도메인과 상관없이 그렇게 쓰는데, 호환 출력과 `-Pv3` 는 필수(`!`) 연관을 뺐다.
  컴파일은 되고, `{"insurer": {"id": ...}}` 로 하던 검색의 조건만 빠진다.
- 이제 2.x 처럼 남긴다. 선언 없이 엔티티의 `@search` 에서 만든 검색 DTO 는 2.x 와 같이 평탄화 id 만 갖고, 바뀌지 않는다.

### Bug Fixes (AI 확장 서비스 - 도구 설명의 엔티티 이름)

#### `supportAI` 도메인의 `@Tool` 설명이 라벨만 쓰던 것 - 엔티티 이름이 빠졌다

```yaml
Accident:
  meta: entity @expose -- 사고
```

- `supportAI: true` 도메인의 확장 서비스(`AccidentService`)는 서비스 메서드를 Spring AI 도구로 내보낸다. 2.x 는 도구 설명에
  엔티티 이름을 쓰고 라벨을 괄호로 곁들인다: `"Retrieves all Accident (사고) records without sorting"`. 규칙엔진은 라벨만
  썼다(`"Retrieves all 사고 records ..."`). 모델이 요청을 도구에 잇는 단서인 이름이 빠졌다.
- 이제 호환·`-Pv3`·Kotlin 모두 2.x 처럼 `이름 (라벨)` 로 쓴다. 라벨이 없는 엔티티는 이름만 쓰고, 바뀌지 않는다.
- 확장 서비스는 처음 한 번 생성하는 파일이다. 손대지 않은 파일은 다음 생성 때 새 설명으로 다시 쓰이고, 손댄 파일은 그대로다.

### Bug Fixes (TypeScript - 검색 DTO 의 연관)

#### TypeScript 검색 DTO 가 생성되지 않는 엔티티 타입을 import 하던 것 - 클라이언트가 컴파일되지 않았다

```yaml
Sale:
  fields:
    insurer: Company @search(eq)
SaleSrchDTO:
  meta: dto @searchable(entity=Sale, pageable=true)
```

- Java 검색 DTO 의 연관은 엔티티 타입(`private Company insurer;`)이고, 검색은 그 키만 보낸다: `{"insurer": {"id": "C1"}}`.
  TypeScript 검색 DTO 는 같은 타입으로 `insurer?: Company` 와 `import { Company } from './Company'` 를 썼는데,
  `elcore3ts` 는 엔티티를 쓰지 않으므로 그 모듈이 없어 `tsc` 가 TS2307 로 멈췄다.
- 이제 그 프로퍼티는 엔티티 키의 모양이다: `insurer?: { id: string }`. 키가 여럿이면 모두 담는다. 열거형과 DTO 는 전처럼
  import 한다.

### Changes (Ossie 내보내기 - 식의 형태)

#### 이식 가능한 식은 최상위 `sql` 로 쓴다. 서브쿼리 `@formula` 만 `dialects` 의 `ANSI_SQL`
- 왜: Ossie core spec 에 apache/ossie #617 이 머지되어(2026-10-26) `expression` 이 최상위 `sql` 을 갖는다.
  `sql` 은 이식 가능한 형태로 `OSSIE_SQL_2026` 으로 쓰고, `sql` 과 `dialects` 중 하나 이상이 있어야 하며(각각 단독 가능),
  둘 다 있으면 `sql` 이 우선한다. dialect 이름은 자유 문자열이 되었다. 같은 전환을 다루는 이슈가 apache/ossie #639 다
- 전: 모든 식이 `dialects` 목록 항목 하나 - 이식 가능한 식은 `dialect: OSSIE_SQL_2026`, 서브쿼리는 `dialect: ANSI_SQL`
- 후: 이식 가능한 식(컬럼 이름, 그 언어 안의 `@formula`, `metrics:` 의 지표)은 `expression: { sql: "..." }`. 서브쿼리
  `@formula` 는 전과 같은 `ANSI_SQL` 항목 하나이고 최상위 `sql` 은 없다(있으면 그것이 우선하므로). 어느 쪽인지는 전처럼
  `OssieSql.portable()` 이 가르고, 식의 본문은 한 글자도 바뀌지 않는다
- 쓰이지 않게 된 `OssieSql.dialect()` 와 `PORTABLE` 상수는 지웠다(패키지 안에서만 쓰던 것). 두 형태는
  `OssieSemanticModelExporter.expression()` 한 곳에서 쓰며 필드와 지표가 함께 쓴다
- 확인: 다시 생성한 `semantic_model.yml` - `elasticore-sample-petstore` 557 → 435줄(식 61개), `elasticore-template-ddd`
  865 → 635줄(식 115개). 두 파일 모두 `OSSIE_SQL_2026`·`ANSI_SQL`·`dialects` 0건, 최상위 `sql` 이 식 수와 같고, 식 본문을
  전후로 순서대로 대조해 176개 모두 같았으며 식 밖의 줄도 모두 같았다. Ossie 저장소의 `validation/validate.py`
  (apache/ossie main, 2026-10-27)로 두 파일과, 서브쿼리 `@formula` 를 `ANSI_SQL` 항목으로 둔 형태를 검증해 모두 통과
- 테스트: `OssieExportTest`(ddd 샘플의 모든 필드가 최상위 `sql`, `dialects`·`OSSIE_SQL_2026` 없음),
  `OssieFormulaDialectTest`(서브쿼리 `@formula` 는 `ANSI_SQL` 항목만 - 최상위 `sql` 없음, 산술 `@formula` 와 컬럼은 `sql`),
  `OssieSqlTest`(분류만), 펫스토어 `MetricsTest`(지표의 `sql` 을 실제 DB 에서 실행해 엔티티로 센 값과 비교)
- 관련 파일: `OssieSemanticModelExporter.java`, `OssieSql.java`, `OssieExportTest.java`, `OssieFormulaDialectTest.java`,
  `OssieSqlTest.java`, `MetricsTest.java`, `elasticore-rule-guide.md`(식의 형태 단락과 대응표), `dsl-guide.html`(펫스토어
  안내 페이지의 예, `guide/build_guide.py` 로 다시 생성)

### Build (CI 가 보지 않던 것)

#### 생성된 TypeScript 를 CI 가 컴파일하고, 빌드가 쓴 `blueprint/` 파일이 커밋된 것과 같은지 본다
- 왜: 9개 도메인의 실제 프로젝트에 v3 를 적용해 2.x 와 다른 곳 네 군데가 나왔다(각각의 수정은 따로).
  호환성 검사(`elcoreRulesCheck`, `elcoreTreeCheck`)는 템플릿 모델이 쓰는 것만 비교하므로 넷 모두 그 밖이었다. 같은 종류의
  빈틈 두 개를 더 찾았다
  - 펫스토어 `build.gradle` 은 "Node 가 있는 CI 작업이 `webBuild`(생성된 클라이언트와 화면의 `tsc`)를 돌린다" 고 적었지만
    그런 단계는 없었다. 생성된 TypeScript 를 CI 가 컴파일하는 것은 Kotlin 샘플의 `elcore3tsVerify` 뿐이었고, 그마저 Node 가
    없으면 건너뛰었다
  - 3.0.261028-rc3 의 Ossie 변경이 규칙 가이드를 고친 뒤 세 템플릿의 `elasticore-guide-index.md` 를 다시 만들지 않아, 색인이
    규칙 가이드를 `1-1909` 줄로, 그 뒤 절들도 옮겨지기 전의 줄 범위로 가리켰다. 빌드는 다시 만들지만 아무도 비교하지 않았다
- `ci.yml` 에 두 단계를 더했다: 빌드 뒤 모든 샘플의 `src/main/resources/blueprint` 가 커밋된 것과 같아야 하고(가이드,
  색인, Ossie 모델), `elcore3tsVerify -Pstrict`(Node 가 없으면 실패)와 펫스토어 `webBuild` 가 통과하고 펫스토어의 생성
  클라이언트(`src/main/ts`)가 커밋된 것과 같아야 한다. 세 템플릿의 색인을 다시 만들어 커밋했다
- `CONTRIBUTING.md` 에 세 가지를 적었다: 호환성 검사는 템플릿 모델이 선언한 것만 본다(건드린 기능이 거기 없으면 더한다),
  "2.x 는 이렇게 생성한다" 는 2.x 로 생성해 본 뒤에만 쓴다(그렇지 않은 주석 하나가 네 차이 중 하나를 가렸다), 빌드가 쓰는
  파일은 다시 만든 그대로 커밋한다. PR 템플릿의 체크리스트에도 같은 세 줄을 넣었다
- 확인: 로컬에서 `elcore3tsVerify -Pstrict` 와 `webBuild` 통과(`tsc --noEmit`), 템플릿 셋을 다시 생성했을 때 바뀐 것은
  세 색인뿐
- 관련 파일: `.github/workflows/ci.yml`, `.github/PULL_REQUEST_TEMPLATE.md`, `CONTRIBUTING.md`,
  `elasticore-sample-petstore/build.gradle`, 세 템플릿의 `blueprint/elasticore-guide-index.md`

### Changes (기본값 - 3.0 코드)

#### 아무것도 선언하지 않으면 3.0 코드. 2.x 의 코드는 `output = 'v2'`
- 왜: 3.0 인데 레이어드 Java 프로젝트가 3.0 코드(`jpa-springboot-v3`)를 받으려면 실행할 때마다 `-Pv3` 를 주거나 도메인마다
  `publisher.rules` 를 적어야 했다. 새로 시작하는 사람은 그 스위치를 알 길이 없었다. 플러그인은 `elasticore { }` 블록이
  없으면 2.x 생성기(`elcore`)를 불렀고, `architecture = 'layered'` 를 적어도 2.x 호환 코드였다
- 전: 선언 없음 → 빌드가 2.x 생성기를 부름. `architecture = 'layered'` → `elcore3` 의 `jpa-springboot`(2.x 호환).
  v3 는 `-Pv3` 또는 `publisher.rules: [jpa-springboot-v3]`. 초기화 마법사는 레이어드 Java 도메인의 `env.yml` 에
  `rules: [jpa-springboot]` 를 적었다
- 후: 선언 없음과 `architecture = 'layered'` → 빌드가 `elcore3` 를 부르고 `jpa-springboot-v3`. 마법사는
  `rules: [jpa-springboot-v3]` 를 적고 2.x 의 코드를 주석 줄(`#rules: [jpa-springboot]`)로 함께 보인다. 마법사가
  마지막에 알려 주는 다음 단계도 레이어드 Java 에 플러그인과 `elcore3` 를 안내한다 - 전에는 "`./gradlew elcore` 를 다시
  실행" 이었는데, 그 2.x 생성기는 `publisher.rules` 를 읽지 않아 env.yml 이 v3 라고 하는데 2.x 코드가 나왔을 것이다.
  env.yml 의 "이 규칙은 규칙 엔진이 읽는다" 주석도 모든 아키텍처에 적는다. 헥사고날·DDD·Kotlin 은 전과 같다(원래 v3)
- 2.x 의 코드를 유지하는 길: `elasticore { output = 'v2' }` - 빌드가 2.x 생성기를 부르고, `elcore3` 를 손으로 돌려도
  2.x 호환 코드(`elasticore.rules.v2`)를 쓴다. 한 번만은 `-Pv2`. 도메인 `env.yml` 이 `jpa-springboot` 를 적고 있으면
  (전의 마법사가 만든 프로젝트) 그 실행도 2.x 호환이다. `output = 'v2'` 를 헥사고날·DDD·Kotlin·프로젝트 규칙과 함께
  쓰거나, `-Pv2` 와 `-Pv3` 를 함께 주면 거절한다 - 2.x 의 코드는 레이어드 Java 뿐이다. `-Pv2`/`output = 'v2'` 인데
  도메인 `env.yml` 이 v3 에만 있는 규칙(`jpa-springboot-v3`, `ddd-...`)을 적었거나 프로젝트 규칙이 더해졌으면 `elcore3` 도
  거절한다(전에는 그런 도메인이 항상 v3 를 골랐으므로, 그 규칙이 2.x 템플릿에 그려지는 일이 없었다)
- **2.x 프로젝트를 조용히 바꾸지 않는다**: 아무것도 고르지 않은 실행이 `src/main/java` 에서 2.x 가 생성한 파일(첫 줄
  해시의 버전이 `_V1.x`/`_V2.x`)을 찾으면, 소스를 하나도 쓰지 않고 1 로 끝나며 `output = 'v2'`(유지)와
  `output = 'v3'`(이동) 중 고르라고 알린다. 버전만 올렸는데 소스가 다시 쓰이고 `like` 검색, 검색 키워드, 접근 검사,
  오류 응답의 동작이 바뀌는 것을 막는다. `output = 'v3'`/`-Pv3` 를 주면 2.x 파일이 있어도 3.0 코드를 쓴다.
  3.0 사전 릴리스를 2.x 호환 모드로 돌린 프로젝트는 파일이 `_V3.0` 이라 해시 줄로는 알아보지 못하므로, 마지막
  `elcore3` 실행이 `build/elasticore/server.mode` 에 남긴 `compatible` 도 같이 본다. 남은 한계: 그런 프로젝트가 `clean`
  으로 그 파일을 지웠고 `env.yml` 에 `jpa-springboot` 도 없으면 3.0 코드가 된다(마법사가 만든 프로젝트는 그 줄이 있다).
  `-PlistRules`(아무것도 쓰지 않는 목록)는 멈추지 않는다. 블루프린트가 없는 새 프로젝트에서 `elcore3` 는 1 로 끝나며
  이제 `./gradlew elcore` 로 도메인을 만들라고 알린다(빌드가 2.x 생성기 대신 `elcore3` 를 부르므로)
- TypeScript 클라이언트(`elcore3ts`)는 서버를 따른다: 아무것도 고르지 않았으면 v3 서버로 보되, 2.x 가 생성한 소스가
  있거나 마지막 `elcore3` 가 `build/elasticore/server.mode` 에 2.x 호환이라고 남겼으면 2.x 서버로 본다
- 저장소: `elasticore-template`(2.x 기준 트리)은 `output = 'v2'` 를 적고, 그 `elcoreTreeGenerate` 는
  `elasticore.rules.v2` 로 생성한다. 2.x 호환 검사 결과는 같다(`150 MATCH / 0 DIFF / 2 ACCEPTED`,
  `0 differ / 0 missing / 0 extra`), 템플릿의 생성 소스도 그대로다
- 테스트: `DefaultGenerationTest`(선언 없음 → v3, `-Pv2` → 2.x 호환, `env.yml` 의 `jpa-springboot` → 2.x 호환,
  선언한 `layered` → v3, 둘 다 → 거절, `-Pv2` + 헥사고날 → 거절, 2.x 파일이 있으면 멈추고 아무 소스도 쓰지 않음,
  `-Pv3` 면 2.x 파일이 있어도 v3, 해시 줄 판별, `-Pv2` + v3 규칙을 적은 도메인·프로젝트 규칙 → 거절,
  `server.mode` 의 `compatible` → 멈춤, 2.x 파일 위의 TypeScript → 2.x 서버를 따름, 목록 → 멈추지 않음),
  `ElastiCorePluginTest`(선언 없음 → `:elcore3`, `output = 'v2'` →
  `:elcore` 와 `elasticore.rules.v2`, `output = 'v3'` 전달, 헥사고날과 함께 쓴 `v2`·모르는 값 거절),
  `InitializationTest`(마법사의 `jpa-springboot-v3` 와 2.x 주석 줄, 레이어드의 다음 단계가 `elcore3`). 2.x 호환 출력을 만든다고 하던 테스트는
  `elasticore.rules.v2` 를 준다 - 기본값만 바꾸면 그중 셋(`RequiredReferenceDtoTest`, `SignInAndReferencesTest`,
  `WritePathsTest`)은 실패했고, 여섯(`EntityNativeClassAnnotationTest`, `DeclaredSearchDtoRelationTest`,
  `AiToolDescriptionTest`, `DtoNamedLikeASearchDtoTest`, `PortModelImportsTest`, `ModelNamedLikeAnAnnotationTest` 의
  레이어드 Java)은 **v3 로 돌면서 통과했다** - 2.x 호환 쪽을 더는 검사하지 않으면서. 모두 명시했고, 레이어드 Java 의
  `@Comment` 검사는 v3 와 2.x 호환 둘 다 본다(`theRuleEngineWritesItInFullOnLayeredJavaKeeping2x`)
- 관련 파일: `ElastiCoreExtension.java`(`output`), `ElastiCorePlugin.java`(`keepsTwoX`, `-Pv2`), `RulesGenRunner.java`
  (모드 결정, `domainKeepingTwoX`, `twoXGenerated`, `defaultBundle`), `RulesConfig.java`(`bundleFor` - 레이어드 Java 는
  `jpa-springboot-v3`), `InitializationHelper.java`, `elasticore-template/build.gradle`, `elasticore-migration-guide.md`,
  `elasticore-rule-guide.md`(어느 세대의 코드인가), `elasticore-env-guide.md`, `elasticore-annotation-guide.md`,
  `README.md`·`README.ko.md`·`docs/tour*.md`(현재 상태), `CONTRIBUTING.md`

## 3.0.261027-rc2

펫스토어에 팀 규칙 모듈(`elasticore { rules = [...] }`)을 붙여 rc1 로 생성·빌드·실행하며 규칙 가이드의 예제를 하나씩 다시
확인했다. 규칙이 쓴 것이 실행 중인 애플리케이션에 닿지 않는 두 자리를 고쳤다. 런타임 라이브러리는 Spring Boot 자동 구성을
갖게 되어 포트에 `@EnableElastiCore` 가 필요 없다. 그것을 실제 포트로 확인하다 찾은, 값 하나를 받거나 돌려주는 DBMS 포트
메서드(`Long countProducts()`, `nameOf(Long id)`, `return: decimal`)의 세 버그를 고쳤다. v3 검색 조건은 `gt`·`gte` 같은
비교 키워드와 같지 않음(`!=`, `ne`)을 받는다. Ossie 시맨틱 모델은 프로젝트가 정한 이름(`ossieName`)을 쓸 수 있다.
2.x 생성기와 `jpa-springboot` 번들의 출력은 그대로다(컴파일되지 않던 `return: decimal` 포트만 바뀐다).

### Bug Fixes (팀 규칙)

#### v3 검색 엔드포인트가 요청 본문을 검증한다 - 규칙이 검색 DTO 에 붙인 제약이 동작
- 규칙이 검색 DTO 에 `@Size(min = 2)` 같은 제약을 붙여도(rule dev guide 6장의 "LIKE 검색은 2글자 이상") 생성된
  `search(@RequestBody XxxSrchDTO searchDTO)` 에 `@Valid` 가 없어 검사되지 않았다. 한 글자 검색이 200 으로 테이블을 훑었다
- 이제 v3·DDD·헥사고날(`v3/control.tmpl`)과 Kotlin(`kt/control.tmpl`)의 검색 엔드포인트가 `@Valid @RequestBody` 로 받는다.
  위반은 생성(`create`)과 같은 400 문제 응답이다(`code: "invalid"`, `fieldErrors[].code: "Size"`) - 펫스토어에서
  `{"name":"a"}` 로 확인
- 동작 변경 없음: 지금까지 생성되는 검색 DTO 에는 검증 어노테이션이 없다(이 저장소의 v3 샘플과 펫스토어의 모든
  `*SrchDTO`, 그리고 `srchdto` target 의 모든 내장 규칙을 확인). 응답이 달라지는 것은 검색 DTO 에 제약을 붙이는 규칙,
  손으로 고친 검색 DTO, 그리고 `env.yml` 의 `sortableInterface`/`pageableInterface` 로 지정한 프로젝트 인터페이스의 getter 에
  검증 어노테이션(예: `@Max(100) int getPageSize()`)이 있을 때뿐이다 - 그때는 그 제약이 이제 400 으로 검사된다
- 컨트롤러는 개발자 파일이라 이미 고친 컨트롤러는 그대로다. 한 번도 고치지 않은 컨트롤러는 다음 생성에 맞춰진다
- 2.x 출력(`control.tmpl`)은 그대로
- 파일: `elasticore-template/v3/control.tmpl`, `elasticore-template/kt/control.tmpl`, 테스트 `ProjectRuleReachTest`

#### `addMethod` 가 메서드 없는 클래스에서 버려지던 것
- `ctx(e).addMethod(...)` 는 클래스의 닫는 중괄호 앞에 메서드를 더하지만, 메서드 구역(`@Operation` 이나 `/**` 로 시작하는
  메서드)을 하나도 찾지 못한 클래스 - 필드만 있는 엔티티(펫스토어의 `Category`, `Country`, `Customer`) - 에서는 아무 말 없이
  버렸다
- 이제 구역이 없어도 마지막 멤버 뒤, 닫는 중괄호 앞에 쓴다. 더하는 것도 빼는 것도 없는 파일은 이전과 바이트까지 같다
- enum·interface·record(Kotlin `object` 포함)에서도 버려졌다 - 선언 줄을 `class` 로만 찾았기 때문. 이제 그 선언도 찾는다
  (ElastiCORE 의 enum 스켈레톤은 상수 목록 뒤에 늘 `;` 와 생성자가 있어, 더한 메서드가 그 뒤에 온다)
- 두 파일로 나뉜 루트에서는 프로젝트의 엔티티 규칙이 개발자 클래스(target `behavior`)에도 돈다 - 클래스 어노테이션을
  위해서다. 그렇게 빌려 도는 규칙의 `addMethod` 는 개발자 클래스에 쓰지 않는다 - 생성기 쪽 `XxxBase` 에 있고 개발자
  클래스가 상속받는다. `target = "behavior"` 로 쓴 규칙은 그대로 개발자 클래스에 더한다(처음 생성될 때)
- 파일: `MethodSections.java`, `SourceCode.java`, 테스트 `ProjectRuleReachTest`. 펫스토어에서 `Category`·`Country`·
  `Customer`·`Item`·`Product`·`PurchaseOrderBase` 에 한 번씩 생기는 것을 확인

### Bug Fixes (DTO - 이름이 SrchDTO 로 끝나는 일반 DTO)

#### `meta: dto` 인 `ReportSrchDTO` 가 `class ReportSrchDTODTO` 로 생성되던 것
- 검색 DTO 가 아닌 일반 DTO 라도 이름이 `SrchDTO` 로 끝나면(보고서 조회 조건 `TransactionStatSrchDTO` 같은 것) 규칙엔진이
  검색 DTO 로 보고 검색 DTO 이름만 정했다. 파일은 일반 DTO 규칙이 쓰는데, 그 규칙이 읽는 일반 DTO 이름은 비어 있어 기본값
  `이름 + "DTO"` 가 되었다 - `ReportSrchDTO.java` 안에 `public class ReportSrchDTODTO`, 컴파일 오류(`class ... is public,
  should be declared in a file named ...`). 2.x 호환 출력(`elcore3`), `-Pv3`, Kotlin, TypeScript 모두 같았다
- 이제 검색 DTO 인지는 `@searchable` 만으로 정한다 - 규칙엔진이 어느 파일 규칙을 쓸지 고를 때와 같은 기준이다. 엔티티의
  `@expose`/`@service` 에서 로더가 만드는 `XxxSrchDTO` 는 늘 `@searchable` 을 달고 있어 그대로다
- 2.x 생성기(`elcore`)는 선언한 이름을 그대로 써 왔다 - 2.x 호환 출력이 그것과 같아진다
- 파일: `SpecFactory.java`, 테스트 `DtoNamedLikeASearchDtoTest`

### Documentation (규칙 API)

- 공개 규칙 API(`io.elasticore.base.model.pub.rules`)의 javadoc 에 남아 있던 "PROTOTYPE - part of the 3.0 publisher extension
  experiment" 문구를 지웠다(30개 파일). `Rule` 은 메서드 모양 여섯 가지와 팀 규칙이 기본 규칙 뒤에 돈다는 것을, `EntitySpec`·
  `SourceCode` 는 실제로 하는 일을 적는다
- rule dev guide 6장: `-PlistRules` 로 확인할 규칙 이름 중 없는 `acme-example` 을 `acme-internal` 로, 없는 메서드
  이름에 대한 로그를 실제 문장(`… is not declared in SalesOrderRepository (repository file); its annotations were not written`)으로
- 사이트의 팀 규칙 문서(https://www.elasticore.io/docs/guides/team-rules)를 펫스토어 예제로 다시 썼다 - 11개 페이지, 모든
  예제를 펫스토어 사본에서 생성·빌드·실행해 확인
- rule dev guide 10장 "규칙 API 레퍼런스": `@Rule` 속성과 메서드 모양, `EntitySpec`·`FieldSpec`·`DomainSpec`·`EnumSpec`·
  `PortSpec`, `SourceCode`(`addMethod`, `excludeMethod` 와 엔드포인트 이름, `removeField`, `dontMake`, `var`), `FieldCode`,
  `AnnoCode`, `ModelCheck`, 새 파일(`ArtifactContributor`), `Syntax`. 사이트에만 있던 것을 프로젝트에 복사되는 가이드에도
  두었다 - 프로젝트 안에서 일하는 코딩 에이전트는 사이트를 읽지 않는다. `excludeMethod` 의 이름과 엔드포인트 표, v3 와 2.x 호환
  번들의 차이(v3 는 `deleteBatch`·`batchUpdate` 를 함께 뺀다)는 이전에 어느 가이드에도 없었다

### New Features (가이드 색인)

#### `blueprint/elasticore-guide-index.md` - 가이드의 절마다 한 줄
- 생성할 때 가이드를 복사한 뒤, 복사된 가이드에서 색인을 쓴다: 절마다 `<가이드>:<첫 줄>-<끝 줄>  <제목>  [다루는 단어]`.
  제목은 `##`~`####`, 코드 블록 안의 `#` 줄은 제목이 아니다. 단어는 절의 코드 표기(`@search(like)`, `publisher.rules`,
  `excludeMethod`)와 예제의 DSL 어노테이션(코드 블록 안의 `@Override` 같은 Java 어노테이션은 빼고)이고, 경로·명령·JSON·
  파일 이름은 넣지 않는다(단어 목록은 160자 안). 코드 블록은 연 표시(백틱 또는 물결, 길이)와 같은 표시로만 닫힌다고 본다.
  줄 끝은 운영체제와 상관없이 `\n`
- 왜: 가이드 아홉 개는 합쳐 50만 바이트가 넘고 그중 규칙 가이드만 1,889줄이다. 가이드를 단어로 검색하면 줄 번호는 나오지만 그 절이
  어디서 끝나는지는 모른다 - 코딩 에이전트가 너무 많이 읽거나 다시 읽는다. 색인을 단어로 검색하면 읽을 범위가 나온다
  (`@access` → `elasticore-annotation-guide.md:1864-2005  ## @access / @owner — 누가 무엇을 할 수 있는가`)
- 색인은 검색용이다(약 570줄). 줄 번호가 옆의 가이드와 같은 버전에서 나오므로 낡지 않는다
- 새 파일 하나가 `blueprint/` 에 더해질 뿐, 생성 코드는 바뀌지 않는다. 쓰지 못하면 경고만 남긴다
- 파일: `GuideIndex.java`, `GuideDeployer.java`, 테스트 `GuideIndexTest`

### New Features (Ossie 내보내기 - 모델 이름)

#### `elasticore { ossieName = '...' }` - 시맨틱 모델의 `name` 을 프로젝트가 정한다
- `semantic_model.yml` 의 `name` 은 도메인 이름을 `_` 로 이은 것이라, 도메인 아홉 개인 실제 프로젝트에서는 85자였다 -
  BI·AI 도구가 모델을 이 이름으로 보여 준다
- 플러그인의 `ossieName`(플러그인 없이는 `-Delasticore.ossie.name`)이 있으면 그것이 `name` 이다. 영문·숫자·`_` 가 아닌
  글자는 `_` 로 바꾸고 그렇게 바꿨다고 경고한다(`[ossie] model name 'acme-erp' is written as acme_erp`).
  `description` 과 `custom_extensions` 의 `domains` 는 그대로 도메인을 적는다
- 동작 변경 없음: 적지 않으면 이전과 같은 이름이다 - 이 저장소의 두 `semantic_model.yml` 도 그대로
- 파일: `OssieSemanticModelExporter.java`, `ElastiCoreExtension.java`, `ElastiCorePlugin.java`,
  `elasticore-rule-guide.md`(Ossie 내보내기 절), 테스트 `OssieModelNameTest`, `OssieExportTest`(이름을 적지 않은 ddd 샘플의
  이름), `ElastiCorePluginTest.theOssieNameIsPassed`

### Improvements (런타임 - `@EnableElastiCore` 없이)

#### 런타임 라이브러리의 Spring Boot 자동 구성 - 포트가 어노테이션 없이 빈이 된다
- 런타임 프록시 포트(HTTP `@ExternalService`, DBMS `@DbmsService`)는 애플리케이션 클래스에 `@EnableElastiCore` 가 있어야
  빈이 되었다. 빠뜨리면 기동이 `No qualifying bean of type 'ShopReportPort'` 로 멈췄다 - 엔티티·로그인·오류 응답은 생성
  코드가 스스로 등록하는데 포트만 한 줄이 더 필요했고, 사람도 코딩 에이전트도 그 한 줄을 빠뜨렸다
- 이제 `elasticore-springboot3`·`elasticore-springboot4` 가 `META-INF/spring/...AutoConfiguration.imports` 로
  `io.elasticore.springboot.autoconfigure.ElastiCoreAutoConfiguration` 을 낸다. 의존성에 있으면 Spring Boot 가 기동할 때 읽고,
  `@SpringBootApplication` 클래스의 패키지와 그 아래(Spring Boot 의 auto-configuration packages - 엔티티·리포지터리를 찾는
  곳)에서 포트를 찾는다. **포트가 있을 때만** `@EnableElastiCore` 가 하던 것을 한다: 포트 등록, `EntityManagerFactory` 가 하나(또는
  `@Primary` 하나)이면 `DbTransactionGateway`, `ApplicationContextProvider`, 기동 배너. 포트가 없는 애플리케이션에는 아무 빈도
  더하지 않는다
- 이미 있는 것이 이긴다: `@EnableElastiCore` 가 함께 있어도 포트·빈은 한 번씩만(같은 포트는 처음 등록한 것), `@ComponentScan
  ("io.elasticore.springboot3")` 나 `@Bean` 으로 등록한 `DbTransactionGateway`(`DbmsSqlExecutor`)·`ApplicationContextProvider`
  가 있으면 자동 구성은 만들지 않고(`@ConditionalOnMissingBean`), 포트에 직접 선언한 `@Bean` 은 예전처럼 프록시를 대신한다.
  자동 구성 클래스는 `io.elasticore.springboot3` 밖의 패키지에 둔다 - 그 패키지를 컴포넌트 스캔하는 프로젝트가 자동 구성을 자기
  구성으로 읽지 않게
- 끄기: `elasticore.autoconfigure.enabled=false`(그때는 `@EnableElastiCore` 로 등록한다). 배너만 끄기:
  `elasticore.banner.enabled=false` - `@EnableElastiCore` 로 등록한 배너에도 적용되고, `false` 가 아닌 값은 무시한다(기동을
  멈추지 않는다)
- 기본으로 켠 이유(opt-in 원칙의 예외, 저장소 주인이 정함): 생성 코드는 한 줄도 바뀌지 않는다(`elcoreRulesCheck`·
  `elcoreTreeCheck` 그대로). 포트가 없는 애플리케이션 - 2.x 로 생성한 대부분의 프로젝트 - 에 생기는 것은 기동 때 루트 패키지를
  한 번 훑는 스캔(Reflections, 결과는 자동 구성과 등록기가 함께 쓴다)뿐이다. 포트가 있는 애플리케이션에서 달라지는 것:
  - 어노테이션을 빠뜨려 `No qualifying bean` 으로 멈추던 애플리케이션이 뜬다
  - `@EnableElastiCore` 가 애플리케이션 클래스보다 아래 패키지의 클래스에 붙어 있었다면, 이제 애플리케이션 클래스 패키지의
    포트도 등록된다
  - `EntityManagerFactory` 가 둘 이상이고 `@Primary` 가 없으면 `DbTransactionGateway` 를 만들지 않는다(어느 쪽인지 정할 수 없다)
    - DBMS 포트를 쓰려면 하나를 `@Primary` 로 하거나 `DbmsSqlExecutor` 를 직접 등록한다. `@EnableElastiCore` 로는 그 구성이
    기동부터 실패했다(`DbTransactionGateway` 생성자가 하나를 요구)
- 함께 고친 것(어노테이션으로 등록할 때도 같다):
  - 단순 이름이 같은 포트 둘(`shop.NotificationPort`, `billing.NotificationPort`)은 기동이 `BeanDefinitionOverrideException` 으로
    멈췄다. 이제 그런 포트는 모두 전체 이름으로 등록하고 경고를 남긴다(타입으로 주입하거나 `@Qualifier("<전체 이름>")`). 어느 하나만
    짧은 이름을 갖게 하지 않는 것은 그것이 클래스 경로를 읽는 순서로 정해지기 때문이다. 포트는 이름 순으로 등록한다
  - 포트 프록시는 빈(DBMS 실행기, HTTP 인증·변환기)을 자기가 만들어진 빈 팩토리에서 찾는다. 전에는 정적
    `ApplicationContextProvider` 에서 찾아, 마지막으로 뜬 다른 컨텍스트를 보거나 닫힌 컨텍스트에 물어 `has been closed already` 로
    실패할 수 있었다(테스트가 컨텍스트를 여럿 띄울 때). 정적 참조는 빈 팩토리 없이 만든 프록시만 쓰고, 자기 컨텍스트가 닫히면
    놓는다(그 사이 다른 컨텍스트가 들어오지 않았다면)
  - 자동 구성의 조건과 등록기가 함께 쓰는 스캔 결과는 등록기가 쓴 뒤 버린다 - 남겨 두면 포트 클래스와 그 클래스 로더를 붙잡아,
    devtools 로 재시작할 때마다 이전 로더가 메모리에 남는다
- 확인: 포트 두 개(DBMS `countProducts()`, HTTP `getProduct(id)` - 같은 서버를 부름)가 있는 헥사고날 프로젝트를 로컬 빌드로 생성해,
  `@EnableElastiCore` 없이 Spring Boot 4.0.8 과 3.5.16 에서 기동·호출(`{"countBySql":2,"nameByHttp":"Pen"}`), 3.5.16 의
  `@SpringBootTest` 에서 두 포트 주입과 호출, `elasticore.autoconfigure.enabled=false` 이면 `No qualifying bean` 으로 실패,
  어노테이션과 함께면 배너 한 번·응답 그대로. 단위 테스트는 Boot 3·4 런타임 모두에서: 어노테이션 없이 포트 등록, 포트 없으면 빈
  없음, `EntityManagerFactory` 없음·하나·둘·`@Primary` 하나, 어노테이션·기본 필터의 컴포넌트 스캔·직접 선언한 빈과 함께일 때
  하나씩, 같은 단순 이름의 포트 둘, 끄기, 닫힌 컨텍스트, imports 파일
- 남은 것: 포트를 애플리케이션 클래스 패키지 밖에 두는 구성(`@SpringBootApplication(scanBasePackages = ...)` 로 다른 패키지를
  스캔하는 경우 포함)은 엔티티 스캔처럼 찾지 못한다 - `@AutoConfigurationPackage(basePackages = ...)` 나 그 패키지 클래스의
  `@EnableElastiCore`, 또는 `@Bean` 으로 등록한다(포트 가이드 5장)
- 파일: `ElastiCoreAutoConfiguration.java`, `AutoConfiguration.imports`, `PortServiceRegistrar.java`(포트 스캔을 한 번,
  찾는 패키지를 하위 클래스가 정함, 이름 충돌), `ApplicationContextProvider.java`, `HttpServiceProxyFactory.java`·
  `DbmsServiceProxyFactory.java`(자기 빈 팩토리에서 찾기), `ElastiCoreBannerPrinter.java`,
  `EnableElastiCore.java`(javadoc), 테스트 `ElastiCoreAutoConfigurationTest`, `ElastiCoreBannerPrinterTest`. 포트 가이드 2장 머리·5장,
  규칙 가이드의 "어댑터는 빈이어야 합니다"

### Bug Fixes (DBMS 포트 - 값 하나를 받고 돌려주는 메서드)

#### `long countX()`·`String`·`BigDecimal` 을 돌려주는 DBMS 포트 메서드
- 포트 가이드 3.3 은 `return: long`·`string`·`boolean` 을 단건 조회로 적고 있지만, 런타임은 결과 행을 늘 DTO 로 만들었다
  (`outputType.getDeclaredConstructor().newInstance()` 에 열 이름의 필드를 채움). 그래서 숫자·불린은 호출마다
  `Failed to map tuple to DTO: Long` 으로 실패했고, `String` 은 빈 문자열(`new String()`), `java.util.Date` 는 호출한 시각을
  돌려주었다. 샘플은 그런 메서드(`ShopReportService.countOrdersByStatus`)를 선언만 하고 부르지 않아 드러나지 않았다
- 이제 반환 타입이 값 하나(기본형과 그 래퍼, `Number` - `BigDecimal`·`BigInteger` 포함 -, `String`, `Boolean`, `Character`,
  `java.time` 의 날짜·시간, `java.util.Date`)이면 행의 첫 열을 그 타입으로 바꿔 돌려준다. 숫자 열은 `BigDecimal`·`BigInteger` 로도,
  한 글자 열은 `char`·`Character` 로도 바꾼다. 열 값이 NULL 이면 `null`, 행이 없으면 단건 조회는 `null`
- 생성되는 포트 인터페이스는 래퍼 타입(`Long countProducts()`)이다. 손으로 기본형(`long`)으로 선언하면 결과가 없을 때 프록시가
  돌려주는 `null` 이 `NullPointerException` 이 된다 - 래퍼로 선언하거나 SQL 에서 `COALESCE` 로 값을 정한다
- 바꾸지 못하는 것(전처럼 DTO 로 만들려다 실패): enum, `UUID`, `byte[]`. 시각 열(`Timestamp`)을 `LocalDate`·`OffsetDateTime` 으로
  받는 것도 아직이다
- 단건 조회가 첫 값을 `stream().findFirst()` 로 꺼내, 값이 NULL 이면(`SUM` 이 빈 집합) `NullPointerException` 이었다. 이제 `null`
- DTO 와 `Map` 을 돌려주는 메서드는 그대로
- 파일: `DbTransactionGateway.java`, 테스트 `DbTransactionGatewayValueTest`(H2 에서 COUNT → `Long`·`long`·`Integer`·`BigDecimal`,
  이름 → `String`(빈 문자열이 아님), 합계 → `BigDecimal`, 불린, `Double`, 한 글자 → `Character`·`char`, NULL 값 → `null`, 행 없음 →
  빈 목록)

#### 값 하나를 받는 DBMS 포트 메서드 - `nameOf(Long productId)`
- 파라미터가 하나이면 프록시는 기본형이나 `String` 만 값으로 보고, 나머지는 객체로 보아 getter 로 SQL 의 `:이름` 을 채웠다. 생성되는
  포트는 DSL 의 `long` 을 `Long` 으로 선언하므로, `long` 파라미터 하나짜리 메서드는 `Long.getProductId()` 를 찾다 아무것도 묶지
  못했다 - 오류 없이 결과가 비었다(`WHERE product_id = NULL`)
- 이제 값 타입(Spring 의 `BeanUtils.isSimpleValueType` - 기본형과 래퍼, `String`, 숫자, 날짜·시간, enum, `UUID` 등)은 파라미터
  이름으로 묶는다. DTO 하나, `Map`, `List`(배치)는 그대로
- 달라지는 경우 하나: enum 파라미터 하나를 그 getter 로 묶던 SQL(`:code` 에 `Status.getCode()`)은 이제 파라미터 이름으로 묶인다 -
  그런 SQL 은 `:status` 로 쓰거나 DTO 로 감싼다. 이름으로 묶으려면 클래스에 파라미터 이름이 남아 있어야 한다(`-parameters`,
  Spring Boot 의 Gradle·Maven 플러그인이 켠다)
- 파일: `DbmsServiceProxyFactory.java`, 테스트 `DbmsPortInputTest`

#### 포트의 `return: decimal` 이 Java 에 없는 타입 `decimal` 로 생성되던 것 (생성기)
- 포트 메서드의 반환 타입은 `long`·`string` 같은 일곱 단어만 래퍼 타입으로 바꾸고 나머지는 그대로 썼다. DSL 가이드가 필드 타입으로
  적는 `decimal`·`bigdecimal` 을 반환 타입에 쓰면 `decimal totalPrice();` 가 생성되어 컴파일되지 않았다
- 이제 `java.math.BigDecimal` 로 쓴다(포트 인터페이스는 `java.math` 를 import 하지 않아 전체 이름). `List<decimal>`·`Page<decimal>`
  안의 것도. 2.x 생성기와 규칙 엔진이 같은
  로더를 쓴다. 이전 출력이 컴파일되지 않던 경우만 바뀐다 - `elcoreRulesCheck`·`elcoreTreeCheck` 그대로
- 파일: `PortModelLoader.java`, 테스트 `PortReturnTypeTest`(생성해서 `Long`·`java.math.BigDecimal`·`String` 확인)
- 확인(위 세 가지): 같은 헥사고날 프로젝트에 `totalPriceAbove(minPrice: long): decimal`, `nameOf(productId: long): string` 을 더해
  Boot 4.0.8 에서 호출 - `{"totalAbove2":3.00,"totalAbove100":null,"name":"Pen"}`, 없는 id 는 `"name":null`

### Bug Fixes (포트 - 엔티티 없는 도메인)

#### 엔티티가 없는 도메인의 포트가 없는 패키지를 import 하던 것
- 포트와 DTO 만 있는 도메인(SQL 로 읽기만 하는 대시보드 같은 것)의 포트에 `import <namespace.entity>.*;` 가 들어가
  `package ... does not exist` 로 컴파일되지 않았다. `env.yml` 의 `namespace.entity` 는 그런 클래스가 갈 자리를 말할 뿐인데,
  규칙엔진은 그것만 보고 엔티티·DTO 패키지를 늘 import 했다. DTO 도 없는 도메인은 `dto.*` 로 같았다
- 이제 2.x(`PortSrcPublisher`)처럼 그 종류의 모델이 도메인에 하나라도 있을 때만 그 패키지를 import 한다 - enum 은 이미
  그랬다. 엔티티가 있는 도메인의 포트는 그대로다
- 2.x 생성기(`elcore`)는 원래 그렇게 쓴다 - 2.x 호환 출력(`elcore3`)과 `-Pv3`, Kotlin 포트가 그것과 같아진다
- 파일: `SpecFactory.java`(`fromPort`), 테스트 `PortModelImportsTest`

### Improvements (검색 조건 - 비교 키워드와 같지 않음)

- `@search(gt)`·`gte`·`ge`·`lt`·`lte`·`le` 가 v3 모드(헥사고날·DDD, `-Pv3`, Kotlin)에서 `>`·`>=`·`<`·`<=` 와 같다. 지금까지는
  "is not a search condition, so it searches by equality" 경고와 함께 정확 일치였다. 2.2 사이트 문서가 키워드를 동작하는 것처럼
  적었고, 에이전트도 기호만큼 키워드를 쓴다. 대소문자 무관(`GTE`)
- `@search(!=)`·`ne`·`neq` 는 같지 않음(`c.notEqual(...)`) - v3 모드 전용. 검색 DTO 의 설명은 "Field does not equal value."
  SQL 의 `<>` 라 컬럼이 비어 있는(NULL) 행은 나오지 않는다 - 빈 값까지 찾으려면 `isnull` 필드를 함께 둔다
- 동작 변경: 이 키워드나 `!=` 를 쓴 v3 프로젝트만 바뀐다 - 경고와 함께 정확 일치로 찾던 것이 쓴 대로 비교한다. 2.x 생성기와
  2.x 호환 출력(레이어드 Java)은 그대로 정확 일치(키워드를 받으면 2.x 호환이 깨진다) - `elcoreRulesCheck`·`elcoreTreeCheck` 그대로
- 기호 `>=`·`<=` 가 DSL 파서를 지나 Criteria 에 닿는다는 테스트는 2.x 호환 모드에만 있었다 - v3·Kotlin 에도 더했다
- 경고 문구가 받는 별칭(`ge`, `le`, `neq`)을 모두 적는다
- 파일: `SearchDtoBaseRules.java`(`effectiveCondition`, `symbolOf`), `MapperSpecRules.java`, `StringUtils.java`, 테스트
  `MapperSpecTest.comparisonSymbolsReachTheCriteria`·`comparisonKeywordsAreTheSymbols`
- 확인: 헥사고날 Java 프로젝트(Boot 4.0.8)에 `stock: int @search(gte)`, `weight: int @search(<=)`, `code: string(10) @search(ne)` 를
  더해 생성·빌드·실행. 상품 셋(재고 1·5·9, 무게 10·20·30, 코드 X·Y·X)에 `{"stock":5}` → B·C, `{"weight":20}` → A·B,
  `{"code":"X"}` → B. 경고 없음

### Bug Fixes (서비스 - 같은 패키지의 같은 이름)

#### 엔티티 이름이 서비스 패키지의 클래스 이름과 같으면 코어 서비스가 다른 타입을 가리키던 것
- 엔티티 `AccidentService`(사고의 수리 항목)와 엔티티 `Accident` 가 함께 있으면, 서비스 패키지에 `Accident` 의 확장
  서비스 `AccidentService` 가 생기고 엔티티 `AccidentService` 의 `AccidentServiceCoreService` 도 같은 패키지에 놓인다.
  `-Pv3`·DDD·헥사고날과 Kotlin 의 코어 서비스는 엔티티를 `import ...entity.*;` 로만 가져오는데, 같은 패키지의 클래스가
  와일드카드 import 보다 앞서므로(Java·Kotlin 같음) 코어 서비스 안의 `AccidentService` 가 모두 서비스 클래스로 읽혀
  컴파일되지 않았다(한 프로젝트에서 22건)
- 이제 코어 서비스와 서비스 테스트, AI 확장 서비스가 엔티티를 이름으로도 import 한다(`import ...entity.AccidentService;`) -
  2.x 의 `service.tmpl` 이 쓰는 방식이고, 이름 import 는 같은 패키지보다 앞선다. 이름이 겹치지 않는 프로젝트는 그 한 줄이
  더해질 뿐 의미가 같다. 서비스 테스트와 AI 확장 서비스는 개발자 파일이라 고친 파일은 그대로고, 한 번도 고치지 않은
  파일은 다음 생성에 맞춰진다
- 2.x 와 2.x 호환 출력은 원래 그렇게 써 왔다 - 그대로
- 파일: `v3/service.tmpl`, `v3/service_test.tmpl`, `v3/service_extend_ai.tmpl`, `kt/service.tmpl`, `kt/service_test.tmpl`,
  테스트 `EntityShadowedInServicePackageTest`

### Documentation (불변식 - 멤버 삭제)

- annotation guide 의 `@invariant` 와 rule guide 의 불변식 절이 "마지막 멤버를 지우면 검사되지 않는다"는 한계를 그대로 적고 있었다.
  그 한계는 멤버의 `XxxRemovalCheck` 리스너로 이미 닫혀 있다(`AggregateInvariantTest.emptyingAnOrderIsCaught`) - rule guide 의
  다른 절은 맞게 적고 있어 두 곳이 서로 달랐다. 두 곳을 지금 동작으로 고치고, 남은 경우(멤버에 루트를 가리키는 필드가 없으면
  DSL 경고)를 적었다

### Documentation (README 첫 화면)

- README 가 476줄이었고, 동작하는 모습(실제 요청과 응답)은 300줄 아래에 있었다 - 처음 온 사람은 거기까지 읽지 않는다.
  첫 화면을 "무엇을 하는지 한 문장 → 동작 GIF → 왜 쓰는지 네 줄 → 한 명령으로 돌려 보기 → 5단계" 로 바꾸고 README 를
  190줄 안쪽으로 줄였다
- GIF(`docs/images/demo.gif`)는 pet store 를 실제로 돌려 받은 것이다: 모델, 401·403·409 응답, `deliver: SHIPPED -> DELIVERED` 와
  `DELIVERED` 두 줄을 더해 다시 생성한 로그(8 written / 72 unchanged / 25 skipped), 새 명령의 200·409. "Java 7개 파일 45줄" 은
  두 줄 전후의 `src/main/java` 를 첫 줄(해시·시각) 빼고 비교해 센 것(42줄 추가, 3줄 삭제). 확인 뒤 예제 DSL 은 되돌렸다
- 덜어 낸 절은 지우지 않고 옮겼다: DSL 읽는 법·무엇이 다른가·규칙(ruleset)·업무 규칙·바운디드 컨텍스트·믿을 수 있는 출력은
  `docs/tour.md`(`.ko.md`), Spring Initializr·Spring Boot 3·소스 빌드는 `docs/getting-started.md`(`.ko.md`). 링크는 `docs/` 기준으로
- 5단계의 코드 블록(build.gradle, env.yml, model/entity.yml, 생성 파일 목록, 로그인 호출)은 모양 그대로 둬서
  `ReadmeQuickStartTest`·`ReadmeTest` 가 계속 실제로 생성해 본다
- 시작하기의 로그인에 `@signin` 을 더했다 - 모델만으로 로그인·로그아웃·"나는 누구" API 가 생긴다(pet store 의 방식)
- 파일: `README.md`, `README.ko.md`, `docs/tour.md`, `docs/tour.ko.md`, `docs/getting-started.md`, `docs/getting-started.ko.md`,
  `docs/images/demo.gif`
- `ReadmeTest` 의 링크 검사가 다른 문서의 절을 가리키는 링크(`docs/tour.md#status`)를 없는 파일로 읽었다 - `#` 뒤를 떼고
  문서가 있는지 본다

### Bug Fixes (DTO - 다른 도메인의 부모)

#### `@extend` 로 다른 도메인의 DTO 를 상속하면 부모를 import 하지 않던 것
- `meta: dto @extend(SignInRequest)` 처럼 부모가 다른 도메인의 DTO 면, `-Pv3`·DDD·헥사고날과 Kotlin 의 DTO 가
  `extends SignInRequest`(Kotlin `: SignInRequest()`) 를 쓰면서 그 import 는 쓰지 않아 `cannot find symbol` 로 컴파일되지
  않았다. 정리된 출력은 클래스가 쓰는 타입만 import 하는데, 그 목록을 필드 타입에서만 모았다
- 이제 부모도 필드 타입과 같은 방식으로 import 한다 - 다른 패키지의 부모만. 같은 도메인의 부모는 같은 패키지라 그대로다
- 2.x 와 2.x 호환 출력은 부모 패키지를 와일드카드로 import 해 왔다 - 그대로
- 파일: `RuleText.java`(`registerParentImport`), `DtoCleanFileRules.java`, `KotlinDtoFileRules.java`, 테스트 `DtoParentImportTest`

## 3.0.261022-rc1

같은 요구사항(도서·회원·대출 API)을 에이전트가 ElastiCORE 로 만들게 하고 어디서 Java 를 손으로 쓰는지 보았다. 네 군데였다:
서버가 정하는 값(반납일)을 클라이언트가 보내도 막을 수 없었고, "아직 반납하지 않은" 검색을 DSL 로 쓸 수 없었고,
ISBN 13자리 같은 정규식 검사는 `@pattern` 이 컴파일되지 않는 코드를 냈고, `member: Member!` 는 키(`memberId`)가 아니라
중첩 객체를 요구했다. 아래 네 항목이 그 자리를 DSL 로 메운다. 네 항목 모두 그 문법을 쓰지 않는 프로젝트의 생성 결과는
바뀌지 않고, 2.x 생성기와 2.x 와 같은 출력을 내는 `jpa-springboot` 번들은 그대로다(`elcoreRulesCheck -Pstrict` 150 MATCH /
0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra).

### New Features (DSL - 요구사항을 손코딩 없이)

#### 필드 `@readonly` 는 서버가 정하는 값 - v3 매퍼가 DTO 에서 엔티티로 쓰지 않는다
- 필드 `@readonly`(별칭 `@noinput`)는 지금까지 DTO 에 `@JsonProperty(access=READ_ONLY)` 만 붙였다. JSON 요청의 값은 Jackson
  이 버렸지만, v3 매퍼는 서버 코드가 DTO 에 넣은 값(`dto.setX(..)` 후 `service.save(dto)`)을 그대로 엔티티에 썼고, `!` 가
  붙은 `@readonly` 필드는 DTO 의 `@NotNull` 이 남아 그 값을 보낼 수 없는 생성 요청을 모두 400 으로 거절했다. DDD 의 나뉜
  루트에서는 매퍼가 요청 값을 `to.creation()::setX` 로 새 루트에 넣었다
- 이제 v3 번들(`jpa-springboot-v3`·DDD·헥사고날·Kotlin)에서 `@readonly`/`@noinput` 필드는
  - 매퍼의 DTO -> 엔티티 방향(`toEntity`, `mapping(dto, entity, ...)`, EntityManager 판 포함)이 쓰지 않는다. 엔티티 -> DTO
    방향은 그대로라 응답에 나간다. 엔티티 필드에 붙이면 그 엔티티의 모든 DTO 에, `dto.yml` 의 DTO 필드에 붙이면 그 DTO 의
    매핑에만 적용된다. `@id` 에 붙은 `@readonly` 는 식별자 쓰기를 그대로 둔다
  - DTO 의 `@NotNull`·`@NotBlank` 를 뺀다. 엔티티 컬럼(`nullable = false`)은 그대로라 서버가 채운다
  - DTO 의 `@Schema` 에 `accessMode = Schema.AccessMode.READ_ONLY` 를 더한다. springdoc 은 `@JsonProperty` 만으로도
    `readOnly: true` 를 냈다 - `@Schema` 만 읽는 도구를 위해 적는다
  - 나뉜 루트에서는 여전히 잠긴 필드다: `protected` setter 와 `creation()` setter 가 남아 서버 코드가 새 루트에 넣는다
- v3 동작 변경: 필드에 `@readonly` 를 붙이고 서버 코드에서 DTO 에 값을 넣어 저장하던 코드는 그 값이 더는 저장되지 않는다.
  엔티티에 넣는다(확장 서비스의 `beforeSave`, 루트의 메서드). HTTP 동작은 같다 - JSON 의 값은 이전에도 버려졌다. 이유:
  `@readonly` 는 "요청으로 받지 않는다" 는 선언인데 매퍼가 그 선언을 무시해, 서비스가 `creation().setX(null)` 같은 우회를
  쓰고 있었다(새 루트에서는 아무 일도 하지 않는다). `@readonly` 가 없는 프로젝트의 v3 출력은 바뀌지 않는다
- 가이드: annotation guide 에 `@readonly / @noinput` 절(생성물 표, `@behavior` 와의 비교, `creation().setX(null)` 은 새 루트에서
  아무 일도 하지 않는다는 것), §2.8 감사 필드, jsonview 표, `@behavior` 는 나뉜 루트에서만 효과가 있다는 것. dsl guide 필드 표와
  `@behavior` 행, rule guide 의 잠긴 루트 표와 '새 루트의 null'
- 확인: 규칙 단위 테스트(Java·Kotlin 매퍼, DTO, 2.x 호환 출력, 나뉜 루트, `@editable` 루트), hexagonal Java·Kotlin 도서관 모델을
  `RulesGenRunner` 로 생성해 매퍼·DTO·`LoanBase` 를 읽는 테스트. `elasticore-template-ddd` 의 catalog `Book` 에 `shelfCode:
  string(10)! @readonly` 와 그 값을 채우는 `beforeSave` 를 임시로 넣고 Spring Boot 3.3.2 에서 실행해 확인했다: 클라이언트가
  값을 보낸 POST·PUT 은 200 이고 `beforeSave` 의 값이 남는다. `service.save(dto)` 도 DTO 에 넣은 값 대신 `beforeSave` 의 값을
  저장한다. `/v3/api-docs` 는 `readOnly: true` 이다. 변경 전 출력에서는 POST 가 400(`shelfCode: must not be null`)이었고,
  DTO 에 넣은 값이 저장되었다
- 남은 것: DTO 의 `@ref(...)` 필드에 붙인 `@readonly` 는 아직 관계 쓰기(`reverseRefPass`)를 막지 않는다. 나뉜 루트의 필드
  `@behavior` 는 `@JsonProperty` READ_ONLY 만 붙고 `@Schema accessMode` 는 붙지 않는다
- 관련 파일: `WriteGuardRules.java`, `DtoBaseRules.java`, `MapperBaseRules.java`, `ServerSetFieldTest.java`, `SealedRootTest.java`,
  `ServerSetFieldGenerationTest.java`

#### `@search(isnull)` - 값이 비어 있는지로 검색한다
- "아직 반납하지 않은 대출", "아직 입고된 적 없는 상품"처럼 값이 비어 있는지로 찾으려면 Service 에
  `Q.Loan.returnedDate(Op.IS_NULL, null)` 를 손으로 쓰거나 Repository 에 JPQL 을 따로 두어야 했다. `@search(isnull)` 은
  2.x 와 규칙 엔진 모두에서 경고 없이 같은 값 비교(`c.equal`)로 만들어졌고, 가이드는 `ISNULL`/`ISNOTNULL` 을 미구현으로
  적고 있었다
- 이제 `returnedDate: date @search(isnull)` 은 검색 DTO 에 `Boolean returnedDateEmpty` 하나를 만든다. 보내지 않거나
  `null` 이면 조건 없음, `true` 면 `c.isNull(r.get("returnedDate"))`, `false` 면 `c.isNotNull(...)`. `@Schema` 설명은
  `... Field is empty (true) or set (false). field:returnedDate` 이고, 보내는 값이 날짜가 아니므로 `@DateTimeFormat`·
  `@JsonFormat`·`example` 은 붙지 않는다. `@ref(order.shippedDate)` 와 쓰면 `r.get("order").get("shippedDate")` 를
  검사하고, 쉼표 뒤 두 번째 값은 다른 조건처럼 엔티티 필드 이름이다(`@search(isnull,open)` -> `openEmpty`, `r.get("open")`)
- Kotlin(`val xEmpty = searchDTO.xEmpty`, `if(xEmpty!!)`, `Specification<E> { r,q,c -> c.isNull(...) }`)과
  TypeScript(`xEmpty?: boolean`)도 같은 규칙에서 나온다
- 필드 하나에 조건 하나: `isnull` 을 적은 필드는 `between` 의 From/To 를 만들지 않는다. `@expose` 의 타입별 자동
  할당(날짜 -> `between`)은 바뀌지 않는다
- v3 번들(`jpa-springboot-v3`·DDD·헥사고날·Kotlin·`ts-model`) 전용이다. 2.x 생성기와 2.x 호환 번들(`jpa-springboot`)은
  이전처럼 같은 값 비교를 만들고 `@Schema` 문구도 그대로다 - `StringUtils.getOperatorDescription(op)` 는 바뀌지 않고 새
  문구는 `getOperatorDescription(op, true)` 에만 있다
- v3 번들에서 이미 `@search(isnull)` 을 적어 둔 필드는 검색 속성이 `<필드>`(필드 타입, 같은 값 비교)에서
  `<필드>Empty`(Boolean)로 바뀐다. 미구현으로 문서화된 키워드가 조용히 같은 값 비교가 되던 것을 바로잡는 것이며,
  저장소의 블루프린트 중 쓰는 곳은 없다
- 확인: 단위 테스트로 Java·Kotlin·TypeScript 출력과 2.x 호환 출력이 그대로인 것을 확인했다. `elasticore-template-ddd`
  의 `Product.lastRestocked` 에 잠시 `@search(isnull)` 을 붙여 생성·컴파일하고, H2 위에서
  `POST /api/v1/shop/product/search` 가 `true` 는 입고일이 없는 상품만, `false` 는 있는 상품만, 생략과 `null` 은 둘 다
  돌려주는 것을 확인했다. `elasticore-template-kt` 의 `Project.endDate` 로 Kotlin 을 생성해 `compileKotlin` 이 통과하고
  TypeScript 에 `endDateEmpty?: boolean;` 이 나오는 것을 확인했다(샘플 변경은 되돌렸다)
- 남은 것: List 필드나 `!` 필드에 `isnull` 을 적어도 경고하지 않는다. v3 의 별칭 구분자는 쉼표인데 가이드 §3.1 은 `$` 로
  적고 있다(별개 버그)
- 관련 파일: `SearchDtoBaseRules.java`, `MapperSpecRules.java`, `StringUtils.java`, `SearchDtoTest.java`,
  `MapperSpecTest.java`, `TypeScriptModelTest.java`, `OperatorDescriptionTest.java`,
  `elasticore-annotation-guide.md`(3.2, 3.4.1, 3.8, 8.5), `elasticore-dsl-guide.md`, `elasticore-dev-guide.md`

### Bug Fixes (생성기)

#### `@pattern` 이 컴파일되는 `@Pattern` 을 쓰고, `@min` 처럼 값 다음에 메시지를 받는다
- 규칙 엔진의 v3·Kotlin 번들에서 `@pattern(regexp="...", message="...")` 이 컴파일되지 않았다. 네 줄짜리
  `@Pattern( regexp="...", msg="..." )` 를 썼는데 Jakarta `@Pattern` 에는 `msg` 속성이 없다. 이제 한 줄
  `@Pattern(regexp = "...", message = "...")` 을 쓴다
- 위치 형식 `@pattern('^[0-9]{13}$', 'ISBN must be 13 digits')`, `@pattern('^[A-Z]+$')` 도 읽는다. 이전에는 아무것도
  생성하지 않았다. 메시지는 따옴표와 괄호 밖의 첫 쉼표 뒤라서 `{10,13}` 은 정규식에 남는다. 메시지를 생략하면 TypeScript
  클라이언트와 같은 `"<라벨> is not in the expected form"` 을 쓴다. TS 의 `validate...()` 도 같은 값을 읽는다.
  이전에 아무 검사도 없던 필드(위치 형식, 메시지 없는 이름 형식)에 요청 검사(400)가 새로 생긴다
- Kotlin 은 문자열 템플릿이 되는 `$`(영문자·`_`·`{` 앞)를 `\$` 로 쓴다. `@Pattern` 은 문자열 필드에만 붙는다. 날짜의
  `@pattern(yyyyMMdd)` 는 이전처럼 날짜 형식이다. clean 출력은 문자열 필드의 `@pattern` 을 Swagger `example` 로 쓰지 않는다
  (위치 형식에서는 `^[0-9]{13}$', 'ISBN must be 13 digits` 같은 깨진 값이 나왔다)
- 정규식은 Java 문자열 리터럴의 내용으로 그대로 들어가므로 백슬래시는 `\\d` 처럼 두 번 쓴다. `\d` 처럼 하나만 쓰면
  규칙 엔진이 DSL 경고를 낸다(2.x 추출기는 경고에 실패하므로 규칙 엔진에서만)
- 2.x 생성기(`elcore`)와 2.x 호환 번들(`jpa-springboot`)의 출력은 그대로다(`msg=`, 이름 형식에 메시지가 있을 때만) -
  2.x 의 `@pattern` 은 여전히 컴파일되지 않는 코드를 내며, 2.x 호환을 위해 손대지 않았다
- 확인: 도서관 모델(`isbn: string(13)! @id @pattern('^[0-9]{13}$', 'ISBN must be 13 digits')`)을 layered·Kotlin·TS 로
  생성했다. Java DTO 는 javac, Kotlin DTO 는 kotlinc 1.9.24 로 컴파일했고, Hibernate Validator 8 이 `12345` 를
  "ISBN must be 13 digits" 로 거부하는 것을 확인했다. TS 의 `validateBook()` 이 node 에서 같은 메시지를 내는 것도 확인했다
- 남은 것: `=` 가 든 정규식은 이름 형식으로만 쓸 수 있다. 따옴표 하나로 감싼 이름 형식의 값은 공백에서 잘린다(기존 파서).
  DDD 루트의 저장 시점 검사(`@min` 처럼)는 하지 않는다
- 관련 파일: `FieldSpec.java`, `DtoBaseRules.java`, `FieldDoc.java`, `TypeScriptDtoFileRules.java`, `AbstractModelLoader.java`,
  `PatternAnnotationTest.java`, `DtoGapCoverageTest.java`, `elasticore-annotation-guide.md`

#### 필수 관계(`post: Post!`)는 저장 요청에서 FK id 필드로 요구한다
- `@expose`/`@service` 엔티티가 자동 생성하는 DTO 에서 `!` 가 붙은 to-one 관계는 중첩 DTO(`PostDTO post`)에 `@NotNull`·
  `requiredMode=REQUIRED` 가 붙고 FK id 필드(`postPostId`)에는 붙지 않았다. 매퍼는 저장/수정할 때 `postPostId` 만 읽으므로
  (`em.getReference`), 키만 보낸 요청은 `@Valid` 검증에서 거절되고 중첩 객체만 보낸 요청은 검증을 통과해도 관계를 연결하지
  못했다. 요청은 둘 다 보내야 했다
- 이제 v3·Kotlin·TypeScript 생성(`jpa-springboot-v3`·DDD·헥사고날·Kotlin·TypeScript 번들)에서는 FK id 필드가 `@NotNull`·
  `requiredMode=REQUIRED` 이고, 응답용 중첩 DTO(`accessMode = READ_ONLY`)는 선택이다. TypeScript 는 `post?: PostDTO;`·
  `postPostId: number;` 로 선언하고 `validateXxx` 가 `post` 를 요구하지 않는다. `ddd-reference` 를 쓰는 번들은 ID 필드(`memberId`)가
  이미 필수였고, 중첩 DTO 만 선택이 된다. 키 이름 규칙(`필드 + 키`, 키가 `id` 면 `topicId`)은 그대로다
- **동작 변경 (v3·Kotlin·TypeScript 사용자)**: 중첩 `post` 만 보내던 클라이언트는 이제 `@Valid` 검증에서 거절된다 - FK id 필드를
  보내야 한다(그 요청은 이전에도 관계를 연결하지 못했다). `postPostId` 를 함께 보내던 클라이언트는 그대로 동작한다. OpenAPI 의
  required 가 `post` 에서 `postPostId` 로 옮겨 간다
- 바뀌지 않는 것: 2.x 생성기(`elcore`)와 2.x 와 같은 출력을 내는 `jpa-springboot` 번들(중첩 DTO 에 `@NotNull` 그대로), 검색 DTO
  (`XxxSrchDTO` 의 `postPostId` 는 검색 조건이라 필수가 아니다), Entity(`@JoinColumn(nullable = false)`), 매퍼, `dto.yml` 에 직접
  선언한 DTO. `!` 가 붙은 엔티티 참조가 없는 프로젝트의 생성 결과는 바뀌지 않는다
- 확인: `Post`·`Comment`(`post: Post!`) 게시판을 v3 layered·Kotlin·TypeScript·호환 모드·2.x 로, `Member`·`Book`·`Loan`
  (`member: Member!`, `book: Book!`, Book 의 키 `isbn`)을 헥사고날로 생성해, v3 `CommentDTO` 의 `postPostId` 에 `@NotNull`·`REQUIRED`
  가 붙고 `post` 에는 없는 것, 키가 `id` 인 `topic: Topic!` 의 `topicId` 가 필수인 것, 선택 관계의 `postPostId` 와 `CommentSrchDTO` 의
  `postPostId` 가 그대로인 것, 헥사고날 `LoanDTO` 의 `memberId`·`bookIsbn` 이 필수이고 `member`·`book` 이 선택인 것, 호환 모드 출력이
  2.x 와 같은 것을 테스트로 확인했다. DTO 와 검색 DTO 가 함께 쓰는 키 필드는 복사본에만 `@notnull` 을 붙여, 어느 DTO 를 먼저
  만들어도 검색 DTO 가 바뀌지 않는 것도 확인했다
- 남은 것: TypeScript 의 `XxxFields` 는 FK id 필드를 여전히 `readOnly: true` 로 표시하고 `validateXxx` 가 검사하지 않는다
- 관련 파일: `SpecFactory.java`, `RulesGenRunner.java`, `RulesVerifyRunner.java`, `RequiredReferenceDtoTest.java`,
  `elasticore-annotation-guide.md`, `elasticore-dsl-guide.md`, `elasticore-rule-guide.md`

### Improvements (런타임 - 손으로 쓴 규칙의 409)

#### `ConflictException` - 손으로 쓴 규칙이 409 를 자기 메시지로 낸다
- "빌려줄 권수가 없다", "이미 반납한 대출이다" 같은 규칙을 `beforeSave` 나 루트 메서드에 쓸 때 409 로 답할 예외가 없었다.
  `DataIntegrityViolationException` 을 던지면 409 는 나가지만 `detail` 이 데이터베이스 제약 문구("A unique value is already
  taken, or the row is still referenced by another.")로 바뀌어 이유를 알 수 없었다
- `io.elasticore.springboot3.validation.ConflictException` (`BadRequestException` 과 같은 자리)을 던지면 생성된
  `XxxApiErrors` 가 409, `code: conflict`, 던진 메시지를 `detail` 로 답한다. Java 와 Kotlin 의 `apiErrors` 템플릿에 처리기를
  더했다. 2.x 와 `jpa-springboot` 번들에는 API 오류 처리기가 없으므로 바뀌지 않는다
- `XxxApiErrors.java` 는 한 번만 생성되는 파일이라 이미 생성한 프로젝트는 그 처리기 다섯 줄을 손으로 더하거나 파일을 지우고
  다시 생성한다. 저장소의 샘플에는 더해 두었다
- 확인: 도서 대출 프로젝트의 `LoanService.beforeSave` 와 반납 메서드에서 던져 `{"status":409,"code":"conflict","detail":"No
  copies of book ... left: 1 of 1 are on loan"}` 이 나오는 것을 확인했다
- 관련 파일: `ConflictException.java`, `elasticore-template/v3/apiErrors.tmpl`, `elasticore-template/kt/apiErrors.tmpl`,
  `elasticore-rule-guide.md`(쓰기 경로가 지키는 것)

### Bug Fixes (Ossie 내보내기 - dialect)

#### 식을 `OSSIE_SQL_2026` 으로 내보낸다. 서브쿼리 `@formula` 만 `ANSI_SQL`
- `OssieSemanticModelExporter` 가 모든 필드 식과 지표 식을 `ANSI_SQL` 로 내보냈다. Ossie core spec 은 `OSSIE_SQL_2026` 만
  의미를 정의하고(`spec.md` "Dialects": 나머지 이름은 벤더 언어, 소비자가 무시해도 됨) 그것을 기본 dialect 로 삼으므로,
  `ANSI_SQL` 로 내보낸 모델은 읽는 쪽이 식을 전부 건너뛰어도 스펙에 맞는 것이었다
- 컬럼 이름인 필드 식과 `metrics:` 의 지표 식(집계, `CASE WHEN`, `CAST`, `NULLIF`, 비교 - 모두 `expression_language.md`
  의 지원 구문)은 `OSSIE_SQL_2026` 으로 내보낸다. `@formula` 는 `OssieSql` 이 가른다: 문자열 리터럴 밖에 SELECT 같은 질의
  단어가 없으면 `OSSIE_SQL_2026`, 서브쿼리·CTE 처럼 그 언어가 지원하지 않는 식이면 `ANSI_SQL` 항목 하나로 둔다. `@formula`
  필드의 `custom_extensions` 에 `"formula": "database"` 를 더했다
- 확인: `OssieSqlTest`(식 16개·질의 5개), `OssieFormulaDialectTest`(@formula 둘을 가진 프로젝트를 생성해 내보낸 파일의
  dialect 확인), `OssieExportTest`(ddd 샘플의 모든 필드가 `OSSIE_SQL_2026`); `elasticore-template-ddd` 와
  `elasticore-sample-petstore` 의 `semantic_model.yml` 을 다시 생성했다
- 관련 파일: `OssieSql.java`, `OssieSemanticModelExporter.java`, `OssieSqlTest.java`, `OssieFormulaDialectTest.java`,
  `OssieExportTest.java`, `elasticore-rule-guide.md`(dialect 단락), `dsl-guide.html`(펫스토어 안내 페이지의 예)

### New Features (로그인 API 생성)

#### 엔티티 줄의 `@signin` - signin·signout·me 가 생성된다
- 펫스토어를 에이전트(agent kit)로 세 번(Java hexagonal, Java ddd, Kotlin hexagonal) 새로 만들게 했더니 셋 다,
  그리고 저장소의 펫스토어 샘플도 같은 로그인 컨트롤러(로그인·로그아웃·지금 누구인지)를 손으로 썼다. 세 에이전트 모두
  `SessionCurrentUser` 의 사용법을 찾으려고 jar 를 `javap` 로 열었다
- `@login`·`@password` 가 있고 `@expose` 된 엔티티의 줄에 `@signin` 을 쓰면 그 컨트롤러에 `POST /signin`
  (`{"<login 필드>", "<password 필드>"}`, 틀리면 401 `Wrong <login> or password`), `POST /signout` (204), `GET /me`
  (방문자 401) 가 붙는다. CoreService·유스케이스에 `me()`, TypeScript 클라이언트에 `signIn`·`signOut`·`me()`(방문자면 null).
  `@login`·`@password` 만으로는 전처럼 `authenticate` 만 생긴다 - 계정 상태 확인 같은 자체 로그인을 하는 애플리케이션에
  두 번째 로그인 경로가 저절로 생기지 않게 **요청할 때만** 만든다 (리뷰에서 기본으로 켜는 안을 되돌렸다)
- 세션에는 행의 키와 역할이 들어간다. 역할은 `@role` 필드, 없으면 `role` 필드인데 **관리자만 쓰는 필드
  (`@access(admin)`) 이고 enum 이나 문자열일 때만** 쓴다 - 아니면 가입자가 자기 역할을 ADMIN 으로 보내고 로그인해
  관리자가 될 수 있으므로, DSL 경고와 함께 역할 없이 로그인한다
- 애플리케이션에서 `@signin` 엔티티는 하나만: 세션은 맨 키만 들고 있어 둘이면 서로의 행으로 읽힌다. 둘 이상이면 어느
  쪽에도 만들지 않고 DSL 경고. 키가 둘 이상이거나 `@expose` 가 없을 때도 경고 (애플리케이션을 어떻게 세는지, 그리고
  비밀번호를 누가 바꿀 수 있어야 하는지는 이 섹션 끝의 "로그인 API - 리뷰" 항목)
- 손댄 컨트롤러는 생성기가 건너뛰므로 엔드포인트가 들어가지 않는다. 이제 생성기가 그 컨트롤러에 없는 엔드포인트
  (`POST /signin` 등, 새 명령도)를 DSL 경고로 알린다 - TypeScript 클라이언트는 늘 다시 생성되어 그것을 부르기 때문
- 펫스토어 샘플의 `Customer` 에 `@signin` 을 붙이고 `AccountController` 는 가입하고 바로 로그인하는 것만 남겼다.
  화면(`web/src/shop.ts`)은 생성된 `CustomerApi.signIn/signOut/me` 를 쓴다
- 확인: `SignInAndReferencesTest`(Java·Kotlin·TypeScript, 역할 거부, 두 엔티티, 키 둘), 펫스토어 `ShopFlowTest`(생성된
  `/customer/signin`, 로그아웃 뒤·방문자의 `/customer/me` 401), `webBuild` 타입 검사, `elasticore-template-kt` 의
  `AgentController`(역할 포함) 컴파일·테스트, agent kit 의 상점 레시피 계약(S3)
- 관련 파일: `ServiceAccessRules.java`(`signInControl`, `me`, `signsIn`, `whyNoSignIn`, `roleField`), `HexUseCaseRules.java`,
  `TypeScriptApiRules.java`, `RulesGenRunner.java`(`warnIfEndpointsMissing`), `SpecFactory.java`, `SignInAndReferencesTest.java`,
  `elasticore-annotation-guide.md`, `elasticore-rule-guide.md`

### Improvements (오류 응답 - 상태 충돌 409, 없는 참조 404)

#### 생명주기가 거절한 명령은 409, 요청이 가리킨 행이 없으면 404
- 세 에이전트 모두 생성된 `XxxApiErrors` 를 고쳐 "배송된 주문 취소"를 422 에서 409 로 바꿨다. RFC 9110 의 409 가
  "대상의 현재 상태와 충돌"이므로 그쪽이 맞다. 다른 불변식 위반은 그대로 422 다
- `apiErrors` 템플릿(Java, Kotlin)의 `refused(...)`: 코드가 `state.` 로 시작하면 409 (제목 코드 `state`, "Not allowed in
  this state"), `reference.` 로 시작하면 404, 나머지는 422. 공통 코드에 `error.state.title` 을 더했다
- 이미 있는 프로젝트: `XxxApiErrors` 를 **손댄 적이 없으면** 다시 생성되어 409·404 로 답한다 - 422 로 분기하던
  클라이언트는 함께 고친다. **손댄 적이 있으면** 그대로라 생명주기 거절도, 없는 참조(아래)도 422 다. 그 파일의
  `refused(...)` 를 고친다. 생성 코드의 주석("Refuses a delete in any other state: 409")도 바뀌었다.
  펫스토어 샘플은 다시 생성하고 테스트를 409·404 로 바꿨다(`petstore-messages_ko.properties` 에 새 제목)
- 관련 파일: `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `ErrorCatalog.java`, `ShopFlowTest.java`, `ShopModelTest.java`,
  `elasticore-rule-guide.md`, `elasticore-dsl-guide.md`, `elasticore-annotation-guide.md`, `elasticore-ddd-customize-guide.md`

### Improvements (DDD 번들 - 참조한 행이 있는지 저장 전에 확인)

#### `requireReferences(lookup)` - 없는 id 를 가리키는 참조는 404 로 거절
- 다른 애그리거트를 id 로 참조하는 필드(`loan.memberId`, 주문 줄의 `itemId`)에 없는 id 를 보내면 외래 키 위반이 409
  "Conflicts with stored data" 로 나갔다. 그래서 도서 대출 레시피와 펫스토어를 만든 세 에이전트가 모두 저장 전에
  `entityManager.find` 로 확인하는 코드를 손으로 썼다
- 그런 참조가 있는 엔티티(직접, 또는 소유한 멤버를 통해)에 `requireReferences(Lookup)` 이 생기고, CoreService 가 생성·
  수정·일괄 수정·조건 수정에서 `beforeSave` 전에 부른다. 없으면 `InvariantViolationException("reference.<Entity>.<relation>",
  "<Target> 99 does not exist")` - API 는 404. `@snapshot` 이 원본 행을 못 찾을 때도 같은 코드·문구(전에는
  "OrderLine.item 99 does not exist", 422)
- 생성되는 Kotlin 서비스 테스트(`testCode`)는 모의 `entityManager.find` 가 요청한 클래스의 모의 객체를 돌려주게 한다(lenient)
- 이미 있는 프로젝트에서 달라지는 것: (1) `beforeSave` 에서 손으로 하던 같은 확인은 생성된 확인이 먼저 하므로 그 코드·
  메시지는 나가지 않는다 - 지우거나, 참조를 객체로 두려면 `@objectref`; (2) 손댄 `XxxApiErrors` 는 이 거절을 422 로 답한다
  (전에는 외래 키 409); (3) 손댄 Kotlin 서비스 테스트는 다시 생성되지 않으므로 참조 id 를 넣는 저장 테스트가 실패한다 -
  `setUp` 에 `Mockito.lenient().\`when\`(entityManager.find(any<Class<Any>>(), any<Any>())).thenAnswer(Answer<Any> { c ->
  Mockito.mock(c.getArgument<Class<Any>>(0)) })` 를 넣는다. 저장할 때마다 참조마다 조회 한 번(바뀌지 않은 참조도)
- 3단계 애그리거트(루트 - 멤버 - 멤버의 멤버)도 컴파일되도록, 참조가 있다고 등록된 엔티티는 확인할 것이 없어도
  `requireReferences` 를 가진다 (리뷰에서 찾음) - 단 `@extend` 부모가 이미 가지면 물려받는다(아래 "리뷰 - 상속" 항목).
  참조하는 엔티티 계산은 생성하는 모델마다 한 번 하고 그 모델의 엔티티들이 나눠 쓴다 (id 참조로 필드가 바뀌면 다시).
  선택으로 담은 값 객체는 실행마다 한 번, 모든 도메인에 걸쳐 센다(아래)
- 관련 파일: `DddReferenceRules.java`(`requireReferences`, `-service`, `-test`), `DddDeriveRules.java`, `DddRepositoryPortRules.java`,
  `EntitySpec.java`(`knownReferencing`), `SpecFactory.java`(`registerReferencing`), `SignInAndReferencesTest.java`

### Bug Fixes (DTO - 최소 개수)

#### `@minsize(1)` 컬렉션을 아예 보내지 않으면 400 (전에는 엔티티 불변식의 422)
- `@Size` 는 null 을 통과시키므로 `lines` 를 뺀 주문이 DTO 검증을 지나 저장 시점 불변식에서 422 가 났다. 빈 배열은 400
  이었다(채점 에이전트가 찾음)
- 정리된 출력(v3)에서 `@minsize(n)` 의 n 이 1 이상이면 DTO 필드에 같은 메시지의 `@NotNull` 을 더한다. 2.x 호환 출력은 그대로
- TypeScript 클라이언트의 `validate<Name>()` 도 같은 메시지로 거절한다: `if (!partial && (value.lines == null ||
  value.lines.length < 1)) errors.lines = "an order needs a line";` (부분 수정 `partial` 에서는 묻지 않음). 이미 있는
  화면에서 달라지는 것: 품목을 넣기 전의 주문 초안을 `validatePurchaseOrder` 로 검사해 빈 결과로 저장 버튼을 켜던 화면은
  이제 `lines` 메시지를 받는다. 처음 커밋의 이 검사는 목록 필드를 검사 대상에서 빼 두어 생성되지 않았다(리뷰에서 찾음,
  아래 "리뷰 - TypeScript" 항목)
- 관련 파일: `DtoBaseRules.java`, `TypeScriptDtoFileRules.java`, `SignInAndReferencesTest.java`, `ShopFlowTest.java`,
  `elasticore-annotation-guide.md`

### Bug Fixes (선택으로 담은 값 객체의 컬럼)

#### 어느 엔티티가 값 객체를 선택으로 담으면 그 값 객체의 컬럼은 NULL 을 허용한다
- agent kit 의 상점 레시피를 처음 빌드해 보니, 배송지(`shipTo: Address @Embedded`, `!` 없음)를 보내지 않은 주문이
  `NULL not allowed for column "ZIPCODE"` - 400 `required` 로 저장되지 않았다. 값 객체의 `!` 필드가 그 값 객체를 담는
  모든 테이블의 컬럼을 NOT NULL 로 만들었기 때문이다. 펫스토어 샘플은 손으로 쓴 코드가 배송지를 늘 채워서 드러나지 않았다
- 정리된 출력(v3)에서, 어느 도메인의 어느 엔티티든 값 객체를 필수 표시(`!`·`@required`·`@nn`) 없이 담으면 그 값 객체의
  필드는 `nullable = false` 를 붙이지 않는다. 선택으로 담은 값 객체 안의 값 객체도 그렇다. 값이 오면 DTO 가 필수 필드를
  검사한다(400). 늘 필수로만 담는 값 객체는 그대로 NOT NULL. 2.x 호환 출력은 그대로 (다른 도메인·안쪽 값 객체·`@required`
  는 리뷰에서 고침 - 아래 "리뷰 - 값 객체" 항목)
- 관련 파일: `JpaEntityBaseRules.java`, `EntitySpec.java`(`knownOptionalEmbeddable`), `SpecFactory.java`
  (`registerOptionalEmbeddables`), `SignInAndReferencesTest.java`, `elasticore-annotation-guide.md`

### Improvements (읽지 못한 모델 파일은 빌드를 멈춘다)

#### `LOAD ERROR` 가 나면 `elcore3` 가 실패한다
- `-- 설명` 에 `: ` 가 들어가 YAML 이 깨지면 로더가 `* LOAD ERROR` 를 찍고 그 파일을 뺀 채 생성이 성공했다. 엔티티가
  통째로 없어진 것은 손으로 쓴 코드의 컴파일 오류("package ...dto does not exist")로만 드러났다 - 레시피를 쓰다 직접 겪었다
- 로더가 읽지 못한 파일을 `ModelCheck.failSaid` 로 기록하고, 규칙 엔진은 생성을 마친 뒤 `[elcore3] the model check
  failed (1) - fix these and build again:` 와 파일·파서 메시지를 찍고 1 로 끝난다. 2.x 생성기는 `ModelCheck` 를 보지
  않으므로 그대로다
- 주석이나 빈 줄만 있는 파일은 선언한 것이 없으므로 빈 파일처럼 건너뛰고, 멈춘 실행은 생성 파일을 지우지 않는다
  (리뷰에서 고침 - 아래 "리뷰 - 모델 파일" 항목)
- 관련 파일: `YamlFileMainModelLoader.java`, `ModelCheck.java`, `RulesGenRunner.java`, `LoadErrorFailsTest.java`,
  `elasticore-dev-guide.md`

### Bug Fixes (`@default(now)`)

#### `timestamp` 필드의 `@default(now)` 가 `Instant.now()` 를 쓴다, 그리고 "만들 때의 시각"을 문서에
- `@default(now)` 는 2.x 부터 `date`·`datetime`·`time` 에 `LocalDate.now()` 같은 초기값을 썼지만 가이드 어디에도 없었고,
  규칙 엔진의 `timestamp`(Instant) 에는 `now` 를 그대로 써서 컴파일되지 않았다. 엔티티에는 `java.time.Instant.now()` 를 쓴다
- DTO 에는 `date`·`datetime`·`time` 처럼 `timestamp` 의 **모든** 기본값을 붙이지 않는다 - `now` 만이 아니다.
  `listedAt: timestamp @default(java.time.Instant.EPOCH)` 의 DTO 는 전에 `@Builder.Default private Instant listedAt =
  java.time.Instant.EPOCH;` 였고 이제 `private Instant listedAt;` 이라, `builder()` 로 만든 DTO 의 값은 null 이다. 엔티티의
  초기값(`= java.time.Instant.EPOCH`)은 그대로다. v3 번들만 해당한다(`timestamp` 는 규칙 엔진의 타입이고 2.x 에는 없다)
- 세 에이전트 모두 "서버가 정하는 주문 시각"을 `beforeSave` 에 손으로 썼다. `orderedAt: datetime @readonly @default(now)`
  한 줄이면 된다는 것(만들 때 그 시각, 요청 값 무시, 수정해도 그대로)을 `elasticore-annotation-guide.md` 의 `@default` 에 적었다
- 확인: `SignInAndReferencesTest.aTimeSetWhenTheRowIsMade` - `joinedAt: timestamp @readonly @default(now)` 가 Java 엔티티의
  `joinedAt = java.time.Instant.now();`, Kotlin 엔티티의 `java.time.Instant.now()`, DTO 에는 초기값 없음;
  `listedAt` 은 엔티티에 `= java.time.Instant.EPOCH`, DTO 에는 없음. 이 커밋 전의 `DefaultValueLiteral.java` 로 같은 모델을
  생성해 DTO 가 `= java.time.Instant.EPOCH` 였음을 확인했다
- 관련 파일: `DefaultValueLiteral.java`(`forDto`, `of`), `SignInAndReferencesTest.java`(`aTimeSetWhenTheRowIsMade`),
  `elasticore-annotation-guide.md`

### Improvements (DSL 경고는 한 번)

#### 같은 DSL 경고가 한 실행에 한 번만 나온다
- "Ticket.agent is left out: AgentDTO is read under @access(read=owner) ..." 처럼 엔티티의 산출물(DTO, 검색 DTO)마다 도는
  규칙의 경고가 두 번씩 찍혀 문제가 둘인 것처럼 읽혔다. `SourceCode.dslWarnOnce` 가 실행마다(`resetWarnings`) 한 번만 말한다.
  `@signin` 의 경고도 같은 방식이다
- 관련 파일: `SourceCode.java`, `ServiceAccessRules.java`

### Bug Fixes (로그인 API - 리뷰)

#### `@signin` 은 본인과 관리자만 비밀번호를 바꿀 수 있을 때만, 로그인은 유일하게, `me()` 는 `@signin` 에만
- **누가 비밀번호를 바꿀 수 있는가.** `@access` 가 없는 `@signin` 엔티티(수정이 `public`)나 `update=user` 에서는 아무나
  `PUT /member {"memberId":1,"password":"x"}` 로 관리자의 비밀번호를 바꾸고 관리자로 로그인할 수 있었다 - 역할을
  `@access(admin)` 으로 막아도 관리자 행 자체를 가져가면 그만이었다. 이제 `update` 수준(없으면 `*`, 그것도 없으면 `admin`,
  `@access` 가 아예 없으면 `public`)이 `admin`·`none` 이거나, `owner` 이면서 `@owner` 가 키일 때만 로그인 API 와 `me()` 를
  만들고, 아니면 이유와 고칠 문장(`@owner(<키>) @access(read=owner, create=public, update=owner)` 등)을 DSL 경고로 낸다.
  그 엔티티의 서비스로 행을 쓰는 경로는 모두 이 수준을 묻는다(다른 엔티티 DTO 의 `@ref` 로 쓰는 길은 아래 "리뷰 2" 가
  막았다): `PUT`·`PUT /batch` 는 행마다 소유자를 확인하고, 조건 수정은 `owner` 수준이면
  관리자만(`AccessPolicy.requireAll`). 펫스토어 `Customer`, Kotlin 샘플 `Agent`, 가이드 예제는 이미 `update=owner` +
  `@owner(키)` 라 그대로 생성된다
- **로그인은 유일.** `@signin` 엔티티의 `@login` 컬럼에 `unique = true` 를 붙인다(이미 `@unique` 이거나, 키이거나, 그 컬럼
  하나만의 unique `@Index` 면 그대로). 같은 로그인이 두 행이면 `authenticate` 가 둘 다 거절하므로, 누구든 남의 로그인으로
  가입해 그 계정(관리자 포함)을 못 들어오게 할 수 있었다. 이제 두 번째 행은 다른 제약 위반처럼 409. `@signin` 없는
  `@login`·`@password` 엔티티의 로그인이 유일하지 않으면 DSL 경고. Kotlin 샘플의 `Agent.login` 컬럼이 unique 가 되었다.
  이미 운영 중인 DB 는 마이그레이션으로 unique 제약을 더한다(중복이 있으면 먼저 정리)
- **`me()` 는 `@signin` 으로 로그인하는 엔티티에만.** 앞 커밋은 `@login`·`@password` 가 있으면 모두 CoreService·유스케이스에
  `me()` 를 만들어, 손으로 `me()` 를 쓴 프로젝트가 컴파일되지 않을 수 있었다(이 섹션의 로그인 항목이 말한 "`@login`·
  `@password` 만으로는 전처럼 `authenticate` 만" 과도 달랐다). 이제 그 문장 그대로다
- **경고는 엔티티마다.** `@signin` 이 막힌 이유를 컨트롤러 규칙이 아니라 모든 엔티티에 도는 규칙(`access-signin-check`)이
  말한다 - 컨트롤러가 없는 엔티티의 "`@expose` 가 없음" 경고가 전에는 나오지 않았다. 역할 경고의 끝은 "The generated
  sign-in puts no role in the session" 으로 바꿨다(손댄 컨트롤러는 다음 줄)
- **손댄 컨트롤러에 남은 로그인.** DSL 이 더는 만들지 않는데 손댄 컨트롤러(`SessionCurrentUser` 를 쓰는 파일)가 아직
  `POST /signin`·`GET /me` 를 가지고 있거나, 세션에 역할을 넣으면(`SessionCurrentUser.signIn` 의 세 번째 인자) DSL 경고 -
  그 로그인은 생성된 검사가 믿는 세션을 계속 만들기 때문이다(`@signin` 을 옮기거나 역할 필드의 `@access(admin)` 을 지운 뒤)
- **애플리케이션마다 하나.** 한 번의 생성이 멀티 모듈(`elasticore.modules`)로 여러 애플리케이션을 만들 수 있으므로
  `@signin` 을 애플리케이션마다 센다: 단일 모듈 프로젝트는 모든 도메인을 함께, 멀티 모듈은 컨트롤러가 생기는 모듈(`control`
  의 `모듈명://` 이 가리키는 모듈, 없으면 그 blueprint 가 있는 모듈)마다. 두 모듈이 한 애플리케이션으로 함께 실행되는지는
  생성기가 알 수 없어 가이드에 "한쪽에만" 이라고 적었다. 세는 일은 실행마다 한 번, 엔티티 줄의 `@signin` 만 본다 - 전에는
  도메인마다 모든 모델의 참조 계산을 다시 해서 셌다
- **같은 세션 키.** 로그인 API 가 생기는 `@signin` 엔티티의 애플리케이션에 다른 `@login`·`@password` 엔티티가 있으면 DSL
  경고 - 그 엔티티를 손으로 `SessionCurrentUser.signIn` 하면 그 키가 `@signin` 엔티티의 키로 읽혀(직원 7 = 고객 7) `me()` 와
  소유자 검사를 통과한다
- **문자열 키.** 요청이 정하는 문자열 키(`@genid`·`@sequence` 가 아님)의 `@signin` 엔티티는 DSL 경고 - 키가 `me` 인 행은
  `GET /{id}` 가 아니라 `GET /me` 가 답한다. 가이드의 "`/me` 라는 키" 문구도 고쳤다
- 이 경고들은 모두 `SourceCode.dslWarnOnce` 로 실행마다 한 번 나온다(앞 항목). 2.x 생성기와 `jpa-springboot` 번들은
  `@signin` 을 처리하지 않으므로 그대로다
- 확인: `SignInAndReferencesTest`(수정 수준 셋과 `@expose` 없음의 경고와 생성 안 함, `@signin` 없는 엔티티에 `me()` 없음,
  로그인 unique 와 경고, 같은 세션 키 경고, 멀티 모듈의 두 애플리케이션이 각자 로그인, 문자열 키 경고, 손댄 컨트롤러의 역할·
  엔드포인트 경고), `elasticore-template-kt` 의 `ShopApiContractTest.aTakenLoginIsAConflict`(같은 로그인으로 두 번째 가입
  409, 첫 행은 그대로 로그인됨, 로그인한 상담원이 자기 로그인을 남의 것으로 바꾸면 409), 펫스토어 `ShopFlowTest`, 2.x 호환
  검사(`elcoreRulesCheck -Pstrict` 150 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra)
- 관련 파일: `ServiceAccessRules.java`(`signsIn`, `whyNoSignIn`, `updateLevel`, `credentialsGuarded`, `signInCheck`,
  `uniqueLogin`, `me`), `HexUseCaseRules.java`, `RulesGenRunner.java`(`countSignIns`, `sayApplication`, `warnIfSignInLeft`),
  `SpecFactory.java`(`signInEntities`, `otherLoginEntities`), `EntitySpec.java`(`knownOtherLoginEntities`),
  `SignInAndReferencesTest.java`, `ShopApiContractTest.kt`,
  `elasticore-annotation-guide.md`, `elasticore-rule-guide.md`, `elasticore-dsl-guide.md`

### Bug Fixes (리뷰 - 상속한 참조 확인, 다른 도메인의 값 객체, 모델 파일, TypeScript 최소 개수, 엔드포인트 경고)

#### 리뷰 - 상속: `@extend` 하위 클래스가 부모의 `requireReferences` 를 지우지 않는다
- 위 "확인할 것이 없어도 `requireReferences` 를 가진다" 뒤로, 참조를 확인하는 엔티티를 `@extend` 한 하위 클래스(가이드의
  `Album: meta: entity @extend(Item) @rollup`)가 빈 `requireReferences` 를 가졌다. 모델은 물려받은 필드까지 보고 하위
  클래스를 "참조한다"로 세지만, 본문은 자기 필드만 읽기 때문이다. Java 는 부모의 확인을 빈 메서드로 말없이 덮어썼고(하위
  클래스 객체에서 부르면 아무것도 확인하지 않음), Kotlin 은 `'requireReferences' hides member of supertype 'Item' and needs
  'override' modifier` 로 컴파일되지 않았다
- 이제 자기 확인이 없고 `@extend` 부모가 확인하면 메서드를 쓰지 않고 물려받는다. 자기 확인이 있으면 부모의 것을 먼저 부른다:
  Java 는 `@Override` 와 `super.requireReferences(lookup);`, Kotlin 은 `override fun` 과 `super.requireReferences(lookup)`
  (all-open 플러그인이 부모의 메서드를 연다). 부모가 다른 도메인에 있으면 그 도메인의 모델로 판단한다
- 확인: `SignInAndReferencesTest.aSubclassKeepsItsParentsReferenceCheck` - Java·Kotlin 에서 자기 참조가 없는 `SeAlbum`
  (`@rollup`)과 `SeDiscBase`(나뉜 루트)에는 메서드가 없고, 자기 참조가 있는 `SeBook` 은 override 하고 super 를 먼저 부른다.
  `aSubclassInAnotherDomainKeepsItsParentsReferenceCheck` - 부모(`rpa` 의 `RpItem`)와 다른 도메인(`rpb`)의 하위 클래스
  `RcAlbumBase` 에도 메서드가 없다(다른 도메인의 사실을 보지 않으면 빈 메서드가 생기는 것도 확인했다). 같은 모양의
  모델을 Kotlin 샘플(`elasticore-template-kt` 의 shop)에 잠시 넣어 `compileKotlin` 으로 컴파일했다 - 고치기 전에는 세
  하위 클래스에서 위 오류, 고친 뒤에는 없음. 확인한 뒤 모델은 뺐다
- 관련 파일: `DddReferenceRules.java`(`requireReferences`, `inheritsReferences`), `SpecFactory.java`(`registerParentReferencing`),
  `RulesGenRunner.java`, `SignInAndReferencesTest.java`

#### 리뷰 - 값 객체: 다른 도메인에서, 안쪽에서, `@required` 로 담을 때
- 선택으로 담은 값 객체를 그 값 객체를 선언한 도메인의 엔티티에서만 찾았다. `common` 의 `Address`(`zipcode: string(10)!`)를
  `shop` 의 주문이 `shipTo: Address @Embedded` 로 담아도 `Address` 의 컬럼은 `nullable = false` 그대로였다 - 위 항목이
  고친 `NULL not allowed for column "ZIPCODE"` 거절이 다른 도메인에서는 남았다. 이제 실행의 모든 도메인을 한 번 본다
- 선택으로 담은 값 객체 안에 `!` 로 담긴 값 객체(`contact: Contact @Embedded` 의 `place: Place! @Embedded`)도 선택이다 -
  바깥 값이 없으면 안쪽 컬럼도 빈다
- 필수 표시로 `!` 만 셌다. `card: Card @Embedded @required` 로만 담는 값 객체도 선택으로 읽혀 NOT NULL 이 빠졌다. 이제
  `FieldSpec.required()` 처럼 `!`·`@required`·`@nn` 을 센다
- 확인: `SignInAndReferencesTest.aValueOfAnotherDomainEmbeddedAsOptionalHasNullableColumns` - 두 도메인(`scommon` 이 값 객체를
  선언, `sdshop` 이 담음)에서 `ScAddress`·`ScContact`·`ScPlace` 는 `nullable = false` 가 없고, `@required` 로만 담는
  `ScCard` 는 있다
- 관련 파일: `SpecFactory.java`(`optionalEmbeddables(List)`, `knownOptionalEmbeddables`), `RulesGenRunner.java`,
  `SignInAndReferencesTest.java`, `elasticore-annotation-guide.md`

#### 리뷰 - 모델 파일: 주석만 있는 파일은 빌드를 멈추지 않고, 멈춘 실행은 생성 파일을 지우지 않는다
- 위 "`LOAD ERROR` 가 나면 실패" 뒤로, 모델 파일을 통째로 주석 처리해 꺼 두거나 빈 줄만 남은 `.yml` 도
  `No content to map due to end-of-input` 으로 빌드를 멈췄다. 0 바이트 파일만 건너뛰었기 때문이다. 이제 YAML 토큰이 하나도
  없는 파일은 `* EMPTY:` 로 건너뛴다. 최상위가 목록인 파일처럼 읽을 수 없는 파일은 그대로 빌드를 멈춘다
- 읽지 못한 파일 때문에 멈추는 실행도 끝에서 "DSL 이 더 만들지 않는 파일"을 지웠다. 그 파일이 선언한 엔티티의 DTO·매퍼·
  리포지토리가 `REMOVED` 되어, YAML 오타 하나에 생성 파일 삭제가 따라왔다. 이제 모델 검사가 실패한 실행은 지우지 않고
  `[elcore3] generated files the DSL no longer produces are kept until the model check passes` 를 찍는다
- 2.x 생성기(`elcore`)도 같은 로더를 쓰므로 주석만 있는 파일에 `LOAD ERROR` 와 스택 대신 `* EMPTY:` 를 찍는다. 생성 결과는
  그대로다
- 확인: `LoadErrorFailsTest.aFileOfOnlyCommentsOrBlankLinesDeclaresNothing`(주석만 있는 파일, 빈 줄만 있는 파일 - 0 으로 끝남),
  `aStoppedRunKeepsTheFilesOfWhatItCouldNotRead`(생성한 뒤 그 파일을 깨면 1 로 끝나고 `LdOrderDTO.java` 가 남으며
  `REMOVED(` 가 없음)
- 관련 파일: `YamlFileMainModelLoader.java`(`holdsNothing`), `RulesGenRunner.java`, `LoadErrorFailsTest.java`,
  `elasticore-dev-guide.md`

#### 리뷰 - TypeScript: `@minsize` 목록 검사가 실제로 생성된다
- 앞 커밋이 `validate<Name>()` 에 넣은 목록 `@minsize` 검사는 생성되지 않았다. 검사할 필드 목록이 `...Fields` 상수와 같아서
  목록 필드를 빼 두었기 때문이다(고치기 전 코드로 생성한 `validateSrOrder` 에 `lines` 검사가 없음을 확인)
- 이제 `@minsize(n)`(n 이 1 이상)인 목록은 `validate` 가 검사하고 상수에는 여전히 넣지 않는다(위 "DTO - 최소 개수" 항목)
- 확인: `SignInAndReferencesTest.theBrowserClientRefusesAnOrderWithoutLines` - `SrOrderDTO.ts` 에
  `if (!partial && (value.lines == null || value.lines.length < 1)) errors.lines = "an order needs a line";`, `SrOrderFields` 에
  `lines` 없음
- 관련 파일: `TypeScriptDtoFileRules.java`(`meta`, `minSize`), `SignInAndReferencesTest.java`, `elasticore-annotation-guide.md`

#### 리뷰 - 엔드포인트 경고: 같은 엔드포인트를 다르게 쓴 컨트롤러, 그리고 2.x 호환 모드
- 손댄 컨트롤러가 DSL 이 주는 엔드포인트를 갖고 있는지 경로 문자열 그대로 비교해서, 경로 변수 이름만 다른
  `@GetMapping("/{id}")`, Kotlin 의 `@GetMapping(value = ["/me"])`, `@RequestMapping(value = "/me", method = RequestMethod.GET)`
  으로 쓴 컨트롤러도 `... does not serve GET /{customerId} ...` 경고를 받았다
- 이제 매핑을 읽어서 비교한다. `{...}` 는 이름과 상관없이 같고 끝의 `/` 는 무시한다. `value`·`path`·위치 인자, `"..."`·
  `{...}`·`[...]`, 메서드 수준의 `@RequestMapping`(method 가 없으면 모든 메서드)을 읽고, 클래스의 `@RequestMapping` 과 주석은
  읽지 않는다. 상수나 이어 붙인 문자열처럼 읽지 못한 매핑이 있으면 그 HTTP 메서드의 엔드포인트는 경고하지 않는다(틀린 경고보다
  말하지 않는 쪽). 인자 없는 `@PostMapping`·`@PutMapping`(생성·수정)도 이제 비교하므로, 그것을 지운 컨트롤러는
  `POST (base path)` 로 경고를 받는다. 문구의 "which the DSL now gives it" 은 "which the DSL gives it" 이 되었다
- 2.x 호환 모드(`jpa-springboot`)에서는 이 경고를 하지 않는다. 그 모드는 한 번 생성한 파일을 다시 만들지 않고 TypeScript
  클라이언트도 없는데, 엔드포인트를 지운 2.x 컨트롤러에 "DSL 이 준다"·"TypeScript 클라이언트가 부른다" 는 경고가 실행마다 났다
- 확인: `SignInAndReferencesTest.endpointsServedAnotherWayAreNotMissing` - 경로 변수를 `{id}` 로 바꾸고 삭제를
  `@RequestMapping(value = "/{id}", method = RequestMethod.DELETE)` 로 쓴 컨트롤러에 경고가 없다(고치기 전에는
  `does not serve GET /{userId}, DELETE /{userId}`). Java·Kotlin 형식, 주석, 상수 경로를 읽는 단위 검사도 있다.
  `theCompatibleModeSaysNothingOfTheDevelopersController` - 호환 모드에서 `@DeleteMapping` 을 지운 컨트롤러에 경고가 없다
  (고치기 전에는 `does not serve DELETE /{userId}`)
- 관련 파일: `RulesGenRunner.java`(`warnIfEndpointsMissing`, `missingEndpoints`, `Endpoints`, `warnIfSignInLeft`),
  `SignInAndReferencesTest.java`, `elasticore-annotation-guide.md`

#### 리뷰 - 문서: 3.0.261022-rc1 전의 응답
- 규칙 가이드가 "전에는 생명주기 거절도 없는 참조도 422" 라고 했다. 없는 행을 id 로 참조한 저장은 외래 키 위반의 409
  "Conflicts with stored data" 였고 `@snapshot` 원본이 없을 때만 422 였다(위 `requireReferences` 항목). 그렇게 고쳤다
- 관련 파일: `elasticore-rule-guide.md`

### Bug Fixes (리뷰 2 - 로그인 API, 참조 확인의 도메인 순서, 상속 엔티티)

#### 리뷰 2 - 다른 엔티티의 `@ref` 로 로그인·비밀번호를 쓰지 못한다
- 레이어드 v3(`jpa-springboot-v3`, `jpa-springboot-kt`)에서 다른 엔티티의 DTO 가 `@ref(assignee.login)`·
  `@ref(assignee.password)` 로 `@signin` 엔티티를 읽으면, 매퍼가 수정 때 `to.getAssignee().setLogin(...)`·
  `setPassword(...)` 로 담당자의 행에 썼다. 티켓의 수정 수준(`update=user`)만 묻고 담당자 엔티티의 검사는 건너뛰므로, 로그인한
  아무나 관리자에게 배정된 티켓으로 관리자의 로그인을 바꾸거나 직접 계산한 PBKDF2 문자열을 넣고 관리자로 로그인할 수 있었다.
  조회에서는 티켓을 읽는 사람 모두에게 담당자의 비밀번호 해시가 나갔다. DDD·헥사고날 번들은 다른 애그리거트로 가는 `@ref` 를
  원래 읽기 전용으로 두어 그대로다
- 이제 `jpa-service-access`(2.x 번들에는 없음)의 매퍼 규칙(`access-mapper-ref`)이, 누가 쓸 수 있는지 정한 엔티티(`@access`·
  `@owner`·`@password`·필드의 `@access(admin)`)로 가는 `@ref` 를 읽기 전용으로 둔다 - 관계의 키를 가리키는 `@ref` 는 전처럼
  관계를 바꾼다. `@password` 를 읽는 `@ref` 는 DTO·매퍼·TypeScript 에서 빠지고(`access-dto-ref-password`) DSL 경고가 난다.
  그런 `@ref` 를 가진 v3 프로젝트의 매퍼에서 그 쓰기 줄이 사라진다(샘플에는 없음)
- 확인: `SignInAndReferencesTest.aRefIntoTheSigningInEntityDoesNotWriteItsCredentials` - Java·Kotlin 레이어드 v3 에서 매퍼에
  담당자의 로그인·비밀번호 쓰기와 비밀번호 읽기가 없고, 로그인 표시(`assigneeLogin`)와 아무도 지키지 않는 엔티티로 가는
  `@ref(tag.label)` 쓰기는 남고, DTO·TypeScript DTO 에 `assigneePassword` 가 없다(고치기 전 코드에서는 실패)
- 관련 파일: `ServiceAccessRules.java`(`refsIntoGuardedRowsAreReadOnly`, `noPasswordThroughRef`, `refEntity`, `guarded(Entity)`),
  `SourceCode.java`(`refHidden`), `MapperBaseRules.java`, `elasticore-annotation-guide.md`

#### 리뷰 2 - 참조 확인이 도메인을 생성하는 순서에 따르지 않는다
- 위 "상속: 부모의 `requireReferences`" 는 다른 도메인의 부모를 그 도메인의 모델로 판단하는데, id 참조로 바꾸는 일
  (`IdReferences`)을 도메인을 생성할 때마다 했다. 하위 클래스의 도메인이 먼저 생성되거나(`caa` 가 `czz` 보다 앞) `-Pdomain` 으로
  하위 클래스의 도메인만 생성하면 부모가 아직 참조하지 않는 것으로 읽혀, Kotlin 은 `'requireReferences' hides member of
  supertype` 으로 컴파일되지 않고 Java 는 부모의 확인을 덮어썼다
- 이제 어떤 도메인도 생성하기 전에 실행의 모든 모델을 각 도메인의 규칙 선택대로 한 번 바꾼다(`shapeReferences`, 컨텍스트 맵
  검사 뒤). 도메인 필터가 있어도 같다
- 확인: `SignInAndReferencesTest.aSubclassKeepsItsParentsCheckWhicheverDomainComesFirst` - 자기 참조가 있는 하위 클래스의 도메인이
  먼저인 모델에서 Java `CaBookBase` 는 `@Override` 와 `super.requireReferences(lookup);`, Kotlin 은 `override fun` 과 `super`,
  `-Delasticore.domain=caa` 로 하위 도메인만 생성해도 같다(고치기 전 코드에서는 실패)
- 관련 파일: `RulesGenRunner.java`(`shapeReferences`), `SignInAndReferencesTest.java`

#### 리뷰 2 - 로그인 API 의 남은 조건과 문서
- **요청이 정하는 키의 삭제.** 키가 생성되지 않는 `@signin` 엔티티(`handle: string(20) @id`)를 아무나 지울 수 있으면, 누가
  `alice` 를 지우고 `alice` 로 다시 가입해 `alice` 가 주인이던 모든 행을 가졌다. 이제 `delete` 수준(없으면 `*`, 그것도 없으면
  `admin`)이 `admin`·`none` 이거나 키를 가리키는 `@owner` 의 `owner` 일 때만 로그인 API 를 만들고, 아니면 고칠 문장(`delete=owner`
  와 `@owner(<키>)`, `delete=admin`, 또는 키를 생성되게)을 DSL 경고로 낸다. 키를 생성하게 하라고만 요구하지 않은 것은, 이름 키
  (`handle`)도 정상인 모델이고 `delete` 를 적지 않으면 `admin` 이라 흔한 선언은 그대로 통과하기 때문이다
- **`*` 는 다른 항목과 함께 써도 센다.** 로더가 `@access(read=public, create=public, *=owner)` 의 `*=owner` 를 키로 읽지 못해
  (`AbstractModelLoader.parseParameters` 의 키 패턴 `\w+`) 버렸다 - 수정이 `admin` 으로 읽혀 본인의 비밀번호 변경이 막혔고,
  `*=user` 는 남이 바꿀 수 있는데도 로그인 API 가 생겼다. 이제 키는 단어 또는 `*` 하나다. 2.x 에는 영향이 없다: 저장소의 어느
  blueprint 도 어노테이션 인자에 `*=` 를 쓰지 않고, `*=x` 하나만 쓴 경우는 전에도 같은 글자(`*=x`)로 읽혔으며, 2.x 호환 검사
  (`elcoreRulesCheck -Pstrict` 150 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra)가 그대로다
- **로그인 unique 는 `@signin` 만으로.** 코드가 하던 대로 문서를 맞췄다: 로그인 API 가 거절되어도 `@signin` 엔티티의 로그인
  컬럼은 unique - `@access` 를 고칠 때 테이블이 바뀌지 않는 쪽이 낫다
- **여러 모듈이 각자 `@signin`.** 멀티 모듈 실행에서 둘 이상의 모듈이 `@signin` 엔티티를 가지면 `@signin is counted per module:
  ... if these modules run as one application, only one of them may say @signin.` 을 한 번 낸다
- 확인: `SignInAndReferencesTest.whoMayChangeThePasswordDecidesTheSignIn` - 모듈마다 하나씩, `update=admin`·`update=none`·`*=owner`
  하나·`*=owner` 와 다른 항목·삭제가 본인뿐인 문자열 키는 로그인 API 가 생기고, `*=user` 하나·`*=user` 와 다른 항목·`delete=user`
  인 문자열 키는 생기지 않으며 그 경고와 모듈 경고가 나온다(고치기 전 코드에서는 실패).
  `theBrowserClientOfARefusedEntityDoesNotSignIn` - 거절된 `@signin` 엔티티의 TypeScript 에 `signIn`·`me` 가 없다
- 관련 파일: `ServiceAccessRules.java`(`keyReusable`, `level`, `whyNoSignIn`), `AbstractModelLoader.java`,
  `RulesGenRunner.java`(`countSignIns`), `SignInAndReferencesTest.java`, `elasticore-annotation-guide.md`,
  `elasticore-rule-guide.md`, `elasticore-dsl-guide.md`

#### 리뷰 2 - `@extend` 하위 엔티티: 없는 리포지토리, 자기 테이블
- v3·Kotlin 실행은 서비스(`@expose`·`@service`)와 자기 키가 있는 엔티티에만 리포지토리를 쓰는데, 도메인의
  `RepositoryHelper` 는 물려받은 키가 있는 `@extend` 하위 엔티티와 서비스가 없는 엔티티의 리포지토리도 주입했다
  (`SeAlbumRepository` - 없는 클래스라 컴파일되지 않음). 이제 v3·Kotlin 의 헬퍼는 실행이 쓰는 리포지토리만 주입한다. 2.x 호환
  출력은 그대로다
- `@extend(X) @rollup` 하위 엔티티가 v3 에서 자기 `@Table(name = "se_book")` 을 가져, 가이드("부모 테이블로 컬럼 통합")와
  2.x 출력과 달리 자기 테이블에 매핑되었다. 이제 v3 에서도 `@Table` 없이 `@DiscriminatorValue("SeBook")`(`@rollup(discriminator=...)`
  이 있으면 그 값)를 붙여 부모의 테이블(JPA 기본 단일 테이블)에 들어간다
- 확인: `SignInAndReferencesTest.theRepositoryHelperNamesOnlyRepositoriesThatExist`, `aRolledUpSubclassHasNoTableOfItsOwn`
  (Java·Kotlin 헥사고날, 고치기 전 코드에서는 실패). 샘플에는 `@rollup` 이 없어 실행으로는 확인하지 않았다
- 관련 파일: `RepositoryHelperBaseRules.java`, `JpaEntityAnnotationRules.java`, `V3SkeletonCoverageTest.java`,
  `SignInAndReferencesTest.java`

#### 리뷰 2 - 문서
- 위 "로그인 API - 리뷰" 의 "행을 쓰는 경로는 모두 이 수준을 묻는다" 를 그 엔티티의 서비스로 쓰는 경로로 좁혔고, 세 템플릿의
  이 문서 사본을 다시 복사해 원본과 같게 했다(`cmp`)
- 관련 파일: `elasticore-history.md`

### Improvements (3.0 전에 - 검색, 쓰기 경로, Ossie, TypeScript 클라이언트)

#### v3 의 `@search(like)` 는 대소문자를 무시한다
- "이름 일부로 검색" 은 모두가 `@search(like)` 로 쓰는데, 생성된 `c.like(r.get("name"), "%" + trim(name) + "%")` 는 H2·
  PostgreSQL·Oracle 에서 대소문자를 구분해 `{"name":"gold"}` 가 Goldfish 를 찾지 못했다(펫스토어 실험의 세 팀 중 둘이 그대로
  냈다). 대소문자를 무시하는 `%__%` 는 Kotlin 에 `x!!.toLowerCase()` 를 써서 Kotlin 2.1 부터 컴파일 오류였다(Boot 4 는 Kotlin
  2.2). 3.0 뒤에 바꾸면 모든 v3 프로젝트의 동작이 바뀌므로 지금 바꾼다
- v3 모드(헥사고날·DDD, `-Pv3`, 모든 Kotlin 빌드)에서 문자열 필드의 `like` 는 `%__%` 와 같다: `c.like(c.lower(...), "%" +
  trim(v).toLowerCase(java.util.Locale.ROOT) + "%")`, Kotlin 은 `trim(v)!!.lowercase()`. 검색 DTO 의 `@Schema` 도 대소문자
  무시 문구다. `%_%`·`%%` 는 모든 모드에서 대소문자를 구분하고, 기호들의 뜻은 그대로다. 소문자 변환은 `Syntax.lowerCase` 를
  지나 언어마다 맞게 쓰이고(로캘 무관), 대소문자 무시 조건 `%__%`·`%__`·`__%` 는 `like` 처럼 값의 앞뒤 공백을 지운다
- 모르는 조건(`@search(ilike)`, `gt` 등)은 전처럼 정확 일치지만 v3 모드에서는 DSL 경고를 낸다. 가이드가 조건 없는
  `@search` 를 정확 일치(`eq`)라 했던 것은 `auto` 로, 구분자 `$` 는 실제 코드대로 `,` 로 고쳤다
- 2.x 호환 출력(레이어드 Java)은 그대로다 - 같은 `@search(like)` 가 그곳에서는 구분한다(마이그레이션 가이드에 적음).
  펫스토어·`elasticore-template-ddd`·`elasticore-template-kt` 의 `like` 검색 매퍼 줄이 바뀐다(생성 파일, 커밋 안 됨)
- 확인: `MapperSpecTest.likeIgnoresCase_v3`·`ignoreCase_kotlin`·`anUnknownConditionIsSaid`, `SearchDtoTest.
  likeCondition_cleanOutputIgnoresCase`(고치기 전 코드에서는 실패), 2.x 문구는 `likeVariants` 가 그대로 지킨다. 펫스토어
  `ShopFlowTest.aVisitorBrowsesTheCatalog` 에 `{"name":"gold"}` -> Golden Retriever, Goldfish, `{"name":"  BULL "}` -> Bulldog 추가.
  같은 모양(`like`, `%__%`, `__=`, `__%`, `%__`)을 샘플 두 개에 잠시 넣어 Java(헥사고날)·Kotlin(헥사고날, Kotlin 1.9.24)으로
  컴파일하고 Java 쪽은 H2 에서 `"  gold "` 로 찾았다 - 확인 뒤 뺐다. Kotlin 2.2 컴파일은 하지 않았다(`lowercase()` 는 1.5 부터 있음)
- 관련 파일: `MapperSpecRules.java`(`lowered`, `knownCondition`), `SearchDtoBaseRules.java`(`effectiveCondition`),
  `Syntax.java`(`lowerCase`), `elasticore-annotation-guide.md`, `elasticore-dev-guide.md`, `elasticore-migration-guide.md`

#### `PUT /batch` 는 행마다 `update(dto)` 를 부른다
- v3 `batchUpdate`(Java·Kotlin 서비스 템플릿)가 `update()` 의 본문을 복사해 돌아서, 확장 서비스에서 `update()` 를 override
  한 규칙("쓴 사람만 고친다", "리뷰의 상품은 바뀌지 않는다")을 `PUT /review/batch` 가 건너뛰었다(실험에서 관리자가 남의 리뷰를
  고치고 리뷰를 다른 상품으로 옮김). 이제 한 트랜잭션 안에서 `for (dto : dtoList) update(dto);` - override, `readBack`, 409 변환이
  일괄 수정에도 적용된다. `update()` 를 override 한 기존 v3 프로젝트는 다시 생성하면 그 규칙이 일괄 수정에도 걸린다(그것이 고침)
- `@restExclude(update)` 는 이제 `batchUpdate` 도 함께 뺀다(서비스·컨트롤러·유스케이스·TypeScript) - 남으면 없는 `update` 를
  부르기 때문이다. 같은 이유로 `deleteById` 는 `delete`(목록)와 `POST /delete` 를, `delete` 는 `POST /delete` 를, `save` 는
  `POST` 를 데려간다(`ControllerBaseRules.restExcluded`). 2.x 호환 컨트롤러는 적힌 대로만 뺀다
- 확인: `WritePathsTest.aBatchUpdateGoesThroughUpdate`, `excludingUpdateTakesTheBatchWithIt`(Java 헥사고날·레이어드 v3, Kotlin;
  고치기 전 코드에서는 실패). 임시 도메인으로 `elasticore-template-ddd` 에서 실행: `update()` 를 override 한 서비스의
  `batchUpdate` 가 그 override 에 거절된다
- 관련 파일: `v3/service.tmpl`, `kt/service.tmpl`, `JpaServiceFileRules.java`, `ControllerBaseRules.java`,
  `TypeScriptApiRules.java`, `elasticore-rule-guide.md`

#### `@restExclude` 가 헥사고날 빌드를 깨지 않는다
- 서비스에서 뺀 메서드를 유스케이스 인터페이스가 그대로 약속해 `@restExclude(batchUpdate)` 가 `XxxService is not abstract
  and does not override abstract method batchUpdate(...) in XxxUseCase` 로 컴파일되지 않았다(`save`·`delete`·`deleteById` 도
  같음). 이제 유스케이스도 같은 목록으로 뺀다. Kotlin 서비스는 원래 메서드를 빼지 않으므로(아래 3.1 목록) 유스케이스·
  컨트롤러만 따르고 컴파일된다
- 확인: `WritePathsTest.theUseCaseDropsWhatTheServiceDropped`(Java·Kotlin), 임시 도메인을 `elasticore-template-ddd`(Java)·
  `elasticore-template-kt`(Kotlin)에 넣어 `@restExclude(update)`·`@restExclude(batchUpdate, deleteById)` 를 컴파일
- 관련 파일: `HexUseCaseRules.java`

#### `@readonly` 소유자 필드가 소유자를 잃지 않는다
- `@owner(customerId)` 의 `customerId` 가 `@readonly` 면, 생성 시 서비스가 DTO 에 넣은 소유자를 매퍼가 쓰지 않아(서버가 정하는
  필드) 행이 소유자 없이 저장되었고, 주인은 자기 행을 수정하려다 404 를 받았다. 이제 그 경우 서비스가 생성 시 엔티티에 직접
  넣는다(`claimOwner(entity)`, 관리자 포함 로그인한 누구든 - 요청이 그 값을 보낼 수 없으므로. `runAsSystem` 안에서는 손대지
  않음. 나뉜 루트는 `creation()` 으로). 수정에서는 매퍼가 쓰지 않으므로 누구의 수정이든 저장된 값이 남는다
- 확인: `WritePathsTest.aReadonlyOwnerIsClaimedOnCreateAndKept`(고치기 전 코드에서는 실패). 임시 도메인을 H2 에서 실행: 회원이
  만든 리뷰의 `writerId` 가 그 회원, 수정 뒤에도 그대로, 관리자가 만들면 관리자, 관리자의 수정 뒤에도 회원의 것,
  `@behavior` 로 나뉜 루트도 `creation()` 으로 들어간다
- 관련 파일: `ServiceAccessRules.java`, `elasticore-annotation-guide.md`

#### 참조의 `@updatable(false)` 가 동작한다
- DDD·헥사고날에서 다른 루트로 가는 참조는 식별자 필드(`itemId`)로 쓰이는데, `item: Item @updatable(false)` 의 표시가 그
  필드로 가지 않아 수정 요청이 리뷰를 다른 상품으로 옮겼다. 이제 식별자 필드가 물려받아 `@Column(name = "item_item_id",
  updatable = false)` 이고, 매퍼는 행에 값이 없을 때(생성)만 쓴다(`&& to.getItemId() == null`)
- 확인: `WritePathsTest.aReferenceThatMayNotChangeKeepsItsRow`(Java·Kotlin, 고치기 전 코드에서는 실패), 임시 도메인을 H2 에서
  실행: 다른 `itemId` 로 수정한 리뷰가 처음 상품에 남는다
- 관련 파일: `IdReferences.java`, `MapperBaseRules.java`, `elasticore-annotation-guide.md`

#### Ossie: `count(엔티티)` 가 어느 dataset 을 세는지 말한다
- `count(Customer)` 가 dataset 을 이름하지 않는 `COUNT(*)` 로 나가, BI 도구가 어느 테이블을 세는지 알 수 없었다. 이제 키
  컬럼을 센다: `COUNT(Customer.customer_id)`(키는 비지 않으므로 행 수와 같다). 조건이 있는 `count(...) where ...` 는 조건이
  이미 dataset 의 컬럼을 이름하므로 그대로다
- 확인: `OssieMetricsTest.countsRowsOrValuesAndDividesSafely`(고치기 전 코드에서는 실패). 샘플의 지표는 모두 조건이 있어
  내보낸 `semantic_model.yml` 은 바뀌지 않았다
- 관련 파일: `OssieMetrics.java`(`Model.keyColumn`), `OssieSemanticModelExporter.java`, `elasticore-dsl-guide.md`

#### TypeScript 클라이언트를 빌드와 함께 (`typescriptClient`)
- `src/main/ts` 는 `elcore3ts` 만 쓰고 빌드는 그 태스크를 부르지 않아, 모델을 바꾼 뒤 클라이언트에 새 필드·엔티티가 없었다
  (실험의 2단계 세 팀 모두). 플러그인의 `elasticore { typescriptClient = true }`(기본 false)는 `elcore3` 뒤, 컴파일 전에
  `elcore3ts` 를 실행한다. 설정이 없는데 생성된 `.ts`(첫 줄 `//ecd:`)가 있으면 `elcore3` 가 끝에 태스크와 설정 이름을 말하는
  경고를 한 번 낸다
- 확인: `ElastiCorePluginTest.theTypescriptClientRunsWithTheBuildWhenAsked`(설정 없이 `compileJava` 가 `elcore3ts` 를 부르지
  않음, 설정하면 `elcore3` -> `elcore3ts` -> `compileJava` 순서, `elcore3` 에 `elasticore.rules.tsWithBuild=true`),
  `StaleClientTest`(생성된 클라이언트만 경고, 설정·TypeScript 실행에서는 경고 없음)
- 관련 파일: `ElastiCoreExtension.java`, `ElastiCorePlugin.java`, `RulesGenRunner.java`(`staleClient`),
  `elasticore-rule-guide.md`, `elasticore-env-guide.md`

### Bug Fixes (리뷰 3 - cascade 로 담은 로그인 행, 생성기의 남은 차이)

#### 리뷰 3 - cascade 로 DTO 에 담은 행으로 로그인·비밀번호를 쓰지 못한다
- `QmProfile @access(update=user)` 의 `member: QmMember @OneToOne @cascade(merge) @dtype(QmMemberDTO)` 처럼 `@signin`
  엔티티를 DTO 로 통째로 담으면, 역매핑이 요청의 `member` 를 관계에 붙이고(`toEntity` 는 `login`·`password` 를 보낸 그대로
  복사) 저장이 cascade 로 회원 행에 merge 했다. 로그인한 아무나 `PUT /qmProfile` 로 회원 1 의 로그인과 (직접 만든 PBKDF2)
  비밀번호를 바꿀 수 있었다 - v3 의 네 번들(레이어드·헥사고날, Java·Kotlin) 모두. `@ref` 의 같은 문제는 리뷰 2 에서 막았다
- 이제 `jpa-service-access`(2.x 번들에는 없음)의 매퍼 규칙 `access-mapper-nested` 가, 누가 쓸 수 있는지 정한 엔티티
  (`@access`·`@owner`·`@password`·필드의 `@access(admin)`, `@ref` 와 같은 기준)로 가는 **cascade 관계**를 역매핑에서 쓰지
  않는다. 매퍼가 다시 쓰는 다른 도메인의 `List<...> @OneToMany @cascade` 도 같다. DTO 가 함께 보내는 키
  (`memberMemberId`)는 전처럼 관계를 가리키고, 응답(정방향)에는 그대로 담긴다. 빌드가 필드를 짚어 DSL 경고를 낸다
  (`QmProfile.member is not written from the DTO: QmMember says who may write it ...`). 수정만이 아니라 생성도 같다 - 요청에
  담아 보낸 그 행은 저장되지 않는다(3.0 전이라 v3 번들의 기본값을 바꾼다 - 검사를 건너뛰는 쓰기 경로를 닫으려고). cascade 가 없는 관계(저장이 그 행을
  쓰지 않음)와 검사가 없는 엔티티로 가는 cascade 는 그대로다. 직접 쓴 `@jpa:OneToOne(cascade = ...)` 도 cascade 로 본다
  (관계 어노테이션 안의 `cascade =` 만, 낱말이 들어 있다고 cascade 가 아님)
- DDD·헥사고날에서 같은 애그리거트의 멤버(`QdAccount @aggregate(QdCustomer)`)는 루트와 함께 오는 것이 설계라 위 규칙에서
  빠지고(`ddd-mapper-member-marks`) 루트가 쓴다. 대신 멤버에는 자기 서비스가 없어 루트의 서비스가 해시·검사하지 못하던 멤버의
  `@password` 와 `@access(admin)` 필드를 DTO 에서 쓰지 않는다(`ddd-mapper-member-guards`, DSL 경고
  `QdAccount.secret is not written from a DTO: QdAccount is a member of QdCustomer's aggregate ...`) - 전에는 루트의 소유자가
  관리자만 쓰는 필드를 바꾸고 해시되지 않은 비밀번호를 저장할 수 있었다. 그런 필드는 루트에 둔다
- `@signin` 을 거절(DSL 경고)하는 대신 이렇게 한 까닭: 모델은 맞다 - 프로필이 회원을 담아 보여 주는 것은 정상이고, 틀린 것은
  생성된 매퍼가 그 행의 검사를 건너뛰는 쓰기 경로다. DSL 은 한 줄 그대로 두고 생성 코드가 안전한 쪽을 기본값으로 한다
  (`@ref` 를 읽기 전용으로 둔 리뷰 2 와 같은 원칙). `@signin` 이 아닌 `@owner`·`@access` 엔티티도 같은 구멍이었다
- 확인: `NestedGuardedRowTest`(리뷰의 모델 + 다른 도메인의 cascade 목록 + 검사 없는 엔티티로 가는 cascade·목록 + 같은
  애그리거트의 멤버, 레이어드·헥사고날 Java·Kotlin, 고치기 전 코드에서는 실패 - 리뷰의 시험 출력이 쓰기를 보였다),
  `aNativeCascadeIsACascade`
- 관련 파일: `ServiceAccessRules.java`(`access-mapper-nested`), `DddMapperRules.java`, `SourceCode.java`(`writtenByRoot`),
  `elasticore-annotation-guide.md`

#### 리뷰 3 - 프로젝트 규칙이 `update` 만 빼도 `batchUpdate` 가 함께 빠진다
- `batchUpdate` 가 `update(dto)` 를 부르게 된 뒤, 프로젝트 규칙이 `ctx(e).excludeMethod("update")` 만 하면 Java 서비스가
  없는 `update` 를 불러 컴파일되지 않았다(`@restExclude` 로 뺄 때만 함께 빠졌다). 이제 v3·Kotlin 출력의 `SourceCode` 가 뺀
  메서드를 부르는 것도 함께 뺀다(`@restExclude` 와 같은 짝: `update` -> `batchUpdate`, `deleteById` -> `delete`·`deleteBatch`,
  `delete` -> `deleteBatch`, `save` -> `create`). 2.x 호환 출력은 적힌 대로만 뺀다
- 확인: `ServiceFileV3Test.excludingUpdateByARuleTakesTheBatch`
- 관련 파일: `SourceCode.java`(`withCallers`), `ControllerBaseRules.java`, `elasticore-rule-guide.md`

#### 리뷰 3 - TypeScript 클라이언트가 서버의 모드를 따른다
- 클라이언트는 언제나 v3 서버처럼 `@restExclude(update)` 에 `batchUpdate` 까지 뺐다. 2.x 호환 서버는 `PUT /batch` 를
  남기므로 클라이언트에 그 메서드가 없었다. 이제 TypeScript 실행이 서버의 모드(v3 스위치·아키텍처, Kotlin, v3 에만 있는
  규칙, 그리고 서버를 만든 마지막 `elcore3` 가 `build/elasticore/server.mode` 에 남긴 값 - `-Pv3` 를 `elcore3` 에만 준
  경우)를 보고, 2.x 서버면 적힌 것만 뺀다
- 확인: `WritePathsTest.theClientOffersWhatItsServerKeeps`(헥사고날 서버의 클라이언트에는 없고, 2.x 서버의 클라이언트에는
  `batchUpdate` 가 있음)
- 관련 파일: `TypeScriptApiRules.java`, `RulesGenRunner.java`(`serverIsClean`), `SpecFactory.java`(`compatibleServer`)

#### 리뷰 3 - `@rollup` 판별값이 2.x 와 같다
- v3(`JpaEntityAnnotationRules`)와 2.x 호환 규칙(`JpaEntityClassRules`)이 `@rollup(Item)` 의 값을 판별값으로 써
  `@DiscriminatorValue("Item")` 을 냈다 - 부모의 값과 겹칠 수 있다. 2.x 생성기(`EntitySrcFilePublisher`)는 그 값을 읽지 않고
  `discriminator=`, `target=`, 엔티티 이름 순서로 정한다. 이제 두 규칙 모두 그렇다. `@rollup(X)` 를 쓴 프로젝트는 `jpa-springboot`(2.x 호환) 출력의 판별값도
  바뀌며, 2.x 생성기와 같아지는 쪽이다(템플릿에는 `@rollup` 이 없어 `elcoreRulesCheck` 가 덮지 않으므로, 임시 모델을 2.x
  생성기·`jpa-springboot`·v3·Kotlin 으로 생성해 `@DiscriminatorValue` 가 같음을 확인 - 엔티티 파일이 첫 줄 `//ecd:` 말고
  같았다)
- 확인: `EntityClassAnnotationTest.rollUp_ownValueIsNotTheDiscriminator`(고치기 전 코드에서는 실패),
  `NestedGuardedRowTest.aRolledUpEntityIsToldApartByItsName`(v3 네 가지), `SignInAndReferencesTest.aRolledUpSubclassHasNoTableOfItsOwn`
- 관련 파일: `JpaEntityAnnotationRules.java`, `JpaEntityClassRules.java`, `elasticore-annotation-guide.md`

#### 리뷰 3 - 경고 문구, javadoc, `*` 키
- `@search(%)` 는 끝 일치(`like '%' + 값`)를 만드는데 경고는 "searches by equality" 라고 했다. 이제
  `@search(%) searches by the end of the value (like '%' + value) - say %_ for that, _% for the start or %_% for any part.`
  (`MapperSpecTest.aLonePercentIsSaidAsTheEndsWithItIs`)
- 생성된 TypeScript 클라이언트가 있으면 `elcore3` 가 매번 경고했다 - 빌드 스크립트의 다른 태스크로 `elcore3ts` 를 부르는
  펫스토어·`elasticore-template-kt` 에서도. 이제 `elcore3ts` 가 만든 모델의 지문(블루프린트 도메인 폴더의 `.yml`(줄바꿈 무시),
  `architecture`·`language`·`rules` 설정, ElastiCORE 버전)을
  `build/elasticore/ts-client.model` 에 남기고, `elcore3` 는 클라이언트가 지금 모델보다 오래되었을 때만
  `... holds a generated TypeScript client made from an older model ...` 을 낸다 (`StaleClientTest`)
- `MapperSpecRules.nn()`·`SearchDtoBaseRules.isNullCheck()` 의 javadoc 이 새 메서드 위로 밀려나 있던 것을 제자리로
- 로더가 `*=` 를 키로 읽는 것(`@access(read=public, *=owner)`)을 `@access` 안에서만 한다. 다른 어노테이션, `--` 주석이
  만드는 `@label` 까지 2.x 의 패턴 그대로라 2.x 가 읽는 값은 바뀌지 않는다(전에는 `-- x *= 2` 같은 주석의 라벨이 달라졌음)
  (`StarParameterTest.aStarOutsideAccessIsReadAs2xDid`)
- 관련 파일: `MapperSpecRules.java`, `SearchDtoBaseRules.java`, `RulesGenRunner.java`(`modelDigest`, `writeStamp`),
  `AbstractModelLoader.java`, `elasticore-annotation-guide.md`, `elasticore-rule-guide.md`

### Bug Fixes (런타임 - 오류 응답의 언어)

#### 한 응답은 한 언어 - 기본은 `spring.web.locale`, 서버 머신의 언어는 쓰지 않는다
- 한국어 OS 의 서버에서 v3 앱의 400 이 두 언어로 나갔다. Bean Validation 의 기본 메시지(`Size`, `NotNull`, `Min` ...)는
  서버 머신의 언어(`크기가 0에서 10 사이여야 합니다`), DSL 문장과 제목은 영어였다. `@Valid` 가 돌 때 Spring 의 resolver 가
  헤더 없는 요청에 컨테이너의 언어(JVM 의 ko_KR)를 주었고, `ErrorMessages` 는 코드로만 찾고 못 찾으면 그 문장을 그대로
  썼다. 번역이 없는 `sv` 도 JDK 의 bundle fallback 으로 한국어였고, 애플리케이션의 `ValidationMessages_ko` 나
  `messages_ko` 가 있으면 `en` 요청까지 한국어가 섞였다. 가이드는 반대를 약속하고 있었다
- 이제 `ErrorMessages` 가 언어를 한 번 정하고 코드 찾기와 기본 메시지에 함께 쓴다(Spring Boot 3·4, Java·Kotlin 공통 런타임):
  - Spring MVC 의 기본 resolver(`AcceptHeaderLocaleResolver`)면 `Accept-Language` 의 언어 중 애플리케이션이 번역한 언어
    (`messages_<언어>`, 도메인 번들 옆 `_<언어>`, `ValidationMessages_<언어>` 파일), 기본 언어, 영어 중 처음 것. 없으면 기본
    언어 = `spring.web.locale`, 설정이 없으면 번들의 기본 문구(Locale.ROOT)
  - 영어는 자기 `_en` 파일이 없으면 Locale.ROOT 로 바꾼다 - 다른 영어 locale 은 `_en` 이 없는 bundle 이 머신의 언어로
    fallback 한다
  - 번역 파일 판단은 fallback 없는 `ResourceBundle.Control` 로, 스레드 class loader 로 한다(언어별 캐시)
  - 코드 번역이 없으면 Hibernate Validator 의 기본 메시지 템플릿(`{jakarta.validation.constraints.*}`)을 그 언어로 숫자와
    함께 다시 말한다(`BeanValidation.messageIn`, Spring 이 구성한 validator 의 interpolator). DSL 문장은 그대로다
  - 세션·쿠키·고정 등 다른 resolver 는 전의 규칙 그대로(헤더 없고 resolver 가 컨테이너 언어를 돌려준 요청만 기본 문구)
  - 서비스 측 검증용 `violation(code, violation, message)`: 코드(`Size.lines`)를 Spring 과 같은 값(`{0}` 필드, 속성 이름순)으로
    찾고, 없으면 같은 방식으로 다시 말한다
- v3 기본 출력 변경: `v3/apiErrors.tmpl`·`kt/apiErrors.tmpl` 의 서비스 측 검증 한 줄이 `messages.violation(...)` 이 된다.
  이유: generate-once 파일이라 3.0 이후에는 바꿀 수 없고, 이 줄 없이는 서비스 측(`PUT`) 400 만 머신의 언어로 남는다.
  `@Valid` 경로(`messages.text(f)`)는 런타임만 올려도 고쳐진다. 런타임 동작 변경(이유: 가이드가 약속한 동작): 한국어 머신에서
  헤더 없는 400·`en`·번역 없는 언어(`fr`, `sv`)의 기본 메시지가 영어(또는 `spring.web.locale`)로 나간다
- 바뀌지 않는 것: 2.x 생성기와 `jpa-springboot` 번들(`spring-api-errors` 가 없음 - `elcoreRulesCheck -Pstrict` 150 MATCH /
  0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra). 런타임 POM - `spring-webmvc` 는 `compileOnly`
- 확인: `ErrorLanguageOnAKoreanServerTest` (`-Duser.language=ko` JVM 에서만 도는 `koreanServerTest` 작업, 두 런타임 모두):
  헤더 없음·`sv`·`fr`·`en` 영어, `spring.web.locale: ko` 한국어(그래도 `en` 은 영어), `de;q=0.9, ko;q=0.8` 한국어, 애플리케이션의
  `ValidationMessages_ko`·`_ko` 만 있는 MessageSource 키가 `en` 에 새지 않음, 코드 번역(`Size.login=...{1}...`) 우선, 서비스 측
  위반, 세션 resolver. Pet Store `ShopFlowTest`(테스트 JVM 이 ko_KR). Pet Store 를 `-Duser.language=ko -Duser.country=KR` 로
  띄워 가입(`@Valid`)·고객 수정(서비스 측)에 헤더 없음/`en`/`sv` 는 `size must be between 0 and 10`, `ko`·`de;q=0.9, ko;q=0.8` 은
  `크기가 0에서 10 사이여야 합니다` 와 `잘못된 요청` 인 것을 확인했다
- 남은 것: `ValidationMessages_<언어>` 만 두고 기본 파일이 없는 애플리케이션은 그 파일이 없는 언어에서 머신의 언어로
  fallback 한다(Hibernate Validator 의 locator, Boot 버전마다 다른 설정 - 가이드에 "쓰지 마세요"). `spring.messages.basename` 으로
  바꾼 번들에만 있는 언어는 번역으로 보지 않는다. DSL 의 `@email`·날짜 제약 문장은 영어 그대로(TS 클라이언트와 같은 문장)
- 관련 파일: `ErrorMessages.java`, `BeanValidation.java`, `elasticore-springboot3/build.gradle`, `elasticore-springboot4/build.gradle`
  (`koreanServerTest`), `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `ErrorLanguageOnAKoreanServerTest.java`, `ErrorMessagesTest.java`,
  `elasticore-sample-petstore/build.gradle`, `ShopFlowTest.java`, `elasticore-rule-guide.md`

### Improvements (오류 응답 - 컨트롤러에 닿기 전의 거절)

#### Spring MVC 가 거절한 요청도 `code` 가 있는 같은 본문으로 - `RequestShapeErrors`
- JSON 이 아닌 본문, 타입이 받지 않는 값(`quantity: -1`, 없는 enum 코드, 숫자 자리의 글자), 없는 파라미터, 파라미터 제약,
  없는 경로·메서드(404·405), 406·415 는 생성된 `XxxApiErrors` 에 처리기가 없어 Spring Boot 의 `{timestamp, status, error,
  path}` 로 나갔다. 가이드와 kit 은 모든 거절에 `code` 가 있다고 했고, 에이전트가 이것을 디버깅하느라 한 단계를 썼다
- 런타임에 `io.elasticore.springboot3.validation.RequestShapeErrors` (처리기 `RequestShapeAdvice` 를 등록하는 클래스 - 처리기는
  애플리케이션 전체에 답하는 `@RestControllerAdvice`, `@Order(LOWEST_PRECEDENCE)`)와 `RequestErrors`, Jackson 2/3 로 두 벌인 `JsonPaths`(springboot3 `src/jackson2/java`, springboot4
  `src/jackson3/java`)를 더했다. 400 `unreadable`, 400 `invalid` + `fieldErrors[].code` `typeMismatch`(필드는 Jackson 경로:
  `lines[0].quantity`), `NotNull`, 제약 이름, 404 `not-found`, 405 `method-not-allowed`(`Allow` 그대로), 406 `not-acceptable`,
  415 `unsupported-media-type`. 손으로 쓴 컨트롤러의 `@Valid` 본문도 같은 모양. 브라우저(`Accept: text/html`)의 404·405 는
  Spring Boot 의 오류 페이지를 둔다. 메시지는 모든 도메인 번들(`i18n/*-messages.properties`)과 그 옆 번역에서 찾는다
- Jackson 2 의 생성 enum(`CustomDeserializer` -> `fromCode`)이 거절한 값은 `ValueInstantiationException` 이 아닌
  `JsonMappingException` 으로 와서, 그 원인의 메시지(`Unknown PaymentMethod code: XX`)를 쓴다. JSON 이 깨진 위치가 중첩 객체 안이어도
  필드 오류가 아니라 `unreadable` 이다(Jackson 이 경로를 붙여도)
- v3 기본 출력 변경: `v3/apiErrors.tmpl`·`kt/apiErrors.tmpl` 에 `@Import(RequestShapeErrors.class)` 한 줄. 이유: 처리기 본문을
  개발자 파일에 얼리지 않고 런타임에 두어, 이후 고침(색인 없는 키, 예외 종류)이 라이브러리로 간다. 공통 코드(`ErrorCatalog.shared()`)에
  `unreadable`, `method-not-allowed`, `unsupported-media-type`, `not-acceptable`, `upstream`, `no-such-path`, `missing-value`,
  `type.*` 가 더해져 모든 v3 도메인 번들에 나온다
- 자동 구성이 아니다 - 생성된 `XxxApiErrors` 가 가져올 때만 생기므로 같은 런타임의 2.x 애플리케이션은 그대로다. 고친 적이 있는
  `XxxApiErrors` 는 다시 생성되지 않으니 그 한 줄을 붙인다(가이드). Spring Framework 6.1(Boot 3.2) 이상
- 확인: `RequestShapeErrorsTest`(springboot3 은 Jackson 2, springboot4 는 Jackson 3 으로 같은 단언), `Jackson2CodedEnumTest`,
  `elasticore-template-ddd` `ApiContractTest`(Boot 3.3.2, 생성 enum 의 `Unknown PaymentMethod code: XX`, 다른 패키지의 손으로 쓴
  컨트롤러), `elasticore-template-kt` `ShopApiContractTest`, Pet Store `ShopFlowTest`, 그리고 한국어 JVM 으로 띄운 Pet Store 에
  curl: `quantity -1` -> 400 `lines[0].quantity: a quantity cannot be negative: -1`(ko: `수량은 0 이상의 정수입니다`), `DINERS` ->
  `creditCard.creditCardType: Unknown CreditCardType code: DINERS`, `{"lines":` -> `unreadable`, `GET /item/abc` -> `itemId: must be a
  number`, 주문 `PUT` -> 405 `Allow: POST`, `text/plain` -> 415, `Accept: image/png` -> 406, 없는 경로 -> 404 `not-found`, 브라우저 ->
  Whitelabel, 모두 `application/problem+json`
- 관련 파일: `RequestShapeErrors.java`, `RequestErrors.java`, `JsonPaths.java`(두 벌), `ErrorCatalog.java`, `ApiErrorRules.java`,
  `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `ProjectRuleSetSupportTest.java`, `ShopModelTest.java`, `SignInAndReferencesTest.java`,
  `ApiContractTest.java`, `ShopApiContractTest.kt`, `petstore-messages_ko.properties`, `build_guide.py`, `elasticore-rule-guide.md`,
  `elasticore-ddd-customize-guide.md`, `elasticore-env-guide.md`

#### 생성된 `findById`·`delete` 의 빈 404 -> 같은 모양의 404
- `GET /order/{id}` 와 `DELETE /order/{id}` 가 없는 행에 본문 없는 404 를 답해 클라이언트가 `code` 를 읽을 수 없었다
- `v3/control.tmpl`·`kt/control.tmpl` 이 런타임의 새 `NotFoundException("Order not found")` 를 던진다. `EntityNotFoundException`
  이라 생성된 `XxxApiErrors` 가 `not-found` 로 답하고, `@ResponseStatus(404)` 라서 `spring-api-errors` 를 끈 앱도 500 이 아닌 404 다.
  v3 기본 출력 변경(이유: 다른 404 와 같은 본문). 2.x 의 `control.tmpl` 은 그대로
- 확인: `NotFoundExceptionTest`, `ShopModelTest`, `SignInAndReferencesTest`(Kotlin), ddd·Kotlin 샘플의 `findByIdOfNothingIsAProblem`,
  Pet Store `GET /item/99999` -> 404 `{"detail":"Item not found","code":"not-found"}`
- 관련 파일: `NotFoundException.java`, `v3/control.tmpl`, `kt/control.tmpl`

### New Features (런타임 - 손으로 쓴 거절)

#### 손으로 쓴 거절도 메시지 코드로 번역된다
- `ConflictException` 은 메시지 하나만 받아 `<도메인>-messages_ko` 로 번역할 수 없었다. 이제 `BadRequestException`,
  `NotFoundException`, `ConflictException`, `security.ForbiddenException`, `security.NotSignedInException`, `http.PortCallException` 이
  `(code, message, args...)` 를 받는다. 생성된 거절과 같은 순서로 찾고, 없으면 기본 문구(값을 넣은 `MessageFormat`), 응답의 `code` 는
  그 코드다. 예전 생성자는 그대로다(코드 없음 = 예전 응답)
- v3 기본 출력 변경: 생성된 처리기가 `coded(problem(..., messages.text(ex.getCode(), ex.getMessage(), ex.getArgs())), ex.getCode())`
  로 답한다. 이유: 손으로 쓴 규칙도 생성된 규칙처럼 번역되도록
- 확인: `ErrorMessagesTest.aRefusalWithACodeIsTranslatedLikeAGeneratedOne`, Pet Store `ShopFlowTest.aRefusalHandCodeThrowsIsSaidByItsCode`
  (테스트용 컨트롤러가 던진 `loan.no-copy-left`: 헤더 없으면 기본 문구, `ko` 면 `messages_ko` 의 번역)
- 관련 파일: `ConflictException.java`, `BadRequestException.java`, `NotFoundException.java`, `ForbiddenException.java`,
  `NotSignedInException.java`, `Refusals.java`, `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `elasticore-rule-guide.md`

#### `PortCallException` - 부른 시스템이 실패하면 502
- 포트는 상대의 오류 상태를 값으로 돌려주고(런타임 프록시와 `httpPort: spring` 모두), 502 로 답할 예외가 없어 에이전트가 택배사
  호출용 예외와 처리기를 손으로 썼다. 런타임에 `io.elasticore.springboot3.http.PortCallException`(`ResponseStatusException`,
  502)을 더하고 두 `apiErrors` 템플릿에 처리기(502, `code: upstream`)를 더했다. `ResponseStatusException` 이라 이 처리기가 없는 예전
  `XxxApiErrors` 에서도 502 다. 실패 정책 설정(`{id}.failOnError`)은 3.1
- 확인: `PortCallExceptionTest`(처리기 없이 502), `PortFailureContractTest`(오류 본문은 값, 읽을 수 없는 본문·답 없음은 예외, 멀티파트는
  `HttpStatusCodeException`), Pet Store `ShopFlowTest` 의 502 `upstream`
- 관련 파일: `PortCallException.java`, `v3/apiErrors.tmpl`, `kt/apiErrors.tmpl`, `ApiErrorRules.java`

### Bug Fixes (포트 등록기)

#### `@EnableElastiCore` 가 붙은 클래스의 패키지를 스캔한다
- `PortServiceRegistrar` 는 호출 스택에서 `@SpringBootApplication` 을 찾고, 못 찾으면 내부 패키지 이름으로 대신했다. 그래서
  `@SpringBootTest` 에서는 포트를 하나도 등록하지 않았고(테스트마다 손으로 `@Bean`), 공개 저장소에 내부 이름이 있었다
- 이제 `@EnableElastiCore` 가 붙은 클래스의 패키지와(테스트가 띄우는 그 클래스) 스택의 `@SpringBootApplication` 패키지를 함께 보고,
  같은 인터페이스는 한 번만 등록한다(예전에는 겹치면 `BeanDefinitionOverrideException`). 내부 패키지 fallback 은 지웠다. 직접 선언한
  `@Bean` 이 있으면 그 포트는 등록하지 않는다 - 예전 안내대로 테스트에 `@Bean` 을 적어 둔 프로젝트가 같은 타입 빈 둘로 깨지지 않게.
  `beanFactory` 를 static 에 두지 않는다(두 테스트 컨텍스트가 서로의 것을 쓰지 않게). 생성 결과는 바뀌지 않는다
- 확인: `PortServiceRegistrarTest`(가져온 클래스의 패키지, 두 번 가져와도 하나, 직접 선언한 빈이 우선; Spring Boot 처럼 덮어쓰기 금지)
- 관련 파일: `PortServiceRegistrar.java`, `elasticore-dsl-port-guide.md`, `elasticore-rule-guide.md`

### Documentation (포트 가이드 - 실패 처리)

#### 2.8 실패 처리, base URL 이 정해지는 때, 가짜 상대로 테스트
- 2장 머리에 두 방식(런타임 프록시 / `httpPort: spring`)과 `@EnableElastiCore` 가 필요한 쪽. 2.8: 두 방식의 실패 표(오류 본문은 값,
  읽을 수 없는 본문·답 없음은 예외, 멀티파트), 502 + 되돌림 패턴(`PortCallException`), 테스트(`@MockitoBean`, 같은 서버의 가짜는
  `DEFINED_PORT`). 2.7: `httpPort: spring` 의 base URL 은 기동 때 한 번 정해진다(`RANDOM_PORT` 테스트는 포트 0 을 부름 - 조사에서
  실행으로 확인), Boot 4 키의 그룹 이름은 `erp--credit-check-port`(두 줄)
- 5장: `elasticore 1.8.x` 와 포트를 등록하지 않던(`ApplicationContextProvider`·`DbTransactionGateway` 만 등록하던)
  `@ComponentScan("io.elasticore.springboot3")` 대신 `@EnableElastiCore`,
  등록기가 보는 패키지
- 관련 파일: `elasticore-dsl-port-guide.md`

#### 개발 가이드의 예제 값에서 내부 이름을 뺐다
- `Op.ILIKE` 예제의 검색어가 내부 이름이었다. `"acme"` 로 바꿨다(설명도 실제로 맞는 값으로: `acme`, `ACME`, `Acme Supply`)
- 관련 파일: `elasticore-dev-guide.md`

### Bug Fixes (런타임 - 오류 응답과 포트 등록기 리뷰)

#### 한 도메인의 번역이 다른 도메인의 영어에 가려지지 않는다
- 도메인이 여럿인 앱(예: `billing`, `catalog`, `shop`)에서 `shop-messages_ko` 에 둔 `error.unreadable` 번역이 `ko` 요청에도
  영어로 나갔다. 모든 도메인의 기본 파일에 공통 코드(`ErrorCatalog.shared()`)가 들어 있고, Spring 의
  `ResourceBundleMessageSource` 는 코드를 가진 첫 번들을 쓰며, `_ko` 가 없는 번들은 자기 기본 파일로 답하기 때문이다
  (이름순으로 앞선 `billing-messages.properties`)
- `ErrorMessages` 가 두 번 찾는다: 먼저 모든 번들의 그 언어 파일(`_ko_KR`, `_ko`)에서, 없으면 예전처럼 기본 파일까지.
  `messages_ko` 가 도메인의 `_ko` 보다 앞서는 것은 그대로다. 단일 도메인 앱에서 달라지는 것은 `messages.properties`(기본 파일)에
  있는 코드를 도메인의 `_ko` 가 번역한 경우뿐이다 - 이제 번역이 나간다
- 확인: `RequestShapeErrorsTest.aTranslationInOneDomainIsNotHiddenByAnotherDomainsEnglish`,
  `ErrorMessagesTest.aTranslationInTheSecondBundleWinsOverTheFirstBundlesEnglish`(테스트 번들 `billing-messages.properties`,
  번역 없음) - 고치기 전에는 둘 다 영어로 실패
- 관련 파일: `ErrorMessages.java`, `elasticore-rule-guide.md`

#### 2.x 앱의 컴포넌트 스캔이 API 오류 처리기를 가져오지 않는다
- 처리기가 `@RestControllerAdvice` 라서 예전 포트 가이드대로 `@ComponentScan("io.elasticore.springboot3")` 를 둔 2.x 앱이
  런타임만 올려도 그것을 가져왔다 - 깨진 JSON, 404, 405, 406, 415 와 앱 컨트롤러의 `MethodArgumentNotValidException` 이 모두
  `problem+json` 으로 바뀐다
- 처리기는 `RequestShapeAdvice` 로 옮기고 결코 맞지 않는 `@Conditional` 을 붙였다(스캔도, 그 클래스의 `@Import` 도 지나친다).
  `RequestShapeErrors` 는 그 정의를 직접 등록하는 `ImportBeanDefinitionRegistrar` 가 되었다 - 생성된 `@Import(RequestShapeErrors.class)`
  는 그대로라 생성 결과는 바뀌지 않는다. 여러 도메인이 가져와도 처리기는 하나다. 처리기의 빈 이름은
  `io.elasticore.springboot3.validation.RequestShapeAdvice` 로 바뀌었다(전에는 `...RequestShapeErrors`)
- 확인: `RequestShapeErrorsRegistrationTest`(런타임 패키지 스캔에 `RequestShape*` 없음, 스캔한 컨텍스트에 `@ControllerAdvice` 없음 -
  고치기 전에는 둘 다 실패; 두 도메인이 가져온 MVC 컨텍스트에 처리기 하나, `unreadable` 로 답함)
- 관련 파일: `RequestShapeErrors.java`, `RequestShapeAdvice.java`, `elasticore-dsl-port-guide.md`, `elasticore-rule-guide.md`

#### 포트 프록시를 감싼 `@Primary` 빈이 다시 동작한다
- 직접 선언한 빈이 있으면 포트 프록시를 지웠으므로 `@Bean @Primary CourierPort retrying(@Qualifier("CourierPort") CourierPort raw)`
  처럼 프록시를 감싸는 앱이 `NoSuchBeanDefinitionException` 으로 뜨지 않았다(공유 런타임의 2.x 앱 포함)
- 이제 다른 빈 중 `@Primary` 가 없을 때만(타입 주입이 모호할 때만) 프록시를 지운다. 또 스캔한 패키지에서 포트 인터페이스를
  구현한 클래스(가짜, 래퍼)도 Reflections 가 돌려줘 프록시로 등록하려다 `IllegalArgumentException` 으로 기동이 실패했다(예전
  등록기도 같았다) - 이제 `@ExternalService`/`@DbmsService` 가 직접 붙은 인터페이스만 등록한다. 붙은 인터페이스를 상속하기만
  한 인터페이스도 이제 등록하지 않는다(예전에는 어노테이션이 null 인 프록시가 되어 첫 호출에서 실패했다)
- `@Primary` 인 가짜(포트를 통째로 바꾸는 테스트 빈)도 이제 프록시를 남기므로 `List<CourierPort>` 는 둘을 받는다(가이드)
- 확인: `PortServiceRegistrarTest.aPrimaryBeanThatWrapsTheProxyKeepsIt`(고치기 전 `NoSuchBeanDefinitionException`),
  `aClassImplementingAPortIsNotMadeAProxy`(고치기 전에는 이 패키지의 모든 등록기 테스트가 `IllegalArgumentException`)
- 관련 파일: `PortServiceRegistrar.java`, `elasticore-dsl-port-guide.md`

#### Boot 4 에서 웹 계층을 Jackson 2 로 되돌린 앱의 필드 오류
- springboot4 의 `JsonPaths` 는 Jackson 3 예외만 알아, `MappingJackson2HttpMessageConverter` 를 쓰는 Boot 4 앱에서 타입이 틀린 값이
  필드 오류가 아니라 `unreadable` 로 나갔다. Jackson 2 가 클래스 경로에 있으면 그 예외도 springboot3 처럼 읽는다
- 확인: springboot4 의 `Jackson2WebLayerOnBoot4Test`(`count: must be a number`, `lines[0].amount` 의 생성자 거절, 깨진 JSON 은
  `unreadable`; 고치기 전에는 첫 테스트가 실패)
- 관련 파일: springboot4 `src/jackson3/java/.../JsonPaths.java`, `src/jackson2test/java/.../Jackson2WebLayerOnBoot4Test.java`, `build.gradle`,
  `elasticore-env-guide.md`

#### 테스트: 한국어 JVM, 공유 테스트 자원
- `elasticore-template-ddd`, `elasticore-template-kt` 의 테스트도 Pet Store 처럼 `-Duser.language=ko -Duser.country=KR` 로 돈다.
  새 테스트(`errorsAreNotSaidInTheMachinesLanguage`, `aFieldErrorIsNotSaidInTheMachinesLanguage`)가 헤더 없음·`en`·`sv` 의 400
  본문에 한글이 없음을 확인한다
- 런타임 테스트의 `ValidationMessages*.properties`(앱이 덮어쓴 `NotBlank` 문구)는 `src/koreanServerTest/resources` 로 옮겨
  `koreanServerTest` 에만 둔다 - 다른 모든 테스트의 기본 문구를 바꾸지 않게
- 관련 파일: 두 샘플과 두 런타임의 `build.gradle`, `ApiContractTest.java`, `ShopApiContractTest.kt`

#### 남은 경우 (리뷰에서 코드를 읽어 찾음, 실행으로 재현하지 않음)
- `messages_en` 은 있고 `ValidationMessages` 는 `_ko` 파일만 있는(기본 파일 없는) 앱: `en` 요청은 `en` 으로 정해지고(`Locale.ROOT`
  가 아님), 기본 제약 메시지를 다시 말할 때 Hibernate Validator 가 앱의 `ValidationMessages` 기본 파일을 찾지 못해 JDK 의 bundle
  fallback 으로 머신 언어 파일을 읽을 수 있다. 피하려면 `ValidationMessages.properties` 기본 파일도 둔다(가이드는 이 파일들로
  언어를 맞추지 말라고 한다)
- `spring.web.locale` 로 `Locale.ROOT` 가 아닌 기본 언어를 둔 앱: 코드가 런타임의 번들에 없어 애플리케이션 `MessageSource` 로 가면,
  Spring Boot 의 `spring.messages.fallback-to-system-locale`(기본 `true`) 때문에 그 언어 파일이 없는 번들은 머신 언어로 답할 수
  있다. 피하려면 `spring.messages.fallback-to-system-locale=false`
- 파라미터 제약 처리기(`HandlerMethodValidationException`)를 Spring Framework 6.1 에서 부르는 테스트는 없다.
  `RequestShapeErrorsTest` 는 springboot3(Spring 6.2)과 springboot4(Spring 7)에서 돌고, Spring 6.1 인 Boot 3.3.2 샘플의 계약
  테스트는 그 처리기에 닿지 않는다

## 3.0.261020-alpha2

### Bug Fixes (생성기)

#### 도메인에 `Comment` 엔티티가 있어도 컴파일된다
- 같은 도메인에 `Comment` 라는 엔티티나 enum 이 있으면 생성된 코드가 컴파일되지 않았다. `-- 설명` 이 붙은 필드마다
  Hibernate 의 `@Comment` 가 붙는데
  - 규칙 엔진(`elcore3`)은 `import org.hibernate.annotations.Comment;` 를 써서 같은 패키지의 `Comment` 엔티티를
    가렸다. `PostBase` 의 `List<Comment>` 가 애노테이션을 가리켜 컴파일 오류가 났다
  - 2.x 생성기(`elcore`)는 `org.hibernate.annotations.*` 를 import 하는데, 같은 패키지의 `Comment` 엔티티가 이름을
    가져가 `@Comment("...")` 가 엔티티를 가리켰다
- 이제 도메인에 그런 엔티티나 enum 이 있으면 `@Comment` 를 import 하지 않고 `@org.hibernate.annotations.Comment("...")`
  처럼 패키지까지 붙여 쓴다. 규칙 엔진의 v3 번들(`jpa-springboot-v3`·DDD·헥사고날)과 Kotlin 번들은 규칙이 만들어
  쓰는 다른 애노테이션도 도메인의 타입과 이름이 겹치면 같게 한다. 2.x 와 같은 출력을 내는 `jpa-springboot` 번들은 2.x
  생성기처럼 `@Comment` 만 바꾼다. 규칙이 정해진 한 줄로 쓰는 애노테이션(DTO 의 `@Size`·`@Schema`,
  `@Setter(AccessLevel.PROTECTED)` 등), 같은 파일에서 그런 한 줄로도 쓰이는 애노테이션, 다른 애노테이션 안이나
  `@jpa:` 로 쓰는 애노테이션(`@Table` 의 `@Index` 등)은 단순 이름과 import 를 그대로 둔다. 그런 이름이 없는 도메인의
  생성 결과는 바뀌지 않는다(`elcoreRulesCheck`·`elcoreTreeCheck` 결과 그대로)
- 아직 남은 경우: 다른 도메인의 `Comment` 엔티티를 참조하는 엔티티(그 파일이 그 엔티티를 import 한다), DTO 에서
  `@Size` 와 이름이 같은 타입(`Size` enum 등)을 필드 타입으로 쓰는 경우, MongoDB 출력은 이전처럼 컴파일되지 않는다
- 확인: 2.x 생성기, 규칙 엔진 layered·hexagonal·Kotlin 으로 `Post`·`Comment` 게시판을 생성해 단순 이름 `@Comment` 와
  그 import 가 없는 것을, `Comment` 가 없는 같은 모델은 단순 이름 `@Comment` 와 그 import 를 그대로 쓰는 것을, `Size`
  enum 과 `Schema` 엔티티가 있는 도메인의 DTO 가 `@Size`·`@Schema` 의 import 를, `Setter`·`JoinColumn` 엔티티가 있는
  도메인의 파일이 `@Setter`·`@JoinColumn` 의 import 를 그대로 두는 것을 테스트로 확인했다. hexagonal 게시판은
  Spring Boot 4.0.8 에서 컴파일하고 실행했다
- 관련 파일: `SourceCode.java`, `AnnoCode.java`, `EntitySpec.java`, `EntitySrcFilePublisher.java`,
  `ModelNamedLikeAnAnnotationTest.java`

### Documentation (README)

#### 시작하기를 실제 작업 순서로, build.gradle 은 그대로 동작하게
- 실제 작업 순서로 고쳤다: ③ `./gradlew elcore3` 로 생성 -> ④ 내 작업(애플리케이션 클래스, `ProductService` 의 업무
  로직, 로그인) -> ⑤ `./gradlew bootRun`. 이전에는 `./gradlew build` 한 단계로 적어, 생성된 코드를 보고 그 위에 작업하는
  순서가 드러나지 않았다. `build`·`bootRun` 이 컴파일 전에 `elcore3` 를 먼저 돌리는 것은 그대로다
- ① 의 `build.gradle` 이 그대로 복사해 동작한다. 이전 예시는 `java` 플러그인이 없어 `implementation()` 을 찾지 못하고
  실패했고, 생성된 코드가 쓰는 의존성(Spring Boot 스타터, springdoc, Lombok, DB 드라이버)과 그 버전을 정하는
  `io.spring.dependency-management` 가 빠져 있었다
- Spring Initializr 로 시작했다면 그 `build.gradle` 을 두고 빠진 줄만 더한다고 적었다. 통째로 바꾸면 함께 생성된
  테스트가 쓰는 테스트 의존성이 빠져 `compileTestJava` 에서 실패한다. 애플리케이션 클래스는 Initializr 가 group·artifact
  로 정한 패키지에 만든다는 것도 적었다 - 생성된 패키지가 그 아래여야 Spring 이 찾는다. Initializr 의 기본값인
  Spring Boot 4.1.1 에서도 생성·빌드(함께 생성된 테스트 포함)·실행을 확인했다
- Spring Boot 3 에서 바꿀 것(`elasticore-springboot3`, `spring-boot-starter-web`, springdoc 2.8.17)과, Spring Boot 4 에는
  Gradle 8.14 이상이 필요하다는 것을 적었다
- 관련 파일: `README.md`, `README.ko.md`, `ReadmeTest.java`

#### 소개를 오래 운영할 업무 시스템에 맞추고, 근거와 현재 상태를 적었다
- 첫 문장과 소개를 오래 운영할 업무 시스템에 맞췄다. 업무 규칙(누가 무엇을 하는지, 주문이 어떻게 진행되는지, 값이
  어떻게 정해지는지)은 개발자나 AI 도구가 고치고 리뷰어가 읽는 한 곳에 남고, 빌드가 그 규칙을 서버의 모든 계층에,
  `./gradlew elcore3ts` 가 TypeScript 클라이언트에 반영한다
- 소개 아래에 어떤 시스템을 위한 것인지(주문·정산·계약·백오피스처럼 여러 사람이 몇 년씩 고쳐 가는 업무 시스템)와
  맞지 않는 곳(화면이 대부분인 애플리케이션, 영속이 관계형이나 JPA 가 아닌 시스템, 선언형 모델로 나타내기 어려운
  로직)을 적고, 근거와 현재 상태로 가는 링크를 두었다
- "AI 코딩 시대에" 는 시제품과 오래 운영할 시스템을 나눠서 시작한다
- "믿을 수 있는 출력" 에 근거를 적었다: 풀 리퀘스트마다 CI 가 하는 검사(모든 모듈 빌드, 2.x 출력과의 산출물별 비교와
  기준 프로젝트 229개 파일 비교, 허용한 차이와 비교에서 빼는 것까지), 변경 증폭 벤치마크의 숫자(검색 필드 DSL 1줄 ->
  9개 파일 39줄 변경, 두 번 생성해 130개 파일 중 0개 다름 - 2026-10-16 에 다시 재어 같은 숫자를 확인), Maven Central
  서명 배포, 생성된 코드도 보안 신고 대상이라는 것(SECURITY.md)
- "현재 상태" 를 새로 두었다. 3.0 은 정식 출시 전이고, `elasticore` 블록에 아무것도 선언하지 않은 프로젝트는 2.x
  생성기로 생성하며 규칙 엔진의 `jpa-springboot` 번들은 풀 리퀘스트마다 그 출력과 비교한다. v3 번들
  (`jpa-springboot-v3`·DDD·헥사고날·Kotlin·TypeScript)의 출력은 정식 출시 전까지 바뀔 수 있고, 바뀔 때마다 이유를
  이력에 적는다. 지원 버전은 SECURITY.md, 브랜치와 릴리스는 BRANCHING.md·RELEASING.md 로 안내한다
- 관련 파일: `README.md`, `README.ko.md`

### Documentation (지원 기간)

#### 3.0 은 첫 정식 릴리스부터 3년간 지원한다
- 3.0 은 첫 정식 릴리스(`-alpha`·`-beta`·`-rc` 가 붙지 않은 첫 버전)부터 3년간 버그·보안 수정을 받는다. 대상은
  `elasticore-base`, Gradle 플러그인(`io.elasticore`), `elasticore-springboot3`·`elasticore-springboot4` 이고, 수정은
  `release/3.0` 에서 `3.0.YYMMDD` 버전으로 낸다. 끝나는 날짜는 그 릴리스가 나올 때 BRANCHING.md 에 적는다. 첫 정식
  릴리스 전까지는 `main` 에서 다음 사전 릴리스(pre-release)로 고친다
- `release/3.0` 은 첫 정식 릴리스에서 만든다고 고쳐 적었다. 이전에는 "첫 3.0 릴리스" 라고만 적어, 정식 출시 전
  버전(3.0.261015-alpha1)에서 만들어야 하는 것처럼 읽혔다. 3.0 을 고치는 브랜치도 `release/3.0` 이 생기기 전까지는
  `main` 에서 딴다
- SECURITY.md 의 지원 버전 표는 행이 겹치지 않게 "3.x 최신 버전(`main`)" 과 "첫 정식 릴리스부터의 3.0(`release/3.0`)"
  으로 나눴다. README 의 "현재 상태" 가 같은 내용을 말한다
- 관련 파일: `README.md`, `README.ko.md`, `SECURITY.md`, `BRANCHING.md`

### Documentation (호환 검사 설명)

#### 2.x 와의 호환 검사를 "바이트 단위" 가 아니라 실제로 하는 대로 적었다
- README, BRANCHING.md, CONTRIBUTING.md, 비교 문서가 2.x 출력과 "바이트 단위로" 같다고 적었지만 검사는 그렇게 하지
  않는다. `elcoreRulesCheck` 는 7가지 산출물을 메모리에서 렌더링해 비교하고 엔티티는 필드 부분만 본다
  (`150 MATCH / 0 DIFF / 2 ACCEPTED`). `elcoreTreeCheck` 는 기준 프로젝트에서 2.x 가 만드는 229개 파일을 모두 비교하되,
  2.x 버그 수정으로 달라지는 4개는 이름을 정해 허용하고 1개는 import 순서만 다르다. 두 검사 모두 각 파일의 첫 줄(내용
  해시·시각), 한 번만 생성되는 파일 표시 주석의 표기, 줄바꿈 문자, 파일 끝의 빈 줄은 비교하지 않고, 트리 비교는 줄 끝
  공백도 무시한다
- 그래서 "byte for byte"·"바이트 단위" 를 빼고, 무엇을 비교하는지와 허용한 차이를 그대로 적었다. 사용자 프로젝트의
  `blueprint/` 로 복사되는 규칙 가이드도 `jpa-springboot` 의 출력을 "바이트 단위로 동일" 하다고 적고 있어 같이 고쳤다
- 관련 파일: `README.md`, `README.ko.md`, `BRANCHING.md`, `CONTRIBUTING.md`, `docs/comparison.md`, `docs/comparison.ko.md`,
  `elasticore-rule-guide.md`, `elasticore-template-ddd/README.md`

### Improvements (호환 검사)

#### CI 가 2.x 기준 트리가 커밋된 그대로인지도 확인한다
- 두 호환 검사(`elcoreRulesCheck`, `elcoreTreeCheck`)는 규칙 엔진을 같은 빌드의 2.x 생성기와 비교한다. 둘이 함께 쓰는
  코드(모델 로더 등)를 바꿔 두 생성기가 똑같이 달라지면 두 검사는 통과하고, 2.x 생성기를 쓰는 프로젝트의 코드는
  바뀐다
- CI 와 릴리스 워크플로가 빌드 직후, 빌드가 다시 생성한 기준 프로젝트(`elasticore-project/elasticore-template/src/main/java`)
  가 커밋된 그대로인지 `git status` 로 확인한다. 바뀐 파일, 없어진 파일, 새로 생긴 파일이 하나라도 있으면 그 목록을 실행
  요약에 남기고 실패한다
- 로컬에서 확인했다: 템플릿을 빌드해도 트리는 그대로이고, 파일 하나를 바꾸거나 새 파일 하나를 두면 이 단계가 실패한다.
  기준 트리 248개 파일은 저장소에 LF 로 들어 있고 2.x 생성기는 OS 의 줄바꿈으로 쓰므로 Linux CI 에서도 같다
- 2.x 출력을 일부러 바꾸는 변경(버그 수정)은 다시 생성된 파일을 함께 커밋하고 이유를 이력에 적는다고 CONTRIBUTING.md 에
  적었다. 풀 리퀘스트에 모든 2.x 프로젝트가 받을 차이가 그대로 보인다
- CONTRIBUTING.md, BRANCHING.md, RELEASING.md 의 검사 목록과 README 의 "믿을 수 있는 출력"·"현재 상태" 가 이 검사를
  말한다
- 관련 파일: `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `CONTRIBUTING.md`, `BRANCHING.md`, `RELEASING.md`,
  `README.md`, `README.ko.md`

### Documentation (지원 범위와 릴리스)

#### 3.0 수정이 맞추는 Spring Boot 버전, 풀 리퀘스트로 하는 릴리스, 서명 키 만료
- 3.0 의 수정은 그 시점에 Spring 팀이 오픈소스로 지원하는 Spring Boot 4 버전에서 빌드하고 테스트하고, Spring Boot 3
  런타임 라이브러리(`elasticore-springboot3`)는 마지막 오픈소스 Spring Boot 3 릴리스인 3.5.16 에서 한다. 3.5 의
  오픈소스 지원은 2026-06-30 에 끝났고(Maven Central 의 마지막 3.x 는 3.5.16), Spring Boot 3 에 머무는 팀이 3년 동안
  테스트된 수정을 받게 하려는 것이다
- `main`·`release/**` 은 CI 가 통과한 풀 리퀘스트로만 바뀐다. 그래서 RELEASING.md 의 버전 커밋과 다음 `-SNAPSHOT`
  커밋도 `version/*` 브랜치의 풀 리퀘스트로 넣고, 태그는 그 머지가 `main` 에 만든 커밋에 단다 - squash 머지라도
  맞는 커밋에 달리도록 머지된 커밋을 직접 지정한다
- 릴리스 서명 키는 2029-10-14 에 만료된다. 3.0 지원이 그 뒤까지 가므로, 그 전에 기한을 늘리고 공개 키를 키 서버에
  다시 올리고 다시 내보낸 비밀 키를 `SIGNING_KEY` 에 넣는다고 적었다
- 관련 파일: `README.md`, `README.ko.md`, `BRANCHING.md`, `RELEASING.md`

### Improvements (README 검사)

#### README 의 시작하기를 테스트가 실제로 생성해 본다
- 지금까지 README 는 링크·버전·번들 이름만 검사했고(`ReadmeTest`), 그 안의 DSL 은 아무도 돌리지 않았다. 더는 읽히지
  않는 블록, 처음 실행에서 만나는 경고, README 가 약속하고 생성기는 더 쓰지 않는 파일이 모두 빌드를 통과했다
- `ReadmeQuickStartTest` 가 README.md·README.ko.md 의 시작하기에서 build.gradle 의 `architecture`·`language` 와 Spring
  Boot 버전, env.yml, model/entity.yml 을 꺼내 ② 가 말하는 대로 새 프로젝트에 넣고, 플러그인이 하듯 생성한다. DSL
  경고나 경고 줄이 하나라도 있거나 ③ 에 적은 파일이 하나라도 없으면 실패한다. ④ 가 부르라는 로그인 메서드
  (`SessionCurrentUser.signIn`)가 런타임 라이브러리에 있는지도 본다
- README 를 일부러 세 가지로 틀리게 해 세 검사가 모두 이유를 말하며 실패하는 것을 확인했다: 어디에도 없는 타입의
  필드(DSL 경고 3개), 생성되지 않는 파일, 이름을 바꾼 로그인 메서드
- 규칙 엔진의 `main` 을 `run()` 과 나눴다. `run()` 은 0 이나 1 을 돌려주고 `main` 만 `System.exit` 를 부르므로
  플러그인이 보는 동작은 그대로다. 테스트는 `run()` 을 불러, 생성기가 모델을 거절해도 테스트 JVM 이 끝나지 않고 생성기의
  설명을 담은 실패가 된다
- 관련 파일: `ReadmeQuickStartTest.java`, `RulesGenRunner.java`, `BaseFieldType.java`

## 3.0.261015-alpha1

### New Features (첫 공개)

#### ElastiCORE 3.0
- 이 저장소에서 처음 공개하는 버전이자 3.0 의 첫 알파. 이 저장소의 변경 이력은 이 버전에서 시작한다
- 알파: 정식 3.0 전까지 v3 번들(`jpa-springboot-v3`, `ddd-*`, `hexagonal-*`, `*-kt`, `ts-model`)의 생성 결과는 바뀔 수
  있다 - 바뀌면 그 이유를 여기에 적는다. 선언하지 않은 프로젝트의 생성 결과(2.x 와 같은 `jpa-springboot`)는 알파에서도
  바뀌지 않는다
- Maven Central 에서 받는다: `io.elasticore:elasticore-base`, `io.elasticore.springboot3:elasticore-springboot3`,
  `io.elasticore.springboot4:elasticore-springboot4`, Gradle 플러그인 `id 'io.elasticore'`. 플러그인도 Maven Central 에
  있으므로 `settings.gradle` 의 `pluginManagement` 에 `mavenCentral()` 을 넣는다 (`README.md` 의 설치)
- 기능과 사용법: `README.md`, 그리고 가이드(`elasticore-guide/` - 생성할 때 프로젝트의 `blueprint/` 에 복사됨)

### Bug Fixes (2.x 와 생성 결과가 다른 곳)

#### 2.x 의 결함을 고쳐 생성 결과가 달라지는 곳
생성 코드 밖의 차이(`schema.yml` 의 위치·인코딩, 모듈 배치 등)는 `elasticore-migration-guide.md` 에 있다.
- `string(n)` 과 `@length(min=..)` 를 함께 쓰면 `@Size` 에 최소 길이도 들어간다. 2.x 는 최소 길이를 빠뜨렸다 -
  더 짧은 값은 이제 400
- `@formula` (2.2.261012 에도 들어간 수정 - 그 이전 2.x 와 다름):
  - 식에 `=` 가 있어도 값이 깨지지 않는다. 이전에는 컴파일되지 않는 `@Formula` 를 생성했다
  - Mapper 는 `@formula` 필드를 DTO -> Entity 방향으로 옮기지 않는다 (DB 가 조회할 때 계산하는 값)
  - Entity 의 `@formula` 필드에는 `@Formula` 만 붙고 `@Column`·`@Comment` 는 붙지 않는다. 이전에는 길이·`!`·`@unique`·
    `@updatable(false)`·`@text` 가 있으면 `@Column` 도 붙어 실제 컬럼이 생겼다 - 그 컬럼은 DB 에서 직접 지운다
    (`ddl-auto: update` 는 지우지 않음). `schema.yml` 에는 `@Formula` 로 표시 (`elasticore-annotation-guide.md` 의 `@formula`)
- 검색 DTO: 같은 대상을 가리키는 `@ManyToOne` 관계가 둘이면 둘 다 검색 조건으로 펼친다 (2.x 는 첫 번째만).
  `elcoreRulesCheck` 가 ACCEPTED 로 보고하는 2건이 이것이다
- Mapper: 변환이 필요한 `@forceupdate` 필드의 메소드 호출을 닫는다 (2.x 는 컴파일되지 않는 코드를 생성했다)
- 생성 서비스 테스트: `LocalDateTime`·`BigDecimal` 처럼 이름을 패키지까지 쓰는 타입의 필드에 `null` 대신
  그 타입의 값을 넣는다

### Bug Fixes (운영체제와 무관한 순서)

#### Linux·macOS 에서 도메인과 모델 파일을 읽는 순서가 Windows 와 달랐던 문제
- 폴더 목록을 파일 시스템이 주는 순서대로 읽어서, Windows(이름 순)와 Linux·macOS(파일 시스템마다 다름)의 읽는 순서가
  달랐다. 그 순서를 따르는 모델, DSL 경고의 순서, 여러 엔티티를 모아 만드는 생성물이 운영체제에 따라 달라질 수 있었다
- 이제 어디서나 Windows 와 같은 이름 순(대소문자 구분 없음)으로 읽는다. Windows 에서 만든 결과는 그대로
- 관련 파일: `FileOrder.java`(신규), `ModelExtractor.java`, `FileBasedModelLoader.java`, `YamlFileMainModelLoader.java`,
  `JavaSrcFileMainModelLoader.java`, `BlueprintLoader.java`, `JPACodePublisher.java`, `RulesGenRunner.java`, `FileOrderTest.java`

### Bug Fixes (보안 - 런타임 라이브러리가 2.x 와 다르게 동작하는 곳)

#### `elasticore-springboot3`·`elasticore-springboot4`
- `ignoreSSL` 을 켠 포트의 multipart 호출이 인증서·호스트 이름 검사를 JVM 전체에서 껐다. `HttpsURLConnection` 의
  기본값을 바꿔서, 그 뒤로는 다른 포트와 다른 라이브러리의 HTTPS 연결도 아무 인증서나 받아들였다. 이제 그 호출의
  연결에만 적용된다. 2.x 에서 이 부작용 덕에 통하던 HTTPS 연결이 있었다면 이제 인증서 오류가 난다 - 그 서버의
  인증서를 JVM 신뢰 저장소에 넣거나, 그 포트에도 `ignoreSSL` 을 켠다
- multipart 호출이 요청 헤더를 Authorization 까지 INFO 로그에 남겼다. 이제 DEBUG 로 남기고, 인증 정보를 담을 수 있는
  헤더(이름에 auth·cookie·session·password·token·secret·signature·key 가 든 헤더 - `Authorization`, `api_key`,
  `Ocp-Apim-Subscription-Key` 등)의 값과 URL 의 쿼리 문자열은 `****` 로 가린다
- `string(n) @id @genid` 키의 값(`IdUtils.newId`)을 `SecureRandom` 으로 만든다. 이전에는 호출마다 시각으로 시드한
  `java.util.Random` - 예측할 수 있는 난수 - 이었다. 형식(접두어 3자 + 영숫자)은 그대로
- 관련 파일: `HttpApiClient.java`, `IdUtils.java`, `MultipartTlsTest.java`(신규)

### Improvements (공개 라이브러리가 선언하는 의존성 버전)

#### POM 에 고정돼 있던 오래된 버전
- `elasticore-springboot3` 은 Spring Boot 3.5.16 의 버전 관리를 따른다 (`elasticore-springboot4` 가 4.0.8 을 따르듯).
  POM 에 고정돼 있던 Spring Framework 6.1.2, Netty 4.1.94, Jackson 2.13·2.15, Reactor 3.5.6, commons-lang3 3.12 를
  뺐다. Maven 과, Gradle 의 `io.spring.dependency-management` 를 쓰는 애플리케이션에서는 애플리케이션의 Spring Boot
  버전이 그대로 쓰인다. Gradle 의 `platform()` 으로 버전을 맞추는 애플리케이션에서는 높은 쪽이 이기므로 3.5.16 이
  하한이 된다
- `elasticore-base`: Jackson 2.13.0 -> 2.22.3 (SnakeYAML 1.x -> 2.x). Java 8 에서 그대로 돌고, 생성 결과도 같다
  (`elcoreRulesCheck` 150 MATCH / 0 DIFF, `elcoreTreeCheck` 0 differ). `platform()` 으로 버전을 맞추는 Spring Boot 4
  애플리케이션에서는 Jackson 2 가 Boot 의 2.21 대신 2.22.3 이 된다
- 관련 파일: `elasticore-springboot3/build.gradle`, `elasticore-base/build.gradle`

### Documentation (설치)

#### Maven Central 에서 받는 설치 안내
- `README.md`·`README.ko.md` 와 가이드(`elasticore-rule-guide.md`, `elasticore-rule-dev-guide.md`,
  `elasticore-ddd-customize-guide.md`, `elasticore-migration-guide.md`)가 로컬 설치 대신 Maven Central 과 이 버전을
  안내한다. README 가 안내하는 버전은 `gradle.properties` 의 `elasticoreReleasedVersion` - 마지막 릴리스 - 이고,
  `ReadmeTest` 가 둘을 맞춰 본다. 다음 릴리스를 개발하는 동안에도 README 는 받을 수 있는 버전을 가리킨다
- `elasticore-annotation-guide.md`: 공개된 적 없는 버전 번호(3.0.261005)를 "2.x"·"3.0" 으로 바꿨다
- 릴리스 절차: `RELEASING.md`
