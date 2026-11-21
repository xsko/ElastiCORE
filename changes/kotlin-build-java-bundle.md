### Bug Fixes (Kotlin - a Java bundle named by the domain)

#### A Kotlin build whose domain names a Java bundle stops before it writes a file
- Why: with `language = 'kotlin'`, a domain whose env.yml names a Java bundle or set - `publisher.rules: [jpa-springboot-v3]`,
  as the catalog domain of `elasticore-template-ddd` does - was generated into `.kt` files holding Java and Lombok
  (`import lombok.Getter`, `@SuperBuilder`), and only `compileKotlin` found it. The other way round (a Java build naming
  `jpa-springboot-kt`) already stopped, through the Kotlin shells' `requireSyntax`; the Java shells say nothing of the
  language they write, so this direction passed.
- Now: before anything is written, a Kotlin run reads the rules each domain names (`publisher.rules.kt`, else
  `publisher.rules`), and one that holds a set only the Java bundles have - a Java file shell, the Lombok value object -
  stops the run: `[elcore3] domain 'shop' names jpa-springboot-v3 in publisher.rules, which writes Java (its set
  jpa-entity-file), but the build's language is kotlin - name jpa-springboot-kt instead, or leave publisher.rules out for
  the bundle the build's architecture names. No source was written.` The build's own lists are read the same way: the
  `elasticore.publisher.rules` a domain naming none runs, and the sets the build adds to every domain (the plugin's
  `rules`). The Kotlin name is given for `jpa-springboot`, `jpa-springboot-v3`, `ddd-springboot`,
  `hexagonal-springboot`, `ddd-value-object` and `jpa-identity`. Which sets are Java's is read from the bundles (in a
  Java bundle and in no Kotlin one - a Kotlin bundle is one named `-kt`, `mongo-springboot-kt` among them), plus the
  two Java shells no bundle names.
- Unchanged: a Java build, a Kotlin domain naming the Kotlin bundles with language-neutral sets
  (`[jpa-springboot-kt, ddd-aggregate, ddd-reference, ossie-export]`, as `elasticore-template-kt` does), a project's own
  rule classes, and 2.x (which has no Kotlin: `-Pv2` with Kotlin is refused already).
- Checked: `KotlinJavaBundleTest` - the refusal tests failed before the fix (exit 0, `.kt` files written) and pass after;
  the audit's repro (`language = 'kotlin'` + `[jpa-springboot-v3]`, Boot 4) now stops with the message above and writes
  no source. `elcoreRulesCheck -Pstrict`, `elcoreTreeCheck`.
- Files: `RulesConfig` (`javaOnlySetOf`, `kotlinCounterpartOf`), `RulesGenRunner`, `KotlinJavaBundleTest`, rule guide (en, ko).
