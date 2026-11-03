### New Features (Ossie export - a row filter on an entity)

#### A class `@jpa:Where` or `@jpa:SQLRestriction`, such as a soft delete, is in the dataset
- Why: an entity whose class says `@jpa:Where(clause = "delete_yn = 'N'")` is read by the application
  without its deleted rows, but its dataset said only the table. A BI tool or an agent reading the
  semantic model counted the deleted rows too, and so did every metric over that dataset, with nothing
  in the model to say why the numbers differ from the application's.
- What: Ossie has no row filter on a dataset, and a query as `source` would break a consumer that reads
  `source` as a table, so `source` stays the table. The dataset's `ELASTICORE` extension gets
  `row_filter`, the clause as written, and its `description` ends with `(rows only where: <clause>)`.
  A metric over a filtered dataset, also through the metrics it is made of, gets
  `dataset_row_filter: true` in its extension.
- Read as Hibernate reads them: `Where` and `SQLRestriction`, fully qualified or not, with the `@jpa:`
  or `@entity:` prefix that writes them onto the class; `SQLRestriction` over `Where`; only the
  entity's own class. A filter on an `@abstract` parent is a warning and is not exported, because
  Hibernate applies it to the child only when the parent is written as an entity, not as a
  `@MappedSuperclass`, and the export does not know which. A clause that is not one string literal is
  a warning and is not exported. `@Filter` is not read: it is off until a session turns it on.
- An entity without a filter, and a metric over none, are exported byte for byte as before.
- Checked: `OssieExportTest` - `@Where`, a fully qualified `@SQLRestriction` with double and single
  quotes in the clause, both on one class, `@entity:`, a parent's filter, a clause that is no literal,
  metrics over a filtered and an unfiltered dataset, and the same model with no filter giving the same
  bytes for what no filter touches. All but the last fail without the change.
- Files: `OssieSemanticModelExporter.java`, `OssieMetrics.java` (a metric's datasets),
  `OssieExportTest.java`, `elasticore-rule-guide.md` (en, ko - DSL -> Ossie table)
