package io.elasticore.base.model.pub.jpa;

import io.elasticore.base.extract.ModelExtractor;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 2.x generator's bounds on a DTO field. @max on a bigdecimal was written
 * with the @min value - DecimalMax("0.01") for @min(0.01) @max(999.99) - so
 * every request above the minimum was refused.
 */
public class TwoXBoundTest {

    @Test
    public void aDecimalMaxIsTheMax() throws Exception {
        File project = Files.createTempDirectory("elcore-2x-bound").toFile();
        try {
            File dir = new File(project, "src/main/resources/blueprint/probe");
            assertTrue(new File(dir, "model").mkdirs());
            Files.write(
                    new File(dir, "env.yml").toPath(),
                    String.join(
                                    "\n",
                                    "elasticore:",
                                    "  enable: true",
                                    "  config:",
                                    "    domainName: probe",
                                    "    framework: springboot",
                                    "    mode: jpa",
                                    "    j2ee: jakarta",
                                    "  namespace:",
                                    "    entity: com.acme.probe.entity",
                                    "    enumeration: com.acme.probe.entity",
                                    "    dto: com.acme.probe.dto",
                                    "    repository: com.acme.probe.repository",
                                    "    service: com.acme.probe.service",
                                    "    control: com.acme.probe.controller",
                                    "")
                            .getBytes(StandardCharsets.UTF_8));
            Files.write(
                    new File(dir, "model/model.yml").toPath(),
                    String.join(
                                    "\n",
                                    "entity:",
                                    "  Gauge:",
                                    "    meta: entity",
                                    "    fields:",
                                    "      gaugeId: long @id",
                                    "dto:",
                                    "  PriceDTO:",
                                    "    meta: dto",
                                    "    fields:",
                                    "      price: bigdecimal(10.2) @min(0.01) @max(999.99)",
                                    "      count: int @min(1) @max(9)",
                                    "")
                            .getBytes(StandardCharsets.UTF_8));
            // The generator writes under the working directory unless told the project: told both ways here.
            String basePath = System.getProperty("elasticore.base.path");
            System.setProperty("elasticore.base.path", project.getAbsolutePath());
            Object[] context = saveContext();
            try {
                new ModelExtractor(new File(project, "src/main/resources/blueprint").getAbsolutePath())
                        .extract(new io.elasticore.base.extract.FileBasedSourceFileAccessFactory(
                                new File(project, "src/main/java").getAbsolutePath()));
            } finally {
                restoreContext(context);
                if (basePath == null) System.clearProperty("elasticore.base.path");
                else System.setProperty("elasticore.base.path", basePath);
            }

            String dto = new String(
                    Files.readAllBytes(new File(project, "src/main/java/com/acme/probe/dto/PriceDTO.java").toPath()),
                    StandardCharsets.UTF_8);
            assertTrue(dto.contains("@jakarta.validation.constraints.DecimalMin(\"0.01\")"), dto);
            assertTrue(dto.contains("@jakarta.validation.constraints.DecimalMax(\"999.99\")"), dto);
            assertFalse(dto.contains("DecimalMax(\"0.01\")"), dto);
            assertTrue(dto.contains("@jakarta.validation.constraints.Min(1)"), dto);
            assertTrue(dto.contains("@jakarta.validation.constraints.Max(9)"), dto);
        } finally {
            delete(project);
        }
    }

    private static final String[] CONTEXT = {"STATIC_DOMAIN_MAP", "context", "tmpCtx"};

    /** The generator's model context is static; a run leaves its domains there for the tests after it. */
    private static Object[] saveContext() throws Exception {
        Object[] saved = new Object[CONTEXT.length];
        for (int i = 0; i < CONTEXT.length; i++) {
            java.lang.reflect.Field f =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField(CONTEXT[i]);
            f.setAccessible(true);
            Object value = f.get(null);
            saved[i] = value instanceof java.util.Map
                    ? new java.util.HashMap<Object, Object>((java.util.Map<?, ?>) value)
                    : value;
        }
        return saved;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void restoreContext(Object[] saved) throws Exception {
        for (int i = 0; i < CONTEXT.length; i++) {
            java.lang.reflect.Field f =
                    io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField(CONTEXT[i]);
            f.setAccessible(true);
            if (saved[i] instanceof java.util.Map) {
                java.util.Map map = (java.util.Map) f.get(null);
                map.clear();
                map.putAll((java.util.Map) saved[i]);
            } else {
                f.set(null, saved[i]);
            }
        }
    }

    private static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) delete(c);
        assertTrue(f.delete() || !f.exists());
    }
}
