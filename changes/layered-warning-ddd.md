### Bug Fixes (elcore3 - a warning's advice)

#### The layered build's "not enforced" warning names the architecture value the build takes, `'ddd'`
- Why: a layered (or 2.x-compatible) build of a model that declares DDD-only words - `states:`, `@stamp`, an
  invariant - warns `... which the selected rules do not enforce - nothing is checked, derived or hashed`, and went
  on `Declare architecture = 'domain-model' or 'hexagonal', or add the rule set.` The documented value is
  `architecture = 'ddd'` (`elasticore-env-guide.md`); `RulesConfig` also reads `domain-model`, which no guide names.
- Now: `Declare architecture = 'ddd' or 'hexagonal', or add the rule set.` Generated code does not change.
- Checked: `StateStampTest` asserts the advice on the layered run's log and fails without the change.
- Files: `RulesGenRunner.java`, `StateStampTest.java`.
