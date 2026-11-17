### Changes (archTest - the packages of the project's own code)

#### The generated architecture test checks the packages hand-written code adds under the base, placed by segment
- Why (issue 173): `archTest: true` took its layers from the namespaces alone. The generated classes keep to those
  boundaries by construction, so the test's work is on hand-written code, which tends to live in packages that are not
  namespaces - `domain.policy`, `infrastructure.mail`, an inbound `adapter.in.mq` - and no rule covered them: a domain
  policy that imported an SMTP sender, or a service that called it past its port, passed.
- What: where the namespaces name their layers by segment under the test's base package (the entities under
  `<base>.domain`, the services under `<base>.application` - what `namespace: { base: ... }` writes), the packages the
  project's main sources declare there are placed by their first segment after the base:
  - `domain.*` with the domain, `application.*` with the application;
  - `adapter.*`, `infrastructure.*` and the first segments of `control`, `repository` and `persistence` (`interfaces`,
    say) outside both (`adapter.out.*` with persistence, the outbound side).
  - A package under a namespace is that namespace's already; the base itself (the application class) and test sources
    are left out.
  - A package it cannot place (`config`, say) is a DSL warning, once - it names the package and says to move it or
    add its rule - and is not guessed.
  - A layered layout, whose segments name no layers, is read from the namespaces as before, and nothing is said.
- The test is generated once and then owned: a file nobody edited takes the new package lists on the next build, and an
  edited one goes through the three-way merge like every other owned file (no special case, as agreed on the issue).
- Unchanged: a project without packages of its own there gets the same file, word for word (the header names the
  project's packages only when there are some).
- Checked: `ArchTestPackagesTest` - a hand-written `domain.policy` class that imports `infrastructure.mail` is in the
  domain rule, and `infrastructure.mail`, `adapter.out.search` and `adapter.in.mq` are in what the domain and the
  application may not use; the base package, a package under a namespace and a test source are not; `config` is said
  once; with `control`, `repository` and `persistence` under `interfaces.rest`, `storage.jpa` and `db.mapping`, a
  hand-written `interfaces.mq` goes with the adapters and `storage.cache` and `db.audit` with persistence; Kotlin's
  `` adapter.`in`.mq `` read as `adapter.in.mq`; without packages of its own the file as before; a
  layered layout neither scanned nor warned; an untouched file updated and an edited one merged (its own rule kept).
  `elasticore-template-ddd`'s `ShopArchitectureTest` (`interfaces.rest`, no packages of its own) is unchanged.
- Files: `ArchitectureTestSource` (`layers`, `segments`), `RulesGenRunner` (`emitArchitectureTest`,
  `mainSourcePackages`, `declaredPackage`); env guide 6.1.3 (en, ko).
