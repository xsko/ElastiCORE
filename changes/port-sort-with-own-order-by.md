### Improvements (DBMS port runtime - sort)

#### A DBMS port query keeps its own ORDER BY when a sort value comes with the request
- Why: `SqlPortGateway` appended ` ORDER BY <requested>` to the end of the SQL, so a query with an
  ORDER BY of its own, or one ending with LIMIT, OFFSET or FETCH, failed as soon as a sort value
  came (#73). A query had to drop its default order, or the service had to clear the sort values
  of the search DTO, and the user could not sort that list.
- After: where the SQL has an ORDER BY at its top level (outside parentheses, quotes and comments -
  the scan the page count already uses), the requested terms go first and the query's own stay
  after them as tie breakers: `ORDER BY reg_date DESC LIMIT 10` + `amount-` ->
  `ORDER BY amount DESC, reg_date DESC LIMIT 10`. Where it has none, the clause goes before a
  top-level LIMIT, OFFSET or FETCH (not a `:limit` parameter or a `t.offset` column). A query
  without either gets exactly what it got before. The checks of the sort terms are unchanged.
  The top-level scan lower-cases char by char, so a character that lengthens in lower case (a
  dotted capital I in a literal) no longer shifts the positions.
- Generated code: unchanged. `mode: mybatis` is not changed: its port mapper is generated, not run
  through `SqlPortGateway`, and still leaves out the requested sort where the SQL names its own
  ORDER BY (`MyBatisPortRules`).
- Checked: `DbTransactionGatewaySortTest` on H2 - own ORDER BY with a sort (ties broken by the own
  terms, also on a counted page), LIMIT / OFFSET ... FETCH / FETCH FIRST with and without an own
  ORDER BY, an ORDER BY or LIMIT in a subquery, a string literal and comments left alone; the
  test that pinned the failure (`ownOrderByBreaksSortedQuery`) is replaced.
  `:elasticore-springboot3:test` and `:elasticore-springboot4:test` pass.
- Files: `elasticore-springboot3/.../bean/SqlPortGateway.java` (shared by elasticore-springboot4),
  `DbTransactionGatewaySortTest.java`, `elasticore-guide/elasticore-dsl-port-guide.md` and
  `ko/elasticore-dsl-port-guide.md` (3.6 sortCode, Using sortCode).
