### Improvements (generated code comments - process manager base)

#### The generated process manager base says what an event added to `on` does now
- Why: the Javadoc every generated `XxxProcessManagerBase` carries said "An event added to 'on' is a new handle(...)
  the compiler asks for there". Since the generated-file merge that is no longer so: the developer's process manager
  gets an empty `handle(NewEvent)` (`// The next step goes here.`), merged into their edits, and the build passes. The
  rule guide and the DSL guide already said so (`guide-new-command-stub.md`); the generated comment was left as it was
  (`comment-new-command-stub.md`) because it is generated output.
- Now: the comment says that an event added to `on` puts an empty `handle(...)` in the developer's class, merged into
  their edits, and the build passes; that only a file the merge does not reach (`KEPT`, `SKIP(user-owned)`) fails to
  compile until the developer writes it; and that a `handle(...)` the developer edited for an event removed from `on`
  stays and fails to compile. Java and Kotlin say the same (`a field here` / `a property here`).
- Regenerating changes the class comment of every project's `XxxProcessManagerBase` and nothing else. The base is
  regenerated on every build, and a project's own process manager class is untouched by this; since generated files
  are merged, a developer's own edits stay.
- Checked: generated `elasticore-template-ddd`, `elasticore-template-ddd-mybatis` and `elasticore-template-kt` with
  the new and the old templates - the only differences are the comment lines of the three process manager bases, and
  all three compile. No committed file holds a generated base (the samples' generated trees are gitignored) and no test
  pins the sentence. `ProcessManagerRulesTest`; `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED and
  `elcoreTreeCheck` 0 differ / 0 missing / 0 extra (no 2.x output changed).
- Files: `v3/processManagerBase.tmpl`, `kt/processManagerBase.tmpl`.
