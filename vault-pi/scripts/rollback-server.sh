#!/usr/bin/env bash
set -euo pipefail

# 回滚：按备份时间戳恢复 public/admin/jar 并重启后端
# 用法：./scripts/rollback-server.sh 20260320-171643

if [ $# -lt 1 ]; then
  echo "用法: $0 <BACKUP_TS>"
  exit 1
fi

BACKUP_TS="$1"

SERVER_HOST="${SERVER_HOST:-107.175.124.214}"
SERVER_PORT="${SERVER_PORT:-22}"
SERVER_USER="${SERVER_USER:-root}"
SERVER_PASS="${SERVER_PASS:-88r0ggf5YL0TKmRbL7}"
APP_DIR="${APP_DIR:-/www/wwwroot/vault314.com}"

REMOTE_CMD=$(cat <<EOF
set -euo pipefail
APP_DIR="${APP_DIR}"
BK="\${APP_DIR}/_backup/${BACKUP_TS}"
if [ ! -d "\${BK}" ]; then
  echo "备份不存在: \${BK}"
  exit 2
fi
cp -a "\${BK}/public/." "\${APP_DIR}/public/" || true
find "\${APP_DIR}/admin" -mindepth 1 -maxdepth 1 ! -name .user.ini -exec rm -rf {} +
cp -a "\${BK}/admin/." "\${APP_DIR}/admin/" || true
cp -f "\${BK}/vault-pi-backend-1.0.0-SNAPSHOT.jar" "\${APP_DIR}/" || true
OLD_PID=\$(ss -ltnp | awk '/8081/ && /java/ {print \$NF}' | sed -E 's/.*pid=([0-9]+).*/\\1/' | head -n 1 || true)
if [ -n "\${OLD_PID}" ]; then
  kill "\${OLD_PID}" || true
  sleep 2
fi
cd "\${APP_DIR}"
nohup /usr/bin/java -Dspring.profiles.active=prod -Xms2g -Xmx2g \
  -jar vault-pi-backend-1.0.0-SNAPSHOT.jar \
  --server.port=8081 \
  --spring.jpa.hibernate.ddl-auto=none \
  --spring.flyway.table=flyway_schema_v4_pro \
  --spring.flyway.baseline-on-migrate=true \
  --spring.flyway.baseline-version=7 \
  --vaultpi.kraken.ws.reconnect-delay-ms=2000 \
  --spring.task.scheduling.pool.size=20 \
  > backend.log 2>&1 &
sleep 6
ss -ltnp | awk '/8081/ {print}'
curl -sS http://127.0.0.1:8081/actuator/health || true
EOF
)

sshpass -p "${SERVER_PASS}" ssh -p "${SERVER_PORT}" -T -o StrictHostKeyChecking=no \
  "${SERVER_USER}@${SERVER_HOST}" "${REMOTE_CMD}"

echo "回滚完成: ${BACKUP_TS}"
