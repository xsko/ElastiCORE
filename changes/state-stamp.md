### New Features (lifecycle - `@stamp`)

#### `@stamp(STATE)` records when a lifecycle entered a state
- Why: `states:` writes the move, but "when was it shipped / cancelled / approved" was hand code in
  every behaviour class - the most common addition to a lifecycle, left to the developer.
- After: `shippedAt: timestamp @stamp(SHIPPED)` on a time field. The command that moves the row into
  the state sets the field right after the move, before the event - whichever command it is, and
  again on each entry (the field holds the last one). A command of two lifecycles stamps each state it
  enters, after every check passed; a refused command stamps nothing. `@stamp(fee.REFUNDED)` names the
  lifecycle where two have the state; `@stamp(SENT, CANCELLED)` takes several. `timestamp`, `datetime`
  and `date` take `Instant.now()`, `LocalDateTime.now()` and `LocalDate.now()`. The field is the
  lifecycle's like the state: read-only in the DTO, not mapped from a request, a protected setter,
  `readOnly: true` in the TypeScript field metadata. The lifecycle comment of the base lists it, and
  the Ossie export carries it as `stamp` in the field's extension.
- Where it cannot be written on a split root the model check fails, with the reason: a state no
  lifecycle has, a state two lifecycles have, a state no move enters (the initial one - `@default(now)`
  is for when a row is made), a field that is not a date or time. A warning would not do: the field is
  the lifecycle's, so left out it would be a column nothing ever writes. On an `@editable` root the
  field stays an ordinary writable one, with a DSL warning; with no lifecycle rule set selected (the
  layered bundle) the unenforced-rule warning names `@stamp (ddd-state)`. A `@stamp` on an
  `@abstract` parent is stamped by each split root that extends it. A state named twice is one line.
- JPA and MyBatis alike: the line is in the entity's command and the MyBatis adapter's UPDATE stores
  it. Java and Kotlin.
- 2.x output: unchanged (rulesCheck -Pstrict 157 MATCH / 0 DIFF / 2 ACCEPTED, treeCheck 0 differ /
  0 missing / 0 extra). A model without `@stamp` generates what it did.
- Samples: `Shipment` of `elasticore-template-ddd` and `elasticore-template-ddd-mybatis` has
  `sentAt` (timestamp), `cancelledAt` (datetime) and `refundedOn` (`date @stamp(fee.REFUNDED)`, set by
  the command of two lifecycles); `Ticket` of `elasticore-template-kt` has `closedAt`, set by `close`
  and by `cancel`. Their DDL files are regenerated with the new columns.
- Checked: `StateStampTest` generates Java and Kotlin hexagonal, Java layered and the TypeScript
  client - one lifecycle, two, several states, a state named twice, an inherited field, read-only DTO
  and client field, each refusal failing the run, the `@editable` root left writable, the layered
  warning; `StateStampApiTest` in `elasticore-template-ddd` over HTTP and the table (send
  stamps, cancel stamps both, a refused cancel stamps nothing, a create or update cannot write it);
  `DomainOnDatabase.aLifecycleStampIsStored` in `elasticore-template-ddd-mybatis` (H2 here; Oracle,
  PostgreSQL and SQL Server in CI); `DomainRulesDslTest` in `elasticore-template-kt` (close and cancel
  stamp, a refusal does not, the service stores it and ignores a request's value).
- Files: `DddStateRules`, `DddBehaviorRules` (guarded), `TypeScriptDtoFileRules`, `RulesGenRunner`
  (warning), `OssieSemanticModelExporter`, the DSL and rule guides (en and ko), the three sample
  models and the DDL of the two JPA samples.
