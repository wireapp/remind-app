-- Personal timezone preference per user, used to interpret the "when" part of a reminder.
CREATE SEQUENCE USER_TIMEZONES_SEQ start 1 increment 1;
CREATE TABLE USER_TIMEZONES (
    id int8 NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    zone_id VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UQ_USER_TIMEZONES_USER_ID UNIQUE (user_id)
);

-- A reminder keeps the timezone it was created with, so its schedule never shifts when the
-- creator later changes their preference. Reminders created before this migration were
-- interpreted as UTC, which is what the default backfills them with.
ALTER TABLE REMINDERS ADD COLUMN zone_id VARCHAR(64) NOT NULL DEFAULT 'UTC';
