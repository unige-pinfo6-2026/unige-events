CREATE SEQUENCE IF NOT EXISTS events_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS events (
    id                    BIGINT       NOT NULL DEFAULT nextval('events_seq'),
    title                 VARCHAR(255),
    description           TEXT,
    location              VARCHAR(255),
    start_date            TIMESTAMP,
    end_date              TIMESTAMP,
    category              VARCHAR(255),
    faculty               VARCHAR(255),
    banner_url            VARCHAR(255),
    creator_id            UUID,
    status                VARCHAR(255) DEFAULT 'DRAFT',
    capacity              INTEGER,
    all_day               BOOLEAN      NOT NULL DEFAULT false,
    website_url           VARCHAR(500),
    contact_email         VARCHAR(255),
    registration_deadline TIMESTAMP,
    share_code            VARCHAR(255),
    created_at            TIMESTAMP,
    updated_at            TIMESTAMP,
    CONSTRAINT pk_events PRIMARY KEY (id),
    CONSTRAINT uq_events_share_code UNIQUE (share_code)
);

CREATE INDEX IF NOT EXISTS idx_event_creator    ON events(creator_id);
CREATE INDEX IF NOT EXISTS idx_event_start_date ON events(start_date);
CREATE INDEX IF NOT EXISTS idx_event_faculty    ON events(faculty);

CREATE TABLE IF NOT EXISTS event_tags (
    event_id BIGINT      NOT NULL,
    tag      VARCHAR(64) NOT NULL,
    CONSTRAINT fk_event_tags_event FOREIGN KEY (event_id) REFERENCES events(id)
);
