package io.elasticore.base.model.pub.rules;

/**
 * One artifact a rule set declares beyond the built-in list.
 *
 * It names three things and no more: the target key its rules are written
 * against, the namespace key that places it, and the class name. Everything
 * else - whether it is made at all, what it contains, which language it is
 * in - is answered where those questions are already answered for every
 * built-in artifact: by the rules, dontMake(), and the run's template set.
 */
public final class ContributedArtifact {

    private final String target;
    private final String namespaceKey;
    private final String fallbackNamespaceKey;
    private final String className;

    private ContributedArtifact(String target, String namespaceKey, String fallbackNamespaceKey,
                                String className) {
        this.target = target;
        this.namespaceKey = namespaceKey;
        this.fallbackNamespaceKey = fallbackNamespaceKey;
        this.className = className;
    }

    public static ContributedArtifact of(String target, String namespaceKey, String className) {
        return new ContributedArtifact(target, namespaceKey, null, className);
    }

    /**
     * The namespace to place this in when the project declares none for its
     * own key.
     *
     * A new artifact arrives with a new place to put it, and every existing
     * env.yml was written before it existed. Without something to fall back
     * on, adding the rule set to a project would mean editing its namespaces
     * first - or emitting into a package named null. The layer it belongs
     * beside is the honest default: a domain event goes where the domain is.
     */
    public ContributedArtifact orNamespace(String key) {
        return new ContributedArtifact(target, namespaceKey, key, className);
    }

    public String target() {
        return target;
    }

    public String namespaceKey() {
        return namespaceKey;
    }

    /** The namespace key to try when {@link #namespaceKey()} is undeclared. */
    public String fallbackNamespaceKey() {
        return fallbackNamespaceKey;
    }

    public String className() {
        return className;
    }
}
