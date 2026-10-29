package io.elasticore.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin driven by a real Gradle build, because that is the only place it
 * has ever had to work.
 *
 * Asserting on the plugin's Java in isolation would prove that a method
 * returns a list. What a project depends on is different: that applying one
 * line produces tasks, that a build declaring nothing passes nothing, and that
 * a multi-module sweep finds the modules with blueprints and no others. Only a
 * build says those.
 *
 * The runs stop before generation - the framework jar is not on a test's
 * classpath and downloading it would make this a network test - so what is
 * checked is the wiring: the tasks, their arguments, and what the build
 * decided to pass. What happens after the process starts is covered where the
 * process is real, in the sample modules.
 */
public class ElastiCorePluginTest {

    @TempDir
    Path projectDir;

    @Test
    @DisplayName("applying the plugin registers the generation tasks")
    public void theTasksAppear() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore' }");

        String output = run("tasks", "--group=elasticore").getOutput();

        assertTrue(output.contains("elcore "), output);
        assertTrue(output.contains("elcore3 "), output);
        // TypeScript is a second output, not a language to choose, so its
        // task is registered always - running it stays opt-in.
        assertTrue(output.contains("elcore3ts "), output);
    }

    @Test
    @DisplayName("a build that declares nothing passes no rule properties")
    public void silenceStaysSilent() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore' }", REPORTER);

        String reported = reportOf(run("reportElcore"));

        // The plugin invents no declaration: the generator tells a build that
        // said nothing from one that chose, and only then checks whether the
        // project holds 2.x's code before writing the 3.0 code over it.
        assertTrue(!reported.contains("elasticore.architecture"), reported);
        assertTrue(!reported.contains("elasticore.language"), reported);
        assertTrue(!reported.contains("elasticore.rules.v2"), reported);
        assertTrue(!reported.contains("elasticore.rules.v3"), reported);
        assertTrue(reported.contains("elasticore.base.path"), reported);
    }

    @Test
    @DisplayName("a declared Spring Boot generation reaches the generator; none is invented")
    public void theSpringBootGenerationIsPassed() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore' }", "elasticore { springBoot = '4' }", REPORTER);
        String reported = reportOf(run("reportElcore"));
        assertTrue(reported.contains("elasticore.springBoot=4"), reported);
    }

    @Test
    @DisplayName("the two words are passed as written, not resolved here")
    public void theDeclarationIsPassedThrough() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore' }",
                "elasticore { architecture = 'ddd'; language = 'kotlin' }",
                REPORTER);

        String reported = reportOf(run("reportElcore"));

        // The plugin holds no table. RulesConfig turns the pair into a bundle
        // and into the language switch, so a build and the initialization
        // wizard cannot reach different conclusions about one project.
        assertTrue(reported.contains("elasticore.architecture=ddd"), reported);
        assertTrue(reported.contains("elasticore.language=kotlin"), reported);
        assertTrue(!reported.contains("ddd-springboot"),
                "the plugin composed a bundle name itself: " + reported);
    }

    @Test
    @DisplayName("the project's own rule sets are passed to be added to the bundle")
    public void theProjectsRulesArePassed() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore' }",
                "elasticore { architecture = 'hexagonal'; rules = ['com.acme.rules.AcmeRules', 'com.acme.rules.More'] }",
                REPORTER);

        String reported = reportOf(run("reportElcore"));
        assertTrue(reported.contains("elasticore.publisher.rules.add=com.acme.rules.AcmeRules,com.acme.rules.More"), reported);
    }

    @Test
    @DisplayName("rule ids switched off for every domain are passed")
    public void suppressionIsPassed() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore' }",
                "elasticore { architecture = 'hexagonal'; suppress = ['spring-api-errors', 'jpa-comment'] }",
                REPORTER);
        assertTrue(reportOf(run("reportElcore")).contains("elasticore.publisher.suppress=spring-api-errors,jpa-comment"));
    }

    @Test
    @DisplayName("merging edited generated files is passed as said, and left to the generator when unsaid")
    public void mergeIsPassed() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore' }", "elasticore { merge = true }", REPORTER);
        assertTrue(reportOf(run("reportElcore")).contains("elasticore.merge=true"));

        writeBuild("plugins { id 'io.elasticore' }", "elasticore { merge = false }", REPORTER);
        assertTrue(reportOf(run("reportElcore")).contains("elasticore.merge=false"));

        writeBuild("plugins { id 'io.elasticore' }", "elasticore { }", REPORTER);
        String reported = reportOf(run("reportElcore"));
        assertTrue(!reported.contains("elasticore.merge"), reported);
    }

    @Test
    @DisplayName("a name for the Ossie model is passed, and only when one is given")
    public void theOssieNameIsPassed() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore' }",
                "elasticore { rules = ['ossie-export']; ossieName = 'acme_erp' }",
                REPORTER);
        assertTrue(reportOf(run("reportElcore")).contains("elasticore.ossie.name=acme_erp"));

        // Unset, the exporter names the model after its domains, as before.
        writeBuild("plugins { id 'io.elasticore' }", "elasticore { rules = ['ossie-export'] }", REPORTER);
        String reported = reportOf(run("reportElcore"));
        assertTrue(!reported.contains("elasticore.ossie.name"), reported);
    }

    @Test
    @DisplayName("a framework version the project declares is left as written")
    public void aDeclaredFrameworkIsLeftAlone() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore' }",
                "dependencies { elasticore 'io.elasticore:elasticore-base:1.2.3' }",
                "tasks.register('reportTools') {\n"
                        + "    doLast {\n"
                        + "        def deps = configurations.elasticore.allDependencies.collect { it.name + ':' + it.version }\n"
                        + "        println '<<<' + deps.join(';') + '>>>'\n"
                        + "    }\n"
                        + "}");

        assertEquals("elasticore-base:1.2.3", reportOf(run("reportTools")));
    }

    @Test
    @DisplayName("rules alone put the build on the rule engine, which is what reads them")
    public void rulesAloneChooseTheRuleEngine() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'java'; id 'io.elasticore' }",
                "elasticore { rules = ['com.acme.rules.AcmeRules'] }");

        String output = run("compileJava", "--dry-run").getOutput();
        assertTrue(output.contains(":elcore3"), output);
    }

    @Test
    @DisplayName("nor do the modules the rules module depends on")
    public void theRulesModulesDependenciesAreNotACycle() throws IOException {
        writeSettings("single", "rules", "shared");
        write("shared/build.gradle", "plugins { id 'java' }\n");
        write("rules/build.gradle", "plugins { id 'java' }\ndependencies { implementation project(':shared') }\n");
        writeBuild("plugins { id 'java'; id 'io.elasticore' }",
                "dependencies { elasticore project(':rules') }",
                "elasticore { architecture = 'hexagonal'; rules = ['com.acme.rules.AcmeRules'] }");

        BuildResult result = run("compileJava", "--dry-run");
        assertTrue(result.getOutput().contains(":shared:compileJava"), result.getOutput());
    }

    @Test
    @DisplayName("also when the root script configures compile tasks eagerly")
    public void eagerConfigurationIsNotACycle() throws IOException {
        writeSettings("single", "rules", "kernel");
        write("kernel/build.gradle", "plugins { id 'java' }\n");
        write("rules/build.gradle", "plugins { id 'java' }\ndependencies { implementation project(':kernel') }\n");
        writeBuild("plugins { id 'java'; id 'io.elasticore' }",
                "subprojects { tasks.withType(JavaCompile) { options.encoding = 'UTF-8' } }",
                "dependencies { elasticore project(':rules') }",
                "elasticore { architecture = 'hexagonal'; rules = ['com.acme.rules.AcmeRules'] }");

        BuildResult result = run("compileJava", "--dry-run");
        assertTrue(result.getOutput().contains(":kernel:compileJava"), result.getOutput());
    }

    @Test
    @DisplayName("a rules module the generator runs does not wait for generation")
    public void theRulesModuleIsNotACycle() throws IOException {
        writeSettings("single", "rules");
        write("rules/build.gradle", "plugins { id 'java' }\n");
        writeBuild("plugins { id 'java'; id 'io.elasticore' }",
                "dependencies { elasticore project(':rules') }",
                "elasticore { architecture = 'hexagonal'; rules = ['com.acme.rules.AcmeRules'] }");

        // Resolving the task graph is enough: the cycle is refused there.
        BuildResult result = run("compileJava", "--dry-run");
        assertTrue(result.getOutput().contains(":rules:compileJava"), result.getOutput());
    }

    @Test
    @DisplayName("adding a rules jar to the generator keeps the framework on its classpath")
    public void theFrameworkStaysOnTheClasspath() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore' }",
                "dependencies { elasticore files('acme-rules.jar') }",
                "tasks.register('reportTools') {\n"
                        + "    doLast {\n"
                        + "        def deps = configurations.elasticore.allDependencies.collect { (it.group ?: '') + ':' + it.name }\n"
                        + "        println '<<<' + deps.join(';') + '>>>'\n"
                        + "    }\n"
                        + "}");

        String reported = reportOf(run("reportTools"));
        // Declaring anything used to replace the framework; the generator's
        // main class then went missing.
        assertTrue(reported.contains("io.elasticore:elasticore-base"), reported);
    }

    @Test
    @DisplayName("a multi-module build sweeps the modules that carry a blueprint")
    public void modulesAreFound() throws IOException {
        writeSettings("multi", "core-domain", "app-admin", "app-report");
        blueprintIn("core-domain");
        blueprintIn("app-admin");
        // app-report has none, and must not be swept in.

        writeBuild("plugins { id 'io.elasticore' }", REPORTER);
        moduleBuild("core-domain");
        moduleBuild("app-admin");
        moduleBuild("app-report");

        String reported = reportOf(run("reportElcore"));

        assertTrue(reported.contains("core-domain"), reported);
        assertTrue(reported.contains("app-admin"), reported);
        assertTrue(!reported.contains("app-report"),
                "a module with no blueprint was swept in: " + reported);
    }

    @Test
    @DisplayName("naming the modules explicitly overrides the sweep")
    public void modulesCanBeNamed() throws IOException {
        writeSettings("multi", "core-domain", "app-admin");
        blueprintIn("core-domain");
        blueprintIn("app-admin");

        writeBuild("plugins { id 'io.elasticore' }",
                "elasticore { modules = ['core-domain'] }",
                REPORTER);
        moduleBuild("core-domain");
        moduleBuild("app-admin");

        String reported = reportOf(run("reportElcore"));

        // A module can hold a blueprint that should not take part, and the
        // sweep cannot know that.
        assertTrue(reported.contains("core-domain"), reported);
        assertTrue(!reported.contains("app-admin"), reported);
    }

    @Test
    @DisplayName("compilation waits for generation - the rule engine, by default")
    public void compileDependsOnGeneration() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore'; id 'java' }");

        String output = run("compileJava", "--dry-run").getOutput();

        // Gradle is otherwise free to compile the previous tree, whose symptom
        // is a compile error in generated code that no longer matches the
        // model and whose cause is invisible from there. Since 3.0.261028-rc3 a
        // build that declares nothing is on the rule engine: ":elcore3
        // SKIPPED", and not the 2.x generator - ":elcore SKIPPED" exactly,
        // since the loose ":elcore" is also satisfied by :elcore3.
        assertTrue(output.contains(":elcore3 SKIPPED"), output);
        assertTrue(!output.contains(":elcore SKIPPED"), output);
    }

    @Test
    @DisplayName("a build that keeps 2.x's code waits for the 2.x generator, and says so to elcore3")
    public void outputV2KeepsTheTwoXGenerator() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore'; id 'java' }",
                "elasticore { output = 'v2' }",
                REPORTER);

        String output = run("compileJava", "--dry-run").getOutput();
        assertTrue(output.contains(":elcore SKIPPED"), output);
        assertTrue(!output.contains(":elcore3 SKIPPED"), output);

        // elcore3 run by hand on the same project writes the 2.x-compatible
        // code too, rather than the default it would otherwise take.
        String reported = reportOf(run("reportElcore"));
        assertTrue(reported.contains("elasticore.rules.v2=true"), reported);
        assertTrue(!reported.contains("elasticore.rules.v3"), reported);
    }

    @Test
    @DisplayName("output = 'v3' is passed as the choice it is")
    public void outputV3IsPassed() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore' }",
                "elasticore { output = 'v3' }",
                REPORTER);

        String reported = reportOf(run("reportElcore"));
        assertTrue(reported.contains("elasticore.rules.v3=true"), reported);
        assertTrue(!reported.contains("elasticore.rules.v2"), reported);
    }

    @Test
    @DisplayName("2.x's code cannot be asked of a hexagon, and an unknown output is refused")
    public void outputV2RefusesWhatOnlyTheRuleEngineReads() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore'; id 'java' }",
                "elasticore { output = 'v2'; architecture = 'hexagonal' }");
        String refused = runAndFail("compileJava", "--dry-run").getOutput();
        assertTrue(refused.contains("output = 'v2'"), refused);

        writeBuild("plugins { id 'io.elasticore'; id 'java' }",
                "elasticore { output = 'v4' }");
        String unknown = runAndFail("compileJava", "--dry-run").getOutput();
        assertTrue(unknown.contains("'v3' (the default) or 'v2'"), unknown);
    }

    @Test
    @DisplayName("a build on the rule engine waits for the rule engine")
    public void theOrderingFollowsTheDeclaration() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore'; id 'java' }",
                "elasticore { architecture = 'hexagonal' }");

        String output = run("compileJava", "--dry-run").getOutput();

        // Waiting for the other generator would let compilation start against
        // a tree nothing had written - the same class of failure as compiling
        // before generation, arrived at from the other side.
        assertTrue(output.contains(":elcore3"), output);
        assertTrue(!output.contains(":elcore SKIPPED"),
                "a rule-engine project also waits for the 2.x extractor: " + output);
    }

    @Test
    @DisplayName("the TypeScript client is written with the build when asked for, and only then")
    public void theTypescriptClientRunsWithTheBuildWhenAsked() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore'; id 'java' }",
                "elasticore { architecture = 'hexagonal' }");
        String off = run("compileJava", "--dry-run").getOutput();
        // Registered, never run by a build: the client went stale after every model change.
        assertTrue(!off.contains(":elcore3ts"), off);

        writeBuild("plugins { id 'io.elasticore'; id 'java' }",
                "elasticore { architecture = 'hexagonal'; typescriptClient = true }",
                "tasks.register('reportElcore3') {\n"
                        + "    doLast {\n"
                        + "        def t = tasks.getByName('elcore3')\n"
                        + "        println '<<<' + t.systemProperties.collect { k, v -> k + '=' + v }.join(';') + '>>>'\n"
                        + "    }\n"
                        + "}");
        String on = run("compileJava", "--dry-run").getOutput();
        assertTrue(on.contains(":elcore3 SKIPPED"), on);
        assertTrue(on.contains(":elcore3ts SKIPPED"), on);
        assertTrue(on.indexOf(":elcore3 SKIPPED") < on.indexOf(":elcore3ts SKIPPED"), on);
        assertTrue(on.indexOf(":elcore3ts SKIPPED") < on.indexOf(":compileJava SKIPPED"), on);
        // elcore3 is told, so it does not warn of a client it did not refresh.
        assertTrue(reportOf(run("reportElcore3")).contains("elasticore.rules.tsWithBuild=true"));
    }

    @Test

    @DisplayName("turning the ordering off leaves compilation alone")
    public void theOrderingCanBeDeclined() throws IOException {
        writeSettings("single");
        writeBuild("plugins { id 'io.elasticore'; id 'java' }",
                "elasticore { generateBeforeCompile = false }");

        String output = run("compileJava", "--dry-run").getOutput();

        assertTrue(!output.contains(":elcore"), output);
    }

    // ---- the harness --------------------------------------------------

    /**
     * A task that prints what elcore was configured with.
     *
     * Nothing is executed: the properties are settled while the build is
     * configured, so they can be read without starting a process - which also
     * keeps the framework jar, and a repository to fetch it from, out of a
     * test that is about wiring.
     */
    private static final String REPORTER =
            "tasks.register('reportElcore') {\n"
            + "    doLast {\n"
            + "        def elcore = tasks.getByName('elcore')\n"
            + "        println '<<<' + elcore.systemProperties.collect { k, v -> k + '=' + v }.join(';') + '>>>'\n"
            + "    }\n"
            + "}";

    private String reportOf(BuildResult result) {
        String output = result.getOutput();
        int open = output.indexOf("<<<");
        int close = output.indexOf(">>>", open);
        assertTrue(open >= 0 && close > open, "the reporter printed nothing:\n" + output);
        return output.substring(open + 3, close);
    }

    private BuildResult run(String... arguments) {
        String[] withStacktrace = new String[arguments.length + 1];
        System.arraycopy(arguments, 0, withStacktrace, 0, arguments.length);
        withStacktrace[arguments.length] = "--stacktrace";

        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(withStacktrace)
                .forwardOutput()
                .build();
    }

    private BuildResult runAndFail(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .forwardOutput()
                .buildAndFail();
    }

    private void writeSettings(String name, String... modules) throws IOException {
        StringBuilder text = new StringBuilder("rootProject.name = '" + name + "'\n");
        for (String module : modules) {
            text.append("include('").append(module).append("')\n");
        }
        write("settings.gradle", text.toString());
    }

    private void writeBuild(String... blocks) throws IOException {
        write("build.gradle", String.join("\n\n", blocks) + "\n");
    }

    private void moduleBuild(String module) throws IOException {
        write(module + "/build.gradle", "// a module of the sample build\n");
    }

    private void blueprintIn(String module) throws IOException {
        Files.createDirectories(projectDir.resolve(module + "/src/main/resources/blueprint/demo"));
    }

    private void write(String path, String content) throws IOException {
        File file = projectDir.resolve(path).toFile();
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs(),
                "could not create " + file.getParent());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("the framework version it resolves is the version it was built with")
    public void theVersionIsGenerated() {
        // Written by hand this would be a second place to bump, and the one
        // people forget - which is how an install snippet ends up four minors
        // behind, as this project's README did.
        assertEquals(Versions.ELASTICORE.trim(), Versions.ELASTICORE,
                "the generated version carries whitespace");
        assertTrue(Versions.ELASTICORE.startsWith("3."),
                "unexpected generated version: " + Versions.ELASTICORE);
    }
}
