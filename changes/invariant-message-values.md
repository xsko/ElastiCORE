### Bug Fixes (runtime)

#### `InvariantViolationException(code, message, args)` fills its `{0}` like the other refusals (#325)
- Why: `ForbiddenException`, `NotSignedInException`, `BadRequestException`, `ConflictException` and
  `NotFoundException` fill the placeholders of their default message from the values; `InvariantViolationException`
  kept `{0}`, so a 422 whose code the application's messages do not translate answered `"detail": "{0} is your only
  way to sign in; ..."`.
- Now: the default message is filled the same way (`Refusals.format`). A message without a placeholder is kept as
  written: the generated checks pass their own sentence with values for the translation, and a sentence that quotes a
  value (`status != 'CANCELLED'`, "can't") would otherwise lose its quotes to the formatting. The code and the values
  still reach the translation unchanged.
- Checked: `InvariantViolationExceptionTest` (filled as `ConflictException` fills it; quotes kept without a
  placeholder; no values; what a placeholder is).
- Files: `InvariantViolationException` (elasticore-springboot3, used by springboot4 too).
