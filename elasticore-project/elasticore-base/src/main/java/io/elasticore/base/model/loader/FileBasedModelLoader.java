package io.elasticore.base.model.loader;

import io.elasticore.base.ModelLoader;
import io.elasticore.base.model.ConstantParam;
import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.EntityModels;
import io.elasticore.base.model.enums.EnumModel;
import io.elasticore.base.model.enums.EnumModels;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.elasticore.base.model.loader.javasrc.JavaSrcFileMainModelLoader;
import io.elasticore.base.model.loader.module.*;
import io.elasticore.base.model.repo.Repository;
import io.elasticore.base.model.repo.RepositoryModels;
import io.elasticore.base.model.port.Port;
import io.elasticore.base.util.ConsoleLog;
import io.elasticore.base.util.FileOrder;


import java.io.File;
import java.util.*;

//import static io.elasticore.base.model.core.BaseECoreModelContext.context;

public class FileBasedModelLoader implements ModelLoader, ConstantParam {

    private static FileBasedModelLoader instance;

    public static FileBasedModelLoader newInstance() {
        return new FileBasedModelLoader();
    }

    private String templateFileDirPath;


    public void setTemplateFileDirPath(String dirPath) {
        this.templateFileDirPath = dirPath;
    }

    List<String> domainNmList = null;

    @Override
    public List<String> getDomainNameList() {
        if(domainNmList ==null) {
            domainNmList = new ArrayList();
            File f = new File(templateFileDirPath);

            domainNmList.add(f.getName());
        }


        return domainNmList;
    }

    private static ObjectMapper mapper = new ObjectMapper(new YAMLFactory());


    private FileSource getLoadData(File f) {

        if (!f.getName().endsWith(".yml") && !f.getName().endsWith(".yaml")) {
            return null;
        }

        FileSource fileSource = null;

        try {
            Map map = mapper.readValue(f, LinkedHashMap.class);
            return FileSource.builder().filepath(f.getAbsolutePath()).infoMap(map).build();
        } catch (Throwable e) {
            System.err.println(f.getAbsolutePath());
            e.printStackTrace();
            return FileSource.builder().filepath(f.getAbsolutePath()).error(e).build();
        }

    }

    private List<FileSource> fileSources = new ArrayList<>();

    private void loadData(File f, List<FileSource> fileSources) {

        if (f.isDirectory()) {
            File[] files = FileOrder.list(f);
            for (File chidFile : files) {
                loadData(chidFile, fileSources);
            }
        } else {

            FileSource fs = getLoadData(f);
            if (fs != null)
                fileSources.add(fs);
        }
    }


    private ECoreModel load2(ModelLoaderContext context) {


        File f = new File(templateFileDirPath);
        loadData(f, fileSources);

        io.elasticore.base.model.loader.ModelLoader<Entity> entityModelLoader = new EntityModelLoader();
        io.elasticore.base.model.loader.ModelLoader<EnumModel> enumerationModelLoader = new EnumerationModelLoader();
        io.elasticore.base.model.loader.ModelLoader<DataTransfer> dataTransferModelLoader = new DataTransferModelLoader();
        io.elasticore.base.model.loader.ModelLoader<Repository> repositoryModelLoader = new RepositoryModelLoader();

        io.elasticore.base.model.loader.ModelLoader<Port> portModelLoader = new PortModelLoader();


        for (FileSource fileSource : fileSources) {

            Map<String, Map> map = fileSource.getInfoMap();

            if (map == null) {
                continue;
            }

            entityModelLoader.loadModel(context, fileSource);
            enumerationModelLoader.loadModel(context, fileSource);
            dataTransferModelLoader.loadModel(context, fileSource);
            repositoryModelLoader.loadModel(context, fileSource);
            portModelLoader.loadModel(context, fileSource);
        }

        entityModelLoader.completeLoad();
        enumerationModelLoader.completeLoad();
        dataTransferModelLoader.completeLoad();
        repositoryModelLoader.completeLoad();

        EntityModels entityModels = EntityModels.create(context.getDomainId(), "entityGrp", null, context.getEntityItems());
        EnumModels enumModels = EnumModels.create(context.getDomainId(), "enumGroup", null, context.getEnumModelItems());
        RepositoryModels repositoryModels = RepositoryModels.create(context.getDomainId(), "repoGroup", null, context.getRepositoryItems());

        // EnumModels enumModels = getEnumModels();
        // EntityModels entityModels = getEntityModels();

        return ECoreModel.builder()
                .entityModels(entityModels)
                .enumModels(enumModels)
                .repositoryModels(repositoryModels)
                // Every builder site passes it, not only the one whose loader reads the
                // sections: a field filled for some modes and not others reads as a defect.
                .removedDeclarations(context.getRemovedDeclarations())
                .metrics(context.getMetrics())
                .configMap(context.getConfigMap())
                .namespaceMap(context.getNsMap())
                .namespaceModuleRedirectMap(context.getModuleRedirectMap())
                .internalDomainName(domainNmList.get(0))
                .build();
    }


    public ModelLoaderContext getModelLoaderContext(String domainName) {
        //test();
        List<FileSource> fileSources = new ArrayList<>();

        if (templateFileDirPath == null) {
            throw new IllegalArgumentException("templateFileDirPath is empty");
        }

        File envFile = new File(templateFileDirPath + "/env.yml");
        if (!envFile.exists())
            throw new IllegalArgumentException("env.yaml does not exist.");

        FileSource envFs = getLoadData(envFile);
        Map envMap = (Map) envFs.getInfoMap().get("env");
        if(envMap==null)
            envMap = (Map) envFs.getInfoMap().get("elasticore");

        if(envMap==null)
            throw new IllegalArgumentException("env.yaml does not have env information.");

        if(Boolean.FALSE.equals(envMap.get("enable"))) {
            return null;
        }

        envMap = withRootEnv(envMap, domainName);

        Map<String, Object> configMap = (Map) envMap.get("config");
        Map<String, String> nsMap = (Map) envMap.get("namespace");

        // Parse module redirect prefixes from namespace values (e.g. "app-admin://com.acme.shop.domain.service")
        Map<String, String> moduleRedirectMap = new HashMap<>();
        if (nsMap != null) {
            Map<String, String> cleanNsMap = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : nsMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value != null) {
                    int sepIdx = value.indexOf("://");
                    if (sepIdx > 0) {
                        String scheme = value.substring(0, sepIdx);
                        // Skip known schemes (java, proto) - only treat unknown schemes as module aliases
                        if (!"java".equals(scheme) && !"proto".equals(scheme)) {
                            String cleanPackage = value.substring(sepIdx + 3);
                            cleanNsMap.put(key, cleanPackage);
                            moduleRedirectMap.put(cleanPackage, scheme);
                            continue;
                        }
                    }
                }
                cleanNsMap.put(key, value);
            }
            nsMap = cleanNsMap;
            nsMap = withBaseLayout(nsMap);
        }

        String userDefinedDomainName = (String) configMap.get("domainName");
        if(userDefinedDomainName!=null)
            domainName = userDefinedDomainName;

        ModelLoaderContext context = new ModelLoaderContext(domainName, configMap, nsMap, moduleRedirectMap, templateFileDirPath);

        return context;
    }
    /**
     * The domain's section with blueprint/env.yml under it, when the blueprint
     * folder has one (RootEnv); the section as written otherwise. Every reader
     * of a domain's settings comes through here, so all take the root the same way.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map withRootEnv(Map envMap, String folderName) {
        File rootFile = RootEnv.fileFor(new File(templateFileDirPath));
        if (rootFile == null) return envMap;
        Object config = envMap.get("config");
        Object named = config instanceof Map ? ((Map) config).get("domainName") : null;
        String domainName = named == null ? folderName : String.valueOf(named);
        RootEnv.Merged merged = RootEnv.apply(RootEnv.read(rootFile).settings(), envMap, domainName);
        for (String warning : merged.warnings()) {
            ConsoleLog.printWarn("[" + domainName + "] " + warning);
        }
        return merged.env();
    }

    /**
     * "namespace: { base: com.acme.erp }" - the hexagonal package layout under
     * one base package, so a project does not list thirteen keys to say the
     * usual thing. A key written beside it wins; without "base" nothing
     * changes.
     */
    static Map<String, String> withBaseLayout(Map<String, String> nsMap) {
        String base = nsMap.get("base");
        if (base == null || base.trim().isEmpty()) return nsMap;
        base = base.trim();
        String[][] layout = {
                {"entity", ".domain.model"}, {"enumeration", ".domain.model"},
                {"event", ".domain.event"}, {"domainService", ".domain.service"},
                {"domainRepository", ".domain.repository"},
                {"repository", ".infrastructure.persistence"}, {"persistence", ".infrastructure.persistence"},
                {"service", ".application"}, {"dto", ".application.dto"},
                {"inboundPort", ".application.port.in"}, {"queryPort", ".application.port.out"},
                {"port", ".application.port.out"}, {"control", ".adapter.in.web"}};
        Map<String, String> out = new LinkedHashMap<>();
        for (String[] kv : layout) out.put(kv[0], base + kv[1]);
        for (Map.Entry<String, String> e : nsMap.entrySet()) {
            if (!"base".equals(e.getKey())) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    @Override
    public ECoreModel load(ModelLoaderContext modelLoaderContext) {
        MainModelLoader loader = getMainModelLoader(modelLoaderContext, modelLoaderContext.getBaseFilePath());
        return loader.load(modelLoaderContext);
    }


    public ECoreModel load(String domainName) {

        ModelLoaderContext context = getModelLoaderContext(domainName);

        MainModelLoader loader = getMainModelLoader(context, context.getBaseFilePath());

        return loader.load(context);
        //return load(context);
    }

    private MainModelLoader getMainModelLoader(ModelLoaderContext context, String baseFilePath) {
        String mode = context.getConfig("mode", "jpa");
        String[] modes = mode.split(",");
        if(containsMode(modes,"java")) {
            return new JavaSrcFileMainModelLoader(baseFilePath);
        }
        return new YamlFileMainModelLoader(baseFilePath);
    }


    private boolean containsMode(String[] modes, String targetMode) {
        for (String m : modes) {
            if (m.trim().equals(targetMode)) {
                return true;
            }
        }
        return false;
    }

}
