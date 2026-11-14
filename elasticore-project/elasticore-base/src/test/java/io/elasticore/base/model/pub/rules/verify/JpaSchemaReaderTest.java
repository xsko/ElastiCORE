package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.pub.rules.schema.Column;
import io.elasticore.base.model.pub.rules.schema.ColumnType;
import io.elasticore.base.model.pub.rules.schema.Database;
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
        assertRevisionKeyAsHibernateNumbersIt(s);
    }

    /**
     * Envers' revision number is Hibernate's native key: an identity column on H2 and SQL Server - as
     * Hibernate 6.5 itself writes it, "rev integer generated by default as identity" - and revinfo_seq on
     * PostgreSQL and Oracle. A plain column with the sequence on H2 refused every audited write.
     */
    private static void assertRevisionKeyAsHibernateNumbersIt(io.elasticore.base.model.pub.rules.schema.Schema s) {
        String h2 = io.elasticore.base.model.pub.rules.schema.SchemaWriter.body(s, Database.H2);
        assertTrue(h2.contains("    rev INTEGER GENERATED BY DEFAULT AS IDENTITY NOT NULL,"), h2);
        assertFalse(h2.contains("revinfo_seq"), h2);
        String sqlServer = io.elasticore.base.model.pub.rules.schema.SchemaWriter.body(s, Database.SQLSERVER);
        assertTrue(sqlServer.contains("    rev INT IDENTITY(1,1) NOT NULL,"), sqlServer);
        assertFalse(sqlServer.contains("revinfo_seq"), sqlServer);
        for (Database db : new Database[] {Database.POSTGRESQL, Database.ORACLE}) {
            String sql = io.elasticore.base.model.pub.rules.schema.SchemaWriter.body(s, db);
            assertTrue(sql.contains("CREATE SEQUENCE revinfo_seq START WITH 1 INCREMENT BY 50;"), sql);
            assertTrue(sql.contains("    rev " + db.type(s.table("revinfo").column("rev")) + " NOT NULL,"), sql);
        }

        // A first migration: the sequence for PostgreSQL and Oracle only; the state keeps the key's kind.
        io.elasticore.base.model.pub.rules.schema.Schema none = new io.elasticore.base.model.pub.rules.schema.Schema();
        java.util.List<io.elasticore.base.model.pub.rules.schema.SchemaDiff.Change> first =
                io.elasticore.base.model.pub.rules.schema.SchemaDiff.between(none, s);
        io.elasticore.base.model.pub.rules.schema.SchemaDiff.Change sequence = first.stream()
                .filter(c -> c.description.equals("sequence revinfo_seq"))
                .findFirst()
                .get();
        assertFalse(sequence.on(Database.H2) || sequence.on(Database.SQLSERVER));
        assertTrue(sequence.on(Database.POSTGRESQL) && sequence.on(Database.ORACLE));
        io.elasticore.base.model.pub.rules.schema.Schema state =
                io.elasticore.base.model.pub.rules.schema.Schema.fromJson(s.toJson());
        assertTrue(state.table("revinfo").column("rev").nativeKey);
        assertTrue(state.sequences().stream().anyMatch(q -> q.name.equals("revinfo_seq") && q.nativeOnly));
        assertTrue(io.elasticore.base.model.pub.rules.schema.SchemaDiff.between(state, s)
                .isEmpty());

        // A history built before: the key is the database's now on H2 and SQL Server - said there, for review.
        String old = s.toJson()
                .replace("\"nativeKey\" : true", "\"nativeKey\" : false")
                .replace("\"nativeOnly\" : true", "\"nativeOnly\" : false");
        java.util.List<io.elasticore.base.model.pub.rules.schema.SchemaDiff.Change> later =
                io.elasticore.base.model.pub.rules.schema.SchemaDiff.between(
                        io.elasticore.base.model.pub.rules.schema.Schema.fromJson(old), s);
        assertEquals(1, later.size(), later.toString());
        io.elasticore.base.model.pub.rules.schema.SchemaDiff.Change review = later.get(0);
        assertFalse(review.safe);
        assertTrue(review.description.startsWith("revinfo.rev is now generated by the database"), review.description);
        assertTrue(review.on(Database.H2) && review.on(Database.SQLSERVER));
        assertFalse(review.on(Database.POSTGRESQL) || review.on(Database.ORACLE));
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

    /** Whoever wrote it: an abstract class under TABLE_PER_CLASS has no rows of its own, and Hibernate makes no table. */
    @Test
    public void aHandWrittenAbstractRootHasNoTableAndNoForeignKeyToIt() throws Exception {
        dir = Files.createTempDirectory("jpa-reader-abstract");
        source(
                "acme/Account.java",
                String.join(
                        "\n",
                        "package acme;",
                        "import jakarta.persistence.*;",
                        "@Entity @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)",
                        "public abstract class Account { @Id private Long accountId; private String owner; }"));
        source(
                "acme/Savings.java",
                String.join(
                        "\n",
                        "package acme;",
                        "import jakarta.persistence.*;",
                        "@Entity public class Savings extends Account { private Integer rate; }"));
        source(
                "acme/Transfer.java",
                String.join(
                        "\n",
                        "package acme;",
                        "import jakarta.persistence.*;",
                        "@Entity public class Transfer {",
                        "    @Id private Long transferId;",
                        "    @ManyToOne private Account account;",
                        "    @ManyToOne private Savings savings;",
                        "}"));
        source(
                "acme/Party.java",
                String.join(
                        "\n",
                        "package acme;",
                        "import jakarta.persistence.*;",
                        "@Entity @Inheritance(strategy = InheritanceType.JOINED)",
                        "public abstract class Party { @Id private Long partyId; }"));
        source(
                "acme/Person.java",
                String.join(
                        "\n",
                        "package acme;",
                        "import jakarta.persistence.*;",
                        "@Entity public class Person extends Party { private String nickname; }"));
        source(
                "acme/Contract.java",
                String.join(
                        "\n",
                        "package acme;",
                        "import jakarta.persistence.*;",
                        "@Entity public class Contract { @Id private Long contractId; @ManyToOne private Party party; }"));

        Schema s = read("acme.Account", "acme.Savings", "acme.Transfer", "acme.Party", "acme.Person", "acme.Contract");

        assertNull(s.table("account"), s.tables().toString());
        assertNotNull(s.table("savings").column("owner"));
        Table transfer = s.table("transfer");
        assertNotNull(transfer.column("account_account_id"), "the column stays, without a key to point at");
        assertEquals(1, transfer.foreignKeys().size(), "only the one to a table that is there");
        assertTrue(transfer.foreignKeys().stream().anyMatch(fk -> fk.table.equals("savings")));

        // JOINED gives the root a table, and so a foreign key to point at.
        assertNotNull(s.table("party"), s.tables().toString());
        assertTrue(s.table("contract").foreignKeys().stream().anyMatch(fk -> fk.table.equals("party")));
    }

    /** Hibernate names a sequence it is not told the name of after the root's table, not the entity. */
    @Test
    public void anUnnamedSequenceIsTheTablesName() throws Exception {
        dir = Files.createTempDirectory("jpa-reader-seq");
        source(
                "acme/Tag.java",
                String.join(
                        "\n",
                        "package acme;",
                        "import jakarta.persistence.*;",
                        "@Entity @Table(name = \"ref_tag\") @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)",
                        "public abstract class Tag { @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) private Long tagId; }"));
        source(
                "acme/ColorTag.java",
                String.join(
                        "\n",
                        "package acme;",
                        "import jakarta.persistence.*;",
                        "@Entity @Table(name = \"ref_color_tag\") public class ColorTag extends Tag { private String rgb; }"));
        source(
                "acme/Ticket.java",
                String.join(
                        "\n",
                        "package acme;",
                        "import jakarta.persistence.*;",
                        // A schema stays out of the name, as it did.
                        "@Entity @Table(schema = \"crm\") public class Ticket {",
                        "    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE) private Long ticketId;",
                        "}"));

        Schema s = read("acme.Tag", "acme.ColorTag", "acme.Ticket");

        java.util.List<String> names = new java.util.ArrayList<String>();
        s.sequences().forEach(q -> names.add(q.name));
        assertEquals(java.util.Arrays.asList("ref_tag_seq", "ticket_seq"), names);
    }

    @Test
    public void anOrmXmlNamesANestedConverterByItsBinaryName() throws Exception {
        dir = Files.createTempDirectory("jpa-reader-orm");
        // mapping: xml - the class says nothing of JPA; its orm.xml does.
        source(
                "acme/Shipment.java",
                String.join(
                        "\n",
                        "package acme;",
                        "import io.elasticore.springboot3.domain.Money;",
                        "import io.elasticore.springboot3.domain.Quantity;",
                        "public class Shipment {",
                        "    private Long shipmentId;",
                        "    private Money freight;",
                        "    private Quantity parcels;",
                        "    private Level level;",
                        "    private Carrier carrier;",
                        "}"));
        // A converter of the project's own, nested in its enum.
        source(
                "acme/Level.java",
                String.join(
                        "\n",
                        "package acme;",
                        "public enum Level {",
                        "    LOW, HIGH;",
                        "    public static class DbConverter implements jakarta.persistence.AttributeConverter<Level, Integer> {",
                        "        public Integer convertToDatabaseColumn(Level l) { return l == null ? null : l.ordinal(); }",
                        "        public Level convertToEntityAttribute(Integer i) { return i == null ? null : values()[i]; }",
                        "    }",
                        "}"));
        // An enum's converter as mapping: xml writes it, moved out to a class of its own.
        source("acme/Carrier.java", "package acme;\npublic enum Carrier { POST, COURIER }\n");
        source(
                "acme/persistence/CarrierConverter.java",
                String.join(
                        "\n",
                        "package acme.persistence;",
                        "import acme.Carrier;",
                        "public class CarrierConverter implements jakarta.persistence.AttributeConverter<Carrier, String> {",
                        "    public String convertToDatabaseColumn(Carrier c) { return c == null ? null : c.name(); }",
                        "    public Carrier convertToEntityAttribute(String s) { return s == null ? null : Carrier.valueOf(s); }",
                        "}"));
        // A nested class by its binary name, Outer$Inner, as OrmMapping writes it.
        File xml = new File(dir.toFile(), "src/main/resources/META-INF/orm.xml");
        assertTrue(xml.getParentFile().mkdirs());
        Files.write(
                xml.toPath(),
                String.join(
                                "\n",
                                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                                "<entity-mappings xmlns=\"https://jakarta.ee/xml/ns/persistence/orm\" version=\"3.1\">",
                                "  <entity class=\"acme.Shipment\" access=\"FIELD\">",
                                "    <table name=\"ship_shipment\"/>",
                                "    <attributes>",
                                "      <id name=\"shipmentId\"/>",
                                "      <basic name=\"freight\">",
                                "        <column name=\"freight\" precision=\"14\" scale=\"2\"/>",
                                "        <convert converter=\"io.elasticore.springboot3.domain.Money$JpaConverter\"/>",
                                "      </basic>",
                                "      <basic name=\"parcels\"><convert converter=\"io.elasticore.springboot3.domain.Quantity$JpaConverter\"/></basic>",
                                "      <basic name=\"level\"><convert converter=\"acme.Level$DbConverter\"/></basic>",
                                "      <basic name=\"carrier\">",
                                "        <column name=\"carrier\" length=\"10\"/>",
                                "        <convert converter=\"acme.persistence.CarrierConverter\"/>",
                                "      </basic>",
                                "    </attributes>",
                                "  </entity>",
                                "</entity-mappings>",
                                "")
                        .getBytes(StandardCharsets.UTF_8));

        JpaSchemaReader r = new JpaSchemaReader()
                .readSettings(Collections.singletonList(new File(dir.toFile(), "src/main/resources")))
                .read(Collections.singletonList(new File(dir.toFile(), "src/main/java")))
                .readOrmXml(xml);
        Table t = r.schema(Collections.singletonList("acme.Shipment")).table("ship_shipment");

        assertNotNull(t, r.warnings().toString());
        assertEquals(ColumnType.DECIMAL, t.column("freight").type, r.warnings().toString());
        assertEquals(14, t.column("freight").precision);
        assertEquals(2, t.column("freight").scale);
        assertEquals(ColumnType.INTEGER, t.column("parcels").type, r.warnings().toString());
        assertEquals(ColumnType.INTEGER, t.column("level").type, r.warnings().toString());
        assertEquals(ColumnType.TEXT, t.column("carrier").type);
        assertEquals(10, t.column("carrier").length);
        assertTrue(
                r.warnings().stream().noneMatch(w -> w.contains("was not read")),
                r.warnings().toString());
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

    @Test
    public void aKotlinRelationReadThroughItsIdKeepsItsForeignKey() throws Exception {
        // A reference by id, as the Kotlin rules write it: the id is the
        // column, and the relation, final so it cannot be reassigned, reads it.
        dir = Files.createTempDirectory("jpa-reader-kt-ref");
        File f = new File(dir.toFile(), "src/main/kotlin/acme/Employee.kt");
        assertTrue(f.getParentFile().mkdirs());
        Files.write(
                f.toPath(),
                String.join(
                                "\n",
                                "package acme",
                                "import jakarta.persistence.*",
                                "@Entity",
                                "@Table(name = \"hr_employee\")",
                                "class Employee {",
                                "    @Id",
                                "    @Column(name = \"emp_id\", length = 36)",
                                "    var empId: String? = null",
                                "    /** Another aggregate, to read. The reference is managerEmpId; change that to point elsewhere. */",
                                "    @ManyToOne(fetch = FetchType.LAZY)",
                                "    @JoinColumn(name = \"manager_emp_id\" , insertable = false, updatable = false)",
                                "    final var manager: Employee? = null",
                                "    @Column(name = \"manager_emp_id\", length = 36)",
                                "    var managerEmpId: String? = null",
                                "}")
                        .getBytes(StandardCharsets.UTF_8));
        JpaSchemaReader r =
                new JpaSchemaReader().read(Collections.singletonList(new File(dir.toFile(), "src/main/kotlin")));
        Table t = r.schema(Collections.singletonList("acme.Employee")).table("hr_employee");
        assertNotNull(t, r.warnings().toString());
        assertEquals(36, t.column("manager_emp_id").length);
        assertTrue(
                t.foreignKeys().stream()
                        .anyMatch(fk -> fk.columns.equals(Collections.singletonList("manager_emp_id"))
                                && fk.table.equals("hr_employee")
                                && fk.referenced.equals(Collections.singletonList("emp_id"))),
                t.foreignKeys().toString());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
    }
}
