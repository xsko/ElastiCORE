package io.elasticore.springboot3.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * A JPA column kept encrypted ({@link FieldCrypto}): the entity holds the value, the database {@code v1:...}.
 *
 * <pre>
 * &#64;Convert(converter = EncryptConverter.class)
 * private String phone;
 * </pre>
 *
 * The 3.0 code writes it on an {@code @encrypt} field; without the generator name it in env.yml:
 * {@code encrypt: "@entity:jakarta.persistence.Convert(converter=io.elasticore.springboot3.crypto.EncryptConverter.class)"}.
 * The column must be long enough for the ciphertext - see the annotation guide's {@code @encrypt}.
 */
@Converter
public class EncryptConverter implements AttributeConverter<String, String> {

    public EncryptConverter() {
        FieldCrypto.markUsed();
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : FieldCrypto.current().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FieldCrypto.current().decrypt(dbData);
    }
}
