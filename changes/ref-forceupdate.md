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
  `relation.key`. Said to do nothing where the mapper does not write the relation: a relation that
  cascades, one joined read-only on another field's column (`@joinBy`, the 3.0 code), and a DDD
  reference to another aggregate, held by its id field.
- A `@ref` without `@forceupdate` is generated as before.
- Checked: `RefForceUpdateTest` - the else in both overloads in the compatible output and the 3.0 code,
  in Kotlin and in MyBatis (one overload, no `EntityManager`), none for a `@ref` without
  `@forceupdate`, the three refusals, the three warnings, and the 2.x generator in a JVM of its own
  writing the same and refusing the same. The template's `AttachmentDTO.articleId` is now
  `@ref(article.articleId) @forceupdate`; `AttachmentArticleForceUpdateTest` runs the mapper 2.x
  generated against H2: a full update without an id (null, or empty) takes the attachment off its
  article, a PATCH keeps it, an id attaches that article.
- Files: `MapperBaseRules.java`, `MapperSrcPublisher.java`, `ForcedRefCheck.java`, `ModelExtractor.java`,
  `RefForceUpdateTest.java`, the template's `content/model/dto.yml`, its regenerated `ContentMapper.java`
  and `AttachmentArticleForceUpdateTest.java`, `elasticore-annotation-guide.md` 2.4 (en, ko)
