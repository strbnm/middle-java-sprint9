DELETE FROM outbox_notifications;
DELETE FROM cash_transactions;
ALTER TABLE cash_transactions ALTER COLUMN id RESTART WITH 1;
ALTER TABLE outbox_notifications ALTER COLUMN id RESTART WITH 1;
