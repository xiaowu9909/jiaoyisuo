-- 撮合查询复合索引：MatchService 按 (symbol, direction, status) 过滤并按 price、create_time 排序，避免全表扫描
CREATE INDEX idx_exchange_order_symbol_direction_status ON exchange_order(symbol, direction, status);

-- 用户历史订单：按 (member_id, create_time) 分页，覆盖常见查询
CREATE INDEX idx_exchange_order_member_create ON exchange_order(member_id, create_time);
