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
 * What the pet store built by agents with the agent kit wrote by hand, and
 * the generator now writes - in Java and in Kotlin, on the hexagonal bundle:
 *
 * - signing in, out and "who am I" for the entity with @login and @password;
 * - a refused lifecycle step answered 409 (it conflicts with the row's state),
 *   a row the request names that is not there 404;
 * - an order without its lines refused as a bad request (400), like an empty list;
 * - a reference by identity to a row that does not exist found before the save.
 */
public class SignInAndReferencesTest {

    private static final String MODEL = String.join("\n",
            "enumeration:",
            "  SrRole:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(1) @code",
            "    enum:",
            "      USER:  U",
            "      ADMIN: A",
            "  SrStatus:",
            "    meta: enum @db(code) @json(code)",
            "    fields:",
            "      code: string(3) @code",
            "    enum:",
            "      OPEN: OPN",
            "      DONE: DON",
            "",
            "entity:",
            "  SrMember:",
            "    meta: entity @root @editable @expose(20) @owner(memberId) @access(read=owner, create=public, update=owner)",
            "    fields:",
            "      memberId: long @id @sequence",
            "      email:    string(100)! @login",
            "      secret:   string(255)! @password",
            "      role:     SrRole @default(SrRole.USER) @access(admin)",
            "",
            "  SrItem:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      itemId: long @id @sequence",
            "      name:   string(30)!",
            "",
            "  SrOrder:",
            "    meta: entity @root @expose(20) @owner(memberId) @access(read=owner, create=user, close=owner)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      member:  SrMember",
            "      status:  SrStatus",
            "      lines:   List<SrLine> @OneToMany(mappedBy=\"order\") @dtype(List<SrLineDTO>) @minsize(1, 'an order needs a line')",
            "    states:",
            "      status:",
            "        initial: OPEN",
            "        close:   OPEN -> DONE",
            "",
            "  SrLine:",
            "    meta: entity",
            "    fields:",
            "      lineId:   long @id @sequence",
            "      order:    SrOrder @ManyToOne",
            "      item:     SrItem @ManyToOne",
            "      quantity: int",
            "",
            "dto:",
            "  SrLineDTO:",
            "    meta: dto @template(SrLine)",
            "");

    /** A second domain that signs in its own way (Spring Security, say). */
    private static final String MODEL_OFF = String.join("\n",
            "entity:",
            "  SoUser:",
            "    meta: entity @root @editable @expose(20) @access(read=user, create=public)",
            "    fields:",
            "      userId:   long @id @sequence",
            "      login:    string(20)! @login",
            "      password: string(255)! @password",
            "");

    private static File java;
    private static File kotlin;

    @BeforeAll
    static void generate() throws Exception {
        java = Files.createTempDirectory("elcore-signin-java").toFile();
        write(java, "srshop", env("srshop", "io.acme.srshop", null), MODEL);
        write(java, "soshop", env("soshop", "io.acme.soshop", "signin: \"false\""), MODEL_OFF);
        run(java, "java", false);
        run(java, "java", true);

        kotlin = Files.createTempDirectory("elcore-signin-kt").toFile();
        write(kotlin, "srshop", env("srshop", "io.acme.srshop", null), MODEL);
        run(kotlin, "kotlin", false);
    }

    private static String env(String domain, String base, String extra) {
        return String.join("\n",
                "elasticore:",
                "  enable: true",
                "  config:",
                "    domainName: " + domain,
                "    framework: springboot",
                "    mode: jpa",
                "    j2ee: jakarta",
                extra == null ? "" : "    " + extra,
                "  namespace:",
                "    base: " + base,
                "");
    }

    private static void write(File project, String domain, String env, String model) throws Exception {
        File blueprint = new File(project, "src/main/resources/blueprint/" + domain);
        assertTrue(new File(blueprint, "model").mkdirs());
        Files.write(new File(blueprint, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(blueprint, "model/model.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));
    }

    private static void run(File project, String language, boolean typescript) {
        String[] keys = {"elasticore.base.path", "elasticore.architecture", "elasticore.language",
                "elasticore.rules.force", "elasticore.rules.ts"};
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = System.getProperty(keys[i]);
        try {
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", "hexagonal");
            System.setProperty("elasticore.language", language);
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

    private static String java(String path) throws Exception {
        return read(java, "src/main/java/io/acme/srshop/" + path);
    }

    private static String kt(String path) throws Exception {
        return read(kotlin, "src/main/kotlin/io/acme/srshop/" + path);
    }

    private static String read(File project, String path) throws Exception {
        File f = new File(project, path);
        assertTrue(f.exists(), "not generated: " + path);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    // ---- signing in ---------------------------------------------------------------

    @Test
    public void theEntityWithALoginAndAPasswordSignsIn() throws Exception {
        String control = java("adapter/in/web/SrMemberController.java");
        assertTrue(control.contains("public record SrMemberSignIn(String email, String secret)"), control);
        assertTrue(control.contains("@PostMapping(\"/signin\")"), control);
        assertTrue(control.contains("srMemberService.authenticate(credentials.email(), credentials.secret())"), control);
        assertTrue(control.contains("new io.elasticore.springboot3.security.NotSignedInException(\"Wrong email or password\")"), control);
        // The session holds the key and the role, which @access reads.
        assertTrue(control.contains("io.elasticore.springboot3.security.SessionCurrentUser.signIn(request, signedIn.getMemberId(), "
                + "signedIn.getRole() == null ? null : signedIn.getRole().name());"), control);
        assertTrue(control.contains("@PostMapping(\"/signout\")"), control);
        assertTrue(control.contains("@GetMapping(\"/me\")"), control);
        assertTrue(control.contains("srMemberService.me().orElseThrow("), control);

        assertTrue(java("application/SrMemberCoreService.java").contains("public Optional<SrMemberDTO> me() {"));
        assertTrue(java("application/SrMemberCoreService.java")
                .contains("repository.findById(io.elasticore.springboot3.security.AccessPolicy.as(id, Long.class))"));
        assertTrue(java("application/port/in/SrMemberUseCase.java").contains("Optional<SrMemberDTO> me();"));
    }

    @Test
    public void onlyThatEntitySignsIn() throws Exception {
        assertFalse(java("adapter/in/web/SrItemController.java").contains("/signin"));
        assertFalse(java("adapter/in/web/SrOrderController.java").contains("/signin"));
    }

    @Test
    public void anApplicationThatSignsInItsOwnWayTurnsItOff() throws Exception {
        String control = read(java, "src/main/java/io/acme/soshop/adapter/in/web/SoUserController.java");
        assertFalse(control.contains("/signin"), control);
        assertTrue(read(java, "src/main/java/io/acme/soshop/application/SoUserCoreService.java")
                .contains("authenticate(String login, String password)"), "authenticate stays");
    }

    @Test
    public void theBrowserClientSignsInToo() throws Exception {
        String api = read(java, "src/main/ts/SrMemberApi.ts");
        assertTrue(api.contains("export async function signIn(email: string, secret: string): Promise<SrMemberDTO>"), api);
        assertTrue(api.contains("request<SrMemberDTO>('POST', BASE + '/signin', { email, secret })"), api);
        assertTrue(api.contains("export async function signOut(): Promise<void>"), api);
        assertTrue(api.contains("export async function me(): Promise<SrMemberDTO | null>"), api);
        assertTrue(api.contains("ApiError"), "imported for the 401 of a visitor");
        assertFalse(read(java, "src/main/ts/SrItemApi.ts").contains("signIn"));
    }

    @Test
    public void kotlinSignsInTheSameWay() throws Exception {
        String control = kt("adapter/in/web/SrMemberController.kt");
        assertTrue(control.contains("class SrMemberSignIn(var email: String? = null, var secret: String? = null)"), control);
        assertTrue(control.contains("srMemberService.authenticate(credentials.email, credentials.secret)"), control);
        assertTrue(control.contains("io.elasticore.springboot3.security.SessionCurrentUser.signIn(request, signedIn.memberId, signedIn.role?.name)"), control);
        assertTrue(control.contains("fun me(): SrMemberDTO ="), control);
        assertTrue(kt("application/SrMemberCoreService.kt").contains("open fun me(): Optional<SrMemberDTO> {"));
        assertTrue(kt("application/port/in/SrMemberUseCase.kt").contains("fun me(): Optional<SrMemberDTO>"));
    }

    // ---- what a refusal answers ------------------------------------------------------

    @Test
    public void aStepTheStateDoesNotAllowIsAConflictAndAMissingRowIsNotFound() throws Exception {
        String errors = java("adapter/in/web/SrshopApiErrors.java");
        assertTrue(errors.contains("code.startsWith(\"state.\")"), errors);
        assertTrue(errors.contains("problem(HttpStatus.CONFLICT, \"state\", \"Not allowed in this state\", detail)"), errors);
        assertTrue(errors.contains("problem(HttpStatus.NOT_FOUND, \"not-found\", \"Not found\", detail)"), errors);
        assertTrue(errors.contains("problem(HttpStatus.UNPROCESSABLE_ENTITY, \"refused\", \"Refused by the domain\", detail)"), errors);

        String kt = kt("adapter/in/web/SrshopApiErrors.kt");
        assertTrue(kt.contains("problem(HttpStatus.CONFLICT, \"state\", \"Not allowed in this state\", detail)"), kt);

        String messages = read(java, "src/main/resources/i18n/srshop-messages.properties");
        assertTrue(messages.contains("error.state.title=Not allowed in this state"), messages);
        assertTrue(messages.contains("reference.SrLine.item=SrItem {0} does not exist"), messages);
        assertTrue(messages.contains("reference.SrOrder.member=SrMember {0} does not exist"), messages);
    }

    @Test
    public void anOrderWithoutItsLinesIsABadRequest() throws Exception {
        String dto = java("application/dto/SrOrderDTO.java");
        assertTrue(dto.contains("@NotNull(message=\"an order needs a line\")"), dto);
        assertTrue(dto.contains("@Size(min=1, message=\"an order needs a line\")"), dto);
    }

    // ---- references by identity ------------------------------------------------------

    @Test
    public void theRowsAReferenceNamesMustExist() throws Exception {
        String order = java("domain/model/SrOrderBase.java");
        assertTrue(order.contains("public void requireReferences(io.elasticore.springboot3.domain.Lookup lookup) {"), order);
        assertTrue(order.contains("if (this.memberId != null && lookup.find(SrMember.class, this.memberId) == null)"), order);
        assertTrue(order.contains("\"reference.SrOrder.member\", \"SrMember \" + this.memberId + \" does not exist\""), order);
        assertTrue(order.contains("for (SrLine member : this.lines) member.requireReferences(lookup);"), order);

        String line = java("domain/model/SrLine.java");
        assertTrue(line.contains("if (this.itemId != null && lookup.find(SrItem.class, this.itemId) == null)"), line);
        assertFalse(java("domain/model/SrItem.java").contains("requireReferences"), "it names no other row");

        String service = java("application/SrOrderCoreService.java");
        int check = service.indexOf("entity.requireReferences(entityManager::find);");
        assertTrue(check > 0, service);
        assertTrue(check < service.indexOf("beforeSave(entity, dto);"), "before the application's own beforeSave");
        assertFalse(java("application/SrItemCoreService.java").contains("requireReferences"));

        String ktOrder = kt("domain/model/SrOrderBase.kt");
        assertTrue(ktOrder.contains("fun requireReferences(lookup: io.elasticore.springboot3.domain.Lookup) {"), ktOrder);
        assertTrue(ktOrder.contains("this.lines?.forEach { it.requireReferences(lookup) }"), ktOrder);
        assertTrue(kt("application/SrOrderCoreService.kt").contains("entity.requireReferences { type, id ->"));
    }

    @AfterAll
    static void clean() throws Exception {
        java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
        SpecFactory.valueObjectsInDto(false);
        SpecFactory.rootsSplitByDefault(false);
        SpecFactory.commandOnlyRoots(false);
        for (File dir : new File[]{java, kotlin}) {
            if (dir == null || !dir.exists()) continue;
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, java.io.IOException e) throws java.io.IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
