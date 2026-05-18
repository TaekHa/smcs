-- V3__seed_categories.sql: Production-shipping category seed (Story 1.2).
-- L1 3 + L2 4 + L3 3 = 10 rows. Applies to ALL profiles (production included).
-- parent_id is NULL by Story 1.2 decision (PRD 5.6: L1/L2/L3 are free-combination).

INSERT INTO categories (parent_id, level, name, keywords, sort_order, active) VALUES
    (NULL, 1, '아파트먼트v1', '[]'::jsonb, 1, TRUE),
    (NULL, 1, '아파트먼트v2', '[]'::jsonb, 2, TRUE),
    (NULL, 1, 'voip/pbx',     '[]'::jsonb, 3, TRUE);

INSERT INTO categories (parent_id, level, name, keywords, sort_order, active) VALUES
    (NULL, 2, '관리자웹', '[]'::jsonb, 1, TRUE),
    (NULL, 2, '입주민앱', '[]'::jsonb, 2, TRUE),
    (NULL, 2, '단말',     '[]'::jsonb, 3, TRUE),
    (NULL, 2, '서버',     '[]'::jsonb, 4, TRUE);

INSERT INTO categories (parent_id, level, name, keywords, sort_order, active) VALUES
    (NULL, 3, '기기미동작', '[]'::jsonb, 1, TRUE),
    (NULL, 3, '기기오동작', '[]'::jsonb, 2, TRUE),
    (NULL, 3, '로그인오류', '[]'::jsonb, 3, TRUE);
