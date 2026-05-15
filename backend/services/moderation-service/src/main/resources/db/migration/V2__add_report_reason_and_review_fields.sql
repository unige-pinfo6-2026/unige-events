ALTER TABLE reports RENAME COLUMN reason TO description;

ALTER TABLE reports ADD COLUMN reason VARCHAR(32);

UPDATE reports SET reason = 'OTHER' WHERE reason IS NULL;

ALTER TABLE reports ALTER COLUMN reason SET NOT NULL;

ALTER TABLE reports ADD CONSTRAINT reports_reason_check
    CHECK (reason IN ('SPAM', 'INAPPROPRIATE', 'FAKE', 'OTHER'));

ALTER TABLE reports ADD COLUMN moderation_note TEXT NULL;
ALTER TABLE reports ADD COLUMN reviewed_at     TIMESTAMP NULL;
ALTER TABLE reports ADD COLUMN reviewed_by     UUID      NULL;
