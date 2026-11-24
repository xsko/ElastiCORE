### Bug Fixes (v3 - a type of another domain)

#### A type of another domain is named simply and imported from its domain, in every mode, language and architecture (#331)
- Why: the rule is 2.x's: a model element of another domain - an `@abstract` parent, an entity, an enum, an
  `@embeddable` value object, a DTO - is written by its simple name, stays in the domain that declares it, and is
  imported from there; type names are unique across domains. The 3.0 code broke it in several places. #331: an
  `@abstract` parent of another domain, `@extend(AuditEntity)`, was not imported by the MyBatis and MongoDB entities
  (`cannot find symbol`), and `@extend(common:AuditEntity)` was refused under MyBatis as table inheritance. A
  two-domain model generated in each combination and compiled found the rest: a port's parameter or return of another
  domain's DTO or enum was not imported (the port interface in every combination, the MyBatis port adapter and mapper
  too); a command's parameter of another domain's enum was not found in the behaviour class (ddd, hexagonal; Java and
  Kotlin; JPA and MyBatis); a DTO with `@template` of another domain's entity wrote a copy of that entity's key DTO
  (`OfficeKeyDTO`) in the using domain. A type written with its domain, which 2.x accepted (`common:Grade`,
  `List<common:ContactDTO>`, `@template(common:Office)`, a DTO's `@extend(common:ContactDTO)`), stopped the run on a
  file named `common:OfficeKeyDTO`, or was written as `common:Grade` into the code.
- Now: the rule engine reads a type written with its domain as its simple name (fields, a port's parameters and
  returns, the `@extend` of an entity or a DTO); the 2.x generator, which writes such a type by its full class name,
  is unchanged. The entity files of JPA, MyBatis and MongoDB, Java and Kotlin, import a parent of another domain
  through one helper (`RuleText.registerParentImport`); a port - the ElastiCORE runtime interface, the Spring HTTP
  client and the MyBatis adapter and mapper - imports each type of another domain its methods name
  (`PortBaseRules.otherDomainImports`, on `RuleText.modelTypeImports`); a command's parameter of another domain is
  written with that domain's package in the aggregate and its behaviour class; the key DTO of another domain's entity
  is that domain's alone (the 2.x-compatible output keeps the copy 2.x writes). A type two domains declare stays a
  DSL warning, which now says what to do (`... a type name is unique across domains: rename one, ...`), and the
  `@template` list refusal for such a name says so too, instead of asking for `domain:Name`.
- Upgrade: a 3.0 project (Java, Kotlin or TypeScript) with a DTO whose `@template` names another domain's entity no
  longer gets that entity's key DTO written in its own dto package (`agency.dto.OfficeKeyDTO`); the generated code
  never used it. Code of your own that imports it imports the declaring domain's (`common.dto.OfficeKeyDTO`).
- Checked: `OtherDomainTypesTest` - the two-domain model in JPA, MyBatis and MongoDB, Java and Kotlin, layered, ddd
  and hexagonal (what release/3.0 writes: MyBatis is Java only, MongoDB layered only): each type one class, in
  `common`; the imports and the parameter above; a model written with `common:` generates the same files as one
  written simply (TypeScript too); a Spring HTTP port (Boot 4); the duplicate-type warning on an `elcore3` run.
  `ExtendOtherDomainTest` - #331's model under MyBatis by both names (import, `extends AuditEntity`, the parent's
  column in the mapper XML, no refusal), JPA and MongoDB. The same model, by simple and by qualified names, generated
  in the 11 combinations and compiled with javac or kotlinc (main sources and the contract tests), and the
  TypeScript client with tsc: all 22 compile; before the fix none of the 11 did, and the qualified one did not
  generate.
- Files: `TypeInfo` (`simpleNamed`), `Method`, `SpecFactory` (`extendOf`, command parameter packages), `RuleText`
  (`simpleModelName`, `modelTypeImports`), `DtoCleanFileRules`, `KotlinDtoFileRules`, `JpaEntityFileRules`,
  `KotlinEntityFileRules`, `MyBatisEntityFileRules`, `MongoEntityFileRules`, `PortBaseRules`, `MyBatisPortRules`,
  `portSpring.tmpl` (Java, Kotlin), `RulesGenRunner` (key DTO), `ModelExtractor`, `TemplateList`, the annotation, DSL
  and env guides (en, ko).
