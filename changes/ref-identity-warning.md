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
- Checked: `SelfReferenceByIdTest.aRefDeclaredUnderAnotherNameIsSaid` - over the hexagonal run, the warning is printed
  once for `SfCommentKeyedDTO.parentKey`, and not for `SfCommentSameNameDTO.parentCommentId`, for `SfTopicDTO`
  (`@objectref`, no identity) or for the DTOs the model does not declare. Without the change the run prints no
  such line.
- Files: `DtoBaseRules.java`, `SearchDtoBaseRules.java`, `SelfReferenceByIdTest.java`, `elasticore-rule-guide.md`
  "Refer to another aggregate by ID" (en, ko)
