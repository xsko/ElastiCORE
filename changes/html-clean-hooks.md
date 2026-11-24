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
  the one being written, with the request's values on it (a new row has a null key); `plainContent` gets what
  `cleanContent` returned. The entity still sets the field and its copy - under ddd and hexagonal the copy's setter is its
  own - from what the service hands it: `entity.cleanContent(html -> cleanContent(entity, html), html ->
  plainContent(entity, html))` (Kotlin `entity.cleanContent({ cleanContent(entity, it) }, { plainContent(entity, it) })`),
  one method per html field. Neither function is called with null, and a field set to null leaves its copy null.
- `cleanHtml()` stays on the entity, public as before, for the developer's own code that calls it: it applies the
  generated defaults (the `@safe` safelist, the `@plain` copy) to each html field through those methods. The generated
  service no longer calls it; the CoreService pair, which the developer's service may override, takes over.
- When: after the request (or the command) changed the row, before `beforeSave`, and only where the field changed - the
  service keeps the value it read: on a create unless the field is null; on an update, a batch update (through update)
  and an update by condition where the request changed it; after a lifecycle or business command where the command
  changed it - and also, where `@plain` names a field, when that copy is null or empty while the html field has a value,
  so a row saved before `@plain` was declared gets its copy on its next write. A refusal thrown from the pair is answered by the generated error handler as one from `beforeSave` (400 with
  its code) and nothing is written. Under MongoDB the holder's service also has the pair of each member it embeds
  (`cleanNote(OrderLine row, String html)`) and cleans the members on every write of the holder, as before - a member
  has no stored value of its own to compare with.
- New runtime method `Html.plainLines(html)`: a line ends after `p`, `h1`-`h6`, `li`, `blockquote`, `pre`, `div`, `tr`,
  `br`, `hr`; no list marker; spaces before a line's end dropped, at most one blank line, both ends stripped; blocks
  inside each other end one line (`<blockquote><p>a</p></blockquote><ul><li><p>b</p></li></ul>` is `a\nb`); a `pre`
  keeps its lines.
- Who is affected: a project with an html field regenerates its CoreService (the pair, and the lines that call it) and
  its entity (`cleanContent(...)` beside `cleanHtml()`). It behaves as before, except that an unchanged field is no
  longer cleaned again; code of the developer's that calls `entity.cleanHtml()` still compiles and gets the defaults.
  A project without an html field generates the same files: the three new skeleton slots are empty
  there and leave no line.
- Checked: `HtmlFieldTest` (rewritten for the pair: the service's lines on save, update, update by condition and the
  command, the value read before the mapper and before the move, the missing-copy condition, the pair with the safelist
  `@safe` names, the entity's methods and the kept `cleanHtml()`; Java and Kotlin; JPA, MyBatis layered and hexagonal,
  MongoDB with members) and `HtmlTest` (+6, `plainLines`) pass. Over HTTP: `elasticore-template-ddd` `BoardHtmlTest`
  7/7 - the board's `PostService` overrides the pair (basic with images, only `/api/files/...` kept, an empty body
  refused with `post.content.required`, `plainLines`): the refusal answers 400 with its code on a create and on the
  `publish` command and writes nothing (no row; the post stays a draft); a `publish` that hands the post the content it
  holds leaves a stored value the cleaning would have changed; a row whose `content_text` is NULL gets it on a `publish`
  of the same content. Kotlin: `elasticore-template-kt` `CommentHtmlTest` 3/3 with `CommentService` overriding the pair
  (the refusal writes no row; an update that leaves the content alone keeps the stored copy, and restores a NULL one; a
  hand-written `cleanHtml()` cleans with the defaults). Java: `elasticore-template-mybatis` `HandWrittenCleanHtmlTest`
  (a hand-written `gadget.cleanHtml()` compiles and cleans with `@safe(relaxed)` and `@plain`) and `MyBatisApiTest`:
  17/17. `elasticore-template-mongo` and `elasticore-template-kt-mongo` on MongoDB 7.0.14 (`ELASTICORE_IT_MONGODB_URI`)
  14/14 and 11/11. Generated and compiled with an html field and a command in every other combination: JPA Java
  layered and ddd, MyBatis Java hexagonal and ddd, JPA Kotlin hexagonal and ddd (temporary domains, not committed).
  For the 2.x baseline: `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED (as before), `elcoreTreeCheck`
  0 differ / 0 missing / 0 extra. `elasticore-base` tests matching Mongo, MyBatis, Command, Html, Mask, Count, Kotlin,
  plus `GuideTranslationTest` and `SourceAsciiTest`: 237 passed. `formatCheck` and `diffErrorprone` pass;
  `diffMutation` kills 34 of the 38 mutations of the changed lines in `elasticore-base` (89%; the survivors are comment
  wording) and 27 of 29 in the runtime (93%).
- Guides: annotation guide (en, ko), html section - the pair, when it runs, overriding with the image check and a
  refusal, `plainLines`; rule guide (en, ko), the `jpa-html` row.
- Files: `base/HtmlRules.java` (the pair, the calls, the entity's methods, the slots), `ddd/DddCommandRules.java` (the
  value read before a command), the four service file rules (`JpaServiceFileRules`, `MyBatisServiceFileRules`,
  `MyBatisDddRules`, `MongoServiceFileRules`: the slots), the six service skeletons (`v3/service`, `v3/mybatis_service`,
  `v3/mybatis_port_service`, `v3/mongo_service`, `kt/service`, `kt/mongo_service`), runtime `Html.java`
  (`plainLines`), `HtmlFieldTest`, `HtmlTest`, the guides, `elasticore-template-ddd` (`PostService.java` kept out of the
  clean, `BoardHtmlTest`), `elasticore-template-kt` (`CommentService.kt` kept out of the clean, `CommentHtmlTest`),
  `elasticore-template-mybatis` (`HandWrittenCleanHtmlTest`).
