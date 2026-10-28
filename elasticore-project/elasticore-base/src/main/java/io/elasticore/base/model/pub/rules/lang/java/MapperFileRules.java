package io.elasticore.base.model.pub.rules.lang.java;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.DomainSpec;
import io.elasticore.base.model.pub.rules.Rule;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * The mapper class that holds a domain's mapping sections, ported from the
 * file-level part of MapperSrcPublisher. Its helpers - the permission check,
 * the hasValue overloads, the setVal overloads - are template text; the rules
 * name the packages and place the sections {@link MapperBaseRules} produced.
 *
 * The classAnnotationList slot is left unset, as 2.x leaves it.
 */
public class MapperFileRules implements CodeRules {

    @Rule(value = "mapper-file", target = "mapper", order = 10)
    void file(DomainSpec d) {
        if (d.mapperSections().isEmpty()) {
            ctx(d).dontMake();
            return;
        }

        // A MyBatis domain's mapper names neither the EntityManager nor a
        // Specification: its relations are keys and its search is the XML's.
        if (io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.applies(d)) {
            ctx(d).template("mybatis_mapper");
        }
        ctx(d).var("packageName", d.pkg("dto"))
              .var("entityPackageName", d.pkg("entity"))
              .var("dtoPackageName", d.pkg("dto"))
              .var("j2eePkgName", "jakarta")
              .var("className", "%sMapper", d.className())
              .var("methodList", sections(d));

        // A domain that declares no enum carries no enumeration package, and
        // then the import line stays blank rather than naming a package no
        // source was written to.
        if (d.pkg("enumeration") != null) {
            ctx(d).var("enumPackageName", d.pkg("enumeration"));
        }

        // The packages the sections reach into. For a single-domain mapper
        // these repeat the three above, which is what 2.x emits.
        if (!d.mapperImports().isEmpty()) {
            ctx(d).varLines("importList", d.mapperImports());
        }
    }

    /**
     * The sections as one value. Each section already carries the indentation
     * of a class member, so the slot's own indentation would double the first
     * line's - the template contributes it, and the value starts bare.
     */
    private String sections(DomainSpec d) {
        StringBuilder sb = new StringBuilder();
        for (String section : d.mapperSections()) {
            sb.append(section);
        }
        return stripLeadingIndent(sb.toString());
    }

    private static String stripLeadingIndent(String text) {
        int i = 0;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
        return text.substring(i);
    }
}
