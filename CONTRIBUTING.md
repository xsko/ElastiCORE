# Contributing to ElastiCORE

Thank you for wanting to work on this. This document is short and specific to
what makes a code generator different from ordinary software: **its output is
somebody else's source tree.** A change that looks harmless here appears as a
diff in every project that regenerates.

## Getting set up

```bash
git clone https://github.com/xsko/ElastiCORE.git
cd ElastiCORE
./gradlew build
```

Run the build on a JDK 17 to 22 — the wrapper's Gradle 8.8 does not start on a
newer one. `elasticore-base` targets Java 8 and is compiled and tested on a
JDK 8 toolchain, the other modules on 17; Gradle uses the JDKs you have and
downloads the ones you don't. The Gradle wrapper is included; don't install
Gradle.

A full `build` generates and compiles the sample modules and runs the test
suite. If that passes on a clean checkout, your environment is fine.

## The checks that must pass

The same ones CI runs:

```bash
./gradlew build
git status --short -- elasticore-project/elasticore-template/src/main/java     # must print nothing
git status --short -- 'elasticore-project/*/src/main/resources/blueprint'      # must print nothing
./gradlew :elasticore-project:elasticore-template-kt:elcore3tsVerify -Pstrict  # needs Node.js
./gradlew :elasticore-project:elasticore-sample-petstore:webBuild              # needs Node.js
./gradlew :elasticore-project:elasticore-template:elcoreRulesCheck -Pstrict
./gradlew :elasticore-project:elasticore-template:elcoreTreeCheck
```

`build` regenerates the template - the reference project - with the 2.x
generator, and must leave it exactly as committed: no file changed, removed or
added. The two checks after it compare the rule engine with that 2.x
generator, so on their own they pass a change that moves both alike, such as
one to a model loader they share; the committed tree is what holds 2.x's
output in place. If your change is meant to change 2.x's output - a bug fix -
commit the regenerated files with it and give the reason in the history: the
pull request then shows exactly what every 2.x project will get.

`elcoreTreeCheck` compares every file 2.x generates for the template with the
rule engine's, and must report `0 differ / 0 missing / 0 extra`.

The Gradle plugin (`elasticore-gradle-plugin`) is an included build; its own
tests run as part of `build` through `elasticore-base:check`, and its artifact
publishes alongside the framework's. If you change the plugin, `./gradlew
build` at the root is enough to know.

### The gates on the lines you write

Many people and assistants change this code, so four more checks hold every
pull request to the same bar. They look at **the lines your change adds or
edits** - between the merge base with `origin/main` and your working tree, new
files and uncommitted edits included - not at the code older than them:

```bash
./gradlew formatChanged     # formats your changed Java lines, and only those (palantir-java-format)
./gradlew prCheck           # all four gates below
```

| Gate | Fails when | CI job |
|---|---|---|
| `formatCheck` | a changed Java line of the libraries, the plugin or the quality tools is not formatted | Format and static analysis of the change |
| `diffErrorprone` | [Error Prone](https://errorprone.info/bugpatterns) reports a warning on a changed library line (an ERROR fails `build` anywhere) | Format and static analysis of the change |
| `diffCoverage` | tests run less than 80% of the changed library lines - the samples' tests count for the runtime libraries | Build, test and compatibility checks |
| `diffMutation` | tests detect less than 70% of PIT's mutations of the changed library lines they run | Mutation testing of the change |

"The libraries" are `elasticore-base`, `elasticore-springboot3` and
`elasticore-springboot4`, main sources, the Jackson adapters (`src/jackson2`,
`src/jackson3`) included. A failing gate annotates the lines on
the pull request and lists them in the job summary; the HTML reports are under
each module's `build/reports/jacoco/` and `build/reports/pitest/`, and in the
run's artifacts.

- **Not run** (coverage): no test executes the line. Write the test that fails
  without your change.
- **Survived** (mutation): PIT changed the line - negated a condition, replaced a
  return value, removed a call - and every test that runs it still passed. The
  test runs the line but does not check what it does; assert the result.
- **An Error Prone warning that is wrong for its line** is suppressed on the
  smallest element with the reason: `@SuppressWarnings("CheckName") // why`.
- The thresholds are properties (`-PdiffCoverageMinimum=`, `-PdiffMutationMinimum=`)
  for trying things locally; CI uses the defaults. `-PdiffBase=<ref>` measures
  against another base; on CI, or with `-PdiffBase` given, a base that cannot be
  found fails the gates rather than letting them compare with nothing.
- Error Prone runs on JDK 21 as a compile of its own (`errorprone`, part of
  `check`, so of `build`); Gradle downloads a JDK 21 where there is none. The
  published classes are still `compileJava`'s.
- A change of more than 300 library lines gets one mutation per changed line
  (`-PmutationsPerLine=0` for all of them); PIT's tests run on four JVMs on CI
  and two on a workstation (`-PmutationThreads=`).
- A file moved and edited counts the lines that differ from the old one.

### `elcoreRulesCheck -Pstrict` — the compatibility gate

This renders the sample's artifacts through the rule engine, in memory - seven
kinds of artifact, an entity by its field block - and compares each with the
2.x output in the repository (the hash line, the wording of the generated-once
marker, line endings and trailing blank lines aside):

```
=== RulesCheck summary: 157 MATCH / 0 DIFF / 2 ACCEPTED / 0 SKIP ===
```

`DIFF` must be zero. If your change moves an existing project's generated code,
that is a breaking change to every user, and the check is where it gets caught.

An intentional difference is possible, but it has to be *declared* with a
written reason that appears in the report, and the reason has to say why it is
safe — the two current entries are additive: they add a search property that
2.x was unable to produce at all. "It looks better this way" is not a reason.
If you find yourself wanting to accept a diff, that is the moment to open an
issue rather than a pull request.

## Adding a generation rule

Rules live in `elasticore-base/src/main/java/io/elasticore/base/model/pub/rules/`
and are registered in `RuleCatalog`.

1. **Keep the 2.x code where it was.** A project that keeps 2.x's code
   (`output = 'v2'`, the template's setting) must generate exactly what it
   generated before; that is what the compatibility gate enforces. The 3.0
   code - the default since 3.0.261028-rc3 - may change before 3.0 is released,
   with the reason in the history; a new behaviour there is still switched on
   by the DSL or a setting rather than by default where a project could be
   surprised by it.
2. **Document it.** `GuideCoverageTest` fails if a rule set, a bundle, a DSL
   word your rule reads, or a namespace key your rule places an artifact by is
   missing from the guides. The test exists because undocumented generated code
   is worse than no generated code — the reader cannot tell what wrote it.
3. **Make it real in a sample.** A rule with no sample using it is a rule
   nobody has watched compile. Add it to one of the template modules.
4. **Cover all three languages, or say which one.** Java, Kotlin and TypeScript
   have separate template sets. A rule that only makes sense in one is fine;
   silently producing Java into a Kotlin project is not.

## Things this codebase has learned the hard way

Each of these was a real bug. They are listed because they are not obvious and
they recur.

- **Never use `FileWriter` or `FileReader`.** They take the platform charset,
  which on Korean Windows is MS949, and generated Korean text is corrupted.
  Use `OutputStreamWriter` / `InputStreamReader` with `StandardCharsets.UTF_8`.
  `PlatformCharsetTest` scans the sources for this and fails.
- **Keep `elasticore-base` main sources ASCII.** The build reads them as UTF-8,
  but an IDE or a plain `javac` on Korean Windows reads MS949, and non-ASCII
  there — even an arrow in a comment — fails to compile. Write `->`, not `→`.
  `SourceAsciiTest` checks it.
- **Adding a field to `ECoreModel` means touching every loader.** There are
  several `MainModelLoader` classes, one per mode. Missing one does not fail
  anything; the feature is just silently absent in that mode.
- **The golden comparison checks content, not paths.** It will not notice a
  file written to the wrong directory. If your change affects *where* something
  lands, that needs its own test.
- **The compatibility checks only see what the template's model uses.** `0 DIFF`
  and `0 differ` say nothing about a DSL word or a combination the template
  never declares. Four 2.x differences were found only by generating a real
  9-domain project: a `@jpa:` class annotation dropped by v3 and Kotlin
  entities, a required relation dropped from a declared search DTO, the AI tool
  wording of `supportAI`, and a TypeScript search DTO importing an entity file
  that is never written. When you touch a feature, check that the template
  declares it; if it does not, add it there, so the checks compare it from then
  on.
- **"2.x does X" is a claim to run, not to remember.** A comment, a history
  entry or a guide that says what 2.x generates is checked by generating it with
  2.x - the template does that - and is not written from reading the code. A
  comment saying a required relation "searches only through its flattened id -
  which is what 2.x emits" was wrong and hid one of the four differences above.
- **A file the build writes and the repository holds must be committed as
  written.** The Ossie model in each sample's `blueprint/` and the pet store's
  TypeScript client: CI regenerates them and fails on any difference. The
  guides and their index are not held at all - a guide changed without the
  index regenerated once left the index pointing at line ranges the guide no
  longer had, and the copies conflicted on every guide change.
- **Generation and compilation can race.** `compileJava` may run before the
  generation task and compile a stale tree. If a sample fails in a way that
  makes no sense, run the generation task explicitly first.

## Pull requests

- One concern per pull request. A rule change and a refactor in the same diff
  makes the compatibility report impossible to read.
- Include the `elcoreRulesCheck -Pstrict` summary line in the description.
- Write comments in English. The guides are Korean prose with English code; the
  source is English throughout.
- Record user-visible changes as a fragment of their own, `changes/<topic>.md`
  (`changes/README.md` has the form), with the files involved - not as an edit to
  `elasticore-history.md`: every pull request editing the top of the history
  conflicted with every other. A release moves the fragments into the history.
  The CI job **Change record** checks it on every pull request: it fails one that
  edits the history, that changes what ElastiCORE ships (`src/main`) without a
  fragment, or that commits a guide copy, and warns when the branch is more than
  ten commits behind `main`. Its message says what to do.
- Branch from the latest `main`, and rebase on it before asking for review. A
  pull request stacked on another one waits for it and conflicts with whatever
  merges first; stack only what really depends on the one below.
- Do not commit the guide copies in the samples' `blueprint/` (`elasticore-*.md`):
  the build writes them from `elasticore-base/src/main/resources/elasticore-guide/`,
  and `.gitignore` keeps them out.

## Reporting a bug

The most useful bug report against a generator is the pair: **the DSL that went
in, and the code that came out.** Please include the entity or port declaration
and the generated file, along with your bundle selection from `env.yml`. A
description of the symptom without the declaration usually cannot be acted on,
because the same symptom has several causes upstream.

## License

By contributing, you agree that your contributions are licensed under the
Apache License 2.0, the same as the rest of the project.
