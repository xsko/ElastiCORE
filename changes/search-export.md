### New Features (REST - `export` in `@expose(methods)`: the search as a CSV file or an .xlsx workbook)

#### `POST /api/<domain>/<entity>/export`, where `@expose(methods="...")` lists `export`
- Why (issue 271): a screen's "download" of a search was code every project wrote again - the same conditions as the
  search, its access check and `@owner` filter, every page, the columns and their titles, quoting, a spreadsheet's formula
  injection, a limit - and each got some of it wrong.
- Now:
  - `export` is a sixth name of `@expose(methods="...")`. Only where it is listed: without `methods` an entity keeps the
    five it has, and a list without it serves what it served. Every mode (JPA, MyBatis, MongoDB), Java and Kotlin, every
    architecture (layered, ddd, hexagonal). A name it does not know is still a DSL warning, which now names `export` among
    the names.
  - The controller gets `POST .../export` beside `POST .../search`, taking `{ search, sortCode, columns, headers, format }`
    (all optional; `search` is the search DTO `POST .../search` takes). It only wires: the search DTO, the order - the
    body's `sortCode`, else the search's own, with the key after it so that rows of one value do not move between pages -
    and every page of the service's (the use case's) own `findBySearch`, so the conditions, the search's `@access` and the
    `@owner` filter are the search's. Nothing changes in the service or the use case.
  - The runtime's new `io.elasticore.springboot3.export` writes it (`SearchExport`, `CsvWriter`, `XlsxWriter`; no interface
    an application implements). Columns: the response DTO's properties as the application's ObjectMapper writes them
    (Jackson 2 on Spring Boot 3, Jackson 3 on 4; its JsonView where the search answers in one), in the DTO's declaration
    order, or those `columns` lists (400 for one it does not have). Titles: `headers` (400 for another length), else each
    column's label in the request's language - `ErrorMessages`' label lookup of issue 274, over the domain's label bundle
    (`Dto.field`, then `Entity.field`; a language with a label file is one the request may be answered in), else the name.
    Values as the response's JSON shows them: `@Mask` masked, an enumeration by its code, money and dates as written.
  - CSV by default: UTF-8 with a BOM, RFC 4180 quoting, CRLF; a text that starts with `=`, `+`, `-`, `@`, a tab or a
    carriage return gets a `'` before it (CSV injection, as OWASP advises) - a JSON number does not, so a negative number
    stays a number. `.xlsx` for `"format": "xlsx"` or an Accept that asks for it (CSV where both are asked alike), through
    POI's streaming `SXSSFWorkbook`, where the application has Apache POI - checked by class name; the runtime only compiles
    against it - else 406 (`not-acceptable`). In the workbook numbers are numbers (more than 15 digits: text), dates dates
    (from the DTO's value, unless masked), text never a formula. Another `format`: 400.
  - `elasticore.export.max-rows` (Spring property, default 100,000): the first page's total - the count through the same
    search - above it is 400 `export.too-many-rows` with the count, before anything is written. A sheet's 1,048,575 rows cap
    an .xlsx export whatever it says.
  - The TypeScript client: `exportXxx(body: ExportRequest<XxxSrchDTO>): Promise<Blob>` in `XxxApi.ts`, and
    `ExportRequest` / `requestFile` in `apiSupport.ts` - both only in a domain with an entity that lists `export`.
  - `ErrorMessages.label(Label)`: a label in the request's language, public for the export.
- Unchanged: a model that does not list `export` generates byte-identical output (the controller, `apiSupport.ts`, the
  client); the 2.x-compatible output and the 2.x generator do not read it. The runtime library brings no new dependency
  (POI is `compileOnly`). A project with `runtimePackages` copies the new `export` classes with the rest.
- Limits: the pages are read one after another, each its own query, so a row written meanwhile may be in or out; a failure
  after the first row leaves the file cut short (the status is already sent); an enumeration is its code, not its label.
- Checked: `SearchExportTest` (runtime, run by elasticore-springboot3 on Jackson 2 and elasticore-springboot4 on Jackson 3:
  BOM and RFC 4180 bytes, the injection guard and a negative number, three pages of 1,007 rows all written in the DTO's
  order, the limit refused after one page with nothing written, columns and headers and their 400s, the crm label bundle in
  en / ko / ja, a JsonView, @Mask in CSV and xlsx, an xlsx with number, date, text-not-formula and a 17-digit id, the Accept
  header, 406 without POI, the sort with the key after it). `ControllerMethodRulesTest` (the endpoint only where listed, the
  paged and the list form, `export` alone, a rule set excluding it, nothing omitted for the five, the 2.x output without it;
  the warning's names) and `ExposeMethodsTest` (the warning). `SearchExportGenerationTest` (through `RulesGenRunner`: JPA
  Java hexagonal / layered / ddd, JPA Kotlin hexagonal / layered, MyBatis Java layered / hexagonal, MongoDB Java and
  Kotlin layered - the endpoint on the entity that lists it, none on the others; an entity without it generated alike
  with or without an export beside it; `apiSupport.ts` the same text without the two; the client's `exportExItem`; a typo
  still warned). Over HTTP: the pet store (JPA, hexagonal, Boot 4) `ExportTest` - the search's rows, columns and headers,
  `Unit cost` / `단가` by Accept-Language, a customer's own orders only (the administrator's not; a visitor 401), the
  status code `PLC`, `=HYPERLINK` and `-1+2` guarded, 406 for xlsx and Accept xlsx without POI, 400s - and `ExportLimitTest`
  (`max-rows=10`: the 32 items refused with the count, a narrower search written); `elasticore-template-mybatis` (MyBatis,
  layered, Boot 3, POI on the test path) `ShopApi` - `serialNo` masked by the application's ObjectMapper, the DTO's
  columns, a workbook with a string `=SUM` cell, a number and a date, 1,100 rows written once each in key order;
  `elasticore-template-kt` (Kotlin) `ShopApiContractTest` - an agent's own tickets only; `elasticore-template-ddd` (ddd)
  `ExposeMethodsApiTest` - a receipt; `elasticore-template-mongo` `ShopApi` - the masked email (runs where Docker does,
  CI). Compiled: `elasticore-template-kt-mongo` (Kotlin, MongoDB) and `elasticore-template-ddd-mybatis` (MyBatis ddd and
  hexagonal). The generated TypeScript type-checked and run (`elasticore-template-kt` `elcore3tsTest -Pstrict`).
  With the change taken back, `SearchExportGenerationTest` and the export tests of `ControllerMethodRulesTest` fail.
  `elcoreRulesCheck -Pstrict` 158 MATCH / 0 DIFF / 2 ACCEPTED, `elcoreTreeCheck` 0 differ / 0 missing / 0 extra.
- Files: `ControllerBaseRules`, `TypeScriptApiRules`, `ts/apiSupport.tmpl`; the runtime's `export/` (`SearchExport`,
  `CsvWriter`, `XlsxWriter`, `ExportSheet`, `ExportColumns`, `JsonRows` on Jackson 2 and Jackson 3), `ErrorMessages`, the
  two runtime `build.gradle` (POI `compileOnly`); tests `SearchExportTest`, `SearchExportGenerationTest` (new),
  `ControllerMethodRulesTest`, `ExposeMethodsTest`; the pet store (`entity.yml`, `ExportTest`, `ExportLimitTest`, the site
  guide page `static/dsl-guide.html` rebuilt by `guide/build_guide.py`), `elasticore-template-mybatis` (`entity.yml`,
  `build.gradle`, `ShopApi`), `elasticore-template-kt` (`ticket.yml`, `ShopApiContractTest`), `elasticore-template-ddd`
  (`billing` `entity.yml`, `ExposeMethodsApiTest`), `elasticore-template-ddd-mybatis` (`billing`, `shop` `entity.yml`),
  `elasticore-template-mongo` (`entity.yml`, `ShopApi`), `elasticore-template-kt-mongo` (`club` `entity.yml`); the
  annotation, env and rule guides (en, ko).
