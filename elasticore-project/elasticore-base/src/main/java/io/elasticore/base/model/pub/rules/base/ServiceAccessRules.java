package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Who may do what, and how a password is kept - said in the DSL, enforced in
 * the CoreService.
 *
 * <pre>
 * PurchaseOrder:
 *   meta: entity @root @expose @owner(customerId)
 *         @access(read=owner, create=user, cancel=owner, ship=admin)
 * Customer:
 *   fields:
 *     login:    string(10)! @login
 *     password: string(255)! @password
 *     role:     UserRole @access(admin)       # only the administrator writes it
 * </pre>
 *
 * The CoreService rather than the controller: the controller is the
 * developer's after it is first written, and the service is regenerated, so
 * a rule changed in the DSL reaches every endpoint - and every other caller
 * that goes through the use case - on the next build.
 *
 * The caller is the application itself only inside CurrentUser.runAsSystem;
 * anyone else nobody signed in is a visitor, in a request or not.
 *
 * The template carries a slot at each place a check belongs; with nothing
 * declared the slots are empty and the service is what it was.
 */
public class ServiceAccessRules implements CodeRules {

    /** The template's line slots, registered empty by the service file rules. */
    public static final String[] SLOTS = {
            "accessFindAll", "accessDeleteBySearch", "accessSearch", "accessFindById",
            "accessCreate", "accessUpdate", "accessDelete", "accessUpdateAll"};

    /** The template's inline slots around a search's specification, empty by default. */
    public static final String[] INLINE = {"accessSpecOpen", "accessSpecClose"};

    private static final String RT = "io.elasticore.springboot3.security.";
    private static final java.util.regex.Pattern ENTRY =
            java.util.regex.Pattern.compile("^\\s*([\\w*]+)\\s*=\\s*(public|user|owner|admin|none)\\s*$",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Whether the entity's service has anything to check or hash. */
    public static boolean guarded(EntitySpec e) {
        if (e.metaText("access") != null || e.metaValue("owner") != null) return true;
        for (FieldSpec f : e.fields()) {
            if (f.hasDsl("password") || adminOnly(f)) return true;
        }
        return false;
    }

    /** A field only the administrator writes: @access(admin) on the field. */
    static boolean adminOnly(FieldSpec f) {
        return "admin".equalsIgnoreCase(trim(f.dslValue("access")));
    }

    /**
     * The levels @access declares, operation -> level; "*" holds the default.
     * An entry that does not read as operation=level is reported by the
     * build, rather than found when the application refuses to start.
     */
    static Map<String, String> levels(EntitySpec e, boolean warn) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        String declared = e.metaText("access");
        if (declared == null || declared.trim().isEmpty()) return out;
        for (String part : declared.split(",")) {
            if (part.trim().isEmpty()) continue;
            java.util.regex.Matcher m = ENTRY.matcher(part.replace("'", "").replace("\"", ""));
            if (!m.matches()) {
                if (warn) {
                    io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                            "[%s] %s @access: '%s' is not operation=level (public, user, owner, admin, none);"
                                    + " the application would refuse to start.",
                            e.domainClassName(), e.getName(), part.trim()));
                }
                continue;
            }
            out.put(m.group(1), m.group(2).toLowerCase(java.util.Locale.ROOT));
        }
        return out;
    }

    /** The level a read is at: what "read" says, else "*", else admin; public with no @access. */
    public static String readLevel(String declaration) {
        if (declaration == null || declaration.trim().isEmpty()) return "public";
        String star = null;
        for (String part : declaration.split(",")) {
            java.util.regex.Matcher m = ENTRY.matcher(part.replace("'", "").replace("\"", ""));
            if (!m.matches()) continue;
            if ("read".equals(m.group(1))) return m.group(2).toLowerCase(java.util.Locale.ROOT);
            if ("*".equals(m.group(1))) star = m.group(2).toLowerCase(java.util.Locale.ROOT);
        }
        return star != null ? star : "admin";
    }

    /** A DTO field carrying a whole row of an entity not every reader may see. */
    public static boolean nestsGuarded(EntitySpec e, FieldSpec f) {
        String level = nestedReadLevel(e, f);
        return level != null && !"public".equals(level);
    }

    /** The read level of the entity a DTO field carries a whole row of - as the entity or as its DTO - or null. */
    private static String nestedReadLevel(EntitySpec e, FieldSpec f) {
        if (f.isList() || f.isBaseType() || f.dslValue("ref") != null) return null;
        String type = simple(f.typeName());
        String level = e.readLevelOf(type);
        if (level == null && type.endsWith("DTO")) {
            String entity = e.dtoTemplateEntity(type);
            level = e.readLevelOf(entity != null && !entity.isEmpty() ? entity : type.substring(0, type.length() - 3));
        }
        return level;
    }

    /**
     * A row of another entity travels in a DTO only if anyone may read it:
     * a ticket must not hand its readers the agent behind it when only that
     * agent may read the agent. The identity stays; the row does not.
     */
    @Rule(value = "access-dto-nested", target = "dto", order = 0)
    void nested(EntitySpec e, FieldSpec f) {
        if (!nestsGuarded(e, f)) return;
        ctx(e).removeField(f);
        io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                "[%s] %s.%s is left out: %s is read under @access(read=%s), and a DTO carrying it would show it"
                        + " to whoever reads %s. Its identity stays.",
                e.domainClassName(), e.getName(), f.name(), simple(f.typeName()), nestedReadLevel(e, f), e.getName()));
    }

    /**
     * No DTO default for what only the administrator writes: sent back on
     * every update, the default reset it - an administrator saving a
     * telephone demoted an administrator. The entity keeps the default.
     */
    @Rule(value = "access-dto-default", target = "dto", order = 32)
    void noDefault(EntitySpec e, FieldSpec f) {
        if (!adminOnly(f) || !f.hasDsl("default")) return;
        ctx(e).field(f).noInitial().removeAnnotation("lombok.Builder");
    }

    @Rule(value = "service-access", target = "service", order = 40)
    void access(EntitySpec e) {
        if (!guarded(e)) return;
        SourceCode c = ctx(e);
        boolean kt = c.syntax() == Syntax.KOTLIN;

        String entity = e.getName();
        String dto = entity + "DTO";
        String srch = e.searchDtoClassName();
        Map<String, String> levels = levels(e, true);
        FieldSpec owner = ownerField(e);
        String ownerType = owner == null ? null : idType(owner.typeName());
        if (owner != null && ownerType == null) {
            io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                    "[%s] %s @owner(%s): an owner is a long, int, string or UUID identity; %s is none, so owner-level"
                            + " operations are the administrator's.", e.domainClassName(), entity, owner.name(), owner.typeName()));
            owner = null;
        }
        if (owner == null && levels.containsValue("owner")) {
            io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                    "[%s] %s @access has owner-level operations but no @owner field resolves%s - they are the"
                            + " administrator's alone until @owner names the identity that holds the owner.",
                    e.domainClassName(), entity, e.metaValue("owner") == null ? "" : " (@owner(" + e.metaValue("owner") + "))"));
        }
        FieldSpec pk = e.pkField();
        boolean ownerIsKey = owner != null && pk != null && owner.name().equals(pk.name());
        boolean keyGenerated = pk != null && (pk.generationType() != null
                || pk.hasDslAny("sequence", "genid", "generated", "identity", "autoincrement"));
        if (pk != null && !keyGenerated) {
            // A key generated another way - a raw @jpa:GeneratedValue - is generated too.
            for (String line : pk.nativeAnnotationLines()) {
                if (line.contains("GeneratedValue")) keyGenerated = true;
            }
        }
        List<FieldSpec> passwords = new ArrayList<FieldSpec>();
        List<FieldSpec> adminOnly = new ArrayList<FieldSpec>();
        for (FieldSpec f : e.fields()) {
            if (f.hasDsl("password")) passwords.add(f);
            if (adminOnly(f)) adminOnly.add(f);
        }

        // The slots.
        c.varLine("accessFindAll", stmt(kt, "accessAll(\"read\")"))
         .varLine("accessDeleteBySearch", stmt(kt, "accessAll(\"delete\")"))
         .varLine("accessSearch", stmt(kt, "accessSearch(searchDTO)"))
         .varLine("accessFindById", kt ? ".filter { readable(it) }" : ".filter(this::readable)")
         .varLine("accessCreate", stmt(kt, "accessCreate(dto)"))
         .varLine("accessUpdate", stmt(kt, "accessUpdate(entity, dto)"))
         .varLine("accessUpdateAll", stmt(kt, "accessUpdateAll(dto)"));
        c.var("accessSpecOpen", "ownedOnly(").var("accessSpecClose", ")");
        String repo = orDefault(c.varValue("cmdRef"), "repository");
        String id = orDefault(c.varValue("pkDtoInfo2"), pk == null ? "id" : pk.name());
        c.varLine("accessDelete", kt
                ? String.format("%s.findById(%s).ifPresent { accessWrite(it, \"delete\") }", repo, id)
                : String.format("%s.findById(%s).ifPresent(e -> accessWrite(e, \"delete\"));", repo, id));

        String declaration = e.metaText("access");
        String adminRole = orDefault(e.config("adminRole"), "ADMIN");
        String ownerRead = owner == null ? "null" : getter(kt, "entity", owner.name());

        Lines m = new Lines(kt);
        // ---- what the service holds
        if (kt) {
            m.add("/** Who is asking: a visitor unless signed in, the application itself inside CurrentUser.runAsSystem. */")
             .add("@set:org.springframework.beans.factory.annotation.Autowired(required = false)")
             .add("protected var currentUser: %sCurrentUser = %sSessionCurrentUser()", RT, RT).add("");
            if (!passwords.isEmpty()) {
                m.add("/** How a password is kept: the application's PasswordHasher bean, or PBKDF2. */")
                 .add("@set:org.springframework.beans.factory.annotation.Autowired(required = false)")
                 .add("protected var passwordHasher: %sPasswordHasher = %sPasswordHasher.standard()", RT, RT).add("");
            }
        } else {
            m.add("/** @access of the DSL. */")
             .add("protected static final %sAccessPolicy ACCESS =", RT)
             .add("        %sAccessPolicy.parse(\"%s\", %s, \"%s\");", RT, entity, quoted(declaration), adminRole).add("")
             .add("/** Who is asking: a visitor unless signed in, the application itself inside CurrentUser.runAsSystem. */")
             .add("protected %sCurrentUser currentUser = new %sSessionCurrentUser();", RT, RT).add("")
             .add("/** The application's CurrentUser bean, when it has one - Spring Security's, say. */")
             .add("@org.springframework.beans.factory.annotation.Autowired(required = false)")
             .add("public void setCurrentUser(%sCurrentUser currentUser) {", RT)
             .add("    this.currentUser = currentUser;")
             .add("}").add("");
            if (!passwords.isEmpty()) {
                m.add("/** How a password is kept: PBKDF2 unless the application has a PasswordHasher bean. */")
                 .add("protected %sPasswordHasher passwordHasher = %sPasswordHasher.standard();", RT, RT).add("")
                 .add("@org.springframework.beans.factory.annotation.Autowired(required = false)")
                 .add("public void setPasswordHasher(%sPasswordHasher passwordHasher) {", RT)
                 .add("    this.passwordHasher = passwordHasher;")
                 .add("}").add("");
            }
        }

        // ---- over many rows
        m.fun("accessAll", "operation: String", "String operation")
         .stmt("ACCESS.requireAll(currentUser, operation)")
         .end();
        m.fun("accessSearch", "searchDTO: " + srch, srch + " searchDTO")
         .stmt("ACCESS.require(currentUser, \"read\")")
         .end();

        // ---- a search narrowed to the caller's own rows, by an equality on the owner
        m.add("/** A search narrowed to the caller's own rows where read is the owner's: an equality, whatever the field searches by. */");
        if (kt) {
            m.add("private fun ownedOnly(spec: Specification<%s>): Specification<%s> {", entity, entity)
             .add("    val owner = ACCESS.ownerFilter(currentUser, \"read\") ?: return spec");
            if (owner != null) {
                m.add("    val id = %sAccessPolicy.`as`(owner, %s::class.javaObjectType)", RT, ownerType)
                 .add("    return spec.and(Specification<%s> { r, _, cb -> cb.equal(r.get<Any>(\"%s\"), id) })", entity, owner.name());
            } else {
                m.add("    throw %sForbiddenException(\"%s is read by its owner, and names none (@owner)\")", RT, entity);
            }
            m.add("}").add("");
        } else {
            m.add("private Specification<%s> ownedOnly(Specification<%s> spec) {", entity, entity)
             .add("    Object owner = ACCESS.ownerFilter(currentUser, \"read\");")
             .add("    if (owner == null) return spec;");
            if (owner != null) {
                m.add("    final %s id = %sAccessPolicy.as(owner, %s.class);", ownerType, RT, ownerType)
                 .add("    Specification<%s> mine = (r, q, cb) -> cb.equal(r.get(\"%s\"), id);", entity, owner.name())
                 .add("    return spec == null ? mine : spec.and(mine);");
            } else {
                m.add("    throw new %sForbiddenException(\"%s is read by its owner, and names none (@owner)\");", RT, entity);
            }
            m.add("}").add("");
        }

        // ---- one row
        m.fun("readable", "entity: " + entity, entity + " entity", "Boolean", "boolean")
         .stmt("ACCESS.require(currentUser, \"read\")")
         .ret("ACCESS.owns(currentUser, \"read\", " + ownerRead + ")")
         .end();
        m.fun("accessWrite", "entity: " + entity + ", operation: String", entity + " entity, String operation")
         .stmt("ACCESS.require(currentUser, operation)");
        if (kt) {
            m.add("    if (!ACCESS.owns(currentUser, operation, %s))", ownerRead)
             .add("        throw EntityNotFoundException(\"%s not found\")", entity);
        } else {
            m.add("    if (!ACCESS.owns(currentUser, operation, %s))", ownerRead)
             .add("        throw new EntityNotFoundException(\"%s not found\");", entity);
        }
        m.end();

        // ---- create: the owner is whoever is signed in, unless the administrator says otherwise
        m.fun("accessCreate", "dto: " + dto, dto + " dto")
         .stmt("ACCESS.require(currentUser, \"create\")");
        FieldSpec claimed = owner == null ? null : (!ownerIsKey || !keyGenerated) ? owner : null;
        if (claimed != null) {
            String idOf = kt
                    ? String.format("%sAccessPolicy.`as`(currentUser.id(), %s::class.javaObjectType)", RT, ownerType)
                    : String.format("%sAccessPolicy.as(currentUser.id(), %s.class)", RT, ownerType);
            if (claimed == pk) {
                // Keyed by its owner: a visitor has no identity to key it by.
                if (kt) m.add("    if (!ACCESS.isAdmin(currentUser) && !currentUser.signedIn()) throw %sNotSignedInException(\"Sign in to create %s\")", RT, entity);
                else m.add("    if (!ACCESS.isAdmin(currentUser) && !currentUser.signedIn()) throw new %sNotSignedInException(\"Sign in to create %s\");", RT, entity);
            }
            m.add("    // Whoever is signed in owns what they create - a visitor's claim is cleared, not kept.");
            if (kt) m.add("    if (!ACCESS.isAdmin(currentUser)) dto.%s = %s", claimed.name(), idOf);
            else m.add("    if (!ACCESS.isAdmin(currentUser)) dto.%s(%s);", setterName(claimed.name()), idOf);
        }
        for (FieldSpec f : adminOnly) {
            String fallback = kt ? entity + "()." + f.name() : "new " + entity + "()." + getterName(f.name()) + "()";
            if (kt) {
                m.add("    if (!ACCESS.isAdmin(currentUser) || dto.%s == null) dto.%s = %s", f.name(), f.name(), fallback);
            } else {
                m.add("    if (!ACCESS.isAdmin(currentUser) || dto.%s() == null) dto.%s(%s);",
                        getterName(f.name()), setterName(f.name()), fallback);
            }
        }
        m.stmt("hashPasswords(dto)").end();

        // ---- update one row: what the caller may not change is what the row holds
        m.fun("accessUpdate", "entity: " + entity + "?, dto: " + dto, entity + " entity, " + dto + " dto")
         .add(kt ? "    if (entity == null) return" : "    if (entity == null) return;")
         .stmt("accessWrite(entity, \"update\")");
        List<FieldSpec> kept = new ArrayList<FieldSpec>(adminOnly);
        if (owner != null && !ownerIsKey) kept.add(owner);
        if (!kept.isEmpty()) {
            m.add("    if (!ACCESS.isAdmin(currentUser)) {");
            for (FieldSpec f : kept) {
                if (kt) m.add("        dto.%s = entity.%s", f.name(), f.name());
                else m.add("        dto.%s(entity.%s());", setterName(f.name()), getterName(f.name()));
            }
            m.add("    }");
        }
        m.stmt("hashPasswords(dto)").end();

        // ---- update by condition: merged skipping nulls, so leaving them out is enough
        m.fun("accessUpdateAll", "dto: " + dto, dto + " dto")
         .stmt("accessAll(\"update\")");
        if (!kept.isEmpty()) {
            m.add("    if (!ACCESS.isAdmin(currentUser)) {");
            for (FieldSpec f : kept) {
                if (kt) m.add("        dto.%s = null", f.name());
                else m.add("        dto.%s(null);", setterName(f.name()));
            }
            m.add("    }");
        }
        m.stmt("hashPasswords(dto)").end();

        m.add("/** A password a request carries is kept only as its hash. */");
        if (kt) {
            m.add("private fun hashPasswords(dto: %s) {", dto);
            for (FieldSpec f : passwords) {
                m.add("    dto.%s?.let { dto.%s = passwordHasher.hash(it) }", f.name(), f.name());
            }
            m.add("}")
             .add("")
             .add("companion object {")
             .add("    /** @access of the DSL. */")
             .add("    @JvmStatic")
             .add("    protected val ACCESS: %sAccessPolicy =", RT)
             .add("        %sAccessPolicy.parse(\"%s\", %s, \"%s\")", RT, entity, quoted(declaration), adminRole)
             .add("}");
        } else {
            m.add("private void hashPasswords(%s dto) {", dto);
            for (FieldSpec f : passwords) {
                m.add("    if (dto.%s() != null) dto.%s(passwordHasher.hash(dto.%s()));",
                        getterName(f.name()), setterName(f.name()), getterName(f.name()));
            }
            m.add("}");
        }
        c.addMethod(m.text());

        authenticate(e, c, kt, passwords);
    }

    /**
     * Signing in: a field marked @login and one marked @password give the
     * service authenticate(login, password) - the row, or empty. A login
     * nobody has costs the same hash as a wrong password, so the time taken
     * does not say which logins exist.
     */
    private void authenticate(EntitySpec e, SourceCode c, boolean kt, List<FieldSpec> passwords) {
        String loginName = loginField(e);
        if (loginName == null) return;
        FieldSpec password = passwords.get(0);
        String entity = e.getName();
        String dto = entity + "DTO";
        String query = orDefault(c.varValue("qryRef"), "repository");
        String mapper = orDefault(c.varValue("mapperName"), e.domainClassName() + "Mapper");

        StringBuilder m = new StringBuilder();
        m.append("    /** The ").append(entity).append(" this ").append(loginName).append(" and password belong to, or empty. */\n");
        if (kt) {
            m.append("    open fun authenticate(").append(loginName).append(": String?, password: String?): Optional<").append(dto).append("> {\n")
             .append("        if (").append(loginName).append(" == null || password == null) return Optional.empty()\n")
             .append("        val found = ").append(query).append(".findAll(Specification<").append(entity).append("> { r, _, cb -> cb.equal(r.get<Any>(\"")
             .append(loginName).append("\"), ").append(loginName).append(") })\n")
             .append("        if (found.size != 1) {\n")
             .append("            passwordHasher.hash(password)\n")
             .append("            return Optional.empty()\n")
             .append("        }\n")
             .append("        if (!passwordHasher.matches(password, found[0].").append(password.name()).append(")) return Optional.empty()\n")
             .append("        return Optional.ofNullable(").append(mapper).append(".toDTO(found[0]))\n")
             .append("    }");
        } else {
            m.append("    public Optional<").append(dto).append("> authenticate(String ").append(loginName).append(", String password) {\n")
             .append("        if (").append(loginName).append(" == null || password == null) return Optional.empty();\n")
             .append("        List<").append(entity).append("> found = ").append(query)
             .append(".findAll((Specification<").append(entity).append(">) (r, q, cb) -> cb.equal(r.get(\"")
             .append(loginName).append("\"), ").append(loginName).append("));\n")
             .append("        if (found.size() != 1) {\n")
             .append("            passwordHasher.hash(password);\n")
             .append("            return Optional.empty();\n")
             .append("        }\n")
             .append("        if (!passwordHasher.matches(password, found.get(0).").append(getterName(password.name())).append("())) return Optional.empty();\n")
             .append("        return Optional.of(").append(mapper).append(".toDTO(found.get(0)));\n")
             .append("    }");
        }
        c.addMethod(m.toString());
        me(e, c, kt);
    }

    /**
     * Who is signed in: the row whose key the session holds - read past the
     * entity's read level, since anyone signed in may see themselves.
     */
    private void me(EntitySpec e, SourceCode c, boolean kt) {
        if (!hasMe(e)) return;
        String keyType = idType(e.pkFields().get(0).typeName());
        String entity = e.getName();
        String dto = entity + "DTO";
        String repo = orDefault(c.varValue("cmdRef"), "repository");
        String mapper = orDefault(c.varValue("mapperName"), e.domainClassName() + "Mapper");
        StringBuilder m = new StringBuilder();
        m.append("    /** The ").append(entity).append(" signed in now - the row the session's identity names - or empty for a visitor. */\n");
        if (kt) {
            m.append("    open fun me(): Optional<").append(dto).append("> {\n")
             .append("        val id = currentUser.id() ?: return Optional.empty()\n")
             .append("        return ").append(repo).append(".findById(").append(RT).append("AccessPolicy.`as`(id, ")
             .append(keyType).append("::class.javaObjectType)).map { ").append(mapper).append(".toDTO(it) }\n")
             .append("    }");
        } else {
            m.append("    public Optional<").append(dto).append("> me() {\n")
             .append("        Object id = currentUser.id();\n")
             .append("        if (id == null) return Optional.empty();\n")
             .append("        return ").append(repo).append(".findById(").append(RT).append("AccessPolicy.as(id, ")
             .append(keyType).append(".class)).map(row -> ").append(mapper).append(".toDTO(row));\n")
             .append("    }");
        }
        c.addMethod(m.toString());
    }

    /**
     * Whether the API signs in through this entity: a @login and a @password,
     * exposed, one key, and not turned off with {@code config: signin: false}
     * (an application that signs in its own way - Spring Security's form, say).
     */
    public static boolean signsIn(EntitySpec e) {
        if (!hasMe(e) || !e.hasMeta("expose")) return false;
        String off = e.config("signin");
        return off == null || !"false".equalsIgnoreCase(off.trim());
    }

    /** Whether the service has me(): an entity that signs in (@login, @password) with one key of a type a session holds. */
    public static boolean hasMe(EntitySpec e) {
        return loginField(e) != null && e.pkFields().size() == 1 && idType(e.pkFields().get(0).typeName()) != null;
    }

    /** The field the signed-in user's role is read from: the one marked @role, else one named role. */
    public static FieldSpec roleField(EntitySpec e) {
        for (FieldSpec f : e.fields()) {
            if (f.hasDsl("role")) return f;
        }
        for (FieldSpec f : e.fields()) {
            if ("role".equals(f.name()) && !f.isList()) return f;
        }
        return null;
    }

    /**
     * The three endpoints a sign-in needs, next to the entity's own:
     *
     * <pre>
     *   POST /api/shop/customer/signin   {"login": "...", "password": "..."}  -> 200 the customer, 401
     *   POST /api/shop/customer/signout                                       -> 204
     *   GET  /api/shop/customer/me                                            -> 200 the customer, 401
     * </pre>
     *
     * The session holds the row's key and its role (the @role field, or one
     * named role); the generated services read them through CurrentUser.
     * Every project wrote this controller by hand before.
     */
    @Rule(value = "spring-signin-control", target = "control", order = 31)
    void signInControl(EntitySpec e) {
        if (!signsIn(e)) return;
        SourceCode c = ctx(e);
        boolean kt = c.syntax() == Syntax.KOTLIN;
        String entity = e.getName();
        String dto = entity + "DTO";
        String service = e.varName() + "Service";
        String login = loginField(e);
        String password = null;
        for (FieldSpec f : e.fields()) {
            if (f.hasDsl("password")) password = f.name();
        }
        FieldSpec pk = e.pkFields().get(0);
        FieldSpec role = roleField(e);
        String body = entity + "SignIn";
        String req = "jakarta.servlet.http.HttpServletRequest";
        String session = RT + "SessionCurrentUser";
        String refused = RT + "NotSignedInException";
        StringBuilder m = new StringBuilder();
        if (kt) {
            String roles = role == null ? ""
                    : e.isKnownEnum(simple(role.typeName())) ? ", signedIn." + role.name() + "?.name"
                    : ", signedIn." + role.name() + "?.toString()";
            m.append("    /** What signin is sent: the ").append(login).append(" and the password. */\n")
             .append("    class ").append(body).append("(var ").append(login).append(": String? = null, var ")
             .append(password).append(": String? = null)\n\n")
             .append("    @Operation(summary = \"").append(entity).append(": sign in - 401 when the ").append(login)
             .append(" or the password is wrong\")\n")
             .append("    @PostMapping(\"/signin\")\n")
             .append("    fun signIn(@RequestBody credentials: ").append(body).append(", request: ").append(req).append("): ").append(dto).append(" {\n")
             .append("        val signedIn = ").append(service).append(".authenticate(credentials.").append(login)
             .append(", credentials.").append(password).append(")\n")
             .append("            .orElseThrow { ").append(refused).append("(\"Wrong ").append(login).append(" or password\") }\n")
             .append("        ").append(session).append(".signIn(request, signedIn.").append(pk.name()).append(roles).append(")\n")
             .append("        return signedIn\n")
             .append("    }\n\n")
             .append("    @Operation(summary = \"").append(entity).append(": sign out\")\n")
             .append("    @PostMapping(\"/signout\")\n")
             .append("    fun signOut(request: ").append(req).append("): ResponseEntity<Void> {\n")
             .append("        ").append(session).append(".signOut(request)\n")
             .append("        return ResponseEntity.noContent().build()\n")
             .append("    }\n\n")
             .append("    @Operation(summary = \"").append(entity).append(": the one signed in - 401 for a visitor\")\n")
             .append("    @GetMapping(\"/me\")\n")
             .append("    fun me(): ").append(dto).append(" =\n")
             .append("        ").append(service).append(".me().orElseThrow { ").append(refused).append("(\"Sign in first\") }");
        } else {
            String roles = role == null ? ""
                    : e.isKnownEnum(simple(role.typeName()))
                    ? String.format(", signedIn.%s() == null ? null : signedIn.%s().name()", getterName(role.name()), getterName(role.name()))
                    : String.format(", signedIn.%s() == null ? null : String.valueOf(signedIn.%s())", getterName(role.name()), getterName(role.name()));
            m.append("    /** What signin is sent: the ").append(login).append(" and the password. */\n")
             .append("    public record ").append(body).append("(String ").append(login).append(", String ").append(password).append(") {\n")
             .append("    }\n\n")
             .append("    @Operation(summary = \"").append(entity).append(": sign in - 401 when the ").append(login)
             .append(" or the password is wrong\")\n")
             .append("    @PostMapping(\"/signin\")\n")
             .append("    public ").append(dto).append(" signIn(@RequestBody ").append(body).append(" credentials, ").append(req).append(" request) {\n")
             .append("        ").append(dto).append(" signedIn = ").append(service).append(".authenticate(credentials.").append(login)
             .append("(), credentials.").append(password).append("())\n")
             .append("                .orElseThrow(() -> new ").append(refused).append("(\"Wrong ").append(login).append(" or password\"));\n")
             .append("        ").append(session).append(".signIn(request, signedIn.").append(getterName(pk.name())).append("()").append(roles).append(");\n")
             .append("        return signedIn;\n")
             .append("    }\n\n")
             .append("    @Operation(summary = \"").append(entity).append(": sign out\")\n")
             .append("    @PostMapping(\"/signout\")\n")
             .append("    public ResponseEntity<Void> signOut(").append(req).append(" request) {\n")
             .append("        ").append(session).append(".signOut(request);\n")
             .append("        return ResponseEntity.noContent().build();\n")
             .append("    }\n\n")
             .append("    @Operation(summary = \"").append(entity).append(": the one signed in - 401 for a visitor\")\n")
             .append("    @GetMapping(\"/me\")\n")
             .append("    public ").append(dto).append(" me() {\n")
             .append("        return ").append(service).append(".me().orElseThrow(() -> new ").append(refused).append("(\"Sign in first\"));\n")
             .append("    }");
        }
        c.addMethod(m.toString());
    }

    /** The @login field's name when the entity also has exactly one @password, else null. */
    public static String loginField(EntitySpec e) {
        if (e.hasMeta("readonly")) return null;
        String login = null;
        int passwords = 0;
        for (FieldSpec f : e.fields()) {
            if (f.hasDsl("login")) login = f.name();
            if (f.hasDsl("password")) passwords++;
        }
        return passwords == 1 ? login : null;
    }

    /**
     * The field @owner names - or, when it names a relation (@owner(customer)),
     * the identity that holds it (customerId). Null when nothing resolves.
     */
    static FieldSpec ownerField(EntitySpec e) {
        String name = trim(e.metaValue("owner"));
        if (name == null || name.isEmpty()) return null;
        for (FieldSpec f : e.fields()) {
            if (!f.name().equals(name)) continue;
            if (!f.isEntityRef()) return f;
            for (FieldSpec other : e.fields()) {
                if (name.equals(other.dslValue("idof"))) return other;
            }
            return null;
        }
        return null;
    }

    /** The owner's identity type as both languages name it, or null for one that is not an identity. */
    private static String idType(String type) {
        String t = simple(type);
        if ("long".equals(t) || "Long".equals(t)) return "Long";
        if ("int".equals(t) || "Integer".equals(t)) return "Integer";
        if ("String".equals(t)) return "String";
        if ("UUID".equals(t)) return "java.util.UUID";
        return null;
    }

    /** Method text written once and read as Java or Kotlin. */
    private static final class Lines {
        private final boolean kt;
        private final StringBuilder out = new StringBuilder();

        Lines(boolean kt) {
            this.kt = kt;
        }

        Lines add(String format, Object... args) {
            String text = args.length == 0 ? format : String.format(format, args);
            out.append(text.isEmpty() ? "" : "    " + text).append('\n');
            return this;
        }

        Lines fun(String name, String ktParams, String javaParams) {
            return fun(name, ktParams, javaParams, null, "void");
        }

        Lines fun(String name, String ktParams, String javaParams, String ktReturn, String javaReturn) {
            if (kt) return add("private fun %s(%s)%s {", name, ktParams, ktReturn == null ? "" : ": " + ktReturn);
            return add("private %s %s(%s) {", javaReturn, name, javaParams);
        }

        Lines stmt(String code) {
            return add("    %s%s", code, kt ? "" : ";");
        }

        Lines ret(String code) {
            return add("    return %s%s", code, kt ? "" : ";");
        }

        Lines end() {
            return add("}").add("");
        }

        String text() {
            String s = out.toString();
            while (s.endsWith("\n")) s = s.substring(0, s.length() - 1);
            return s;
        }
    }

    private static String stmt(boolean kt, String code) {
        return kt ? code : code + ";";
    }

    private static String getter(boolean kt, String receiver, String field) {
        return kt ? receiver + "." + field : receiver + "." + getterName(field) + "()";
    }

    private static String getterName(String field) {
        return "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }

    private static String setterName(String field) {
        return "set" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }

    static String simple(String type) {
        if (type == null) return "";
        String t = type.replace("?", "").trim();
        int dot = t.lastIndexOf('.');
        return dot >= 0 ? t.substring(dot + 1) : t;
    }

    private static String quoted(String declaration) {
        if (declaration == null) return "null";
        return "\"" + declaration.trim().replace("\\", "\\\\").replace("\"", "'") + "\"";
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
