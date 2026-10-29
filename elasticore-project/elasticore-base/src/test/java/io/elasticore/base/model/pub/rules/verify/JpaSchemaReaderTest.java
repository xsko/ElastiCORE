package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.pub.rules.schema.Column;
import io.elasticore.base.model.pub.rules.schema.ColumnType;
import io.elasticore.base.model.pub.rules.schema.Schema;
import io.elasticore.base.model.pub.rules.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tables entity sources map to, by Spring Boot's and Hibernate's rules -
 * what the samples' ddl-auto=validate tests then check against Hibernate
 * itself.
 */
public class JpaSchemaReaderTest {

    private Path dir;

    @AfterEach
    void clean() throws IOException {
        if (dir == null) return;
        try (Stream<Path> all = Files.walk(dir)) {
            List<Path> paths = new ArrayList<Path>();
            all.forEach(paths::add);
            for (int i = paths.size() - 1; i >= 0; i--) Files.deleteIfExists(paths.get(i));
        }
    }

    private void source(String path, String text) throws IOException {
        File f = new File(dir.toFile(), "src/main/java/" + path);
        assertTrue(f.getParentFile().isDirectory() || f.getParentFile().mkdirs());
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private Schema read(String... entities) {
        JpaSchemaReader r = new JpaSchemaReader()
                .readSettings(Collections.singletonList(new File(dir.toFile(), "src/main/resources")))
                .read(Collections.singletonList(new File(dir.toFile(), "src/main/java")));
        return r.schema(Arrays.asList(entities));
    }

    @Test
    public void namesTypesAndRelationsAreHibernates() throws Exception {
        dir = Files.createTempDirectory("jpa-reader");
        source("acme/Base.java", String.join("\n",
                "package acme;",
                "import jakarta.persistence.*;",
                "@MappedSuperclass",
                "public abstract class Base {",
                "    @Column(name = \"created_by\", length = 50) private String createdBy;",
                "}"));
        source("acme/Address.java", String.join("\n",
                "package acme;",
                "import jakarta.persistence.*;",
                "import jakarta.validation.constraints.*;",
                "@Embeddable",
                "public class Address {",
                "    @NotNull @Size(max = 40) private String street;",
                "    @Column(nullable = false, length = 2) private String countryCode;",
                "}"));
        source("acme/Tag.java", String.join("\n",
                "package acme;",
                "import jakarta.persistence.*;",
                "@Entity",
                "public class Tag {",
                "    @Id private String tagCode;",
                "}"));
        source("acme/CustomerAccount.java", String.join("\n",
                "package acme;",
                "import jakarta.persistence.*;",
                "import jakarta.validation.constraints.*;",
                "import java.math.BigDecimal;",
                "import java.time.*;",
                "import java.util.List;",
                "@Entity",
                "@Table(name = \"crm_customer\", indexes = {@Index(name = \"ix_crm_email\", columnList = \"emailAddress\", unique = true)})",
                "@org.hibernate.envers.Audited",
                "public class CustomerAccount extends Base {",
                "    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long accountId;",
                "    @NotNull @Size(max = 80) private String emailAddress;",
                "    private int loginCount;",
                "    @Column(precision = 12, scale = 2) private BigDecimal balance;",
                "    private Instant joinedAt;",
                "    @Lob private String notes;",
                "    @Column(columnDefinition = \"TEXT\") private String profile;",
                "    @Embedded private Address home;",
                "    @Embedded @AttributeOverrides({@AttributeOverride(name = \"street\", column = @Column(name = \"work_street\")),",
                "        @AttributeOverride(name = \"countryCode\", column = @Column(name = \"work_country\"))}) private Address work;",
                "    @ManyToOne private CustomerAccount referrer;",
                "    @ManyToMany private List<Tag> tags;",
                "    @org.hibernate.envers.NotAudited @Transient private String scratch;",
                "    @Version private Long version;",
                "}"));
        File res = new File(dir.toFile(), "src/main/resources");
        assertTrue(res.mkdirs());
        Files.write(new File(res, "application.yml").toPath(), String.join("\n",
                "spring:", "  jpa:", "    properties:", "      org.hibernate.envers.audit_table_suffix: _hist", "").getBytes(StandardCharsets.UTF_8));

        Schema s = read("acme.CustomerAccount", "acme.Tag");
        Table c = s.table("crm_customer");
        assertNotNull(c, "the @Table name");
        assertEquals(Arrays.asList("account_id"), c.primaryKey());
        Column id = c.column("account_id");
        assertTrue(id.identity && !id.nullable);
        // The mapped superclass's column, first.
        assertEquals("created_by", c.columns().get(0).name);
        // @Size gives the length and @NotNull the NOT NULL, as Hibernate applies Bean Validation to its DDL.
        assertEquals(80, c.column("email_address").length);
        assertFalse(c.column("email_address").nullable);
        // A primitive cannot be null.
        assertFalse(c.column("login_count").nullable);
        assertEquals(12, c.column("balance").precision);
        assertEquals(ColumnType.TIMESTAMP_TZ, c.column("joined_at").type);
        assertEquals(ColumnType.LONG_TEXT, c.column("notes").type);
        assertEquals(ColumnType.UNBOUNDED_TEXT, c.column("profile").type);
        // An embedded value: its columns; an optional value's Bean Validation is not a NOT NULL, its own @Column is.
        assertTrue(c.column("street").nullable);
        assertFalse(c.column("country_code").nullable);
        assertNotNull(c.column("work_street"));
        assertNotNull(c.column("work_country"));
        // A to-one: attribute_targetkey, with its foreign key.
        assertEquals(ColumnType.BIGINT, c.column("referrer_account_id").type);
        assertTrue(c.foreignKeys().stream().anyMatch(fk -> fk.columns.equals(Collections.singletonList("referrer_account_id"))));
        assertNull(c.column("scratch"));
        assertTrue(c.indexes().stream().anyMatch(i -> i.name.equals("ix_crm_email") && i.unique
                && i.columns.equals(Collections.singletonList("email_address"))));

        // The join table: owning table _ attribute; its columns: entity _ key, attribute _ target key.
        Table join = s.table("crm_customer_tags");
        assertNotNull(join);
        assertNotNull(join.column("customer_account_account_id"));
        assertNotNull(join.column("tags_tag_code"));

        // Envers: the suffix the application set, the revision columns, the revision table and its sequence.
        Table audit = s.table("crm_customer_hist");
        assertNotNull(audit, s.tables().toString());
        assertNotNull(audit.column("rev"));
        assertNotNull(audit.column("revtype"));
        assertEquals(Arrays.asList("account_id", "rev"), audit.primaryKey());
        assertNotNull(s.table("crm_customer_tags_hist"));
        assertNotNull(s.table("revinfo"));
        assertTrue(s.sequences().stream().anyMatch(q -> q.name.equals("revinfo_seq")));
    }

    @Test
    public void inheritanceCollectionsAndRelationsAsHibernateAndEnversMakeThem() throws Exception {
        dir = Files.createTempDirectory("jpa-reader-inh");
        source("acme/Party.java", String.join("\n",
                "package acme;",
                "import jakarta.persistence.*;",
                "@Entity @Inheritance(strategy = InheritanceType.JOINED) @org.hibernate.envers.Audited",
                "public class Party {",
                "    @Id private Long partyId;",
                "    private String label;",
                "}"));
        source("acme/Person.java", String.join("\n",
                "package acme;",
                "import jakarta.persistence.*;",
                "import java.util.*;",
                "@Entity @org.hibernate.envers.Audited",
                "public class Person extends Party {",
                "    private String nickname;",
                "    @ElementCollection private Set<String> phones;",
                "    @ElementCollection @jakarta.validation.constraints.Size(max = 3) @Column(nullable = false, length = 10)",
                "    private Set<String> codes;",
                "    @ElementCollection @OrderColumn private List<Slot> slots;",
                "    @OneToOne private Badge badge;",
                "    @ManyToMany private Set<Club> clubs;",
                "    @ManyToMany private List<Club> visited;",
                "    @OneToMany @JoinColumn(name = \"owner_id\") private List<Badge> spare;",
                "}"));
        source("acme/Slot.java", String.join("\n",
                "package acme;",
                "import jakarta.persistence.*;",
                "@Embeddable public class Slot { private Integer hour; }"));
        source("acme/Badge.java", String.join("\n",
                "package acme;",
                "import jakarta.persistence.*;",
                "@Entity public class Badge { @Id private Long badgeId; }"));
        source("acme/Club.java", String.join("\n",
                "package acme;",
                "import jakarta.persistence.*;",
                "import java.util.*;",
                "@Entity public class Club {",
                "    @Id private Long clubId;",
                "    @ManyToMany(mappedBy = \"clubs\") private Set<Person> members;",
                "}"));
        source("acme/Vehicle.java", String.join("\n",
                "package acme;",
                "import jakarta.persistence.*;",
                "@Entity @org.hibernate.envers.Audited",
                "public class Vehicle { @Id private Long vehicleId; }"));
        source("acme/Truck.java", String.join("\n",
                "package acme;",
                "import jakarta.persistence.*;",
                "@Entity @org.hibernate.envers.Audited",
                "public class Truck extends Vehicle { private Integer axles; }"));

        source("acme/Ledger.java", String.join("\n",
                "package acme;",
                "import jakarta.persistence.*;",
                "import java.util.*;",
                "@Entity @Table(name = \"ledger\", schema = \"acct\")",
                "public class Ledger { @Id private Long ledgerId; @ManyToMany private Set<Club> clubs; }"));
        source("acme/Account.java", String.join("\n",
                "package acme;",
                "import jakarta.persistence.*;",
                "@Entity @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)",
                "public abstract class Account { @Id private Long accountId; private String owner; }"));
        source("acme/Savings.java", String.join("\n",
                "package acme;",
                "import jakarta.persistence.*;",
                "@Entity public class Savings extends Account { private Integer rate; }"));

        Schema s = read("acme.Party", "acme.Person", "acme.Badge", "acme.Club", "acme.Vehicle", "acme.Truck",
                "acme.Ledger", "acme.Account", "acme.Savings");

        // A join table of an entity in a schema of its own is in the default schema, named by the table alone.
        assertNotNull(s.table("ledger_clubs"), s.tables().toString());
        // TABLE_PER_CLASS: the subclass's table has every column of the entities above it.
        Table savings = s.table("savings");
        assertNotNull(savings, s.tables().toString());
        assertNotNull(savings.column("owner"));
        assertNotNull(savings.column("rate"));
        assertEquals(Arrays.asList("account_id"), savings.primaryKey());

        // JOINED: the subclass's audit table has its key and own columns; the revision's type is in the root's.
        Table personAud = s.table("person_aud");
        assertNotNull(personAud, s.tables().toString());
        assertNotNull(personAud.column("party_id"));
        assertNotNull(personAud.column("nickname"));
        assertNull(personAud.column("revtype"));
        assertEquals(Arrays.asList("party_id", "rev"), personAud.primaryKey());
        assertTrue(personAud.foreignKeys().stream().anyMatch(fk -> fk.table.equals("party_aud")));
        assertNotNull(s.table("party_aud").column("revtype"));

        // SINGLE_TABLE: the subclass's columns and the discriminator in the root's table and its audit table.
        assertNotNull(s.table("vehicle").column("axles"));
        assertNotNull(s.table("vehicle_aud").column("axles"));
        assertNotNull(s.table("vehicle_aud").column("dtype"));

        // Element collections: entity _ attribute, entity _ key, the value - nullable, as Hibernate makes it, so a set
        // of values that may be empty has no key; one whose values are required is keyed by all.
        Table phones = s.table("person_phones");
        assertNotNull(phones);
        assertNotNull(phones.column("person_party_id"));
        assertTrue(phones.column("phones").nullable);
        assertTrue(phones.primaryKey().isEmpty());
        Table codes = s.table("person_codes");
        assertEquals(Arrays.asList("person_party_id", "codes"), codes.primaryKey());
        assertEquals(10, codes.column("codes").length, "the @Size on the collection is not the value's");
        Table slots = s.table("person_slots");
        assertNotNull(slots.column("hour"));
        assertEquals(Arrays.asList("person_party_id", "slots_order"), slots.primaryKey());
        assertNotNull(s.table("person_phones_aud"));

        // An owning one-to-one's column is unique.
        assertTrue(s.table("person").indexes().stream().anyMatch(i -> i.unique
                && i.columns.equals(Collections.singletonList("badge_badge_id"))));
        // Many-to-many: the owner's column is named by the attribute mapping it back; a set's pairs key the table,
        // a list's do not.
        Table clubs = s.table("person_clubs");
        assertNotNull(clubs.column("members_party_id"), clubs.columns().toString());
        assertEquals(Arrays.asList("members_party_id", "clubs_club_id"), clubs.primaryKey());
        Table visited = s.table("person_visited");
        assertNotNull(visited.column("person_party_id"));
        assertTrue(visited.primaryKey().isEmpty());
        // A one-to-many by a join column: the key in the target's table, pointing back.
        Table badge = s.table("badge");
        assertNotNull(badge.column("owner_id"));
        assertTrue(badge.foreignKeys().stream().anyMatch(fk -> fk.columns.equals(Collections.singletonList("owner_id"))
                && fk.table.equals("person")));
    }

    @Test
    public void kotlinEntitiesAreReadToo() throws Exception {
        dir = Files.createTempDirectory("jpa-reader-kt");
        File f = new File(dir.toFile(), "src/main/kotlin/acme/Order.kt");
        assertTrue(f.getParentFile().mkdirs());
        Files.write(f.toPath(), String.join("\n",
                "package acme",
                "import jakarta.persistence.*",
                "import jakarta.validation.constraints.NotNull",
                "@Entity",
                "@Table(name = \"shop_order\")",
                "class Order {",
                "    @Id",
                "    @GeneratedValue(strategy = GenerationType.IDENTITY)",
                "    var orderId: Long? = null",
                "    @field:NotNull",
                "    @Column(name = \"order_no\", length = 20)",
                "    var orderNo: String? = null",
                "    var lineCount: Int = 0",
                "    @Convert(converter = Status.EntityConverter::class)",
                "    @Column(length = 1)",
                "    var status: Status? = null",
                "}",
                "enum class Status {",
                "    NEW;",
                "    class EntityConverter : AttributeConverter<Status, String> {",
                "        override fun convertToDatabaseColumn(a: Status?): String? = null",
                "        override fun convertToEntityAttribute(d: String?): Status? = null",
                "    }",
                "}").getBytes(StandardCharsets.UTF_8));
        JpaSchemaReader r = new JpaSchemaReader().read(Collections.singletonList(new File(dir.toFile(), "src/main/kotlin")));
        Table t = r.schema(Collections.singletonList("acme.Order")).table("shop_order");
        assertNotNull(t, r.warnings().toString());
        assertTrue(t.column("order_id").identity);
        assertEquals(20, t.column("order_no").length);
        assertFalse(t.column("order_no").nullable, "@field:NotNull");
        // A Kotlin Int that cannot be null is a JVM int: NOT NULL.
        assertFalse(t.column("line_count").nullable);
        assertEquals(ColumnType.TEXT, t.column("status").type);
        assertEquals(1, t.column("status").length);
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
    }
}
