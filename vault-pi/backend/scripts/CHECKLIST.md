# 上线前最后检查清单

生产部署前请逐项确认以下**必须完成**项。

---

## 0. 环境对齐（必须）

- [ ] **MySQL 8.0**（必须是 8.0！不可用 5.7）
- [ ] **Redis** 已安装并启动
- [ ] **JDK 17**（必须 17，不可用 8/11）

---

## 1. 数据库迁移脚本验证（Flyway）

- [ ] **V1__baseline.sql**：基线脚本已执行或已 baseline，无报错
- [ ] **V2__add_indexes.sql**：索引已创建，无重复创建错误
- [ ] **V3__virtual_trend_audit.sql**：表 `virtual_trend_audit` 已创建
- [ ] **V4__exchange_order_match_indexes.sql**：撮合/用户订单复合索引已创建
- [ ] **V5__member_wallet_version.sql**：钱包乐观锁版本列已创建
- [ ] **V6__exchange_coin_order_limits.sql**：交易对 min_amount/max_amount/min_notional 已创建
- [ ] **V7__audit_log.sql**：审计日志表已创建
- [ ] 生产使用 `spring.jpa.hibernate.ddl-auto: validate`，**不要**使用 `update`/`create`，所有表结构变更仅通过 Flyway 管理
- 验证方式：在与生产同版本的 MySQL 上执行 `mvn flyway:migrate`（或启动应用触发迁移），检查 `flyway_schema_history` 表

---

## 2. 生产环境配置审查（application-prod.yml）

- [ ] `vaultpi.bootstrap.create-default-admin: false` 已设置且未被覆盖
- [ ] `vaultpi.security.csrf-enabled: true`、`hsts-enabled: true`
- [ ] `spring.datasource.password` 通过环境变量 `SPRING_DATASOURCE_PASSWORD` 提供，无默认弱密码
- [ ] `app.cors.allowed-origins` 仅包含实际使用的前台/管理后台域名（HTTPS）
- [ ] Redis 密码、数据库 URL 等敏感信息均来自环境变量或密钥管理，未写死在配置文件中

---

## 3. 管理员账户独立创建（非默认弱密码）

- [ ] 未使用默认 `admin`/`admin123`；生产已确认不会自动创建该账户（见上条）
- [ ] 首个管理员已通过以下方式之一创建并完成首次登录改密或使用强密码：
  - 独立脚本/写库（见 `scripts/create-admin.md`）
  - 或临时启用一次 `create-default-admin` 后立即改密并关闭
- [ ] 若曾临时启用默认管理员，已再次确认配置中已关闭且重启后不会再现

---

## 4. SSL 证书配置（HTTPS 强制）

- [ ] 对外提供服务的入口已使用 HTTPS（Nginx/负载均衡终止 SSL 或应用内配置 `server.ssl`）
- [ ] 若在应用层配置 SSL：已配置 `server.ssl.key-store`、密码等，并验证浏览器可正常访问
- [ ] 前台/管理后台仅通过 HTTPS 访问，HTTP 已重定向到 HTTPS（由反向代理或应用完成）
- [ ] `application-prod.yml` 中 `app.cors.allowed-origins` 使用 `https://` 域名

---

## 5. 备份脚本首次运行验证

- [ ] `scripts/backup-mysql.sh` 已在目标环境执行成功（需设置 `SPRING_DATASOURCE_PASSWORD` 或 `MYSQL_*` 等）
- [ ] 备份文件已生成且可解压/可被 MySQL 识别（如 `gunzip -c xxx.sql.gz | head -50`）
- [ ] 备份目录与保留天数（如 7 天）已按策略配置，并已加入 cron 或调度任务
- [ ] 备份文件已上传至异地或另一台机器（建议至少一份异地副本）

---

## 可选但建议

- [ ] 虚拟盘趋势审计：管理后台已可访问「趋势设置审计」页面并验证能查询到记录
- [ ] 监控与告警：Prometheus 已抓取 `/actuator/prometheus`，Grafana 仪表板可查看 `vaultpi.*` 指标（可导入 `scripts/grafana-dashboard-vaultpi.json`）；**批量持久化失败**告警规则已配置（`vaultpi_batch_persistence_failures_total > 0`），运行手册见 `scripts/RUNBOOK_BATCH_PERSISTENCE.md`
- [ ] 日志与排查：日志级别、输出格式已按生产需求配置；若使用 ELK，已确认可采集并检索
- [ ] 灾难恢复：团队已阅读 `scripts/DISASTER_RECOVERY.md`，并在测试环境演练过一次恢复流程

完成上述必检项后，即可进行生产部署与发布。
