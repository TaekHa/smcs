package com.smcs.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@SuppressWarnings("unused")
	private void sample(short level) {
	}

	@Test
	void typeMismatchMapsTo400ValidationFailed() throws Exception {
		Method m = GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", short.class);
		MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
				"abc", short.class, "level", new MethodParameter(m, 0), new NumberFormatException());

		ResponseEntity<ErrorResponse> resp = handler.handleTypeMismatch(ex);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resp.getBody().code()).isEqualTo("VALIDATION_FAILED");
		assertThat(resp.getBody().message()).contains("level");
	}

	@Test
	void missingParamMapsTo400ValidationFailed() {
		ResponseEntity<ErrorResponse> resp = handler.handleMissingParam(
				new MissingServletRequestParameterException("level", "short"));

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resp.getBody().code()).isEqualTo("VALIDATION_FAILED");
		assertThat(resp.getBody().message()).contains("level");
	}

	// SW-004 P2 regression — malformed JSON / bad UTF-8 must surface as 400, not 500,
	// so the client can tell its own encoding bug from a server fault.
	@Test
	void notReadableMapsTo400MalformedRequest() {
		HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
				"JSON parse error", (HttpInputMessage) null);

		ResponseEntity<ErrorResponse> resp = handler.handleNotReadable(ex);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resp.getBody().code()).isEqualTo("MALFORMED_REQUEST");
	}
}
