package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.ArtifactContributor;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.Collections;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * {@code @signin(refresh=request)}: a signed-in session follows its row.
 *
 * <pre>
 * Customer:
 *   meta: entity @root @expose(20) @signin(refresh=request) ...
 * </pre>
 *
 * The sign-in puts the row's key and role in the session, and without this
 * the session keeps them until the next sign-in. A role the administrator
 * changes waits for that sign-in, and a deleted member keeps a working
 * session.
 *
 * With it, the entity gets {@code CustomerSessionRefresh}, a filter beside
 * its controller (the runtime's SessionRefreshFilter). Before each request
 * whose session holds a signed-in key, the filter reads the row again
 * through the service's me(), the same read GET /me makes, so a row kept
 * under @deleted counts as gone:
 *
 * <ul>
 *   <li>gone: the session ends, and the request goes on as a visitor's;</li>
 *   <li>a different role: the session takes it and keeps its id.</li>
 * </ul>
 *
 * A file of its own rather than code in the controller. The controller is
 * written once and is the developer's from then on, so a project whose
 * controller is already its own would never get a filter added there. This
 * file is regenerated with the model, like the CoreService that has me().
 *
 * It is opt-in, because it costs one read of the row per request.
 */
public class SignInRefreshRules implements CodeRules, ArtifactContributor {

    /** One filter, for the entity that signs in and asks for it. */
    @Override
    public List<ContributedArtifact> artifactsFor(EntitySpec e) {
        if (!ServiceAccessRules.refreshesPerRequest(e)) return Collections.emptyList();
        return Collections.singletonList(ContributedArtifact.of("sessionRefresh", "control", className(e))
                .orNamespace("service"));
    }

    static String className(EntitySpec e) {
        return e.getName() + "SessionRefresh";
    }

    /**
     * The filter: the service it reads through, and the role as the sign-in
     * puts it in the session. A hexagonal bundle injects the inbound port
     * instead (hex-usecase-refresh), as it does for the controller.
     */
    @Rule(value = "spring-signin-refresh", target = "sessionRefresh", order = 10)
    void sessionRefresh(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntaxAnyOf("spring-signin-refresh", Syntax.JAVA, Syntax.KOTLIN);
        boolean kt = c.syntax() == Syntax.KOTLIN;

        String pkg = e.pkg("control");
        if (pkg == null || pkg.isEmpty()) pkg = e.pkg("service");
        String role = ServiceAccessRules.roleText(e, kt, "signedIn");
        // refresh=password: the runtime's PasswordRefreshFilter, which reads the roles and the stamp in one read.
        if (ServiceAccessRules.refreshesWithPassword(e)) c.template("sessionRefreshPassword");

        c.var("packageName", pkg)
                .var("className", className(e))
                .var("entityName", e.getName())
                .var("serviceClassName", "%sService", e.getName())
                .var("servicePackageName", e.pkg("service"))
                .var("rolesExpression", "SessionRefreshFilter.roles(%s)", role == null ? "" : role);
    }
}
