package com.smcs.issue.dto;

/** Minimal category reference (id + resolved name) for the issue detail. */
public record CategoryRef(Long id, String name) {
}
