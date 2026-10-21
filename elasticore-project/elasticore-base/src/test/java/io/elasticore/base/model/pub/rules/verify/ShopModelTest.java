package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What building a shop on the hexagonal bundle turned up, generated the way a
 * project generates it: the server in Java, then the browser's client.
 *
 * Each assertion is a defect the pet store sample hit - a card number in every
 * order read back, a password a search could read one prefix at a time, and a
 * TypeScript client that described a different server from the one it calls.
 */
public class ShopModelTest {

    private static final String ENV = String.join("\n",
            "elasticore:",
            "  enable: true",
            "  config:",
            "    domainName: ptshop",
            "    framework: springboot",
            "    mode: jpa",
            "    j2ee: jakarta",
            "    testCode: \"true\"",
            "  namespace:",
            "    base: io.acme.ptshop",
            "");

    private static final String MODEL = String.join("\n",
            "enumeration:",
            "  PtCardType:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(4) @code",
            "    enum:",
            "      VISA: VISA",
            "      AMEX: AMEX",
            "  PtOrderStatus:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code:  string(3) @code",
            "      label: string(20)",
            "    enum:",
            "      PLACED:  PLC, Placed",
            "      SHIPPED: SHP, On its way",
            "      LOST:    LST, Lost",
            "  PtPayStatus:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(3) @code",
            "    enum:",
            "      UNPAID:   UNP",
            "      PAID:     PAI",
            "      REFUNDED: REF",
            "",
            "entity:",
            "  PtItem:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      itemId:   long @id @sequence",
            "      name:     string(30)!",
            "      unitCost: money(10.2)!",
            "",
            "  PtAddress:",
            "    meta: entity @embeddable",
            "    fields:",
            "      street1: string(50)!",
            "      city:    string(50)",
            "",
            "  PtCard:",
            "    meta: entity @embeddable",
            "    fields:",
            "      cardNumber: string(30)! @writeonly",
            "      cardType:   PtCardType!",
            "",
            "  PtBuyer:",
            "    meta: entity @root @editable @expose(20) @owner(buyerId) @access(read=owner, create=public, update=owner)",
            "    fields:",
            "      buyerId:  long @id @sequence",
            "      login:    string(10)! @search(eq) @login",
            "      password: string(255)! @password",
            "      nickname: string(30) @default(\"anon\") @access(admin)",
            "      home:     PtAddress @Embedded",
            "",
            "  PtOrder:",
            "    meta: entity @root @expose(20) @event(Shipped) @owner(buyerId) @access(read=owner, create=user, ship=admin)",
            "          @sort(orderId-, nope+)",
            "    fields:",
            "      orderId: string(36) @id @genid",
            "      buyer:   PtBuyer @ManyToOne",
            "      address: PtAddress @Embedded",
            "      card:    PtCard @Embedded",
            "      status:  PtOrderStatus",
            "      total:   money(12.2) @compute(sum(lines.subTotal))",
            "      lines:   List<PtLine> @OneToMany(mappedBy=\"order\") @dtype(List<PtLineDTO>)",
            "    states:",
            "      status:",
            "        initial: PLACED",
            "        ship:    PLACED -> SHIPPED",
            "        delete:  [PLACED, LOST]",
            "",
            "  PtCoupon:",
            "    meta: entity @root @editable @expose(20) @invariant(rate > 0, 'a rate is positive')",
            "          @invariant(rate > 0, 'a rate, said again') @invariant(rate < 100, 'back\\slash')",
            "    fields:",
            "      couponId: long @id @sequence",
            "      rate:     int",
            "      note:     string(10)",
            "      status:   PtOrderStatus",
            "    states:",
            "      status:",
            "        initial: PLACED",
            "        delete:  []",
            "",
            "  PtLine:",
            "    meta: entity @behavior",
            "    fields:",
            "      lineId:   string(36) @id @genid",
            "      order:    PtOrder @ManyToOne",
            "      item:     PtItem @ManyToOne",
            "      quantity: quantity! @min(1)",
            "      unitCost: money(10.2) @snapshot(item.unitCost)",
            "      subTotal: money(12.2) @compute(unitCost * quantity)",
            "",
            "  PtProfile:",
            "    meta: entity @root @editable @expose(20) @owner(userId) @access(read=owner, create=user, update=owner)",
            "    fields:",
            "      userId: long @id",
            "      motto:  string(50)",
            "",
            "  PtLog:",
            "    meta: entity @root @editable @expose(20) @access(read=owner)",
            "    fields:",
            "      logId: long @id @sequence",
            "      text:  string(50)",
            "",
            "  PtQuote:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      quoteId: long @id @sequence",
            "      item:    PtItem @ManyToOne",
            "      price:   money(10.2) @snapshot(item.unitCost)",
            "",
            "  PtReturn:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      returnId: string(36) @id @genid",
            "      status:   PtOrderStatus @behavior",
            "    commands:",
            "      refund:",
            "",
            "  PtFlow:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      flowId: string(36) @id @genid",
            "      stage:  string(10)",
            "    states:",
            "      stage:",
            "        initial: NEW",
            "        close:   NEW -> DONE",
            "",
            "  PtParcel:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      parcelId: string(36) @id @genid",
            "      status:   PtOrderStatus",
            "    states:",
            "      status:",
            "        initial: PLACED",
            "        send:    PLACED -> SHIPED",
            "",
            "  PtBill:",
            "    meta: entity @root @expose(20) @event(Refunded)",
            "    fields:",
            "      billId:  string(36) @id @genid",
            "      status:  PtOrderStatus",
            "      payment: PtPayStatus",
            "    states:",
            "      status:",
            "        initial: PLACED",
            "        cancel:  PLACED -> LOST",
            "      payment:",
            "        initial: UNPAID",
            "        pay:     UNPAID -> PAID",
            "        cancel:  PAID -> REFUNDED",
            "",
            "dto:",
            "  PtLineDTO:",
            "    meta: dto @template(PtLine)",
            "");

    private static File project;

    /** What the server's generation printed, its DSL warnings among it. */
    private static String serverLog = "";

    @BeforeAll
    static void generate() throws Exception {
        project = Files.createTempDirectory("elcore-ptshop").toFile();
        File blueprint = new File(project, "src/main/resources/blueprint/ptshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(new File(blueprint, "env.yml").toPath(), ENV.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), MODEL.getBytes(StandardCharsets.UTF_8));

        java.io.PrintStream out = System.out;
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(captured, true, "UTF-8"));
        try {
            run(false);   // the server, as elcore3 writes it
        } finally {
            System.setOut(out);
        }
        serverLog = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        out.print(serverLog);
        run(true);    // the browser's client, as elcore3ts writes it
    }

    private static void run(boolean typescript) {
        String[] keys = {"elasticore.base.path", "elasticore.architecture", "elasticore.language",
                "elasticore.rules.force", "elasticore.rules.ts"};
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "hexagonal");
            System.setProperty("elasticore.language", "java");
            System.setProperty("elasticore.rules.force", "true");
            if (typescript) System.setProperty("elasticore.rules.ts", "true");
            RulesGenRunner.main(new String[0]);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
    }

    @AfterAll
    static void clean() throws Exception {
        // The generated model stays process-wide; later tests expect none loaded.
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        if (project == null || !project.exists()) return;
        Files.walkFileTree(project.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, java.io.IOException e) throws java.io.IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String java(String path) throws Exception {
        return read("src/main/java/io/acme/ptshop/" + path);
    }

    private static String ts(String name) throws Exception {
        return read("src/main/ts/" + name + ".ts");
    }

    private static String read(String path) throws Exception {
        File f = new File(project, path);
        assertTrue(f.exists(), "not generated: " + path);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    // ---- the server -------------------------------------------------------------

    /** A DTO holds the embeddable itself, so what a reply must not show is said on it. */
    @Test
    public void aWriteOnlyValueInsideAnEmbeddableStaysOutOfEveryReply() throws Exception {
        String card = java("domain/model/PtCard.java");
        assertTrue(card.contains("@JsonProperty(access=JsonProperty.Access.WRITE_ONLY)"), card);
    }

    /** And what a request must hold is checked with the DTO, as a 400 rather than a constraint failure. */
    @Test
    public void anEmbeddedValueIsValidatedWithItsDto() throws Exception {
        String address = java("domain/model/PtAddress.java");
        assertTrue(address.contains("@NotNull") && address.contains("@Size(max=50)"), address);

        String order = java("application/dto/PtOrderDTO.java");
        assertTrue(order.matches("(?s).*@Valid\\s+private PtAddress address;.*"), order);
        assertTrue(order.matches("(?s).*@Valid\\s+private PtCard card;.*"), order);
    }

    /** A write-only value never leaves through a DTO, so server code cannot carry it back in. */
    @Test
    public void aWriteOnlyValueIsNotCopiedIntoADto() throws Exception {
        String mapper = java("application/dto/PtshopMapper.java");
        // Two DTO-to-entity overloads write it; the entity-to-DTO pass, once a third, no longer reads it.
        int copies = mapper.split(java.util.regex.Pattern.quote("from.getPassword(), to::setPassword"), -1).length - 1;
        assertTrue(copies == 2, "only the request writes a password: " + copies);
    }

    /** A relation held by its identity is searched by that identity, once - not also through the join. */
    @Test
    public void aReferenceIsSearchedOnce() throws Exception {
        String search = java("application/dto/PtOrderSrchDTO.java");
        assertTrue(search.contains("private Long buyerId;"), search.replaceAll("(?s).*class PtOrderSrchDTO", ""));
        assertFalse(search.contains("buyerBuyerId"), search.replaceAll("(?s).*class PtOrderSrchDTO", ""));
        String mapper = java("application/dto/PtshopMapper.java");
        assertFalse(mapper.contains("BuyerBuyerId"), "the mapper must not read or write what the DTOs no longer have");
        String dto = java("application/dto/PtOrderDTO.java");
        assertFalse(dto.contains("buyerBuyerId"), dto.replaceAll("(?s).*class PtOrderDTO", ""));
    }

    /** A derived search on a write-only field read a password hash one LIKE prefix at a time. */
    @Test
    public void aWriteOnlyFieldIsNoSearchCondition() throws Exception {
        String search = java("application/dto/PtBuyerSrchDTO.java");
        assertFalse(search.contains("password"), search);
        assertTrue(search.contains("private String login;"), search);
    }

    /**
     * What the request carried, on every write path at once: a password is
     * hashed in one override rather than in save, update, batchUpdate and
     * updateBySpecification each.
     */
    @Test
    public void everyWritePathHandsTheRequestToOneHook() throws Exception {
        String core = java("application/PtBuyerCoreService.java");
        assertTrue(core.contains("protected void beforeSave(PtBuyer entity, PtBuyerDTO request) {"), core);
        int calls = core.split("beforeSave\\(entity, dto\\);", -1).length - 1;
        // batchUpdate goes through update(dto) for each row.
        assertTrue(calls == 3, "save, update and updateBySpecification each call it: " + calls);
        assertTrue(core.contains("update(dto);"), core);
    }

    // ---- the domain rules the DSL states ----------------------------------------

    /** states: each move is a command the base writes, refusing a state it does not start in. */
    @Test
    public void aLifecycleIsWrittenNotPromised() throws Exception {
        String base = java("domain/model/PtOrderBase.java");
        assertTrue(base.contains("public void ship() {") && base.contains("moveStatus(\"ship\", PtOrderStatus.SHIPPED, PtOrderStatus.PLACED);"), base);
        assertTrue(base.contains("raiseShipped();") && base.contains("public boolean canShip()"), base);
        assertTrue(base.contains("private PtOrderStatus status = PtOrderStatus.PLACED;"), "a new order starts PLACED");
        assertFalse(base.contains("abstract void ship"), base);
        assertFalse(java("domain/model/PtOrder.java").contains("ship()"), "nothing left for the developer to write");
    }

    /** The generated class says the whole lifecycle: where it starts, each move, where it ends, what is never reached. */
    @Test
    public void theLifecycleIsSaidWhereItIsWritten() throws Exception {
        String base = java("domain/model/PtOrderBase.java");
        assertTrue(base.contains("Lifecycle of status (PtOrderStatus), from the DSL's states:"), base);
        assertTrue(base.contains("  start:  PLACED"), base);
        assertTrue(base.contains("  ship:   PLACED -> SHIPPED"), base);
        assertTrue(base.contains("  end:    SHIPPED - no command leaves it"), base);
        assertTrue(base.contains("  unused: LOST - no command moves a row there"), base);
    }

    /** One command in two lifecycles: every lifecycle is checked before any moves. */
    @Test
    public void aCommandOfTwoLifecyclesChecksBothThenMovesBoth() throws Exception {
        String base = java("domain/model/PtBillBase.java");
        int requireStatus = base.indexOf("requireStatus(\"cancel\", PtOrderStatus.PLACED);");
        int requirePayment = base.indexOf("requirePayment(\"cancel\", PtPayStatus.PAID);");
        int moveStatus = base.indexOf("this.status = PtOrderStatus.LOST;");
        int movePayment = base.indexOf("this.payment = PtPayStatus.REFUNDED;");
        assertTrue(requireStatus > 0 && requirePayment > requireStatus && moveStatus > requirePayment && movePayment > moveStatus, base);
        assertTrue(base.contains("raiseRefunded();"), base);
        assertTrue(base.contains("return (this.status == PtOrderStatus.PLACED) && (this.payment == PtPayStatus.PAID);"), base);
        assertTrue(base.contains("movePayment(\"pay\", PtPayStatus.PAID, PtPayStatus.UNPAID);"), "a command of one lifecycle is as it was");
        assertTrue(base.contains("  cancel: PAID -> REFUNDED  (with status)"), base);
        assertFalse(base.contains("abstract void cancel"), base);
        assertFalse(serverLog.contains("in two lifecycles"), serverLog);

        String api = ts("PtBillApi");
        assertTrue(api.contains("return value != null && (value.status === PtOrderStatus.PLACED) && (value.payment === PtPayStatus.PAID);"), api);
        assertTrue(api.split("export function canCancel", -1).length == 2, "one canCancel");
    }

    /**
     * A refusal carries a code, and the domain's bundle lists every code with
     * its default wording - what a translator copies to _ko.properties.
     */
    @Test
    public void everyRefusalHasACodeTheBundleLists() throws Exception {
        String base = java("domain/model/PtOrderBase.java");
        assertTrue(base.contains("InvariantViolationException(\"state.PtOrder.status.\" + command,"), base);
        assertTrue(base.contains("String.valueOf(this.status), java.util.Arrays.toString(from));"), base);

        String bundle = read("src/main/resources/i18n/ptshop-messages.properties");
        assertTrue(bundle.contains("state.PtOrder.status.ship=PtOrder cannot ship when status is {0}; it must be {1}"), bundle);
        // One command of two lifecycles: either can refuse it, each by its own code.
        assertTrue(bundle.contains("state.PtBill.status.cancel="), bundle);
        assertTrue(bundle.contains("state.PtBill.payment.cancel="), bundle);
        assertTrue(bundle.contains("error.not-found.title=Not found"), bundle);
        assertTrue(bundle.contains("error.stale=The row was changed after it was read. Read it again and resend."), bundle);

        String errors = java("adapter/in/web/PtshopApiErrors.java");
        assertTrue(errors.contains("new ErrorMessages(messageSource.getIfUnique(), \"i18n/ptshop-messages\")"), errors);
        assertTrue(errors.contains("String code = ex.getCode();"), errors);
        assertTrue(errors.contains("messages.text(code, ex.getMessage(), ex.getArgs())"), errors);
        // What Spring MVC refuses before a controller runs: the runtime's one advice, imported.
        assertTrue(errors.contains("@Import(RequestShapeErrors.class)"), errors);
        // A service-side violation is said in the request's language, built-ins too.
        assertTrue(errors.contains("String message = messages.violation(code + \".\" + field, v, v.getMessage());"), errors);
        assertTrue(errors.contains("problem(HttpStatus.BAD_GATEWAY, \"upstream\", \"A called system failed\","), errors);
        // The codes those answers use are in the bundle a translator copies.
        assertTrue(bundle.contains("error.unreadable=The request body is missing or is not valid JSON."), bundle);
        assertTrue(bundle.contains("error.method-not-allowed.title=Method not allowed"), bundle);
        assertTrue(bundle.contains("error.type.enum=must be one of: {0}"), bundle);
        assertTrue(bundle.contains("error.upstream.title=A called system failed"), bundle);

        // findById and delete of a row that is not there: the problem body's 404, not an empty one.
        String controller = java("adapter/in/web/PtOrderController.java");
        assertTrue(controller.contains("orElseThrow(() -> new NotFoundException(\"PtOrder not found\"))"), controller);
        assertTrue(controller.contains("import io.elasticore.springboot3.validation.NotFoundException;"), controller);
        assertFalse(controller.contains("ResponseEntity.notFound()"), controller);

        String support = read("src/main/ts/apiSupport.ts");
        assertTrue(support.contains("get code(): string | undefined"), support);
    }

    /** states: delete - a row is deleted only in the states named, checked before JPA removes it. */
    @Test
    public void aLifecycleSaysWhenARowMayBeDeleted() throws Exception {
        String base = java("domain/model/PtOrderBase.java").replace("\r\n", "\n");
        assertTrue(base.contains("public boolean canDelete() {\n\t\treturn this.status == PtOrderStatus.PLACED || this.status == PtOrderStatus.LOST;")
                || base.contains("return this.status == PtOrderStatus.PLACED || this.status == PtOrderStatus.LOST;"), base);
        // No invariant marks this root's removal, so the check is the @PreRemove itself.
        assertTrue(base.matches("(?s).*@jakarta\\.persistence\\.PreRemove\\s+void checkDeletable\\(\\).*"), base);
        assertTrue(base.contains("InvariantViolationException(\"state.PtOrder.status.delete\","), base);
        // delete names states, not a command: no command method, no POST /{id}/delete.
        assertFalse(base.contains("void delete(") || base.contains("abstract void delete"), base);
        assertFalse(java("adapter/in/web/PtOrderController.java").contains("}/delete\""), "delete is no command");
        assertTrue(read("src/main/resources/i18n/ptshop-messages.properties")
                .contains("state.PtOrder.status.delete=PtOrder cannot be deleted when status is {0}; it must be {1}"));
    }

    /** Two promises with one condition get two codes; a message keeps its backslash; delete: needs a split root. */
    @Test
    public void invariantCodesStayDistinctAndMessagesStayAsWritten() throws Exception {
        String bundle = read("src/main/resources/i18n/ptshop-messages.properties");
        assertTrue(bundle.contains("invariant.PtCoupon.rate-gt-0=a rate is positive"), bundle);
        assertTrue(bundle.contains("invariant.PtCoupon.rate-gt-0-2=a rate, said again"), bundle);
        assertTrue(bundle.contains("invariant.PtCoupon.rate-lt-100=back\\\\slash"), bundle);

        String invariants = find("PtCouponInvariants.java");
        assertTrue(invariants.contains("\"invariant.PtCoupon.rate-lt-100\", \"back\\\\slash\""), invariants);
        assertTrue(ts("PtCouponInvariants").contains("broken.push('back\\\\slash');"), ts("PtCouponInvariants"));

        // @editable keeps the root in one class: no base to write the guard in, and that is said.
        assertTrue(serverLog.contains("PtCoupon states.status.delete: written only for a root with a behaviour class"), serverLog);

        String api = ts("PtOrderApi");
        assertTrue(api.contains("export function canDelete(value: PtOrderDTO | null | undefined): boolean"), api);
        assertTrue(api.contains("(value.status === PtOrderStatus.PLACED || value.status === PtOrderStatus.LOST)"), api);
    }

    private static String find(String fileName) throws Exception {
        java.util.List<Path> hits = new java.util.ArrayList<Path>();
        try (java.util.stream.Stream<Path> all = Files.walk(project.toPath())) {
            all.filter(p -> p.getFileName().toString().equals(fileName)).forEach(hits::add);
        }
        assertTrue(hits.size() == 1, fileName + ": " + hits);
        return new String(Files.readAllBytes(hits.get(0)), StandardCharsets.UTF_8);
    }

    /** @sort: the order a search answers in when the request names none; a property that is not there is said. */
    @Test
    public void aSearchWithoutASortUsesTheDeclaredOne() throws Exception {
        String search = java("application/dto/PtOrderSrchDTO.java").replace("\r\n", "\n");
        assertTrue(search.contains("@Builder.Default\n\tprivate String sortCode=\"orderId-\";"), search);
        assertTrue(java("application/dto/PtBuyerSrchDTO.java").contains("private String sortCode;"),
                "a search without @sort is unsorted, as before");
        assertTrue(serverLog.contains("@sort(orderId-, nope+): nope is not a field of PtOrder"), serverLog);
    }

    /** A state the enum does not have is a warning, not a compile error: the command stays the developer's. */
    @Test
    public void aStateTheEnumDoesNotHaveIsSaid() throws Exception {
        assertTrue(serverLog.contains("PtParcel states.status names SHIPED, which PtOrderStatus does not have (it has PLACED, SHIPPED, LOST)"),
                serverLog);
        String base = java("domain/model/PtParcelBase.java");
        assertFalse(base.contains("SHIPED"), base);
        assertTrue(base.contains("public abstract void send();"), base);
    }

    /** @snapshot is taken once from the row referred to; @compute is worked out on every save. */
    @Test
    public void derivedValuesAreFilledInBeforeEachSave() throws Exception {
        String line = java("domain/model/PtLineBase.java");
        assertTrue(line.contains("PtItem source = (PtItem) lookup.find(PtItem.class, this.itemId);"), line);
        assertTrue(line.contains("this.subTotal = (this.unitCost == null || this.quantity == null) ? null : this.unitCost.times(this.quantity);"), line);
        String order = java("domain/model/PtOrderBase.java");
        assertTrue(order.contains("for (PtLine member : this.lines) member.derive(lookup);"), order);
        assertTrue(order.contains("totalSum = totalSum.plus(linesItem.getSubTotal())"), order);
        String service = java("application/PtOrderCoreService.java");
        // A batch update derives through update(dto).
        assertTrue(service.split("entity\\.derive\\(entityManager::find\\);", -1).length - 1 >= 4,
                "create, update, update by condition and the command derive first");
        assertTrue(java("application/dto/PtLineDTO.java").contains("@JsonProperty(access=JsonProperty.Access.READ_ONLY)"),
                "a derived value is shown, never taken from a request");
    }

    /** @access and @owner are the regenerated service's to enforce. */
    @Test
    public void accessIsCheckedWhereEveryCallerPasses() throws Exception {
        String service = java("application/PtOrderCoreService.java");
        assertTrue(service.contains("AccessPolicy.parse(\"PtOrder\", \"create=user, read=owner, ship=admin\", \"ADMIN\")"), service);
        assertTrue(service.contains(".filter(this::readable)") && service.contains("accessSearch(searchDTO);"), service);
        assertTrue(service.contains("accessWrite(entity, \"ship\");"), service);
        assertTrue(service.contains("if (!ACCESS.isAdmin(currentUser)) dto.setBuyerId("), "a new order belongs to whoever is signed in");
        String buyer = java("application/PtBuyerCoreService.java");
        assertTrue(buyer.contains("dto.setNickname(entity.getNickname());"), "an update keeps it");
        assertTrue(buyer.contains("dto.setPassword(passwordHasher.hash(dto.getPassword()))"), buyer);
        assertTrue(buyer.contains("public Optional<PtBuyerDTO> authenticate(String login, String password)"), buyer);
        assertTrue(java("application/port/in/PtBuyerUseCase.java").contains("authenticate(String login, String password)"));
        assertTrue(java("adapter/in/web/PtshopApiErrors.java").contains("HttpStatus.UNAUTHORIZED"));
    }

    /** The search is narrowed by an equality on the owner, whatever the owner field searches by. */
    @Test
    public void aSearchIsNarrowedByAnEqualityOnTheOwner() throws Exception {
        String service = java("application/PtOrderCoreService.java");
        assertTrue(service.contains("ownedOnly(PtshopMapper.toSpec(searchDTO))"), service);
        assertTrue(service.contains("cb.equal(r.get(\"buyerId\"), id)"), service);
        assertFalse(service.contains("searchDTO.setBuyerId"), "not through the search field, which may be a LIKE");
    }

    /** Owner-level without an owner the build can find: said at build time, refused at run time. */
    @Test
    public void ownerLevelWithNoOwnerIsTheAdministratorsAlone() throws Exception {
        String service = java("application/PtLogCoreService.java");
        assertTrue(service.contains("ForbiddenException(\"PtLog is read by its owner, and names none (@owner)\")"), service);
    }

    /** What the caller may not change is what the row already holds - not null, which a full merge would write. */
    @Test
    public void anUpdateKeepsTheOwnerAndTheAdministratorsFields() throws Exception {
        String service = java("application/PtBuyerCoreService.java");
        assertTrue(service.contains("dto.setNickname(entity.getNickname());"), service);
        String order = java("application/PtOrderCoreService.java");
        assertTrue(order.contains("dto.setBuyerId(entity.getBuyerId());"), order);
        assertTrue(order.indexOf("accessUpdate(entity, dto);") < order.indexOf("Invalid PtOrderDTO ID\");"),
                "checked before the version, so a stale version does not tell a stranger the row exists");
    }

    /** An administrator's update does not send back a default for what only they write. */
    @Test
    public void anAdministratorsFieldHasNoDtoDefault() throws Exception {
        String dto = java("application/dto/PtBuyerDTO.java");
        assertTrue(dto.contains("private String nickname;"), dto);
        assertTrue(java("application/PtBuyerCoreService.java").contains("dto.setNickname(new PtBuyer().getNickname());"));
    }

    /** A key that is its owner and is not generated is the signed-in user's, not the request's. */
    @Test
    public void anOwnerKeyIsTheCallers() throws Exception {
        String service = java("application/PtProfileCoreService.java");
        assertTrue(service.contains("if (!ACCESS.isAdmin(currentUser)) dto.setUserId(io.elasticore.springboot3.security.AccessPolicy.as(currentUser.id(), Long.class));"), service);
    }

    /** A derived value is out of a request's reach on an @editable entity too. */
    @Test
    public void aSnapshotIsReadOnlyWithoutABehaviourClass() throws Exception {
        String dto = java("application/dto/PtQuoteDTO.java");
        assertTrue(dto.matches("(?s).*@JsonProperty\\(access=JsonProperty.Access.READ_ONLY\\)\\s+private Money price;.*"), dto);
        assertTrue(java("domain/model/PtQuote.java").contains("public void derive("), "the entity derives it");
    }

    /** A row of an entity only its owner reads does not ride along in another's DTO. */
    @Test
    public void aGuardedRowIsNotNested() throws Exception {
        String dto = java("application/dto/PtOrderDTO.java");
        String body = dto.replaceAll("(?s).*class PtOrderDTO", "");
        assertFalse(dto.contains("PtBuyerDTO buyer"), body);
        assertTrue(dto.contains("private Long buyerId;"), body);
    }

    /** A command without a move is still promised to the developer. */
    @Test
    public void aCommandWithoutAMoveStaysAbstract() throws Exception {
        assertTrue(java("domain/model/PtReturnBase.java").contains("public abstract void refund();"));
        assertTrue(java("domain/model/PtReturn.java").contains("PtReturn.refund: write the rule"));
    }

    /** A lifecycle the generator leaves out leaves its commands promised, not missing. */
    @Test
    public void aLifecycleLeftOutKeepsItsCommandsAbstract() throws Exception {
        String base = java("domain/model/PtFlowBase.java");
        assertTrue(base.contains("public abstract void close();"), base);
        assertFalse(base.contains("String.NEW"), "a string is no lifecycle");
    }

    // ---- the browser's client ---------------------------------------------------

    /** The client describes the JSON this server sends: references by id, values inline. */
    @Test
    public void theClientHasTheServersShape() throws Exception {
        String order = ts("PtOrderDTO");
        assertTrue(order.contains("buyerId?: number;"), order);
        assertTrue(order.contains("address?: PtAddress;") && order.contains("import { PtAddress } from './PtAddress';"), order);
        assertTrue(order.contains("card?: PtCard;"), order);
        assertTrue(ts("PtLineDTO").contains("itemId?: number;"), ts("PtLineDTO"));

        String card = ts("PtCard");
        assertTrue(card.contains("export interface PtCard {"), card);
        assertTrue(card.contains("cardType: PtCardType;"), card);
    }

    /** A write-only value is in no reply, and one interface is both. */
    @Test
    public void aWriteOnlyFieldIsOptional() throws Exception {
        assertTrue(ts("PtBuyerDTO").contains("password?: string;"), ts("PtBuyerDTO"));
        assertTrue(ts("PtCard").contains("cardNumber?: string;"), ts("PtCard"));
    }

    /** Jackson writes an amount as a JSON number, so that is what the browser holds. */
    @Test
    public void anAmountIsANumber() throws Exception {
        assertTrue(ts("PtItemDTO").contains("unitCost: number;"), ts("PtItemDTO"));
        assertTrue(ts("PtOrderDTO").contains("total?: number;"), ts("PtOrderDTO"));
    }

    /** A root changed by its commands only has no update endpoint, and its client offers none. */
    @Test
    public void theClientOffersOnlyTheEndpointsTheControllerHas() throws Exception {
        String controller = java("adapter/in/web/PtOrderController.java");
        assertFalse(controller.contains("@PutMapping"), controller);

        String api = ts("PtOrderApi");
        assertFalse(api.contains("function update(") || api.contains("function batchUpdate("), api);
        assertTrue(api.contains("function ship(") && api.contains("function create("), api);
        assertTrue(ts("PtItemApi").contains("function update("), "an @editable root keeps its update");
    }

    /** What a screen needs from the DSL: labels, checks, the lifecycle, the server's problem. */
    @Test
    public void theClientCarriesTheRulesAScreenUses() throws Exception {
        assertTrue(ts("PtOrderStatus").contains("[PtOrderStatus.SHIPPED]: \"On its way\""), ts("PtOrderStatus"));
        String address = ts("PtAddress");
        assertTrue(address.contains("export function validatePtAddress(") && address.contains("street1 is required"), address);
        String line = ts("PtLineDTO");
        assertTrue(line.contains("quantity must be at least 1"), line);
        assertFalse(line.contains("errors.subTotal"), "a derived value is not checked: no request writes it");
        assertFalse(ts("PtOrderDTO").contains("buyer?: PtBuyerDTO"), "the browser is not promised the row the server leaves out");
        String api = ts("PtOrderApi");
        assertTrue(api.contains("export function canShip(value: PtOrderDTO | null | undefined): boolean"), api);
        assertTrue(api.contains("import { PtOrderStatus } from './PtOrderStatus';"), api);
        assertTrue(read("src/main/ts/apiSupport.ts").contains("readonly fieldErrors: Record<string, string>"));
    }

    /** A search may leave any condition out, and pages and sorts as the Java one does. */
    @Test
    public void aSearchConditionIsOptionalAndTheSearchPages() throws Exception {
        String search = ts("PtItemSrchDTO");
        assertTrue(search.contains("name?: string;") && search.contains("unitCost?: number;"), search);
        assertTrue(search.contains("pageNumber?: number;") && search.contains("pageSize?: number;"), search);
        assertTrue(search.contains("sortColumn?: string;") && search.contains("sortAscending?: boolean;"), search);
    }
}
