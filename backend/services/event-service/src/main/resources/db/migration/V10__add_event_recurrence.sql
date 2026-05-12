ALTER TABLE events
    ADD COLUMN IF NOT EXISTS parent_event_id BIGINT,
    ADD COLUMN IF NOT EXISTS recurrence_rule VARCHAR(500);

ALTER TABLE events
    ADD CONSTRAINT fk_events_parent
        FOREIGN KEY (parent_event_id) REFERENCES events(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_event_parent ON events(parent_event_id);
