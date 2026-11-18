package io.elasticore.springboot3.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.elasticore.springboot.autoconfigure.ElastiCoreCryptoAutoConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The JPA converter and the MyBatis type handler over the installed keys, and the auto-configuration that installs
 * them from the Spring properties - which stops the start without a key only where an encrypted field is mapped.
 */
public class EncryptConverterTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ElastiCoreCryptoAutoConfiguration.class));

    @AfterEach
    void forget() {
        FieldCrypto.reset();
    }

    @Test
    public void theConverterWritesCiphertextAndReadsTheValue() {
        FieldCrypto.install(FieldCrypto.of(FieldCryptoTest.keys()::get));
        EncryptConverter converter = new EncryptConverter();
        assertTrue(FieldCrypto.used());
        String column = converter.convertToDatabaseColumn("010-1234-5678");
        assertTrue(column.startsWith("v1:"), column);
        assertEquals("010-1234-5678", converter.convertToEntityAttribute(column));
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    public void theTypeHandlerDoesTheSameOverJdbc() throws Exception {
        FieldCrypto.install(FieldCrypto.of(FieldCryptoTest.keys()::get));
        EncryptTypeHandler handler = new EncryptTypeHandler();
        try (Connection db = DriverManager.getConnection("jdbc:h2:mem:encrypt;DB_CLOSE_DELAY=-1")) {
            try (Statement s = db.createStatement()) {
                s.execute("create table person (id int primary key, phone varchar(147))");
            }
            try (PreparedStatement ps = db.prepareStatement("insert into person values (?, ?)")) {
                ps.setInt(1, 1);
                handler.setParameter(ps, 2, "010-1234-5678", JdbcType.VARCHAR);
                ps.executeUpdate();
                ps.setInt(1, 2);
                handler.setParameter(ps, 2, null, JdbcType.VARCHAR);
                ps.executeUpdate();
            }
            try (Statement s = db.createStatement();
                    ResultSet rs = s.executeQuery("select phone from person order by id")) {
                rs.next();
                assertTrue(rs.getString(1).startsWith("v1:"), rs.getString(1));
                assertEquals("010-1234-5678", handler.getResult(rs, 1));
                assertEquals("010-1234-5678", handler.getResult(rs, "phone"));
                rs.next();
                assertNull(handler.getResult(rs, 1));
            }
        }
    }

    @Test
    public void theAutoConfigurationInstallsTheKeysOfTheProperties() {
        Map<String, String> keys = FieldCryptoTest.keys();
        runner.withPropertyValues(
                        FieldCrypto.KEY + "=" + keys.get(FieldCrypto.KEY),
                        FieldCrypto.INDEX_KEY + "=" + keys.get(FieldCrypto.INDEX_KEY))
                .run(context -> {
                    assertFalse(context.getStartupFailure() != null, String.valueOf(context.getStartupFailure()));
                    String stored = FieldCrypto.current().encrypt("x");
                    assertEquals("x", FieldCrypto.of(keys::get).decrypt(stored));
                    assertEquals(FieldCrypto.of(keys::get).index("1"), Bidx.of("1"));
                });
    }

    @Test
    public void withoutAKeyAnApplicationWithAnEncryptedFieldDoesNotStart() {
        FieldCrypto.markUsed();
        runner.run(context -> {
            Throwable failure = context.getStartupFailure();
            assertTrue(failure != null, "started without a key");
            assertTrue(messages(failure).contains("elasticore.crypto.key is not set"), messages(failure));
        });
    }

    @Test
    public void withoutAKeyAnApplicationWithoutOneStartsAsBefore() {
        runner.run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    public void aMalformedKeyStopsTheStartWhateverIsMapped() {
        runner.withPropertyValues(FieldCrypto.INDEX_KEY + "=abc").run(context -> {
            Throwable failure = context.getStartupFailure();
            assertTrue(failure != null, "started with a malformed key");
            assertTrue(messages(failure).contains("elasticore.crypto.index-key holds 2 bytes"), messages(failure));
        });
    }

    @Test
    public void aConverterWithoutKeysSaysWhatToSet() {
        EncryptConverter converter = new EncryptConverter();
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> converter.convertToDatabaseColumn("x"));
        assertTrue(ex.getMessage().contains("elasticore.crypto.key"), ex.getMessage());
    }

    private static String messages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable x = t; x != null; x = x.getCause())
            sb.append(x.getMessage()).append('\n');
        return sb.toString();
    }
}
