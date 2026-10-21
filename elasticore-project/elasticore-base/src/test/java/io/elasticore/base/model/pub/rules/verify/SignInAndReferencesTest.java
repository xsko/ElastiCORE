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
            "  SrAddress:",
            "    meta: entity @embeddable",
            "    fields:",
            "      street: string(50)!",
            "      city:   string(50)",
            "",
            "  SrCard:",
            "    meta: entity @embeddable",
            "    fields:",
            "      number: string(30)! @writeonly",
            "",
            "  SrMember:",
            "    meta: entity @root @editable @expose(20) @signin @owner(memberId) @access(read=owner, create=public, update=owner)",
            "    fields:",
            "      memberId: long @id @sequence",
            "      email:    string(100)! @login",
            "      secret:   string(255)! @password",
            "      role:     SrRole @default(SrRole.USER) @access(admin)",
            "      home:     SrAddress! @Embedded",
            "      joinedAt: timestamp @readonly @default(now)",
            "      joinedOn: date @readonly @default(now)",
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
            "      shipTo:  SrAddress @Embedded",
            "      card:    SrCard! @Embedded",
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
            "      options:  List<SrLineOption> @OneToMany(mappedBy=\"line\") @cascade",
            "",
            // A member of a member that names another root: three levels, the review's model.
            "  SrLineOption:",
            "    meta: entity",
            "    fields:",
            "      optionId: long @id @sequence",
            "      line:     SrLine @ManyToOne",
            "      extra:    SrExtra @ManyToOne",
            "",
            "  SrExtra:",
            "    meta: entity @root @editable @expose(20)",
            "    fields:",
            "      extraId: long @id @sequence",
            "      name:    string(20)",
            "",
            "dto:",
            "  SrLineDTO:",
            "    meta: dto @template(SrLine)",
            "");

    /** A second domain that signs in its own way (Spring Security, say): @login and @password, no @signin. */
    private static final String MODEL_OFF = String.join("\n",
            "entity:",
            "  SoUser:",
            "    meta: entity @root @editable @expose(20) @access(read=user, create=public)",
            "    fields:",
            "      userId:   long @id @sequence",
            "      login:    string(20)! @login",
            "      password: string(255)! @password",
            "");

    /** A role a member writes on their own record: the session must not get it. */
    private static final String MODEL_UNSAFE_ROLE = String.join("\n",
            "entity:",
            "  SuUser:",
            "    meta: entity @root @editable @expose(20) @signin @owner(userId) @access(read=owner, create=public, update=owner)",
            "    fields:",
            "      userId:   long @id @sequence",
            "      login:    string(20)! @login",
            "      password: string(255)! @password",
            "      role:     string(10)",
            "");

    /** Two entities that ask to sign in (one session for both), and one whose key the session cannot hold. */
    private static final String MODEL_TWO_A = String.join("\n",
            "entity:",
            "  StBuyer:",
            "    meta: entity @root @editable @expose(20) @signin @owner(buyerId) @access(read=owner, create=public, update=owner)",
            "    fields:",
            "      buyerId:  long @id @sequence",
            "      login:    string(20)! @login",
            "      password: string(255)! @password",
            "");
    private static final String MODEL_TWO_B = String.join("\n",
            "entity:",
            "  StSeller:",
            "    meta: entity @root @editable @expose(20) @signin @owner(sellerId) @access(read=owner, create=public, update=owner)",
            "    fields:",
            "      sellerId: long @id @sequence",
            "      login:    string(20)! @login",
            "      password: string(255)! @password",
            "  StDesk:",
            "    meta: entity @root @editable @expose(20) @signin",
            "    fields:",
            "      site:     string(5) @id",
            "      seat:     int @id",
            "      login:    string(20)! @login",
            "      password: string(255)! @password",
            "");

    /**
     * SEC1 of the review: @signin where someone other than the row's own user
     * may change its password - no @access (update is public), update=user,
     * owner through a field that is not the key - and one that is not exposed.
     */
    private static final String MODEL_REFUSED = String.join("\n",
            "entity:",
            "  SxOpen:",
            "    meta: entity @root @editable @expose(20) @signin",
            "    fields:",
            "      openId:   long @id @sequence",
            "      login:    string(20)! @login",
            "      password: string(255)! @password",
            "      role:     string(10) @access(admin)",
            "  SxUser:",
            "    meta: entity @root @editable @expose(20) @signin @access(read=public, create=public, update=user)",
            "    fields:",
            "      userId:   long @id @sequence",
            "      login:    string(20)! @login",
            "      password: string(255)! @password",
            "  SxSponsored:",
            "    meta: entity @root @editable @expose(20) @signin @owner(sponsorId) @access(read=owner, create=public, update=owner)",
            "    fields:",
            "      sponsoredId: long @id @sequence",
            "      sponsorId:   long",
            "      login:       string(20)! @login",
            "      password:    string(255)! @password",
            "  SxHidden:",
            "    meta: entity @root @editable @signin @owner(hiddenId) @access(read=owner, create=public, update=owner)",
            "    fields:",
            "      hiddenId: long @id @sequence",
            "      login:    string(20)! @login",
            "      password: string(255)! @password",
            "  SxStaff:",
            "    meta: entity @root @editable @expose(20) @access(read=user, create=admin, update=admin)",
            "    fields:",
            "      staffId:  long @id @sequence",
            "      login:    string(20)! @login @unique",
            "      password: string(255)! @password",
            "");

    /** SIGN1: two modules, two applications - each may sign in through its own entity. */
    private static final String MODEL_ADMIN_APP = String.join("\n",
            "entity:",
            "  SmStaff:",
            "    meta: entity @root @editable @expose(20) @signin @owner(staffId) @access(read=owner, create=admin, update=owner)",
            "    fields:",
            "      staffId:  long @id @sequence",
            "      login:    string(20)! @login",
            "      password: string(255)! @password",
            "");
    private static final String MODEL_SHOP_APP = String.join("\n",
            "entity:",
            "  SmClient:",
            "    meta: entity @root @editable @expose(20) @signin @owner(handle) @access(read=owner, create=public, update=owner)",
            "    fields:",
            "      handle:   string(20) @id @login",
            "      password: string(255)! @password",
            "");

    /** SEC2: a sign-in left in a controller the developer owns, after the DSL stopped asking for it. */
    private static final String MODEL_OWNED = String.join("\n",
            "entity:",
            "  SwUser:",
            "    meta: entity @root @editable @expose(20) @signin @owner(userId) @access(read=owner, create=public, update=owner)",
            "    fields:",
            "      userId:   long @id @sequence",
            "      login:    string(20)! @login @unique",
            "      password: string(255)! @password",
            "      role:     string(10) @access(admin)",
            "");

    private static File jproject;
    private static File kproject;
    private static File unsafe;
    private static File two;
    private static File refused;
    private static File modules;
    private static File owned;
    private static String jLog;
    private static String unsafeLog;
    private static String twoLog;
    private static String refusedLog;
    private static String modulesLog;
    private static String ownedRoleLog;
    private static String ownedGoneLog;

    @BeforeAll
    static void generate() throws Exception {
        jproject = Files.createTempDirectory("elcore-signin-java").toFile();
        write(jproject, "srshop", env("srshop", "io.acme.srshop", null), MODEL);
        write(jproject, "soshop", env("soshop", "io.acme.soshop", null), MODEL_OFF);
        jLog = captured(jproject);
        run(jproject, "java", true);

        kproject = Files.createTempDirectory("elcore-signin-kt").toFile();
        write(kproject, "srshop", env("srshop", "io.acme.srshop", null), MODEL);
        run(kproject, "kotlin", false);

        unsafe = Files.createTempDirectory("elcore-signin-role").toFile();
        write(unsafe, "sushop", env("sushop", "io.acme.sushop", null), MODEL_UNSAFE_ROLE);
        unsafeLog = captured(unsafe);

        two = Files.createTempDirectory("elcore-signin-two").toFile();
        write(two, "sta", env("sta", "io.acme.sta", null), MODEL_TWO_A);
        write(two, "stb", env("stb", "io.acme.stb", null), MODEL_TWO_B);
        twoLog = captured(two);

        refused = Files.createTempDirectory("elcore-signin-refused").toFile();
        write(refused, "sxshop", env("sxshop", "io.acme.sxshop", null), MODEL_REFUSED);
        refusedLog = captured(refused);

        modules = Files.createTempDirectory("elcore-signin-modules").toFile();
        write(new File(modules, "admin-api"), "smadmin", env("smadmin", "io.acme.smadmin", null), MODEL_ADMIN_APP);
        write(new File(modules, "shop-api"), "smshop", env("smshop", "io.acme.smshop", null), MODEL_SHOP_APP);
        modulesLog = captured(modules, new File(modules, "admin-api").getAbsolutePath() + ","
                + new File(modules, "shop-api").getAbsolutePath());

        owned = Files.createTempDirectory("elcore-signin-owned").toFile();
        write(owned, "swshop", env("swshop", "io.acme.swshop", null), MODEL_OWNED);
        run(owned, "java", false);
        // The developer edits the generated controller: it is theirs from now on.
        File control = new File(owned, "src/main/java/io/acme/swshop/adapter/in/web/SwUserController.java");
        String text = new String(Files.readAllBytes(control.toPath()), StandardCharsets.UTF_8);
        int end = text.lastIndexOf('}');
        Files.write(control.toPath(), (text.substring(0, end) + "    // the team's own endpoints go here\n}\n")
                .getBytes(StandardCharsets.UTF_8));
        File model = new File(owned, "src/main/resources/blueprint/swshop/model/model.yml");
        // Then the role stops being the administrator's alone ...
        Files.write(model.toPath(), MODEL_OWNED.replace("string(10) @access(admin)", "string(10)")
                .getBytes(StandardCharsets.UTF_8));
        ownedRoleLog = captured(owned);
        // ... and @signin goes.
        Files.write(model.toPath(), MODEL_OWNED.replace(" @signin", "").getBytes(StandardCharsets.UTF_8));
        ownedGoneLog = captured(owned);
    }

    /** A Java generation, with what it printed. */
    private static String captured(File project) throws Exception {
        return captured(project, null);
    }

    private static String captured(File project, String modulePaths) throws Exception {
        java.io.PrintStream out = System.out;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(bytes, true, "UTF-8"));
        String savedModules = System.getProperty("elasticore.modules");
        try {
            if (modulePaths != null) System.setProperty("elasticore.modules", modulePaths);
            run(project, "java", false);
        } finally {
            if (savedModules == null) System.clearProperty("elasticore.modules");
            else System.setProperty("elasticore.modules", savedModules);
            System.setOut(out);
        }
        String log = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        out.print(log);
        return log;
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
        return read(jproject, "src/main/java/io/acme/srshop/" + path);
    }

    private static String kt(String path) throws Exception {
        return read(kproject, "src/main/kotlin/io/acme/srshop/" + path);
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

    /** DOC1/H3 of the review: without @signin the service and the use case are what they were - no me(). */
    @Test
    public void withoutSigninThereAreNoEndpoints() throws Exception {
        String control = read(jproject, "src/main/java/io/acme/soshop/adapter/in/web/SoUserController.java");
        assertFalse(control.contains("/signin"), control);
        String core = read(jproject, "src/main/java/io/acme/soshop/application/SoUserCoreService.java");
        assertTrue(core.contains("authenticate(String login, String password)"), "authenticate stays");
        assertFalse(core.contains(" me()"), core);
        String useCase = read(jproject, "src/main/java/io/acme/soshop/application/port/in/SoUserUseCase.java");
        assertTrue(useCase.contains("authenticate("), useCase);
        assertFalse(useCase.contains(" me()"), useCase);
        assertFalse(read(jproject, "src/main/ts/SoUserApi.ts").contains("signIn"));
    }

    /** SEC3: the login a @signin entity signs in with is unique; a plain @login that is not is said. */
    @Test
    public void theLoginOfTheEntityThatSignsInIsUnique() throws Exception {
        String member = java("domain/model/SrMember.java");
        assertTrue(member.contains("@Column(name = \"email\", nullable = false, length = 100, unique = true)"), member);
        String kt = kt("domain/model/SrMember.kt");
        assertTrue(kt.contains("@Column(name = \"email\", nullable = false, length = 100, unique = true)"), kt);
        // Already unique through @unique: one unique, not two.
        String owned = read(SignInAndReferencesTest.owned, "src/main/java/io/acme/swshop/domain/model/SwUser.java");
        assertTrue(owned.contains("unique = true") && owned.indexOf("unique") == owned.lastIndexOf("unique"), owned);
        // Not @signin: left as declared, and the build says what a shared login does.
        assertFalse(read(jproject, "src/main/java/io/acme/soshop/domain/model/SoUser.java").contains("unique"));
        assertTrue(jLog.contains("SoUser: login is @login but not unique"), jLog);
        assertFalse(jLog.contains("SrMember: email is @login but not unique"), jLog);
    }

    /** SEC1: sign-in only where nobody but the row's own user and the administrator may change the password. */
    @Test
    public void noSignInWhereSomeoneElseMayChangeThePassword() throws Exception {
        for (String entity : new String[]{"SxOpen", "SxUser", "SxSponsored", "SxHidden"}) {
            File control = new File(refused, "src/main/java/io/acme/sxshop/adapter/in/web/" + entity + "Controller.java");
            assertFalse(control.isFile() && new String(Files.readAllBytes(control.toPath()), StandardCharsets.UTF_8)
                    .contains("/signin"), entity);
            File core = new File(refused, "src/main/java/io/acme/sxshop/application/" + entity + "CoreService.java");
            if (!core.isFile()) continue;   // not exposed: no service either
            String text = new String(Files.readAllBytes(core.toPath()), StandardCharsets.UTF_8);
            assertTrue(text.contains("authenticate("), entity + ": " + text);
            assertFalse(text.contains(" me()"), entity + ": " + text);
        }
        assertTrue(refusedLog.contains("SxOpen: @signin generates no sign-in: update is public (it has no @access), so"
                + " anyone may change another row's password and login"), refusedLog);
        assertTrue(refusedLog.contains("Let only the row's own user change it: @owner(openId)"
                + " @access(read=owner, create=public, update=owner)"), refusedLog);
        assertTrue(refusedLog.contains("SxUser: @signin generates no sign-in: update is user, so anyone signed in may"
                + " change another row's password and login"), refusedLog);
        assertTrue(refusedLog.contains("SxSponsored: @signin generates no sign-in: update is owner, but"
                + " @owner(sponsorId) is not the key sponsoredId"), refusedLog);
        // DOC2/L7: no @expose, no controller - said all the same.
        assertTrue(refusedLog.contains("SxHidden: @signin generates no sign-in: it is not @expose"), refusedLog);
    }

    /** SEC4: another @login + @password entity beside the one that signs in shares its session key. */
    @Test
    public void anotherEntityWithALoginIsToldTheSessionKeyIsShared() {
        assertTrue(jLog.contains("SrMember: the session the generated sign-in starts holds only a bare SrMember key, and"
                + " SoUser has @login and @password too."), jLog);
        // Only where something signs in: beside refused @signin entities SxStaff is no one's concern.
        assertFalse(refusedLog.contains("holds only a bare"), refusedLog);
    }

    /** SIGN1: @signin is counted per application - two modules of one run are two. */
    @Test
    public void eachApplicationOfAMultiModuleRunSignsInThroughItsOwnEntity() throws Exception {
        assertTrue(read(modules, "admin-api/src/main/java/io/acme/smadmin/adapter/in/web/SmStaffController.java")
                .contains("@PostMapping(\"/signin\")"), modulesLog);
        assertTrue(read(modules, "shop-api/src/main/java/io/acme/smshop/adapter/in/web/SmClientController.java")
                .contains("@PostMapping(\"/signin\")"), modulesLog);
        assertFalse(modulesLog.contains("more than one entity"), modulesLog);
        // L8: a key the request names can be 'me'.
        assertTrue(modulesLog.contains("SmClient: its key handle is a string the request gives: a row keyed 'me' is"
                + " answered by GET /me"), modulesLog);
        assertFalse(modulesLog.contains("SmStaff: its key"), modulesLog);
    }

    /** SEC2: a controller the developer owns that still signs in where the DSL no longer would. */
    @Test
    public void anOwnedControllerThatStillSignsInIsSaid() throws Exception {
        String control = read(owned, "src/main/java/io/acme/swshop/adapter/in/web/SwUserController.java");
        assertTrue(control.contains("the team's own endpoints go here"), "left alone");
        assertTrue(ownedRoleLog.contains("SwUserController.java is yours and still puts a role in the session"), ownedRoleLog);
        assertTrue(ownedGoneLog.contains("SwUserController.java is yours and still serves POST /signin, GET /me, which"
                + " the DSL does not generate now"), ownedGoneLog);
        assertTrue(RulesGenRunner.passesRole("SessionCurrentUser.signIn(request, u.getId(), u.getRole() == null ? null : u.getRole().name());"));
        assertFalse(RulesGenRunner.passesRole("SessionCurrentUser.signIn(request, f(a, b));"));
        assertTrue(RulesGenRunner.passesRole("SessionCurrentUser.signIn(request, signedIn.memberId, signedIn.role?.name)"));
    }

    /** H1 of the review: a role a member could write is not a session role. */
    @Test
    public void aRoleTheMemberCouldWriteIsNotPutInTheSession() throws Exception {
        String control = read(unsafe, "src/main/java/io/acme/sushop/adapter/in/web/SuUserController.java");
        assertTrue(control.contains("io.elasticore.springboot3.security.SessionCurrentUser.signIn(request, signedIn.getUserId());"), control);
        assertTrue(unsafeLog.contains("SuUser: role is not put in the session as a role"), unsafeLog);
    }

    /** H2: the session holds a bare key, so two entities that sign in get nothing - and are told. */
    @Test
    public void twoEntitiesThatSignInGetNoEndpoints() throws Exception {
        assertFalse(read(two, "src/main/java/io/acme/sta/adapter/in/web/StBuyerController.java").contains("/signin"));
        assertFalse(read(two, "src/main/java/io/acme/stb/adapter/in/web/StSellerController.java").contains("/signin"));
        assertTrue(twoLog.contains("StBuyer: @signin generates no sign-in: more than one entity of the application says @signin"), twoLog);
        assertFalse(read(two, "src/main/java/io/acme/sta/application/StBuyerCoreService.java").contains(" me()"));
        assertTrue(twoLog.contains("StDesk: @signin generates no sign-in: its key must be one long, int, string or UUID field"), twoLog);
    }

    @Test
    public void theBrowserClientSignsInToo() throws Exception {
        String api = read(jproject, "src/main/ts/SrMemberApi.ts");
        assertTrue(api.contains("export async function signIn(email: string, secret: string): Promise<SrMemberDTO>"), api);
        assertTrue(api.contains("request<SrMemberDTO>('POST', BASE + '/signin', { email, secret })"), api);
        assertTrue(api.contains("export async function signOut(): Promise<void>"), api);
        assertTrue(api.contains("export async function me(): Promise<SrMemberDTO | null>"), api);
        assertTrue(api.contains("ApiError"), "imported for the 401 of a visitor");
        assertFalse(read(jproject, "src/main/ts/SrItemApi.ts").contains("signIn"));
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
        assertTrue(kt.contains("@Import(RequestShapeErrors::class)"), kt);
        assertTrue(kt.contains("messages.violation(code + \".\" + field, it, it.message)"), kt);

        // A row a GET or DELETE names that is not there: the problem body's 404, in Kotlin too.
        String ktControl = kt("adapter/in/web/SrOrderController.kt");
        assertTrue(ktControl.contains("orElseThrow { NotFoundException(\"SrOrder not found\") }"), ktControl);
        assertTrue(ktControl.contains("throw NotFoundException(\"SrOrder not found\")"), ktControl);

        String messages = read(jproject, "src/main/resources/i18n/srshop-messages.properties");
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

    // ---- a time the server sets when the row is made ---------------------------------

    /** @readonly @default(now): the entity's initializer, no DTO default - timestamp (Instant) too. */
    @Test
    public void aTimeSetWhenTheRowIsMade() throws Exception {
        String entity = java("domain/model/SrMember.java");
        assertTrue(entity.contains("joinedAt = java.time.Instant.now();"), entity);
        assertTrue(entity.contains("joinedOn = LocalDate.now();"), entity);
        String dto = java("application/dto/SrMemberDTO.java");
        assertFalse(dto.contains("Instant.now()") || dto.contains("LocalDate.now()"), dto);
        String kt = kt("domain/model/SrMember.kt");
        assertTrue(kt.contains("java.time.Instant.now()"), kt);
    }

    // ---- value types ---------------------------------------------------------------

    /**
     * An order's delivery address is optional, a member's home address is not: the
     * value's columns are nullable, since the order's table holds them too and an
     * order without one was refused NOT NULL. A card always required stays NOT NULL.
     */
    @Test
    public void aValueEmbeddedAsOptionalHasNullableColumns() throws Exception {
        String address = java("domain/model/SrAddress.java");
        assertFalse(address.contains("nullable = false") || address.contains("nullable=false"), address);
        String card = java("domain/model/SrCard.java");
        assertTrue(card.contains("nullable = false") || card.contains("nullable=false"), card);
        // Required when sent: the DTO still checks it.
        String kt = kt("domain/model/SrAddress.kt");
        assertFalse(kt.contains("nullable = false") || kt.contains("nullable=false"), kt);
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
        // Three levels (M3 of the review): the line checks its options, the option its extra.
        assertTrue(line.contains("for (SrLineOption member : this.options) member.requireReferences(lookup);"), line);
        String option = java("domain/model/SrLineOption.java");
        assertTrue(option.contains("if (this.extraId != null && lookup.find(SrExtra.class, this.extraId) == null)"), option);

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
        for (File dir : new File[]{jproject, kproject, unsafe, two, refused, modules, owned}) {
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
