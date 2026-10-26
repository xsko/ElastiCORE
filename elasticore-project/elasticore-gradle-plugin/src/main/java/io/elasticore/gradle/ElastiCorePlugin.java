package io.elasticore.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The generation tasks a project would otherwise write out by hand.
 *
 * What this replaces is twenty-odd lines of {@code JavaExec} copied between
 * projects, and in a multi-module build those lines name a specific module for
 * their classpath - the guide's own example says
 * {@code project(':core-domain').sourceSets.main.runtimeClasspath}. Renaming
 * that module breaks generation, and the failure points nowhere near the
 * cause. The plugin resolves its own tools configuration instead, so it
 * depends on no module of yours.
 *
 * <p><strong>Applying it changes nothing on its own.</strong> With no
 * {@code elasticore} block, no rule property is passed and the generator takes
 * the path it always took. That is deliberate and it is why the extension's
 * defaults are absent rather than set to their old values: sending "layered"
 * down a channel that used to be silent would be a different thing than
 * silence, however identical it looks.
 */
public class ElastiCorePlugin implements Plugin<Project> {

    private static final String EXTENSION = "elasticore";
    private static final String TOOLS_CONFIGURATION = "elasticore";
    private static final String GROUP = "elasticore";

    private static final String EXTRACTOR = "io.elasticore.base.extract.ModelExtractor";
    private static final String RULE_ENGINE =
            "io.elasticore.base.model.pub.rules.verify.RulesGenRunner";

    /** Where a module keeps its DSL. A subproject with this takes part. */
    private static final String BLUEPRINT = "src/main/resources/blueprint";

    @Override
    public void apply(Project project) {
        ElastiCoreExtension extension =
                project.getExtensions().create(EXTENSION, ElastiCoreExtension.class);
        extension.getGenerateBeforeCompile().convention(Boolean.TRUE);
        extension.getTypescriptClient().convention(Boolean.FALSE);

        Configuration tools = toolsConfiguration(project);

        TaskProvider<JavaExec> elcore = project.getTasks().register("elcore", JavaExec.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Generates sources from the blueprint DSL");
            task.setClasspath(tools);
            task.getMainClass().set(EXTRACTOR);

            // The initialization wizard asks questions on the console. Without
            // this it reads end-of-stream and reports a non-interactive
            // environment, which is what a first-time user meets.
            task.setStandardInput(System.in);
        });

        TaskProvider<JavaExec> elcore3 = project.getTasks().register("elcore3", JavaExec.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Generates sources through the 3.0 rule engine");
            task.setClasspath(tools);
            task.getMainClass().set(RULE_ENGINE);
        });

        // The TypeScript client is a second output rather than a different
        // one: a browser model coexists with whatever the server is written
        // in, so it gets its own task instead of a language a project has to
        // choose between. Registered always, run only when asked for.
        TaskProvider<JavaExec> elcore3ts = project.getTasks().register("elcore3ts", JavaExec.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Generates the TypeScript client model");
            task.setClasspath(tools);
            task.getMainClass().set(RULE_ENGINE);
            task.systemProperty("elasticore.rules.ts", "true");
        });

        // The arguments are settled here, while the build is being configured,
        // and not inside a task action. An action that reaches for the project
        // is the one thing the configuration cache refuses, so the lazier
        // version of this was the less compatible one.
        project.afterEvaluate(evaluated -> {
            elcore.configure(task -> {
                arguments(task, evaluated, extension);

                // Where the sources go, which the extractor takes as an
                // argument rather than a property. Only for a single module:
                // in a multi-module run each module's own root is derived from
                // the blueprint it holds, and one path here would send them all
                // to the same tree.
                if (modulesOf(evaluated, extension).isEmpty()) {
                    task.args(new File(evaluated.getProjectDir(), "src/main/java").getAbsolutePath());
                }
            });
            elcore3.configure(task -> arguments(task, evaluated, extension));
            elcore3ts.configure(task -> arguments(task, evaluated, extension));

            // The client written with the server, where the build asks for it:
            // elcore3 says so to the generator, which otherwise warns that the
            // client it finds is not refreshed by this run.
            boolean client = Boolean.TRUE.equals(extension.getTypescriptClient().get());
            if (client) {
                elcore3.configure(task -> {
                    task.systemProperty("elasticore.rules.tsWithBuild", "true");
                    task.finalizedBy(elcore3ts);
                });
                elcore3ts.configure(task -> task.mustRunAfter(elcore3));
            }

            if (Boolean.TRUE.equals(extension.getGenerateBeforeCompile().get())) {
                // Whichever task this project actually runs. A build that
                // declared an architecture or a language is on the rule
                // engine, and waiting for the other generator would let
                // compilation start against a tree nothing had written.
                dependOnGeneration(evaluated, declaresRules(extension) ? elcore3 : elcore);
                if (client) dependOnGeneration(evaluated, elcore3ts);
            }
        });
    }

    /**
     * The framework, resolved without going through a module of the project.
     *
     * The version is the plugin's own, so a project names it once - applying
     * the plugin - rather than in three dependency lines that can drift apart.
     */
    private Configuration toolsConfiguration(Project project) {
        Configuration tools = project.getConfigurations().maybeCreate(TOOLS_CONFIGURATION);
        tools.setVisible(false);
        tools.setDescription("The ElastiCORE generator this project runs");

        // The framework, also when the project adds its own rules module
        // here: defaultDependencies dropped it as soon as anything else was
        // declared, and the generator's main class went missing. Decided once
        // the build script has been read, and only when the project did not
        // declare the framework itself - a pinned version, or a jar of it for
        // an offline build, is left as the project wrote it.
        project.afterEvaluate(evaluated -> {
            for (org.gradle.api.artifacts.Dependency d : tools.getDependencies()) {
                if ("elasticore-base".equals(d.getName())) return;
                if (d instanceof org.gradle.api.artifacts.FileCollectionDependency) {
                    for (File f : ((org.gradle.api.artifacts.FileCollectionDependency) d).getFiles()) {
                        if (f.getName().startsWith("elasticore-base")) return;
                    }
                }
            }
            evaluated.getDependencies().add(TOOLS_CONFIGURATION, "io.elasticore:elasticore-base:" + version());
        });
        return tools;
    }

    /** The plugin's own version, which is the framework version it was built with. */
    private String version() {
        String implementation = ElastiCorePlugin.class.getPackage().getImplementationVersion();
        return implementation != null ? implementation : Versions.ELASTICORE;
    }

    /**
     * Whether this build has said something the rule engine reads.
     *
     * Only the build's own declaration counts. A bundle named in a domain's
     * env.yml says the same thing, but reading YAML here to find out would
     * make the plugin a second parser of the model - and one that is wrong
     * whenever the two disagree.
     */
    private boolean declaresRules(ElastiCoreExtension extension) {
        // Rule sets of the project's own are read only by the rule engine;
        // running the 2.x generator would ignore them without a word.
        return extension.getArchitecture().isPresent() || extension.getLanguage().isPresent()
                || !extension.getRules().getOrElse(new ArrayList<>()).isEmpty();
    }

    /** What the generator is told, decided once the build script has been read. */
    private void arguments(JavaExec task, Project project, ElastiCoreExtension extension) {
        task.systemProperty("elasticore.base.path", project.getProjectDir().getAbsolutePath());

        List<String> modules = modulesOf(project, extension);
        if (!modules.isEmpty()) {
            task.systemProperty("elasticore.modules", String.join(",", modules));
        }

        // The two words, passed as they were written. The plugin holds no
        // table of its own - RulesConfig turns the pair into a bundle and into
        // the language switch, so a build and the initialization wizard cannot
        // reach different conclusions about one project.
        if (extension.getArchitecture().isPresent()) {
            task.systemProperty("elasticore.architecture", extension.getArchitecture().get());
        }
        if (extension.getLanguage().isPresent()) {
            task.systemProperty("elasticore.language", extension.getLanguage().get());
        }

        String springBoot = springBootOf(project, extension);
        if (springBoot != null) {
            task.systemProperty("elasticore.springBoot", springBoot);
        }

        List<String> suppress = extension.getSuppress().getOrElse(new ArrayList<>());
        if (!suppress.isEmpty()) {
            task.systemProperty("elasticore.publisher.suppress", String.join(",", suppress));
        }
        List<String> rules = extension.getRules().getOrElse(new ArrayList<>());
        if (!rules.isEmpty()) {
            task.systemProperty("elasticore.publisher.rules.add", String.join(",", rules));
        }
        if (extension.getOssieName().isPresent()) {
            task.systemProperty("elasticore.ossie.name", extension.getOssieName().get());
        }

        passThrough(project, task, "mode", "mode");
        passThrough(project, task, "domain", "elasticore.domain");

        // The switches the hand-written tasks already take, kept so that
        // adopting the plugin is not also a change of command line. A project
        // that declares its language does not need -Pkt; both are accepted
        // because the two ways of saying it will coexist for a while.
        flag(project, task, "force", "elasticore.rules.force");
        flag(project, task, "v3", "elasticore.rules.v3");
        flag(project, task, "kt", "elasticore.rules.kt");
        flag(project, task, "ts", "elasticore.rules.ts");
        flag(project, task, "listRules", "elasticore.rules.list");
    }

    /** A {@code -P} with no value, present or absent. */
    private void flag(Project project, JavaExec task, String property, String systemProperty) {
        if (project.hasProperty(property)) {
            task.systemProperty(systemProperty, "true");
        }
    }

    /** A {@code -P} on the command line reaching the process that needs it. */
    private void passThrough(Project project, JavaExec task, String property, String systemProperty) {
        if (project.hasProperty(property)) {
            task.systemProperty(systemProperty, String.valueOf(project.property(property)));
        }
    }

    /**
     * The module directories to generate across, or empty for a single module.
     *
     * Empty is the single-module answer rather than a special case: the
     * generator reads its own project directory when told about no modules,
     * which is what a one-module project wants.
     */
    private List<String> modulesOf(Project project, ElastiCoreExtension extension) {
        List<String> out = new ArrayList<String>();

        if (extension.getModules().isPresent() && !extension.getModules().get().isEmpty()) {
            for (String name : extension.getModules().get()) {
                out.add(new File(project.getProjectDir(), name).getAbsolutePath());
            }
            return out;
        }

        for (Project subproject : project.getSubprojects()) {
            if (new File(subproject.getProjectDir(), BLUEPRINT).isDirectory()) {
                out.add(subproject.getProjectDir().getAbsolutePath());
            }
        }
        return out;
    }

    /**
     * Compilation waits for generation.
     *
     * Gradle is otherwise free to compile first, and it then compiles the
     * previous tree - a failure this project has had, whose symptom is a
     * compile error in generated code that no longer matches the model and
     * whose cause is invisible from there. Applied to every module, since in a
     * multi-module build one root task generates for all of them.
     */
    /** The modules on the generator's classpath: those declared in it, and theirs, transitively. */
    private static java.util.Set<String> generatorModules(Project root) {
        java.util.Set<String> found = new java.util.LinkedHashSet<String>();
        java.util.Deque<org.gradle.api.artifacts.Configuration> todo = new java.util.ArrayDeque<org.gradle.api.artifacts.Configuration>();
        todo.add(root.getConfigurations().getByName(TOOLS_CONFIGURATION));
        while (!todo.isEmpty()) {
            for (org.gradle.api.artifacts.Dependency d : todo.poll().getAllDependencies()) {
                if (!(d instanceof org.gradle.api.artifacts.ProjectDependency)) continue;
                String path = projectPath((org.gradle.api.artifacts.ProjectDependency) d);
                if (!found.add(path)) continue;
                Project module = root.findProject(path);
                if (module == null) continue;
                for (String name : new String[]{"compileClasspath", "runtimeClasspath", "api", "implementation", "runtimeOnly", "compileOnly"}) {
                    org.gradle.api.artifacts.Configuration c = module.getConfigurations().findByName(name);
                    if (c != null) todo.add(c);
                }
            }
        }
        return found;
    }

    /**
     * The path of a project dependency: getPath() where Gradle has it (8.11+),
     * getDependencyProject(), deprecated there, before that.
     */
    private static String projectPath(org.gradle.api.artifacts.ProjectDependency d) {
        try {
            return (String) d.getClass().getMethod("getPath").invoke(d);
        } catch (ReflectiveOperationException e) {
            return d.getDependencyProject().getPath();
        }
    }

    private void dependOnGeneration(Project project, TaskProvider<JavaExec> generation) {
        List<Project> targets = new ArrayList<Project>(project.getSubprojects());
        targets.add(project);

        // A module the generator runs - the project's own rules, declared as
        // "elasticore project(':rules')" - is compiled before generation, so
        // it cannot also wait for it: that is a cycle Gradle refuses. The same
        // for the modules it depends on in turn. Decided when a compile task
        // is configured, by which time every project has declared its own.
        for (Project target : targets) {
            target.getTasks().matching(task ->
                    task.getName().equals("compileJava") || task.getName().equals("compileKotlin")
                            // Generation writes resources too - the defaults of
                            // Spring HTTP ports - which have to be there when
                            // they are copied to the classpath.
                            || task.getName().equals("processResources")
            ).configureEach(task -> task.dependsOn(project.provider(() -> {
                // Asked when the task graph is built, after every build script
                // has declared its dependencies - also when a script
                // configures compile tasks eagerly.
                if (!generatorModules(project).contains(target.getPath())) {
                    return java.util.Collections.singletonList(generation.get());
                }
                if (new File(target.getProjectDir(), BLUEPRINT).isDirectory()) {
                    target.getLogger().warn("[elasticore] " + target.getPath() + " holds a blueprint but is on the"
                            + " generator's classpath, so it compiles before generation and without the generated sources.");
                }
                return java.util.Collections.emptyList();
            })));
        }
    }

    /**
     * The Spring Boot version the generated code is compiled against: what
     * the elasticore block declares, or the version of the Spring Boot Gradle
     * plugin applied anywhere in the build - a root that only declares it
     * (apply false) and the modules that apply it alike. Null when the build
     * has no Spring Boot plugin, which the generator reads as Spring Boot 3.
     */
    static String springBootOf(Project project, ElastiCoreExtension extension) {
        if (extension.getSpringBoot().isPresent()) return extension.getSpringBoot().get();
        for (Project p : project.getRootProject().getAllprojects()) {
            Object plugin = p.getPlugins().findPlugin("org.springframework.boot");
            String version = plugin == null ? null : plugin.getClass().getPackage().getImplementationVersion();
            if (version != null) return version;
        }
        try {
            Class<?> type = Class.forName("org.springframework.boot.gradle.plugin.SpringBootPlugin",
                    false, project.getBuildscript().getClassLoader());
            return type.getPackage().getImplementationVersion();
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
