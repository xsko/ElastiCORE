### Features (v3 controller - `GET /schema/info` from the model)

#### `config: schemaInfo: model` - `/schema/info` says what the entity's `POST .../search` takes
- Why (issue 373, third step): a screen that builds its condition bar from `/schema/info` had to restate each search
  property, its operator and the page and sort the search accepts; the answer from the model listed only the DTO's fields.
- Now, under the same setting (no new one), each entity whose controller serves `POST /search` also has `search` in its
  answer, after `fields`: `{pageSize, sortable, fields: {<property>: {field, op, end?, in?}}}`. Without a search, no `search`.
  - The properties are exactly the search DTO's, as the search DTO names them (`xFrom`/`xTo`, an alias, `xEmpty`, `xItem`,
    `words`): `SearchDtoBaseRules.properties` recorded when the search DTO is written, those still declared. `field` is the
    entity field the search compares (the `@ref` path, the alias, else the field; the list of fields for `words`), `end`
    is `from`/`to` of a range, `in: true` marks a list of values.
  - `op` is the DSL's word for what the generated search applies: `eq`, `ne`, `gt`, `gte`, `lt`, `lte`, `like` (contains
    ignoring case, the 3.0 `like` on text), `%_%`, `__=`, `_%`, `__%`, `%_`, `%__`, `in` (a list only; one value is `eq`),
    `between`, `isnull`, `auto`, `words`; an unknown condition `eq` and a lone `%` `%_`, as JPA's mapper writes them;
    `null` for a property that searches by nothing.
  - MyBatis and MongoDB are read from what their query applies (`MyBatisMapperRules.searched`,
    `MongoRepositoryRules.searched`, recorded when the mapper or repository is written): `@search(words: ...)`, one element
    of a list, a `@formula` and (MyBatis) a large value searched by anything but text have no condition there, so `op` is
    `null` (the build says so already); MongoDB's text match on a number, an enum or a converted value is `eq`; the field
    is the entity path of the query's condition (a blind index: the field it indexes). The schema file is now written
    after the domain's mappers and repositories.
  - `pageSize` is the search DTO's page size where the controller answers a page; `sortable` is what the search DTO's sort
    may name (`SearchSortList.listed`), under MyBatis and MongoDB only what the query also has a column for; `null` under
    JPA where nothing checks the sort (a project's own `interface.sortable`, a search without a response DTO).
  - The runtime `SchemaInfo` passes the file's `search` through (elasticore-springboot3, shared by -springboot4).
- Before and after: without the setting nothing changes; with it, the schema file gains `search`. Every sample generates
  the same files as before except the pet store's `petstore-schema.json`.
- Checked: the pet store's `SchemaInfoTest` over HTTP (Boot 4): Item's `search` (page size 50, the sortable list, every
  property and its operator), then `POST /api/petstore/item/search {"name":"PUPPY"}` with the property it names finds only
  the six items whose name holds "puppy" (of 32), sorted by `sortCode` `name+`; Product's `keyword` says `words` over
  `name`, `description`, `category.name`, and `"fish japan"` finds only Koi. On the MyBatis pet store (the setting and a
  words search DTO turned on temporarily) the answer says `op: null` for `keyword`, and a search by it found all 16
  products, while `unitCostFrom: 100` found only items of 100 or more. Generated and compiled with the setting,
  temporarily: `-ddd` (hexagonal JPA), `-kt` (Kotlin JPA), `-mongo`, `-ddd-mybatis`, `-kt-mongo`. In-process tests:
  `SchemaSearchTest` (every operator, every property kind, a query's conditions, a blind index, page and sort),
  `SchemaCatalogTest`, `SchemaInfoGenerationTest` (JPA, MyBatis, MongoDB; Java, Kotlin; layered, ddd, hexagonal; MongoDB's
  text match; a controller without search); runtime `SchemaInfoTest`. Without the setting every sample generates the
  same files as before. `elasticore-base` tests pass (2093, 3 skipped), `-springboot3` (472, 10 skipped), `-springboot4`
  (473, 10 skipped), the pet store's (205); `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED; `elcoreTreeCheck`
  0 differ / 0 missing / 0 extra; `diffCoverage` 98.5% of 132 changed lines; `diffMutation` 100% of 62 mutations.
- Files: `rules/SchemaSearch.java` (new), `rules/SchemaCatalog.java`, `rules/verify/RulesGenRunner.java`,
  `rules/mybatis/MyBatisMapperRules.java`, `rules/mybatis/MyBatisSearchRules.java` (`operator` public),
  `rules/mongo/MongoRepositoryRules.java`; elasticore-springboot3 `schema/SchemaInfo.java`; guide `elasticore-env-guide.md`
  6-6, en and ko; tests `SchemaSearchTest` (new), `SchemaCatalogTest`, `SchemaInfoGenerationTest`, runtime `SchemaInfoTest`
  and its `desk-schema.json`, the pet store's `SchemaInfoTest`.
