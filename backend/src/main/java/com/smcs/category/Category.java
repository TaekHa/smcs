package com.smcs.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps the {@code categories} table (V2). {@code keywords} (jsonb) feeds Story 4.2 auto
 * category suggestion; Story 4.5 surfaces it through the admin upsert endpoint.
 */
@Entity
@Table(name = "categories")
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "parent_id")
	private Long parentId;

	@Column(nullable = false)
	private Short level;

	@Column(nullable = false, length = 100)
	private String name;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "keywords", nullable = false, columnDefinition = "jsonb")
	private List<String> keywords;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@Column(nullable = false)
	private boolean active;

	protected Category() {
	}

	/** Creates a new category. {@code active} defaults to true; {@code keywords} null → empty list. */
	public Category(Long parentId, short level, String name, List<String> keywords, int sortOrder) {
		this.parentId = parentId;
		this.level = level;
		this.name = name;
		this.keywords = keywords == null ? new ArrayList<>() : new ArrayList<>(keywords);
		this.sortOrder = sortOrder;
		this.active = true;
	}

	/** Admin upsert mutator (Story 4.5) — single entry point for all editable fields. */
	public void update(String name, List<String> keywords, int sortOrder, boolean active) {
		this.name = name;
		this.keywords = keywords == null ? new ArrayList<>() : new ArrayList<>(keywords);
		this.sortOrder = sortOrder;
		this.active = active;
	}

	public Long getId() {
		return id;
	}

	public Long getParentId() {
		return parentId;
	}

	public Short getLevel() {
		return level;
	}

	public String getName() {
		return name;
	}

	/** Defensive copy — callers must not mutate the returned list. */
	public List<String> getKeywords() {
		return keywords == null ? List.of() : Collections.unmodifiableList(keywords);
	}

	public Integer getSortOrder() {
		return sortOrder;
	}

	public boolean isActive() {
		return active;
	}
}
