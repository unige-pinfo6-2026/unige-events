-- SCRUM-169 — add `username` (public-facing handle) to users.
-- Atomic migration (cf. spec Décision H) : add column nullable, back-fill via
-- PL/pgSQL slug generator with numeric-suffix anti-collision (Décision G), then
-- flip to NOT NULL + UNIQUE + CHECK regex.
--
-- Once this migration is committed and pushed to a preview/prod DB it becomes
-- IMMUTABLE — any later correction lives in a new V4__... (cf. backend/AGENTS.md).

CREATE EXTENSION IF NOT EXISTS unaccent;

ALTER TABLE users ADD COLUMN username VARCHAR(30);

-- Back-fill : slug from displayName > firstName.lastName > 'user',
-- ASCII-folded via unaccent, normalized to [a-z0-9._-], blocklist-avoided,
-- collision-resolved by incremental numeric suffix.
DO $$
DECLARE
    rec        RECORD;
    base       TEXT;
    candidate  TEXT;
    suffix_num INT;
    reserved   TEXT[] := ARRAY['me', 'admin', 'api', 'login', 'logout', 'signup', 'register', 'settings'];
BEGIN
    -- ORDER BY id for deterministic ordering (UUID v4 — stable per row) so
    -- that re-running on the same dataset (preview rebuild) yields the same
    -- usernames.
    FOR rec IN
        SELECT id, display_name, first_name, last_name
        FROM users
        WHERE username IS NULL
        ORDER BY id
    LOOP
        -- 1. Pick the best textual source.
        base := COALESCE(
            NULLIF(trim(rec.display_name), ''),
            NULLIF(
                TRIM(BOTH '.' FROM concat_ws('.',
                    NULLIF(trim(rec.first_name), ''),
                    NULLIF(trim(rec.last_name), '')
                )),
                ''
            ),
            'user'
        );

        -- 2. ASCII-fold accents + lowercase.
        base := lower(unaccent(base));

        -- 3. Normalize : whitespace -> '.', drop chars outside [a-z0-9._-],
        --    collapse runs of '.', trim leading/trailing '.', '_', '-'.
        base := regexp_replace(base, '\s+', '.', 'g');
        base := regexp_replace(base, '[^a-z0-9._-]', '', 'g');
        base := regexp_replace(base, '\.+', '.', 'g');
        base := regexp_replace(base, '^[._-]+|[._-]+$', '', 'g');

        -- 4. Truncate to fit VARCHAR(30), re-trim trailing punctuation
        --    in case the cut landed on '.', '_', '-'.
        IF length(base) > 30 THEN
            base := left(base, 30);
            base := regexp_replace(base, '[._-]+$', '', 'g');
        END IF;

        -- 5. Apply final fallback if the result became empty or < 3 chars.
        IF base IS NULL OR length(base) < 3 THEN
            base := 'user';
        END IF;

        -- 6. Resolve collisions + blocklist with incremental numeric suffix.
        --    A reserved base name starts at suffix 2 directly (Décision F).
        IF base = ANY(reserved) THEN
            suffix_num := 2;
        ELSE
            suffix_num := 1;
        END IF;

        IF suffix_num = 1 THEN
            candidate := base;
        ELSE
            candidate := left(base, 30 - length(suffix_num::text)) || suffix_num::text;
        END IF;

        WHILE EXISTS (SELECT 1 FROM users WHERE username = candidate) LOOP
            suffix_num := suffix_num + 1;
            candidate := left(base, 30 - length(suffix_num::text)) || suffix_num::text;
        END LOOP;

        UPDATE users SET username = candidate WHERE id = rec.id;
    END LOOP;
END $$;

-- Enforce constraints now that every row has a username.
ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_username UNIQUE (username);
ALTER TABLE users ADD CONSTRAINT ck_users_username CHECK (username ~ '^[a-z0-9._-]{3,30}$');
