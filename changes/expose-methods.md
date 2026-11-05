### New Features (v3 controller - `@expose(methods)`)

#### `@expose(20, methods="read,search")` lists the endpoints an entity's controller serves
- Why: every `@expose` entity got the whole CRUD controller, and most roots need part of it - a
  receipt the application writes is only read over HTTP. Taking methods away with `@restExclude`
  named internal section ids (`batchUpdate`, `deleteBatch`, `deleteById`), listed what to drop
  rather than what to keep, and also took the methods off the service.
- After: `methods` names what the controller serves - `create`, `read`, `search`, `update`
  (`PUT /` and `PUT /batch`), `delete` (`DELETE /{id}` and `POST /delete`); the rest is not
  generated. `GET /schema/info`, commands and sign-in stay. The TypeScript client (`XxxApi.ts`)
  calls only those and imports only what its functions read (a client compiled with
  `noUnusedLocals` failed on the key DTO of an entity that deletes nothing); a client with nothing
  to call is not written. The use case (hexagonal, DDD, Java and Kotlin) drops the writes not
  served and keeps the reads. The service keeps every method. Without `methods` the server output
  is unchanged. The list is quoted: unquoted, the loader reads only its first name (an annotation
  with key=value parameters keeps no bare value), which serves fewer endpoints, not more.
- Changed by default (3.0 TypeScript clients, of a 3.0 or a 2.x server): a client whose server
  leaves endpoints out - `@readonly`, `@restExclude` - no longer imports what none of its functions
  read. The `@readonly` client imported the key DTO of a delete
  it does not have, which a client compiled with `noUnusedLocals` refused. A client with every
  endpoint is byte for byte what it was (the pet store's `webBuild` and the template-kt
  `elcore3tsVerify -Pstrict` pass).
- A name it does not know is a DSL warning and serves nothing (`methods="raed,search"` serves
  search) - a typo gives fewer endpoints, never more; `methods=""` serves none. A parameter of
  `@expose` other than `methods`, and a write listed beside `@readonly`, are DSL warnings too.
- `@restExclude` is deprecated: it still applies, and each entity using it gets a DSL warning naming
  the `methods` list with the same endpoints - or, where no list has them (`@restExclude(batchUpdate)`
  keeps `PUT /`; `schemaInfo` stays under every list), the nearest one, which serves fewer, and what
  it changes. It reached the guides in 3.0.261015-alpha1, so it is not removed. Names in `methods`
  and the key itself are read without regard to case.
- A generated controller kept as the developer's because the DSL's change does not merge (an
  endpoint that goes was edited) now gets a DSL warning naming each endpoint the generator wrote
  last, no longer writes, and the file still maps - `@expose(methods)`, `@readonly` and
  `@restExclude` alike. An endpoint the developer added is never named.
- 2.x output: unchanged (rulesCheck -Pstrict 157 MATCH / 0 DIFF / 2 ACCEPTED, treeCheck 0 differ /
  0 missing / 0 extra). The 2.x-compatible bundle does not read `methods`, and a TypeScript client of
  a 2.x server calls every endpoint that server has.
- Samples: `Receipt` of `elasticore-template-ddd` and `elasticore-template-ddd-mybatis` is
  `@expose(20, methods="read,search")` - billing's process manager writes receipts through the
  repository port, and the API reads them.
- Checked: `ExposeMethodsTest` generates Java and Kotlin hexagonal, Java layered, their TypeScript
  clients and a client of a 2.x server (also a `@readonly` client's imports, an inexact
  `@restExclude` suggestion, an unquoted list, capitals); `ControllerMethodRulesTest` (2.x skeleton ignores it, each
  name, the warnings); `GeneratedMergeTest.narrowingTheMethodsTakesTheEndpointsOutOrNamesWhatIsLeft`
  (never edited, edited elsewhere, edited in an endpoint that goes beside an endpoint of the
  developer's own); `ExposeMethodsApiTest` in `elasticore-template-ddd` over HTTP (read 200,
  search finds it, `DELETE /{id}` and `POST /delete` 405, the service still saves).
- Files: `ControllerBaseRules`, `EntitySpec` (`metaProperty`), `SpecFactory`, `TypeScriptApiRules`,
  `RulesGenRunner` (warnings), the annotation, rule, rule-dev and env guides (en and ko), the two
  billing blueprints.
