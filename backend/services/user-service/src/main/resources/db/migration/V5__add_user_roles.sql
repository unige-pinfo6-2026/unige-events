-- SCRUM-214 follow-up — expose Auth0 roles on user profiles so the
-- frontend can render the "Staff" badge on any admin's profile (not
-- just the viewer's own).
--
-- Roles live in the Auth0 token claim only ; we mirror them in the DB
-- via `UserService.syncRoles(...)` called on every GET /users/me hit
-- (the natural sync point — frontend bootstraps from /me on every page
-- load). Stale roles are accepted (the next /me round-trips refreshes
-- them) — refreshing on every authenticated request would amplify the
-- write load with no real benefit.
--
-- Same shape as `user_interests` (V1) : @ElementCollection-mapped
-- side-table with `user_id` FK + the field column. Bag semantics
-- (no PK) match the entity contract.

CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID         NOT NULL,
    roles   VARCHAR(255) NOT NULL,
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Index for the rare reverse-lookup "give me every admin" used by the
-- admin dashboard. Without it, listing admins would require a full
-- scan of the table — cheap today (handful of admins), but the index
-- is one line and keeps the query plan stable as the table grows.
CREATE INDEX IF NOT EXISTS ix_user_roles_role ON user_roles (roles);
