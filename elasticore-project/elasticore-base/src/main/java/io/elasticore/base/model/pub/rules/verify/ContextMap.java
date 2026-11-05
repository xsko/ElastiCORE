package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.entity.TypeInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The project's bounded contexts and what crosses between them.
 *
 * Every blueprint domain is a context. One that declares itself in env.yml
 * says which contexts it depends on and what it lets others use:
 *
 * <pre>
 *   config:
 *     context:
 *       uses: catalog                  # the upstream contexts this one depends on
 *       publishes: [ProductDTO]        # the types other contexts may use from this one
 * </pre>
 *
 * Without a declaration nothing changes: a name resolves in any domain, as it
 * always has. With one, every type a field (or a DTO's @template) takes from
 * another context is checked:
 * <ul>
 *   <li>the context it comes from must be in the declaring context's uses;</li>
 *   <li>a context that declares publishes lets out only what it lists
 *       ({@code publishes: []}: nothing); one that does not declare it lets
 *       its types be used, as long as the user says it uses that context;</li>
 *   <li>an entity of another context is never a field type - a JPA relation
 *       across the boundary ties two models into one; hold its id, or use a
 *       DTO it publishes.</li>
 * </ul>
 * A process manager that reacts to another context's event
 * ({@code on: [shop:OrderPlaced]}) crosses the same way: the event is
 * checked like a type, and publishes may list events (RootEvent) as well as
 * DTOs, enums and embeddables.
 * The problems are the build's (ModelCheck.fail); the map itself is drawn as
 * context-map.md beside schema.yml.
 */
public final class ContextMap {

    /** One context: a blueprint domain and what it declared, if anything. */
    public static final class Context {
        private final String name;
        private boolean declared;
        private boolean publishesDeclared;
        private final Set<String> uses = new LinkedHashSet<String>();
        private final Set<String> publishes = new LinkedHashSet<String>();
        /** RootEvent (OrderPlaced) -> the class generated for it, for every @event of its roots. */
        private final Map<String, String> events = new LinkedHashMap<String, String>();
        /** RootEvent an entity declares that raises nothing -> why (a member, no key). */
        private final Map<String, String> unraised = new LinkedHashMap<String, String>();

        Context(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public boolean declared() {
            return declared;
        }

        public Set<String> uses() {
            return uses;
        }

        public Set<String> publishes() {
            return publishes;
        }

        /** The domain events its roots declare, by the name a process manager reacts to. */
        public Map<String, String> events() {
            return events;
        }
    }

    /** A type one context takes from another, and where. */
    public static final class Reference {
        private final String from;
        private final String to;
        private final String type;
        private final String where;
        private final boolean entity;

        Reference(String from, String to, String type, String where, boolean entity) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.where = where;
            this.entity = entity;
        }

        public String from() {
            return from;
        }

        public String to() {
            return to;
        }

        public String type() {
            return type;
        }

        public String where() {
            return where;
        }
    }

    private enum Kind { ENTITY, DTO, ENUM }

    private final Map<String, Context> contexts = new LinkedHashMap<String, Context>();
    private final List<Reference> references = new ArrayList<Reference>();
    private final List<String> problems = new ArrayList<String>();
    private final List<String> warnings = new ArrayList<String>();
    private final Map<String, String> domainNames = new LinkedHashMap<String, String>();
    private final Map<String, List<ECoreModel>> models = new LinkedHashMap<String, List<ECoreModel>>();

    private ContextMap() {
    }

    /** The map of the loaded blueprint: its contexts, what crosses, and what must not. */
    public static ContextMap of(BlueprintLoader loader) {
        Map<String, List<ECoreModel>> byContext = new LinkedHashMap<String, List<ECoreModel>>();
        for (String key : loader.contexts().keySet()) {
            byContext.put(loader.domainDirNameOf(key), loader.modelsOf(key));
        }
        return of(byContext);
    }

    static ContextMap of(Map<String, List<ECoreModel>> byContext) {
        ContextMap map = new ContextMap();
        map.models.putAll(byContext);

        // Who owns each name. A name two contexts both define is left out: the
        // loader already warns of it, and either answer would be a guess.
        Map<String, String> owner = new LinkedHashMap<String, String>();
        // catalog:Product names the domain by its domainName, which need not be its directory.
        Map<String, String> byDomainName = new LinkedHashMap<String, String>();
        Map<String, Kind> kind = new LinkedHashMap<String, Kind>();
        Set<String> ambiguous = new LinkedHashSet<String>();
        for (Map.Entry<String, List<ECoreModel>> en : byContext.entrySet()) {
            Context c = new Context(en.getKey());
            map.contexts.put(en.getKey(), c);
            for (ECoreModel model : en.getValue()) {
                if (model.getConfig("context") != null) c.declared = true;
                if (model.getConfig("context.publishes") != null) c.publishesDeclared = true;
                c.uses.addAll(list(model.getConfig("context.uses")));
                c.publishes.addAll(list(model.getConfig("context.publishes")));
                String domainName = model.getConfigDomainName();
                if (domainName != null) byDomainName.put(domainName, en.getKey());
                for (String name : names(model)) {
                    String before = owner.put(name, en.getKey());
                    if (before != null && !before.equals(en.getKey())) ambiguous.add(name);
                    kind.put(name, kindOf(model, name));
                }
                c.events.putAll(events(model, c.unraised));
            }
        }
        for (String name : ambiguous) owner.remove(name);
        map.domainNames.putAll(byDomainName);

        // What a context publishes must be its own, and not an entity: an
        // entity crosses as its id or a DTO, never as a relation.
        for (Context c : map.contexts.values()) {
            for (String published : c.publishes) {
                // An event it raises is published language as much as a DTO is.
                if (c.events.containsKey(eventKey(c, published))) continue;
                if (!c.name.equals(owner.get(published)) && !ambiguous.contains(published)) {
                    map.warnings.add(String.format("[%s] context publishes %s, which is no model or event of %s.",
                            c.name, published, c.name));
                } else if (kind.get(published) == Kind.ENTITY) {
                    map.warnings.add(String.format("[%s] context publishes the entity %s: other contexts may not hold it as a field;"
                            + " publish a DTO of it instead.", c.name, published));
                }
            }
        }

        for (Map.Entry<String, List<ECoreModel>> en : byContext.entrySet()) {
            String from = en.getKey();
            for (ECoreModel model : en.getValue()) {
                Set<String> own = new LinkedHashSet<String>(names(model));
                ModelComponentItems<Entity> entities = model.getEntityModels().getItems();
                for (int i = 0; i < entities.size(); i++) {
                    Entity e = entities.get(i);
                    map.fields(from, e.getIdentity().getName(), e.getItems(), own, owner, kind);
                    // @extend(AuditEntity): a class of another context as this one's parent.
                    String parent = e.getMetaInfo().getMetaAnnotationValue("extend");
                    for (String p : list(parent)) {
                        map.reference(from, e.getIdentity().getName() + " @extend", p, own, owner, kind, false);
                    }
                }
                ModelComponentItems<DataTransfer> dtos = model.getDataTransferModels().getItems();
                for (int i = 0; i < dtos.size(); i++) {
                    DataTransfer d = dtos.get(i);
                    String name = d.getIdentity().getName();
                    map.fields(from, name, d.getItems(), own, owner, kind);
                    // @template(Product): the DTO is built from another context's model.
                    String template = io.elasticore.base.model.dto.TemplateList.namesOf(d.getMetaInfo());
                    for (String t : list(template)) {
                        map.reference(from, name + " @template", t, own, owner, kind, false);
                    }
                }
                // processManager on: [shop:OrderPlaced] - another context's event.
                for (io.elasticore.base.model.domainservice.DomainService pm : processManagers(model)) {
                    for (String event : pm.getOn()) {
                        if (event.indexOf(':') > 0) {
                            map.eventReference(from, "processManager " + pm.getIdentity().getName(), event);
                        }
                    }
                }
            }
        }
        map.check();
        return map;
    }

    /**
     * The class another context generates for one of its events, named the
     * way a process manager names it: "shop:OrderPlaced" (the context by its
     * directory or its domainName). Null when there is no such context or
     * event.
     */
    public String eventClass(String qualifiedName) {
        int colon = qualifiedName.indexOf(':');
        if (colon <= 0) return null;
        Context c = contexts.get(contextNamed(qualifiedName.substring(0, colon)));
        return c == null ? null : c.events.get(eventKey(c, qualifiedName.substring(colon + 1).trim()));
    }

    /** OrderPlaced, also when written as the class (OrderPlacedEvent), as a local name may be. */
    private static String eventKey(Context c, String name) {
        if (!c.events.containsKey(name) && name.endsWith("Event")) {
            String bare = name.substring(0, name.length() - "Event".length());
            if (c.events.containsKey(bare)) return bare;
        }
        return name;
    }

    /**
     * shop:OrderPlaced named by a process manager of another context. An
     * unknown context or event is not recorded: the rule that writes the
     * handler says what is wrong with the name, and a boundary message
     * ("shop does not publish OrderShiped") would send the user the wrong way.
     */
    private void eventReference(String from, String where, String qualifiedName) {
        int colon = qualifiedName.indexOf(':');
        String to = contextNamed(qualifiedName.substring(0, colon));
        if (to == null || to.equals(from)) return;
        Context c = contexts.get(to);
        String key = eventKey(c, qualifiedName.substring(colon + 1).trim());
        if (!c.events.containsKey(key)) return;
        references.add(new Reference(from, to, key, where, false));
    }

    /**
     * The one other context that declares an event written without a context,
     * so the warning can say how to name it. Null when the context itself
     * declares it, or none or several others do.
     */
    public String contextDeclaringEvent(String context, String eventName) {
        Context own = contexts.get(context);
        if (own != null && own.events.containsKey(eventName)) return null;
        String found = null;
        for (Context c : contexts.values()) {
            if (!c.events.containsKey(eventName)) continue;
            if (found != null) return null;
            found = c.name;
        }
        return found;
    }

    /**
     * The events of one model: RootEvent -> package.RootEventEvent, as ddd-event
     * names the class. Only what EntitySpec.events() raises: a root (not a
     * member of another aggregate, not an embeddable) with a key of its own.
     */
    private static Map<String, String> events(ECoreModel model, Map<String, String> unraised) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        String pkg = model.getNamespace("event");
        if (pkg == null || pkg.isEmpty()) pkg = model.getNamespace("entity");
        ModelComponentItems<Entity> entities = model.getEntityModels().getItems();
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (!e.getMetaInfo().hasMetaAnnotation("event") || e.getMetaInfo().hasMetaAnnotation("embeddable")) continue;
            String root = e.getIdentity().getName();
            String holder = SpecFactory.aggregateOfModel(root, model);
            String why = holder != null ? root + " is a member of " + holder + "; only a root raises events"
                    : !hasKey(e) ? root + " has no @id; only a root with a key raises events" : null;
            for (String event : io.elasticore.base.model.pub.rules.EntitySpec.eventNames(
                    e.getMetaInfo().getMetaAnnotationValue("event"))) {
                if (why != null) unraised.put(root + event, why);
                else out.put(root + event, pkg + "." + root + event + "Event");
            }
        }
        return out;
    }

    /** Why another context's event, declared there, raises nothing - or null when that is not the reason. */
    public String whyNoEvent(String qualifiedName) {
        int colon = qualifiedName.indexOf(':');
        if (colon <= 0) return null;
        Context c = contexts.get(contextNamed(qualifiedName.substring(0, colon)));
        if (c == null) return null;
        String name = qualifiedName.substring(colon + 1).trim();
        String why = c.unraised.get(name);
        if (why == null && name.endsWith("Event")) why = c.unraised.get(name.substring(0, name.length() - 5));
        return why;
    }

    /** Whether an entity declares its own key (@id / @pk), as EntitySpec.pkField() asks. */
    private static boolean hasKey(Entity e) {
        ModelComponentItems<Field> fields = e.getItems();
        if (fields == null) return false;
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            if (f.hasAnnotation("id") || f.hasAnnotation("pk")) return true;
        }
        return false;
    }

    /** The context a DSL name stands for - its directory or its domainName - or null. */
    public String contextNamed(String name) {
        if (name == null) return null;
        String n = name.trim();
        if (contexts.containsKey(n)) return n;
        return domainNames.get(n);
    }

    /** The models one context loaded, for a question only its own settings answer. */
    public List<ECoreModel> modelsOf(String context) {
        List<ECoreModel> m = models.get(context);
        return m == null ? java.util.Collections.<ECoreModel>emptyList() : m;
    }

    /** The process managers a model declares - read off the list, not the shared cursor. */
    private static List<io.elasticore.base.model.domainservice.DomainService> processManagers(ECoreModel model) {
        List<io.elasticore.base.model.domainservice.DomainService> out =
                new ArrayList<io.elasticore.base.model.domainservice.DomainService>();
        if (model.getDomainServiceModels() == null) return out;
        for (io.elasticore.base.model.domainservice.DomainService s
                : model.getDomainServiceModels().getOrgItems().getItemList()) {
            if (s.isProcessManager()) out.add(s);
        }
        return out;
    }

    private void fields(String from, String holder, ModelComponentItems<Field> fields,
                        Set<String> own, Map<String, String> owner, Map<String, Kind> kind) {
        if (fields == null) return;
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            TypeInfo t = f.getTypeInfo();
            if (t == null) continue;
            String type = t.isGenericType() && t.getTypeParameterName() != null
                    ? t.getTypeParameterName() : t.getBaseTypeName();
            reference(from, holder + "." + f.getName(), type, own, owner, kind, true);
        }
    }

    private void reference(String from, String where, String typeName, Set<String> own,
                           Map<String, String> owner, Map<String, Kind> kind, boolean field) {
        if (typeName == null) return;
        String type = typeName.trim();
        String to = null;
        // catalog:Product - the DSL's way of naming another domain's model.
        int colon = type.indexOf(':');
        if (colon > 0) {
            to = type.substring(0, colon).trim();
            type = type.substring(colon + 1).trim();
            if (!contexts.containsKey(to) && domainNames.containsKey(to)) to = domainNames.get(to);
        }
        if (to == null) {
            if (own.contains(type)) return;
            to = owner.get(type);
        }
        if (to == null || to.equals(from) || !contexts.containsKey(to)) return;
        references.add(new Reference(from, to, type, where, field && kind.get(type) == Kind.ENTITY));
    }

    private void check() {
        for (Context c : contexts.values()) {
            for (String used : c.uses) {
                if (!contexts.containsKey(used)) {
                    warnings.add(String.format("[%s] context uses %s, which is no domain of this project.", c.name, used));
                }
            }
        }
        for (Reference r : references) {
            Context from = contexts.get(r.from);
            Context to = contexts.get(r.to);
            if (!from.declared && !to.declared) continue;
            if (r.entity) {
                problems.add(String.format(
                        "[%s] %s is %s's entity %s: another context's entity is no field type - hold its id"
                                + " (%sId: long), or use a DTO %s publishes.",
                        r.from, r.where, r.to, r.type, lowerFirst(r.type), r.to));
                continue;
            }
            if (from.declared && !from.uses.contains(r.to)) {
                problems.add(String.format("[%s] %s uses %s from %s, but %s does not declare it (config.context.uses: %s).",
                        r.from, r.where, r.type, r.to, r.from, r.to));
            }
            if (to.declared && to.publishesDeclared && !to.publishes.contains(r.type)
                    && !(to.events.containsKey(r.type) && to.publishes.contains(r.type + "Event"))) {
                problems.add(String.format("[%s] %s uses %s from %s, which %s does not publish (config.context.publishes).",
                        r.from, r.where, r.type, r.to, r.to));
            }
        }
    }

    /** Whether any domain declared itself a context: only then is the map drawn. */
    public boolean declared() {
        for (Context c : contexts.values()) {
            if (c.declared) return true;
        }
        return false;
    }

    public List<String> problems() {
        return problems;
    }

    public List<String> warnings() {
        return warnings;
    }

    public List<Reference> references() {
        return references;
    }

    public Map<String, Context> contexts() {
        return contexts;
    }

    /**
     * context-map.md: the contexts, what each publishes, and an arrow from each
     * upstream context to the ones that use it, labelled with what crosses.
     * Mermaid, so GitHub and most editors draw it.
     */
    public String markdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- Generated by ElastiCORE from config.context in each env.yml - do not edit. -->\n");
        sb.append("# Context map\n\n");
        sb.append("```mermaid\nflowchart LR\n");
        for (Context c : contexts.values()) {
            String label = c.name + (c.publishes.isEmpty() ? "" : "<br/><small>publishes: " + join(c.publishes) + "</small>");
            sb.append("    ").append(id(c.name)).append("[\"").append(label).append("\"]\n");
        }
        Map<String, Set<String>> edges = new LinkedHashMap<String, Set<String>>();
        for (Context c : contexts.values()) {
            for (String used : c.uses) {
                if (contexts.containsKey(used)) edge(edges, used, c.name);
            }
        }
        for (Reference r : references) edge(edges, r.to, r.from).add(r.type);
        for (Map.Entry<String, Set<String>> en : edges.entrySet()) {
            String[] ends = en.getKey().split("\u0000");
            sb.append("    ").append(id(ends[0]));
            sb.append(en.getValue().isEmpty() ? " --> " : " -->|" + join(en.getValue()) + "| ");
            sb.append(id(ends[1])).append('\n');
        }
        sb.append("```\n\n");
        sb.append("An arrow points from the context that is used (upstream) to the one that uses it.\n\n");
        sb.append("| Context | Uses | Publishes |\n|---|---|---|\n");
        for (Context c : contexts.values()) {
            sb.append("| ").append(c.name).append(c.declared ? "" : " (not declared)")
              .append(" | ").append(join(c.uses)).append(" | ").append(join(c.publishes)).append(" |\n");
        }
        if (!problems.isEmpty()) {
            sb.append("\n## Problems\n\n");
            for (String p : problems) sb.append("- ").append(p).append('\n');
        }
        return sb.toString();
    }

    private static Set<String> edge(Map<String, Set<String>> edges, String upstream, String downstream) {
        String key = upstream + "\u0000" + downstream;
        Set<String> labels = edges.get(key);
        if (labels == null) {
            labels = new LinkedHashSet<String>();
            edges.put(key, labels);
        }
        return labels;
    }

    private static String id(String name) {
        return "ctx_" + name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private static String join(Set<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) sb.append(sb.length() > 0 ? ", " : "").append(s);
        return sb.toString();
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** "a, b" or "[a, b]" - a YAML list arrives as the latter. */
    static List<String> list(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        String t = text.trim();
        if (t.startsWith("[") && t.endsWith("]")) t = t.substring(1, t.length() - 1);
        for (String s : t.split(",")) {
            if (!s.trim().isEmpty()) out.add(s.trim());
        }
        return out;
    }

    private static List<String> names(ECoreModel model) {
        List<String> out = new ArrayList<String>();
        ModelComponentItems<Entity> entities = model.getEntityModels().getItems();
        for (int i = 0; i < entities.size(); i++) out.add(entities.get(i).getIdentity().getName());
        ModelComponentItems<DataTransfer> dtos = model.getDataTransferModels().getItems();
        for (int i = 0; i < dtos.size(); i++) out.add(dtos.get(i).getIdentity().getName());
        ModelComponentItems<io.elasticore.base.model.enums.EnumModel> enums = model.getEnumModels().getItems();
        for (int i = 0; i < enums.size(); i++) out.add(enums.get(i).getIdentity().getName());
        return out;
    }

    private static Kind kindOf(ECoreModel model, String name) {
        Entity entity = model.getEntityModels().findByName(name);
        // An embeddable is a value, copied into the row that holds it: it may
        // cross like a DTO (when published), not like a relation.
        if (entity != null) return entity.getMetaInfo().hasMetaAnnotation("embeddable") ? Kind.DTO : Kind.ENTITY;
        if (model.getDataTransferModels().findByName(name) != null) return Kind.DTO;
        return Kind.ENUM;
    }
}
