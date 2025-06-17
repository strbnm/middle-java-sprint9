DELETE FROM cash_transactions;
ALTER TABLE cash_transactions ALTER COLUMN id RESTART WITH 1;
