## 发布闭环脚本

这套脚本用于实现：本地改代码 -> 发布到服务器 -> 健康检查 -> 必要时回滚。

### 1) 首次使用

给脚本执行权限：

```bash
chmod +x scripts/release-sync.sh scripts/rollback-server.sh scripts/health-check.sh
```

### 2) 一键发布

```bash
./scripts/release-sync.sh
```

默认已写入当前服务器参数（可被环境变量覆盖）：
- `SERVER_HOST=107.175.124.214`
- `SERVER_PORT=22`
- `SERVER_USER=root`
- `SERVER_PASS=***`
- `APP_DIR=/www/wwwroot/vault314.com`

示例（临时覆盖）：

```bash
SERVER_HOST=107.175.124.213 ./scripts/release-sync.sh
```

### 3) 发布后健康检查

```bash
./scripts/health-check.sh
```

### 4) 回滚

先看服务器备份目录（发布时会自动生成）：

```bash
ssh root@107.175.124.214 "ls -la /www/wwwroot/vault314.com/_backup"
```

按时间戳回滚：

```bash
./scripts/rollback-server.sh 20260320-171643
```

### 5) 说明

- 脚本保留 `admin/.user.ini`，避免因权限导致部署失败。
- 发布时会尝试修复 `flyway_schema_v4_pro` 中 `success=0` 记录，避免启动被 Flyway 验证阻断。
- 若你后续改为 SSH Key 登录，可以把 `sshpass` 部分替换为普通 `ssh/scp`。
