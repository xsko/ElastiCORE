### Bug Fixes (Kotlin service - `@readonly` and `@restExclude`)

#### A Kotlin `@readonly` service has no write methods, as the Java one
- Why: `@readonly` takes the write endpoints off the controller and the write methods (`save`, `update`,
  `batchUpdate`, `delete`, `deleteById`, `updateBySpecification`) off the service. The service's sections are named
  by the method's signature, read as Java's `public`/`protected ...` - a Kotlin `open fun save(` named no section, so
  the Kotlin JPA service (every architecture) kept every write method. Over HTTP nothing changed: the Kotlin
  controller already had no write endpoint (`POST`/`PUT`/`DELETE` 404 or 405). But the service bean offered writes to
  any code that called it - a read-only entity written from inside the application. The same for a
  `@restExclude(deleteById)` service method. (The Kotlin MongoDB service declares its methods `public` and was right.)
- Now: a Kotlin method signature (`open fun`, `override fun`, `fun`; not `private fun`) names its section, so the
  Kotlin service leaves out what the Java one does. The generated Kotlin service test (`testCode`) called those
  methods; a test method is now a section too (`test:save` for `save_Should...`), and the tests of a method the
  service leaves out go with it (rule `service-test-method-filter`; the 2.x-compatible output keeps every test, as
  its service keeps every method; the Java code writes no service test). A Kotlin model without `@readonly` or
  `@restExclude` generates the same: in the samples only the `@readonly` entity's `CountryCoreService.kt` and
  `CountryServiceTest.kt` changed. Upgrading: Kotlin code of your own that overrides or calls one of those methods of
  a `@readonly` (or `@restExclude`) entity's service - the extension service `XService.kt`, or a generated service
  test you edited and the build kept - no longer compiles; remove the override or the call. Also in 3.0.261120.
- Checked: `ExposeMethodsTest.aReadonlyServiceHasNoWriteMethodInEitherLanguage` (fails without the change) and
  `theServiceTestTestsOnlyWhatTheServiceHas`, `MethodSectionsKotlinTest` (the Kotlin forms, a private method, a test's
  section with `@Disabled` or a javadoc above it, no blank line left before the closing brace),
  `ServiceTestMethodFilterTest` (each exclusion takes its test alone; the 2.x-compatible test keeps every test). `elasticore-template-kt`: a `@readonly` `Country` in `refdata` and
  `ReadOnlyEntityTest` - read and searched over HTTP, every write path 404/405 and the table unchanged, and
  `CountryService` has no write method (it had all six before the change); the module's whole suite passes, its
  generated `CountryServiceTest` included.
- Files: `MethodSections.java`, `ServiceTestBaseRules.java`, `ExposeMethodsTest.java`, `MethodSectionsKotlinTest.java`
  (new), `ServiceTestMethodFilterTest.java` (new), `elasticore-template-kt`
  (`refdata/model/code.yml`, `schema.yml`, `db/schema-*.sql`, `ReadOnlyEntityTest.kt`).
