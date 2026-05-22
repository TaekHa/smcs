package com.smcs.comment;

/**
 * Comment classification (matches the {@code comments.kind} CHECK constraint, V2).
 * Desktop detail comments are {@link #NOTE}; {@link #FIELD_ACTION} is the mobile
 * field-action record (Story 2.6) and {@link #SYSTEM} is auto-generated.
 */
public enum CommentKind {
	NOTE, FIELD_ACTION, SYSTEM
}
