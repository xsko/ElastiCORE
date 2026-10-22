package io.elasticore.base.model.pub.rules;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a code emission rule.
 *
 * The method signature decides when the engine calls it:
 * - (EntitySpec)            : once per file of an entity (HEAD before the
 *                             field rules, TAIL after them)
 * - (EntitySpec, FieldSpec) : once per field
 * - (DomainSpec)            : once per file of a domain (mapper, a contributed file)
 * - (EnumSpec)              : once per enum.yml enum
 * - (PortSpec)              : once per port.yml port
 * - (ProtoServiceSpec)      : once per proto port
 *
 * A project's own rule sets run after ElastiCORE's within each step, so
 * what they add or take off stays.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Rule {

    /** Unique rule id, used for tracing and suppression (e.g. "jpa-column"). */
    String value();

    /** Artifact name this rule contributes to. Matches env.yml namespace keys. */
    String target() default "entity";

    /** Execution order. Lower runs earlier. Same order falls back to registration order. */
    int order() default 0;

    /**
     * When the rule runs. HEAD entity-level rules run before field rules,
     * TAIL entity-level rules run after all field rules. Field rules
     * (two-parameter signature) ignore this attribute.
     */
    Phase phase() default Phase.HEAD;

    enum Phase { HEAD, TAIL }
}
