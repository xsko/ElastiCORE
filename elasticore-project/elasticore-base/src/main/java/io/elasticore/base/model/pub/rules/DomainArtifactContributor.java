package io.elasticore.base.model.pub.rules;

import java.util.List;

/**
 * A rule set that brings its own artifacts for a whole domain.
 *
 * The counterpart of {@link ArtifactContributor}, for the files a domain has
 * rather than the files an entity has. It exists because the asymmetry was
 * costing the extension point its own premise: entity artifacts were declared
 * by the rule set and the runner never learned their names, while the first
 * domain-level artifact - a domain service - was emitted by a loop in the
 * runner that imported the rule class, called its static helpers and named
 * the rule set in a string. Everything the interface promises was true of one
 * half and false of the other.
 *
 * Same shape and same reasoning as the entity form: conditions are code, and
 * a set that has nothing to add for this domain answers an empty list.
 */
public interface DomainArtifactContributor {

    /** The artifacts this set adds for one domain - possibly none. */
    List<ContributedArtifact> artifactsFor(DomainSpec domain);
}
