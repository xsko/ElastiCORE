### Bug Fixes (sample - pet store schema files)

#### The pet store's schema files say the length of `credit_card_type` the generator writes
- Why: since the v3 column of a required enum is as long as its `@db` code (3.0.261104-rc9), the generator writes `credit_card_type VARCHAR(4)`, but the committed `db/schema-*.sql` of the pet store still said `VARCHAR(255)`; every build that generated rewrote them, so a fresh checkout showed four changed files.
- Before / after: `VARCHAR(255)` / `VARCHAR2(255 CHAR)` / `NVARCHAR(255)` -> `VARCHAR(4)` / `VARCHAR2(4 CHAR)` / `NVARCHAR(4)` for H2 and PostgreSQL, Oracle and SQL Server. Sample output only; the generator is unchanged.
- Checked: regenerated with `:elasticore-sample-petstore:elcore3` from `main`; the four files differ in that line only.
- Files: `elasticore-sample-petstore/src/main/resources/db/schema-{h2,postgresql,oracle,sqlserver}.sql`
