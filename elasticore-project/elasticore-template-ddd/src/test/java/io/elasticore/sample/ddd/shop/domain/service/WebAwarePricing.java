package io.elasticore.sample.ddd.shop.domain.service;

import io.elasticore.sample.ddd.shop.interfaces.rest.OrderController;

/**
 * A class written by hand in the domain.service package that names a
 * controller - what the generated architecture test must refuse
 * (ddd.DomainServiceArchitectureTest).
 *
 * Test code, and no bean: the build's own ShopArchitectureTest leaves tests
 * out, so it never sees this class, and nothing starts it.
 */
public class WebAwarePricing {

    private final OrderController orders;

    public WebAwarePricing(OrderController orders) {
        this.orders = orders;
    }

    public OrderController orders() {
        return orders;
    }
}
