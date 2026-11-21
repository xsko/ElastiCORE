package com.acme.probe;

import io.elasticore.base.model.pub.rules.CodeRules;
import io.elasticore.base.model.pub.rules.ContributedArtifact;
import io.elasticore.base.model.pub.rules.DomainArtifactContributor;
import io.elasticore.base.model.pub.rules.DomainSpec;
import io.elasticore.base.model.pub.rules.Rule;

import java.util.Collections;
import java.util.List;

import static io.elasticore.base.model.pub.rules.Ctx.ctx;

/** A team's domain-level TypeScript file, listed under publisher.rules.ts (issue 266). */
public class TsUiRules implements CodeRules, DomainArtifactContributor {

    @Override
    public List<ContributedArtifact> artifactsFor(DomainSpec d) {
        return Collections.singletonList(ContributedArtifact.of("tsUiModel", "dto", d.getName() + "UiModel"));
    }

    @Rule(value = "ts-ui-model", target = "tsUiModel", order = 10)
    void model(DomainSpec d) {
        ctx(d).var("domain", d.getName());
    }
}
