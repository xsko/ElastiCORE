## What and why

<!-- What changes for a user of ElastiCORE, and the problem it solves. -->

## Checklist

- [ ] `./gradlew build` passes
- [ ] `./gradlew :elasticore-project:elasticore-template:elcoreRulesCheck -Pstrict` - 0 DIFF
- [ ] `./gradlew :elasticore-project:elasticore-template:elcoreTreeCheck` - 0 differ / missing / extra
- [ ] A project that does not use this change generates exactly what it did before
- [ ] The template's model declares the feature this touches, so the two checks above compare it
- [ ] Anything said about 2.x's output (comments, history, guides) was checked by generating it with 2.x
- [ ] Files the build writes into `blueprint/` are committed as regenerated
- [ ] Tests that fail without the change
- [ ] `./gradlew formatChanged` run and committed, and `./gradlew prCheck` passes - changed lines formatted, no Error Prone finding on them, run by tests (80%), their mutations detected (70%) (CONTRIBUTING.md, "The gates on the lines you write")
- [ ] A change fragment, `changes/<topic>.md` (`changes/README.md`) - not an edit to `elasticore-history.md`
- [ ] Branched from the latest `main`, and not stacked on another open pull request unless it needs it
- [ ] Guides updated (`elasticore-base/src/main/resources/elasticore-guide/`, English docs in `docs/`)
- [ ] Main Java sources in `elasticore-base` are ASCII only (`SourceAsciiTest`)
