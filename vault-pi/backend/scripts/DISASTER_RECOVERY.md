# 灾难恢复预案

## 1. 恢复优先级

1. **用户资产表**：`member_wallet`、`member_transaction` — 优先保证余额与流水一致  
2. **订单与成交**：`exchange_order`、`exchange_trade` — 用于对账与争议  
3. **充值/提现记录**：`member_deposit`、提现相关表 — 资金审核依据  
4. **配置与用户**：`member`、`exchange_coin`、`system_config` 等  

## 2. 备份策略（最低方案）

- **全量备份**：每日执行 `scripts/backup-mysql.sh`（mysqldump + gzip），保留至少 7 天  
- **存放**：备份文件上传至异地存储（如 OSS/S3 或另一台机器），避免与数据库同机  
- **操作日志**：所有资金变动（充值、提现、成交）已通过 `member_transaction` 及业务表记录，支持财务对账  

## 3. 从备份恢复步骤

1. 停止应用，避免新数据写入  
2. 如需恢复整库：  
   - `gunzip -c vaultpi_YYYYMMDD_HHMMSS.sql.gz | mysql -u root -p vaultpi`  
   - 或先 `mysql -e "DROP DATABASE vaultpi; CREATE DATABASE vaultpi;"` 再导入  
3. 若仅恢复部分表：从备份 SQL 中提取对应表的 `INSERT` 或使用 `mysql vaultpi < partial.sql`  
4. 校验关键表行数及余额汇总，确认无误后启动应用  

## 4. 虚拟盘状态恢复

- 虚拟盘价格/深度/K 线由 `VirtualMarketEngine` 维护，已支持 **Redis 快照**（每 30 秒、TTL 5 分钟）  
- 重启后优先从 Redis 恢复价格与 24h 统计；若 Redis 无数据则按配置中心价重新初始化  
- 多实例部署时，各实例从 Redis 读取同一快照，避免价格不一致  

## 5. 审计与对账

- **虚拟盘趋势操作**：`virtual_trend_audit` 表记录管理员、时间、交易对、涨跌幅、周期、IP，管理端可通过 `/api/admin/exchange/coin/trend/audit` 查询  
- **资金流水**：`member_transaction` 记录类型（RECHARGE、WITHDRAW、TRADE 等），可按会员、币种、时间范围导出对账  

## 6. 联系与升级

- 数据丢失或逻辑错误导致资金异常时，除恢复备份外，应人工核对 `member_wallet` 与 `member_transaction` 汇总，必要时通过人工调账或补偿流程处理  
- 建议定期演练恢复流程（在测试环境恢复备份并验证应用与数据一致性）  
