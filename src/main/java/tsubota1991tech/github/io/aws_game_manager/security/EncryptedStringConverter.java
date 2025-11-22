package tsubota1991tech.github.io.aws_game_manager.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        EncryptionService encryptionService = EncryptionService.getInstance();
        if (encryptionService == null) {
            return attribute;
        }
        return encryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        EncryptionService encryptionService = EncryptionService.getInstance();
        if (encryptionService == null) {
            return dbData;
        }
        return encryptionService.decrypt(dbData);
    }
}
