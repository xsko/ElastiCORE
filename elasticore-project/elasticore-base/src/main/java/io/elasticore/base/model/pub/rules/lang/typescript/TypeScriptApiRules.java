package io.elasticore.base.model.pub.rules.lang.typescript;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.DomainSpec;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * A typed client for the endpoints the generated controller exposes.
 *
 * The controller and this are written from one model, so the paths, the
 * request bodies and the response shapes cannot disagree - a DSL change shows
 * up here as a type error rather than as a 400 at runtime.
 *
 * Only what a browser calls. The DSL's other ports - a database query, an
 * outbound HTTP service - are how the server reaches something else, and have
 * no counterpart on this side.
 */
public class TypeScriptApiRules implements CodeRules {

    /** The runtime the clients share: the page shape, fetch, and errors. */
    @Rule(value = "ts-api-support", target = "apiSupport", order = 10)
    void support(DomainSpec d) {
        // Nothing to fill: the skeleton is the whole file. The rule exists so
        // the artifact has one, and so a domain with no exposed entity still
        // gets the runtime its siblings import.
        ctx(d).var("domainName", d.getName());
    }

    @Rule(value = "ts-api", target = "api", order = 10)
    void api(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntax(Syntax.TYPESCRIPT, "ts-api");
        Syntax syntax = c.syntax();

        List<FieldSpec> pks = e.pkFields();
        if (pks.isEmpty()) {
            c.dontMake();
            return;
        }

        String dto = String.format("%sDTO", e.getName());
        String keyDto = String.format("%sKeyDTO", e.getName());
        String srchDto = e.searchDtoClassName();
        String result = e.searchResultDtoName() == null ? dto : e.searchResultDtoName();
        boolean paged = e.pageable();
        String searchResult = paged
                ? String.format("Page<%s>", result)
                : String.format("%s[]", result);

        c.var("entityClassName", e.getName())
         .var("className", String.format("%sController", e.getName()))
         .var("apiBase", String.format("%s/%s/%s", e.apiPath(), e.domainName(), e.varName()))
         .var("importList", imports(syntax, dto, keyDto, srchDto, result, paged, e));

        String idParams = idParameters(syntax, pks);
        String idPath = idPath(pks);

        java.util.Set<String> gone = excluded(e);

        if (!gone.contains("create")) method(c, "create", syntax.parameter(dto, "input"), dto,
                String.format("request<%s>('POST', BASE, input)", dto),
                "Registers a new " + e.getName() + ".");

        if (!gone.contains("read")) method(c, "findById", idParams, String.format("%s | null", dto),
                String.format("requestOrNull<%s>('GET', BASE + %s)", dto, idPath),
                "The " + e.getName() + " with this identifier, or null when there is none.");

        if (!gone.contains("search")) method(c, "search", syntax.parameter(srchDto, "condition"), searchResult,
                String.format("request<%s>('POST', BASE + '/search', condition)", searchResult),
                "Every " + e.getName() + " matching the condition.");

        if (!gone.contains("update")) method(c, "update", syntax.parameter(dto, "input"), dto,
                String.format("request<%s>('PUT', BASE, input)", dto),
                "Updates an existing " + e.getName() + ".");

        if (!gone.contains("batchUpdate")) method(c, "batchUpdate", syntax.parameter(String.format("%s[]", dto), "inputs"), "void",
                "request<void>('PUT', BASE + '/batch', inputs)",
                "Updates several in one transaction; none are applied if any fails.");

        if (!gone.contains("deleteBatch")) method(c, "deleteMany", syntax.parameter(String.format("%s[]", keyDto), "keys"), "boolean[]",
                "request<boolean[]>('POST', BASE + '/delete', keys)",
                "Deletes each key, answering for each whether it was there.");

        if (!gone.contains("deleteById")) method(c, "deleteById", idParams, "boolean",
                String.format("request<boolean>('DELETE', BASE + %s)", idPath),
                "Deletes the " + e.getName() + " with this identifier.");

        // A lifecycle's commands, asked before they are given: which buttons a
        // screen shows. The same test the entity's canShip() makes.
        java.util.Set<String> asked = new java.util.HashSet<String>();
        for (EntitySpec.StateMachine m : e.stateMachines()) {
            // Only where the server writes the lifecycle: a root with a behaviour class.
            if (!io.elasticore.base.model.pub.rules.ddd.DddBehaviorRules.splits(e)) continue;
            for (EntitySpec.Move move : m.moves()) {
                if (!e.lifecycleAccepts(move.command()) || !asked.add(move.command())) continue;
                // A command that moves several lifecycles needs each of them in a starting state.
                List<String> tests = new ArrayList<String>();
                List<String> said = new ArrayList<String>();
                for (EntitySpec.StateMachine with : e.machinesOf(move.command())) {
                    String type = null;
                    for (FieldSpec f : e.fields()) {
                        if (f.name().equals(with.field())) type = syntax.typeName(f.typeName());
                    }
                    StringBuilder test = new StringBuilder();
                    for (String from : with.moveOf(move.command()).from()) {
                        if (test.length() > 0) test.append(" || ");
                        test.append(String.format("value.%s === %s.%s", with.field(), type, from));
                    }
                    tests.add("(" + test + ")");
                    said.add(String.format("the %s is %s", with.field(), String.join(" or ", with.moveOf(move.command()).from())));
                }
                c.varLine("methodList", "/** Whether %s may be given now: %s. */", move.command(), String.join(" and ", said));
                c.varLine("methodList", "export function can%s(value: %s | null | undefined): boolean {",
                        Character.toUpperCase(move.command().charAt(0)) + move.command().substring(1), dto);
                c.varLine("methodList", "    return value != null && %s;", String.join(" && ", tests));
                c.varLine("methodList", "}");
                c.varLine("methodList", "");
            }
        }

        // states: delete - whether a row may be deleted now: the same test the
        // entity's canDelete() makes before JPA removes it.
        List<EntitySpec.StateMachine> guards = io.elasticore.base.model.pub.rules.ddd.DddStateRules.deleteGuards(e);
        if (!guards.isEmpty()) {
            List<String> tests = new ArrayList<String>();
            List<String> said = new ArrayList<String>();
            for (EntitySpec.StateMachine m : guards) {
                String type = null;
                for (FieldSpec f : e.fields()) {
                    if (f.name().equals(m.field())) type = syntax.typeName(f.typeName());
                }
                StringBuilder test = new StringBuilder();
                for (String state : m.deletable()) {
                    if (test.length() > 0) test.append(" || ");
                    test.append(String.format("value.%s === %s.%s", m.field(), type, state));
                }
                tests.add(test.length() == 0 ? "false" : "(" + test + ")");
                said.add(m.deletable().isEmpty() ? "never"
                        : String.format("the %s is %s", m.field(), String.join(" or ", m.deletable())));
            }
            c.varLine("methodList", "/** Whether it may be deleted now: %s. */", String.join(" and ", said));
            c.varLine("methodList", "export function canDelete(value: %s | null | undefined): boolean {", dto);
            c.varLine("methodList", "    return value != null && %s;", String.join(" && ", tests));
            c.varLine("methodList", "}");
            c.varLine("methodList", "");
        }

        // The business commands the DSL declares ("commands:"), one POST each,
        // as the DDD controller serves them: /{id}/ship with the command's DTO.
        if (pks.size() == 1) {
            for (EntitySpec.CommandSpec cmd : e.commands()) {
                String path = cmd.name().replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
                String params = idParams;
                String body = "";
                if (cmd.dtoName() != null) {
                    params += ", " + syntax.parameter(cmd.dtoName(), "command");
                    body = ", command";
                }
                method(c, cmd.name(), params, dto,
                        String.format("request<%s>('POST', BASE + %s + '/%s'%s)", dto, idPath, path, body),
                        String.format("The %s command: %s decides, and answers with what it became.", cmd.name(), e.getName()));
            }
        }

        // Signing in, as the controller serves it where the DSL marks @login and @password.
        if (io.elasticore.base.model.pub.rules.base.ServiceAccessRules.signsIn(e)) {
            String login = io.elasticore.base.model.pub.rules.base.ServiceAccessRules.loginField(e);
            String password = "password";
            for (FieldSpec f : e.fields()) {
                if (f.hasDsl("password")) password = f.name();
            }
            method(c, "signIn", syntax.parameter("string", login) + ", " + syntax.parameter("string", password), dto,
                    String.format("request<%s>('POST', BASE + '/signin', { %s, %s })", dto, login, password),
                    "Signs in: the session cookie is set (same origin) and the " + e.getName() + " answered; ApiError 401 when the "
                            + login + " or the password is wrong.");
            method(c, "signOut", "", "void", "request<void>('POST', BASE + '/signout')",
                    "Signs out: the session ends.");
            c.varLine("methodList", "/** The %s signed in now, or null for a visitor. */", e.getName())
             .varLine("methodList", "export async function me(): Promise<%s | null> {", dto)
             .varLine("methodList", "    try {")
             .varLine("methodList", "        return await request<%s>('GET', BASE + '/me');", dto)
             .varLine("methodList", "    } catch (e) {")
             .varLine("methodList", "        if (e instanceof ApiError && e.status === 401) return null;")
             .varLine("methodList", "        throw e;")
             .varLine("methodList", "    }")
             .varLine("methodList", "}")
             .varLine("methodList", "");
        }
    }

    /**
     * The endpoints the controller does not have, by its section ids.
     *
     * The same three reasons the controller drops one for: @readonly,
     * @restExclude, and - on a DDD server - a root with nothing a request
     * may edit, which is changed by its commands only. Without them the
     * client offered an update the server answers 405 to.
     */
    private static java.util.Set<String> excluded(EntitySpec e) {
        java.util.Set<String> gone = new java.util.HashSet<String>();
        if (e.hasMeta("readonly")) {
            gone.addAll(java.util.Arrays.asList("create", "update", "batchUpdate", "deleteBatch", "deleteById"));
        }
        // The server's controller drops what goes with each (restExcluded) -
        // a clean one; a 2.x server drops only what is named, and keeps the rest.
        gone.addAll(io.elasticore.base.model.pub.rules.base.ControllerBaseRules.restExcluded(e,
                !io.elasticore.base.model.pub.rules.verify.SpecFactory.compatibleServer()));
        if (io.elasticore.base.model.pub.rules.verify.SpecFactory.commandOnlyRoots()
                && io.elasticore.base.model.pub.rules.ddd.DddCommandRules.nothingEditable(e)) {
            gone.add("update");
            gone.add("batchUpdate");
        }
        return gone;
    }

    /**
     * One exported function per endpoint.
     *
     * Written as functions rather than a class: there is no state to hold, and
     * a module's exports are what a bundler can drop what nobody called.
     */
    private void method(SourceCode c, String name, String params,
                        String returns, String call, String description) {
        c.varLine("methodList", "/** %s */", description)
         .varLine("methodList", "export async function %s(%s): Promise<%s> {", name, params, returns)
         .varLine("methodList", "    return %s;", call)
         .varLine("methodList", "}")
         .varLine("methodList", "");
    }

    /** The identifier as this client's caller passes it. */
    private String idParameters(Syntax syntax, List<FieldSpec> pks) {
        StringBuilder sb = new StringBuilder();
        for (FieldSpec f : pks) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(syntax.parameter(f.typeName(), f.name()));
        }
        return sb.toString();
    }

    /**
     * The path the identifier makes, built by concatenation rather than a
     * template literal: the skeletons are read by a substituting engine that
     * would take a "${...}" of its own.
     */
    private String idPath(List<FieldSpec> pks) {
        StringBuilder sb = new StringBuilder();
        for (FieldSpec f : pks) {
            sb.append(String.format(" + '/' + seg(%s)", f.name()));
        }
        return sb.substring(3);
    }

    private String imports(Syntax syntax, String dto, String keyDto,
                           String srchDto, String result, boolean paged, EntitySpec e) {
        Set<String> models = new LinkedHashSet<String>();
        models.add(dto);
        models.add(keyDto);
        models.add(srchDto);
        models.add(result);
        if (e.pkFields().size() == 1) {
            for (EntitySpec.CommandSpec cmd : e.commands()) {
                if (cmd.dtoName() != null) models.add(cmd.dtoName());
            }
        }

        StringBuilder sb = new StringBuilder();
        List<String> runtime = new ArrayList<String>();
        runtime.add("request");
        runtime.add("requestOrNull");
        runtime.add("seg");
        if (paged) runtime.add("Page");
        if (io.elasticore.base.model.pub.rules.base.ServiceAccessRules.signsIn(e)) runtime.add("ApiError");
        sb.append(String.format("import { %s } from './apiSupport';%n",
                String.join(", ", runtime)));

        // The state enums the lifecycle checks compare against.
        for (EntitySpec.StateMachine machine : e.stateMachines()) {
            for (FieldSpec f : e.fields()) {
                if (f.name().equals(machine.field())) models.add(syntax.typeName(f.typeName()));
            }
        }
        for (String m : models) {
            sb.append(syntax.importLine(m)).append('\n');
        }
        return sb.toString();
    }
}
