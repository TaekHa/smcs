package com.smcs.security;

import org.springframework.security.core.AuthenticationException;

public class InvalidJwtException extends AuthenticationException {

	public InvalidJwtException(String message, Throwable cause) {
		super(message, cause);
	}
}
