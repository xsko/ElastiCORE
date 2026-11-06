### Documentation and tests (DDD - a root's reference to its own kind)

#### What a self-reference by id means for a caller is run over HTTP, and the env guide says what `referenceColumn: short` leaves alone
- Why: three follow-ups of the review of #100. The env guide still said `referenceColumn: short` names the column of
  every id reference after its field, the comment in both ddd templates' `dto.yml` read as if it described
  `subCategories`, and the 404 and the `null` relation were checked only in generated text.
- What: `SelfReferenceApiTest` in `elasticore-template-ddd` sends the requests. A `parentCategoryCatId` that names no row
  answers 404 `reference.Category.parentCategory`, one that names a row is written and the relation reads it, and on a
  `Category` in memory the id does not set the relation. No generated output changes: the `dto.yml` comment moves above
  `fields:`, and YAML comments are not read.
- Files: `SelfReferenceApiTest.java`, `elasticore-env-guide.md` 6-2 (en, ko), `shop/model/dto.yml` of both ddd templates
