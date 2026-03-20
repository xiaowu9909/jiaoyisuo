-- 单笔订单数量与名义价值限制，用于下单前校验
ALTER TABLE exchange_coin ADD COLUMN min_amount DECIMAL(26,16) NULL COMMENT '单笔最小数量';
ALTER TABLE exchange_coin ADD COLUMN max_amount DECIMAL(26,16) NULL COMMENT '单笔最大数量';
ALTER TABLE exchange_coin ADD COLUMN min_notional DECIMAL(26,16) NULL COMMENT '单笔最小名义价值(基币)';
