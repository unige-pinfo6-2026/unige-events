-- V18: outbox table for events.banned to guarantee at-least-once delivery.
-- Acted in PR #158 Etape 24.4.1 (Decision K -- ADR-003).
CREATE TABLE event_banned_outbox (
    id            BIGSERIAL PRIMARY KEY,
    event_id      BIGINT NOT NULL,
    banned_by     UUID,
    occurred_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    payload_json  JSONB NOT NULL,
    published_at  TIMESTAMP WITH TIME ZONE,
    attempts      INT NOT NULL DEFAULT 0,
    last_error    TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_event_banned_outbox_unpublished
    ON event_banned_outbox (created_at) WHERE published_at IS NULL;
