package io.elasticore.base.model.pub.jpa;

import io.elasticore.base.extract.FileBasedSourceFileAccessFactory;
import io.elasticore.base.extract.ModelExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 2.x generator, run in this JVM, on the model of issue 376 in both orders: the @service entity's search DTO
 * and its toSpec carry the @extend parent's fields whichever entity is written first (SearchDtoFieldOrderTest
 * runs each generator in a JVM of its own).
 */
class SearchDtoOrderInProcessTest {

    private static final String AUDIT = String.join(
            "\n",
            "  OrdAudit:",
            "    meta: entity @abstract",
            "    fields:",
            "      createdBy:  string(50) @updatable(false)",
            "      createDate: datetime @updatable(false)",
            "");

    private static final String NOTICE = String.join(
            "\n",
            "  OrdNotice:",
            "    meta: entity @service @extend(OrdAudit)",
            "    fields:",
            "      noticeId: long @id @sequence",
            "      title:    string(100) @search(like)",
            "      memo:     string(100)",
            "");

    private static final String POST = String.join(
            "\n",
            "  OrdPost:",
            "    meta: entity @expose @extend(OrdAudit)",
            "    fields:",
            "      postId: long @id @sequence",
            "      title:  string(100)",
            "");

    @Test
    void theServiceSearchDtoIsTheSameInBothOrders(@TempDir Path tmp) throws Exception {
        Path noticeFirst = generate(tmp.resolve("notice-first"), "entity:\n" + AUDIT + NOTICE + POST);
        Path postFirst = generate(tmp.resolve("post-first"), "entity:\n" + AUDIT + POST + NOTICE);

        String a = read(noticeFirst, "dto/OrdNoticeSrchDTO.java");
        assertEquals(withoutStamp(read(postFirst, "dto/OrdNoticeSrchDTO.java")), withoutStamp(a));
        for (String field : new String[] {"String title;", "String createdBy;", "createDateFrom;", "createDateTo;"}) {
            assertTrue(a.contains(field), field + "\n" + a);
        }
        assertFalse(a.contains(" memo;"), "its own field without @search is not searched:\n" + a);

        String spec = toSpec(read(noticeFirst, "dto/OrdMapper.java"), "OrdNotice");
        assertEquals(toSpec(read(postFirst, "dto/OrdMapper.java"), "OrdNotice"), spec);
        assertTrue(spec.contains("sp=setSpec(sp, \"createdBy\", searchDTO.getCreatedBy());"), spec);
        assertTrue(spec.contains("c.between(r.get(\"createDate\"),createDateFrom,createDateTo)"), spec);
        assertFalse(spec.contains("getMemo()"), spec);

        // the @expose entity's search DTO searches every field
        String post = read(noticeFirst, "dto/OrdPostSrchDTO.java");
        assertTrue(post.contains("String title;") && post.contains("String createdBy;"), post);
    }

    /** The blueprint written, then the 2.x generator in this JVM; the root of the domain's sources. */
    private static Path generate(Path project, String entityYml) throws Exception {
        Path dir = project.resolve("src/main/resources/blueprint/ord");
        Files.createDirectories(dir.resolve("model"));
        Files.write(
                dir.resolve("env.yml"),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: ord",
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "  namespace:",
                                "    entity: com.acme.ord.entity",
                                "    enumeration: com.acme.ord.enums",
                                "    dto: com.acme.ord.dto",
                                "    repository: com.acme.ord.repository",
                                "    service: com.acme.ord.service",
                                "    control: com.acme.ord.controller",
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("model/entity.yml"), entityYml.getBytes(StandardCharsets.UTF_8));
        Path src = project.resolve("src/main/java");
        Files.createDirectories(src);

        // The generator writes under the working directory unless told the project: told both ways here.
        String basePath = System.getProperty("elasticore.base.path");
        System.setProperty("elasticore.base.path", project.toAbsolutePath().toString());
        Object[] context = AutoSearchTest.ModelContexts.save();
        try {
            new ModelExtractor(project.resolve("src/main/resources/blueprint")
                            .toAbsolutePath()
                            .toString())
                    .extract(new FileBasedSourceFileAccessFactory(
                            src.toAbsolutePath().toString()));
        } finally {
            AutoSearchTest.ModelContexts.restore(context);
            if (basePath == null) System.clearProperty("elasticore.base.path");
            else System.setProperty("elasticore.base.path", basePath);
        }
        return src.resolve("com/acme/ord");
    }

    private static String read(Path dir, String file) throws Exception {
        return new String(Files.readAllBytes(dir.resolve(file)), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    // the body of toSpec(<entity>SrchDTO searchDTO, Specification sp)
    private static String toSpec(String mapper, String entity) {
        int start = mapper.indexOf("toSpec(" + entity + "SrchDTO searchDTO, ");
        assertTrue(start >= 0, "no toSpec for " + entity + "\n" + mapper);
        return mapper.substring(start, mapper.indexOf("return sp;", start));
    }

    private static String withoutStamp(String s) {
        return s.startsWith("//ecd:") ? s.substring(s.indexOf('\n') + 1) : s;
    }
}
