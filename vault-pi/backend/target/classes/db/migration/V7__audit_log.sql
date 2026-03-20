CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL COMMENT '会员ID，未登录操作为空',
    action VARCHAR(64) NOT NULL COMMENT 'LOGIN_SUCCESS, LOGIN_FAIL, PASSWORD_UPDATE, PASSWORD_RESET 等',
    detail VARCHAR(512) NULL COMMENT '补充说明，如用户名、订单号',
    ip VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_user_created (user_id, created_at),
    INDEX idx_audit_action_created (action, created_at)
);
