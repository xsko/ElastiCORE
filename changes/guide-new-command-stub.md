### Documentation (rule guide, DSL guide - a command or event added later)

#### The guides say a command or an event added later builds, with a stub that throws or an empty `handle`
- Why: the rule guide said a command added to an existing behavior class "does not compile until you write the rule - it
  cannot be forgotten", that its stub "is not added to an existing behavior class", and that an event added to a process
  manager's `on` "does not compile until you write it in the developer class". The DSL guide said the same (the
  `SalesOrderBase` row, the `LoyaltyProcessManager is not abstract ...` message, and a dropped lifecycle leading to a
  compile error). Since the generated-file merge, `elcore3` adds the missing method to the generate-once file - it
  regenerates an untouched one and merges into an edited one - so the project builds and nothing reminds you.
- Now: the rule guide ("Business commands", "Process manager") and the DSL guide (the command table, the
  process-manager list, the lifecycle warnings) say what lands: a stub that throws
  (`throw new UnsupportedOperationException("Xxx.ship: write the rule")`, Kotlin `TODO("Xxx.ship: write the rule")`),
  answering 500 until the rule is written; an empty `handle(new event)` (`// The next step goes here.`) that does
  nothing until the step is written; regenerated when untouched, `MERGED(your edits kept)` when edited. A file the merge
  does not reach (`KEPT(yours ...)`, `SKIP(user-owned)`, `merge = false`) still fails to compile, and the guides say so.
- Checked: standalone hexagonal projects (plugin and `elasticore-base` from this branch), Java and Kotlin, each once
  with the generate-once files untouched and once edited (a rule written for `close`, a line in `handle`), then a
  command `escalate` added to `commands:` and `TicketClosed` added to `on`:
  - Java: `Ticket.java` and `FollowupProcessManager.java` `WROTE` (untouched) / `MERGED(your edits kept)` (edited);
    the edits kept, `escalate(Integer level)` added with the throwing stub, `handle(TicketClosedEvent)` added empty;
    `compileJava` passes in both. Booted, `POST /api/v1/desk/ticket/1/escalate` answers 500
    (`UnsupportedOperationException: Ticket.escalate: write the rule` in the log); `close` answers 200.
  - Kotlin: the same files, `TODO("Ticket.escalate: write the rule")` and an empty `handle`; `compileKotlin` passes;
    booted, `escalate` answers 500 (`kotlin.NotImplementedError`).
  - A lifecycle given to `escalate` removes the untouched stub from the edited class (merged); a typo in it
    (`ESCALATD`, the DSL warning names it) brings the stub back by the merge, and `compileJava` passes.
  - Where the merge does not reach, the guides keep the compile error: with `merge = false` an edited `Ticket.java` is
    `SKIP(user-owned)` and `compileJava` fails (`Ticket is not abstract and does not override abstract method
    escalate(Integer)`), while the untouched process manager is `REGENERATED(owned, never edited)`. Removing the event
    of an edited `handle` from `on` leaves the file `KEPT(yours - the DSL's changes to it do not merge)` and fails
    (`method does not override or implement a method from a supertype`); removing the event of an untouched `handle`
    merges it out and builds. The DSL guide's line on removing an event says so.
- Files: rule guide (en, ko), DSL guide (en, ko).
