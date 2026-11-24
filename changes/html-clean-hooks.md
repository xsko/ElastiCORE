### Features (v3 - `html`: the cleaning is the application's to override)

#### An `html` field is cleaned by a pair of CoreService methods the developer's service overrides, and only where it changed (issue 217)
- Why: the generated `cleanHtml()` on the entity ran the safelist `@safe` names before `beforeSave`. A site whose editor
  embeds its own uploads (`<img src="/api/files/3">`) and must refuse another host's image, or refuse an empty body with a
  code of its own, could not use `html`: the generated cleaning removed or kept the images before its code saw them, and
  editing `cleanHtml()` in a generated entity is a merge conflict at the next `@safe` change. Under layered there was no
  class of the developer's to override it in. The plain copy (`@plain`) joined the paragraphs into one line. And the
  cleaning ran on every write, also after a command that did not touch the field - for a cleaner that looks up each
  embedded file, wasted queries. The DSL stays as it is: no new word or option (the model is read without the JVM too;
  a Java class or a jsoup type means nothing there).
- Now: the CoreService has a pair per html field, which does what `@safe` and `@plain` say and what `cleanHtml()` did:

  ```java
  protected String cleanContent(Post row, String html) { return Html.clean(html, Safelist.basic()); } // @safe
  protected String plainContent(Post row, String html) { return Html.plain(html); }                  // @plain(contentText)
  ```
  ```kotlin
  protected open fun cleanContent(row: Post, html: String?): String? = Html.clean(html, Safelist.basic())
  ```
  The developer's `PostService extends PostCoreService` overrides them - plain methods, nothing to implement. The row is
  the one being written, with the request's values on it (on a create, a key the database draws may not be there yet);
  `plainContent` gets what `cleanContent` returned. The entity still sets the field and its copy - under ddd and
  hexagonal the copy's setter is its own - from what the service hands it: `entity.cleanContent(html ->
  cleanContent(entity, html), html -> plainContent(entity, html))` (Kotlin `entity.cleanContent({ cleanContent(entity,
  it) }, { plainContent(entity, it) })`), one method per html field, written in the class that declares the field (an
  `@abstract` parent's field in the parent, which its `@extend` children inherit). Neither function is called with null,
  and a field set to null leaves its copy null.
- `cleanHtml()` stays on the entity, public as before, for the developer's own code that calls it: it applies the
  generated defaults (the `@safe` safelist, the `@plain` copy) to each html field through those methods; a child's calls
  its parent's first (Kotlin: the parent's is `open`, the child's `override`). The generated service no longer calls it;
  the CoreService pair, which the developer's service may override, takes over.
- When: after the request (or the command) changed the row - right after the mapping or the command, before the values
  the model derives and before `beforeSave` - and only where the field changed - the service keeps the value it read: on
  a create unless the field is null; on an update, a batch update (through update) and an update by condition where the
  request changed it; after a lifecycle or business command where the command changed it - and also, where `@plain`
  names a field, when that copy is null while the html field has a value, so a row saved before `@plain` was declared
  gets its copy on its next write. An empty copy is a result (a body of images only) and is left alone; Oracle stores an
  empty text as NULL, so there such a row is cleaned again on its next write. A value stored before the field became
  html, or under a looser rule, is not cleaned again until it changes: run the pair, or `cleanHtml()`, once over such
  rows. A refusal thrown from the pair is answered by the generated error handler as one from `beforeSave` (400 with its
  code) and the row is not written; under MongoDB a batch update and an update by condition write document by
  document without a transaction, so the documents before the refused one stay written. Under MongoDB the holder's
  service also has the pair of each member it embeds (`cleanNote(OrderLine row, String html)`) and cleans the members on
  every write of the holder, as before - a member has no stored value of its own to compare with.
- New runtime method `Html.plainLines(html)`: a line ends after `p`, `h1`-`h6`, `li`, `blockquote`, `pre`, `div`, `tr`,
  `br`, `hr`; no list marker; spaces before a line's end dropped, at most one blank line, both ends stripped; blocks
  inside each other end one line (`<blockquote><p>a</p></blockquote><ul><li><p>b</p></li></ul>` is `a\nb`); a `pre`'s
  text is kept as written (its blank lines, spaces and indentation).
- Who is affected: a project with an html field regenerates its CoreService (the pair, and the lines that call it) and
  its entity (`cleanContent(...)` beside `cleanHtml()`). It behaves as before, except that an unchanged field is no
  longer cleaned again; code of the developer's that calls `entity.cleanHtml()` still compiles and gets the defaults.
  Upgrade note: a project whose own service already has a method named `cleanX` or `plainX` (X the html field) with
  another signature or visibility stops compiling where it clashes with the generated pair - rename it, or make it the
  override. A project without an html field generates the same files: the three new skeleton slots are empty there and
  leave no line.
- Checked: `HtmlFieldTest` (rewritten for the pair: the service's lines on save, update, update by condition and the
  command, the value read before the mapper and before the command, the missing-copy condition, the pair with the
  safelist `@safe` names, the entity's methods and the kept `cleanHtml()`, an `@abstract` parent's field written once
  in the parent with the child's `cleanHtml()` calling it; Java and Kotlin; JPA, MyBatis layered and hexagonal, MongoDB
  with members; a model without html, Java and Kotlin) and `HtmlTest` (+8, `plainLines`, a `pre` as written) pass. Over
  HTTP: `elasticore-template-ddd` `BoardHtmlTest` 8/8 - the board's `PostService` overrides the pair (basic with images,
  only `/api/files/...` kept, an empty body refused with `post.content.required`, `plainLines`), and `Post.content` is
  now `@editable`: the refusal answers 400 with its code on a create, on an update (PUT; the post keeps its content,
  text and version) and on the `publish` command, and writes nothing; an update is cleaned like a create; a `publish`
  that hands the post the content it holds leaves a stored value the cleaning would have changed; a row whose
  `content_text` is NULL gets it on a `publish` of the same content. Kotlin: `elasticore-template-kt` `CommentHtmlTest`
  3/3. Java: `elasticore-template-mybatis` `HandWrittenCleanHtmlTest` and `MyBatisApiTest` 17/17.
  `elasticore-template-mongo` 15/15 on MongoDB 7.0.14 (`ELASTICORE_IT_MONGODB_URI`), with a batch update refused on its
  second document keeping the first written; `elasticore-template-kt-mongo` 11/11. Generated and compiled with an html
  field and a command in every other combination (JPA Java layered and ddd, MyBatis Java hexagonal and ddd, JPA Kotlin
  hexagonal and ddd) and with an html field on an `@abstract` parent (JPA Java and Kotlin) - temporary domains, not
  committed. For the 2.x baseline: `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED (as before),
  `elcoreTreeCheck` 0 differ / 0 missing / 0 extra. `formatCheck` and `diffErrorprone` pass; `diffMutation` (before
  the review's fixes) kills 34 of 38 mutations in `elasticore-base` and 27 of 29 in the runtime.
- Guides: annotation guide (en, ko), html section - the pair, when it runs, overriding with the image check and a
  refusal, `plainLines`; rule guide (en, ko), the `jpa-html` row.
- Files: `base/HtmlRules.java` (the pair, the calls, the entity's methods, the slots), `ddd/DddCommandRules.java` (the
  value read before a command), the four service file rules (`JpaServiceFileRules`, `MyBatisServiceFileRules`,
  `MyBatisDddRules`, `MongoServiceFileRules`: the slots), the six service skeletons (`v3/service`, `v3/mybatis_service`,
  `v3/mybatis_port_service`, `v3/mongo_service`, `kt/service`, `kt/mongo_service`), runtime `Html.java`
  (`plainLines`), `HtmlFieldTest`, `HtmlTest`, the guides, `elasticore-template-ddd` (`PostService.java` kept out of the
  clean, `BoardHtmlTest`), `elasticore-template-kt` (`CommentService.kt` kept out of the clean, `CommentHtmlTest`),
  `elasticore-template-mybatis` (`HandWrittenCleanHtmlTest`), `elasticore-template-ddd` board `entity.yml` (`content` @editable),
  `elasticore-template-mongo` `ShopApi.java`, `changes/mongo-embedded-html.md` (a note that this supersedes its lines).
