CREATE SEQUENCE IF NOT EXISTS attendances_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS attendances (
    id         BIGINT       NOT NULL DEFAULT nextval('attendances_seq'),
    user_id    UUID         NOT NULL,
    event_id   BIGINT       NOT NULL,
    status     VARCHAR(255) NOT NULL,
    created_at TIMESTAMP,
    CONSTRAINT pk_attendances PRIMARY KEY (id),
    CONSTRAINT uq_attendance_user_event UNIQUE (user_id, event_id),
    CONSTRAINT attendances_status_check CHECK (status IN ('ATTENDING', 'WAITLISTED'))
);
