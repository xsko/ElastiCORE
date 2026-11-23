### Bug Fixes (v3 generation - the warning for rules the selection does not carry)

#### The "selected rules do not enforce" warning gives the advice that fits where the selection came from (#247)
- Why: a domain that declares `@access`, `@owner`, `@password`, `states:`, `@stamp`, `@deleted`, `@snapshot` or `@compute`
  while the selected rules do not carry the rule set for it is warned (`DSL_WARN`), and the warning always ended "Declare
  architecture = 'ddd' or 'hexagonal', or add the rule set.". A `publisher.rules` list in env.yml replaces the architecture's
  bundle, so for a hexagonal domain with `publisher.rules: [ossie-export]` - whose `@password` is then stored as typed and whose
  `@access` is not checked - declaring the architecture changes nothing; the 2.x code (`output = 'v2'`) refuses both an
  architecture and an added rule set. And the check read the selection without the build's property
  `-Delasticore.publisher.rules`, which replaces the bundle as the env.yml list does: a hexagonal build naming
  `ossie-export` there generated `@password` and `@access` unchecked without any warning.
- Now the check reads the selection the engine runs (the env.yml list, else the property, else the bundle, plus the rule sets
  the build adds), and the advice follows it, naming the missing rule set:
  - a list in env.yml (or the property, "The build's -Delasticore.publisher.rules ..."): "publisher.rules in env.yml replaces
    the architecture's bundle rather than adding to it, and its list does not name jpa-service-access. To add rule sets to
    the bundle, leave publisher.rules out and name them in rules = [...] in the elasticore { } block
    (-Delasticore.publisher.rules.add=...); to keep the list, name jpa-service-access in it." (`publisher.rules.kt` for a
    Kotlin domain that uses that key; for `ddd-state` and `ddd-derive` "... name a bundle that carries ddd-state with the
    domain-model rule sets it works on (hexagonal-springboot or ddd-springboot) in it", the domain's own ones - `-kt` in
    Kotlin, the MyBatis ones in mode: mybatis - from `RulesConfig.bundleFor`);
  - a list naming `jpa-springboot` (as the wizard wrote before 3.0.261028-rc3): "publisher.rules in env.yml names
    jpa-springboot, the code 2.x generates, which has no jpa-service-access; the 3.0 code carries it: take jpa-springboot out
    of the list (leave the list out for the architecture's bundle), and output = 'v2' out of the elasticore { } block if it
    is there - see elasticore-migration-guide.md.";
  - the 2.x code: "The code 2.x generates (output = 'v2', bundle jpa-springboot) has no jpa-service-access; the 3.0 code
    carries it: elasticore { output = 'v3' } - see elasticore-migration-guide.md." (with "with architecture = 'ddd' or
    'hexagonal'" for `ddd-state` and `ddd-derive`);
  - a layered bundle: "The jpa-springboot-v3 bundle does not carry ddd-state. Declare architecture = 'ddd' or 'hexagonal',
    or add the rule set." (the sentence it ended with before, after the bundle's name);
  - a lifecycle or derived value over MongoDB, which has no domain-model bundle: "This domain's mode has no domain-model
    bundle: ddd-state and the domain-model rule sets it works on are generated for JPA and MyBatis (architecture = 'ddd' or
    'hexagonal').";
  - `@deleted` names the set of the domain's mode - `jpa-entity-annotations`, `mybatis-mapper` or `mongo-service-file` - in
    "declares @deleted (...)" too, which said "(jpa-entity-annotations or mode: mybatis)" before.
- Still a warning; whether it becomes a build failure is open on the issue. No generated file changes:
  `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 / 0 / 0.
- Checked: `UnenforcedWarningTest` - each form; the reported run (hexagonal, `publisher.rules: [ossie-export]`, entities with
  `@password`/`@access`, `@deleted` and `@compute`) told that the list replaces the bundle, with each missing set; the same
  list as the build's property, now warned; `[jpa-springboot]` in env.yml told to take it out; both ways the message offers
  (the plugin's `rules = ['ossie-export']`, and `[ossie-export, jpa-service-access]` in env.yml) each ending the warning with
  the access checks written (`AccessPolicy`); an `output = 'v2'` run sent to the 3.0 code. `StateStampTest` (the layered
  bundle named with `ddd-state`); `OssieSensitiveTest` unchanged.
- Files: `RulesGenRunner.java`.
