package com.smcs.common;

public record ErrorResponse(String code, String message, String traceId) {

	public static ErrorResponse of(String code, String message) {
		return new ErrorResponse(code, message, TraceIdFilter.currentTraceId());
	}
}
