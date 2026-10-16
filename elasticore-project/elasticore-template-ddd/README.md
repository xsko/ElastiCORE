# elasticore-template-ddd

The domain-model reading of the pipeline, kept where a compiler can check it.

## What this module is for

`elasticore-template` proves the Java rules by generating its 2.x sources
through the rule engine and comparing them with the committed ones (see the
compatibility checks in `CONTRIBUTING.md`), and has no `publisher.rules`
configured on purpose - so it
never exercises the DDD bundle. `elasticore-template-kt` compiles Kotlin, and
composes a different set. Neither can say whether the DDD rules produce a Java
project that still builds.

This module runs `ddd-springboot` over **the same shop blueprint** the golden
module uses. The DSL is identical apart from the aggregate boundary being
declared rather than left to be inferred, so anything that differs between the
two generated trees is attributable to the rules and nothing else.

```bash
./gradlew :elasticore-project:elasticore-template-ddd:elcore3dddVerify
```

Generated sources are not committed; the blueprint is.

## What to look at

### The boundary decides what exists

`Order` and `Customer` are roots; `OrderItem` and `VipProfile` are members
(`@aggregate(Order)`, `@aggregate(Customer)`). A member is reached by
navigating from its root, so it gets no repository, no service, no endpoint,
no search DTO and no key DTO:

```
domain/repository/   OrderRepository  CustomerRepository  ProductRepository  CategoryRepository
interfaces/rest/     OrderController  CustomerController  ProductController  CategoryController
```

No `OrderItemRepository`. No `VipProfileController`. The generator did not
skip them - the rules declined them, which is a different thing: the run
reports each one as `SKIP(dontMake)`.

### A reference across the boundary cannot be written through

`Order.customer` points at another aggregate. The DSL says `@fetchjoin`, and
the rule overrides it:

```java
/** Another aggregate: referenced, never written through. */
@ManyToOne(fetch = FetchType.LAZY)
private Customer customer;
```

Eager loading is what makes a second aggregate available to modify in the same
transaction. Lazy is the annotation that says do not.

### Members are changed one at a time, through the root

```java
/** Members of this aggregate. Changed one at a time, through this root. */
@OneToMany(fetch = FetchType.LAZY ,cascade = CascadeType.ALL,mappedBy="order")
@Setter(AccessLevel.NONE)
private List<OrderItem> items;

public void addOrderItem(OrderItem member) { ... }
public void removeOrderItem(OrderItem member) { ... }
```

The setter Lombok would have generated is withheld: handing out the list is
handing out the aggregate.

### A value object has no identity

`Address` is `@embeddable`. It gets value equality and loses its setters -
a value object is replaced, not changed.

### Layering is configuration, not a rule

Each artifact's package is named in `env.yml`, so the layers a domain model is
usually drawn in cost nothing to have:

```yaml
namespace:
  entity:     io.elasticore.sample.ddd.shop.domain.model
  repository: io.elasticore.sample.ddd.shop.domain.repository
  service:    io.elasticore.sample.ddd.shop.application
  control:    io.elasticore.sample.ddd.shop.interfaces.rest
```

Nothing is generated differently for it.

### The hexagon, both directions

`rules: [hexagonal-springboot]` adds the inbound port to everything above.
Each exposed root gets a use-case interface, the application service declares
it implements it - which makes the compiler re-check the surface on every
build - and the controller injects the interface instead of the class:

```java
public interface OrderUseCase { OrderDTO save(OrderDTO dto); ... }
public class OrderService extends OrderCoreService implements OrderUseCase { }
private final OrderUseCase orderService;   // in OrderController
```

The outbound side is the port DSL. `blueprint/shop/port/report_port.yml`
declares a read model - revenue by customer is Order data grouped by
Customer data, a view the aggregates deliberately cannot answer - and what
is generated is one interface in `application.port.out`; the adapter is a
runtime proxy, so there is no persistence code to keep honest:

```java
@DbmsService(id="shop.ShopReportService", sqlSource="blueprint/shop/port/report_port.yml")
public interface ShopReportService {
    List<CustomerRevenueDTO> revenueByCustomer(RevenueSearchInput input);
}
```

`HexagonalPortTest` drives it against the same H2 the aggregates persist to:
writes go in through the domain model, the report comes out through the
port, and a dependency-direction check asserts the domain never looks upward.

## What this does not do

Named honestly, because the gap is the point:

- **The identity form of a cross-aggregate reference.** `Order.customer` stays
  a `Customer`, not a `customerId`. That changes the field's type, and the
  mapper, the DTO and the Q class are generated from the same spec by rules
  that would still see the old one - a spec-level change, left out rather than
  half-done.
- **Domain events, domain services, a repository interface split from its
  implementation.** Each is a new kind of file. The artifact extension point
  (`ArtifactContributor`) now makes them possible without touching shared
  code - the use-case interface is its first consumer - but they are not
  built: events need DSL vocabulary (`@emits`) designed first.
- **Invariants and factories.** The DSL has no vocabulary for them yet.

See `docs/design/ddd-ruleset.md` for the full pattern inventory and why each
line above falls where it does.
