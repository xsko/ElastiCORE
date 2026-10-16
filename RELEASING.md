# Releasing ElastiCORE

A release is a tag. Pushing `v{version}` runs the Release workflow (`.github/workflows/release.yml`): it builds and
checks the tagged commit, signs every published artifact, uploads them to Maven Central and makes the GitHub release.

The version is `{major}.{minor}.{YYMMDD}`, e.g. `3.0.261020`. A pre-release adds `-alpha{n}`, `-beta{n}` or `-rc{n}`
(`3.0.261015-alpha1`); Maven and Gradle order it before any release of the same line, and its GitHub release is marked
as a pre-release. What is published:

| Artifact | Coordinates |
|---|---|
| Generator | `io.elasticore:elasticore-base` |
| Runtime library, Spring Boot 3 | `io.elasticore.springboot3:elasticore-springboot3` |
| Runtime library, Spring Boot 4 | `io.elasticore.springboot4:elasticore-springboot4` |
| Gradle plugin | `io.elasticore:elasticore-gradle-plugin`, and the marker `io.elasticore:io.elasticore.gradle.plugin` that `id 'io.elasticore'` resolves |

## Cutting a release

1. On `main` (or a `release/*` branch), everything passes:
   ```bash
   ./gradlew build
   git status --short -- elasticore-project/elasticore-template/src/main/java     # must print nothing
   ./gradlew :elasticore-project:elasticore-template:elcoreRulesCheck -Pstrict   # 0 DIFF
   ./gradlew :elasticore-project:elasticore-template:elcoreTreeCheck             # 0 differ / missing / extra
   ```
2. On a branch of its own - `main` takes changes only through pull requests (`BRANCHING.md`) - one commit names the
   version everywhere (`git switch -c version/3.0.261020` before committing):
   - `elasticoreVersion` and `elasticoreReleasedVersion` in `gradle.properties`, both the release version
   - the install snippets that carry the previous release (`git grep -n <previous version>` finds them in the
     READMEs and the guides; the build refreshes the guides' copies in the template `blueprint/` folders)
   - the `## {version}` heading in `elasticore-history.md` - and its three copies in the template `blueprint/`
     folders, by hand: the build does not copy the history
3. Push the branch, open its pull request and merge it once CI passes. Then tag the commit the merge made on `main` -
   the pull request names it - and push the tag; the workflow refuses a tag whose commit is on no branch:
   ```bash
   git push origin version/3.0.261020        # then open the pull request and merge it
   git fetch origin
   git tag -a v3.0.261020 -m "ElastiCORE 3.0.261020" <the commit the merge made on main>
   git push origin v3.0.261020
   ```
4. The workflow waits for approval when the `release` environment asks for one. It stops unless the tag and both
   versions in `gradle.properties` agree, the history has the section, and the commit is on `main` or a `release/*`
   branch.
5. Central validates the upload. By default the deployment then waits at
   [central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments): check it, then
   **Publish** (or **Drop**). The GitHub release is made as a draft - publish it once the artifacts are on Maven
   Central, usually within half an hour. With the environment variable `CENTRAL_PUBLISHING_TYPE` set to `AUTOMATIC`,
   Central publishes as soon as the upload is valid, and the workflow waits for that and publishes the GitHub release;
   set required reviewers on the `release` environment first, since nothing else then stands between a `v*` tag and
   Maven Central.
6. Set the next version, in a pull request too: `elasticoreVersion` becomes a `-SNAPSHOT` that sorts after the
   release, the next day's date (`3.0.261021-SNAPSHOT`). `elasticoreReleasedVersion` stays: the README keeps naming
   the version people can download.

A version on Maven Central never changes and is never removed. A deployment that fails validation or is dropped
publishes nothing: fix the cause and move the tag to the fixed commit -
`git tag -f -a v{version} -m "..." <commit>` and `git push --force origin v{version}`. If a run fails after the
upload, drop that deployment on central.sonatype.com before running again. An `AUTOMATIC` deployment cannot be
dropped once it is publishing.

## What the workflow needs

In the repository's **Settings → Environments → release**, with the deployment rule `v*` so that no other branch or
tag reaches these secrets:

| Secret | What |
|---|---|
| `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` | A user token of a Central Portal account that owns the `io.elasticore` namespace (central.sonatype.com → View User Tokens → Generate User Token) |
| `SIGNING_KEY` | The ASCII-armored private key that signs the artifacts (`gpg --armor --export-secret-keys <fingerprint>`); Central's guide says it verifies against the primary key, so sign with that, not with a subkey |
| `SIGNING_PASSWORD` | Its passphrase |

The public half of the signing key must be on a key server Central reads - `keyserver.ubuntu.com` or
`keys.openpgp.org` - before the first upload:
```bash
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys <fingerprint>
```

The current key expires on 2029-10-14, and 3.0 is supported past that. Before then, extend it
(`gpg --quick-set-expire <fingerprint> <new date>`), send the public key to the key server again, and put the
re-exported private key in `SIGNING_KEY`.

## The bundle, locally

`./gradlew centralBundle` does what the workflow does short of the upload: it signs every published artifact, stages
it in `build/central-bundle/` and writes `build/central-bundle.zip`, after checking that every file has its signature
and checksums and every jar its sources and javadoc. It refuses a `-SNAPSHOT` version and a build without a key:
```bash
ORG_GRADLE_PROJECT_signingKey="$(cat signing-key.asc)" ORG_GRADLE_PROJECT_signingPassword=... ./gradlew centralBundle
```
`./gradlew publishToMavenLocal` needs neither. `./gradlew publish` also sends to a Maven repository of your own when
`elasticorePublishUrl`, `-Username` and `-Password` are set in `~/.gradle/gradle.properties`.

See `BRANCHING.md` for which branch a fix goes to.
