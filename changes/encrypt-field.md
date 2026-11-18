### New Features (v3 entity and runtime - `@encrypt`)

#### `@encrypt`: a column kept encrypted (AES-256-GCM), and with `@search(eq)` found by a blind index
- Why: projects kept personal data encrypted with a converter of their own (`phone: string(20) @env:encrypt
  @env:maskphone`), and then could not search it: the column holds a different text for the same value each time,
  so `@s(eq)` found nothing.
- Now: `phone: string(20) @encrypt @s(eq)`. The runtime (`io.elasticore.springboot3.crypto`, both runtimes) encrypts
  with AES-256-GCM and a random 12-byte IV per write and stores `v1:` + base64(IV, ciphertext, tag); the prefix names
  the key version, so after a rotation old rows are read with `elasticore.crypto.keys.v1` and every write uses the
  current `elasticore.crypto.key` (`elasticore.crypto.key-version`). JPA: the entity field gets
  `@Convert(converter = EncryptConverter.class)` - the same line a project without the generator names in env.yml
  (`@entity:jakarta.persistence.Convert(...)`). MyBatis: the mapper binds and reads the column through
  `EncryptTypeHandler`. The column is `ceil((n * 4 + 28) / 3) * 4 + 3` long (147 for `string(20)`) in the entity,
  the DDL, Flyway and schema.yml; the DTO still checks n. An `@encrypt` field searched by equality (`eq`, `=`, a bare
  `@search`) gets a companion field the model writes, `phoneBidx` / `phone_bidx` (22 characters, indexed): HMAC-SHA256
  with `elasticore.crypto.index-key` over the trimmed value, a value of digits and `-`, ` `, `.` kept to its digits
  (`Bidx.normalize`), 16 bytes in base64url. The entity's `indexEncrypted()` writes it, the CoreService calls it before
  every write and after a lifecycle command (as `cleanHtml()`), and the search compares it with `Bidx.of(value)` - JPA
  (Java, Kotlin) and MyBatis (`<bind>` of `@...Bidx@of`). No DTO, search DTO or TypeScript model carries it. The
  auto-configuration `ElastiCoreCryptoAutoConfiguration` installs the keys before any bean is made; a missing
  `elasticore.crypto.key` stops the start only where an encrypted field is mapped (Hibernate makes the converter at
  start - checked; MyBatis is expected to make the handler as it reads the mapper XML, not checked by a test: at worst
  the first read or write says the missing property), a malformed key stops any start, both named in the message.
- Refused (the build stops, each with its message): a search other than equality on an encrypted column (like, a
  prefix or a suffix, `>`/`>=`/`<`/`<=`, between, in, `!=`, the ignore-case conditions, `words:`) - on the entity, and
  on a declared search DTO, which is also refused when the entity field has no `@search(eq)` index; `@encrypt` on a
  field that is not a single string, on a key, with `@unique`, with `@formula`, on a value object; a searched encrypted
  field of an aggregate member; mode: mongo; the 2.x-compatible output; and the 2.x generator (a DSL warning, as for
  `html`). Not done: existing plaintext rows (a one-off migration, steps in the annotation guide); sorting by an
  encrypted column sorts its ciphertext; a new index key needs every `_bidx` column rewritten.
- 2.x: a model without `@encrypt` generates as before (`elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED,
  `elcoreTreeCheck` 0 / 0 / 0).
- Checked: `FieldCryptoTest` (round trip, a random IV, a tampered or foreign text refused, rotation v1 -> v2, a
  missing or malformed key named), `BidxTest` (`010-1234-5678` == `01012345678`, 22 characters, the index key's own),
  `EncryptConverterTest` (the converter, the type handler over H2, the auto-configuration: no key with an encrypted
  field fails the start, without one starts), `EncryptConverterHibernateTest` (Hibernate under Spring's bean container
  makes the converter at start: no key, no start; with keys `v1:` in the row) - in both runtimes;
  `EncryptFieldTest` (JPA layered and hexagonal, Kotlin, MyBatis layered and hexagonal, TypeScript, the DDL and
  schema.yml, each refusal), `EncryptFieldMarkTest` (the 2.x generator stops), `EnvAnnotationAliasTest` (the env.yml
  converter on the entity, not the DTO); over HTTP with the database read by JDBC: `elasticore-template-ddd`
  `BoardEncryptTest` (Subscriber), `elasticore-template-kt` `CommentEncryptTest` (Comment.authorPhone),
  `elasticore-template-mybatis` `ShopApi.anEncryptedColumnHoldsCiphertextAndIsFoundByItsIndex` (Gadget.serialNo) -
  ciphertext in the column, the value in the API, the same value stored differently, a search with and without
  dashes, an update re-indexed. The samples' test keys are made per run (`TestCryptoKeys`); none is in the repository.
- Files: `EncryptRules.java` (new), `SpecFactory.prepareEncrypt`, `RulesGenRunner`, `RuleCatalog` (`jpa-encrypt` in
  every bundle), `JpaEntityBaseRules`, `MapperSpecRules`, `MyBatisSearchRules`, `MyBatisMapperRules`, `MyBatisSql`,
  `MyBatisTables`, `DddCommandRules`, `JpaSchemaReader`, `SchemaCodePublisher`, `AbstractModelLoader`;
  `elasticore-springboot3/.../crypto/` (`FieldCrypto`, `Bidx`, `EncryptConverter`, `EncryptTypeHandler`),
  `ElastiCoreCryptoAutoConfiguration` and its AutoConfiguration.imports line; the annotation, DSL, env and rule guides
  (en, ko); the three samples' blueprints, schemas and tests.
