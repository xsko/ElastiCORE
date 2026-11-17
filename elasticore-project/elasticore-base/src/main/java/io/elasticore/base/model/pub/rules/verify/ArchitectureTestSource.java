package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.ECoreModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
 * Where the namespaces are laid out by segment under the test's base package
 * - the domain's under base.domain, the application's under base.application
 * - the packages the project's own code adds there are placed too, by their
 * first segment after the base (issue 173): domain, application, adapter or
 * control's segment (adapter.out, infrastructure or the repository's segment
 * go with persistence: the outbound side). A package it cannot place is
 * named by the runner, once, rather than guessed. A layered layout, whose
 * segments name no layers, is read from the namespaces alone.
 *
 * The domain depends on none of the others; the application depends on
 * neither adapters nor persistence; nothing in the domain names the web. With
 * {@code mapping: xml} the domain also names no persistence technology.
 *
 * Written once: the file is the developer's to add rules to. It needs
 * com.tngtech.archunit:archunit-junit5 on the test classpath.
 */
final class ArchitectureTestSource {

    private ArchitectureTestSource() {
    }

    /** The four places a package can go; each layer's rules forbid the ones outside it. */
    enum Layer {
        DOMAIN,
        APPLICATION,
        ADAPTER,
        PERSISTENCE
    }

    /** The layers' packages: the namespaces, then the project's own packages placed among them. */
    static final class Layers {
        final Set<String> domain;
        final Set<String> application;
        final Set<String> adapters;
        final Set<String> persistence;
        /** The project's own packages placed, in order. */
        final List<String> placed = new ArrayList<String>();
        /** The project's own packages under the base that no segment places. */
        final List<String> unplaced = new ArrayList<String>();

        Layers(ECoreModel model) {
            domain = packages(model, "entity", "enumeration", "event", "domainService", "domainRepository");
            application = packages(model, "service", "dto", "inboundPort", "queryPort");
            adapters = packages(model, "control");
            persistence = packages(model, "persistence", "repository");
            // A namespace two layers share is neither's to forbid.
            application.removeAll(domain);
            adapters.removeAll(domain);
            adapters.removeAll(application);
            persistence.removeAll(domain);
            persistence.removeAll(application);
        }

        private Set<String> of(Layer layer) {
            switch (layer) {
                case DOMAIN:
                    return domain;
                case APPLICATION:
                    return application;
                case ADAPTER:
                    return adapters;
                default:
                    return persistence;
            }
        }
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

    /**
     * The layers of the domain, with the packages of the project's own code under the base placed by their
     * first segment - where the namespaces name their layers that way. A package under a namespace is that
     * namespace's already; the base itself (the application class) is in no layer.
     */
    static Layers layers(ECoreModel model, String base, Collection<String> sourcePackages) {
        Layers layers = new Layers(model);
        Map<String, Layer> segments = base == null ? null : segments(model, base);
        if (segments == null || sourcePackages == null) return layers;
        Set<String> namespaces = new LinkedHashSet<String>();
        for (Layer layer : Layer.values()) namespaces.addAll(layers.of(layer));
        for (String p : new TreeSet<String>(sourcePackages)) {
            if (!p.startsWith(base + ".") || under(p, namespaces)) continue;
            String rest = p.substring(base.length() + 1);
            Layer layer = under(rest, "adapter.out") ? Layer.PERSISTENCE : segments.get(head(rest));
            if (layer == null) {
                layers.unplaced.add(p);
            } else {
                layers.of(layer).add(p);
                layers.placed.add(p);
            }
        }
        return layers;
    }

    /**
     * Which first segment after the base names which layer, or null when the namespaces are not laid out that
     * way: the domain's entities under base.domain and the application's services under base.application.
     */
    static Map<String, Layer> segments(ECoreModel model, String base) {
        if (!under(model.getNamespace("entity"), base + ".domain")
                || !under(model.getNamespace("service"), base + ".application")) {
            return null;
        }
        Map<String, Layer> segments = new LinkedHashMap<String, Layer>();
        segments.put("domain", Layer.DOMAIN);
        segments.put("application", Layer.APPLICATION);
        segments.put("adapter", Layer.ADAPTER);
        segments.put("infrastructure", Layer.PERSISTENCE);
        // Where the project put its web and its persistence: interfaces.rest, say.
        putIfFree(segments, firstSegment(model.getNamespace("control"), base), Layer.ADAPTER);
        putIfFree(segments, firstSegment(model.getNamespace("repository"), base), Layer.PERSISTENCE);
        putIfFree(segments, firstSegment(model.getNamespace("persistence"), base), Layer.PERSISTENCE);
        return segments;
    }

    private static void putIfFree(Map<String, Layer> segments, String segment, Layer layer) {
        if (segment != null && !segments.containsKey(segment)) segments.put(segment, layer);
    }

    private static String firstSegment(String ns, String base) {
        if (ns == null || !ns.startsWith(base + ".")) return null;
        return head(ns.substring(base.length() + 1));
    }

    /** A dotted name's first segment: the whole of it when it has no dot. */
    private static String head(String dotted) {
        int dot = dotted.indexOf('.');
        return dot < 0 ? dotted : dotted.substring(0, dot);
    }

    private static boolean under(String p, String prefix) {
        return p != null && (p.equals(prefix) || p.startsWith(prefix + "."));
    }

    private static boolean under(String p, Set<String> namespaces) {
        for (String ns : namespaces) {
            if (under(p, ns)) return true;
        }
        return false;
    }

    static String render(
            ECoreModel model, String className, String pkg, boolean kotlin, boolean xmlMapping, Layers layers) {
        Set<String> domain = layers.domain;
        Set<String> application = layers.application;
        Set<String> adapters = layers.adapters;
        Set<String> persistence = layers.persistence;

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
        // The same words as before where nothing of the project's own was placed.
        String source = layers.placed.isEmpty()
                ? " * namespaces the domain's env.yml declares. Yours from here: add rules.\n"
                : " * namespaces the domain's env.yml declares, and the packages of your own\n"
                        + " * code under them, by their first segment. Yours from here: add rules.\n";
        return kotlin ? kotlin(className, pkg, rules, source) : java(className, pkg, rules, source);
    }

    private static String java(String className, String pkg, List<String[]> rules, String source) {
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
                .append(" * The hexagon's boundaries, checked on every build. The packages are the\n")
                .append(source)
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

    private static String kotlin(String className, String pkg, List<String[]> rules, String source) {
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
                .append(" * The hexagon's boundaries, checked on every build. The packages are the\n")
                .append(source)
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
