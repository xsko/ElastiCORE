package io.elasticore.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * What a project says about itself, in the two words that decide the rest.
 *
 * <pre>
 * elasticore {
 *     architecture = 'ddd'      // layered | ddd | hexagonal
 *     language     = 'kotlin'   // java | kotlin | typescript
 * }
 * </pre>
 *
 * <strong>Declaring nothing keeps 2.x behaviour.</strong> That is the point of
 * the defaults being absent rather than set: with no block, the plugin passes
 * no rule properties at all and the generator takes the path it has always
 * taken. A default written as {@code layered} would look the same and would
 * not be - it would send a value down a channel that was previously silent.
 *
 * The two are not independently spellable on purpose. A bundle name decides
 * which rules run and a system property decides which tree and extension they
 * write; naming them separately is how a project ends up asking for Kotlin
 * rules that emit .java, which the rule guide warns about and
 * {@code SourceCode.requireSyntax} throws on later and further away. Here the
 * pair composes one bundle through {@code RulesConfig.bundleFor}, so the
 * mismatch has nowhere to live.
 */
public abstract class ElastiCoreExtension {

    /** {@code layered} (default), {@code ddd}, or {@code hexagonal}. */
    public abstract Property<String> getArchitecture();

    /** {@code java} (default), {@code kotlin}, or {@code typescript}. */
    public abstract Property<String> getLanguage();

    /**
     * The Spring Boot generation the generated code runs on: {@code 3} or
     * {@code 4}. Normally left unset - the plugin reads it off the Spring
     * Boot Gradle plugin the build applies. Spring Boot 4 reads JSON with
     * Jackson 3, so the enums and the outbox are written for it.
     */
    public abstract Property<String> getSpringBoot();

    /**
     * Module directory names to generate across, when the automatic sweep is
     * wrong.
     *
     * Normally left unset: the plugin takes every subproject that has a
     * {@code src/main/resources/blueprint} directory, which is what the
     * hand-written task in the guide did. Set it when a module holds a
     * blueprint that should not take part.
     */
    public abstract ListProperty<String> getModules();

    /**
     * The project's own rule sets, by class name, added to the bundle the
     * architecture and language chose.
     *
     * The classes come from the {@code elasticore} configuration - typically
     * {@code elasticore project(':rules')} - which is the generator's
     * classpath. Added, not substituted: the project does not restate its
     * bundle, so it cannot restate it for the wrong language.
     */
    public abstract ListProperty<String> getRules();

    /**
     * Rule ids switched off in every domain, added to what each env.yml
     * suppresses. The ids are the ones -PlistRules prints.
     */
    public abstract ListProperty<String> getSuppress();

    /**
     * Whether compilation waits for generation. Default true.
     *
     * Off by default would reproduce a failure this project has hit: Gradle is
     * free to run {@code compileJava} before the generation task, and it then
     * compiles the previous tree - which fails in ways that point nowhere near
     * the cause.
     */
    public abstract Property<Boolean> getGenerateBeforeCompile();

    /**
     * Whether the build also writes the TypeScript client (src/main/ts).
     * Default false.
     *
     * The client is written by {@code elcore3ts}, which a build never ran: after
     * a change to the model it silently lacked the new fields and entities.
     * Set, {@code elcore3ts} runs right after {@code elcore3}, before compile.
     */
    public abstract Property<Boolean> getTypescriptClient();
}
