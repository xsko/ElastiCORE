package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.ErrorCatalog;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;

import java.util.ArrayList;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * A lifecycle, said once in the DSL:
 *
 * <pre>
 * PurchaseOrder:
 *   states:
 *     status:
 *       initial: PLACED
 *       ship:    PLACED -> SHIPPED
 *       cancel:  PLACED -> CANCELLED
 * </pre>
 *
 * Each move becomes its command, written: the command checks the state it is
 * given in, moves to the next one and raises the event named for it
 * (SHIPPED raises Shipped, when @event declares one). A move from a state it
 * does not start in is refused - 409, it conflicts with the row as it is - in the
 * domain's words. A new row starts in the initial state.
 *
 * The command was an abstract method the developer had to write; with a move
 * it is a method of the regenerated base, and the developer's class may
 * override it to do more and call super for the move.
 */
public class DddStateRules implements CodeRules {

    /**
     * The first state, as the field's initial value. After the field's
     * @default (70), which it replaces - and says so, since two starting
     * states is one too many. Before the class shell renders fields (90).
     */
    @Rule(value = "ddd-state-initial", target = "entity", order = 71)
    void initial(EntitySpec e, FieldSpec f) {
        for (EntitySpec.StateMachine m : e.stateMachines()) {
            if (!m.field().equals(f.name())) continue;
            // Without a behaviour class moves() never runs, so the warning is said here.
            boolean ok = usable(e, m, null, !DddBehaviorRules.splits(e));
            if (m.initial() == null || !ok) continue;
            if (f.hasDsl("default")) {
                io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                        "[%s] %s.%s has @default and states.%s.initial; the initial state (%s) is the one kept.",
                        e.domainClassName(), e.getName(), f.name(), f.name(), m.initial()));
            }
            ctx(e).field(f).initial("%s.%s", simple(f.typeName()), m.initial());
        }
    }

    /**
     * Whether the base writes this command as a move: every lifecycle it
     * moves is one the generator accepts. Otherwise the command stays the
     * developer's promise - an abstract method and a stub - so nothing calls
     * a method that was never written.
     */
    public static boolean writesMove(EntitySpec e, String command) {
        return DddBehaviorRules.splits(e) && e.lifecycleAccepts(command);
    }

    /**
     * Whether the lifecycle can be written at all: an enum field of the
     * entity, every state a constant of that enum. The set is no longer read
     * (a command may move several lifecycles) and is kept for callers.
     */
    public static boolean usable(EntitySpec e, EntitySpec.StateMachine m, java.util.Set<String> seen, boolean warn) {
        String why = e.lifecycleProblem(m);
        if (why != null && warn) {
            io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                    "[%s] %s states%s; the lifecycle is left out.", e.domainClassName(), e.getName(),
                    why.startsWith(".") ? why : " " + why));
        }
        return why == null;
    }

    /**
     * One method per command, and the checks they share. A command that
     * moves one lifecycle is checked and moved by that lifecycle's mover; one
     * that moves several (cancel: the order CANCELLED and the payment
     * REFUNDED) is checked against every lifecycle before any of them moves,
     * so a refusal leaves the row as it was. After the command promises (97).
     */
    @Rule(value = "ddd-state-moves", target = "entity", order = 98, phase = Rule.Phase.TAIL)
    void moves(EntitySpec e) {
        if (e.stateMachines().isEmpty() || !DddBehaviorRules.splits(e)) return;
        SourceCode c = ctx(e);
        boolean kt = c.syntax() == Syntax.KOTLIN;

        java.util.Set<String> written = new java.util.HashSet<String>();
        for (EntitySpec.StateMachine m : e.stateMachines()) {
            if (!usable(e, m, null, true)) continue;
            FieldSpec field = field(e, m.field());
            String type = simple(field.typeName());
            String mover = "move" + cap(m.field());
            boolean shares = false;
            // The mover is written only where a command of this lifecycle alone calls it.
            boolean usesMover = false;

            for (EntitySpec.Move move : m.moves()) {
                List<EntitySpec.StateMachine> machines = e.machinesOf(move.command());
                if (machines.size() > 1 && e.lifecycleAccepts(move.command())) shares = true;
                // Every lifecycle a command moves can refuse it, each with its own code.
                if (e.lifecycleAccepts(move.command())) {
                    ErrorCatalog.record(refusalCode(e, m.field(), move.command()),
                            String.format("%s cannot %s when %s is {0}; it must be {1}", e.getName(), move.command(), m.field()));
                }
                if (!e.lifecycleAccepts(move.command()) || !written.add(move.command())) continue;
                if (machines.size() > 1) {
                    together(c, e, move.command(), machines, kt);
                    continue;
                }
                usesMover = true;
                EntitySpec.CommandSpec cmd = command(e, move.command());
                String params = cmd == null ? "" : DddCommandRules.paramsOf(c, cmd, e);
                List<String> from = new ArrayList<String>();
                for (String s : move.from()) from.add(type + "." + s);
                String event = eventFor(e, move.to());

                c.varLine("fieldList", "")
                 .varLine("fieldList", "/**")
                 .varLine("fieldList", " * The %s command: %s -> %s, as the DSL's states say.", move.command(),
                         join(move.from(), ", "), move.to())
                 .varLine("fieldList", " * To do more, override it in %s and call super.%s(%s) for the move.", e.getName(), move.command(),
                         params.isEmpty() ? "" : "...")
                 .varLine("fieldList", " */");
                if (kt) {
                    c.varLine("fieldList", "open fun %s(%s) {", move.command(), params)
                     .varLine("fieldList", "    %s(\"%s\", %s.%s, %s)", mover, move.command(), type, move.to(), join(from, ", "));
                    if (event != null) c.varLine("fieldList", "    raise%s()", event);
                    c.varLine("fieldList", "}");
                } else {
                    c.varLine("fieldList", "public void %s(%s) {", move.command(), params)
                     .varLine("fieldList", "    %s(\"%s\", %s.%s, %s);", mover, move.command(), type, move.to(), join(from, ", "));
                    if (event != null) c.varLine("fieldList", "    raise%s();", event);
                    c.varLine("fieldList", "}");
                }

                // Whether the command may be given now - for a screen deciding
                // which buttons to show as much as for the domain itself.
                c.varLine("fieldList", "")
                 .varLine("fieldList", "/** Whether %s may be given now: the %s is %s. */", move.command(),
                         m.field(), join(move.from(), " or "));
                String test = anyOf(m.field(), from);
                if (kt) {
                    c.varLine("fieldList", "fun can%s(): Boolean = %s", cap(move.command()), test);
                } else {
                    c.varLine("fieldList", "public boolean can%s() {", cap(move.command()))
                     .varLine("fieldList", "    return %s;", test)
                     .varLine("fieldList", "}");
                }
            }

            if (usesMover) {
            c.varLine("fieldList", "")
             .varLine("fieldList", "/**")
             .varLine("fieldList", " * Moves %s to the next state, or refuses: the command was given in a state it does not start in.", m.field());
            for (String line : lifecycle(e, m, type)) c.varLine("fieldList", "%s", line.isEmpty() ? " *" : " * " + line);
            c.varLine("fieldList", " */");
            if (kt) {
                c.varLine("fieldList", "private fun %s(command: String, to: %s, vararg from: %s) {", mover, type, type)
                 .varLine("fieldList", "    if (this.%s !in from)", m.field())
                 .varLine("fieldList", "        throw io.elasticore.springboot3.validation.InvariantViolationException(\"%s\" + command,", refusalCode(e, m.field(), ""))
                 .varLine("fieldList", "            \"%s cannot \" + command + \" when %s is \" + this.%s + \"; it must be \" + from.joinToString(\" or \"),", e.getName(), m.field(), m.field())
                 .varLine("fieldList", "            this.%s.toString(), from.joinToString(\" or \"))", m.field())
                 .varLine("fieldList", "    this.%s = to", m.field())
                 .varLine("fieldList", "}");
            } else {
                c.varLine("fieldList", "private void %s(String command, %s to, %s... from) {", mover, type, type)
                 .varLine("fieldList", "    for (%s allowed : from) {", type)
                 .varLine("fieldList", "        if (allowed == this.%s) {", m.field())
                 .varLine("fieldList", "            this.%s = to;", m.field())
                 .varLine("fieldList", "            return;")
                 .varLine("fieldList", "        }")
                 .varLine("fieldList", "    }")
                 .varLine("fieldList", "    throw new io.elasticore.springboot3.validation.InvariantViolationException(\"%s\" + command,", refusalCode(e, m.field(), ""))
                 .varLine("fieldList", "            \"%s cannot \" + command + \" when %s is \" + this.%s + \"; it must be \" + java.util.Arrays.toString(from),", e.getName(), m.field(), m.field())
                 .varLine("fieldList", "            String.valueOf(this.%s), java.util.Arrays.toString(from));", m.field())
                 .varLine("fieldList", "}");
            }
            }
            // Where no command uses the mover, the lifecycle is said above the check instead.
            if (shares) requirer(c, e, m, type, kt, !usesMover);
        }
    }

    /**
     * A command that moves several lifecycles: every one is checked before
     * any moves, so a refusal - in whichever lifecycle - changes nothing.
     */
    private void together(SourceCode c, EntitySpec e, String command, List<EntitySpec.StateMachine> machines, boolean kt) {
        EntitySpec.CommandSpec cmd = command(e, command);
        String params = cmd == null ? "" : DddCommandRules.paramsOf(c, cmd, e);
        String end = kt ? "" : ";";

        c.varLine("fieldList", "")
         .varLine("fieldList", "/**")
         .varLine("fieldList", " * The %s command, as the DSL's states say. It moves %d lifecycles, each checked", command, machines.size())
         .varLine("fieldList", " * before any of them moves:");
        List<String> body = new ArrayList<String>();
        List<String> moves = new ArrayList<String>();
        List<String> tests = new ArrayList<String>();
        List<String> said = new ArrayList<String>();
        java.util.Set<String> events = new java.util.LinkedHashSet<String>();
        for (EntitySpec.StateMachine m : machines) {
            EntitySpec.Move move = m.moveOf(command);
            String type = simple(field(e, m.field()).typeName());
            List<String> from = new ArrayList<String>();
            for (String s : move.from()) from.add(type + "." + s);
            c.varLine("fieldList", " *   %s: %s -> %s", m.field(), join(move.from(), ", "), move.to());
            body.add(String.format("    require%s(\"%s\", %s)%s", cap(m.field()), command, join(from, ", "), end));
            moves.add(String.format("    this.%s = %s.%s%s", m.field(), type, move.to(), end));
            tests.add("(" + anyOf(m.field(), from) + ")");
            said.add("the " + m.field() + " is " + join(move.from(), " or "));
            String event = eventFor(e, move.to());
            if (event != null) events.add(event);
        }
        c.varLine("fieldList", " * To do more, override it in %s and call super.%s(%s) for the move.", e.getName(), command,
                params.isEmpty() ? "" : "...")
         .varLine("fieldList", " */")
         .varLine("fieldList", kt ? "open fun %s(%s) {" : "public void %s(%s) {", command, params);
        for (String line : body) c.varLine("fieldList", "%s", line);
        for (String line : moves) c.varLine("fieldList", "%s", line);
        for (String event : events) c.varLine("fieldList", "    raise%s()%s", event, end);
        c.varLine("fieldList", "}");

        c.varLine("fieldList", "")
         .varLine("fieldList", "/** Whether %s may be given now: %s. */", command, join(said, " and "));
        if (kt) {
            c.varLine("fieldList", "fun can%s(): Boolean = %s", cap(command), join(tests, " && "));
        } else {
            c.varLine("fieldList", "public boolean can%s() {", cap(command))
             .varLine("fieldList", "    return %s;", join(tests, " && "))
             .varLine("fieldList", "}");
        }
    }

    /**
     * The lifecycles that say when a row may be deleted ("delete: CANCELLED")
     * and can be written. Empty when none does.
     */
    public static List<EntitySpec.StateMachine> deleteGuards(EntitySpec e) {
        List<EntitySpec.StateMachine> out = new ArrayList<EntitySpec.StateMachine>();
        if (!DddBehaviorRules.splits(e)) return out;
        for (EntitySpec.StateMachine m : e.stateMachines()) {
            if (m.deletable() != null && e.lifecycleProblem(m) == null) out.add(m);
        }
        return out;
    }

    /** Whether the base checks, before a row is deleted, that its lifecycle allows it. */
    public static boolean guardsDelete(EntitySpec e) {
        return !deleteGuards(e).isEmpty();
    }

    /**
     * delete: CANCELLED - a row is deleted only in the states its lifecycle
     * names. canDelete() says whether it may be now (a screen hides the
     * button); checkDeletable() refuses (409) before JPA removes it, on every
     * path a delete takes - by id, by search, by cascade. JPA allows one
     * @PreRemove per class: where the invariant rules already mark the root
     * as being removed, that method calls this check and this one is not
     * annotated (DddInvariantRules.marksRemoval).
     */
    /** The rule that writes the delete check. */
    public static final String DELETE_RULE = "ddd-state-delete";

    @Rule(value = DELETE_RULE, target = "entity", order = 99, phase = Rule.Phase.TAIL)
    void deleteGuard(EntitySpec e) {
        List<EntitySpec.StateMachine> guards = deleteGuards(e);
        if (guards.isEmpty()) {
            // Said rather than dropped: the guard lives in the base of a split root.
            for (EntitySpec.StateMachine m : e.stateMachines()) {
                if (m.deletable() != null && !DddBehaviorRules.splits(e)) {
                    io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                            "[%s] %s states.%s.delete: written only for a root with a behaviour class (@root in a DDD bundle);"
                                    + " deleting %s is not guarded.", e.domainClassName(), e.getName(), m.field(), e.getName()));
                }
            }
            return;
        }
        SourceCode c = ctx(e);
        boolean kt = c.syntax() == Syntax.KOTLIN;
        if (!kt && c.syntax() != Syntax.JAVA) return;
        // The invariant rule writes the class's one @PreRemove and calls this check from it.
        boolean annotate = !(DddInvariantRules.marksRemoval(e) && c.hasRule(DddInvariantRules.REMOVING_RULE));

        List<String> tests = new ArrayList<String>();
        for (EntitySpec.StateMachine m : guards) {
            String type = simple(field(e, m.field()).typeName());
            tests.add(m.deletable().isEmpty() ? "false" : anyOf(m.field(), qualified(type, m.deletable())));
            ErrorCatalog.record(refusalCode(e, m.field(), EntitySpec.DELETE),
                    String.format("%s cannot be deleted when %s is {0}; it must be {1}", e.getName(), m.field()));
        }
        String test = tests.size() == 1 ? tests.get(0) : "(" + join(tests, ") && (") + ")";

        c.varLine("fieldList", "")
         .varLine("fieldList", "/** Whether this row may be deleted now: the states its lifecycle names (states: delete). */");
        if (kt) c.varLine("fieldList", "fun canDelete(): Boolean = %s", test);
        else c.varLine("fieldList", "public boolean canDelete() {")
              .varLine("fieldList", "    return %s;", test)
              .varLine("fieldList", "}");

        c.varLine("fieldList", "")
         .varLine("fieldList", "/** Refuses a delete in any other state: 409, and the row stays. */");
        if (annotate) c.varLine("fieldList", "@jakarta.persistence.PreRemove");
        c.varLine("fieldList", kt ? "fun checkDeletable() {" : "void checkDeletable() {");
        for (EntitySpec.StateMachine m : guards) {
            String type = simple(field(e, m.field()).typeName());
            String allowed = "[" + join(m.deletable(), ", ") + "]";
            c.varLine("fieldList", "    if (!(%s))", m.deletable().isEmpty() ? "false" : anyOf(m.field(), qualified(type, m.deletable())))
             .varLine("fieldList", kt
                     ? "        throw io.elasticore.springboot3.validation.InvariantViolationException(\"%s\","
                     : "        throw new io.elasticore.springboot3.validation.InvariantViolationException(\"%s\",",
                     refusalCode(e, m.field(), EntitySpec.DELETE))
             .varLine("fieldList", "            \"%s cannot be deleted when %s is \" + this.%s + \"; it must be %s\",",
                     e.getName(), m.field(), m.field(), allowed)
             .varLine("fieldList", kt ? "            this.%s.toString(), \"%s\")" : "            String.valueOf(this.%s), \"%s\");", m.field(), allowed);
        }
        c.varLine("fieldList", "}");
    }

    private static List<String> qualified(String type, List<String> states) {
        List<String> out = new ArrayList<String>();
        for (String s : states) out.add(type + "." + s);
        return out;
    }

    /** The message code of a refused lifecycle step: state.<Entity>.<field>.<command>. */
    static String refusalCode(EntitySpec e, String field, String command) {
        return "state." + e.getName() + "." + field + "." + command;
    }

    /** The check alone, for the commands that move this lifecycle together with another. */
    private void requirer(SourceCode c, EntitySpec e, EntitySpec.StateMachine m, String type, boolean kt, boolean summary) {
        String field = m.field();
        c.varLine("fieldList", "");
        if (summary) {
            c.varLine("fieldList", "/**")
             .varLine("fieldList", " * Refuses the command unless %s is a state it starts in; moves nothing.", field);
            for (String line : lifecycle(e, m, type)) c.varLine("fieldList", "%s", line.isEmpty() ? " *" : " * " + line);
            c.varLine("fieldList", " */");
        } else {
            c.varLine("fieldList", "/** Refuses the command unless %s is a state it starts in; moves nothing. */", field);
        }
        if (kt) {
            c.varLine("fieldList", "private fun require%s(command: String, vararg from: %s) {", cap(field), type)
             .varLine("fieldList", "    if (this.%s !in from)", field)
             .varLine("fieldList", "        throw io.elasticore.springboot3.validation.InvariantViolationException(\"%s\" + command,", refusalCode(e, field, ""))
             .varLine("fieldList", "            \"%s cannot \" + command + \" when %s is \" + this.%s + \"; it must be \" + from.joinToString(\" or \"),", e.getName(), field, field)
             .varLine("fieldList", "            this.%s.toString(), from.joinToString(\" or \"))", field)
             .varLine("fieldList", "}");
        } else {
            c.varLine("fieldList", "private void require%s(String command, %s... from) {", cap(field), type)
             .varLine("fieldList", "    for (%s allowed : from) {", type)
             .varLine("fieldList", "        if (allowed == this.%s) return;", field)
             .varLine("fieldList", "    }")
             .varLine("fieldList", "    throw new io.elasticore.springboot3.validation.InvariantViolationException(\"%s\" + command,", refusalCode(e, field, ""))
             .varLine("fieldList", "            \"%s cannot \" + command + \" when %s is \" + this.%s + \"; it must be \" + java.util.Arrays.toString(from),", e.getName(), field, field)
             .varLine("fieldList", "            String.valueOf(this.%s), java.util.Arrays.toString(from));", field)
             .varLine("fieldList", "}");
        }
    }

    private static String anyOf(String field, List<String> states) {
        StringBuilder test = new StringBuilder();
        for (String s : states) {
            if (test.length() > 0) test.append(" || ");
            test.append("this.").append(field).append(" == ").append(s);
        }
        return test.toString();
    }

    /**
     * The lifecycle in words, for the reader of the generated class: where a
     * row starts, each move, the states no command leaves, and the states no
     * command reaches.
     */
    static List<String> lifecycle(EntitySpec e, EntitySpec.StateMachine m, String type) {
        List<String> out = new ArrayList<String>();
        out.add("");
        out.add("Lifecycle of " + m.field() + " (" + type + "), from the DSL's states:");
        if (m.initial() != null) out.add("  start:  " + m.initial());
        java.util.Set<String> leaves = new java.util.LinkedHashSet<String>();
        java.util.Set<String> reached = new java.util.LinkedHashSet<String>();
        if (m.initial() != null) reached.add(m.initial());
        for (EntitySpec.Move move : m.moves()) {
            String with = "";
            for (EntitySpec.StateMachine other : e.machinesOf(move.command())) {
                if (other != m && e.lifecycleProblem(other) == null) with += (with.isEmpty() ? "  (with " : ", ") + other.field();
            }
            if (!with.isEmpty()) with += ")";
            out.add("  " + move.command() + ":" + pad(move.command()) + join(move.from(), ", ") + " -> " + move.to() + with);
            leaves.addAll(move.from());
            reached.add(move.to());
        }
        List<String> end = new ArrayList<String>();
        for (String s : reached) {
            if (!leaves.contains(s)) end.add(s);
        }
        if (!end.isEmpty()) out.add("  end:    " + join(end, ", ") + (end.size() == 1
                ? " - no command leaves it" : " - no command leaves them"));
        List<String> constants = e.enumConstantNamesOf(type);
        if (constants != null) {
            List<String> never = new ArrayList<String>();
            for (String s : constants) {
                if (!reached.contains(s) && !leaves.contains(s)) never.add(s);
            }
            if (!never.isEmpty()) out.add("  unused: " + join(never, ", ") + " - no command moves a row there");
        }
        return out;
    }

    private static String pad(String command) {
        StringBuilder sb = new StringBuilder(" ");
        for (int i = command.length() + 1; i < 7; i++) sb.append(' ');
        return sb.toString();
    }

    /** SHIPPED raises Shipped: the event @event declares under the state's name, if any. */
    static String eventFor(EntitySpec e, String state) {
        String wanted = state.replace("_", "");
        for (String event : e.events()) {
            if (event.equalsIgnoreCase(wanted)) return event;
        }
        return null;
    }

    private static EntitySpec.CommandSpec command(EntitySpec e, String name) {
        for (EntitySpec.CommandSpec cmd : e.commands()) {
            if (cmd.name().equals(name)) return cmd;
        }
        return null;
    }

    static FieldSpec field(EntitySpec e, String name) {
        for (FieldSpec f : e.fields()) {
            if (f.name().equals(name)) return f;
        }
        return null;
    }

    private static String simple(String type) {
        if (type == null) return null;
        String t = type.trim();
        if (t.endsWith("?")) t = t.substring(0, t.length() - 1);
        int dot = t.lastIndexOf('.');
        return dot >= 0 ? t.substring(dot + 1) : t;
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String join(List<String> items, String sep) {
        StringBuilder out = new StringBuilder();
        for (String s : items) {
            if (out.length() > 0) out.append(sep);
            out.append(s);
        }
        return out.toString();
    }
}
