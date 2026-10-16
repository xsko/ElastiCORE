package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.util.ConsoleLog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The front page's quick start, generated as it is written.
 *
 * ReadmeTest keeps the README's links, versions and bundle names honest, but
 * the DSL in it was never run. A block that no longer loads, a warning a new
 * user would meet on the first run, or a file the README promises and the
 * generator no longer writes - each of those passed the build, and each is
 * the first thing somebody deciding whether to trust the tool runs into.
 *
 * So the test takes the blocks out of each front page - the build file's
 * elasticore block and Spring Boot version, env.yml, model/entity.yml - puts
 * them in a fresh project the way step 2 says, generates it the way the
 * plugin would, and reads the result against the list in step 3. It also
 * checks the sign-in call step 4 names. What it reads is the README itself, so
 * a change to the quick start is checked without anyone remembering to.
 */
public class ReadmeQuickStartTest {

    private static final List<File> projects = new ArrayList<File>();

    @Test
    public void theEnglishQuickStartGeneratesWhatItLists() throws Exception {
        check("README.md");
    }

    @Test
    public void theKoreanQuickStartGeneratesWhatItLists() throws Exception {
        check("README.ko.md");
    }

    @Test
    public void theSignInCallTheQuickStartNamesExists() throws IOException {
        for (String page : new String[]{"README.md", "README.ko.md"}) {
            String readme = read(new File(root(), page));
            Matcher call = Pattern.compile("`([A-Z][A-Za-z]+)\\.([a-z][A-Za-z]+)\\(request,\\s*\\w+,\\s*\"").matcher(readme);
            Matcher pkg = Pattern.compile("`(io\\.elasticore\\.springboot3(?:\\.[a-z]+)+)`").matcher(readme);
            assertTrue(call.find(), page + " names no sign-in call - has step 4 changed shape?");
            assertTrue(pkg.find(), page + " names no package for the sign-in call - has step 4 changed shape?");

            // The runtime library for Boot 4 compiles these same sources, so
            // the one file answers for both.
            File source = new File(root(), "elasticore-project/elasticore-springboot3/src/main/java/"
                    + pkg.group(1).replace('.', '/') + "/" + call.group(1) + ".java");
            assertTrue(source.isFile(), page + " tells the reader to call " + call.group(1) + "." + call.group(2)
                    + " in " + pkg.group(1) + ", which does not exist: " + source);

            // The call as step 4 writes it: a request, an id and role names.
            Pattern declared = Pattern.compile("public\\s+static\\s+\\S+\\s+" + call.group(2)
                    + "\\s*\\(\\s*HttpServletRequest\\s+\\w+\\s*,\\s*Object\\s+\\w+\\s*,\\s*String\\s*\\.\\.\\.\\s*\\w+\\s*\\)");
            assertTrue(declared.matcher(read(source)).find(), page + " tells the reader to call "
                    + call.group(1) + "." + call.group(2) + "(request, userId, \"ADMIN\"), which "
                    + source.getName() + " no longer declares");
        }
    }

    private void check(String page) throws Exception {
        String readme = read(new File(root(), page));

        // Step 1's build file: the one block that applies the plugin.
        String gradle = block(readme, "gradle", "id 'io.elasticore'", page);
        String architecture = setting(gradle, "architecture", page);
        String language = setting(gradle, "language", page);
        String springBoot = find(gradle, "id 'org\\.springframework\\.boot' version '([^']+)'", page,
                "the Spring Boot plugin's version");
        String env = block(readme, "yaml", "# env.yml", page);
        String model = block(readme, "yaml", "# model/entity.yml", page);
        String domain = find(readme, "`src/main/resources/blueprint/([^/`]+)/`", page, "the blueprint folder");

        File project = Files.createTempDirectory("elcore-readme").toFile();
        projects.add(project);
        File dir = new File(project, "src/main/resources/blueprint/" + domain);
        assertTrue(new File(dir, "model").mkdirs(), "could not make " + new File(dir, "model"));
        Files.write(new File(dir, "env.yml").toPath(), env.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "model/entity.yml").toPath(), model.getBytes(StandardCharsets.UTF_8));

        StringBuilder log = new StringBuilder();
        String setup = page + "'s quick start (" + architecture + ", " + language + ", Spring Boot " + springBoot + ")";
        int status;
        try {
            status = generate(project, architecture, language, springBoot, log);
        } catch (RuntimeException e) {
            throw new AssertionError(setup + " made the generator throw " + e + "\n--- generator output ---\n" + log, e);
        }
        if (status != 0) {
            fail(setup + " made the generator stop with " + status + "\n--- generator output ---\n" + log);
        }

        List<String> problems = new ArrayList<String>();
        int warnings = ConsoleLog.getAccumulatedCount("DSL_WARN") + ConsoleLog.countStoredLogkey("DSL_WARN");
        if (warnings > 0) problems.add(warnings + " DSL warning(s)");
        for (String line : log.toString().split("\n")) {
            if (line.contains(ConsoleLog.RED)) {
                problems.add("warned: " + line.replace(ConsoleLog.RED, "").replace(ConsoleLog.RESET, "").trim());
            }
        }

        // Step 3: "The code appears under `src/main/java/...`:" and a list of
        // paths, one per line; a path ending in "..." is a folder that must
        // hold something.
        Matcher where = Pattern.compile("`(src/main/java/[^`]+)`").matcher(readme);
        assertTrue(where.find(), page + " names no folder for the generated code - has step 3 changed shape?");
        String listing = nextFence(readme, where.end(), page);
        File code = new File(project, where.group(1));
        int listed = 0;
        for (String line : listing.split("\n")) {
            String path = line.trim().split("\\s+")[0];
            if (path.isEmpty()) continue;
            listed++;
            if (path.endsWith("/...")) {
                File folder = new File(code, path.substring(0, path.length() - 4));
                if (!hasFile(folder)) problems.add("lists " + path + ", but " + folder + " holds no file");
            } else if (!new File(code, path).isFile()) {
                problems.add("lists " + path + ", which was not generated");
            }
        }
        assertTrue(listed > 0, page + " lists no generated file under " + where.group(1));

        assertTrue(problems.isEmpty(), setup + " does not hold: " + problems + "\n--- generator output ---\n" + log);
    }

    /**
     * One generation of the project, with what the plugin would pass and nothing else: every elasticore property
     * another test may have left is set aside for the run and put back after it.
     */
    private static int generate(File project, String architecture, String language, String springBoot,
                                StringBuilder log) throws Exception {
        Map<String, String> saved = new LinkedHashMap<String, String>();
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("elasticore.") || key.equals("mode")) saved.put(key, System.getProperty(key));
        }
        PrintStream out = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        ConsoleLog.clear();
        int status;
        ReflectiveOperationException resetFailure = null;
        try {
            for (String key : saved.keySet()) System.clearProperty(key);
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            System.setProperty("elasticore.architecture", architecture);
            System.setProperty("elasticore.language", language);
            System.setProperty("elasticore.springBoot", springBoot);
            System.setOut(new PrintStream(captured, true, "UTF-8"));
            status = RulesGenRunner.run(new String[0]);
        } finally {
            System.setOut(out);
            // Kept whatever the run did, so a run that throws still shows what it said.
            String text = new String(captured.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            out.print(text);
            log.append(text);
            for (String key : System.getProperties().stringPropertyNames()) {
                if (key.startsWith("elasticore.") || key.equals("mode")) System.clearProperty(key);
            }
            for (Map.Entry<String, String> en : saved.entrySet()) System.setProperty(en.getKey(), en.getValue());
            try {
                java.lang.reflect.Field tmp = io.elasticore.base.model.core.BaseECoreModelContext.class
                        .getDeclaredField("tmpCtx");
                tmp.setAccessible(true);
                tmp.set(null, null);
            } catch (ReflectiveOperationException e) {
                resetFailure = e;
            }
            SpecFactory.valueObjectsInDto(false);
            SpecFactory.rootsSplitByDefault(false);
            SpecFactory.commandOnlyRoots(false);
        }
        // Reached only when the run itself did not throw, so this cannot hide its failure.
        if (resetFailure != null) {
            throw new AssertionError("could not reset the model context the generator leaves behind", resetFailure);
        }
        return status;
    }

    /** The fenced block of the given language whose first line starts with the marker, without its fences. */
    private static String block(String text, String lang, String marker, String page) {
        Matcher fence = Pattern.compile("(?m)^```" + lang + "\\s*\\n(.*?)^```", Pattern.DOTALL).matcher(text);
        String found = null;
        while (fence.find()) {
            String body = fence.group(1);
            boolean first = body.trim().startsWith(marker);
            if (first || ("gradle".equals(lang) && body.contains(marker))) {
                if (found != null) fail(page + " has two ```" + lang + " blocks for " + marker);
                found = body;
            }
        }
        if (found == null) fail(page + " has no ```" + lang + " block for " + marker + " - has the quick start changed shape?");
        return found;
    }

    /** The fenced block right after the given position. */
    private static String nextFence(String text, int from, String page) {
        Matcher fence = Pattern.compile("(?m)^```[a-z]*\\s*\\n(.*?)^```", Pattern.DOTALL).matcher(text);
        if (!fence.find(from)) fail(page + " has no listing after the folder of the generated code");
        return fence.group(1);
    }

    private static String setting(String gradle, String name, String page) {
        return find(gradle, "(?m)^\\s*" + name + "\\s*=\\s*'([a-z]+)'", page, "the elasticore block's " + name);
    }

    private static String find(String text, String regex, String page, String what) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) fail(page + " does not say " + what + " - has the quick start changed shape?");
        return m.group(1);
    }

    private static boolean hasFile(File folder) {
        File[] children = folder.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (child.isFile() || hasFile(child)) return true;
        }
        return false;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** The checkout root, found from the test's own location. */
    private static File root() {
        File dir = new File(ReadmeQuickStartTest.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath().replace("%20", " "));
        while (dir != null && !new File(dir, "settings.gradle").isFile()) {
            dir = dir.getParentFile();
        }
        assertTrue(dir != null, "could not find the checkout root from the test's own location");
        return dir;
    }

    @AfterAll
    static void clean() throws IOException {
        String temp = new File(System.getProperty("java.io.tmpdir")).getCanonicalPath() + File.separator;
        for (File project : projects) {
            // Only what this test made: a folder it created under the temp directory.
            if (!project.getCanonicalPath().startsWith(temp) || !project.getName().startsWith("elcore-readme")) continue;
            Files.walkFileTree(project.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
