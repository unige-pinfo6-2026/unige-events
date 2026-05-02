-- Sprint 7 — public username identifier for /profile/<username> URLs.
-- 1) Add column nullable.
-- 2) Back-fill via PL/pgSQL: slug(displayName) | firstName.lastName | "user", anti-collision suffix.
-- 3) Switch to NOT NULL UNIQUE.

ALTER TABLE users ADD COLUMN username VARCHAR(30);

-- Slug helper: ASCII-fold via translate map, lowercase, spaces -> '.', strip out-of-charset.
CREATE OR REPLACE FUNCTION pg_temp.username_slug(input TEXT) RETURNS TEXT AS $$
DECLARE
    slug TEXT;
BEGIN
    IF input IS NULL OR length(trim(input)) = 0 THEN
        RETURN NULL;
    END IF;
    slug := lower(input);
    slug := translate(
        slug,
        'àáâãäåāăąèéêëēĕėęěìíîïĩīĭįòóôõöøōŏőùúûüũūŭůűųýÿñçčćďđģğħĥĵķĺľłŕřśşšţťžźżƒœæß',
        'aaaaaaaaaeeeeeeeeeiiiiiiiioooooooooouuuuuuuuuuyynccccddggghhjkllllrrsssttzzzfoeaess'
    );
    slug := regexp_replace(slug, '\s+', '.', 'g');
    slug := regexp_replace(slug, '[^a-z0-9._-]', '', 'g');
    slug := regexp_replace(slug, '\.{2,}', '.', 'g');
    slug := regexp_replace(slug, '^[._-]+|[._-]+$', '', 'g');
    IF length(slug) < 3 THEN
        RETURN NULL;
    END IF;
    IF length(slug) > 30 THEN
        slug := substring(slug FROM 1 FOR 30);
        slug := regexp_replace(slug, '[._-]+$', '', 'g');
    END IF;
    RETURN slug;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Back-fill loop: pick a base slug from displayName or firstName.lastName, fall back to "user",
-- then append an incremental numeric suffix until a free slot is found.
DO $$
DECLARE
    rec RECORD;
    base_slug TEXT;
    candidate TEXT;
    suffix INT;
    blocklist TEXT[] := ARRAY['me','admin','api','login','logout','signup','register','settings'];
BEGIN
    FOR rec IN SELECT id, display_name, first_name, last_name FROM users WHERE username IS NULL LOOP
        base_slug := pg_temp.username_slug(rec.display_name);
        IF base_slug IS NULL THEN
            base_slug := pg_temp.username_slug(
                CONCAT_WS('.', NULLIF(trim(rec.first_name), ''), NULLIF(trim(rec.last_name), ''))
            );
        END IF;
        IF base_slug IS NULL THEN
            base_slug := 'user';
        END IF;

        candidate := base_slug;
        suffix := 1;
        WHILE candidate = ANY(blocklist)
              OR EXISTS (SELECT 1 FROM users WHERE LOWER(username) = candidate) LOOP
            suffix := suffix + 1;
            candidate := base_slug || suffix::TEXT;
            IF length(candidate) > 30 THEN
                base_slug := substring(base_slug FROM 1 FOR 30 - length(suffix::TEXT));
                base_slug := regexp_replace(base_slug, '[._-]+$', '', 'g');
                candidate := base_slug || suffix::TEXT;
            END IF;
        END LOOP;

        UPDATE users SET username = candidate WHERE id = rec.id;
    END LOOP;
END $$;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_username_unique UNIQUE (username);
CREATE UNIQUE INDEX users_username_lower_idx ON users (LOWER(username));
