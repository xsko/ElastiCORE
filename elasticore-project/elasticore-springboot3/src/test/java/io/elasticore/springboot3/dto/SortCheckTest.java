package io.elasticore.springboot3.dto;

import io.elasticore.springboot3.validation.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * The 3.0 search DTOs list what their sort may name - what the search's response shows - and the generated
 * SortableObject hands each property a request sorts by through SortCheck: one not listed is refused (400,
 * no-such-property) before the query is built, so the order of a value no response shows is not given away.
 */
class SortCheckTest {

    private static final Set<String> SHOWN =
            new LinkedHashSet<String>(Arrays.asList("orderId", "total", "customer.customerId"));

    private static BadRequestException refused(String property) {
        try {
            SortCheck.sortable(property, SHOWN);
        } catch (BadRequestException e) {
            return e;
        }
        return fail("not refused: " + property);
    }

    /** A search DTO on the runtime's interface, as a project the init wizard made has them. */
    private static final class Search implements PageableObject {
        private final String sortCode;
        private final Set<String> sortable;

        Search(String sortCode, Set<String> sortable) {
            this.sortCode = sortCode;
            this.sortable = sortable;
        }

        @Override
        public String getSortCode() {
            return sortCode;
        }

        @Override
        public String getSortColumn() {
            return null;
        }

        @Override
        public Boolean getSortAscending() {
            return null;
        }

        @Override
        public Set<String> sortableProperties() {
            return sortable;
        }

        @Override
        public int getPageNumber() {
            return 0;
        }

        @Override
        public int getPageSize() {
            return 20;
        }
    }

    @Test
    @DisplayName("the runtime's SortableObject keeps a listing search DTO's sort to its list, and any other as it was")
    void theRuntimesInterfaceChecksAListingDto() {
        assertThat(new Search("total-,orderId+", SHOWN).getPageable().getSort().toString())
                .isEqualTo("total: DESC,orderId: ASC");
        assertThatThrownBy(() -> new Search("total-,customer.password+", SHOWN).getPageable())
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("The search cannot be sorted by 'customer.password'");
        // A DTO that lists nothing (2.x code, every other implementation) sorts as it did.
        assertThat(new Search("customer.password+", null).getSort().toString()).isEqualTo("customer.password: ASC");
    }

    @Test
    @DisplayName("a listed property passes as it is")
    void aListedPropertyPasses() {
        assertThat(SortCheck.sortable("total", SHOWN)).isEqualTo("total");
        assertThat(SortCheck.sortable("customer.customerId", SHOWN)).isEqualTo("customer.customerId");
    }

    @Test
    @DisplayName("a search DTO that lists nothing sorts by any property, as before")
    void nothingListedIsAnyProperty() {
        assertThat(SortCheck.sortable("customer.password", null)).isEqualTo("customer.password");
    }

    @Test
    @DisplayName("a property the response does not show is a 400 no-such-property that names it and the listed ones")
    void anUnlistedPropertyIsRefused() {
        BadRequestException refused = refused("customer.password");
        assertThat(refused.getCode()).isEqualTo("no-such-property");
        assertThat(refused.getMessage())
                .isEqualTo(
                        "The search cannot be sorted by 'customer.password'; sort by one of [orderId, total, customer.customerId]");
        // The values a translation says it with: the property and the list.
        assertThat(refused.getArgs()).containsExactly("customer.password", "orderId, total, customer.customerId");
    }

    @Test
    @DisplayName("the case and the spacing count: a near name is not the property")
    void aNearNameIsRefused() {
        assertThatThrownBy(() -> SortCheck.sortable("Total", SHOWN)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SortCheck.sortable(" total", SHOWN)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SortCheck.sortable("", SHOWN)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SortCheck.sortable(null, SHOWN)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("the refused property is repeated short and without line breaks or hidden characters")
    void theRefusedPropertyIsRepeatedSafely() {
        String forged = "a" + (char) 13 + (char) 10 + "FAKE LOG LINE " + (char) 0x202E + "x".repeat(60);
        BadRequestException refused = refused(forged);
        assertThat(refused.getMessage()).startsWith("The search cannot be sorted by 'a??FAKE LOG LINE ?xxx");
        assertThat(refused.getMessage()).doesNotContain("\r", "\n", String.valueOf((char) 0x202E));
        assertThat(refused.getArgs()[0].toString()).hasSize(43).endsWith("...");
        // A pattern in the request is a value, never read as one.
        BadRequestException braces = refused("{0}'{1}");
        assertThat(braces.getMessage()).contains("'{0}'{1}'");
    }
}
