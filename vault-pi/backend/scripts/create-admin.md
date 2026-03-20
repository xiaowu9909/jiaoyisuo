# 生产环境首次创建管理员

生产环境已强制 `vaultpi.bootstrap.create-default-admin: false`，不会自动创建 admin 账户。

## 方式一：通过现有 API（需先有至少一个管理员）

若已有管理员账号，可在管理后台「会员管理」中创建新用户并赋予 ADMIN 角色。

## 方式二：独立脚本（首次部署）

首次部署、尚无任何管理员时，可通过以下方式之一创建：

1. **临时启用一次默认管理员（不推荐）**  
   启动时加参数：`--vaultpi.bootstrap.create-default-admin=true`，启动后立即登录并修改 admin 密码、创建其他管理员，再将该参数去掉并重启。  
   **注意**：仅限首次部署且无法执行脚本时使用，使用后务必改密并关闭该开关。

2. **直接写库（推荐，需 DBA/运维执行）**  
   - 使用 BCrypt 对目标密码做哈希（可与现有 Spring Security 一致，rounds=10）。  
   - 在 `member` 表中插入一条记录：`username`、`email`、`password`（哈希值）、`status='NORMAL'`、`role='ADMIN'`、`user_type='INTERNAL'`、`registration_time=now()` 等必填字段按表结构补全。  
   - 具体 SQL 或脚本可由运维根据当前表结构编写。

3. **调用内部接口（若已暴露）**  
   若项目提供「首次安装初始化」等受控接口（如带安装 token），可通过该接口创建首个管理员。

上线前请确认生产配置中 **未** 设置 `vaultpi.bootstrap.create-default-admin: true`，避免遗留默认弱密码账户。
