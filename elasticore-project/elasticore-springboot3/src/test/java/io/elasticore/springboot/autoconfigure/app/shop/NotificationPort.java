package io.elasticore.springboot.autoconfigure.app.shop;

import io.elasticore.runtime.port.ExternalService;

/** A port whose simple name another package's port has too (billing.NotificationPort). */
@ExternalService(protocol = "http", id = "shop.NotificationPort", url = "http://localhost:1")
public interface NotificationPort {
    String notifyShop(String message);
}
