package io.elasticore.base.model.pub.rules.base;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.DomainSpec;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/**
 * Base rules for the per-domain repository helper, ported from
 * RepositoryHelperFilePublisher. The helper's query methods live in
 * repositoryHelper.tmpl unchanged; rules decide which repositories it holds.
 *
 * The methodList slot is not filled here. 2.x fills it from the dynamic and
 * DTO-projecting queries of the repository model, and no declared repository
 * method currently reaches a publisher - RepositoryModelLoader reads methods
 * from a "methods" entry that the blueprint DSL does not write - so every
 * committed helper has an empty slot and there is nothing to verify against.
 */
public class RepositoryHelperBaseRules implements CodeRules {

    @Rule(value = "helper-vars", target = "repositoryHelper", order = 10)
    void vars(DomainSpec d) {
        // Over MyBatis the helper holds the mappers and nothing of JPA's.
        if (io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.applies(d)) {
            ctx(d).template("mybatis_repositoryHelper");
        }
        ctx(d).var("packageName", d.pkg("repository"))
              .var("entityPackageName", d.pkg("entity"))
              .var("dtoPackageName", d.pkg("dto"))
              .var("j2eePkgName", "jakarta")
              .var("className", "%sRepositoryHelper", d.className());
    }

    /**
     * One injected repository per entity that owns a table, plus one per
     * entity of another domain this one reaches into. An entity with no
     * primary key has no repository to inject, and an @embeddable type is
     * part of another table rather than one of its own.
     */
    @Rule(value = "helper-repositories", target = "repositoryHelper", order = 20)
    void repositories(DomainSpec d) {
        SourceCode c = ctx(d);

        // Over MyBatis an entity a collection holds has a mapper even without
        // a service of its own: its rows are read and written with the root.
        java.util.Set<String> held = new java.util.HashSet<String>();
        if (io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.applies(d)) {
            for (EntitySpec e : d.entities()) {
                for (io.elasticore.base.model.pub.rules.FieldSpec f
                        : io.elasticore.base.model.pub.rules.mybatis.MyBatisSql.collections(e)) {
                    held.add(f.coreItemType());
                }
            }
        }

        for (EntitySpec e : d.entities()) {
            if (held.contains(e.getName()) && e.pkField() != null && !(e.hasMeta("expose") || e.hasMeta("service"))) {
                inject(c, e.getName());
                continue;
            }
            // An entity that inherits its key still has one: 2.x asks the
            // entity for its pk field, which resolves through the parent.
            if (e.pkField() == null && !hasInheritedKey(e)) continue;
            if (e.hasMeta("embeddable")) continue;
            // The v3 and Kotlin runs write a repository only for an entity
            // with a service and a key of its own (RulesGenRunner); an @extend
            // child or an entity without @expose/@service has none to inject,
            // and naming it failed the compile.
            if (!c.compatible() && (e.pkField() == null || !(e.hasMeta("expose") || e.hasMeta("service")))) continue;
            inject(c, e.getName());
        }

        for (Map.Entry<String, String> ext : d.externalEntities().entrySet()) {
            String entityName = ext.getKey();
            inject(c, entityName);
            c.varLine("importList", "%s.%sRepository", ext.getValue(), entityName);
        }

        // With nothing to inject there is no helper worth writing.
        if (!c.hasVarLines("repositoryList")) c.dontMake();
    }

    /** Whether a key reaches the entity through what it extends. */
    private boolean hasInheritedKey(EntitySpec e) {
        for (io.elasticore.base.model.pub.rules.FieldSpec f : e.allFields()) {
            if (f.hasDsl("id") || f.hasDsl("pk")) return true;
        }
        return false;
    }

    /** A declaration followed by the blank separator 2.x writes after each. */
    private void inject(SourceCode c, String entityName) {
        c.varLine("repositoryList", "%s", c.syntax().injectedProperty(
                        String.format("%sRepository", entityName),
                        RuleText.uncapital(entityName)))
         .varLine("repositoryList", "");
    }
}
