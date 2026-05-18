-- V2__create_tables.sql: SMCS core schema (Story 1.2).
-- 7 tables in FK-dependency order: categories -> users -> issues -> comments -> attachments -> issue_events -> notifications.
-- Indexes follow architecture/5-data-architecture.md#5.2.
-- AC 6: sla_policies (or any equivalent) MUST NOT be created.

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT NULL REFERENCES categories(id),
    level       SMALLINT NOT NULL CHECK (level IN (1, 2, 3)),
    name        VARCHAR(100) NOT NULL,
    keywords    JSONB NOT NULL DEFAULT '[]'::jsonb,
    sort_order  INT NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_categories_parent_level_sort ON categories (parent_id, level, sort_order);

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(50) NOT NULL,
    role          VARCHAR(10) NOT NULL CHECK (role IN ('AGENT', 'FIELD', 'ADMIN')),
    phone         VARCHAR(100) NULL,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_role ON users (role);

CREATE TABLE issues (
    id                 BIGSERIAL PRIMARY KEY,
    title              VARCHAR(200) NOT NULL,
    description        TEXT NULL,
    caller_name_enc    BYTEA NULL,
    caller_phone_enc   BYTEA NULL,
    caller_phone_hash  VARCHAR(64) NULL,
    category_l1_id     BIGINT NOT NULL REFERENCES categories(id),
    category_l2_id     BIGINT NOT NULL REFERENCES categories(id),
    category_l3_id     BIGINT NOT NULL REFERENCES categories(id),
    priority           VARCHAR(10) NOT NULL CHECK (priority IN ('URGENT', 'HIGH', 'NORMAL', 'LOW')),
    status             VARCHAR(15) NOT NULL CHECK (status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'DONE', 'VERIFIED')),
    created_by         BIGINT NOT NULL REFERENCES users(id),
    assigned_to        BIGINT NULL REFERENCES users(id),
    resolved_at        TIMESTAMPTZ NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_issues_priority_created_at ON issues (priority, created_at);
CREATE INDEX idx_issues_status              ON issues (status);
CREATE INDEX idx_issues_assigned_status     ON issues (assigned_to, status);
CREATE INDEX idx_issues_category_l1         ON issues (category_l1_id);
CREATE INDEX idx_issues_category_l2         ON issues (category_l2_id);
CREATE INDEX idx_issues_category_l3         ON issues (category_l3_id);
CREATE INDEX idx_issues_caller_phone_hash   ON issues (caller_phone_hash);
CREATE INDEX idx_issues_created_at          ON issues (created_at);

CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    issue_id   BIGINT NOT NULL REFERENCES issues(id),
    author_id  BIGINT NOT NULL REFERENCES users(id),
    body       TEXT NOT NULL,
    kind       VARCHAR(15) NOT NULL CHECK (kind IN ('NOTE', 'FIELD_ACTION', 'SYSTEM')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_comments_issue_created ON comments (issue_id, created_at);

CREATE TABLE attachments (
    id             BIGSERIAL PRIMARY KEY,
    issue_id       BIGINT NOT NULL REFERENCES issues(id),
    uploader_id    BIGINT NOT NULL REFERENCES users(id),
    filename       VARCHAR(100) NOT NULL,
    original_name  VARCHAR(255) NOT NULL,
    mime_type      VARCHAR(50) NOT NULL,
    size_bytes     BIGINT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_attachments_issue ON attachments (issue_id);

CREATE TABLE issue_events (
    id         BIGSERIAL PRIMARY KEY,
    issue_id   BIGINT NOT NULL REFERENCES issues(id),
    actor_id   BIGINT NOT NULL REFERENCES users(id),
    event_type VARCHAR(30) NOT NULL CHECK (event_type IN ('CREATED', 'STATUS_CHANGED', 'ASSIGNED', 'COMMENTED', 'ATTACHMENT_ADDED', 'RESOLVED')),
    from_value VARCHAR(50) NULL,
    to_value   VARCHAR(50) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_issue_events_issue_created ON issue_events (issue_id, created_at);

CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL REFERENCES users(id),
    kind         VARCHAR(30) NOT NULL CHECK (kind IN ('ISSUE_ASSIGNED', 'ISSUE_COMMENTED', 'ISSUE_STATUS_CHANGED', 'ISSUE_REOPENED')),
    issue_id     BIGINT NOT NULL REFERENCES issues(id),
    actor_id     BIGINT NULL REFERENCES users(id),
    message      VARCHAR(255) NOT NULL,
    read_at      TIMESTAMPTZ NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_recipient_read    ON notifications (recipient_id, read_at);
CREATE INDEX idx_notifications_recipient_created ON notifications (recipient_id, created_at);
