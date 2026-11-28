### Features (v3 controller - `GET /schema/info` from the model)

#### `config: schemaInfo: model` - `/schema/info` says what the caller may do (`actions`)
- Why (issue 373, fourth and last step): a screen that cannot import the TypeScript client decided which buttons to show
  with a hand-written role table that mirrored `@access`; the answer from the model said nothing about the caller.
- Now, under the same setting (no new one), each entity's answer ends with `actions`: `read`, `create`, `update`,
  `delete` and each command (ddd, hexagonal), those the controller serves, for the signed-in user of the request.
  - A value is `true`, `false`, or the conditions a row must meet: `["owner"]` (a row of the caller's own, `@owner`),
    `["state"]` (a state the lifecycle allows: a command `states:` moves, a `states: delete:`), or `["owner", "state"]`.
  - It is decided per request by the check the service applies: `AccessPolicy.parse` of the service's own arguments
    (`ServiceAccessRules.policyArguments`: the declaration and `adminRole`), `require`, then `owns` on a row that is not
    the caller's and on one that is, for the `CurrentUser` the service takes - the application's bean where it has one,
    else `SessionCurrentUser` (the only one, the primary one, or among several the one named `currentUser`, as the
    service's `@Autowired` setter picks it). `create` is never narrowed to the owner, as the service does not; an entity
    keyed by its owner (`@owner` on a key the request gives) refuses a visitor's `create`, as the service does. Where the
    controller deletes only by `DELETE /{key}`, which reads the row first, `delete` is both checks, `read` and `delete`.
  - The generator records only the facts (`SchemaActions`, written into `<domain>-schema.json` as `actions`): the
    operations the controller serves (`read`: GET /{key}, POST /search or /export; `create`; `update`; `delete`; the
    commands where `ddd-command-control` wrote them), whether the service checks anything (`service-access` ran and the
    entity is guarded - otherwise `policy: null` and everything is `true`), the `@owner` identity the service compares
    (none: owner-level operations are the administrator's), `createNeedsSignIn`, `deleteReadsFirst` (`DELETE /{key}`
    without `POST /delete`), and the operations a row's state decides
    (`ddd-state-moves`, `ddd-state-delete` as they ran on the entity). An entity without `@access` but with `@owner` or a
    `@password` has the service's policy without a declaration: everything public, as `AccessPolicy` reads it.
  - Every mode checks the same way: JPA, MyBatis and MongoDB, Java and Kotlin, layered, ddd and hexagonal services all
    call the one `ServiceAccessRules` check, and the commands' `accessWrite`. Layered has no commands and no lifecycle
    moves, so it says neither.
  - The answer stays `Cache-Control: private` and has no access gate of its own (the owner's decision); a visitor is told
    `false`, not refused.
- Before and after: without the setting nothing changes; with it, the schema file gains `actions` and the answer
  `actions`. The controller's line is the same as in step 2. Every sample generates the same files as before except the
  pet store's `petstore-schema.json`.
- Checked: the pet store's `SchemaInfoTest` over HTTP (Boot 4). A visitor, a plain customer, a MANAGER and the
  administrator get different `actions` for PurchaseOrder (`read=owner|MANAGER, create=user, cancel=owner,
  delete=MANAGER&owner`, a lifecycle) and Customer, and the service agrees with each:
  - a visitor's `create` of an order is 401, and a sign-up (`create: true`) is 200;
  - the customer's `ship` is 403, and `cancel` (`["owner", "state"]`) is 200 while PLACED and 409 after;
  - the MANAGER reads the customer's order (`read: true`), and `ship` is 403;
  - the administrator's `ship` (`["state"]`) is 409 on a cancelled order and 200 on a placed one.
  Generated and compiled with the setting, temporarily: `-ddd` (hexagonal JPA), `-kt` (Kotlin hexagonal), `-mongo`,
  `-ddd-mybatis`, `-kt-mongo`, the MyBatis pet store (layered). Kotlin's `Ticket` file matches its generated controller,
  base and service. In-process tests:
  - `SchemaActionsTest`: what is served, the batches, export, commands, the state, the service's facts, the owner, a
    create keyed by its owner, a delete that reads first;
  - `SchemaInfoGenerationTest`: JPA, MyBatis and MongoDB; Java and Kotlin; layered, ddd and hexagonal; `adminRole`;
  - runtime `SchemaInfoTest`: every caller, `none`, roles, `adminRole`, the system, no policy, no owner, a null id, the
    session's and the application's `CurrentUser`.
  Without the setting every sample generates the same files as before. Suite results: `elasticore-base` 2103 tests
  (3 skipped), `-springboot3` 479 (10 skipped), `-springboot4` 480 (10 skipped), the pet store 206. Other gates:
  `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED; `elcoreTreeCheck` 0 differ / 0 missing / 0 extra;
  `diffCoverage` 98.9% of 274 changed lines; `diffMutation` 97.6% of 125 mutations.
- Files: `rules/SchemaActions.java` (new), `rules/SchemaCatalog.java`, `rules/verify/RulesGenRunner.java`,
  `rules/base/ServiceAccessRules.java` (`comparedOwner`, `createNeedsSignIn`, `SERVICE_RULE`),
  `rules/ddd/DddCommandRules.java` (`serves`, `CONTROL_RULE`), `rules/ddd/DddStateRules.java` (`MOVES_RULE`);
  elasticore-springboot3 `schema/SchemaInfo.java`; guide `elasticore-env-guide.md` 6-6, en and ko; tests
  `SchemaActionsTest` (new), `SchemaInfoGenerationTest`, runtime `SchemaInfoTest` and its `desk-schema.json`, the pet
  store's `SchemaInfoTest`.
