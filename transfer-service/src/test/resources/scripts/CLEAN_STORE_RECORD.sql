DELETE FROM outbox_notifications;
DELETE FROM transfer_transactions;
ALTER TABLE transfer_transactions ALTER COLUMN id RESTART WITH 1;
ALTER TABLE outbox_notifications ALTER COLUMN id RESTART WITH 1;
