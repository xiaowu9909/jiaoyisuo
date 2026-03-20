## Vault Pi 项目总览（C 端 / B 端 / 后端）

> 本文面向运维、产品和二次开发者，完整说明 Vault Pi 当前项目结构、各界面功能、以及关键实现方式。

---

## 1. 代码结构与运行方式

- **整体目录**
  - `backend/`：Spring Boot 后端，提供业务 API、认证、撮合、虚拟盘引擎等。
  - `frontend/`：C 端（用户前台），基于 Vue + Vite。
  - `admin/`：B 端（管理后台），基于 Vue + Vite。
  - `部署包/`：打包好的前后端与配置，用于服务器部署。
  - `scripts/` / `*.command`：本地一键启动与监听脚本。

- **典型运行方式（开发环境）**
  - 后端：`cd backend && ./mvnw spring-boot:run` 或使用根目录脚本。
  - C 端：`cd frontend && npm install && npm run dev`。
  - B 端：`cd admin && npm install && npm run dev`。

---

## 2. B 端管理后台（admin）

B 端是你当前重点视觉重构的部分，采用暗色 / 亮色双主题 + 液态玻璃风格。

### 2.1 布局与主题（`Layout.vue`）

- **主要职责**
  - 左侧侧边栏：模块导航（会员管理、币币管理、财务管理等）。
  - 顶部导航：面包屑、标签页（多页签路由）和用户菜单。
  - 主题切换：暗色 / 亮色双主题按钮。

- **关键实现点**
  - 使用 `vue-router` 的 `route` 信息生成：
    - 面包屑（`breadcrumbs`）
    - 标签页（`openedTags`，支持关闭、关闭其他、关闭全部）
  - 权限控制：
    - `getAdminMe()` 获取管理员及其 `adminPermissions`。
    - 菜单项和子菜单通过 `permission` 字段与权限列表匹配实现“可见/隐藏”。
  - 主题切换：
    - 使用 `document.documentElement.setAttribute('data-theme', theme)`。
    - CSS 中 `:root[data-theme='light']` 与 `:root[data-theme='dark']` 提供两套变量。
    - 用户选择保存在 `localStorage('vaultpi-admin-theme')`。

### 2.2 首页总览（`Home.vue`）

- **页面位置**：`/admin/#/home`
- **视觉风格**
  - 使用全局基础组件：
    - `UiPage`：负责页面标题、副标题、右上角徽章区域。
    - `UiCard`：统一大圆角、液态玻璃背景的卡片容器。
  - 顶部三块卡片：
    - 用户指标（注册、实名、商家）
    - 合约活跃度（持仓、委托、累计单量）
    - 业绩表现（当日 / 昨日 / 本月盈亏，币种切换）
  - 底部一块卡片：
    - 30 日业绩曲线（自绘 SVG 折线图 + 网格 + 点）

- **数据来源**
  - 接口文件：`admin/src/api/admin.ts`（未在此文件中，但所有请求统一封装在这里）
  - 使用的 API：
    - `getAdminMemberPage(1, 1)`：
      - 用 `totalElements` 统计总注册用户数。
    - `getAdminFuturesStats()`：
      - 返回合约持仓、挂单、累计订单等统计数据。
    - `getAdminFinanceStats(unit)` 与 `getAdminFinanceTrend(30, unit)`：
      - 返回按币种（USDT/BTC/ETH）统计的当日、昨日、本月业绩，以及 30 日趋势数组。

- **业务逻辑**
  - 页面挂载时使用 `Promise.all` 并发拉取会员数与合约统计。
  - 业绩卡片中的下拉框切换 `unit` 后，重新请求财务统计与趋势。
  - 趋势图：
    - 使用 `computed` 计算 `trendPoints`，将每个点映射到 `viewBox="0 0 1000 300"` 上的 `(x, y)` 坐标。
    - 自行绘制折线和圆点，无外部图表库，简单可靠。

### 2.3 会员管理（`Member.vue`）

- **页面位置**：`/admin/#/member`
- **界面功能**
  - 顶部操作区：
    - 搜索框：按用户名 / 邮箱 / 手机模糊查询。
    - 状态筛选：正常 / 异常。
    - 刷新按钮、导出报表按钮（导出目前提示“需后端支持”）。
    - 添加用户按钮（根据权限 `member-add` 控制显隐）。
  - 数据表格：
    - 展示 UID、会员基本信息、邀请码、实名信息、VIP 等级、累计充值、注册时间、状态、推荐人。
    - 行尾操作：详情、送彩金、拉黑（后两项依赖权限 `member-operate`）。
  - 弹窗功能：
    - 添加用户（用户名 + 密码 + 用户类型 + 邀请码）。
    - 送彩金（对指定用户加余额）。

- **关键实现方式**
  - 分页与查询：
    - `getAdminMemberPage(page, size, searchKey, statusFilter)`：
      - 后端返回 `content`（列表）和 `totalElements`（总数）。
      - 前端维护 `currentPage` 并在分页组件里调用 `changePage()`。
  - 权限：
    - `getAdminMe()` 读取 `adminPermissions`。
    - `canAddUser` / `canOperate` 控制按钮显隐。
  - 表单校验：
    - 添加用户时校验用户名非空、密码长度 >= 6。
    - 送彩金校验金额为正数。
  - 错误提示：
    - 使用本地 `errorMsg` / `addError` / `bonusError` 文本展示在卡片顶部或弹窗内。

### 2.4 币币交易对管理（`ExchangeCoin.vue`）

- **页面位置**：`/admin/#/exchange-coin`
- **界面功能**
  - 顶部操作区：
    - 按交易对（如 BTC/USDT）和结算币（如 USDT）进行本地筛选。
    - 刷新列表、新增交易对按钮。
  - 表格信息：
    - ID、交易对名、结算币、交易币。
    - 类型：虚拟盘 / 实盘。
    - 价格区间（虚拟盘专用）。
    - 活跃度（一般 / 活跃 / 热门）。
    - GBM 趋势 / 波动参数。
    - 价格精度 / 数量精度。
    - 启用状态与操作项（编辑 / 删除）。
  - 弹窗功能：
    - 新增 / 编辑交易对，配置完整精度与虚拟盘参数。

- **关键实现方式**
  - 数据获取：
    - `getAdminExchangeCoinList()` 拉取所有交易对。
    - 前端不分页，通过前端 filter 做符号 / 结算币筛选。
  - 新增 / 编辑：
    - `postAdminExchangeCoinAdd(formData)`、`postAdminExchangeCoinUpdate(formData)`。
    - 对虚拟盘配置：
      - 支持价格区间（`customPriceLow` / `customPriceHigh`）。
      - 或单一自设价格 `customPrice`。
      - 前端进行健壮性校验（范围是否合法、值 > 0）。
  - 删除：
    - 弹出 `confirm` 做二次确认。
    - 调 `postAdminExchangeCoinDelete(id)`，成功后刷新列表。

### 2.5 财务相关页面（`Finance.vue` / `FinanceStats.vue` / `RechargeAddress.vue`）

> 这些页面结构基本统一：顶部过滤 + 表格列表 + 部分带审核 / 通过 / 拒绝等操作。

- **资产审核（`Finance.vue`）**
  - 展示充值 / 提现记录。
  - 提供状态筛选（待审核 / 已通过 / 已拒绝）。
  - 可进行人工审核操作，通常调用类似 `postAdminFinanceApprove(id)` 等接口。

- **财务统计（`FinanceStats.vue`）**
  - 以表格形式展示细粒度统计项（如总充值、总提现、手动加减金额等）。
  - 和首页的“业绩表现”卡片数据是一致的，只是更偏列表。

- **充币地址管理（`RechargeAddress.vue`）**
  - 管理各币种在不同链上的充值地址。
  - 支持新增 / 编辑 / 禁用。

### 2.6 日志与系统管理页面

- **操作日志（`OperationLog.vue`）**
  - 展示后台管理员的操作记录。
  - 列出操作人、操作模块、请求参数、结果等信息。

- **错误日志（`ErrorLog.vue`）**
  - 展示后端异常日志摘要。
  - 方便查问题和审计。

- **管理员管理（`Admins.vue`）**
  - 添加 / 编辑后台管理员。
  - 分配 `adminPermissions` 权限集合。

- **全局参数配置（`System.vue`）**
  - 控制虚拟盘参数、风控开关、站点文案等系统级配置。

### 2.7 内容与首页配置（`Announcement.vue` / `HelpManage.vue` / `Advertise.vue` / `GettingStart.vue` / `AboutBrand.vue` / `AppDownload.vue`）

- **公告管理（`Announcement.vue`）**
  - 管理 C 端公告列表：标题、内容、上线状态、排序等。
  - 支持新增 / 编辑 / 下线。

- **帮助管理（`HelpManage.vue`）**
  - 管理帮助中心文章。
  - 通常通过富文本编辑器进行内容维护。

- **广告管理（`Advertise.vue`）**
  - 控制首页 Banner、活动位等投放内容。

- **首页文案配置**
  - `GettingStart.vue`：新手引导内容（图文模块）。
  - `AboutBrand.vue`：关于我们介绍。
  - `AppDownload.vue`：APP 下载链接与二维码设置。

---

## 3. C 端用户前台（frontend）

### 3.1 主要板块

- **首页 / 行情展示**
  - 展示主推交易对和市场概览。
  - 通过 WebSocket 或轮询方式获取虚拟盘或实盘行情。

- **现货交易（`/exchange`）**
  - 展示深度、K 线、委托簿。
  - 前端根据 `exchange_coin` 表的配置加载可用交易对。

- **合约交易**
  - 展示杠杆 / 合约持仓、保证金、强平价等。
  - 与后端虚拟盘引擎 / 实盘撮合对接。

- **资产与账户**
  - 充值 / 提现、资金划转记录。
  - 安全设置、邮箱 / 手机绑定、登录日志等。

### 3.2 样式与实现

- 使用 Vite + Vue 3 组合。
- 全局样式已调整为深色未来感主题，与 B 端视觉统一（字体、按钮风格相似）。
- 行情图表和订单簿多采用自绘或轻量图表库，以保证性能。

---

## 4. 后端（backend）

> 后端是 Spring Boot 应用，提供 REST API + WebSocket，负责认证、撮合、虚拟盘、风控等功能。

### 4.1 技术栈与设计

- Spring Boot + Spring MVC + Spring Data JPA。
- MySQL 作为主数据库，Flyway 管理表结构迁移。
- Redis：
  - 登录防刷、验证码、找回密码验证码。
  - 市场数据缓存、虚拟盘中间态、分布式锁。
- 分布式锁：
  - 使用 `LockProvider`（有 Redis 时启用，没有时退化为本地空实现）。
  - 典型应用：下单、余额修改等关键路径。

### 4.2 核心模块

- **用户与会员（`com.vaultpi.user`）**
  - `MemberService`：
    - 登录、注册、密码校验。
    - 支持 Redis 登录限制（多次失败锁定）。
  - `ResetPasswordController`：
    - 发送找回密码验证码（依赖 Redis 存储验证码）。
    - 校验验证码并重置密码。

- **现货撮合与订单（`com.vaultpi.exchange`）**
  - `OrderController`：
    - 提交现货订单。
    - 使用 `LockProvider` 在用户 + 交易对维度加锁，防止并发下单导致资金不一致。
    - 与 `MatchService` 交互，完成撮合逻辑。
  - `IdempotencyService`：
    - 基于 Redis 的幂等服务。
    - 防止重复请求创建重复订单（使用幂等键记录状态与结果）。

- **虚拟盘市场（`com.vaultpi.market`）**
  - `KrakenMarketRedisService`：
    - 与 Redis 交互保存虚拟行情数据。
  - `MarketStreamTask`：
    - 周期性（500ms）从虚拟盘引擎拉取行情，推送到前端（WebSocket）。
    - 依赖 `KrakenMarketRedisService` / `MarketCircuitBreaker` / `MarketLatencyRecorder`，并对这些依赖做了可选注入（无 Redis 时自动降级）。

### 4.3 数据库与初始化

- Flyway 脚本（如 `V1__baseline.sql`）负责初始化核心表结构：
  - `member`、`member_wallet`、`exchange_coin`、`exchange_order` 等。
- 配置：
  - `application-prod.yml` 通过 `vaultpi.bootstrap.create-default-admin: true` 控制是否自动创建默认管理员 `admin/admin123`。

---

## 5. 部署与配置（部署包）

- `部署包/admin/`：
  - 管理后台打包后的静态文件（`index.html` + `assets`）。
  - 已针对子域名部署（`admin.vault314.com`）做了 `base: './'` 与 Nginx SPA 配置适配。

- `部署包/config/application-prod.yml`：
  - 指定生产数据库、Redis、虚拟盘参数等。

- `部署包/config/nginx.conf.example`：
  - 为 C 端、B 端和后端 API/WebSocket 提供完整 Nginx 示例：
    - C 端：`vault314.com`。
    - B 端：`admin.vault314.com`。
    - API：`/api/` 反向代理到后端。
    - WebSocket：`/ws/` 配置 `Upgrade` / `Connection` 头。

---

## 6. 设计与扩展建议

- B 端所有新页面建议：
  - 使用 `UiPage` + `UiCard` 作为基础布局。
  - 表格统一使用 `.admin-card` + `.data-table` 样式。
  - 文案统一简体中文，字段名与接口字段保持一致，避免歧义。

- C 端扩展功能时：
  - 优先基于现有后端 API 与数据库表字段。
  - 涉及资金、撮合的逻辑必须经过幂等控制与分布式锁。

如需后续为项目添加英文版、移动端管理后台或多主题（品牌换肤），可以在当前的 `data-theme` / CSS 变量体系之上继续扩展。 

