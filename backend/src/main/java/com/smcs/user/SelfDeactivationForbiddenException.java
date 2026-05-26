package com.smcs.user;

/**
 * Story 4.4 AC5 — blocks the only-ADMIN-locks-themselves-out scenario by refusing
 * any request that would set {@code active=false} on the actor's own account.
 */
public class SelfDeactivationForbiddenException extends RuntimeException {

	public SelfDeactivationForbiddenException() {
		super("An admin cannot deactivate their own account.");
	}
}
