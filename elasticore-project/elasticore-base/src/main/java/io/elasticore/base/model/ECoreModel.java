package io.elasticore.base.model;

import java.util.*;
import java.util.stream.Collectors;

import io.elasticore.base.ECoreModelContext;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.model.core.BaseECoreModelContext;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.dto.DataTransferModels;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.EntityModels;
import io.elasticore.base.model.enums.EnumModel;
import io.elasticore.base.model.enums.EnumModels;
import io.elasticore.base.model.port.PortModels;
import io.elasticore.base.model.repo.RepositoryModels;
import io.elasticore.base.util.ConsoleLog;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ECoreModel {

    private EntityModels entityModels;

    private EnumModels enumModels;

    private DataTransferModels dataTransferModels;

    private RepositoryModels repositoryModels;

    private PortModels portModels;

    /**
     * Operations the domain performs that belong to no single aggregate.
     *
     * Null for a model whose loader has no such section, which is every
     * loader but the YAML one and every project written before this existed.
     */
    private io.elasticore.base.model.domainservice.DomainServiceModels domainServiceModels;

    /**
     * The business's measures (metrics:), name -> the line as written. Null
     * or empty for a model that declares none, and for every loader but the
     * YAML one.
     */
    private Map<String, String> metrics;

    private Map<String, Object> configMap;

    private Map<String, String> namespaceMap;

    // Module redirect map: package prefix -> module alias (e.g. "com.acme.shop.domain.service" -> "app-admin")
    private Map<String, String> namespaceModuleRedirectMap;

    private Set<String> externalEntities;

    private static Map<String, ShadowModel> shadowModelMap = null;

    private String internalDomainName;



    /**
     * Adds a non-null, non-empty entity name to the external entity set.
     *
     * @param entity the entity name to add
     * @return true if the entity was added, false if it was null, empty, or already present
     */
    public boolean addExternalEntity(String entity) {
        if (entity == null || entity.trim().isEmpty()) {
            return false;
        }
        if(externalEntities==null)
            externalEntities = new HashSet<>();
        return externalEntities.add(entity.trim());
    }

    /**
     * Returns a snapshot of the external entity names as a String array.
     *
     * @return a new array containing all external entity names
     */
    public String[] getExternalEntitiesAsArray() {
        if(externalEntities==null)
            return new String[0];
        return externalEntities.toArray(new String[0]);
    }



    public String getConfig(String key, String defualtValue) {
        String val = getConfig(key);
        if(val==null)
            val = defualtValue;

        return val;
    }

    public void setShadowModel(ShadowModel shadowModel) {
        if(shadowModelMap==null)
            shadowModelMap = new HashMap<>();
        shadowModel.setECoreModel(this);
        shadowModelMap.put(shadowModel.getName(), shadowModel);
    }

    public ShadowModel getShadowModel(String name) {
        ShadowModel result =  shadowModelMap.get(name);

        /*
        if(result==null) {
            Set<String> keys = shadowModelMap.keySet();
            for(String key: keys) {
                ConsoleLog.printWarn(">> "+key);
            }

        }
         */

        return result;

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
            } else {
                if(value==null)
                    return null;
                return value instanceof String ? (String) value : String.valueOf(value);
            }
        }
        return null;
    }

    public String getNamespace(String key) {
        if(namespaceMap==null) return null;
        return namespaceMap.get(key);
    }

    /**
     * Every namespace the domain declares, keyed as env.yml keys them.
     *
     * A rule set that contributes an artifact brings its own namespace key
     * with it, and nothing that copies a fixed list of keys can see one it
     * has never heard of.
     */
    public Map<String, String> getNamespaceMap() {
        if(namespaceMap==null) return java.util.Collections.emptyMap();
        return java.util.Collections.unmodifiableMap(namespaceMap);
    }



    /**
     *
     *
     * @param name
     * @return
     */
    public DataModelComponent findModelByName(String modelName) {

        String[] items = modelName.split(":");
        String targetDomainName = null;
        if(items.length==2) {
            targetDomainName = getConfig(items[0]);
        }
        else if(this.internalDomainName!=null) {
            targetDomainName = this.internalDomainName;
        }
        else {
            targetDomainName = getConfig("domainName");
        }


        return findModelByName(targetDomainName ,modelName);
    }


    public String getConfigDomainName() {
        return  getConfig("domainName");
    }

    /**
     *
     * @param domainId
     * @param name
     * @return
     */
    protected DataModelComponent findModelByName(String domainId, String name) {
        ECoreModel model = BaseECoreModelContext.getContext().getDomain(domainId).getModel();

        Entity entity = model.getEntityModels().findByName(name);
        if(entity!=null)
            return entity;

        DataTransfer dataTransfer = model.getDataTransferModels().findByName(name);
        if(dataTransfer!=null)
            return dataTransfer;

        EnumModel enumModel = model.getEnumModels().findByName(name);
        if(enumModel!=null)
            return enumModel;

        return null;
    }


}
