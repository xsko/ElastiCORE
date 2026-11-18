package io.elasticore.base.model.pub.mongodb;

import io.elasticore.base.CodePublisher;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.model.ConstantParam;
import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.core.Items;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.entity.PkField;
import io.elasticore.base.model.pub.jpa.SrcFilePublisher;
import io.elasticore.base.model.repo.Method;
import io.elasticore.base.model.repo.Repository;
import io.elasticore.base.util.CodeTemplate;
import io.elasticore.base.util.StringUtils;

public class RepositoryFilePublisher extends SrcFilePublisher {

    private CodeTemplate baseCodeTmpl;

    private CodePublisher publisher;


    private String packageName;
    private String entityPackageName;
    private String dtoPackageName;
    private String enumPackageName;


    public RepositoryFilePublisher(CodePublisher publisher) {
        super(publisher);

        this.publisher = publisher;

        String templatePath = this.publisher.getECoreModelContext().getDomain().getModel().getConfig("template.repository");
        if(templatePath==null)
            templatePath = "elasticore-template/repository_mongo.tmpl";
        else
            templatePath = "resource://"+templatePath;

        this.baseCodeTmpl = CodeTemplate.newInstance(templatePath);

        ECoreModel model = publisher.getECoreModelContext().getDomain().getModel();
        this.packageName = model.getNamespace(ConstantParam.KEYNAME_REPOSITORY);
        this.entityPackageName = model.getNamespace(ConstantParam.KEYNAME_ENTITY);
        this.dtoPackageName = model.getNamespace(ConstantParam.KEYNAME_DTO);

        if(model.getEnumModels().getItems().size()>0)
            this.enumPackageName = model.getNamespace(ConstantParam.KEYNAME_ENUMERATION);
        else
            this.enumPackageName = null;

    }

    public void publish(ModelDomain domain, Repository repo) {

        String targetModelName = repo.getIdentity().getName();
        Entity entity = this.publisher.getECoreModelContext().findModelComponent(targetModelName, Entity.class);
        PkField pkField = entity.findPkField(domain);
        if (entity == null
                //|| entity.getMetaInfo().hasMetaAnnotation("abstract")
                || pkField == null) {
            return;
        }

        CodeTemplate.Parameters params = CodeTemplate.newParameters();


        String classNm = targetModelName + "Repository";

        params
                .set("packageName", packageName)
                .set("entityPackageName", entityPackageName)
                .set("dtoPackageName", dtoPackageName)
                .set("enumPackageName", enumPackageName)

                .set("className", classNm)
                .set("targetModel", targetModelName)
                .set("identity", "Long")

                .set("extendInfo", "")
                .set("implementInfo", "implements Serializable")
                .set("identity", pkField.getType());


        CodeTemplate.Paragraph p = getMethodInfo(repo);
        params.set("methodList", p);

        String code = baseCodeTmpl.toString(params);
        String qualifiedClassName = packageName + "." + classNm;

        this.writeSrcCode(this.publisher, repo, qualifiedClassName, code);
    }


    /*
     @Query(
            value = "SELECT *, earth_distance(ll_to_earth(:latitude, :longitude), ll_to_earth(urfs.car_port_mapy, urfs.car_port_mapx)) as map_point FROM used_rent_for_sale urfs WHERE urfs.month1age21is_use AND urfs.for_sale_status = 'SALEING' AND urfs.car_maker_type = :carMakerType ORDER BY map_point ASC, month1age21price ASC",
            nativeQuery = true
    )
     */
    private CodeTemplate.Paragraph getMethodInfo(Repository repo) {
        ModelComponentItems<Method> methodItems = repo.getItems();

        CodeTemplate.Paragraph p = CodeTemplate.newParagraph();
        while (methodItems.hasNext()) {
            Method method = methodItems.next();

            //if(method.getName()==null) continue;
            if (!method.isEnable()) continue;

            // A method without query: is a derived query, read from its name.
            if(method.getQueryInfo() != null && !method.getQueryInfo().isSelectQuery()) {
                p.add("@org.springframework.data.jpa.repository.Modifying");
            }


            boolean isNeedParamAnnotation = false;
            if (method.getQuery() != null && method.isNeedQueryAnnotation()) {
                if(method.getQuery().indexOf("/* if:")>0) continue;
                String query = StringUtils.splitByDoubleQuotation(method.getQuery());
                String queryAnnotation = String.format("@Query(nativeQuery=%s, value=%s)", method.isNative(), query);
                p.add(queryAnnotation);
                isNeedParamAnnotation = true;
            }


            p.add("%s %s(%s);"
                    , method.getReturnType()
                    , method.getName()
                    , getParametersForMethod(method, isNeedParamAnnotation));
            p.add("");
        }
        return p;
    }

    private String getParametersForMethod(Method method, boolean isNeedParamAnnotation) {
        Items<Field> params = method.getParams();
        StringBuilder sb = new StringBuilder();
        if (params != null) {
            for (Field f : params.getItemList()) {
                if (sb.length() > 0)
                    sb.append(" ,");
                // @Param("username")
                if (isNeedParamAnnotation)
                    sb.append("@Param(\"").append(f.getName()).append("\") ");
                sb.append(f.getTypeInfo().getDefaultTypeName()).append(" ").append(f.getName());
            }
        }

        if(!method.isNative() && method.isPageable()) {
            if(sb.length()>0) sb.append(",");
            sb.append(" Pageable pageable");
        }

        return sb.toString();
    }

}
