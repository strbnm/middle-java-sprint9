DELETE FROM transfer_transactions;
ALTER TABLE transfer_transactions ALTER COLUMN id RESTART WITH 1;