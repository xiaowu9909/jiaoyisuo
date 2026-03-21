-- AI features: member AI fields, order AI fields, and new tables

ALTER TABLE member ADD COLUMN ai_status TINYINT NOT NULL DEFAULT 0;
ALTER TABLE member ADD COLUMN ai_expire_time DATETIME NULL;

ALTER TABLE exchange_order ADD COLUMN is_ai TINYINT NOT NULL DEFAULT 0;
ALTER TABLE exchange_order ADD COLUMN is_missed TINYINT NOT NULL DEFAULT 0;
ALTER TABLE exchange_order ADD COLUMN required_balance DECIMAL(18,8) NULL;
ALTER TABLE exchange_order ADD COLUMN ai_note VARCHAR(255) NULL;
ALTER TABLE exchange_order ADD COLUMN profit DECIMAL(18,8) NULL;

CREATE TABLE IF NOT EXISTS ai_strategy_phrases (
  id INT PRIMARY KEY AUTO_INCREMENT,
  type TINYINT NOT NULL COMMENT '1=运行状态流, 2=盈利理由, 3=亏损理由, 4=错过理由',
  content VARCHAR(500) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用, 0=禁用'
);

CREATE TABLE IF NOT EXISTS ai_plan (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  price DECIMAL(18,8) NOT NULL,
  days INT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1=上架, 0=下架'
);

CREATE TABLE IF NOT EXISTS ai_subscribe_record (
  id INT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  plan_name VARCHAR(100) NOT NULL,
  cost DECIMAL(18,8) NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_ai_sub_user (user_id)
);

-- Seed initial phrases
INSERT INTO ai_strategy_phrases (type, content, status) VALUES
(1, 'AI量化引擎运行正常，已完成市场深度扫描，当前策略执行率 98.6%', 1),
(1, '检测到市场波动信号，多空博弈激烈，策略模型动态调仓中...', 1),
(1, '高频因子模型更新完毕，下一个窗口期信号评估中，请稍候', 1),
(1, '策略引擎持续监控 BTC、ETH、SOL 等主流资产联动走势', 1),
(2, '基于MACD金叉信号与成交量突破，动量因子评分达到买入阈值，精准入场', 1),
(2, 'RSI超卖区域反弹叠加布林带收口，多重技术共振触发买点，策略执行完毕', 1),
(2, '链上大额地址吸筹信号与资金费率反转共振，量化模型给出强烈买入信号', 1),
(2, '市场情绪指标从恐慌转向中性，策略抓住短期均值回归机会，盈利出仓', 1),
(3, '市场出现突发宏观事件，高频因子给出风险警报，本次执行产生计划内风险对冲亏损', 1),
(3, '多个资产相关性骤升，策略进行了保护性止损操作，本次亏损在预设风险预算内', 1),
(3, '市场流动性短暂枯竭导致滑点超出预期，量化模型已记录此次异常并更新参数', 1),
(4, '账户可用保证金低于该策略最低执行门槛，当前窗口被迫跳过。据测算，此信号原本有约62%概率触及目标收益区间', 1),
(4, '本次信号强度评级为A+，但执行所需流动性头寸超过账户当前余额，策略已记录为"计划错过"', 1),
(4, '检测到高胜率入场时机，因账户资金不满足最低仓位要求，本次机会未能参与。建议及时补充资金以捕捉后续机会', 1);

-- Seed initial AI plans
INSERT INTO ai_plan (name, price, days, status) VALUES
('AI幻影引擎-月度版', 100.00000000, 30, 1),
('AI幻影引擎-季度版', 280.00000000, 90, 1),
('AI幻影引擎-年度版', 800.00000000, 365, 1);
