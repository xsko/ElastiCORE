package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.ModelCheck;
import io.elasticore.base.model.pub.rules.RuleCatalog;
import io.elasticore.base.model.pub.rules.SourceCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** @stamp, rule by rule, on a model built in memory. */
public class StateStampRulesTest {

    @BeforeEach
    void start() {
        // timestamp is a type of the rule engine, as a 3.0 build reads it.
        io.elasticore.base.model.entity.BaseFieldType.ruleEngineTypes(true);
        ModelCheck.reset();
        io.elasticore.base.util.ConsoleLog.clear();
    }

    @AfterEach
    void forget() {
        io.elasticore.base.model.entity.BaseFieldType.ruleEngineTypes(false);
        ModelCheck.reset();
        io.elasticore.base.util.ConsoleLog.clear();
    }

    /** The DSL warnings stored so far. */
    @SuppressWarnings("unchecked")
    private static String warnings() {
        Object[] stored = (Object[]) io.elasticore.base.util.ConsoleLog.snapshot();
        List<String> warn = ((Map<String, List<String>>) stored[0]).get("DSL_WARN");
        return warn == null ? "" : String.join("\n", warn);
    }

    private static Map<String, String> lifecycle(String... keyValues) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (int i = 0; i < keyValues.length; i += 2) out.put(keyValues[i], keyValues[i + 1]);
        return out;
    }

    /** A shipment with two lifecycles; cancel moves both. */
    private static EntitySpec shipment(boolean split, String... stamped) {
        String base = "com.acme.ship";
        EntitySpec e = new EntitySpec("Shipment")
                .domain("ship", "Ship")
                .meta("root", "")
                .meta("expose", "")
                .pkg("entity", base + ".domain.model")
                .pkg("dto", base + ".application.dto")
                .pkg("service", base + ".application")
                .pkg("control", base + ".adapter.in.web")
                .pkg("inboundPort", base + ".application.port.in")
                .knownEnum("Status", "3", true)
                .enumConstantNames("Status", Arrays.asList("READY", "SENT", "CANCELLED"))
                .knownEnum("Fee", "3", true)
                .enumConstantNames("Fee", Arrays.asList("DUE", "PAID", "CANCELLED"))
                .field("shipmentId", "string(36) @id")
                .field("status", "Status")
                .field("fee", "Fee");
        for (int i = 0; i < stamped.length; i += 2) e.field(stamped[i], stamped[i + 1]);
        e.addStateMachine(
                "status", lifecycle("initial", "READY", "send", "READY -> SENT", "cancel", "READY, SENT -> CANCELLED"));
        e.addStateMachine("fee", lifecycle("initial", "DUE", "pay", "DUE -> PAID", "cancel", "PAID -> CANCELLED"));
        if (split) e.entitySplitForBehavior("Shipment");
        return e;
    }

    private static String render(EntitySpec e, String bundle, String target) {
        return RuleCatalog.standard()
                .engineFor(bundle)
                .templateSet(bundle.endsWith("-kt") ? SourceCode.TemplateSet.KOTLIN : SourceCode.TemplateSet.V3)
                .run(e, target)
                .render()
                .replace("\r", "")
                .replaceAll("(?m)^[ \t]+", "");
    }

    private static List<String> written(EntitySpec e) {
        List<String> out = new java.util.ArrayList<String>();
        for (DddStateRules.Stamp s : DddStateRules.stamps(e, true))
            out.add(s.field.name() + "<-" + s.machine.field() + "." + s.state);
        return out;
    }

    @Test
    public void eachFormNamesItsLifecycleAndState() {
        EntitySpec e = shipment(
                true,
                "sentAt",
                "timestamp @stamp(SENT)",
                "paidOn",
                "date @stamp(PAID)",
                "cancelledAt",
                "datetime @stamp(status.CANCELLED)",
                "feeEndedAt",
                "timestamp @stamp(fee.CANCELLED)",
                "endedAt",
                "timestamp @stamp([SENT, 'status.CANCELLED'])",
                "twiceAt",
                "timestamp @stamp(SENT, status.SENT)");
        assertEquals(
                Arrays.asList(
                        "sentAt<-status.SENT",
                        "paidOn<-fee.PAID",
                        "cancelledAt<-status.CANCELLED",
                        "feeEndedAt<-fee.CANCELLED",
                        "endedAt<-status.SENT",
                        "endedAt<-status.CANCELLED",
                        "twiceAt<-status.SENT"),
                written(e));
        assertTrue(ModelCheck.failures().isEmpty(), ModelCheck.failures().toString());
    }

    @Test
    public void whatCannotBeWrittenFailsWithItsReason() {
        EntitySpec e = shipment(
                true,
                "none",
                "timestamp @stamp",
                "word",
                "string(10) @stamp(SENT)",
                "either",
                "timestamp @stamp(CANCELLED)",
                "start",
                "timestamp @stamp(READY)",
                "typo",
                "timestamp @stamp(SENTT)",
                "other",
                "timestamp @stamp(fee.SENT)",
                "dot",
                "timestamp @stamp(.SENT)");
        assertTrue(written(e).isEmpty(), written(e).toString());
        String said = String.join("\n", ModelCheck.failures());
        assertTrue(said.contains("Shipment.none @stamp names no state"), said);
        assertTrue(said.contains("Shipment.word @stamp is String, not a time"), said);
        assertTrue(
                said.contains(
                        "Shipment.either @stamp names CANCELLED, which status and fee both have - write @stamp(status.CANCELLED)"),
                said);
        assertTrue(
                said.contains(
                        "Shipment.start @stamp names READY, which no move of states.status enters (it is where a row starts"),
                said);
        assertTrue(said.contains("Shipment.typo @stamp names SENTT, which no lifecycle of Shipment has"), said);
        assertTrue(said.contains("Shipment.other @stamp names fee.SENT, which no lifecycle of Shipment has"), said);
        assertTrue(said.contains("Shipment.dot @stamp names .SENT, which no lifecycle of Shipment has"), said);
        assertEquals(7, ModelCheck.failures().size(), said);
    }

    @Test
    public void readingTheStampsForAnotherRuleSaysNothing() {
        EntitySpec e = shipment(true, "typo", "timestamp @stamp(SENTT)", "sentAt", "timestamp @stamp(SENT)");
        assertEquals(1, DddStateRules.stamps(e, false).size());
        assertTrue(ModelCheck.failures().isEmpty(), ModelCheck.failures().toString());
    }

    @Test
    public void aStateNoMoveEntersButTheStartIsSaidWithoutTheHint() {
        EntitySpec e = shipment(true, "lostAt", "timestamp @stamp(fee.CANCELLED)");
        e.addStateMachine("lost", lifecycle("initial", "READY"));
        e.field("lost", "Status").field("lostOn", "timestamp @stamp(lost.SENT)");
        written(e);
        String said = String.join("\n", ModelCheck.failures());
        assertTrue(
                said.contains("Shipment.lostOn @stamp names SENT, which no move of states.lost enters - nothing"),
                said);
    }

    @Test
    public void theCommandsWriteTheStamps() {
        EntitySpec e = shipment(
                true,
                "sentAt",
                "timestamp @stamp(SENT)",
                "cancelledAt",
                "datetime @stamp(status.CANCELLED)",
                "feeEndedAt",
                "date @stamp(fee.CANCELLED)");
        String java = render(e, "hexagonal-springboot", "entity");
        assertTrue(
                java.contains(
                        "moveStatus(\"send\", Status.SENT, Status.READY);\nthis.sentAt = java.time.Instant.now();\n}"),
                java);
        assertTrue(
                java.contains("this.status = Status.CANCELLED;\nthis.cancelledAt = java.time.LocalDateTime.now();\n"
                        + "this.fee = Fee.CANCELLED;\nthis.feeEndedAt = java.time.LocalDate.now();\n}"),
                java);
        assertTrue(java.contains("stamp:  sentAt - the time it last entered SENT"), java);
        assertFalse(
                java.contains("stamp:  feeEndedAt - the time it last entered CANCELLED\n *   stamp:  sentAt"), java);
        String kt = render(e, "hexagonal-springboot-kt", "entity");
        assertTrue(
                kt.contains(
                        "moveStatus(\"send\", Status.SENT, Status.READY)\nthis.sentAt = java.time.Instant.now()\n}"),
                kt);
        assertTrue(kt.contains("this.fee = Fee.CANCELLED\nthis.feeEndedAt = java.time.LocalDate.now()\n}"), kt);
    }

    @Test
    public void aSplitRootKeepsTheStampToItself() {
        EntitySpec e = shipment(true, "sentAt", "timestamp @stamp(SENT)");
        String java = render(e, "hexagonal-springboot", "entity");
        assertTrue(
                java.matches(
                        "(?s).*@Setter\\(AccessLevel.PROTECTED\\)\\s+(@\\S+\\s+)*private (java.time.)?Instant sentAt;.*"),
                java);
        String dto = render(e, "hexagonal-springboot", "dto");
        assertTrue(dto.matches("(?s).*READ_ONLY\\)\\s+private (java.time.)?Instant sentAt;.*"), dto);
    }

    @Test
    public void anUnsplitRootIsSaidAndKeepsAWritableField() {
        EntitySpec e = shipment(false, "sentAt", "timestamp @stamp(SENT)");
        String java = render(e, "hexagonal-springboot", "entity");
        assertFalse(java.contains("this.sentAt ="), java);
        assertFalse(java.matches("(?s).*PROTECTED\\)\\s+(@\\S+\\s+)*private (java.time.)?Instant sentAt;.*"), java);
        String dto = render(e, "hexagonal-springboot", "dto");
        assertFalse(dto.matches("(?s).*READ_ONLY\\)\\s+private (java.time.)?Instant sentAt;.*"), dto);
        String warned = warnings();
        assertTrue(warned.contains("Shipment.sentAt @stamp: written only for a root with a behaviour class"), warned);
        assertFalse(warned.contains("is @behavior but"), warned);
        assertTrue(ModelCheck.failures().isEmpty(), ModelCheck.failures().toString());
    }

    @Test
    public void aParentWithoutALifecycleIsSaidAsSuch() {
        EntitySpec parent = new EntitySpec("Doc")
                .domain("ship", "Ship")
                .pkg("entity", "com.acme.ship.domain.model")
                .field("postedAt", "timestamp @stamp(SENT)");
        render(parent, "hexagonal-springboot", "entity");
        String warned = warnings();
        assertTrue(
                warned.contains(
                        "Doc.postedAt @stamp: Doc has no states:; it is stamped only by a root with a behaviour class that"
                                + " extends Doc and moves into the state."),
                warned);
    }

    @Test
    public void aSplitRootWithoutALifecycleFails() {
        EntitySpec e = new EntitySpec("Memo")
                .domain("ship", "Ship")
                .meta("root", "")
                .pkg("entity", "com.acme.ship.domain.model")
                .field("memoId", "string(36) @id")
                .field("sentAt", "timestamp @stamp(SENT)")
                .entitySplitForBehavior("Memo");
        render(e, "hexagonal-springboot", "entity");
        assertTrue(
                String.join("\n", ModelCheck.failures())
                        .contains("Memo.sentAt @stamp names SENT, which no lifecycle of Memo has"),
                ModelCheck.failures().toString());
    }

    @Test
    public void nowIsTheFieldsOwnType() {
        EntitySpec e = shipment(true, "a", "date", "b", "datetime", "c", "timestamp", "d", "time", "f", "long");
        assertEquals("java.time.LocalDate.now()", DddStateRules.nowOf(e.fields().get(3)));
        assertEquals(
                "java.time.LocalDateTime.now()", DddStateRules.nowOf(e.fields().get(4)));
        assertEquals("java.time.Instant.now()", DddStateRules.nowOf(e.fields().get(5)));
        assertEquals(null, DddStateRules.nowOf(e.fields().get(6)));
        assertEquals(null, DddStateRules.nowOf(e.fields().get(7)));
    }
}
