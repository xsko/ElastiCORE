package io.elasticore.base.model.pub.rules;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The methods the developer's half of a split entity - the class with the
 * entity's name beside its regenerated base - overrides, as read from the file
 * on disk. The runner reads the file and hands the names to the rules through
 * the entity's config, so a rule can tell a command the developer took over
 * from one left to the generated code (issue 229).
 */
public final class BehaviorOverrides {

    /** The entity config key: the overridden method names, comma-joined. */
    public static final String CONFIG = "behavior.overridden";

    private static final Pattern JAVA = Pattern.compile("\\bvoid\\s+(\\w+)\\s*\\(");
    private static final Pattern KOTLIN = Pattern.compile("\\boverride\\s+fun\\s+(\\w+)\\s*\\(");

    private BehaviorOverrides() {}

    /**
     * The methods a source declares as overrides of a command: "void name(" in
     * Java, "override fun name(" in Kotlin. Comments are left out, so a note
     * that names a method is not one.
     */
    public static Set<String> in(String source, boolean kotlin) {
        Set<String> out = new TreeSet<String>();
        if (source == null) return out;
        String code = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
        Matcher m = (kotlin ? KOTLIN : JAVA).matcher(code);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** Whether the entity's behavior class overrides the method, as the runner read it. */
    public static boolean overrides(EntitySpec e, String method) {
        String names = e.config(CONFIG);
        if (names == null || names.isEmpty()) return false;
        return Arrays.asList(names.split(",")).contains(method);
    }
}
