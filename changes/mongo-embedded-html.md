### Bug Fixes (v3 MongoDB - security: `html` on an embedded member)

#### MongoDB cleans the html of the members a document embeds, on every write of the document
- Why: under `mode: mongo` an entity without a collection of its own (no service, no `@repository` - an order's line) is
  kept inside the document that holds it, and the holder's mapper writes it from the request. An `html` field on such a
  member was accepted - `prepareHtml` refuses html on a member it finds through `@aggregate`, `@cascade` or a `@OneToMany`
  from a `@root`, and MongoDB decides what to embed by another rule (`MongoDocs.ownCollection`) - and the member got its
  `cleanHtml()`, but nothing called it: the holder's service cleaned only its own fields. The markup was stored exactly as
  the request sent it, `<script>` and `on...` handlers included, with no build error or warning. Reproduced on
  `elasticore-template-mongo` with `OrderLine.note: html`: a `PUT /purchaseOrder` with
  `<b onclick="steal()">hi</b><script>steal()</script>` in a line handed that text to MongoDB unchanged.
- Who is affected: MongoDB projects (Java or Kotlin) with an `html` field on an embedded member. A page that shows the value
  as HTML (the TypeScript client marks it `html: true`) ran what a writer put in it. JPA and MyBatis are not affected: their
  root's mapper does not write such a member from the request, and html on a member the rules write with their root
  (`@aggregate`, `@cascade`, a `@root`'s `@OneToMany`) or on a value object (`@embeddable`) is refused there as here.
- Now the holder's CoreService calls each embedded member's `cleanHtml()` on every write - a save, an update, an update by
  condition, and a batch update (each through update) - after the holder's own `entity.cleanHtml()` and before the
  developer's `beforeSave`: a collection's members, a single member, and the members those hold in turn (Java and Kotlin):

  ```java
  if (entity.getLines() != null) for (io.acme.shop.domain.model.OrderLine m : entity.getLines()) {
      m.cleanHtml();
  }
  ```
  ```kotlin
  for (m in entity.lines ?: emptyList<io.acme.shop.domain.model.OrderLine>()) {
      m.cleanHtml()
  }
  ```
  Superseded in part by `html-clean-hooks.md` (same release): the generated lines now hand each member the pair the holder's
  service has for it - `m.cleanNote(html -> cleanNote(m, html), html -> plainNote(m, html));` - in place of `m.cleanHtml();`,
  which stays on the member for code that calls it.
  MongoDB has no lifecycle commands, so there is no other write path. A member written after the clean (in `beforeSave`, or
  through the repository) is the application's to clean, as a root's field is. Members that hold each other (a line's parts
  holding lines) are kept to any depth, which the generated lines cannot reach: an html field among them now stops the build
  (`... MongoDB keeps HtLine inside the HtOrder document to any depth (HtLine -> HtPart -> HtLine) ...`).
- Unchanged: the rule adds lines only under `mode: mongo` and only where a member has an html field, so a project without
  html, a JPA or MyBatis project, and a MongoDB project without html on a member generate byte-identical output (by
  construction; `HtmlFieldTest` checks such a MongoDB and JPA service). For the 2.x baseline: `elcoreRulesCheck -Pstrict`
  158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra. No runtime library change, no DSL word.
- Checked: `HtmlFieldTest` (+3): the Java and Kotlin services of a MongoDB model with a collection of members holding a
  collection and a single member of their own (each cleaned on save, update and update by condition, before `beforeSave`;
  a member without html left alone), a root's own clean before its members', the same model without html and under JPA
  without a member call, and members holding each other refused - the three fail without the fix. Over HTTP on MongoDB
  7.0.14 (`ELASTICORE_IT_MONGODB_URI`): `elasticore-template-mongo` and `elasticore-template-kt-mongo`
  `ShopApi.anEmbeddedMembersHtmlFieldIsCleanedOnEveryWrite` - a line's `<b onclick="steal()">hi</b><script>steal()</script>`
  answered and stored as `<b>hi</b>` on `POST`, `PUT`, `PUT /batch` and `updateBySpecification`; whole suites 48 and 42
  passed (their Docker tests skipped as before). Both samples generated with the member html taken out give the same 121
  source files and 34 resources before and after the change. `MongoGenerationTest`, `GuideTranslationTest`,
  `SourceAsciiTest` pass; `formatCheck` and `diffErrorprone` pass; `diffMutation` kills 37 of the 40 mutations of the changed
  lines (93%).
- Guides: annotation guide (en, ko), html section.
- Files: `base/HtmlRules.java`, `verify/RulesGenRunner.java` (`relateMembersEarly`: under MongoDB a member's own relations
  are known when the service is written, so its members are reached), `HtmlFieldTest.java`, the annotation guide (en, ko), `elasticore-template-mongo` and
  `elasticore-template-kt-mongo`'s shop `entity.yml` (`OrderLine.note: html`) and `ShopApi.java`.
