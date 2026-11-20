### Bug Fixes (gRPC ports - the Kotlin client, and the build setup)

#### The Kotlin gRPC client escapes its Spring placeholders, and the port guide says what a gRPC build needs
- Why: with `language = 'kotlin'`, a `meta: proto @client` port wrote
  `@Value("${grpc.syncGrpcService.ip:localhost}")` into `SyncGrpcServiceClient.kt`. Kotlin reads `"${...}"` as its own
  string template, so the file did not parse. The template modules exclude `**/grpc/**` from compilation (they run no
  protoc), so no build of this repository compiled it. The Java client also needs the protobuf Gradle plugin and the gRPC
  libraries, which the port guide did not mention.
- Now: `GrpcStubRules` writes the placeholder as Kotlin spells it, `@Value("\${grpc.syncGrpcService.ip:localhost}")`,
  as `DddOutboxRules` already did for the outbox relay's. The Java client is written as before, byte for byte. The port
  guide has a "Build setup for gRPC" section: the protobuf plugin 0.9.4, protoc 3.25.1, grpc 1.60.0, `generateProto`
  after `elcore3` (which writes `src/main/proto`), the client's two properties, and what is not generated (a runtime
  transport, a server for the provider).
- Other Kotlin output: every `"${` of the `kt/` templates is a slot filled with a name, and the outbox relay's
  placeholders were escaped already. The 2.x generator writes the gRPC stubs in Java only (`ProtoSrcPublisher`), so it
  is unchanged.
- Checked: `KotlinSpringPlaceholderTest` - the Kotlin client test and a scan of every generated `.kt` of a hexagonal
  Kotlin domain with a proto port for an unescaped `${name:default}` failed before the fix (`SyncGrpcServiceClient.kt:
  ${grpc.syncGrpcService.ip:localhost}`) and pass after it; the Java client is checked unchanged. The audit's Kotlin
  repro (layered, Boot 4) with the guide's block added runs `generateProto` and `compileKotlin` successfully, and the
  Java repro (hexagonal, Boot 4) with the same block runs `compileJava` successfully. `elcoreRulesCheck -Pstrict`,
  `elcoreTreeCheck`, `GuideTranslationTest`.
- Files: `GrpcStubRules`, `KotlinSpringPlaceholderTest`, port guide (en, ko).
