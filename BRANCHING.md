# ElastiCORE branches and releases

This repository holds the 3.x line. The 2.x line (2.2) is maintained outside it;
3.x still generates the 2.x output, apart from the bug fixes the history lists (see the compatibility checks in
`CONTRIBUTING.md`).

| Branch | Line | What goes in |
|---|---|---|
| `main` | 3.x mainline | Features and fixes through pull requests |
| `release/3.0` | 3.0 maintenance | Cut from `main` at the first stable 3.0 release (no `-alpha`, `-beta` or `-rc`). Bug and security fixes only; tags `v3.0.YYMMDD` |
| `feature/*`, `fix/*` | - | Branched from the line they change; a 3.0 fix branches from `release/3.0` once it exists, from `main` before that |

## Support

**3.0 is supported for three years from its first stable release**: bug and security fixes to `elasticore-base`,
the Gradle plugin (`io.elasticore`), `elasticore-springboot3` and `elasticore-springboot4`, released from
`release/3.0` as `3.0.YYMMDD` versions. The end date is written here when that release is out. Until then, fixes
come in the next pre-release from `main`. Supported versions and how to report a vulnerability:
[SECURITY.md](SECURITY.md).

## Rules

1. **Fix the oldest supported line first, then carry the fix forward**
   (here: `release/3.0` first, then `main`).
   `git cherry-pick -x <commit>` onto the newer line, so the commit records
   where it came from. Write the entry in `elasticore-history.md` of both
   lines.
2. **Spring Boot versions are not branches.** 3.x supports Spring Boot 3 and
   4 from one branch: `elasticore-springboot3` / `elasticore-springboot4`
   and the `elasticore.springBoot` switch. A branch per Boot version would be
   a second generator to keep in step.
3. **A release is a version that does not change.** Tagged releases go out
   without `-SNAPSHOT`, and Maven Central never lets a released version change;
   `-SNAPSHOT` is for work in progress only. A snapshot can be cached as "missing"
   or replaced under a user, which is what makes builds on other machines disagree.
4. **Before a release:** `./gradlew build`, `elcoreTreeCheck` (every 2.x
   generated file, 0 differ / 0 missing / 0 extra) and
   `elcoreRulesCheck -Pstrict` pass on the release branch.

## Versions

The version is `elasticoreVersion` in `gradle.properties`; the Gradle plugin build reads it too.
