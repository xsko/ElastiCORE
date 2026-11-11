### Changes (Ossie export - a metric over one entity is its dataset's)

#### A metric over one entity is written in that dataset's `metrics`, where Layer 2 exposes it as a measure
- Why: the export wrote every metric at the model root, on fields named with their dataset:
  `SUM(CASE WHEN PurchaseOrder.status <> 'CAN' THEN PurchaseOrder.total END)`. In the core spec that is a model-scoped
  metric, for aggregations that span datasets ("Metric Scoping"). The relational interface (Layer 2) does not expose a
  model-scoped metric as a measure, so none of the exported metrics was one there. A DSL metric is an aggregate over one
  entity or arithmetic over metrics declared before it, so it spans datasets only when it combines the metrics of two or
  more entities (#97).
- What: a metric whose aggregates all read one entity - also arithmetic over that entity's metrics
  (`averageOrder: revenue / orderCount`) - is written in the dataset's `metrics`, after its fields, on its fields by their
  own names: `SUM(CASE WHEN status <> 'CAN' THEN total END)`. Arithmetic over the metrics of two or more entities stays at
  the model root as before, and a root name two domains use keeps the domain before it. A dataset's fields and metrics
  share one set of names: a metric named like a field of its dataset, in any case, or like a metric another domain put
  there, is left out with `[ossie] [domain] metric total: PurchaseOrder has a field total, and a dataset's fields and
  metrics share one set of names - rename the metric; it is left out.` It is not renamed, and no domain goes before a
  dataset's metric.
- Upgrade: a project that names the `ossie-export` rule set and declares `metrics:` gets a new `semantic_model.yml` the next
  time it generates. A consumer that names a metric of one entity changes: `revenue` is now `PurchaseOrder.revenue`.
  ElastiCORE 3.0.261106-rc11 and earlier write every metric at the model root. A project with no `metrics:` generates the
  same document.
- Checked: `OssieMetricScopeTest` generates projects: a filtered and an unfiltered metric over one entity, a one-entity
  composite, a two-entity composite at the root, names clashing with a field (`Total`, `status`, `name`), and two domains
  with the same metric on one dataset and the same root metric.
  - The pet store's `semantic_model.yml`, regenerated, has its 13 metrics in the `Customer` and `PurchaseOrder` datasets and
    no root `metrics`; its `MetricsTest` runs each one on the database as `SELECT <sql> FROM <table>`, and each equals
    what the test computes from the entities. The two templates declare no metrics and generate the same document.
  - Against apache/ossie `ee89dbd`, with sqlglot, `validation/validate.py` passes the three documents, and a copy of
    the pet store's with a two-entity composite at the root as well. A dataset's metric written with `PurchaseOrder.` fails
    with `[Scope] ... uses qualified reference(s) 'PurchaseOrder'`, and one named `total` with `[Unique] Name 'total' in
    dataset 'PurchaseOrder' is used by both a field and a dataset-scoped metric`.
- Files: `OssieMetrics.java`, `OssieSemanticModelExporter.java`, `OssieMetricsTest.java`, `OssieExportTest.java`,
  `OssieMetricScopeTest.java` (new), the pet store's `semantic_model.yml`, `MetricsTest.java`, `build_guide.py` and
  `dsl-guide.html`, the rule guide and the DSL guide (en, ko)
