package io.elasticore.base.model.pub.rules;

/**
 * A rule set that draws aggregate boundaries (ddd-aggregate): the domain is
 * generated as a domain model, not as the layered code.
 *
 * The runner asks for this rather than for the rule set by name - archTest
 * writes the hexagon's rules for such a domain, and the layers' rules for
 * any other.
 */
public interface AggregateBoundaries {}
