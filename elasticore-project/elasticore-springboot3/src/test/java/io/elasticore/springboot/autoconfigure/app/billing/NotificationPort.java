package io.elasticore.springboot.autoconfigure.app.billing;

import io.elasticore.runtime.port.ExternalService;

/** A port whose simple name another package's port has too (shop.NotificationPort). */
@ExternalService(protocol = "http", id = "billing.NotificationPort", url = "http://localhost:1")
public interface NotificationPort {
    String notifyBilling(String message);
}
