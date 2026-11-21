package com.acme.probe;

import io.elasticore.base.model.pub.rules.ArtifactContributor;
import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.Rule;
import io.elasticore.base.model.pub.rules.SourceCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/** A team marker read through the rule API (issue 273): @ui(menu=sales, order=30, staff). */
public class UiMarkerRules implements CodeRules, ArtifactContributor {

    @Override
    public List<ContributedArtifact> artifactsFor(EntitySpec spec) {
        if (!spec.hasMeta("ui")) return new ArrayList<ContributedArtifact>();
        return Collections.singletonList(ContributedArtifact.of("uiMarker", "dto", spec.getName() + "UiMarker"));
    }

    @Rule(value = "ui-marker", target = "uiMarker", order = 10)
    void write(EntitySpec e) {
        SourceCode c = ctx(e);
        c.var("className", e.getName() + "UiMarker");
        c.var("metaPairs", String.valueOf(e.metaProperties("ui")));
        c.var("metaFlags", String.valueOf(e.metaFlags("ui")));
        StringBuilder fields = new StringBuilder();
        for (FieldSpec f : e.fields()) {
            if (!f.hasDsl("ui")) continue;
            fields.append(f.name())
                    .append(' ')
                    .append(f.dslProperties("ui"))
                    .append(' ')
                    .append(f.dslFlags("ui"))
                    .append("; ");
        }
        c.var("fieldMarks", fields.toString().trim());
    }
}
