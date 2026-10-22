package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.ECoreModelContext;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.core.BaseECoreModelContext;
import io.elasticore.base.model.loader.FileBasedModelLoader;
import io.elasticore.base.util.FileOrder;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads every domain of a blueprint directory through the same path
 * ModelExtractor.extract() uses, so the rule pipeline consumes exactly what
 * the 2.x pipeline consumes. All domains are loaded regardless of any
 * -Pdomain filter: cross-domain references (@extend, enum types, shared
 * DTO templates) must resolve before per-domain work begins.
 */
public final class BlueprintLoader {

    /** blueprint directory name -> loaded context, in directory order */
    private final Map<String, ECoreModelContext> contexts =
            new LinkedHashMap<String, ECoreModelContext>();

    private BlueprintLoader() {
    }

    public static BlueprintLoader load(String blueprintRoot) {
        BlueprintLoader loader = new BlueprintLoader();
        File[] dirs = FileOrder.list(new File(blueprintRoot));
        if (dirs == null) return loader;

        for (File dir : dirs) {
            if (!dir.isDirectory()) continue;
            if (!new File(dir, "env.yml").isFile()) continue;
            FileBasedModelLoader fileLoader = FileBasedModelLoader.newInstance();
            fileLoader.setTemplateFileDirPath(dir.getAbsolutePath());
            ECoreModelContext ctx = BaseECoreModelContext.getContext(fileLoader);
            if (ctx != null) {
                loader.contexts.put(dir.getName(), ctx);
                loader.captureModels(dir.getName(), ctx);
            }
        }
        return loader;
    }

    /**
     * Context key -> the models that context loaded, captured right after the
     * load. The shared domain registry keeps only the LAST model per domain
     * name, so two blueprints naming the same domain would otherwise both
     * resolve to one of them at generation time.
     */
    private final Map<String, List<ECoreModel>> modelsByKey =
            new LinkedHashMap<String, List<ECoreModel>>();

    /** The models a context key loaded, in its own declaration order. */
    public List<ECoreModel> modelsOf(String key) {
        List<ECoreModel> models = modelsByKey.get(key);
        return models == null ? java.util.Collections.<ECoreModel>emptyList() : models;
    }

    private void captureModels(String key, ECoreModelContext ctx) {
        List<ECoreModel> models = new ArrayList<ECoreModel>();
        for (String domainName : ctx.getInternalDomainNames()) {
            ModelDomain d = ctx.getDomain(domainName);
            if (d != null) models.add(d.getModel());
        }
        modelsByKey.put(key, models);
    }

    /** Context key -> the blueprint directory name it was loaded from. */
    private final Map<String, String> domainDirName = new LinkedHashMap<String, String>();

    /** The blueprint directory a context key stands for. */
    public String domainDirNameOf(String key) {
        String name = domainDirName.get(key);
        return name == null ? key : name;
    }

    /** Blueprint directory name -> the module root that owns it. */
    private final Map<String, String> domainModuleRoot = new LinkedHashMap<String, String>();

    /** Module alias -> its root path, for the module:// namespace redirect. */
    private final Map<String, String> moduleRoots = new LinkedHashMap<String, String>();

    /**
     * Loads the domains of several modules, as ModelExtractor.extractMultiModule
     * does: each module keeps its blueprints under its own resources, and every
     * domain is loaded before any is generated so cross-module references
     * resolve.
     *
     * @param modulePaths comma-separated module roots, as -Delasticore.modules gives them
     */
    public static BlueprintLoader loadModules(String modulePaths) {
        BlueprintLoader loader = new BlueprintLoader();

        for (String raw : modulePaths.split(",")) {
            String modulePath = raw.trim();
            if (modulePath.isEmpty()) continue;

            File moduleDir = new File(modulePath);
            loader.moduleRoots.put(moduleDir.getName(), absolute(moduleDir));

            File blueprintDir = new File(moduleDir, "src/main/resources/blueprint");
            File[] dirs = FileOrder.list(blueprintDir);
            if (dirs == null) continue;

            for (File dir : dirs) {
                if (!dir.isDirectory()) continue;
                if (!new File(dir, "env.yml").isFile()) continue;

                // Templates resolve against the module being loaded.
                System.setProperty("elasticore.base.path", absolute(moduleDir));

                FileBasedModelLoader fileLoader = FileBasedModelLoader.newInstance();
                fileLoader.setTemplateFileDirPath(dir.getAbsolutePath());
                ECoreModelContext ctx = BaseECoreModelContext.getContext(fileLoader);
                if (ctx == null) continue;

                // Keyed by module so same-named domains of different modules
                // both survive; the dir name alone is not unique across modules.
                String key = moduleDir.getName() + "/" + dir.getName();
                loader.contexts.put(key, ctx);
                loader.captureModels(key, ctx);
                loader.domainDirName.put(key, dir.getName());
                loader.domainModuleRoot.put(key, absolute(moduleDir));
            }
        }
        return loader;
    }

    private static String absolute(File f) {
        try {
            return f.getCanonicalPath();
        } catch (java.io.IOException e) {
            return f.getAbsolutePath();
        }
    }

    /** The module a domain's output belongs to, or null in a single-module project. */
    public String moduleRootOf(String domainDirName) {
        return domainModuleRoot.get(domainDirName);
    }

    public Map<String, String> moduleRoots() {
        return moduleRoots;
    }

    public boolean isEmpty() {
        return contexts.isEmpty();
    }

    public Map<String, ECoreModelContext> contexts() {
        return contexts;
    }

    /** Every loaded domain model, used to resolve cross-domain enum references. */
    public List<ECoreModel> allModels() {
        List<ECoreModel> models = new ArrayList<ECoreModel>();
        for (List<ECoreModel> captured : modelsByKey.values()) {
            models.addAll(captured);
        }
        return models;
    }
}
