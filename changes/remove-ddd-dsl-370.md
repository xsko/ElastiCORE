### Removals (ddd / hexagonal - `domainService:`, `processManager:`, `@specification`, issue 370)

#### `domainService:`, `processManager:` and `@specification` are no longer generated; a project that still has them builds
- Why: phase 2 of issue 370 (3.0 deprecated them). Each generated little more than a few lines of plain Spring code - an
  empty `@Service` over ports injected as fields, a listener with a fixed `AFTER_COMMIT` + `REQUIRES_NEW` calling an abstract
  `handle`, a one-line `Predicate` - and each cost DSL to learn, guides in two languages, and rules and templates for Java,
  Kotlin and TypeScript.
- Before: `domainService:` generated `XxxDomainServiceBase` + `XxxDomainService`, `processManager:` generated
  `XxxProcessManagerBase` + `XxxProcessManager` (and checked an event of another context, `on: [shop:OrderPlaced]`, against
  `config.context`), `@specification` generated `<Entity>Specifications` (Java, Kotlin) and `<Entity>Specifications.ts`.
- After: none of them is generated, in Java, Kotlin (JPA and MyBatis, ddd and hexagonal) or TypeScript. An existing project:
  - The load does not fail and the build does not break on a leftover section or annotation. The server's run prints one
    notice per declaration under `[elcore3] DSL no longer read since 3.1 - remove it (a notice: -Pstrict does not count it):`,
    for example `[shop] domainService Pricing is no longer read since 3.1 - remove it; write a @Service class in the
    domain.service package that takes the repository ports of Order, Product in its constructor.`, `[shop] processManager
    Loyalty is no longer read since 3.1 - remove it; write a @Component with a method for each event of 'on' (OrderPlaced)
    annotated @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true) and @Transactional(propagation =
    REQUIRES_NEW), or a plain @EventListener when it must run in the caller's transaction.` and `[shop] Order
    @specification(highValue) is no longer read since 3.1 - remove it; write a static Predicate<Order> highValue() method in
    a class of your own, plus the TypeScript function if screens need it.` The notices are stored as `DSL_NOTICE`, not
    `DSL_WARN`, so `elcoreValidate -Pstrict` does not fail on them; `validate.md` lists them under "DSL no longer read since
    3.1". The old checks of the sections (an empty `uses`, an event nobody declares) are gone with them - one message only.
    A section of any other shape (a list, a scalar, an empty map) is one notice for the section; the load never fails on it.
    A client-only run (`elcore3ts` alone) says them too; with `typescriptClient = true` the plugin tells the client's run
    that the server's run before it has said them (`elasticore.rules.tsWithBuild`), so they are said once.
  - A selection that still names the rule sets `ddd-specification` or `ddd-domain-service` (`publisher.rules`) runs without
    them, with a notice, instead of stopping on "Unknown rule set".
  - What 3.0 generated for them is handled by the stale-file cleanup as before: a class the project's code still uses or
    extends stays ("... is no longer generated, but X still uses it - kept"), an edited one is the project's, an unused,
    unedited one is removed. So `PricingDomainService extends PricingDomainServiceBase` keeps compiling, and an edited
    process manager keeps its base and its listener. A project that turned the merge off (`merge = false`) now asks the same
    question for a stale `<Entity>Specifications.java` / `.kt` that 3.0's `@specification` wrote (before, a stamped stale
    file was removed even when used); the other stale files going in the same run are no users of it, and a class of another
    kind with such a name (an entity `ProductSpecifications`) goes with its files as before.
  - `<Entity>Specifications.ts` is handed over to the project instead of removed - the cleanup reads only Java and Kotlin
    for users, and screens import it: `[elcore3] src/main/ts/OrderSpecifications.ts is no longer generated (@specification
    was removed in 3.1) - it is yours now: kept as it is, and ElastiCORE no longer updates or removes it.` It leaves the
    merge cache (a stamped copy gets the "no longer managed" line), edited or not. Only a file of that name with the header
    3.0 wrote is handed over, whatever JVM class shares its name; every other stale file goes as before.
  - The warning a developer's `@behavior` class gets when it does not extend its regenerated `XxxBase` no longer speaks of
    `on`, `uses` and `handle` (the process manager's words): `... does not extend XxxBase, which is regenerated from the
    DSL - so a change to the DSL does not reach it. Make it extend XxxBase (Kotlin: ': XxxBase()'), or delete the file to have
    it generated anew.`
  - `namespace.domainService` stays: the architecture test reads it as a domain-layer package, so a hand-written class in
    `domain.service` is checked as domain code. `namespace.processManager` is read by nothing now and is ignored silently.
  - The context map no longer draws or checks an event crossing (it came only from `processManager:`); a context may still
    publish its events.
- The samples use plain Spring code: `elasticore-template-ddd` and `elasticore-template-ddd-mybatis` have `PricingService`
  (`shop.domain.service`), `LoyaltyListener` (`shop.application`), billing's `ReceiptingListener` and `OrderRules`
  (`highValue()`, `hasLines()`); `elasticore-template-kt` has `PricingService`, `LoyaltyListener`, `OrderRules` and the
  TypeScript `check/orderRules.ts`.
- Guides (English and Korean): the annotation guide's `domainService` and `@specification` sections, the DSL guide's chapter
  8, and the rule guide's "Specification", "Process manager" and "Domain service" are each a short "Removed in 3.1" note
  with the hand-written replacement; the rule tables, the env guide, the port and migration guides, the ddd-customize
  target table and `docs/tour` no longer describe them.
- Removed: `DddDomainServiceRules`, `DddSpecificationRules`, the `domainservice` model (`DomainService`,
  `DomainServiceModels`), `DomainSpec.DomainServiceSpec` and the foreign-event lookup, the eleven templates
  (`v3/`, `kt/`: `domainService`, `domainServiceBase`, `processManager`, `processManagerBase`, `specifications`;
  `ts/specifications`), the context map's event references, and `ProcessManagerRulesTest`,
  `CrossContextGenerationTest`, `DomainServiceRegenerationTest`.
- Checked: `RemovedDslTest` (replaces `DeprecatedDslTest`) - a hexagonal project that still declares a domain service, two
  process managers (one naming an event of a context that does not exist) and two `@specification`s builds, prints each
  notice once and no DSL warning, generates none of the removed files, and passes `elcoreValidate -Pstrict`; a selection
  naming `ddd-specification` / `ddd-domain-service` runs with a notice; the files 3.0 wrote (in the merge cache, and
  stamped with `merge = false`) stay while used or extended, an edited one stays as the developer's with its base, the
  unused ones go, and go later once nothing uses them; `OrderSpecifications.ts` is handed over in both modes while another
  stale `.ts` file is removed, and an edited copy is handed over too; an entity named `ProductSpecifications` removed from a
  stamped layered project goes in one run with its repository, service and enum (with the stamped question asked of every
  `*Specifications` class and the going files counted as users, it was kept - checked by running the test against that
  code); a Kotlin, a MyBatis and a client-only run still declaring them are told; a list, a scalar, a null, an empty map and
  a scalar entry as the section load. `ContextMapTest` (a leftover `processManager:` crosses nothing), `ConditionComparisonTest`
  and `TypeScriptModelTest` (the condition grammar through `@invariant` only), the guide tests. Upgrades run for real:
  `elasticore-template-ddd` and `elasticore-template-kt` generated by 3.0 (`72454094`), then by this code without the
  sample's clean step - the unused classes were removed, an edited `LoyaltyProcessManager` / Kotlin `PricingDomainService`
  kept itself and its base, `OrderSpecifications.java` stayed while a class of the project used it and went on the next
  run after that class was deleted, `OrderSpecifications.ts` was handed over, and the samples compiled (`tsc` included)
  and their tests passed in that state; with the sections put back, the ddd sample printed the five notices.
  `elasticore-base` tests: 1983, 0 failed. `elasticore-template-ddd` 332 tests, `elasticore-template-ddd-mybatis` 161,
  `elasticore-template-kt` 240 and its TypeScript tests (15) - all passed. `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF /
  2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `RemovedDsl.java` (was `DeprecatedDsl.java`), `RemovedDeclaration.java` (new), `DomainLevelModelLoader.java` (was
  `DomainServiceModelLoader.java`), `ECoreModel.java`, `ModelLoaderContext.java`, the three model loaders, `RuleCatalog.java`,
  `DomainSpec.java`, `SpecFactory.java`, `ContextMap.java`, `RulesGenRunner.java`; the removed rules, model, templates and
  tests above; `RemovedDslTest.java`, `ContextMapTest.java`, `ConditionComparisonTest.java`, `TypeScriptModelTest.java`,
  `ExtensionPointTest.java`, `GuideCoverageTest.java`, `DddCondition.java` (Javadoc), `ElastiCorePlugin.java` and
  `ElastiCorePluginTest.java` (`tsWithBuild` on the client's run); the three samples; the annotation, DSL, rule, env, port,
  migration and ddd-customize guides with their `ko/` copies; `docs/tour.md`, `docs/tour.ko.md`.
