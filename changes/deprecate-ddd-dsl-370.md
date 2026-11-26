### Deprecations (ddd / hexagonal - `domainService:`, `processManager:`, `@specification`, issue 370)

#### `domainService:`, `processManager:` and `@specification` are deprecated; 3.1 removes them
- Why: each generates little more than a few lines of plain Spring code - an empty `@Service` over ports injected as fields,
  a listener with fixed `AFTER_COMMIT` + `REQUIRES_NEW` that calls an abstract `handle`, a one-line `Predicate` - and each
  costs DSL to learn, guides in two languages, rules and templates for Java, Kotlin and TypeScript. The fixed "after commit,
  in a new transaction" is also not always what an application needs. This is phase 1 of the issue: 3.0 says so, 3.1
  stops generating them.
- Nothing generated changes: a project that uses them generates byte for byte what it did before.
- After: the server's run (Java or Kotlin, not the TypeScript one) prints one notice per declaration, with the domain and
  the name, under its own heading after the DSL warnings:
  `[elcore3] Deprecated DSL - still generated, removed in 3.1 (a notice: -Pstrict does not count it):`
  - `[shop] domainService Pricing is deprecated and is removed in 3.1 (it is still generated as before): write a @Service
    class in the domain.service package that takes the repository ports of Order, Product in its constructor.`
  - `[shop] processManager Loyalty is deprecated ...: write a @Component with a method for each event of 'on' (OrderPlaced)
    annotated @TransactionalEventListener(phase = AFTER_COMMIT) and @Transactional(propagation = REQUIRES_NEW), or a plain
    @EventListener when it must run in the caller's transaction.`
  - `[shop] Order @specification(highValue) is deprecated ...: write a static Predicate<Order> highValue() method of your
    own, plus the TypeScript function if screens need it.` - one per `@specification`.
- A notice, not a DSL warning: it is stored under its own key (`DSL_NOTICE`), not `DSL_WARN`, which `elcoreValidate -Pstrict`
  counts - a project that passes today keeps passing. `elcoreValidate` prints the notices and lists them in
  `build/elasticore/validate.md` ("Deprecated DSL - still generated, removed in 3.1 (not a failure; -Pstrict does not count
  it)"). The `@restExclude` deprecation is a `DSL_WARN` and still fails `-Pstrict`;
  it is left as it is.
- Guides (English and Korean): the `domainService` and `@specification` sections of the annotation guide, chapter 8 and its
  process manager section in the DSL guide, "Specification", "Process manager" and "Domain service" in the rule guide say
  "Deprecated - removed in 3.1", with the hand-written replacement: a `@Service` with the ports in its constructor, a
  `@Component` listener with the two annotations (or `@EventListener` for the caller's transaction), a static
  `Predicate<Order>` and its TypeScript function with `Number(...)`. The rule guide's rule tables, the env guide's
  `namespace.domainService` and the ddd-customize guide's target table mark them too. The existing documentation stays:
  3.0 projects still use them.
- Checked: `DeprecatedDslTest` (new) - a hexagonal project with a domain service, a process manager and two
  `@specification`s prints each notice exactly once, still generates `PricingDomainServiceBase`, `LoyaltyProcessManagerBase`
  and `OrderSpecifications`, and passes `elcoreValidate -Pstrict` with "DSL warnings: 0" and the notices listed; a project
  without them prints none. With the notices stored as `DSL_WARN` the strict validation fails ("4 DSL warning(s)
  (-Pstrict)") - the test fails without the separate key. `elasticore-template-ddd` generated before and after the change:
  the same 511 files, byte for byte, and the five notices of its shop and billing domains. The replacement classes compile
  in `elasticore-template-ddd` (`PricingService`, a `@TransactionalEventListener` listener of `OrderPlacedEvent`,
  `OrderRules`; not committed). `ValidateTest`, `DomainServiceRegenerationTest`, the guide tests (`--tests "*Guide*"`),
  `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 / 0 / 0.
- Files: `DeprecatedDsl.java` (new), `RulesGenRunner.java`, `ConsoleLog.java` (`storedLogs`), `DeprecatedDslTest.java`
  (new), `elasticore-annotation-guide.md`, `elasticore-dsl-guide.md`, `elasticore-rule-guide.md`, `elasticore-env-guide.md`,
  `elasticore-ddd-customize-guide.md` and their `ko/` copies.
