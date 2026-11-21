### Bug Fixes (v3 service - a masked field sent back unchanged)

#### An update keeps a `@Mask` field's stored value when the text sent is that value masked
- Why (issue 270): `@Mask(MaskType.X)` writes a response field masked, and a request was read as sent. A client that
  reads a row and sends it back - an edit form, a `PUT` of the read DTO - sent `010-****-5678` for a phone it never
  saw, and the update wrote that text over the number for good. The env guide only told the client not to.
- Now: the 3.0 code's update (`PUT` and `PUT /batch`, which goes through it) compares a masked field's text with the
  stored value masked by the runtime's own `MaskType.apply` - what the response showed - and when they are the same,
  puts the stored value back on the DTO before the mapper writes it. With `@encrypt` the stored value is the one the
  persistence layer decrypted (the JPA converter, the MyBatis type handler). Any other text is written as before; a
  create writes what it is sent. In the CoreService of JPA (Java and Kotlin), MyBatis (layered, and the DDD/hexagonal
  port service) and MongoDB (Java and Kotlin), in every architecture.
- Which fields: the mask is not a DSL word. A DTO field whose `@dto:` annotation names the class
  `io.elasticore.springboot3.mask.Mask` - or the name `elasticore { runtimePackages }` renames it to, when the
  annotation is written with the project's package - with a `MaskType` constant, on a text field the entity stores
  under the same name. The comparison names the `MaskType` of the annotation's package, so it is renamed with the
  annotation. A `@Mask` whose argument is not a `MaskType` constant is a DSL warning, and its field is written as sent;
  a `@readonly` field is not touched (the mapper never writes it).
- Unchanged: a model without a masked field generates the same code (no line, no comment); the 2.x-compatible output;
  an update by condition (`updateBySpecification`) and code that calls the mapper itself write what they are given;
  `except=owner|admin` stays with issue 178.
- Checked: `MaskRoundTripTest` - the line in the update of JPA Java and Kotlin, hexagonal JPA, MyBatis layered and
  hexagonal, MongoDB Java and Kotlin, after the not-found check and before the mapper, not in the create; another
  `@dto:` annotation, another class named `Mask` and a `@readonly` field left out; the warning; `runtimePackages` with
  the annotation in the runtime's package and in the project's; no line without a masked field. Samples, generated,
  compiled and run: `elasticore-template-ddd` `BoardEncryptTest` (hexagonal JPA, `Subscriber.email` `@encrypt` and
  `@Mask(EMAIL)`: the read DTO sent to the service's update and batch update keeps the ciphertext; with the line taken
  out the same test fails - the root refuses the masked text as a change of a sealed field); `elasticore-template-kt`
  `CommentEncryptTest` (Kotlin JPA over HTTP, `authorPhone` `@encrypt(digits)` and `@Mask(PHONE)`: `PUT` and
  `PUT /batch` of the read JSON keep the phone and its blind index, a new phone is written, masked text of the old
  phone is written as sent); `elasticore-template-mybatis` `ShopApi` (MyBatis over HTTP on H2, `Gadget.serialNo`
  `@encrypt(digits)` and `@Mask(CARD)`, the same four cases); `elasticore-template-mongo` `MaskedUpdateTest` (the
  generated MongoDB service with a mocked repository) and `ShopApi` / `elasticore-template-kt-mongo` `ShopApi` over
  HTTP (`Customer.email` `@Mask(EMAIL)`; run where Docker or `ELASTICORE_IT_MONGODB_URI` is, skipped locally; both
  compiled). The seven sample modules, generated before and after the change without their new masked fields, were
  byte-identical. `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing
  / 0 extra; the full test suites of `elasticore-template-ddd`, `-kt` and `-mybatis`.
- Files: `MaskedFields` (new), `JpaServiceFileRules`, `MyBatisServiceFileRules`, `MongoServiceFileRules`, the
  templates `v3/service`, `kt/service`, `v3/mongo_service`, `kt/mongo_service`, `v3/mybatis_service`,
  `v3/mybatis_port_service` (`${maskKeep}`), `MaskRoundTripTest`, the env guide (en, ko), the samples above (blueprint
  and tests).
