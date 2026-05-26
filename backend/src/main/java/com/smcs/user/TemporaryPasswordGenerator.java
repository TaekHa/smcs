package com.smcs.user;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Story 4.4 — one-time temporary password generator. 12 chars from the alphanumeric
 * alphabet (A-Z a-z 0-9 — Deviation #1: special chars excluded to keep the value safe
 * for hand-off through Slack/SMS where shell-style escaping is brittle). 62^12 ≈ 71 bits
 * of entropy is plenty for a value that is meant to be reset by the user on first login.
 */
@Component
public class TemporaryPasswordGenerator {

	private static final char[] ALPHABET =
			"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
	private static final int LENGTH = 12;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		char[] out = new char[LENGTH];
		for (int i = 0; i < LENGTH; i++) {
			out[i] = ALPHABET[random.nextInt(ALPHABET.length)];
		}
		return new String(out);
	}
}
