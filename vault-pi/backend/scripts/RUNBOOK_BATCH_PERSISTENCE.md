# 运行手册：批量持久化失败

当 **vaultpi.batch.persistence.failures** 指标大于 0 时，表示撮合产生的成交批次在落库时失败（事务回滚后已重新入队）。

## 1. 告警配置建议

- **Prometheus 告警规则**：`increase(vaultpi_batch_persistence_failures_total[5m]) > 0` 即触发告警。
- **Grafana**：在「撮合与持久化」大盘中为该指标设置「无数据或 >0 时告警」。
- **钉钉/企业微信**：通过 Alertmanager 或 Grafana 通知渠道，将上述告警推送运维群。

## 2. 处理步骤

1. **确认现象**  
   查看日志关键字：`批量持久化失败`，确认失败时的批次大小（orders/trades/txs/wallets）及异常堆栈。

2. **排查数据库与网络**  
   - 检查 MySQL 连接数、慢查询、锁等待；必要时重启 DB 或扩容。  
   - 检查应用与 MySQL 之间网络是否抖动、超时。  
   - 若为乐观锁冲突（日志中出现 `OptimisticLockException` 或「钱包乐观锁冲突」）：说明同一钱包被并发更新，需看是否存在重复撮合或对同一用户/币种的高频并发成交。

3. **队列积压**  
   - 查看 **vaultpi.batch.persistence.queue.size**：若持续升高，说明 flush 持续失败或吞吐不足。  
   - 在 DB 与网络恢复后，队列会由定时任务（每 100ms）继续消费；若应用已重启，未落库的批次会丢失（仅当次进程内存中的数据），需对账。

4. **对账与重放**  
   - 资金与订单已做原子性设计：单次 flush 内钱包+订单+成交+流水同事务，要么全成功要么全回滚，不会出现「钱动单不动」。  
   - 若长时间失败导致业务侧发现「订单与余额不一致」，需用 `member_wallet`、`member_transaction`、`exchange_order`、`exchange_trade` 做人工或脚本对账，必要时联系开发做补偿或重放。

5. **恢复后验证**  
   - 观察 `vaultpi.batch.persistence.failures` 不再增长，`vaultpi.batch.persistence.queue.size` 回落。  
   - 做一笔小额撮合测试，确认成交与余额变动一致。

## 3. 预防

- 保证 MySQL 与应用间网络稳定，连接池与超时配置合理。  
- 生产环境建议优雅停机（收到 SIGTERM 后执行批次 flush 再退出），避免 kill -9 导致未刷批丢失。
