-- A reminder that is a short note plus a link runs past the original 250 characters easily, and
-- the overflow was only caught when the transaction committed, which left the user without an
-- answer. Widen the column and keep ValidateReminder.MAX_TASK_LENGTH in step with it.
-- Growing the length of a varchar is a metadata-only change in Postgres, so no table rewrite.
ALTER TABLE REMINDERS ALTER COLUMN task TYPE VARCHAR(1000);
