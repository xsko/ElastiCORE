package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * mapping: xml - the JPA annotations come off the domain class and the same
 * mapping is said in orm.xml.
 */
public class OrmMappingTest {

    private static final String BASE = String.join("\n",
            "package com.acme.erp.domain.model;",
            "",
            "import jakarta.persistence.Column;",
            "import jakarta.persistence.Convert;",
            "import jakarta.persistence.EntityListeners;",
            "import jakarta.persistence.FetchType;",
            "import jakarta.persistence.Id;",
            "import jakarta.persistence.JoinColumn;",
            "import jakarta.persistence.ManyToOne;",
            "import jakarta.persistence.MappedSuperclass;",
            "import jakarta.persistence.OneToMany;",
            "import jakarta.persistence.CascadeType;",
            "import jakarta.persistence.PrePersist;",
            "import jakarta.persistence.Version;",
            "import io.elasticore.springboot3.domain.Money;",
            "import lombok.Getter;",
            "import org.springframework.data.jpa.domain.support.AuditingEntityListener;",
            "",
            "/** The state. */",
            "@Getter",
            "@MappedSuperclass",
            "@EntityListeners({AuditingEntityListener.class, SalesOrderInvariants.class})",
            "public abstract class SalesOrderBase implements java.io.Serializable {",
            "",
            "\t@Id",
            "\t@Column(name = \"order_id\", length = 36)",
            "\tprivate String orderId;",
            "",
            "\t@PrePersist",
            "\tpublic void prePersist() {",
            "\t  if (orderId == null) orderId = \"x\";",
            "\t}",
            "",
            "\t@Column(name = \"status\", length = 3)",
            "\t@Convert(converter = OrderStatus.EntityConverter.class)",
            "\tprivate OrderStatus status;",
            "",
            "\t@Column(name = \"total_amount\", precision = 14, scale = 2)",
            "\t@Convert(converter = Money.JpaConverter.class)",
            "\tprivate Money totalAmount;",
            "",
            "\t@ManyToOne(fetch = FetchType.LAZY)",
            "\t@JoinColumn(name = \"partner_partner_id\" , insertable = false, updatable = false)",
            "\tprivate Partner partner;",
            "",
            "\t@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = \"order\")",
            "\tprivate java.util.List<SalesOrderLine> lines;",
            "",
            "\t@Column(name = \"version\")",
            "\t@Version",
            "\tprivate Long version;",
            "",
            "\t@jakarta.persistence.Transient",
            "\t@lombok.Getter(lombok.AccessLevel.NONE)",
            "\tprivate final transient java.util.List<Object> domainEvents",
            "\t        = new java.util.ArrayList<Object>();",
            "",
            "\tpublic final class Creation {",
            "\t    private String inner;",
            "\t}",
            "}",
            "");

    private static final String LISTENER = String.join("\n",
            "package com.acme.erp.domain.model;",
            "",
            "import jakarta.persistence.PrePersist;",
            "import jakarta.persistence.PreUpdate;",
            "",
            "public class SalesOrderInvariants {",
            "",
            "\t@PrePersist",
            "\t@PreUpdate",
            "\tvoid check(SalesOrder salesOrder) {",
            "\t}",
            "}",
            "");

    private static final String CONCRETE = String.join("\n",
            "package com.acme.erp.domain.model;",
            "",
            "import jakarta.persistence.Entity;",
            "import jakarta.persistence.Index;",
            "import jakarta.persistence.Table;",
            "import org.hibernate.annotations.DynamicUpdate;",
            "",
            "@Entity",
            "@Table(name = \"erp_sales_order\", indexes = {@Index(name = \"uk_no\", columnList = \"order_no\", unique = true)})",
            "@DynamicUpdate",
            "public class SalesOrder extends SalesOrderBase {",
            "}",
            "");

    @Test
    public void theClassesLoseTheirJpaAnnotations() {
        OrmMapping m = new OrmMapping();
        String base = m.extract(BASE, "com.acme.erp.domain.model", "SalesOrderBase", false);
        String listener = m.extract(LISTENER, "com.acme.erp.domain.model", "SalesOrderInvariants", false);
        String concrete = m.extract(CONCRETE, "com.acme.erp.domain.model", "SalesOrder", false);

        for (String s : new String[]{base, listener, concrete}) {
            assertFalse(s.contains("jakarta.persistence"), s);
            assertFalse(s.contains("org.hibernate"), s);
        }
        // What is not JPA stays where it was.
        assertTrue(base.contains("@Getter"), base);
        assertTrue(base.contains("@lombok.Getter(lombok.AccessLevel.NONE)"), base);
        assertTrue(base.contains("import org.springframework.data.jpa.domain.support.AuditingEntityListener;"), base);
        assertTrue(base.contains("import io.elasticore.springboot3.domain.Money;"), base);
        assertTrue(base.contains("private Money totalAmount;"), base);
    }

    @Test
    public void theXmlSaysWhatTheyDid() {
        OrmMapping m = new OrmMapping();
        m.converterMoved("com.acme.erp.domain.model.OrderStatus",
                "com.acme.erp.infrastructure.persistence.OrderStatusConverter");
        m.extract(BASE, "com.acme.erp.domain.model", "SalesOrderBase", false);
        m.extract(LISTENER, "com.acme.erp.domain.model", "SalesOrderInvariants", false);
        m.extract(CONCRETE, "com.acme.erp.domain.model", "SalesOrder", false);
        String xml = m.toXml();

        assertTrue(xml.contains("<mapped-superclass class=\"com.acme.erp.domain.model.SalesOrderBase\" access=\"FIELD\">"), xml);
        assertTrue(xml.contains("<entity-listener class=\"org.springframework.data.jpa.domain.support.AuditingEntityListener\"/>"), xml);
        assertTrue(xml.contains("<entity-listener class=\"com.acme.erp.domain.model.SalesOrderInvariants\">"), xml);
        assertTrue(xml.contains("<pre-update method-name=\"check\"/>"), xml);
        assertTrue(xml.contains("<pre-persist method-name=\"prePersist\"/>"), xml);
        assertTrue(xml.contains("<id name=\"orderId\">"), xml);
        assertTrue(xml.contains("<convert converter=\"com.acme.erp.infrastructure.persistence.OrderStatusConverter\"/>"), xml);
        assertTrue(xml.contains("<convert converter=\"io.elasticore.springboot3.domain.Money$JpaConverter\"/>"), xml);
        assertTrue(xml.contains("<column name=\"total_amount\" precision=\"14\" scale=\"2\"/>"), xml);
        assertTrue(xml.contains("<join-column name=\"partner_partner_id\" insertable=\"false\" updatable=\"false\"/>"), xml);
        assertTrue(xml.contains("<one-to-many name=\"lines\" fetch=\"LAZY\" mapped-by=\"order\" orphan-removal=\"true\">"), xml);
        assertTrue(xml.contains("<cascade-all/>"), xml);
        assertTrue(xml.contains("<version name=\"version\">"), xml);
        assertTrue(xml.contains("<transient name=\"domainEvents\"/>"), xml);
        assertFalse(xml.contains("name=\"inner\""), "a nested class's fields are not the entity's: " + xml);
        assertTrue(xml.contains("<entity class=\"com.acme.erp.domain.model.SalesOrder\" access=\"FIELD\">"), xml);
        assertTrue(xml.contains("<index name=\"uk_no\" column-list=\"order_no\" unique=\"true\"/>"), xml);
        // The schema's order: basic before version before relations before transient.
        assertTrue(xml.indexOf("<basic name=\"status\"") < xml.indexOf("<version name=\"version\">"), xml);
        assertTrue(xml.indexOf("<version name=\"version\">") < xml.indexOf("<many-to-one"), xml);
        assertTrue(xml.indexOf("<one-to-many") < xml.indexOf("<transient"), xml);
    }

    @Test
    public void kotlinReadsTheSame() {
        String kt = String.join("\n",
                "package com.acme.erp.domain.model",
                "",
                "import jakarta.persistence.Column",
                "import jakarta.persistence.EntityListeners",
                "import jakarta.persistence.MappedSuperclass",
                "import jakarta.persistence.Id",
                "import org.springframework.data.jpa.domain.support.AuditingEntityListener",
                "",
                "@MappedSuperclass",
                "@EntityListeners(AuditingEntityListener::class, StockInvariants::class)",
                "abstract class StockBase : java.io.Serializable {",
                "",
                "    @Id",
                "    @Column(name = \"stock_id\", length = 36)",
                "    var stockId: String? = null",
                "",
                "    @jakarta.persistence.Transient",
                "    @kotlin.jvm.Transient",
                "    private var settled: Boolean = false",
                "",
                "    @jakarta.persistence.PostLoad",
                "    fun settleOnLoad() {",
                "        settled = true",
                "    }",
                "}",
                "");
        OrmMapping m = new OrmMapping();
        String out = m.extract(kt, "com.acme.erp.domain.model", "StockBase", true);
        assertFalse(out.contains("jakarta.persistence"), out);
        assertTrue(out.contains("@kotlin.jvm.Transient"), out);
        // Kotlin's all-open plugin needs an annotation to open the class by.
        assertTrue(out.contains("@io.elasticore.springboot3.entity.PersistentModel"), out);
        String xml = m.toXml();
        assertTrue(xml.contains("<entity-listener class=\"com.acme.erp.domain.model.StockInvariants\"/>"), xml);
        assertTrue(xml.contains("<post-load method-name=\"settleOnLoad\"/>"), xml);
        assertTrue(xml.contains("<transient name=\"settled\"/>"), xml);
        assertTrue(xml.contains("<column name=\"stock_id\" length=\"36\"/>"), xml);
    }

    /** A base still extended by the developer's own class is not stale. */
    @Test
    public void aBaseStillExtendedIsKept() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("stale").toFile();
        java.io.File base = new java.io.File(dir, "OrderBase.java");
        java.io.File owned = new java.io.File(dir, "Order.java");
        java.nio.file.Files.write(base.toPath(), "public abstract class OrderBase {}".getBytes("UTF-8"));
        java.nio.file.Files.write(owned.toPath(), "public class Order extends OrderBase {}".getBytes("UTF-8"));
        assertTrue(RulesGenRunner.stillExtended(base));
        java.nio.file.Files.write(owned.toPath(), "public class Order {}".getBytes("UTF-8"));
        assertFalse(RulesGenRunner.stillExtended(base));
    }

    @Test
    public void theEnumsConverterMovesOut() {
        String kt = String.join("\n",
                "package com.acme.erp.domain.model",
                "",
                "import jakarta.persistence.AttributeConverter",
                "import jakarta.persistence.Converter",
                "",
                "enum class OrderStatus(val code: String) {",
                "    DRAFT(\"DRF\");",
                "",
                "companion object{",
                "    fun fromCode(code: String?): OrderStatus? = values().firstOrNull { it.code == code }",
                "}",
                "",
                "@Converter(autoApply = true)",
                "class EntityConverter : AttributeConverter<OrderStatus, String>{",
                "    override fun convertToDatabaseColumn(attribute: OrderStatus?): String? = attribute?.code",
                "    override fun convertToEntityAttribute(dbData: String?): OrderStatus?{",
                "        return fromCode(dbData)",
                "    }",
                "}",
                "}",
                "");
        String[] split = OrmMapping.splitEnumConverter(kt, "OrderStatus", "com.acme.erp.domain.model.OrderStatus",
                "com.acme.erp.infrastructure.persistence", true);
        assertNotNull(split);
        assertFalse(split[0].contains("EntityConverter"), split[0]);
        assertFalse(split[0].contains("jakarta.persistence"), split[0]);
        assertTrue(split[1].contains("package com.acme.erp.infrastructure.persistence"), split[1]);
        assertTrue(split[1].contains("class OrderStatusConverter : AttributeConverter<OrderStatus, String>"), split[1]);
        assertTrue(split[1].contains("return OrderStatus.fromCode(dbData)"), split[1]);
        assertTrue(split[1].contains("@Converter(autoApply = true)"), split[1]);
    }

    private static String entity(String pkg, String name, String kind) {
        return String.join("\n",
                "package " + pkg + ";",
                "",
                "import jakarta.persistence." + kind + ";",
                "import jakarta.persistence.Id;",
                "",
                "@" + kind,
                "public class " + name + " {",
                "",
                "\t@Id",
                "\tprivate Long id;",
                "}",
                "");
    }

    @Test
    public void aRunOverOneDomainKeepsOnlyTheOtherDomainsClasses() {
        // The last whole run: shop's Order, Item and Coupon, billing's Invoice.
        OrmMapping whole = new OrmMapping();
        whole.extract(entity("com.acme.shop", "Order", "Entity"), "com.acme.shop", "Order", false);
        whole.extract(entity("com.acme.shop", "Item", "Entity"), "com.acme.shop", "Item", false);
        whole.extract(entity("com.acme.shop", "Coupon", "Entity"), "com.acme.shop", "Coupon", false);
        whole.extract(entity("com.acme.billing", "Invoice", "Entity"), "com.acme.billing", "Invoice", false);
        // Written with CRLF, and a block of billing's with no <attributes>.
        String previous = whole.toXml().replace(
                "<entity class=\"com.acme.billing.Invoice\" access=\"FIELD\">\n        <attributes>\n"
                        + "            <id name=\"id\"/>\n        </attributes>\n",
                "<entity class=\"com.acme.billing.Invoice\" access=\"FIELD\">\n").replace("\n", "\r\n");
        assertTrue(previous.contains("Invoice\" access=\"FIELD\">\r\n    </entity>"), previous);

        // shop alone: Coupon gone from the DSL, Item now an embeddable, Gift new.
        OrmMapping shop = new OrmMapping();
        shop.extract(entity("com.acme.shop", "Order", "Entity"), "com.acme.shop", "Order", false);
        shop.extract(entity("com.acme.shop", "Item", "Embeddable"), "com.acme.shop", "Item", false);
        shop.extract(entity("com.acme.shop", "Gift", "Entity"), "com.acme.shop", "Gift", false);
        String xml = shop.toXml(previous, java.util.Collections.singleton("com.acme.billing"));

        assertTrue(xml.contains("<entity class=\"com.acme.billing.Invoice\" access=\"FIELD\">\n    </entity>"), xml);
        assertFalse(xml.contains("Coupon"), "a class the domain no longer has leaves: " + xml);
        assertTrue(xml.contains("<embeddable class=\"com.acme.shop.Item\""), xml);
        assertFalse(xml.contains("<entity class=\"com.acme.shop.Item\""), xml);
        // The new class right after its package's last one, before billing's.
        int order = xml.indexOf("com.acme.shop.Order"), gift = xml.indexOf("com.acme.shop.Gift");
        assertTrue(order < gift && gift < xml.indexOf("com.acme.billing.Invoice"), xml);
        assertFalse(xml.contains("\r"), xml);

        // A whole run keeps nothing and writes in the order the classes came.
        String alone = shop.toXml();
        assertFalse(alone.contains("Invoice"), alone);
        assertTrue(alone.indexOf("shop.Order") < alone.indexOf("shop.Gift"), alone);
    }
}
