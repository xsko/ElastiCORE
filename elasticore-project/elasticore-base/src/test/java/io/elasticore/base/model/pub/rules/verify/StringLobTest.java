package io.elasticore.base.model.pub.rules.verify;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A String field with @Lob - one added by hand to a 2.x file - is said; other fields are not. */
public class StringLobTest {

    private static List<String> lobs(String path, String text) {
        Map<String, String> sources = new TreeMap<String, String>();
        sources.put(path, text);
        return RulesGenRunner.stringLobs(sources);
    }

    @Test
    public void aStringWithLobIsSaid() {
        String java = "public class Workspace {\n"
                + "    @Lob\n"
                + "    @Column(name = \"doc\", columnDefinition = \"TEXT\")\n"
                + "    private String document;\n"
                + "    @jakarta.persistence.Lob private String note;\n"
                + "}\n";
        List<String> out = lobs("src/main/java/a/Workspace.java", java);
        assertEquals(1, out.size(), out.toString());
        assertTrue(out.get(0).startsWith("[elcore3] src/main/java/a/Workspace.java: document, note - a String with @Lob"),
                out.get(0));

        String kotlin = "class Workspace {\n    @Lob\n    @Column(name = \"doc\")\n    var document: String? = null\n}\n";
        assertEquals(1, lobs("src/main/kotlin/a/Workspace.kt", kotlin).size());
    }

    @Test
    public void otherFieldsAreNot() {
        // A byte[] with @Lob, a String without it, a comment, and the 3.0 mapping.
        String java = "public class Workspace {\n"
                + "    @Lob private byte[] image;\n"
                + "    @Column(columnDefinition = \"TEXT\") private String document;\n"
                + "    // @Lob private String old;\n"
                + "    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) private String story;\n"
                + "}\n";
        assertTrue(lobs("src/main/java/a/Workspace.java", java).isEmpty());
    }
}
