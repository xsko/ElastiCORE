### Bug Fixes (`schema.yml`)

#### `schema.yml` names the columns the entity maps: references, value types and collections (#203)
- Why: `blueprint/schema.yml` is read by tools and assistants to write SQL, and its relation lines named columns the
  table does not have.
  - A reference's `@foreignKey` was always `{field}_{target key}`: right only for JPA's default, wrong with
    `referenceColumn: short` or `field` (`author_member_id` for the `author_id` column) and with `@column`
    (`editor_member_id` for `last_editor_id`).
  - A field whose type resolves to a value class - `java.math.BigDecimal`, `java.time.Instant`, `java.time.LocalTime`,
    `Money`, `Quantity`, `byte[]` - fell through to the relation branch:
    `price: java.math.BigDecimal @relation(mappedBy="price_id") @foreignKey(price_id)` for a `price DECIMAL(10,2)`
    column.
  - Every `@relation(mappedBy=...)` held a made-up column (`post_id`, `orders_id`), and a collection's
    `@foreignKey(orders_id)` named no column anywhere.
- After: only a type that names an entity of the model is a relation.
  - A to-one reference is `author: Member @foreignKey(author_id) -- Member entity relationship`. The column follows
    the rules the entity's `@JoinColumn` follows: a native `@jpa:JoinColumn(name=...)`, else the column of the field
    `@joinBy` names (a reference by id, with any `referenceColumn` setting), else `@column`, else JPA's default - the
    field and the target key's own column, by Spring Boot's physical naming (`topic_topic_no` for a key written
    `@column(topic_no)`).
  - A one-to-one written `@OneToOne(mappedBy=...)` is the other side, with no column: `@relation(mappedBy="...")`.
  - A collection of entities holds no column here: `orders: List<Order> @relation(mappedBy="customer")` names the
    other side's field when `@OneToMany(mappedBy=...)` gives it, and nothing otherwise (`tags: List<Tag>` of a
    `@ManyToMany`).
  - A value type is a column, like a string: `price: io.elasticore.springboot3.domain.Money @Column(name = "price")`,
    `rate: BigDecimal @Column(name = "rate")`, `deletedAt: java.time.Instant @Column(name = "deleted_at")`.
  - An embedded value (`@embedded`, or an `@embeddable` type) is `billingAddress: Address @Embedded`, its columns
    the value's own. A list of enum codes or of an embeddable value is `@ElementCollection`: a table of its own.
- Not changed: a reference to an entity with a composite key still names one column, and a subclass of a JOINED
  hierarchy still lists its parent's fields under its own table.
- Generated code is unchanged: `schema.yml` is a description beside the model, which `elcoreRulesCheck` and
  `elcoreTreeCheck` do not compare. The 2.x template's `schema.yml` changes too - its lines described columns its
  own DDL does not have.
- Checked: `SchemaColumnsTest` generates the issue's model, with a collection, a list of enum codes, an embedded value
  and a target key with `@column`, under `hexagonal-springboot` with `referenceColumn: field` (`author_id`,
  `last_editor_id`, `parent_id`, as the generated entity maps them) and under the layered default
  (`author_member_id`, `last_editor_id`, `parent_post_id`, `topic_topic_no`); it fails on the old code. The five
  samples' `schema.yml` were regenerated, and every to-one `@foreignKey` and every entity field's `@Column` name in
  them was found among the columns of that entity's table in the sample's `db/schema-h2.sql`, except the parent
  fields a JOINED subclass lists (above). No collection line names a key any more.
- Files: `SchemaCodePublisher.java`, `SchemaColumnsTest.java`, `blueprint/schema.yml` of `elasticore-template`,
  `-template-ddd`, `-template-ddd-mybatis`, `-template-kt`, `-template-mybatis`.
