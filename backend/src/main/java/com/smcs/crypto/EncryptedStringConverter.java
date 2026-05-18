package com.smcs.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Transparently AES-GCM encrypts a String entity field into a {@code bytea} column.
 * Applied explicitly per field via {@code @Convert} (not auto-applied).
 */
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, byte[]> {

	@Override
	public byte[] convertToDatabaseColumn(String attribute) {
		return CryptoSupport.cipher().encrypt(attribute);
	}

	@Override
	public String convertToEntityAttribute(byte[] dbData) {
		return CryptoSupport.cipher().decrypt(dbData);
	}
}
