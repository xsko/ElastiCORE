package io.elasticore.base.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Puts the guides beside the model they describe.
 *
 * The version a reader has is the version they are running, because the copy
 * happens on every generation rather than when somebody remembers. That is
 * also why it is here rather than inside one entry point: it lived in
 * {@code ModelExtractor}, so a project generating through the rule engine got
 * no guides at all - and that is precisely the project reading about bundles
 * for the first time. Both sample modules on the rule engine had zero .md
 * files in their blueprint directory, and nothing reported it, because a
 * document that was never written looks the same as one nobody opened.
 *
 * Failure is always a warning, never an exception. A guide that could not be
 * copied is a worse manual, not a broken build.
 *
 * After the copy it writes {@link GuideIndex#FILE_NAME}: every section of the
 * guides with its line range, so a reader can open one section instead of a
 * whole guide.
 */
public final class GuideDeployer {

    /**
     * The guides a project gets. Adding one here is the whole registration -
     * {@code GuideCoverageTest} then holds it to naming what it claims to.
     */
    public static final String[] GUIDE_FILES = {
            "elasticore-dsl-guide.md",
            "elasticore-env-guide.md",
            "elasticore-dsl-port-guide.md",
            "elasticore-dev-guide.md",
            "elasticore-annotation-guide.md",
            "elasticore-rule-guide.md",
            "elasticore-rule-dev-guide.md",
            "elasticore-ddd-customize-guide.md",
            "elasticore-migration-guide.md"
    };

    private static final String CLASSPATH_DIR = "/elasticore-guide/";

    private GuideDeployer() {
    }

    /**
     * Copies every guide into the blueprint root.
     *
     * @param blueprintRootPath the directory holding the domain directories
     * @param developmentRoot   a checkout root to read from when running inside
     *                          this repository; null in a user's project, where
     *                          the classpath copy is the only one there is
     */
    public static void deployTo(String blueprintRootPath, String developmentRoot) {
        if (blueprintRootPath == null) return;

        ConsoleLog.printInfo("Copying guide files to blueprint root: " + blueprintRootPath);
        try {
            File source = developmentSource(developmentRoot);
            if (source == null) {
                copyFromClasspath(blueprintRootPath);
            } else {
                copyFromDirectory(source, blueprintRootPath);
            }
        } catch (Exception e) {
            ConsoleLog.printWarn("Error copying guide files to blueprint root: " + e.getMessage());
        }
        writeIndex(blueprintRootPath);
    }

    /**
     * Indexes the guides that are now in the blueprint root. Read from the
     * copies, not the sources, so the line numbers are those of the files a
     * reader opens.
     */
    private static void writeIndex(String blueprintRootPath) {
        Path blueprint = Paths.get(blueprintRootPath);
        if (!Files.isRegularFile(blueprint.resolve(GUIDE_FILES[0]))) return;
        try {
            GuideIndex.writeTo(blueprint, GUIDE_FILES);
        } catch (Exception e) {
            ConsoleLog.printWarn("Failed to write " + GuideIndex.FILE_NAME + ": " + e.getMessage());
        }
    }

    /**
     * The guide directory in a checkout, or null when there is not one.
     *
     * Two shapes are tried because this repository is one of its own users:
     * the module path when run from the root, and the module-relative path
     * when run from inside elasticore-base.
     */
    private static File developmentSource(String developmentRoot) {
        if (developmentRoot == null) return null;

        String[] candidates = {
                developmentRoot + "/elasticore-project/elasticore-base/src/main/resources/elasticore-guide",
                developmentRoot + "/src/main/resources/elasticore-guide"
        };
        for (String candidate : candidates) {
            File dir = new File(candidate);
            if (dir.isDirectory()) return dir;
        }
        return null;
    }

    private static void copyFromDirectory(File source, String blueprintRootPath) {
        Path blueprint = Paths.get(blueprintRootPath);

        for (String guide : GUIDE_FILES) {
            Path from = Paths.get(source.getPath(), guide);
            if (!Files.exists(from)) {
                ConsoleLog.printWarn("Guide file not found: " + from);
                continue;
            }
            try {
                Files.copy(from, blueprint.resolve(guide), StandardCopyOption.REPLACE_EXISTING);
                ConsoleLog.printInfo("Copied guide file: " + guide);
            } catch (IOException e) {
                ConsoleLog.printWarn("Failed to copy guide file " + guide + ": " + e.getMessage());
            }
        }
    }

    private static void copyFromClasspath(String blueprintRootPath) {
        if (GuideDeployer.class.getResourceAsStream(CLASSPATH_DIR + GUIDE_FILES[0]) == null) {
            ConsoleLog.printWarn("Guide files not found in classpath or file system."
                    + " Skipping guide file copying.");
            return;
        }

        Path blueprint = Paths.get(blueprintRootPath);
        for (String guide : GUIDE_FILES) {
            String resource = CLASSPATH_DIR + guide;
            InputStream in = GuideDeployer.class.getResourceAsStream(resource);
            if (in == null) {
                ConsoleLog.printWarn("Guide file not found in classpath: " + resource);
                continue;
            }
            try {
                Files.copy(in, blueprint.resolve(guide), StandardCopyOption.REPLACE_EXISTING);
                ConsoleLog.printInfo("Copied guide file from classpath: " + guide);
            } catch (IOException e) {
                ConsoleLog.printWarn("Failed to copy guide file " + guide
                        + " from classpath: " + e.getMessage());
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // closing a resource stream that already delivered its bytes
                }
            }
        }
    }
}
