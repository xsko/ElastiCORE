package io.elasticore.sample.petstore.adapter.in.web;

import io.elasticore.springboot3.http.PortCallException;
import io.elasticore.springboot3.validation.ConflictException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test only: refusals hand code throws, the way a rule in a service would -
 * in the generated PetstoreApiErrors' package, so it answers for them
 * (ShopFlowTest.aRefusalHandCodeThrowsIsSaidByItsCode).
 */
@RestController
@RequestMapping("/api/petstore/test-refusal")
public class HandRefusalController {

    @PostMapping("/conflict")
    public String conflict() {
        throw new ConflictException("loan.no-copy-left", "No copy of {0} is left to lend", "978-0-00");
    }

    @PostMapping("/plain")
    public String plain() {
        throw new ConflictException("The loan can't be returned twice");
    }

    @PostMapping("/courier")
    public String courier() {
        throw new PortCallException("The courier did not answer", new java.io.IOException("connection refused"));
    }
}
