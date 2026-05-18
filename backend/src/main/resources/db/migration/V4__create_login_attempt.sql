-- V4__create_login_attempt.sql: login attempts audit table (Story 1.3).
-- Used by LoginAttemptService for 5-failures-in-10-minutes lockout policy + audit trail (architecture §6.8).

CREATE TABLE login_attempt (
    id           BIGSERIAL PRIMARY KEY,
    username     VARCHAR(50) NOT NULL,
    ip_address   VARCHAR(45) NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    success      BOOLEAN NOT NULL
);
CREATE INDEX idx_login_attempt_username_time   ON login_attempt (username, attempted_at);
CREATE INDEX idx_login_attempt_ip_time         ON login_attempt (ip_address, attempted_at);
