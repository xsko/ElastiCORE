### Documentation (env guide)

#### The env guide names the release `mode: mongo` came in
- Why: the env guide's MongoDB section (2.4, English and Korean) still said "Since: 3.0 (unreleased)" after
  3.0.261119 shipped it.
- Now: "Since: 3.0.261119", the form the other sections use.
- Checked: `GuideCoverageTest`, `GuideCoverageKoTest`, `GuideTranslationTest`.
- Files: `elasticore-guide/elasticore-env-guide.md`, `elasticore-guide/ko/elasticore-env-guide.md`.
