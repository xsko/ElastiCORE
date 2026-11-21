### Samples (elasticore-template-mongo - an html field)

#### The MongoDB sample keeps a product description as html, and its test checks the stored document
- Why: `html` (#208) is cleaned over MongoDB too - the Mongo bundle of #209 got `jpa-html` in #208 - but no sample
  used it there, so nothing ran the Mongo path end to end against a database.
- What: the shop's `Product` gets `description: html @plain(descriptionText)` and `descriptionText: text @search(like)`;
  the module puts jsoup on its classpath, as an application that uses `html` does.
- Checked: `ShopApi.anHtmlFieldIsCleanedAndItsTextKept`, run by `MongoApiTest` on a MongoDB container (and by
  `MongoApiOnServerTest` on a server named by `ELASTICORE_IT_MONGODB_URI`). A create drops the `onclick` and the
  `<script>`, keeps the `<b>`, and writes the text without tags over the `descriptionText` the request sent - in the
  response and in the stored document. An update drops an `<img onerror=...>` and gives the link `rel="nofollow"`.
- Files: `elasticore-template-mongo` - `build.gradle`, `blueprint/shop/model/entity.yml`, `blueprint/schema.yml`
  (regenerated), `src/test/java/mongo/ShopApi.java`.
