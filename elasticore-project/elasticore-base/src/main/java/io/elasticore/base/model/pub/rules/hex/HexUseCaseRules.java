package io.elasticore.base.model.pub.rules.hex;

import io.elasticore.base.model.pub.rules.ArtifactContributor;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.Ctx;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.base.RuleText;

import java.util.Collections;
import java.util.List;

/**
 * The inbound port of a hexagonal architecture: what a driving adapter may
 * ask of the application, stated as an interface.
 *
 * The first consumer of the artifact extension point - a rule set declares
 * an artifact the runner has never heard of, and the runner emits it like
 * any built-in.
 *
 * The interface mirrors the entity's application service surface, and the
 * wiring lives here with it: the extension service implements the interface
 * and the controller injects it instead of the concrete class. All three
 * changes are one statement - "the application is entered through its
 * ports" - so they belong to one rule set, switched on and off together.
 *
 * The surface is built through {@link Syntax} rather than written out, so
 * the port says in Kotlin exactly what it says in Java - including the
 * nullability the Kotlin service declares, which is part of the signature
 * there and therefore part of whether the claim compiles at all.
 */
public class HexUseCaseRules implements CodeRules, ArtifactContributor {

    @Override
    public List<ContributedArtifact> artifactsFor(EntitySpec e) {
        // The same shape the controller has: only an exposed root with a key
        // has an application surface to state. A member's use cases are its
        // root's - giving it an inbound port would hand back the endpoint
        // the aggregate rules took away.
        if (!applies(e)) {
            return Collections.emptyList();
        }
        // A namespace of its own, falling back to the service's.
        //
        // The two directions were asymmetric until this: an outbound port -
        // a dbms or http declaration - had "port" and landed in
        // application.port.out, while the inbound port had nothing and landed
        // among the services that implement it. A hexagon whose two sides are
        // named differently is one whose diagram nobody can draw from the
        // package list.
        return Collections.singletonList(ContributedArtifact
                .of("usecase", "inboundPort", String.format("%sUseCase", e.getName()))
                .orNamespace("service"));
    }

    /** Where the inbound port goes: its own namespace, or the service's. */
    public static String inboundPortPackage(EntitySpec e) {
        String pkg = e.pkg("inboundPort");
        return (pkg == null || pkg.isEmpty()) ? e.pkg("service") : pkg;
    }

    /**
     * The service is what stands behind the port, so it says so.
     *
     * The base skeleton leaves the clause empty - a slot nobody fills
     * renders as nothing, which is what keeps every non-hexagonal project's
     * file byte-identical. The methods already exist on the core service the
     * extension extends; this line is what makes the compiler check that
     * claim on every build.
     */
    @Rule(value = "hex-usecase-impl", target = "service_extend", order = 20)
    void serviceImplementsThePort(EntitySpec e) {
        if (!applies(e)) return;
        SourceCode c = Ctx.ctx(e);
        // Java extends a class and implements an interface with a word
        // between them; Kotlin lists both after one colon, so the clause
        // continues a list that has already started.
        // Written in full when the port is not in the service's package.
        //
        // The extension skeleton has a fixed import block rather than a slot,
        // and adding one would put a blank line into every v3 project's file
        // to serve the projects that separate the two directions. Naming the
        // type in full costs those projects nothing and says the same thing.
        String clause = c.syntax() == Syntax.KOTLIN ? ", %s" : " implements %s";
        c.var("implementsClause", clause, useCaseType(e, e.pkg("service")));
    }

    /**
     * The driving adapter depends on the port, not on the application class.
     *
     * Overwrites the injected type the base rule chose at order 10. The
     * variable names the type in one place, so the field, the constructor
     * Lombok writes for it and every call site follow.
     */
    @Rule(value = "hex-usecase-inject", target = "control", order = 20)
    void controllerInjectsThePort(EntitySpec e) {
        if (!applies(e)) return;

        SourceCode c = Ctx.ctx(e);
        c.var("serviceClassName", "%sUseCase", e.getName());

        // The skeleton builds the import as package plus class, so the port's
        // package goes in the package half. Putting a qualified name in the
        // class half - which is what the service extension needs, since its
        // import block is fixed - produced an import of one package glued to
        // another. Two slots, two answers.
        String portPkg = inboundPortPackage(e);
        if (portPkg != null && !portPkg.isEmpty()) {
            c.var("servicePackageName", portPkg);
        }
    }

    /**
     * The inbound port's name as the file naming it has to write it.
     *
     * Simple where the two share a package, which is every project that has
     * not separated the directions; qualified where they do not.
     */
    private String useCaseType(EntitySpec e, String fromPackage) {
        String simple = String.format("%sUseCase", e.getName());
        String portPkg = inboundPortPackage(e);

        if (portPkg == null || portPkg.isEmpty() || portPkg.equals(fromPackage)) {
            return simple;
        }
        return portPkg + "." + simple;
    }

    private boolean applies(EntitySpec e) {
        return e.hasMeta("expose") && e.pkField() != null && e.aggregateRoot();
    }

    /**
     * The whole file. A contributed target has no built-in file shell at
     * order 90 the way entity or dto do, so the rule that declares the
     * artifact also renders it - which is the arrangement anyway: the
     * runner knows where the file goes, and only this set knows what it is.
     */
    @Rule(value = "hex-usecase", target = "usecase", order = 10)
    void shell(EntitySpec e) {
        SourceCode c = Ctx.ctx(e);
        c.requireSyntaxAnyOf("hex-usecase", Syntax.JAVA, Syntax.KOTLIN);

        Syntax s = c.syntax();
        String name = e.getName();
        String dto = String.format("%sDTO", name);
        String keyDto = String.format("%sKeyDTO", name);
        String srchDto = e.searchDtoClassName();

        c.addImport("java.util.Optional");
        c.addImport("org.springframework.data.domain.Page");
        c.addImport("%s.%s", e.pkg("dto"), dto);
        c.addImport("%s.%s", e.pkg("dto"), keyDto);
        // The search DTO is placed by the dto namespace, same as the KeyDTO.
        c.addImport("%s.%s", e.pkg("dto"), srchDto);
        // Kotlin's List is its own and needs no import; Java's does.
        if (s == Syntax.JAVA) c.addImport("java.util.List");

        // The surface is the one the generated service already offers -
        // same names, same shapes - so that "the service implements the
        // port" is a fact the compiler establishes rather than a hope.
        String resultDto = e.searchResultDtoName();
        if (resultDto == null) resultDto = dto;

        // A read-only entity's service is missing every write method, so a
        // port that named them would promise what nothing behind it can do.
        boolean writes = !e.hasMeta("readonly");
        // And a method @restExclude took off the service is not promised
        // either: the use case kept it, and "XService implements XUseCase"
        // stopped compiling.
        java.util.Set<String> gone = io.elasticore.base.model.pub.rules.base.ControllerBaseRules.restExcluded(e, true);
        for (String id : new String[]{"save", "update", "batchUpdate", "delete", "deleteById"}) {
            if (c.isMethodExcluded(id)) gone.add(id);
        }

        java.util.List<String> methods = new java.util.ArrayList<String>();
        if (writes && !gone.contains("save")) {
            methods.add(method(s, s.nullableType(dto), "save", s.parameter(dto, "dto")));
        }
        methods.add(method(s, String.format("Optional<%s>", dto), "findById", pkParams(s, e)));
        methods.add(method(s, String.format("Page<%s>", resultDto), "findBySearch",
                s.parameter(srchDto, "searchDTO")));
        if (writes && !gone.contains("update")) {
            methods.add(method(s, s.nullableType(dto), "update", s.parameter(dto, "dto")));
        }
        if (writes && !gone.contains("batchUpdate")) {
            methods.add(method(s, null, "batchUpdate",
                    s.parameter(s.nullableType(String.format("List<%s>", dto)), "dtoList")));
        }
        if (writes && !gone.contains("delete")) {
            methods.add(method(s, "List<Boolean>", "delete",
                    s.parameter(String.format("List<%s>", keyDto), "dtoList")));
        }
        if (writes && !gone.contains("deleteById")) {
            methods.add(method(s, "boolean", "deleteById", pkParams(s, e)));
        }
        // Signing in, where the DSL marks a @login and a @password - and who is
        // signed in, for the entity the API signs in through (@signin).
        String login = io.elasticore.base.model.pub.rules.base.ServiceAccessRules.loginField(e);
        if (login != null) {
            methods.add(method(s, String.format("Optional<%s>", dto), "authenticate",
                    s.parameter(s.nullableType("String"), login) + ", " + s.parameter(s.nullableType("String"), "password")));
            if (io.elasticore.base.model.pub.rules.base.ServiceAccessRules.signsIn(e)) {
                methods.add(method(s, String.format("Optional<%s>", dto), "me", ""));
            }
        }

        for (int i = 0; i < methods.size(); i++) {
            if (i > 0) c.varLine("methodList", "");
            c.varLine("methodList", "%s", methods.get(i));
        }

        c.var("packageName", inboundPortPackage(e))
         .var("className", String.format("%sUseCase", name))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", RuleText.stripTrailing(c.renderImports()));
    }

    /** One declaration, in the language being written. */
    private String method(Syntax s, String returnType, String name, String params) {
        return s.statement(s.methodSignature("", returnType, name, params));
    }

    /**
     * The identifier as the service takes it: every @id field, in order, and
     * nullable where the service declares it so. A composite key is passed
     * as separate parameters there too.
     */
    private String pkParams(Syntax s, EntitySpec e) {
        StringBuilder out = new StringBuilder();
        for (FieldSpec f : e.pkFields()) {
            if (out.length() > 0) out.append(", ");
            out.append(s.parameter(s.nullableType(f.typeName()), f.name()));
        }
        return out.toString();
    }
}
