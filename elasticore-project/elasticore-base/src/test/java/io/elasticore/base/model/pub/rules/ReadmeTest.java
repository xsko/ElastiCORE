package io.elasticore.base.model.pub.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The front page, checked against the thing it describes.
 *
 * This exists because of what was actually found in it. The README taught
 * {@code decimal(10,2)} when the DSL separates precision with a dot; it linked
 * to three sample domains that had been renamed years earlier; it pinned an
 * install version four minors behind. None of it failed anything, because a
 * README is not compiled - and it is the first and often only file a new user
 * reads, so every one of those was a wrong answer given to somebody at the
 * exact moment they were deciding whether to trust the tool.
 *
 * The checks are deliberately narrow. They cover the claims that go stale on
 * their own as the code moves underneath them - a path, a version, a bundle
 * name - and not prose, which no test can keep honest.
 */
public class ReadmeTest {

    /** The front page in each language: the Korean one carries the same snippets and links. */
    private static final String[] FRONT_PAGES = {"README.md", "README.ko.md"};

    @Test
    @DisplayName("every path the front page points at exists")
    public void theLinksResolve() throws IOException {
        List<String> broken = new ArrayList<String>();

        for (String doc : new String[]{"README.md", "README.ko.md", "CONTRIBUTING.md"}) {
            File file = new File(repositoryRoot(), doc);
            assertTrue(file.isFile(), doc + " is linked from the project and must exist");

            Matcher link = Pattern.compile("\\]\\(([^)\\s]+)\\)").matcher(read(file));
            while (link.find()) {
                String target = link.group(1);
                if (target.startsWith("http") || target.startsWith("#")) continue;
                // A section of another page (docs/tour.md#status): the page must exist.
                if (target.indexOf('#') > 0) target = target.substring(0, target.indexOf('#'));
                if (!new File(repositoryRoot(), target).exists()) {
                    broken.add(doc + " -> " + target);
                }
            }
        }

        // A dead link on the front page reads as an abandoned project, and the
        // reader has no way to tell whether the file moved or never existed.
        assertTrue(broken.isEmpty(), "the front page points at paths that are not there: " + broken);
    }

    @Test
    @DisplayName("the install snippet names the version on Maven Central")
    public void theInstallVersionIsCurrent() throws IOException {
        String declared = releasedVersion();
        // The README installs from Maven Central, where a snapshot never is.
        assertTrue(!declared.endsWith("-SNAPSHOT"),
                "elasticoreReleasedVersion is the last release, never a snapshot: " + declared);
        List<String> stale = new ArrayList<String>();
        for (String doc : FRONT_PAGES) {
            String readme = read(new File(repositoryRoot(), doc));

            // The runtime libraries have groups of their own -
            // io.elasticore.springboot4 - which a pattern for io.elasticore
            // alone passed over: the runtime pin was the one never checked.
            Matcher coordinate = Pattern.compile("io\\.elasticore(?:\\.[a-z0-9]+)*:[a-z0-9-]+:([0-9][^'\"\\s`]*)")
                    .matcher(readme);
            while (coordinate.find()) {
                if (!coordinate.group(1).equals(declared)) stale.add(doc + ": " + coordinate.group(0));
            }

            // The plugin line spells its version differently - id then version -
            // so the coordinate pattern never sees it, and an audit found exactly
            // that shape of gap: a pin this test did not know it was missing.
            Matcher plugin = Pattern.compile("id 'io\\.elasticore' version '([^']+)'").matcher(readme);
            while (plugin.find()) {
                if (!plugin.group(1).equals(declared)) stale.add(doc + ": " + plugin.group(0));
            }
        }

        // Copying a version that no longer resolves is the first thing a new
        // user does, and the error they get names a missing artifact rather
        // than a stale document.
        assertTrue(stale.isEmpty(), "the README installs " + stale + " but the last release is " + declared
                + " (elasticoreReleasedVersion in gradle.properties)");
    }

    @Test
    @DisplayName("every bundle the front page offers is one a project can actually select")
    public void theBundlesExist() throws IOException {
        Set<String> known = new LinkedHashSet<String>(RuleCatalog.standard().bundleNames());
        Set<String> sets = RuleCatalog.standard().setNames();

        List<String> unknown = new ArrayList<String>();
        for (String doc : FRONT_PAGES) {
            String readme = read(new File(repositoryRoot(), doc));
            int checked = 0;

            // Backticked words in the bundle table shape: the naming is distinctive
            // enough to find without parsing markdown, which would be a parser
            // nobody trusts the verdict of.
            Matcher named = Pattern.compile("`([a-z0-9]+(?:-[a-z0-9]+)+)`").matcher(readme);
            while (named.find()) {
                String word = named.group(1);
                boolean looksLikeABundle = word.contains("springboot") || word.startsWith("ts-")
                        || word.startsWith("ddd-") || word.startsWith("hex");
                if (!looksLikeABundle) continue;
                checked++;
                if (!known.contains(word) && !sets.contains(word)) unknown.add(doc + ": " + word);
            }

            // The names a reader copies into env.yml - publisher: rules: [a, b, ...] -
            // sit inside one longer code span, where the pattern above never looks:
            // until this list was read, the test found nothing on either page.
            Matcher list = Pattern.compile("rules:\\s*\\[([^\\]]*)\\]").matcher(readme);
            while (list.find()) {
                for (String item : list.group(1).split(",")) {
                    String word = item.trim();
                    if (!word.matches("[a-z0-9]+(?:-[a-z0-9]+)+")) continue;   // the closing ellipsis
                    checked++;
                    if (!known.contains(word) && !sets.contains(word)) unknown.add(doc + ": " + word);
                }
            }

            // A check with nothing to check passes forever.
            assertTrue(checked > 0, doc + " names no bundle this test can find - has the way it writes them changed?");
        }

        assertTrue(unknown.isEmpty(),
                "the README offers " + unknown + ", which no project can select; known bundles are " + known);
    }

    /**
     * The version the README must name: the last one released to Maven Central.
     *
     * It used to be the version this build produces, which was right while the
     * README told people to build and install locally. Since the README installs
     * from Maven Central, the build's own version is a -SNAPSHOT between
     * releases that no repository has - so gradle.properties keeps the last
     * release apart, as elasticoreReleasedVersion, and the README follows that.
     */
    private String releasedVersion() throws IOException {
        String text = read(new File(repositoryRoot(), "gradle.properties"));

        Matcher m = Pattern.compile("(?m)^\\s*elasticoreReleasedVersion\\s*=\\s*(\\S+)").matcher(text);
        assertTrue(m.find(), "gradle.properties names no elasticoreReleasedVersion");
        return m.group(1);
    }

    private String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** The checkout root, found by the file this test is about. */
    private File repositoryRoot() {
        File dir = new File(ReadmeTest.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath().replace("%20", " "));
        while (dir != null && !new File(dir, "settings.gradle").isFile()) {
            dir = dir.getParentFile();
        }
        assertTrue(dir != null, "could not find the checkout root from the test's own location");
        return dir;
    }
}
