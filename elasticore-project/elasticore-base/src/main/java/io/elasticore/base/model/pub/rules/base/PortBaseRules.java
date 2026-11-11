package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.PortSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.SourceCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Base rules for a transaction port, ported from PortSrcPublisher: an
 * interface whose implementation ElastiCORE supplies at runtime.
 *
 * What the port reaches is declared on the interface - an HTTP service with
 * its url and timeouts, or a database with its datasource and sql source -
 * and an HTTP operation additionally declares the endpoint it calls.
 *
 * A proto port is a different artifact and is not produced here.
 */
public class PortBaseRules implements CodeRules {

    private static final String DEFAULT_CONTENT_TYPE = "application/json";

    @Rule(value = "port-vars", target = "port", order = 10)
    void vars(PortSpec p) {
        if ("proto".equalsIgnoreCase(p.protocol())) {
            ctx(p).dontMake();
            return;
        }

        // A port with nothing to call is not a port. 2.x reaches the same
        // conclusion by assembling the body first and returning when it is
        // empty (PortSrcPublisher.publish).
        if (p.operations().isEmpty()) {
            ctx(p).dontMake();
            return;
        }

        SourceCode c = ctx(p);
        if (p.springHttp()) {
            // A Spring HTTP service client (httpPort: spring, Spring Boot 4).
            c.template("portSpring")
             .var("packageName", p.pkg("port"))
             .var("entityPackageName", p.pkg("entity"))
             .var("dtoPackageName", p.pkg("dto"))
             .var("className", p.getName())
             .var("description", p.description() == null || p.description().isEmpty() ? p.getName() : p.description())
             .var("groupName", SpringHttpPorts.group(p))
             .var("configClassName", SpringHttpPorts.configClassName(p.domainClassName()));
            SpringHttpPorts.operations(c, p);
            if (p.pkg("enumeration") != null) c.var("enumPackageName", p.pkg("enumeration"));
            return;
        }
        c.var("packageName", p.pkg("port"))
         .var("entityPackageName", p.pkg("entity"))
         .var("dtoPackageName", p.pkg("dto"))
         .var("j2eePkgName", "jakarta")
         .var("className", p.getName())
         .var("description", p.description() == null ? "" : p.description())
         .var("classAnnotationList", serviceAnnotation(p));
        operations(c, p);
        // A method that takes the page or the order it asks for names Spring Data's type.
        // The 2.x skeleton imports neither; its output stays as it was.
        if (!c.compatible()) {
            String paging = pagingImports(p, c.syntax() == Syntax.KOTLIN ? "" : ";");
            if (!paging.isEmpty()) c.var("importList", paging);
        }

        if (p.pkg("enumeration") != null) c.var("enumPackageName", p.pkg("enumeration"));
    }

    /** Pageable and Sort, as the methods' parameters name them without a package: the import line's text. */
    static String pagingImports(PortSpec p, String end) {
        List<String> types = new ArrayList<String>();
        for (PortSpec.Operation op : p.operations()) {
            for (String[] param : op.params()) {
                String type = PortSpec.Operation.typeOf(param).trim();
                if (("Pageable".equals(type) || "Sort".equals(type)) && !types.contains(type)) types.add(type);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String type : types) {
            if (sb.length() > 0) sb.append(end).append("\nimport ");
            sb.append("org.springframework.data.domain.").append(type);
        }
        return sb.toString();
    }

    /**
     * What the port connects to. An HTTP port names its url and whatever
     * timeouts were declared; a database port names its sql source, and its
     * datasource when one was chosen.
     *
     * These two are written out rather than declared through AnnoCode, as the
     * endpoint below is: 2.x separates their attributes inconsistently - a
     * plain ", " after the first, then " ," before each of the rest - and a
     * builder has one separator. Reproducing that irregularity is the point
     * of the compatibility mode, so the text stays literal here.
     */
    private String serviceAnnotation(PortSpec p) {
        if ("dbms".equalsIgnoreCase(p.protocol())) {
            if (p.datasource() != null) {
                return String.format("@DbmsService(id=%s ,datasource=%s ,sqlSource=%s)",
                        RuleText.quote(p.id()), RuleText.quote(p.datasource()), RuleText.quote(p.sqlSource()));
            }
            return String.format("@DbmsService(id=%s ,sqlSource=%s)",
                    RuleText.quote(p.id()), RuleText.quote(p.sqlSource()));
        }

        if (!"http".equalsIgnoreCase(p.protocol()) || p.url() == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("@ExternalService(protocol=\"http\", id=%s ,url=%s",
                RuleText.quote(p.id()), RuleText.quote(p.url())));
        if (p.authKey() != null && !p.authKey().isEmpty()) {
            sb.append(String.format(", authKey=%s", RuleText.quote(p.authKey())));
        }
        for (Map.Entry<String, Integer> t : p.timeouts().entrySet()) {
            sb.append(String.format(", %s=%s", t.getKey(), t.getValue()));
        }
        sb.append(")");
        return sb.toString();
    }

    /** Each operation: its comment, its endpoint if it has one, its signature. */
    private void operations(SourceCode c, PortSpec p) {
        for (PortSpec.Operation op : p.operations()) {
            c.varLine("methodList", "/*")
             .varLine("methodList", " * %s", op.description())
             .varLine("methodList", "*/");

            if (op.url() != null) {
                c.varLine("methodList", AnnoCode.of("io.elasticore.runtime.port.HttpEndpoint")
                        .compact()
                        .val("url", op.url())
                        .val("method", op.httpMethod())
                        .val("contentType", op.contentType(), DEFAULT_CONTENT_TYPE)
                        .val("paramNames", paramNames(op))
                        .render());
            }

            c.varLine("methodList", "%s", c.syntax().statement(
                            // The runtime hands back the response body as it
                            // came, and an empty response has none - so what a
                            // port returns is a value that may be absent.
                            c.syntax().methodSignature("",
                                    c.syntax().nullableType(op.returnType()), op.name(),
                                    signature(c.syntax(), op))))
             .varLine("methodList", "");
        }
    }

    /** The parameter names the runtime binds the call's arguments by. */
    private static String paramNames(PortSpec.Operation op) {
        StringBuilder sb = new StringBuilder();
        for (String[] param : op.params()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(param[1]);
        }
        return sb.toString();
    }

    private static String signature(Syntax syntax, PortSpec.Operation op) {
        StringBuilder sb = new StringBuilder();
        for (String[] param : op.params()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(syntax.parameter(param[0], param[1]));
        }
        return sb.toString();
    }

}
