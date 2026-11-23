package io.elasticore.base.model.pub.rules.lang.kotlin;

import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.util.CodeTemplate;

import java.util.ArrayList;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Full-file search DTO rendering in Kotlin (kt/dto.tmpl, shared with the DTO).
 *
 * Which conditions a search DTO carries is decided by the shared rules; what
 * this adds is the sort and paging properties every search DTO ends with, and
 * they go through {@link Syntax} rather than being written as text, so the one
 * statement of how Kotlin declares a property keeps serving them.
 *
 * Lombok's @Builder.Default has no counterpart here and needs none: a Kotlin
 * property states its default in the declaration itself.
 */
public class KotlinSearchDtoFileRules implements CodeRules {

    @Rule(value = "kt-srchdto-file", target = "srchdto", order = 90, phase = Rule.Phase.TAIL)
    void file(EntitySpec e) {
        SourceCode c = ctx(e);
        c.requireSyntax(Syntax.KOTLIN, "kt-srchdto-file");

        c.template("dto");

        for (FieldSpec f : e.fields()) {
            RuleText.registerFieldTypeImport(c, e, f, e.pkg("dto"));
        }
        sortAndPageImports(c, e);

        c.var("packageName", e.pkg("dto"))
         .var("className", e.searchDtoClassName())
         .var("classDeclaration", classDeclaration(e))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", RuleText.stripTrailing(c.renderImports()));

        body(e, c);
    }

    /**
     * Every search DTO sorts; one with a page size also pages. A project that
     * named its own interfaces in env.yml gets those names.
     */
    private String classDeclaration(EntitySpec e) {
        List<String> interfaces = new ArrayList<String>();
        interfaces.add("java.io.Serializable");
        interfaces.add(interfaceName(e.sortableInterface(), "SortableObject"));
        if (e.searchPageSize() != null) {
            interfaces.add(interfaceName(e.pageableInterface(), "PageableObject"));
        }
        return KotlinClass.declaration(
                e.searchDtoClassName(),
                e.hasMeta("abstract") ? "abstract" : "open",
                null,
                interfaces.toArray(new String[0]));
    }

    /** The interface a project named for itself, or the generated one. */
    private String interfaceName(String declared, String fallback) {
        return RuleText.simpleName((declared == null || declared.isEmpty()) ? fallback : declared);
    }

    /**
     * An interface named by its full package has to be imported; one named by
     * a bare class is the generated one, in the same package as this DTO.
     */
    private void sortAndPageImports(SourceCode c, EntitySpec e) {
        String sortable = e.sortableInterface();
        if (sortable != null && sortable.indexOf('.') >= 0) c.addImport(sortable);

        if (e.searchPageSize() == null) return;
        String pageable = e.pageableInterface();
        if (pageable != null && pageable.indexOf('.') >= 0) c.addImport(pageable);
    }

    /** The search properties, then the sort and paging ones. */
    private void body(EntitySpec e, SourceCode c) {
        CodeTemplate.Paragraph body = c.paragraph("fieldList");
        Syntax kt = Syntax.KOTLIN;

        String fields = c.renderFieldBlock(1);
        if (!fields.isEmpty()) {
            for (String line : RuleText.linesOf(fields)) {
                body.add((Object) line);
            }
        }

        // These implement SortableObject and PageableObject, and Kotlin marks
        // an implementing member override rather than inferring it.
        final String OVERRIDE = "override var";

        body.add((Object) "");
        // @sort: the order a search answers in when the request names none.
        body.add((Object) kt.declaration(OVERRIDE, "String", "sortCode",
                e.defaultSort() == null ? "" : String.format(" = \"%s\"", e.defaultSort()), false));

        if (e.searchPageSize() != null) {
            body.add((Object) "");
            body.add((Object) kt.declaration(OVERRIDE, "Int", "pageNumber", " = 0", true));
            body.add((Object) kt.declaration(OVERRIDE, "Int", "pageSize",
                    String.format(" = %d", e.searchPageSize()), true));
        }

        body.add((Object) "");
        body.add((Object) (kt.declaration(OVERRIDE, "String", "sortColumn", "", false)
                + " // Column to sort by"));
        body.add((Object) (kt.declaration(OVERRIDE, "Boolean", "sortAscending", "", false)
                + " // Sort order (true: ascending, false: descending)"));

        List<String> sortable = io.elasticore.base.model.pub.rules.base.SearchSortList.listed(e);
        if (sortable == null) return;
        body.add((Object) "");
        body.add((Object) ("/** " + io.elasticore.base.model.pub.rules.base.SearchSortList.DOC + " */"));
        StringBuilder items = new StringBuilder("override fun sortableProperties(): Set<String> = setOf(");
        for (int i = 0; i < sortable.size(); i++) {
            items.append(i == 0 ? "\n" : ",\n").append("        ").append(RuleText.quote(sortable.get(i)));
        }
        items.append(')');
        for (String line : RuleText.linesOf(items.toString())) {
            body.add((Object) line);
        }
    }
}
