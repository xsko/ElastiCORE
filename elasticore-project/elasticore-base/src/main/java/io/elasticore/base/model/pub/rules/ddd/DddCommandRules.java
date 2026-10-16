package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.EntitySpec.CommandSpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.base.RuleText;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Business commands: what an aggregate is asked to do, declared in the DSL.
 *
 * <pre>
 *   SalesOrder:
 *     meta: entity @root @expose(20)
 *     commands:
 *       confirm:
 *       ship:
 *         warehouse: string(20)!
 *       cancel:
 * </pre>
 *
 * A CRUD API answers "change these fields"; a domain is asked "confirm this
 * order". Without a place for the second, every command was a use case, a
 * service method and an endpoint written by hand beside the generated CRUD -
 * two ways into the same aggregate. A command declared here is one way in,
 * from the edge to the rule:
 *
 * <pre>
 *   POST /api/erp/salesOrder/{orderId}/ship      controller     (new file: generated)
 *   SalesOrderShipCommand { warehouse }          DTO, validated like any other
 *   SalesOrderUseCase.ship(orderId, command)     inbound port   (regenerated)
 *   SalesOrderCoreService.ship(...)              load, call, save, in a transaction
 *   SalesOrderBase: abstract void ship(String)   the promise    (regenerated)
 *   SalesOrder.ship(String)                      the rule       (yours)
 * </pre>
 *
 * The rule itself is not generated: the base declares the method abstract,
 * so a command added to the DSL and not yet written is a compile error rather
 * than a gap found in production. The behaviour class gets a stub that throws
 * the first time it is written, for the same reason the factory's does.
 *
 * A command needs a class to be written in, so it is for a root the
 * behaviour rules split - which, with these rule sets, is every root that is
 * not @editable. Declared anywhere else it is reported and ignored.
 */
public class DddCommandRules implements CodeRules, io.elasticore.base.model.pub.rules.CommandOnlyRoots {

    private static final java.util.Set<String> WARNED =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /** Whether the entity's commands can be generated at all. */
    private static boolean applies(EntitySpec e) {
        if (e.commands().isEmpty()) return false;
        if (!DddBehaviorRules.splits(e) || e.pkFields().size() != 1) {
            if (WARNED.add(e.domainName() + "." + e.getName())) {
                io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                        "[%s] %s declares commands, but it has no behaviour class to write them in"
                                + " (a root that is not @editable, with a single key). The commands are ignored.",
                        e.domainClassName(), e.getName()));
            }
            return false;
        }
        return true;
    }

    // ---- the aggregate ----------------------------------------------------

    /** The promise: one abstract method per command, after the creation writer (96). */
    @Rule(value = "ddd-command-abstract", target = "entity", order = 97, phase = Rule.Phase.TAIL)
    void abstractMethods(EntitySpec e) {
        if (!applies(e)) return;
        SourceCode c = ctx(e);
        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        for (CommandSpec cmd : e.commands()) {
            warnEntityParams(e, cmd);
            // A command the states block moves is written, not promised (DddStateRules).
            if (DddStateRules.writesMove(e, cmd.name())) continue;
            c.varLine("fieldList", "")
             .varLine("fieldList", "/** The %s command, declared in the DSL. %s's rule; written in %s. */",
                     cmd.name(), e.getName(), e.getName());
            if (kotlin) {
                c.varLine("fieldList", "abstract fun %s(%s)", cmd.name(), params(c, cmd, e));
            } else {
                c.varLine("fieldList", "public abstract void %s(%s);", cmd.name(), params(c, cmd, e));
            }
        }
    }

    /**
     * The place to keep it, written once with the behaviour class. A stub
     * that throws: one that quietly did nothing would reach production.
     * After the class shell (10).
     */
    @Rule(value = "ddd-command-stub", target = "behavior", order = 20)
    void stubs(EntitySpec e) {
        if (!applies(e)) return;
        SourceCode c = ctx(e);
        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        for (CommandSpec cmd : e.commands()) {
            if (DddStateRules.writesMove(e, cmd.name())) continue;
            c.varLine("methodList", "");
            c.varLine("methodList", "    /** The %s command. Check what must hold, change the state, raise the event. */",
                    cmd.name());
            if (kotlin) {
                c.varLine("methodList", "    override fun %s(%s) {", cmd.name(), params(c, cmd, e))
                 .varLine("methodList", "        TODO(\"%s.%s: write the rule\")", e.getName(), cmd.name())
                 .varLine("methodList", "    }");
            } else {
                c.varLine("methodList", "    @Override")
                 .varLine("methodList", "    public void %s(%s) {", cmd.name(), params(c, cmd, e))
                 .varLine("methodList", "        throw new UnsupportedOperationException(\"%s.%s: write the rule\");",
                         e.getName(), cmd.name())
                 .varLine("methodList", "    }");
            }
        }
    }

    // ---- the application --------------------------------------------------

    /** The inbound port says it too. After the port's own surface (10). */
    @Rule(value = "ddd-command-usecase", target = "usecase", order = 20)
    void useCase(EntitySpec e) {
        if (!applies(e)) return;
        SourceCode c = ctx(e);
        Syntax s = c.syntax();
        String dto = String.format("%sDTO", e.getName());
        for (CommandSpec cmd : e.commands()) {
            if (cmd.dtoName() != null) c.addImport("%s.%s", e.pkg("dto"), cmd.dtoName());
            c.varLine("methodList", "")
             .varLine("methodList", "%s", s.statement(s.methodSignature("", s.nullableType(dto), cmd.name(),
                     serviceParams(e, cmd, s))));
        }
        c.var("importList", RuleText.stripTrailing(c.renderImports()));
    }

    /**
     * The transaction around the rule: load the aggregate, ask it, save it.
     * Override it in the extension service when a command needs more than
     * the aggregate - a domain service, another port.
     */
    @Rule(value = "ddd-command-service", target = "service", order = 30)
    void service(EntitySpec e) {
        if (!applies(e)) return;
        SourceCode c = ctx(e);
        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        String repo = orDefault(c.varValue("cmdRef"), "repository");
        String mapper = orDefault(c.varValue("mapperName"), e.domainClassName() + "Mapper");
        // Read back as create and update do, when the entity references another by id.
        String open = orDefault(c.varValue("readBackOpen"), "");
        String close = orDefault(c.varValue("readBackClose"), "");
        String dto = String.format("%sDTO", e.getName());
        FieldSpec pk = e.pkFields().get(0);

        for (CommandSpec cmd : e.commands()) {
            StringBuilder m = new StringBuilder();
            m.append("    /**\n")
             .append(String.format("     * The %s command: loads the %s, asks it to %s, saves it.%n", cmd.name(), e.getName(), cmd.name()))
             .append("     *\n")
             .append(String.format("     * The rule is %s.%s; this is the transaction around it. Override it in%n", e.getName(), cmd.name()))
             .append(String.format("     * %sService when the command needs more than the aggregate.%n", e.getName()))
             .append("     */\n")
             .append("    @Transactional\n");
            String call = String.format("entity.%s(%s)", cmd.name(), arguments(cmd, kotlin));
            if (kotlin) {
                m.append(String.format("    open fun %s(%s): %s? {%n", cmd.name(), serviceParams(e, cmd, c.syntax()), dto));
                if (cmd.dtoName() != null) m.append("        BeanValidation.validate(command)\n");
                m.append(String.format("        val entity = %s.findById(%s)%n", repo, pk.name()))
                 .append(String.format("                .orElseThrow { EntityNotFoundException(\"%s \" + %s + \" not found\") }%n",
                         e.getName(), pk.name()));
                // Who may give the command: @access(ship=admin), against the row's owner.
                if (io.elasticore.base.model.pub.rules.base.ServiceAccessRules.guarded(e)) {
                    m.append(String.format("        accessWrite(entity, \"%s\")%n", cmd.name()));
                }
                m.append(String.format("        %s%n", call));
                if (DddDeriveRules.derives(e)) m.append(String.format("        %s%n", DddDeriveRules.call(c)));
                m.append("")
                 .append(String.format("        return %s.toDTO(%s%s.save(entity)%s)%n", mapper, open, repo, close))
                 .append("    }");
            } else {
                m.append(String.format("    public %s %s(%s) {%n", dto, cmd.name(), serviceParams(e, cmd, c.syntax())));
                if (cmd.dtoName() != null) m.append("        BeanValidation.validate(command);\n");
                m.append(String.format("        %s entity = %s.findById(%s)%n", e.getName(), repo, pk.name()))
                 .append(String.format("                .orElseThrow(() -> new EntityNotFoundException(\"%s \" + %s + \" not found\"));%n",
                         e.getName(), pk.name()));
                if (io.elasticore.base.model.pub.rules.base.ServiceAccessRules.guarded(e)) {
                    m.append(String.format("        accessWrite(entity, \"%s\");%n", cmd.name()));
                }
                m.append(String.format("        %s;%n", call));
                if (DddDeriveRules.derives(e)) m.append(String.format("        %s%n", DddDeriveRules.call(c)));
                m.append("")
                 .append(String.format("        return %s.toDTO(%s%s.save(entity)%s);%n", mapper, open, repo, close))
                 .append("    }");
            }
            c.addMethod(m.toString().replace("\r\n", "\n"));
        }
    }

    /** One endpoint per command: POST /{id}/ship. After the method filter (20). */
    @Rule(value = "ddd-command-control", target = "control", order = 30)
    void control(EntitySpec e) {
        if (!applies(e)) return;
        SourceCode c = ctx(e);
        boolean kotlin = c.syntax() == Syntax.KOTLIN;
        FieldSpec pk = e.pkFields().get(0);
        String service = e.varName() + "Service";
        String dto = String.format("%sDTO", e.getName());
        String pkType = kotlin ? c.syntax().typeName(pk.typeName()) : pk.typeName();

        for (CommandSpec cmd : e.commands()) {
            String body = cmd.dtoName() == null ? "" : String.format("%s.%s", e.pkg("dto"), cmd.dtoName());
            StringBuilder m = new StringBuilder();
            m.append(String.format("    @Operation(summary = \"%s: %s\")%n", e.getName(), cmd.name()))
             .append(String.format("    @PostMapping(\"/{%s}/%s\")%n", pk.name(), kebab(cmd.name())));
            if (kotlin) {
                String params = String.format("@PathVariable(\"%s\") %s: %s", pk.name(), pk.name(), pkType);
                if (!body.isEmpty()) params += String.format(", @Valid @RequestBody command: %s", body);
                m.append(String.format("    fun %s(%s): %s? {%n", cmd.name(), params, dto))
                 .append(String.format("        return %s.%s(%s%s)%n", service, cmd.name(), pk.name(),
                         body.isEmpty() ? "" : ", command"))
                 .append("    }");
            } else {
                String params = String.format("@PathVariable(\"%s\") %s %s", pk.name(), pkType, pk.name());
                if (!body.isEmpty()) params += String.format(", @Valid @RequestBody %s command", body);
                m.append(String.format("    public %s %s(%s) {%n", dto, cmd.name(), params))
                 .append(String.format("        return %s.%s(%s%s);%n", service, cmd.name(), pk.name(),
                         body.isEmpty() ? "" : ", command"))
                 .append("    }");
            }
            c.addMethod(m.toString().replace("\r\n", "\n"));
        }
    }

    // ---- a root with nothing a request may edit ---------------------------

    /**
     * A sealed root whose fields are all its own to change has nothing an
     * update could write: its API is create, read, delete and its commands.
     * Before the port's surface (10) and the controller's filter (20).
     */
    @Rule(value = "ddd-command-no-update-port", target = "usecase", order = 5)
    void noUpdatePort(EntitySpec e) {
        if (nothingEditable(e)) ctx(e).excludeMethod("update", "batchUpdate");
    }

    @Rule(value = "ddd-command-no-update", target = "control", order = 21)
    void noUpdate(EntitySpec e) {
        if (nothingEditable(e)) ctx(e).excludeMethod("update", "batchUpdate");
    }

    /**
     * Whether a root has no update endpoint for this reason. Public for the
     * TypeScript client, which describes the same controller from outside.
     */
    public static boolean nothingEditable(EntitySpec e) {
        if (!DddBehaviorRules.splits(e)) return false;
        for (FieldSpec f : e.fields()) {
            if (f.hasDsl("editable")) return false;
        }
        return true;
    }

    // ---- shapes -----------------------------------------------------------

    /** The parameters the aggregate's method takes, as the language writes them. */
    private static String params(SourceCode c, CommandSpec cmd) {
        return params(c, cmd, null);
    }

    /** The parameters the aggregate's method takes - for the lifecycle's commands too. */
    static String paramsOf(SourceCode c, CommandSpec cmd, EntitySpec e) {
        return params(c, cmd, e);
    }

    private static String params(SourceCode c, CommandSpec cmd, EntitySpec e) {
        StringBuilder out = new StringBuilder();
        for (FieldSpec p : cmd.params()) {
            if (out.length() > 0) out.append(", ");
            String type = qualified(p.typeName(), e);
            if (c.syntax() == Syntax.KOTLIN) {
                String kt = c.syntax().typeName(type);
                if (!p.required() && !kt.endsWith("?")) kt += "?";
                out.append(p.name()).append(": ").append(kt);
            } else {
                out.append(type).append(' ').append(p.name());
            }
        }
        return out.toString();
    }

    /** What the service passes: the command's values, asserted present where required. */
    private static String arguments(CommandSpec cmd, boolean kotlin) {
        StringBuilder out = new StringBuilder();
        for (FieldSpec p : cmd.params()) {
            if (out.length() > 0) out.append(", ");
            if (kotlin) {
                out.append("command.").append(p.name()).append(p.required() ? "!!" : "");
            } else {
                out.append("command.get").append(p.capitalName()).append("()");
            }
        }
        return out.toString();
    }

    /** The service's and the port's parameters: the identity, then the command DTO if any. */
    private static String serviceParams(EntitySpec e, CommandSpec cmd, Syntax s) {
        FieldSpec pk = e.pkFields().get(0);
        String out = s == Syntax.KOTLIN
                ? String.format("%s: %s", pk.name(), s.typeName(pk.typeName()))
                : String.format("%s %s", pk.typeName(), pk.name());
        if (cmd.dtoName() != null) {
            out += s == Syntax.KOTLIN
                    ? String.format(", command: %s", cmd.dtoName())
                    : String.format(", %s command", cmd.dtoName());
        }
        return out;
    }

    /**
     * A command takes values; an entity is named by its identity instead.
     * Entities alone, a list of them included: a list of plain values - tags:
     * List<string> - is a value too, and was warned about as though it were not.
     */
    private static void warnEntityParams(EntitySpec e, CommandSpec cmd) {
        for (FieldSpec p : cmd.params()) {
            if (!p.isEntityRef()) continue;
            if (WARNED.add(e.domainName() + "." + e.getName() + "." + cmd.name() + "." + p.name())) {
                io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                        "[%s] %s.%s(%s): a command parameter is a value - pass the other aggregate's"
                                + " identity (e.g. itemCode: string(20)) and load it in the service.",
                        e.domainClassName(), e.getName(), cmd.name(), p.name()));
            }
        }
    }

    /** Types the generated files do not import, written in full. */
    private static String qualified(String type, EntitySpec e) {
        if (type == null) return "Object";
        String simple = type.substring(type.lastIndexOf('.') + 1);
        if (e != null && type.indexOf('.') < 0 && e.typePackageOf(type) != null) {
            return e.typePackageOf(type) + "." + type;
        }
        if ("Money".equals(simple) || "Quantity".equals(simple)) return "io.elasticore.springboot3.domain." + simple;
        switch (type) {
            case "BigDecimal":
            case "BigInteger":
                return "java.math." + type;
            case "LocalDate":
            case "LocalDateTime":
            case "LocalTime":
            case "Instant":
            case "OffsetDateTime":
            case "ZonedDateTime":
                return "java.time." + type;
            default:
                return type;
        }
    }

    /** shipPartially -> ship-partially, the way a path segment is spelled. */
    static String kebab(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }

    private static String orDefault(String v, String d) {
        return v == null || v.isEmpty() ? d : v;
    }
}
