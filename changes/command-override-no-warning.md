### Bug Fixes (elcore3 - a lifecycle command's parameters)

#### A lifecycle command the behavior class overrides gets no "does not keep" warning (issue 229)
- Why: since rc17 (issue 170) a lifecycle command parameter that is not named as a field gets
  `[Shop] Member.withdraw(confirmText): the generated move does not keep confirmText - name a parameter as the field it
  fills ..., or override withdraw in Member and keep it there` on every build. Overriding the command in the behavior class,
  as the warning advises, did not silence it: the rule that says it never looked at that class.
- Now: before an entity is written, the generator reads the developer's behavior class (`Member.java` / `Member.kt`
  beside `MemberBase`) and notes the commands it overrides - `void withdraw(` in Java, `override fun withdraw(` in
  Kotlin, comments left out. A command overridden there is not warned about; one that is not is warned as before.
  Generated code does not change. A way to say in the DSL that a parameter is meant to be dropped (the issue's first
  point) is not part of this fix.
- Checked: `CommandParamKeptTest` - Java and Kotlin, a second run after adding the override to the generated behavior
  class says nothing (both fail without the change); an override of another command, and a comment naming the
  command, still warn. `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ /
  0 missing / 0 extra.
- Files: `DddStateRules.java`, `RulesGenRunner.java`, `CommandParamKeptTest.java`.
