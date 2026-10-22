package io.elasticore.base.model.pub.rules;

/**
 * A rule set that brings its own artifacts.
 *
 * The runner's built-in list says what every project gets. This says what a
 * rule set adds: the runner walks the configured sets, asks each one, and
 * emits what it answers. A new kind of file thereby lives entirely inside
 * the rule set that wants it - the runner never learns its name - and a
 * project that configures nothing gets nothing new.
 *
 * Conditions ("roots only", "exposed only") are code, not fields: the
 * method sees the spec and answers an empty list where the artifact does
 * not apply. The spec already knows how to say everything a condition
 * would - inventing a condition language here would only shadow it.
 */
public interface ArtifactContributor {

    /** The artifacts this set adds for one entity - possibly none. */
    java.util.List<ContributedArtifact> artifactsFor(EntitySpec spec);
}
