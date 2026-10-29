package io.elasticore.base.model.pub.rules.verify;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import io.elasticore.base.model.pub.rules.schema.Column;
import io.elasticore.base.model.pub.rules.schema.ColumnType;
import io.elasticore.base.model.pub.rules.schema.Schema;
import io.elasticore.base.model.pub.rules.schema.Table;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The tables a JPA domain's entities map to, read off the entity sources -
 * what Hibernate reads - rather than the model, so the schema is the one
 * {@code ddl-auto=validate} accepts: the generator's classes as written, the
 * developer's own changes to them, an entity written by hand.
 *
 * <p>The names are Spring Boot's: an explicit name or the attribute's, through
 * CamelCaseToUnderscoresNamingStrategy (lower case, an underscore before a
 * capital that starts a word); a to-one's column {@code attribute_targetkey};
 * a join table {@code owningtable_attribute} (SpringImplicitNamingStrategy).
 * The types are Hibernate 6's for the Java type, @Lob, @JdbcTypeCode, a
 * converter's column type; the lengths and the NOT NULLs include what Bean
 * Validation says (@Size, @NotNull), as Hibernate applies it to its DDL.
 * Envers' audit tables (@Audited) and its revision table are written too.
 *
 * <p>Java is read with JavaParser; Kotlin line by line, for the forms the
 * generator writes. What cannot be mapped is said in {@link #warnings()}.
 */
public final class JpaSchemaReader {

    private final Map<String, JClass> classes = new LinkedHashMap<String, JClass>();
    private final Map<String, String> enums = new HashMap<String, String>();
    private final List<String> warnings = new ArrayList<String>();

    public List<String> warnings() {
        return warnings;
    }

    private void warnOnce(String w) {
        if (!warnings.contains(w)) warnings.add(w);
    }

    /** Envers' names, which an application may change (org.hibernate.envers.audit_table_suffix ...). */
    private String auditPrefix = "";
    private String auditSuffix = "_aud";
    private String revisionField = "rev";
    private String revisionTypeField = "revtype";

    /**
     * Reads Envers' naming settings out of the application's configuration -
     * application.yml / .yaml / .properties under the roots' resources - so
     * the audit tables are the ones Envers looks for.
     */
    public JpaSchemaReader readSettings(List<File> resourceDirs) {
        for (File dir : resourceDirs) {
            for (String name : new String[]{"application.yml", "application.yaml", "application.properties"}) {
                File f = new File(dir, name);
                if (!f.isFile()) continue;
                try {
                    String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                    auditPrefix = setting(text, "audit_table_prefix", auditPrefix);
                    auditSuffix = setting(text, "audit_table_suffix", auditSuffix);
                    revisionField = setting(text, "revision_field_name", revisionField);
                    revisionTypeField = setting(text, "revision_type_field_name", revisionTypeField);
                } catch (IOException e) {
                    warnings.add(name + ": not read (" + e.getMessage() + ")");
                }
            }
        }
        return this;
    }

    private static String setting(String text, String key, String otherwise) {
        Matcher m = Pattern.compile("envers\\.?" + key + "\\s*[:=]\\s*['\"]?([\\w$]*)['\"]?").matcher(text);
        if (!m.find()) m = Pattern.compile("\\b" + key + "\\s*[:=]\\s*['\"]?([\\w$]*)['\"]?").matcher(text);
        return m.find(0) ? m.group(1) : otherwise;
    }

    // ---- what was read ---------------------------------------------------------

    static final class JClass {
        String fqcn;
        String simple;
        String pkg;
        Map<String, String> imports = new HashMap<String, String>();
        String superName;
        String kind;
        final List<OrmMapping.Anno> annos = new ArrayList<OrmMapping.Anno>();
        final List<JField> fields = new ArrayList<JField>();
        /** The column type a converter class says (AttributeConverter&lt;X, Y&gt;), when it is one. */
        String converterColumn;

        OrmMapping.Anno anno(String simple) {
            for (OrmMapping.Anno a : annos) if (a.simple.equals(simple)) return a;
            return null;
        }
    }

    static final class JField {
        String name;
        String type;
        boolean primitive;
        final List<OrmMapping.Anno> annos = new ArrayList<OrmMapping.Anno>();

        OrmMapping.Anno anno(String simple) {
            for (OrmMapping.Anno a : annos) if (a.simple.equals(simple)) return a;
            return null;
        }

        boolean has(String simple) {
            return anno(simple) != null;
        }
    }

    // ---- reading -------------------------------------------------------------------

    /** Reads every .java and .kt under the given source roots. */
    public JpaSchemaReader read(List<File> roots) {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        this.roots.addAll(roots);
        for (File root : roots) {
            if (root == null || !root.isDirectory()) continue;
            List<File> files = new ArrayList<File>();
            collect(root, files);
            for (File f : files) {
                try {
                    String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                    if (!text.contains("jakarta.persistence") && !text.contains("javax.persistence")
                            && !text.contains("AttributeConverter") && !text.contains("enum ")) continue;
                    if (f.getName().endsWith(".java")) readJava(text, f);
                    else readKotlin(text, f);
                } catch (IOException | RuntimeException e) {
                    warnings.add(f.getName() + ": not read (" + e.getMessage() + ")");
                }
            }
        }
        return this;
    }

    /**
     * A JPA mapping said in XML (META-INF/orm.xml - mapping: xml), laid onto
     * the classes read from source as the annotations it stands for: the
     * classes keep their fields and types, the XML says how they map.
     */
    public JpaSchemaReader readOrmXml(File file) {
        if (file == null || !file.isFile()) return this;
        try {
            javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            org.w3c.dom.Document doc = f.newDocumentBuilder().parse(file);
            for (org.w3c.dom.Element e : children(doc.getDocumentElement())) {
                String kind = e.getTagName();
                if (!kind.equals("entity") && !kind.equals("mapped-superclass") && !kind.equals("embeddable")) continue;
                JClass c = classes.get(e.getAttribute("class"));
                if (c == null) c = readClassFile(e.getAttribute("class"));
                if (c == null) {
                    warnings.add(file.getName() + ": " + e.getAttribute("class") + " has no source read here - left out");
                    continue;
                }
                c.annos.add(anno(kind.equals("entity") ? "Entity" : kind.equals("embeddable") ? "Embeddable" : "MappedSuperclass",
                        "name", e.getAttribute("name")));
                for (org.w3c.dom.Element x : children(e)) {
                    if (x.getTagName().equals("table")) c.annos.add(tableAnno(x));
                    if (x.getTagName().equals("inheritance")) c.annos.add(anno("Inheritance", "strategy", "InheritanceType." + x.getAttribute("strategy")));
                    if (x.getTagName().equals("discriminator-column")) c.annos.add(columnAnno("DiscriminatorColumn", x));
                    if (!x.getTagName().equals("attributes")) continue;
                    for (org.w3c.dom.Element a : children(x)) attribute(c, a);
                }
                kind(c);
            }
        } catch (Exception ex) {
            warnings.add(file.getName() + ": not read (" + ex.getMessage() + ")");
        }
        return this;
    }

    /** The source of a class named in orm.xml: it names no JPA type, so reading every file did not take it. */
    private JClass readClassFile(String fqcn) {
        String path = fqcn.replace('.', '/');
        for (File root : roots) {
            for (String ext : new String[]{".java", ".kt"}) {
                File f = new File(root, path + ext);
                if (!f.isFile()) continue;
                try {
                    String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                    if (ext.equals(".java")) readJava(text, f);
                    else readKotlin(text, f);
                } catch (IOException | RuntimeException e) {
                    warnings.add(f.getName() + ": not read (" + e.getMessage() + ")");
                }
                return classes.get(fqcn);
            }
        }
        return null;
    }

    private final List<File> roots = new ArrayList<File>();

    private void attribute(JClass c, org.w3c.dom.Element a) {
        JField f = null;
        for (JField x : c.fields) if (x.name.equals(a.getAttribute("name"))) f = x;
        if (f == null) return;
        switch (a.getTagName()) {
            case "id": f.annos.add(anno("Id")); break;
            case "embedded-id": f.annos.add(anno("EmbeddedId")); break;
            case "version": f.annos.add(anno("Version")); break;
            case "transient": f.annos.add(anno("Transient")); return;
            case "basic": break;
            case "embedded": f.annos.add(anno("Embedded")); break;
            case "element-collection": f.annos.add(anno("ElementCollection")); break;
            case "many-to-one": f.annos.add(anno("ManyToOne", "optional", token(a.getAttribute("optional")))); break;
            case "one-to-one": f.annos.add(anno("OneToOne", "mappedBy", a.getAttribute("mapped-by"))); break;
            case "one-to-many": f.annos.add(anno("OneToMany", "mappedBy", a.getAttribute("mapped-by"))); break;
            case "many-to-many": f.annos.add(anno("ManyToMany", "mappedBy", a.getAttribute("mapped-by"))); break;
            default: return;
        }
        for (org.w3c.dom.Element x : children(a)) {
            switch (x.getTagName()) {
                case "column": f.annos.add(columnAnno("Column", x)); break;
                case "join-column": f.annos.add(columnAnno("JoinColumn", x)); break;
                case "join-table": f.annos.add(anno("JoinTable", "name", x.getAttribute("name"))); break;
                case "collection-table": f.annos.add(anno("CollectionTable", "name", x.getAttribute("name"))); break;
                case "order-column": f.annos.add(anno("OrderColumn", "name", x.getAttribute("name"))); break;
                case "generated-value": f.annos.add(anno("GeneratedValue", "strategy", "GenerationType." + x.getAttribute("strategy"))); break;
                case "sequence-generator": {
                    OrmMapping.Anno s = anno("SequenceGenerator", "name", x.getAttribute("name"));
                    put(s, "sequenceName", x.getAttribute("sequence-name"), true);
                    put(s, "allocationSize", x.getAttribute("allocation-size"), false);
                    put(s, "initialValue", x.getAttribute("initial-value"), false);
                    f.annos.add(s);
                    break;
                }
                case "lob": f.annos.add(anno("Lob")); break;
                case "enumerated": f.annos.add(anno("Enumerated", "value", "EnumType." + x.getTextContent().trim())); break;
                case "convert": f.annos.add(anno("Convert", "converter", x.getAttribute("converter") + ".class")); break;
                case "attribute-override": {
                    OrmMapping.Anno ov = anno("AttributeOverride", "name", x.getAttribute("name"));
                    for (org.w3c.dom.Element col : children(x)) {
                        if (col.getTagName().equals("column")) ov.args.put("column", columnAnno("Column", col));
                    }
                    f.annos.add(ov);
                    break;
                }
                default: break;
            }
        }
    }

    private static OrmMapping.Anno tableAnno(org.w3c.dom.Element x) {
        OrmMapping.Anno t = anno("Table", "name", x.getAttribute("name"));
        put(t, "schema", x.getAttribute("schema"), true);
        List<Object> indexes = new ArrayList<Object>();
        List<Object> uniques = new ArrayList<Object>();
        for (org.w3c.dom.Element i : children(x)) {
            if (i.getTagName().equals("index")) {
                OrmMapping.Anno ix = anno("Index", "name", i.getAttribute("name"));
                put(ix, "columnList", i.getAttribute("column-list"), true);
                put(ix, "unique", i.getAttribute("unique"), false);
                indexes.add(ix);
            } else if (i.getTagName().equals("unique-constraint")) {
                OrmMapping.Anno u = anno("UniqueConstraint", "name", i.getAttribute("name"));
                List<Object> cols = new ArrayList<Object>();
                for (org.w3c.dom.Element c : children(i)) if (c.getTagName().equals("column-name")) cols.add(c.getTextContent().trim());
                u.args.put("columnNames", cols);
                uniques.add(u);
            }
        }
        if (!indexes.isEmpty()) t.args.put("indexes", indexes);
        if (!uniques.isEmpty()) t.args.put("uniqueConstraints", uniques);
        return t;
    }

    private static OrmMapping.Anno columnAnno(String simple, org.w3c.dom.Element x) {
        OrmMapping.Anno c = anno(simple, "name", x.getAttribute("name"));
        for (String attr : new String[]{"nullable", "unique", "length", "precision", "scale"}) {
            put(c, attr, x.getAttribute(attr), false);
        }
        put(c, "columnDefinition", x.getAttribute("column-definition"), true);
        return c;
    }

    private static OrmMapping.Anno anno(String simple) {
        OrmMapping.Anno a = new OrmMapping.Anno();
        a.simple = simple;
        a.text = "@" + simple;
        return a;
    }

    /** An annotation with one argument, a string unless it is a token (an enum constant, a class). */
    private static OrmMapping.Anno anno(String simple, String key, String value) {
        OrmMapping.Anno a = anno(simple);
        boolean token = value != null && (value.startsWith("GenerationType.") || value.startsWith("InheritanceType.")
                || value.startsWith("EnumType.") || value.endsWith(".class") || "true".equals(value) || "false".equals(value));
        put(a, key, value, !token);
        return a;
    }

    private static void put(OrmMapping.Anno a, String key, String value, boolean string) {
        if (value == null || value.isEmpty()) return;
        a.args.put(key, string ? new OrmMapping.Str(value) : value);
    }

    private static String token(String v) {
        return v == null || v.isEmpty() ? null : v;
    }

    private static List<org.w3c.dom.Element> children(org.w3c.dom.Element e) {
        List<org.w3c.dom.Element> out = new ArrayList<org.w3c.dom.Element>();
        for (org.w3c.dom.Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof org.w3c.dom.Element) out.add((org.w3c.dom.Element) n);
        }
        return out;
    }

    private static void collect(File dir, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        java.util.Arrays.sort(list);
        for (File f : list) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")) out.add(f);
        }
    }

    private void readJava(String text, File file) {
        CompilationUnit cu = StaticJavaParser.parse(text);
        String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        Map<String, String> imports = new HashMap<String, String>();
        for (ImportDeclaration i : cu.getImports()) {
            if (i.isStatic() || i.isAsterisk()) continue;
            String n = i.getNameAsString();
            imports.put(n.substring(n.lastIndexOf('.') + 1), n);
        }
        for (TypeDeclaration<?> t : cu.getTypes()) readType(t, pkg, pkg, imports);
    }

    private void readType(TypeDeclaration<?> t, String pkg, String outer, Map<String, String> imports) {
        String fqcn = outer + "." + t.getNameAsString();
        if (t instanceof EnumDeclaration) {
            enums.put(t.getNameAsString(), fqcn);
            enums.put(fqcn, fqcn);
        }
        JClass c = new JClass();
        c.fqcn = fqcn;
        c.simple = t.getNameAsString();
        c.pkg = pkg;
        c.imports = imports;
        for (AnnotationExpr a : t.getAnnotations()) OrmMapping.parseAnnotations(a.toString(), c.annos, imports, pkg);
        if (t instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cd = (ClassOrInterfaceDeclaration) t;
            if (!cd.getExtendedTypes().isEmpty()) c.superName = cd.getExtendedTypes().get(0).getNameAsString();
            for (com.github.javaparser.ast.type.ClassOrInterfaceType it : cd.getImplementedTypes()) {
                if (it.getNameAsString().equals("AttributeConverter") && it.getTypeArguments().isPresent()
                        && it.getTypeArguments().get().size() == 2) {
                    c.converterColumn = it.getTypeArguments().get().get(1).asString();
                }
            }
        }
        for (BodyDeclaration<?> m : t.getMembers()) {
            if (m instanceof FieldDeclaration) {
                FieldDeclaration fd = (FieldDeclaration) m;
                if (fd.isStatic() || fd.isTransient()) continue;
                for (VariableDeclarator v : fd.getVariables()) {
                    JField f = new JField();
                    f.name = v.getNameAsString();
                    f.type = v.getType().asString();
                    f.primitive = v.getType().isPrimitiveType();
                    for (AnnotationExpr a : fd.getAnnotations()) OrmMapping.parseAnnotations(a.toString(), f.annos, imports, pkg);
                    c.fields.add(f);
                }
            } else if (m instanceof TypeDeclaration) {
                readType((TypeDeclaration<?>) m, pkg, fqcn, imports);
            }
        }
        kind(c);
        classes.put(fqcn, c);
    }

    private static final Pattern KT_PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)");
    private static final Pattern KT_CLASS = Pattern.compile("\\b(?:(enum)\\s+)?class\\s+(\\w+)(?:[^:{]*?:\\s*([\\w.]+))?");
    private static final Pattern KT_PROP = Pattern.compile("^\\s*(?:(?:private|protected|internal|public|open|override|lateinit)\\s+)*(var|val)\\s+(\\w+)\\s*:\\s*([\\w.<>?, ]+?)\\s*(?:=.*)?$");
    private static final java.util.Set<String> KT_PRIMITIVES = new java.util.HashSet<String>(java.util.Arrays.asList(
            "Int", "Long", "Short", "Byte", "Boolean", "Double", "Float", "Char"));
    private static final Pattern KT_CONVERTER =Pattern.compile("AttributeConverter<\\s*[\\w.?]+\\s*,\\s*([\\w.?]+)\\s*>");

    /** Kotlin as the generator writes it: annotations on the lines above a {@code var name: Type}. */
    private void readKotlin(String text, File file) {
        String[] lines = text.replace("\r\n", "\n").split("\n");
        String pkg = "";
        Map<String, String> imports = new HashMap<String, String>();
        for (String l : lines) {
            Matcher m = KT_PACKAGE.matcher(l);
            if (m.find()) pkg = m.group(1);
            String t = l.trim();
            if (t.startsWith("import ")) {
                String n = t.substring(7).trim();
                if (n.contains(" as ")) imports.put(n.substring(n.indexOf(" as ") + 4).trim(), n.substring(0, n.indexOf(" as ")).trim());
                else if (!n.endsWith(".*")) imports.put(n.substring(n.lastIndexOf('.') + 1), n);
            }
        }
        JClass c = null;
        List<OrmMapping.Anno> pending = new ArrayList<OrmMapping.Anno>();
        int depth = 0;
        int classDepth = -1;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || t.startsWith("import ") || t.startsWith("package ")) {
                depth += count(t, '{') - count(t, '}');
                continue;
            }
            if (t.startsWith("@")) {
                StringBuilder sb = new StringBuilder(t);
                while (!balanced(sb.toString()) && i + 1 < lines.length) sb.append(' ').append(lines[++i].trim());
                int had = pending.size();
                String rest = OrmMapping.parseAnnotations(sb.toString(), pending, imports, pkg).trim();
                // @field:NotNull, @get:Id - the use-site target is Kotlin's, the annotation the same.
                for (int k = had; k < pending.size(); k++) {
                    OrmMapping.Anno a = pending.get(k);
                    if (a.simple.indexOf(':') >= 0) a.simple = a.simple.substring(a.simple.indexOf(':') + 1);
                }
                if (rest.isEmpty()) continue;
                t = rest;
            }
            Matcher cm = KT_CLASS.matcher(t);
            boolean declares = cm.find() && !t.startsWith("val ") && !t.startsWith("var ");
            if (declares && c != null && depth > classDepth) {
                // A class inside the class - an enum's EntityConverter - kept for what its converter says.
                Matcher conv = KT_CONVERTER.matcher(t);
                if (conv.find()) {
                    JClass nested = new JClass();
                    nested.simple = cm.group(2);
                    nested.pkg = pkg;
                    nested.fqcn = c.fqcn + "." + nested.simple;
                    nested.imports = imports;
                    nested.converterColumn = conv.group(1).replace("?", "");
                    classes.put(nested.fqcn, nested);
                }
                pending.clear();
                depth += count(t, '{') - count(t, '}');
                continue;
            }
            if (declares && (c == null || depth <= classDepth)) {
                if (c != null) {
                    kind(c);
                    classes.put(c.fqcn, c);
                }
                c = new JClass();
                c.simple = cm.group(2);
                c.pkg = pkg;
                c.fqcn = pkg + "." + c.simple;
                c.imports = imports;
                c.annos.addAll(pending);
                if (cm.group(3) != null) c.superName = cm.group(3).replaceAll("\\(.*", "");
                if ("enum".equals(cm.group(1))) {
                    enums.put(c.simple, c.fqcn);
                    enums.put(c.fqcn, c.fqcn);
                }
                Matcher conv = KT_CONVERTER.matcher(t);
                if (conv.find()) c.converterColumn = conv.group(1).replace("?", "");
                pending.clear();
                classDepth = depth;
            } else if (c != null && depth == classDepth + 1) {
                Matcher pm = KT_PROP.matcher(t);
                if (pm.find()) {
                    boolean skip = false;
                    for (OrmMapping.Anno a : pending) skip |= a.simple.equals("Transient") && a.fqcn == null;
                    JField f = new JField();
                    f.name = pm.group(2);
                    String declared = pm.group(3).trim();
                    f.type = declared.replace("?", "").trim();
                    // A Kotlin value type that cannot be null is a JVM primitive, which Hibernate makes NOT NULL.
                    f.primitive = !declared.endsWith("?") && KT_PRIMITIVES.contains(f.type);
                    f.annos.addAll(pending);
                    if (!skip) c.fields.add(f);
                }
                pending.clear();
            } else {
                pending.clear();
            }
            depth += count(t, '{') - count(t, '}');
        }
        if (c != null) {
            kind(c);
            classes.put(c.fqcn, c);
        }
    }

    private static int count(String s, char ch) {
        int n = 0;
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '"') q = !q;
            if (!q && s.charAt(i) == ch) n++;
        }
        return n;
    }

    private static boolean balanced(String s) {
        return count(s, '(') == count(s, ')');
    }

    private static void kind(JClass c) {
        if (c.anno("Entity") != null) c.kind = "entity";
        else if (c.anno("MappedSuperclass") != null) c.kind = "mapped";
        else if (c.anno("Embeddable") != null) c.kind = "embeddable";
    }

    // ---- the schema -------------------------------------------------------------------

    /** Which entities were read, by class name - for the runner, to tell the domains' apart. */
    public List<String> entityClasses() {
        List<String> out = new ArrayList<String>();
        for (JClass c : classes.values()) if ("entity".equals(c.kind)) out.add(c.fqcn);
        return out;
    }

    /** The tables of the entities whose class names are given; another entity they point at is looked up all the same. */
    public Schema schema(List<String> entityClasses) {
        Schema s = new Schema();
        boolean audited = false;
        for (String fqcn : entityClasses) {
            JClass c = classes.get(fqcn);
            if (c == null || !"entity".equals(c.kind)) continue;
            JClass root = inheritanceRoot(c);
            boolean joined = root != c && "JOINED".equals(strategy(root));
            boolean perClass = root != c && "TABLE_PER_CLASS".equals(strategy(root));
            if (root != c && !joined && !perClass) {
                // SINGLE_TABLE: the subclass's columns join its root's table, all of them nullable.
                Table rootTable = s.table(new Table(tableName(root), source(root)));
                List<Mapped> own = columnsOf(c);
                for (Mapped m : own) addTo(rootTable, m.column.nullable(true), m);
                joinTables(c, rootTable, s);
                indexes(c, rootTable);
                elementCollections(c, rootTable, s);
                if (audited(c)) {
                    // Envers keeps the whole hierarchy in the root's audit table too.
                    audited = true;
                    Table at = s.table(new Table(auditName(rootTable.name), rootTable.source + " (audit)"));
                    for (Mapped m : own) if (m.audited) at.column(m.column.nullable(true).identity(false));
                }
                continue;
            }
            Table t = s.table(new Table(tableName(c), source(c)));
            List<Mapped> cols = new ArrayList<Mapped>();
            if (perClass) {
                // TABLE_PER_CLASS: a table of its own, with every column of the entities above it.
                List<JClass> above = new ArrayList<JClass>();
                for (JClass x = superOf(c); x != null; x = superOf(x)) if ("entity".equals(x.kind)) above.add(0, x);
                for (JClass x : above) cols.addAll(columnsOf(x));
            }
            cols.addAll(columnsOf(c));
            List<String> pk = new ArrayList<String>();
            for (Mapped m : cols) {
                addTo(t, m.column, m);
                if (m.key) pk.add(m.column.name);
            }
            if (joined) {
                // JOINED: the subclass's key is its root's, and points at the table of the entity it extends.
                pk.clear();
                for (Mapped m : keyColumns(root)) {
                    t.column(m.column.identity(false));
                    pk.add(m.column.name);
                }
                t.foreignKey(new Table.ForeignKey(Table.constraintName("fk_", t.name, "parent"), pk,
                        tableName(entityParent(c, root)), pk));
            }
            Column discriminator = null;
            if (hasSubclasses(c) && !"JOINED".equals(strategy(c)) && !"TABLE_PER_CLASS".equals(strategy(c))) {
                OrmMapping.Anno dc = c.anno("DiscriminatorColumn");
                String name = dc == null ? "dtype" : physical(text(dc.arg("name"), "dtype"));
                int len = dc == null ? 31 : num(dc.arg("length"), 31);
                discriminator = Column.of(name, ColumnType.TEXT).length(len).nullable(false);
                t.column(discriminator);
            }
            t.primaryKey(pk);
            joinTables(c, t, s);
            indexes(c, t);
            elementCollections(c, t, s);
            sequences(c, s);
            if (audited(c)) {
                audited = true;
                audit(c, joined ? entityParent(c, root) : null, t, discriminator, s);
            }
        }
        if (audited) revisionTable(s);
        return s;
    }

    /** A column with where it came from: a key, a relation's foreign key. */
    private static final class Mapped {
        final Column column;
        final boolean key;
        final String fkTable;
        final String fkColumn;
        final String attribute;
        final boolean audited;

        Mapped(Column column, boolean key, String fkTable, String fkColumn, String attribute, boolean audited) {
            this.column = column;
            this.key = key;
            this.fkTable = fkTable;
            this.fkColumn = fkColumn;
            this.attribute = attribute;
            this.audited = audited;
        }
    }

    private static void addTo(Table t, Column c, Mapped m) {
        t.column(c);
        if (m.fkTable != null) {
            t.foreignKey(new Table.ForeignKey(Table.constraintName("fk_", t.name, m.attribute), Collections.singletonList(c.name),
                    m.fkTable, Collections.singletonList(m.fkColumn)));
        }
    }

    /** The entity's columns: its own fields and those of the mapped superclasses above it, not its entity parents'. */
    private List<Mapped> columnsOf(JClass c) {
        List<JClass> chain = new ArrayList<JClass>();
        for (JClass x = c; x != null && !(x != c && "entity".equals(x.kind)); x = superOf(x)) chain.add(0, x);
        List<Mapped> out = new ArrayList<Mapped>();
        for (JClass x : chain) {
            for (JField f : x.fields) out.addAll(columns(x, f, "", null));
        }
        return out;
    }

    /**
     * The key's columns alone - read off the @Id / @EmbeddedId fields only, so
     * two entities pointing at each other (a one-to-one both ways) do not
     * read each other's relations without end.
     */
    private List<Mapped> keyColumns(JClass entity) {
        List<Mapped> out = new ArrayList<Mapped>();
        JClass root = inheritanceRoot(entity);
        for (JClass x = root; x != null; x = superOf(x)) {
            for (JField f : x.fields) {
                if (!f.has("Id") && !f.has("EmbeddedId")) continue;
                if (f.has("ManyToOne") || f.has("OneToOne")) {
                    warnings.add(entity.simple + "." + f.name + ": a key that is a relation is not read - its columns are left out");
                    continue;
                }
                for (Mapped m : columns(x, f, "", null)) if (m.key) out.add(m);
            }
        }
        return out;
    }

    /** The columns one field maps to; an embedded value gives several, a collection none. */
    private List<Mapped> columns(JClass owner, JField f, String prefix, Map<String, String> overrides) {
        List<Mapped> out = new ArrayList<Mapped>();
        if (f.has("Transient") || f.has("Formula") || f.has("OneToMany") || f.has("ManyToMany")
                || f.has("ElementCollection")) return out;
        boolean audited = !f.has("NotAudited");
        if ((f.has("OneToOne") && f.anno("OneToOne").arg("mappedBy") != null)) return out;

        if (f.has("ManyToOne") || f.has("OneToOne")) {
            JClass target = resolve(owner, generic(f.type));
            if (target == null) {
                warnings.add(owner.simple + "." + f.name + ": " + f.type + " is no entity read here - the relation's column is left out");
                return out;
            }
            List<Mapped> keys = keyColumns(target);
            OrmMapping.Anno jc = f.anno("JoinColumn");
            for (Mapped k : keys) {
                String name = jc != null && jc.arg("name") != null && keys.size() == 1
                        ? physical(text(jc.arg("name"), null))
                        : physical(f.name + "_" + k.column.name);
                boolean required = (jc != null && "false".equals(String.valueOf(jc.arg("nullable"))))
                        || "false".equals(String.valueOf(anyArg(f, "optional"))) || f.has("NotNull");
                Column col = k.column.named(name).nullable(!required);
                out.add(new Mapped(col, f.has("Id"), tableName(target), k.column.name, physical(f.name), audited));
            }
            return out;
        }
        if (f.has("Embedded") || f.has("EmbeddedId") || isEmbeddable(owner, f.type)) {
            JClass type = resolve(owner, f.type);
            if (type == null) {
                warnings.add(owner.simple + "." + f.name + ": the embedded type " + f.type + " was not read");
                return out;
            }
            Map<String, String> names = new HashMap<String, String>();
            if (overrides != null) names.putAll(overrides);
            for (OrmMapping.Anno ov : overrideAnnos(f)) {
                Object col = ov.arg("column");
                String attr = text(ov.arg("name"), null);
                if (attr != null && col instanceof OrmMapping.Anno) {
                    names.put(attr, text(((OrmMapping.Anno) col).arg("name"), attr));
                }
            }
            boolean required = f.has("NotNull") || f.has("EmbeddedId");
            for (JField p : type.fields) {
                OrmMapping.Anno pc = p.anno("Column");
                // The part's own @Column(nullable = false) is Hibernate's NOT NULL wherever the value is;
                // its Bean Validation @NotNull only where the value itself is required.
                boolean explicit = pc != null && "false".equals(String.valueOf(pc.arg("nullable")));
                for (Mapped m : columns(type, p, "", null)) {
                    String name = names.containsKey(p.name) ? physical(names.get(p.name)) : m.column.name;
                    Column col = m.column.named(name);
                    if (!required && !explicit) col = col.nullable(true);
                    out.add(new Mapped(col, f.has("EmbeddedId"), m.fkTable, m.fkColumn, m.attribute, audited));
                }
            }
            return out;
        }

        OrmMapping.Anno column = f.anno("Column");
        String name = physical(column != null && column.arg("name") != null ? text(column.arg("name"), f.name) : f.name);
        Column c = basic(owner, f, name);
        if (c == null) return out;
        boolean key = f.has("Id");
        boolean required = key || f.primitive || f.has("NotNull") || f.has("NotBlank") || f.has("NotEmpty")
                || (column != null && "false".equals(String.valueOf(column.arg("nullable"))));
        c = c.nullable(!required);
        OrmMapping.Anno gv = f.anno("GeneratedValue");
        if (key && gv != null && String.valueOf(gv.arg("strategy")).endsWith("IDENTITY")) c = c.identity(true);
        out.add(new Mapped(c, key, null, null, name, audited));
        return out;
    }

    private static List<OrmMapping.Anno> overrideAnnos(JField f) {
        List<OrmMapping.Anno> out = new ArrayList<OrmMapping.Anno>();
        for (OrmMapping.Anno a : f.annos) {
            if (a.simple.equals("AttributeOverride")) out.add(a);
            if (a.simple.equals("AttributeOverrides")) {
                Object v = a.arg("value");
                if (v instanceof OrmMapping.Anno) out.add((OrmMapping.Anno) v);
                if (v instanceof List) {
                    for (Object o : (List<?>) v) if (o instanceof OrmMapping.Anno) out.add((OrmMapping.Anno) o);
                }
            }
        }
        return out;
    }

    private static Object anyArg(JField f, String key) {
        for (OrmMapping.Anno a : f.annos) if (a.arg(key) != null) return a.arg(key);
        return null;
    }

    /** A value column: the type Hibernate gives the Java type, its converter's column, @Lob, @JdbcTypeCode. */
    private Column basic(JClass owner, JField f, String name) {
        OrmMapping.Anno column = f.anno("Column");
        int length = column != null ? num(column.arg("length"), 0) : 0;
        if (length == 0 && f.has("Size")) length = num(f.anno("Size").arg("max"), 0);
        int precision = column != null ? num(column.arg("precision"), 0) : 0;
        int scale = column != null ? num(column.arg("scale"), 0) : 0;
        if (precision == 0 && f.has("Digits")) {
            int integer = num(f.anno("Digits").arg("integer"), 0);
            scale = num(f.anno("Digits").arg("fraction"), 0);
            precision = integer + scale;
        }
        String type = simpleType(f.type);
        boolean lob = f.has("Lob");

        OrmMapping.Anno jt = f.anno("JdbcTypeCode");
        if (jt != null) {
            String code = String.valueOf(jt.arg("value"));
            code = code.substring(code.lastIndexOf('.') + 1);
            switch (code) {
                case "JSON": return Column.of(name, ColumnType.JSON);
                case "SQLXML": return Column.of(name, ColumnType.XML);
                case "LONG32VARCHAR": case "LONGVARCHAR": case "LONG32NVARCHAR": case "CLOB": case "NCLOB":
                    return Column.of(name, ColumnType.LONG_TEXT);
                case "LONG32VARBINARY": case "BLOB": return Column.of(name, ColumnType.LONG_BINARY);
                default: warnings.add(owner.simple + "." + f.name + ": @JdbcTypeCode(" + code + ") is read as its Java type");
            }
        }
        String definition = column == null ? null : text(column.arg("columnDefinition"), null);
        if (definition != null) {
            String d = definition.trim().toUpperCase(Locale.ROOT);
            if (d.startsWith("TEXT") || d.startsWith("LONGTEXT") || d.startsWith("MEDIUMTEXT")) {
                // A VARCHAR to JDBC; Oracle has no such type, and Hibernate validates "text" there against nothing.
                warnOnce("columnDefinition \"" + definition + "\" (" + owner.simple + "." + f.name + "): Oracle has no TEXT -"
                        + " its schema makes the column a CLOB, which ddl-auto=validate refuses there; use clob or a length");
                return Column.of(name, ColumnType.UNBOUNDED_TEXT);
            }
            if (d.startsWith("CLOB")) return Column.of(name, ColumnType.LONG_TEXT);
            warnings.add(owner.simple + "." + f.name + ": columnDefinition \"" + definition + "\" is the database's own"
                    + " - the column is written for its Java type instead");
        }

        OrmMapping.Anno convert = f.anno("Convert");
        if (convert != null) {
            String conv = String.valueOf(convert.arg("converter"));
            String col = converterColumn(owner, conv);
            if (col != null) type = simpleType(col);
            else {
                warnings.add(owner.simple + "." + f.name + ": the converter " + conv + " was not read - the column is text");
                type = "String";
            }
        } else if (enums.containsKey(type) || enums.containsKey(resolveName(owner, type))) {
            OrmMapping.Anno en = f.anno("Enumerated");
            boolean asText = en != null && String.valueOf(en.arg("value")).endsWith("STRING");
            return asText ? Column.of(name, ColumnType.TEXT).length(length > 0 ? length : 255).ascii(true)
                    : Column.of(name, ColumnType.TINYINT);
        }

        switch (type) {
            case "String": case "char[]": case "Character[]":
                return lob ? Column.of(name, ColumnType.LONG_TEXT) : Column.of(name, ColumnType.TEXT).length(length > 0 ? length : 255);
            case "char": case "Character": return Column.of(name, ColumnType.TEXT).length(1);
            case "Long": case "long": return Column.of(name, ColumnType.BIGINT);
            case "Integer": case "int": return Column.of(name, ColumnType.INTEGER);
            case "Short": case "short": return Column.of(name, ColumnType.SMALLINT);
            case "Byte": case "byte": return Column.of(name, ColumnType.TINYINT);
            case "Boolean": case "boolean": return Column.of(name, ColumnType.BOOLEAN);
            case "Double": case "double": return Column.of(name, ColumnType.DOUBLE);
            case "Float": case "float": return Column.of(name, ColumnType.REAL);
            case "BigDecimal": return Column.of(name, ColumnType.DECIMAL).decimal(precision, scale);
            case "BigInteger": return Column.of(name, ColumnType.DECIMAL).decimal(precision > 0 ? precision : 38, 0);
            case "LocalDate": case "Date": return "Date".equals(type) ? Column.of(name, ColumnType.TIMESTAMP) : Column.of(name, ColumnType.DATE);
            case "LocalTime": return Column.of(name, ColumnType.TIME);
            case "LocalDateTime": case "Timestamp": return Column.of(name, ColumnType.TIMESTAMP);
            case "Instant": case "OffsetDateTime": case "ZonedDateTime": return Column.of(name, ColumnType.TIMESTAMP_TZ);
            case "UUID": return Column.of(name, ColumnType.UUID);
            case "byte[]": case "Byte[]":
                return lob ? Column.of(name, ColumnType.LONG_BINARY) : Column.of(name, ColumnType.BINARY).length(length > 0 ? length : 255);
            default:
                warnings.add(owner.simple + "." + f.name + ": no column type for " + f.type + " - left out");
                return null;
        }
    }

    /** The column type a converter declares (AttributeConverter&lt;X, Y&gt;): the runtime's known ones, or one read. */
    private String converterColumn(JClass owner, String converter) {
        String s = converter.replace("::class.java", "").replace("::class", "").replace(".class", "").trim();
        // The runtime library's converters, which are not among the project's sources.
        if (s.endsWith("Money.JpaConverter")) return "BigDecimal";
        if (s.endsWith("Quantity.JpaConverter")) return "Integer";
        if (s.endsWith("StringArrayConverter") || s.endsWith("IntegerArrayConverter")) return "String";
        JClass c = classes.get(s);
        if (c == null && !Character.isLowerCase(s.charAt(0))) {
            String fq = resolveName(owner, s.contains(".") ? s.substring(0, s.indexOf('.')) : s);
            String rest = s.contains(".") ? s.substring(s.indexOf('.')) : "";
            c = classes.get(fq + rest);
            if (c == null) c = classes.get(owner.pkg + "." + s);
        }
        // A converter of its own file not yet read (one named in full, an orm.xml's).
        if (c == null && Character.isLowerCase(s.charAt(0))) c = readClassFile(s);
        return c == null ? null : c.converterColumn;
    }

    /** The join tables of the entity's many-to-many and unmapped one-to-many collections. */
    private void joinTables(JClass c, Table owner, Schema s) {
        for (JClass x = c; x != null && !(x != c && "entity".equals(x.kind)); x = superOf(x)) {
            for (JField f : x.fields) {
                boolean m2m = f.has("ManyToMany") && f.anno("ManyToMany").arg("mappedBy") == null;
                boolean o2m = f.has("OneToMany") && f.anno("OneToMany").arg("mappedBy") == null && !f.has("JoinColumn");
                if (f.has("OneToMany") && f.anno("OneToMany").arg("mappedBy") == null && f.has("JoinColumn")) {
                    // The target's table holds the key back.
                    JClass target = resolve(x, generic(f.type));
                    if (target == null) continue;
                    Table tt = s.table(new Table(tableName(target), source(target)));
                    for (Mapped k : keyColumns(c)) {
                        String col = physical(text(f.anno("JoinColumn").arg("name"), f.name + "_" + k.column.name));
                        tt.column(k.column.named(col).nullable(true).identity(false));
                        tt.foreignKey(new Table.ForeignKey(Table.constraintName("fk_", tt.name, f.name),
                                Collections.singletonList(col), owner.name, Collections.singletonList(k.column.name)));
                    }
                    continue;
                }
                if (!m2m && !o2m) continue;
                JClass target = resolve(x, generic(f.type));
                if (target == null) {
                    warnings.add(c.simple + "." + f.name + ": " + f.type + " is no entity read here - its join table is left out");
                    continue;
                }
                OrmMapping.Anno jt = f.anno("JoinTable");
                String name = jt != null && jt.arg("name") != null ? physical(text(jt.arg("name"), null))
                        // The owner's table name without its schema: the join table is in the default one.
                        : physical(owner.name.substring(owner.name.lastIndexOf('.') + 1) + "_" + f.name);
                Table t = s.table(new Table(name, source(c) + "." + f.name));
                List<String> pk = new ArrayList<String>();
                // The owner's side: the attribute that maps it back (the entity's name when none does) and its key;
                // the other: the attribute and the target's key.
                String back = m2m ? inverseOf(target, x, f.name) : null;
                for (Mapped k : keyColumns(c)) {
                    String col = physical((back != null ? back : entityName(c)) + "_" + k.column.name);
                    t.column(k.column.named(col).nullable(false));
                    pk.add(col);
                    t.foreignKey(new Table.ForeignKey(Table.constraintName("fk_", name, entityName(c)),
                            Collections.singletonList(col), owner.name, Collections.singletonList(k.column.name)));
                }
                List<String> other = new ArrayList<String>();
                for (Mapped k : keyColumns(target)) {
                    String col = physical(f.name + "_" + k.column.name);
                    t.column(k.column.named(col).nullable(false));
                    other.add(col);
                    t.foreignKey(new Table.ForeignKey(Table.constraintName("fk_", name, f.name),
                            Collections.singletonList(col), tableName(target), Collections.singletonList(k.column.name)));
                }
                if (!m2m) {
                    t.index(new Table.Index(Table.constraintName("uk_", name, f.name), other, true));
                } else if (isSet(f.type)) {
                    // A set's pairs are unique and key the table; a list's may repeat.
                    pk.addAll(other);
                    t.primaryKey(pk);
                }
                if (audited(c) && !f.has("NotAudited")) auditCollection(t, s);
            }
        }
    }

    /** The target's attribute mapped by the owner's many-to-many, or null when the relation is one way. */
    private String inverseOf(JClass target, JClass owner, String attribute) {
        for (JClass x = target; x != null; x = superOf(x)) {
            for (JField g : x.fields) {
                if (!g.has("ManyToMany")) continue;
                String mappedBy = text(g.anno("ManyToMany").arg("mappedBy"), null);
                if (attribute.equals(mappedBy) && resolve(x, generic(g.type)) != null) return g.name;
            }
        }
        return null;
    }

    private static boolean isSet(String type) {
        String raw = simpleType(type.contains("<") ? type.substring(0, type.indexOf('<')) : type);
        return raw.endsWith("Set");
    }

    /** @Table(indexes, uniqueConstraints), @Column(unique = true) and an owning one-to-one's join column. */
    private void indexes(JClass c, Table t) {
        OrmMapping.Anno table = c.anno("Table");
        if (table != null) {
            for (Object o : list(table.arg("indexes"))) {
                if (!(o instanceof OrmMapping.Anno)) continue;
                OrmMapping.Anno i = (OrmMapping.Anno) o;
                List<String> cols = columnList(text(i.arg("columnList"), ""));
                if (cols.isEmpty()) continue;
                boolean unique = "true".equals(String.valueOf(i.arg("unique")));
                String name = text(i.arg("name"), null);
                t.index(new Table.Index(name != null ? name : Table.constraintName(unique ? "uk_" : "ix_", t.name, String.join("_", cols)),
                        cols, unique));
            }
            for (Object o : list(table.arg("uniqueConstraints"))) {
                if (!(o instanceof OrmMapping.Anno)) continue;
                OrmMapping.Anno u = (OrmMapping.Anno) o;
                List<String> cols = new ArrayList<String>();
                for (Object n : list(u.arg("columnNames"))) cols.add(physical(String.valueOf(n)));
                if (cols.isEmpty()) continue;
                String name = text(u.arg("name"), null);
                t.index(new Table.Index(name != null ? name : Table.constraintName("uk_", t.name, String.join("_", cols)), cols, true));
            }
        }
        for (JClass x = c; x != null && !(x != c && "entity".equals(x.kind)); x = superOf(x)) {
            for (JField f : x.fields) {
                if (f.has("OneToOne") && f.anno("OneToOne").arg("mappedBy") == null && !f.has("Id") && !f.has("MapsId")) {
                    // Hibernate keeps a one-to-one one: its join column is unique.
                    List<String> cols = new ArrayList<String>();
                    for (Mapped m : columns(x, f, "", null)) cols.add(m.column.name);
                    if (!cols.isEmpty()) {
                        t.index(new Table.Index(Table.constraintName("uk_", t.name, String.join("_", cols)), cols, true));
                    }
                    continue;
                }
                OrmMapping.Anno col = f.anno("Column");
                if (col == null || !"true".equals(String.valueOf(col.arg("unique"))) || f.has("Id")) continue;
                String name = physical(col.arg("name") != null ? text(col.arg("name"), f.name) : f.name);
                t.index(new Table.Index(Table.constraintName("uk_", t.name, name), Collections.singletonList(name), true));
            }
        }
    }

    private static List<String> columnList(String text) {
        List<String> out = new ArrayList<String>();
        for (String p : text.split(",")) {
            String c = p.trim().split("\\s+")[0];
            if (!c.isEmpty()) out.add(physical(c));
        }
        return out;
    }

    /** A key taken from a sequence: @SequenceGenerator's, or Hibernate's own {entity}_seq. */
    private void sequences(JClass c, Schema s) {
        for (JClass x = c; x != null && !(x != c && "entity".equals(x.kind)); x = superOf(x)) {
            for (JField f : x.fields) {
                OrmMapping.Anno gv = f.anno("GeneratedValue");
                if (gv == null || !f.has("Id")) continue;
                String strategy = String.valueOf(gv.arg("strategy"));
                if (strategy.endsWith("IDENTITY") || strategy.endsWith("UUID") || strategy.endsWith("TABLE")) continue;
                OrmMapping.Anno sg = f.anno("SequenceGenerator");
                if (sg != null) {
                    s.sequence(new Schema.Sequence(physical(text(sg.arg("sequenceName"), text(sg.arg("name"), entityName(c) + "_seq"))),
                            num(sg.arg("initialValue"), 1), num(sg.arg("allocationSize"), 50)));
                } else {
                    s.sequence(new Schema.Sequence(physical(entityName(c) + "_seq"), 1, 50));
                }
            }
        }
    }

    /** The entity a subclass extends - past mapped superclasses between them. */
    private JClass entityParent(JClass c, JClass otherwise) {
        JClass parent = superOf(c);
        while (parent != null && !"entity".equals(parent.kind)) parent = superOf(parent);
        return parent != null ? parent : otherwise;
    }

    private static boolean audited(JClass c) {
        return c.anno("Audited") != null;
    }

    /**
     * Envers' audit table of an audited entity: its key and audited columns,
     * the revision and its type. A JOINED subclass's holds its key, its own
     * columns and the revision, and points at its root's audit row; the type
     * of the revision is said once, in the root's.
     */
    private void audit(JClass c, JClass joinedParent, Table t, Column discriminator, Schema s) {
        Table at = s.table(new Table(auditName(t.name), t.source + " (audit)"));
        String rev = physical(revisionField);
        at.column(Column.of(rev, ColumnType.INTEGER).nullable(false));
        if (joinedParent == null) at.column(Column.of(physical(revisionTypeField), ColumnType.TINYINT));
        for (String key : t.primaryKey()) if (t.column(key) != null) at.column(t.column(key).identity(false));
        for (Mapped m : columnsOf(c)) {
            if (m.key || !m.audited) continue;
            at.column(m.column.nullable(true).identity(false));
        }
        if (discriminator != null) at.column(discriminator);
        List<String> pk = new ArrayList<String>(t.primaryKey());
        pk.add(rev);
        at.primaryKey(pk);
        if (joinedParent == null) {
            at.foreignKey(new Table.ForeignKey(Table.constraintName("fk_", at.name, "rev"), Collections.singletonList(rev),
                    "revinfo", Collections.singletonList("rev")));
        } else {
            at.foreignKey(new Table.ForeignKey(Table.constraintName("fk_", at.name, "parent"), pk,
                    auditName(tableName(joinedParent)), pk));
        }
    }

    /**
     * The table of an @ElementCollection: {entity}_{attribute} (or
     * @CollectionTable's), the owner's key as {entity}_{key}, and the value -
     * a basic value's column named after the attribute (or @Column's), an
     * embeddable's columns. A Set's rows are keyed by all of them when all are required; a
     * List's by an @OrderColumn when it has one. A Map is not read.
     */
    private static final java.util.Set<String> ELEMENT_SKIPS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "ElementCollection", "OrderColumn", "CollectionTable", "Size", "NotNull", "NotEmpty", "NotBlank"));

    private void elementCollections(JClass c, Table owner, Schema s) {
        for (JClass x = c; x != null && !(x != c && "entity".equals(x.kind)); x = superOf(x)) {
            for (JField f : x.fields) {
                if (!f.has("ElementCollection") || f.has("Transient")) continue;
                String raw = simpleType(f.type.contains("<") ? f.type.substring(0, f.type.indexOf('<')) : f.type);
                if (raw.endsWith("Map")) {
                    warnings.add(c.simple + "." + f.name + ": a Map element collection is not read - its table is left out");
                    continue;
                }
                OrmMapping.Anno ct = f.anno("CollectionTable");
                String name = ct != null && ct.arg("name") != null ? physical(text(ct.arg("name"), null))
                        : physical(entityName(c) + "_" + f.name);
                Table t = s.table(new Table(name, source(c) + "." + f.name));
                List<String> key = new ArrayList<String>();
                List<Mapped> ownerKeys = keyColumns(c);
                Object jc = ct == null ? null : ct.arg("joinColumns");
                List<?> joinColumns = list(jc);
                for (int i = 0; i < ownerKeys.size(); i++) {
                    Mapped k = ownerKeys.get(i);
                    Object given = i < joinColumns.size() ? joinColumns.get(i) : null;
                    String col = given instanceof OrmMapping.Anno && ((OrmMapping.Anno) given).arg("name") != null
                            ? physical(text(((OrmMapping.Anno) given).arg("name"), null))
                            : physical(entityName(c) + "_" + k.column.name);
                    t.column(k.column.named(col).nullable(false).identity(false));
                    key.add(col);
                }
                List<String> referenced = new ArrayList<String>();
                for (Mapped k : ownerKeys) referenced.add(k.column.name);
                t.foreignKey(new Table.ForeignKey(Table.constraintName("fk_", name, entityName(c)), key, owner.name, referenced));
                boolean set = raw.endsWith("Set");
                List<String> values = new ArrayList<String>();
                JField element = new JField();
                element.name = f.name;
                element.type = generic(f.type);
                // The mapping of the value; Bean Validation on the collection is about the collection, not its values.
                for (OrmMapping.Anno a : f.annos) {
                    if (!ELEMENT_SKIPS.contains(a.simple)) element.annos.add(a);
                }
                // Hibernate makes the value's columns nullable unless they say otherwise, and a set's key only of
                // columns that are all required.
                boolean allRequired = true;
                for (Mapped m : columns(x, element, "", null)) {
                    t.column(m.column.identity(false));
                    values.add(m.column.name);
                    allRequired &= !m.column.nullable;
                }
                set &= allRequired && !values.isEmpty();
                OrmMapping.Anno oc = f.anno("OrderColumn");
                List<String> pk = new ArrayList<String>(key);
                if (oc != null) {
                    String order = physical(text(oc.arg("name"), f.name + "_order"));
                    t.column(Column.of(order, ColumnType.INTEGER).nullable(false));
                    pk.add(order);
                    t.primaryKey(pk);
                } else if (set) {
                    pk.addAll(values);
                    t.primaryKey(pk);
                }
                if (audited(c) && !f.has("NotAudited")) auditCollection(t, s);
            }
        }
    }

    /** Envers' audit table of a join or collection table: every column, the revision and its type, keyed by all but the type. */
    private void auditCollection(Table t, Schema s) {
        Table at = s.table(new Table(auditName(t.name), t.source + " (audit)"));
        at.column(Column.of(physical(revisionField), ColumnType.INTEGER).nullable(false));
        for (Column col : t.columns()) at.column(col.nullable(false));
        at.column(Column.of(physical(revisionTypeField), ColumnType.TINYINT));
        List<String> apk = new ArrayList<String>();
        apk.add(physical(revisionField));
        for (Column col : t.columns()) apk.add(col.name);
        at.primaryKey(apk);
    }

    /** The audit table of a table: Envers' prefix and suffix around its name, in the same schema. */
    private String auditName(String table) {
        int dot = table.lastIndexOf('.');
        String schema = dot >= 0 ? table.substring(0, dot + 1) : "";
        return schema + physical(auditPrefix + table.substring(dot + 1) + auditSuffix);
    }

    /** Envers' revision table and the sequence its key is taken from (Hibernate 6). */
    private static void revisionTable(Schema s) {
        Table r = s.table(new Table("revinfo", "Envers"));
        r.column(Column.of("rev", ColumnType.INTEGER).nullable(false));
        r.column(Column.of("revtstmp", ColumnType.BIGINT).nullable(false));
        r.primaryKey("rev");
        s.sequence(new Schema.Sequence("revinfo_seq", 1, 50));
    }

    // ---- names and lookups ----------------------------------------------------------

    /** Spring Boot's CamelCaseToUnderscoresNamingStrategy. */
    static String physical(String name) {
        StringBuilder sb = new StringBuilder(name.replace('.', '_'));
        for (int i = 1; i < sb.length() - 1; i++) {
            if (Character.isLowerCase(sb.charAt(i - 1)) && Character.isUpperCase(sb.charAt(i))
                    && Character.isLowerCase(sb.charAt(i + 1))) {
                sb.insert(i++, '_');
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static String entityName(JClass c) {
        OrmMapping.Anno e = c.anno("Entity");
        String n = e == null ? null : text(e.arg("name"), null);
        return n != null ? n : c.simple;
    }

    private String tableName(JClass c) {
        JClass root = inheritanceRoot(c);
        if (root != c && !"JOINED".equals(strategy(root)) && !"TABLE_PER_CLASS".equals(strategy(root))) c = root;
        OrmMapping.Anno t = c.anno("Table");
        String name = t == null ? null : text(t.arg("name"), null);
        String table = physical(name != null ? name : entityName(c));
        String schema = t == null ? null : text(t.arg("schema"), null);
        return schema != null && !schema.isEmpty() ? physical(schema) + "." + table : table;
    }

    private static String source(JClass c) {
        return c.fqcn;
    }

    private JClass superOf(JClass c) {
        if (c.superName == null) return null;
        return resolve(c, c.superName);
    }

    private JClass inheritanceRoot(JClass c) {
        JClass root = c;
        for (JClass x = superOf(c); x != null; x = superOf(x)) if ("entity".equals(x.kind)) root = x;
        return root;
    }

    private boolean hasSubclasses(JClass c) {
        for (JClass x : classes.values()) {
            if (x != c && "entity".equals(x.kind) && superOf(x) == c) return true;
        }
        return false;
    }

    private static String strategy(JClass c) {
        OrmMapping.Anno i = c.anno("Inheritance");
        if (i == null) return "SINGLE_TABLE";
        String s = String.valueOf(i.arg("strategy"));
        return s.substring(s.lastIndexOf('.') + 1);
    }

    private boolean isEmbeddable(JClass owner, String type) {
        JClass t = resolve(owner, type);
        return t != null && "embeddable".equals(t.kind);
    }

    private JClass resolve(JClass from, String type) {
        String name = resolveName(from, simpleType(type));
        JClass c = classes.get(name);
        if (c != null) return c;
        for (JClass x : classes.values()) if (x.simple.equals(simpleType(type)) && x.kind != null) return x;
        return null;
    }

    private static String resolveName(JClass from, String simple) {
        if (simple.contains(".")) return simple;
        String imported = from.imports.get(simple);
        return imported != null ? imported : from.pkg + "." + simple;
    }

    /** List&lt;OrderLine&gt; -&gt; OrderLine; anything else as it is. */
    private static String generic(String type) {
        int lt = type.indexOf('<');
        return lt < 0 ? type : type.substring(lt + 1, type.lastIndexOf('>')).trim();
    }

    private static String simpleType(String type) {
        String t = type.trim();
        if (t.startsWith("java.") || t.startsWith("kotlin.")) t = t.substring(t.lastIndexOf('.') + 1);
        switch (t) {
            case "Int": return "Integer";
            case "ByteArray": return "byte[]";
            case "Char": return "Character";
            default: return t;
        }
    }

    private static String text(Object v, String otherwise) {
        if (v == null) return otherwise;
        String s = v.toString().trim();
        if (s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s.isEmpty() ? otherwise : s;
    }

    private static int num(Object v, int otherwise) {
        if (v == null) return otherwise;
        try {
            return Integer.parseInt(v.toString().trim().replace("L", ""));
        } catch (NumberFormatException e) {
            return otherwise;
        }
    }

    private static List<?> list(Object v) {
        if (v == null) return Collections.emptyList();
        if (v instanceof List) return (List<?>) v;
        return Collections.singletonList(v);
    }
}
