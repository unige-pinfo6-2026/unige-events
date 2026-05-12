CREATE SEQUENCE IF NOT EXISTS reports_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS reports (
    id          BIGINT       NOT NULL DEFAULT nextval('reports_seq'),
    event_id    BIGINT       NOT NULL,
    reporter_id UUID,
    status      VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    reason      TEXT,
    created_at  TIMESTAMP,
    CONSTRAINT pk_reports PRIMARY KEY (id),
    CONSTRAINT uk_report_reporter_event UNIQUE (reporter_id, event_id),
    CONSTRAINT reports_status_check CHECK (status IN ('PENDING', 'REVIEWED', 'DISMISSED'))
);

CREATE INDEX IF NOT EXISTS idx_report_event  ON reports(event_id);
CREATE INDEX IF NOT EXISTS idx_report_status ON reports(status);
