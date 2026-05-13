ALTER TABLE events
    ADD COLUMN IF NOT EXISTS featured    BOOLEAN   NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS featured_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_event_featured_status_end ON events(featured, status, end_date);
