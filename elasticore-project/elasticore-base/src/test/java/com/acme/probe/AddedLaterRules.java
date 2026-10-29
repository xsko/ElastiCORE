package com.acme.probe;

import io.elasticore.base.model.pub.rules.AnnoCode;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.Rule;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/** A project's rules added after the files they reach were written and edited. */
public class AddedLaterRules implements CodeRules {

    @Rule(value = "acme-web-adapter", target = "control", order = 99, phase = Rule.Phase.TAIL)
    void webAdapter(EntitySpec e) {
        ctx(e).ensureClassAnnotation(AnnoCode.of("com.acme.arch.WebAdapter"));
    }

    @Rule(value = "acme-aggregate-root", target = "entity", order = 99, phase = Rule.Phase.TAIL)
    void aggregateRoot(EntitySpec e) {
        if (e.aggregateRoot()) ctx(e).ensureClassAnnotation(AnnoCode.of("org.jmolecules.ddd.annotation.AggregateRoot"));
    }
}
