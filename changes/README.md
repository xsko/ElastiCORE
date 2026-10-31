# Change fragments

A pull request records what it changes as **one new file here**, not as an edit to
`elasticore-history.md`. Every pull request used to edit the top of the history at the same
lines, so every second one conflicted; a file of its own does not.

- **Name:** a short topic in kebab-case, e.g. `v3-join-fetch.md`. Fragments are assembled in the
  order of their names.
- **Content:** what the history holds, in its form - one `###` section with its `####` entries:
  why, before and after, how it was checked, the files it touched. No `## version` heading; the
  release adds it.
- **Language:** English. The entries up to 3.0.261031-rc7 are in Korean and stay so.
- **Several changes:** one fragment each, or several `###` sections in one.

```markdown
### Bug Fixes (v3 service - `@fetchjoin`)

#### A v3 CoreService fetches `@fetchjoin` associations together with the root in a page search
- Why: ...
- Checked: ...
- Files: ...
```

When a release is cut, `./gradlew assembleHistory` moves every fragment into the history section
of that version and deletes the fragments (RELEASING.md). The Release workflow refuses a tag
while a fragment is left here.
