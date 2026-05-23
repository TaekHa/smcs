-- V6__create_reports.sql: Story 3.4 — reports archive (auto-generated PDF metadata).
-- File bytes live under smcs.files.dir/reports/{kind}/{period_key}.pdf;
-- this table is the metadata index + idempotent key (UNIQUE(kind, period_key) — Story 3.4 AC4).

CREATE TABLE reports (
    id          BIGSERIAL PRIMARY KEY,
    kind        VARCHAR(10) NOT NULL CHECK (kind IN ('DAILY', 'WEEKLY')),
    period_key  VARCHAR(10) NOT NULL,
    file_path   VARCHAR(200) NOT NULL,
    size_bytes  BIGINT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (kind, period_key)
);

CREATE INDEX idx_reports_kind_created_at ON reports (kind, created_at DESC);
