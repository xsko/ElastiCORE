package io.elasticore.base.model.pub.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of a transaction port - an interface whose implementation
 * ElastiCORE supplies at runtime, reaching either an HTTP service or a
 * database.
 */
public final class PortSpec {

    /** One operation of the port. */
    public static final class Operation {
        private final String name;
        private final String returnType;
        private final List<String[]> params = new ArrayList<String[]>();

        private String description;
        private String url;
        private String httpMethod;
        private String contentType;

        Operation(String name, String returnType) {
            this.name = name;
            this.returnType = returnType;
        }

        /** Declares a parameter as its type followed by its name. */
        public Operation param(String typeName, String name) {
            params.add(new String[]{typeName, name, typeName});
            return this;
        }

        /**
         * A parameter whose declaration carries more than its type - a
         * multipart part's @MultipartContentType - with the type alone.
         */
        public Operation param(String declaration, String name, String typeName) {
            params.add(new String[]{declaration, name, typeName});
            return this;
        }

        /** The parameter's type without anything its declaration adds. */
        public static String typeOf(String[] param) {
            return param.length > 2 ? param[2] : param[0];
        }

        /**
         * Whether the port runtime sends the parameter as a value of its own -
         * a path variable or one query parameter - rather than as an object:
         * a primitive, a String, a number, a boolean or a char, or an array
         * of them (HttpServiceProxyFactory.isPrimitiveOrWrapper).
         */
        public static boolean isSimple(String[] param) {
            String t = typeOf(param).trim();
            if (t.endsWith("[]")) t = t.substring(0, t.length() - 2).trim();
            return SIMPLE.contains(t);
        }

        private static final java.util.Set<String> SIMPLE = new java.util.HashSet<String>(java.util.Arrays.asList(
                "String", "java.lang.String", "int", "long", "short", "byte", "double", "float", "boolean", "char",
                "Integer", "Long", "Short", "Byte", "Double", "Float", "Boolean", "Character",
                "BigDecimal", "java.math.BigDecimal", "BigInteger", "java.math.BigInteger",
                "java.lang.Integer", "java.lang.Long", "java.lang.Double", "java.lang.Boolean"));

        /** The HTTP endpoint this operation reaches, if any. */
        public Operation endpoint(String url, String httpMethod, String contentType) {
            this.url = url;
            this.httpMethod = httpMethod;
            this.contentType = contentType;
            return this;
        }

        public Operation description(String description) {
            this.description = description;
            return this;
        }

        /** The SQL of a database port's operation as the DSL writes it, or null. */
        private String sql;

        /** The SQL a database port's operation runs, as the DSL writes it - :name binds, /* if:name *&#47; lines. */
        public Operation sql(String sql) {
            this.sql = sql;
            return this;
        }

        public String sql() {
            return sql;
        }

        public String name() { return name; }
        public String returnType() { return returnType; }
        public List<String[]> params() { return params; }
        public String url() { return url; }
        public String httpMethod() { return httpMethod; }
        public String contentType() { return contentType; }

        /** Falls back to the operation's own name, as 2.x does. */
        public String description() {
            return (description == null || description.isEmpty()) ? name : description;
        }
    }

    private final String name;
    private final String domainId;

    private final List<Operation> operations = new ArrayList<Operation>();
    private final Map<String, String> packages = new LinkedHashMap<String, String>();

    /** "http" or "dbms"; a proto port is a different artifact. */
    private String protocol;

    private String url;
    private String authKey;
    private final Map<String, Integer> timeouts = new LinkedHashMap<String, Integer>();

    private String datasource;
    private String sqlSource;
    private String description;

    private SourceCode code;

    /**
     * Whether this HTTP port is written as a Spring HTTP service client
     * (@HttpExchange, Spring Boot 4) instead of for the ElastiCORE port
     * runtime - env.yml "httpPort: spring".
     */
    private boolean springHttp;

    public PortSpec springHttp(boolean springHttp) {
        this.springHttp = springHttp;
        return this;
    }

    public boolean springHttp() {
        return springHttp;
    }

    private String domainClassName;

    /** The domain's class-name prefix - Erp for erp - which names its configuration class. */
    public PortSpec domainClassName(String domainClassName) {
        this.domainClassName = domainClassName;
        return this;
    }

    public String domainClassName() {
        return domainClassName == null ? domainId : domainClassName;
    }

    /** The domain's env.yml mode - mybatis writes a database port as a MyBatis mapper and adapter. */
    private String mode;

    public PortSpec mode(String mode) {
        this.mode = mode;
        return this;
    }

    public String mode() {
        return mode;
    }

    public PortSpec(String name, String domainId) {
        this.name = name;
        this.domainId = domainId;
    }

    public String getName() {
        return name;
    }

    /** The id the runtime resolves the port by: domain and port name. */
    public String id() {
        return String.format("%s.%s", domainId, name);
    }

    public PortSpec protocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    public String protocol() {
        return protocol;
    }

    public PortSpec url(String url) {
        this.url = url;
        return this;
    }

    public String url() {
        return url;
    }

    public PortSpec authKey(String authKey) {
        this.authKey = authKey;
        return this;
    }

    public String authKey() {
        return authKey;
    }

    /**
     * A timeout in milliseconds, by the attribute the annotation names it
     * with: connectTimeout, readTimeout, writeTimeout, responseTimeout.
     */
    public PortSpec timeout(String attribute, Integer milliseconds) {
        if (milliseconds != null && milliseconds >= 0) timeouts.put(attribute, milliseconds);
        return this;
    }

    public Map<String, Integer> timeouts() {
        return timeouts;
    }

    public PortSpec datasource(String datasource) {
        this.datasource = datasource;
        return this;
    }

    public String datasource() {
        return datasource;
    }

    public PortSpec sqlSource(String sqlSource) {
        this.sqlSource = sqlSource;
        return this;
    }

    public String sqlSource() {
        return sqlSource;
    }

    public PortSpec description(String description) {
        this.description = description;
        return this;
    }

    public String description() {
        return description;
    }

    public Operation operation(String name, String returnType) {
        Operation op = new Operation(name, returnType);
        operations.add(op);
        return op;
    }

    public List<Operation> operations() {
        return operations;
    }

    public PortSpec pkg(String artifact, String packageName) {
        packages.put(artifact, packageName);
        return this;
    }

    public String pkg(String artifact) {
        return packages.get(artifact);
    }

    void attach(SourceCode code) {
        this.code = code;
    }

    SourceCode code() {
        return code;
    }
}
