package com.smcs.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps the {@code categories} table (V2). {@code keywords} (jsonb) is intentionally
 * unmapped — unused by Story 2.1 and ddl-auto=validate only checks mapped attributes.
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

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@Column(nullable = false)
	private boolean active;

	protected Category() {
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

	public Integer getSortOrder() {
		return sortOrder;
	}

	public boolean isActive() {
		return active;
	}
}
