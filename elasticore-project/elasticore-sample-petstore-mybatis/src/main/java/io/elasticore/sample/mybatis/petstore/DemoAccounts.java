package io.elasticore.sample.mybatis.petstore;

import io.elasticore.sample.mybatis.petstore.application.CustomerService;
import io.elasticore.sample.mybatis.petstore.application.dto.CustomerDTO;
import io.elasticore.sample.mybatis.petstore.application.dto.CustomerSrchDTO;
import io.elasticore.sample.mybatis.petstore.domain.model.Address;
import io.elasticore.sample.mybatis.petstore.domain.model.UserRole;
import io.elasticore.springboot3.security.CurrentUser;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * The two accounts the Java EE pet store shipped with - admin/admin and
 * user/user - made through the service, so the passwords are hashed as any
 * other customer's are. A sample's convenience: turned off with
 * petstore.demo-accounts=false.
 */
@Component
@ConditionalOnProperty(name = "petstore.demo-accounts", havingValue = "true")
class DemoAccounts implements ApplicationRunner {

    private final CustomerService customers;

    DemoAccounts(CustomerService customers) {
        this.customers = customers;
    }

    /** As the application itself: only it may make an administrator. */
    @Override
    public void run(ApplicationArguments args) {
        CurrentUser.runAsSystem(() -> {
            create("admin", "Admin", "The Admin Alley", UserRole.ADMIN);
            create("user", "User", "The User Boulevard", UserRole.USER);
        });
    }

    private void create(String login, String name, String street, UserRole role) {
        CustomerSrchDTO existing = new CustomerSrchDTO();
        existing.setLogin(login);
        if (customers.countBySearch(existing) > 0) return;

        CustomerDTO c = new CustomerDTO();
        c.setLogin(login);
        c.setPassword(login);
        c.setFirstName(name);
        c.setLastName(name);
        c.setEmail(login + "@petstore.com");
        c.setTelephone("+1 123 3564 4567");
        c.setDateOfBirth(LocalDate.of(1970, 1, 1));
        c.setRole(role);
        c.setHomeAddress(address(street));
        customers.save(c);
    }

    private static Address address(String street) {
        Address a = new Address();
        a.setStreet1(street);
        a.setCity("San Francisco");
        a.setState("CA");
        a.setZipcode("87653");
        a.setCountryCode("US");
        return a;
    }
}
