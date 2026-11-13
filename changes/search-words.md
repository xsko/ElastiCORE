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
