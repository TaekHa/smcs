package com.smcs.stats;

import java.time.Instant;

/** Half-open instant range {@code [from, to)} used for period-scoped aggregation. */
public record StatsRange(Instant from, Instant to) {}
