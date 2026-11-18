### New Features (v3 entity and runtime - `@encrypt`)

#### `@encrypt`: a column kept encrypted (AES-256-GCM), and with `@search(eq)` found by a blind index
- Why: projects kept personal data encrypted with a converter of their own (`phone: string(20) @env:encrypt
  @env:maskphone`), and then could not search it: the column holds a different text for the same value each time,
  so `@s(eq)` found nothing.
- Now: `phone: string(20) @encrypt @s(eq)`. The runtime (`io.elasticore.springboot3.crypto`, both runtimes) encrypts
  with AES-256-GCM and a random 12-byte IV per write and stores `v1:` + base64(IV, ciphertext, tag), the version bound
  to it as associated data; the prefix names the key (v1 to v9), so after a rotation old rows are read with
  `elasticore.crypto.keys.v1` and every write uses the current `elasticore.crypto.key` (`elasticore.crypto.key-version`).
  JPA: the entity field gets `@Convert(converter = EncryptConverter.class)` - the same line a project without the
  generator names in env.yml (`@entity:jakarta.persistence.Convert(...)`). MyBatis: the mapper binds and reads the
  column through `EncryptTypeHandler`. The column is `ceil((n * 4 + 28) / 3) * 4 + 3` long (147 for `string(20)`) in
  the entity, the DDL, Flyway and schema.yml, and Oracle's large-column check reads that length; the DTO still checks
  n. An `@encrypt` field searched by equality (`eq`, `=`, a bare `@search`, any case) gets a companion field the model
  writes, `phoneBidx` / `phone_bidx` (22 characters, indexed - on the child's table for a field of a plain `@abstract`
  parent): HMAC-SHA256 with `elasticore.crypto.index-key` over the trimmed value, 16 bytes in base64url. Values that
  differ stay apart; `@encrypt(digits)` opts in to dropping the `-`, ` `, `.` of a number (`Bidx.ofDigits`), so
  `010-1234-5678` finds `01012345678`. The entity's `indexEncrypted()` writes the index just before the row is
  written: under JPA the runtime's `BlindIndexListener` (`@EntityListeners`, `@PrePersist`/`@PreUpdate`), after
  `beforeSave`, the derived values, a lifecycle command and any code that set the field; under the DDD rules over
  MyBatis the root's `Lifecycle` (`beforeInsert`/`beforeUpdate`); the layered MyBatis service after `beforeSave`,
  before its insert and in `write()`. The search compares it with `Bidx.of(value)` - JPA (Java, Kotlin) and MyBatis
  (`<bind>` of `@...Bidx@of`). No DTO, search DTO, TypeScript model or generated service-test fixture carries it, and
  the Ossie model leaves out the encrypted column and its index, as it leaves out a password. The auto-configuration
  `ElastiCoreCryptoAutoConfiguration` installs the keys before any bean is made; an application whose converter or
  type handler was made does not start without both keys, nor with a malformed key or an index key equal to the key,
  each named in the message.
- Refused (the build stops, each with its message): a search other than equality on an encrypted column (like, a
  prefix or a suffix, `>`/`>=`/`<`/`<=`, between, in, `!=`, the ignore-case conditions, `words:`) - on the entity, and
  on a declared search DTO, which is also refused when the entity field has no `@search(eq)` index; a search through a
  relation that reaches an encrypted field (`@ref(customer.phone)`, `words: customer.phone`); `@encrypt` on a field
  that is not a single string, on a key, with `@unique`, with `@formula`, on a value object, or with a value other than
  `digits`; a searched encrypted field of an aggregate member; in Kotlin, searched encrypted fields both in a class and
  in its parent; mode: mongo; the 2.x-compatible output; and the 2.x generator (a DSL warning, as for `html`). Not
  done: existing plaintext rows (a one-off migration, steps in the annotation guide); sorting by an encrypted column
  sorts its ciphertext; a new index key needs every `_bidx` column rewritten; the converter has no table or column, so
  a ciphertext copied into another row decrypts there (documented).
- 2.x: a model without `@encrypt` generates as before (`elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED,
  `elcoreTreeCheck` 0 / 0 / 0).
- Checked: `FieldCryptoTest` (round trip, a random IV, a tampered, relabelled or foreign text refused, rotation v1 ->
  v2, one-digit versions, both keys required, the index key not the key), `BidxTest` (`123-45-678` != `12-345-678`,
  `10.11.1.1` != `101.1.1.1`, `1.5` != `15` by default; `010-1234-5678` == `01012345678` with digits),
  `EncryptConverterTest` (the converter, the type handler over H2, the auto-configuration: no key or no index key with
  an encrypted field fails the start, without one starts), `EncryptConverterHibernateTest` (under Spring's bean
  container Hibernate makes the converter at start: no key, no start; the listener indexes a persist and an update,
  `@DynamicUpdate` leaves an untouched encrypted column as it was) - in both runtimes; `EncryptFieldTest` (JPA layered
  and hexagonal, Kotlin, MyBatis layered and hexagonal, TypeScript, digits, the DDL, an inherited index, schema.yml,
  each refusal), `EncryptFieldMarkTest` (the 2.x generator stops), `EnvAnnotationAliasTest` (the env.yml converter on
  the entity, not the DTO); over HTTP with the database read by JDBC: `elasticore-template-ddd` `BoardEncryptTest`
  (Subscriber; the move command re-indexes), `elasticore-template-kt` `CommentEncryptTest` (Comment.authorPhone; a
  partial update keeps the index, PUT /batch and an update by condition re-index), `elasticore-template-mybatis`
  `ShopApi.anEncryptedColumnHoldsCiphertextAndIsFoundByItsIndex` (Gadget.serialNo; update and PUT /batch) - ciphertext
  in the column, the value in the API, the same value stored differently, a search with and without dashes. The
  samples' keys are made per test run (`TestCryptoKeys`); none is in the repository, and the samples have no
  application of their own to start (their READMEs say so).
- Files: `EncryptRules.java` (new), `SpecFactory.prepareEncrypt`, `RulesGenRunner`, `RuleCatalog` (`jpa-encrypt` in
  every bundle), `JpaEntityBaseRules`, `MapperSpecRules`, `ServiceTestBaseRules`, `MyBatisSearchRules`,
  `MyBatisMapperRules`, `MyBatisServiceFileRules` and `v3/mybatis_service.tmpl` (`encryptBeforeWrite`),
  `MyBatisDddRules`, `MyBatisSql`, `MyBatisTables`, `JpaSchemaReader`, `SchemaCodePublisher`,
  `OssieSemanticModelExporter`, `AbstractModelLoader`; `elasticore-springboot3/.../crypto/` (`FieldCrypto`, `Bidx`,
  `EncryptConverter`, `EncryptTypeHandler`, `BlindIndexListener`), `ElastiCoreCryptoAutoConfiguration` and its
  AutoConfiguration.imports line; the annotation, DSL, env and rule guides (en, ko); the three samples' blueprints,
  schemas, READMEs and tests.
