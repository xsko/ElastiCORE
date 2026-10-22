package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.EntityAnnotation;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.pub.rules.EntitySpec;

/**
 * Builds rule-engine specs from the REAL loaded model (ECoreModel), so the
 * new pipeline consumes exactly what the 2.x pipeline consumes.
 */
public final class SpecFactory {

    private SpecFactory() {
    }

    /**
     * Wraps a loaded entity for the rule engine.
     *
     * @param entity        the model entity
     * @param model         the domain model (namespaces, sibling entities)
     * @param domainDirName blueprint directory name, e.g. "content"
     */
    /**
     * The entity that holds this one by a cascading relation, or null.
     *
     * Cascade is the DSL saying the other entity's rows are written and
     * removed with this one's - which is the same boundary an aggregate
     * draws. Nothing new has to be declared for a domain model to see it.
     */
    private static String aggregateHolderOf(String name, ECoreModel model) {
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        while (all.hasNext()) {
            Entity holder = all.next();
            String holderName = holder.getIdentity().getName();
            if (holderName.equals(name)) continue;

            ModelComponentItems<io.elasticore.base.model.entity.Field> fields = holder.getItems();
            if (fields == null) continue;
            for (int i = 0; i < fields.size(); i++) {
                io.elasticore.base.model.entity.Field f = fields.get(i);
                if (f.getAnnotation("cascade") == null) continue;
                if (name.equals(f.getTypeInfo().getCoreItemType())) return holderName;
            }
        }
        return ownerByCollection(name, model);
    }

    /**
     * The one declared root that holds this entity in a to-many it maps
     * (@OneToMany), when the entity is not a root itself.
     *
     * A root that says "lines: List<SalesOrderLine> @OneToMany(mappedBy=order)"
     * has already said the lines are its own; an entity no root claims, or
     * that two roots claim, stays undecided and needs @aggregate(X).
     */
    private static String ownerByCollection(String name, ECoreModel model) {
        Entity self = model.getEntityModels().findByName(name);
        if (self == null || self.getMetaInfo().hasMetaAnnotation("root")
                || self.getMetaInfo().hasMetaAnnotation("embeddable")
                || self.getMetaInfo().hasMetaAnnotation("abstract")) return null;
        String owner = null;
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        for (int n = 0; n < all.size(); n++) {
            Entity holder = all.get(n);
            if (!holder.getMetaInfo().hasMetaAnnotation("root")) continue;
            String holderName = holder.getIdentity().getName();
            if (holderName.equals(name) || holder.getItems() == null) continue;
            ModelComponentItems<io.elasticore.base.model.entity.Field> fields = holder.getItems();
            for (int i = 0; i < fields.size(); i++) {
                io.elasticore.base.model.entity.Field f = fields.get(i);
                if (f.getTypeInfo() == null || !f.getTypeInfo().isList()) continue;
                if (!f.hasAnnotation("onetomany")) continue;
                if (!name.equals(f.getTypeInfo().getCoreItemType())) continue;
                if (owner != null && !owner.equals(holderName)) return null;
                owner = holderName;
            }
        }
        return owner;
    }

    /**
     * Which aggregate an entity belongs to, from the model rather than from a
     * persistence setting.
     *
     * The DSL states the boundary directly - "@root" for one, "@aggregate(X)"
     * for a member - and that statement wins. Cascade is consulted only when
     * nothing was declared, which keeps a model written before the
     * annotations existed reading the same way it always did.
     *
     * The order matters and is the point. Cascade is the ORM being told what
     * to write with what; an aggregate is the domain saying what belongs to
     * what. Letting the first decide the second inverts the causality - and
     * it did, until every rule that draws the boundary was made to come
     * through here.
     */
    /** The aggregate an entity belongs to, or null for a root - for the model rewrites of this package. */
    static String aggregateOfModel(String name, ECoreModel model) {
        return aggregateOf(name, model);
    }

    private static String aggregateOf(String name, ECoreModel model) {
        Entity entity = model.getEntityModels().findByName(name);
        if (entity != null) {
            if (entity.getMetaInfo().hasMetaAnnotation("root")) return null;
            if (entity.getMetaInfo().hasMetaAnnotation("aggregate")) {
                String declared = entity.getMetaInfo().getMetaAnnotationValue("aggregate");
                return (declared == null || declared.trim().isEmpty()) ? null : declared.trim();
            }
        }
        return aggregateHolderOf(name, model);
    }

    /**
     * Tells a spec which aggregate every entity of the model belongs to, so a
     * rule looking at a field can ask about the type it points at instead of
     * inferring it from the relation.
     */
    /**
     * Whether an entity is split into a regenerated base and a behaviour class
     * (ddd-behavior). Known to every spec, because the DTO and mapper specs
     * carry the DTO's meta annotations and cannot read the entity's.
     */
    private static boolean splitsForBehavior(Entity entity, ECoreModel model) {
        if (!entity.getMetaInfo().hasMetaAnnotation("behavior")) {
            // A root is split by default: its state is regenerated and its
            // behaviour has a class to live in, so the model does not start
            // out anemic. @editable says the root is plain data on purpose -
            // master data edited field by field - and keeps it whole.
            if (!rootsSplitByDefault) return false;
            if (entity.getMetaInfo().hasMetaAnnotation("editable")) return false;
            if (entity.getMetaInfo().hasMetaAnnotation("root")) {
                // declared, nothing more to ask
            } else if (aggregateOf(entity.getIdentity().getName(), model) != null) {
                return false;
            }
        }
        if (entity.getMetaInfo().hasMetaAnnotation("abstract")
                || entity.getMetaInfo().hasMetaAnnotation("embeddable")
                || entity.getMetaInfo().hasMetaAnnotation("rollup")) return false;
        io.elasticore.base.model.core.ListMap<String, Field> fields = entity.getAllFieldListMap();
        if (fields == null) return false;
        int keys = 0;
        for (Field f : fields.getList()) {
            if (f.hasAnnotation("discriminator")) return false;
            if (f.hasAnnotation("id") || f.hasAnnotation("pk")) keys++;
        }
        return keys == 1;
    }

    /**
     * The query methods the repository DSL declares for this entity.
     *
     * The repository rules have rendered them since the port from 2.x, but
     * nothing handed them over: every v3 repository, Java and Kotlin, came
     * out without the queries its DSL declared, and said nothing.
     */
    private static void declaredQueries(EntitySpec spec, String entityName, ECoreModel model) {
        if (model.getRepositoryModels() == null) return;
        ModelComponentItems<io.elasticore.base.model.repo.Repository> repos = model.getRepositoryModels().getItems();
        if (repos == null) return;
        for (int i = 0; i < repos.size(); i++) {
            io.elasticore.base.model.repo.Repository repo = repos.get(i);
            if (!entityName.equals(repo.getIdentity().getName())) continue;
            ModelComponentItems<io.elasticore.base.model.repo.Method> methods = repo.getItems();
            if (methods == null) continue;
            for (int j = 0; j < methods.size(); j++) spec.addQueryMethod(methods.get(j));
        }
    }

    /** Whether a field of the entity declares @min / @max - a promise the DDD rules check on write. */
    private static boolean declaresFieldBounds(Entity entity) {
        io.elasticore.base.model.core.ListMap<String, Field> fields = entity.getAllFieldListMap();
        if (fields == null) return false;
        for (Field f : fields.getList()) {
            if (f.getTypeInfo() != null && f.getTypeInfo().isList()) {
                if (f.hasAnnotation("minsize") || f.hasAnnotation("maxsize")) return true;
                continue;
            }
            for (String k : new String[]{"min", "minval", "minvalue", "max", "maxval", "maxvalue"}) {
                if (f.hasAnnotation(k)) return true;
            }
        }
        return false;
    }

    private static void teachAggregates(EntitySpec spec, ECoreModel model) {
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        while (all.hasNext()) {
            Entity entity = all.next();
            // A value object is not an aggregate and does not belong to one:
            // it has no identity, so there is no boundary for it to sit
            // inside or to cross. Left in, it answers "itself" and every
            // reference to one reads as a reference to another aggregate.
            if (entity.getMetaInfo().hasMetaAnnotation("embeddable")) continue;
            spec.knownAggregate(entity.getIdentity().getName(),
                    aggregateOf(entity.getIdentity().getName(), model));

            // Read through the field map, not getItems(): that iterator is
            // shared, and walking it here would leave it spent for the spec
            // that is built from the same entity next.
            String entityName = entity.getIdentity().getName();
            // A root: it is its own aggregate, which aggregateOf answers with
            // null or its own name.
            String holder = aggregateOf(entityName, model);
            if ((entity.getMetaInfo().hasMetaAnnotation("invariant") || declaresFieldBounds(entity))
                    && !entity.getMetaInfo().hasMetaAnnotation("abstract")
                    && (holder == null || holder.equals(entityName))) {
                spec.rootWithInvariants(entityName);
            }
            String owner = ownersOf(model).get(entityName);
            if (owner != null) spec.knownOwnerField(entityName, owner);
            if (splitsForBehavior(entity, model)) spec.entitySplitForBehavior(entityName);
            // An aggregate one of whose entities holds another by id: the
            // root's service reads the saved row back (DddReferenceRules).
            io.elasticore.base.model.core.ListMap<String, Field> byIdFields = entity.getAllFieldListMap();
            if (byIdFields != null) {
                for (Field f : byIdFields.getList()) {
                    if (f.hasAnnotation("idref")) {
                        spec.aggregateReferencingById(holder == null ? entityName : holder);
                        break;
                    }
                }
            }

            io.elasticore.base.model.core.ListMap<String, Field> fields = entity.getAllFieldListMap();
            if (fields != null) {
                for (Field f : fields.getList()) {
                    if (f.hasAnnotation("id") || f.hasAnnotation("pk")) {
                        spec.knownEntityKey(entity.getIdentity().getName(), f.getName());
                        break;
                    }
                }
            }
        }
    }

    /**
     * The field by which a member points back at its root.
     *
     * Read from the root's side first - the mappedBy its relation names -
     * because a member can hold more than one reference of the root's type,
     * and only the one the root maps is its owner. Inherited fields count.
     */
    /** Member name -> its owner field, computed once per model (specs are built per artifact). */
    private static final java.util.Map<ECoreModel, java.util.Map<String, String>> OWNERS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<ECoreModel, java.util.Map<String, String>>());

    private static java.util.Map<String, String> ownersOf(ECoreModel model) {
        java.util.Map<String, String> known = OWNERS.get(model);
        if (known != null) return known;
        known = new java.util.HashMap<String, String>();
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        java.util.Map<String, Entity> byName = new java.util.HashMap<String, Entity>();
        for (int i = 0; i < all.size(); i++) byName.put(all.get(i).getIdentity().getName(), all.get(i));
        for (Entity member : byName.values()) {
            String owner = ownerOf(member, byName, model);
            if (owner != null) known.put(member.getIdentity().getName(), owner);
        }
        OWNERS.put(model, known);
        return known;
    }

    /**
     * The field by which a member points back at its root, or null.
     *
     * Read from the root's side: the mappedBy of a relation the root holds
     * whose target is this member. Only when the root names none is a field of
     * the root's type taken instead, and then only if there is exactly one -
     * a member may hold other references of that type, and the generator
     * writes through the owner, so a guess would write the wrong one.
     */
    private static String ownerOf(Entity member, java.util.Map<String, Entity> byName, ECoreModel model) {
        String name = member.getIdentity().getName();
        String root = aggregateOf(name, model);
        if (root == null || root.equals(name)) return null;

        java.util.List<String> candidates = new java.util.ArrayList<String>();
        io.elasticore.base.model.core.ListMap<String, Field> fields = member.getAllFieldListMap();
        if (fields != null) {
            for (Field f : fields.getList()) {
                if (f.getTypeInfo().isList()) continue;
                if (root.equals(f.getTypeInfo().getDefaultTypeName())) candidates.add(f.getName());
            }
        }
        if (candidates.isEmpty()) return null;

        Entity rootEntity = byName.get(root);
        io.elasticore.base.model.core.ListMap<String, Field> rootFields =
                rootEntity == null ? null : rootEntity.getAllFieldListMap();
        if (rootFields != null) {
            for (Field rf : rootFields.getList()) {
                String target = rf.getTypeInfo().isList()
                        ? rf.getTypeInfo().getCoreItemType() : rf.getTypeInfo().getDefaultTypeName();
                if (!name.equals(target)) continue;
                for (String rel : new String[]{"onetomany", "onetoone", "manytomany"}) {
                    String mb = propertyOf(rf, rel, "mappedBy");
                    if (mb == null) continue;
                    mb = mb.trim().replace("\"", "");
                    if (candidates.contains(mb)) return mb;
                }
            }
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    /** One property of a relation annotation, read the way FieldSpec.dslProperty reads it. */
    private static String propertyOf(Field f, String annotation, String key) {
        io.elasticore.base.model.core.Annotation a = f.getAnnotation(annotation);
        if (a == null || a.getProperties() == null) return null;
        String v = a.getProperties().getProperty(key);
        return v != null ? v : a.getProperties().getProperty(key.toLowerCase());
    }

    /** The domain's env.yml config, for a rule to read by dotted key. */
    static io.elasticore.base.model.pub.rules.ConfigSource configOf(final ECoreModel model) {
        return new io.elasticore.base.model.pub.rules.ConfigSource() {
            public String get(String key) {
                return model.getConfig(key);
            }
        };
    }

    public static EntitySpec fromEntity(Entity entity, ECoreModel model, String domainDirName) {
        String name = entity.getIdentity().getName();
        EntitySpec spec = new EntitySpec(name);
        spec.configSource(configOf(model));

        // Sibling entities of the domain: relation detection base
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        while (all.hasNext()) {
            spec.knownEntity(all.next().getIdentity().getName());
        }

        spec.containedIn(aggregateOf(name, model));
        teachAggregates(spec, model);
        for (java.util.Map.Entry<String, java.util.Map<String, String>> machine : entity.getStates().entrySet()) {
            spec.addStateMachine(machine.getKey(), machine.getValue());
        }
        registerDeriving(spec, model);
        registerReferencing(spec, model);
        registerOptionalEmbeddables(spec, model);
        spec.knownSignInEntities(signInsFor(model));
        spec.knownOtherLoginEntities(otherLoginsFor(model));
        registerReadLevels(spec, model);
        if (entity.getCommands() != null) {
            for (java.util.Map.Entry<String, io.elasticore.base.model.core.Items<Field>> cmd
                    : entity.getCommands().entrySet()) {
                spec.addCommand(cmd.getKey(), cmd.getValue() == null ? null : cmd.getValue().getItemList());
            }
            // The domain's enums, for a command parameter of one.
            if (!entity.getCommands().isEmpty() && model.getEnumModels() != null) {
                String enumPkg = model.getNamespace("enumeration");
                if (enumPkg == null || enumPkg.isEmpty()) enumPkg = model.getNamespace("entity");
                ModelComponentItems<io.elasticore.base.model.enums.EnumModel> enums = model.getEnumModels().getItems();
                for (int i = 0; i < enums.size(); i++) {
                    spec.typePackage(enums.get(i).getIdentity().getName(), enumPkg);
                }
            }
        }
        declaredQueries(spec, name, model);
        // A declared query may answer with a DTO; the repository rules have
        // to know one when they see it, as 2.x does.
        registerDtos(spec, model);

        String parent = entity.getMetaInfo().getMetaAnnotationValue("extend");
        if (parent != null && !parent.isEmpty()) {
            spec.parent(parent);
        }

        // Annotations the DSL hands to the class as written: "@jpa:..." first,
        // then "@entity:...", which is the order setNativeAnnotation appends.
        java.util.Map<String, io.elasticore.base.model.core.Annotation> nativeMap =
                entity.getMetaInfo().getMetaAnnotationMap();
        if (nativeMap != null) {
            for (String prefix : new String[]{"jpa:", "entity:"}) {
                for (java.util.Map.Entry<String, io.elasticore.base.model.core.Annotation> en
                        : nativeMap.entrySet()) {
                    if (!en.getKey().contains(prefix)) continue;
                    spec.nativeEntityAnnotation(String.valueOf(en.getValue()));
                }
            }
        }

        // The class javadoc text, as 2.x reads it off the meta info.
        spec.docLabel(entity.getMetaInfo().getLabel());
        spec.docDescription(entity.getMetaInfo().getDescription());

        // A rolled-up entity extends its target and declares a discriminator;
        // the aliases live behind the dotted meta lookups.
        String rollupTarget = entity.getMetaInfo().getMetaAnnotationValue(EntityAnnotation.META_ROLL_UP_TARGET);
        if (rollupTarget != null && !rollupTarget.isEmpty()) {
            spec.meta("rollupTarget", rollupTarget);
        }
        String rollupDisc = entity.getMetaInfo().getMetaAnnotationValue(EntityAnnotation.META_ROLL_UP_DISCRIMINATOR);
        if (rollupDisc != null && !rollupDisc.isEmpty()) {
            spec.meta("rollup.discriminator", rollupDisc);
        }

        // Meta annotations (@expose, @readonly, @restExclude...) drive
        // artifact-level decisions such as endpoint selection.
        java.util.Map<String, io.elasticore.base.model.core.Annotation> metaMap =
                entity.getMetaInfo().getMetaAnnotationMap();
        if (metaMap != null) {
            for (java.util.Map.Entry<String, io.elasticore.base.model.core.Annotation> en : metaMap.entrySet()) {
                io.elasticore.base.model.core.Annotation a = en.getValue();
                spec.meta(en.getKey(), a == null ? null : a.getValue());
                if (a != null) spec.metaKeyValues(en.getKey(), keyValues(a));

                // The loader keeps a second and further occurrence of the same
                // annotation as siblings of the first. Nothing in 2.x read
                // them, because nothing in 2.x could be written twice and mean
                // two things; an invariant can, so they are carried across.
                if (a == null || a.getSiblings() == null) continue;
                for (io.elasticore.base.model.core.Annotation sibling : a.getSiblings()) {
                    spec.metaOccurrence(en.getKey(), sibling.getValue());
                }
            }
        }

        // Domain enums: db column length and converter flag (setFieldColumnAnnotation
        // lines 1498-1514 / setConvertAnnotation). Index access, not hasNext/next:
        // EnumModels.getItems() returns a shared iterator instance that would be
        // consumed by the first entity otherwise.
        registerEnums(spec, model);
        registerEmbeddables(spec, model);

        ModelComponentItems<Field> fields = entity.getItems();
        while (fields.hasNext()) {
            spec.addField(fields.next());
        }

        // Inherited fields included: an artifact describing the entity as a
        // table has to name every column, not just the ones declared here.
        io.elasticore.base.model.core.ListMap<String, Field> everyField = entity.getAllFieldListMap();
        if (everyField != null) {
            for (Field f : everyField.getList()) {
                spec.addAllField(f);
            }
        }

        spec.domain(domainDirName, domainClassNameOf(model, domainDirName));

        // env.yml config values relevant to emission (ControlSrcPublisher /
        // resolveJsonViewMode fallback path).
        // @Index declarations repeat, so they arrive as annotation siblings.
        io.elasticore.base.model.core.Annotation idx =
                entity.getMetaInfo().getMetaAnnotation("index");
        if (idx != null && idx.getSiblings() != null) {
            for (io.elasticore.base.model.core.Annotation one : idx.getSiblings()) {
                java.util.Properties props = one.getProperties();
                if (props == null) continue;
                spec.index(props.getProperty("name"),
                        props.getProperty("columnList"),
                        props.getProperty("unique"));
            }
        }

        // Entity @schema wins over the env.yml tableSchema config.
        String schema = entity.getMetaInfo().getMetaAnnotationValue("schema");
        if (schema == null || schema.isEmpty()) {
            schema = model.getConfig("tableSchema");
        }
        spec.schema(schema);

        // 2.x asks the search DTO whether it carries a pageSize. That page size
        // is derived for every @expose entity - a bare @expose gets the default
        // one - so the observable condition here is the annotation itself, not
        // its value. Skill is declared "@expose" with no size and still pages.
        spec.pageable(entity.getMetaInfo().hasMetaAnnotation("expose"));

        // What the generated service actually returns: a Page when the search
        // DTO names a page size, a List otherwise (ControlSrcPublisher.isPageable).
        io.elasticore.base.model.dto.DataTransfer srch = searchDtoOf(entity, model);
        if (srch != null) spec.searchDtoClassName(srch.getIdentity().getName());
        spec.testCode("true".equals(model.getConfig("testCode", "false")));
        spec.supportAI("true".equals(model.getConfig("supportAI", "false")));

        spec.searchPageable(srch != null
                && srch.getMetaInfo().getMetaAnnotationValue("searchable.pageSize") != null);

        // The fields the entity's DTO carries. An artifact that writes to a
        // DTO - the generated test's fixture - can only touch these: a field
        // the DTO left out has no setter there.
        io.elasticore.base.model.dto.DataTransfer view = model.getDataTransferModels() == null
                ? null
                : model.getDataTransferModels().getItems()
                        .findByName(String.format("%sDTO", name));
        if (view != null) {
            io.elasticore.base.model.core.ListMap<String, Field> viewFields = view.getAllFieldListMap();
            if (viewFields != null) {
                for (Field f : viewFields.getList()) spec.addDtoField(f);
            }
        }

        spec.apiPath(model.getConfig("apiPath", "/api"));
        String jsonView = model.getConfig("jsonview", "false");
        spec.jsonView(!"false".equalsIgnoreCase(jsonView));
        spec.jsonViewConfig(jsonView);

        copyNamespaces(spec, model);

        // v3 clean mode inputs (harmless for the 2.x-compatible path)
        spec.tablePrefix(model.getConfig("table.prefix", ""));

        return spec;
    }

    /**
     * Wraps a loaded DTO for the rule engine.
     *
     * The spec is named after the entity the DTO is a view of, because the
     * artifact names are built from that, and it carries both field lists:
     * the entity's own, which the reverse direction of the mapper walks, and
     * the DTO's expanded one, which everything else does.
     *
     * Meta annotations come from the DTO, not the entity. They decide things
     * the two disagree about - whether views are on, whether the search
     * derives a condition per field - and 2.x asks the DTO in every case.
     */
    public static EntitySpec fromDto(io.elasticore.base.model.dto.DataTransfer dto,
                                     ECoreModel model, String domainDirName) {
        // A relation the DSL deferred is resolved before the field list is
        // read: 2.x does this in DtoSrcFilePublisher before asking for the
        // fields, and the resolution adds fields of its own.
        resolveDeferredFields(dto, model);

        String entityName = viewedEntityOf(dto);
        Entity entity = entityName == null ? null
                : model.getEntityModels().findByName(entityName);

        EntitySpec spec = new EntitySpec(entityName == null ? dto.getIdentity().getName() : entityName);
        spec.configSource(configOf(model));
        spec.valueObjectsInDto(valueObjectsInDto);

        // A DTO stands for an entity, so it stands inside whatever aggregate
        // that entity does. Without this a rule reading the boundary off a DTO
        // spec would see every one of them as a root.
        if (entityName != null) spec.containedIn(aggregateOf(entityName, model));
        teachAggregates(spec, model);

        // Sibling entities and the domain's enums and embeddables: the same
        // model knowledge an entity spec carries, since the DTO's fields are
        // the entity's fields.
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        while (all.hasNext()) {
            spec.knownEntity(all.next().getIdentity().getName());
        }
        registerEnums(spec, model, true);
        documentSharedEnums(spec);
        registerEmbeddables(spec, model);
        registerDtos(spec, model);
        registerReadLevels(spec, model);

        if (entity != null) {
            String parent = entity.getMetaInfo().getMetaAnnotationValue("extend");
            if (parent != null && !parent.isEmpty()) spec.parent(parent);
        }

        // The DTO's own fields, expanded: what @template brought in, minus
        // what "--" removed, plus whatever the DTO declares for itself.
        //
        // These are the spec's fields, because a per-field rule walks those,
        // and every DTO rule is asking about the DTO. The entity's fields are
        // the mapper's business, and it gets a spec of its own.
        boolean searchable = dto.getMetaInfo().hasMetaAnnotation(
                io.elasticore.base.model.dto.DataTransferAnnotation.META_SEARCHABLE);

        // A DTO extending another declares only its own fields; the parent's
        // arrive through the extends clause. 2.x swaps the field list for the
        // DTO's own items in that case. The search DTO ignores extension.
        String parentDto = dto.getMetaInfo().getMetaAnnotationValue(EntityAnnotation.META_EXTEND);
        boolean ownFieldsOnly = !searchable && parentDto != null && !parentDto.isEmpty();

        io.elasticore.base.model.core.ListMap<String, Field> dtoFields = dto.getAllFieldListMap();
        java.util.List<Field> fieldSource = new java.util.ArrayList<Field>();
        if (ownFieldsOnly) {
            ModelComponentItems<Field> own = dto.getItems();
            for (int i = 0; i < own.size(); i++) fieldSource.add(own.get(i));
        } else if (dtoFields != null) {
            fieldSource.addAll(dtoFields.getList());
        }
        // The request of an auto-generated DTO names a required reference by
        // its key, so the flattened key is what is required there.
        boolean keyRequired = referenceRequiredByKey && !searchable && entity != null
                && dto.getMetaInfo().hasMetaAnnotation(EntityAnnotation.AUTOGENERATED);
        {
            for (Field f : fieldSource) {
                if (keyRequired && flattensRequiredReference(f, entity, model)) {
                    f = requiredCopyOf(f, dto);
                }
                // A field still typed as an entity has no place in a DTO: the
                // relation is carried by the counterpart's DTO and by the
                // flattened id, both added when the deferral was resolved.
                // The search DTO is the exception: an optional relation stays
                // as an equality property, while a required one searches only
                // through its flattened id - which is what 2.x emits.
                if (namesAnEntity(f, model)) {
                    // A value object travels whole: it is immutable, has no
                    // identity and no relation to follow, so the DTO can carry
                    // it as it is. v3 and Kotlin only - 2.x leaves it out.
                    boolean value = valueObjectsInDto && !searchable && !f.getTypeInfo().isList()
                            && isEmbeddable(f.getTypeInfo().getDefaultTypeName(), model);
                    if (value) {
                        spec.addField(f);
                        spec.addDtoField(f);
                        continue;
                    }
                    if (!searchable) continue;
                    if (f.getTypeInfo().isList()) continue;
                    if (f.hasAnnotation(EntityAnnotation.NOT_NULL)) continue;
                }
                // A named model type the context cannot serve is dropped, as
                // isEnableInDTO drops it: a relation whose counterpart has no
                // DTO travels only through its flattened id.
                if (!enabledInDto(f)) continue;
                if (searchable && f.hasAnnotation(
                        io.elasticore.base.model.dto.DataTransferAnnotation.META_SEARCHABLE_BYPASS)) {
                    continue;
                }
                spec.addField(f);
                spec.addDtoField(f);
            }
        }

        // Asked directly, because a DTO can be exposed without the annotation
        // appearing in its own declared meta map.
        if (dto.getMetaInfo().hasMetaAnnotation(EntityAnnotation.META_EXPOSE)) {
            spec.meta("expose", dto.getMetaInfo().getMetaAnnotationValue(EntityAnnotation.META_EXPOSE));
        }

        // The entity's fields in the entity's order: the mapper's reverse
        // direction walks these, as 2.x walks the entity's field map.
        if (entity != null) {
            io.elasticore.base.model.core.ListMap<String, Field> entityFields =
                    entity.getAllFieldListMap();
            if (entityFields != null) {
                for (Field f : entityFields.getList()) {
                    spec.addAllField(f);
                }
            }
        }

        // The DTO's own body vs what the referenced entity carried in: a
        // declared field stays in a search DTO even without a condition.
        for (io.elasticore.base.model.pub.rules.FieldSpec fs : spec.fields()) {
            fs.declaredInDto(dto.getItems().findByName(fs.name()) != null);
        }

        java.util.Map<String, io.elasticore.base.model.core.Annotation> metaMap =
                dto.getMetaInfo().getMetaAnnotationMap();
        if (metaMap != null) {
            for (java.util.Map.Entry<String, io.elasticore.base.model.core.Annotation> en : metaMap.entrySet()) {
                io.elasticore.base.model.core.Annotation a = en.getValue();
                spec.meta(en.getKey(), a == null ? null : a.getValue());
                if (a != null) spec.metaKeyValues(en.getKey(), keyValues(a));
            }
        }

        String declaredName = dto.getIdentity().getName();
        // What makes a search DTO is @searchable, not its name: CouponQuery
        // is one as much as CouponSrchDTO is.
        if (declaredName.endsWith("SrchDTO") || dto.getMetaInfo().hasMetaAnnotation("searchable")) {
            spec.searchDtoClassName(declaredName);
        } else {
            spec.dtoClassName(declaredName);
        }
        spec.searchPageSize(searchPageSizeOf(dto));
        if (declaredName.endsWith("SrchDTO") || dto.getMetaInfo().hasMetaAnnotation("searchable")) {
            spec.defaultSort(defaultSortOf(dto, entity));
        }

        // What the DTO leaves out of its JSON.
        String include = dto.getMetaInfo().getMetaAnnotationValue("jsonInclude");
        if (include != null && !include.isEmpty()) {
            spec.nativeClassAnnotation(String.format(
                    "@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.%s)",
                    include.toUpperCase(java.util.Locale.ROOT)));
        }
        spec.domain(domainDirName, domainClassNameOf(model, domainDirName));
        spec.jsonViewConfig(model.getConfig("jsonview", "false"));
        spec.pageableInterface(model.getConfig("interface.pageable"));
        spec.sortableInterface(model.getConfig("interface.sortable"));

        copyNamespaces(spec, model);

        collectShadowImports(spec, dto, searchable, parentDto);
        return spec;
    }

    /**
     * The entity file's import list, as 2.x assembles it: @Audited first,
     * then the HashSet of every referenced model type's FQCN. The @Entity
     * import and a cross-domain parent's FQCN are appended by the file rule,
     * because they depend on what the class rules rendered.
     */
    public static void collectEntityImports(EntitySpec spec, Entity entity, ECoreModel model) {
        if (entity.getMetaInfo().hasMetaAnnotation("audited")) {
            spec.dtoImport("org.hibernate.envers.Audited");
        }

        // @NotAudited names its own import while the field renders, as
        // @Audited does for the class.
        ModelComponentItems<Field> forAudit = entity.getItems();
        int auditCount = forAudit.size();
        for (int i = 0; i < auditCount; i++) {
            if (forAudit.get(i).hasAnnotation("notaudited")) {
                spec.dtoImport("org.hibernate.envers.NotAudited");
                break;
            }
        }

        // getDefaultValueSetup adds these while the fields render, before the
        // referenced-type set is appended.
        ModelComponentItems<Field> forDefaults = entity.getItems();
        int defaultCount = forDefaults.size();
        for (int i = 0; i < defaultCount; i++) {
            Field f = forDefaults.get(i);
            if (!f.hasAnnotation("default")) continue;
            String val = f.getAnnotationValue("default");
            String base = f.getTypeInfo().getBaseFieldType() == null
                    ? null : f.getTypeInfo().getBaseFieldType().name();
            if (f.getTypeInfo().isBigDecimal()) {
                spec.dtoImport("java.math.BigDecimal");
            } else if ("now".equalsIgnoreCase(val)) {
                if ("DATE".equals(base)) spec.dtoImport("java.time.LocalDate");
                else if ("DATETIME".equals(base)) spec.dtoImport("java.time.LocalDateTime");
                else if ("TIME".equals(base)) spec.dtoImport("java.time.LocalTime");
            }
        }

        java.util.Set<String> importSet = new java.util.HashSet<String>();
        boolean hasEnumNs = model.getNamespace("enumeration") != null;

        ModelComponentItems<Field> fields = entity.getItems();
        int count = fields.size();
        for (int i = 0; i < count; i++) {
            Field f = fields.get(i);
            // The shadow model's individual class set: every named model type.
            if (!f.getTypeInfo().isBaseType()) {
                for (String t : f.getTypeInfo().getTypes()) {
                    String fq = componentFqcn(t);
                    if (fq != null) importSet.add(fq);
                }
            }
            // addEnumImportsToSet resolves the core item type the same way,
            // but only when the domain names an enumeration namespace.
            if (hasEnumNs) {
                String fq = componentFqcn(f.getTypeInfo().getCoreItemType());
                if (fq != null) importSet.add(fq);
            }
        }
        for (String fq : importSet) {
            spec.dtoImport(fq);
        }

        // A parent of another domain is named simply and imported fully.
        String parent = spec.parentName();
        if (parent == null || parent.isEmpty()) parent = spec.metaValue("rollupTarget");
        if (parent != null && !parent.isEmpty()) {
            try {
                io.elasticore.base.ECoreModelContext ctx =
                        io.elasticore.base.model.core.BaseECoreModelContext.getContext();
                io.elasticore.base.model.DataModelComponent c =
                        ctx == null ? null : ctx.findModelComponent(parent);
                if (c != null && !c.getIdentity().getDomainId()
                        .equals(entity.getIdentity().getDomainId())) {
                    String fq = componentFqcn(c.getIdentity().getName());
                    if (fq != null && fq.indexOf('.') > 0) {
                        spec.parentImport(fq);
                    }
                }
            } catch (Throwable ignore) {
            }
        }
    }

    /**
     * The fully-qualified name of a type, searched across every loaded
     * domain in the order 2.x searches them, so a name declared in several
     * domains lands on the same one.
     */
    /**
     * Every other domain's enums, for the example a field typed with one
     * documents itself by: a common Indicator used from account is listed
     * the way account's own enums are. The spec's own domain registered
     * first, so its enums win a shared name, as in 2.x's getExample.
     */
    static void documentSharedEnums(EntitySpec spec) {
        try {
            io.elasticore.base.ECoreModelContext ctx =
                    io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            if (ctx == null || ctx.getAllDomainNames() == null) return;
            for (String domainNm : ctx.getAllDomainNames()) {
                io.elasticore.base.ModelDomain d = ctx.getDomain(domainNm);
                if (d == null || d.getModel() == null || d.getModel().getEnumModels() == null) continue;
                ModelComponentItems<io.elasticore.base.model.enums.EnumModel> enums =
                        d.getModel().getEnumModels().getItems();
                for (int i = 0; i < enums.size(); i++) {
                    spec.documentEnum(fromEnum(enums.get(i), d.getModel()));
                }
            }
        } catch (RuntimeException ignore) {
            // Documentation only: a context that cannot be walked leaves the example out.
        }
    }

    private static String fqcnAcrossDomains(String typeName) {
        try {
            io.elasticore.base.ECoreModelContext ctx =
                    io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            if (ctx == null) return null;
            String[] domains = ctx.getAllDomainNames();
            if (domains == null) return null;
            for (String domainNm : domains) {
                io.elasticore.base.ModelDomain d = ctx.getDomain(domainNm);
                if (d == null) continue;
                io.elasticore.base.model.DataModelComponent c =
                        d.getModel().findModelByName(typeName);
                if (c == null) continue;
                String ns = d.getModel().getNamespace(
                        c.getIdentity().getComponentType().getName());
                if (ns == null) continue;
                return String.format("%s.%s", ns, c.getIdentity().getName());
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    /** The package a model type lives in - shared with the mapper's shadow. */
    public static String packageOfType(String typeName) {
        return componentPackage(typeName);
    }

    /** The fully-qualified name of a model type, or null when it is not one. */
    private static String componentFqcn(String typeName) {
        if (typeName == null) return null;
        String pkg = componentPackage(typeName);
        return pkg == null ? null : String.format("%s.%s", pkg, typeName);
    }

    /**
     * The import list, as the 2.x shadow model collects it: the packages of
     * every referenced model type, plus jackson wildcards driven by what the
     * field rules will emit. The same HashSet as 2.x, because the file's
     * import order is that set's iteration order.
     */
    private static void collectShadowImports(EntitySpec spec,
                                             io.elasticore.base.model.dto.DataTransfer dto,
                                             boolean searchable, String parentDto) {
        java.util.Set<String> shadow = new java.util.HashSet<String>();

        if (parentDto != null && !parentDto.isEmpty()) {
            String pkg = componentPackage(parentDto);
            if (pkg != null) shadow.add(pkg + ".*");
        }

        String mode = spec.jsonViewMode();
        boolean expose = spec.hasMeta("expose");

        for (io.elasticore.base.model.pub.rules.FieldSpec fs : spec.fields()) {
            if (searchable) {
                // Only the fields the search DTO will emit reach its shadow:
                // conditioned ones, and the ones its own body declares.
                if (!fs.hasSearch() && !expose && !fs.declaredInDto()) continue;
                // A relation is searched by its flattened id and a collection
                // of them not at all, so neither names a package here - the
                // same two the search rule drops.
                if (fs.hasDsl("deferred") && fs.isEntityRef()) continue;
                if (fs.isList()
                        && !fs.isBaseItemType()
                        && !spec.isKnownEnum(fs.coreItemType())) continue;
                // The search DTO reads json names but not access markers.
                if (fs.dslValueAny("jsonproperty", "json") != null) {
                    shadow.add("com.fasterxml.jackson.annotation.*");
                }
            } else {
                if (io.elasticore.base.model.pub.rules.base.DtoBaseRules.jsonPropertyFires(fs)) {
                    shadow.add("com.fasterxml.jackson.annotation.*");
                }
                if (io.elasticore.base.model.pub.rules.base.DtoBaseRules.viewAnnotationFires(mode, fs)) {
                    shadow.add("com.fasterxml.jackson.annotation.JsonView");
                }
            }

            String declaredType = fs.dtoTypeName();
            if (declaredType != null && !declaredType.isEmpty()) {
                String pkg = componentPackage(declaredType);
                if (pkg != null) shadow.add(pkg + ".*");
            } else if (!fs.raw().getTypeInfo().isBaseType()) {
                for (String t : fs.raw().getTypeInfo().getTypes()) {
                    String pkg = componentPackage(t);
                    if (pkg != null) shadow.add(pkg + ".*");
                }
            }
        }

        for (String ns : shadow) {
            spec.dtoImport(ns);
        }

        // File-level appends follow the set, in the 2.x sequence. The
        // JsonInclude pair and the sortable/pageable interfaces are appended
        // by the file rules after these.
        if (!searchable && !"false".equals(mode)) {
            spec.dtoImport("com.fasterxml.jackson.annotation.JsonView");
        }
    }

    /**
     * The package a model type lives in, resolved the way the 2.x shadow
     * model resolves it - through the global context, so cross-domain and
     * cross-module references land on their declared namespaces.
     */
    private static String componentPackage(String typeName) {
        try {
            io.elasticore.base.ECoreModelContext ctx =
                    io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            if (ctx == null) return null;
            io.elasticore.base.model.DataModelComponent c = ctx.findModelComponent(typeName);
            if (c == null) return null;
            String domainId = c.getIdentity().getDomainId();
            return ctx.getDomain(domainId).getModel()
                    .getNamespace(c.getIdentity().getComponentType().getName());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The repositories the helper injects for entities of another domain.
     * 2.x records them while the entities render (storeRefEntityInfo): a
     * relation whose target lives in a different entity namespace is
     * external. A rolled-up target shares its parent's table and gets no
     * repository of its own.
     */
    private static void collectExternalRepositories(
            io.elasticore.base.model.pub.rules.DomainSpec spec, ECoreModel model,
            java.util.List<io.elasticore.base.model.pub.rules.EntitySpec> entitySpecs) {

        String ownNamespace = model.getNamespace("entity");
        if (ownNamespace == null) return;

        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (io.elasticore.base.model.pub.rules.EntitySpec e : entitySpecs) {
            for (io.elasticore.base.model.pub.rules.FieldSpec f : e.fields()) {
                if (f.isBaseType()) continue;
                String type = f.coreItemType();
                if (type == null || !seen.add(type)) continue;

                Entity target = globalEntity(type);
                if (target == null) continue;
                if (target.getMetaInfo().hasMetaAnnotation("embeddable")) continue;
                if (target.getMetaInfo().hasMetaAnnotation("rollup")) continue;

                try {
                    io.elasticore.base.ECoreModelContext ctx =
                            io.elasticore.base.model.core.BaseECoreModelContext.getContext();
                    io.elasticore.base.ModelDomain d =
                            ctx.getDomain(target.getIdentity().getDomainId());
                    if (d == null) continue;
                    String targetNamespace = d.getModel().getNamespace("entity");
                    if (targetNamespace == null || ownNamespace.equals(targetNamespace)) continue;

                    String repoPkg = d.getModel().getNamespace("repository");
                    if (repoPkg != null) spec.externalEntity(type, repoPkg);
                } catch (Throwable ignore) {
                }
            }
        }
    }

    /** The mapper-owning class name: the configured domain name, camel-cased. */

    /**
     * Every namespace the domain declares, not a fixed list of them.
     *
     * A rule set that contributes an artifact names the namespace key that
     * places it, and that key can be one no built-in artifact uses. Copying
     * a hard-coded six meant such a key resolved to null in the rules while
     * the runner placed the file correctly - the file landed in one package
     * and declared another.
     */
    private static void copyNamespaces(EntitySpec spec, ECoreModel model) {
        for (java.util.Map.Entry<String, String> en : model.getNamespaceMap().entrySet()) {
            spec.pkg(en.getKey(), en.getValue());
        }
    }

    private static String domainClassNameOf(ECoreModel model, String domainDirName) {
        String configName = model.getConfigDomainName();
        String base = (configName == null || configName.isEmpty()) ? domainDirName : configName;
        return capitalize(io.elasticore.base.util.StringUtils.snakeToCamel(base, "-"));
    }

    /**
     * Wraps a declared transaction port for the rule engine: what it connects
     * to, and the operations it offers.
     */
    public static io.elasticore.base.model.pub.rules.PortSpec fromPort(
            io.elasticore.base.model.port.Port port, ECoreModel model) {

        io.elasticore.base.model.MetaInfo meta = port.getMetaInfo();
        io.elasticore.base.model.pub.rules.PortSpec spec =
                new io.elasticore.base.model.pub.rules.PortSpec(
                        port.getIdentity().getName(), port.getIdentity().getDomainId());

        String type = meta.getMetaAnnotationValue("type");
        spec.protocol(type == null ? null : type.toLowerCase(java.util.Locale.ROOT));
        spec.description(meta.getLabel());

        spec.url(meta.getMetaAnnotationValue(
                io.elasticore.base.model.port.PortAnnotation.META_URL));
        spec.authKey(meta.getMetaAnnotationValue(
                io.elasticore.base.model.port.PortAnnotation.META_AUTHORIZATION));
        timeout(spec, meta, "connectTimeout", "timeout.connect");
        timeout(spec, meta, "readTimeout", "timeout.read");
        timeout(spec, meta, "writeTimeout", "timeout.write");
        timeout(spec, meta, "responseTimeout", "timeout.response");

        spec.springHttp(springHttpPorts(model) && "http".equals(spec.protocol()));
        spec.domainClassName(domainClassNameOf(model, port.getIdentity().getDomainId()));
        spec.datasource(meta.getMetaAnnotationValue("datasource"));
        spec.sqlSource(meta.getMetaAnnotationValue("sqlsource"));

        ModelComponentItems<io.elasticore.base.model.repo.Method> methods = port.getItems();
        int count = methods.size();
        for (int i = 0; i < count; i++) {
            io.elasticore.base.model.repo.Method m = methods.get(i);
            io.elasticore.base.model.pub.rules.PortSpec.Operation op =
                    spec.operation(m.getIdentity().getName(), m.getReturnType());
            op.description(m.getDescription());

            io.elasticore.base.model.core.Items<Field> params = m.getParams();
            if (params != null) {
                for (Field f : params.getItemList()) {
                    String declaration = paramTypeName(f);
                    op.param(declaration, f.getName(), bareParamType(declaration));
                }
            }
            endpoint(op, m.getMetaInfo());
        }

        // A port the Spring client cannot send as the runtime does stays on the runtime.
        if (spec.springHttp()) {
            String reason = io.elasticore.base.model.pub.rules.base.SpringHttpPorts.runtimeOnlyReason(spec);
            if (reason != null) {
                spec.springHttp(false);
                io.elasticore.base.util.ConsoleLog.printWarn(String.format(
                        "[elcore3] %s stays on the ElastiCORE port runtime (httpPort: spring): %s", spec.id(), reason));
            }
        }
        spec.pkg("port", model.getNamespace("port"))
            .pkg("entity", model.getNamespace("entity"))
            .pkg("dto", model.getNamespace("dto"));
        if (model.getEnumModels() != null && model.getEnumModels().getItems().size() > 0) {
            spec.pkg("enumeration", model.getNamespace("enumeration"));
        }
        return spec;
    }

    /**
     * Wraps a declared gRPC/proto port for the rule engine: one rpc entry per
     * method, plus every message and enum reachable from an rpc's input or
     * output type, collected the way ProtoSrcPublisher's makeMessage does -
     * except in declaration order, since that publisher's HashSet leaves
     * message order dependent on hash values rather than the DSL.
     *
     * A method type name with no matching DTO (2.x's "_Empty" convention for
     * a parameterless rpc) still gets an empty message, so the rpc's own
     * reference to it resolves; 2.x reaches the same empty body by falling
     * through every one of its special cases for an unrecognized type name.
     */
    public static io.elasticore.base.model.pub.rules.ProtoServiceSpec fromProtoService(
            io.elasticore.base.model.port.Port port, ECoreModel model) {

        io.elasticore.base.model.pub.rules.ProtoServiceSpec spec =
                new io.elasticore.base.model.pub.rules.ProtoServiceSpec(
                        port.getIdentity().getName(),
                        model.getNamespace(io.elasticore.base.model.ConstantParam.KEYNAME_PROTO));

        io.elasticore.base.model.MetaInfo meta = port.getMeta();
        spec.server(meta.hasMetaAnnotation("server"))
            .client(meta.hasMetaAnnotation("client"), meta.getMetaAnnotationValue("client"));

        java.util.LinkedHashSet<String> referencedTypes = new java.util.LinkedHashSet<String>();

        ModelComponentItems<io.elasticore.base.model.repo.Method> methods = port.getItems();
        int count = methods.size();
        for (int i = 0; i < count; i++) {
            io.elasticore.base.model.repo.Method m = methods.get(i);
            String inputType = protoInputType(m.getParams());
            String outputType = m.getReturnType();
            spec.rpc(m.getIdentity().getName(), inputType, outputType, m.getDescription());
            referencedTypes.add(inputType);
            referencedTypes.add(outputType);
        }

        io.elasticore.base.ECoreModelContext ctx =
                io.elasticore.base.model.core.BaseECoreModelContext.getContext();
        java.util.Set<String> seenMessages = new java.util.HashSet<String>();
        java.util.Set<String> seenEnums = new java.util.HashSet<String>();
        for (String type : referencedTypes) {
            collectProtoMessage(spec, ctx, type, seenMessages, seenEnums);
        }

        return spec;
    }

    /** A method's own single parameter type, or the 2.x "_Empty" sentinel for none. */
    private static String protoInputType(io.elasticore.base.model.core.Items<Field> params) {
        if (params != null) {
            for (Field f : params.getItemList()) {
                return f.getTypeInfo().getDefaultTypeName();
            }
        }
        return "_Empty";
    }

    private static void collectProtoMessage(io.elasticore.base.model.pub.rules.ProtoServiceSpec spec,
                                             io.elasticore.base.ECoreModelContext ctx, String typeName,
                                             java.util.Set<String> seenMessages, java.util.Set<String> seenEnums) {
        if (typeName == null || ctx == null || !seenMessages.add(typeName)) return;

        io.elasticore.base.model.dto.DataTransfer dto =
                ctx.findModelComponent(typeName, io.elasticore.base.model.dto.DataTransfer.class);
        if (dto == null) {
            spec.message(new EntitySpec(typeName));
            return;
        }

        EntitySpec msg = new EntitySpec(typeName);
        java.util.List<String> nestedTypes = new java.util.ArrayList<String>();

        io.elasticore.base.model.core.ListMap<String, Field> dtoFields = dto.getAllFieldListMap();
        for (Field f : dtoFields.getList()) {
            msg.addField(f);

            io.elasticore.base.model.entity.TypeInfo t = f.getTypeInfo();
            if (t.isBaseType()) continue;

            String itemType = t.isList() ? t.getCoreItemType() : t.getInitTypeInfo();
            if (ctx.findModelComponent(itemType, io.elasticore.base.model.dto.DataTransfer.class) != null) {
                nestedTypes.add(itemType);
            } else if (seenEnums.add(itemType)) {
                io.elasticore.base.model.enums.EnumModel enumModel =
                        ctx.findModelComponent(itemType, io.elasticore.base.model.enums.EnumModel.class);
                if (enumModel != null) addProtoEnum(spec, enumModel);
            }
        }

        spec.message(msg);
        for (String nestedType : nestedTypes) {
            spec.nest(typeName, nestedType);
            collectProtoMessage(spec, ctx, nestedType, seenMessages, seenEnums);
        }
    }

    private static void addProtoEnum(io.elasticore.base.model.pub.rules.ProtoServiceSpec spec,
                                     io.elasticore.base.model.enums.EnumModel enumModel) {
        java.util.List<io.elasticore.base.model.enums.EnumConstant> constants =
                enumModel.getEnumConstantItems().getItemList();
        String[] names = new String[constants.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = constants.get(i).getIdentity().getName();
        }
        spec.enumeration(enumModel.getIdentity().getName(), names);
    }

    /**
     * How a port operation declares one parameter: its type, and the multipart
     * content type when the parameter names one. A DSL list becomes a Java
     * List, with a base item type in its wrapper form.
     */
    private static String paramTypeName(Field f) {
        io.elasticore.base.model.entity.TypeInfo typeInfo = f.getTypeInfo();
        String type;
        if (typeInfo.isList()) {
            String itemType = typeInfo.getTypeParameterName();
            if (itemType == null || itemType.isEmpty()) {
                type = "List<?>";
            } else {
                io.elasticore.base.model.entity.BaseFieldType itemBase =
                        io.elasticore.base.model.entity.BaseFieldType.findByName(itemType);
                if (itemBase != null
                        && itemBase != io.elasticore.base.model.entity.BaseFieldType.UNKNOWN
                        && itemBase != io.elasticore.base.model.entity.BaseFieldType.LIST) {
                    itemType = itemBase.getWrapperClassName();
                }
                type = String.format("List<%s>", itemType);
            }
        } else {
            type = typeInfo.getDefaultTypeName();
        }

        String contentType = f.getAnnotationValue("contenttype", "contentType");
        if (contentType != null && !contentType.isEmpty()) {
            return String.format("@MultipartContentType(%s) %s",
                    io.elasticore.base.util.StringUtils.quoteString(contentType), type);
        }
        return type;
    }

    /** A parameter's type without the @MultipartContentType a declaration may lead with. */
    private static String bareParamType(String declaration) {
        if (!declaration.startsWith("@")) return declaration;
        int close = declaration.indexOf(") ");
        return close < 0 ? declaration : declaration.substring(close + 2).trim();
    }

    /**
     * Whether the domain's HTTP ports are written as Spring HTTP service
     * clients: env.yml "config: httpPort: spring", on Spring Boot 4 - the
     * service registry (@ImportHttpServices, spring.http.serviceclient) is
     * Spring Framework 7's. Asked for on Boot 3, the ports stay as they were
     * and the build says why.
     */
    public static boolean springHttpPorts(ECoreModel model) {
        String v = model.getConfig("httpPort");
        if (v == null || !"spring".equalsIgnoreCase(v.trim())) return false;
        if (io.elasticore.base.model.pub.rules.RulesConfig.springBootMajor() >= 4) return true;
        if (WARNED_HTTP_PORT.add(String.valueOf(model.getConfigDomainName()))) {
            io.elasticore.base.util.ConsoleLog.printWarn(String.format(
                    "[%s] httpPort: spring needs Spring Boot 4 (Spring Framework 7's HTTP service registry);"
                            + " the ports are generated for the ElastiCORE port runtime.", model.getConfigDomainName()));
        }
        return false;
    }

    private static final java.util.Set<String> WARNED_HTTP_PORT =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /** A declared timeout, ignored unless it reads as a non-negative count of ms. */
    private static void timeout(io.elasticore.base.model.pub.rules.PortSpec spec,
                                io.elasticore.base.model.MetaInfo meta,
                                String attribute, String annotationKey) {
        String val = meta.getMetaAnnotationValue(annotationKey);
        if (val == null) return;
        val = val.trim();
        if (val.isEmpty()) return;
        try {
            int ms = Integer.parseInt(val);
            if (ms >= 0) spec.timeout(attribute, Integer.valueOf(ms));
        } catch (NumberFormatException ignore) {
        }
    }

    /**
     * The endpoint an HTTP operation reaches. An explicit @httpEndpoint names
     * all three; the per-method shorthands name a url and imply the verb.
     */
    private static void endpoint(io.elasticore.base.model.pub.rules.PortSpec.Operation op,
                                 io.elasticore.base.model.MetaInfo meta) {
        if (meta == null) return;

        String url = null;
        String httpMethod = null;
        String contentType = null;

        io.elasticore.base.model.core.Annotation declared = meta.getMetaAnnotation(
                io.elasticore.base.model.port.PortAnnotation.META_HTTP_ENDPOINT);
        if (declared != null) {
            try {
                java.util.Properties props = declared.getProperties();
                url = (String) props.get("url");
                httpMethod = props.get("method").toString().toUpperCase(java.util.Locale.ROOT);
                contentType = (String) props.get("contentType");
            } catch (RuntimeException ignore) {
            }
        }
        if (contentType == null) {
            contentType = meta.getMetaAnnotationValue(
                    io.elasticore.base.model.port.PortAnnotation.META_CONTENT_TYPE);
        }

        String implied = null;
        if (url == null) {
            url = meta.getMetaAnnotationValue(
                    io.elasticore.base.model.port.PortAnnotation.META_GET_MAPPING);
            implied = "GET";
        }
        if (url == null) {
            url = meta.getMetaAnnotationValue(
                    io.elasticore.base.model.port.PortAnnotation.META_POST_MAPPING);
            implied = "POST";
        }
        if (url == null) {
            url = meta.getMetaAnnotationValue(
                    io.elasticore.base.model.port.PortAnnotation.META_PUT_MAPPING);
            implied = "PUT";
        }
        if (url == null) {
            url = meta.getMetaAnnotationValue(
                    io.elasticore.base.model.port.PortAnnotation.META_DELETE_MAPPING);
            implied = "DELETE";
        }
        if (url == null) return;

        if (httpMethod == null) httpMethod = implied;
        op.endpoint(url, httpMethod, contentType);
    }

    /** The entity a DTO is a view of: its @template, or what @searchable names. */
    private static String viewedEntityOf(io.elasticore.base.model.dto.DataTransfer dto) {
        String template = dto.getMetaInfo().getMetaAnnotationValue("template");
        if (template != null && !template.isEmpty()) return template;

        String searchable = dto.getMetaInfo().getMetaAnnotationValue("searchable.entity");
        if (searchable != null && !searchable.isEmpty()) return searchable;
        return null;
    }

    /**
     * @sort(orderDate-, name+): the order a search answers in when the
     * request names none - the search DTO's own, else its entity's. Each part
     * is a property and a direction (+ ascending, - descending; neither:
     * ascending). A property the entity does not have is a DSL warning and is
     * left out: sorting by it would answer every search with a 400.
     */
    static String defaultSortOf(io.elasticore.base.model.dto.DataTransfer dto, Entity entity) {
        String text = dto.getMetaInfo().getMetaAnnotationValue("sort");
        String owner = dto.getIdentity().getName();
        if ((text == null || text.trim().isEmpty()) && entity != null) {
            text = entity.getMetaInfo().getMetaAnnotationValue("sort");
            owner = entity.getIdentity().getName();
        }
        if (text == null || text.trim().isEmpty()) return null;

        StringBuilder out = new StringBuilder();
        for (String part : text.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            char last = p.charAt(p.length() - 1);
            boolean directed = last == '+' || last == '-';
            String property = directed ? p.substring(0, p.length() - 1).trim() : p;
            String root = property.contains(".") ? property.substring(0, property.indexOf('.')) : property;
            if (entity == null || entity.getAllFieldListMap() == null
                    || entity.getAllFieldListMap().get(root) == null) {
                io.elasticore.base.util.ConsoleLog.storeLog("DSL_WARN", String.format(
                        "[%s] @sort(%s): %s is not a field of %s; it is left out of the default order.",
                        owner, text.trim(), property, entity == null ? owner : entity.getIdentity().getName()));
                continue;
            }
            if (out.length() > 0) out.append(',');
            out.append(property).append(directed ? last : '+');
        }
        return out.length() == 0 ? null : out.toString();
    }

    /**
     * The page size a search DTO declares, or the default when it only says
     * it pages. Null when the search does not page at all.
     */
    private static Integer searchPageSizeOf(io.elasticore.base.model.dto.DataTransfer dto) {
        String declared = dto.getMetaInfo().getMetaAnnotationValue("searchable.pageSize");
        if (declared != null && !declared.isEmpty()) {
            try {
                return Integer.valueOf(declared.trim());
            } catch (NumberFormatException ignore) {
                return Integer.valueOf(100);
            }
        }
        String pageable = dto.getMetaInfo().getMetaAnnotationValue("searchable.pageable");
        return "true".equals(pageable) ? Integer.valueOf(100) : null;
    }

    /** Registers the DTOs of a domain, so a field's type can be recognised as one. */
    public static void registerDtos(EntitySpec spec, ECoreModel model) {
        if (model.getDataTransferModels() == null) return;
        ModelComponentItems<io.elasticore.base.model.dto.DataTransfer> dtos =
                model.getDataTransferModels().getItems();
        for (int i = 0; i < dtos.size(); i++) {
            io.elasticore.base.model.dto.DataTransfer d = dtos.get(i);
            String template = d.getMetaInfo().getMetaAnnotationValue("template");
            spec.knownDto(d.getIdentity().getName(), template);
        }
    }

    /**
     * Wraps a loaded enumeration for the rule engine: its declared fields, the
     * constants and the values they pass, and the fields @json and @db name.
     */
    public static io.elasticore.base.model.pub.rules.EnumSpec fromEnum(
            io.elasticore.base.model.enums.EnumModel model, ECoreModel domainModel) {

        io.elasticore.base.model.pub.rules.EnumSpec spec =
                new io.elasticore.base.model.pub.rules.EnumSpec(model.getIdentity().getName());

        spec.pkg("enumeration", domainModel.getNamespace("enumeration"));

        for (Field f : model.getFieldItems().getItemList()) {
            spec.addField(f);
        }

        if (model.getEnumConstantItems() != null) {
            for (io.elasticore.base.model.enums.EnumConstant c
                    : model.getEnumConstantItems().getItemList()) {
                java.util.List<String> values = new java.util.ArrayList<String>();
                for (io.elasticore.base.model.enums.EnumConstant.ConstructParam p
                        : c.getConstructParamList()) {
                    values.add(String.valueOf(p));
                }
                spec.constant(c.getIdentity().getName(), values.toArray(new String[0]));
            }
        }

        spec.jsonField(model.getMetaInfo().getMetaAnnotationValue("json"));
        spec.dbField(model.getMetaInfo().getMetaAnnotationValue("db"));
        return spec;
    }

    /**
     * Wraps a whole domain for the artifacts that exist once per domain: the
     * repository helper, the Q class, the Views class and the two support
     * interfaces.
     *
     * @param entitySpecs the entity specs already built for this domain, in
     *                    declaration order
     */
    public static io.elasticore.base.model.pub.rules.DomainSpec fromDomain(
            String domainDirName, ECoreModel model,
            java.util.List<io.elasticore.base.model.pub.rules.EntitySpec> entitySpecs) {

        // The class name follows the configured domain name, as 2.x names
        // BatchMapper for a blueprint directory called "management".
        String configName = model.getConfigDomainName();
        String base = (configName == null || configName.isEmpty()) ? domainDirName : configName;
        io.elasticore.base.model.pub.rules.DomainSpec spec =
                new io.elasticore.base.model.pub.rules.DomainSpec(
                        domainDirName,
                        capitalize(io.elasticore.base.util.StringUtils.snakeToCamel(base, "-")));

        for (io.elasticore.base.model.pub.rules.EntitySpec e : entitySpecs) {
            spec.entity(e);
        }

        spec.pkg("entity", model.getNamespace("entity"))
            .pkg("dto", model.getNamespace("dto"))
            .pkg("repository", model.getNamespace("repository"))
            .pkg("service", model.getNamespace("service"))
            .pkg("control", model.getNamespace("control"))
            .pkg("domainService", model.getNamespace("domainService"))
            .pkg("domainRepository", model.getNamespace("domainRepository"))
            .pkg("persistence", model.getNamespace("persistence"))
            .pkg("event", model.getNamespace("event"))
            .pkg("processManager", model.getNamespace("processManager"));
        // Every key the domain declares, for namespace(key) - base: expands
        // several, and a project's rule set may add one of its own. Kept
        // apart from pkg(), whose null for an undeclared key the built-in
        // rules read as "fall back".
        spec.namespaces(model.getNamespaceMap());
        for (String key : new String[]{"outbox", "table.prefix"}) {
            spec.config(key, model.getConfig(key));
        }
        spec.configSource(configOf(model));

        // The ports the DSL declares, so a domain service naming one is handed
        // the interface rather than a repository port that does not exist.
        if (model.getPortModels() != null) {
            io.elasticore.base.model.ModelComponentItems<io.elasticore.base.model.port.Port>
                    ports = model.getPortModels().getItems();
            for (int i = 0; i < ports.size(); i++) {
                String portName = ports.get(i).getIdentity().getName();
                // The port loader builds one of these per entity as well, and
                // the port rule declines for them. A name that is an entity is
                // an aggregate reference - registering it as a port had a
                // domain service asking to be given an Order rather than an
                // OrderRepositoryPort.
                if (model.getEntityModels().findByName(portName) != null) continue;
                spec.port(portName, model.getNamespace("port"));
            }
        }

        // A domain-level section, so it is read here rather than off any
        // entity - which is the definition of the pattern, not a filing
        // choice. Absent for every loader that has no such section and for
        // every project written before it existed.
        if (model.getDomainServiceModels() != null) {
            io.elasticore.base.model.ModelComponentItems<io.elasticore.base.model.domainservice.DomainService>
                    services = model.getDomainServiceModels().getItems();
            while (services.hasNext()) {
                io.elasticore.base.model.domainservice.DomainService service = services.next();
                spec.domainService(new io.elasticore.base.model.pub.rules.DomainSpec.DomainServiceSpec(
                        service.getIdentity().getName(),
                        service.getDescription(),
                        service.getUses(),
                        service.getOn()));
            }
        }

        // Only a domain that declares enums names their package: 2.x leaves
        // enumPackageName null otherwise, and the mapper's import line with it.
        boolean declaresEnums = model.getEnumModels() != null
                && model.getEnumModels().getItems().size() > 0;
        spec.hasEnums(declaresEnums);
        if (declaresEnums) spec.pkg("enumeration", model.getNamespace("enumeration"));

        // Types the Q class names by their simple name have to be imported.
        // The same HashSet as 2.x: the import order of the file is that set's
        // iteration order. Types resolve across every loaded domain, in the
        // domain order 2.x searches, and a type of the Q class's own package
        // needs no import.
        java.util.Set<String> qImports = new java.util.HashSet<String>();
        String qPackage = model.getNamespace("entity");
        for (io.elasticore.base.model.pub.rules.EntitySpec e : entitySpecs) {
            if (e.hasMeta("abstract")) continue;
            for (io.elasticore.base.model.pub.rules.FieldSpec f : e.allFields()) {
                if (f.isBaseType()) continue;
                String type = f.coreItemType();
                if (type == null || type.isEmpty()) continue;
                String fq = fqcnAcrossDomains(type);
                if (fq == null) continue;
                int lastDot = fq.lastIndexOf('.');
                if (lastDot <= 0) continue;
                if (fq.substring(0, lastDot).equals(qPackage)) continue;
                qImports.add(fq);
            }
        }
        for (String imp : qImports) {
            spec.qImport(imp);
        }

        // The mapper's imports are the shadow set of the types its sections
        // referenced; the runner collects them while the sections are built.

        // Repositories the domain declares for entities of another domain:
        // 2.x walks the repository models, which carry those too.
        collectExternalRepositories(spec, model, entitySpecs);

        spec.jsonViewConfig(model.getConfig("jsonview", "false"))
            .customPageableInterface(model.getConfig("interface.pageable"))
            .customSortableInterface(model.getConfig("interface.sortable"));

        return spec;
    }

    /**
     * Registers the enums of a domain model onto a spec. Also used by the
     * verify runner to make enums of OTHER domains visible (2.x resolves
     * enum references across all loaded domains).
     */
    /**
     * Turns each deferred relation of a DTO into the two things the API needs:
     * the counterpart's DTO for the response, and the counterpart's id
     * flattened onto this DTO for the request. A search DTO takes only the id.
     */
    private static void resolveDeferredFields(io.elasticore.base.model.dto.DataTransfer dto,
                                              ECoreModel model) {
        ModelComponentItems<Field> items = dto.getItems();
        int count = items.size();
        for (int i = 0; i < count; i++) {
            Field f = items.get(i);
            if (!f.hasAnnotation(EntityAnnotation.META_DEFERRED)) continue;

            String typeName = f.getTypeInfo().getDefaultTypeName();
            Entity entity = model.getEntityModels().findByName(typeName);
            if (entity == null) entity = globalEntity(typeName);
            if (entity == null) continue;
            // An embeddable is a value, not a relation: no DTO counterpart
            // and no flattened id.
            if (entity.getMetaInfo().hasMetaAnnotation("embeddable")) continue;

            boolean searchable = dto.getMetaInfo().hasMetaAnnotation(
                    io.elasticore.base.model.dto.DataTransferAnnotation.META_SEARCHABLE);
            if (!searchable) {
                java.util.Map<String, io.elasticore.base.model.core.Annotation> anno =
                        new java.util.HashMap<String, io.elasticore.base.model.core.Annotation>();
                put(anno, io.elasticore.base.model.core.Annotation.create(
                        io.elasticore.base.model.dto.DataTransferAnnotation.META_SEARCHABLE_BYPASS));
                // 2.x requires the nested object of a required relation. Where
                // the reference is required through its key instead, the
                // nested object is only the response's and stays optional.
                if (f.hasAnnotation(EntityAnnotation.NOT_NULL) && !referenceRequiredByKey) {
                    put(anno, io.elasticore.base.model.core.Annotation.create(EntityAnnotation.NOT_NULL[0]));
                }
                io.elasticore.base.model.core.Annotation label = f.getAnnotation("label");
                if (label != null) put(anno, label);
                // The response carries the relation; the request sets it through
                // the flattened id, so the schema documents it as read-only.
                put(anno, io.elasticore.base.model.core.Annotation.create(
                        io.elasticore.base.model.dto.DataTransferAnnotation.META_AUTO_RELATION));

                dto.addField(Field.builder()
                        .name(f.getName())
                        .parentMetaInfo(dto.getMetaInfo())
                        .type(typeName + "DTO")
                        .annotationMap(anno)
                        .build());
            }

            flattenIdentity(dto, entity, f.getName(), model);
        }
    }

    /**
     * Mirrors isEnableInDTO for the DTO's field list: a non-base type without
     * a @dtype and without a @ref must resolve to some model component - a
     * DTO, an enum, or an entity - or the field is dropped.
     */
    private static boolean enabledInDto(Field f) {
        if (f.getTypeInfo().isBaseType()) return true;
        for (String alias : new String[]{"dtotype", "dtype", "dto"}) {
            String declared = f.getAnnotationValue(alias);
            if (declared != null && !declared.isEmpty()) return true;
        }
        String typeName = f.getTypeInfo().isList()
                ? f.getTypeInfo().getCoreItemType()
                : f.getTypeInfo().getDefaultTypeName();
        if (typeName == null || typeName.indexOf('.') >= 0) return true;
        if (f.getAnnotationValue("ref") != null) return true;
        try {
            io.elasticore.base.ECoreModelContext ctx =
                    io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            return ctx == null || ctx.findModelComponent(typeName) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    /** Tells a spec the level each entity with @access is read at. */
    static void registerReadLevels(EntitySpec spec, ECoreModel model) {
        if (model.getEntityModels() == null) return;
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        for (int i = 0; i < all.size(); i++) {
            Entity candidate = all.get(i);
            io.elasticore.base.model.core.Annotation a = candidate.getMetaInfo().getMetaAnnotationMap() == null
                    ? null : candidate.getMetaInfo().getMetaAnnotationMap().get("access");
            if (a == null) continue;
            String text = a.getValue() != null ? a.getValue() : keyValues(a);
            spec.knownReadLevel(candidate.getIdentity().getName(),
                    io.elasticore.base.model.pub.rules.base.ServiceAccessRules.readLevel(text));
        }
    }

    /** Tells a spec which entities of the model derive values (@snapshot, @compute). */
    static void registerDeriving(EntitySpec spec, ECoreModel model) {
        if (model.getEntityModels() == null) return;
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        for (int i = 0; i < all.size(); i++) {
            Entity candidate = all.get(i);
            ModelComponentItems<Field> fields = candidate.getItems();
            if (fields == null) continue;
            for (int j = 0; j < fields.size(); j++) {
                Field f = fields.get(j);
                if (f.hasAnnotation("snapshot") || f.hasAnnotation("compute")) {
                    spec.knownDeriving(candidate.getIdentity().getName());
                    break;
                }
            }
        }
    }

    /**
     * Tells a spec which entities name rows of other aggregates by identity
     * (an @idof field, written by the id-reference rewrite) - directly, or
     * through a collection of members of their own aggregate that do.
     */
    static void registerReferencing(EntitySpec spec, ECoreModel model) {
        for (String name : facts(model).referencing) spec.knownReferencing(name);
    }

    /**
     * What the model-wide registrations find, worked out once per model:
     * every entity's spec asks the same question of the same model, and a
     * fixed point over all entities for each of them grew with the cube.
     */
    private static final class ModelFacts {
        final java.util.Set<String> referencing;
        final java.util.Set<String> optionalEmbeddables;

        ModelFacts(java.util.Set<String> referencing, java.util.Set<String> optionalEmbeddables) {
            this.referencing = referencing;
            this.optionalEmbeddables = optionalEmbeddables;
        }
    }

    private static ECoreModel factsModel;
    private static int factsFingerprint;
    private static ModelFacts modelFacts;

    /**
     * Cached per model and per its shape - the id-reference rewrite adds fields
     * to the same model object, and facts taken before it would be stale.
     */
    private static synchronized ModelFacts facts(ECoreModel model) {
        int fingerprint = fingerprint(model);
        if (model != factsModel || fingerprint != factsFingerprint || modelFacts == null) {
            modelFacts = new ModelFacts(referencing(model), optionalEmbeddables(model));
            factsModel = model;
            factsFingerprint = fingerprint;
        }
        return modelFacts;
    }

    private static int fingerprint(ECoreModel model) {
        if (model.getEntityModels() == null) return 0;
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        int fields = all.size();
        for (int i = 0; i < all.size(); i++) fields = 31 * fields + fieldsOf(all.get(i)).size();
        return fields;
    }

    /**
     * @signin entities of the application the model's controllers belong to,
     * said by the runner before each model; -1 when not said.
     */
    private static int applicationSignIns = -1;

    /** The application's other @login + @password entities, said with it; null when not said. */
    private static java.util.List<String> applicationOtherLogins;

    static void applicationSignIns(int count, java.util.List<String> otherLogins) {
        applicationSignIns = count;
        applicationOtherLogins = otherLogins;
    }

    /** What a spec is told: the application's count when the runner said it, this model's otherwise. */
    private static int signInsFor(ECoreModel model) {
        return applicationSignIns >= 0 ? applicationSignIns : signInEntities(model).size();
    }

    private static java.util.List<String> otherLoginsFor(ECoreModel model) {
        return applicationOtherLogins != null ? applicationOtherLogins : otherLoginEntities(model);
    }

    /** The entities of this model that say @signin - counted from the meta lines, nothing else worked out. */
    static java.util.List<String> signInEntities(ECoreModel model) {
        java.util.List<String> names = new java.util.ArrayList<String>();
        if (model.getEntityModels() == null) return names;
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getMetaInfo().hasMetaAnnotation("signin")) names.add(all.get(i).getIdentity().getName());
        }
        return names;
    }

    /**
     * The entities of this model with a @login and one @password that do not
     * say @signin - they sign in some other way, and the generated sign-in's
     * session key could be read as theirs.
     */
    static java.util.List<String> otherLoginEntities(ECoreModel model) {
        java.util.List<String> names = new java.util.ArrayList<String>();
        if (model.getEntityModels() == null) return names;
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        for (int i = 0; i < all.size(); i++) {
            Entity entity = all.get(i);
            if (entity.getMetaInfo().hasMetaAnnotation("signin") || entity.getMetaInfo().hasMetaAnnotation("readonly")) continue;
            ModelComponentItems<Field> fields = entity.getItems();
            boolean login = false;
            int passwords = 0;
            for (int j = 0; fields != null && j < fields.size(); j++) {
                if (fields.get(j).hasAnnotation("login")) login = true;
                if (fields.get(j).hasAnnotation("password")) passwords++;
            }
            if (login && passwords == 1) names.add(entity.getIdentity().getName());
        }
        return names;
    }

    private static java.util.Set<String> referencing(ECoreModel model) {
        java.util.Set<String> referencing = new java.util.HashSet<String>();
        if (model.getEntityModels() == null) return referencing;
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        java.util.List<Entity> entities = new java.util.ArrayList<Entity>();
        for (int i = 0; i < all.size(); i++) entities.add(all.get(i));
        java.util.Map<String, String> roots = new java.util.HashMap<String, String>();
        for (Entity candidate : entities) {
            String name = candidate.getIdentity().getName();
            String root = aggregateOfModel(name, model);
            roots.put(name, root == null ? name : root);
        }
        for (Entity candidate : entities) {
            for (Field f : fieldsOf(candidate)) {
                if (f.hasAnnotation("idof")) {
                    referencing.add(candidate.getIdentity().getName());
                    break;
                }
            }
        }
        // A root whose lines name items checks them too: until nothing changes.
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Entity candidate : entities) {
                String name = candidate.getIdentity().getName();
                if (referencing.contains(name)) continue;
                String root = roots.get(name);
                for (Field f : fieldsOf(candidate)) {
                    if (f.getTypeInfo() == null || !f.getTypeInfo().isList()) continue;
                    String member = f.getTypeInfo().getCoreItemType();
                    if (member == null || !referencing.contains(member)) continue;
                    if (!root.equals(roots.get(member)) && !f.hasAnnotation("cascade")) continue;
                    referencing.add(name);
                    grew = true;
                    break;
                }
            }
        }
        return referencing;
    }

    /**
     * Tells a spec which value types (@embeddable) an entity embeds without
     * requiring them - an order's optional delivery address. Their columns
     * live in that entity's table, so a field the value requires when it is
     * there cannot be NOT NULL in the database.
     */
    static void registerOptionalEmbeddables(EntitySpec spec, ECoreModel model) {
        for (String name : facts(model).optionalEmbeddables) spec.knownOptionalEmbeddable(name);
    }

    /**
     * The same over every model of the run: a value type declared in one
     * domain is embedded in another, and its columns are written on its own
     * class, which the domain declaring it generates. Worked out once per run
     * (the id-reference rewrite does not touch embedded values) and handed to
     * each spec with {@link #knownOptionalEmbeddables}.
     */
    public static java.util.Set<String> optionalEmbeddables(java.util.List<ECoreModel> models) {
        java.util.List<Entity> entities = new java.util.ArrayList<Entity>();
        for (ECoreModel model : models) {
            if (model == null || model.getEntityModels() == null) continue;
            ModelComponentItems<Entity> all = model.getEntityModels().getItems();
            for (int i = 0; i < all.size(); i++) entities.add(all.get(i));
        }
        return optionalAmong(entities);
    }

    /** Tells a spec which value types the run embeds without requiring them. */
    public static void knownOptionalEmbeddables(EntitySpec spec, java.util.Set<String> names) {
        if (names == null) return;
        for (String name : names) spec.knownOptionalEmbeddable(name);
    }

    private static java.util.Set<String> optionalEmbeddables(ECoreModel model) {
        java.util.List<Entity> entities = new java.util.ArrayList<Entity>();
        if (model.getEntityModels() != null) {
            ModelComponentItems<Entity> all = model.getEntityModels().getItems();
            for (int i = 0; i < all.size(); i++) entities.add(all.get(i));
        }
        return optionalAmong(entities);
    }

    /**
     * An embedding is required only when the field says so - "!", @required
     * or @nn, the words FieldSpec.required reads. A value inside a value
     * embedded optionally is optional too, required inside it or not: when the
     * outer one is absent, so are the inner one's columns.
     */
    private static java.util.Set<String> optionalAmong(java.util.List<Entity> entities) {
        java.util.Set<String> optional = new java.util.HashSet<String>();
        java.util.Map<String, Entity> embeddables = new java.util.HashMap<String, Entity>();
        for (Entity candidate : entities) {
            if (candidate.getMetaInfo().hasMetaAnnotation("embeddable")) {
                embeddables.put(candidate.getIdentity().getName(), candidate);
            }
        }
        if (embeddables.isEmpty()) return optional;
        for (Entity candidate : entities) {
            for (Field f : fieldsOf(candidate)) {
                String type = embeddedType(f, embeddables);
                if (type != null && !requiredField(f)) optional.add(type);
            }
        }
        boolean grew = !optional.isEmpty();
        while (grew) {
            grew = false;
            for (String outer : new java.util.ArrayList<String>(optional)) {
                for (Field f : fieldsOf(embeddables.get(outer))) {
                    String type = embeddedType(f, embeddables);
                    if (type != null && optional.add(type)) grew = true;
                }
            }
        }
        return optional;
    }

    /** The value type a single field embeds, or null. */
    private static String embeddedType(Field f, java.util.Map<String, Entity> embeddables) {
        if (f.getTypeInfo() == null || f.getTypeInfo().isList()) return null;
        String type = f.getTypeInfo().getBaseTypeName();
        return type != null && embeddables.containsKey(type) ? type : null;
    }

    private static boolean requiredField(Field f) {
        return f.hasAnnotation("notnull") || f.hasAnnotation("required") || f.hasAnnotation("nn");
    }

    /**
     * An @extend parent declared in another domain: whether its class checks
     * references (and so has requireReferences, which the child inherits) is a
     * fact of that domain's model, not of this one - registerReferencing
     * holds only this model's entities.
     */
    public static void registerParentReferencing(EntitySpec spec, ECoreModel model, java.util.List<ECoreModel> models) {
        String parent = spec.parentName();
        if (parent == null || parent.trim().isEmpty()) return;
        parent = parent.trim();
        if (model.getEntityModels() != null && model.getEntityModels().findByName(parent) != null) return;
        for (ECoreModel other : models) {
            if (other == null || other == model || other.getEntityModels() == null) continue;
            if (other.getEntityModels().findByName(parent) == null) continue;
            if (referencing(other).contains(parent)) spec.knownReferencing(parent);
            return;
        }
    }

    /** Every field of an entity, inherited and derived ones included. */
    private static java.util.Collection<Field> fieldsOf(Entity entity) {
        io.elasticore.base.model.core.ListMap<String, Field> fields = entity.getAllFieldListMap();
        return fields == null ? java.util.Collections.<Field>emptyList() : fields.values();
    }

    /** An annotation's key=value pairs as written, joined: "read=public, ship=admin"; null when it has none. */
    static String keyValues(io.elasticore.base.model.core.Annotation a) {
        java.util.Properties p = a.getProperties();
        if (p == null || p.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (String key : new java.util.TreeSet<String>(p.stringPropertyNames())) {
            if (out.length() > 0) out.append(", ");
            out.append(key).append('=').append(p.getProperty(key));
        }
        return out.toString();
    }

    /** Whether DTOs carry an entity's embedded value objects (v3 and Kotlin; 2.x does not). */
    private static volatile boolean valueObjectsInDto;

    public static void valueObjectsInDto(boolean on) {
        valueObjectsInDto = on;
    }

    public static boolean valueObjectsInDto() {
        return valueObjectsInDto;
    }

    /**
     * Whether a required to-one reference ("post: Post!") is required through
     * its flattened key in an auto-generated DTO (v3, Kotlin, TypeScript).
     * 2.x requires the nested object instead, and the key not at all.
     */
    private static volatile boolean referenceRequiredByKey;

    public static void referenceRequiredByKey(boolean on) {
        referenceRequiredByKey = on;
    }

    public static boolean referenceRequiredByKey() {
        return referenceRequiredByKey;
    }

    /**
     * Whether a DTO field is the flattened key of a required to-one relation:
     * postPostId with @ref(post.postId), where the entity's "post" is required
     * and postId is the key of Post. A @ref to anything else on the relation -
     * its title - is not, and neither is a field already required.
     */
    private static boolean flattensRequiredReference(Field f, Entity entity, ECoreModel model) {
        if (f.hasAnnotation(EntityAnnotation.NOT_NULL)) return false;
        String ref = f.getAnnotationValue("ref");
        if (ref == null) return false;
        int dot = ref.indexOf('.');
        if (dot <= 0 || dot == ref.length() - 1 || ref.indexOf('.', dot + 1) >= 0) return false;
        String relationName = ref.substring(0, dot);
        String keyName = ref.substring(dot + 1);
        // The name flattenIdentity gives it: a field the DSL declared under
        // another name is the DSL's to make required.
        if (!f.getName().equals(relationName + io.elasticore.base.util.StringUtils.capitalize(keyName))) {
            return false;
        }

        io.elasticore.base.model.core.ListMap<String, Field> entityFields = entity.getAllFieldListMap();
        Field relation = entityFields == null ? null : entityFields.get(relationName);
        if (relation == null || relation.getTypeInfo().isList()) return false;
        if (!relation.hasAnnotation(EntityAnnotation.NOT_NULL)) return false;

        String targetName = relation.getTypeInfo().getDefaultTypeName();
        Entity target = model.getEntityModels().findByName(targetName);
        if (target == null) target = globalEntity(targetName);
        if (target == null || target.getMetaInfo().hasMetaAnnotation("embeddable")) return false;
        return isKeyOf(target, keyName, model);
    }

    /** Whether a field of this name is an @id of the entity or of an entity it extends. */
    private static boolean isKeyOf(Entity entity, String fieldName, ECoreModel model) {
        java.util.Set<String> seen = new java.util.HashSet<String>();
        while (entity != null && seen.add(entity.getIdentity().getName())) {
            ModelComponentItems<Field> fields = entity.getItems();
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                if (field.getName().equals(fieldName) && field.hasAnnotation(EntityAnnotation.META_ID)) return true;
            }
            String parentNm = entity.getMetaInfo().getMetaAnnotationValue(EntityAnnotation.META_EXTEND);
            if (parentNm == null) return false;
            Entity parent = model.getEntityModels().findByName(parentNm);
            entity = parent != null ? parent : globalEntity(parentNm);
        }
        return false;
    }

    /**
     * The field with @notnull added, as a copy: the flattened key is shared
     * with the entity's search DTO, which must not become required with it.
     */
    private static Field requiredCopyOf(Field f, io.elasticore.base.model.dto.DataTransfer dto) {
        java.util.Map<String, io.elasticore.base.model.core.Annotation> anno =
                new java.util.LinkedHashMap<String, io.elasticore.base.model.core.Annotation>();
        if (f.getAnnotationMap() != null) anno.putAll(f.getAnnotationMap());
        put(anno, io.elasticore.base.model.core.Annotation.create(EntityAnnotation.NOT_NULL[0]));
        return Field.builder()
                .name(f.getName())
                .parentMetaInfo(f.getParentMetaInfo() != null ? f.getParentMetaInfo() : dto.getMetaInfo())
                .type(f.getTypeInfo().getInitTypeInfo())
                .description(f.getDescription())
                .annotationMap(anno)
                .build();
    }

    /** Whether a root is split for its behaviour without saying @behavior (the DDD bundles). */
    private static volatile boolean rootsSplitByDefault;

    public static void rootsSplitByDefault(boolean on) {
        rootsSplitByDefault = on;
    }

    /** Whether the server's controllers drop update where a root has nothing editable (ddd-command). */
    private static volatile boolean commandOnlyRoots;

    public static void commandOnlyRoots(boolean on) {
        commandOnlyRoots = on;
    }

    public static boolean commandOnlyRoots() {
        return commandOnlyRoots;
    }

    /** Whether a TypeScript run's server renders the 2.x-compatible skeletons (no v3, no Kotlin). */
    private static volatile boolean compatibleServer;

    public static void compatibleServer(boolean on) {
        compatibleServer = on;
    }

    public static boolean compatibleServer() {
        return compatibleServer;
    }

    private static boolean isEmbeddable(String typeName, ECoreModel model) {
        if (typeName == null) return false;
        Entity e = model.getEntityModels().findByName(typeName);
        if (e == null) e = globalEntity(typeName);
        return e != null && e.getMetaInfo().hasMetaAnnotation("embeddable");
    }

    private static boolean namesAnEntity(Field f, ECoreModel model) {
        if (f.getTypeInfo().isBaseType()) return false;
        for (String alias : new String[]{"dtotype", "dtype", "dto"}) {
            String declared = f.getAnnotationValue(alias);
            if (declared != null && !declared.isEmpty()) return false;
        }
        // A list of entities is a relation too: 2.x resolves the element type
        // before deciding (isEntityReturnType).
        String typeName = f.getTypeInfo().isList()
                ? f.getTypeInfo().getCoreItemType()
                : f.getTypeInfo().getDefaultTypeName();
        if (model.getEntityModels().findByName(typeName) != null) return true;
        return globalEntity(typeName) != null;
    }

    /** An entity by name anywhere in the loaded context, as 2.x resolves it. */
    private static Entity globalEntity(String typeName) {
        if (typeName == null) return null;
        try {
            io.elasticore.base.ECoreModelContext ctx =
                    io.elasticore.base.model.core.BaseECoreModelContext.getContext();
            return ctx == null ? null : ctx.findModelComponent(typeName, Entity.class);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Copies the entity's id onto the DTO as "<relation><Id>", searchable by equality. */
    private static void flattenIdentity(io.elasticore.base.model.dto.DataTransfer dto,
                                        Entity entity, String baseFieldNm, ECoreModel model) {
        String parentNm = entity.getMetaInfo().getMetaAnnotationValue(EntityAnnotation.META_EXTEND);
        if (parentNm != null) {
            Entity parent = model.getEntityModels().findByName(parentNm);
            if (parent == null) parent = globalEntity(parentNm);
            if (parent != null) flattenIdentity(dto, parent, baseFieldNm, model);
        }

        ModelComponentItems<Field> fields = entity.getItems();
        int count = fields.size();
        for (int i = 0; i < count; i++) {
            Field entityField = fields.get(i);
            if (!entityField.hasAnnotation(EntityAnnotation.META_ID)) continue;

            String newFieldNm = baseFieldNm
                    + io.elasticore.base.util.StringUtils.capitalize(entityField.getName());
            if (dto.getAllFieldListMap().containsKey(newFieldNm)) continue;

            java.util.Map<String, io.elasticore.base.model.core.Annotation> anno =
                    new java.util.HashMap<String, io.elasticore.base.model.core.Annotation>();
            put(anno, io.elasticore.base.model.core.Annotation.create(
                    "ref", baseFieldNm + "." + entityField.getName()));
            put(anno, io.elasticore.base.model.core.Annotation.create(
                    io.elasticore.base.model.dto.DataTransferAnnotation.META_SEARCHABLE, "eq"));

            dto.addField(Field.builder()
                    .name(newFieldNm)
                    .parentMetaInfo(dto.getMetaInfo())
                    .type(entityField.getTypeInfo().getDefaultTypeName())
                    .annotationMap(anno)
                    .build());
        }
    }

    private static void put(java.util.Map<String, io.elasticore.base.model.core.Annotation> map,
                            io.elasticore.base.model.core.Annotation a) {
        map.put(a.getName(), a);
    }

    public static void registerEnums(EntitySpec spec, ECoreModel model) {
        registerEnums(spec, model, false);
    }

    /**
     * @param withConstants also carry each enum's constants, which a field of
     *                      that type documents itself with
     */
    public static void registerEnums(EntitySpec spec, ECoreModel model, boolean withConstants) {
        if (model.getEnumModels() == null) return;
        ModelComponentItems<io.elasticore.base.model.enums.EnumModel> enums =
                model.getEnumModels().getItems();
        for (int i = 0; i < enums.size(); i++) {
            io.elasticore.base.model.enums.EnumModel em = enums.get(i);
            String enumName = em.getIdentity().getName();
            boolean hasDb = em.getMetaInfo().hasMetaAnnotation("db");
            String length = null;
            if (hasDb) {
                String dbFieldNm = em.getMetaInfo().getMetaAnnotationValue("db");
                if (dbFieldNm != null) {
                    Field enumField = em.getFieldItems().findByName(dbFieldNm);
                    if (enumField != null) {
                        length = enumField.getAnnotationValue("length");
                    }
                }
            }
            if (withConstants) {
                spec.knownEnum(fromEnum(em, model), length, hasDb);
            } else {
                spec.knownEnum(enumName, length, hasDb);
            }
            // The names alone, for what checks a constant is real (a lifecycle's states).
            java.util.List<String> names = new java.util.ArrayList<String>();
            if (em.getEnumConstantItems() != null) {
                for (io.elasticore.base.model.enums.EnumConstant c : em.getEnumConstantItems().getItemList()) {
                    names.add(c.getIdentity().getName());
                }
            }
            if (!names.isEmpty()) spec.enumConstantNames(enumName, names);
        }
    }

    /**
     * Registers every loaded domain's @embeddable entities, because a value
     * type can be declared in one domain and embedded in another.
     */
    public static void registerAllEmbeddables(EntitySpec spec, java.util.List<ECoreModel> models) {
        for (ECoreModel m : models) {
            registerEmbeddables(spec, m);
        }
    }

    /** Registers @embeddable entities so @Embedded / @AttributeOverrides resolve. */
    public static void registerEmbeddables(EntitySpec spec, ECoreModel model) {
        if (model.getEntityModels() == null) return;
        ModelComponentItems<Entity> all = model.getEntityModels().getItems();
        for (int i = 0; i < all.size(); i++) {
            Entity candidate = all.get(i);
            if (!candidate.getMetaInfo().hasMetaAnnotation("embeddable")) continue;

            java.util.List<String> names = new java.util.ArrayList<String>();
            ModelComponentItems<Field> fields = candidate.getItems();
            for (int j = 0; j < fields.size(); j++) {
                names.add(fields.get(j).getName());
            }
            spec.knownEmbeddable(candidate.getIdentity().getName(), names);
        }
    }

    static String capitalize(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * The search DTO of an entity: the conventionally named one, or else the
     * first DTO declared @searchable(entity=...) of it, as 2.x finds it.
     */
    static io.elasticore.base.model.dto.DataTransfer searchDtoOf(Entity entity, ECoreModel model) {
        if (model.getDataTransferModels() == null) return null;
        String name = entity.getIdentity().getName();
        io.elasticore.base.model.dto.DataTransfer byName = model.getDataTransferModels().getItems()
                .findByName(String.format("%sSrchDTO", name));
        if (byName != null) return byName;
        java.util.List<io.elasticore.base.model.relation.ModelRelationship> rels =
                io.elasticore.base.model.core.RelationshipManager.getInstance(entity.getIdentity().getDomainId())
                        .findByToNameAndType(name, io.elasticore.base.model.relation.RelationType.SEARCHABLE);
        for (io.elasticore.base.model.relation.ModelRelationship r : rels) {
            io.elasticore.base.model.dto.DataTransfer d = model.getDataTransferModels().getItems().findByName(r.getFromName());
            if (d != null) return d;
        }
        return null;
    }
}
