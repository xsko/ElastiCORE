package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.DomainArtifactContributor;
import io.elasticore.base.model.pub.rules.DomainSpec;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The transactional outbox: a domain event leaves the service only if the
 * change that raised it was committed, and is not lost if the broker is down.
 *
 * Spring Data publishes an aggregate's events in-process, at save time. That
 * reaches listeners in the same JVM and nothing else; sending to a broker
 * from there either sends for a transaction that then rolls back or loses the
 * message when the send fails after the commit. So each event is written to
 * an outbox table in the same transaction (BEFORE_COMMIT), and a relay reads
 * the table afterwards and hands each row to the application's
 * {@code OutboxPublisher}, marking it published only when that succeeds.
 *
 * Per domain, when env.yml says {@code outbox: true} and some root declares
 * events: the message entity, its repository, the recorder and the relay.
 * All four are regenerated; the publisher is the application's.
 */
public class DddOutboxRules implements CodeRules, DomainArtifactContributor {

    @Override
    public List<ContributedArtifact> artifactsFor(DomainSpec d) {
        if (!d.isOn("outbox") || events(d).isEmpty()) return Collections.emptyList();
        List<ContributedArtifact> out = new ArrayList<ContributedArtifact>();
        // All four are infrastructure: the outbox is how events leave the
        // service, an adapter concern. Placed with the persistence adapters,
        // they depend inward (on the events) and nothing inside depends on
        // them - the application and the domain never name the outbox.
        String ns = placement(d);
        out.add(ContributedArtifact.of("outboxMessage", ns, messageName(d)).orNamespace("entity"));
        out.add(ContributedArtifact.of("outboxRepository", ns, repositoryName(d)).orNamespace("entity"));
        out.add(ContributedArtifact.of("outboxRecorder", ns, recorderName(d)).orNamespace("entity"));
        out.add(ContributedArtifact.of("outboxRelay", ns, relayName(d)).orNamespace("entity"));
        return out;
    }

    /** One declared event of one root. */
    static final class Declared {
        final EntitySpec root;
        final String eventClass;

        Declared(EntitySpec root, String eventClass) {
            this.root = root;
            this.eventClass = eventClass;
        }
    }

    static List<Declared> events(DomainSpec d) {
        List<Declared> out = new ArrayList<Declared>();
        for (EntitySpec e : d.entities()) {
            for (String event : DddDomainEventRules.declaredEvents(e)) {
                out.add(new Declared(e, DddDomainEventRules.className(e, event)));
            }
        }
        return out;
    }

    @Rule(value = "ddd-outbox-message", target = "outboxMessage", order = 10)
    void message(DomainSpec d) {
        SourceCode c = ctx(d);
        c.requireSyntaxAnyOf("ddd-outbox", Syntax.JAVA, Syntax.KOTLIN);
        // Over MyBatis the message is a plain class, its table in the schema the domain's tables are.
        if (io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.applies(d)) {
            c.template("mybatis_outboxMessage");
            io.elasticore.base.model.pub.rules.mybatis.MyBatisTables.outboxOf(d.getName(),
                    tableName(d.config("table.prefix"), d.className()));
        }
        c.var("packageName", pkgOr(d, "persistence"))
         .var("className", messageName(d))
         .var("domainName", d.className())
         .var("tableName", tableName(d.config("table.prefix"), d.className()));
    }

    /**
     * The outbox's table. Each domain its own: with a prefix it is the
     * domain's already, without one two domains would map two entities onto
     * one table.
     */
    public static String tableName(String prefix, String domainClassName) {
        return prefix != null && !prefix.isEmpty() ? prefix + "outbox"
                : io.elasticore.base.util.StringUtils.camelToSnake(domainClassName) + "_outbox";
    }

    /** Whether a domain writes an outbox: outbox: true, and a root of it declares events. */
    public static boolean writesOutbox(String outboxConfig, List<EntitySpec> entities) {
        if (!"true".equalsIgnoreCase(outboxConfig == null ? "" : outboxConfig.trim())) return false;
        for (EntitySpec e : entities) {
            if (!DddDomainEventRules.declaredEvents(e).isEmpty()) return true;
        }
        return false;
    }

    @Rule(value = "ddd-outbox-repository", target = "outboxRepository", order = 10)
    void repository(DomainSpec d) {
        SourceCode c = ctx(d);
        // Over MyBatis a mapper with the same method names, its SQL locking per database.
        if (io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.applies(d)) c.template("mybatis_outboxRepository");
        c.var("packageName", pkgOr(d, "repository"))
         .var("className", repositoryName(d))
         .var("messageName", messageName(d))
         .var("tableName", tableName(d.config("table.prefix"), d.className()))
         .var("messageImport", "");
    }

    @Rule(value = "ddd-outbox-recorder", target = "outboxRecorder", order = 10)
    void recorder(DomainSpec d) {
        SourceCode c = ctx(d);
        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        // Spring Boot 4 configures a Jackson 3 JsonMapper and no Jackson 2
        // ObjectMapper: the Jackson 2 skeleton would quietly fall back to a
        // mapper of its own and write events unlike the rest of the API.
        if (io.elasticore.base.model.pub.rules.RulesConfig.jackson3()) c.template("outboxRecorderJackson3");
        String pkg = pkgOr(d, "service");
        List<String> imports = new ArrayList<String>();

        for (Declared ev : events(d)) {
            addImport(imports, c, DddDomainEventRules.eventPackage(ev.root), ev.eventClass, pkg);
            String id = aggregateId(ev.root, kotlin);
            if (kotlin) {
                c.varLine("handlerList", "@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)")
                 .varLine("handlerList", "fun on(event: %s) = record(\"%s\", %s, event, event.occurredOn)",
                         ev.eventClass, ev.root.getName(), id)
                 .varLine("handlerList", "");
            } else {
                c.varLine("handlerList", "@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)")
                 .varLine("handlerList", "public void on(%s event) {", ev.eventClass)
                 .varLine("handlerList", "    record(\"%s\", %s, event, event.getOccurredOn());", ev.root.getName(), id)
                 .varLine("handlerList", "}")
                 .varLine("handlerList", "");
            }
        }
        c.var("packageName", pkg)
         .var("className", recorderName(d))
         .var("messageName", messageName(d))
         .var("repositoryName", repositoryName(d))
         .varLines("importList", imports);
    }

    @Rule(value = "ddd-outbox-relay", target = "outboxRelay", order = 10)
    void relay(DomainSpec d) {
        SourceCode c = ctx(d);
        // Over MyBatis the relay writes each message it changed; nothing tracks it.
        if (io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.applies(d)) c.template("mybatis_outboxRelay");
        String pkg = pkgOr(d, "service");
        List<String> imports = new ArrayList<String>();
        c.var("packageName", pkg)
         .var("className", relayName(d))
         .var("messageName", messageName(d))
         .var("repositoryName", repositoryName(d))
         // Spring placeholders, handed in as values so the template engine
         // does not read them as its own slots.
         .var("relayDelay", placeholder(c, "elasticore.outbox.relay-delay-ms:5000"))
         .var("purgeDelay", placeholder(c, "elasticore.outbox.purge-delay-ms:3600000"))
         .var("maxAttemptsProperty", placeholder(c, "elasticore.outbox.max-attempts:10"))
         .var("retentionProperty", placeholder(c, "elasticore.outbox.retention-days:7"))
         .varLines("importList", imports);
    }

    /**
     * A Spring property placeholder as this language's string literal must
     * spell it: Kotlin reads "${...}" as its own string template, so there
     * it is escaped to reach Spring as written.
     */
    private static String placeholder(SourceCode c, String property) {
        return (c.syntax() == Syntax.KOTLIN ? "\\" : "") + "${" + property + "}";
    }

    /** The aggregate's identity as the event carries it, as one string. */
    private static String aggregateId(EntitySpec root, boolean kotlin) {
        List<FieldSpec> pks = root.pkFields();
        StringBuilder sb = new StringBuilder();
        for (FieldSpec f : pks) {
            if (sb.length() > 0) sb.append(kotlin ? ", " : " + \":\" + ");
            sb.append(kotlin ? "event." + f.name() : "event.get" + f.capitalName() + "()");
        }
        if (pks.size() == 1) return sb.toString();
        // Kotlin has no Long + String; the parts are joined as a list instead.
        return kotlin ? "listOf(" + sb + ").joinToString(\":\")" : "(" + sb + ")";
    }

    /** The namespace key the outbox goes to: the persistence adapters', else the repositories'. */
    static String placement(DomainSpec d) {
        String persistence = d.pkg("persistence");
        return persistence != null && !persistence.isEmpty() ? "persistence" : "repository";
    }

    /** The package the outbox's files are written to. */
    private static String pkgOr(DomainSpec d, String key) {
        String pkg = d.pkg(placement(d));
        return pkg == null || pkg.isEmpty() ? d.pkg("entity") : pkg;
    }

    private static void addImport(List<String> imports, SourceCode c, String pkg, String name, String own) {
        String line = importLine(c, pkg, name, own);
        if (!line.isEmpty() && !imports.contains(line)) imports.add(line);
    }

    private static String importLine(SourceCode c, String pkg, String name, String own) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(own)) return "";
        return String.format(c.syntax() == Syntax.KOTLIN ? "import %s.%s" : "import %s.%s;", pkg, name);
    }

    static String messageName(DomainSpec d) { return d.className() + "OutboxMessage"; }

    static String repositoryName(DomainSpec d) { return d.className() + "OutboxRepository"; }

    static String recorderName(DomainSpec d) { return d.className() + "OutboxRecorder"; }

    static String relayName(DomainSpec d) { return d.className() + "OutboxRelay"; }
}
