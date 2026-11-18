package io.elasticore.base.model.loader.module;

import io.elasticore.base.model.ComponentType;
import io.elasticore.base.model.ConstantParam;
import io.elasticore.base.model.MetaInfo;
import io.elasticore.base.model.core.BaseComponentIdentity;
import io.elasticore.base.model.core.Items;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.FileSource;
import io.elasticore.base.model.loader.ModelLoader;
import io.elasticore.base.model.loader.ModelLoaderContext;
import io.elasticore.base.model.repo.Method;
import io.elasticore.base.model.repo.Repository;
import io.elasticore.base.model.repo.SqlQueryInfo;
import io.elasticore.base.util.MapWrapper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RepositoryModelLoader extends AbstractModelLoader implements ConstantParam, ModelLoader<Repository> {


    private ModelLoaderContext ctx;

    private List<SqlQueryInfo> sqlQueryInfoList = new ArrayList<>();


    public boolean loadModel(ModelLoaderContext ctx, FileSource source) {
        return loadModel(ctx, source.getInfoMap());
    }

    public boolean loadModel(ModelLoaderContext ctx, Map<String, Map> map) {
        this.ctx = ctx;
        boolean isProcess = false;
        if (map.containsKey(ConstantParam.KEYNAME_REPOSITORY)) {
            loadModel(ctx.getRepositoryItems(), map.get(ConstantParam.KEYNAME_REPOSITORY));
            isProcess = true;
        }

        if (map.containsKey(ConstantParam.KEYNAME_ENTITY)) {
            loadModel(ctx.getRepositoryItems(), map.get(ConstantParam.KEYNAME_ENTITY));
            isProcess = true;
        }


        if (map.containsKey(ConstantParam.KEYNAME_DTO)) {
            loadModel(ctx.getRepositoryItems(), map.get(ConstantParam.KEYNAME_DTO));
            isProcess = true;
        }

        return isProcess;
    }


    public void loadModel(Items<Repository> items, Map<String, LinkedHashMap> entityMap) {
        entityMap.forEach((repoName, value) -> {

            Repository repo = loadRepository(repoName, value);
            if(repo!=null)
                items.addItem(repo);
        });
    }


    @Override
    public void completeLoad() {
        for (Entity entity : this.ctx.getEntityItems().getItemList()) {
            String entityNm = entity.getIdentity().getName();
            BaseComponentIdentity identity = BaseComponentIdentity.create(ComponentType.REPOSITORY, ctx.getDomainId(), entityNm);
            if (this.ctx.getRepositoryItems().find(identity) == null) {

                Repository repo = Repository.create(ctx.getDomainId(), entityNm, null, null);
                this.ctx.getRepositoryItems().addItem(repo);
            }
        }

        for (SqlQueryInfo sqlQueryInfo : this.sqlQueryInfoList) {
            sqlQueryInfo.initialize(this.ctx);
        }


    }

    private static boolean looksLikeKeyedMethods(Map<String, Object> entityMap) {
        if (entityMap == null) return false;
        for (Object v : entityMap.values()) {
            if (!(v instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) v;
            if (m.containsKey("@") || m.containsKey("->") || m.containsKey("--")
                    || m.containsKey("query") || m.containsKey("return")) return true;
        }
        return false;
    }

    protected Repository loadRepository(String entityNm, Map<String, Object> entityMap) {

        MetaInfo metaInfo = parseMetaInfoObject(entityMap, ConstantParam.KEYNAME_REPOSITORY, entityNm);

        Object declared = entityMap.get(PROPERTY_METHODS);
        if (!(declared instanceof List)) {
            // Said rather than skipped silently when the entry is plainly a
            // repository written the other way - methods keyed by name, with
            // "@" / "->" / "--" or query / return inside. Other maps reach this
            // loader too (entities, DTOs) and are not repositories at all.
            if (looksLikeKeyedMethods(entityMap)) io.elasticore.base.util.ConsoleLog.printWarn(String.format(
                    "[repository] %s: no 'methods' list, so nothing is generated from it. Write it as"
                            + "%n  repository:%n    <EntityName>:%n      methods:%n        - id: findByName%n"
                            + "          params: { name: string }%n          return: List<EntityName>%n"
                            + "          query: SELECT e FROM EntityName e WHERE e.name = :name   # omit for a derived query",
                    entityNm));
            return null;
        }
        List<Map> methods = (List) declared;

        Items<Method> methodItems = null;

        methodItems = Items.create(Method.class);

        for (Map<String, Object> method : methods) {
            Method methodInfo = loadMethodInfo(entityNm,  method);

            methodItems.addItem(methodInfo);
        }

        //return Repository.create(entityNm, methodItems, null);
        return Repository.create(ctx.getDomainId(), entityNm, methodItems, metaInfo);

    }

    /**
     * @param map
     * @return
     */
    private Method loadMethodInfo(String rootObjName, Map<String, Object> map) {
        MapWrapper mapWrapper = new MapWrapper(map);
        String id = mapWrapper.getString("id");
        String methodName = mapWrapper.getString("name");

        boolean isDefaultNativeQuery = false;
        if(rootObjName.indexOf("DTO")>0)
            isDefaultNativeQuery = true;

        boolean isNativeQuery = mapWrapper.getBoolean("nativeQuery", isDefaultNativeQuery);
        boolean pageable = mapWrapper.getBoolean("pageable", false);
        String query = mapWrapper.getString("query");
        String returnType = mapWrapper.getString("return");

        boolean isReturnTypeSet = true;
        if(returnType==null) {
            isReturnTypeSet = false;
            if(isNativeQuery)
                returnType = "List<"+rootObjName+">";
        }

        if (returnType == null && (query == null || query.isEmpty())) {
            returnType = derivedReturnType(methodName != null ? methodName : id, rootObjName, pageable);
        }

        SqlQueryInfo queryInfo = null;
        if(query !=null && query.length()>0) {
            queryInfo = SqlQueryInfo.creat(ctx.getDomainId(), query, isNativeQuery , pageable, mapWrapper ,returnType ,isReturnTypeSet);
            this.sqlQueryInfoList.add(queryInfo);

            if(!queryInfo.isSelectQuery()) {
                returnType = queryInfo.getReturnType();
            }


        }




        /*
        if (returnType != null) {
            Field returnField = this.parseFieldLine("return", returnType);
        }

         */

        String requestType = mapWrapper.getString("input");
        Items<Field> fieldItems = null;
        Object paramsObj = map.get("params");
        if(paramsObj instanceof Map) {
            Map params = (Map) paramsObj;
            if (params != null) {
                fieldItems = this.parseField(params);
            }
        }else if(paramsObj !=null) {
            requestType = paramsObj.toString();
        }


        return Method.builder()
                .identity(BaseComponentIdentity.create(ComponentType.METHOD,ctx.getDomainId(),id))
                .name(methodName)
                .query(query)
                .inputType(requestType)
                .returnType(returnType)
                .params(fieldItems)
                .queryInfo(queryInfo)
                .pageable(pageable)
                .build();
    }

    /**
     * What a derived query (no query:) without return: answers, read from its name as Spring Data reads
     * it: a find/read/get/query/search/stream...By the entity's rows (a Page when pageable), countBy a long,
     * existsBy a boolean. A deleteBy/removeBy, or a name that is not a query, stays void - it was declared
     * void for every name, and a find declared void returned nothing.
     */
    static String derivedReturnType(String name, String root, boolean pageable) {
        if (name == null) return null;
        if (name.startsWith("count") && name.indexOf("By", 5) >= 0) return "long";
        if (name.startsWith("exists") && name.indexOf("By", 6) >= 0) return "boolean";
        for (String prefix : new String[] {"find", "read", "get", "query", "search", "stream"}) {
            if (name.startsWith(prefix) && name.indexOf("By", prefix.length()) >= 0) {
                return (pageable ? "Page<" : "List<") + root + ">";
            }
        }
        return null;
    }

    private String getMethodName(Select selectQuery) {
        StringBuilder sb = new StringBuilder();

        SelectBody selectBody = selectQuery.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            List<SelectItem> selectItems = plainSelect.getSelectItems();
            if (selectItems.size() == 1) {
                SelectItem selectItem = selectItems.get(0);
                if (selectItem instanceof AllColumns) {
                    sb.append("findByAll");


                    Expression expression = plainSelect.getWhere();
                    sb.append("test");

                }
            }


        }
        if (sb.length() == 0)
            return null;

        return sb.toString();

    }


}
