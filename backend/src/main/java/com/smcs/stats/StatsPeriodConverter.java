package com.smcs.stats;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code period} query value (lowercase {@code today|week|month}) to {@link StatsPeriod}.
 * Invalid values throw {@code IllegalArgumentException}, which Spring wraps as
 * {@code MethodArgumentTypeMismatchException} → 400 VALIDATION_FAILED (GlobalExceptionHandler).
 */
@Component
public class StatsPeriodConverter implements Converter<String, StatsPeriod> {

	@Override
	public StatsPeriod convert(String source) {
		return StatsPeriod.from(source);
	}
}
