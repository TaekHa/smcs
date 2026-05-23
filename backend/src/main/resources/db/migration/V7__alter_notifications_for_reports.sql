-- V7__alter_notifications_for_reports.sql: Story 3.4 — extend notifications for report archive alerts.
-- (a) Allow issue_id to be NULL for report-scoped notifications (no owning issue).
-- (b) Extend the kind CHECK to permit REPORT_READY / REPORT_FAILED.
--
-- V2 created the kind CHECK as an anonymous constraint; Postgres auto-names it
-- "{table}_{column}_check" → "notifications_kind_check". DROP IF EXISTS is safe: if the
-- anonymous name differs in any environment, the migration fails fast in CI rather than
-- silently leaving stale constraints behind.
--
-- Rollback (manual V8 — only if no REPORT_* rows exist):
--   1) DELETE FROM notifications WHERE kind IN ('REPORT_READY', 'REPORT_FAILED');
--   2) ALTER TABLE notifications ALTER COLUMN issue_id SET NOT NULL;
--   3) ALTER TABLE notifications DROP CONSTRAINT notifications_kind_check;
--      ALTER TABLE notifications ADD CONSTRAINT notifications_kind_check
--          CHECK (kind IN ('ISSUE_ASSIGNED', 'ISSUE_COMMENTED', 'ISSUE_STATUS_CHANGED', 'ISSUE_REOPENED'));
--   Step 1 is mandatory — SET NOT NULL fails if any row has issue_id IS NULL.

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_kind_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_kind_check
    CHECK (kind IN ('ISSUE_ASSIGNED', 'ISSUE_COMMENTED', 'ISSUE_STATUS_CHANGED', 'ISSUE_REOPENED',
                    'REPORT_READY', 'REPORT_FAILED'));

ALTER TABLE notifications ALTER COLUMN issue_id DROP NOT NULL;
