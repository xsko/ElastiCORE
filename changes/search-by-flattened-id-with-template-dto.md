### Bug Fixes (rule engine - a searched relation with a declared DTO)

#### The mapper searches a relation by its flattened id alone when the model declares the entity's DTO
- Why: a relation with `@search` (`board: Board! @search(eq)`) is searched through the id flattened onto the
  search DTO, `boardBoardId`; the search DTO derived from the entity leaves the relation itself out. When the model
  also declares the entity's DTO (`PostDTO: meta: dto @template(Post)`), the relation reached the mapper's search
  conditions as it was: `toSpec` read `Board board = searchDTO.getBoard();`, a property the search DTO does not
  have, so the generated project did not compile. Under `mode: mybatis` the run stopped with "board is further than
  that". The rule engine only - in the 2.x-compatible output, the v3 output (Java, Kotlin; layered, DDD,
  hexagonal) and MyBatis. 2.x writes the condition on the flattened id alone: the 2.2 generator, run on the same
  model, reads `searchDTO.getBoardBoardId()` and nothing else.
- What: the search DTO, the mapper and MyBatis's search ask one question, `SearchDtoBaseRules.searchedByItsFlattenedId`,
  and leave out the same relation. A project whose model does not declare such a DTO generates what it did.
- The template's `hr` model now declares it: `Project.lead: Employee @ManyToOne @search(eq)`, with
  `ProjectDTO @template(Project)`. `elcoreTreeCheck` compared `HrMapper.java` as `DIFFER` without the change. The
  regenerated `Project.java`, `ProjectSrchDTO.java`, `HrMapper.java`, `Q.java`, the schemas and `schema.yml` are
  committed: an optional `lead_emp_id` column, and a search by `leadEmpId`.
- Checked: `TemplateDtoSearchRelationTest` - the compatible and v3 output (layered and hexagonal), Kotlin and MyBatis;
  three of its four tests fail without the change. `elcoreTreeCheck` 0 differ.
- Files: `SearchDtoBaseRules.java`, `MapperSpecRules.java`, `MyBatisSearchRules.java`, `TemplateDtoSearchRelationTest.java`,
  the template's `hr/model/entity.yml` and its regenerated files, `elasticore-annotation-guide.md` 2, "Required relationship" (en, ko)
