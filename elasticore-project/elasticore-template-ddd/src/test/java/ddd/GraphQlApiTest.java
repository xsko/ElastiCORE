package ddd;

import io.elasticore.sample.ddd.billing.application.ReceiptService;
import io.elasticore.sample.ddd.billing.application.dto.ReceiptDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * shop and billing turn GraphQL on (env.yml graphql.enable): one endpoint,
 * /graphql, serves both domains' operations from the schema the build wrote
 * out of the compiled controllers (graphql/*.graphqls), over the same use
 * cases the REST API calls - validation and refusals included.
 */
@SpringBootTest
@AutoConfigureHttpGraphQlTester
class GraphQlApiTest {

    @Autowired private HttpGraphQlTester graphql;
    @Autowired private ReceiptService receipts;

    @Test
    @DisplayName("a category is created, read and searched through GraphQL")
    void categoryRoundTrip() {
        Long id = graphql.document("mutation { createCategory(input: {name: \"GraphQL books\", sortOrder: 3,"
                        + " active: true}) { catId name } }")
                .execute()
                .path("createCategory.name").entity(String.class).isEqualTo("GraphQL books")
                .path("createCategory.catId").entity(Long.class).get();

        graphql.document("{ category(catId: " + id + ") { name sortOrder active createDate } }")
                .execute()
                .path("category.name").entity(String.class).isEqualTo("GraphQL books")
                .path("category.sortOrder").entity(Integer.class).isEqualTo(3)
                .path("category.createDate").entity(String.class).satisfies(d -> assertThat(d).isNotBlank());

        graphql.document("{ categories(search: {name: \"GraphQL books\"}) { totalElements content { catId } } }")
                .execute()
                .path("categories.totalElements").entity(Long.class).isEqualTo(1L)
                .path("categories.content[0].catId").entity(Long.class).isEqualTo(id);
    }

    @Test
    @DisplayName("an embedded value without setters is sent in a mutation and read back")
    void valueObjectInput() {
        Long id = graphql.document("mutation { createCustomer(input: {firstName: \"Ada\", lastName: \"Lovelace\","
                        + " email: \"ada.graphql@example.com\", dateOfBirth: \"1990-12-10\","
                        + " billingAddress: {city: \"London\", zipCode: \"W1\"}}) { customerId } }")
                .execute()
                .path("createCustomer.customerId").entity(Long.class).get();

        graphql.document("{ customer(customerId: " + id + ") { dateOfBirth billingAddress { city zipCode country } } }")
                .execute()
                .path("customer.dateOfBirth").entity(String.class).isEqualTo("1990-12-10")
                .path("customer.billingAddress.city").entity(String.class).isEqualTo("London")
                .path("customer.billingAddress.zipCode").entity(String.class).isEqualTo("W1");
    }

    @Test
    @DisplayName("a taken unique value is a conflict whose message names neither the value nor the SQL")
    void aTakenUniqueValueNamesNoValue() {
        String create = "mutation { createCustomer(input: {firstName: \"Grace\", lastName: \"Hopper\","
                + " email: \"grace.taken@example.com\", billingAddress: {city: \"Arlington\", zipCode: \"22201\"}})"
                + " { customerId } }";
        graphql.document(create).execute().path("createCustomer.customerId").entity(Long.class).get();

        graphql.document(create)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getExtensions()).containsEntry("status", 409);
                    assertThat(errors.get(0).getMessage())
                            .as("the message of %s", errors.get(0))
                            .doesNotContain("grace.taken@example.com")
                            .doesNotContainIgnoringCase("insert")
                            .doesNotContainIgnoringCase("idx_shop_cust_email");
                });
    }

    @Test
    @DisplayName("billing's operations are served at the same endpoint, read and search only")
    void secondDomainSameEndpoint() {
        ReceiptDTO saved = receipts.save(ReceiptDTO.builder()
                .orderId("order-graphql-1").amount(new BigDecimal("42.50")).build());

        graphql.document("{ receipt(receiptId: " + saved.getReceiptId() + ") { orderId amount } }")
                .execute()
                .path("receipt.orderId").entity(String.class).isEqualTo("order-graphql-1")
                .path("receipt.amount").entity(BigDecimal.class)
                .satisfies(a -> assertThat(a).isEqualByComparingTo("42.50"));

        // @expose(methods="read,search"): no mutation of receipts exists in the schema.
        graphql.document("mutation { deleteReceipt(receiptId: 1) }")
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty()
                        .allSatisfy(e -> assertThat(e.getMessage()).contains("deleteReceipt")));
    }

    @Test
    @DisplayName("a command is a mutation: refused for an order that is not there, validated like a body")
    void commands() {
        graphql.document("mutation { cancelOrder(orderId: \"no-such-order\") { orderId } }")
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getErrorType().toString()).isEqualTo("NOT_FOUND");
                    assertThat(errors.get(0).getExtensions()).containsEntry("status", 404);
                });

        graphql.document("mutation { shipOrder(orderId: \"any-order\", command: {}) { orderId } }")
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getErrorType().toString()).isEqualTo("BAD_REQUEST");
                });
    }
}
