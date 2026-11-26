package io.elasticore.blueprint.domain.shop.domainservice

import io.elasticore.blueprint.domain.shop.repository.OrderRepositoryPort
import io.elasticore.blueprint.domain.shop.repository.ProductRepositoryPort
import org.springframework.stereotype.Service

/**
 * What an order costs once the products it names are priced: an operation of
 * no single aggregate, so a domain service.
 *
 * Plain Spring code, written by hand: the repository ports of the aggregates
 * it spans in its constructor, in the domainService package the architecture
 * test checks as domain code.
 */
@Service
class PricingService(
    private val orders: OrderRepositoryPort,
    private val products: ProductRepositoryPort,
) {
    // The operation goes here, with orders and products.
}
