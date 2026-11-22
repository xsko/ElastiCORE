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

/** A team's screen model lists the fields a user sees - the blind index of an encrypted one left out (issue 290). */
public class ScreenFieldRules implements CodeRules, ArtifactContributor {

    @Override
    public List<ContributedArtifact> artifactsFor(EntitySpec spec) {
        if (!spec.hasMeta("expose")) return new ArrayList<ContributedArtifact>();
        return Collections.singletonList(ContributedArtifact.of("screenFields", "dto", spec.getName() + "Screen"));
    }

    @Rule(value = "screen-fields", target = "screenFields", order = 10)
    void write(EntitySpec e) {
        SourceCode c = ctx(e);
        c.var("className", e.getName() + "Screen");
        StringBuilder shown = new StringBuilder();
        StringBuilder indexes = new StringBuilder();
        for (FieldSpec f : e.fields()) {
            if (f.blindIndexOf() != null) {
                indexes.append(f.name()).append("->").append(f.blindIndexOf()).append(' ');
                continue;
            }
            shown.append(f.name()).append(' ');
        }
        StringBuilder dto = new StringBuilder();
        for (FieldSpec f : e.dtoFields()) {
            if (f.blindIndexOf() != null) continue;
            dto.append(f.name()).append(' ');
        }
        c.var("shown", shown.toString().trim());
        c.var("indexes", indexes.toString().trim());
        c.var("dtoShown", dto.toString().trim());
    }
}
