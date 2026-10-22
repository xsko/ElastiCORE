package io.elasticore.base.model.pub.rules;

/**
 * Static entry point used inside rule methods:
 *
 *   ctx(e).field(f).ensure(AnnoCode.of("jakarta.persistence.Id"));
 */
public final class Ctx {

    private Ctx() {
    }

    public static SourceCode ctx(EntitySpec e) {
        SourceCode code = e.code();
        if (code == null) {
            throw new IllegalStateException(
                    "No SourceCode attached. Rules must run through RuleEngine.run().");
        }
        return code;
    }

    public static SourceCode ctx(DomainSpec d) {
        SourceCode code = d.code();
        if (code == null) {
            throw new IllegalStateException(
                    "No SourceCode attached. Rules must run through RuleEngine.run().");
        }
        return code;
    }

    public static SourceCode ctx(EnumSpec e) {
        SourceCode code = e.code();
        if (code == null) {
            throw new IllegalStateException(
                    "No SourceCode attached. Rules must run through RuleEngine.run().");
        }
        return code;
    }

    public static SourceCode ctx(PortSpec p) {
        SourceCode code = p.code();
        if (code == null) {
            throw new IllegalStateException(
                    "No SourceCode attached. Rules must run through RuleEngine.run().");
        }
        return code;
    }

    public static SourceCode ctx(ProtoServiceSpec s) {
        SourceCode code = s.code();
        if (code == null) {
            throw new IllegalStateException(
                    "No SourceCode attached. Rules must run through RuleEngine.run().");
        }
        return code;
    }
}
