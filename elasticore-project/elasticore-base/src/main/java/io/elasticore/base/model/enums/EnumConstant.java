package io.elasticore.base.model.enums;


import io.elasticore.base.model.ComponentIdentity;
import io.elasticore.base.model.ComponentType;
import io.elasticore.base.model.ModelComponent;
import io.elasticore.base.model.core.BaseComponentIdentity;
import lombok.Builder;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Builder
@Setter
public class EnumConstant implements ModelComponent {


    private ComponentIdentity identity;


    private String name;


    private List<ConstructParam> constructParamList;


    public EnumConstant param(String name, String type) {
        if(constructParamList==null) constructParamList = new ArrayList<>();

        constructParamList.add(new ConstructParam(name, type) );
        return this;
    }

    @Override
    public ComponentIdentity getIdentity() {
        return BaseComponentIdentity.create(ComponentType.FIELD, "param", this.name);
    }

    public List<ConstructParam> getConstructParamList() {
        return constructParamList;
    }

    public static class ConstructParam {

        ConstructParam(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public static ConstructParam create(String line) {
            if (line.startsWith("\"") && line.endsWith("\"")) {
                String name =  line.substring(1, line.length() - 1);

                return new ConstructParam(name, "string");
            }

            if (line.startsWith("'") && line.endsWith("'")) {
                String name =  line.substring(1, line.length() - 1);

                return new ConstructParam(name, "string");
            }


            int p = line.indexOf(".");
            if(p>0) {
                String name = line.substring(p+1);
                String type = line.substring(0,p);
                return new ConstructParam(name, type);
            }

            return  new ConstructParam(line, null);
        }

        /** A value the enum line does not give: the constant's name stands in for it. */
        public static ConstructParam missing(String constantName) {
            ConstructParam param = create(constantName);
            param.missing = true;
            return param;
        }

        private String name;
        private String type;
        private boolean missing;

        /** True when the enum line gave no value here and the constant's name stands in. */
        public boolean isMissing() {
            return missing;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String toString() {
            if("String".equalsIgnoreCase(type)) {
                return "\""+name+"\"";
            }else if( "Integer".equalsIgnoreCase(type) || "int".equalsIgnoreCase(type)) {
                return name;
            }
            if(type==null)
                return name;

            return type+"."+name;
        }
    }
}
