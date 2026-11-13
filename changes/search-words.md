### New Features (search - `@search(words:...)`)

#### One search box over several fields: `keyword: string(50) @search(words:title, body, author.nickname)`
- Why (issue 182): a search box that looks in the title, the body and the author's name had to be a hand-written
  `Specification`: split the words, an OR over the fields per word with a LEFT JOIN for the author, an AND over the
  words, the like escaped.
- What: a search DTO field with `@search(words:a, b, c.d)`. Its text is split on white space; every word has to be
  found, each in any of the fields - a contains ignoring case, `%` and `_` as characters (as `@search(like)`). The
  first five words are searched. A relation path is joined LEFT, each once; a collection on the way gives each row
  once. The 3.0 JPA mapper (Java, Kotlin) calls the runtime's new `io.elasticore.springboot3.mapper.Words.match`.
  A path that is not a text field of the entity is said by a DSL warning and left out.
- Not in: MyBatis (`mode: mybatis`) and the 2.x-compatible output say so in a warning and do not search by it.
- Form: no space after `words:` - YAML reads `: ` as a key (the form the issue sketched does not load).
- Unchanged: a model without the form generates the same.
- Checked: `WordsTest` (runtime, H2 + Hibernate: any field, every word, escaping, LEFT JOIN, two relations, a
  collection once, five words). `SearchWordsTest` (generator: the Java and Kotlin mapper lines, the DTO's
  `@Size(max=50)` and description, the three kinds of wrong path, the MyBatis warning). The pet store's
  `ProductSrchDTO.keyword` is generated, compiled and searched over HTTP by `WordsSearchCheck` - on H2 in
  `SchemaValidateTest`, and on PostgreSQL, Oracle and SQL Server in the databases job.
- Files: `Words.java` (springboot3, shared with springboot4), `MapperSpecRules.java`, `SearchDtoBaseRules.java`,
  `MyBatisSearchRules.java`, `SpecFactory.java`, the tests above, pet store `dto.yml`, `SchemaValidateTest.java`,
  `SchemaValidateOnDatabase.java`, `elasticore-annotation-guide.md` and `elasticore-dsl-guide.md` (en, ko).
