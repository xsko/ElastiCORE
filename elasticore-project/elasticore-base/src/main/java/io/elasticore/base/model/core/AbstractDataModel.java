package io.elasticore.base.model.core;

import io.elasticore.base.ECoreModelContext;
import io.elasticore.base.model.*;
import io.elasticore.base.model.dto.DataTransferAnnotation;
import io.elasticore.base.model.entity.*;
import io.elasticore.base.model.relation.ModelRelationship;
import io.elasticore.base.model.relation.RelationType;
import lombok.Getter;

import java.util.*;

@Getter
public abstract class AbstractDataModel<T extends AbstractReplaceableModel<T>> extends AbstractReplaceableModel<T> implements DataModelComponent {



    protected AbstractDataModel(ComponentIdentity componentIdentity) {
        super(componentIdentity);
    }


    abstract public MetaInfo getMetaInfo();

    abstract public ModelComponentItems<Field> getItems();

    protected void setRelationModel() {
        RelationshipManager rm = RelationshipManager.getInstance(this.getIdentity().getDomainId());
        String fromName = getIdentity().getName();

        Annotation extendAnt = this.getMetaInfo().getMetaAnnotation(EntityAnnotation.META_EXTEND);
        if(extendAnt!=null) {
            String toName = extendAnt.getValue();
            if(toName==null) {
                throw new IllegalStateException("Meta annotation 'extend' must have a value");
            }

            rm.addRelationship(ModelRelationship.create(fromName, toName, RelationType.SUPER));
            rm.addRelationship(ModelRelationship.create(toName, fromName, RelationType.CHILD));
        }

        String rollupTargetNm = this.getMetaInfo().getMetaAnnotationValue(EntityAnnotation.META_ROLL_UP_TARGET);
        if(rollupTargetNm!=null) {
            String[] toNames =rollupTargetNm.split(",");
            for(String toNm : toNames) {
                rm.addRelationship(ModelRelationship.create(fromName, toNm, RelationType.ROLLUP));
                rm.addRelationship(ModelRelationship.create(toNm, fromName, RelationType.ROLLDOWN));
            }
        }else {
            Annotation rolldownAnt = this.getMetaInfo().getMetaAnnotation(RelationType.ROLLDOWN.getName());
            if(rolldownAnt!=null) {
                String toName = rolldownAnt.getValue();
                if(toName!=null) {
                    rm.addRelationship(ModelRelationship.create(fromName, toName, RelationType.ROLLDOWN));
                    rm.addRelationship(ModelRelationship.create(toName, fromName, RelationType.ROLLUP));
                }

            }
        }

        Annotation templateAnt = this.getMetaInfo().getMetaAnnotation(RelationType.TEMPLATE.getName());
        if(templateAnt!=null) {
            String toName = templateAnt.getValue();
            if(toName !=null) {
                rm.addRelationship(ModelRelationship.create(fromName, toName, RelationType.TEMPLATE));
                rm.addRelationship(ModelRelationship.create(toName, fromName, RelationType.TEMPLATE_TO));
            }
        }

        String annotationNm = RelationType.SEARCHABLE.getName();
        String searchableEntityNm = this.getMetaInfo().getMetaAnnotationValue(annotationNm+".entity", annotationNm);

        if(searchableEntityNm !=null) {
            rm.addRelationship(ModelRelationship.create(fromName, searchableEntityNm, RelationType.SEARCHABLE));
            rm.addRelationship(ModelRelationship.create(searchableEntityNm, fromName, RelationType.SEARCHED));
        }



        annotationNm = RelationType.SEARCH_RESULT.getName();

        if(this.getMetaInfo().hasMetaAnnotation(annotationNm)) {

            String searchResultEntityNm = this.getMetaInfo().getMetaAnnotationValue(annotationNm+".entity", annotationNm);

            if(searchResultEntityNm !=null) {
                rm.addRelationship(ModelRelationship.create(searchResultEntityNm, fromName, RelationType.SEARCH_RESULT));
            }
            else {
                String type = this.getMetaInfo().getMetaAnnotationValue("type");
                if("entity".equals(type)) {
                    rm.addRelationship(ModelRelationship.create(fromName, fromName+"ResultDTO", RelationType.SEARCH_RESULT));
                }
            }
        }
    }


    protected void setPkRelationship(Items<Field> pkFields) {

        if(pkFields==null || pkFields.size()==0)
            return;

        // for parent key names
        StringBuilder sb = new StringBuilder();
        sb.append("PK:");

        String firstKeyInfo = null;
        String refEntityIfno = null;
        Field lastField = null;
        List<Field> pkFieldList = new ArrayList<>();
        int pkSize = pkFields.getItemList().size();
        if(pkSize<1)
            return;





        RelationshipManager rm = RelationshipManager.getInstance(this.getIdentity().getDomainId());
        String fromName = getIdentity().getName();



        int keySize=0;
        for(Field f: pkFields.getItemList()) {
            if(lastField!=null) {
                keySize++;
                pkFieldList.add(lastField);
                sb.append(getKeyInfo(lastField));

                if(keySize>=2) {
                    String keyInfo = sb.toString();
                    rm.addRelationship(ModelRelationship.create(keyInfo, fromName, RelationType.ONE_TO_MANY, "FK1"));
                    rm.addRelationship(ModelRelationship.create(fromName, keyInfo, RelationType.MANY_TO_ONE, "FK2"));
                }


            }else {
                firstKeyInfo = f.getName();
                refEntityIfno = f.getAnnotationValue("ref");
            }
            lastField = f;
        }
        String parentKeyNames = sb.toString();

        String thisKeyNames = parentKeyNames+getKeyInfo(lastField);

        if(pkSize!=1 || !"id".equals(lastField.getName())) {
            rm.setRefTargetInfo(thisKeyNames, fromName);
        }









        if(pkSize>1) {
            /*
            if(pkSize>2) {
                rm.addRelationship(ModelRelationship.create(fromName, parentKeyNames, RelationType.MANY_TO_ONE, "FK"));
                rm.addRelationship(ModelRelationship.create(parentKeyNames, fromName, RelationType.ONE_TO_MANY, "FK"));
            }

             */

            for(Field f: pkFields.getItemList()) {
                String keyInfo= "PK:"+getKeyInfo(f);
                rm.addRelationship(ModelRelationship.create(fromName, keyInfo, RelationType.AOSSOCIATION, "FK"));
            }
        }

        if(refEntityIfno!=null) {
            rm.addRelationship(ModelRelationship.create(fromName, refEntityIfno, RelationType.MANY_TO_ONE, firstKeyInfo));
            rm.addRelationship(ModelRelationship.create(refEntityIfno, fromName, RelationType.ONE_TO_MANY, firstKeyInfo));
        }

    }

    private String getKeyInfo(Field lastField) {
        return lastField.getName()+"["+lastField.getTypeInfo().getInitTypeInfo()+"]";
    }

    protected void setRelationship(Field f) {
        RelationshipManager rm = RelationshipManager.getInstance(this.getIdentity().getDomainId());
        TypeInfo typeInfo = f.getTypeInfo();
        String fromName = getIdentity().getName();
        String toName = typeInfo.getBaseTypeName();

        if (typeInfo.isGenericType()) {
            toName = typeInfo.getTypeParameterName();
        }

        if (f.hasAnnotation(RelationType.ONE_TO_ONE.getName())) {
            rm.addRelationship(ModelRelationship.create(fromName, toName, RelationType.ONE_TO_ONE, f.getName()));
            //rm.addRelationship(ModelRelationship.create(toName, fromName, RelationType.ONE_TO_ONE, f.getName()));
        } else if (f.hasAnnotation(RelationType.ONE_TO_MANY.getName())) {
            rm.addRelationship(ModelRelationship.create(fromName, toName, RelationType.ONE_TO_MANY, f.getName()));
            rm.addRelationship(ModelRelationship.create(toName, fromName, RelationType.MANY_TO_ONE, f.getName()));
        } else if (f.hasAnnotation(RelationType.MANY_TO_ONE.getName())) {
            rm.addRelationship(ModelRelationship.create(fromName, toName, RelationType.MANY_TO_ONE, f.getName()));
            rm.addRelationship(ModelRelationship.create(toName, fromName, RelationType.ONE_TO_MANY, f.getName()));
        } else if (f.hasAnnotation(RelationType.MANY_TO_MANY.getName())) {
            rm.addRelationship(ModelRelationship.create(fromName, toName, RelationType.MANY_TO_MANY, f.getName()));
            rm.addRelationship(ModelRelationship.create(toName, fromName, RelationType.MANY_TO_MANY, f.getName()));
        } else if (f.hasAnnotation(RelationType.EMBEDDED.getName())) {
            rm.addRelationship(ModelRelationship.create(fromName, toName, RelationType.EMBEDDED, f.getName()));
            rm.addRelationship(ModelRelationship.create(toName, fromName, RelationType.EMBEDDABLE, f.getName()));
        } else if (f.hasAnnotation(RelationType.EMBEDDABLE.getName())) {
            rm.addRelationship(ModelRelationship.create(fromName, toName, RelationType.EMBEDDABLE, f.getName()));
            rm.addRelationship(ModelRelationship.create(toName, fromName, RelationType.EMBEDDED, f.getName()));
        } else if (f.hasAnnotation(io.elasticore.base.model.entity.EntityAnnotation.FK)) {
            // @fk(Target) or @fk(Target.field) or @fk(Target.field, groupName)
            // Target is the referenced entity, extracted from annotation value (not field type)
            String fkValue = f.getAnnotationValue(io.elasticore.base.model.entity.EntityAnnotation.FK);
            if (fkValue != null) {
                // Strip group name if present (second positional param after comma)
                int commaIdx = fkValue.indexOf(',');
                String targetRef = (commaIdx > 0) ? fkValue.substring(0, commaIdx).trim() : fkValue.trim();
                // Strip field suffix if present (Target.field -> Target)
                int dotIdx = targetRef.indexOf('.');
                String targetEntity = (dotIdx > 0) ? targetRef.substring(0, dotIdx) : targetRef;
                if (!targetEntity.isEmpty()) {
                    rm.addRelationship(ModelRelationship.create(fromName, targetEntity, RelationType.FK_REFERENCE, f.getName()));
                }
            }
        } else if (!typeInfo.isBaseType()) {
            if (typeInfo.isGenericType()) {

                if(!typeInfo.isTypeParameterBaseType()) {
                    rm.addRelationship(ModelRelationship.create(fromName, toName, RelationType.MANY_TO_ONE, f.getName()));
                    rm.addRelationship(ModelRelationship.create(toName, fromName, RelationType.ONE_TO_MANY, f.getName()));
                }

            } else {
                rm.addRelationship(ModelRelationship.create(fromName, toName, RelationType.ONE_TO_ONE, f.getName()));
                //rm.addRelationship(ModelRelationship.create(toName, fromName, RelationType.ONE_TO_ONE, f.getName()));
            }

        }
    }



    private void loadFieldInfo(DataModelComponent model,ListMap<String, Field> list) {

         if(model == this) {
            ModelComponentItems<Field> fields = model.getItems();
            while (fields.hasNext()) {
                Field f = fields.next();
                if(!list.containsKey(f.getName()))
                    list.put(f.getName(), f);
            }
        }else {
            ListMap<String, Field> fieldListMap = model.getAllFieldListMap();
            Set<String> only = onlyNames();

            for(Field f : fieldListMap.getList()) {
                if (only != null && !only.contains(f.getName()) && !only.contains(f.getAnnotationValue("idof")))
                    continue;
                if(!list.containsKey(f.getName()))
                    list.put(f.getName(), f);
            }
        }

    }

    /**
     * The names @only(note, deliveryDate) keeps of the fields copied from other
     * models, or null without the mark. A relation's name keeps the field that
     * holds its id too (customerId, written @idof(customer) for a DDD reference).
     */
    private Set<String> onlyNames() {
        if (!getMetaInfo().hasMetaAnnotation(DataTransferAnnotation.META_ONLY)) return null;
        return new LinkedHashSet<>(names(getMetaInfo().getMetaAnnotationValue(DataTransferAnnotation.META_ONLY)));
    }

    /** The names of a comma list, trimmed, an empty one left out; none for null. */
    private static List<String> names(String list) {
        List<String> names = new ArrayList<>();
        if (list == null) return names;
        for (String name : list.split(",", -1)) {
            if (!name.trim().isEmpty()) names.add(name.trim());
        }
        return names;
    }

    /**
     * What is wrong with this model's @only, one sentence each; empty without
     * the mark. Asked once every domain is loaded, with find answering a model
     * name from all of them, so a template of another domain is found.
     */
    public List<String> onlyProblems(java.util.function.Function<String, DataModelComponent> find) {
        List<String> problems = new ArrayList<>();
        Set<String> only = onlyNames();
        if (only == null) return problems;
        String name = getIdentity().getName();
        MetaInfo meta = getMetaInfo();
        if (only.isEmpty()) problems.add(name + ": @only names no field");
        if (meta.hasMetaAnnotation(DataTransferAnnotation.META_EXTEND)) {
            problems.add(name + ": @only cannot go with @extend, whose fields the DTO always has");
        }
        List<String> copied = new ArrayList<>(names(meta.getMetaAnnotationValue(DataTransferAnnotation.META_TEMPLATE)));
        copied.addAll(names(meta.getMetaAnnotationValue(DataTransferAnnotation.META_SEARCHABLE_NAME)));
        copied.addAll(names(meta.getMetaAnnotationValue(DataTransferAnnotation.META_SEARCH_RESULT_NAME)));
        if (copied.isEmpty()) {
            problems.add(name + ": @only keeps fields of a @template, @searchable or @searchResult, and " + name
                    + " has none");
            return problems;
        }
        Set<String> known = new HashSet<>();
        for (String model : copied) {
            DataModelComponent found = find.apply(model);
            if (found == null) continue;
            // A DDD reference keeps its relation beside the id field, so the relation's name is here too.
            for (Field f : found.getAllFieldListMap().getList()) known.add(f.getName());
        }
        for (String listed : only) {
            if (!known.contains(listed)) {
                problems.add(
                        name + ": @only(" + listed + ") - " + String.join(", ", copied) + " has no field " + listed);
            }
        }
        ModelComponentItems<Field> own = getItems();
        for (int i = 0; i < own.size(); i++) {
            Field f = own.get(i);
            if (f.hasAnnotation("disable") && only.contains(f.getName())) {
                problems.add(name + "." + f.getName() + " is removed with -- and kept by @only - say one of them");
            }
        }
        return problems;
    }

    private String getReferenceModelNames() {
        MetaInfo meta = this.getMetaInfo();

        String templateNames = meta.getMetaAnnotationValue(DataTransferAnnotation.META_TEMPLATE);
        String extendNames = meta.getMetaAnnotationValue(DataTransferAnnotation.META_EXTEND);
        String searchNames = meta.getMetaAnnotationValue(DataTransferAnnotation.META_SEARCHABLE_NAME);
        String srchResultNames = meta.getMetaAnnotationValue( DataTransferAnnotation.META_SEARCH_RESULT_NAME);

        String rollupNames = meta.getMetaAnnotationValue(EntityAnnotation.META_ROLL_UP_TARGET);


        StringBuilder sb = new StringBuilder();

        if(templateNames!=null) {
            sb.append(templateNames.trim());
        }
        if(extendNames!=null) {
            if(sb.length()>0) sb.append(",");
            sb.append(extendNames.trim());
        }
        if(searchNames!=null) {
            if(sb.length()>0) sb.append(",");
            sb.append(searchNames.trim());
        }
        if(srchResultNames!=null) {
            if(sb.length()>0) sb.append(",");
            sb.append(srchResultNames.trim());
        }
        if(rollupNames!=null) {
            if(sb.length()>0) sb.append(",");
            sb.append(rollupNames.trim());
        }

        return sb.toString();
    }

    /**
     *
     * @return
     */
    protected String findDomainId() {
        return getIdentity().getDomainId();
    }

    /**
     *
     * @return
     */
    private ECoreModel getEcoreModel() {
        ECoreModel eCoreModel = null;
        try {
            eCoreModel = BaseModelDomain.getModelDomain(findDomainId())
                    .getModel();
        }catch (RuntimeException re) {

        }
        if(eCoreModel ==null)
            eCoreModel = BaseModelDomain.getCurrentModel();

        return eCoreModel;
    }


    /**
     *
     * @return
     */
    public String getFullName() {
        ComponentType componentType = this.getIdentity().getComponentType();

        String ns = getEcoreModel().getNamespace(componentType.getName());

        return ns+"."+this.getIdentity().getName();
    }

    protected DataModelComponent findModelFromAllDomain_OLD(String name) {
        String[] items = name.split(":");
        String domainId = getIdentity().getDomainId();
        String modelCoreName = name;
        if(items.length==2) {
            domainId = items[0];
            modelCoreName = items[1];
        }
        try {
            return BaseECoreModelContext.getContext().getDomain(domainId).getModel().findModelByName(modelCoreName);
        }catch (RuntimeException re){}
        return null;
    }

    protected DataModelComponent findModelFromAllDomain(String name) {

        ECoreModelContext context = BaseECoreModelContext.getTempContext();


        if(context==null) {
            System.err.println("test");
            System.err.println("test1");
            System.err.println("test2");
            System.err.println("tes3t");
        }

        String[] domainNames = context.getAllDomainNames();

        String[] items = name.split(":");
        String domainId = null;
        String modelCoreName = name;
        if(items.length==2) {
            domainId = items[0];
            modelCoreName = items[1];
        }


        for(String domainName: domainNames) {
            if(domainId!=null && !domainName.equals(domainId))
                continue;
            DataModelComponent modelByName = context.getDomain(domainName).getModel().findModelByName(modelCoreName);
            if (modelByName != null) {

                return modelByName;
            }
        }

        return null;
    }


    public ListMap<String, Field> getAllFieldListMap() {

        ECoreModel eCoreModel = getEcoreModel();

        ListMap<String, Field> fieldListMap = new ListMap<>();
        loadFieldInfo(this, fieldListMap);

        {
            String templates = getReferenceModelNames();

            if (templates != null && templates.length() > 0) {
                String[] templateNmArray = templates.split(",");

                for (String templateNm : templateNmArray) {
                    DataModelComponent model = findModelFromAllDomain(templateNm);
                    if(model!=null)
                        loadFieldInfo(model, fieldListMap);
                }

                /*
                DataTransferModels models = eCoreModel.getDataTransferModels();
                for (String templateNm : templateNmArray) {
                    DataTransfer templateEntity = models.findByName(templateNm);
                    if (templateEntity != null)
                        loadFieldInfo(templateEntity, fieldListMap);
                }


                EntityModels entityModels = eCoreModel.getEntityModels();
                for (String templateNm : templateNmArray) {
                    Entity templateEntity = entityModels.findByName(templateNm);
                    if (templateEntity != null)
                        loadFieldInfo(templateEntity, fieldListMap);
                }

                 */
            }

        }



        return fieldListMap;
    }

}
