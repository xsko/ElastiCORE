package io.elasticore.base.model.pub.rules.lang.java;

import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.base.SearchDtoBaseRules;
import io.elasticore.base.model.pub.rules.base.SearchSortList;
import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.util.CodeTemplate;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Full-file search DTO rendering for the 3.0 clean mode. Shares v3/dto.tmpl
 * with {@link DtoCleanFileRules}, as the compatibility mode shares its own.
 *
 * The search properties come from {@link SearchDtoBaseRules}; this rule
 * shapes the class around them and, below the search properties, the sort
 * column and - when the search pages - the page number and size, each
 * declared by an interface on the class.
 */
public class SearchDtoCleanFileRules implements CodeRules {

    @Rule(value = "v3-srchdto-file", target = "srchdto", order = 90, phase = Rule.Phase.TAIL)
    void file(EntitySpec e) {
        SourceCode c = ctx(e);
        String className = e.searchDtoClassName();

        c.template("dto")
         .ensureClassAnnotation(AnnoCode.of("lombok.Getter"))
         .ensureClassAnnotation(AnnoCode.of("lombok.Setter"))
         .ensureClassAnnotation(AnnoCode.of("lombok.experimental.SuperBuilder"))
         .ensureClassAnnotation(AnnoCode.of("lombok.NoArgsConstructor"))
         .ensureClassAnnotation(AnnoCode.of("lombok.AllArgsConstructor"));

        for (FieldSpec f : e.fields()) {
            RuleText.registerFieldTypeImport(c, e, f, e.pkg("dto"));
        }
        sortAndPageImports(c, e);

        c.var("packageName", e.pkg("dto"))
         .var("className", className)
         .var("abstract", e.hasMeta("abstract") ? "abstract" : "")
         .var("extendInfo", "")
         .var("implementInfo", implementClause(e))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", RuleText.stripTrailing(c.renderImports()));

        body(e, c);
    }

    /**
     * Every search DTO sorts; one with a page size also pages. The interfaces
     * say so, and a project that named its own in env.yml gets that name.
     */
    private String implementClause(EntitySpec e) {
        StringBuilder sb = new StringBuilder("implements java.io.Serializable");
        sb.append(", ").append(interfaceName(e.sortableInterface(), "SortableObject"));
        if (e.searchPageSize() != null) {
            sb.append(", ").append(interfaceName(e.pageableInterface(), "PageableObject"));
        }
        return sb.toString();
    }

    /** The interface a project named for itself, or the generated one. */
    private String interfaceName(String declared, String fallback) {
        return RuleText.simpleName((declared == null || declared.isEmpty()) ? fallback : declared);
    }

    /**
     * An interface named by its full package has to be imported; one named
     * by a bare class is the generated one, in the same package as this DTO.
     * A paging search also gets @Builder.Default on its two page fields,
     * which resolves through the outer lombok.Builder class.
     */
    private void sortAndPageImports(SourceCode c, EntitySpec e) {
        String sortable = e.sortableInterface();
        if (sortable != null && sortable.indexOf('.') >= 0) c.addImport(sortable);

        // @sort puts @Builder.Default on the sort field, paged or not.
        if (e.defaultSort() != null) c.addImport("lombok.Builder");
        if (e.searchPageSize() == null) return;
        c.addImport("lombok.Builder");
        String pageable = e.pageableInterface();
        if (pageable != null && pageable.indexOf('.') >= 0) c.addImport(pageable);
    }

    /** The search properties, then the sort and paging ones. */
    private void body(EntitySpec e, SourceCode c) {
        CodeTemplate.Paragraph body = c.paragraph("fieldList");

        // Split here rather than by the paragraph: the block's trailing
        // blank line is content, and the paragraph's splitter drops it.
        // A search DTO with no condition of its own has no block at all,
        // and an empty one would still contribute its separator line.
        String fields = c.renderFieldBlock(1);
        if (!fields.isEmpty()) {
            for (String line : RuleText.linesOf(fields)) {
                body.add((Object) line);
            }
        }

        body.add((Object) "");
        if (e.defaultSort() != null) {
            // @sort: the order a search answers in when the request names none.
            body.add((Object) "@Builder.Default");
            body.add("private String sortCode=\"%s\";", e.defaultSort());
        } else {
            body.add((Object) "private String sortCode;");
        }

        if (e.searchPageSize() != null) {
            body.add((Object) "");
            body.add((Object) "@Builder.Default");
            body.add((Object) "private int pageNumber=0;");
            body.add((Object) "");
            body.add((Object) "@Builder.Default");
            body.add("private int pageSize=%d;", e.searchPageSize());
        }

        // SortableObject's legacy two-field form: dto.tmpl's own print-if
        // block adds these unconditionally to every search DTO, compat and
        // clean mode alike.
        body.add((Object) "private String sortColumn; // Column to sort by");
        body.add((Object) "private Boolean sortAscending; // Sort order (true: ascending, false: descending)");

        java.util.List<String> sortable = SearchSortList.listed(e);
        if (sortable == null) return;
        body.add((Object) "");
        body.add((Object) ("/** " + SearchSortList.DOC + " */"));
        body.add(
                (Object)
                        "private static final java.util.Set<String> SORTABLE_PROPERTIES = java.util.Collections.unmodifiableSet(");
        StringBuilder items = new StringBuilder();
        for (String p : sortable) {
            if (items.length() > 0) items.append(",\n");
            items.append("\t\t\t\t").append(RuleText.quote(p));
        }
        body.add((Object) "\t\tnew java.util.LinkedHashSet<String>(java.util.Arrays.asList(");
        for (String line : RuleText.linesOf(items.toString() + ")));")) {
            body.add((Object) line);
        }
        body.add((Object) "");
        body.add((Object) "@Override");
        body.add((Object) "public java.util.Set<String> sortableProperties() {");
        body.add((Object) "\treturn SORTABLE_PROPERTIES;");
        body.add((Object) "}");
    }
}
