package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.pub.rules.ModelCheck;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @stamp(STATE): the time a lifecycle entered a state, written by the command
 * that moves the row there - in Java and in Kotlin, on a command of one
 * lifecycle and on one of two, on an inherited field - and refused, with the
 * reason, where it cannot be written.
 */
public class StateStampTest {

    private static final String ENV = String.join(
            "\n",
            "elasticore:",
            "  enable: true",
            "  config:",
            "    domainName: stshop",
            "    framework: springboot",
            "    mode: jpa",
            "    j2ee: jakarta",
            "  namespace:",
            "    base: io.acme.stshop",
            "");

    private static final String ENUMS = String.join(
            "\n",
            "enumeration:",
            "  StStatus:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(3) @code",
            "    enum:",
            "      READY:     RDY",
            "      SENT:      SNT",
            "      CANCELLED: CAN",
            "  StFee:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(3) @code",
            "    enum:",
            "      DUE:       DUE",
            "      PAID:      PAD",
            "      CANCELLED: CAN",
            "",
            "");

    private static final String LIFECYCLES = String.join(
            "\n",
            "    states:",
            "      status:",
            "        initial: READY",
            "        send:    READY -> SENT",
            "        cancel:  READY, SENT -> CANCELLED",
            "      fee:",
            "        initial: DUE",
            "        pay:     DUE -> PAID",
            "        cancel:  PAID -> CANCELLED",
            "");

    private static final String MODEL = ENUMS
            + String.join(
                    "\n",
                    "entity:",
                    "  StShipment:",
                    "    meta: entity @root @expose(20) @event(Sent)",
                    "    fields:",
                    "      shipmentId:  string(36) @id @genid",
                    "      status:      StStatus",
                    "      fee:         StFee",
                    "      sentAt:      timestamp @stamp(SENT)",
                    "      paidOn:      date @stamp(PAID)",
                    "      cancelledAt: datetime @stamp(status.CANCELLED)",
                    "      feeEndedAt:  timestamp @stamp(fee.CANCELLED)",
                    "      endedAt:     timestamp @stamp(SENT, status.CANCELLED)",
                    "      twiceAt:     timestamp @stamp(SENT, status.SENT)",
                    LIFECYCLES,
                    "  StNote:",
                    "    meta: entity @root @editable @expose(20)",
                    "    fields:",
                    "      noteId: long @id @sequence",
                    "      status: StStatus",
                    "      sentAt: timestamp @stamp(SENT)",
                    "    states:",
                    "      status:",
                    "        initial: READY",
                    "        send:    READY -> SENT",
                    "",
                    "  StDoc:",
                    "    meta: entity @abstract",
                    "    fields:",
                    "      postedAt: timestamp @stamp(SENT)",
                    "",
                    "  StLetter:",
                    "    meta: entity @root @expose(20) @extend(StDoc)",
                    "    fields:",
                    "      letterId: string(36) @id @genid",
                    "      status:   StStatus",
                    "    states:",
                    "      status:",
                    "        initial: READY",
                    "        send:    READY -> SENT",
                    "");

    /** Each @stamp the generator cannot write, on a root whose lifecycle is written. */
    private static final String BROKEN = ENUMS
            + String.join(
                    "\n",
                    "entity:",
                    "  StBroken:",
                    "    meta: entity @root @expose(20)",
                    "    fields:",
                    "      brokenId: string(36) @id @genid",
                    "      status:   StStatus",
                    "      fee:      StFee",
                    "      either:   timestamp @stamp(CANCELLED)",
                    "      readyAt:  timestamp @stamp(READY)",
                    "      typo:     timestamp @stamp(SENTT)",
                    "      note:     string(10) @stamp(SENT)",
                    LIFECYCLES,
                    "");

    private static File project;
    private static File kotlin;
    private static File broken;
    private static String log = "";
    private static String brokenLog = "";
    private static int brokenStatus;

    @BeforeAll
    static void generate() throws Exception {
        project = write("elcore-stshop", MODEL);
        kotlin = write("elcore-stshop-kt", MODEL);
        broken = write("elcore-stshop-broken", BROKEN);
        log = captured(project, "java", false);
        run(project, "java", true);
        run(kotlin, "kotlin", false);
        ModelCheck.reset();
        brokenLog = captured(broken, "java", false);
        ModelCheck.reset();
        layered = write("elcore-stshop-layered", MODEL);
        architecture = "layered";
        try {
            layeredLog = captured(layered, "java", false);
        } finally {
            architecture = "hexagonal";
        }
    }

    private static File layered;
    private static String layeredLog = "";
    private static String architecture = "hexagonal";

    private static String captured(File dir, String language, boolean typescript) throws Exception {
        java.io.PrintStream out = System.out;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(bytes, true, "UTF-8"));
        int status;
        try {
            status = run(dir, language, typescript);
        } finally {
            System.setOut(out);
        }
        String text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(text);
        if (dir == broken) brokenStatus = status;
        return text;
    }

    private static File write(String prefix, String model) throws Exception {
        File dir = Files.createTempDirectory(prefix).toFile();
        File blueprint = new File(dir, "src/main/resources/blueprint/stshop");
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(new File(blueprint, "env.yml").toPath(), ENV.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static int run(File dir, String language, boolean typescript) {
        String[] keys = {
            "elasticore.base.path",
            "elasticore.architecture",
            "elasticore.language",
            "elasticore.rules.force",
            "elasticore.rules.ts"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            System.setProperty("elasticore.base.path", dir.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.rules.force", "true");
            if (typescript) System.setProperty("elasticore.rules.ts", "true");
            return RulesGenRunner.run(new String[0]);
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], saved[i]);
            }
        }
    }

    @AfterAll
    static void clean() throws Exception {
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        ModelCheck.reset();
        for (File dir : new File[] {project, kotlin, broken, layered}) {
            if (dir == null || !dir.exists()) continue;
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
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
    }

    private static String read(File dir, String path) throws Exception {
        File f = new File(dir, path);
        assertTrue(f.exists(), "not generated: " + path);
        // Indentation is the printer's; the lines are what is checked.
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)
                .replace("\r", "")
                .replaceAll("(?m)^[ \t]+", "");
    }

    private static String java(String path) throws Exception {
        return read(project, "src/main/java/io/acme/stshop/" + path);
    }

    /** The command of one lifecycle stamps after the move, before the event; a state named twice is one line. */
    @Test
    public void theCommandThatEntersTheStateStampsIt() throws Exception {
        String base = java("domain/model/StShipmentBase.java");
        assertTrue(
                base.contains("moveStatus(\"send\", StStatus.SENT, StStatus.READY);\n"
                        + "this.sentAt = java.time.Instant.now();\n"
                        + "this.endedAt = java.time.Instant.now();\n"
                        + "this.twiceAt = java.time.Instant.now();\n"
                        + "raiseSent();"),
                base);
        assertTrue(
                base.contains(
                        "moveFee(\"pay\", StFee.PAID, StFee.DUE);\n" + "this.paidOn = java.time.LocalDate.now();\n"),
                base);
    }

    /** A command of two lifecycles stamps each field after both moved - and only when both may. */
    @Test
    public void aCommandOfTwoLifecyclesStampsEach() throws Exception {
        String base = java("domain/model/StShipmentBase.java");
        int checks = base.indexOf("requireFee(\"cancel\", StFee.PAID);");
        int status =
                base.indexOf("this.status = StStatus.CANCELLED;\nthis.cancelledAt = java.time.LocalDateTime.now();\n"
                        + "this.endedAt = java.time.Instant.now();");
        int fee = base.indexOf("this.fee = StFee.CANCELLED;\nthis.feeEndedAt = java.time.Instant.now();");
        assertTrue(checks > 0 && status > checks && fee > status, base);
    }

    /** An inherited field is stamped by the split root that extends it. */
    @Test
    public void anInheritedFieldIsStampedByTheRoot() throws Exception {
        String base = java("domain/model/StLetterBase.java");
        assertTrue(
                base.contains("moveStatus(\"send\", StStatus.SENT, StStatus.READY);\n"
                        + "this.postedAt = java.time.Instant.now();"),
                base);
        assertTrue(
                log.contains(
                        "StDoc.postedAt @stamp: StDoc has no states:; it is stamped only by a root with a behaviour class"
                                + " that extends StDoc"),
                log);
    }

    /** The stamp is the entity's: no request sends it, and the class says it. */
    @Test
    public void aStampIsReadOnlyAndSaid() throws Exception {
        String base = java("domain/model/StShipmentBase.java");
        assertTrue(base.contains("stamp:  sentAt - the time it last entered SENT"), base);
        assertTrue(base.contains("stamp:  feeEndedAt - the time it last entered CANCELLED"), base);
        String dto = java("application/dto/StShipmentDTO.java");
        assertTrue(
                dto.matches(
                        "(?s).*@JsonProperty\\(access=JsonProperty.Access.READ_ONLY\\)\\s+private (java.time.)?Instant sentAt;.*"),
                dto);
    }

    /** A root without a behaviour class is said, and its field stays one a request writes. */
    @Test
    public void anUnsplitRootIsSaidAndStaysWritable() throws Exception {
        assertTrue(log.contains("StNote.sentAt @stamp: written only for a root with a behaviour class"), log);
        String note = java("application/dto/StNoteDTO.java");
        assertFalse(note.contains("READ_ONLY"), note);
    }

    /** What cannot be written on a split root fails the model check, each with its reason. */
    @Test
    public void whatCannotBeStampedFailsTheBuild() throws Exception {
        assertEquals(1, brokenStatus, brokenLog);
        assertTrue(
                brokenLog.contains(
                        "StBroken.either @stamp names CANCELLED, which status and fee both have - write @stamp(status.CANCELLED)"),
                brokenLog);
        assertTrue(
                brokenLog.contains(
                        "StBroken.readyAt @stamp names READY, which no move of states.status enters (it is where a row starts"),
                brokenLog);
        assertTrue(
                brokenLog.contains("StBroken.typo @stamp names SENTT, which no lifecycle of StBroken has"), brokenLog);
        assertTrue(brokenLog.contains("StBroken.note @stamp is String, not a time"), brokenLog);
        assertTrue(
                brokenLog.contains("nothing else writes the field (it is the lifecycle's), so fix the @stamp"),
                brokenLog);
    }

    /** The layered bundle writes no lifecycle: its warning names the stamp too. */
    @Test
    public void theLayeredBundleSaysItIsNotEnforced() throws Exception {
        assertTrue(
                layeredLog.contains(
                        "StShipment declares states: and @stamp (ddd-state), which the selected rules do not enforce"),
                layeredLog);
        assertTrue(
                layeredLog.contains("StDoc declares @stamp (ddd-state), which the selected rules do not enforce"),
                layeredLog);
    }

    @Test
    public void theClientDoesNotSendIt() throws Exception {
        String ts = read(project, "src/main/ts/StShipmentDTO.ts");
        assertTrue(ts.matches("(?s).*\\bsentAt: \\{[^}]*readOnly: true.*"), ts);
        // Not split, not stamped: the client sends it like any other field.
        String note = read(project, "src/main/ts/StNoteDTO.ts");
        assertTrue(
                note.matches("(?s).*\\bsentAt: \\{[^}]*\\}.*")
                        && !note.matches("(?s).*\\bsentAt: \\{[^}]*readOnly: true.*"),
                note);
    }

    @Test
    public void kotlinStampsTheSame() throws Exception {
        String base = read(kotlin, "src/main/kotlin/io/acme/stshop/domain/model/StShipmentBase.kt");
        assertTrue(
                base.contains("moveStatus(\"send\", StStatus.SENT, StStatus.READY)\n"
                        + "this.sentAt = java.time.Instant.now()\n"),
                base);
        assertTrue(base.contains("this.fee = StFee.CANCELLED\nthis.feeEndedAt = java.time.Instant.now()"), base);
    }
}
