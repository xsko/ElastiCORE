package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.EnumSpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.base.AccessExpression;
import io.elasticore.base.model.pub.rules.base.ControllerBaseRules;
import io.elasticore.base.model.pub.rules.base.DtoBaseRules;
import io.elasticore.base.model.pub.rules.base.FieldDoc;
import io.elasticore.base.model.pub.rules.base.ServiceAccessRules;
import io.elasticore.base.model.pub.rules.ddd.DddBehaviorRules;
import io.elasticore.base.model.pub.rules.ddd.DddCommandRules;
import io.elasticore.base.model.pub.rules.ddd.DddInvariantRules;
import io.elasticore.base.model.pub.rules.ddd.DddStateRules;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The contract tests ({@code testCode: "contract"} in env.yml): what the DSL
 * declares of each entity, checked over the project's own API.
 *
 * <pre>
 *   states:            each move allowed from every state it lists (2xx, the state
 *                      moved, a @stamp set) and refused from a state it does not
 *                      (409, code state.Entity.field.command); a new row starts in
 *                      the initial state
 *   !                  required: sent as null, refused (400, the field, NotNull)
 *   string(n), length  the length passes, one past it is refused (400, Size)
 *   min, max           the bound passes, one past it is refused (400, Min/Max or
 *                      DecimalMin/DecimalMax)
 *   pattern            a value that matches passes, one that does not is refused
 *                      (400, Pattern); with leading lookaheads, one that matches the
 *                      rest and misses a lookahead
 *   notblank, email    text passes, blank text is refused (400, NotBlank); an
 *                      address passes, the address without its @ is refused (400,
 *                      Email)
 *   past, future       a year ago (a year from now) passes, two days ahead (ago) on
 *                      a date, a day on a datetime or timestamp, is
 *                      refused (400, Past, PastOrPresent, Future, FutureOrPresent)
 *   minsize            that many members pass, one fewer is refused (400, Size)
 *   invariant          a field compared with a number, a collection's size, a
 *                      field that must be set: the boundary passes, one past it is
 *                      refused (422, code invariant.Entity....)
 * </pre>
 *
 * One test class per entity, each extending one support class per domain:
 * the support class carries the @SpringBootTest, so every class starts the
 * same application (Spring caches it for the suite), and every request goes
 * through it - request(...) is where a project adds what every request needs
 * (a CSRF token), signIn() how it signs in. All of them are written once
 * (the generate-once marker) and merged when the DSL changes, as the
 * architecture test is. Each test makes its own rows, with unique values,
 * and checks the status and the problem's code - never the message.
 *
 * A row the generator cannot build is said where it knows (the summary
 * counts it as skipped); one the application refuses at run time aborts the
 * test with the reason (Assumptions.abort), never fails it.
 */
final class ContractTestSource {

    /** The environment variable a MongoDB domain's tests read their server from. */
    static final String MONGO_URI = "ELASTICORE_IT_MONGODB_URI";

    /** One file: the class and its source. */
    static final class Source {
        final String className;
        final String text;
        final int tests;

        Source(String className, String text, int tests) {
            this.className = className;
            this.text = text;
            this.tests = tests;
        }
    }

    /** What a domain asks of the run: its language and store, and what its rules write. */
    static final class Settings {
        boolean kotlin;
        boolean boot4;
        boolean mongo;
        /** The lifecycles are written (states: - the domain-model rule sets). */
        boolean lifecycles;
        /** The root's @invariant is checked (the domain-model rule sets). */
        boolean invariants;
        /** A split root's commands get endpoints, and one with nothing editable no update (the domain-model rule sets). */
        boolean commands;
        /** mode: mybatis - the schema is the application's, no Hibernate makes it. */
        boolean mybatis;
        /** The application the tests start, when Spring would not find it from their package; null when it would. */
        String application;

        /**
         * Reads which of the domain-model rule sets the run selected. The rule
         * sets are named here, not in the runner, which knows none of them.
         */
        void readRuleSets(java.util.function.Predicate<Class<?>> selected) {
            lifecycles = selected.test(DddStateRules.class);
            invariants = selected.test(DddInvariantRules.class);
            commands = selected.test(DddCommandRules.class);
        }
    }

    private final List<ECoreModel> models;

    /** The entities whose controller this run wrote, as domain:Entity (key). */
    private final Set<String> served;

    /** What the summary says: the tests written, and what was not with why. */
    int written;

    /** Why the sign-in's principal of the last domain asked was not built, as the summary says it; null when it was. */
    private String principalProblem;

    final List<String> skipped = new ArrayList<String>();

    /** The specs of the run's entities, as domain:Entity - two domains may each have a Customer. */
    private final Map<String, EntitySpec> specs = new HashMap<String, EntitySpec>();

    /** Each spec's DTO, and the model each spec was read from: by the spec itself, never by a name. */
    private final java.util.IdentityHashMap<EntitySpec, EntitySpec> dtos =
            new java.util.IdentityHashMap<EntitySpec, EntitySpec>();

    private final java.util.IdentityHashMap<EntitySpec, ECoreModel> modelOf =
            new java.util.IdentityHashMap<EntitySpec, ECoreModel>();

    /** The model of the domain being written: an entity's name is looked up there first. */
    private ECoreModel current;

    /** The key an entity is known by in the run: its domain and its name. */
    static String key(ECoreModel model, String entityName) {
        return model.getInternalDomainName() + ":" + entityName;
    }

    ContractTestSource(List<ECoreModel> models, Set<String> served) {
        this.models = models;
        this.served = served;
    }

    // ---- the domain -------------------------------------------------------------------

    /**
     * The domain's files: the support class, and a test class for each entity
     * with something to test. Empty when there is nothing.
     */
    List<Source> domain(ECoreModel model, String domainName, List<EntitySpec> own, String pkg, Settings s) {
        current = model;
        for (EntitySpec e : own) {
            specs.put(key(model, e.getName()), e);
            modelOf.put(e, model);
        }
        String domainClass = classNameOf(domainName, own);
        String support = domainClass + "ContractSupport";
        Lang lang = new Lang(s.kotlin);

        List<EntityTests> planned = new ArrayList<EntityTests>();
        boolean asks = false;
        for (EntitySpec e : own) {
            if (!testable(e)) continue;
            EntityTests t = new EntityTests(e, lang, s);
            if (!t.plan()) continue;
            planned.add(t);
            asks |= (t.guarded && !t.tests.isEmpty()) || t.declaresAccess();
        }
        // Whom the tests sign in: asked where an entity with tests asks who is calling, or declares @access.
        Principal principal = asks ? principal(lang) : null;
        String noPrincipal = principal == null ? principalProblem : null;
        accessSkipped.clear();
        boolean callers = false;
        for (EntityTests t : planned) {
            t.access(principal, noPrincipal);
            callers |= t.accessTests > 0;
        }
        List<Source> out = new ArrayList<Source>();
        boolean guarded = false;
        boolean access = false;
        for (EntityTests t : planned) {
            if (t.tests.isEmpty()) continue;
            guarded |= t.guarded;
            access |= t.declaresAccess();
            written += t.tests.size();
            out.add(new Source(t.e.getName() + "ContractTest", t.render(pkg, support), t.tests.size()));
        }
        if (out.isEmpty()) return out;
        Access a = new Access(callers, access, accessSkipped);
        out.add(
                0,
                new Source(
                        support,
                        supportClass(pkg, support, domainName, s, lang, guarded, guarded ? principal : null, a),
                        0));
        return out;
    }

    /** What the support class says and holds of @access: the callers it signs in, what is not tested and why. */
    static final class Access {
        final boolean callers;
        final boolean declared;
        final List<String> skipped;

        Access(boolean callers, boolean declared, List<String> skipped) {
            this.callers = callers;
            this.declared = declared;
            this.skipped = new ArrayList<String>(skipped);
        }
    }

    /** What the generator knew it cannot test of the domain's @access, as the summary says it: for the support class's comment. */
    private final List<String> accessSkipped = new ArrayList<String>();

    private void accessSkip(String why) {
        skip(why);
        if (!accessSkipped.contains(why)) accessSkipped.add(why);
    }

    private static String classNameOf(String domainName, List<EntitySpec> own) {
        for (EntitySpec e : own) {
            if (e.domainClassName() != null && !e.domainClassName().isEmpty()) return e.domainClassName();
        }
        return Character.toUpperCase(domainName.charAt(0)) + domainName.substring(1);
    }

    /** An entity of the domain with an endpoint to create its rows through: a root the API serves. */
    private boolean testable(EntitySpec e) {
        if (e.hasMeta("embeddable") || e.hasMeta("abstract") || e.isMappedSuperclass()) return false;
        return served.contains(key(current, e.getName()));
    }

    // ---- the model --------------------------------------------------------------------

    /** The entity of that name - the domain being written's first, then any other of the run - or null. */
    private EntitySpec entity(String name) {
        if (name == null) return null;
        List<ECoreModel> order = new ArrayList<ECoreModel>();
        if (current != null) order.add(current);
        for (ECoreModel m : models) {
            if (!java.util.Objects.equals(m, current)) order.add(m);
        }
        for (ECoreModel m : order) {
            EntitySpec found = entityIn(m, name);
            if (found != null) return found;
        }
        return null;
    }

    /** The entity of that name in that domain, as a spec; null when it has none. */
    private EntitySpec entityIn(ECoreModel m, String name) {
        if (m.getEntityModels() == null) return null;
        String k = key(m, name);
        if (specs.containsKey(k)) return specs.get(k);
        Entity entity = m.getEntityModels().findByName(name);
        if (entity == null) return null;
        EntitySpec found = SpecFactory.fromEntity(entity, m, m.getInternalDomainName());
        specs.put(k, found);
        modelOf.put(found, m);
        return found;
    }

    /** The DTO the entity's API reads and answers, from the entity's own domain: its fields as a request sends them. */
    private EntitySpec dto(EntitySpec e) {
        if (dtos.containsKey(e)) return dtos.get(e);
        EntitySpec found = null;
        ECoreModel m = modelOf.get(e);
        if (m != null && m.getDataTransferModels() != null) {
            io.elasticore.base.model.dto.DataTransfer d =
                    m.getDataTransferModels().findByName(e.dtoClassName());
            if (d != null) found = SpecFactory.fromDto(d, m, m.getInternalDomainName());
        }
        dtos.put(e, found);
        return found;
    }

    /** Whether the entity's controller is written: as this run recorded it for its domain. */
    private boolean servedHere(EntitySpec e) {
        ECoreModel m = modelOf.get(e);
        return m != null && served.contains(key(m, e.getName()));
    }

    /** Whether the API serves a create of the entity: its controller is written and create is not left out. */
    private boolean creates(EntitySpec e) {
        if (e == null || !e.hasMeta("expose") || e.pkField() == null || e.hasMeta("readonly")) return false;
        if (ControllerBaseRules.exposeOmitted(e).contains("create")) return false;
        if (ControllerBaseRules.restExcluded(e, true).contains("create")) return false;
        return true;
    }

    /** The entity's API: /api/v1/shop/order. */
    private static String path(EntitySpec e) {
        return e.apiPath() + "/" + e.domainName() + "/" + e.varName();
    }

    /** The constant a test class names the path with: ORDER_ITEM. */
    private static String constant(EntitySpec e) {
        return e.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(java.util.Locale.ROOT);
    }

    private static String simple(String type) {
        if (type == null) return null;
        String t = type.trim();
        if (t.endsWith("?")) t = t.substring(0, t.length() - 1);
        int lt = t.indexOf('<');
        if (lt >= 0 && t.endsWith(">")) t = t.substring(lt + 1, t.length() - 1);
        int dot = t.lastIndexOf('.');
        return dot >= 0 ? t.substring(dot + 1) : t;
    }

    /** What the request names the field by: @json / @jsonproperty, or its name. */
    private static String json(FieldSpec f) {
        String named = f.dslValueAny("jsonproperty", "json");
        if (named != null) {
            String n = named.trim();
            if (n.length() >= 2 && (n.charAt(0) == '"' || n.charAt(0) == '\'')) n = n.substring(1, n.length() - 1);
            if (!n.isEmpty() && n.matches("[A-Za-z_][A-Za-z0-9_]*")) return n;
        }
        return f.name();
    }

    /** What a request may write: not shown-only, not derived, not the persistence layer's or the lifecycle's. */
    private static boolean writable(EntitySpec e, FieldSpec f) {
        if (f.hasDslAny(
                "readonly",
                "noinput",
                "behavior",
                "statefield",
                "snapshot",
                "compute",
                "formula",
                "count",
                "deleted",
                "autoRelation",
                "writeonlyshadow")) return false;
        if (f.systemManaged() || f.blindIndexOf() != null) return false;
        if (f.hasDsl(DddStateRules.STAMP)) return false;
        // The lifecycle's field is set by its moves; a split root takes no state from the request.
        for (EntitySpec.StateMachine m : e.stateMachines()) {
            if (m.field().equals(f.name()) && DddBehaviorRules.splits(e)) return false;
        }
        return true;
    }

    /**
     * Whether the DTO refuses the field blank - and null: @notblank on text, as
     * dto-validation writes it, less where v3-dto-system-managed takes it off
     * again (a field the server sets, which a request cannot send).
     */
    private static boolean blankRefused(FieldSpec f) {
        return f.isStringType()
                && f.hasDsl("notblank")
                && !io.elasticore.base.model.pub.rules.base.WriteGuardRules.serverSet(f);
    }

    /** The expression of the field's @pattern as the DTO's @Pattern reads it (PatternSample.unescape); null without one. */
    private static String regex(FieldSpec f) {
        return f.hasDsl("pattern") ? PatternSample.unescape(f.patternRegexp()) : null;
    }

    /** Whether a valid row sends the field: '!', or @notblank, which refuses null too. */
    private static boolean sent(FieldSpec f) {
        return f.required() || blankRefused(f);
    }

    /** What a temporal field is, as the marks read it: date, datetime, time, instant; null for any other. */
    static String temporalKind(FieldSpec f) {
        String base = f.baseTypeName();
        if ("DATE".equals(base) || "LocalDate".equals(base)) return "date";
        if ("DATETIME".equals(base) || "LocalDateTime".equals(base)) return "datetime";
        if ("TIME".equals(base) || "LocalTime".equals(base)) return "time";
        if ("INSTANT".equals(base)) return "instant";
        return null;
    }

    /**
     * The reference a DTO field carries the key of, as relation.key: its @ref
     * (customerCustomerId, the flattened key) or - a reference by id, the DDD
     * rule sets - its @idof (customerId, the identity the entity holds).
     */
    private static String reference(FieldSpec f) {
        String ref = f.dslValue("ref");
        if (ref != null) return ref;
        String of = f.dslValue("idof");
        return of == null || of.trim().isEmpty() ? null : of.trim() + ".";
    }

    /** A flattened key the DTO leaves out for the identity that holds the same key (dto-reference-once). */
    private static boolean removedKey(EntitySpec dto, FieldSpec f) {
        return f.dslValue("ref") != null
                && io.elasticore.base.model.pub.rules.base.SearchDtoBaseRules.identityOfReference(dto, f) != null;
    }

    private static FieldSpec field(EntitySpec e, String name) {
        if (e == null || name == null) return null;
        for (FieldSpec f : e.allFields()) {
            if (f.name().equals(name)) return f;
        }
        return null;
    }

    // ---- values ----------------------------------------------------------------------

    /** The bounds a number must keep: the DTO's and the invariants'. */
    static final class Range {
        BigDecimal low;
        boolean lowOpen;
        BigDecimal high;
        boolean highOpen;

        void atLeast(BigDecimal v, boolean open) {
            if (low == null || v.compareTo(low) > 0 || (v.compareTo(low) == 0 && open)) {
                low = v;
                lowOpen = open;
            }
        }

        void atMost(BigDecimal v, boolean open) {
            if (high == null || v.compareTo(high) < 0 || (v.compareTo(high) == 0 && open)) {
                high = v;
                highOpen = open;
            }
        }

        boolean admits(BigDecimal v) {
            if (low != null && (lowOpen ? v.compareTo(low) <= 0 : v.compareTo(low) < 0)) return false;
            if (high != null && (highOpen ? v.compareTo(high) >= 0 : v.compareTo(high) > 0)) return false;
            return true;
        }

        /** A value inside: the lower end (a step above an open one), else 1 or the upper end; null when empty. */
        BigDecimal pick(BigDecimal step) {
            BigDecimal v;
            if (low != null) {
                v = lowOpen ? low.add(step) : low;
            } else {
                v = BigDecimal.ONE;
                if (high != null && !admits(v)) v = highOpen ? high.subtract(step) : high;
            }
            return admits(v) ? v : null;
        }

        /** A whole value inside: the least one, else 1 or the greatest; null when none is. */
        BigDecimal pickWhole() {
            BigDecimal v;
            if (low != null) {
                v = lowOpen
                        ? low.setScale(0, java.math.RoundingMode.FLOOR).add(BigDecimal.ONE)
                        : low.setScale(0, java.math.RoundingMode.CEILING);
            } else {
                v = BigDecimal.ONE;
                if (high != null && !admits(v)) {
                    v = highOpen
                            ? high.setScale(0, java.math.RoundingMode.CEILING).subtract(BigDecimal.ONE)
                            : high.setScale(0, java.math.RoundingMode.FLOOR);
                }
            }
            return admits(v) ? v : null;
        }

        /** For a float: a value inside, never a fractional end, which a float holds only approximately. */
        BigDecimal pickFloat() {
            BigDecimal v = pick(BigDecimal.ONE);
            boolean atEnd = (low != null && v != null && v.compareTo(low) == 0)
                    || (high != null && v != null && v.compareTo(high) == 0);
            if (v == null || !fractional(v) || !atEnd) return v;
            if (low != null && admits(low.add(BigDecimal.ONE))) return low.add(BigDecimal.ONE);
            if (low != null && high != null) {
                BigDecimal mid = low.add(high).divide(BigDecimal.valueOf(2));
                if (admits(mid)) return mid;
            }
            return null;
        }

        /** How many values from v in whole steps the range holds, up to a billion. */
        long wholeSteps(BigDecimal v) {
            long most = 1000000000L;
            if (high == null) return most;
            BigDecimal room = high.subtract(v).setScale(0, java.math.RoundingMode.FLOOR);
            long n = room.compareTo(BigDecimal.valueOf(most)) > 0 ? most : room.longValue() + 1;
            // An open end is not a value: high itself is left out when v reaches it in whole steps.
            if (highOpen && v.add(BigDecimal.valueOf(n - 1)).compareTo(high) >= 0) n--;
            return n;
        }
    }

    /** A number type's step: 1 for a whole number and a floating one, the scale's for a decimal. */
    private static BigDecimal step(FieldSpec f) {
        if ((f.isBigDecimal() || f.isMoney()) && f.hasPrecisionScale() && f.scale() > 0) {
            return BigDecimal.ONE.movePointLeft(f.scale());
        }
        return BigDecimal.ONE;
    }

    private static boolean whole(FieldSpec f) {
        String t = f.baseTypeName();
        return "P_INT".equals(t)
                || "P_LONG".equals(t)
                || "INTEGER".equals(t)
                || "LONG".equals(t)
                || "BIGINTEGER".equals(t)
                || f.isQuantity();
    }

    private static boolean numeric(FieldSpec f) {
        String t = f.baseTypeName();
        return whole(f)
                || f.isBigDecimal()
                || f.isMoney()
                || "P_FLOAT".equals(t)
                || "P_DOUBLE".equals(t)
                || "FLOAT".equals(t)
                || "DOUBLE".equals(t);
    }

    /** A float, which keeps a fraction only approximately: its checks compare it widened to a double. */
    private static boolean float32(FieldSpec f) {
        String t = f.baseTypeName();
        return "P_FLOAT".equals(t) || "FLOAT".equals(t);
    }

    private static boolean fractional(BigDecimal v) {
        return v.stripTrailingZeros().scale() > 0;
    }

    /** A whole number's {passes, fails} at a promise's bound: qty > 0.5 is 1 and 0; null where none is (== 0.5). */
    static BigDecimal[] wholeEnds(String operator, BigDecimal bound) {
        BigDecimal floor = bound.setScale(0, java.math.RoundingMode.FLOOR);
        BigDecimal ceil = bound.setScale(0, java.math.RoundingMode.CEILING);
        switch (operator) {
            case ">":
                return new BigDecimal[] {floor.add(BigDecimal.ONE), floor};
            case ">=":
                return new BigDecimal[] {ceil, ceil.subtract(BigDecimal.ONE)};
            case "<":
                return new BigDecimal[] {ceil.subtract(BigDecimal.ONE), ceil};
            case "<=":
                return new BigDecimal[] {floor, floor.add(BigDecimal.ONE)};
            default:
                return fractional(bound) ? null : new BigDecimal[] {bound, bound.add(BigDecimal.ONE)};
        }
    }

    private static final java.util.regex.Pattern NOT_ON_H2 = java.util.regex.Pattern.compile(
            "(?i)\\b(jsonb|xml|hstore|inet|cidr|macaddr|tsvector|citext|geometry|geography)\\b");

    private static final java.util.regex.Pattern COLUMN_DEFINITION =
            java.util.regex.Pattern.compile("columnDefinition\\s*=\\s*\"([^\"]*)\"");

    /**
     * Why H2 - the database the tests write - cannot make the entity's table: a column declared with a
     * type it has not (@columnDef('jsonb'), xml ...); null when none is.
     */
    static String h2Refuses(EntitySpec e) {
        for (FieldSpec f : e.allFields()) {
            List<String> definitions = new ArrayList<String>();
            String def = f.dslValueAny("columnDef", "columndef", "columnDefinition", "columndefinition");
            if (def != null) definitions.add(def);
            for (String line : f.nativeAnnotationLines()) {
                java.util.regex.Matcher m = COLUMN_DEFINITION.matcher(line);
                while (m.find()) definitions.add(m.group(1));
            }
            for (String d : definitions) {
                java.util.regex.Matcher m = NOT_ON_H2.matcher(d);
                if (m.find()) {
                    return f.name() + "'s column is " + m.group(1).toLowerCase(java.util.Locale.ROOT)
                            + ", which H2 - the tests' database - cannot make";
                }
            }
        }
        return null;
    }

    /** Whether each row needs its own value of the field: @unique, a unique index on it alone, or the key. */
    static boolean unique(EntitySpec e, FieldSpec f) {
        if (declaresUnique(f)) return true;
        FieldSpec declared = field(e, f.name());
        if (declared != null && declaresUnique(declared)) return true;
        if (e.pkFields().size() == 1 && e.pkFields().get(0).name().equals(f.name())) return true;
        String column = declared == null ? null : declared.dbColumnName();
        String snake = f.name().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
        for (EntitySpec.IndexSpec idx : e.indexes()) {
            if (idx.unique() == null
                    || !"true".equalsIgnoreCase(idx.unique().replace("\"", "").trim())) continue;
            if (idx.columnList() == null) continue;
            String c = idx.columnList().replace("\"", "").trim();
            if (c.equalsIgnoreCase(f.name())
                    || c.equalsIgnoreCase(snake)
                    || (column != null && c.equalsIgnoreCase(column))) return true;
        }
        return false;
    }

    private static boolean declaresUnique(FieldSpec f) {
        return f.hasDsl("unique")
                && !"false".equals(String.valueOf(f.dslValue("unique")).trim());
    }

    private static BigDecimal number(String text) {
        if (text == null) return null;
        String t = DtoBaseRules.Bound.split(text)[0];
        if (t == null) return null;
        t = t.trim();
        if (t.length() >= 2 && (t.charAt(0) == '"' || t.charAt(0) == '\''))
            t = t.substring(1, t.length() - 1).trim();
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** The DTO's bounds of a number field: @min, @max. */
    private static Range dtoRange(FieldSpec f) {
        Range r = new Range();
        BigDecimal min = number(f.dslValueAny("min", "minval", "minvalue"));
        BigDecimal max = number(f.dslValueAny("max", "maxval", "maxvalue"));
        if (min != null) r.atLeast(min, false);
        if (max != null) r.atMost(max, false);
        return r;
    }

    private static String plain(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        if (s.scale() < 0) s = s.setScale(0);
        return s.toPlainString();
    }

    // ---- the plan of one entity -------------------------------------------------------

    /** One test: its method name, what it says, and its statements. */
    static final class Test {
        final String method;
        final String display;
        final List<String> lines = new ArrayList<String>();

        Test(String method, String display) {
            this.method = method;
            this.display = display;
        }
    }

    /** A condition @invariant declares, as the tests read it. */
    static final class Promise {
        final String condition;
        final String code;
        /** The field it reads (items for items.size). */
        String field;
        /** >, >=, <, <=, ==, notNull, notEmpty */
        String operator;

        BigDecimal bound;
        boolean size;

        Promise(String condition, String code) {
            this.condition = condition;
            this.code = code;
        }
    }

    private static final java.util.regex.Pattern COMPARISON = java.util.regex.Pattern.compile(
            "\\s*([A-Za-z_][A-Za-z0-9_]*)(\\.size)?\\s*(>=|<=|==|>|<)\\s*(-?\\d+(?:\\.\\d+)?)\\s*");
    private static final java.util.regex.Pattern PREDICATE = java.util.regex.Pattern.compile(
            "\\s*([A-Za-z_][A-Za-z0-9_]*)\\s+(notNull|notEmpty)\\s*", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static Promise read(String condition, String code) {
        Promise p = new Promise(condition, code);
        java.util.regex.Matcher m = COMPARISON.matcher(condition);
        if (m.matches()) {
            p.field = m.group(1);
            p.size = m.group(2) != null;
            p.operator = m.group(3);
            p.bound = new BigDecimal(m.group(4));
            return p;
        }
        m = PREDICATE.matcher(condition);
        if (m.matches()) {
            p.field = m.group(1);
            p.operator = m.group(2).equalsIgnoreCase("notNull") ? "notNull" : "notEmpty";
            return p;
        }
        return p;
    }

    /**
     * The tests of one entity, and the builder methods they make rows with:
     * the entity's own, and those of the rows it needs first.
     */
    final class EntityTests {
        final EntitySpec e;
        final Lang lang;
        final Settings s;
        EntitySpec dto;
        final List<Test> tests = new ArrayList<Test>();
        /** The builders the class declares, by entity name, in the order they are needed. */
        final Map<String, Builder> builders = new LinkedHashMap<String, Builder>();

        final Set<String> methods = new java.util.HashSet<String>();
        final List<Promise> promises = new ArrayList<Promise>();
        boolean guarded;

        EntityTests(EntitySpec e, Lang lang, Settings s) {
            this.e = e;
            this.lang = lang;
            this.s = s;
        }

        /** Plans the tests; false when there are none to write. */
        boolean plan() {
            dto = dto(e);
            if (dto == null) {
                skip(e.getName() + ": no " + e.dtoClassName() + " to send");
                return false;
            }
            if (!creates(e)) {
                if (declaresAnything(e))
                    skip(e.getName() + ": its API takes no create (@expose methods), so no row is made to test");
                return false;
            }
            if (dto.jsonView() || e.jsonView()) {
                skip(e.getName() + ": jsonview - what a create reads depends on the views");
                return false;
            }
            if (s.invariants && e.aggregateRoot() && e.hasMeta("invariant")) {
                for (String[] declared : DddInvariantRules.declaredPromises(e))
                    promises.add(read(declared[0], declared[1]));
            }
            Builder own = builder(e, new ArrayDeque<String>());
            if (own.problem != null) {
                skip(e.getName() + ": " + own.problem);
                return false;
            }
            guarded = ServiceAccessRules.guarded(e);
            for (Builder b : builders.values()) guarded |= ServiceAccessRules.guarded(b.e);

            if (s.lifecycles) lifecycles(own);
            constraints(own);
            invariants(own);
            return true;
        }

        private boolean declaresAnything(EntitySpec e) {
            if (!e.stateMachines().isEmpty() || e.hasMeta("invariant")) return true;
            for (FieldSpec f : e.fields()) {
                if (f.required()
                        || f.validationMaxLength() >= 0
                        || f.hasDslAny(
                                "min",
                                "max",
                                "pattern",
                                "notblank",
                                "email",
                                "past",
                                "pastorpresent",
                                "future",
                                "futureorpresent")) return true;
            }
            return false;
        }

        private String method(String wanted) {
            String name = wanted.replaceAll("[^A-Za-z0-9_]", "");
            if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) name = "t" + name;
            String unique = name;
            for (int n = 2; !methods.add(unique); n++) unique = name + n;
            return unique;
        }

        private Test test(String method, String display) {
            Test t = new Test(method(method), display);
            tests.add(t);
            return t;
        }

        private String newRow() {
            return builders.get(e.getName()).call();
        }

        // ---- builders

        /** The builder of an entity's rows, planned with what it needs first; its problem when none can be. */
        Builder builder(EntitySpec target, Deque<String> visiting) {
            Builder known = builders.get(target.getName());
            if (known != null) return known;
            Builder b = new Builder(target);
            if (visiting.contains(target.getName())) {
                b.problem =
                        "its references come back to " + target.getName() + " (" + String.join(" -> ", visiting) + ")";
                return b;
            }
            // Hibernate makes the tables on H2 (JPA): a column type H2 has not is a table it cannot make.
            String refused = s.mongo || s.mybatis ? null : h2Refuses(target);
            if (refused != null) {
                b.problem = refused;
                return b;
            }
            EntitySpec targetDto = target == e ? dto : dto(target);
            if (targetDto == null) {
                b.problem = "no " + target.dtoClassName() + " to send";
                return b;
            }
            if (target != e && (!creates(target) || !servedAnywhere(target))) {
                b.problem = "it needs a " + target.getName() + ", which the API does not create";
                return b;
            }
            visiting.push(target.getName());
            try {
                b.fields = fields(
                        target,
                        targetDto,
                        target == e ? promises : java.util.Collections.<Promise>emptyList(),
                        visiting,
                        b,
                        null);
            } finally {
                visiting.pop();
            }
            if (b.problem == null) builders.put(target.getName(), b);
            return b;
        }

        /** Whether its controller is written: as this run wrote it for the domain, as the DSL says for another. */
        private boolean servedAnywhere(EntitySpec target) {
            return !java.util.Objects.equals(modelOf.get(target), current) || servedHere(target);
        }

        /**
         * The fields a valid row is sent with: what the DTO requires, what the
         * promises read, the references a derived value reads through - each
         * as an expression of the test class.
         */
        List<String[]> fields(
                EntitySpec target,
                EntitySpec targetDto,
                List<Promise> rules,
                Deque<String> visiting,
                Builder into,
                String backTo) {
            List<String[]> out = new ArrayList<String[]>();
            FieldSpec pk = target.pkFields().size() == 1 ? target.pkFields().get(0) : null;
            Set<String> derivedThrough = derivedThrough(target);
            for (FieldSpec f : targetDto.fields()) {
                if (removedKey(targetDto, f)) continue;
                String ref = reference(f);
                if (ref != null) {
                    int dot = ref.indexOf('.');
                    if (dot <= 0) continue;
                    FieldSpec relation = field(target, ref.substring(0, dot));
                    if (relation == null || relation.isList()) continue;
                    // A member's reference to its root is the root's to set.
                    if (backTo != null && backTo.equals(simple(relation.typeName()))) continue;
                    boolean needed = relation.required() || f.required() || derivedThrough.contains(relation.name());
                    if (!needed) continue;
                    EntitySpec other = entity(simple(relation.typeName()));
                    if (other == null) {
                        into.problem = relation.name() + " names " + relation.typeName()
                                + ", which is not an entity of the run";
                        return out;
                    }
                    String keyName = ref.substring(dot + 1);
                    if (keyName.isEmpty() && other.pkField() != null)
                        keyName = other.pkField().name();
                    // itemName @ref(item.name) shows the row; only its key sets the reference.
                    if (other.pkField() == null
                            || !keyName.equals(other.pkField().name())) continue;
                    Builder needs = builder(other, visiting);
                    if (needs.problem != null) {
                        into.problem = relation.name() + ": " + needs.problem;
                        return out;
                    }
                    out.add(new String[] {json(f), needs.id(keyName)});
                    continue;
                }
                if (!writable(target, f)) continue;
                if (pk != null && f.name().equals(pk.name())) {
                    if (ServiceAccessRules.keyGenerated(pk)) continue;
                }
                boolean isKey = pk != null && f.name().equals(pk.name());
                Promise promise = promiseOn(rules, f.name());
                if (f.isList()) {
                    int members = members(f, rules);
                    if (members == 0) continue;
                    String list = membersOf(target, f, members, visiting, into);
                    if (into.problem != null) return out;
                    out.add(new String[] {json(f), list});
                    continue;
                }
                boolean needed = sent(f) || isKey || promise != null;
                if (!needed) continue;
                String value = value(target, targetDto, f, rules, visiting, into);
                if (value == null) {
                    if (into.problem == null)
                        into.problem = f.name() + ": no value of " + f.typeName() + " can be built";
                    return out;
                }
                out.add(new String[] {json(f), value});
            }
            return out;
        }

        /** The references a @snapshot or @compute of the entity reads through: item for @snapshot(item.unitCost). */
        private Set<String> derivedThrough(EntitySpec target) {
            Set<String> out = new java.util.HashSet<String>();
            for (FieldSpec f : target.fields()) {
                for (String key : new String[] {"snapshot", "compute"}) {
                    String v = f.dslValue(key);
                    if (v == null) continue;
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\.")
                            .matcher(v);
                    while (m.find()) out.add(m.group(1));
                }
            }
            return out;
        }

        private Promise promiseOn(List<Promise> rules, String fieldName) {
            for (Promise p : rules) {
                if (fieldName.equals(p.field) && p.operator != null) return p;
            }
            return null;
        }

        /** How many members a valid row is sent with: what @minsize, '!' and the promises ask. */
        private int members(FieldSpec f, List<Promise> rules) {
            int least = f.required() ? 1 : 0;
            BigDecimal min = number(f.dslValueAny("minsize"));
            if (min != null) least = Math.max(least, min.intValue());
            for (Promise p : rules) {
                if (!f.name().equals(p.field) || p.operator == null) continue;
                if ("notEmpty".equals(p.operator)) least = Math.max(least, 1);
                if (p.size && ">".equals(p.operator)) least = Math.max(least, p.bound.intValue() + 1);
                if (p.size && (">=".equals(p.operator) || "==".equals(p.operator)))
                    least = Math.max(least, p.bound.intValue());
            }
            return least;
        }

        /** The members sent with the row: list(obj(...), ...), each what the member's DTO requires. */
        private String membersOf(EntitySpec target, FieldSpec f, int count, Deque<String> visiting, Builder into) {
            String member = member(target, f, visiting, into);
            if (member == null) return null;
            List<String> items = new ArrayList<String>();
            for (int i = 0; i < count; i++) items.add(member);
            return "list(" + String.join(", ", items) + ")";
        }

        /** One member of the collection, as an expression; null with the problem said when none can be built. */
        String member(EntitySpec target, FieldSpec f, Deque<String> visiting, Builder into) {
            // The entity's own field names the member's entity; the DTO's, its DTO (OrderItemDTO).
            FieldSpec declared = field(target, f.name());
            String itemType =
                    simple(declared != null && declared.isList() ? declared.coreItemType() : f.coreItemType());
            EntitySpec item = entity(itemType);
            if (item == null && itemType != null && itemType.endsWith("DTO")) {
                item = entity(itemType.substring(0, itemType.length() - 3));
            }
            if (item == null) {
                into.problem = f.name() + ": " + f.coreItemType() + " is not an entity, and no member can be built";
                return null;
            }
            String root = target.ownAggregate();
            if (item.aggregateRoot()
                    && !item.getName().equals(root)
                    && !target.getName().equals(item.ownAggregate())) {
                into.problem = f.name() + ": a " + item.getName() + " is a root of its own, not sent with the "
                        + target.getName();
                return null;
            }
            EntitySpec itemDto = dto(item);
            if (itemDto == null) {
                into.problem = f.name() + ": no " + item.dtoClassName() + " to send its members with";
                return null;
            }
            // Its fields: what its DTO requires, less the reference back to the root - the root's to set.
            List<String[]> fields =
                    fields(item, itemDto, java.util.Collections.<Promise>emptyList(), visiting, into, target.getName());
            if (into.problem != null) return null;
            return obj(fields);
        }

        /** A valid value of one field, as an expression; null when none can be built. */
        String value(
                EntitySpec target,
                EntitySpec targetDto,
                FieldSpec f,
                List<Promise> rules,
                Deque<String> visiting,
                Builder into) {
            String type = simple(f.typeName());
            boolean unique = unique(target, f);
            if (unique && !f.isStringType() && !numeric(f)) {
                // An enumeration, a boolean, a date: too few values, or none the generator varies, to make one per row.
                into.problem = f.name() + " is unique, and the generator makes no value of " + type + " for each row";
                return null;
            }
            if (targetDto.isKnownEnum(type) || target.isKnownEnum(type)) {
                String v = enumValue(targetDto.isKnownEnum(type) ? targetDto : target, type, null);
                return v == null ? null : lang.string(v);
            }
            if (targetDto.isEmbeddableType(type) || target.isEmbeddableType(type)) {
                EntitySpec value = entity(type);
                if (value == null) return null;
                List<String[]> parts = new ArrayList<String[]>();
                for (FieldSpec part : value.fields()) {
                    if (!sent(part) || !writable(value, part)) continue;
                    String v =
                            this.value(value, value, part, java.util.Collections.<Promise>emptyList(), visiting, into);
                    if (v == null) return null;
                    parts.add(new String[] {json(part), v});
                }
                return obj(parts);
            }
            if (f.isStringType()) {
                String v = text(f, unique);
                if (v == null && unique && f.hasDsl("pattern") && into.problem == null) {
                    into.problem = f.name() + " is unique, and its @pattern lets the generator make one value only";
                }
                return v;
            }
            if (numeric(f)) {
                Range r = dtoRange(f);
                for (Promise p : rules) {
                    if (f.name().equals(p.field) && !p.size && p.bound != null) bound(r, p.operator, p.bound);
                }
                BigDecimal v = whole(f) ? r.pickWhole() : float32(f) ? r.pickFloat() : r.pick(step(f));
                if (v == null || (whole(f) && v.stripTrailingZeros().scale() > 0)) return null;
                if (!unique) return "num(" + lang.string(plain(v)) + ")";
                // One value per row: whole steps up from the first, as many as the range holds.
                long span = r.wholeSteps(v);
                if (span < 2) {
                    into.problem = f.name() + " is unique, and its bounds let one value only";
                    return null;
                }
                return "serial(" + lang.string(plain(v)) + ", " + span + "L)";
            }
            String base = f.baseTypeName();
            if ("P_BOOLEAN".equals(base) || "BOOLEAN".equals(base)) return "true";
            return temporal(f, lang);
        }

        /**
         * A string the field takes: its pattern's - varied per row where the
         * pattern lets it, which a unique one needs - an email, or unique text of
         * a length it allows.
         */
        private String text(FieldSpec f, boolean unique) {
            int max = f.validationMaxLength();
            if (max < 0) max = f.rawLength() > 0 ? f.rawLength() : 0;
            int min = Math.max(0, f.minLength());
            String regexp = regex(f);
            if (regexp != null) {
                PatternSample.Sample sample = PatternSample.sample(regexp, min, max > 0 ? max : 64);
                if (sample == null) return null;
                if (!sample.varies()) return unique ? null : lang.string(sample.text);
                StringBuilder call = new StringBuilder("vary(").append(lang.string(sample.text));
                for (String alphabet : sample.alphabets) call.append(", ").append(lang.string(alphabet));
                return call.append(")").toString();
            }
            boolean email = f.hasDsl("email");
            int length = max > 0 ? Math.min(max, email ? 24 : 12) : (email ? 24 : 12);
            length = Math.max(length, Math.max(min, 1));
            if (max > 0 && length > max) return null;
            if (email) return length < 6 ? null : "email(" + length + ")";
            return "text(" + length + ")";
        }

        // ---- lifecycles

        /** Each move from each state it lists, refused from the others, and where a new row starts. */
        void lifecycles(Builder own) {
            if (e.pkFields().size() != 1) {
                if (!e.stateMachines().isEmpty())
                    skip(e.getName() + " states: a composite key - no command endpoint to test");
                return;
            }
            List<EntitySpec.StateMachine> machines = new ArrayList<EntitySpec.StateMachine>();
            for (EntitySpec.StateMachine m : e.stateMachines()) {
                if (e.lifecycleProblem(m) == null) machines.add(m);
            }
            Set<String> commands = new LinkedHashSet<String>();
            for (EntitySpec.StateMachine m : machines) {
                for (EntitySpec.Move mv : m.moves()) {
                    if (DddStateRules.writesMove(e, mv.command())) commands.add(mv.command());
                }
            }
            if (commands.isEmpty()) return;
            List<EntitySpec.StateMachine> used = new ArrayList<EntitySpec.StateMachine>();
            for (EntitySpec.StateMachine m : machines) {
                for (String c : commands) {
                    if (m.moveOf(c) != null && !used.contains(m)) used.add(m);
                }
            }
            String key = json(e.pkFields().get(0));
            String pathConstant = constant(e);

            // A new row starts where each lifecycle says.
            Test start = null;
            for (EntitySpec.StateMachine m : used) {
                String shown = stateJson(m.field());
                if (m.initial() == null || shown == null) continue;
                if (start == null) {
                    start = test(
                            "aNew" + e.getName() + "StartsInItsInitialState",
                            "a new " + e.getName() + " starts in its initial state");
                    start.lines.add(lang.val("created", "create(" + pathConstant + ", " + newRow() + ")"));
                }
                start.lines.add(lang.stmt("assertValue(created, " + lang.string(shown) + ", "
                        + lang.string(stateValue(m, m.initial())) + ")"));
            }

            // The bodies the commands are sent with; a command whose parameters cannot be built is not used.
            Map<String, String> bodies = new LinkedHashMap<String, String>();
            for (String c : commands) {
                String body = commandBody(c);
                if (body == null) {
                    skip(e.getName() + "." + c + ": no value can be built for a parameter it requires");
                    continue;
                }
                bodies.put(c, body);
            }
            Graph graph = new Graph(used, bodies.keySet());
            if (graph.start == null) {
                skip(e.getName() + " states: a lifecycle names no initial state, so no row starts in one");
                return;
            }
            Set<String> written = new java.util.HashSet<String>();
            for (EntitySpec.StateMachine m : used) {
                for (EntitySpec.Move mv : m.moves()) {
                    String c = mv.command();
                    if (!bodies.containsKey(c)) continue;
                    for (String from : mv.from()) {
                        List<String> at = graph.reach(m, from, c, true);
                        if (at == null) {
                            skip(e.getName() + "." + c + " from " + from + ": no sequence of commands reaches it");
                            continue;
                        }
                        String tuple = graph.last(at);
                        if (!written.add(c + "@" + tuple)) continue;
                        allowed(c, at, graph, bodies, key, pathConstant);
                    }
                }
            }
            for (EntitySpec.StateMachine m : used) {
                List<String> states =
                        e.enumConstantNamesOf(simple(field(e, m.field()).typeName()));
                if (states == null) continue;
                for (EntitySpec.Move mv : m.moves()) {
                    String c = mv.command();
                    if (!bodies.containsKey(c)) continue;
                    for (String state : states) {
                        if (mv.from().contains(state)) continue;
                        List<String> at = graph.reach(m, state, c, false);
                        if (at == null) continue;
                        String tuple = graph.last(at);
                        String code = graph.refusal(c, graph.tupleOf(tuple));
                        if (code == null || !written.add("!" + c + "@" + tuple)) continue;
                        refused(c, at, bodies, key, pathConstant, code, m, state);
                    }
                }
            }
        }

        private void allowed(
                String c, List<String> at, Graph graph, Map<String, String> bodies, String key, String pathConstant) {
            String[] tuple = graph.tupleOf(graph.last(at));
            StringBuilder said = new StringBuilder();
            StringBuilder name = new StringBuilder(c);
            name.append("From");
            List<EntitySpec.StateMachine> moving = e.machinesOf(c);
            for (EntitySpec.StateMachine m : moving) {
                int i = graph.machines.indexOf(m);
                if (i < 0) continue;
                EntitySpec.Move mv = m.moveOf(c);
                if (said.length() > 0) said.append(", ");
                said.append(m.field())
                        .append(' ')
                        .append(tuple[i])
                        .append(" -> ")
                        .append(mv.to());
                name.append(camel(tuple[i]));
            }
            Test t = test(name.toString(), c + ": " + said);
            t.lines.add(lang.val("id", "createdId(" + pathConstant + ", " + newRow() + ", " + lang.string(key) + ")"));
            steps(t, at, bodies, pathConstant);
            t.lines.add(lang.val(
                    "moved",
                    "command(" + pathConstant + ", id, " + lang.string(DddCommandRules.kebab(c)) + ", " + bodies.get(c)
                            + ")"));
            t.lines.add(lang.stmt("assertMoved(moved)"));
            for (EntitySpec.StateMachine m : moving) {
                String shown = stateJson(m.field());
                if (shown == null || graph.machines.indexOf(m) < 0) continue;
                t.lines.add(lang.stmt("assertValue(moved, " + lang.string(shown) + ", "
                        + lang.string(stateValue(m, m.moveOf(c).to())) + ")"));
                for (String[] stamp : DddStateRules.stampedStates(e)) {
                    if (!stamp[1].equals(m.field())
                            || !stamp[2].equals(m.moveOf(c).to())) continue;
                    FieldSpec shownStamp = field(dto, stamp[0]);
                    if (shownStamp == null || shownStamp.hasDslAny("writeonly", "nooutput")) continue;
                    t.lines.add(lang.stmt("assertStamped(moved, " + lang.string(json(shownStamp)) + ")"));
                }
            }
        }

        private void refused(
                String c,
                List<String> at,
                Map<String, String> bodies,
                String key,
                String pathConstant,
                String code,
                EntitySpec.StateMachine m,
                String state) {
            Test t = test(
                    c + "IsRefusedWhen" + camel(m.field()) + "Is" + camel(state),
                    c + " is refused when " + m.field() + " is " + state + " (409 " + code + ")");
            t.lines.add(lang.val("id", "createdId(" + pathConstant + ", " + newRow() + ", " + lang.string(key) + ")"));
            steps(t, at, bodies, pathConstant);
            t.lines.add(
                    lang.stmt("assertRefused(command(" + pathConstant + ", id, " + lang.string(DddCommandRules.kebab(c))
                            + ", " + bodies.get(c) + "), 409, " + lang.string(code) + ")"));
        }

        private void steps(Test t, List<String> at, Map<String, String> bodies, String pathConstant) {
            for (int i = 1; i < at.size(); i++) {
                String c = at.get(i).substring(0, at.get(i).indexOf('@'));
                t.lines.add(lang.stmt("given(command(" + pathConstant + ", id, " + lang.string(DddCommandRules.kebab(c))
                        + ", " + bodies.get(c) + "))"));
            }
        }

        /** What a command is sent with: null when it takes no body, obj(...) of its required parameters. */
        private String commandBody(String command) {
            for (EntitySpec.CommandSpec cmd : e.commands()) {
                if (!cmd.name().equals(command)) continue;
                if (cmd.dtoName() == null) return "null";
                List<String[]> parts = new ArrayList<String[]>();
                for (FieldSpec p : cmd.params()) {
                    if (!sent(p)) continue;
                    String v = value(
                            e,
                            dto,
                            p,
                            java.util.Collections.<Promise>emptyList(),
                            new ArrayDeque<String>(),
                            new Builder(e));
                    if (v == null) return null;
                    parts.add(new String[] {p.name(), v});
                }
                return obj(parts);
            }
            return "null";
        }

        /** The JSON name the answer shows the lifecycle's field by, or null when it does not show it. */
        private String stateJson(String fieldName) {
            FieldSpec f = field(dto, fieldName);
            if (f == null || f.hasDslAny("writeonly", "nooutput")) return null;
            return json(f);
        }

        private String stateValue(EntitySpec.StateMachine m, String state) {
            String v = enumValue(
                    dto.isKnownEnum(simple(field(e, m.field()).typeName())) ? dto : e,
                    simple(field(e, m.field()).typeName()),
                    state);
            return v == null ? state : v;
        }

        // ---- constraints

        /** Each constraint of what a request writes, at its boundary and one past it. */
        void constraints(Builder own) {
            String pathConstant = constant(e);
            FieldSpec pk = e.pkFields().size() == 1 ? e.pkFields().get(0) : null;
            for (FieldSpec f : dto.fields()) {
                if (removedKey(dto, f)) continue;
                String ref = reference(f);
                if (ref == null && !writable(e, f)) continue;
                if (pk != null && f.name().equals(pk.name()) && ServiceAccessRules.keyGenerated(pk)) continue;
                String name = json(f);
                if (f.required() && (ref != null || writable(e, f))) {
                    Test t = test(f.name() + "IsRequired", f.name() + " is required (400 NotNull)");
                    t.lines.add(lang.stmt("assertFieldError(post(" + pathConstant + ", withValue(" + newRow() + ", "
                            + lang.string(name) + ", null)), " + lang.string(f.name()) + ", \"NotNull\")"));
                }
                if (ref != null) continue;
                if (f.isList()) {
                    listSize(f, pathConstant);
                    continue;
                }
                String type = simple(f.typeName());
                if (dto.isKnownEnum(type) || dto.isEmbeddableType(type)) continue;
                if (f.isStringType()) {
                    textMarks(f, pathConstant);
                    strings(f, pathConstant);
                    continue;
                }
                if (temporalKind(f) != null) {
                    clockMarks(f, pathConstant);
                    continue;
                }
                if (numeric(f)) bounds(f, pathConstant);
            }
        }

        /**
         * The marks @notblank and @email, as the DTO writes them on text: a value passes,
         * blank text is refused (400 NotBlank), and the address without its @
         * (400 Email).
         */
        private void textMarks(FieldSpec f, String pathConstant) {
            String name = json(f);
            boolean blank = blankRefused(f);
            boolean email = f.hasDsl("email");
            if (!blank && !email) return;
            String passes = value(e, dto, f, promises, new ArrayDeque<String>(), new Builder(e));
            if (passes == null) {
                skip(e.getName() + "." + f.name() + " @" + (blank ? "notblank" : "email") + ": no value of "
                        + f.typeName() + " can be built");
                return;
            }
            if (blank) {
                Test t = test(f.name() + "NotBlank", f.name() + ": text passes, blank text is refused (400 NotBlank)");
                t.lines.add(lang.stmt("assertCreated(post(" + pathConstant + ", withValue(" + newRow() + ", "
                        + lang.string(name) + ", " + passes + ")))"));
                t.lines.add(lang.stmt("assertFieldError(post(" + pathConstant + ", withValue(" + newRow() + ", "
                        + lang.string(name) + ", \"   \")), " + lang.string(f.name()) + ", \"NotBlank\")"));
            }
            if (!email) return;
            if (f.hasDsl("pattern")) {
                // The value is the pattern's: an address only where the pattern makes one.
                int max = f.validationMaxLength();
                String sample = PatternSample.matching(regex(f), Math.max(0, f.minLength()), max >= 0 ? max : 64);
                if (sample == null || sample.indexOf('@') <= 0) {
                    skip(e.getName() + "." + f.name() + " @email: the value its @pattern makes is no address");
                    return;
                }
            }
            Test t = test(
                    f.name() + "Email",
                    f.name() + ": an address passes, the address without its @ is refused (400 Email)");
            t.lines.add(lang.stmt("assertCreated(post(" + pathConstant + ", withValue(" + newRow() + ", "
                    + lang.string(name) + ", " + passes + ")))"));
            t.lines.add(lang.stmt("assertFieldError(post(" + pathConstant + ", withValue(" + newRow() + ", "
                    + lang.string(name) + ", " + passes + ".replace(\"@\", \"\"))), " + lang.string(f.name())
                    + ", \"Email\")"));
        }

        /**
         * The marks @past, @pastorpresent, @future and @futureorpresent on a date or a
         * time: a year ago (from now) passes, a moment past now the other way is
         * refused - read from the clock when the test runs, so no time zone moves
         * it across now: a date two days from today (a midnight passing during
         * the request leaves it a day away), a datetime or a timestamp one day.
         */
        private void clockMarks(FieldSpec f, String pathConstant) {
            String name = json(f);
            String[][] marks = {
                {"past", "Past"},
                {"pastorpresent", "PastOrPresent"},
                {"future", "Future"},
                {"futureorpresent", "FutureOrPresent"}
            };
            boolean past = f.hasDslAny("past", "pastorpresent");
            boolean future = f.hasDslAny("future", "futureorpresent");
            if (past && future) {
                skip(e.getName() + "." + f.name() + ": @past and @future together - no value is both");
                return;
            }
            for (String[] mark : marks) {
                if (!f.hasDsl(mark[0])) continue;
                boolean before = mark[0].startsWith("past");
                String passes = moment(f, before ? -365 : 365, lang);
                int days = "date".equals(temporalKind(f)) ? 2 : 1;
                String refused = moment(f, before ? days : -days, lang);
                if (passes == null || refused == null) {
                    skip(e.getName() + "." + f.name() + " @" + mark[0] + ": "
                            + ("time".equals(temporalKind(f))
                                    ? "a time of day is past or future by the hour the test runs at"
                                    : "its format writes no date"));
                    continue;
                }
                Test t = test(
                        f.name() + mark[1],
                        f.name() + ": a year " + (before ? "ago" : "from now") + " passes, "
                                + (days == 2
                                        ? (before ? "two days ahead" : "two days ago")
                                        : (before ? "tomorrow" : "yesterday"))
                                + " is refused (400 " + mark[1] + ")");
                t.lines.add(lang.stmt("assertCreated(post(" + pathConstant + ", withValue(" + newRow() + ", "
                        + lang.string(name) + ", " + passes + ")))"));
                t.lines.add(lang.stmt("assertFieldError(post(" + pathConstant + ", withValue(" + newRow() + ", "
                        + lang.string(name) + ", " + refused + ")), " + lang.string(f.name()) + ", "
                        + lang.string(mark[1]) + ")"));
            }
        }

        private void strings(FieldSpec f, String pathConstant) {
            String name = json(f);
            String regexp = regex(f);
            int max = f.validationMaxLength();
            int min = f.minLength();
            if (regexp != null) {
                String matching = PatternSample.matching(regexp, Math.max(min, 0), max >= 0 ? max : 64);
                String other = PatternSample.notMatching(regexp, matching);
                if (matching != null && other == null && PatternSample.lookaheads(regexp)) {
                    // What a lookahead refuses is what is tested: a value the rest refuses would not show it.
                    skip(e.getName() + "." + f.name() + " @pattern: no value the generator can build matches the rest"
                            + " of " + f.patternRegexp() + " and misses one of its lookaheads");
                } else if (matching == null || other == null) {
                    skip(e.getName() + "." + f.name() + " @pattern: no value the generator can build "
                            + (matching == null ? "matches" : "fails") + " " + f.patternRegexp());
                } else {
                    Test t = test(
                            f.name() + "Pattern",
                            f.name() + ": a value that matches passes, one that does not is"
                                    + " refused (400 Pattern)");
                    // Varied per row where the pattern lets it: a unique field takes a value of its own each time.
                    String passes = text(f, false);
                    t.lines.add(lang.stmt("assertCreated(post(" + pathConstant + ", withValue(" + newRow() + ", "
                            + lang.string(name) + ", " + (passes != null ? passes : lang.string(matching)) + ")))"));
                    t.lines.add(lang.stmt("assertFieldError(post(" + pathConstant + ", withValue(" + newRow() + ", "
                            + lang.string(name) + ", " + lang.string(other) + ")), " + lang.string(f.name())
                            + ", \"Pattern\")"));
                }
                // A length past the bound is refused whatever it holds; the bound itself only by a value that matches.
                if (max >= 0) patternLength(f, pathConstant, regexp, max, max + 1);
                if (min > 0) patternLength(f, pathConstant, regexp, min, min - 1);
                return;
            }
            boolean email = f.hasDsl("email");
            if (max >= 0) {
                if (email && max < 6) {
                    skip(e.getName() + "." + f.name() + " length: no email is " + max + " characters long");
                } else {
                    Test t = test(
                            f.name() + "Length",
                            f.name() + ": " + max + " characters pass, " + (max + 1) + " are refused (400 Size)");
                    String fits = email ? "email(" + max + ")" : "text(" + max + ")";
                    String past = email ? "email(" + (max + 1) + ")" : "text(" + (max + 1) + ")";
                    t.lines.add(lang.stmt("assertCreated(post(" + pathConstant + ", withValue(" + newRow() + ", "
                            + lang.string(name) + ", " + fits + ")))"));
                    t.lines.add(lang.stmt("assertFieldError(post(" + pathConstant + ", withValue(" + newRow() + ", "
                            + lang.string(name) + ", " + past + ")), " + lang.string(f.name()) + ", \"Size\")"));
                }
            }
            if (min > 0) {
                if (email && min < 7) {
                    skip(e.getName() + "." + f.name() + " length: no email is " + (min - 1) + " characters long");
                } else {
                    Test t = test(
                            f.name() + "LeastLength",
                            f.name() + ": " + min + " characters pass, " + (min - 1) + " are refused (400 Size)");
                    String fits = email ? "email(" + min + ")" : "text(" + min + ")";
                    String past = email ? "email(" + (min - 1) + ")" : "text(" + (min - 1) + ")";
                    t.lines.add(lang.stmt("assertCreated(post(" + pathConstant + ", withValue(" + newRow() + ", "
                            + lang.string(name) + ", " + fits + ")))"));
                    t.lines.add(lang.stmt("assertFieldError(post(" + pathConstant + ", withValue(" + newRow() + ", "
                            + lang.string(name) + ", " + past + ")), " + lang.string(f.name()) + ", \"Size\")"));
                }
            }
        }

        /** The length of a field with @pattern: a matching value of the bound passes, when one exists; one past it is refused. */
        private void patternLength(FieldSpec f, String pathConstant, String regexp, int bound, int past) {
            String name = json(f);
            String fits = PatternSample.matching(regexp, bound, bound);
            Test t = test(
                    f.name() + (past > bound ? "Length" : "LeastLength"),
                    f.name() + ": " + (fits == null ? "" : bound + " characters pass, ") + past
                            + (past == 1 ? " character is" : " characters are") + " refused (400 Size)");
            if (fits != null) {
                t.lines.add(lang.stmt("assertCreated(post(" + pathConstant + ", withValue(" + newRow() + ", "
                        + lang.string(name) + ", " + lang.string(fits) + ")))"));
            }
            t.lines.add(lang.stmt("assertFieldError(post(" + pathConstant + ", withValue(" + newRow() + ", "
                    + lang.string(name) + ", text(" + past + "))), " + lang.string(f.name()) + ", \"Size\")"));
        }

        private void bounds(FieldSpec f, String pathConstant) {
            String name = json(f);
            Range promised = new Range();
            String promise = null;
            for (Promise p : promises) {
                if (f.name().equals(p.field) && !p.size && p.bound != null) {
                    bound(promised, p.operator, p.bound);
                    promise = p.condition;
                }
            }
            String[][] ends = {{"min", "Min", "atLeast"}, {"max", "Max", "atMost"}};
            for (String[] end : ends) {
                String declared = "min".equals(end[0])
                        ? f.dslValueAny("min", "minval", "minvalue")
                        : f.dslValueAny("max", "maxval", "maxvalue");
                BigDecimal v = number(declared);
                if (v == null) continue;
                if (whole(f) && v.stripTrailingZeros().scale() > 0) {
                    skip(e.getName() + "." + f.name() + " @" + end[0] + "(" + plain(v) + "): a whole number field");
                    continue;
                }
                if (float32(f) && fractional(v)) {
                    skip(e.getName() + "." + f.name() + " @" + end[0] + "(" + plain(v) + "): a float holds no exact "
                            + plain(v) + ", and its check compares the float widened to a double");
                    continue;
                }
                BigDecimal past = "min".equals(end[0]) ? v.subtract(step(f)) : v.add(step(f));
                if (!promised.admits(v)) {
                    skip(e.getName() + "." + f.name() + " @" + end[0] + "(" + plain(v) + "): @invariant(" + promise
                            + ") refuses the boundary");
                    continue;
                }
                // As the DTO writes it: the bound as declared decides (@max(5.0) on a double is a DecimalMax).
                String code = DtoBaseRules.decimalBound(f, DtoBaseRules.Bound.split(declared)[0].trim(), false)
                        ? "Decimal" + end[1]
                        : end[1];
                Test t = test(
                        f.name() + end[1],
                        f.name() + ": " + plain(v) + " passes, " + plain(past) + " is refused (400 " + code + ")");
                t.lines.add(lang.stmt("assertCreated(post(" + pathConstant + ", withValue(" + newRow() + ", "
                        + lang.string(name) + ", num(" + lang.string(plain(v)) + "))))"));
                t.lines.add(lang.stmt("assertFieldError(post(" + pathConstant + ", withValue(" + newRow() + ", "
                        + lang.string(name) + ", num(" + lang.string(plain(past)) + "))), " + lang.string(f.name())
                        + ", " + lang.string(code) + ")"));
            }
        }

        /** The least size a list takes, minsize(n): n members pass (the row as built), n - 1 are refused (400 Size). */
        private void listSize(FieldSpec f, String pathConstant) {
            BigDecimal least = number(f.dslValueAny("minsize"));
            if (least == null || least.intValue() < 1) return;
            int n = least.intValue();
            String member = member(e, f, new ArrayDeque<String>(), new Builder(e));
            if (member == null) return;
            List<String> fewer = new ArrayList<String>();
            for (int i = 0; i < n - 1; i++) fewer.add(member);
            Test t = test(f.name() + "Size", f.name() + ": " + n + " pass, " + (n - 1) + " are refused (400 Size)");
            t.lines.add(lang.stmt("assertCreated(post(" + pathConstant + ", " + newRow() + "))"));
            t.lines.add(lang.stmt("assertFieldError(post(" + pathConstant + ", withValue(" + newRow() + ", "
                    + lang.string(json(f)) + ", list(" + String.join(", ", fewer) + "))), " + lang.string(f.name())
                    + ", \"Size\")"));
        }

        // ---- invariants

        /** Each promise the generator can read: its boundary passes, one past it is refused (422, its code). */
        void invariants(Builder own) {
            String pathConstant = constant(e);
            for (Promise p : promises) {
                FieldSpec f = p.field == null ? null : field(dto, p.field);
                if (p.operator == null || f == null) {
                    skip(e.getName() + " @invariant(" + p.condition + "): only a field compared with a number, a"
                            + " collection's size, notNull and notEmpty are tested");
                    continue;
                }
                String name = json(f);
                if (p.size || "notEmpty".equals(p.operator)) {
                    int least = members(f, java.util.Collections.singletonList(p));
                    if (least < 1 || ("<".equals(p.operator) || "<=".equals(p.operator))) {
                        skip(e.getName() + " @invariant(" + p.condition + "): only a least size is tested");
                        continue;
                    }
                    BigDecimal minsize = number(f.dslValueAny("minsize"));
                    if ((minsize != null && minsize.intValue() >= least) || f.required()) {
                        skip(e.getName() + " @invariant(" + p.condition
                                + "): the DTO refuses fewer members first (400)");
                        continue;
                    }
                    String member = member(e, f, new ArrayDeque<String>(), new Builder(e));
                    if (member == null) continue;
                    List<String> fewer = new ArrayList<String>();
                    for (int i = 0; i < least - 1; i++) fewer.add(member);
                    Test t = test(
                            "invariant" + camel(f.name()) + "Size",
                            "@invariant(" + p.condition + "): " + least + " pass, " + (least - 1) + " are refused (422 "
                                    + p.code + ")");
                    t.lines.add(lang.stmt("assertCreated(post(" + pathConstant + ", " + newRow() + "))"));
                    t.lines.add(lang.stmt("assertRefused(post(" + pathConstant + ", withValue(" + newRow() + ", "
                            + lang.string(name) + ", list(" + String.join(", ", fewer) + "))), 422, "
                            + lang.string(p.code) + ")"));
                    continue;
                }
                if ("notNull".equals(p.operator)) {
                    if (f.required()) {
                        skip(e.getName() + " @invariant(" + p.condition + "): " + f.name() + " is required - the DTO"
                                + " refuses it first (400)");
                        continue;
                    }
                    Test t = test(
                            "invariant" + camel(f.name()) + "NotNull",
                            "@invariant(" + p.condition + "): a " + e.getName() + " without " + f.name()
                                    + " is refused (422 " + p.code + ")");
                    t.lines.add(lang.stmt("assertCreated(post(" + pathConstant + ", " + newRow() + "))"));
                    t.lines.add(lang.stmt("assertRefused(post(" + pathConstant + ", withValue(" + newRow() + ", "
                            + lang.string(name) + ", null)), 422, " + lang.string(p.code) + ")"));
                    continue;
                }
                if (!numeric(f)) {
                    skip(e.getName() + " @invariant(" + p.condition + "): " + f.name() + " is not a number");
                    continue;
                }
                if (float32(f) && fractional(p.bound)) {
                    skip(e.getName() + " @invariant(" + p.condition + "): a float holds no exact " + plain(p.bound));
                    continue;
                }
                BigDecimal[] whole = whole(f) ? wholeEnds(p.operator, p.bound) : null;
                if (whole(f) && whole == null) {
                    skip(e.getName() + " @invariant(" + p.condition + "): no whole number of " + f.name() + " is "
                            + plain(p.bound));
                    continue;
                }
                BigDecimal step = step(f);
                BigDecimal passes;
                BigDecimal fails;
                switch (whole != null ? "whole" : p.operator) {
                    case "whole":
                        passes = whole[0];
                        fails = whole[1];
                        break;
                    case ">":
                        passes = p.bound.add(step);
                        fails = p.bound;
                        break;
                    case ">=":
                        passes = p.bound;
                        fails = p.bound.subtract(step);
                        break;
                    case "<":
                        passes = p.bound.subtract(step);
                        fails = p.bound;
                        break;
                    case "<=":
                        passes = p.bound;
                        fails = p.bound.add(step);
                        break;
                    default:
                        passes = p.bound;
                        fails = p.bound.add(step);
                        break;
                }
                Range dtoBounds = dtoRange(f);
                if (!dtoBounds.admits(fails) || !dtoBounds.admits(passes)) {
                    skip(e.getName() + " @invariant(" + p.condition
                            + "): the DTO's @min/@max refuse the value first (400)");
                    continue;
                }
                Test t = test(
                        "invariant" + camel(f.name()),
                        "@invariant(" + p.condition + "): " + plain(passes) + " passes, " + plain(fails)
                                + " is refused (422 " + p.code + ")");
                t.lines.add(lang.stmt("assertCreated(post(" + pathConstant + ", withValue(" + newRow() + ", "
                        + lang.string(name) + ", num(" + lang.string(plain(passes)) + "))))"));
                t.lines.add(lang.stmt("assertRefused(post(" + pathConstant + ", withValue(" + newRow() + ", "
                        + lang.string(name) + ", num(" + lang.string(plain(fails)) + "))), 422, "
                        + lang.string(p.code) + ")"));
            }
        }

        // ---- @access

        /** How many of the tests are of @access: the support class then signs their callers in. */
        int accessTests;

        /** The levels @access names, operation -> its text as AccessPolicy reads it ("*" the default). */
        private final Map<String, String> entries = new LinkedHashMap<String, String>();

        /** How a test makes a row of a given owner: self, sent, claimed - or null, and ownershipProblem says why. */
        private String ownership;

        private String ownershipProblem;
        private FieldSpec ownerDto;
        private Callers callers;
        private Principal principal;

        boolean declaresAccess() {
            String declared = e.metaText("access");
            return declared != null && !declared.trim().isEmpty();
        }

        /**
         * The tests of @access: each operation the API serves - read, search, create,
         * update, delete and each command - at the level its entry gives it, else the
         * default (*), else admin. A caller each term of it lets in passes; a visitor, a
         * caller of a role no term lets in and, where owner is a word, a caller on
         * another's row are refused - by the status and code the generated checks
         * answer. Each test signs in principals of its own (signIn(role)) and makes its
         * own rows. What the generator cannot test is counted, with why.
         */
        void access(Principal p, String noPrincipal) {
            if (!declaresAccess()) return;
            String name = e.getName();
            if (p == null) {
                accessSkip(name + " @access: not tested - no caller can be signed in ("
                        + (noPrincipal != null ? noPrincipal : "the run has no generated sign-in, @signin") + ")");
                return;
            }
            for (String part : e.metaText("access").split(",", -1)) {
                if (part.trim().isEmpty()) continue;
                java.util.regex.Matcher m =
                        ACCESS_ENTRY.matcher(part.replace("'", "").replace("\"", ""));
                AccessExpression x = m.matches() ? AccessExpression.parse(m.group(2)) : null;
                if (x == null) {
                    accessSkip(name + " @access: '" + part.trim() + "' is not read as operation=level, so none of it"
                            + " is tested (the application would not start)");
                    entries.clear();
                    return;
                }
                entries.put(m.group(1), x.text());
            }
            principal = p;
            callers = new Callers(p, entries.values());
            ownership();
            int before = tests.size();
            if (e.pkFields().size() != 1) {
                accessSkip(name + " @access: a composite key - only create is tested");
            }
            boolean rows = e.pkFields().size() == 1;
            if (rows && !ControllerBaseRules.exposeOmitted(e).contains("read")) accessOf("read");
            if (rows && !ControllerBaseRules.exposeOmitted(e).contains("search")) accessOf("search");
            accessOf("create");
            if (rows && updates()) accessOf("update");
            if (rows && deletes()) accessOf("delete");
            if (rows && s.commands && DddBehaviorRules.splits(e)) {
                // POST /{id}/command: each declared command, and each move of a lifecycle.
                Set<String> done = new LinkedHashSet<String>();
                for (EntitySpec.CommandSpec c : e.commands()) done.add(c.name());
                if (s.lifecycles) {
                    for (EntitySpec.StateMachine m : e.stateMachines()) {
                        for (EntitySpec.Move mv : m.moves()) {
                            if (DddStateRules.writesMove(e, mv.command())) done.add(mv.command());
                        }
                    }
                }
                for (String c : done) accessOf(c);
            }
            accessTests = tests.size() - before;
        }

        /** Whether the API serves an update of one row: PUT, not left out, and not a root with nothing to edit. */
        private boolean updates() {
            if (e.hasMeta("readonly")) return false;
            if (ControllerBaseRules.exposeOmitted(e).contains("update")) return false;
            if (ControllerBaseRules.restExcluded(e, true).contains("update")) return false;
            return !(s.commands && DddCommandRules.nothingEditable(e));
        }

        private boolean deletes() {
            if (e.hasMeta("readonly")) return false;
            if (ControllerBaseRules.exposeOmitted(e).contains("deleteById")) return false;
            return !ControllerBaseRules.restExcluded(e, true).contains("deleteById");
        }

        /**
         * How a row of a given owner is made: the caller's own row where the entity is
         * the one they sign in as and @owner is its key (self); one the administrator
         * makes with the owner set, where a request may send it (sent); one the owner
         * makes, who owns what they create (claimed).
         */
        private void ownership() {
            FieldSpec owner = ServiceAccessRules.ownerField(e);
            if (owner == null) {
                ownershipProblem = e.metaValue("owner") == null
                        ? "it has no @owner"
                        : "@owner(" + e.metaValue("owner").trim() + ") names no field the row holds";
                return;
            }
            FieldSpec pk = e.pkFields().size() == 1 ? e.pkFields().get(0) : null;
            boolean ownerIsKey = pk != null && pk.name().equals(owner.name());
            if (ownerIsKey && principal.spec != null && path(principal.spec).equals(path(e))) {
                ownership = "self";
                return;
            }
            if (ownerIsKey && ServiceAccessRules.keyGenerated(pk)) {
                ownershipProblem = "@owner(" + owner.name() + ") is its generated key, which no caller's is";
                return;
            }
            FieldSpec sent = field(dto, owner.name());
            // The administrator keeps the owner a request names; anyone else owns what they create. The rows the
            // tests make go through signIn() - an administrator only where the principal has a role to be one by.
            if (sent != null
                    && callers.adminSignsIn()
                    && !(ownerIsKey && ServiceAccessRules.keyGenerated(pk))
                    && writable(e, sent)
                    && !sent.hasDslAny("writeonly")
                    && !io.elasticore.base.model.pub.rules.base.WriteGuardRules.serverSet(sent)) {
                ownership = "sent";
                ownerDto = sent;
                return;
            }
            ownership = "claimed";
        }

        /** The level of an operation as the generated checks read it: its entry, else *, else admin. */
        private AccessExpression level(String operation) {
            String text = entries.containsKey(operation)
                    ? entries.get(operation)
                    : entries.containsKey("*") ? entries.get("*") : "admin";
            return AccessExpression.parse(text);
        }

        /**
         * What the generated code answers the caller, as AccessPolicy and the service
         * call it: null when let in, else 401, 403 or 404. A read and a write of one row
         * ask the level, then whether the row is the caller's (404 when it is not); the
         * API reads the row before it deletes it, so a delete asks the read first.
         */
        String outcome(String op, Who w, boolean mine) {
            if ("delete".equals(op)) {
                String read = outcome("read", w, mine);
                if (read != null) return read;
            }
            AccessExpression x = level("search".equals(op) ? "read" : op);
            String refused = Policy.require(x, w);
            if (refused != null || "create".equals(op) || "search".equals(op)) return refused;
            return Policy.owns(x, w, mine);
        }

        /** The tests of one operation. */
        private void accessOf(String op) {
            boolean search = "search".equals(op);
            boolean create = "create".equals(op);
            String levelOp = search ? "read" : op;
            AccessExpression x = level(levelOp);
            String said = (search ? "search, read=" : op + "=")
                    + x.text()
                    + (entries.containsKey(levelOp) ? "" : entries.containsKey("*") ? " (from *)" : " (the default)");
            String where = e.getName() + " " + said;
            boolean ownerWord =
                    x.mentions("owner") || ("delete".equals(op) && level("read").mentions("owner"));
            if (!create && ownerWord && ownership == null) {
                accessSkip(where + ": owner is a word of it, and " + ownershipProblem + " - so no row of a caller's"
                        + " own is made");
                return;
            }
            String steps = null;
            if ("delete".equals(op) || (!create && !search && !"read".equals(op) && !"update".equals(op))) {
                steps = stepsTo(op);
            }
            String body = null;
            if (!create && !search && !"read".equals(op) && !"update".equals(op) && !"delete".equals(op)) {
                body = commandBody(op);
                if (body == null) {
                    accessSkip(where + ": no value can be built for a parameter the command requires");
                    return;
                }
            }
            Set<String> seen = new java.util.HashSet<String>();
            // A caller each term lets in.
            for (List<String> term : x.terms()) {
                if (term.contains("none")) continue;
                Who w = callers.forTerm(term);
                if (w == null) {
                    accessSkip(where + ": " + callers.problem);
                    continue;
                }
                boolean mine = term.contains("owner") && !create;
                // A row not theirs first; theirs where the read before a delete would refuse another's.
                if (!create && !mine && outcome(op, w, false) != null && canOwn(w)) mine = true;
                if (outcome(op, w, mine) != null) {
                    accessSkip(where + ": " + w.label + " is let in by it, and refused "
                            + ("delete".equals(op)
                                    ? "by read=" + level("read").text() + " first - the API reads the row before it"
                                            + " deletes it"
                                    : "on a row not theirs"));
                    continue;
                }
                if (!seen.add("in:" + w.key() + mine)) continue;
                if (steps == null && needsState(op)) {
                    accessSkip(where + ": no sequence of commands brings a new row to a state it is given in");
                    continue;
                }
                caseOf(op, said, w, mine, false, null, steps, body);
            }
            // A visitor, where the entry does not let one in.
            Who visitor = callers.visitor();
            String refusal = outcome(op, visitor, false);
            if (refusal != null) caseOf(op, said, visitor, false, false, refusal, steps, body);
            // A caller of a role no term lets in - on a row of theirs, where owner is a word: the role decides.
            for (Who w : callers.candidates()) {
                boolean byRole = Policy.require(x, w) != null
                        || ("delete".equals(op) && Policy.require(level("read"), w) != null);
                if (!byRole) continue;
                boolean mine = !create && ownerWord && canOwn(w);
                String r = outcome(op, w, mine);
                if (r != null && seen.add("role:" + w.key())) caseOf(op, said, w, mine, false, r, steps, body);
                break;
            }
            // Where owner is a word: a caller each owner term lets in, on another's row.
            if (create || !x.mentions("owner")) return;
            for (List<String> term : x.terms()) {
                if (!term.contains("owner") || term.contains("none")) continue;
                Who w = callers.forTerm(term);
                if (w == null || !seen.add("other:" + w.key())) continue;
                if (search) {
                    if (Policy.require(x, w) != null || Policy.seesAll(x, w)) continue;
                    caseOf(op, said, w, false, true, null, steps, body);
                    continue;
                }
                String r = outcome(op, w, false);
                if (r != null) caseOf(op, said, w, false, true, r, steps, body);
            }
        }

        /** Whether the operation is a move of a lifecycle, or a delete a lifecycle guards: a new row may not be where it is given. */
        private boolean needsState(String op) {
            if ("delete".equals(op)) return s.lifecycles && DddStateRules.guardsDelete(e);
            if (!s.lifecycles) return false;
            for (EntitySpec.StateMachine m : e.machinesOf(op)) {
                if (e.lifecycleProblem(m) == null) return true;
            }
            return false;
        }

        /**
         * The commands that bring a new row to where the operation is given - a state
         * each guarding lifecycle deletes from, or one the command moves from - as
         * given(...) lines; "" when a new row is there already, null when none does.
         */
        private String stepsTo(String op) {
            if (!needsState(op)) return "";
            List<EntitySpec.StateMachine> machines = new ArrayList<EntitySpec.StateMachine>();
            for (EntitySpec.StateMachine m : e.stateMachines()) {
                if (e.lifecycleProblem(m) == null) machines.add(m);
            }
            Map<String, String> bodies = new LinkedHashMap<String, String>();
            for (EntitySpec.StateMachine m : machines) {
                for (EntitySpec.Move mv : m.moves()) {
                    String c = mv.command();
                    if (bodies.containsKey(c) || !DddStateRules.writesMove(e, c)) continue;
                    String b = commandBody(c);
                    if (b != null) bodies.put(c, b);
                }
            }
            Graph graph = new Graph(machines, bodies.keySet());
            if (graph.start == null) return null;
            for (Map.Entry<String, List<String>> en : graph.paths.entrySet()) {
                String[] tuple = graph.tupleOf(en.getKey());
                boolean there = true;
                for (int i = 0; i < machines.size(); i++) {
                    EntitySpec.StateMachine m = machines.get(i);
                    if ("delete".equals(op)) {
                        if (m.deletable() != null
                                && (tuple[i] == null || !m.deletable().contains(tuple[i]))) there = false;
                    }
                }
                if (!"delete".equals(op) && graph.refusal(op, tuple) != null) there = false;
                if (!there) continue;
                StringBuilder lines = new StringBuilder();
                List<String> at = en.getValue();
                for (int i = 1; i < at.size(); i++) {
                    String c = at.get(i).substring(0, at.get(i).indexOf('@'));
                    if (lines.length() > 0) lines.append('\n');
                    lines.append(lang.stmt("given(command(" + constant(e) + ", id, "
                            + lang.string(DddCommandRules.kebab(c)) + ", " + bodies.get(c) + "))"));
                }
                return lines.toString();
            }
            return null;
        }

        /**
         * One test: the caller signed in, the row made - theirs, another's, or one the
         * administrator made - the steps to the state the operation is given in, and the
         * request: let in (refusal null) or refused with that status and code.
         */
        private void caseOf(
                String op,
                String said,
                Who w,
                boolean mine,
                boolean anothers,
                String refusal,
                String steps,
                String body) {
            boolean search = "search".equals(op);
            boolean create = "create".equals(op);
            String path = constant(e);
            String key = e.pkFields().size() == 1 ? json(e.pkFields().get(0)) : null;
            boolean hidden = search && anothers && refusal == null;
            String on = create || search ? "" : mine ? " on a row of theirs" : anothers ? " on another's row" : "";
            String display;
            String method;
            if (hidden) {
                display = said + ": " + w.label + " does not find another's row";
                method = op + "HidesAnothersRowFrom" + w.method;
            } else if (refusal == null) {
                display = said + ": " + w.label
                        + (search ? (mine ? " finds a row of theirs" : " finds the row") : " is let in" + on);
                method = op + "LetsIn" + w.method + (mine ? "OnTheirRow" : "");
            } else {
                display =
                        said + ": " + w.label + " is refused" + on + " (" + refusal + " " + Policy.code(refusal) + ")";
                method = op + "Refuses" + w.method + (mine ? "OnTheirRow" : anothers ? "OnAnothersRow" : "");
            }
            Test t = new Test(method(method), display);
            List<String> lines = t.lines;
            String caller = w.visitor ? "null" : "caller";
            String id = null;
            if (!create) {
                if (anothers) {
                    Who someone = callers.plain();
                    if (someone == null) {
                        accessSkip(e.getName() + " " + said + ": no caller of another role can be signed in to own"
                                + " the row");
                        return;
                    }
                    lines.add(lang.caller("owner", "signIn(" + roleArg(someone) + ")"));
                    if (!row(lines, "owner", someone, e.getName() + " " + said)) return;
                } else if (mine) {
                    lines.add(lang.caller("caller", "signIn(" + roleArg(w) + ")"));
                    if (!row(lines, "caller", w, e.getName() + " " + said)) return;
                } else {
                    if (!row(lines, null, null, e.getName() + " " + said)) return;
                }
                id = "id";
                if (refusal == null && steps != null && !steps.isEmpty()) {
                    for (String step : steps.split("\n", -1)) lines.add(step);
                }
            }
            if (!w.visitor && !mine) lines.add(lang.caller("caller", "signIn(" + roleArg(w) + ")"));
            String request;
            switch (create ? "create" : search ? "search" : op) {
                case "create":
                    request = "creating(" + path + ", " + newRow() + ")";
                    break;
                case "search":
                    request = "searching(" + path + ", " + searchBody(id) + ")";
                    break;
                case "read":
                    request = "reading(" + path + ", id)";
                    break;
                case "update":
                    request = "updating(" + path + ", obj(" + lang.string(key) + ", id))";
                    break;
                case "delete":
                    request = "deleting(" + path + ", id)";
                    break;
                default:
                    request = "commanding(" + path + ", id, " + lang.string(DddCommandRules.kebab(op)) + ", " + body
                            + ")";
            }
            String sent = "sentBy(" + caller + ", " + request + ")";
            if (refusal != null) {
                lines.add(lang.stmt(
                        "assertDenied(" + sent + ", " + refusal + ", " + lang.string(Policy.code(refusal)) + ")"));
            } else if (search) {
                if (searchKey() == null) {
                    accessSkip(e.getName() + " " + said + ": its search takes no " + key + ", so no search is narrowed"
                            + " to the test's row to find it");
                    if (!anothers) return;
                }
                lines.add(lang.stmt(
                        (anothers ? "assertMisses(" : "assertFinds(") + sent + ", " + lang.string(key) + ", id)"));
            } else {
                lines.add(lang.stmt("assertAllowed(" + sent + ")"));
            }
            tests.add(t);
        }

        /** Whether a row of this caller's can be made: as ownership says, the owner making it where the server sets @owner. */
        private boolean canOwn(Who w) {
            if (ownership == null || w.visitor) return false;
            return !"claimed".equals(ownership) || outcome("create", w, false) == null;
        }

        /** signIn's argument for the caller: the role as the principal's field takes it, or null. */
        private String roleArg(Who w) {
            return w.json == null ? "null" : lang.string(w.json);
        }

        /**
         * The line that makes the row a test checks, its key in id: of the caller named
         * (as ownership says), or - for none - one the administrator makes. False, with
         * why counted, when it cannot be made.
         */
        private boolean row(List<String> lines, String ownerVar, Who ownerWho, String where) {
            String path = constant(e);
            String key = lang.string(json(e.pkFields().get(0)));
            if (ownerVar == null) {
                lines.add(lang.val("id", "createdId(" + path + ", " + newRow() + ", " + key + ")"));
                return true;
            }
            if ("self".equals(ownership)) {
                lines.add(lang.val("id", ownerVar + ".id"));
                return true;
            }
            if ("sent".equals(ownership)) {
                lines.add(lang.val(
                        "id",
                        "createdId(" + path + ", withValue(" + newRow() + ", " + lang.string(json(ownerDto)) + ", "
                                + ownerVar + ".id), " + key + ")"));
                return true;
            }
            if ("claimed".equals(ownership) && outcome("create", ownerWho, false) == null) {
                lines.add(lang.val("id", "createdIdBy(" + ownerVar + ", " + path + ", " + newRow() + ", " + key + ")"));
                return true;
            }
            accessSkip(where + ": " + ownerWho.label + " cannot be made the owner of a row - the server sets @owner,"
                    + " and create=" + level("create").text() + " does not let them make one");
            return false;
        }

        /** The search's field of the key, as the request names it; null when the search takes none. */
        private String searchKey() {
            if (e.pkFields().size() != 1) return null;
            EntitySpec srch = searchDto(e);
            FieldSpec f = srch == null ? null : field(srch, e.pkFields().get(0).name());
            return f == null || f.isList() ? null : json(f);
        }

        /** A search narrowed to the row of key id, where the search takes the key; any row otherwise. */
        private String searchBody(String id) {
            String k = searchKey();
            return k == null || id == null ? "obj()" : "obj(" + lang.string(k) + ", " + id + ")";
        }

        // ---- the class

        String render(String pkg, String support) {
            StringBuilder s = new StringBuilder();
            s.append(OWNED).append('\n');
            s.append("package ").append(pkg).append(lang.kotlin ? "\n\n" : ";\n\n");
            s.append(lang.importLine("org.junit.jupiter.api.DisplayName"));
            s.append(lang.importLine("org.junit.jupiter.api.Test"));
            boolean results = false;
            for (Test t : tests) {
                for (String line : t.lines) results |= line.startsWith("MvcResult ");
            }
            if (results) s.append(lang.importLine("org.springframework.test.web.servlet.MvcResult"));
            if (!lang.kotlin) s.append("\nimport java.util.Map;\n");
            s.append('\n');
            s.append("/**\n")
                    .append(" * What the DSL declares of ")
                    .append(e.getName())
                    .append(", checked over its API (testCode: contract): each\n")
                    .append(" * move of its lifecycles, and the constraints of what a request writes - by\n")
                    .append(" * status and code, never by message. Every request goes through ")
                    .append(support)
                    .append(".\n");
            if (accessTests > 0) {
                s.append(" * Who @access lets in is checked too: for each operation, a caller each of its\n")
                        .append(" * terms lets in, and those it refuses - each signed in for the test.\n");
            }
            s.append(" *\n")
                    .append(" * Written once, then merged when the DSL changes: yours from here. Change a test\n")
                    .append(" * where you changed a rule on purpose.\n")
                    .append(" */\n");
            if (lang.kotlin)
                s.append("class ")
                        .append(e.getName())
                        .append("ContractTest : ")
                        .append(support)
                        .append("() {\n");
            else
                s.append("class ")
                        .append(e.getName())
                        .append("ContractTest extends ")
                        .append(support)
                        .append(" {\n");
            s.append('\n');
            for (Builder b : builders.values()) {
                if (lang.kotlin) {
                    s.append("    private val ")
                            .append(constant(b.e))
                            .append(" = ")
                            .append(lang.string(path(b.e)))
                            .append('\n');
                } else {
                    s.append("    private static final String ")
                            .append(constant(b.e))
                            .append(" = ")
                            .append(lang.string(path(b.e)))
                            .append(";\n");
                }
            }
            for (Builder b : builders.values()) {
                s.append('\n');
                s.append("    /** ")
                        .append(capital(article(b.e.getName())))
                        .append(b.e.getName())
                        .append(" the API takes: what the model requires, unique where it must be. */\n");
                String body = obj(b.fields, "            ");
                if (lang.kotlin) {
                    s.append("    private fun ")
                            .append(b.method)
                            .append("(): MutableMap<String, Any?> =\n")
                            .append("        ")
                            .append(body)
                            .append("\n");
                } else {
                    s.append("    private Map<String, Object> ")
                            .append(b.method)
                            .append("() throws Exception {\n")
                            .append("        return ")
                            .append(body)
                            .append(";\n")
                            .append("    }\n");
                }
            }
            for (Test t : tests) {
                s.append('\n');
                s.append("    @Test\n");
                s.append("    @DisplayName(").append(lang.string(t.display)).append(")\n");
                if (lang.kotlin) s.append("    fun ").append(t.method).append("() {\n");
                else s.append("    void ").append(t.method).append("() throws Exception {\n");
                for (String line : t.lines) s.append("        ").append(line).append('\n');
                s.append("    }\n");
            }
            s.append("}\n");
            return s.toString();
        }

        /** obj(...) on one line, or over several when it is long. */
        private String obj(List<String[]> fields) {
            return obj(fields, null);
        }

        private String obj(List<String[]> fields, String indent) {
            if (fields.isEmpty()) return "obj()";
            List<String> parts = new ArrayList<String>();
            int length = 0;
            for (String[] kv : fields) {
                String part = lang.string(kv[0]) + ", " + kv[1];
                parts.add(part);
                length += part.length() + 2;
            }
            if (indent == null || length < 80) return "obj(" + String.join(", ", parts) + ")";
            StringBuilder sb = new StringBuilder("obj(\n");
            for (int i = 0; i < parts.size(); i++) {
                sb.append(indent).append(parts.get(i)).append(i < parts.size() - 1 ? ",\n" : ")");
            }
            return sb.toString();
        }

        // ---- the state graph

        /** The states the lifecycles can be in together, from the initial ones, by the commands the tests send. */
        final class Graph {
            final List<EntitySpec.StateMachine> machines;
            /** Tuple key -> the path to it: the start, then command@tuple for each step. */
            final Map<String, List<String>> paths = new LinkedHashMap<String, List<String>>();

            final String start;

            Graph(List<EntitySpec.StateMachine> machines, Set<String> commands) {
                this.machines = machines;
                String[] first = new String[machines.size()];
                boolean any = false;
                for (int i = 0; i < first.length; i++) {
                    first[i] = machines.get(i).initial();
                    any |= first[i] != null;
                }
                start = any ? key(first) : null;
                if (start == null) return;
                Deque<String> queue = new ArrayDeque<String>();
                List<String> origin = new ArrayList<String>();
                origin.add(start);
                paths.put(start, origin);
                queue.add(start);
                while (!queue.isEmpty() && paths.size() < 2000) {
                    String at = queue.poll();
                    String[] tuple = tupleOf(at);
                    for (String c : commands) {
                        if (refusal(c, tuple) != null) continue;
                        String[] next = tuple.clone();
                        for (int i = 0; i < machines.size(); i++) {
                            EntitySpec.Move mv = machines.get(i).moveOf(c);
                            if (mv != null) next[i] = mv.to();
                        }
                        String k = key(next);
                        if (paths.containsKey(k)) continue;
                        List<String> path = new ArrayList<String>(paths.get(at));
                        path.add(c + "@" + k);
                        paths.put(k, path);
                        queue.add(k);
                    }
                }
            }

            /** The code the command is refused with in this tuple; null when it is given. */
            String refusal(String command, String[] tuple) {
                for (EntitySpec.StateMachine m : e.machinesOf(command)) {
                    int i = machines.indexOf(m);
                    if (i < 0) return "state." + e.getName() + "." + m.field() + "." + command;
                    if (tuple[i] == null || !m.moveOf(command).from().contains(tuple[i])) {
                        return "state." + e.getName() + "." + m.field() + "." + command;
                    }
                }
                return null;
            }

            /**
             * The shortest path to a tuple with the lifecycle in that state - one
             * the command is given in (allowed), or one only this lifecycle
             * refuses it in, else any (refused); null when none is reached.
             */
            List<String> reach(EntitySpec.StateMachine m, String state, String command, boolean allowed) {
                int i = machines.indexOf(m);
                List<String> fallback = null;
                for (Map.Entry<String, List<String>> en : paths.entrySet()) {
                    String[] tuple = tupleOf(en.getKey());
                    if (!state.equals(tuple[i])) continue;
                    String code = refusal(command, tuple);
                    if (allowed) {
                        if (code == null) return en.getValue();
                        continue;
                    }
                    if (code == null) continue;
                    if (code.equals("state." + e.getName() + "." + m.field() + "." + command)) {
                        boolean onlyThis = true;
                        for (EntitySpec.StateMachine other : e.machinesOf(command)) {
                            int j = machines.indexOf(other);
                            if (other == m || j < 0) continue;
                            if (tuple[j] == null
                                    || !other.moveOf(command).from().contains(tuple[j])) onlyThis = false;
                        }
                        if (onlyThis) return en.getValue();
                    }
                    if (fallback == null) fallback = en.getValue();
                }
                return fallback;
            }

            String last(List<String> path) {
                String l = path.get(path.size() - 1);
                int at = l.indexOf('@');
                return at < 0 ? l : l.substring(at + 1);
            }

            String key(String[] tuple) {
                StringBuilder sb = new StringBuilder();
                for (String t : tuple) sb.append(t == null ? "-" : t).append('|');
                return sb.toString();
            }

            String[] tupleOf(String key) {
                String[] parts = key.split("\\|", -1);
                String[] out = new String[machines.size()];
                for (int i = 0; i < out.length; i++) out[i] = "-".equals(parts[i]) ? null : parts[i];
                return out;
            }
        }
    }

    /** What a lower or upper bound of a promise does to a range. */
    private static void bound(Range r, String operator, BigDecimal v) {
        if (">".equals(operator)) r.atLeast(v, true);
        else if (">=".equals(operator)) r.atLeast(v, false);
        else if ("<".equals(operator)) r.atMost(v, true);
        else if ("<=".equals(operator)) r.atMost(v, false);
        else if ("==".equals(operator)) {
            r.atLeast(v, false);
            r.atMost(v, false);
        }
    }

    /** The article of a role's name, read letter by letter: an ADMIN, an EDITOR, a USER, a MANAGER. */
    static String roleArticle(String role) {
        return role != null && !role.isEmpty() && "AEIOaeio".indexOf(role.charAt(0)) >= 0 ? "an " : "a ";
    }

    /** "an " before a vowel, "a " before anything else. */
    static String article(String noun) {
        return noun != null && !noun.isEmpty() && "AEIOUaeiou".indexOf(noun.charAt(0)) >= 0 ? "an " : "a ";
    }

    private static String capital(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String camel(String text) {
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '_' || ch == '-' || ch == ' ' || ch == '.') {
                up = true;
                continue;
            }
            sb.append(up ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
            up = false;
        }
        return sb.toString();
    }

    /** A builder method of a test class: a valid row of one entity, and its key read back. */
    static final class Builder {
        final EntitySpec e;
        final String method;
        List<String[]> fields = new ArrayList<String[]>();
        String problem;

        Builder(EntitySpec e) {
            this.e = e;
            this.method = "new" + e.getName();
        }

        String call() {
            return method + "()";
        }

        /** The row made through the API, and its key: createdId(ITEM, newItem(), "itemId"). */
        String id(String keyField) {
            FieldSpec key = field(e, keyField);
            String name = key == null ? keyField : json(key);
            return "createdId(" + constant(e) + ", " + call() + ", \"" + name + "\")";
        }
    }

    // ---- enums and time --------------------------------------------------------------

    /** The JSON value of an enum constant (the first one when none is named): its @json field's, or its name. */
    private static String enumValue(EntitySpec owner, String type, String constant) {
        EnumSpec spec = owner.enumSpec(type);
        if (spec == null || spec.constants().isEmpty()) {
            List<String> names = owner.enumConstantNamesOf(type);
            if (constant != null) return constant;
            return names == null || names.isEmpty() ? null : names.get(0);
        }
        EnumSpec.Constant chosen = null;
        for (EnumSpec.Constant c : spec.constants()) {
            if (constant == null || c.name().equals(constant)) {
                chosen = c;
                break;
            }
        }
        if (chosen == null) return constant;
        String jsonField = spec.jsonFieldName();
        if (jsonField == null) return chosen.name();
        for (int i = 0; i < spec.fields().size(); i++) {
            if (spec.fields().get(i).name().equals(jsonField)
                    && i < chosen.values().size()) {
                return unquote(chosen.values().get(i));
            }
        }
        return chosen.name();
    }

    private static String unquote(String v) {
        String t = v == null ? "" : v.trim();
        if (t.length() >= 2 && (t.charAt(0) == '"' || t.charAt(0) == '\'') && t.charAt(t.length() - 1) == t.charAt(0)) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    /**
     * A date or a time the field takes, as an expression of the test class,
     * formatted as its DTO reads it; null for a field that is not one. One the
     * DTO checks against the clock (@past, @future) is a year from it, read
     * when the test runs, so it holds whenever the test runs and in any time
     * zone; any other is a fixed one.
     */
    static String temporal(FieldSpec f, Lang lang) {
        String kind = temporalKind(f);
        if (kind == null) return null;
        boolean past = f.hasDslAny("past", "pastorpresent");
        boolean future = f.hasDslAny("future", "futureorpresent");
        if (past != future && !"time".equals(kind)) return moment(f, past ? -365 : 365, lang);
        String pattern = FieldDoc.formatPattern(f);
        if (!formats(f, pattern)) return null;
        java.time.LocalDateTime at = java.time.LocalDateTime.of(2026, 1, 15, 10, 0, 0);
        switch (kind) {
            case "date":
                return lang.string(
                        pattern == null
                                ? at.toLocalDate().toString()
                                : at.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern(pattern)));
            case "datetime":
                return lang.string(
                        pattern == null
                                ? at.toString() + ":00"
                                : at.format(java.time.format.DateTimeFormatter.ofPattern(pattern)));
            case "time":
                // A time of day against the clock is past or future by the hour the test runs at: no value is made.
                return past || future ? null : lang.string("10:00:00");
            default:
                return lang.string("2026-01-15T10:00:00Z");
        }
    }

    /** Whether the field's format, if it has one, formats its type (a pattern the DTO's @JsonFormat would refuse is not). */
    private static boolean formats(FieldSpec f, String pattern) {
        if (pattern == null) return true;
        String kind = temporalKind(f);
        try {
            java.time.format.DateTimeFormatter format = java.time.format.DateTimeFormatter.ofPattern(pattern);
            String written = "date".equals(kind)
                    ? java.time.LocalDate.of(2026, 1, 15).format(format)
                    : "datetime".equals(kind)
                            ? java.time.LocalDateTime.of(2026, 1, 15, 10, 0, 0).format(format)
                            : "";
            return written != null;
        } catch (IllegalArgumentException | java.time.DateTimeException notAPattern) {
            return false;
        }
    }

    /**
     * The expression of a moment days from the test's now, as the DTO reads
     * the field: java.time.LocalDate.now().minusYears(1).toString(). A year is
     * written plusYears(1); null for a time of day, or a format that formats
     * no date.
     */
    static String moment(FieldSpec f, int days, Lang lang) {
        String kind = temporalKind(f);
        if (kind == null || "time".equals(kind)) return null;
        if ("instant".equals(kind)) {
            return "java.time.Instant.now()." + (days < 0 ? "minus" : "plus") + "(java.time.Duration.ofDays("
                    + Math.abs(days) + ")).toString()";
        }
        String pattern = FieldDoc.formatPattern(f);
        if (!formats(f, pattern)) return null;
        String shift = days == 365
                ? "plusYears(1)"
                : days == -365 ? "minusYears(1)" : (days < 0 ? "minusDays(" : "plusDays(") + Math.abs(days) + ")";
        // A date's ISO form is what toString() writes.
        if ("date".equals(kind) && "yyyy-MM-dd".equals(pattern)) pattern = null;
        String format = pattern != null
                ? ".format(java.time.format.DateTimeFormatter.ofPattern(" + lang.string(pattern) + "))"
                : "date".equals(kind)
                        ? ".toString()"
                        : ".format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)";
        return "date".equals(kind)
                ? "java.time.LocalDate.now()." + shift + format
                : "java.time.LocalDateTime.now()." + shift + ".withNano(0)" + format;
    }

    // ---- the principal ---------------------------------------------------------------

    /** Whom the tests sign in as: an administrator of the entity the API signs in through. */
    static final class Principal {
        String path;
        String constant;
        String body;
        String login;
        String password;
        String role;
        String roleValue;
        String entity;
        /** The JSON name of its key: what signIn(role) reads back as the caller's id. */
        String key;
        /** The role field, and the spec its enum - if it is one - is known to; null without a role. */
        FieldSpec roleField;

        EntitySpec roleOwner;
        /** The administrator's role as the session holds it (adminRole, ADMIN by default). */
        String admin;
        /** The entity a caller signs in as, and the model it is of: a row of it is the caller's own. */
        EntitySpec spec;
    }

    // ---- the callers of the @access tests ------------------------------------------------

    /** An entry of @access as ServiceAccessRules reads it: operation=level, quotes aside. */
    private static final java.util.regex.Pattern ACCESS_ENTRY =
            java.util.regex.Pattern.compile("^\\s*([\\w*]+)\\s*=\\s*(\\S.*?)\\s*$");

    /** Whom a test of @access sends a request as. */
    static final class Who {
        /** "a visitor", "the administrator", "a MANAGER". */
        final String label;
        /** The same in a method name: AVisitor, TheAdministrator, AManager. */
        final String method;
        /** The role as the session holds it - and @access names it; null for none. */
        final String role;
        /** The role as signIn(role) sends it (an enum's JSON value); null to set none. */
        final String json;

        final boolean visitor;
        final boolean admin;

        Who(String label, String role, String json, boolean visitor, boolean admin) {
            this.label = label;
            this.method = camel(label.replace("'", ""));
            this.role = role;
            this.json = json;
            this.visitor = visitor;
            this.admin = admin;
        }

        String key() {
            return visitor ? "-" : admin ? "*" : String.valueOf(role);
        }
    }

    /**
     * What the generated checks answer a caller - AccessPolicy's reading, as the
     * service calls it: require the level, then (one row) whether it is the
     * caller's. The administrator passes every term without none.
     */
    static final class Policy {
        private Policy() {}

        /** null when the caller may do it at all; 401 for a visitor signing in would help, else 403. */
        static String require(AccessExpression x, Who w) {
            boolean signingInHelps = false;
            for (List<String> all : x.terms()) {
                if (mayHold(all, w, w.admin)) return null;
                if (!all.contains("none")) signingInHelps = true;
            }
            return w.visitor && signingInHelps ? "401" : "403";
        }

        /** Whether every word of the term may hold - owner taken as signed in. */
        static boolean mayHold(List<String> all, Who w, boolean admin) {
            for (String word : all) {
                if ("none".equals(word)) return false;
                if (admin || "public".equals(word)) continue;
                if (w.visitor) return false;
                if ("admin".equals(word)) return false;
                if (!AccessExpression.LEVELS.contains(word) && !word.equals(w.role)) return false;
            }
            return true;
        }

        /** null when the row may be the caller's to do it to - theirs (mine), or a term without owner holds; else 404. */
        static String owns(AccessExpression x, Who w, boolean mine) {
            if (!x.mentions("owner") || w.admin) return null;
            for (List<String> all : x.terms()) {
                if (mayHold(all, w, false) && (mine || !all.contains("owner"))) return null;
            }
            return "404";
        }

        /** Whether the caller's search is not narrowed to their own rows. */
        static boolean seesAll(AccessExpression x, Who w) {
            if (w.admin) return true;
            for (List<String> all : x.terms()) {
                if (!all.contains("owner") && mayHold(all, w, false)) return true;
            }
            return false;
        }

        /** The problem's code the generated error handler answers with that status. */
        static String code(String status) {
            if ("401".equals(status)) return "not-signed-in";
            if ("403".equals(status)) return "forbidden";
            return "not-found";
        }
    }

    /**
     * The callers a principal can be signed in as: a visitor, the administrator, one
     * of a role - each role as the session holds it and as signIn(role) sends it -
     * and a plain one, of a role no entry names. Null, with problem said, for one
     * that cannot be made.
     */
    static final class Callers {
        private final Principal p;
        private final List<String> named = new ArrayList<String>();
        private final List<String> constants;
        String problem;
        private Who plain;
        private boolean plainAsked;

        Callers(Principal p, java.util.Collection<String> levels) {
            this.p = p;
            for (String level : levels) {
                AccessExpression x = AccessExpression.parse(level);
                if (x == null) continue;
                for (String r : x.roles()) {
                    if (!named.contains(r)) named.add(r);
                }
            }
            String type = p.roleField == null ? null : simple(p.roleField.typeName());
            constants = type != null && p.roleOwner.isKnownEnum(type) ? p.roleOwner.enumConstantNamesOf(type) : null;
        }

        Who visitor() {
            return new Who("a visitor", null, null, true, false);
        }

        /** The value of the role field that holds the role; null, with problem said, where it cannot. */
        private String value(String role) {
            if (p.roleField == null) {
                problem = p.entity + " has no role field (@role, @access(admin)) to sign in " + roleArticle(role) + role
                        + " with";
                return null;
            }
            String type = simple(p.roleField.typeName());
            if (constants != null) {
                if (!constants.contains(role)) {
                    problem = role + " is not a constant of " + type + ", " + p.entity + "." + p.roleField.name()
                            + "'s type";
                    return null;
                }
                return enumValue(p.roleOwner, type, role);
            }
            int max = p.roleField.validationMaxLength();
            if (max < 0) max = p.roleField.rawLength();
            String regexp = regex(p.roleField);
            if ((max > 0 && role.length() > max)
                    || role.length() < Math.max(0, p.roleField.minLength())
                    || (regexp != null && !role.matches(regexp))) {
                problem = p.entity + "." + p.roleField.name() + " cannot hold " + role;
                return null;
            }
            return role;
        }

        Who admin() {
            String v = value(p.admin);
            return v == null ? null : new Who("the administrator", p.admin, v, false, true);
        }

        /** Whether signIn() signs in the administrator: the principal has a role field that holds the administrator's role. */
        boolean adminSignsIn() {
            String before = problem;
            boolean admin = p.roleValue != null && value(p.admin) != null;
            problem = before;
            return admin;
        }

        Who role(String role) {
            String v = value(role);
            return v == null ? null : new Who(roleArticle(role) + role, role, v, false, role.equals(p.admin));
        }

        /** One signed in of a role no entry names: the first such constant, else USER, GUEST, MEMBER; no role without a field. */
        Who plain() {
            if (plainAsked) return plain;
            plainAsked = true;
            if (p.roleField == null) {
                plain = new Who("a signed-in caller", null, null, false, false);
                return plain;
            }
            List<String> tried = constants != null ? constants : java.util.Arrays.asList("USER", "GUEST", "MEMBER");
            for (String c : tried) {
                if (c.equals(p.admin) || named.contains(c)) continue;
                String before = problem;
                Who w = role(c);
                problem = before;
                if (w != null) {
                    plain = w;
                    return plain;
                }
            }
            return null;
        }

        /** The caller each term lets in: the administrator, one of its role, a plain one, a visitor for public alone. */
        Who forTerm(List<String> term) {
            List<String> roles = new ArrayList<String>();
            boolean admin = false;
            boolean signedIn = false;
            for (String word : term) {
                if ("admin".equals(word)) admin = true;
                else if ("user".equals(word) || "owner".equals(word)) signedIn = true;
                else if (!"public".equals(word) && !"none".equals(word) && !roles.contains(word)) roles.add(word);
            }
            if (admin) return admin();
            if (roles.size() > 1) {
                problem = String.join("&", term) + " asks for " + String.join(" and ", roles)
                        + ", and a session holds one role";
                return null;
            }
            if (roles.size() == 1) return role(roles.get(0));
            if (signedIn) {
                Who w = plain();
                if (w == null)
                    problem = "every role of " + p.entity + " is named or the administrator's, so no caller"
                            + " of another is signed in";
                return w;
            }
            return visitor();
        }

        /** The callers of a role that may be refused, in order: the plain one, then each other role the field takes. */
        List<Who> candidates() {
            List<Who> out = new ArrayList<Who>();
            Who first = plain();
            if (first != null) out.add(first);
            List<String> more = constants != null ? constants : named;
            for (String c : more) {
                if (c.equals(p.admin) || (first != null && c.equals(first.role))) continue;
                String before = problem;
                Who w = role(c);
                problem = before;
                if (w != null) out.add(w);
            }
            return out;
        }
    }

    /** The search DTO the entity's API reads: its fields as a search sends them; null when there is none. */
    private EntitySpec searchDto(EntitySpec e) {
        ECoreModel m = modelOf.get(e);
        if (m == null || m.getDataTransferModels() == null || e.searchDtoClassName() == null) return null;
        io.elasticore.base.model.dto.DataTransfer d = m.getDataTransferModels().findByName(e.searchDtoClassName());
        return d == null ? null : SpecFactory.fromDto(d, m, m.getInternalDomainName());
    }

    /**
     * The run's generated sign-in: the one entity that @signin and has it;
     * null when there is none, or when no principal of it can be built - then
     * principalProblem says why, as the summary does.
     */
    private Principal principal(Lang lang) {
        principalProblem = null;
        Principal found = signInPrincipal(lang);
        if (found != null) principalProblem = null;
        else if (principalProblem != null) skip(principalProblem);
        return found;
    }

    private Principal signInPrincipal(Lang lang) {
        for (ECoreModel m : models) {
            if (m.getEntityModels() == null) continue;
            ModelComponentItems<Entity> items = m.getEntityModels().getItems();
            for (int i = 0; i < items.size(); i++) {
                EntitySpec p = entityIn(m, items.get(i).getIdentity().getName());
                if (p == null || !ServiceAccessRules.signsIn(p)) continue;
                if (!creates(p)) {
                    if (principalProblem == null)
                        principalProblem = p.getName() + " (signing in): its API takes no create, so no administrator"
                                + " is made to sign in as";
                    continue;
                }
                Principal out = new Principal();
                out.entity = p.getName();
                out.path = path(p);
                out.constant = "PRINCIPAL";
                out.login = ServiceAccessRules.loginField(p);
                for (FieldSpec f : p.fields()) {
                    if (f.hasDsl("password")) out.password = f.name();
                }
                FieldSpec role = ServiceAccessRules.roleField(p);
                String admin = p.config("adminRole");
                if (admin == null || admin.trim().isEmpty()) admin = "ADMIN";
                out.admin = admin.trim();
                out.spec = p;
                out.key = json(p.pkFields().get(0));
                if (role != null) {
                    out.role = json(role);
                    String type = simple(role.typeName());
                    EntitySpec pdto = dto(p);
                    EntitySpec owner = pdto != null && pdto.isKnownEnum(type) ? pdto : p;
                    out.roleValue = owner.isKnownEnum(type) ? enumValue(owner, type, admin.trim()) : admin.trim();
                    out.roleField = role;
                    out.roleOwner = owner;
                }
                EntityTests plan = new EntityTests(p, lang, new Settings());
                plan.dto = dto(p);
                if (plan.dto == null) {
                    principalProblem = p.getName() + " (signing in): no " + p.dtoClassName() + " to send";
                    return null;
                }
                Builder b = plan.builder(p, new ArrayDeque<String>());
                if (b.problem != null) {
                    principalProblem = p.getName() + " (signing in): " + b.problem;
                    return null;
                }
                // What the principal needs made first is made as the application too: the support class has no
                // builders of its own beside this one.
                for (String[] kv : b.fields) {
                    if (kv[1].contains("createdId(")) {
                        principalProblem = p.getName() + " (signing in): it needs other rows made first, which the"
                                + " support class does not make";
                        return null;
                    }
                }
                boolean hasLogin = false;
                boolean hasPassword = false;
                for (String[] kv : b.fields) {
                    hasLogin |= kv[0].equals(out.login);
                    hasPassword |= kv[0].equals(out.password);
                }
                if (!hasLogin || !hasPassword) {
                    principalProblem = p.getName() + " (signing in): its " + out.login + " and password are not both"
                            + " required, so the support class does not know what to sign in with";
                    return null;
                }
                out.body = plan.obj(b.fields, "                ");
                return out;
            }
        }
        return null;
    }

    // ---- the support class -------------------------------------------------------------

    static final String OWNED = "// Initially generated by ElastiCORE; no longer managed - do not remove this line.";

    private String supportClass(
            String pkg,
            String name,
            String domainName,
            Settings s,
            Lang lang,
            boolean guarded,
            Principal principal,
            Access access) {
        String mockMvc = s.boot4
                ? "org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc"
                : "org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc";
        boolean system = guarded && principal == null;
        boolean tx = !s.mongo;
        String why = system ? principalProblem : null;
        return lang.kotlin
                ? kotlinSupport(pkg, name, domainName, s, mockMvc, guarded, system, principal, tx, why, access)
                : javaSupport(pkg, name, domainName, s, mockMvc, guarded, system, principal, tx, why, access);
    }

    /**
     * The support class's comment. Without a sign-in it says why: the run has
     * no @signin, or its principal cannot be built - with the summary's reason.
     * With tests of @access, how they sign in, and what of it is not tested.
     */
    private static String doc(
            String domainName,
            Settings s,
            boolean guarded,
            boolean system,
            Principal principal,
            String why,
            Access access) {
        StringBuilder d = new StringBuilder();
        d.append("/**\n")
                .append(" * What every contract test of the ")
                .append(domainName)
                .append(" domain goes through (testCode: contract): the\n")
                .append(" * application they start, how each request is sent and signed in, and the checks\n")
                .append(" * they make - by the status and the problem's code, never by message text.\n")
                .append(" *\n")
                .append(" * Written once: yours from here. request(...) is what every request gets added -\n")
                .append(" * nothing as generated; a project that asks every write for a CSRF token adds its\n")
                .append(" * cookie and header there. signIn() is how a test signs in");
        if (!guarded) d.append(" - nobody, as no operation of\n * the domain asks who is calling (@access).\n");
        else if (system && why != null)
            d.append(": the generated sign-in's\n * principal cannot be built (")
                    .append(comment(why))
                    .append("),\n * so the requests run as the application itself (CurrentUser.runAsSystem); sign\n"
                            + " * in your way there and set the session.\n");
        else if (system)
            d.append(": the run has no generated\n * sign-in (@signin), so the requests run as the application itself\n"
                    + " * (CurrentUser.runAsSystem); sign in your way there and set the session.\n");
        else
            d.append(": as an administrator, through the\n * generated sign-in of " + principal.entity
                    + ", made for the test as the application itself.\n");
        if (access.callers) {
            d.append(" * The tests of @access sign in callers of their own the same way - signIn(role),\n")
                    .append(" * a principal of a role - and send their requests as them (sentBy).\n");
        } else if (access.declared && principal == null) {
            d.append(" * No test of @access is written: each needs callers signed in through it.\n");
        }
        if (!access.skipped.isEmpty()) {
            d.append(" * Not tested of @access, as the build's summary says:\n");
            for (String reason : access.skipped)
                d.append(" *   - ").append(comment(reason)).append('\n');
        }
        d.append(" *\n")
                .append(" * Every test class extends this one, so all of them start the same application and\n")
                .append(" * Spring keeps one context for the suite");
        if (s.mongo) {
            d.append(". The tests run on the MongoDB server\n * " + MONGO_URI + " names (mongodb://host:port) and are"
                    + " skipped without it; each\n * test's rows are its own, with unique values.\n");
        } else {
            d.append("; each test's rows are rolled back.\n");
        }
        if (!s.mongo) {
            d.append(" * The tests write an in-memory H2 of their own, whatever the application's\n")
                    .append(" * datasource is - the properties below")
                    .append(
                            s.mybatis
                                    ? "; the schema is the application's (spring.sql.init, Flyway).\n"
                                    : ", Hibernate making the tables.\n")
                    .append(" * Change them here to test on another database.\n");
        }
        d.append(" * The project's test classpath needs spring-boot-starter-test");
        d.append(s.mongo ? ".\n" : " and H2.\n");
        d.append(" */\n");
        return d.toString();
    }

    /**
     * Text safe inside a Java or Kotlin block comment: a reason may quote a
     * pattern, whose * / would end the comment, whose / * would open one in
     * Kotlin (they nest there), and whose backslash-u Java reads as a
     * character before it reads the comment - every backslash is doubled, so
     * none starts one, however many stand before a u.
     */
    static String comment(String text) {
        return text.replace("*/", "* /").replace("/*", "/ *").replace("\\", "\\\\");
    }

    /**
     * The @SpringBootTest of the support class: the application, when the
     * build names it, and - over JPA and MyBatis - the in-memory H2 the
     * tests write, whatever datasource the application's own properties
     * name. Plain properties, so it compiles on Spring Boot 3 and 4 with
     * spring-boot-starter-test alone.
     */
    static String springBootTest(Settings s, Lang lang) {
        List<String> properties = new ArrayList<String>();
        if (!s.mongo) {
            properties.add("spring.datasource.url=jdbc:h2:mem:elasticore-contract-${random.uuid};DB_CLOSE_DELAY=-1");
            properties.add("spring.datasource.driver-class-name=org.h2.Driver");
            properties.add("spring.datasource.username=sa");
            properties.add("spring.datasource.password=");
            if (!s.mybatis) {
                // Hibernate makes the tables; a migration written for another database would not run here.
                properties.add("spring.jpa.hibernate.ddl-auto=create-drop");
                properties.add("spring.jpa.database-platform=org.hibernate.dialect.H2Dialect");
                properties.add("spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect");
                properties.add("spring.flyway.enabled=false");
                properties.add("spring.liquibase.enabled=false");
            }
        }
        List<String> args = new ArrayList<String>();
        if (s.application != null) {
            args.add(
                    lang.kotlin ? "classes = [" + s.application + "::class]" : "classes = " + s.application + ".class");
        }
        if (!properties.isEmpty()) {
            StringBuilder p = new StringBuilder(lang.kotlin ? "properties = [\n" : "properties = {\n");
            for (int i = 0; i < properties.size(); i++) {
                p.append(lang.kotlin ? "        " : "            ").append(lang.string(properties.get(i)));
                p.append(i < properties.size() - 1 ? ",\n" : "\n");
            }
            p.append(lang.kotlin ? "    ]" : "        }");
            args.add(p.toString());
        }
        if (args.isEmpty()) return "@SpringBootTest\n";
        StringBuilder a = new StringBuilder("@SpringBootTest(\n");
        for (int i = 0; i < args.size(); i++) {
            a.append(lang.kotlin ? "    " : "        ").append(args.get(i)).append(i < args.size() - 1 ? ",\n" : ")\n");
        }
        return a.toString();
    }

    private static String javaSupport(
            String pkg,
            String name,
            String domainName,
            Settings s,
            String mockMvc,
            boolean guarded,
            boolean system,
            Principal principal,
            boolean tx,
            String why,
            Access access) {
        Lang lang = new Lang(false);
        StringBuilder j = new StringBuilder();
        j.append(OWNED).append('\n').append("package ").append(pkg).append(";\n\n");
        j.append("import com.jayway.jsonpath.JsonPath;\n");
        if (guarded) j.append("import io.elasticore.springboot3.security.CurrentUser;\n");
        j.append("import org.junit.jupiter.api.Assertions;\n").append("import org.junit.jupiter.api.Assumptions;\n");
        if (s.mongo) j.append("import org.junit.jupiter.api.BeforeAll;\n");
        j.append("import org.springframework.beans.factory.annotation.Autowired;\n")
                .append("import ")
                .append(mockMvc)
                .append(";\n")
                .append("import org.springframework.boot.test.context.SpringBootTest;\n")
                .append("import org.springframework.http.MediaType;\n")
                .append("import org.springframework.mock.web.MockHttpSession;\n");
        if (s.mongo) {
            j.append("import org.springframework.test.context.DynamicPropertyRegistry;\n")
                    .append("import org.springframework.test.context.DynamicPropertySource;\n");
        }
        j.append("import org.springframework.test.web.servlet.MockMvc;\n")
                .append("import org.springframework.test.web.servlet.MvcResult;\n")
                .append("import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;\n")
                .append("import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;\n");
        if (tx) j.append("import org.springframework.transaction.annotation.Transactional;\n");
        j.append("\n")
                .append("import java.math.BigDecimal;\n")
                .append("import java.nio.charset.StandardCharsets;\n")
                .append("import java.util.ArrayList;\n")
                .append("import java.util.Arrays;\n")
                .append("import java.util.Collection;\n")
                .append("import java.util.LinkedHashMap;\n")
                .append("import java.util.List;\n")
                .append("import java.util.Map;\n")
                .append("import java.util.concurrent.ThreadLocalRandom;\n")
                .append("import java.util.concurrent.atomic.AtomicLong;\n\n");
        j.append(doc(domainName, s, guarded, system, principal, why, access));
        j.append(springBootTest(s, lang));
        j.append("@AutoConfigureMockMvc\n");
        if (tx) j.append("@Transactional\n");
        j.append("public abstract class ").append(name).append(" {\n\n");
        j.append("    /** A test's values are its own (logins, unique fields): each run starts somewhere new. */\n")
                .append(
                        "    private static final AtomicLong SEQUENCE = new AtomicLong(ThreadLocalRandom.current().nextLong(1L << 30));\n\n");
        if (principal != null) {
            j.append("    private static final String ")
                    .append(principal.constant)
                    .append(" = ")
                    .append(lang.string(principal.path))
                    .append(";\n\n");
        }
        j.append("    @Autowired\n")
                .append("    protected MockMvc mvc;\n\n")
                .append("    /** The session signIn() opened, sent with every request after it; null for none. */\n")
                .append("    protected MockHttpSession session;\n\n")
                .append("    private boolean signedIn;\n\n");
        if (s.mongo) {
            j.append("    /** The tests run on the MongoDB server " + MONGO_URI
                            + " names; without one they are skipped. */\n")
                    .append("    @BeforeAll\n")
                    .append("    static void mongoServer() {\n")
                    .append("        Assumptions.assumeTrue(System.getenv(\"" + MONGO_URI + "\") != null,\n")
                    .append("                \"" + MONGO_URI + " names no MongoDB server (mongodb://host:port)\");\n")
                    .append("    }\n\n")
                    .append("    @DynamicPropertySource\n")
                    .append("    static void mongo(DynamicPropertyRegistry registry) {\n")
                    .append("        String server = System.getenv(\"" + MONGO_URI + "\");\n")
                    .append("        if (server == null) return;\n")
                    .append(
                            "        String uri = (server.endsWith(\"/\") ? server : server + \"/\") + \"elasticore_contract\";\n")
                    .append("        registry.add(\"spring.data.mongodb.uri\", () -> uri);\n")
                    .append("        registry.add(\"spring.mongodb.uri\", () -> uri);\n")
                    .append("        registry.add(\"spring.data.mongodb.database\", () -> \"elasticore_contract\");\n")
                    .append("        registry.add(\"spring.mongodb.database\", () -> \"elasticore_contract\");\n")
                    .append("    }\n\n");
        }
        j.append("    /**\n")
                .append("     * What every request gets added: nothing, as generated. Add the application's\n")
                .append("     * CSRF cookie and header here, say.\n")
                .append("     */\n")
                .append(
                        "    protected MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder builder) {\n")
                .append("        return builder;\n")
                .append("    }\n\n");
        if (principal != null) {
            j.append("    /**\n")
                    .append("     * Signs in before a test's first request: as an administrator, through the\n")
                    .append("     * generated sign-in - ")
                    .append(article(principal.entity))
                    .append(principal.entity)
                    .append(" made for the test as the application itself\n")
                    .append("     * (CurrentUser.runAsSystem). The tests check what the model declares, not who\n")
                    .append("     * may ask; sign in your way here if the application does.\n")
                    .append("     */\n")
                    .append("    protected void signIn() throws Exception {\n")
                    .append("        Map<String, Object> principal = ")
                    .append(principal.body)
                    .append(";\n");
            if (principal.role != null) {
                j.append("        principal.put(")
                        .append(lang.string(principal.role))
                        .append(", ")
                        .append(lang.string(principal.roleValue))
                        .append(");\n");
            }
            j.append("        MvcResult made = asSystem(MockMvcRequestBuilders.post(")
                    .append(principal.constant)
                    .append(")\n")
                    .append("                .contentType(MediaType.APPLICATION_JSON).content(json(principal)));\n")
                    .append(
                            "        Assertions.assertTrue(ok(made), () -> \"the administrator to sign in as was not made: \" + answer(made));\n")
                    .append("        MvcResult in = mvc.perform(request(MockMvcRequestBuilders.post(")
                    .append(principal.constant)
                    .append(" + \"/signin\")\n")
                    .append(
                            "                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)\n")
                    .append("                .content(json(obj(")
                    .append(lang.string(principal.login))
                    .append(", principal.get(")
                    .append(lang.string(principal.login))
                    .append("), ")
                    .append(lang.string(principal.password))
                    .append(", principal.get(")
                    .append(lang.string(principal.password))
                    .append(")))))).andReturn();\n")
                    .append(
                            "        Assertions.assertTrue(ok(in), () -> \"the generated sign-in refused the administrator: \" + answer(in));\n")
                    .append("        session = (MockHttpSession) in.getRequest().getSession(false);\n")
                    .append("    }\n\n");
        } else {
            j.append("    /** Signs in before a test's first request: ")
                    .append(
                            guarded
                                    ? "nobody - the requests run as the application itself."
                                    : "nobody, as nothing asks who is calling.")
                    .append(" */\n")
                    .append("    protected void signIn() throws Exception {\n")
                    .append("    }\n\n");
        }
        j.append(
                        "    /** Sends a request as every contract test does: JSON, signed in, with what request(...) adds. */\n")
                .append("    protected MvcResult perform(MockHttpServletRequestBuilder builder) throws Exception {\n")
                .append("        if (!signedIn) {\n")
                .append("            signedIn = true;\n")
                .append("            signIn();\n")
                .append("        }\n")
                .append("        builder.accept(MediaType.APPLICATION_JSON);\n")
                .append("        if (session != null) builder.session(session);\n");
        if (system) j.append("        if (session == null) return asSystem(builder);\n");
        j.append("        return mvc.perform(request(builder)).andReturn();\n").append("    }\n\n");
        if (guarded) {
            j.append("    /** The request as the application itself: every @access check passes. */\n")
                    .append(
                            "    protected MvcResult asSystem(MockHttpServletRequestBuilder builder) throws Exception {\n")
                    .append("        Exception[] failure = new Exception[1];\n")
                    .append("        MvcResult result = CurrentUser.callAsSystem(() -> {\n")
                    .append("            try {\n")
                    .append(
                            "                return mvc.perform(request(builder.accept(MediaType.APPLICATION_JSON))).andReturn();\n")
                    .append("            } catch (Exception e) {\n")
                    .append("                failure[0] = e;\n")
                    .append("                return null;\n")
                    .append("            }\n")
                    .append("        });\n")
                    .append("        if (failure[0] != null) throw failure[0];\n")
                    .append("        return result;\n")
                    .append("    }\n\n");
        }
        if (access.callers && principal != null) j.append(javaCallers(principal, lang));
        j.append(JAVA_HELPERS);
        j.append("}\n");
        return j.toString();
    }

    /**
     * What the tests of @access send their requests with (Java): a principal of a
     * role signed in as signIn() signs in the administrator, the request as it or
     * as a visitor, and the checks of what @access answers.
     */
    private static String javaCallers(Principal p, Lang lang) {
        StringBuilder j = new StringBuilder();
        j.append("    // ---- the callers of the @access tests\n\n")
                .append(
                        "    /** Whom a test of @access sends a request as: a principal signIn(role) made, and the session it opened. */\n")
                .append("    protected static final class Caller {\n")
                .append("        /** The principal's key: what the @owner of a row of theirs holds. */\n")
                .append("        public final Object id;\n")
                .append("        public final MockHttpSession session;\n\n")
                .append("        public Caller(Object id, MockHttpSession session) {\n")
                .append("            this.id = id;\n")
                .append("            this.session = session;\n")
                .append("        }\n")
                .append("    }\n\n")
                .append("    /**\n")
                .append("     * Signs in a principal of its own for a test of @access: ")
                .append(article(p.entity))
                .append(p.entity)
                .append(" made as the\n")
                .append("     * application itself (CurrentUser.runAsSystem) with this role")
                .append(p.role == null ? " - it has none to set -" : ", as its " + p.role + " sends it,")
                .append("\n")
                .append("     * and signed in through the generated sign-in, as signIn() signs in the administrator.\n")
                .append("     * Sign in your way here if the application does.\n")
                .append("     */\n")
                .append("    protected Caller signIn(String role) throws Exception {\n")
                .append("        Map<String, Object> principal = ")
                .append(p.body)
                .append(";\n");
        if (p.role != null) {
            j.append("        if (role != null) principal.put(")
                    .append(lang.string(p.role))
                    .append(", role);\n");
        }
        j.append("        MvcResult made = asSystem(MockMvcRequestBuilders.post(")
                .append(p.constant)
                .append(")\n")
                .append("                .contentType(MediaType.APPLICATION_JSON).content(json(principal)));\n")
                .append(
                        "        Assertions.assertTrue(ok(made), () -> \"the principal to sign in as was not made: \" + answer(made));\n")
                .append("        MvcResult in = mvc.perform(request(MockMvcRequestBuilders.post(")
                .append(p.constant)
                .append(" + \"/signin\")\n")
                .append("                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)\n")
                .append("                .content(json(obj(")
                .append(lang.string(p.login))
                .append(", principal.get(")
                .append(lang.string(p.login))
                .append("), ")
                .append(lang.string(p.password))
                .append(", principal.get(")
                .append(lang.string(p.password))
                .append(")))))).andReturn();\n")
                .append(
                        "        Assertions.assertTrue(ok(in), () -> \"the generated sign-in refused the principal: \" + answer(in));\n")
                .append("        return new Caller(value(made, ")
                .append(lang.string(p.key))
                .append("), (MockHttpSession) in.getRequest().getSession(false));\n")
                .append("    }\n\n");
        j.append(JAVA_CALLER_HELPERS);
        return j.toString();
    }

    /** The requests and checks of the @access tests (Java). */
    private static final String JAVA_CALLER_HELPERS = ""
            + "    /** The request as this caller - or as a visitor, for null: with what request(...) adds, and no other session. */\n"
            + "    protected MvcResult sentBy(Caller caller, MockHttpServletRequestBuilder builder) throws Exception {\n"
            + "        builder.accept(MediaType.APPLICATION_JSON);\n"
            + "        if (caller != null && caller.session != null) builder.session(caller.session);\n"
            + "        return mvc.perform(request(builder)).andReturn();\n"
            + "    }\n"
            + "\n"
            + "    protected MockHttpServletRequestBuilder reading(String path, Object id) {\n"
            + "        return MockMvcRequestBuilders.get(path + \"/\" + id);\n"
            + "    }\n"
            + "\n"
            + "    protected MockHttpServletRequestBuilder searching(String path, Object body) {\n"
            + "        return MockMvcRequestBuilders.post(path + \"/search\").contentType(MediaType.APPLICATION_JSON).content(json(body));\n"
            + "    }\n"
            + "\n"
            + "    protected MockHttpServletRequestBuilder creating(String path, Object body) {\n"
            + "        return MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(json(body));\n"
            + "    }\n"
            + "\n"
            + "    /** An update of the row by its key alone: nothing changes, and who may change it is asked all the same. */\n"
            + "    protected MockHttpServletRequestBuilder updating(String path, Object body) {\n"
            + "        return MockMvcRequestBuilders.put(path).contentType(MediaType.APPLICATION_JSON).content(json(body));\n"
            + "    }\n"
            + "\n"
            + "    protected MockHttpServletRequestBuilder deleting(String path, Object id) {\n"
            + "        return MockMvcRequestBuilders.delete(path + \"/\" + id);\n"
            + "    }\n"
            + "\n"
            + "    protected MockHttpServletRequestBuilder commanding(String path, Object id, String command, Object body) {\n"
            + "        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(path + \"/\" + id + \"/\" + command);\n"
            + "        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json(body));\n"
            + "        return builder;\n"
            + "    }\n"
            + "\n"
            + "    /** A row this caller makes, and its key: let in (assertAllowed says how it fails or is aborted). */\n"
            + "    protected Object createdIdBy(Caller caller, String path, Object body, String key) throws Exception {\n"
            + "        MvcResult made = sentBy(caller, creating(path, body));\n"
            + "        assertAllowed(made);\n"
            + "        Object id = value(made, key);\n"
            + "        Assertions.assertNotNull(id, () -> \"POST \" + path + \" answered no \" + key + \": \" + answer(made));\n"
            + "        return id;\n"
            + "    }\n"
            + "\n"
            + "    /**\n"
            + "     * Let in: 2xx. Refused as @access refuses - 401, 403, or 404 not-found for a row the caller\n"
            + "     * may not see - or failed (5xx), the test fails; refused by another rule of the\n"
            + "     * application's own (a reference to a row it needs, say), it is aborted with the reason, as\n"
            + "     * succeeded says.\n"
            + "     */\n"
            + "    protected void assertAllowed(MvcResult result) throws Exception {\n"
            + "        int status = result.getResponse().getStatus();\n"
            + "        String code = code(result);\n"
            + "        if (status == 401 || status == 403 || (status == 404 && (code == null || \"not-found\".equals(code)))) {\n"
            + "            Assertions.fail(\"let in by @access, and refused: \" + answer(result));\n"
            + "        }\n"
            + "        succeeded(result, \"the request\");\n"
            + "    }\n"
            + "\n"
            + "    /** Refused by @access: this status, and this problem code where the answer carries one. */\n"
            + "    protected void assertDenied(MvcResult result, int status, String code) throws Exception {\n"
            + "        Assertions.assertEquals(status, result.getResponse().getStatus(), () -> \"expected \" + status + \" \" + code + \": \" + answer(result));\n"
            + "        String sent = code(result);\n"
            + "        if (sent != null) Assertions.assertEquals(code, sent, () -> answer(result));\n"
            + "    }\n"
            + "\n"
            + "    /** The search let the caller in, and its answer holds the row of this key. */\n"
            + "    protected void assertFinds(MvcResult result, String key, Object id) throws Exception {\n"
            + "        assertAllowed(result);\n"
            + "        Assertions.assertTrue(keysOf(result, key).contains(String.valueOf(id)), () -> \"the search does not hold \" + key + \" \" + id + \": \" + answer(result));\n"
            + "    }\n"
            + "\n"
            + "    /** The search let the caller in, and its answer does not hold the row of this key. */\n"
            + "    protected void assertMisses(MvcResult result, String key, Object id) throws Exception {\n"
            + "        assertAllowed(result);\n"
            + "        Assertions.assertFalse(keysOf(result, key).contains(String.valueOf(id)), () -> \"the search holds \" + key + \" \" + id + \": \" + answer(result));\n"
            + "    }\n"
            + "\n"
            + "    /** The keys of the rows a search answered: those of its page, or of the list it is. */\n"
            + "    protected List<String> keysOf(MvcResult result, String key) {\n"
            + "        Object rows;\n"
            + "        try {\n"
            + "            rows = JsonPath.read(body(result), \"$.content\");\n"
            + "        } catch (RuntimeException notAPage) {\n"
            + "            rows = null;\n"
            + "        }\n"
            + "        if (!(rows instanceof List)) {\n"
            + "            try {\n"
            + "                rows = JsonPath.read(body(result), \"$\");\n"
            + "            } catch (RuntimeException notAList) {\n"
            + "                rows = null;\n"
            + "            }\n"
            + "        }\n"
            + "        List<String> keys = new ArrayList<>();\n"
            + "        if (rows instanceof List) {\n"
            + "            for (Object row : (List<?>) rows) {\n"
            + "                if (row instanceof Map) keys.add(String.valueOf(((Map<?, ?>) row).get(key)));\n"
            + "            }\n"
            + "        }\n"
            + "        return keys;\n"
            + "    }\n"
            + "\n";

    /** The requests, the checks and the values every contract test uses (Java). */
    private static final String JAVA_HELPERS = ""
            + "    protected MvcResult post(String path, Object body) throws Exception {\n"
            + "        return perform(MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(json(body)));\n"
            + "    }\n"
            + "\n"
            + "    /** A command of the row: POST path/{id}/command, with its parameters when it takes any. */\n"
            + "    protected MvcResult command(String path, Object id, String command, Object body) throws Exception {\n"
            + "        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(path + \"/\" + id + \"/\" + command);\n"
            + "        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json(body));\n"
            + "        return perform(builder);\n"
            + "    }\n"
            + "\n"
            + "    /**\n"
            + "     * A row the test needs: made, or the test is aborted with the reason - a row the\n"
            + "     * application refuses to make (a rule of its own) is no regression of what is tested.\n"
            + "     */\n"
            + "    protected MvcResult create(String path, Object body) throws Exception {\n"
            + "        MvcResult made = post(path, body);\n"
            + "        succeeded(made, \"POST \" + path + \", a row the test needs,\");\n"
            + "        return made;\n"
            + "    }\n"
            + "\n"
            + "    /** A row made, and its key as the answer says it. */\n"
            + "    protected Object createdId(String path, Object body, String key) throws Exception {\n"
            + "        MvcResult made = create(path, body);\n"
            + "        Object id = value(made, key);\n"
            + "        Assertions.assertNotNull(id, () -> \"POST \" + path + \" answered no \" + key + \": \" + answer(made));\n"
            + "        return id;\n"
            + "    }\n"
            + "\n"
            + "    /** A step to the state the test starts from: given, or the test is aborted when a rule of the application's refuses it. */\n"
            + "    protected void given(MvcResult step) throws Exception {\n"
            + "        succeeded(step, \"a step to the state the test starts from\");\n"
            + "    }\n"
            + "\n"
            + "    /** The command was given: 2xx. */\n"
            + "    protected void assertMoved(MvcResult result) throws Exception {\n"
            + "        succeeded(result, \"the command\");\n"
            + "    }\n"
            + "\n"
            + "    /**\n"
            + "     * 2xx. Refused by a rule of the application's own - a check its code makes, a unique value\n"
            + "     * someone holds - the test is aborted with the reason; refused for what the model declares (a\n"
            + "     * field, an invariant, a state) or failed (5xx), it fails: that is what the tests are about.\n"
            + "     */\n"
            + "    private void succeeded(MvcResult result, String what) throws Exception {\n"
            + "        if (ok(result)) return;\n"
            + "        if (refused(result) && !declared(code(result))) {\n"
            + "            skip(what + \" was refused by a rule of the application: \" + answer(result));\n"
            + "        }\n"
            + "        Assertions.fail(what + \" failed: \" + answer(result));\n"
            + "    }\n"
            + "\n"
            + "    /** Whether a refusal is one the model declares: a field's constraint (invalid), an invariant, a lifecycle. */\n"
            + "    protected static boolean declared(String code) {\n"
            + "        return \"invalid\".equals(code) || (code != null && (code.startsWith(\"invariant.\") || code.startsWith(\"state.\")));\n"
            + "    }\n"
            + "\n"
            + "    /** The row was made: 2xx (refused as succeeded says). */\n"
            + "    protected void assertCreated(MvcResult result) throws Exception {\n"
            + "        succeeded(result, \"the row\");\n"
            + "    }\n"
            + "\n"
            + "    /** Aborts the test with the reason - in the report, and in the output, which a build's XML report keeps. */\n"
            + "    protected void skip(String reason) {\n"
            + "        System.out.println(\"[contract test skipped] \" + reason);\n"
            + "        Assumptions.abort(reason);\n"
            + "    }\n"
            + "\n"
            + "    /** Refused with this status and the problem's code. */\n"
            + "    protected void assertRefused(MvcResult result, int status, String code) throws Exception {\n"
            + "        Assertions.assertEquals(status, result.getResponse().getStatus(), () -> \"expected \" + status + \" \" + code + \": \" + answer(result));\n"
            + "        Assertions.assertEquals(code, code(result), () -> answer(result));\n"
            + "    }\n"
            + "\n"
            + "    /** Refused (400) for this field, with this constraint's code: NotNull, Size, Min ... */\n"
            + "    protected void assertFieldError(MvcResult result, String field, String code) throws Exception {\n"
            + "        Assertions.assertEquals(400, result.getResponse().getStatus(), () -> \"expected 400 \" + field + \" \" + code + \": \" + answer(result));\n"
            + "        for (Map<String, Object> error : fieldErrors(result)) {\n"
            + "            if (field.equals(error.get(\"field\")) && code.equals(error.get(\"code\"))) return;\n"
            + "        }\n"
            + "        Assertions.fail(\"expected the field error \" + field + \" \" + code + \": \" + answer(result));\n"
            + "    }\n"
            + "\n"
            + "    /** The answer shows this value for the field. */\n"
            + "    protected void assertValue(MvcResult result, String field, Object expected) throws Exception {\n"
            + "        Assertions.assertEquals(String.valueOf(expected), String.valueOf(value(result, field)), () -> field + \": \" + answer(result));\n"
            + "    }\n"
            + "\n"
            + "    /** The answer shows the field set: a @stamp written by the move. */\n"
            + "    protected void assertStamped(MvcResult result, String field) throws Exception {\n"
            + "        Assertions.assertNotNull(value(result, field), () -> field + \" is not set: \" + answer(result));\n"
            + "    }\n"
            + "\n"
            + "    /** A field of the answer, or null. */\n"
            + "    protected Object value(MvcResult result, String field) {\n"
            + "        try {\n"
            + "            return JsonPath.read(body(result), \"$['\" + field + \"']\");\n"
            + "        } catch (RuntimeException notThere) {\n"
            + "            return null;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    /** The problem's code, or null. */\n"
            + "    protected String code(MvcResult result) {\n"
            + "        Object code = value(result, \"code\");\n"
            + "        return code == null ? null : code.toString();\n"
            + "    }\n"
            + "\n"
            + "    private List<Map<String, Object>> fieldErrors(MvcResult result) {\n"
            + "        try {\n"
            + "            return JsonPath.read(body(result), \"$.fieldErrors\");\n"
            + "        } catch (RuntimeException none) {\n"
            + "            return new ArrayList<>();\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    protected String body(MvcResult result) {\n"
            + "        try {\n"
            + "            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);\n"
            + "        } catch (java.io.UnsupportedEncodingException unreadable) {\n"
            + "            return \"\";\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    /** The status and the body, for a message. */\n"
            + "    protected String answer(MvcResult result) {\n"
            + "        return result.getResponse().getStatus() + \" \" + body(result);\n"
            + "    }\n"
            + "\n"
            + "    protected static boolean ok(MvcResult result) {\n"
            + "        int status = result.getResponse().getStatus();\n"
            + "        return status >= 200 && status < 300;\n"
            + "    }\n"
            + "\n"
            + "    protected static boolean refused(MvcResult result) {\n"
            + "        int status = result.getResponse().getStatus();\n"
            + "        return status >= 400 && status < 500;\n"
            + "    }\n"
            + "\n"
            + "    // ---- values\n"
            + "\n"
            + "    /** Text of exactly this length, unique in the run. */\n"
            + "    protected String text(int length) {\n"
            + "        String unique = Long.toString(SEQUENCE.incrementAndGet(), 36);\n"
            + "        if (unique.length() >= length) return unique.substring(unique.length() - length);\n"
            + "        StringBuilder text = new StringBuilder(length);\n"
            + "        while (text.length() + unique.length() < length) text.append('0');\n"
            + "        return text.append(unique).toString();\n"
            + "    }\n"
            + "\n"
            + "    /** An email address of exactly this length, unique in the run. */\n"
            + "    protected String email(int length) {\n"
            + "        if (length < 6) return text(length);\n"
            + "        String local = text(Math.min(64, length - 5));\n"
            + "        int rest = length - local.length() - 1;\n"
            + "        StringBuilder domain = new StringBuilder(\"io\");\n"
            + "        while (domain.length() < rest) {\n"
            + "            int room = rest - domain.length() - 1;\n"
            + "            int label = room > 63 ? (room - 64 >= 1 ? 63 : 62) : room;\n"
            + "            StringBuilder part = new StringBuilder();\n"
            + "            for (int i = 0; i < label; i++) part.append('x');\n"
            + "            domain.insert(0, part.append('.'));\n"
            + "        }\n"
            + "        return local + \"@\" + domain;\n"
            + "    }\n"
            + "\n"
            + "    protected BigDecimal num(String value) {\n"
            + "        return new BigDecimal(value);\n"
            + "    }\n"
            + "\n"
            + "    /** A number of its own for each row: from the first, in whole steps, within as many as the bounds hold. */\n"
            + "    protected BigDecimal serial(String first, long span) {\n"
            + "        return new BigDecimal(first).add(BigDecimal.valueOf(SEQUENCE.incrementAndGet() % span));\n"
            + "    }\n"
            + "\n"
            + "    /**\n"
            + "     * The pattern's value, of its own for each row: each position given an alphabet takes one of\n"
            + "     * its characters (each was checked against the pattern when the test was written).\n"
            + "     */\n"
            + "    protected String vary(String sample, String... alphabets) {\n"
            + "        long n = SEQUENCE.incrementAndGet();\n"
            + "        char[] text = sample.toCharArray();\n"
            + "        for (int i = alphabets.length - 1; i >= 0 && n > 0; i--) {\n"
            + "            String alphabet = alphabets[i];\n"
            + "            if (alphabet.isEmpty()) continue;\n"
            + "            text[i] = alphabet.charAt((int) (n % alphabet.length()));\n"
            + "            n /= alphabet.length();\n"
            + "        }\n"
            + "        return new String(text);\n"
            + "    }\n"
            + "\n"
            + "    /** A JSON object: name, value, name, value ... */\n"
            + "    protected Map<String, Object> obj(Object... namesAndValues) {\n"
            + "        Map<String, Object> object = new LinkedHashMap<>();\n"
            + "        for (int i = 0; i + 1 < namesAndValues.length; i += 2) object.put((String) namesAndValues[i], namesAndValues[i + 1]);\n"
            + "        return object;\n"
            + "    }\n"
            + "\n"
            + "    protected List<Object> list(Object... items) {\n"
            + "        return new ArrayList<>(Arrays.asList(items));\n"
            + "    }\n"
            + "\n"
            + "    /** The body with one field set - or sent as null. */\n"
            + "    protected Map<String, Object> withValue(Map<String, Object> body, String field, Object value) {\n"
            + "        body.put(field, value);\n"
            + "        return body;\n"
            + "    }\n"
            + "\n"
            + "    /** The value as JSON: maps, lists, text, numbers, booleans and null. */\n"
            + "    protected String json(Object value) {\n"
            + "        if (value == null) return \"null\";\n"
            + "        if (value instanceof BigDecimal) return ((BigDecimal) value).toPlainString();\n"
            + "        if (value instanceof Number || value instanceof Boolean) return value.toString();\n"
            + "        if (value instanceof Map) {\n"
            + "            StringBuilder object = new StringBuilder(\"{\");\n"
            + "            for (Map.Entry<?, ?> en : ((Map<?, ?>) value).entrySet()) {\n"
            + "                if (object.length() > 1) object.append(',');\n"
            + "                object.append(json(String.valueOf(en.getKey()))).append(':').append(json(en.getValue()));\n"
            + "            }\n"
            + "            return object.append('}').toString();\n"
            + "        }\n"
            + "        if (value instanceof Collection) {\n"
            + "            StringBuilder array = new StringBuilder(\"[\");\n"
            + "            for (Object item : (Collection<?>) value) {\n"
            + "                if (array.length() > 1) array.append(',');\n"
            + "                array.append(json(item));\n"
            + "            }\n"
            + "            return array.append(']').toString();\n"
            + "        }\n"
            + "        StringBuilder text = new StringBuilder(\"\\\"\");\n"
            + "        for (char ch : value.toString().toCharArray()) {\n"
            + "            if (ch == '\"' || ch == '\\\\') text.append('\\\\').append(ch);\n"
            + "            else if (ch < 0x20) text.append(String.format(\"\\\\u%04x\", (int) ch));\n"
            + "            else text.append(ch);\n"
            + "        }\n"
            + "        return text.append('\"').toString();\n"
            + "    }\n";

    private static String kotlinSupport(
            String pkg,
            String name,
            String domainName,
            Settings s,
            String mockMvc,
            boolean guarded,
            boolean system,
            Principal principal,
            boolean tx,
            String why,
            Access access) {
        Lang lang = new Lang(true);
        StringBuilder k = new StringBuilder();
        k.append(OWNED).append('\n').append("package ").append(pkg).append("\n\n");
        k.append("import com.jayway.jsonpath.JsonPath\n");
        if (guarded) k.append("import io.elasticore.springboot3.security.CurrentUser\n");
        k.append("import org.junit.jupiter.api.Assertions\n").append("import org.junit.jupiter.api.Assumptions\n");
        if (s.mongo) k.append("import org.junit.jupiter.api.BeforeAll\n");
        k.append("import org.springframework.beans.factory.annotation.Autowired\n")
                .append("import ")
                .append(mockMvc)
                .append('\n')
                .append("import org.springframework.boot.test.context.SpringBootTest\n")
                .append("import org.springframework.http.MediaType\n")
                .append("import org.springframework.mock.web.MockHttpSession\n");
        if (s.mongo) {
            k.append("import org.springframework.test.context.DynamicPropertyRegistry\n")
                    .append("import org.springframework.test.context.DynamicPropertySource\n");
        }
        k.append("import org.springframework.test.web.servlet.MockMvc\n")
                .append("import org.springframework.test.web.servlet.MvcResult\n")
                .append("import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder\n")
                .append("import org.springframework.test.web.servlet.request.MockMvcRequestBuilders\n");
        if (tx) k.append("import org.springframework.transaction.annotation.Transactional\n");
        k.append("import java.math.BigDecimal\n")
                .append("import java.nio.charset.StandardCharsets\n")
                .append("import java.util.concurrent.ThreadLocalRandom\n")
                .append("import java.util.concurrent.atomic.AtomicLong\n\n");
        k.append(doc(domainName, s, guarded, system, principal, why, access));
        k.append(springBootTest(s, lang));

        k.append("@AutoConfigureMockMvc\n");
        if (tx) k.append("@Transactional\n");
        k.append("abstract class ").append(name).append(" {\n\n");
        k.append("    @Autowired\n")
                .append("    protected lateinit var mvc: MockMvc\n\n")
                .append("    /** The session signIn() opened, sent with every request after it; null for none. */\n")
                .append("    protected var session: MockHttpSession? = null\n\n")
                .append("    private var signedIn = false\n\n");
        k.append("    companion object {\n")
                .append(
                        "        /** A test's values are its own (logins, unique fields): each run starts somewhere new. */\n")
                .append("        private val SEQUENCE = AtomicLong(ThreadLocalRandom.current().nextLong(1L shl 30))\n");
        if (principal != null) {
            k.append("\n        private const val ")
                    .append(principal.constant)
                    .append(" = ")
                    .append(lang.string(principal.path))
                    .append('\n');
        }
        if (s.mongo) {
            k.append("\n")
                    .append("        /** The tests run on the MongoDB server " + MONGO_URI
                            + " names; without one they are skipped. */\n")
                    .append("        @JvmStatic\n")
                    .append("        @BeforeAll\n")
                    .append("        fun mongoServer() {\n")
                    .append("            Assumptions.assumeTrue(System.getenv(\"" + MONGO_URI + "\") != null,\n")
                    .append("                \"" + MONGO_URI + " names no MongoDB server (mongodb://host:port)\")\n")
                    .append("        }\n\n")
                    .append("        @JvmStatic\n")
                    .append("        @DynamicPropertySource\n")
                    .append("        fun mongo(registry: DynamicPropertyRegistry) {\n")
                    .append("            val server = System.getenv(\"" + MONGO_URI + "\") ?: return\n")
                    .append(
                            "            val uri = (if (server.endsWith(\"/\")) server else \"$server/\") + \"elasticore_contract\"\n")
                    .append("            registry.add(\"spring.data.mongodb.uri\") { uri }\n")
                    .append("            registry.add(\"spring.mongodb.uri\") { uri }\n")
                    .append("            registry.add(\"spring.data.mongodb.database\") { \"elasticore_contract\" }\n")
                    .append("            registry.add(\"spring.mongodb.database\") { \"elasticore_contract\" }\n")
                    .append("        }\n");
        }
        k.append("    }\n\n");
        k.append("    /**\n")
                .append("     * What every request gets added: nothing, as generated. Add the application's\n")
                .append("     * CSRF cookie and header here, say.\n")
                .append("     */\n")
                .append(
                        "    protected open fun request(builder: MockHttpServletRequestBuilder): MockHttpServletRequestBuilder = builder\n\n");
        if (principal != null) {
            k.append("    /**\n")
                    .append("     * Signs in before a test's first request: as an administrator, through the\n")
                    .append("     * generated sign-in - ")
                    .append(article(principal.entity))
                    .append(principal.entity)
                    .append(" made for the test as the application itself\n")
                    .append("     * (CurrentUser.runAsSystem). The tests check what the model declares, not who\n")
                    .append("     * may ask; sign in your way here if the application does.\n")
                    .append("     */\n")
                    .append("    protected open fun signIn() {\n")
                    .append("        val principal = ")
                    .append(principal.body)
                    .append('\n');
            if (principal.role != null) {
                k.append("        principal[")
                        .append(lang.string(principal.role))
                        .append("] = ")
                        .append(lang.string(principal.roleValue))
                        .append('\n');
            }
            k.append("        val made = asSystem(MockMvcRequestBuilders.post(")
                    .append(principal.constant)
                    .append(")\n")
                    .append("            .contentType(MediaType.APPLICATION_JSON).content(json(principal)))\n")
                    .append(
                            "        Assertions.assertTrue(ok(made)) { \"the administrator to sign in as was not made: \" + answer(made) }\n")
                    .append("        val signedInAs = mvc.perform(request(MockMvcRequestBuilders.post(")
                    .append(principal.constant)
                    .append(" + \"/signin\")\n")
                    .append("            .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)\n")
                    .append("            .content(json(obj(")
                    .append(lang.string(principal.login))
                    .append(", principal[")
                    .append(lang.string(principal.login))
                    .append("], ")
                    .append(lang.string(principal.password))
                    .append(", principal[")
                    .append(lang.string(principal.password))
                    .append("]))))).andReturn()\n")
                    .append(
                            "        Assertions.assertTrue(ok(signedInAs)) { \"the generated sign-in refused the administrator: \" + answer(signedInAs) }\n")
                    .append("        session = signedInAs.request.getSession(false) as MockHttpSession?\n")
                    .append("    }\n\n");
        } else {
            k.append("    /** Signs in before a test's first request: ")
                    .append(
                            guarded
                                    ? "nobody - the requests run as the application itself."
                                    : "nobody, as nothing asks who is calling.")
                    .append(" */\n")
                    .append("    protected open fun signIn() {\n")
                    .append("    }\n\n");
        }
        k.append(
                        "    /** Sends a request as every contract test does: JSON, signed in, with what request(...) adds. */\n")
                .append("    protected fun perform(builder: MockHttpServletRequestBuilder): MvcResult {\n")
                .append("        if (!signedIn) {\n")
                .append("            signedIn = true\n")
                .append("            signIn()\n")
                .append("        }\n")
                .append("        builder.accept(MediaType.APPLICATION_JSON)\n")
                .append("        session?.let { builder.session(it) }\n");
        if (system) k.append("        if (session == null) return asSystem(builder)\n");
        k.append("        return mvc.perform(request(builder)).andReturn()\n").append("    }\n\n");
        if (guarded) {
            k.append("    /** The request as the application itself: every @access check passes. */\n")
                    .append("    protected fun asSystem(builder: MockHttpServletRequestBuilder): MvcResult =\n")
                    .append(
                            "        CurrentUser.callAsSystem<MvcResult> { mvc.perform(request(builder.accept(MediaType.APPLICATION_JSON))).andReturn() }\n\n");
        }
        if (access.callers && principal != null) k.append(kotlinCallers(principal, lang));
        k.append(KOTLIN_HELPERS);
        k.append("}\n");
        return k.toString();
    }

    /** What the tests of @access send their requests with (Kotlin): as javaCallers. */
    private static String kotlinCallers(Principal p, Lang lang) {
        StringBuilder k = new StringBuilder();
        k.append("    // ---- the callers of the @access tests\n\n")
                .append(
                        "    /** Whom a test of @access sends a request as: a principal signIn(role) made - its key, which the @owner of a row of theirs holds - and the session it opened. */\n")
                .append("    protected class Caller(val id: Any?, val session: MockHttpSession?)\n\n")
                .append("    /**\n")
                .append("     * Signs in a principal of its own for a test of @access: ")
                .append(article(p.entity))
                .append(p.entity)
                .append(" made as the\n")
                .append("     * application itself (CurrentUser.runAsSystem) with this role")
                .append(p.role == null ? " - it has none to set -" : ", as its " + p.role + " sends it,")
                .append("\n")
                .append("     * and signed in through the generated sign-in, as signIn() signs in the administrator.\n")
                .append("     * Sign in your way here if the application does.\n")
                .append("     */\n")
                .append("    protected open fun signIn(role: String?): Caller {\n")
                .append("        val principal = ")
                .append(p.body)
                .append('\n');
        if (p.role != null) {
            k.append("        if (role != null) principal[")
                    .append(lang.string(p.role))
                    .append("] = role\n");
        }
        k.append("        val made = asSystem(MockMvcRequestBuilders.post(")
                .append(p.constant)
                .append(")\n")
                .append("            .contentType(MediaType.APPLICATION_JSON).content(json(principal)))\n")
                .append(
                        "        Assertions.assertTrue(ok(made)) { \"the principal to sign in as was not made: \" + answer(made) }\n")
                .append("        val signedInAs = mvc.perform(request(MockMvcRequestBuilders.post(")
                .append(p.constant)
                .append(" + \"/signin\")\n")
                .append("            .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)\n")
                .append("            .content(json(obj(")
                .append(lang.string(p.login))
                .append(", principal[")
                .append(lang.string(p.login))
                .append("], ")
                .append(lang.string(p.password))
                .append(", principal[")
                .append(lang.string(p.password))
                .append("]))))).andReturn()\n")
                .append(
                        "        Assertions.assertTrue(ok(signedInAs)) { \"the generated sign-in refused the principal: \" + answer(signedInAs) }\n")
                .append("        return Caller(value(made, ")
                .append(lang.string(p.key))
                .append("), signedInAs.request.getSession(false) as MockHttpSession?)\n")
                .append("    }\n\n");
        k.append(KOTLIN_CALLER_HELPERS);
        return k.toString();
    }

    /** The requests and checks of the @access tests (Kotlin). */
    private static final String KOTLIN_CALLER_HELPERS = ""
            + "    /** The request as this caller - or as a visitor, for null: with what request(...) adds, and no other session. */\n"
            + "    protected fun sentBy(caller: Caller?, builder: MockHttpServletRequestBuilder): MvcResult {\n"
            + "        builder.accept(MediaType.APPLICATION_JSON)\n"
            + "        caller?.session?.let { builder.session(it) }\n"
            + "        return mvc.perform(request(builder)).andReturn()\n"
            + "    }\n"
            + "\n"
            + "    protected fun reading(path: String, id: Any?): MockHttpServletRequestBuilder = MockMvcRequestBuilders.get(\"$path/$id\")\n"
            + "\n"
            + "    protected fun searching(path: String, body: Any?): MockHttpServletRequestBuilder =\n"
            + "        MockMvcRequestBuilders.post(\"$path/search\").contentType(MediaType.APPLICATION_JSON).content(json(body))\n"
            + "\n"
            + "    protected fun creating(path: String, body: Any?): MockHttpServletRequestBuilder =\n"
            + "        MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(json(body))\n"
            + "\n"
            + "    /** An update of the row by its key alone: nothing changes, and who may change it is asked all the same. */\n"
            + "    protected fun updating(path: String, body: Any?): MockHttpServletRequestBuilder =\n"
            + "        MockMvcRequestBuilders.put(path).contentType(MediaType.APPLICATION_JSON).content(json(body))\n"
            + "\n"
            + "    protected fun deleting(path: String, id: Any?): MockHttpServletRequestBuilder = MockMvcRequestBuilders.delete(\"$path/$id\")\n"
            + "\n"
            + "    protected fun commanding(path: String, id: Any?, command: String, body: Any?): MockHttpServletRequestBuilder {\n"
            + "        val builder = MockMvcRequestBuilders.post(\"$path/$id/$command\")\n"
            + "        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json(body))\n"
            + "        return builder\n"
            + "    }\n"
            + "\n"
            + "    /** A row this caller makes, and its key: let in (assertAllowed says how it fails or is aborted). */\n"
            + "    protected fun createdIdBy(caller: Caller?, path: String, body: Any?, key: String): Any {\n"
            + "        val made = sentBy(caller, creating(path, body))\n"
            + "        assertAllowed(made)\n"
            + "        return value(made, key) ?: Assertions.fail<Any>(\"POST $path answered no $key: \" + answer(made))\n"
            + "    }\n"
            + "\n"
            + "    /**\n"
            + "     * Let in: 2xx. Refused as @access refuses - 401, 403, or 404 not-found for a row the caller\n"
            + "     * may not see - or failed (5xx), the test fails; refused by another rule of the\n"
            + "     * application's own (a reference to a row it needs, say), it is aborted with the reason, as\n"
            + "     * succeeded says.\n"
            + "     */\n"
            + "    protected fun assertAllowed(result: MvcResult) {\n"
            + "        val status = result.response.status\n"
            + "        val code = code(result)\n"
            + "        if (status == 401 || status == 403 || (status == 404 && (code == null || code == \"not-found\"))) {\n"
            + "            Assertions.fail<Unit>(\"let in by @access, and refused: \" + answer(result))\n"
            + "        }\n"
            + "        succeeded(result, \"the request\")\n"
            + "    }\n"
            + "\n"
            + "    /** Refused by @access: this status, and this problem code where the answer carries one. */\n"
            + "    protected fun assertDenied(result: MvcResult, status: Int, expected: String) {\n"
            + "        Assertions.assertEquals(status, result.response.status) { \"expected $status $expected: \" + answer(result) }\n"
            + "        val sent = code(result)\n"
            + "        if (sent != null) Assertions.assertEquals(expected, sent) { answer(result) }\n"
            + "    }\n"
            + "\n"
            + "    /** The search let the caller in, and its answer holds the row of this key. */\n"
            + "    protected fun assertFinds(result: MvcResult, key: String, id: Any?) {\n"
            + "        assertAllowed(result)\n"
            + "        Assertions.assertTrue(keysOf(result, key).contains(id.toString())) { \"the search does not hold $key $id: \" + answer(result) }\n"
            + "    }\n"
            + "\n"
            + "    /** The search let the caller in, and its answer does not hold the row of this key. */\n"
            + "    protected fun assertMisses(result: MvcResult, key: String, id: Any?) {\n"
            + "        assertAllowed(result)\n"
            + "        Assertions.assertFalse(keysOf(result, key).contains(id.toString())) { \"the search holds $key $id: \" + answer(result) }\n"
            + "    }\n"
            + "\n"
            + "    /** The keys of the rows a search answered: those of its page, or of the list it is. */\n"
            + "    protected fun keysOf(result: MvcResult, key: String): List<String> {\n"
            + "        var rows: Any? = try {\n"
            + "            JsonPath.read<Any?>(body(result), \"\\$.content\")\n"
            + "        } catch (notAPage: RuntimeException) {\n"
            + "            null\n"
            + "        }\n"
            + "        if (rows !is List<*>) {\n"
            + "            rows = try {\n"
            + "                JsonPath.read<Any?>(body(result), \"\\$\")\n"
            + "            } catch (notAList: RuntimeException) {\n"
            + "                null\n"
            + "            }\n"
            + "        }\n"
            + "        return (rows as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { it[key].toString() }\n"
            + "    }\n"
            + "\n";

    /** The requests, the checks and the values every contract test uses (Kotlin). */
    private static final String KOTLIN_HELPERS = ""
            + "    protected fun post(path: String, body: Any?): MvcResult =\n"
            + "        perform(MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(json(body)))\n"
            + "\n"
            + "    /** A command of the row: POST path/{id}/command, with its parameters when it takes any. */\n"
            + "    protected fun command(path: String, id: Any?, command: String, body: Any?): MvcResult {\n"
            + "        val builder = MockMvcRequestBuilders.post(\"$path/$id/$command\")\n"
            + "        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json(body))\n"
            + "        return perform(builder)\n"
            + "    }\n"
            + "\n"
            + "    /**\n"
            + "     * A row the test needs: made, or the test is aborted with the reason - a row the\n"
            + "     * application refuses to make (a rule of its own) is no regression of what is tested.\n"
            + "     */\n"
            + "    protected fun create(path: String, body: Any?): MvcResult {\n"
            + "        val made = post(path, body)\n"
            + "        succeeded(made, \"POST $path, a row the test needs,\")\n"
            + "        return made\n"
            + "    }\n"
            + "\n"
            + "    /** A row made, and its key as the answer says it. */\n"
            + "    protected fun createdId(path: String, body: Any?, key: String): Any {\n"
            + "        val made = create(path, body)\n"
            + "        return value(made, key) ?: Assertions.fail<Any>(\"POST $path answered no $key: \" + answer(made))\n"
            + "    }\n"
            + "\n"
            + "    /** A step to the state the test starts from: given, or the test is aborted when a rule of the application's refuses it. */\n"
            + "    protected fun given(step: MvcResult) = succeeded(step, \"a step to the state the test starts from\")\n"
            + "\n"
            + "    /** The command was given: 2xx. */\n"
            + "    protected fun assertMoved(result: MvcResult) = succeeded(result, \"the command\")\n"
            + "\n"
            + "    /**\n"
            + "     * 2xx. Refused by a rule of the application's own - a check its code makes, a unique value\n"
            + "     * someone holds - the test is aborted with the reason; refused for what the model declares (a\n"
            + "     * field, an invariant, a state) or failed (5xx), it fails: that is what the tests are about.\n"
            + "     */\n"
            + "    private fun succeeded(result: MvcResult, what: String) {\n"
            + "        if (ok(result)) return\n"
            + "        if (refused(result) && !declared(code(result))) {\n"
            + "            skip(\"$what was refused by a rule of the application: \" + answer(result))\n"
            + "        }\n"
            + "        Assertions.fail<Unit>(\"$what failed: \" + answer(result))\n"
            + "    }\n"
            + "\n"
            + "    /** Whether a refusal is one the model declares: a field's constraint (invalid), an invariant, a lifecycle. */\n"
            + "    protected fun declared(code: String?): Boolean =\n"
            + "        code == \"invalid\" || (code != null && (code.startsWith(\"invariant.\") || code.startsWith(\"state.\")))\n"
            + "\n"
            + "    /** The row was made: 2xx (refused as succeeded says). */\n"
            + "    protected fun assertCreated(result: MvcResult) = succeeded(result, \"the row\")\n"
            + "\n"
            + "    /** Aborts the test with the reason - in the report, and in the output, which a build's XML report keeps. */\n"
            + "    protected fun skip(reason: String) {\n"
            + "        println(\"[contract test skipped] \" + reason)\n"
            + "        Assumptions.abort<Unit>(reason)\n"
            + "    }\n"
            + "\n"
            + "    /** Refused with this status and the problem's code. */\n"
            + "    protected fun assertRefused(result: MvcResult, status: Int, expected: String) {\n"
            + "        Assertions.assertEquals(status, result.response.status) { \"expected $status $expected: \" + answer(result) }\n"
            + "        Assertions.assertEquals(expected, code(result)) { answer(result) }\n"
            + "    }\n"
            + "\n"
            + "    /** Refused (400) for this field, with this constraint's code: NotNull, Size, Min ... */\n"
            + "    protected fun assertFieldError(result: MvcResult, field: String, code: String) {\n"
            + "        Assertions.assertEquals(400, result.response.status) { \"expected 400 $field $code: \" + answer(result) }\n"
            + "        for (error in fieldErrors(result)) {\n"
            + "            if (field == error[\"field\"] && code == error[\"code\"]) return\n"
            + "        }\n"
            + "        Assertions.fail<Unit>(\"expected the field error $field $code: \" + answer(result))\n"
            + "    }\n"
            + "\n"
            + "    /** The answer shows this value for the field. */\n"
            + "    protected fun assertValue(result: MvcResult, field: String, expected: Any?) {\n"
            + "        Assertions.assertEquals(expected.toString(), value(result, field).toString()) { \"$field: \" + answer(result) }\n"
            + "    }\n"
            + "\n"
            + "    /** The answer shows the field set: a @stamp written by the move. */\n"
            + "    protected fun assertStamped(result: MvcResult, field: String) {\n"
            + "        Assertions.assertNotNull(value(result, field)) { \"$field is not set: \" + answer(result) }\n"
            + "    }\n"
            + "\n"
            + "    /** A field of the answer, or null. */\n"
            + "    protected fun value(result: MvcResult, field: String): Any? =\n"
            + "        try {\n"
            + "            JsonPath.read<Any?>(body(result), \"\\$['$field']\")\n"
            + "        } catch (notThere: RuntimeException) {\n"
            + "            null\n"
            + "        }\n"
            + "\n"
            + "    /** The problem's code, or null. */\n"
            + "    protected fun code(result: MvcResult): String? = value(result, \"code\")?.toString()\n"
            + "\n"
            + "    private fun fieldErrors(result: MvcResult): List<Map<String, Any?>> =\n"
            + "        try {\n"
            + "            JsonPath.read<List<Map<String, Any?>>>(body(result), \"\\$.fieldErrors\")\n"
            + "        } catch (none: RuntimeException) {\n"
            + "            emptyList()\n"
            + "        }\n"
            + "\n"
            + "    protected fun body(result: MvcResult): String = result.response.getContentAsString(StandardCharsets.UTF_8)\n"
            + "\n"
            + "    /** The status and the body, for a message. */\n"
            + "    protected fun answer(result: MvcResult): String = \"${result.response.status} \" + body(result)\n"
            + "\n"
            + "    protected fun ok(result: MvcResult): Boolean = result.response.status in 200..299\n"
            + "\n"
            + "    protected fun refused(result: MvcResult): Boolean = result.response.status in 400..499\n"
            + "\n"
            + "    // ---- values\n"
            + "\n"
            + "    /** Text of exactly this length, unique in the run. */\n"
            + "    protected fun text(length: Int): String {\n"
            + "        val unique = java.lang.Long.toString(SEQUENCE.incrementAndGet(), 36)\n"
            + "        if (unique.length >= length) return unique.substring(unique.length - length)\n"
            + "        return \"0\".repeat(length - unique.length) + unique\n"
            + "    }\n"
            + "\n"
            + "    /** An email address of exactly this length, unique in the run. */\n"
            + "    protected fun email(length: Int): String {\n"
            + "        if (length < 6) return text(length)\n"
            + "        val local = text(minOf(64, length - 5))\n"
            + "        val rest = length - local.length - 1\n"
            + "        val domain = StringBuilder(\"io\")\n"
            + "        while (domain.length < rest) {\n"
            + "            val room = rest - domain.length - 1\n"
            + "            val label = if (room > 63) (if (room - 64 >= 1) 63 else 62) else room\n"
            + "            domain.insert(0, \"x\".repeat(label) + \".\")\n"
            + "        }\n"
            + "        return \"$local@$domain\"\n"
            + "    }\n"
            + "\n"
            + "    protected fun num(value: String): BigDecimal = BigDecimal(value)\n"
            + "\n"
            + "    /** A number of its own for each row: from the first, in whole steps, within as many as the bounds hold. */\n"
            + "    protected fun serial(first: String, span: Long): BigDecimal =\n"
            + "        BigDecimal(first).add(BigDecimal.valueOf(SEQUENCE.incrementAndGet() % span))\n"
            + "\n"
            + "    /**\n"
            + "     * The pattern's value, of its own for each row: each position given an alphabet takes one of\n"
            + "     * its characters (each was checked against the pattern when the test was written).\n"
            + "     */\n"
            + "    protected fun vary(sample: String, vararg alphabets: String): String {\n"
            + "        var n = SEQUENCE.incrementAndGet()\n"
            + "        val text = sample.toCharArray()\n"
            + "        var i = alphabets.size - 1\n"
            + "        while (i >= 0 && n > 0) {\n"
            + "            val alphabet = alphabets[i]\n"
            + "            if (alphabet.isNotEmpty()) {\n"
            + "                text[i] = alphabet[(n % alphabet.length).toInt()]\n"
            + "                n /= alphabet.length\n"
            + "            }\n"
            + "            i--\n"
            + "        }\n"
            + "        return String(text)\n"
            + "    }\n"
            + "\n"
            + "    /** A JSON object: name, value, name, value ... */\n"
            + "    protected fun obj(vararg namesAndValues: Any?): MutableMap<String, Any?> {\n"
            + "        val obj = LinkedHashMap<String, Any?>()\n"
            + "        var i = 0\n"
            + "        while (i + 1 < namesAndValues.size) {\n"
            + "            obj[namesAndValues[i] as String] = namesAndValues[i + 1]\n"
            + "            i += 2\n"
            + "        }\n"
            + "        return obj\n"
            + "    }\n"
            + "\n"
            + "    protected fun list(vararg items: Any?): MutableList<Any?> = items.toMutableList()\n"
            + "\n"
            + "    /** The body with one field set - or sent as null. */\n"
            + "    protected fun withValue(body: MutableMap<String, Any?>, field: String, value: Any?): MutableMap<String, Any?> {\n"
            + "        body[field] = value\n"
            + "        return body\n"
            + "    }\n"
            + "\n"
            + "    /** The value as JSON: maps, lists, text, numbers, booleans and null. */\n"
            + "    protected fun json(value: Any?): String = when (value) {\n"
            + "        null -> \"null\"\n"
            + "        is BigDecimal -> value.toPlainString()\n"
            + "        is Number, is Boolean -> value.toString()\n"
            + "        is Map<*, *> -> value.entries.joinToString(\",\", \"{\", \"}\") { json(it.key.toString()) + \":\" + json(it.value) }\n"
            + "        is Collection<*> -> value.joinToString(\",\", \"[\", \"]\") { json(it) }\n"
            + "        else -> {\n"
            + "            val text = StringBuilder(\"\\\"\")\n"
            + "            for (ch in value.toString()) {\n"
            + "                when {\n"
            + "                    ch == '\"' || ch == '\\\\' -> text.append('\\\\').append(ch)\n"
            + "                    ch < ' ' -> text.append(String.format(\"\\\\u%04x\", ch.code))\n"
            + "                    else -> text.append(ch)\n"
            + "                }\n"
            + "            }\n"
            + "            text.append('\"').toString()\n"
            + "        }\n"
            + "    }\n";

    private void skip(String why) {
        if (!skipped.contains(why)) skipped.add(why);
    }

    /** The summary line: contract tests: 132 written, 4 skipped (Quote.lines: ...). */
    String summary() {
        StringBuilder sb = new StringBuilder("contract tests: ")
                .append(written)
                .append(" written, ")
                .append(skipped.size())
                .append(" skipped");
        if (!skipped.isEmpty())
            sb.append(" (").append(String.join("; ", skipped)).append(')');
        return sb.toString();
    }

    // ---- the two languages -------------------------------------------------------------

    /** What is written differently in Java and Kotlin: a statement, a value, a string. */
    static final class Lang {
        final boolean kotlin;

        Lang(boolean kotlin) {
            this.kotlin = kotlin;
        }

        String stmt(String code) {
            return kotlin ? code : code + ";";
        }

        String val(String name, String expression) {
            return kotlin ? "val " + name + " = " + expression : (typeOf(name) + " " + name + " = " + expression + ";");
        }

        /** A caller signIn(role) signed in: Caller owner = signIn("U"); */
        String caller(String name, String expression) {
            return kotlin ? "val " + name + " = " + expression : "Caller " + name + " = " + expression + ";";
        }

        private static String typeOf(String name) {
            return "id".equals(name) ? "Object" : "MvcResult";
        }

        String importLine(String fqcn) {
            return kotlin ? "import " + fqcn + "\n" : "import " + fqcn + ";\n";
        }

        /** A string literal: quotes and backslashes escaped, $ too in Kotlin, anything past ASCII as \\uXXXX. */
        String string(String text) {
            StringBuilder sb = new StringBuilder("\"");
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '"' || ch == '\\') sb.append('\\').append(ch);
                else if (ch == '$' && kotlin) sb.append("\\$");
                else if (ch == '\n') sb.append("\\n");
                else if (ch == '\t') sb.append("\\t");
                else if (ch < 0x20 || ch > 0x7e) sb.append(String.format("\\u%04x", (int) ch));
                else sb.append(ch);
            }
            return sb.append('"').toString();
        }
    }
}
