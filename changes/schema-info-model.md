### Features (v3 controller - `GET /schema/info` from the model)

#### `config: schemaInfo: model` - `/schema/info` answers what a screen needs, generated from the model
- Why (issue 373, second step): screens that cannot import the TypeScript client - a commercial UI platform, the screens a
  modernisation keeps - read their field definitions over HTTP, and `GET /api/<domain>/<entity>/schema/info` told them only
  each field's Java class name, `@Schema` description, `@Size(max)` and an enum's raw columns. The TypeScript client of
  the same model says much more (`XxxFields`, `XxxMeta`, `XxxLabels`).
- Now, with the domain-wide env.yml setting `config: schemaInfo: model` (3.0 code: JPA, MyBatis, MongoDB; Java, Kotlin;
  layered, ddd, hexagonal):
  - The generator writes `src/main/resources/elasticore/<domain>-schema.json` beside the controllers (through the resource
    writer, merged against the cache like every v3 output; ASCII, every other character a JSON escape). It holds, for each
    entity whose controller serves the endpoint, the entity and each field of the DTO the endpoint describes, stated by
    `FieldFacts` - the same facts the TypeScript client is written from.
  - The controller's one line becomes `return io.elasticore.springboot3.schema.SchemaInfo.answer("<domain>", "<Entity>")`
    (the new `${schemaInfoAnswer}` slot of `v3/control.tmpl` and `kt/control.tmpl`; the unused `DTOUtils` import goes).
  - `SchemaInfo` (elasticore-springboot3, shared by elasticore-springboot4) reads the file once per domain (Jackson 2) and
    answers `metaVersion: 1` with `language`, `entity` (`name`, `domain`, `label`, `labels`, `path`, `key`, `dto`,
    `searchDto`) and `fields`: each with the keys it always had (`type`, `description`, `maxSize`, `enums` - read by
    `DTOUtils.getMetaInfo` as before) and `kind`, `label`, `labels`, `required`, `maxLength`, `minLength`, `min`, `max`,
    `readOnly`, `html`, an enum's `options` (`value`, `name`, `label`, `labels`) and a reference's `ref` (`entity`,
    `domain`, `key`, `search`, and `display` only where the DTO has an `@ref` field showing that row). A label is said in the
    request's language (`ErrorMessages.labelOrDefault`: the label bundle's file of that language, its default file, then
    the DSL's `--` text); an enumeration's values by the label bundle of the domain that declares it. `Cache-Control: private`.
  - The file is removed when the setting is taken out, as every generated file the DSL no longer produces; a failure
    writing it is counted as a failed artifact.
  - Access is not changed: `/schema/info` keeps the access it has (the guides say how to close it).
  - Warnings (`DSL_WARN`): the setting in a domain of the 2.x code (output v2; nothing is written); a value other than
    `model`; a project's `control.tmpl` without the slot; a controller the developer keeps whose `/schema/info` does not
    call `SchemaInfo.answer` (the file and the line to write are named).
  - `FieldFacts.kind` says `Object` for a value object (`@embeddable`), which it said was a `Ref`; nothing read it before.
    `FieldFacts.reference` (the relation a DTO field reads, `@ref` or `@idof`) is the contract tests' former private helper.
- Before and after: without the setting every sample generates the same files, byte for byte. With it, the controller's
  `getMetaInfo()` line and its imports change, and the schema file is added. The pet store turns it on.
- Checked: every sample regenerated without the setting (`elasticore-template-ddd`, `-kt` with `elcore3ts`,
  `-mybatis`, `-mongo`, `-ddd-mybatis`, `-kt-mongo`, `elasticore-sample-petstore-mybatis`) is byte for byte what main
  generates, apart from the guides copied into their blueprint folders; the pet store differs only in its six controllers'
  `getMetaInfo()` line and imports and the added schema file. Generated and compiled with the setting, temporarily: Kotlin
  JPA (`-kt`), Java MongoDB layered (`-mongo`), Kotlin MongoDB (`-kt-mongo`), hexagonal MyBatis (`-ddd-mybatis`), layered
  MyBatis on Spring Boot 4 (`elasticore-sample-petstore-mybatis`), hexagonal JPA (`-ddd`); taking the setting out again
  removed each file. The pet store's `SchemaInfoTest` asks over HTTP (Boot 4, Jackson 3): a label in English, Japanese
  and Korean, an enum's options and their labels, a reference with its search and display, a value object, a read-only
  field, required and lengths, `Cache-Control: private`; the pet store's tests pass (203). `elasticore-base` tests pass
  (2084, 3 skipped), `elasticore-springboot3` (471, 10 skipped) and `-springboot4` (472, 10 skipped);
  `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED; `elcoreTreeCheck` 0 differ / 0 missing / 0 extra;
  `diffCoverage` 95.5% of 403 changed lines; `diffMutation` 89.2% of 157 mutations. New in-process tests:
  `SchemaCatalogTest` (the line, the warnings, the file, references and displays, composite keys, numbers, ASCII),
  `SchemaInfoGenerationTest` (JPA, MyBatis, MongoDB; Java, Kotlin; layered, ddd, hexagonal; unset, 2.x code, an unknown
  value, a kept controller, the setting taken out, an enumeration of another domain), `FieldFactsTest` (`reference`, a
  value object's kind); runtime `SchemaInfoTest` (languages, the old keys, another domain's enumeration, read-only cache,
  a missing file or entity).
- Files: `rules/SchemaCatalog.java` (new), `rules/FieldFacts.java`, `rules/SourceCode.java`,
  `rules/base/ControllerBaseRules.java`, `rules/verify/RulesGenRunner.java`, `rules/verify/ContractTestSource.java`,
  `elasticore-template/v3/control.tmpl`, `elasticore-template/kt/control.tmpl`, elasticore-springboot3
  `schema/SchemaInfo.java` (new); guides `elasticore-env-guide.md` (6-6) and `elasticore-annotation-guide.md`, en and ko;
  the pet store's `env.yml` and `SchemaInfoTest`; tests `SchemaCatalogTest`, `SchemaInfoGenerationTest`, `FieldFactsTest`,
  `SchemaInfoTest` (runtime).
