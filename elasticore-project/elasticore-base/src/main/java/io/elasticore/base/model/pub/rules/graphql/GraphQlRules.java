package io.elasticore.base.model.pub.rules.graphql;

import io.elasticore.base.model.pub.rules.ArtifactContributor;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.base.ControllerBaseRules;
import io.elasticore.base.model.pub.rules.base.RuleText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * A GraphQL API over the services, where a domain's env.yml turns it on:
 *
 * <pre>
 *   elasticore:
 *     config:
 *       graphql:
 *         enable: true
 *         mutations: true      # false: queries only
 *         path: /graphql       # spring.graphql.path; one value for the application
 * </pre>
 *
 * One controller per entity the REST API serves (@expose, with a key; under
 * the domain rules, a root): queries named after the entity - member(id),
 * members(search) - and mutations - createMember, updateMember,
 * deleteMember, and one per command (shipOrder). Each calls the service the
 * REST controller calls, so access, validation, invariants, soft deletion and
 * the lifecycle hold the same way, and @readonly, @expose(methods=...) and
 * @restExclude take the same operations away.
 *
 * The schema is not written here: the build writes graphql/*.graphqls from
 * the compiled controllers and the classes they take and answer (the
 * runtime's GraphQlSchemaWriter, run as elcoreGraphQl), so it has the types
 * the classes have. The controllers carry @GraphQlDomain for it to find them,
 * and import the runtime's configuration - scalars and errors.
 *
 * The domain rules add their part to the same target: a member gets no
 * controller (ddd-graphql), a command is a mutation (ddd-command-graphql),
 * and under the hexagon the use case is what is injected (hex-usecase-graphql).
 */
public class GraphQlRules implements CodeRules, ArtifactContributor {

    /** The env.yml switch, under elasticore.config. */
    public static final String ENABLE = "graphql.enable";

    @Override
    public List<ContributedArtifact> artifactsFor(EntitySpec e) {
        if (!applies(e)) return Collections.emptyList();
        return Collections.singletonList(
                ContributedArtifact.of("graphql", "control", className(e)).orNamespace("service"));
    }

    /** Whether the entity's domain turned GraphQL on and the entity is served. */
    public static boolean applies(EntitySpec e) {
        return enabled(e.config(ENABLE)) && e.hasMeta("expose") && e.pkField() != null;
    }

    /** "true", yes or on, as env.yml writes a switch. */
    public static boolean enabled(String value) {
        if (value == null) return false;
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("yes") || v.equals("on");
    }

    /** Whether the domain's mutations are served: graphql.mutations, true when unsaid. */
    static boolean mutations(EntitySpec e) {
        String v = e.config("graphql.mutations");
        return v == null || enabled(v);
    }

    public static String className(EntitySpec e) {
        return e.getName() + "GraphQlController";
    }

    /** The query that lists: members of member, categories of category. */
    public static String plural(String name) {
        if (name.endsWith("y") && name.length() > 1 && "aeiou".indexOf(name.charAt(name.length() - 2)) < 0) {
            return name.substring(0, name.length() - 1) + "ies";
        }
        if (name.endsWith("s")
                || name.endsWith("x")
                || name.endsWith("z")
                || name.endsWith("ch")
                || name.endsWith("sh")) {
            return name + "es";
        }
        return name + "s";
    }

    @Rule(value = "graphql-vars", target = "graphql", order = 10)
    void vars(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntaxAnyOf("graphql-vars", Syntax.JAVA, Syntax.KOTLIN);
        c.var("serviceClassName", "%sService", e.getName())
                .var("servicePackageName", e.pkg("service"))
                // Layered and DDD services answer a page where the search DTO pages; the use case always does.
                .var("searchPage", String.valueOf(e.pageable()));

        if (e.hasMeta("readonly")) c.excludeMethod("create", "update", "deleteById");
        for (String id : ControllerBaseRules.restExcluded(e, true)) c.excludeMethod(id);
        for (String id : ControllerBaseRules.exposeOmitted(e)) c.excludeMethod(id);
        if (!mutations(e)) c.excludeMethod("create", "update", "deleteById", "commands");
    }

    @Rule(value = "graphql-file", target = "graphql", order = 90, phase = Rule.Phase.TAIL)
    void file(EntitySpec e) {
        SourceCode c = ctx(e);
        Syntax s = c.syntax();
        boolean kotlin = s == Syntax.KOTLIN;
        String name = e.getName();
        String var = e.varName();
        String dto = name + "DTO";
        String srch = e.searchDtoClassName();
        String result = e.searchResultDtoName() == null ? dto : e.searchResultDtoName();
        boolean page = Boolean.parseBoolean(c.varValue("searchPage"));

        c.addImport("org.springframework.stereotype.Controller");
        c.addImport("org.springframework.context.annotation.Import");
        c.addImport("org.springframework.graphql.data.method.annotation.Argument");
        c.addImport("org.springframework.graphql.data.method.annotation.QueryMapping");
        c.addImport("io.elasticore.springboot3.graphql.ElastiCoreGraphQlConfiguration");
        c.addImport("io.elasticore.springboot3.graphql.GraphQlDomain");
        c.addImport("%s.%s", c.varValue("servicePackageName"), c.varValue("serviceClassName"));
        c.addImport("%s.%s", e.pkg("dto"), dto);
        if (!result.equals(dto)) c.addImport("%s.%s", e.pkg("dto"), result);
        c.addImport("%s.%s", e.pkg("dto"), srch);
        // Runtime-visible, in Kotlin too: the schema writer reads it as a required argument.
        c.addImport("jakarta.validation.constraints.NotNull");
        if (page) c.addImport("org.springframework.data.domain.Page");
        else if (!kotlin) c.addImport("java.util.List");

        List<String> methods = new ArrayList<String>();
        String keyParams = keyParams(e, s);
        String keyArgs = keyArgs(e);
        String answer = (page ? "Page<%s>" : "List<%s>");

        if (!c.isMethodExcluded("read")) {
            methods.add(
                    kotlin
                            ? String.format(
                                    "    @QueryMapping(\"%s\")%n    fun %s(%s): %s? = service.findById(%s).orElse(null)",
                                    var, var, keyParams, dto, keyArgs)
                            : String.format(
                                    "    @QueryMapping(\"%s\")%n    public %s %s(%s) {%n        return service.findById(%s).orElse(null);%n    }",
                                    var, dto, var, keyParams, keyArgs));
        }
        if (!c.isMethodExcluded("search")) {
            String type = String.format(answer, result);
            String plural = plural(var);
            methods.add(
                    kotlin
                            ? String.format(
                                    "    @QueryMapping(\"%s\")%n    fun %s(@Argument(\"search\") @NotNull search: %s): %s = service.findBySearch(search)",
                                    plural, plural, srch, type)
                            : String.format(
                                    "    @QueryMapping(\"%s\")%n    public %s %s(@Argument(\"search\") @NotNull %s search) {%n"
                                            + "        return service.findBySearch(search);%n    }",
                                    plural, type, plural, srch));
        }
        boolean anyMutation = false;
        if (!c.isMethodExcluded("create")) {
            anyMutation = true;
            methods.add(write(kotlin, "create" + name, dto, "save"));
        }
        if (!c.isMethodExcluded("update")) {
            anyMutation = true;
            methods.add(write(kotlin, "update" + name, dto, "update"));
        }
        if (!c.isMethodExcluded("deleteById")) {
            anyMutation = true;
            c.addImport("io.elasticore.springboot3.validation.NotFoundException");
            String op = "delete" + name;
            methods.add(
                    kotlin
                            ? String.format(
                                    "    @MutationMapping(\"%s\")%n    fun %s(%s): Boolean {%n"
                                            + "        if (!service.findById(%s).isPresent) throw NotFoundException(\"%s not found\")%n"
                                            + "        return service.deleteById(%s)%n    }",
                                    op, op, keyParams, keyArgs, name, keyArgs)
                            : String.format(
                                    "    @MutationMapping(\"%s\")%n    public boolean %s(%s) {%n"
                                            + "        if (!service.findById(%s).isPresent()) throw new NotFoundException(\"%s not found\");%n"
                                            + "        return service.deleteById(%s);%n    }",
                                    op, op, keyParams, keyArgs, name, keyArgs));
        }
        // What the domain rules added: one mutation per command.
        String commands = c.varValue("graphqlCommands");
        if (commands != null && !commands.isEmpty() && !c.isMethodExcluded("commands")) {
            anyMutation = true;
            methods.add(commands);
        }
        if (anyMutation) {
            c.addImport("org.springframework.graphql.data.method.annotation.MutationMapping");
            c.addImport("jakarta.validation.Valid");
        }

        c.slot("methodList");
        for (int i = 0; i < methods.size(); i++) {
            if (i > 0) c.varLine("methodList", "");
            for (String line : RuleText.linesOf(methods.get(i).replace("\r\n", "\n")))
                c.varLine("methodList", "%s", line);
        }
        // The package is the runner's: the control namespace, else the service one.
        c.var("className", className(e))
                .var("entityClassName", name)
                .var("domainName", e.domainName())
                .var("importList", RuleText.stripTrailing(c.renderImports()));
    }

    /** create and update: the DTO, validated as the REST API validates its body. */
    private static String write(boolean kotlin, String op, String dto, String call) {
        return kotlin
                ? String.format(
                        "    @MutationMapping(\"%s\")%n    fun %s(@Argument(\"input\") @Valid @NotNull input: %s): %s? = service.%s(input)",
                        op, op, dto, dto, call)
                : String.format(
                        "    @MutationMapping(\"%s\")%n    public %s %s(@Argument(\"input\") @Valid @NotNull %s input) {%n"
                                + "        return service.%s(input);%n    }",
                        op, dto, op, dto, call);
    }

    /** The key as arguments, every @id field in order - a composite key is several. */
    static String keyParams(EntitySpec e, Syntax s) {
        StringBuilder out = new StringBuilder();
        for (FieldSpec f : e.pkFields()) {
            if (out.length() > 0) out.append(", ");
            if (s == Syntax.KOTLIN) {
                out.append(String.format(
                        "@Argument(\"%s\") @NotNull %s: %s", f.name(), f.name(), s.typeName(f.typeName())));
            } else {
                out.append(String.format("@Argument(\"%s\") @NotNull %s %s", f.name(), f.typeName(), f.name()));
            }
        }
        return out.toString();
    }

    static String keyArgs(EntitySpec e) {
        StringBuilder out = new StringBuilder();
        for (FieldSpec f : e.pkFields()) {
            if (out.length() > 0) out.append(", ");
            out.append(f.name());
        }
        return out.toString();
    }

    /** The operation names this entity's controller serves, for the run's check that they are one per schema. */
    public static Set<String> operationNames(EntitySpec e) {
        Set<String> out = new java.util.LinkedHashSet<String>();
        out.add(e.varName());
        out.add(plural(e.varName()));
        out.add("create" + e.getName());
        out.add("update" + e.getName());
        out.add("delete" + e.getName());
        return out;
    }
}
