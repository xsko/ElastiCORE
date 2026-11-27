### Bug Fixes (TypeScript client - a bound written without a number)

#### `@min(,)` no longer stops the TypeScript generation
- Why: the TypeScript client reads a bound as the text before the first comma - `@min(0.01, 'too cheap')` is
  0.01. It took that text with `split(",")[0]`, and for a value of only commas (`@min(,)`, `@max(,,)`) that split has
  no first element: `elcore3ts` stopped with an `ArrayIndexOutOfBoundsException` (seen on c3442c05 through the
  TypeScript rules, `BoundWithoutANumberTypeScriptTest.aBoundOfOnlyACommaIsNoBound`).
- Now: such a bound is no bound in the TypeScript client - no `min`/`max` in `XxxFields`, no check in `validateXxx`
  or `validateXxxCodes` - as a bound that is not a number (`@min(ten)`) already was. The DSL has no warning for a
  bound that is not a number, so none is added. A model with a number in its bound generates what it did.
- Checked: `BoundWithoutANumberTypeScriptTest` (only commas, not a number, a number with a message) fails on
  c3442c05 and passes now; `FieldFactsTest.aBoundIsANumberOrNothing`; the samples regenerate byte for byte as
  before.
- Files: `elasticore-base` `rules/FieldFacts.java` (`bound`); tests `BoundWithoutANumberTypeScriptTest`,
  `FieldFactsTest`.
