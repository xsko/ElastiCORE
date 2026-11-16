### New Features (runtime - Spring Security and the generated access checks)

#### `SpringSecurityCurrentUser`: the generated `@access` / `@owner` checks read who is asking from Spring Security
- Why: an application that signs people in with Spring Security (a login form, a JWT resource server, an OAuth2 / OIDC
  login) had to write its own `CurrentUser`, and the guide said so in one line. The step that goes wrong was not written
  anywhere: `AccessPolicy` compares the identity with a row's owner as text, but a search is narrowed by it and a new row's
  owner field is set to it, so a `CurrentUser` answering the username where the `@owner` key is a `Long` owns nothing and
  fails on the first search or create.
- After: `io.elasticore.springboot3.security.SpringSecurityCurrentUser` (elasticore-springboot3 and -springboot4) answers from
  `SecurityContextHolder`. No authentication, an `AnonymousAuthenticationToken`, an unauthenticated one or a mapping that
  answers `null` is a visitor; `hasRole("ADMIN")` holds for the authority `ROLE_ADMIN` and for `ADMIN` itself
  (`withRolePrefix` for another prefix); `system()` is `CurrentUser.inSystemScope()`, so `runAsSystem` works as before. The
  identity is the application's mapping: `SpringSecurityCurrentUser.of(auth -> key)`, `claim("customer_id", Long::valueOf)`
  for a token claim (read through the principal's `getClaim(String)`, which Spring's `Jwt` and `OidcUser` have - no compile
  dependency on the OAuth2 modules), or `new SpringSecurityCurrentUser()` for the name, documented as right only for a
  string key holding the username. The annotation guide (en, ko) has a section "Spring Security instead of @signin": when,
  the bean, a login form (username -> key through the query port), a JWT resource server, an OIDC login (claim or account
  link), the pitfalls (the key's type, the `ROLE_` prefix, someone signed in without a row) and `runAsSystem`.
- Not changed: nothing registers it - an application declares the bean (the generated services already take an application
  `CurrentUser` bean). `spring-security-core` is `compileOnly`, so the published POMs do not change. No generated code
  changes, so 2.x output and the v3 bundles' output are as before.
- Checked:
  - `SpringSecurityCurrentUserTest` (elasticore-springboot3, run again by -springboot4 on Boot 4): a visitor with no,
    an anonymous or an unauthenticated authentication; roles with and without the prefix, another prefix, none; the name
    as identity and why a Long key cannot take it; the mapping with `AccessPolicy` (`owns`, `ownerFilter`, `ownerToClaim`,
    `require`), an account without a row as a visitor, the administrator; the claim helper on a principal with
    `getClaim`, a missing claim, a principal without `getClaim`, a claim that throws; `runAsSystem`.
  - `SpringSecurityGuideTest` (the pet store, HTTP through a Spring Security filter chain with HTTP Basic and in-memory
    accounts, the guide's bean mapping the username to `customerId`): a customer reads and searches only their own row,
    someone else's customer row and order are 404, cancelling someone else's order is 404; an order is created with the
    signed-in customer's `Long` key as owner; the administrator (`ROLE_ADMIN`) reads every row and ships the order, which
    a customer may not (403); a visitor gets 401 and a wrong password is refused by Spring Security; the guide's JWT and
    OIDC beans map a claim and a linked account. The pet store's other tests run without a filter chain: its tests have
    `spring-security-config` / `-web`, not the starter, so only the test that imports the guide's configuration is
    secured. `SecurityGuideCodeTest`: the guide's three blocks stand in those sources, and the Korean guide shows the same.
- Files: `elasticore-springboot3/src/main/java/io/elasticore/springboot3/security/SpringSecurityCurrentUser.java`,
  `SpringSecurityCurrentUserTest.java`; `elasticore-springboot3/build.gradle`, `elasticore-springboot4/build.gradle`
  (`compileOnly` / `testImplementation` spring-security-core); `elasticore-sample-petstore/build.gradle` and its tests
  `guide/security/` - `ShopSecurity`, `TokenSignIn`, `SpringSecurityGuideTest`, `SecurityGuideCodeTest`;
  `elasticore-annotation-guide.md` (en, ko).
