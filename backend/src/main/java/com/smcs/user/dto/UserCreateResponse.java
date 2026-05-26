package com.smcs.user.dto;

/**
 * Admin-create response — wraps the new user record alongside the one-time temporary
 * password (AC2). The temporary password is the ONLY response that ever carries the
 * plaintext value; after this it lives in the {@code users.password_hash} column as a
 * BCrypt hash, and there is no API path to recover it (Story 4.4 #7/#8).
 */
public record UserCreateResponse(UserAdminResponse user, String temporaryPassword) {
}
