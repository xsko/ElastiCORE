### Bug Fixes (v3 access-controlled nested DTOs)

#### Include a nested DTO when the holder's readers are allowed to read the referenced entity
- Why: A nested DTO was omitted whenever the referenced entity was not public, including the safe case where both holder and referenced entity used `read=user`.
- Before and after: Nested DTO visibility now uses access-expression implication. Every reader admitted by the holder must also be admitted by the referenced entity; the referenced `owner` level is never implied. The FK identity remains available. A separate mapper rule continues to prevent cascading reverse mapping from writing a referenced row protected by its own write access.
- Checked: `AccessExpressionTest`, `ServiceAccessRulesTest`, and `NestedGuardedRowTest` (same-level `user`/`user` nested DTO visibility plus reverse-mapping write protection; layered and hexagonal, Java and Kotlin).
- Files: `AccessExpression.java`, `ServiceAccessRules.java`, `NestedGuardedRowTest.java`, `elasticore-annotation-guide.md` and `ko/elasticore-annotation-guide.md`.
