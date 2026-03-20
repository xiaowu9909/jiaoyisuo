-- 虚拟盘趋势设置审计表：记录谁、何时、对哪个交易对设置了什么趋势
CREATE TABLE IF NOT EXISTS virtual_trend_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    percent DECIMAL(10,4) NOT NULL,
    duration INT NOT NULL,
    operation_time BIGINT NOT NULL,
    ip VARCHAR(64),
    start_price DECIMAL(24,8),
    INDEX idx_virtual_trend_audit_symbol (symbol),
    INDEX idx_virtual_trend_audit_time (operation_time),
    INDEX idx_virtual_trend_audit_admin (admin_id)
);
