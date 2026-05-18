package com.smcs.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HmacHasherTest {

	private HmacHasher newHasher() {
		HmacHasher hasher = new HmacHasher("test-hmac-key");
		hasher.init();
		return hasher;
	}

	@Test
	void deterministicForSameNormalizedInput() {
		HmacHasher hasher = newHasher();
		assertThat(hasher.hashPhone("01012345678")).isEqualTo(hasher.hashPhone("01012345678"));
	}

	@Test
	void normalizesToDigitsOnly() {
		HmacHasher hasher = newHasher();
		assertThat(hasher.hashPhone("010-1234-5678")).isEqualTo(hasher.hashPhone("01012345678"));
		assertThat(hasher.hashPhone("+82 10 1234 5678")).isEqualTo(hasher.hashPhone("821012345678"));
	}

	@Test
	void outputIsLowercaseHex64Chars() {
		HmacHasher hasher = newHasher();
		String hash = hasher.hashPhone("010-1234-5678");
		assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
	}

	@Test
	void nullOrNoDigitsReturnsNull() {
		HmacHasher hasher = newHasher();
		assertThat(hasher.hashPhone(null)).isNull();
		assertThat(hasher.hashPhone("no-digits-here")).isNull();
	}
}
