package com.smcs.crypto;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 searchable hash for caller phone (exact-match only).
 * Input is normalized to digits only before hashing. Null/blank → null.
 * Key is intentionally separate from the AES data key.
 */
@Component
public class HmacHasher {

	private static final String ALGORITHM = "HmacSHA256";

	private final String hmacKey;
	private byte[] keyBytes;

	public HmacHasher(@Value("${smcs.crypto.hmac-key}") String hmacKey) {
		this.hmacKey = hmacKey;
	}

	@PostConstruct
	void init() {
		if (hmacKey == null || hmacKey.isBlank()) {
			throw new IllegalStateException("smcs.crypto.hmac-key must be set");
		}
		this.keyBytes = hmacKey.getBytes(StandardCharsets.UTF_8);
	}

	/** Normalize to digits only, then HMAC-SHA256 → lowercase hex (64 chars). */
	public String hashPhone(String raw) {
		if (raw == null) {
			return null;
		}
		String normalized = raw.replaceAll("\\D", "");
		if (normalized.isEmpty()) {
			return null;
		}
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(keyBytes, ALGORITHM));
			byte[] digest = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				hex.append(Character.forDigit((b >> 4) & 0xF, 16));
				hex.append(Character.forDigit(b & 0xF, 16));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("HMAC-SHA256 hashing failed", e);
		}
	}
}
