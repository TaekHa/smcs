-- V5__add_issue_resolved_at_index.sql: Story 3.1 (stats aggregation).
-- First new migration after the Epic 2 "schema-frozen (ddl-auto=validate)" period.
-- Adds the only aggregation index missing from V2: resolved_at (powers resolvedCount /
-- avgResolveMinutes / byAssignee). created_at / status / (assigned_to,status) /
-- category_l1_id / (priority,created_at) already exist (V2). No entity change → validate holds.
CREATE INDEX idx_issues_resolved_at ON issues (resolved_at);
