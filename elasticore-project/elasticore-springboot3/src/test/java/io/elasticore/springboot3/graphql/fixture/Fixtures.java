package io.elasticore.springboot3.graphql.fixture;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.elasticore.springboot3.domain.Money;
import io.elasticore.springboot3.graphql.GraphQlDomain;
import io.elasticore.springboot3.validation.ConflictException;
import io.elasticore.springboot3.validation.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Two domains' generated-style controllers and the DTOs they take and answer. */
public final class Fixtures {

    private Fixtures() {}

    public enum Grade {
        GOLD,
        SILVER
    }

    /** A value object: getters, no setters - sent into its fields, as Jackson reads it. */
    public static class AddressDTO {
        private String city;
        private String zip;

        public AddressDTO() {}

        public String getCity() {
            return city;
        }

        public String getZip() {
            return zip;
        }
    }

    public static class MemberDTO {
        private Long id;
        private String name;
        private Grade grade;
        private BigDecimal rate;
        private LocalDate joined;
        private Money balance;

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private String password;

        private AddressDTO address;
        private List<String> tags;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Grade getGrade() {
            return grade;
        }

        public void setGrade(Grade grade) {
            this.grade = grade;
        }

        public BigDecimal getRate() {
            return rate;
        }

        public void setRate(BigDecimal rate) {
            this.rate = rate;
        }

        public LocalDate getJoined() {
            return joined;
        }

        public void setJoined(LocalDate joined) {
            this.joined = joined;
        }

        public Money getBalance() {
            return balance;
        }

        public void setBalance(Money balance) {
            this.balance = balance;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public AddressDTO getAddress() {
            return address;
        }

        public void setAddress(AddressDTO address) {
            this.address = address;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    public static class MemberSrchDTO {
        private String name;
        private Integer pageNumber;
        private Integer pageSize;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getPageNumber() {
            return pageNumber;
        }

        public void setPageNumber(Integer pageNumber) {
            this.pageNumber = pageNumber;
        }

        public Integer getPageSize() {
            return pageSize;
        }

        public void setPageSize(Integer pageSize) {
            this.pageSize = pageSize;
        }
    }

    public static class ItemDTO {
        private Long id;
        private String title;
        private AddressDTO origin;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public AddressDTO getOrigin() {
            return origin;
        }

        public void setOrigin(AddressDTO origin) {
            this.origin = origin;
        }
    }

    @Controller
    @GraphQlDomain("shop")
    public static class MemberGraphQlController {
        private final Map<Long, MemberDTO> rows = new ConcurrentHashMap<>();
        private long next = 1;

        @QueryMapping
        public MemberDTO member(@Argument("id") Long id) {
            return rows.get(id);
        }

        @QueryMapping("members")
        public Page<MemberDTO> search(@Argument("search") MemberSrchDTO search) {
            List<MemberDTO> found = new ArrayList<>();
            for (MemberDTO m : rows.values()) {
                if (search == null
                        || search.getName() == null
                        || search.getName().equals(m.getName())) found.add(m);
            }
            int size = search == null || search.getPageSize() == null ? 20 : search.getPageSize();
            return new PageImpl<>(found, PageRequest.of(0, size), found.size());
        }

        @MutationMapping
        public MemberDTO createMember(@Argument("input") MemberDTO input) {
            if ("taken".equals(input.getName())) throw new ConflictException("member.name.taken", "The name is taken");
            input.setId(next++);
            rows.put(input.getId(), input);
            return input;
        }

        @MutationMapping
        public boolean deleteMember(@Argument("id") long id) {
            if (rows.remove(id) == null) throw new NotFoundException("Member not found");
            return true;
        }
    }

    @Controller
    @GraphQlDomain("catalog")
    public static class ItemGraphQlController {
        @QueryMapping
        public ItemDTO item(@Argument("id") Long id) {
            ItemDTO item = new ItemDTO();
            item.setId(id);
            item.setTitle("Lamp");
            return item;
        }
    }

    /** A second domain naming an operation the first one has. */
    @Controller
    @GraphQlDomain("billing")
    public static class OtherMemberController {
        @QueryMapping
        public ItemDTO member(@Argument("id") Long id) {
            return null;
        }
    }

    /** Types a GraphQL schema cannot be made of. */
    public interface Shape {
        String getName();
    }

    public static class Empty {}

    @Controller
    @GraphQlDomain("odd")
    public static class OddController {
        @QueryMapping
        public Shape shape(@Argument("id") Long id) {
            return null;
        }

        @QueryMapping
        public Empty empty() {
            return null;
        }

        @QueryMapping
        public java.util.List<Grade> grades(@Argument("page") Page<MemberDTO> page) {
            return null;
        }

        @QueryMapping
        public Thread thread() {
            return null;
        }
    }

    /** Another class with the simple name of the shop's MemberDTO. */
    public static final class Other {
        private Other() {}

        public static class MemberDTO {
            private String code;

            public String getCode() {
                return code;
            }

            public void setCode(String code) {
                this.code = code;
            }
        }

        @Controller
        @GraphQlDomain("billing")
        public static class BillingController {
            @QueryMapping
            public MemberDTO payer(@Argument("code") String code) {
                return null;
            }
        }
    }
}
