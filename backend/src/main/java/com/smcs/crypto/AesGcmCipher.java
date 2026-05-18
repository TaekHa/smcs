package com.smcs.crypto;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM authenticated encryption for caller PII columns.
 * Output layout: [12-byte random IV][ciphertext+tag]. Null in → null out.
 */
@Component
public class AesGcmCipher {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_LENGTH = 12;
	private static final int TAG_LENGTH_BITS = 128;
	private static final int KEY_BYTES = 32;

	private final String base64Key;
	private final SecureRandom random = new SecureRandom();
	private SecretKeySpec key;

	public AesGcmCipher(@Value("${smcs.crypto.data-key}") String base64Key) {
		this.base64Key = base64Key;
	}

	@PostConstruct
	void init() {
		if (base64Key == null || base64Key.isBlank()) {
			throw new IllegalStateException("smcs.crypto.data-key must be set (Base64 of 32 bytes)");
		}
		byte[] keyBytes = Base64.getDecoder().decode(base64Key);
		if (keyBytes.length != KEY_BYTES) {
			throw new IllegalStateException(
					"smcs.crypto.data-key must decode to " + KEY_BYTES + " bytes (AES-256), got " + keyBytes.length);
		}
		this.key = new SecretKeySpec(keyBytes, "AES");
	}

	public byte[] encrypt(String plaintext) {
		if (plaintext == null) {
			return null;
		}
		try {
			byte[] iv = new byte[IV_LENGTH];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			byte[] out = new byte[IV_LENGTH + ciphertext.length];
			System.arraycopy(iv, 0, out, 0, IV_LENGTH);
			System.arraycopy(ciphertext, 0, out, IV_LENGTH, ciphertext.length);
			return out;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("AES-GCM encryption failed", e);
		}
	}

	public String decrypt(byte[] blob) {
		if (blob == null) {
			return null;
		}
		try {
			byte[] iv = Arrays.copyOfRange(blob, 0, IV_LENGTH);
			byte[] ciphertext = Arrays.copyOfRange(blob, IV_LENGTH, blob.length);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("AES-GCM decryption failed", e);
		}
	}
}
