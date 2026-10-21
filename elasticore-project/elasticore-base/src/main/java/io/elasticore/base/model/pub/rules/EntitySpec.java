package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.entity.Field;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only view of a DSL entity for rule authors, plus the handle
 * to its {@link SourceCode} under construction.
 *
 * PROTOTYPE - in the full implementation this wraps the existing
 * model Entity; the prototype builds it directly from parsed Fields.
 */
public final class EntitySpec {

    private final String name;
    private final List<FieldSpec> fields = new ArrayList<FieldSpec>();

    /** Names of entities known to the model, used to detect relation fields. */
    private final Set<String> knownEntities = new HashSet<String>();

    /** Parent entity name from @extend, or null. */
    private String parentName;

    /** Enum models of the domain: name -> db-column length ("" when none). */
    private final java.util.Map<String, String> enumDbLength = new java.util.HashMap<String, String>();

    /** Enum models whose meta carries @db (they get an EntityConverter). */
    private final Set<String> enumWithDb = new HashSet<String>();

    /** Domain identifiers: lower form for paths, Pascal form for class names. */
    private String domainName;
    private String domainClassName;

    /** Package roots per artifact, e.g. package("entity", "io.elasticore...entity"). */
    private final java.util.Map<String, String> packages = new java.util.HashMap<String, String>();

    private SourceCode code;

    public EntitySpec(String name) {
        this.name = name;
        this.knownEntities.add(name);
    }

    public EntitySpec knownEntity(String entityName) {
        this.knownEntities.add(entityName);
        return this;
    }

    /**
     * The names of the model's own types this spec knows - its entities, its
     * enums and itself. Generated files write them by simple name, from their
     * own package or an import, so an annotation of the same name has to be
     * written in full: an entity named Comment and Hibernate's @Comment.
     */
    public Set<String> modelTypeNames() {
        Set<String> names = new HashSet<String>(knownEntities);
        names.addAll(enumDbLength.keySet());
        names.add(name);
        return names;
    }

    public EntitySpec addField(Field field) {
        this.fields.add(new FieldSpec(field, this));
        return this;
    }

    /** A business command the DSL declares on the entity: its name and parameters. */
    public static final class CommandSpec {
        private final String name;
        private final List<FieldSpec> params;
        private final String dtoName;

        CommandSpec(String name, List<FieldSpec> params, String dtoName) {
            this.name = name;
            this.params = params;
            this.dtoName = dtoName;
        }

        public String name() {
            return name;
        }

        public List<FieldSpec> params() {
            return params;
        }

        /** The DTO the parameters are sent as, or null when there are none. */
        public String dtoName() {
            return params.isEmpty() ? null : dtoName;
        }
    }

    private final List<CommandSpec> commands = new ArrayList<CommandSpec>();

    // ---- lifecycles ("states:") ------------------------------------------

    /** One command's move: from any of these states to that one. */
    public static final class Move {
        private final String command;
        private final List<String> from;
        private final String to;

        Move(String command, List<String> from, String to) {
            this.command = command;
            this.from = from;
            this.to = to;
        }

        public String command() {
            return command;
        }

        public List<String> from() {
            return from;
        }

        public String to() {
            return to;
        }
    }

    /** A field's lifecycle: where it starts and how each command moves it. */
    public static final class StateMachine {
        private final String field;
        private final String initial;
        private final List<Move> moves;
        private final List<String> deletable;

        StateMachine(String field, String initial, List<Move> moves, List<String> deletable) {
            this.field = field;
            this.initial = initial;
            this.moves = moves;
            this.deletable = deletable;
        }

        /**
         * The states a row may be deleted in ("delete: CANCELLED, PLACED"),
         * or null when the lifecycle does not say - deleting is then not its
         * business, as before.
         */
        public List<String> deletable() {
            return deletable;
        }

        public String field() {
            return field;
        }

        /** The state a new row starts in, or null when the DSL names none. */
        public String initial() {
            return initial;
        }

        public List<Move> moves() {
            return moves;
        }

        /** This lifecycle's move for the command, or null. */
        public Move moveOf(String command) {
            for (Move move : moves) {
                if (move.command().equals(command)) return move;
            }
            return null;
        }
    }

    private final List<StateMachine> stateMachines = new ArrayList<StateMachine>();

    /**
     * Declares a lifecycle: "initial" names the first state, "delete" the
     * states a row may be deleted in, every other key is a command and its
     * move, "PLACED -> SHIPPED" or "PLACED, PAID -> CANCELLED". A move that
     * does not read that way is left out, and said so.
     */
    public EntitySpec addStateMachine(String field, java.util.Map<String, String> declared) {
        String initial = null;
        List<String> deletable = null;
        List<Move> moves = new ArrayList<Move>();
        for (java.util.Map.Entry<String, String> en : declared.entrySet()) {
            if ("initial".equals(en.getKey())) {
                initial = en.getValue();
                continue;
            }
            if (DELETE.equals(en.getKey())) {
                // An empty list (delete: []) is a list: no state may be deleted.
                deletable = statesOf(en.getValue());
                if (en.getValue() != null && en.getValue().contains("->")) {
                    io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                            "[%s] states.%s.delete: '%s' is not a list of states (delete: CANCELLED, PLACED); deleting is left unguarded.",
                            name, field, en.getValue()));
                    deletable = null;
                }
                continue;
            }
            String text = en.getValue();
            int arrow = text == null ? -1 : text.indexOf("->");
            if (arrow <= 0) {
                io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                        "[%s] states.%s.%s: '%s' is not FROM -> TO; the move is left out.",
                        name, field, en.getKey(), text));
                continue;
            }
            List<String> from = new ArrayList<String>();
            for (String s : text.substring(0, arrow).split(",")) {
                if (!s.trim().isEmpty()) from.add(s.trim());
            }
            moves.add(new Move(en.getKey(), from, text.substring(arrow + 2).trim()));
        }
        stateMachines.add(new StateMachine(field, initial, moves, deletable));
        return this;
    }

    /** The key of a lifecycle that names the states a row may be deleted in, not a command. */
    public static final String DELETE = "delete";

    /** "CANCELLED, PLACED" or "[CANCELLED, PLACED]" - a YAML list arrives as the latter. */
    private static List<String> statesOf(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        String t = text.trim();
        if (t.startsWith("[") && t.endsWith("]")) t = t.substring(1, t.length() - 1);
        for (String s : t.split(",")) {
            if (!s.trim().isEmpty()) out.add(s.trim());
        }
        return out;
    }

    public List<StateMachine> stateMachines() {
        return stateMachines;
    }

    /**
     * Why a lifecycle cannot be written, or null when it can: its field must
     * be an enum of the model and every state it names a constant of that
     * enum. A command may move several lifecycles; it is then written only
     * when all of them can be (lifecycleAccepts).
     */
    public String lifecycleProblem(StateMachine m) {
        FieldSpec field = null;
        for (FieldSpec f : fields) {
            if (f.name().equals(m.field())) field = f;
        }
        String why = null;
        String type = field == null ? null : simpleTypeName(field.typeName());
        if (field == null) why = "names " + m.field() + ", which " + getName() + " has no field for";
        else if (!isKnownEnum(type)) why = "names " + m.field() + ", which is not an enum of the model";
        else {
            // A typo would otherwise reach the compiler as an unknown symbol.
            List<String> constants = enumConstantNamesOf(type);
            if (constants != null) {
                List<String> named = new ArrayList<String>();
                if (m.initial() != null) named.add(m.initial());
                if (m.deletable() != null) named.addAll(m.deletable());
                for (Move move : m.moves()) {
                    named.addAll(move.from());
                    named.add(move.to());
                }
                for (String state : named) {
                    if (!constants.contains(state)) {
                        StringBuilder all = new StringBuilder();
                        for (String c : constants) all.append(all.length() > 0 ? ", " : "").append(c);
                        why = "." + m.field() + " names " + state + ", which " + type + " does not have (it has " + all + ")";
                        break;
                    }
                }
            }
        }
        return why;
    }

    private static String simpleTypeName(String type) {
        if (type == null) return null;
        String t = type.trim();
        if (t.endsWith("?")) t = t.substring(0, t.length() - 1);
        int dot = t.lastIndexOf('.');
        return dot >= 0 ? t.substring(dot + 1) : t;
    }

    /** Every lifecycle this command moves, in the order the DSL declares them. */
    public List<StateMachine> machinesOf(String command) {
        List<StateMachine> out = new ArrayList<StateMachine>();
        for (StateMachine m : stateMachines) {
            if (m.moveOf(command) != null) out.add(m);
        }
        return out;
    }

    /** Whether the command is a move of at least one lifecycle, and every lifecycle it moves can be written. */
    public boolean lifecycleAccepts(String command) {
        List<StateMachine> machines = machinesOf(command);
        if (machines.isEmpty()) return false;
        for (StateMachine m : machines) {
            if (lifecycleProblem(m) != null) return false;
        }
        return true;
    }

    /** The first lifecycle that holds this command's move, or null. */
    public StateMachine machineOf(String command) {
        for (StateMachine m : stateMachines) {
            for (Move move : m.moves()) {
                if (move.command().equals(command)) return m;
            }
        }
        return null;
    }

    /** The move a command makes, or null when it makes none. */
    public Move moveOf(String command) {
        for (StateMachine m : stateMachines) {
            for (Move move : m.moves()) {
                if (move.command().equals(command)) return move;
            }
        }
        return null;
    }

    // ---- derived values (@snapshot, @compute) ----------------------------

    private final java.util.Set<String> derivingEntities = new java.util.HashSet<String>();

    private final java.util.Map<String, String> readLevels = new java.util.HashMap<String, String>();

    /** Registers the level an entity of the model is read at (@access), for DTOs that would carry a row of it. */
    public EntitySpec knownReadLevel(String entityName, String level) {
        readLevels.put(entityName, level);
        return this;
    }

    /** The level that entity is read at, or null when it declares no @access. */
    public String readLevelOf(String entityName) {
        return readLevels.get(entityName);
    }

    /** Registers an entity of the model that has @snapshot or @compute fields. */
    public EntitySpec knownDeriving(String entityName) {
        derivingEntities.add(entityName);
        return this;
    }

    /** Whether that entity derives values of its own - and so has derive(). */
    public boolean derives(String entityName) {
        return derivingEntities.contains(entityName);
    }

    private final java.util.Set<String> referencingEntities = new java.util.HashSet<String>();

    /**
     * Registers an entity of the model that names rows of other aggregates by
     * their identity - itself, or through the members it owns.
     */
    public EntitySpec knownReferencing(String entityName) {
        referencingEntities.add(entityName);
        return this;
    }

    /** Whether that entity checks the rows it names - and so has requireReferences(). */
    public boolean references(String entityName) {
        return referencingEntities.contains(entityName);
    }

    private int signInEntities;

    /** How many entities of the application say @signin - the generated sign-in is written for one only. */
    public EntitySpec knownSignInEntities(int count) {
        this.signInEntities = count;
        return this;
    }

    public int signInEntities() {
        return signInEntities;
    }

    private java.util.List<String> otherLoginEntities = java.util.Collections.emptyList();

    /** The application's entities with @login and @password that do not say @signin. */
    public EntitySpec knownOtherLoginEntities(java.util.List<String> names) {
        this.otherLoginEntities = names == null ? java.util.Collections.<String>emptyList() : names;
        return this;
    }

    public java.util.List<String> otherLoginEntities() {
        return otherLoginEntities;
    }

    private final java.util.Set<String> optionalEmbeddables = new java.util.HashSet<String>();

    /** Registers a value type (@embeddable) some entity of the model embeds without requiring it. */
    public EntitySpec knownOptionalEmbeddable(String embeddableName) {
        optionalEmbeddables.add(embeddableName);
        return this;
    }

    /**
     * Whether that value type is embedded somewhere as optional - so its columns
     * may be empty there, whatever its own fields require when it is present.
     */
    public boolean embeddedOptionally(String embeddableName) {
        return optionalEmbeddables.contains(embeddableName);
    }

    /** Declares a business command; its parameters are parsed like fields. */
    public EntitySpec addCommand(String name, List<Field> params) {
        List<FieldSpec> specs = new ArrayList<FieldSpec>();
        if (params != null) {
            for (Field f : params) specs.add(new FieldSpec(f, this));
        }
        String dto = getName() + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "Command";
        commands.add(new CommandSpec(name, java.util.Collections.unmodifiableList(specs), dto));
        return this;
    }

    private final java.util.Map<String, String> typePackages = new java.util.HashMap<String, String>();

    /** Where a type a command names lives, so a file that does not import it can write it in full. */
    public EntitySpec typePackage(String simpleName, String pkg) {
        if (simpleName != null && pkg != null && !pkg.isEmpty()) typePackages.put(simpleName, pkg);
        return this;
    }

    public String typePackageOf(String simpleName) {
        return typePackages.get(simpleName);
    }

    /** The business commands, in the order the DSL declares them. */
    public List<CommandSpec> commands() {
        return java.util.Collections.unmodifiableList(commands);
    }

    /**
     * Adds a field written as the DSL writes it - field("orderId", "string(36) @id").
     * For a rule set's own tests, which build a model without a project.
     */
    public EntitySpec field(String name, String dsl) {
        return addField(DslFieldLine.PARSER.parse(name, dsl));
    }

    /** The DSL loader's own field parser, reached from here. */
    private static final class DslFieldLine extends io.elasticore.base.model.loader.module.AbstractModelLoader {
        static final DslFieldLine PARSER = new DslFieldLine();

        Field parse(String name, String dsl) {
            return parseFieldLine(name, dsl, null);
        }
    }

    /** Adds a field inherited from the parent entity (@extend). */
    public EntitySpec addParentField(Field field) {
        this.fields.add(new FieldSpec(field, this, true));
        return this;
    }

    public EntitySpec parent(String parentEntityName) {
        this.parentName = parentEntityName;
        return this;
    }

    public EntitySpec domain(String lowerName, String pascalName) {
        this.domainName = lowerName;
        this.domainClassName = pascalName;
        return this;
    }

    public EntitySpec pkg(String artifact, String packageName) {
        this.packages.put(artifact, packageName);
        return this;
    }

    public String parentName() {
        return parentName;
    }

    public String domainName() {
        return domainName;
    }

    public String domainClassName() {
        return domainClassName;
    }

    public String pkg(String artifact) {
        return packages.get(artifact);
    }

    /** Entity name with the first letter lower-cased, e.g. Comment -> comment. */
    public String varName() {
        return name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /** The primary key field (first field with @id/@pk), or null. */
    public FieldSpec pkField() {
        for (FieldSpec f : fields) {
            if (f.hasDsl("id") || f.hasDsl("pk")) return f;
        }
        return null;
    }

    /** Number of primary key fields; more than one means an @IdClass composite key. */
    public int pkCount() {
        int n = 0;
        for (FieldSpec f : fields) {
            if (f.hasDsl("id") || f.hasDsl("pk")) n++;
        }
        return n;
    }

    /** Every primary key field, in declaration order - the order the generated <Entity>Identity class lists them in. */
    public List<FieldSpec> pkFields() {
        List<FieldSpec> out = new ArrayList<FieldSpec>();
        for (FieldSpec f : fields) {
            if (f.hasDsl("id") || f.hasDsl("pk")) out.add(f);
        }
        return out;
    }

    // ---- entity meta annotations (meta: entity @expose @readonly ...) ----

    private final java.util.Map<String, String> metaAnnotations =
            new java.util.LinkedHashMap<String, String>();

    /** Registers a meta annotation (name lowercase, value may be null). */
    public EntitySpec meta(String name, String value) {
        metaAnnotations.put(name.toLowerCase(), value);
        return this;
    }

    public boolean hasMeta(String name) {
        return metaAnnotations.containsKey(name.toLowerCase());
    }

    public String metaValue(String name) {
        return metaAnnotations.get(name.toLowerCase());
    }

    private final java.util.Map<String, String> metaKeyValues = new java.util.LinkedHashMap<String, String>();

    /** Records a meta annotation written as key=value pairs: @access(read=public, ship=admin). */
    public EntitySpec metaKeyValues(String name, String text) {
        if (name != null && text != null) metaKeyValues.put(name.toLowerCase(), text);
        return this;
    }

    /**
     * A meta annotation as written: its value, or its key=value pairs joined
     * as "read=public, ship=admin" - which metaValue answers null for, since
     * the loader keeps pairs apart from a value.
     */
    public String metaText(String name) {
        String value = metaValue(name);
        return value != null ? value : metaKeyValues.get(name.toLowerCase());
    }

    private final java.util.Map<String, java.util.List<String>> repeatedMeta =
            new java.util.LinkedHashMap<String, java.util.List<String>>();

    /**
     * Registers one occurrence of a meta annotation that may be written more
     * than once on the same entity.
     *
     * The single-valued map above answers with the first occurrence, which is
     * all any 2.x annotation ever needed: writing @expose twice says nothing
     * the first did not. An invariant is the opposite - an aggregate holds
     * several, and each is a separate statement - so the occurrences are kept
     * side by side as well rather than the last one silently winning.
     */
    public EntitySpec metaOccurrence(String name, String value) {
        String key = name.toLowerCase();
        java.util.List<String> values = repeatedMeta.get(key);
        if (values == null) {
            values = new java.util.ArrayList<String>();
            repeatedMeta.put(key, values);
        }
        values.add(value);
        return this;
    }

    /** Every occurrence of a meta annotation, in declaration order. */
    public java.util.List<String> metaValues(String name) {
        java.util.List<String> values = repeatedMeta.get(name.toLowerCase());
        if (values != null && !values.isEmpty()) {
            return java.util.Collections.unmodifiableList(values);
        }
        String single = metaValue(name);
        return single == null
                ? java.util.Collections.<String>emptyList()
                : java.util.Collections.singletonList(single);
    }

    /**
     * Annotation lines the DSL carries for the class itself: "jpa:" entries
     * and the env.yml annotation mappings the loader resolved. Rendered as
     * 2.x setNativeAnnotation renders them, in declaration order.
     */
    private final List<String> nativeClassAnnotations = new ArrayList<String>();

    public EntitySpec nativeClassAnnotation(String line) {
        nativeClassAnnotations.add(line);
        return this;
    }

    public List<String> nativeClassAnnotationLines() {
        return nativeClassAnnotations;
    }

    /**
     * Extra imports the DTO carries. Kept as given rather than deduplicated:
     * 2.x repeats an import that two field annotations both ask for, and the
     * committed files show it.
     */
    private final List<String> dtoImports = new ArrayList<String>();

    private boolean testCode;

    /**
     * Whether the domain asks for generated tests. JPACodePublisher reads
     * env.yml's testCode and defaults it to false, so a domain that says
     * nothing gets none.
     */
    public EntitySpec testCode(boolean enabled) {
        this.testCode = enabled;
        return this;
    }

    private boolean supportAI;

    /**
     * env.yml's supportAI. A domain that turns it on gets its extension
     * service written as Spring AI tools over the core one, so a model can
     * call what the DSL declared.
     */
    public EntitySpec supportAI(boolean enabled) {
        this.supportAI = enabled;
        return this;
    }

    public boolean supportAI() {
        return supportAI;
    }

    private String containedIn;

    /**
     * The aggregate this entity belongs to, when it is not one of its own.
     *
     * An entity another entity holds by a cascading relation lives and dies
     * with that one - the DSL already says so for JPA's sake, and it is the
     * same statement a domain model makes when it draws an aggregate.
     */
    public EntitySpec containedIn(String rootName) {
        this.containedIn = rootName;
        return this;
    }

    /**
     * The aggregate this entity belongs to, as the DSL says or as the model
     * implies.
     *
     * Two annotations, each saying one thing: @root is a root, and
     * @aggregate(Order) is part of Order. Either one wins over the boundary
     * read off cascade, which is a persistence decision that usually
     * coincides with the domain one and sometimes does not.
     *
     * A bare @aggregate is read as @root. It is the same statement and
     * someone will write it.
     */
    public String containedIn() {
        if (hasMeta("root")) return null;

        if (hasMeta("aggregate")) {
            String declared = metaValue("aggregate");
            return (declared == null || declared.trim().isEmpty()) ? null : declared.trim();
        }
        return containedIn;
    }

    /**
     * Whether this entity is reached directly or only through another.
     *
     * A root is what a repository loads and a transaction is drawn around; a
     * member is reached by navigating from one. An @embeddable has no identity
     * at all, so it is neither.
     */
    public boolean aggregateRoot() {
        return containedIn() == null && !hasMeta("embeddable");
    }

    /**
     * The domain events this root declares with @event, by name as the class
     * carries it: @event(Confirmed(totalAmount), Shipped) answers
     * [Confirmed, Shipped]. Empty for a member, an embeddable, or an entity
     * without an identity - only a root raises events.
     */
    public List<String> events() {
        String declared = metaValue("event");
        if (!hasMeta("event") || !aggregateRoot() || pkField() == null
                || declared == null || declared.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return eventNames(declared);
    }

    /**
     * The event names of an @event value, payloads dropped and capitalised:
     * "placed(total), Shipped" answers [Placed, Shipped]. Shared with the
     * context map, which reads other contexts' events off their entities.
     */
    public static List<String> eventNames(String declared) {
        List<String> out = new ArrayList<String>();
        if (declared == null) return out;
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char ch : (declared + ",").toCharArray()) {
            if (ch == '(') depth++;
            if (ch == ')') depth--;
            if (ch == ',' && depth == 0) {
                String name = cur.toString().trim();
                int paren = name.indexOf('(');
                if (paren >= 0) name = name.substring(0, paren).trim();
                if (!name.isEmpty()) out.add(Character.toUpperCase(name.charAt(0)) + name.substring(1));
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        return out;
    }

    /** The class generated for one of {@link #events()}: SalesOrderShippedEvent. */
    public String eventClassName(String event) {
        return String.format("%s%sEvent", getName(), event);
    }

    /** Where the event classes are generated: namespace event, else the entity's. */
    public String eventPackage() {
        String pkg = pkg("event");
        return (pkg == null || pkg.isEmpty()) ? pkg("entity") : pkg;
    }

    public boolean testCode() {
        return testCode;
    }

    private final java.util.Map<String, String> configValues = new java.util.HashMap<String, String>();
    private ConfigSource configSource;

    /** Where config(key) reads the domain's env.yml; set by the generator. */
    public EntitySpec configSource(ConfigSource source) {
        this.configSource = source;
        return this;
    }

    /** A setting for this spec alone - for a rule set's own tests. */
    public EntitySpec config(String key, String value) {
        if (value != null) configValues.put(key, value);
        return this;
    }

    /**
     * A setting from the domain's env.yml config section, by dotted key:
     * config("acme.topicPrefix") reads elasticore.config.acme.topicPrefix.
     * Null when it is not set.
     */
    public String config(String key) {
        String v = configValues.get(key);
        if (v == null && configSource != null) v = configSource.get(key);
        return v;
    }

    private boolean searchPageable;

    /**
     * Whether the entity's search DTO declares a page size. The service
     * returns a Page when it does and a List when it does not, so the test
     * has to read the same signal - @expose alone is not it.
     */
    public EntitySpec searchPageable(boolean pageable) {
        this.searchPageable = pageable;
        return this;
    }

    public boolean searchPageable() {
        return searchPageable;
    }

    private int mapperOrdinal = 1;

    /**
     * Which DTO of the entity this spec is, counting from one within the
     * domain. The reverse mapper methods of the second and later DTOs carry
     * the ordinal, because their list overloads erase to the same signature.
     */
    public EntitySpec mapperOrdinal(int ordinal) {
        this.mapperOrdinal = ordinal;
        return this;
    }

    public int mapperOrdinal() {
        return mapperOrdinal;
    }

    private String keyDtoClassName;

    /**
     * The key DTO is the same file as the DTO with only the key fields in it,
     * so it is rendered by the same rules with this name set. Null means the
     * spec is rendering the DTO itself.
     */
    public EntitySpec keyDtoClassName(String name) {
        this.keyDtoClassName = name;
        return this;
    }

    public String keyDtoClassName() {
        return keyDtoClassName;
    }

    private String dtoClassName;

    /**
     * The DTO's own declared name. A DTO of an entity is conventionally
     * <Entity>DTO, but one declared in its own right is whatever it says.
     */
    public EntitySpec dtoClassName(String name) {
        this.dtoClassName = name;
        return this;
    }

    public String dtoClassName() {
        return dtoClassName == null ? String.format("%sDTO", name) : dtoClassName;
    }

    private String searchDtoClassName;

    /** The search DTO's own declared name. */
    public EntitySpec searchDtoClassName(String name) {
        this.searchDtoClassName = name;
        return this;
    }

    public String searchDtoClassName() {
        return searchDtoClassName == null ? String.format("%sSrchDTO", name) : searchDtoClassName;
    }

    private final List<String> nativeEntityAnnotations = new ArrayList<String>();

    /** An annotation the DSL hands to the entity class as written. */
    public EntitySpec nativeEntityAnnotation(String line) {
        nativeEntityAnnotations.add(line);
        return this;
    }

    public List<String> nativeEntityAnnotationLines() {
        return nativeEntityAnnotations;
    }

    private String docLabel;
    private String docDescription;
    private String parentImport;

    /** The label text of the class javadoc, as MetaInfo carries it. */
    public EntitySpec docLabel(String label) { this.docLabel = label; return this; }
    public String docLabel() { return docLabel; }

    /** The description text of the class javadoc. */
    public EntitySpec docDescription(String description) { this.docDescription = description; return this; }
    public String docDescription() { return docDescription; }

    /** The FQCN a cross-domain parent is imported by, or null. */
    public EntitySpec parentImport(String fqcn) { this.parentImport = fqcn; return this; }
    public String parentImport() { return parentImport; }

    private Integer searchPageSize;

    /**
     * Page size of a search DTO: from @searchable(pageSize=N), or the default
     * 100 when it only says pageable. Null when the search does not page.
     */
    public EntitySpec searchPageSize(Integer size) {
        this.searchPageSize = size;
        return this;
    }

    public Integer searchPageSize() {
        return searchPageSize;
    }

    private String defaultSort;

    /**
     * The order a search answers in when the request names none: @sort on
     * the entity or the search DTO, as a sortCode ("orderDate-,name+").
     * Null when neither says one - the search is unsorted, as before.
     */
    public EntitySpec defaultSort(String sortCode) {
        this.defaultSort = sortCode;
        return this;
    }

    public String defaultSort() {
        return defaultSort;
    }

    private String pageableInterface;
    private String sortableInterface;

    /** env.yml config.interface.pageable, when the project owns one. */
    public EntitySpec pageableInterface(String name) {
        this.pageableInterface = name;
        return this;
    }

    public String pageableInterface() {
        return pageableInterface;
    }

    /** env.yml config.interface.sortable, when the project owns one. */
    public EntitySpec sortableInterface(String name) {
        this.sortableInterface = name;
        return this;
    }

    public String sortableInterface() {
        return sortableInterface;
    }

    public EntitySpec dtoImport(String packageName) {
        dtoImports.add(packageName);
        return this;
    }

    /** Adds the import unless the spec already carries it. */
    public EntitySpec dtoImportOnce(String packageName) {
        if (!dtoImports.contains(packageName)) dtoImports.add(packageName);
        return this;
    }

    public List<String> dtoImports() {
        return dtoImports;
    }

    // ---- env.yml config values relevant to emission ----

    private String apiPath;
    private boolean jsonView;
    private String tablePrefix = "";

    public EntitySpec tablePrefix(String prefix) {
        this.tablePrefix = prefix == null ? "" : prefix;
        return this;
    }

    /** env.yml config.table.prefix, e.g. "cms_"; empty when unset. */
    public String tablePrefix() {
        return tablePrefix;
    }

    public EntitySpec apiPath(String apiPath) {
        this.apiPath = apiPath;
        return this;
    }

    /** env.yml config.apiPath, default "/api" as in ControlSrcPublisher. */
    public String apiPath() {
        return apiPath == null ? "/api" : apiPath;
    }

    private boolean pageable;
    private String searchResultDtoName;

    /** True when the search DTO declares a page size, so search returns a Page. */
    public EntitySpec pageable(boolean pageable) {
        this.pageable = pageable;
        return this;
    }

    public boolean pageable() {
        return pageable;
    }

    /** Dedicated search-result DTO name, or null to fall back to the plain DTO. */
    public EntitySpec searchResultDtoName(String name) {
        this.searchResultDtoName = name;
        return this;
    }

    public String searchResultDtoName() {
        return searchResultDtoName;
    }

    public EntitySpec jsonView(boolean enabled) {
        this.jsonView = enabled;
        return this;
    }

    public boolean jsonView() {
        return jsonView;
    }

    private String jsonViewConfig = "false";

    /** env.yml config.jsonview: "false", "true" or "auto". */
    public EntitySpec jsonViewConfig(String value) {
        this.jsonViewConfig = value == null ? "false" : value;
        return this;
    }

    /**
     * Effective JsonView mode of the DTO, resolved as
     * {@code SrcFilePublisher.resolveJsonViewMode} does: the DTO's own
     * {@code @jsonview} meta wins over the env.yml config, and anything that
     * is neither "false" nor "auto" means manual mode.
     *
     * @return "false", "true" (manual) or "auto"
     */
    public String jsonViewMode() {
        if (hasMeta("jsonview")) {
            String v = metaValue("jsonview");
            // A bare @jsonview selects manual mode.
            if (v == null || v.isEmpty()) return "true";
            if ("false".equalsIgnoreCase(v)) return "false";
            if ("auto".equalsIgnoreCase(v)) return "auto";
            return "true";
        }
        if ("auto".equalsIgnoreCase(jsonViewConfig)) return "auto";
        if ("true".equalsIgnoreCase(jsonViewConfig)) return "true";
        return "false";
    }

    public String getName() {
        return name;
    }

    /** Entity name in constant form, e.g. Bookmark -> BOOKMARK. */
    public String constantName() {
        return name.toUpperCase();
    }

    public List<FieldSpec> fields() {
        return fields;
    }

    public boolean isKnownEntity(String typeName) {
        return knownEntities.contains(typeName);
    }

    // ---- DTO side of the mapper ----------------------------------------

    /**
     * Fields of the matching DTO. The mapper walks the fields of whichever
     * model it is writing INTO, so the forward direction follows this list and
     * the reverse direction follows the entity's own. They differ whenever the
     * DTO adds @ref fields or drops template fields with "--".
     */
    private final List<FieldSpec> dtoFields = new ArrayList<FieldSpec>();

    public EntitySpec addDtoField(Field field) {
        this.dtoFields.add(new FieldSpec(field, this));
        return this;
    }

    /** Adds a DTO field inherited through the template's parent entity. */
    public EntitySpec addParentDtoField(Field field) {
        this.dtoFields.add(new FieldSpec(field, this, true));
        return this;
    }

    /** DTO fields, falling back to the entity's own when none were declared. */
    public List<FieldSpec> dtoFields() {
        return dtoFields.isEmpty() ? fields : dtoFields;
    }

    /**
     * Every field the entity answers for, inherited ones included.
     *
     * The entity file declares only its own - the rest arrive through
     * extends - but anything describing the entity as a table, like the Q
     * class, has to name all of them.
     */
    private final List<FieldSpec> allFields = new ArrayList<FieldSpec>();

    public EntitySpec addAllField(Field field) {
        this.allFields.add(new FieldSpec(field, this));
        return this;
    }

    public List<FieldSpec> allFields() {
        return allFields.isEmpty() ? fields : allFields;
    }

    /** DTO names of the model, mapped to the entity of their @template ("" when none). */
    private final java.util.Map<String, String> knownDtos =
            new java.util.LinkedHashMap<String, String>();

    public EntitySpec knownDto(String dtoName, String templateEntityName) {
        knownDtos.put(dtoName, templateEntityName == null ? "" : templateEntityName);
        return this;
    }

    public boolean isKnownDto(String name) {
        return knownDtos.containsKey(name);
    }

    /** Entity behind a DTO's @template, or "" when the DTO declares none. */
    public String dtoTemplateEntity(String dtoName) {
        String v = knownDtos.get(dtoName);
        return v == null ? "" : v;
    }

    /**
     * Which aggregate each entity of the model belongs to, a root mapping to
     * itself.
     *
     * A rule looking at a field knows the type it points at and nothing else,
     * so without this it has to guess the boundary from whatever the relation
     * happens to declare - which is how three rules came to read @cascade
     * while a fourth read @aggregate, and a boundary the DSL stated plainly
     * was honoured in one place and ignored in the others.
     */
    private final java.util.Map<String, String> aggregateOfEntity =
            new java.util.LinkedHashMap<String, String>();

    public EntitySpec knownAggregate(String entityName, String rootName) {
        aggregateOfEntity.put(entityName, rootName == null ? entityName : rootName);
        return this;
    }

    /** The root of the aggregate this entity belongs to, or null if unknown. */
    public String aggregateOf(String entityName) {
        return aggregateOfEntity.get(entityName);
    }

    /** The root of this entity's own aggregate; its own name when it is one. */
    public String ownAggregate() {
        String root = aggregateOfEntity.get(getName());
        if (root != null) return root;
        String held = containedIn();
        return held == null ? getName() : held;
    }

    /** Whether anything is known about which aggregate a type belongs to. */
    public boolean knowsAggregateOf(String typeName) {
        return aggregateOfEntity.containsKey(typeName);
    }

    /**
     * Whether a type belongs to this entity's own aggregate instance.
     *
     * The single question every boundary rule asks, in one place, because
     * asking it three ways got three answers.
     *
     * Comparing which aggregate two types belong to is not enough: a root
     * pointing at its own type points at a DIFFERENT instance of it. A
     * category is not its own parent, and a category tree is not one
     * aggregate - read the other way it made every parent cascade deletes
     * into its children, which is not what the model said.
     */
    public boolean sameAggregate(String typeName) {
        String theirs = aggregateOf(typeName);
        if (theirs == null || !theirs.equals(ownAggregate())) return false;
        return !typeName.equals(getName());
    }

    /** Primary key field name per entity, needed to resolve @ref(rel.pk). */
    private final java.util.Map<String, String> entityPkFields =
            new java.util.HashMap<String, String>();

    public EntitySpec knownEntityPk(String entityName, String pkFieldName) {
        entityPkFields.put(entityName, pkFieldName);
        return this;
    }

    public String pkFieldOf(String entityName) {
        return entityPkFields.get(entityName);
    }

    /**
     * The identity field of every entity in the model, for rules that write
     * into another entity - an aggregate member written through its root.
     *
     * Kept apart from {@link #pkFieldOf}, which the base mapper consults and
     * which is populated only where 2.x populated it: filling that one in for
     * every entity would change what the compatibility bundle writes.
     */
    private final java.util.Map<String, String> entityKeys = new java.util.HashMap<String, String>();

    public EntitySpec knownEntityKey(String entityName, String keyFieldName) {
        entityKeys.put(entityName, keyFieldName);
        return this;
    }

    public String keyFieldOf(String entityName) {
        return entityKeys.get(entityName);
    }

    /** Roots of the model that declare an @invariant, so their members can honour it too. */
    private final java.util.Set<String> rootsWithInvariants = new java.util.HashSet<String>();

    public EntitySpec rootWithInvariants(String rootName) {
        rootsWithInvariants.add(rootName);
        return this;
    }

    public boolean rootHasInvariants(String rootName) {
        return rootsWithInvariants.contains(rootName);
    }

    /** Whether this DTO carries its entity's immutable value objects (decided per domain run). */
    private boolean valueObjectsInDto;

    public EntitySpec valueObjectsInDto(boolean on) {
        this.valueObjectsInDto = on;
        return this;
    }

    public boolean valueObjectsInDto() {
        return valueObjectsInDto;
    }

    /** Entities split into a regenerated base and a behaviour class (ddd-behavior). */
    private final java.util.Set<String> referencingById = new java.util.HashSet<String>();

    public EntitySpec aggregateReferencingById(String rootName) {
        referencingById.add(rootName);
        return this;
    }

    /** Whether the aggregate rooted here holds another aggregate by id, in any of its entities. */
    public boolean aggregateReferencesById(String rootName) {
        return referencingById.contains(rootName);
    }

    private final java.util.Set<String> splitForBehavior = new java.util.HashSet<String>();

    public EntitySpec entitySplitForBehavior(String entityName) {
        splitForBehavior.add(entityName);
        return this;
    }

    public boolean isSplitForBehavior(String entityName) {
        return splitForBehavior.contains(entityName);
    }

    /** A member's field that holds its root, per member entity. */
    private final java.util.Map<String, String> ownerFields = new java.util.HashMap<String, String>();

    public EntitySpec knownOwnerField(String memberName, String fieldName) {
        ownerFields.put(memberName, fieldName);
        return this;
    }

    /** The field of this member that points back at its root, or null when it has none. */
    public String ownerFieldOf(String memberName) {
        return ownerFields.get(memberName);
    }

    // ---- repository query methods --------------------------------------

    /** Declared query methods of the entity's repository, in order. */
    private final List<io.elasticore.base.model.repo.Method> queryMethods =
            new ArrayList<io.elasticore.base.model.repo.Method>();

    public EntitySpec addQueryMethod(io.elasticore.base.model.repo.Method method) {
        queryMethods.add(method);
        return this;
    }

    public List<io.elasticore.base.model.repo.Method> queryMethods() {
        return queryMethods;
    }

    /**
     * The methods repository.yml declares for this entity, as a rule reads
     * them. Empty for an entity repository.yml does not mention.
     */
    public List<QuerySpec> queries() {
        List<QuerySpec> out = new ArrayList<QuerySpec>();
        for (io.elasticore.base.model.repo.Method m : queryMethods) {
            if (!m.isEnable()) continue;
            // A conditional query (/* if: */) is assembled at runtime: the
            // repository declares no method for it, so none is listed.
            if (m.getQuery() != null && m.isNeedQueryAnnotation() && m.getQuery().indexOf("/* if:") > 0) continue;
            out.add(QuerySpec.of(m));
        }
        out.addAll(declaredQueries);
        return out;
    }

    private final List<QuerySpec> declaredQueries = new ArrayList<QuerySpec>();

    /** The queries declared through {@link #query} and {@link #nativeQuery} rather than loaded from YAML. */
    public List<QuerySpec> declaredQueries() {
        return declaredQueries;
    }

    /**
     * A repository.yml method, declared in a test the way the YAML declares
     * it. A query that starts with SELECT reads; one written with
     * nativeQuery goes through {@link #nativeQuery}.
     *
     *   new EntitySpec("SalesOrder").query("summarize", "List&lt;Object[]&gt;", "SELECT o FROM SalesOrder o")
     */
    public EntitySpec query(String name, String returnType, String sql) {
        return addQuery(name, returnType, sql, false);
    }

    /** A repository.yml method whose query is SQL for the database. */
    public EntitySpec nativeQuery(String name, String returnType, String sql) {
        return addQuery(name, returnType, sql, true);
    }

    private EntitySpec addQuery(String name, String returnType, String sql, boolean nativeQuery) {
        boolean select = sql == null || sql.trim().toLowerCase(java.util.Locale.ROOT).startsWith("select");
        declaredQueries.add(new QuerySpec(name, sql, nativeQuery, select, returnType,
                new java.util.LinkedHashMap<String, String>()));
        return this;
    }

    /** DTO names of the domain, used to decide a query's projection type. */
    public boolean hasDtoModels() {
        return !knownDtos.isEmpty();
    }

    /** Declared type of one of this entity's own fields, or null. */
    public String typeOfField(String fieldName) {
        for (FieldSpec f : fields) {
            if (f.name().equals(fieldName)) return f.typeName();
        }
        return null;
    }

    /** One @Index declaration of the entity meta. */
    public static final class IndexSpec {
        private final String name;
        private final String columnList;
        private final String unique;

        IndexSpec(String name, String columnList, String unique) {
            this.name = name;
            this.columnList = columnList;
            this.unique = unique;
        }

        public String name() { return name; }
        public String columnList() { return columnList; }
        public String unique() { return unique; }
    }

    private final java.util.List<IndexSpec> indexes = new ArrayList<IndexSpec>();
    private String schema;

    /** Registers an @Index(name=, columnList=, unique=) declaration, in order. */
    public EntitySpec index(String name, String columnList, String unique) {
        indexes.add(new IndexSpec(name, columnList, unique));
        return this;
    }

    public java.util.List<IndexSpec> indexes() {
        return indexes;
    }

    /** Table schema from the entity @schema meta or the env.yml tableSchema config. */
    public EntitySpec schema(String schema) {
        this.schema = schema;
        return this;
    }

    public String schema() {
        return schema;
    }

    /** Entity names marked @embeddable, and their field names in order. */
    private final java.util.Map<String, java.util.List<String>> embeddables =
            new java.util.LinkedHashMap<String, java.util.List<String>>();

    /** Registers an @embeddable type so @Embedded / @AttributeOverrides can resolve. */
    public EntitySpec knownEmbeddable(String typeName, java.util.List<String> fieldNames) {
        embeddables.put(typeName, new ArrayList<String>(fieldNames));
        return this;
    }

    public boolean isEmbeddableType(String typeName) {
        return embeddables.containsKey(typeName);
    }

    /** Field names of an @embeddable type, in declaration order. */
    public java.util.List<String> embeddableFieldNames(String typeName) {
        java.util.List<String> list = embeddables.get(typeName);
        return list == null ? java.util.Collections.<String>emptyList() : list;
    }

    /** DB column name of one of this entity's own fields, or null. */
    public String columnNameOfField(String fieldName) {
        for (FieldSpec f : fields) {
            if (f.name().equals(fieldName)) return f.dbColumnName();
        }
        return null;
    }

    /** Registers a domain enum: its @db column length ("" when none) and converter flag. */
    public EntitySpec knownEnum(String enumName, String dbLength, boolean hasDbAnnotation) {
        enumDbLength.put(enumName, dbLength == null ? "" : dbLength);
        if (hasDbAnnotation) enumWithDb.add(enumName);
        return this;
    }

    /** True when the domain declares any enum, which decides its import line. */
    public boolean hasEnums() {
        return !enumDbLength.isEmpty();
    }

    public boolean isKnownEnum(String typeName) {
        return enumDbLength.containsKey(typeName);
    }

    /**
     * Constant names of the domain enums, which a lifecycle's states must be.
     * The first registration of a name wins: the entity's own domain is
     * registered first, so its enum is the one a same-named enum of another
     * domain cannot replace.
     */
    private final java.util.Map<String, List<String>> enumConstantNames =
            new java.util.HashMap<String, List<String>>();

    public EntitySpec enumConstantNames(String enumName, List<String> names) {
        if (!enumConstantNames.containsKey(enumName)) enumConstantNames.put(enumName, names);
        return this;
    }

    /** The constant names of a domain enum, or null when they are not known. */
    public List<String> enumConstantNamesOf(String enumName) {
        return enumConstantNames.get(enumName);
    }

    /** Full definitions of the domain's enums, for the ones that document them. */
    private final java.util.Map<String, EnumSpec> enumSpecs =
            new java.util.LinkedHashMap<String, EnumSpec>();

    /**
     * Registers an enum together with its constants, which a field of that
     * type documents as its example.
     */
    public EntitySpec knownEnum(EnumSpec spec, String dbLength, boolean hasDbAnnotation) {
        knownEnum(spec.getName(), dbLength, hasDbAnnotation);
        enumSpecs.put(spec.getName(), spec);
        return this;
    }

    /**
     * An enum of another domain, known only for what it documents: its
     * constants become the example of a field typed with it. Registered
     * apart from knownEnum, which decides mapping and imports and stays
     * with the spec's own domain. The first registration of a name wins.
     */
    public EntitySpec documentEnum(EnumSpec spec) {
        if (!enumSpecs.containsKey(spec.getName())) enumSpecs.put(spec.getName(), spec);
        return this;
    }

    public EnumSpec enumSpec(String typeName) {
        return enumSpecs.get(typeName);
    }

    /** Length of the enum's db column, or "" when the enum defines none. */
    public String enumDbLength(String typeName) {
        String v = enumDbLength.get(typeName);
        return v == null ? "" : v;
    }

    public boolean enumHasDb(String typeName) {
        return enumWithDb.contains(typeName);
    }

    void attach(SourceCode code) {
        this.code = code;
    }

    SourceCode code() {
        return code;
    }
}
