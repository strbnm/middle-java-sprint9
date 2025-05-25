DELETE FROM exchange_rates;
ALTER TABLE exchange_rates ALTER COLUMN id RESTART WITH 1;
