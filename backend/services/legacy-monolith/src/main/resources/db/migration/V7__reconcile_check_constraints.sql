ALTER TABLE events DROP CONSTRAINT IF EXISTS events_faculty_check;
ALTER TABLE events ADD CONSTRAINT events_faculty_check
    CHECK (faculty IN ('SCIENCES', 'MEDICINE', 'LETTERS', 'SOCIAL_SCIENCES',
                       'GSEM', 'LAW', 'THEOLOGY', 'PSYCHOLOGY', 'FTI'));

ALTER TABLE events DROP CONSTRAINT IF EXISTS events_category_check;
ALTER TABLE events ADD CONSTRAINT events_category_check
    CHECK (category IN ('ACADEMIC', 'SPORTS', 'CULTURAL', 'SOCIAL', 'CONFERENCE', 'OTHER'));

ALTER TABLE events DROP CONSTRAINT IF EXISTS events_status_check;
ALTER TABLE events ADD CONSTRAINT events_status_check
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED'));

ALTER TABLE attendances DROP CONSTRAINT IF EXISTS attendances_status_check;
ALTER TABLE attendances ADD CONSTRAINT attendances_status_check
    CHECK (status IN ('ATTENDING', 'WAITLISTED'));

ALTER TABLE reports DROP CONSTRAINT IF EXISTS reports_status_check;
ALTER TABLE reports ADD CONSTRAINT reports_status_check
    CHECK (status IN ('PENDING', 'REVIEWED', 'DISMISSED'));
