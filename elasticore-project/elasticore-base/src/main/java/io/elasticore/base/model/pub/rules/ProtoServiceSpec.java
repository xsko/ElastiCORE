package io.elasticore.base.model.pub.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of a gRPC/proto port service for rule authors.
 *
 * Messages reuse {@link EntitySpec}/{@link FieldSpec} - a proto message is a
 * named list of typed fields, the same shape the field rules already work
 * with - so no parallel field model is introduced.
 *
 * Collections keep DECLARATION order. The 2.x publisher collects message
 * types into a HashSet, so its message order is hash-dependent and varies
 * between runs; deterministic order is a precondition for diff-based
 * regeneration, so v3 fixes it here.
 */
public final class ProtoServiceSpec {

    /** One rpc entry of the service block. */
    public static final class Rpc {
        private final String name;
        private final String inputType;
        private final String outputType;
        private final String description;

        Rpc(String name, String inputType, String outputType, String description) {
            this.name = name;
            this.inputType = inputType;
            this.outputType = outputType;
            this.description = description;
        }

        public String name() { return name; }
        public String inputType() { return inputType; }
        public String outputType() { return outputType; }
        public String description() { return description; }
    }

    private final String name;
    private final String javaPackage;

    private final List<Rpc> rpcs = new ArrayList<Rpc>();

    /** message name -> fields; a message may carry nested messages */
    private final List<EntitySpec> messages = new ArrayList<EntitySpec>();
    private final Map<String, List<String>> nested = new LinkedHashMap<String, List<String>>();

    /** enum name -> constant names, in declaration order */
    private final Map<String, List<String>> enums = new LinkedHashMap<String, List<String>>();

    private SourceCode code;

    public ProtoServiceSpec(String name, String javaPackage) {
        this.name = name;
        this.javaPackage = javaPackage;
    }

    public ProtoServiceSpec rpc(String name, String inputType, String outputType, String description) {
        rpcs.add(new Rpc(name, inputType, outputType, description));
        return this;
    }

    /** Adds a top-level message. Field types are proto types or message names. */
    public ProtoServiceSpec message(EntitySpec message) {
        messages.add(message);
        return this;
    }

    /** Declares that {@code messageName} embeds {@code nestedName} as a nested message. */
    public ProtoServiceSpec nest(String messageName, String nestedName) {
        List<String> list = nested.get(messageName);
        if (list == null) {
            list = new ArrayList<String>();
            nested.put(messageName, list);
        }
        list.add(nestedName);
        return this;
    }

    public ProtoServiceSpec enumeration(String enumName, String... constants) {
        List<String> list = new ArrayList<String>();
        for (String c : constants) list.add(c);
        enums.put(enumName, list);
        return this;
    }

    private boolean server;
    private boolean client;
    private String clientName;

    /** Whether the DSL asked for a service implementation stub. */
    public ProtoServiceSpec server(boolean value) {
        this.server = value;
        return this;
    }

    public boolean server() {
        return server;
    }

    /** Whether the DSL asked for a calling stub, and under what name. */
    public ProtoServiceSpec client(boolean value, String name) {
        this.client = value;
        this.clientName = name;
        return this;
    }

    public boolean client() {
        return client;
    }

    /** The configuration prefix the client reads its address from. */
    public String clientName() {
        return (clientName == null || clientName.isEmpty())
                ? Character.toLowerCase(name.charAt(0)) + name.substring(1)
                : clientName;
    }

    /** The stub artifacts, which sit beside the generated protobuf classes. */
    public ArtifactTarget stubTarget(String suffix) {
        return ArtifactTarget.java(javaPackage, name + suffix);
    }

    public String getName() {
        return name;
    }

    public String javaPackage() {
        return javaPackage;
    }

    public List<Rpc> rpcs() {
        return rpcs;
    }

    public List<EntitySpec> messages() {
        return messages;
    }

    /** Nested message names of the given message, in declaration order. */
    public List<String> nestedOf(String messageName) {
        List<String> list = nested.get(messageName);
        return list == null ? java.util.Collections.<String>emptyList() : list;
    }

    public Map<String, List<String>> enums() {
        return enums;
    }

    /** Message by name, or null. */
    public EntitySpec message(String messageName) {
        for (EntitySpec m : messages) {
            if (m.getName().equals(messageName)) return m;
        }
        return null;
    }

    /** Output identity: src/main/proto/<Service>.proto */
    public ArtifactTarget target() {
        return ArtifactTarget.proto(javaPackage, name);
    }

    void attach(SourceCode code) {
        this.code = code;
    }

    SourceCode code() {
        return code;
    }
}
