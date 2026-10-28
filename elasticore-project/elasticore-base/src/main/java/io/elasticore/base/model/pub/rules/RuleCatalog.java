package io.elasticore.base.model.pub.rules;

import io.elasticore.base.model.pub.rules.base.ControllerBaseRules;
import io.elasticore.base.model.pub.rules.base.DtoBaseRules;
import io.elasticore.base.model.pub.rules.base.JpaEntityBaseRules;
import io.elasticore.base.model.pub.rules.base.MapperBaseRules;
import io.elasticore.base.model.pub.rules.base.RepositoryBaseRules;
import io.elasticore.base.model.pub.rules.base.ServiceExtendBaseRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named catalog of rule sets. Users pick sets by name - ultimately from
 * env.yml - instead of instantiating classes:
 *
 *   publisher:
 *     rules: [ jpa-springboot, kr.co.acme.AcmeRules ]
 *
 * A name resolves to either a registered set, a registered bundle
 * (a list of set names), or a fully qualified CodeRules class name.
 * Factories create a fresh instance per engine so rule sets may keep
 * per-run state.
 */
public final class RuleCatalog {

    /** Factory interface (Java 8 compatible, no java.util.function dependency needed). */
    public interface Factory {
        CodeRules create();
    }

    private final Map<String, Factory> sets = new LinkedHashMap<String, Factory>();
    private final Map<String, List<String>> bundles = new LinkedHashMap<String, List<String>>();

    /** Registers a named rule set. */
    public RuleCatalog register(String name, Factory factory) {
        sets.put(name, factory);
        return this;
    }

    /** Registers a bundle: one name that expands to several set names. */
    public RuleCatalog bundle(String name, String... memberNames) {
        List<String> members = new ArrayList<String>();
        for (String m : memberNames) members.add(m);
        bundles.put(name, members);
        return this;
    }

    /** Builds an engine with the given set/bundle/class names, in order. */
    public RuleEngine engineFor(String... names) {
        RuleEngine engine = new RuleEngine();
        for (String name : names) {
            for (CodeRules rs : resolve(name)) {
                engine.register(rs);
            }
        }
        return engine;
    }

    /** Fresh instances of one named set, bundle or class. */
    public List<CodeRules> rulesOf(String name) {
        return resolve(name);
    }

    /**
     * Every registered set name, and every bundle name.
     *
     * Public so that a test can ask what this catalog offers and compare it
     * with what the guide says it offers. A name a user cannot select because
     * nothing documents it is not a feature, and the way that happens is by
     * writing the register() line and stopping there.
     */
    public java.util.Set<String> setNames() {
        return java.util.Collections.unmodifiableSet(sets.keySet());
    }

    public java.util.Set<String> bundleNames() {
        return java.util.Collections.unmodifiableSet(bundles.keySet());
    }

    private List<CodeRules> resolve(String name) {
        List<CodeRules> result = new ArrayList<CodeRules>();
        if (bundles.containsKey(name)) {
            for (String member : bundles.get(name)) {
                result.addAll(resolve(member));
            }
            return result;
        }
        Factory f = sets.get(name);
        if (f != null) {
            result.add(f.create());
            return result;
        }
        // Fallback: fully qualified user class implementing CodeRules
        try {
            Class<?> cls = Class.forName(name);
            result.add((CodeRules) cls.newInstance());
            return result;
        } catch (ClassNotFoundException e) {
            if (name.contains(".")) {
                // A project's own class, by the look of it: the list of what
                // the catalog registers would not help find it.
                throw new IllegalArgumentException("Rule set class '" + name + "' is not on the generator's classpath."
                        + " Check the name, and that its module is declared: dependencies { elasticore project(':rules') }", e);
            }
            throw new IllegalArgumentException("Unknown rule set '" + name
                    + "'. Bundles: " + bundles.keySet() + ", sets: " + sets.keySet(), e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Rule set '" + name + "' cannot be used: it must be a public class"
                    + " implementing CodeRules with a public no-argument constructor (" + e + ")", e);
        }
    }

    /** The standard catalog shipped with ElastiCORE. */
    public static RuleCatalog standard() {
        RuleCatalog c = new RuleCatalog();
        c.register("jpa-entity", new Factory() {
            public CodeRules create() { return new JpaEntityBaseRules(); }
        });
        c.register("jpa-entity-file", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.java.JpaEntityFileRules();
            }
        });
        c.register("jpa-entity-annotations", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.JpaEntityAnnotationRules();
            }
        });
        c.register("jpa-entity-file-kt", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.kotlin.KotlinEntityFileRules();
            }
        });
        c.register("jpa-dto-file-kt", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.kotlin.KotlinDtoFileRules();
            }
        });
        c.register("jpa-q-kt", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.kotlin.KotlinQRules();
            }
        });
        c.register("jpa-identity", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.JpaIdentityRules();
            }
        });
        c.register("jpa-identity-kt", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.kotlin.KotlinIdentityFileRules();
            }
        });
        c.register("jpa-dto-file-ts", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.typescript.TypeScriptDtoFileRules();
            }
        });
        c.register("ddd-aggregate", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddAggregateRules();
            }
        });
        c.register("ddd-value-object", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddValueObjectRules();
            }
        });
        c.register("ddd-reference", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddReferenceRules();
            }
        });
        c.register("ddd-collection", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddCollectionRules();
            }
        });
        c.register("hex-usecase", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.hex.HexUseCaseRules();
            }
        });
        c.register("ddd-event", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddDomainEventRules();
            }
        });
        c.register("ddd-invariant", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddInvariantRules();
            }
        });
        c.register("ddd-factory", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddFactoryRules();
            }
        });
        c.register("ddd-specification", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddSpecificationRules();
            }
        });
        c.register("ddd-repository-port", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddRepositoryPortRules();
            }
        });
        // What the API answers when a service refuses a request. Composed
        // into the v3 bundles (and so every DDD one), whose services refuse
        // for reasons a client can act on: a taken identifier, a stale
        // version, a constraint, a broken invariant. Not in jpa-springboot,
        // whose output is 2.x's byte for byte.
        c.register("jpa-write-guard", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.WriteGuardRules();
            }
        });
        // An @embeddable is what a DTO holds for an embedded value, so the
        // request rules its fields carry are written on the embeddable itself.
        // @access, @owner and @password, enforced in the regenerated CoreService.
        c.register("jpa-service-access", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.ServiceAccessRules();
            }
        });
        c.register("jpa-value-object-boundary", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.ValueObjectBoundaryRules();
            }
        });
        c.register("spring-api-errors", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.ApiErrorRules();
            }
        });
        c.register("ddd-domain-service", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddDomainServiceRules();
            }
        });
        c.register("ddd-outbox", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddOutboxRules();
            }
        });
        c.register("ddd-behavior", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddBehaviorRules();
            }
        });
        c.register("ddd-command", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddCommandRules();
            }
        });
        c.register("ddd-mapper-ref", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddMapperRules();
            }
        });
        // A lifecycle (states:) and derived values (@snapshot, @compute), in either language.
        c.register("ddd-state", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddStateRules();
            }
        });
        c.register("ddd-derive", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.DddDeriveRules();
            }
        });
        c.register("ddd-value-object-kt", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.ddd.lang.DddKotlinValueObjectRules();
            }
        });
        c.register("ts-api", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.typescript.TypeScriptApiRules();
            }
        });
        c.register("jpa-enum-ts", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.typescript.TypeScriptEnumRules();
            }
        });
        c.register("jpa-enum-kt", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.kotlin.KotlinEnumRules();
            }
        });
        c.register("jpa-searchdto-file-kt", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.kotlin.KotlinSearchDtoFileRules();
            }
        });
        c.register("jpa-service-file", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.java.JpaServiceFileRules();
            }
        });
        // The 2.x-identical CoreService (service.tmpl). The compatibility
        // mode's counterpart of "jpa-service-file"; never both in one run.
        c.register("jpa-service-compat", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.java.JpaServiceCompatFileRules();
            }
        });
        c.register("grpc-stub", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.GrpcStubRules();
            }
        });
        c.register("proto", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.ProtoBaseRules();
            }
        });
        c.register("jpa-dto", new Factory() {
            public CodeRules create() { return new DtoBaseRules(); }
        });
        c.register("jpa-repository", new Factory() {
            public CodeRules create() { return new RepositoryBaseRules(); }
        });
        c.register("jpa-repository-file-v3", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.RepositoryCleanFileRules();
            }
        });
        c.register("jpa-mapper", new Factory() {
            public CodeRules create() { return new MapperBaseRules(); }
        });
        c.register("jpa-mapper-assemble", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.MapperAssembleRules();
            }
        });
        c.register("jpa-mapper-assemble-kt", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.kotlin.KotlinMapperAssembleRules();
            }
        });
        c.register("jpa-mapper-spec-shell", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.MapperSpecShellRules();
            }
        });
        c.register("jpa-mapper-spec-shell-kt", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.kotlin.KotlinMapperSpecShellRules();
            }
        });
        c.register("jpa-mapper-spec", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.MapperSpecRules();
            }
        });
        c.register("spring-service-extend", new Factory() {
            public CodeRules create() { return new ServiceExtendBaseRules(); }
        });
        c.register("spring-controller", new Factory() {
            public CodeRules create() { return new ControllerBaseRules(); }
        });
        c.register("jpa-entity-class", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.java.JpaEntityClassRules();
            }
        });
        c.register("jpa-entity-file-compat", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.java.JpaEntityCompatFileRules();
            }
        });
        c.register("jpa-dto-file", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.java.DtoFileRules();
            }
        });
        c.register("jpa-dto-file-v3", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.java.DtoCleanFileRules();
            }
        });
        c.register("jpa-searchdto", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.SearchDtoBaseRules();
            }
        });
        c.register("jpa-searchdto-file", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.java.SearchDtoFileRules();
            }
        });
        c.register("jpa-searchdto-file-v3", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.java.SearchDtoCleanFileRules();
            }
        });
        c.register("jpa-mapper-file", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.lang.java.MapperFileRules();
            }
        });
        c.register("jpa-repository-helper", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.RepositoryHelperBaseRules();
            }
        });
        c.register("jpa-enum", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.EnumBaseRules();
            }
        });
        c.register("jpa-views", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.ViewsBaseRules();
            }
        });
        c.register("jpa-support-types", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.SupportTypeBaseRules();
            }
        });
        c.register("port", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.PortBaseRules();
            }
        });
        c.register("jpa-q", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.QBaseRules();
            }
        });
        c.register("spring-service-test", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.base.ServiceTestBaseRules();
            }
        });

        // MyBatis (env.yml mode: mybatis): the entity without JPA, its mapper
        // interface and XML, the service over them and the schema.
        c.register("mybatis-entity-file", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.mybatis.MyBatisEntityFileRules();
            }
        });
        c.register("mybatis-mapper", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.mybatis.MyBatisMapperRules();
            }
        });
        c.register("mybatis-search", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.mybatis.MyBatisSearchRules();
            }
        });
        c.register("mybatis-service-file", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.mybatis.MyBatisServiceFileRules();
            }
        });
        c.register("mybatis-schema", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.rules.mybatis.MyBatisSchemaRules();
            }
        });

        // Everything a JPA Spring Boot project got from the 2.x pipeline.
        // Not a generator: the set is the opt-in for the Apache Ossie
        // semantic-model export, read by OssieSemanticModelExporter. It is
        // registered so a project turns the export on the way it turns
        // anything on - by naming a set - rather than by a switch that
        // exists only for this feature.
        c.register("ossie-export", new Factory() {
            public CodeRules create() {
                return new io.elasticore.base.model.pub.ossie.OssieExportRules();
            }
        });

        c.bundle("jpa-springboot",
                "jpa-entity", "jpa-entity-class", "jpa-entity-file-compat", "jpa-q",
                "jpa-dto", "jpa-dto-file", "jpa-searchdto", "jpa-searchdto-file",
                "jpa-enum", "jpa-views", "jpa-support-types",
                "jpa-repository", "jpa-repository-helper",
                "jpa-mapper", "jpa-mapper-assemble", "jpa-mapper-spec", "jpa-mapper-spec-shell", "jpa-mapper-file",
                "spring-service-extend", "spring-controller", "spring-service-test",
                "port", "proto", "grpc-stub");

        // The clean-mode counterpart: field rules are mode-agnostic and stay
        // the same, but "jpa-entity-file-compat" (the 2.x-identical file
        // shell) gives way to "jpa-entity-file" (the used-imports-only one).
        // The two must never both be in one bundle - each writes the same
        // ${importList} slot by a different mechanism (a plain var vs a
        // paragraph), and a paragraph entry wins the substitution regardless
        // of which rule ran second, silently corrupting the plain var's text.
        c.bundle("jpa-springboot-v3",
                "jpa-entity", "jpa-entity-annotations", "jpa-entity-file", "jpa-q",
                "jpa-dto", "jpa-dto-file-v3", "jpa-searchdto", "jpa-searchdto-file-v3",
                "jpa-enum", "jpa-views", "jpa-support-types",
                "jpa-repository", "jpa-repository-helper",
                "jpa-mapper", "jpa-mapper-assemble", "jpa-mapper-spec", "jpa-mapper-spec-shell", "jpa-mapper-file",
                "spring-service-extend", "spring-controller", "spring-service-test", "spring-api-errors", "jpa-write-guard",
                "jpa-value-object-boundary", "jpa-service-access",
                "port", "proto", "grpc-stub");
        // The v3 layered bundle over MyBatis: the same DTOs, mapper, controller
        // and access checks, with the JPA entity shell, repository, Q and
        // Specification search given way to the MyBatis sets. No generated
        // service test: its skeleton is written against JPA.
        c.bundle("mybatis-springboot",
                "jpa-entity", "mybatis-entity-file",
                "jpa-dto", "jpa-dto-file-v3", "jpa-searchdto", "jpa-searchdto-file-v3",
                "jpa-enum", "jpa-views", "jpa-support-types",
                "mybatis-mapper", "mybatis-search", "jpa-repository-helper",
                "jpa-mapper", "jpa-mapper-assemble", "jpa-mapper-file",
                "mybatis-service-file", "spring-service-extend", "spring-controller", "spring-api-errors",
                "jpa-write-guard", "jpa-value-object-boundary", "jpa-service-access", "mybatis-schema",
                "port", "proto", "grpc-stub");
        // The domain-model reading of the same artifacts: everything the v3
        // bundle produces, minus what an aggregate boundary says should not
        // exist. The subtraction is the rules declining, not a mode.
        // Java: the boundary plus the Java way of making a value object.
        // A Kotlin project composes the pieces instead -
        //   publisher.rules: [jpa-springboot-kt, ddd-aggregate]
        // since the boundary is language-neutral and Lombok is not.
        c.bundle("ddd-springboot",
                "jpa-springboot-v3",
                "ddd-aggregate", "ddd-reference", "ddd-mapper-ref",
                "ddd-collection", "ddd-value-object", "ddd-event",
                "ddd-invariant", "ddd-factory", "ddd-specification", "ddd-domain-service",
                "ddd-repository-port", "ddd-behavior", "ddd-command", "ddd-outbox",
                "ddd-state", "ddd-derive");
        // The full hexagon: the domain reading plus the inbound port. The
        // name waited until it was earned - an interface alone was not it.
        // What makes it whole now is that both directions exist and run:
        // inward, a use-case interface per exposed root, implemented by the
        // application service and injected by the controller; outward, the
        // port DSL, whose adapter is a runtime proxy. The outbound side
        // needs no member here because it is not a rule - a port is declared
        // in the blueprint and generated whenever one is.
        c.bundle("hexagonal-springboot",
                "ddd-springboot", "hex-usecase");

        // Kotlin. The field rules are the Java ones - an annotation reads the
        // same in both - and only the file shell is written per language.
        // Artifacts without a kt/ skeleton are absent from this bundle rather
        // than falling back to a Java one, which would emit Java silently.
        c.bundle("jpa-springboot-kt",
                "jpa-entity", "jpa-entity-annotations", "jpa-entity-file-kt",
                "jpa-dto", "jpa-dto-file-kt",
                "jpa-searchdto", "jpa-searchdto-file-kt",
                "jpa-repository", "jpa-repository-file-v3", "jpa-repository-helper",
                "port", "proto", "grpc-stub",
                "jpa-enum-kt", "jpa-identity-kt", "jpa-q-kt",
                "jpa-mapper", "jpa-mapper-assemble-kt", "jpa-mapper-spec", "jpa-mapper-spec-shell-kt", "jpa-mapper-file",
                "jpa-service-file", "spring-service-extend", "spring-controller", "spring-service-test", "spring-api-errors", "jpa-write-guard",
                "jpa-value-object-boundary", "jpa-service-access",
                "jpa-views", "jpa-support-types");

        // The same two readings in Kotlin. They are separate bundles rather
        // than a language flag on one because the members differ: the value
        // object is made immutable by Lombok in Java and by the language
        // itself in Kotlin, so each names its own rule for that.
        c.bundle("ddd-springboot-kt",
                "jpa-springboot-kt",
                "ddd-aggregate", "ddd-reference", "ddd-mapper-ref",
                "ddd-collection", "ddd-value-object-kt",
                "ddd-invariant", "ddd-specification", "ddd-factory",
                "ddd-event", "ddd-domain-service", "ddd-repository-port", "ddd-behavior", "ddd-command", "ddd-outbox",
                "ddd-state", "ddd-derive");
        c.bundle("hexagonal-springboot-kt",
                "ddd-springboot-kt", "hex-usecase");

        // TypeScript: the model a browser sees. Only the artifacts that are
        // data - no service, no repository, nothing that runs on the server.
        // The field walks are the shared ones; what they annotate is dropped
        // by the renderer, since this language has no annotations.
        // The domain rules a browser can honestly hold: a specification is a
        // question about data it already has, and an invariant is what the
        // server will refuse - worth asking early, never worth trusting here.
        // The boundary needs no member: a member of an aggregate has no
        // endpoint, so ts-api generates no client for one and the browser
        // cannot reach past a root even if it wanted to.
        c.bundle("ts-model",
                "jpa-dto", "jpa-dto-file-ts",
                "jpa-searchdto", "jpa-enum-ts", "ts-api", "jpa-service-access",
                "ddd-specification", "ddd-invariant");
        return c;
    }
}
