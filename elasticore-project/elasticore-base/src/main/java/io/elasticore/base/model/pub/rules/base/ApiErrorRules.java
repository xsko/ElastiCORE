package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.DomainArtifactContributor;
import io.elasticore.base.model.pub.rules.DomainSpec;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.Collections;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * What the generated API answers when a request is refused.
 *
 * The services now refuse for reasons a client can act on - the row is not
 * there, the identifier is taken, someone else changed it first, a value
 * breaks its constraint, the aggregate will not be put in that state - and
 * without a handler every one of them reached the client as the same 500.
 * A status that does not say which of them happened is one the client can
 * only retry.
 *
 * <pre>
 *   EntityNotFoundException              404  the row is not there
 *   DuplicateKeyException                409  a create named a taken identifier
 *   OptimisticLockingFailureException    409  the version sent is not the row's
 *   ConstraintViolationException         400  a value breaks its constraint
 *   InvariantViolationException (cause)  409  a step the state does not allow (state.*)
 *                                        404  a row the request refers to is not there (reference.*)
 *                                        422  any other rule the aggregate refuses
 *   PortCallException                    502  a system this one called failed it
 * </pre>
 *
 * One file per domain that exposes anything, beside its controllers and
 * scoped to their package, so it answers for them and for nothing else in
 * the application. Generate-once: what the body of an error looks like is a
 * house style, and the house is the developer's.
 *
 * What Spring MVC refuses before any controller runs (a body that is not
 * JSON, a value of the wrong type, a missing parameter, 404/405/406/415) is
 * answered by the runtime's RequestShapeAdvice, which the RequestShapeErrors the file imports registers: one
 * advice for the whole application, so the handler bodies are not frozen into
 * every generated file, and no auto-configuration changes an application that
 * does not import it.
 */
public class ApiErrorRules implements CodeRules, DomainArtifactContributor {

    @Override
    public List<ContributedArtifact> artifactsFor(DomainSpec domain) {
        if (!exposesAnything(domain)) return Collections.emptyList();
        return Collections.singletonList(
                ContributedArtifact.of("apiErrors", "control", className(domain)).orNamespace("service"));
    }

    @Rule(value = "spring-api-errors", target = "apiErrors", order = 10)
    void apiErrors(DomainSpec d) {
        SourceCode c = ctx(d);
        c.requireSyntaxAnyOf("spring-api-errors", Syntax.JAVA, Syntax.KOTLIN);

        String pkg = d.pkg("control");
        if (pkg == null || pkg.isEmpty()) pkg = d.pkg("service");

        c.var("packageName", pkg)
         .var("className", className(d))
         .var("domainName", d.className())
         // The bundle the runner writes beside it: i18n/<domain>-messages.properties.
         .var("bundleName", "i18n/" + d.getName() + "-messages")
         // Spring Data moved it between the Boot 3 and Boot 4 lines.
         .var("propertyReferenceException", io.elasticore.base.model.pub.rules.RulesConfig.springBootMajor() >= 4
                 ? "org.springframework.data.core.PropertyReferenceException"
                 : "org.springframework.data.mapping.PropertyReferenceException");
    }

    private static boolean exposesAnything(DomainSpec domain) {
        for (EntitySpec e : domain.entities()) {
            if (e.hasMeta("expose") && e.pkField() != null) return true;
        }
        return false;
    }

    static String className(DomainSpec d) {
        return d.className() + "ApiErrors";
    }
}
