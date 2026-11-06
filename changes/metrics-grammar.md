### New Features (metrics - lists, alternatives and distinct counts)

#### A metric's filter takes `in`, `not in`, `or`, parentheses and `not`, and `count(distinct Entity.field)` counts different values
- Why: a filter was `field op value` joined by `and`, so a real project's KPIs needed workarounds (#98). A list of
  statuses had to be written as the statuses left out, and that counts a status added to the enum later without a word.
  An alternative took two metrics and a sum, and a distinct count could not be written at all. All of these are in
  `OSSIE_SQL_2026`, the language the export writes.
- What: `where status in (A, B)` and `not in (A, B)`, `or` with `and` binding tighter as in SQL, parentheses to group,
  and `not` before a parenthesis. `count(distinct Entity.field)`, and with `where`
  `COUNT(DISTINCT CASE WHEN ... THEN Entity.field END)`. Every value of a list is read as a single value is: an enum
  constant by how it is stored, and one the enum does not have drops the line, naming it.
- Refused, as a line that cannot be read always was: the line is dropped with a warning that quotes it. Parentheses that
  do not balance, an empty `in ()` or an empty value in a list, an `or` or `and` with nothing on one side, empty
  parentheses, `distinct` with anything but `count` (`sum(distinct ...)`), and `count(distinct Entity)`, which names no
  field to count the values of.
- A line that uses none of these writes what it did, and a word that only looks like one stays what it was: a constant
  in `region = OR`, a field in `orderNo = 1`. The one thing such a line can see is a different sentence: `qty = 1 and`
  was "cannot read the value 1 and", and now says the `and` has nothing after it. It is dropped either way. The export
  is the only output of `metrics:`, so no code changes for anyone.
- Checked: `OssieMetricsTest` - each form, what binds tighter, the words inside values and names, each refusal, and each
  value of a list checked. `OssieSqlTest` - the new forms stay the portable `sql`. `MetricsTest` in the pet store runs
  the exported SQL of the new metrics on H2 and compares them with the same numbers worked out from the entities, and
  holds the SQL of the six metrics that were there. The regenerated `semantic_model.yml` passes Ossie's
  `validation/validate.py` with `sqlglot`, at `172a3e5`.
- The pet store's `dsl-guide.html` is rebuilt by `guide/build_guide.py` for its metrics. The rebuild also takes the
  `discontinuedAt` of #112, which the page had not caught up with.
- Files: `OssieMetrics.java`, `OssieMetricsTest.java`, `OssieSqlTest.java`, the pet store's `metrics.yml`,
  `semantic_model.yml`, `MetricsTest.java`, `build_guide.py` and `dsl-guide.html`, `elasticore-dsl-guide.md` "Business
  metrics" (en, ko)
