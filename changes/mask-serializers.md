### New Features (runtime - masking personal data in responses)

#### `@Mask(MaskType.PHONE)`: a response field written masked, named in env.yml as any other annotation
- Why: projects mask phone numbers, names and plates in their responses (`telNumber: string(20) @env:maskphone`), and
  each wrote its own Jackson serializer for it - one for Jackson 2 and, on Spring Boot 4, another for Jackson 3.
- Now: the runtime has `io.elasticore.springboot3.mask.Mask` and `MaskType` (`PHONE`, `EMAIL`, `NAME`, `PLATE`, `CARD`,
  `RRN`), and a Jackson module its auto-configuration registers (`ElastiCoreMaskAutoConfiguration`; Jackson 2 in
  `elasticore-springboot3`, Jackson 3 in `elasticore-springboot4`, the same class name `MaskJackson`). An env.yml
  annotation `maskphone: "@dto:io.elasticore.springboot3.mask.Mask(io.elasticore.springboot3.mask.MaskType.PHONE)"`
  puts it on the DTO field; the entity keeps the value. Only the response is masked; a request is read as sent. The
  module acts only on fields that carry `@Mask`: an application without one writes the same JSON. No DSL word and
  no generated code changed.
- Checked: `MaskTypeTest` (each type, separators kept, a Seoul number `02-123-4567` -> `02-***-4567`) in both
  runtimes; `MaskJacksonTest` (Spring Boot 3's auto-configured ObjectMapper) and `MaskJackson3Test` (Spring Boot 4's
  JsonMapper) mask the marked fields, leave the others and read a request unchanged; `EnvAnnotationAliasTest` - the
  env.yml alias reaches the v3 DTO field and not the entity.
- Files: `elasticore-springboot3/src/main/java/io/elasticore/springboot3/mask/`, `.../jackson2/.../MaskJackson.java`,
  `elasticore-springboot4/src/jackson3/.../MaskJackson.java`, `ElastiCoreMaskAutoConfiguration.java`, the env guide
  (en, ko) section 4.
