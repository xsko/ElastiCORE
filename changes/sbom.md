### Build and release (SBOM)

#### Every published artifact is published with a CycloneDX SBOM, signed, on Maven Central and on the GitHub release
- Why: the only SBOM of ElastiCORE was GitHub's dependency-graph export, which held the npm packages of two sample
  lockfiles, the GitHub Actions and the repository - none of the published libraries' dependencies (Spring, Jackson,
  Netty, ...). Checked in SBOM Viewer against CISA's 2026 Minimum Elements v2.1 it failed author, component producer
  (0/36) and component hash (0/36), and could not express a signature or the generation context.
- Before: no SBOM tooling in the build or the workflows.
- After:
  - `gradle/publishing.gradle` applies the CycloneDX Gradle plugin (`org.cyclonedx.bom` 3.6.1) to elasticore-base,
    elasticore-springboot3, elasticore-springboot4 and the Gradle plugin, over their `runtimeClasspath` - what the POM
    declares. The task `sbom` completes the bill from what the build has in hand: metadata authors, manufacturer and
    supplier (The ElastiCORE Authors, the POM's developer), lifecycle phase `build`, the artifact's license and the
    hashes of the published jar, each component's supplier from the `<organization>` of its POM or the nearest parent
    POM (Maven's inheritance; `unknown` where none names one), the POM's developers as the component's authors, the
    hashes of BOM (pom-only) components and of elasticore-base in the runtime libraries' bills, and the published purl
    of a module of this build instead of the plugin's `?project_path=` one.
  - Published as `<artifactId>-<version>-cyclonedx.json` (classifier `cyclonedx`, extension `json`), signed like every
    file, so the `.asc` meets the signature element. `centralBundle` refuses a jar without its SBOM.
  - The Release workflow attaches the four SBOMs and their signatures to the GitHub release.
  - A Dependency graph workflow submits the published artifacts' `runtimeClasspath` to GitHub (Gradle dependency
    submission, pushes to `main` and `release/*`), so Dependabot alerts and GitHub's export include them. GitHub's
    SPDX export still has no hashes, suppliers or author - GitHub writes it; the CycloneDX files are the SBOM to give a
    customer.
  - The generated code, the POMs and the jars are unchanged.
- Checked: `publishToMavenLocal` writes the four `-cyclonedx.json` files; `centralBundle`, run with a throwaway signing
  key and a release version, passes its checks and zips the four SBOMs with their `.asc`; the main component's SHA-256
  equals the published jar's. The four files validate against the CycloneDX 1.6 JSON schema. Per CISA element: author,
  timestamp, tool name and version, serial number and version, lifecycle present; name, version, purl, SHA-256 hash and
  license on every component (springboot4: 91/91), a supplier on every component (springboot4: 74 named, 17 declared
  `unknown`), every component in the dependency graph. A local run of the dependency-submission plugin with the
  workflow's filters reports the same 140 packages as the SBOMs together, none of the samples. `elcoreRulesCheck
  -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ.
- Files: `gradle/publishing.gradle`, `build.gradle`, `elasticore-project/elasticore-gradle-plugin/build.gradle`,
  `.github/workflows/release.yml`, `.github/workflows/dependency-graph.yml`, `RELEASING.md`, `README.md`, `README.ko.md`
