package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ProtoServiceSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The gRPC stubs a proto port earns: the service implementation the DSL's
 * @server asks for, and the calling stub its @client asks for.
 *
 * Ported from ProtoSrcPublisher, which the rule pipeline had no counterpart of
 * - so until now neither language generated these, and a project that declared
 * a proto port got its .proto file and nothing to implement it with.
 *
 * The bodies go through the language seam like every other rule's, so the two
 * skeletons differ only in what a class and a method look like.
 */
public class GrpcStubRules implements CodeRules {

    /** The implementation stub, one method per rpc, left to the developer. */
    @Rule(value = "grpc-provider", target = "grpc_svc_provider", order = 10)
    void provider(ProtoServiceSpec p) {
        SourceCode c = ctx(p);
        if (!p.server()) {
            c.dontMake();
            return;
        }
        Syntax syntax = c.syntax();

        c.var("packageName", p.javaPackage())
         .var("gRpcServiceName", p.getName())
         .var("importList", String.format("%s.*", p.javaPackage()));

        for (ProtoServiceSpec.Rpc rpc : p.rpcs()) {
            describe(c, rpc);
            // The provider's methods override the generated ImplBase.
            c.varLine("gRpcMethodList", "%s {", syntax.methodSignature(
                    syntax.overriding(), null, rpc.name(),
                    String.format("%s, %s",
                            syntax.parameter(rpc.inputType(), "request"),
                            syntax.parameter(String.format("StreamObserver<%s>", rpc.outputType()),
                                    "responseObserver"))))
             .varLine("gRpcMethodList", "    // TODO: implement for gRPC service")
             .varLine("gRpcMethodList", "    %s", syntax.localVar(
                     String.format("%s.Builder", rpc.outputType()), "builder",
                     String.format("%s.newBuilder()", rpc.outputType())))
             .varLine("gRpcMethodList", "    %s", syntax.localVar(
                     rpc.outputType(), "response", "builder.build()"))
             .varLine("gRpcMethodList", "    %s", syntax.statement("responseObserver.onNext(response)"))
             .varLine("gRpcMethodList", "    %s", syntax.statement("responseObserver.onCompleted()"))
             .varLine("gRpcMethodList", "}")
             .varLine("gRpcMethodList", "");
        }
    }

    /** The calling stub, which ElastiCORE keeps managed. */
    @Rule(value = "grpc-client", target = "grpc_svc_client", order = 10)
    void client(ProtoServiceSpec p) {
        SourceCode c = ctx(p);
        if (!p.client()) {
            c.dontMake();
            return;
        }
        Syntax syntax = c.syntax();
        String prefix = String.format("grpc.%s", p.clientName());

        c.var("packageName", p.javaPackage())
                .var("gRpcServiceName", p.getName())
                .var("importList", String.format("%s.*", p.javaPackage()))
                // Written as the Spring placeholder the generated @Value reads, not
                // as a template variable of this generator.
                .var("gRpcServiceIp", placeholder(syntax, String.format("%s.ip:localhost", prefix)))
                .var("gRpcServicePort", placeholder(syntax, String.format("%s.port:9090", prefix)));

        for (ProtoServiceSpec.Rpc rpc : p.rpcs()) {
            describe(c, rpc);
            c.varLine("gRpcMethodList", "%s {", syntax.methodSignature("public", rpc.outputType(),
                    rpc.name(), syntax.parameter(rpc.inputType(), "request")))
             .varLine("gRpcMethodList", "    %s", syntax.localVar(
                     rpc.outputType(), "response",
                     String.format("blockingStub.%s(request)", rpc.name())))
             .varLine("gRpcMethodList", "    %s", syntax.statement("return response"))
             .varLine("gRpcMethodList", "}")
             .varLine("gRpcMethodList", "");
        }
    }

    /**
     * A Spring property placeholder as this language's string literal must
     * spell it: Kotlin reads "${...}" as its own string template, so there it
     * is escaped to reach Spring as written. Java's is as it always was.
     */
    private static String placeholder(Syntax syntax, String property) {
        return (syntax == Syntax.KOTLIN ? "\\" : "") + "${" + property + "}";
    }

    /** The rpc's own description, above the method it becomes. */
    private void describe(SourceCode c, ProtoServiceSpec.Rpc rpc) {
        String desc = rpc.description();
        if (desc == null || desc.isEmpty()) desc = rpc.name();
        c.varLine("gRpcMethodList", "/*")
         .varLine("gRpcMethodList", " * %s", desc)
         .varLine("gRpcMethodList", "*/");
    }
}
