### Documentation (annotation guide - `AccessPolicy` for an operation of no entity, issue 365)

#### The annotation guide shows how a hand-written class guards an operation of no entity with `AccessPolicy`
- Why: a compare, a report or a calculation belongs to no entity; `domainService:` takes no `@access` (and is not generated
  in layered). Projects wrote their own role helper, repeating the administrator and the system scope `AccessPolicy` already
  knows, because the guides named `AccessPolicy` as a runtime class without describing it. This is option 2 of the issue;
  `@access` on `domainService:` (option 1) is not part of it.
- Added: annotation guide, at the end of "@access / @owner" (English and Korean), "An operation of no entity - `AccessPolicy`
  in your own class": a `RateTools` service with `AccessPolicy.parse("RateTools", "compare=RATING_ADMIN,
  performance=BRANCH_SALES|ACCOUNTING", "ADMIN")` and `ACCESS.require(currentUser, "compare")`, and what was found by reading
  `AccessPolicy` and running it:
  - the declaration is an entity's `@access` text: levels, role names (capital first) with `|` and `&`, `*=`; an operation not
    named is the administrator's; a word that is neither makes `parse` throw `IllegalArgumentException`; the role enum is
    not checked at run time;
  - `owner` with no row holds for anyone signed in (`require` takes it as signed in);
  - `require` throws `NotSignedInException` (`Sign in to compare RateTools`) or `ForbiddenException` (`compare RateTools is
    for RATING_ADMIN`, `purge RateTools is for the administrator`); the administrator passes all but `none`, `runAsSystem`
    everything; `isAdmin`, `requireAll`;
  - the administrator's role is the third argument: `config: adminRole`, `ADMIN` by default, as `ServiceAccessRules` passes
    it to the generated CoreService;
  - `CurrentUser` through `ObjectProvider<CurrentUser>` with `SessionCurrentUser` as the fallback, as the generated services
    take it: the runtime and the generated code declare no `CurrentUser` bean;
  - the statuses come from the generated `<Domain>ApiErrors` (401, 403), which advises only the controllers in its package
    and below (`basePackageClasses`).
- Checked: `AccessPolicyOwnOperationTest` (new, `elasticore-springboot3`; the same sources run in `elasticore-springboot4`)
  runs the guide's class with no `CurrentUser` bean: a visitor, each role, another role, a plain user, the administrator,
  `runAsSystem`, another `adminRole`, and `owner` without a row. The statuses were checked over HTTP with a throwaway test in
  `elasticore-template-mybatis` (not committed): the class's checks in a controller in the package of `ShopApiErrors`
  answered 401 to a visitor, 403 to `USER`, 200 to `RATING_ADMIN` and `ADMIN`, 200 to `ACCOUNTING` for `performance`; the same
  checks in a controller outside that package were not answered by it (the exception reached the servlet: a 500).
- No DSL, code or generated output changed. The guide tests (`--tests "*Guide*"`).
- Files: `elasticore-annotation-guide.md` and its `ko/` copy, `AccessPolicyOwnOperationTest.java` (new).
