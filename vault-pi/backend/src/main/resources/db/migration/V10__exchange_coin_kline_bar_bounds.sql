-- Add kline bar price bounds to exchange_coin for virtual market constraints

ALTER TABLE exchange_coin
  ADD COLUMN IF NOT EXISTS virtual_kline_bar_floor DECIMAL(24,8) NULL,
  ADD COLUMN IF NOT EXISTS virtual_kline_bar_ceil  DECIMAL(24,8) NULL;
