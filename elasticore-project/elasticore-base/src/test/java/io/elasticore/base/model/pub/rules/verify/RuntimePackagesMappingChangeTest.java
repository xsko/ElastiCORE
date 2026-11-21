package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;

import static io.elasticore.base.model.pub.rules.verify.RuntimePackagesGenerationTest.delete;
import static io.elasticore.base.model.pub.rules.verify.RuntimePackagesGenerationTest.resetContext;
import static io.elasticore.base.model.pub.rules.verify.RuntimePackagesGenerationTest.run;
import static io.elasticore.base.model.pub.rules.verify.RuntimePackagesGenerationTest.snapshot;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * runtimePackages changed in runs that do not write the whole project: one
 * domain (-Pdomain), a run whose model check fails. Each cached text carries
 * the mapping it was renamed with, so the next run renames each file from its
 * own - never twice. And a cache whose record of that mapping is unreadable,
 * hand-edited, or cannot be undone.
 */
public class RuntimePackagesMappingChangeTest {

    /** B is under A: renaming from A twice would make com.acme.rt.sb3.sb3. */
    private static final String A = "io.elasticore.springboot3=com.acme.rt";

    private static final String B = "io.elasticore.springboot3=com.acme.rt.sb3";

    private static final String MAPPINGS = ".runtime-packages";

    private File project;

    @BeforeEach
    void create() throws IOException {
        project = Files.createTempDirectory("elcore-rtpkg").toFile();
    }

    @AfterEach
    void clean() throws Exception {
        resetContext();
        delete(project);
    }

    private void domain(String name, String entity) throws IOException {
        File dir = new File(project, "src/main/resources/blueprint/" + name);
        if (!dir.isDirectory()) assertTrue(new File(dir, "model").mkdirs());
        Files.write(
                new File(dir, "env.yml").toPath(),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: " + name,
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    base: com.acme." + name,
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(
                new File(dir, "model/model.yml").toPath(),
                String.join(
                                "\n",
                                "entity:",
                                "  " + entity + ":",
                                "    meta: entity @root @expose(20)",
                                "    fields:",
                                "      " + Character.toLowerCase(entity.charAt(0)) + entity.substring(1)
                                        + "Id: long @id @sequence",
                                "      name: string(30)!",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private String generate(String mapping, String domainFilter) {
        resetQuietly();
        String saved = System.getProperty("elasticore.domain");
        if (domainFilter == null) System.clearProperty("elasticore.domain");
        else System.setProperty("elasticore.domain", domainFilter);
        try {
            return run(project, mapping, null, "hexagonal", "java");
        } finally {
            if (saved == null) System.clearProperty("elasticore.domain");
            else System.setProperty("elasticore.domain", saved);
        }
    }

    private static void resetQuietly() {
        try {
            resetContext();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private File find(String suffix) throws IOException {
        try (java.util.stream.Stream<java.nio.file.Path> all = Files.walk(project.toPath())) {
            return all.filter(p -> p.toString().replace('\\', '/').endsWith(suffix))
                    .findFirst()
                    .map(java.nio.file.Path::toFile)
                    .orElse(null);
        }
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void write(File f, String text) throws IOException {
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private File cacheFile() {
        return new File(project, GeneratedCache.DIR + "/" + GeneratedCache.NAME);
    }

    private Map<String, String> rawCache() throws IOException {
        return GeneratedCache.read(Files.readAllBytes(cacheFile().toPath()));
    }

    private void writeRawCache(Map<String, String> entries) throws IOException {
        Files.write(cacheFile().toPath(), GeneratedCache.zip(new TreeMap<String, String>(entries)));
    }

    /** The line after an inline runtime name in a domain's ApiErrors (a skeleton), edited " // mine". */
    private File editNextToTheInlineName(String domainClass, String pkg) throws IOException {
        File errors = find(domainClass + "ApiErrors.java");
        assertTrue(errors != null, "no " + domainClass + "ApiErrors.java");
        String text = read(errors);
        String inline = "@ExceptionHandler(" + pkg + ".validation.NotFoundException.class)";
        int at = text.indexOf(inline);
        assertTrue(at >= 0, text);
        int end = text.indexOf('\n', text.indexOf('\n', at) + 1);
        write(errors, text.substring(0, end) + " // mine" + text.substring(end));
        return errors;
    }

    /** Nothing anywhere names the mapping twice, and the edited files are the developer's, renamed once. */
    private void assertRenamedOnce(File... edited) throws IOException {
        for (Map.Entry<String, String> en : snapshot(project).entrySet()) {
            assertFalse(en.getValue().contains("sb3.sb3"), en.getKey() + " is renamed twice");
        }
        for (String text : rawCache().values()) assertFalse(text.contains("sb3.sb3"), "the cache is renamed twice");
        for (File f : edited) {
            String text = read(f);
            assertTrue(text.contains("@ExceptionHandler(com.acme.rt.sb3.validation.NotFoundException.class)"), text);
            assertTrue(text.contains(" // mine"), text);
            assertFalse(text.contains("<<<<<<<"), text);
        }
    }

    /**
     * The mapping changed, then one domain was generated alone: its files and
     * their bases name B, the other domain's still A. The next whole run
     * renames each from its own mapping - once.
     */
    @Test
    public void aRunOfOneDomainAfterAChangeRenamesOnce() throws Exception {
        domain("shop", "Item");
        domain("billing", "Bill");
        assertTrue(generate(A, null).startsWith("exit 0\n"));
        File shop = editNextToTheInlineName("Shop", "com.acme.rt");
        File billing = editNextToTheInlineName("Billing", "com.acme.rt");

        String one = generate(B, "shop");
        assertTrue(one.startsWith("exit 0\n"), one);
        assertTrue(read(shop).contains("com.acme.rt.sb3.validation"), read(shop));
        assertTrue(read(billing).contains("@ExceptionHandler(com.acme.rt.validation"), "billing was not to be written");
        // And billing's base says the edited line was something else: the whole run keeps the file as the
        // developer's (KEPT), renamed - from A, the mapping its base and the file were written with.
        Map<String, String> cache = rawCache();
        String key = "src/main/java/com/acme/billing/adapter/in/web/BillingApiErrors.java";
        assertTrue(cache.containsKey(key), cache.keySet().toString());
        cache.put(key, read(billing).replace(" // mine", " // old"));
        writeRawCache(cache);

        String whole = generate(B, null);
        assertTrue(whole.startsWith("exit 0\n"), whole);
        assertTrue(whole.contains("BillingApiErrors.java  KEPT("), whole);
        assertRenamedOnce(shop, billing);
        String kept = rawCache().get(key);
        assertTrue(kept.contains(" // old") && kept.contains("com.acme.rt.sb3.validation"), kept);
    }

    /**
     * The mapping changed in a run whose model check failed - it wrote what
     * it could read, and saved the cache. The next run renames from what
     * each file was written with: once.
     */
    @Test
    public void aRunWhoseModelCheckFailedAfterAChangeRenamesOnce() throws Exception {
        domain("shop", "Item");
        assertTrue(generate(A, null).startsWith("exit 0\n"));
        File shop = editNextToTheInlineName("Shop", "com.acme.rt");
        File broken = new File(project, "src/main/resources/blueprint/shop/model/broken.yml");
        write(
                broken,
                "entity:\n  Broken:\n    meta: entity @root\n    fields:\n      brokenId: long @id -- a note: with a colon\n");

        String failed = generate(B, null);
        assertTrue(failed.startsWith("exit 1\n"), failed);
        assertTrue(read(shop).contains("com.acme.rt.sb3.validation"), "the failed run did not write: " + read(shop));

        assertTrue(broken.delete());
        String whole = generate(B, null);
        assertTrue(whole.startsWith("exit 0\n"), whole);
        assertRenamedOnce(shop);
    }

    /** The mapping of each text is recorded in the cache itself, and is not there without the setting. */
    @Test
    public void theMappingIsRecordedInTheCacheAndOnlyWithTheSetting() throws Exception {
        domain("shop", "Item");
        assertTrue(generate(null, null).startsWith("exit 0\n"));
        assertFalse(rawCache().containsKey(MAPPINGS), "a cache without the setting records a mapping");
        assertFalse(new File(project, ".elasticore/runtime-packages").exists());

        assertTrue(generate(A, null).startsWith("exit 0\n"));
        String said = rawCache().get(MAPPINGS);
        assertTrue(said != null && said.contains("ShopApiErrors.java\t" + A + "\n"), String.valueOf(said));
        assertTrue(new File(project, RuntimePackages.REPORT).isFile());

        // Removed: the texts name io.elasticore again, the record and the class list go.
        assertTrue(generate(null, null).startsWith("exit 0\n"));
        assertFalse(rawCache().containsKey(MAPPINGS), rawCache().get(MAPPINGS));
        assertFalse(
                new File(project, RuntimePackages.REPORT).exists(), "the class list of the removed setting is left");
    }

    /** A record that cannot be read is said; the files are renamed with this run's mapping only. */
    @Test
    public void anUnreadableRecordIsSaid() throws Exception {
        domain("shop", "Item");
        assertTrue(generate(A, null).startsWith("exit 0\n"));
        Map<String, String> cache = rawCache();
        cache.put(MAPPINGS, cache.get(MAPPINGS).replace(A, "not a mapping"));
        writeRawCache(cache);

        String said = generate(B, null);
        assertTrue(said.startsWith("exit 0\n"), said);
        assertTrue(said.contains("records a mapping that cannot be read ('not a mapping'"), said);
    }

    /**
     * A hand-edited record whose value holds the project's packages would
     * rename the project's own classes back to the runtime's: refused, said,
     * and the project's names are left as they are.
     */
    @Test
    public void aRecordThatWouldRenameTheProjectIsRefused() throws Exception {
        domain("shop", "Item");
        assertTrue(generate(A, null).startsWith("exit 0\n"));
        Map<String, String> cache = rawCache();
        cache.put(MAPPINGS, cache.get(MAPPINGS).replace(A, "io.elasticore.springboot3=com.acme"));
        writeRawCache(cache);

        String said = generate(B, null);
        assertTrue(said.contains("are not renamed back from it - the value 'com.acme'"), said);
        for (Map.Entry<String, String> en : snapshot(project).entrySet()) {
            if (en.getKey().startsWith("src/main/resources/blueprint/")) continue;
            assertFalse(en.getValue().contains("io.elasticore.springboot3.shop"), en.getKey() + " renamed the project");
        }
        assertTrue(read(find("ShopApiErrors.java")).contains("package com.acme.shop.adapter.in.web;"));
    }

    /** A previous mapping with two keys on one value cannot be undone by name: said, not guessed. */
    @Test
    public void anIrreversiblePreviousMappingIsSaid() throws Exception {
        domain("shop", "Item");
        String twoOnOne = "io.elasticore.springboot3=com.acme.rt,io.elasticore.runtime=com.acme.rt";
        assertTrue(generate(twoOnOne, null).startsWith("exit 0\n"));
        String said = generate(B, null);
        assertTrue(said.startsWith("exit 0\n"), said);
        assertTrue(said.contains("cannot be undone by name"), said);
        assertFalse(said.contains("changed since the last run"), said);
    }

    /** A value that is a namespace of the project, exactly: refused before anything is written. */
    @Test
    public void aValueEqualToANamespaceIsRefused() throws Exception {
        domain("shop", "Item");
        String said = generate("io.elasticore.springboot3=com.acme.shop.domain.model", null);
        assertTrue(said.startsWith("exit 1\n"), said);
        assertTrue(
                said.contains("the value 'com.acme.shop.domain.model' of 'io.elasticore.springboot3' is"
                        + " com.acme.shop.domain.model, the "),
                said);
        assertFalse(new File(project, "src/main/java").exists(), "sources were written");
    }
}
