-- 为高频查询字段添加索引，提升订单列表、钱包查询性能
-- member 的 username/email 通常已有唯一索引，不再重复创建

-- exchange_order: 当前委托/历史按 member_id、symbol 查询
CREATE INDEX idx_exchange_order_member_id ON exchange_order(member_id);
CREATE INDEX idx_exchange_order_symbol ON exchange_order(symbol);
CREATE INDEX idx_exchange_order_member_status ON exchange_order(member_id, status);

-- member_wallet: 按会员与币种查询
CREATE INDEX idx_member_wallet_member_coin ON member_wallet(member_id, coin_id);
