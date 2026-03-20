# Vault π (Vault314.com)

新栈重构的数字货币交易平台：Spring Boot 3 单体后端 + Vue 3 + Vite 前端。品牌统一为 **Vault π**，官方入口 **Vault314.com**。老项目仅作业务参考。

## 结构

- `backend/`  Spring Boot 3.2，**JDK 17**，**MySQL 8.0**（必须 8.0）+ **Redis**
- `frontend/`  用户前台（Vue 3 + Vite，端口 5173）
- `admin/`  管理后台（独立 Vue 3 + Vite 项目，端口 5174）
- `REFACTOR_PLAN.md`  功能范围与实施阶段

前台和后台为**两个独立前端项目**，分别安装依赖、分别启动。

## 本地运行

### 后端

需 **JDK 17**（必须 17，不可用 8/11）。MySQL 必须 **8.0**（不可用 5.7），Redis 已启动。

```bash
cd backend
# 确保 MySQL 8.0 已建库 vaultpi、Redis 已启动；可改 application.yml 中账号密码
mvn spring-boot:run
```

健康检查：`GET http://localhost:8080/api/health`

### 前台（用户端）

```bash
cd frontend
npm install
npm run dev
```

访问：`http://localhost:5173`

### 管理后台

```bash
cd admin
npm install
npm run dev
```

访问：`http://localhost:5174`（API 通过 Vite 代理到后端 8080）

**管理后台登录**：首次运行会自动创建管理员账号 **admin / admin123**。上线前请在数据库中修改该账号密码或禁用默认账号并创建新管理员（将某用户的 `role` 设为 `ADMIN`）。

**若管理后台请求 `/api/admin/...` 出现 404：**

1. **必须先启动后端**：`cd backend && mvn spring-boot:run`，确认 `http://localhost:8080/api/health` 可访问。
2. **必须用 `npm run dev` 启动管理后台**：不要直接打开打包后的 `dist/index.html` 或从文件协议打开，否则没有代理，`/api` 会 404。
3. 确认本机 8080 端口未被占用，且后端无启动报错。

## 生产部署

1. **后端**  
   - 打包：`cd backend && mvn -DskipTests package`，得到 `target/vault-pi-backend-1.0.0-SNAPSHOT.jar`。  
   - 运行：`java -jar target/vault-pi-backend-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod`。  
   - 生产配置见 `application-prod.yml`，通过环境变量覆盖：`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_PASSWORD`、`SPRING_REDIS_HOST` 等。  
   - 生产环境建议 `spring.jpa.hibernate.ddl-auto=validate`（已写在 prod 配置），由 Flyway/Liquibase 或手动执行建表。

2. **前台与管理后台**  
   - 分别执行 `cd frontend && npm ci && npm run build`、`cd admin && npm ci && npm run build`，将 `dist/` 部署到 Nginx 或其他静态服务器。  
   - 将前台的 `/api`、管理后台的 `/api` 反向代理到后端地址（如 `http://127.0.0.1:8080`）。  
   - 若前后端同域，可设 `VITE_API_BASE` 为空或 `/api`；若跨域，需后端通过环境变量 `APP_CORS_ORIGINS` 或配置 `app.cors.allowed-origins` 放行前台与管理后台域名（逗号分隔）。

3. **安全**  
   - 修改默认管理员密码；生产库勿使用弱密码。  
   - 管理后台仅允许管理员（`member.role=ADMIN`）访问；未登录或非管理员访问 `/api/admin/*` 会返回 401/403。

## 部署包一键同步

改过代码后，将构建产物同步到 **`../部署包`**（供上传服务器），只需在项目根目录执行：

```bash
./sync-deploy.sh
```

脚本会自动：构建后端 JAR、C 端与 B 端前端，并覆盖部署包中的 `vault-pi-backend-1.0.0-SNAPSHOT.jar`、`public/`、`admin/`、`config/`。部署包路径可通过环境变量覆盖：`DEPLOY_DIR=/path/to/部署包 ./sync-deploy.sh`。

## 功能说明

- **限价单**：下单时冻结对应资产，挂单后立即尝试撮合；未成交部分可撤单解冻。订单状态：TRADING（挂单中）、FILLED（完全成交）、CANCELED（已撤单）。  
- **撮合**：新限价单入库后与反向挂单按价格优先、时间优先尝试成交，资金即时划转。

## 后续

按 `REFACTOR_PLAN.md` 的 Phase 推进；当前已完成：用户与资产、鉴权、行情与下单、管理后台（含管理员登录与鉴权）、撮合与资金结算、生产配置与部署说明。
