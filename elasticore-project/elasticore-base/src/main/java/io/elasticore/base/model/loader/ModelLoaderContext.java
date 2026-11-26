package io.elasticore.base.model.loader;

import io.elasticore.base.model.core.Items;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.enums.EnumModel;
import io.elasticore.base.model.port.Port;
import io.elasticore.base.model.repo.Repository;

import java.util.List;
import java.util.Map;

public class ModelLoaderContext {

    private String domainId;

    private Map<String, Object> configMap;
    private Map<String, String> nsMap;

    private Items<Entity> entityItems = Items.create(Entity.class);

    private Items<EnumModel> enumModelItems = Items.create(EnumModel.class);

    private Items<DataTransfer> dtoItems = Items.create(DataTransfer.class);

    private Items<Repository> repositoryItems = Items.create(Repository.class);

    private Items<Port> portItems = Items.create(Port.class);

    private String baseFilePath;

    // Module redirect map: package prefix -> module alias (e.g. "com.acme.shop.domain.service" -> "app-admin")
    private Map<String, String> moduleRedirectMap;

    ModelLoaderContext(String domainId, Map<String, Object> configMap, Map<String, String> nsMap) {
        this.domainId = domainId;
        this.configMap = configMap;
        this.nsMap = nsMap;
    }


    ModelLoaderContext(String domainId, Map<String, Object> configMap, Map<String, String> nsMap, String baseFilePath) {
        this.domainId = domainId;
        this.configMap = configMap;
        this.nsMap = nsMap;
        this.baseFilePath = baseFilePath;
    }

    ModelLoaderContext(String domainId, Map<String, Object> configMap, Map<String, String> nsMap, Map<String, String> moduleRedirectMap, String baseFilePath) {
        this.domainId = domainId;
        this.configMap = configMap;
        this.nsMap = nsMap;
        this.moduleRedirectMap = moduleRedirectMap;
        this.baseFilePath = baseFilePath;
    }

    // Public factory for runtime-only DSL loading (no env.yml required).
    // Used by DslLoader to parse a standalone YAML into ECoreModel at runtime.
    public static ModelLoaderContext createForRuntime(String domainId, String baseFilePath) {
        return new ModelLoaderContext(domainId, new java.util.HashMap<String, Object>(), new java.util.HashMap<String, String>(), baseFilePath);
    }

    public String getBaseFilePath() {
        return this.baseFilePath;
    }

    public String getDomainId() {
        return this.domainId;
    }

    public String getConfig(String key, String defaultValue) {
        String val = getConfig(key);
        if(val==null)
            return defaultValue;

        return val;
    }

    public String getConfig(String key) {
        if (configMap == null) {
            return null;
        }
        String[] keys = key.split("\\.");
        Map<String, Object> currentMap = configMap;

        for (int i = 0; i < keys.length; i++) {
            Object value = currentMap.get(keys[i]);

            if (i < keys.length - 1) {
                if (value instanceof Map) {
                    currentMap = (Map<String, Object>) value;
                } else {
                    return null;
                }
            } else if(value!=null){
                return value instanceof String ? (String) value : value.toString();
            }
        }
        return null;
    }

    public List<String> getConfigList(String key) {
        if (configMap == null) {
            return null;
        }
        String[] keys = key.split("\\.");
        Map<String, Object> currentMap = configMap;

        for (int i = 0; i < keys.length; i++) {
            Object value = currentMap.get(keys[i]);

            if (i < keys.length - 1) {
                if (value instanceof Map) {
                    currentMap = (Map<String, Object>) value;
                } else {
                    return null;
                }
            } else {
                return value instanceof List ? (List<String>) value : null;
            }
        }
        return null;
    }

    public String getNamespace(String key) {
        if(nsMap==null) return null;
        return nsMap.get(key);
    }


    public Items<Entity> getEntityItems() {
        return this.entityItems;
    }

    public Items<EnumModel> getEnumModelItems() {
        return this.enumModelItems;
    }

    public Items<DataTransfer> getDataTransferItems() {
        return this.dtoItems;
    }

    public Items<Repository> getRepositoryItems() {
        return this.repositoryItems;
    }

    public Items<Port> getPortItems() {
        return this.portItems;
    }

    /** The domainService: and processManager: entries 3.1 no longer reads, in file order. */
    private final java.util.List<io.elasticore.base.model.RemovedDeclaration> removedDeclarations =
            new java.util.ArrayList<io.elasticore.base.model.RemovedDeclaration>();

    public java.util.List<io.elasticore.base.model.RemovedDeclaration> getRemovedDeclarations() {
        return this.removedDeclarations;
    }

    /** metrics: name -> its line as written ("sum(Order.total) where ... -- label"), in file order. */
    private final java.util.Map<String, String> metrics = new java.util.LinkedHashMap<String, String>();

    public java.util.Map<String, String> getMetrics() {
        return this.metrics;
    }


    public Map<String, Object> getConfigMap() {
        return configMap;
    }

    public Map<String, String> getNsMap() {
        return nsMap;
    }

    public Map<String, String> getModuleRedirectMap() {
        return moduleRedirectMap;
    }
}
