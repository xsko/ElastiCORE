package io.elasticore.base.model.pub.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of a whole domain, for artifacts that exist once per domain
 * rather than once per entity - the repository helper, and the mapper class
 * that wraps the per-entity mapping sections.
 *
 * Entities reuse {@link EntitySpec}, so rules filter them with the same
 * accessors they already use elsewhere.
 */
public final class DomainSpec {

    private final String name;
    private final String className;

    private final List<EntitySpec> entities = new ArrayList<EntitySpec>();

    /** Entities of other domains this one reaches into: name -> repository package. */
    private boolean hasEnums;

    /** Whether the domain declares any enumeration of its own. */
    public DomainSpec hasEnums(boolean declared) {
        this.hasEnums = declared;
        return this;
    }

    public boolean hasEnums() {
        return hasEnums;
    }

    private final Map<String, String> externalEntities = new LinkedHashMap<String, String>();

    private final Map<String, String> packages = new LinkedHashMap<String, String>();

    private final Map<String, String> ports = new LinkedHashMap<String, String>();

    /**
     * A port the DSL declares, and the package it was placed in - for a
     * project's own rule set that names the ports of a domain.
     */
    public DomainSpec port(String name, String packageName) {
        ports.put(name, packageName);
        return this;
    }

    /** Whether the DSL declares a port of this name. */
    public boolean isPort(String name) {
        return ports.containsKey(name);
    }

    /** Where that port was placed, or null when there is no such port. */
    public String portPackage(String name) {
        return ports.get(name);
    }

    public Map<String, String> ports() {
        return java.util.Collections.unmodifiableMap(ports);
    }

    private SourceCode code;

    /**
     * @param name      blueprint directory name, e.g. "content"
     * @param className the same name in the form used for class names
     */
    public DomainSpec(String name, String className) {
        this.name = name;
        this.className = className;
    }

    public String getName() {
        return name;
    }

    /** Domain name in the form 2.x builds class names from. */
    public String className() {
        return className;
    }

    public DomainSpec entity(EntitySpec entity) {
        entities.add(entity);
        return this;
    }

    public List<EntitySpec> entities() {
        return entities;
    }

    public DomainSpec externalEntity(String entityName, String repositoryPackage) {
        externalEntities.put(entityName, repositoryPackage);
        return this;
    }

    public Map<String, String> externalEntities() {
        return externalEntities;
    }

    private String jsonViewConfig = "false";

    /** env.yml config.jsonview: "false", "true" or "auto". */
    public DomainSpec jsonViewConfig(String value) {
        this.jsonViewConfig = value == null ? "false" : value;
        return this;
    }

    public String jsonViewConfig() {
        return jsonViewConfig;
    }

    /**
     * Packages the mapping sections reach into. 2.x collects these in a
     * HashSet, so its import order varies between runs; declaration order is
     * kept here because diff-based regeneration needs a stable file.
     */
    private final List<String> mapperImports = new ArrayList<String>();

    public DomainSpec mapperImport(String packageName) {
        if (!mapperImports.contains(packageName)) mapperImports.add(packageName);
        return this;
    }

    public List<String> mapperImports() {
        return mapperImports;
    }

    /** Per-entity mapping sections, in the order the mapper declares them. */
    private final List<String> mapperSections = new ArrayList<String>();

    public DomainSpec mapperSection(String section) {
        mapperSections.add(section);
        return this;
    }

    public List<String> mapperSections() {
        return mapperSections;
    }

    /** Types the Q class names, which its imports have to reach. */
    private final List<String> qImports = new ArrayList<String>();

    public DomainSpec qImport(String typeName) {
        if (!qImports.contains(typeName)) qImports.add(typeName);
        return this;
    }

    public List<String> qImports() {
        return qImports;
    }

    /**
     * Package of the domain that owns an entity, when it is not this one.
     * A relation into another domain is reached through that domain's Q,
     * since an unqualified name would resolve to this one.
     */
    private final Map<String, String> foreignEntityPackages = new LinkedHashMap<String, String>();

    public DomainSpec foreignEntity(String entityName, String packageName) {
        foreignEntityPackages.put(entityName, packageName);
        return this;
    }

    public String qPackageOf(String entityName) {
        return foreignEntityPackages.get(entityName);
    }

    private String customPageableInterface;
    private String customSortableInterface;

    /** env.yml config.interface.pageable: a paging interface the project owns. */
    public DomainSpec customPageableInterface(String name) {
        this.customPageableInterface = name;
        return this;
    }

    public String customPageableInterface() {
        return customPageableInterface;
    }

    /** env.yml config.interface.sortable: a sort interface the project owns. */
    public DomainSpec customSortableInterface(String name) {
        this.customSortableInterface = name;
        return this;
    }

    public String customSortableInterface() {
        return customSortableInterface;
    }

    public DomainSpec pkg(String artifact, String packageName) {
        packages.put(artifact, packageName);
        return this;
    }

    public String pkg(String artifact) {
        return packages.get(artifact);
    }

    private final Map<String, String> namespaces = new java.util.HashMap<String, String>();

    public DomainSpec namespaces(Map<String, String> declared) {
        if (declared != null) namespaces.putAll(declared);
        return this;
    }

    /**
     * The package of any namespace key the domain's env.yml has - the ones
     * namespace: base expands, and a project's own. Null when it has none.
     */
    public String namespace(String key) {
        String v = namespaces.get(key);
        return v != null ? v : packages.get(key);
    }

    /** Domain-level settings from env.yml (config section) a rule set may read: outbox, table.prefix. */
    private final Map<String, String> config = new java.util.HashMap<String, String>();

    public DomainSpec config(String key, String value) {
        if (value != null) config.put(key, value);
        return this;
    }

    public String config(String key) {
        String v = config.get(key);
        if (v == null && configSource != null) v = configSource.get(key);
        return v;
    }

    private ConfigSource configSource;

    /** Where config(key) reads the rest of the domain's env.yml; set by the generator. */
    public DomainSpec configSource(ConfigSource source) {
        this.configSource = source;
        return this;
    }

    /** Whether a boolean setting is on ("true", ignoring case). */
    public boolean isOn(String key) {
        return "true".equalsIgnoreCase(config(key));
    }

    void attach(SourceCode code) {
        this.code = code;
    }

    SourceCode code() {
        return code;
    }
}
