### Bug Fixes (2.x generator - `@max` on a `bigdecimal`)

#### The 2.x generator writes `@DecimalMax` with the `@max` value, not the `@min` one
- Why: `SrcFilePublisher.setFieldValidation` wrote the `@min` value into the `@DecimalMax` of a `bigdecimal` or
  `biginteger` DTO field. `price: bigdecimal(10.2) @min(0.01) @max(999.99)` became `@DecimalMin("0.01")
  @DecimalMax("0.01")`, so every request with a price above the minimum was refused (400). The 3.0 code and the
  2.x-compatible output (`output = 'v2'`) already wrote `@DecimalMax("999.99")`; only the 2.x generator (`elcore`, the
  annotation processor) had it.
- What: the `@DecimalMax` carries the `@max` value. A field with `@max` on a `bigdecimal` or `biginteger` now gets the
  bound it declares; nothing else the 2.x generator writes changes.
- Checked: `TwoXBoundTest` runs the 2.x generator (`ModelExtractor`) on a DTO with `price: bigdecimal(10.2) @min(0.01)
  @max(999.99)` and `count: int @min(1) @max(9)`: `@DecimalMin("0.01")`, `@DecimalMax("999.99")`, `@Min(1)`, `@Max(9)`.
  Before the change it wrote `@DecimalMax("0.01")`.
- Files: `SrcFilePublisher.java`, `TwoXBoundTest.java`
