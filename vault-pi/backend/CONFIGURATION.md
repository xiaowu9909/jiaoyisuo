# vaultpi.* 配置项说明

本文档列出后端 `vaultpi.*` 相关配置、默认值与生产建议。

## vaultpi.bootstrap

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| create-default-admin | boolean | true | 是否自动创建默认管理员；**生产必须设为 false**，通过独立脚本创建管理员。 |

## vaultpi.security

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| expose-error-message | boolean | false | 是否向前端暴露详细错误信息。 |
| csrf-enabled | boolean | - | 生产建议 true。 |
| hsts-enabled | boolean | false | 生产建议 true。 |
| csp | string | - | 内容安全策略。 |

## vaultpi.websocket

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| allowed-origin-patterns | string | * | 逗号分隔的允许来源；**生产必须配置为前端域名列表**，禁止 *。 |

## vaultpi.virtual

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| kline-history-bars | int | 500 | K 线历史条数上限。 |
| default-price-low | number | 60 | 虚拟盘默认价格下限。 |
| default-price-high | number | 120 | 虚拟盘默认价格上限。 |
| entropy-noise-enabled | boolean | true | 是否混入熵噪声。 |
| dynamic-volatility-enabled | boolean | true | 是否启用波动率扰动。 |
| ou-mode-enabled | boolean | true | 是否启用 OU 过程。 |

## vaultpi.exchange

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| allow-self-trade | boolean | false | 是否允许同一用户买卖单自成交。**默认禁止**：同一账户的买单与卖单不会互相成交；建议在委托说明或帮助文档中向用户说明此规则。 |

## vaultpi.ratelimit

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| enabled | boolean | true | 是否启用限流。 |
| login-per-min | int | 60 | 登录每分钟上限。 |
| register-per-min | int | 5 | 注册每分钟上限。 |
| order-per-min | int | 30 | 下单每分钟上限。 |

## 监控指标（Prometheus）

| 指标名 | 说明 |
|--------|------|
| vaultpi.batch.persistence.queue.size | 批量持久化队列积压量。 |
| vaultpi.batch.persistence.failures | 批量持久化失败次数。 |
| vaultpi.match.exceptions | 撮合过程异常次数。 |
| vaultpi.match.duration | 单次撮合耗时。 |
| vaultpi.login.failures | 登录失败次数。 |
| vaultpi.order.failures | 下单失败次数（带 reason 标签）。 |
