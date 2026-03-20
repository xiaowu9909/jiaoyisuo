-- 乐观锁版本号，供撮合批量更新钱包时并发安全
ALTER TABLE member_wallet ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
