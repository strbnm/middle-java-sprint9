DELETE FROM notifications;
ALTER TABLE notifications ALTER COLUMN id RESTART WITH 1;
