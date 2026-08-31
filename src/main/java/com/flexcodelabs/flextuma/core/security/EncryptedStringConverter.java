package com.flexcodelabs.flextuma.core.security;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
@Converter public class EncryptedStringConverter implements AttributeConverter<String, String> {
    @Override public String convertToDatabaseColumn(String value) { return ConnectorSecretCrypto.encrypt(value); }
    @Override public String convertToEntityAttribute(String value) { return ConnectorSecretCrypto.decrypt(value); }
}
