package io.elasticore.base.extract;

import io.elasticore.base.ECoreModelContext;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.SourceFileAccessFactory;
import io.elasticore.base.model.ConstantParam;
import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.core.BaseECoreModelContext;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.dto.DataTransferModels;
import io.elasticore.base.model.entity.BaseFieldType;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.EntityModels;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.entity.TypeInfo;
import io.elasticore.base.model.enums.EnumModel;
import io.elasticore.base.model.enums.EnumModels;
import io.elasticore.base.model.loader.FileBasedModelLoader;
import io.elasticore.base.model.pub.CodePublishManager;
import io.elasticore.base.model.pub.schema.SchemaCodePublisher;
import io.elasticore.base.util.ConsoleLog;
import io.elasticore.base.util.FileOrder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ModelExtractor {


    private final static String SRC_PATH = "src/main/java";


    private String modelResourcePath;

    private static void log(String msg) {
        ConsoleLog.print(msg);
    }

    /**
     * Deploys the guides beside the model.
     *
     * Delegates: the copy used to live here, which meant a project
     * generating through the rule engine never got one. See GuideDeployer.
     */
    private void copyGuideFilesToBlueprintRoot() {
        io.elasticore.base.util.GuideDeployer.deployTo(this.modelResourcePath, getRootDir());
    }


    /**
     * Validates field type references and detects duplicate type definitions across all loaded domains.
     * Runs after every domain (across all modules) is loaded so cross-domain shared types resolve correctly.
     *
     * Two checks performed:
     *   1. Duplicate detection: Entity/DTO/Enum names defined in 2+ domains -> DSL_WARN
     *   2. Cross-domain type reference: each Entity/DTO field with a non-primitive type that does not
     *      exist as an Entity, DTO or Enum in ANY domain -> DSL_WARN
     */
    public static void validateGlobalTypeReferences(List<ECoreModelContext> allContexts) {
        if (allContexts == null || allContexts.isEmpty()) return;

        // Build global type registry: type name -> ordered list of domains where defined
        Map<String, List<String>> typeRegistry = new LinkedHashMap<>();

        for (ECoreModelContext ctx : allContexts) {
            String[] domainNames = ctx.getInternalDomainNames();
            if (domainNames == null) continue;
            for (String domainName : domainNames) {
                ModelDomain domain = ctx.getDomain(domainName);
                if (domain == null) continue;
                ECoreModel model = domain.getModel();
                if (model == null) continue;

                EntityModels ems = model.getEntityModels();
                if (ems != null) {
                    ModelComponentItems<Entity> items = ems.getItems();
                    int n = items.size();
                    for (int i = 0; i < n; i++) {
                        Entity e = items.get(i);
                        if (e == null) continue;
                        registerType(typeRegistry, e.getIdentity().getName(), domainName);
                    }
                }

                DataTransferModels dms = model.getDataTransferModels();
                if (dms != null) {
                    ModelComponentItems<DataTransfer> items = dms.getItems();
                    int n = items.size();
                    for (int i = 0; i < n; i++) {
                        DataTransfer d = items.get(i);
                        if (d == null) continue;
                        registerType(typeRegistry, d.getIdentity().getName(), domainName);
                    }
                }

                EnumModels enms = model.getEnumModels();
                if (enms != null) {
                    ModelComponentItems<EnumModel> items = enms.getItems();
                    int n = items.size();
                    for (int i = 0; i < n; i++) {
                        EnumModel em = items.get(i);
                        if (em == null) continue;
                        registerType(typeRegistry, em.getIdentity().getName(), domainName);
                    }
                }
            }
        }

        // Detect duplicates: same type name in multiple domains
        for (Map.Entry<String, List<String>> entry : typeRegistry.entrySet()) {
            List<String> domains = entry.getValue();
            if (domains.size() > 1) {
                // Anchor warning to the first domain so it gets routed correctly when printed
                ConsoleLog.storeLog("DSL_WARN",
                        String.format("[%s] duplicate type '%s' is defined in multiple domains: %s",
                                domains.get(0), entry.getKey(), String.join(", ", domains)));
            }
        }

        // Validate field type references using the global registry
        for (ECoreModelContext ctx : allContexts) {
            String[] domainNames = ctx.getInternalDomainNames();
            if (domainNames == null) continue;
            for (String domainName : domainNames) {
                ModelDomain domain = ctx.getDomain(domainName);
                if (domain == null) continue;
                ECoreModel model = domain.getModel();
                if (model == null) continue;

                EntityModels ems = model.getEntityModels();
                if (ems != null) {
                    ModelComponentItems<Entity> items = ems.getItems();
                    int n = items.size();
                    for (int i = 0; i < n; i++) {
                        Entity e = items.get(i);
                        if (e == null) continue;
                        validateFieldTypes(e.getIdentity().getName(), e.getItems(), domainName, typeRegistry);
                    }
                }

                DataTransferModels dms = model.getDataTransferModels();
                if (dms != null) {
                    ModelComponentItems<DataTransfer> items = dms.getItems();
                    int n = items.size();
                    for (int i = 0; i < n; i++) {
                        DataTransfer d = items.get(i);
                        if (d == null) continue;
                        validateFieldTypes(d.getIdentity().getName(), d.getItems(), domainName, typeRegistry);
                    }
                }
            }
        }
    }

    private static void registerType(Map<String, List<String>> registry, String typeName, String domainName) {
        if (typeName == null || typeName.isEmpty()) return;
        List<String> domains = registry.get(typeName);
        if (domains == null) {
            domains = new ArrayList<>();
            registry.put(typeName, domains);
        }
        if (!domains.contains(domainName)) {
            domains.add(domainName);
        }
    }

    private static void validateFieldTypes(String componentName, ModelComponentItems<Field> fieldItems,
                                           String domainName, Map<String, List<String>> typeRegistry) {
        if (fieldItems == null) return;
        int size = fieldItems.size();
        for (int i = 0; i < size; i++) {
            Field field = fieldItems.get(i);
            if (field == null) continue;
            // Skip disabled fields ("--" -> "undefined @disable")
            if (field.hasAnnotation("disable")) continue;

            TypeInfo ti = field.getTypeInfo();
            if (ti == null) continue;

            String typeToCheck = null;
            if (ti.getBaseFieldType() == BaseFieldType.UNKNOWN) {
                typeToCheck = ti.getBaseTypeName();
            } else if (ti.isList() && ti.getTypeParameterName() != null && !ti.isTypeParameterBaseType()) {
                typeToCheck = ti.getTypeParameterName();
            }

            if (typeToCheck == null) continue;
            // Skip fully qualified class names (e.g., "java.util.HashMap")
            if (typeToCheck.contains(".")) continue;
            // Skip cross-domain explicit references (e.g., "otherdomain:SomeEntity")
            if (typeToCheck.contains(":")) continue;

            if (!typeRegistry.containsKey(typeToCheck)) {
                ConsoleLog.storeLog("DSL_WARN",
                        String.format("[%s] %s.%s: unresolved type '%s' - no matching Entity, DTO, or Enum found in any domain",
                                domainName, componentName, field.getName(), typeToCheck));
            }
        }
    }


    public ModelExtractor() {
        this.modelResourcePath = getRootDir() + "/" + ConstantParam.PROPERTY_ELCORE_HOME;
    }

    public ModelExtractor(String modelResourcePath) {
        this.modelResourcePath = modelResourcePath;
    }


    private List<String> findTemplateFilePath() throws FileNotFoundException {

        // Note: the -Pdomain=<name> filter is intentionally NOT applied here.
        // Cross-domain references (@extend, @audited, shared DTO templates, etc.) require
        // every domain's DSL to be fully loaded. Filtering is applied later:
        //   (1) in extract()/extractMultiModule() to skip publishing non-targeted domains
        //   (2) in YamlFileMainModelLoader to skip remote-URL refresh for non-targeted domains

        String checkDir = this.modelResourcePath;

        log("Base dir: " + checkDir);

        File f = new File(checkDir);

        if (!f.exists()) {
            //throw new FileNotFoundException("Template directory not found:"+checkDir);
            return null;
        }

        List<String> dirList = new ArrayList<>();

        File[] files = FileOrder.list(f);
        if (files != null) {
            for (File child : files) {
                if (child.isDirectory()) {
                    String envFilePath = child.getAbsolutePath() + "/env.yml";
                    ConsoleLog.storeLog("CHECK_ENV", envFilePath);
                    File envFile = new File(envFilePath);
                    if (envFile.exists()) {
                        dirList.add(child.getAbsolutePath());
                    }
                }
            }
        }
        ConsoleLog.print("[INFO] Environment files:");
        ConsoleLog.print("--------------------------------------");
        ConsoleLog.printStoredInfoLog("CHECK_ENV", "    ");
        return dirList;
    }

    /**
     * Returns the -Pdomain filter value (trimmed, non-empty) or null if the filter is inactive.
     * Shared by extract() and extractMultiModule() so the publish-stage gate is consistent.
     */
    private static String getDomainFilter() {
        String spec = System.getProperty("elasticore.domain");
        if (spec == null) return null;
        spec = spec.trim();
        return spec.isEmpty() ? null : spec;
    }

    /**
     * Returns true when {@code spec} is a comma-separated filter value that includes {@code domainName}.
     * {@code spec} is the raw value from {@link #getDomainFilter()}; pass null when the filter is off.
     */
    public static boolean isDomainIncluded(String spec, String domainName) {
        if (spec == null) return true;
        String needle = "," + spec + ",";
        return needle.indexOf("," + domainName + ",") >= 0;
    }

    private String getRootDir() {
        String basePath = System.getProperty("elasticore.base.path");
        if (basePath == null) {
            basePath = System.getProperty("user.dir");
        }

        return basePath;
    }

    /**
     * The module that owns the model - the directory holding src/main/resources/blueprint.
     *
     * Generated code belongs beside the DSL that describes it. The root directory cannot
     * say where that is: the annotation processor never sets elasticore.base.path, so the
     * root falls back to the JVM working directory, which under Gradle is the root project
     * and not the module being compiled. A multi-module build therefore wrote every
     * module's sources into the repository root. The blueprint path is known on both entry
     * paths, so the module is derived from it, and the root remains the answer only when
     * the blueprint sits somewhere this cannot read.
     */
    private String getModuleRootDir() {
        return moduleRootOf(this.modelResourcePath, getRootDir());
    }

    /**
     * The module a blueprint path names, or the fallback when it names none.
     *
     * Separated from the lookup so a test can ask it directly. What went
     * wrong here was not subtle once seen - generated code landing in the
     * repository root of a multi-module build - and it still survived every
     * gate this project had, because the golden-tree check compares what a
     * file contains and never asks where it was put. A pure function is a
     * thing a test can pin down; a path read off system properties is not.
     */
    static String moduleRootOf(String blueprintPath, String fallback) {
        if (blueprintPath != null) {
            String suffix = "/" + ConstantParam.PROPERTY_ELCORE_HOME;
            String normalized = blueprintPath.replace('\\', '/');
            if (normalized.endsWith(suffix)) {
                return blueprintPath.substring(0, blueprintPath.length() - suffix.length());
            }
        }
        return fallback;
    }

    public void deleteGenSource(String baseDir) throws FileNotFoundException {
        String rootDir = getRootDir();

        List<String> dirList = findTemplateFilePath();

        if (dirList == null || dirList.size() == 0) {
            return;
        }

        CodePublishManager codePublishManager = CodePublishManager.getInstance();

        for (String path : dirList) {
            FileBasedModelLoader loader = FileBasedModelLoader.newInstance();
            loader.setTemplateFileDirPath(path);

            ECoreModelContext ctx = BaseECoreModelContext.getContext(loader);

            if(ctx!=null)
                codePublishManager.deleteGenSource(ctx ,baseDir);
        }

    }


    /**
     * Multi-module extraction: loads all blueprints across modules first,
     * then publishes code for each domain with the correct module's src path.
     * Cross-module model sharing is automatic via STATIC_DOMAIN_MAP.
     */
    public void extractMultiModule(String modulePaths) throws FileNotFoundException {
        String[] modules = modulePaths.split(",");

        CodePublishManager codePublishManager = CodePublishManager.getInstance();
        SchemaCodePublisher schemaPublisher = new SchemaCodePublisher();

        // Build module alias -> absolute path registry for cross-module namespace redirect
        Map<String, String> moduleRootMap = new LinkedHashMap<>();
        for (String mp : modules) {
            mp = mp.trim();
            File moduleDir = new File(mp);
            String moduleName = moduleDir.getName();
            try {
                moduleRootMap.put(moduleName, moduleDir.getCanonicalPath());
            } catch (Exception e) {
                moduleRootMap.put(moduleName, moduleDir.getAbsolutePath());
            }
        }
        codePublishManager.setModuleRootMap(moduleRootMap);

        // Discover all domain blueprint paths and map to module src paths.
        // Note: the -Pdomain filter is NOT applied here --every domain must be loaded so
        // cross-module / cross-domain references (e.g. @extend from a common domain) resolve.
        // The filter is enforced in Phase 2 below to restrict publishing, and in
        // YamlFileMainModelLoader to suppress remote-URL refresh for non-targeted domains.
        Map<String, String> domainSrcPathMap = new LinkedHashMap<>();

        for (String modulePath : modules) {
            modulePath = modulePath.trim();
            String blueprintDir = modulePath + "/" + ConstantParam.PROPERTY_ELCORE_HOME;
            String srcDir = modulePath; // module root (factory adds src/main/java internally)

            File bpDir = new File(blueprintDir);
            if (!bpDir.exists()) continue;

            // Copy guide files per module (skip in validation mode)
            if (!ConsoleLog.isValidationMode()) {
                this.modelResourcePath = blueprintDir;
                copyGuideFilesToBlueprintRoot();
            }

            File[] files = FileOrder.list(bpDir);
            if (files == null) continue;

            for (File child : files) {
                if (child.isDirectory()) {
                    File envFile = new File(child.getAbsolutePath() + "/env.yml");
                    if (envFile.exists()) {
                        domainSrcPathMap.put(child.getAbsolutePath(), srcDir);
                        ConsoleLog.storeLog("CHECK_ENV", envFile.getAbsolutePath());
                    }
                }
            }
        }

        // As in a single module: a check that found no model compared nothing.
        if (domainSrcPathMap.isEmpty() && ConsoleLog.isValidationMode()) {
            ConsoleLog.printWarn("Not found model DSL file in " + modulePaths);
            ConsoleLog.storeLog("VALIDATION_NO_MODEL", modulePaths);
            return;
        }

        ConsoleLog.print("[INFO] Multi-module mode: " + modules.length + " module(s)");
        ConsoleLog.print("[INFO] Environment files:");
        ConsoleLog.print("--------------------------------------");
        ConsoleLog.printStoredInfoLog("CHECK_ENV", "    ");

        // Phase 1: Load all models (shared via STATIC_DOMAIN_MAP)
        List<ECoreModelContext> allContexts = new ArrayList<>();
        List<String> srcPaths = new ArrayList<>();
        List<String> domainNamesForContexts = new ArrayList<>();

        for (Map.Entry<String, String> entry : domainSrcPathMap.entrySet()) {
            // Set base.path per domain so template paths resolve correctly
            System.setProperty("elasticore.base.path", entry.getValue());

            FileBasedModelLoader loader = FileBasedModelLoader.newInstance();
            loader.setTemplateFileDirPath(entry.getKey());

            ECoreModelContext ctx = BaseECoreModelContext.getContext(loader);
            if (ctx != null) {
                allContexts.add(ctx);
                srcPaths.add(entry.getValue());
                domainNamesForContexts.add(new File(entry.getKey()).getName());
            }
        }

        // Phase 1.5: Validate type references and detect duplicates across all loaded domains
        validateGlobalTypeReferences(allContexts);
        io.elasticore.base.model.pub.rules.RulesConfig.settleSpringBoot(allContexts);

        // Collect and generate schema (skip in validation mode).
        // Because every domain is loaded regardless of -Pdomain, schema.yml reflects the
        // full project --safe to regenerate.
        if (!ConsoleLog.isValidationMode()) {
            for (ECoreModelContext ctx : allContexts) {
                String[] domainNames = ctx.getInternalDomainNames();
                for (String domainName : domainNames) {
                    ModelDomain domain = ctx.getDomain(domainName);
                    if (domain != null) {
                        try {
                            schemaPublisher.publish(ctx, domain);
                        } catch (Exception e) {
                            ConsoleLog.printWarn("Failed to collect schema: " + domainName + " - " + e.getMessage());
                        }
                    }
                }
            }
            try {
                schemaPublisher.generateSchemaFile(modules[0].trim());
            } catch (Exception e) {
                ConsoleLog.printWarn("Failed to generate schema.yml: " + e.getMessage());
            }
        }

        // Phase 2: Publish code --filtered by -Pdomain when active.
        String domainFilter = getDomainFilter();
        int publishedCount = 0;
        for (int i = 0; i < allContexts.size(); i++) {
            String domainName = domainNamesForContexts.get(i);
            if (!isDomainIncluded(domainFilter, domainName)) continue;

            String modulePath = srcPaths.get(i);
            // Set base.path so CodeTemplate resolves _tmpl paths from the correct module
            System.setProperty("elasticore.base.path", modulePath);
            codePublishManager.setSourceBasePath(modulePath);
            codePublishManager.publish(allContexts.get(i));
            publishedCount++;
        }

        if (domainFilter != null && publishedCount == 0) {
            ConsoleLog.printWarn("[DomainFilter] -Pdomain=" + domainFilter
                    + " matched no domains. Available: " + domainNamesForContexts);
        }
    }


    public void extract(SourceFileAccessFactory sourceFileAccessFactory) throws FileNotFoundException {

        String rootDir = getRootDir();
        // Where the code goes. Not rootDir - see getModuleRootDir().
        String moduleRootDir = getModuleRootDir();

        List<String> dirList = findTemplateFilePath();

        if (dirList == null || dirList.size() == 0) {
            ConsoleLog.printWarn("Not found model DSL file in " + rootDir);

            // A check with no model has compared nothing. It used to go on to
            // the setup wizard and then pass; it is said instead, and main
            // fails the run.
            if (ConsoleLog.isValidationMode()) {
                ConsoleLog.storeLog("VALIDATION_NO_MODEL", rootDir);
                return;
            }

            // Check if this is an uninitialized ElastiCORE project and offer initialization
            if (!io.elasticore.base.util.InitializationHelper.isInitialized()) {
                ConsoleLog.printInfo("ElastiCORE environment not found. Starting initialization...");
                io.elasticore.base.util.InitializationHelper helper = new io.elasticore.base.util.InitializationHelper();
                boolean initialized = helper.startInitialization();
                
                if (initialized) {
                    ConsoleLog.printInfo("Domain initialization completed successfully!");
                    ConsoleLog.printInfo("Please run './gradlew elcore' again to start model generation.");
                    return; // Exit after successful initialization
                } else {
                    return; // User cancelled initialization or non-interactive mode
                }
            } else {
                return;
            }
        }


        if (sourceFileAccessFactory == null) {
            sourceFileAccessFactory = new FileBasedSourceFileAccessFactory(rootDir + "/" + ConstantParam.PROPERTY_JAVA_SRC_HOME);
        }

        CodePublishManager codePublishManager = CodePublishManager.getInstance();
        SchemaCodePublisher schemaPublisher = new SchemaCodePublisher();

        List<ECoreModelContext> contextList = new ArrayList<>();
        List<String> contextDomainNames = new ArrayList<>();

        // Copy guide files to blueprint root directory (only once, skip in validation mode)
        if (!ConsoleLog.isValidationMode()) {
            copyGuideFilesToBlueprintRoot();
        }

        for (String path : dirList) {
            FileBasedModelLoader loader = FileBasedModelLoader.newInstance();
            loader.setTemplateFileDirPath(path);

            ECoreModelContext ctx = BaseECoreModelContext.getContext(loader);
            if(ctx!=null) {
                contextList.add(ctx);
                contextDomainNames.add(new File(path).getName());
            }
        }

        // Validate type references and detect duplicates across all loaded domains
        validateGlobalTypeReferences(contextList);
        io.elasticore.base.model.pub.rules.RulesConfig.settleSpringBoot(contextList);

        // Collect schema information from all domains. All DSL is loaded regardless of
        // -Pdomain filter, so schema aggregation always sees the full model.
        for(ECoreModelContext ctx: contextList) {
            String[] domainNames = ctx.getInternalDomainNames();
            for (String domainName : domainNames) {
                ModelDomain domain = ctx.getDomain(domainName);
                if (domain != null) {
                    try {
                        schemaPublisher.publish(ctx, domain);
                    } catch (Exception e) {
                        ConsoleLog.printWarn("Failed to collect schema from domain: " + domainName + " - " + e.getMessage());
                    }
                }
            }
        }

        // Generate unified schema.yml file (skip in validation mode).
        // Because every domain is loaded (cross-references resolve), the schema file reflects
        // the full project even when -Pdomain restricts publishing.
        if (!ConsoleLog.isValidationMode()) {
            try {
                // Beside the blueprint that was loaded, not under the root directory:
                // during annotation processing the root falls back to the JVM working
                // directory, which is the Gradle root project rather than this module.
                schemaPublisher.generateSchemaFileInBlueprintDir(this.modelResourcePath);

                // The Ossie export answers to the same opt-in whichever door
                // the project came in through - entry-point parity is the
                // rule the schema work above just re-established.
                java.util.List<io.elasticore.base.model.ECoreModel> loadedModels =
                        new ArrayList<>();
                for (ECoreModelContext each : contextList) {
                    String[] names = each.getInternalDomainNames();
                    if (names == null) continue;
                    for (String dn : names) {
                        ModelDomain md = each.getDomain(dn);
                        if (md != null && md.getModel() != null) loadedModels.add(md.getModel());
                    }
                }
                if (io.elasticore.base.model.pub.ossie.OssieSemanticModelExporter.requested(loadedModels)) {
                    io.elasticore.base.model.pub.ossie.OssieSemanticModelExporter.export(
                            contextList, this.modelResourcePath);
                }
            } catch (Exception e) {
                ConsoleLog.printWarn("Failed to generate schema.yml file: " + e.getMessage());
            }
        }

        // publish code --only for domains allowed by the -Pdomain filter (or all when filter is off).
        String domainFilter = getDomainFilter();
        int publishedCount = 0;
        for (int i = 0; i < contextList.size(); i++) {
            String domainName = contextDomainNames.get(i);
            if (!isDomainIncluded(domainFilter, domainName)) continue;

            codePublishManager.setSourceBasePath(moduleRootDir);
            codePublishManager.publish(contextList.get(i));
            publishedCount++;
        }

        if (domainFilter != null && publishedCount == 0) {
            ConsoleLog.printWarn("[DomainFilter] -Pdomain=" + domainFilter
                    + " matched no domains. Available: " + contextDomainNames);
        }

    }

    /**
     * Single-module validation: generates in memory against the tree, writes
     * nothing, and ends the run - non-zero when anything differs.
     * The factory is null when the extractor is to find the source root itself.
     */
    private static void validate(ModelExtractor extractor, SourceFileAccessFactory factory, long startTime)
            throws FileNotFoundException {
        ConsoleLog.setValidationMode(true);
        ConsoleLog.printInfo("[VALID] Validation mode: checking generated code without writing...");
        extractor.extract(factory);
        failIfNothingWasChecked();

        // Flush any DSL warnings whose domain prefix did not match a published domain
        ConsoleLog.printRemainingAsOrphans("DSL_WARN",
                "[WARN] DSL Validation Warnings (unmatched domain):", "  ", true);

        int diffCount = ConsoleLog.getAccumulatedCount("VALIDATION_DIFF");
        int newCount = ConsoleLog.getAccumulatedCount("VALIDATION_NEW");
        int userModCount = ConsoleLog.getAccumulatedCount("VALID_USER_MODIFIED");
        int dslWarnCount = ConsoleLog.getAccumulatedCount("DSL_WARN");
        int unreadCount = ConsoleLog.countStoredLogkey("VALIDATION_LOAD_ERROR");
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        if (diffCount > 0 || newCount > 0 || userModCount > 0 || dslWarnCount > 0 || unreadCount > 0) {
            if (diffCount > 0 || newCount > 0) {
                ConsoleLog.printWarn("[VALID] Validation FAILED: " + diffCount + " stale file(s), " + newCount + " new file(s) pending. (Execution time: " + executionTime + "ms)");
            }
            if (userModCount > 0) {
                ConsoleLog.printWarn("[VALID] Validation FAILED: " + userModCount + " user-modified file(s) differ from DSL. (Execution time: " + executionTime + "ms)");
            }
            if (unreadCount > 0) {
                ConsoleLog.printWarn("[VALID] Validation FAILED: " + unreadCount + " model file(s) could not be read - see LOAD ERROR above.");
            }
            if (dslWarnCount > 0) {
                ConsoleLog.printWarn("[VALID] DSL Validation: " + dslWarnCount + " warning(s) found. (Execution time: " + executionTime + "ms)");
            }
            ConsoleLog.printWarn("[VALID] Run './gradlew elcore' to regenerate.");
            ConsoleLog.clear();
            System.exit(1);
        } else {
            ConsoleLog.printInfo("[VALID] Validation PASSED: All generated files are up-to-date. (Execution time: " + executionTime + "ms)");
            ConsoleLog.clear();
        }
    }

    /** A validation that found no model has compared nothing: said, and the run fails. */
    private static void failIfNothingWasChecked() {
        if (ConsoleLog.countStoredLogkey("VALIDATION_NO_MODEL") == 0) return;
        ConsoleLog.printWarn("[VALID] Validation FAILED: no model DSL found - nothing was checked.");
        ConsoleLog.clear();
        System.exit(1);
    }

    public static void main(String[] args) {

        String mode = System.getProperty("mode");
        if (mode == null) mode = "default";

        ConsoleLog.printInfo(
                        "    ________           __  _ __________  ____  ______\n" +
                        "   / ____/ /___ ______/ /_(_) ____/ __ \\/ __ \\/ ____/\n" +
                        "  / __/ / / __ `/ ___/ __/ / /   / / / / /_/ / __/   \n" +
                        " / /___/ / /_/ (__  ) /_/ / /___/ /_/ / _, _/ /___   \n" +
                        "/_____/_/\\__,_/____/\\__/_/\\____/\\____/_/ |_/_____/   \n" +
                        "                                                     \n");


        int p = mode.indexOf(":");
        if(p>0) {
            String prefix = mode.substring(0, p);
            String value = mode.substring(p+1);
            System.getProperties().setProperty(prefix, value);
            mode = "default";
        }

        //elasticore.domain
        ConsoleLog.printInfo("ModelExtractor execution mode: " + mode);

        long startTime = System.currentTimeMillis();
        
        try {
            ModelExtractor extractor = new ModelExtractor();

            // Multi-module mode: unified code generation across modules
            String modulePaths = System.getProperty("elasticore.modules");
            if (modulePaths != null && !modulePaths.trim().isEmpty()) {
                if ("valid".equals(mode)) {
                    ConsoleLog.setValidationMode(true);
                    ConsoleLog.printInfo("[VALID] Validation mode (multi-module)...");
                }

                extractor.extractMultiModule(modulePaths);
                if ("valid".equals(mode)) failIfNothingWasChecked();

                // Flush any DSL warnings whose domain prefix did not match a published domain
                ConsoleLog.printRemainingAsOrphans("DSL_WARN",
                        "[WARN] DSL Validation Warnings (unmatched domain):", "  ", true);

                int dslWarnCount = ConsoleLog.getAccumulatedCount("DSL_WARN");

                if ("valid".equals(mode)) {
                    int diffCount = ConsoleLog.getAccumulatedCount("VALIDATION_DIFF");
                    int newCount = ConsoleLog.getAccumulatedCount("VALIDATION_NEW");
                    int userModCount = ConsoleLog.getAccumulatedCount("VALID_USER_MODIFIED");
                    int unreadCount = ConsoleLog.countStoredLogkey("VALIDATION_LOAD_ERROR");
                    long endTime = System.currentTimeMillis();
                    long executionTime = endTime - startTime;
                    if (diffCount > 0 || newCount > 0 || userModCount > 0 || dslWarnCount > 0 || unreadCount > 0) {
                        if (diffCount > 0 || newCount > 0) {
                            ConsoleLog.printWarn("[VALID] Validation FAILED: " + diffCount + " stale file(s), " + newCount + " new file(s) pending. (" + executionTime + "ms)");
                        }
                        if (userModCount > 0) {
                            ConsoleLog.printWarn("[VALID] Validation FAILED: " + userModCount + " user-modified file(s) differ from DSL. (" + executionTime + "ms)");
                        }
                        if (unreadCount > 0) {
                            ConsoleLog.printWarn("[VALID] Validation FAILED: " + unreadCount + " model file(s) could not be read - see LOAD ERROR above.");
                        }
                        if (dslWarnCount > 0) {
                            ConsoleLog.printWarn("[VALID] DSL Validation: " + dslWarnCount + " warning(s) found. (" + executionTime + "ms)");
                        }
                        ConsoleLog.printWarn("[VALID] Run './gradlew elcore' to regenerate.");
                        ConsoleLog.clear();
                        System.exit(1);
                    } else {
                        ConsoleLog.printInfo("[VALID] Validation PASSED: All generated files are up-to-date. (" + executionTime + "ms)");
                        ConsoleLog.clear();
                        return;
                    }
                }

                // Regular elcore mode: also fail on DSL warnings
                if (dslWarnCount > 0) {
                    long endTime = System.currentTimeMillis();
                    ConsoleLog.printWarn("[ERROR] DSL Validation: " + dslWarnCount + " warning(s) found. (" + (endTime - startTime) + "ms)");
                    ConsoleLog.printWarn("[ERROR] Fix DSL warnings above before running ./gradlew elcore.");
                    ConsoleLog.clear();
                    System.exit(1);
                }

                long endTime = System.currentTimeMillis();
                log("Multi-module extract completed (" + (endTime - startTime) + "ms)");
                return;
            }

            // Single-module mode (existing behavior)
            if (args != null && args.length > 0) {
                String srcTargetPath = args[0];
                log("srcTargetPath: " + srcTargetPath);

                if("clean".equals(mode)) {
                    extractor.deleteGenSource(srcTargetPath);
                }
                else if("valid".equals(mode)) {
                    validate(extractor, new FileBasedSourceFileAccessFactory(srcTargetPath), startTime);
                    return;
                }
                else {
                    extractor.extract(new FileBasedSourceFileAccessFactory(srcTargetPath));
                }
            } else if ("valid".equals(mode)) {
                // Without a source root the extractor finds its own, as below.
                // The check used to take that branch - generation - and write.
                validate(extractor, null, startTime);
                return;
            } else {
                extractor.extract(null);
            }

            // Flush any DSL warnings whose domain prefix did not match a published domain
            ConsoleLog.printRemainingAsOrphans("DSL_WARN",
                    "[WARN] DSL Validation Warnings (unmatched domain):", "  ", true);

            // Regular elcore mode: also fail on DSL warnings
            int dslWarnCountSingle = ConsoleLog.getAccumulatedCount("DSL_WARN");
            if (dslWarnCountSingle > 0) {
                long endTime0 = System.currentTimeMillis();
                ConsoleLog.printWarn("[ERROR] DSL Validation: " + dslWarnCountSingle + " warning(s) found. (Execution time: " + (endTime0 - startTime) + "ms)");
                ConsoleLog.printWarn("[ERROR] Fix DSL warnings above before running ./gradlew elcore.");
                ConsoleLog.clear();
                System.exit(1);
            }

            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            log("ModelExtractor extract completed successfully (Execution time: " + executionTime + "ms)");


        } catch (Throwable e) {
            // A check that broke has checked nothing, and its answer is the exit
            // status: it used to print the trace and pass.
            if ("valid".equals(mode)) {
                e.printStackTrace();
                ConsoleLog.printWarn("[VALID] Validation FAILED: the run itself failed - see above.");
                System.exit(1);
            }
            // Check if this is an initialization-related error
            if (e instanceof FileNotFoundException || e.getMessage().contains("Not found model DSL file")) {
                FileNotFoundException fnfe = (FileNotFoundException) e;
                ConsoleLog.printWarn("ElastiCORE extraction failed. file not found: "+fnfe.getMessage());
                ConsoleLog.printInfo("Please run './gradlew elcore' to start the initialization process.");
            } else {
                e.printStackTrace();
            }
        }


    }

}
