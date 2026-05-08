CREATE TABLE IF NOT EXISTS users (
    id             UUID         NOT NULL,
    auth0_id       VARCHAR(255) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    display_name   VARCHAR(255),
    first_name     VARCHAR(255),
    last_name      VARCHAR(255),
    faculty        VARCHAR(255),
    study_level    VARCHAR(255),
    bio            TEXT,
    avatar_url     VARCHAR(255),
    banner_url     VARCHAR(255),
    profile_public BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMP    NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    calendar_token UUID,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_auth0_id UNIQUE (auth0_id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_calendar_token UNIQUE (calendar_token)
);

CREATE TABLE IF NOT EXISTS user_interests (
    user_id   UUID         NOT NULL,
    interests VARCHAR(255),
    CONSTRAINT fk_user_interests_user FOREIGN KEY (user_id) REFERENCES users(id)
);
