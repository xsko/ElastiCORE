### Improvements (source comments - a command or event added later)

#### The rule sets' comments say a command or event added later builds, as the guides do
- Why: `DddCommandRules` said a command added to the DSL and not yet written "is a compile error", and
  `DddDomainServiceRules` said an event added to a process manager's `on` is an abstract method "the compiler asks
  the developer's class for". Since the generated-file merge that holds only where the merge does not reach; the
  guides were corrected (`guide-new-command-stub.md`), the code comments were not.
- Now: the comments say what lands - the behaviour class gets a stub that throws (Java
  `UnsupportedOperationException`, Kotlin `TODO`), the process manager an empty `handle`, both regenerated when
  untouched and merged into the developer's edits - and that a file the merge does not reach (`KEPT`, or an edited
  one under `merge = false`, `SKIP(user-owned)`) still fails to compile. Comments only; no generated text changed.
- Left as it is: the Javadoc the process manager base templates write into every generated `XxxBase`
  (`v3/processManagerBase.tmpl`, `kt/processManagerBase.tmpl`: "An event added to 'on' is a new handle(...) the
  compiler asks for there") is generated output; changing it changes every project's regenerated base.
- Checked: `SourceAsciiTest`; `elcoreRulesCheck -Pstrict` and `elcoreTreeCheck` unchanged.
- Files: `DddCommandRules.java`, `DddDomainServiceRules.java`.
