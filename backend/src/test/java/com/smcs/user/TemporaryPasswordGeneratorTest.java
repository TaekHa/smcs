package com.smcs.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TemporaryPasswordGeneratorTest {

	private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();
	private static final Pattern ALNUM = Pattern.compile("^[A-Za-z0-9]{12}$");

	@Test
	void generatedPasswordIs12CharactersFromAlphanumericAlphabet() {
		String value = generator.generate();
		assertThat(value).hasSize(12);
		assertThat(ALNUM.matcher(value).matches()).isTrue();
	}

	@Test
	void thousandConsecutiveGenerationsHaveNoCollisions() {
		// 62^12 ≈ 71 bits — 1000 samples should never collide (birthday paradox: collision
		// probability at 1000 samples is ~1e-15). Treat this as a SecureRandom smoke test.
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			String value = generator.generate();
			assertThat(seen.add(value)).as("unexpected collision at iteration %d", i).isTrue();
		}
	}

	@Test
	void noSpecialCharactersInGeneratedValue() {
		for (int i = 0; i < 200; i++) {
			String value = generator.generate();
			// Deviation #1 — Slack/SMS channels should treat the value as a single token; no
			// shell-meaningful chars allowed.
			assertThat(value).doesNotContainAnyWhitespaces();
			assertThat(value).doesNotContain("/", "\\", "\"", "'", "&", "|", ";", "<", ">", "?", "!", "@", "#", "$", "%", "^", "*", "(", ")", "[", "]", "{", "}");
		}
	}
}
