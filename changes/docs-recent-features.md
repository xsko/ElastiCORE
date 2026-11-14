### Documentation (the pet store, README, tour, getting started)

#### The pet store uses `@count` and `@onDelete`; the site guide, README and tour say what is new
- Why: `@count`, `@onDelete`, `@search(words:...)` and `elcoreValidate` were in the guides only; the pet store's DSL
  guide page still listed "a product with items cannot be deleted" as a rule the DSL could not say (the database's
  foreign key) - since `@deleted` such a product is taken off the catalog - and "default order" as missing, which
  `@sort(name+)` already does. Its numbers at the top were typed by hand and stale.
- What: the pet store's `Category.productCount: long @count(Product.category)` (discontinued products not counted;
  `data.sql` counts the seeded rows once) and `Product.category @onDelete(restrict)` (a category a product on sale
  names answers 409 `restricted`; one whose products are all discontinued is still refused, by the foreign key - 409
  `referenced`). `dsl-guide.html` (built by `guide/build_guide.py`): the one search box and the default order in
  "1. 상품 둘러보기", the count and the restrict in "2. 상품 관리" (now all DSL), `@onDelete`/`@count` in "어디서
  동작하나", the review's rows 7, 9 and 10; the numbers at the top are counted by the script (DSL lines, generated
  and hand-written Java, the TypeScript client). README, tour and getting started: `@count`, `@onDelete`, the search
  box, and `elcoreValidate` in CI; the DSL guide's annotation summary gains `@onDelete` and `@count` (en, ko).
- Checked: `ShopFlowTest.anAdministratorKeepsTheCategoriesTidy` (Fish counts 4; a new category 0, a product 1,
  discontinued 0; a request's `productCount` is not written; 409 `restricted` while a product is on sale, 409
  `referenced` after), the pet store's tests, its DDL files written again (`product_count`).
- Files: the pet store's `entity.yml`, `data.sql`, `db/schema-*.sql`, `ShopFlowTest.java`, `guide/build_guide.py`,
  `static/dsl-guide.html`; `README.md`, `docs/tour.md`, `docs/getting-started.md`, `elasticore-dsl-guide.md` (en, ko).
