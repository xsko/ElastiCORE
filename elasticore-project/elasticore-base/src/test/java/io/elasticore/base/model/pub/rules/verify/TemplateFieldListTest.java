package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.extract.ModelExtractor;
import io.elasticore.base.model.dto.TemplateList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A DTO names the fields it takes right after the model they come from,
 * {@code @template(Order[note, deliveryDate], Customer)}, instead of removing
 * the others one by one with "--". A field added to the entity later is then
 * not in the DTO, nor in a request that binds it, until someone lists it. Only
 * what is copied is filtered: a field the DTO declares is always kept, and may
 * override a listed one. Read in the model, so the 2.x generator, its
 * compatible output and the 3.0 code all take the same fields.
 */
public class TemplateFieldListTest {

    private static final String CRM = String.join(
            "\n",
            "entity:",
            "  OyCustomer:",
            "    meta: entity",
            "    fields:",
            "      customerId: long @id @sequence",
            "      name: string(50)",
            "      email: string(100)",
            "");

    private static final String DESK = String.join(
            "\n",
            "entity:",
            "  OyOrder:",
            "    meta: entity",
            "    fields:",
            "      orderId: long @id @sequence",
            "      note: string(200) @search(like)",
            "      deliveryDate: date",
            "      status: string(10) @search(eq)",
            "      total: bigdecimal(12.2)",
            "      addedLater: string(20) -- a field the entity got after the DTO was written",
            "",
            "  OyMemo:",
            "    meta: entity",
            "    fields:",
            "      memoId: long @id @sequence",
            "      body: string(200)",
            "      writer: string(50)",
            "",
            "  OyStamped:",
            "    meta: entity @abstract",
            "    fields:",
            "      createdBy: string(20)",
            "",
            "  OyAudited:",
            "    meta: entity @extend(OyStamped)",
            "    fields:",
            "      auditedId: long @id @sequence",
            "      title: string(50)",
            "",
            "dto:",
            "  OyOrderDTO:",
            "    meta: dto @template(OyOrder)",
            "",
            "  OyOrderNoteDTO:",
            "    meta: dto @template(OyOrder[note, deliveryDate])",
            "    fields:",
            "      deliveryDate: datetime -- declared here, so the DTO's own wins",
            "      reason: string(100)",
            "",
            "  OyListAndWholeDTO:",
            "    meta: dto @template(OyOrder[status], OyMemo)",
            "",
            "  OyTwoListsDTO:",
            "    meta: dto @template(OyOrder[total], OyMemo[writer])",
            "",
            "  OyCustomerCardDTO:",
            "    meta: dto @template(OyCustomer[name])",
            "",
            "  OyCustomerMailDTO:",
            "    meta: dto @template(oycrm:OyCustomer[email])",
            "",
            "  OyOfADtoDTO:",
            "    meta: dto @template(OyOrderDTO[status])",
            "",
            "  OyAuditedStampDTO:",
            "    meta: dto @template(OyAudited[createdBy])",
            "");

    private final List<File> projects = new ArrayList<File>();

    @Test
    public void aValueWithoutBracketsComesBackAsItWasWritten() {
        // What every reader of the value gets, so a DTO without brackets generates what it did.
        for (String value :
                new String[] {"Order", "Order, Customer", "Order,Customer", " shop:Order ", "Order, Order"}) {
            TemplateList list = TemplateList.of(value);
            assertEquals(value, list.names());
            assertFalse(list.hasLists());
            assertNull(list.fieldsOf("Order"));
        }
        assertNull(TemplateList.of((String) null).names());
    }

    @Test
    public void aValueWithBracketsComesBackAsItsPlainNames() {
        TemplateList list = TemplateList.of("Order[note, deliveryDate], Customer, shop:Article[aid]");
        assertEquals("Order,Customer,shop:Article", list.names());
        assertTrue(list.hasLists());
        assertEquals(Arrays.asList("note", "deliveryDate"), list.fieldsOf("Order"));
        assertNull(list.fieldsOf("Customer"));
        // As the readers ask, a name split from the plain names.
        assertEquals(Arrays.asList("aid"), list.fieldsOf(" shop:Article"));
        assertEquals("Order", TemplateList.of("Order[note]").names());
    }

    @Test
    public void aDtoTakesTheListedFieldsOfItsTemplateAndItsOwn() throws Exception {
        for (boolean v3 : new boolean[] {false, true}) {
            File project = project(DESK);
            assertEquals(0, run(project, "layered", v3 ? "elasticore.rules.v3" : "elasticore.rules.v2", null));
            String dto = found(project, "OyOrderNoteDTO.java");
            assertTrue(dto.contains(" note;"), dto);
            assertTrue(dto.contains("LocalDateTime deliveryDate"), "the DTO's own type wins:\n" + dto);
            assertTrue(dto.contains(" reason;"), dto);
            for (String left : new String[] {" orderId", " status", " total", " addedLater"}) {
                assertFalse(dto.contains(left + ";") || dto.contains(left + " ="), left + " was copied:\n" + dto);
            }
            // Without brackets, everything, as ever.
            String whole = found(project, "OyOrderDTO.java");
            for (String all : new String[] {" orderId;", " note;", " status;", " total;", " addedLater;"}) {
                assertTrue(whole.contains(all), all + " is missing:\n" + whole);
            }
        }
    }

    @Test
    public void eachModelCarriesItsOwnList() throws Exception {
        File project = project(DESK);
        assertEquals(0, run(project, "layered", "elasticore.rules.v3", null));

        String oneWhole = found(project, "OyListAndWholeDTO.java");
        assertTrue(oneWhole.contains(" status;"), oneWhole);
        assertFalse(oneWhole.contains(" note;") || oneWhole.contains(" total;"), oneWhole);
        for (String all : new String[] {" memoId;", " body;", " writer;"}) {
            assertTrue(oneWhole.contains(all), all + " is missing:\n" + oneWhole);
        }

        String both = found(project, "OyTwoListsDTO.java");
        assertTrue(both.contains(" total;") && both.contains(" writer;"), both);
        assertFalse(both.contains(" status;") || both.contains(" body;"), both);
    }

    @Test
    public void aTemplateOfAnotherDomainIsFoundWithOrWithoutItsDomain() throws Exception {
        File project = project(DESK);
        assertEquals(0, run(project, "layered", "elasticore.rules.v3", null));
        String card = found(project, "OyCustomerCardDTO.java");
        assertTrue(card.contains(" name;"), card);
        assertFalse(card.contains(" email;") || card.contains(" customerId;"), card);
        String mail = found(project, "OyCustomerMailDTO.java");
        assertTrue(mail.contains(" email;"), mail);
        assertFalse(mail.contains(" name;") || mail.contains(" customerId;"), mail);
    }

    /** The mapper pairs a DTO with its entity by the name, so it must not ask for a model called OyOrder[note...]. */
    @Test
    public void theMapperPairsTheDtoWithItsModel() throws Exception {
        for (boolean v3 : new boolean[] {false, true}) {
            File project = project(DESK);
            assertEquals(0, run(project, "layered", v3 ? "elasticore.rules.v3" : "elasticore.rules.v2", null));
            String mapper = found(project, "OydeskMapper.java");
            assertTrue(mapper.contains("mapping(OyOrderNoteDTO from, OyOrder to"), mapper);
            assertTrue(mapper.contains("mapping(OyOrder from, OyOrderNoteDTO to"), mapper);
            assertTrue(mapper.contains("from.getNote()"), mapper);
            assertFalse(mapper.contains("OyOrder["), mapper);
        }
    }

    /** One model's key DTO: a DTO that does not list the key writes none, and leaves the other's standing. */
    @Test
    public void aListWithoutTheKeyLeavesTheKeyDtoAlone() throws Exception {
        for (boolean v3 : new boolean[] {false, true}) {
            File project = project(DESK);
            assertEquals(0, run(project, "layered", v3 ? "elasticore.rules.v3" : "elasticore.rules.v2", null));
            String key = found(project, "OyOrderKeyDTO.java");
            assertTrue(key.contains(" orderId;"), key);
        }
    }

    /** No DTO of the model lists its key: 2.x writes no key DTO, and neither does the 3.0 code. */
    @Test
    public void noKeyDtoWithoutAKeyToHold() throws Exception {
        for (boolean v3 : new boolean[] {false, true}) {
            File project = project(DESK);
            assertEquals(0, run(project, "layered", v3 ? "elasticore.rules.v3" : "elasticore.rules.v2", null));
            List<File> hits = new ArrayList<File>();
            collect(project, "OyCustomerKeyDTO.java", hits);
            assertTrue(hits.isEmpty(), (v3 ? "v3: " : "2.x: ") + hits);
        }
    }

    @Test
    public void theTemplateMayBeADtoAndAFieldMayBeInherited() throws Exception {
        File project = project(DESK);
        assertEquals(0, run(project, "layered", "elasticore.rules.v3", null));
        String ofDto = found(project, "OyOfADtoDTO.java");
        assertTrue(ofDto.contains(" status;"), ofDto);
        assertFalse(ofDto.contains(" note;") || ofDto.contains(" total;"), ofDto);
        String inherited = found(project, "OyAuditedStampDTO.java");
        assertTrue(inherited.contains(" createdBy;"), inherited);
        assertFalse(inherited.contains(" title;") || inherited.contains(" auditedId;"), inherited);
    }

    @Test
    public void kotlinTakesTheSameFields() throws Exception {
        File project = project(DESK);
        assertEquals(0, run(project, "layered", "kotlin", null, null));
        String dto = found(project, "OyOrderNoteDTO.kt");
        assertTrue(dto.contains("var note:") && dto.contains("var reason:"), dto);
        assertFalse(dto.contains("var status:") || dto.contains("var addedLater:"), dto);
    }

    /** A DDD reference is held by its id, customerId @idof(customer): naming the relation keeps the id. */
    @Test
    public void aRelationNameKeepsTheFieldThatHoldsItsId() throws Exception {
        File project = Files.createTempDirectory("elcore-list-ddd").toFile();
        projects.add(project);
        domain(
                project,
                "shop",
                null,
                String.join(
                        "\n",
                        "entity:",
                        "  ShCustomer:",
                        "    meta: entity @root @expose(20)",
                        "    fields:",
                        "      customerId: long @id @sequence",
                        "      name: string(50)",
                        "  ShOrder:",
                        "    meta: entity @root @expose(20)",
                        "    fields:",
                        "      orderId: long @id @sequence",
                        "      note: string(200)",
                        "      status: string(10)",
                        "      customer: ShCustomer @ManyToOne",
                        "",
                        "dto:",
                        "  ShOrderNoteDTO:",
                        "    meta: dto @template(ShOrder[note, customer])",
                        ""));
        assertEquals(0, run(project, "hexagonal", null, null));
        String dto = found(project, "ShOrderNoteDTO.java");
        assertTrue(dto.contains(" note;") && dto.contains(" customerId;"), dto);
        assertFalse(dto.contains(" status;"), dto);
    }

    private static final String LINE = String.join(
            "\n",
            "  OyLine:",
            "    meta: entity",
            "    fields:",
            "      lineId: long @id @sequence",
            "      quantity: int",
            "      order: OyOrder @ManyToOne",
            "");

    /** A relation with no field that holds its id gives the DTO nothing, so listing it is refused like a typo. */
    @Test
    public void aListedRelationWithoutAnIdFieldIsRefused() throws Exception {
        String model = withEntity(LINE
                        + String.join(
                                "\n",
                                "  OyBasket:",
                                "    meta: entity",
                                "    fields:",
                                "      basketId: long @id @sequence",
                                "      lines: List<OyLine> @OneToMany",
                                ""))
                + String.join(
                        "\n",
                        "  OyLineDTO:",
                        "    meta: dto @template(OyLine[quantity, order])",
                        "",
                        "  OyBasketDTO:",
                        "    meta: dto @template(OyBasket[basketId, lines])",
                        "");
        for (boolean v3 : new boolean[] {false, true}) {
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            int code = run(project(model), "layered", v3 ? "elasticore.rules.v3" : "elasticore.rules.v2", captured);
            String log = new String(captured.toByteArray(), StandardCharsets.UTF_8);
            assertEquals(1, code, log);
            assertTrue(
                    log.contains("OyLineDTO: @template(OyLine[quantity, order]): order is a relation, which a DTO"
                            + " does not copy: declare its id with @ref(order.orderId)"),
                    log);
            assertTrue(
                    log.contains("OyBasketDTO: @template(OyBasket[basketId, lines]): lines is a relation, which a DTO"
                            + " does not copy: take it out of the list"),
                    log);
        }
        String[] run = extract(project(model));
        assertFalse("0".equals(run[0]), run[1]);
        assertTrue(run[1].contains("order is a relation, which a DTO does not copy"), run[1]);
    }

    /** What the refusal says to do works: the id is a field the DTO declares. */
    @Test
    public void aRelationsIdIsDeclaredWithARef() throws Exception {
        String model = withEntity(LINE)
                + String.join(
                        "\n",
                        "  OyLineRefDTO:",
                        "    meta: dto @template(OyLine[quantity])",
                        "    fields:",
                        "      orderId: long @ref(order.orderId)",
                        "");
        for (boolean v3 : new boolean[] {false, true}) {
            File project = project(model);
            assertEquals(0, run(project, "layered", v3 ? "elasticore.rules.v3" : "elasticore.rules.v2", null));
            String ref = found(project, "OyLineRefDTO.java");
            assertTrue(ref.contains(" quantity;") && ref.contains(" orderId;"), ref);
            assertFalse(ref.contains(" lineId;"), ref);
        }
    }

    /** Where a rule set keeps a reference an object, or the target is elsewhere, there is no id field either. */
    @Test
    public void anObjectReferenceInADddDomainIsRefusedToo() throws Exception {
        File project = Files.createTempDirectory("elcore-list-objectref").toFile();
        projects.add(project);
        domain(
                project,
                "shop",
                null,
                String.join(
                        "\n",
                        "entity:",
                        "  ShCustomer:",
                        "    meta: entity @root @expose(20)",
                        "    fields:",
                        "      customerId: long @id @sequence",
                        "  ShOrder:",
                        "    meta: entity @root @expose(20)",
                        "    fields:",
                        "      orderId: long @id @sequence",
                        "      customer: ShCustomer @ManyToOne @objectref",
                        "",
                        "dto:",
                        "  ShOrderNoteDTO:",
                        "    meta: dto @template(ShOrder[orderId, customer])",
                        ""));
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        assertEquals(1, run(project, "hexagonal", null, captured));
        String log = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(log.contains("customer is a relation, which a DTO does not copy"), log);
    }

    private static final String ADDRESSED = String.join(
            "\n",
            "entity:",
            "  ShAddress:",
            "    meta: entity @embeddable",
            "    fields:",
            "      city: string(50)",
            "  ShCustomer:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      customerId: long @id @sequence",
            "      name: string(50)",
            "      address: ShAddress @embedded",
            "",
            "dto:",
            "  ShCustomerAddressDTO:",
            "    meta: dto @template(ShCustomer[name, address])",
            "");

    /** A value object travels whole where a rule set makes it immutable; elsewhere the DTO would not have it. */
    @Test
    public void aListedValueObjectIsRefusedWhereTheDtoDoesNotCarryOne() throws Exception {
        File carried = Files.createTempDirectory("elcore-list-value").toFile();
        projects.add(carried);
        domain(carried, "shop", null, ADDRESSED);
        assertEquals(0, run(carried, "hexagonal", null, null));
        String dto = found(carried, "ShCustomerAddressDTO.java");
        assertTrue(dto.contains(" address;") && dto.contains(" name;"), dto);

        File layered = Files.createTempDirectory("elcore-list-value-layered").toFile();
        projects.add(layered);
        domain(layered, "shop", null, ADDRESSED);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        assertEquals(1, run(layered, "layered", "elasticore.rules.v3", captured));
        String log = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(log.contains("address is a value object, which this DTO does not carry"), log);
    }

    /** A name that finds the model itself: its own fields, where it used to ask itself until the stack ran out. */
    @Test
    public void aDtoThatNamesItselfHasItsOwnFields() throws Exception {
        String model = DESK
                + String.join(
                        "\n",
                        "  OySelfDTO:",
                        "    meta: dto @template(OySelfDTO)",
                        "    fields:",
                        "      own: string(10)",
                        "",
                        "  OySelfListDTO:",
                        "    meta: dto @template(OySelfListDTO[own])",
                        "    fields:",
                        "      own: string(10)",
                        "");
        for (boolean v3 : new boolean[] {false, true}) {
            File project = project(model);
            assertEquals(0, run(project, "layered", v3 ? "elasticore.rules.v3" : "elasticore.rules.v2", null));
            assertTrue(found(project, "OySelfDTO.java").contains(" own;"));
            assertTrue(found(project, "OySelfListDTO.java").contains(" own;"));
        }
    }

    private static String shared(String own) {
        return String.join(
                "\n",
                "entity:",
                "  OyShared:",
                "    meta: entity",
                "    fields:",
                "      sharedId: long @id @sequence",
                "      " + own + ": string(20)",
                "");
    }

    /**
     * The check looked in the DTO's own domain first and the copy took the first
     * domain with the name, so a field could pass the one and be lost by the other.
     */
    @Test
    public void aNameTwoDomainsHaveMustSayWhich() throws Exception {
        File project = Files.createTempDirectory("elcore-list-two").toFile();
        projects.add(project);
        domain(project, "oycrm", "io.acme.oycrm", shared("inCrm"));
        domain(
                project,
                "oydesk",
                "io.acme.oydesk",
                shared("nickname")
                        + String.join(
                                "\n",
                                "",
                                "dto:",
                                "  OyUnsaidDTO:",
                                "    meta: dto @template(OyShared[nickname])",
                                "",
                                "  OyUnsaidBesideDTO:",
                                "    meta: dto @template(OyShared, oycrm:OyShared[inCrm])",
                                ""));
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        assertEquals(1, run(project, "layered", "elasticore.rules.v3", captured));
        String log = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(
                log.contains("OyUnsaidDTO: @template(OyShared[nickname]): oycrm and oydesk both have OyShared,"
                        + " so say which: oycrm:OyShared"),
                log);
        assertTrue(
                log.contains("OyUnsaidBesideDTO: @template(OyShared, oycrm:OyShared[inCrm]): oycrm and oydesk"), log);
    }

    @Test
    public void aDomainInTheNameFindsThatModel() throws Exception {
        for (boolean v3 : new boolean[] {false, true}) {
            File project = Files.createTempDirectory("elcore-list-said").toFile();
            projects.add(project);
            domain(project, "oycrm", "io.acme.oycrm", shared("inCrm"));
            domain(
                    project,
                    "oydesk",
                    "io.acme.oydesk",
                    shared("nickname")
                            + String.join(
                                    "\n",
                                    "",
                                    "dto:",
                                    "  OySaidHereDTO:",
                                    "    meta: dto @template(oydesk:OyShared[nickname])",
                                    "",
                                    "  OySaidThereDTO:",
                                    "    meta: dto @template(oycrm:OyShared[inCrm])",
                                    "",
                                    "  OyUnlistedDTO:",
                                    "    meta: dto @template(OyShared)",
                                    ""));
            assertEquals(0, run(project, "layered", v3 ? "elasticore.rules.v3" : "elasticore.rules.v2", null));
            assertTrue(found(project, "OySaidHereDTO.java").contains(" nickname;"));
            assertTrue(found(project, "OySaidThereDTO.java").contains(" inCrm;"));
        }
    }

    @Test
    public void theTypescriptClientTakesTheSameFields() throws Exception {
        File project = project(DESK);
        assertEquals(0, run(project, "layered", "elasticore.rules.ts", null));
        String client = found(project, "OyOrderNoteDTO.ts");
        assertTrue(client.contains("note") && client.contains("reason"), client);
        assertFalse(client.contains("status") || client.contains("addedLater") || client.contains("total"), client);
    }

    /** The ddd bundle, as hexagonal above: the DTO, and the mapper that reads it. */
    @Test
    public void theDddArchitectureTakesTheSameFields() throws Exception {
        File project = Files.createTempDirectory("elcore-list-dddarch").toFile();
        projects.add(project);
        domain(
                project,
                "shop",
                null,
                String.join(
                        "\n",
                        "entity:",
                        "  ShCustomer:",
                        "    meta: entity @root @expose(20)",
                        "    fields:",
                        "      customerId: long @id @sequence",
                        "  ShOrder:",
                        "    meta: entity @root @expose(20)",
                        "    fields:",
                        "      orderId: long @id @sequence",
                        "      note: string(200)",
                        "      status: string(10)",
                        "      customer: ShCustomer @ManyToOne",
                        "",
                        "dto:",
                        "  ShOrderNoteDTO:",
                        "    meta: dto @template(ShOrder[note, customer])",
                        ""));
        assertEquals(0, run(project, "ddd", null, null));
        String dto = found(project, "ShOrderNoteDTO.java");
        assertTrue(dto.contains(" note;") && dto.contains(" customerId;"), dto);
        assertFalse(dto.contains(" status;") || dto.contains(" orderId;"), dto);
    }

    /** MyBatis has its own mapper, and reads the same fields: its statement must not select the others. */
    @Test
    public void myBatisTakesTheSameFields() throws Exception {
        File project = Files.createTempDirectory("elcore-list-mybatis").toFile();
        projects.add(project);
        domain(project, "oydesk", "io.acme.oydesk", DESK);
        File env = new File(project, "src/main/resources/blueprint/oydesk/env.yml");
        String yml = new String(Files.readAllBytes(env.toPath()), StandardCharsets.UTF_8);
        Files.write(env.toPath(), yml.replace("mode: jpa", "mode: mybatis").getBytes(StandardCharsets.UTF_8));
        // OyCustomerCardDTO and OyCustomerMailDTO name a domain this project does not have, and OyAudited
        // extends an abstract one, which MyBatis does not write: only the DTO of a plain table is asked here.
        String model = String.join(
                "\n",
                "entity:",
                "  OyOrder:",
                "    meta: entity @expose(20)",
                "    fields:",
                "      orderId: long @id @sequence",
                "      note: string(200)",
                "      status: string(10)",
                "",
                "dto:",
                "  OyOrderDTO:",
                "    meta: dto @template(OyOrder)",
                "",
                "  OyOrderNoteDTO:",
                "    meta: dto @template(OyOrder[note])",
                "");
        Files.write(
                new File(project, "src/main/resources/blueprint/oydesk/model/model.yml").toPath(),
                model.getBytes(StandardCharsets.UTF_8));
        assertEquals(0, run(project, "layered", "elasticore.rules.v3", null));
        String dto = found(project, "OyOrderNoteDTO.java");
        assertTrue(dto.contains(" note;"), dto);
        assertFalse(dto.contains(" status;") || dto.contains(" orderId;"), dto);
    }

    /** The first @template of an entity is its CRUD DTO in the compatible output, whatever it is called. */
    @Test
    public void aListDeclaredFirstIsNotTheEntitysCrudDtoWithoutItsKey() throws Exception {
        String first = withEntity("")
                .replace("  OyOrder:\n    meta: entity\n", "  OyOrder:\n    meta: entity @expose(20)\n")
                .replace(
                        "dto:\n  OyOrderDTO:",
                        String.join(
                                "\n",
                                "dto:",
                                "  OyOrderFirstDTO:",
                                "    meta: dto @template(OyOrder[note])",
                                "",
                                "  OyOrderSrchDTO:",
                                "    meta: dto @searchable(entity=OyOrder, pageSize=20)",
                                "    fields:",
                                "      status: string(10) @search(eq)",
                                "",
                                "  OyOrderDTO:"));
        for (boolean v3 : new boolean[] {false, true}) {
            File project = project(first);
            assertEquals(0, run(project, "layered", v3 ? "elasticore.rules.v3" : "elasticore.rules.v2", null));
            String service = found(project, "OyOrderCoreService.java");
            assertTrue(service.contains("OyOrderDTO"), service);
            assertFalse(service.contains("OyOrderFirstDTO"), service);
        }
        // The 2.x generator's own lookup, in a JVM of its own.
        File project = project(first);
        String[] run = extract(project);
        assertEquals("0", run[0], run[1]);
        String service = found(project, "OyOrderCoreService.java");
        assertTrue(service.contains("OyOrderDTO"), service);
        assertFalse(service.contains("OyOrderFirstDTO"), service);
    }

    /** The lists are checked before a DDD reference is rewritten, so its id field goes by its relation's name. */
    @Test
    public void aDddIdFieldIsListedByItsRelationsName() throws Exception {
        File project = Files.createTempDirectory("elcore-list-ddd-id").toFile();
        projects.add(project);
        domain(
                project,
                "shop",
                null,
                String.join(
                        "\n",
                        "entity:",
                        "  ShCustomer:",
                        "    meta: entity @root @expose(20)",
                        "    fields:",
                        "      customerId: long @id @sequence",
                        "  ShOrder:",
                        "    meta: entity @root @expose(20)",
                        "    fields:",
                        "      orderId: long @id @sequence",
                        "      customer: ShCustomer @ManyToOne",
                        "",
                        "dto:",
                        "  ShOrderNoteDTO:",
                        "    meta: dto @template(ShOrder[customerId])",
                        ""));
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        assertEquals(1, run(project, "hexagonal", null, captured));
        String log = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(log.contains("ShOrder has no field customerId"), log);
    }

    @Test
    public void whatCannotBeKeptIsSaidAndStopsTheRun() throws Exception {
        File project = project(DESK
                + String.join(
                        "\n",
                        "  OyNoSuchFieldDTO:",
                        "    meta: dto @template(OyOrder[note, nothing])",
                        "",
                        "  OyWithExtendDTO:",
                        "    meta: dto @template(OyOrder[note]) @extend(OyOrderNoteDTO)",
                        "",
                        "  OyRemovedDTO:",
                        "    meta: dto @template(OyOrder[note, status])",
                        "    fields:",
                        "      status: --",
                        "",
                        "  OyEmptyDTO:",
                        "    meta: dto @template(OyOrder[])",
                        "",
                        "  OyBlankDTO:",
                        "    meta: dto @template(OyOrder[ ])",
                        "",
                        "  OyEmptyNameDTO:",
                        "    meta: dto @template(OyOrder[note,,status])",
                        "",
                        "  OyTwiceDTO:",
                        "    meta: dto @template(OyOrder[note], OyOrder[total])",
                        "",
                        "  OyWholeAndListDTO:",
                        "    meta: dto @template(OyOrder, OyOrder[note])",
                        "",
                        "  OyTwiceByDomainDTO:",
                        "    meta: dto @template(OyCustomer[name], oycrm:OyCustomer[email])",
                        "",
                        "  OyNoModelDTO:",
                        "    meta: dto @template(OyMissing[note])",
                        "",
                        "  OyOpenDTO:",
                        "    meta: dto @template(OyOrder[note)",
                        "",
                        "  OyAfterDTO:",
                        "    meta: dto @template(OyOrder[note]x)",
                        "",
                        "  OyClosedDTO:",
                        "    meta: dto @template(OyOrder note])",
                        "",
                        "  OyFieldTwiceDTO:",
                        "    meta: dto @template(OyOrder[note, note])",
                        "",
                        "  OySearchListDTO:",
                        "    meta: dto @searchable(entity=OyOrder[note])",
                        "",
                        "  OyResultListDTO:",
                        "    meta: dto @searchResult(OyOrder[note])",
                        ""));
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        assertEquals(1, run(project, "layered", "elasticore.rules.v3", captured));
        String log = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        for (String said : new String[] {
            "OyNoSuchFieldDTO: @template(OyOrder[note, nothing]): OyOrder has no field nothing",
            "OyWithExtendDTO: @template(OyOrder[note]) cannot go with @extend",
            "OyRemovedDTO.status is removed with -- and listed by @template(OyOrder[note, status])",
            "OyEmptyDTO: @template(OyOrder[]) lists no field of OyOrder",
            "OyBlankDTO: @template(OyOrder[ ]) lists no field of OyOrder",
            "OyEmptyNameDTO: @template(OyOrder[note,,status]) has an empty name in the list of OyOrder",
            "OyTwiceDTO: @template(OyOrder[note], OyOrder[total]) names OyOrder twice",
            "OyWholeAndListDTO: @template(OyOrder, OyOrder[note]) names OyOrder twice",
            "OyTwiceByDomainDTO: @template(OyCustomer[name], oycrm:OyCustomer[email]) names oycrm:OyCustomer twice",
            "OyNoModelDTO: @template(OyMissing[note]): no domain has a model OyMissing",
            "OyOpenDTO: @template(OyOrder[note) cannot be read",
            "OyAfterDTO: @template(OyOrder[note]x) cannot be read",
            "OyClosedDTO: @template(OyOrder note]) cannot be read",
            "OyFieldTwiceDTO: @template(OyOrder[note, note]) lists note twice",
            "OySearchListDTO: @searchable(OyOrder[note]) takes no list",
            "OyResultListDTO: @searchResult(OyOrder[note]) takes no list"
        }) {
            assertTrue(log.contains(said), said + "\n" + log);
        }
        assertFalse(log.contains("OyOrderNoteDTO: @template") || log.contains("OyCustomerCardDTO: @template"), log);
    }

    /** A DTO without brackets stays as free as it was, and the same model twice is not refused. */
    @Test
    public void aDtoWithoutBracketsIsNotRefused() throws Exception {
        File project = project(DESK
                + String.join(
                        "\n",
                        "  OySameModelTwiceDTO:",
                        "    meta: dto @template(OyOrder,OyOrder)",
                        "",
                        "  OyUnknownModelDTO:",
                        "    meta: dto @template(OyMissing)",
                        ""));
        assertEquals(0, run(project, "layered", "elasticore.rules.v3", null));
    }

    @Test
    public void aListIsForADto() throws Exception {
        File project = project(withEntity(String.join(
                "\n",
                "  OyListedEntity:",
                "    meta: entity @template(OyMemo[body])",
                "    fields:",
                "      listedId: long @id @sequence",
                "")));
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        assertEquals(1, run(project, "layered", "elasticore.rules.v3", captured));
        String log = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(log.contains("OyListedEntity: a @template lists the fields of a DTO"), log);
    }

    /** The 2.x generator, as ./gradlew elcore runs it: the same fields, and the same refusal. */
    @Test
    public void the2xGeneratorTakesTheSameFieldsAndRefusesTheSame() throws Exception {
        File project = project(DESK);
        String[] run = extract(project);
        assertEquals("0", run[0], run[1]);
        String dto = found(project, "OyOrderNoteDTO.java");
        assertTrue(
                dto.contains(" note;") && dto.contains("LocalDateTime deliveryDate") && dto.contains(" reason;"), dto);
        assertFalse(dto.contains(" status;") || dto.contains(" addedLater;") || dto.contains(" total;"), dto);

        File refused = project(
                DESK + String.join("\n", "  OyNoSuchFieldDTO:", "    meta: dto @template(OyOrder[note, nothing])", ""));
        run = extract(refused);
        assertFalse("0".equals(run[0]), run[1]);
        assertTrue(
                run[1].contains("OyNoSuchFieldDTO: @template(OyOrder[note, nothing]): OyOrder has no field nothing"),
                run[1]);
    }

    /** DESK with more entities, in its entity section: a second entity: key would replace the first. */
    private static String withEntity(String entities) {
        return DESK.replace("\ndto:\n", "\n" + entities + "\ndto:\n");
    }

    /** Two domains: oycrm holds OyCustomer, oydesk the model given. */
    private File project(String desk) throws Exception {
        File project = Files.createTempDirectory("elcore-list").toFile();
        projects.add(project);
        domain(project, "oycrm", "io.acme.oycrm", CRM);
        domain(project, "oydesk", "io.acme.oydesk", desk);
        return project;
    }

    private static void domain(File project, String name, String ns, String model) throws IOException {
        File dir = new File(project, "src/main/resources/blueprint/" + name);
        assertTrue(new File(dir, "model").mkdirs());
        String namespace = ns == null
                ? "    base: io.acme." + name
                : String.join(
                        "\n",
                        "    entity: " + ns + ".entity",
                        "    dto: " + ns + ".dto",
                        "    repository: " + ns + ".repository",
                        "    service: " + ns + ".service",
                        "    control: " + ns + ".controller");
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: " + name,
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                namespace,
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    /** One rule-engine run; its output captured when asked for, and echoed. */
    private static int run(File project, String architecture, String output, ByteArrayOutputStream captured) {
        return run(project, architecture, "java", output, captured);
    }

    private static int run(
            File project, String architecture, String language, String output, ByteArrayOutputStream captured) {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.v3",
            "elasticore.rules.v2",
            "elasticore.rules.ts"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        PrintStream out = System.out;
        ByteArrayOutputStream bytes = captured != null ? captured : new ByteArrayOutputStream();
        try {
            for (String k : keys) System.clearProperty(k);
            System.setOut(new PrintStream(bytes, true, "UTF-8"));
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if (output != null) System.setProperty(output, "true");
            return RulesGenRunner.run(new String[0]);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } finally {
            System.setOut(out);
            out.print(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
    }

    /**
     * The 2.x generator in a JVM of its own, as ./gradlew elcore runs it: the
     * extractor keeps the loaded model in static state. Its exit code and log.
     */
    private static String[] extract(File project) throws Exception {
        File log = new File(project, "gen.log");
        Process p = new ProcessBuilder(
                        Paths.get(System.getProperty("java.home"), "bin", "java")
                                .toString(),
                        "-Xmx256m",
                        "-Dfile.encoding=UTF-8",
                        "-Delasticore.base.path=" + project.getAbsolutePath(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        ModelExtractor.class.getName())
                .directory(project)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start();
        try {
            assertTrue(p.waitFor(120, TimeUnit.SECONDS), "generation did not finish");
        } finally {
            p.destroyForcibly();
        }
        return new String[] {
            String.valueOf(p.exitValue()), new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8)
        };
    }

    private static String found(File project, String fileName) throws IOException {
        List<File> hits = new ArrayList<File>();
        collect(project, fileName, hits);
        assertEquals(1, hits.size(), fileName + " in " + project + ": " + hits);
        return new String(Files.readAllBytes(hits.get(0).toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void collect(File dir, String fileName, List<File> hits) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, fileName, hits);
            else if (f.getName().equals(fileName)) hits.add(f);
        }
    }

    @AfterEach
    void clean() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        io.elasticore.base.model.pub.rules.ModelCheck.reset();
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        for (File project : projects) delete(project);
    }

    private static void delete(File root) throws IOException {
        if (!root.exists()) return;
        Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
