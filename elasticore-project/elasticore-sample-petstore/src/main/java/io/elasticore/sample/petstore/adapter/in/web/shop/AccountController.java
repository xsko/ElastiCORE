package io.elasticore.sample.petstore.adapter.in.web.shop;

import io.elasticore.sample.petstore.application.dto.CustomerDTO;
import io.elasticore.sample.petstore.application.port.in.CustomerUseCase;
import io.elasticore.springboot3.security.SessionCurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signing up and being signed in by it, in one request - the shop's choice.
 * Signing in, out and "who am I" are generated from @login and @password
 * (POST /customer/signin, POST /customer/signout, GET /customer/me); who may
 * do what afterwards is the DSL's (@access, @owner), enforced by the
 * generated services.
 */
@RestController
@RequestMapping("/api/petstore/account")
public class AccountController {

    private final CustomerUseCase customers;

    public AccountController(CustomerUseCase customers) {
        this.customers = customers;
    }

    /** A new customer, signed in. The service keeps the role out of a visitor's hands. */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDTO signUp(@Valid @RequestBody CustomerDTO customer, HttpServletRequest request) {
        customer.setCustomerId(null);
        CustomerDTO created = customers.save(customer);
        SessionCurrentUser.signIn(request, created.getCustomerId(), created.getRole().name());
        return created;
    }
}
