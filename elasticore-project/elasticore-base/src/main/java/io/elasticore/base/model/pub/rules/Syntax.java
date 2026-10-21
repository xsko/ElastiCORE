package io.elasticore.base.model.pub.rules;

/**
 * How a target language writes the two things the emission layer builds
 * itself rather than reading from a template: an import, and a field
 * declaration.
 *
 * Everything else a rule produces is either language-neutral - an
 * annotation reads the same in Java and Kotlin - or comes from the template
 * set, which is chosen alongside this. Keeping the list this short is the
 * point: a new language is a template directory plus an entry here, not a
 * second emission layer.
 *
 * PROTOTYPE - part of the 3.0 publisher extension experiment.
 */
public enum Syntax {

    JAVA {
        public String importLine(String fqcn) {
            return String.format("import %s;", fqcn);
        }

        public String classLiteral(String typeName) {
            return String.format("%s.class", typeName);
        }

        /** Java has only the one. */
        public String javaClassLiteral(String typeName) {
            return classLiteral(typeName);
        }

        public String declaration(String modifier, String typeName, String name,
                                  String initializer, boolean required) {
            return String.format("%s %s %s%s;", modifier, typeName, name, initializer);
        }

        /** The model's type names are already Java's. */
        public String typeName(String javaType) {
            return javaType;
        }

        public String parameter(String type, String name) {
            return String.format("%s %s", typeName(type), name);
        }

        public String methodSignature(String modifier, String returnType,
                                      String name, String params) {
            String head = (modifier == null || modifier.isEmpty()) ? "" : modifier + " ";
            String type = (returnType == null || returnType.isEmpty()) ? "void" : typeName(returnType);
            return String.format("%s%s %s(%s)", head, type, name, params);
        }

        public String statement(String code) {
            return String.format("%s;", code);
        }

        public String getter(String receiver, String property) {
            return String.format("%s.get%s()", receiver, capital(property));
        }

        public String setter(String receiver, String property, String value) {
            return String.format("%s.set%s(%s)", receiver, capital(property), value);
        }

        public String setterRef(String receiver, String property) {
            return String.format("%s::set%s", receiver, capital(property));
        }

        public String localVar(String type, String name, String init) {
            return String.format("%s %s = %s;", typeName(type), name, init);
        }

        /** Written without spaces, as the 2.x output it reproduces. */
        public String lambda(String params, String body) {
            return String.format("%s->%s", params, body);
        }

        public String lambdaSpaced(String params, String body) {
            return String.format("%s -> %s", params, body);
        }

        public String sam(String type, String params, String body) {
            return String.format("(%s) -> %s", params, body);
        }

        /** Every Java reference is nullable already. */
        public String nullableType(String type) {
            return typeName(type);
        }

        public String samBlockOpen(String type, String params) {
            return String.format("(%s) -> {", params);
        }

        public String samBlockClose() {
            return "});";
        }

        public String lambdaReturn(String expression) {
            return String.format("return %s;", expression);
        }

        public String cast(String type, String expression) {
            return String.format("(%s) %s", type, expression);
        }

        /** Java marks an override with an optional annotation, not a modifier. */
        public String overriding() {
            return "public";
        }

        /** Java never asks; a reference is used as it stands. */
        public String notNull(String expression) {
            return expression;
        }

        /** Locale-independent, so a Turkish JVM does not turn I into a dotless i. */
        public String lowerCase(String expression) {
            return expression + ".toLowerCase(java.util.Locale.ROOT)";
        }

        /** Nothing in Java's grammar collides with an identifier. */
        public String escapeName(String name) {
            return name;
        }

        /** Java infers a generic method's type argument from the target. */
        public String genericCall(String receiver, String method, String typeArg, String args) {
            return String.format("%s.%s(%s)", receiver, method, args);
        }

        public String newInstance(String type, String args) {
            return String.format("new %s(%s)", typeName(type), args);
        }

        /**
         * A field, which Lombok's generated constructor then injects. The
         * class carries @AllArgsConstructor for this to work.
         */
        public String injectedProperty(String type, String name) {
            return String.format("private final %s %s;", typeName(type), name);
        }

        /** Only java.lang is in scope without asking. */
        public boolean needsImport(String fqcn) {
            return !fqcn.startsWith("java.lang.");
        }
    },

    KOTLIN {
        public String importLine(String fqcn) {
            return String.format("import %s", fqcn);
        }

        public String classLiteral(String typeName) {
            return String.format("%s::class", typeName);
        }

        /**
         * The java.lang.Class rather than the KClass, for a Java API that
         * asks for one - EntityManager.getReference does.
         */
        public String javaClassLiteral(String typeName) {
            return String.format("%s.java", classLiteral(typeName));
        }

        /**
         * A property rather than a field: the type follows the name, and a
         * declaration has to be initialised. A property the DSL did not mark
         * required is nullable and starts null; a required one keeps its
         * declared default when the DSL gave it one.
         */
        public String declaration(String modifier, String typeName, String name,
                                  String initializer, boolean required) {
            boolean hasInitializer = initializer != null && !initializer.isEmpty();
            String mapped = typeName(typeName);
            String type = (required && hasInitializer) ? mapped : mapped + "?";
            String value = hasInitializer ? initializer : " = null";
            return String.format("%s %s: %s%s", modifier, name, type, value);
        }

        /**
         * The model states its types in Java's names, which Kotlin mostly
         * shares - java.time and BigDecimal are the same class - but not for
         * the primitives' wrappers, for Object, or for an array, whose square
         * brackets are a generic type here.
         */
        public String typeName(String javaType) {
            if (javaType == null || javaType.isEmpty()) return javaType;

            String t = javaType.trim();
            if ("byte[]".equals(t)) return "ByteArray";
            if (t.endsWith("[]")) {
                String element = typeName(t.substring(0, t.length() - 2));
                return String.format("Array<%s>", element);
            }

            int lt = t.indexOf('<');
            if (lt > 0 && t.endsWith(">")) {
                String raw = typeName(t.substring(0, lt));
                String arg = typeName(t.substring(lt + 1, t.length() - 1));
                return String.format("%s<%s>", raw, arg);
            }

            if ("int".equals(t) || "Integer".equals(t)) return "Int";
            if ("long".equals(t)) return "Long";
            if ("boolean".equals(t)) return "Boolean";
            if ("double".equals(t)) return "Double";
            if ("float".equals(t)) return "Float";
            if ("short".equals(t)) return "Short";
            if ("byte".equals(t)) return "Byte";
            if ("char".equals(t) || "Character".equals(t)) return "Char";
            if ("Object".equals(t) || "java.lang.Object".equals(t)) return "Any";
            return t;
        }

        /** A parameter names itself before its type. */
        public String parameter(String type, String name) {
            return String.format("%s: %s", name, typeName(type));
        }

        /**
         * A function rather than a method, with the return type following the
         * parameters and omitted when there is none. Public is the default
         * here, so the modifier Java has to write is dropped.
         */
        public String methodSignature(String modifier, String returnType,
                                      String name, String params) {
            // Public is the default and static is not a thing a member of an
            // object can be, so both of Java's words fall away.
            String head = "";
            if (modifier != null) {
                StringBuilder kept = new StringBuilder();
                for (String word : modifier.trim().split(" ")) {
                    if (word.isEmpty() || "public".equals(word) || "static".equals(word)) continue;
                    // "override" is not optional here, so it survives.
                    if (kept.length() > 0) kept.append(' ');
                    kept.append(word);
                }
                if (kept.length() > 0) head = kept + " ";
            }
            boolean returnsNothing = returnType == null || returnType.isEmpty()
                    || "void".equals(returnType) || "Unit".equals(returnType);
            String tail = returnsNothing ? "" : String.format(": %s", typeName(returnType));
            return String.format("%sfun %s(%s)%s", head, name, params, tail);
        }

        /** A statement ends where the line does. */
        public String statement(String code) {
            return code;
        }

        /**
         * A property is read by its own name, not through an accessor.
         *
         * Every generated property is nullable, so reading through one - the
         * receiver is itself a read rather than a parameter - is a safe call.
         * Java has no such distinction and chains the accessors.
         */
        public String getter(String receiver, String property) {
            return String.format("%s%s%s", receiver, link(receiver), property);
        }

        public String setter(String receiver, String property, String value) {
            return String.format("%s%s%s = %s", receiver, link(receiver), property, value);
        }

        /** Dot for a parameter, safe call for anything read through. */
        private String link(String receiver) {
            // A call that returns the object itself is not a nullable read.
            if (receiver.endsWith(".creation()")) return ".";
            return receiver.indexOf('.') >= 0 ? "?." : ".";
        }

        /**
         * Kotlin has no reference to a setter, so what stands in for one is a
         * lambda that assigns - which is what the Consumer being passed does
         * with it either way.
         */
        public String setterRef(String receiver, String property) {
            return String.format("{ v -> %s.%s = v }", receiver, property);
        }

        /**
         * The type is inferred from what it is assigned - except from null,
         * which says only that the value is absent.
         */
        public String localVar(String type, String name, String init) {
            if ("null".equals(init)) {
                return String.format("val %s: %s = null", name, nullableType(type));
            }
            return String.format("val %s = %s", name, init);
        }

        /**
         * The arrow spacing the two Java forms differ in has no counterpart
         * here, so both arrive at the same place. The parameters come in as
         * Java writes them, parenthesised or not.
         */
        public String lambda(String params, String body) {
            return String.format("{ %s -> %s }", bareParams(params), body);
        }

        public String lambdaSpaced(String params, String body) {
            return lambda(params, body);
        }

        /**
         * A lambda where the target type cannot be inferred - Kotlin converts
         * a lambda to a Java functional interface at a call, but an assignment
         * needs the interface named.
         */
        public String sam(String type, String params, String body) {
            return String.format("%s %s", type, lambda(params, body));
        }

        /**
         * A value the caller may not get.
         *
         * Only where Java could have handed back null: a Java primitive
         * return cannot, and marking it nullable here would say something
         * about the runtime that is not true of the Java port either.
         */
        public String nullableType(String type) {
            if (type != null && JAVA_PRIMITIVES.contains(type.trim())) return typeName(type);
            String t = typeName(type);
            return t.endsWith("?") ? t : t + "?";
        }

        public String samBlockOpen(String type, String params) {
            return String.format("%s { %s ->", type, bareParams(params));
        }

        public String samBlockClose() {
            return "})";
        }

        /** A lambda's value is its last expression, not a returned one. */
        public String lambdaReturn(String expression) {
            return expression;
        }

        /** Overriding is a modifier here, and a required one. */
        public String overriding() {
            return "override";
        }

        /** A narrowing conversion is a method here, not a prefix. */
        public String cast(String type, String expression) {
            return String.format("%s.to%s()", expression, capital(typeName(type)));
        }

        /**
         * A value a runtime guard has already established. The guard is a
         * method call, which Kotlin's flow analysis cannot see through, so
         * what the generated code knows has to be said.
         */
        public String notNull(String expression) {
            return String.format("%s!!", expression);
        }

        /** lowercase(), locale-invariant; toLowerCase() is an error from Kotlin 2.1 on. */
        public String lowerCase(String expression) {
            return expression + ".lowercase()";
        }

        /** A name that is a keyword here is written in backticks. */
        public String escapeName(String name) {
            return KEYWORDS.contains(name) ? String.format("`%s`", name) : name;
        }

        /**
         * Kotlin infers a type argument from the arguments alone, so a method
         * whose argument does not mention it - Path.get(String) - has to be
         * told what it returns where Java read it off the assignment.
         */
        public String genericCall(String receiver, String method, String typeArg, String args) {
            return String.format("%s.%s<%s>(%s)", receiver, method, typeName(typeArg), args);
        }

        private String bareParams(String params) {
            String p = params.trim();
            if (p.startsWith("(") && p.endsWith(")")) {
                return p.substring(1, p.length() - 1).trim();
            }
            return p;
        }

        /** A constructor is called like any other function. */
        public String newInstance(String type, String args) {
            return String.format("%s(%s)", typeName(type), args);
        }

        /**
         * A constructor property, which is how Kotlin both declares a
         * dependency and takes it - so it carries the separator that puts it
         * in a parameter list rather than the terminator of a statement.
         */
        public String injectedProperty(String type, String name) {
            return String.format("val %s: %s,", name, typeName(type));
        }

        /**
         * Kotlin's default imports already carry its own collections, and its
         * List is not java.util.List - importing the Java one would make the
         * name ambiguous rather than resolve it. java.lang is in scope too.
         */
        public boolean needsImport(String fqcn) {
            if (fqcn.startsWith("java.lang.")) return false;
            return !JAVA_TYPES_KOTLIN_REPLACES.contains(fqcn);
        }
    },

    /**
     * TypeScript, for the model a browser sees: the DTOs and enums an API
     * hands out, generated from the same DSL the server is.
     *
     * This is the language that shows where the seam's edges are. Java and
     * Kotlin share a runtime, so most of what follows meant the same thing to
     * both; a third of it means nothing here. Those members throw rather than
     * answer, because a rule that reaches one is a rule that assumed the JVM,
     * and a plausible-looking answer would hide that.
     */
    TYPESCRIPT {

        /**
         * A module path and a named binding, not a dotted name.
         *
         * This is the seam's one real break: importLine was written for a
         * language where a fully qualified name IS the import. Everything the
         * model emits lands in one directory, so the path is the simple name;
         * a TypeScript target with a deeper layout would need the seam to
         * carry a path rather than a class name.
         */
        public String importLine(String fqcn) {
            String simple = simpleOf(fqcn);
            return String.format("import { %s } from './%s';", simple, simple);
        }

        /** Everything is imported; nothing is in scope for free. */
        public boolean needsImport(String fqcn) {
            return true;
        }

        public String typeName(String javaType) {
            if (javaType == null || javaType.isEmpty()) return javaType;
            String t = javaType.trim();

            // Jackson writes bytes as one base64 string, not an array of numbers.
            if ("byte[]".equals(t)) return "string";
            if (t.endsWith("[]")) {
                return String.format("%s[]", typeName(t.substring(0, t.length() - 2)));
            }
            int lt = t.indexOf('<');
            if (lt > 0 && t.endsWith(">")) {
                String raw = simpleOf(t.substring(0, lt));
                String arg = typeName(t.substring(lt + 1, t.length() - 1));
                if ("List".equals(raw) || "Set".equals(raw) || "Collection".equals(raw)) {
                    return String.format("%s[]", arg);
                }
                return String.format("%s<%s>", raw, arg);
            }

            String n = simpleOf(t);
            if (STRING_LIKE.contains(n)) return "string";
            if (NUMBER_LIKE.contains(n)) return "number";
            if ("boolean".equals(n) || "Boolean".equals(n)) return "boolean";
            if ("Object".equals(n)) return "unknown";
            if ("void".equals(n)) return "void";
            return n;
        }

        /** Absence is spelled by the property being optional, not by a union. */
        public String nullableType(String type) {
            return typeName(type);
        }

        /**
         * A property of an interface. A field the DSL did not mark required
         * is declared optional, which is how TypeScript says it may be absent.
         */
        public String declaration(String modifier, String typeName, String name,
                                  String initializer, boolean required) {
            return String.format("%s%s: %s;", name, required ? "" : "?", typeName(typeName));
        }

        public String statement(String code) {
            return String.format("%s;", code);
        }

        public String parameter(String type, String name) {
            return String.format("%s: %s", name, typeName(type));
        }

        public String methodSignature(String modifier, String returnType,
                                      String name, String params) {
            boolean returnsNothing = returnType == null || returnType.isEmpty()
                    || "void".equals(returnType);
            String tail = returnsNothing ? ": void" : String.format(": %s", typeName(returnType));
            return String.format("%s(%s)%s", name, params, tail);
        }

        public String localVar(String type, String name, String init) {
            return String.format("const %s = %s;", name, init);
        }

        public String getter(String receiver, String property) {
            return String.format("%s.%s", receiver, property);
        }

        public String setter(String receiver, String property, String value) {
            return String.format("%s.%s = %s", receiver, property, value);
        }

        public String setterRef(String receiver, String property) {
            return String.format("(v) => %s.%s = v", receiver, property);
        }

        public String lambda(String params, String body) {
            return String.format("(%s) => %s", bareOf(params), body);
        }

        public String lambdaSpaced(String params, String body) {
            return lambda(params, body);
        }

        public String newInstance(String type, String args) {
            return String.format("new %s(%s)", typeName(type), args);
        }

        /** The non-null assertion, which TypeScript spells after the value. */
        public String notNull(String expression) {
            return String.format("%s!", expression);
        }

        public String lowerCase(String expression) {
            return expression + ".toLowerCase()";
        }

        public String cast(String type, String expression) {
            return String.format("%s as %s", expression, typeName(type));
        }

        public String escapeName(String name) {
            return TS_KEYWORDS.contains(name) ? String.format("%s_", name) : name;
        }

        /** Inferred from the arguments, as Java infers from the target. */
        public String genericCall(String receiver, String method, String typeArg, String args) {
            return String.format("%s.%s(%s)", receiver, method, args);
        }

        // ---- what the JVM meant and this does not ----------------------

        public String classLiteral(String typeName) {
            throw jvmOnly("classLiteral");
        }

        public String javaClassLiteral(String typeName) {
            throw jvmOnly("javaClassLiteral");
        }

        public String sam(String type, String params, String body) {
            throw jvmOnly("sam");
        }

        public String samBlockOpen(String type, String params) {
            throw jvmOnly("samBlockOpen");
        }

        public String samBlockClose() {
            throw jvmOnly("samBlockClose");
        }

        public String lambdaReturn(String expression) {
            throw jvmOnly("lambdaReturn");
        }

        public String injectedProperty(String type, String name) {
            throw jvmOnly("injectedProperty");
        }

        public String overriding() {
            throw jvmOnly("overriding");
        }
    };

    /**
     * A member that only means something on the JVM.
     *
     * Answering it with something plausible would let a rule that assumed a
     * class literal, a functional interface or an injected field pass through
     * and emit nonsense. Failing here names the assumption instead.
     */
    static IllegalStateException jvmOnly(String member) {
        return new IllegalStateException(String.format(
                "Syntax.%s has no meaning outside the JVM. The rule that called it "
                        + "assumes a JVM concept and does not belong in a bundle for "
                        + "this language.", member));
    }

    /** The last segment of a dotted name. */
    public static String simpleOf(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    static String bareOf(String params) {
        String p = params == null ? "" : params.trim();
        if (p.startsWith("(") && p.endsWith(")")) {
            return p.substring(1, p.length() - 1).trim();
        }
        return p;
    }

    private static final java.util.Set<String> STRING_LIKE =
            new java.util.HashSet<String>(java.util.Arrays.asList(
                    "String", "char", "Character", "UUID",
                    "LocalDate", "LocalDateTime", "LocalTime", "Date", "Timestamp",
                    // ISO-8601 text in UTC, as Jackson's time module writes an instant.
                    "Instant"));

    private static final java.util.Set<String> NUMBER_LIKE =
            new java.util.HashSet<String>(java.util.Arrays.asList(
                    "int", "Integer", "long", "Long", "short", "Short",
                    "byte", "Byte", "double", "Double", "float", "Float",
                    // The runtime's count travels as its integer.
                    "Quantity",
                    // Jackson writes a decimal as a JSON number, and the runtime's
                    // amount as its decimal: JSON.parse hands the browser a number
                    // whatever the declaration says, so the declaration says number.
                    "BigDecimal", "BigInteger", "Money"));

    private static final java.util.Set<String> TS_KEYWORDS =
            new java.util.HashSet<String>(java.util.Arrays.asList(
                    "function", "class", "interface", "type", "const", "let",
                    "var", "new", "delete", "in", "of", "default", "enum"));

    /** Kotlin words that a Java method may nonetheless be named. */
    private static final java.util.Set<String> KEYWORDS =
            new java.util.HashSet<String>(java.util.Arrays.asList(
                    "in", "is", "as", "object", "typealias", "val", "var",
                    "fun", "when", "when", "when"));

    /** The Java types that hold a value rather than a reference to one. */
    private static final java.util.Set<String> JAVA_PRIMITIVES =
            new java.util.HashSet<String>(java.util.Arrays.asList(
                    "int", "long", "boolean", "double", "float",
                    "short", "byte", "char", "void"));

    /**
     * Java types Kotlin has its own of, so a Kotlin file names the Kotlin one
     * and importing the Java one would collide.
     */
    private static final java.util.Set<String> JAVA_TYPES_KOTLIN_REPLACES =
            new java.util.HashSet<String>(java.util.Arrays.asList(
                    "java.util.List", "java.util.Set", "java.util.Map",
                    "java.util.Collection", "java.util.Iterator"));

    /**
     * Whether a declaration can carry annotations at all.
     *
     * The shared rules decide two things at once: which fields survive and
     * what each one is annotated with. The first is a fact about the model
     * and the second is a fact about the JVM, and a language without
     * annotations wants the first without the second - so the renderer asks
     * here rather than every rule guarding itself.
     */
    public boolean supportsAnnotations() {
        return this != TYPESCRIPT;
    }

    /** The default modifier of a generated declaration. */
    public String fieldModifier() {
        return this == KOTLIN ? "var" : "private";
    }

    public abstract String importLine(String fqcn);

    /**
     * The model names its types as Java does, so a language that names them
     * differently says so here rather than at each place a type is written.
     */
    public abstract String typeName(String javaType);

    /** One parameter of a method: the two languages order it differently. */
    public abstract String parameter(String type, String name);

    /**
     * The declaration line of a method, up to but not including the brace.
     *
     * @param modifier   e.g. "public", dropped by a language where it is the default
     * @param returnType null, empty or "void" for a method that returns nothing
     * @param params     already built, one per {@link #parameter}
     */
    public abstract String methodSignature(String modifier, String returnType,
                                           String name, String params);

    /** A statement, terminated the way the language terminates one. */
    public abstract String statement(String code);

    /**
     * Whether a type has to be imported to be named, or is already in scope.
     */
    public abstract boolean needsImport(String fqcn);

    /**
     * A dependency the framework injects. Java declares a field and lets a
     * generated constructor fill it; Kotlin declares it in the constructor
     * itself, so the two differ in role and not only in spelling.
     */
    public abstract String injectedProperty(String type, String name);

    /**
     * Reading a property of an object. Java generated an accessor for it and
     * names that; Kotlin names the property.
     *
     * @param property the plain name, as the model declares it
     */
    public abstract String getter(String receiver, String property);

    /** Writing a property, as an expression without its terminator. */
    public abstract String setter(String receiver, String property, String value);

    /** A property write passed as a function, for a Consumer parameter. */
    public abstract String setterRef(String receiver, String property);

    /** A local declaration, with its terminator where the language wants one. */
    public abstract String localVar(String type, String name, String init);

    /**
     * A single-expression lambda.
     *
     * @param params as Java writes them, with or without parentheses - 2.x
     *               emits both forms and the compatibility mode reproduces each
     */
    public abstract String lambda(String params, String body);

    /** The same, where the Java form puts spaces around the arrow. */
    public abstract String lambdaSpaced(String params, String body);

    /** A lambda assigned rather than passed, so its interface may need naming. */
    public abstract String sam(String type, String params, String body);

    /**
     * A type whose value may be absent. Java says nothing - every reference
     * can be null - where Kotlin has to declare it, and declaring otherwise
     * turns an absent value into a NullPointerException at the boundary.
     */
    public abstract String nullableType(String type);

    /** A lambda with a body rather than one expression: its opening. */
    public abstract String samBlockOpen(String type, String params);

    /** The closing of a block lambda passed as the only argument. */
    public abstract String samBlockClose();

    /** The value a block lambda produces. */
    public abstract String lambdaReturn(String expression);

    /** The modifier a method that overrides another carries. */
    public abstract String overriding();

    /** Converting a value to a narrower numeric type. */
    public abstract String cast(String type, String expression);

    /** A value already guarded as present, where the language needs telling. */
    public abstract String notNull(String expression);

    /** A string expression in lower case, the same in every locale. */
    public abstract String lowerCase(String expression);

    /** A name the target language may reserve. */
    public abstract String escapeName(String name);

    /** A call to a generic method whose type argument one language must state. */
    public abstract String genericCall(String receiver, String method,
                                       String typeArg, String args);

    /** Constructing an instance, which Java announces with a keyword. */
    public abstract String newInstance(String type, String args);

    /** The name with its first letter capitalised, as an accessor spells it. */
    static String capital(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * A type named as a value, which an annotation attribute often takes -
     * @EntityListeners and @IdClass both do. The shape of an annotation is
     * the same in both languages but this is not: Java writes X.class where
     * Kotlin writes X::class.
     */
    public abstract String classLiteral(String typeName);

    /** The same type as a java.lang.Class, which Kotlin reaches through KClass. */
    public abstract String javaClassLiteral(String typeName);

    /**
     * @param initializer already carrying its separator, e.g. " = 0", or empty
     * @param required    whether the DSL marked the field as required
     */
    public abstract String declaration(String modifier, String typeName, String name,
                                       String initializer, boolean required);
}
