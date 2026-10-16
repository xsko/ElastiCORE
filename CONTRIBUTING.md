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

### `elcoreRulesCheck -Pstrict` — the compatibility gate

This renders the sample's artifacts through the rule engine, in memory - seven
kinds of artifact, an entity by its field block - and compares each with the
2.x output in the repository (the hash line, the wording of the generated-once
marker, line endings and trailing blank lines aside):

```
=== RulesCheck summary: 150 MATCH / 0 DIFF / 2 ACCEPTED / 0 SKIP ===
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

1. **Make it opt-in.** A project that does not name your rule must generate
   exactly what it generated before. This is not a convention; it is what the
   compatibility gate enforces.
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
- **Generation and compilation can race.** `compileJava` may run before the
  generation task and compile a stale tree. If a sample fails in a way that
  makes no sense, run the generation task explicitly first.

## Pull requests

- One concern per pull request. A rule change and a refactor in the same diff
  makes the compatibility report impossible to read.
- Include the `elcoreRulesCheck -Pstrict` summary line in the description.
- Write comments in English. The guides are Korean prose with English code; the
  source is English throughout.
- Record user-visible changes in
  `elasticore-base/src/main/resources/elasticore-guide/elasticore-history.md`
  under the current version, with the files involved.

## Reporting a bug

The most useful bug report against a generator is the pair: **the DSL that went
in, and the code that came out.** Please include the entity or port declaration
and the generated file, along with your bundle selection from `env.yml`. A
description of the symptom without the declaration usually cannot be acted on,
because the same symptom has several causes upstream.

## License

By contributing, you agree that your contributions are licensed under the
Apache License 2.0, the same as the rest of the project.
