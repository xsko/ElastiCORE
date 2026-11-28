package io.elasticore.base.model.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.elasticore.base.util.ConsoleLog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * blueprint/env.yml: the settings the domains of one blueprint folder share,
 * written once instead of in every domain's env.yml (issue 248).
 *
 * <p>Only a project that has the file is touched; without it nothing here runs
 * and every domain is read as before. The file is a lower layer of each
 * domain's env.yml:
 *
 * <pre>
 *   the domain's env.yml  >  blueprint/env.yml  >  the build (elasticore { }, -Delasticore.*)  >  the defaults
 * </pre>
 *
 * It is merged into the domain's own settings before anything reads them
 * (FileBasedModelLoader), so every reader of a domain's settings - the rule
 * engine, the 2.x generator, the annotation processor, a multi-module build
 * with one root file per blueprint folder - takes it the same way.
 *
 * <ul>
 *   <li>A map ({@code config:}, {@code annotations:}, ...) merges key by key and
 *       the domain wins. A list or a single value is replaced as a whole. A key
 *       the domain writes with no value takes the root's. There is no syntax
 *       for removing a key: a domain writes its own value instead.</li>
 *   <li>{@code namespace:} in the root holds patterns ({@code {base}},
 *       {@code {domain}}) that switch nothing on. A key the domain writes with
 *       no value takes the root's pattern; a key with a value keeps it; a key
 *       with no value and no pattern is said and generates nothing, as before;
 *       a domain without {@code namespace:} takes none.</li>
 *   <li>{@code enable} and {@code config.domainName} are one per domain: in the
 *       root file they are said and ignored.</li>
 * </ul>
 */
public final class RootEnv {

    /** The name of the root file, the same as a domain's. */
    public static final String FILE_NAME = "env.yml";

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    private static final String NAMESPACE = "namespace";
    private static final String CONFIG = "config";
    private static final String BASE = "base";

    private RootEnv() {}

    /** The root file of the blueprint folder holding this domain folder, or null when there is none. */
    public static File fileFor(File domainDir) {
        if (domainDir == null) return null;
        return fileIn(domainDir.getAbsoluteFile().getParentFile());
    }

    /** The env.yml directly in this blueprint folder, or null when there is none. */
    public static File fileIn(File blueprintDir) {
        if (blueprintDir == null) return null;
        File file = new File(blueprintDir, FILE_NAME);
        return file.isFile() ? file : null;
    }

    /** A root file as read: the settings it gives the domains, and the per-domain keys it held. */
    public static final class Root {
        private final Map<String, Object> settings;
        private final List<String> ignored;

        Root(Map<String, Object> settings, List<String> ignored) {
            this.settings = settings;
            this.ignored = ignored;
        }

        /** What the domains inherit, without enable and config.domainName. */
        public Map<String, Object> settings() {
            return settings;
        }

        /** The per-domain keys the file held (enable, config.domainName), which are ignored. */
        public List<String> ignored() {
            return ignored;
        }

        /** How many values the file gives: a list counts once, a key with no value not at all. */
        public int count() {
            return countLeaves(settings);
        }
    }

    /**
     * Reads a root file. Its section is elasticore: (or env:, as a domain's may
     * be); a file without one gives nothing. A file that is no YAML stops the
     * load, as a domain's env.yml does.
     */
    public static Root read(File rootFile) {
        Object whole = null;
        if (rootFile.length() > 0) {
            try {
                whole = MAPPER.readValue(rootFile, Object.class);
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        rootFile.getPath() + " is not a readable env.yml: " + e.getMessage(), e);
            }
        }
        Object section = null;
        if (whole instanceof Map) {
            section = ((Map<?, ?>) whole).get("env");
            if (section == null) section = ((Map<?, ?>) whole).get("elasticore");
        }
        Map<String, Object> settings = new LinkedHashMap<String, Object>();
        List<String> ignored = new ArrayList<String>();
        if (section instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) section).entrySet()) {
                String key = String.valueOf(e.getKey());
                if ("enable".equals(key)) {
                    ignored.add(key);
                    continue;
                }
                Object value = copy(e.getValue());
                if (CONFIG.equals(key) && value instanceof Map && ((Map<?, ?>) value).containsKey("domainName")) {
                    ((Map<?, ?>) value).remove("domainName");
                    ignored.add("config.domainName");
                }
                settings.put(key, value);
            }
        }
        return new Root(settings, ignored);
    }

    /** One domain's settings with the root file applied. */
    public static final class Merged {
        private final Map<String, Object> env;
        private final Set<String> inherited;
        private final List<String> warnings;

        Merged(Map<String, Object> env, Set<String> inherited, List<String> warnings) {
            this.env = env;
            this.inherited = inherited;
            this.warnings = warnings;
        }

        /** The domain's elasticore: section as the readers take it. */
        public Map<String, Object> env() {
            return env;
        }

        /** The dotted keys whose value came from the root file (config.mode, namespace.port). */
        public Set<String> inherited() {
            return inherited;
        }

        /** What to say about this domain: a namespace key with no value and no pattern. */
        public List<String> warnings() {
            return warnings;
        }
    }

    /**
     * The domain's section with the root's settings under it.
     *
     * @param root       what the root file gives (Root.settings())
     * @param domain     the domain's elasticore: section as read
     * @param domainName what {domain} stands for: the domain's domainName, else its folder name
     */
    public static Merged apply(Map<String, Object> root, Map<String, Object> domain, String domainName) {
        Set<String> inherited = new LinkedHashSet<String>();
        List<String> warnings = new ArrayList<String>();
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        Map<String, Object> own = domain == null ? Collections.<String, Object>emptyMap() : domain;
        for (Map.Entry<String, Object> e : root.entrySet()) {
            String key = e.getKey();
            if (NAMESPACE.equals(key)) continue;
            if (own.containsKey(key)) out.put(key, merge(e.getValue(), own.get(key), key, inherited));
            else out.put(key, inherit(e.getValue(), key, inherited));
        }
        for (Map.Entry<String, Object> e : own.entrySet()) {
            String key = e.getKey();
            if (NAMESPACE.equals(key)) {
                out.put(key, namespace(root.get(NAMESPACE), e.getValue(), domainName, inherited, warnings));
            } else if (!out.containsKey(key)) {
                out.put(key, e.getValue());
            }
        }
        return new Merged(out, inherited, warnings);
    }

    /** The domain's value over the root's: maps key by key, anything else whole; no value takes the root's. */
    @SuppressWarnings("unchecked")
    private static Object merge(Object rootValue, Object domainValue, String path, Set<String> inherited) {
        if (domainValue == null) return inherit(rootValue, path, inherited);
        if (!(domainValue instanceof Map) || !(rootValue instanceof Map)) return domainValue;
        Map<String, Object> rootMap = (Map<String, Object>) rootValue;
        Map<String, Object> domainMap = (Map<String, Object>) domainValue;
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> e : rootMap.entrySet()) {
            String key = e.getKey();
            String child = path + "." + key;
            if (domainMap.containsKey(key)) out.put(key, merge(e.getValue(), domainMap.get(key), child, inherited));
            else out.put(key, inherit(e.getValue(), child, inherited));
        }
        for (Map.Entry<String, Object> e : domainMap.entrySet()) {
            if (!out.containsKey(e.getKey())) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /** A copy of the root's value, every value in it recorded as inherited. */
    @SuppressWarnings("unchecked")
    private static Object inherit(Object value, String path, Set<String> inherited) {
        if (value == null) return null;
        if (value instanceof Map) {
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                out.put(e.getKey(), inherit(e.getValue(), path + "." + e.getKey(), inherited));
            }
            return out;
        }
        inherited.add(path);
        return copy(value);
    }

    /**
     * The domain's namespace: what it names is what is generated; a key it
     * names with no value is placed by the root's pattern.
     */
    private static Object namespace(
            Object rootValue, Object domainValue, String domainName, Set<String> inherited, List<String> warnings) {
        if (!(domainValue instanceof Map)) return domainValue;
        Map<?, ?> patterns = rootValue instanceof Map ? (Map<?, ?>) rootValue : Collections.emptyMap();
        String base = text(patterns.get(BASE));
        if (base != null) base = base.replace("{domain}", domainName);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) domainValue).entrySet()) {
            String key = String.valueOf(e.getKey());
            if (e.getValue() != null) {
                out.put(key, e.getValue());
                continue;
            }
            String pattern = BASE.equals(key) ? base : text(patterns.get(key));
            String resolved = pattern == null ? null : resolve(pattern, base, domainName);
            if (resolved == null) {
                warnings.add(
                        pattern == null
                                ? "namespace." + key + " has no value, and blueprint/env.yml has no pattern for it"
                                        + " - nothing is generated for it. Write the package here, or a pattern for "
                                        + key + " in blueprint/env.yml."
                                : "namespace." + key + " takes the pattern \"" + pattern + "\" of blueprint/env.yml,"
                                        + " which uses {base}, and blueprint/env.yml has no namespace.base - nothing is"
                                        + " generated for it.");
                out.put(key, null);
                continue;
            }
            out.put(key, resolved);
            inherited.add(NAMESPACE + "." + key);
        }
        return out;
    }

    /** A pattern with {base} and {domain} put in; null when it uses {base} and there is none. */
    static String resolve(String pattern, String base, String domainName) {
        String out = pattern;
        if (out.contains("{base}")) {
            if (base == null) return null;
            out = out.replace("{base}", base);
        }
        return out.replace("{domain}", domainName);
    }

    private static String text(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    @SuppressWarnings("unchecked")
    private static Object copy(Object value) {
        if (value instanceof Map) {
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet())
                out.put(String.valueOf(e.getKey()), copy(e.getValue()));
            return out;
        }
        if (value instanceof List) {
            List<Object> out = new ArrayList<Object>();
            for (Object each : (List<Object>) value) out.add(copy(each));
            return out;
        }
        return value;
    }

    private static int countLeaves(Object value) {
        if (value == null) return 0;
        if (!(value instanceof Map)) return 1;
        int n = 0;
        for (Object each : ((Map<?, ?>) value).values()) n += countLeaves(each);
        return n;
    }

    /**
     * The one line a load of a blueprint folder says when it has a root file,
     * after its per-domain keys are said to be ignored. Nothing without the file.
     *
     * @param blueprintDir the blueprint folder
     * @param label        how the file is named in the line (blueprint/env.yml, or with its module)
     * @param domains      how many domains of the folder were loaded
     */
    public static void announce(File blueprintDir, String label, int domains) {
        File file = fileIn(blueprintDir);
        if (file == null) return;
        Root root = read(file);
        for (String key : root.ignored()) {
            ConsoleLog.printWarn("[ElastiCORE] " + label + ": " + key + " is one per domain - ignored here;"
                    + " write it in each domain's env.yml");
        }
        ConsoleLog.print("[ElastiCORE] " + line(label, root.count(), domains));
    }

    /** "blueprint/env.yml: 6 settings inherited by 9 domains". */
    static String line(String label, int settings, int domains) {
        return label + ": " + settings + (settings == 1 ? " setting" : " settings") + " inherited by " + domains
                + (domains == 1 ? " domain" : " domains");
    }

    /**
     * -PlistRules: a domain's settings as its readers take them, those from the
     * root file marked. Empty when the blueprint folder has no root file, so a
     * listing without one is what it was.
     */
    public static List<String> listing(File domainDir) {
        List<String> lines = new ArrayList<String>();
        File rootFile = fileFor(domainDir);
        File own = domainDir == null ? null : new File(domainDir, FILE_NAME);
        if (rootFile == null || !own.isFile()) return lines;
        Map<String, Object> section = sectionOf(own);
        Object config = section.get(CONFIG);
        Object named = config instanceof Map ? ((Map<?, ?>) config).get("domainName") : null;
        String domainName = named == null ? domainDir.getName() : String.valueOf(named);
        Merged merged = apply(read(rootFile).settings(), section, domainName);
        lines.add("  settings (* from blueprint/env.yml):");
        for (Map.Entry<String, Object> e : merged.env().entrySet()) {
            if ("enable".equals(e.getKey())) continue;
            leaves(e.getKey(), e.getValue(), merged.inherited(), lines);
        }
        return lines;
    }

    private static void leaves(String path, Object value, Set<String> inherited, List<String> lines) {
        if (value instanceof Map && !((Map<?, ?>) value).isEmpty()) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                leaves(path + "." + e.getKey(), e.getValue(), inherited, lines);
            }
            return;
        }
        lines.add("    " + (inherited.contains(path) ? "* " : "  ") + path + " = " + value);
    }

    /** A domain env.yml's section (env:, else elasticore:), as the loader picks it; empty when none. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> sectionOf(File envFile) {
        try {
            Object whole = MAPPER.readValue(envFile, Object.class);
            if (whole instanceof Map) {
                Object section = ((Map<?, ?>) whole).get("env");
                if (section == null) section = ((Map<?, ?>) whole).get("elasticore");
                if (section instanceof Map) return (Map<String, Object>) section;
            }
        } catch (IOException e) {
            // The load said it already; the listing shows nothing for it.
        }
        return new LinkedHashMap<String, Object>();
    }
}
