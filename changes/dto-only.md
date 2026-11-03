### New Features (DTO - `@only`)

#### A DTO names the fields it takes of its template: `@only(note, deliveryDate)`
- Why: `@template(Order)` copies every field of the entity, inherited audit fields included, and a
  smaller DTO was made by removing the rest one by one with `field: --`. A DTO taking four fields of
  thirty listed twenty-six removals, and a field added to the entity later appeared in the DTO, and so
  in every request that binds it, until someone added another removal.
- What: `dto @template(Order) @only(note, deliveryDate)`, its own mark on the meta line (a parameter of
  `@template` would clash with its list of templates). Only the copied fields are filtered, where the
  model gathers them (`getAllFieldListMap`), so the 2.x generator, its compatible output and the 3.0
  code take the same fields and the mapper needs no change. A field the DTO declares is always kept,
  and with a listed name it overrides the copied one. The same with `@searchable` and
  `@searchResult`. A relation's name keeps the field that holds its id (`customerId @idof(customer)`
  of a DDD reference).
- Refused once every domain is loaded, so a template of another domain is found: a name no copied
  model has, `@only` without `@template`, `@searchable` or `@searchResult`, `@only` with `@extend`,
  `--` on a listed field, and `@only` naming nothing. Both generators stop on it (2.x on the DSL
  warning, the rule engine on the model check).
- A DTO without `@only` is generated as before.
- Checked: `DtoOnlyTest` - a template DTO in the compatible output and the 3.0 code, a search DTO, a
  template of another domain, a relation name in a DDD domain, the refusals (and a template no domain
  has, whose listed names are then unknown), and the 2.x generator
  in a JVM of its own taking the same fields and refusing the same; all fail without the change. The
  template's `hr` model declares `EmployeeContactDTO @template(Employee) @only(...)`, so
  `elcoreRulesCheck` and `elcoreTreeCheck` compare it between 2.x and the rule engine.
- Files: `AbstractDataModel.java` (`onlyNames`, `onlyProblems`), `DataTransferAnnotation.java`,
  `ModelExtractor.java` (`refuseOnly`), `DtoOnlyTest.java`, the template's `hr/model/dto.yml` and its
  regenerated sources, `elasticore-annotation-guide.md` 4.1 and `elasticore-dsl-guide.md` (en, ko)
