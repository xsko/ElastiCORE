package io.elasticore.base.model.pub.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Recognizes the endpoint methods of a rendered controller source as named
 * sections, so rules can drop individual methods or append new ones without
 * splitting the template file itself.
 *
 * A section starts at a line whose trimmed form begins with "@Operation" and
 * runs until the next section start (trailing blank lines belong to the
 * section). The class-closing brace is the footer. Section ids are derived
 * from the mapping annotation inside the section:
 *
 *   create, read, search, update, batchUpdate, deleteBatch, deleteById,
 *   schemaInfo
 *
 * When neither exclusions nor additions are requested the input is returned
 * untouched, so default output stays byte-identical.
 */
public final class MethodSections {

    private MethodSections() {
    }

    public static String apply(String rendered, Set<String> excluded, List<String> additions) {
        if ((excluded == null || excluded.isEmpty()) && (additions == null || additions.isEmpty())) {
            return rendered;
        }

        List<String> lines = new ArrayList<String>();
        for (String s : rendered.replace("\r\n", "\n").split("\n", -1)) lines.add(s);

        // Footer: the last line that is exactly "}" (class closing brace).
        int footer = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).equals("}")) { footer = i; break; }
        }
        if (footer < 0) return rendered;

        // Sections live inside the class body: find the class declaration first
        // so the file-header javadoc is never mistaken for a method section.
        int classDecl = -1;
        for (int i = 0; i < footer; i++) {
            String t = lines.get(i).trim();
            // Java opens the body on the declaration line; Kotlin may put a
            // constructor between them - "class X(" ... ") {" - so the body
            // starts at the first line that opens it once a class is declared.
            // A declaration line, not a comment or javadoc that mentions a class.
            boolean declares = !t.startsWith("*") && !t.startsWith("/") && !t.startsWith("@")
                    && t.matches("^((public|protected|private|internal|open|abstract|final|data|sealed|enum|static)\\s+)*(class|enum|interface|record|object)\\s+.*");
            if (declares && t.endsWith("{")) { classDecl = i; break; }
            if (declares) {
                for (int j = i; j < footer; j++) {
                    if (lines.get(j).trim().endsWith("{")) { classDecl = j; break; }
                }
                break;
            }
        }
        if (classDecl < 0) return rendered;

        // Section starts: "@Operation" (controller style) or a method javadoc
        // "/**" (service style), after the class declaration.
        List<Integer> starts = new ArrayList<Integer>();
        for (int i = classDecl + 1; i < footer; i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("@Operation") || t.startsWith("/**")) starts.add(i);
        }
        // A class without method sections (an entity with only fields and
        // accessors) has nothing to exclude, but a rule's added method still
        // goes before its closing brace.
        boolean hasAdditions = additions != null && !additions.isEmpty();
        if (starts.isEmpty() && !hasAdditions) return rendered;

        StringBuilder out = new StringBuilder();
        int bodyEnd = starts.isEmpty() ? footer : starts.get(0);
        for (int i = 0; i < bodyEnd; i++) {
            out.append(lines.get(i)).append('\n');
        }
        for (int s = 0; s < starts.size(); s++) {
            int from = starts.get(s);
            int to = (s + 1 < starts.size()) ? starts.get(s + 1) : footer;
            String id = sectionId(lines, from, to);
            if (excluded != null && excluded.contains(id)) continue;
            for (int i = from; i < to; i++) {
                out.append(lines.get(i)).append('\n');
            }
        }
        if (additions != null) {
            for (String method : additions) {
                // One blank line between methods: the section before ends
                // with its own line break.
                out.append('\n');
                for (String l : method.replace("\r\n", "\n").split("\n", -1)) {
                    out.append(l).append('\n');
                }
            }
        }
        for (int i = footer; i < lines.size(); i++) {
            out.append(lines.get(i));
            if (i < lines.size() - 1) out.append('\n');
        }
        return out.toString();
    }

    private static final java.util.regex.Pattern SIGNATURE =
            java.util.regex.Pattern.compile("^(?:public|protected)\\s+[^=]*?(\\w+)\\(");

    /**
     * Derives the section id: the Spring mapping annotation for controller
     * sections, otherwise the method name from the signature line (overloads
     * share one id, so excluding e.g. "delete" drops every overload).
     */
    static String sectionId(List<String> lines, int from, int to) {
        for (int i = from; i < to; i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("@GetMapping(\"/schema/info\")")) return "schemaInfo";
            if (t.startsWith("@PostMapping(\"/search\")")) return "search";
            if (t.startsWith("@PostMapping(\"/delete\")")) return "deleteBatch";
            if (t.startsWith("@PutMapping(\"/batch\")")) return "batchUpdate";
            if (t.equals("@PostMapping")) return "create";
            if (t.equals("@PutMapping")) return "update";
            if (t.startsWith("@DeleteMapping")) return "deleteById";
            if (t.startsWith("@GetMapping(")) return "read";
        }
        for (int i = from; i < to; i++) {
            java.util.regex.Matcher m = SIGNATURE.matcher(lines.get(i).trim());
            if (m.find()) return m.group(1);
        }
        return "unknown";
    }
}
