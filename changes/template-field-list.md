### New Features (DTO - the fields of a `@template`)

#### A DTO lists the fields it takes right after the model they come from: `@template(Order[note, deliveryDate], Customer)`
- Why: `@template(Order)` copies every field of the entity, inherited audit fields included, and a smaller DTO was
  made by removing the rest one by one with `field: --`. A DTO taking four fields of thirty listed twenty-six
  removals, and a field added to the entity later appeared in the DTO, and so in every request that binds it, until
  someone added another removal (#79).
- What: a model in `@template` may carry its list in brackets. A model without brackets still copies all its fields,
  and a model of another domain is `shop:Order[note]`. Only the copied fields are filtered, where the model gathers
  them (`getAllFieldListMap`), so the 2.x generator, its compatible output and the 3.0 code take the same fields and
  the mapper needs no change. A field the DTO declares is always kept, and with a listed name it overrides the
  copied one. A field the model inherits through its own `@extend` can be listed, a DTO can be the template, and a
  relation's name keeps the field that holds its id (`customerId @idof(customer)` of a DDD reference). `customerId`
  itself is refused, as the list is checked before the reference is rewritten. Without such a field a declared DTO
  leaves the relation out, listed or not, as it does today; the id is a `@ref`.
- One reader: `TemplateList` is now the only place that reads the value. What asks which models a DTO is made from
  gets the plain names: the template's field copy, the DTO-to-entity relationship, the key DTO, the DTO's size
  validation, both mappers, `ForcedRefCheck`, `ContextMap` and `SpecFactory`. A value without brackets comes back
  exactly as it was written, so a DTO without brackets generates what it did, an existing two-template DTO included.
- Refused once every domain is loaded, so a template of another domain is found: a name the model does not have, a
  model no domain has, the same model twice (`Order[note], Order[total]` or `Order, Order[note]`), empty brackets
  (`Order[]`), an empty name in a list, brackets that cannot be read (`Order[note`), `--` on a listed field, a list
  together with `@extend`, and a list on an entity. Both generators stop on it (2.x on the DSL warning, the rule
  engine on the model check). `@searchable` and `@searchResult` take no list.
- Checked: `TemplateFieldListTest` - the reader, a template DTO in the compatible output and the 3.0 code, each model
  with its own list, a model with and without its domain, the mapper's pairing in both outputs, the key DTO, a DTO
  as the template, an inherited field, a relation name with and without an id field, a DDD id field by its own name, Kotlin, the refusals, a DTO without brackets
  that is not refused, and the 2.x generator in a JVM of its own taking the same fields and refusing the same. The
  template's `hr` model declares `EmployeeContactDTO @template(Employee[...])`, so `elcoreRulesCheck` and
  `elcoreTreeCheck` compare it between 2.x and the rule engine.
- Files: `TemplateList.java`, `AbstractDataModel.java`, `ForcedRefCheck.java`, `ModelExtractor.java`,
  `DtoSrcFilePublisher.java`, `MapperSrcPublisher.java`, `SrcFilePublisher.java`, `ContextMap.java`,
  `RulesGenRunner.java`, `SpecFactory.java`, `TemplateFieldListTest.java`, the template's `hr/model/dto.yml` and its
  regenerated sources, `elasticore-annotation-guide.md` 4.1 and `elasticore-dsl-guide.md` (en, ko)
