package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.ECoreModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An ArchUnit test that states the hexagon's boundaries as rules the build
 * checks ({@code archTest: true} in env.yml).
 *
 * Which package is which is read from the namespaces the domain already
 * declares, so the rules are about this project and not about a naming
 * convention:
 *
 *   domain       entity, enumeration, event, domainService, domainRepository
 *   application  service, dto, inboundPort, queryPort
 *   adapters     control
 *   persistence  persistence, repository
 *
 * The domain depends on none of the others; the application depends on
 * neither adapters nor persistence; nothing in the domain names the web. With
 * {@code mapping: xml} the domain also names no persistence technology.
 *
 * The layered code (no domain-model rules selected) gets the layers' rules
 * on the same namespaces instead: the services depend on no web class, and
 * persistence and the entities on neither a service nor the web.
 *
 * Written once: the file is the developer's to add rules to. It needs
 * com.tngtech.archunit:archunit-junit5 on the test classpath.
 */
final class ArchitectureTestSource {

    private ArchitectureTestSource() {
    }

    /** The package the test sits in: the longest one every namespace of the domain shares. */
    static String basePackage(ECoreModel model) {
        List<String> all = new ArrayList<String>();
        for (String key : new String[]{"entity", "service", "dto", "control", "repository"}) {
            String ns = model.getNamespace(key);
            if (ns != null && !ns.isEmpty()) all.add(ns);
        }
        if (all.isEmpty()) return null;
        String[] common = all.get(0).split("\\.");
        int n = common.length;
        for (String ns : all) {
            String[] p = ns.split("\\.");
            int i = 0;
            while (i < n && i < p.length && p[i].equals(common[i])) i++;
            n = i;
        }
        if (n == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append('.');
            sb.append(common[i]);
        }
        return sb.toString();
    }

    static String render(ECoreModel model, String className, String pkg, boolean kotlin, boolean xmlMapping) {
        return render(model, className, pkg, kotlin, xmlMapping, false);
    }

    /**
     * As above; layered says the domain's code is the layered code (no
     * domain-model rules selected), whose services call the repositories and
     * the persistence helper directly - which the hexagon's rule forbids. Its
     * test holds the layers instead: the web uses the services, the services
     * use persistence, the entities and the DTOs, and nothing points back up.
     */
    static String render(
            ECoreModel model, String className, String pkg, boolean kotlin, boolean xmlMapping, boolean layered) {
        Set<String> domain = packages(model, "entity", "enumeration", "event", "domainService", "domainRepository");
        Set<String> application = packages(model, "service", "dto", "inboundPort", "queryPort");
        Set<String> adapters = packages(model, "control");
        Set<String> persistence = packages(model, "persistence", "repository");
        // A namespace two layers share is neither's to forbid.
        application.removeAll(domain);
        adapters.removeAll(domain);
        adapters.removeAll(application);
        persistence.removeAll(domain);
        persistence.removeAll(application);
        if (layered)
            return layered(model, className, pkg, kotlin, xmlMapping, domain, application, adapters, persistence);

        List<String[]> rules = new ArrayList<String[]>();
        Set<String> outsideDomain = new LinkedHashSet<String>();
        outsideDomain.addAll(application);
        outsideDomain.addAll(adapters);
        outsideDomain.addAll(persistence);
        if (!domain.isEmpty() && !outsideDomain.isEmpty()) {
            rules.add(new String[]{"domainDependsOnNothingOutside",
                    "The domain depends on nothing outside it: not the application, not an adapter, not persistence.",
                    join(domain), join(outsideDomain)});
        }
        Set<String> outsideApplication = new LinkedHashSet<String>();
        outsideApplication.addAll(adapters);
        outsideApplication.addAll(persistence);
        if (!application.isEmpty() && !outsideApplication.isEmpty()) {
            rules.add(new String[]{"applicationDependsOnPortsNotAdapters",
                    "The application reaches the outside through its ports, never an adapter's class.",
                    join(application), join(outsideApplication)});
        }
        if (!domain.isEmpty()) {
            rules.add(new String[]{"domainNamesNoWeb",
                    "Nothing in the domain knows it is served over HTTP.",
                    join(domain), "\"org.springframework.web..\", \"jakarta.servlet..\""});
        }
        if (xmlMapping && !domain.isEmpty()) {
            // Envers has no XML form, so an audited domain keeps its annotations.
            boolean audited = audited(model);
            rules.add(new String[]{"domainNamesNoPersistence",
                    "mapping: xml - the domain names no persistence technology; its mapping is META-INF/orm.xml."
                            + (audited ? " Envers (@Audited) stays: it has no XML form." : ""),
                    join(domain), "\"jakarta.persistence..\", \"javax.persistence..\", \"org.hibernate..\"",
                    audited ? "envers" : ""});
        }
        return kotlin ? kotlin(className, pkg, rules) : java(className, pkg, rules);
    }

    /**
     * The layered code's rules, on the same four sets of namespaces: the
     * entities (domain), the services and DTOs (application), the web
     * (control) and persistence.
     */
    private static String layered(
            ECoreModel model,
            String className,
            String pkg,
            boolean kotlin,
            boolean xmlMapping,
            Set<String> entities,
            Set<String> services,
            Set<String> web,
            Set<String> persistence) {
        List<String[]> rules = new ArrayList<String[]>();
        if (!services.isEmpty() && !web.isEmpty()) {
            rules.add(new String[] {
                "servicesDependOnNoWeb",
                "The web layer calls the services, never the other way round.",
                join(services),
                join(web)
            });
        }
        Set<String> below = new LinkedHashSet<String>();
        below.addAll(persistence);
        below.addAll(entities);
        // The DTOs and the outbound ports are what the layers share, not a
        // layer above: a MyBatis mapper takes the search DTO, and an adapter
        // implements its port. They sit under the services' namespace by
        // default, so they are taken out of it rather than out of the rule.
        Set<String> dtos = packages(model, "dto");
        Set<String> above = new LinkedHashSet<String>();
        above.addAll(services);
        above.removeAll(dtos);
        above.addAll(web);
        Set<String> shared = new LinkedHashSet<String>();
        for (String p : packages(model, "dto", "port")) {
            for (String a : above) {
                if (p.startsWith(a + ".")) shared.add(p);
            }
        }
        if (!below.isEmpty() && !above.isEmpty()) {
            rules.add(new String[] {
                "persistenceDependsOnNothingAbove",
                "Persistence and the entities point at nothing above them: not a service, not the web layer."
                        + (shared.isEmpty() ? "" : " The DTOs and the ports are shared."),
                join(below),
                join(above),
                shared.isEmpty() ? "" : "except:" + join(shared)
            });
        }
        if (xmlMapping && !entities.isEmpty()) {
            boolean audited = audited(model);
            rules.add(new String[] {
                "domainNamesNoPersistence",
                "mapping: xml - the entities name no persistence technology; their mapping is META-INF/orm.xml."
                        + (audited ? " Envers (@Audited) stays: it has no XML form." : ""),
                join(entities),
                "\"jakarta.persistence..\", \"javax.persistence..\", \"org.hibernate..\"",
                audited ? "envers" : ""
            });
        }
        String doc = " * The layers, checked on every build: the web uses the services, the services\n"
                + " * use persistence, the entities and the DTOs, and nothing points back up. The\n"
                + " * packages are the namespaces the domain's env.yml declares. Yours from here:\n"
                + " * add rules.\n";
        return kotlin ? kotlin(className, pkg, rules, doc) : java(className, pkg, rules, doc);
    }

    /** The hexagon's comment, as it has always been written. */
    private static final String HEXAGON_DOC =
            " * The hexagon's boundaries, checked on every build. The packages are the\n"
                    + " * namespaces the domain's env.yml declares. Yours from here: add rules.\n";

    private static String java(String className, String pkg, List<String[]> rules) {
        return java(className, pkg, rules, HEXAGON_DOC);
    }

    private static String kotlin(String className, String pkg, List<String[]> rules) {
        return kotlin(className, pkg, rules, HEXAGON_DOC);
    }

    private static String java(String className, String pkg, List<String[]> rules, String doc) {
        StringBuilder s = new StringBuilder();
        s.append("// Initially generated by ElastiCORE; no longer managed - do not remove this line.\n")
                .append("package ")
                .append(pkg)
                .append(";\n\n")
                .append("import com.tngtech.archunit.base.DescribedPredicate;\n")
                .append("import com.tngtech.archunit.core.domain.JavaClass;\n")
                .append("import com.tngtech.archunit.core.importer.ImportOption;\n")
                .append("import com.tngtech.archunit.junit.AnalyzeClasses;\n")
                .append("import com.tngtech.archunit.junit.ArchTest;\n")
                .append("import com.tngtech.archunit.lang.ArchRule;\n\n")
                .append("import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;\n\n")
                .append("/**\n")
                .append(doc)
                .append(" */\n")
                .append("@AnalyzeClasses(packages = \"")
                .append(pkg)
                .append("\", importOptions = ImportOption.DoNotIncludeTests.class)\n")
                .append("class ")
                .append(className)
                .append(" {\n");
        for (String[] r : rules) {
            s.append("\n    /** ").append(r[1]).append(" */\n")
             .append("    @ArchTest\n")
             .append("    static final ArchRule ").append(r[0]).append(" = noClasses()\n")
             .append("            .that().resideInAnyPackage(").append(r[2]).append(")\n")
             .append("            .should().").append(should(r)).append(";\n");
        }
        s.append("}\n");
        return s.toString();
    }

    private static String kotlin(String className, String pkg, List<String[]> rules, String doc) {
        StringBuilder s = new StringBuilder();
        s.append("// Initially generated by ElastiCORE; no longer managed - do not remove this line.\n")
                .append("package ")
                .append(pkg)
                .append("\n\n")
                .append("import com.tngtech.archunit.base.DescribedPredicate\n")
                .append("import com.tngtech.archunit.core.domain.JavaClass\n")
                .append("import com.tngtech.archunit.core.importer.ImportOption\n")
                .append("import com.tngtech.archunit.junit.AnalyzeClasses\n")
                .append("import com.tngtech.archunit.junit.ArchTest\n")
                .append("import com.tngtech.archunit.lang.ArchRule\n")
                .append("import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses\n\n")
                .append("/**\n")
                .append(doc)
                .append(" */\n")
                .append("@AnalyzeClasses(packages = [\"")
                .append(pkg)
                .append("\"], importOptions = [ImportOption.DoNotIncludeTests::class])\n")
                .append("class ")
                .append(className)
                .append(" {\n");
        for (String[] r : rules) {
            s.append("\n    /** ").append(r[1]).append(" */\n")
             .append("    @ArchTest\n")
             .append("    val ").append(r[0]).append(": ArchRule = noClasses()\n")
             .append("        .that().resideInAnyPackage(").append(r[2]).append(")\n")
             .append("        .should().").append(should(r)).append("\n");
        }
        s.append("}\n");
        return s.toString();
    }

    /** What the rule forbids depending on: the packages, less Envers where the domain is audited. */
    private static String should(String[] r) {
        if (r.length >= 5 && r[4].startsWith("except:")) {
            return "dependOnClassesThat(JavaClass.Predicates.resideInAnyPackage(" + r[3] + ")"
                    + ".and(DescribedPredicate.not(JavaClass.Predicates.resideInAnyPackage("
                    + r[4].substring("except:".length()) + "))))";
        }
        if (r.length < 5 || !"envers".equals(r[4])) {
            return "dependOnClassesThat().resideInAnyPackage(" + r[3] + ")";
        }
        return "dependOnClassesThat(JavaClass.Predicates.resideInAnyPackage(" + r[3] + ")"
                + ".and(DescribedPredicate.not(JavaClass.Predicates.resideInAPackage(\"org.hibernate.envers..\"))))";
    }

    private static boolean audited(ECoreModel model) {
        io.elasticore.base.model.ModelComponentItems<io.elasticore.base.model.entity.Entity> all =
                model.getEntityModels().getItems();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getMetaInfo().hasMetaAnnotation("audited")) return true;
        }
        return false;
    }

    private static Set<String> packages(ECoreModel model, String... keys) {
        Set<String> out = new LinkedHashSet<String>();
        for (String k : keys) {
            String ns = model.getNamespace(k);
            if (ns != null && !ns.isEmpty()) out.add(ns);
        }
        return out;
    }

    private static String join(Set<String> packages) {
        StringBuilder sb = new StringBuilder();
        for (String p : packages) {
            // "a.b.." already covers "a.b.c..".
            boolean covered = false;
            for (String other : packages) {
                if (!other.equals(p) && p.startsWith(other + ".")) covered = true;
            }
            if (covered) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append('"').append(p).append("..\"");
        }
        return sb.toString();
    }
}
