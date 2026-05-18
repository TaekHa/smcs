package com.smcs.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmCipherTest {

	private static final String KEY =
			Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

	private AesGcmCipher newCipher() {
		AesGcmCipher cipher = new AesGcmCipher(KEY);
		cipher.init();
		return cipher;
	}

	@Test
	void encryptThenDecryptRoundTrip() {
		AesGcmCipher cipher = newCipher();
		String plaintext = "홍길동 010-1234-5678";

		String decrypted = cipher.decrypt(cipher.encrypt(plaintext));

		assertThat(decrypted).isEqualTo(plaintext);
	}

	@Test
	void sameInputProducesDifferentCiphertextButSamePlaintext() {
		AesGcmCipher cipher = newCipher();
		String plaintext = "발신자";

		byte[] a = cipher.encrypt(plaintext);
		byte[] b = cipher.encrypt(plaintext);

		assertThat(a).isNotEqualTo(b); // random IV per encryption
		assertThat(cipher.decrypt(a)).isEqualTo(plaintext);
		assertThat(cipher.decrypt(b)).isEqualTo(plaintext);
	}

	@Test
	void nullInputReturnsNull() {
		AesGcmCipher cipher = newCipher();
		assertThat(cipher.encrypt(null)).isNull();
		assertThat(cipher.decrypt(null)).isNull();
	}

	@Test
	void wrongKeyLengthFailsInit() {
		AesGcmCipher cipher = new AesGcmCipher(
				Base64.getEncoder().encodeToString("short-key".getBytes(StandardCharsets.UTF_8)));
		assertThatThrownBy(cipher::init)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32 bytes");
	}
}
