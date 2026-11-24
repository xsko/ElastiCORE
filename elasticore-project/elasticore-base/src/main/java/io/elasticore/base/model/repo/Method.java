package io.elasticore.base.model.repo;

import io.elasticore.base.model.ComponentIdentity;
import io.elasticore.base.model.MetaInfo;
import io.elasticore.base.model.ModelComponent;
import io.elasticore.base.model.core.Items;
import io.elasticore.base.model.entity.Field;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class Method implements ModelComponent {

    private ComponentIdentity identity;

    private MetaInfo metaInfo;

    private String name;

    private String type;

    private String description;

    private boolean isNative;

    private String query;

    public Items<Field> params;

    public String inputType;

    public String returnType;

    public SqlQueryInfo queryInfo;

    private boolean pageable;


    public boolean isNeedQueryAnnotation() {
        if (queryInfo == null)
            return false;

        /*
        if(this.name !=null)
            return true;

         */

        return queryInfo.isJpaQueryAnnotationNeed();
    }

    public boolean isEnable() {

        if (queryInfo != null) {
            if (queryInfo.isPredefinedJpaMethodName())
                return false;
        }

        /*
        if(queryInfo!=null)
            return queryInfo.isEnableNativeJpaMethod();


         */
        return true;

    }

    public String getQuery() {
        if (this.queryInfo == null)
            return null;
        return this.queryInfo.getSqlTxt();
    }


    public Items<Field> getParams() {
        if (params != null)
            return params;

        if (this.queryInfo != null)
            return this.queryInfo.getSetVarFieldItems();

        return null;
    }

    public String getInputType() {
        return inputType;
    }


    public String getReturnType() {
        // common:ContactDTO is ContactDTO to the rule engine, as a field's type is (TypeInfo.simpleNamed).
        if (returnType != null) return io.elasticore.base.model.entity.TypeInfo.simpleNamed(returnType);
        if (this.queryInfo != null)
            return this.queryInfo.getDefaultReturnType();

        return "void";
    }

    public String getName() {
        if (name != null)
            return name;


        if (this.queryInfo != null) {
            String jpaMethodName = this.queryInfo.getJpaMethodName();
            if (jpaMethodName != null)
                return jpaMethodName;

        }

        return this.getIdentity().getName();
        //return "unknownMethod";
    }


    public boolean isNative() {
        if (this.queryInfo != null) {
            isNative = this.queryInfo.isNativeQuery();
        }

        return isNative;
    }

    public String getDescription() {
        if(description==null) {
            description = getMetaInfo().getMetaAnnotationValue("label");
        }
        return description;
    }
}
