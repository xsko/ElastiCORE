### Documentation and tests (follow-ups of the reviews of #102, #110 and #115)

#### A metric's not, and what the guides and the counts said
- Why: the reviews of #102, #110 and #115 left small items. The DSL guide said only `!=` and `not in` leave out a row
  whose field is null, though `not (...)` does too, so `unfinished` read as what `finishedOrders` leaves; its table
  showed SQL of models the guide does not declare (`Offer.status`, `Login.user_id`); nothing ran a null status. The
  annotation guide did not say which outputs carry a value object in a DTO. `CONTRIBUTING.md` and the tour still gave
  the counts of the compatibility checks before #102's `EmployeeContactDTO`. Two fragments missed a consequence and a
  `Checked:` line.
- What: the DSL guide (en, ko) says `!=`, `not in` and `not (...)` leave out a null, with the consequence for
  `unfinished`, and its table shows the pet store's SQL (`PurchaseOrder.customer_id`, `PurchaseOrder.status IN ('PLC',
  'SHP')`). `MetricsTest.aNullStatusIsInNeitherAConditionNorItsNot` takes one order's status away in a transaction it
  rolls back and runs the exported SQL of `finishedOrders`, `unfinished`, `otherOrders` and `orderCount`: the order is
  in none of them. The annotation guide (en, ko) names the outputs that carry a value object whole: `ddd`,
  `hexagonal` and layered MyBatis. The counts are `158 MATCH` and 239 / 234 files. `template-field-list.md` says that an
  entity whose only `@template` is a list without the key gets no service or controller; `self-reference-followups.md`
  has its `Checked:` line. No generated output changes.
- Not done: #102's review also asked to ask whether a DTO carries a value object only for a DTO with a list, so that a
  run over one domain is not stopped by another domain's rule sets. Run, that run stops earlier anyway - the references
  are shaped from every domain's rule sets (`shapeReferences`) before the lists are checked - so the change would not
  have helped.
- Checked: `elcoreRulesCheck -Pstrict` `158 MATCH / 0 DIFF / 2 ACCEPTED / 0 SKIP`; `elcoreTreeCheck` `239 generated
  by 2.x | 234 identical | 1 import order only | 4 accepted | 0 differ | 0 missing | 0 extra`; the pet store's
  `MetricsTest`; `GuideTranslationTest`.
- Files: `elasticore-dsl-guide.md` 5 (en, ko), `elasticore-annotation-guide.md` 4.1 (en, ko), `MetricsTest.java`,
  `CONTRIBUTING.md`, `docs/tour.md`, `docs/tour.ko.md`, `changes/template-field-list.md`,
  `changes/self-reference-followups.md`
