package io.elasticore.base.model.loader.module;

import io.elasticore.base.model.ComponentType;
import io.elasticore.base.model.ConstantParam;
import io.elasticore.base.model.MetaInfo;
import io.elasticore.base.model.core.Annotation;
import io.elasticore.base.model.core.BaseComponentIdentity;
import io.elasticore.base.model.core.Items;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.loader.FileSource;
import io.elasticore.base.model.loader.ModelLoader;
import io.elasticore.base.model.loader.ModelLoaderContext;
import io.elasticore.base.model.port.Port;
import io.elasticore.base.model.repo.Method;
import io.elasticore.base.model.repo.SqlQueryInfo;
import io.elasticore.base.util.ConsoleLog;
import io.elasticore.base.util.MapWrapper;
import io.elasticore.base.util.PrimitiveTypeUtil;
import io.elasticore.base.util.StringUtils;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.*;

import java.io.File;
import java.util.*;

public class PortModelLoader extends AbstractModelLoader implements ConstantParam, ModelLoader<Port> {

    /**
     * The DSL words "decimal" and "bigdecimal" as a whole type name: alone, or a
     * type argument. Lower case only - a class of the project's called Decimal is
     * its own type.
     */
    private static final java.util.regex.Pattern DECIMAL_WORD =
            java.util.regex.Pattern.compile("(?<![\\w.])(?:big)?decimal(?![\\w.])");


    private ModelLoaderContext ctx;

    private List<SqlQueryInfo> sqlQueryInfoList = new ArrayList<>();


    public boolean loadModel(ModelLoaderContext ctx, FileSource source) {
        return loadModel(ctx, source.getInfoMap(), source);
    }

    public boolean loadModel(ModelLoaderContext ctx, Map<String, Map> map, FileSource source) {
        this.ctx = ctx;
        boolean isProcess = false;
        if (map.containsKey(ConstantParam.KEYNAME_PORT)) {
            loadModel(ctx.getPortItems(), map.get(ConstantParam.KEYNAME_PORT), source);
            isProcess = true;
        }

        return isProcess;
    }

    /**
     * Extracts the relative path from an absolute file path based on "src/main/resources/blueprint".
     * It ensures the output is a clean concatenation of folder and file names without any separators.
     *
     * @param absolutePath The absolute path to be converted.
     * @return A clean, concatenated string without separators.
     */
    public static String getResourcePath(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty.");
        }

        // Normalize the path by converting all separators to '/'
        String normalizedPath = absolutePath.replace(File.separatorChar, '/');

        // Key path to search for
        String keyPath = "/resources/blueprint";
        int index = normalizedPath.lastIndexOf(keyPath);
        if (index == -1) {
            throw new IllegalArgumentException("'src/main/resources/blueprint' not found in the given path.");
        }

        // Extract the relative path after "src/main/resources/blueprint"
        String relativePath = normalizedPath.substring(index + 11);

        // Remove all slashes to get a clean output
        return relativePath.replace("\\", "/");
    }

    public void loadModel(Items<Port> items, Map<String, LinkedHashMap> entityMap, FileSource source) {

        String sqlSource = getResourcePath(source.getFilepath());

        entityMap.forEach((repoName, value) -> {

            Port repo = loadPort(repoName, value, sqlSource);
            if (repo != null)
                items.addItem(repo);
        });
    }


    @Override
    public void completeLoad() {
        for (Entity entity : this.ctx.getEntityItems().getItemList()) {
            String entityNm = entity.getIdentity().getName();
            BaseComponentIdentity identity = BaseComponentIdentity.create(ComponentType.PORT, ctx.getDomainId(), entityNm);
            if (this.ctx.getPortItems().find(identity) == null) {

                Port repo = Port.create(ctx.getDomainId(), entityNm, null, null);
                this.ctx.getPortItems().addItem(repo);
            }
        }

        for (SqlQueryInfo sqlQueryInfo : this.sqlQueryInfoList) {
            sqlQueryInfo.initialize(this.ctx);
        }

    }


    /**
     *
     * @param entityMap
     * @param type
     * @param mainClassName
     * @param sqlSource
     * @return
     */
    private MetaInfo parseMetaInfoObject(Map<String, Object> entityMap, String type, String mainClassName, String sqlSource) {
        if (entityMap == null)
            entityMap = new HashMap<>();

        Object infoObj = entityMap.get(PROPERTY_INFO);
        Map<String, Annotation> infoAnnotation = getAnnotationObj(infoObj);


        Object metaInfoObj = entityMap.get(PROPERTY_META);
        Map<String, Annotation> metaAnnotation = null;
        if (metaInfoObj == null) {
            metaAnnotation = new HashMap<>();
            if (type != null)
                metaAnnotation.put("type", Annotation.create("type", type));
        } else {
            metaAnnotation = getAnnotationObj(metaInfoObj);
        }

        if (mainClassName != null) {
            metaAnnotation.put("name", Annotation.create("name", mainClassName));
        }

        if (sqlSource != null) {
            metaAnnotation.put("sqlsource", Annotation.create("sqlsource", sqlSource));
        }
        String desc = (String) entityMap.get(PROPERTY_DESC);
        return MetaInfo.creat(infoAnnotation, metaAnnotation, desc);
    }


    protected Port loadPort(String portAdapterName, Map<String, Object> entityMap, String sqlSource) {

        MetaInfo metaInfo = parseMetaInfoObject(entityMap, ConstantParam.KEYNAME_PORT, portAdapterName, sqlSource);

        Map<String, LinkedHashMap> methods = (Map<String, LinkedHashMap>) entityMap.get(PROPERTY_METHODS);

        if (methods == null)
            return null;

        Items<Method> methodItems = Items.create(Method.class);

        methods.forEach((methodName, value) -> {
            Method methodInfo = loadMethodInfo(methodName, value);
            methodItems.addItem(methodInfo);
        });

        return Port.create(ctx.getDomainId(), portAdapterName, methodItems, metaInfo);

    }

    /**
     * @param map
     * @return
     */
    private Method loadMethodInfo(String rootObjName, Map<String, Object> map) {

        MetaInfo metaInfo = parseMetaInfoObject(map, ConstantParam.KEYNAME_METHOD, rootObjName);

        MapWrapper mapWrapper = new MapWrapper(map);
        String id = mapWrapper.getString("id");
        if (id == null || id.isEmpty())
            id = rootObjName;
        String methodName = mapWrapper.getString("name");

        boolean isDefaultNativeQuery = false;
        if (rootObjName.indexOf("DTO") > 0)
            isDefaultNativeQuery = true;

        boolean isNativeQuery = mapWrapper.getBoolean("nativeQuery", isDefaultNativeQuery);
        boolean pageable = mapWrapper.getBoolean("pageable", false);
        String query = mapWrapper.getString("query");
        String returnType = mapWrapper.getString("return");

        boolean isReturnTypeSet = true;
        if (returnType == null) {
            isReturnTypeSet = false;
            if (isNativeQuery)
                returnType = "List<" + rootObjName + ">";
        } else {
            // Handle primitive type declarations - convert to wrapper types
            if (PrimitiveTypeUtil.isPrimitiveType(returnType)) {
                returnType = PrimitiveTypeUtil.getWrapperType(returnType);
            } else {
                // The DSL's decimal, as on a field - alone or in List<...>/Page<...>.
                // Written out in full: a port interface imports java.util, the model
                // and the DTOs, not java.math.
                returnType = DECIMAL_WORD.matcher(returnType).replaceAll("java.math.BigDecimal");
            }
        }

        SqlQueryInfo queryInfo = null;
        if (query != null && query.length() > 0) {
            queryInfo = SqlQueryInfo.creat(ctx.getDomainId(), query, isNativeQuery, pageable, mapWrapper, returnType, isReturnTypeSet);
            this.sqlQueryInfoList.add(queryInfo);

            if (!queryInfo.isSelectQuery()) {
                returnType = queryInfo.getReturnType();
            }
            
            // Validate return type for SQL query
            if (isReturnTypeSet && !PrimitiveTypeUtil.isValidReturnType(query, returnType)) {
                //metaInfo
                //ConsoleLog.printWarn("[WARN] Return type '" + returnType + "' may not be appropriate for query\n"+ StringUtils.limitLines(query,3));

                ConsoleLog.printWarn("[WARN] Return type '" + returnType + "' may not be appropriate for query: " +metaInfo.getMetaAnnotationValue("name"));
            }
        }

        String requestType = mapWrapper.getString("input");
        Items<Field> fieldItems = null;
        Object paramsObj = map.get("params");
        if (paramsObj instanceof Map) {
            Map params = (Map) paramsObj;
            if (params != null) {
                fieldItems = this.parseField(params);
            }
        } else if (paramsObj != null) {
            requestType = paramsObj.toString();
        }

        return Method.builder()
                .identity(BaseComponentIdentity.create(ComponentType.METHOD, ctx.getDomainId(), id))
                .metaInfo(metaInfo)
                .name(methodName)
                .query(query)
                .inputType(requestType)
                .returnType(returnType)
                .params(fieldItems)
                .queryInfo(queryInfo)
                .pageable(pageable)
                .build();
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
