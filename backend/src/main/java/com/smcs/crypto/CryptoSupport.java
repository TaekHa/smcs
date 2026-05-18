package com.smcs.crypto;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Bridges the Spring-managed {@link AesGcmCipher} into {@link EncryptedStringConverter},
 * which JPA instantiates outside the Spring container (so it cannot be injected directly).
 */
@Component
public class CryptoSupport {

	private static AesGcmCipher staticCipher;

	private final AesGcmCipher cipher;

	public CryptoSupport(AesGcmCipher cipher) {
		this.cipher = cipher;
	}

	@PostConstruct
	void register() {
		CryptoSupport.staticCipher = cipher;
	}

	static AesGcmCipher cipher() {
		if (staticCipher == null) {
			throw new IllegalStateException("CryptoSupport not initialized");
		}
		return staticCipher;
	}
}
