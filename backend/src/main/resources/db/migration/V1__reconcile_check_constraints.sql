-- V1: Reconcile CHECK constraints with current enum values.
--
-- Hibernate `update` mode previously generated CHECK constraints on the initial
-- table creation but never updated them when enum values were renamed (events_*)
-- or added (WAITLISTED on attendances). This migration drops the stale
-- constraints and recreates them with the current values.
--
-- Wrapped in conditional DO blocks so the script is a no-op against an empty
-- database: in tests, DevServices provisions a fresh PostgreSQL and Hibernate
-- (drop-and-create in %test) creates the schema *after* Flyway runs. For
-- existing dev/staging/prod databases that already have the tables, the
-- constraints are dropped and recreated normally.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema() AND table_name = 'events'
    ) THEN
        ALTER TABLE events DROP CONSTRAINT IF EXISTS events_faculty_check;
        ALTER TABLE events ADD CONSTRAINT events_faculty_check
            CHECK (faculty IN
                ('SCIENCES','MEDICINE','LETTERS','SOCIAL_SCIENCES',
                 'GSEM','LAW','THEOLOGY','PSYCHOLOGY','FTI'));

        ALTER TABLE events DROP CONSTRAINT IF EXISTS events_category_check;
        ALTER TABLE events ADD CONSTRAINT events_category_check
            CHECK (category IN
                ('ACADEMIC','SPORTS','CULTURAL','SOCIAL','CONFERENCE','OTHER'));

        ALTER TABLE events DROP CONSTRAINT IF EXISTS events_status_check;
        ALTER TABLE events ADD CONSTRAINT events_status_check
            CHECK (status IN ('DRAFT','PUBLISHED','CANCELLED'));
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema() AND table_name = 'attendances'
    ) THEN
        ALTER TABLE attendances DROP CONSTRAINT IF EXISTS attendances_status_check;
        ALTER TABLE attendances ADD CONSTRAINT attendances_status_check
            CHECK (status IN ('ATTENDING','WAITLISTED'));
    END IF;
END
$$;
