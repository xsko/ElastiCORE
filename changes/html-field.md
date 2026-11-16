### New Features (field types - `html`, `@safe`, `@plain`)

#### An html field: kept to an allow-list before every write, with a plain-text copy for the search
- Why (issue 183): a field holding what a rich-text editor sends was a `text`, stored as sent - stored XSS the moment a
  page renders it. Applications cleaned it by hand before saving (jsoup's `Safelist`) and kept a second field without
  tags for search and previews. `html` as a type did not exist: the DSL read it as an unknown type, wrote
  `private html content;` into the entity and dropped the field from the DTO.
- What: `content: html! @safe(basic) @plain(contentText)` beside `contentText: text @search(like)`.
  - `html` is a long text, as `text` is (the same column), marked html.
  - The entity gets `cleanHtml()`: each html field kept to a jsoup safelist - `@safe(basic)`, the default (text markup,
    links with `rel="nofollow"`, no images), or `@safe(relaxed)` (headings, tables, images too) - and, with
    `@plain(field)`, its text without tags written to that long text field. What is kept is not reformatted.
  - The CoreService calls it on every write - a save, an update, an update by condition, and after a lifecycle
    command, whose parameter is stored by the move inside the entity, past the save path - before the row is written
    and before `beforeSave`. The method is the entity's, as `derive()` is: under the DDD rule sets the setters are
    protected.
  - The `@plain` field is the server's: `@readonly` (no request writes it; read-only in the DTO and TypeScript).
  - TypeScript: the field's metadata says `html: true`.
- jsoup: the runtime's `io.elasticore.springboot3.html.Html` is only compiled against it (`compileOnly`, so the POM
  does not change), and the generated line names `org.jsoup.safety.Safelist`: an application with an html field adds
  `org.jsoup:jsoup`, and without it the build stops at that line.
- Refused, the build stops: `@safe` with another value; `@plain` naming no field, a field that is not a long text
  (`text`, `longtext`, `clob`) or the html field itself; `@safe` or `@plain` on a field that is not html; an html field
  on a member of an aggregate or on a value object, which no service of theirs writes; the 2.x-compatible output; the
  2.x generator, by a DSL warning (it stops on any), since it would store what a request sends.
- Works in: v3 and Kotlin, JPA and MyBatis (layered and hexagonal). The layered MyBatis service gets the before-save
  slot the other services have; empty, it writes nothing, so a model without html generates the same.
- Samples: `elasticore-template-ddd`'s board `Post.content` (with a publish command whose parameter sets it),
  `elasticore-template-kt`'s `Comment.content`, `elasticore-template-mybatis`'s `Gadget.page` (`@safe(relaxed)`); their
  DDL and `schema.yml` (and the board's semantic model) written again with the new columns.
- Checked:
  - `HtmlTest` (runtime, springboot3 and springboot4): basic keeps text markup and links with `rel="nofollow"` and
    drops a script with its text, a handler, a `javascript:` link and an image; relaxed keeps tables and https images;
    nothing reformatted; the plain text without a script's text, entities read; null stays null.
  - `HtmlFieldTest` (generator): layered Java (the three writes call it before `beforeSave`; the method, basic and
    relaxed; the copy read-only in the DTO; a TEXT column), Kotlin, layered MyBatis, a hexagonal command (cleaned after
    the move), TypeScript (`html: true`, the copy `readOnly`), a model without html unchanged in JPA and MyBatis (no
    line left by the empty slot), and every refusal above. `HtmlFieldTypeTest` (loader): the type, and the DSL warning
    under the 2.x generator only.
  - HTTP and on the generated schema: `BoardHtmlTest` (a create, the publish command, the search reading the plain
    copy and not a removed script's text); `CommentHtmlTest` (Kotlin, a save and an update, the caller's copy
    replaced); `ShopApi.anHtmlColumnIsCleanedAndItsTextKept` (MyBatis, a create and an update; H2 here, PostgreSQL,
    Oracle and SQL Server in CI).
  - Without the clean after a command's move, `BoardHtmlTest` fails: the `onerror` handler is stored.
- Files: `HtmlRules` (new), `SpecFactory.prepareHtml`, `RulesGenRunner`, `AbstractModelLoader`, `DddCommandRules`,
  `TypeScriptDtoFileRules`, `MyBatisServiceFileRules`, `RuleCatalog` (`jpa-html`, every bundle), template
  `v3/mybatis_service.tmpl` and `v3/mongo_service.tmpl` (`jpa-html` in `mongo-springboot` too; `HtmlFieldTest.mongoCleansInItsSavePath`); runtime `Html` (new) and the jsoup `compileOnly` in springboot3 and springboot4; the DSL
  guide's types and annotations, the annotation guide's "html / @safe / @plain" and the rule guide (en, ko).
