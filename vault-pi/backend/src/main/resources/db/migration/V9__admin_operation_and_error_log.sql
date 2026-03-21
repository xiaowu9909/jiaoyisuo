-- Admin operation log and error log tables

CREATE TABLE IF NOT EXISTS admin_operation_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  admin_id BIGINT NOT NULL COMMENT '操作管理员 memberId',
  operator_name VARCHAR(128) DEFAULT NULL COMMENT '冗余展示名',
  module VARCHAR(64) NOT NULL COMMENT '模块，如 Member、Announcement',
  action VARCHAR(128) NOT NULL COMMENT '方法或动作标识',
  detail VARCHAR(1024) DEFAULT NULL COMMENT 'URI、摘要等',
  ip VARCHAR(64) DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_admin_op_time (created_at),
  KEY idx_admin_op_admin (admin_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理后台操作日志';

CREATE TABLE IF NOT EXISTS admin_error_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  level VARCHAR(16) NOT NULL COMMENT 'ERROR、WARN 等',
  source VARCHAR(256) NOT NULL COMMENT '异常类或来源',
  message TEXT NOT NULL,
  stack_trace MEDIUMTEXT,
  request_path VARCHAR(512) DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_admin_err_time (created_at),
  KEY idx_admin_err_level (level, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务端错误日志（供管理端查看）';
