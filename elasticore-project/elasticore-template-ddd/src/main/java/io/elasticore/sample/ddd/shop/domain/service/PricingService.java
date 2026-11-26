package io.elasticore.sample.ddd.shop.domain.service;

import io.elasticore.sample.ddd.shop.domain.repository.OrderRepositoryPort;
import io.elasticore.sample.ddd.shop.domain.repository.ProductRepositoryPort;
import org.springframework.stereotype.Service;

/**
 * What an order costs once the products it names are priced: an operation of
 * no single aggregate, so a domain service.
 *
 * Plain Spring code, written by hand: the repository ports of the aggregates
 * it spans in its constructor. It sits in the domain.service package
 * (namespace.domainService), which the architecture test checks as domain
 * code - it may name no outer layer.
 */
@Service
public class PricingService {

    private final OrderRepositoryPort orders;
    private final ProductRepositoryPort products;

    public PricingService(OrderRepositoryPort orders, ProductRepositoryPort products) {
        this.orders = orders;
        this.products = products;
    }

    // The operation goes here, with orders and products.
}
