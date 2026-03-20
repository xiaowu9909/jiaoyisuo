#!/usr/bin/env bash
set -euo pipefail

# 一键发布：本地构建 -> 上传 -> 服务器备份替换 -> 重启 -> 健康检查

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TMP_DIR="${ROOT_DIR}/.deploy-temp"
PKG_FILE="/tmp/vaultpi-deploy.tgz"
REMOTE_SCRIPT="/root/vaultpi-remote-deploy.sh"

# 默认参数（可通过环境变量覆盖）
SERVER_HOST="${SERVER_HOST:-107.175.124.214}"
SERVER_PORT="${SERVER_PORT:-22}"
SERVER_USER="${SERVER_USER:-root}"
SERVER_PASS="${SERVER_PASS:-88r0ggf5YL0TKmRbL7}"
APP_DIR="${APP_DIR:-/www/wwwroot/vault314.com}"

echo "==> 1/7 本地构建 backend/frontend/admin"
(cd "${ROOT_DIR}/backend" && mvn -q -DskipTests package)
(cd "${ROOT_DIR}/frontend" && npm run -s build)
(cd "${ROOT_DIR}/admin" && npm run -s build)

echo "==> 2/7 组装部署包"
rm -rf "${TMP_DIR}"
mkdir -p "${TMP_DIR}"
cp "${ROOT_DIR}/backend/target/vault-pi-backend-1.0.0-SNAPSHOT.jar" "${TMP_DIR}/"
cp -R "${ROOT_DIR}/frontend/dist" "${TMP_DIR}/public"
cp -R "${ROOT_DIR}/admin/dist" "${TMP_DIR}/admin"
(cd "${TMP_DIR}" && tar -czf "${PKG_FILE}" .)

echo "==> 3/7 上传部署包到服务器"
sshpass -p "${SERVER_PASS}" scp -P "${SERVER_PORT}" -o StrictHostKeyChecking=no \
  "${PKG_FILE}" "${SERVER_USER}@${SERVER_HOST}:/root/vaultpi-deploy.tgz"

echo "==> 4/7 生成远程部署脚本"
cat > /tmp/vaultpi-remote-deploy.sh <<'EOF'
set -euo pipefail
APP_DIR="__APP_DIR__"

TS="$(date +%Y%m%d-%H%M%S)"
BK="${APP_DIR}/_backup/${TS}"
mkdir -p "${BK}"
cp -a "${APP_DIR}/public" "${BK}/public" || true
cp -a "${APP_DIR}/admin" "${BK}/admin" || true
cp -a "${APP_DIR}/vault-pi-backend-1.0.0-SNAPSHOT.jar" "${BK}/" || true

mkdir -p /root/vaultpi-deploy
rm -rf /root/vaultpi-deploy/*
tar -xzf /root/vaultpi-deploy.tgz -C /root/vaultpi-deploy

mkdir -p "${APP_DIR}/public" "${APP_DIR}/admin"
cp -a /root/vaultpi-deploy/public/. "${APP_DIR}/public/"
find "${APP_DIR}/admin" -mindepth 1 -maxdepth 1 ! -name .user.ini -exec rm -rf {} +
cp -a /root/vaultpi-deploy/admin/. "${APP_DIR}/admin/"
cp -f /root/vaultpi-deploy/vault-pi-backend-1.0.0-SNAPSHOT.jar "${APP_DIR}/"

# 若存在失败 Flyway 记录，先修复 success=0（避免 prod 启动卡死）
if [ -f "${APP_DIR}/config/env.prod" ]; then
  set -a
  . "${APP_DIR}/config/env.prod"
  set +a
fi
if [ -n "${SPRING_DATASOURCE_URL:-}" ] && [ -n "${SPRING_DATASOURCE_USERNAME:-}" ] && [ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]; then
  DB_HOST="$(echo "${SPRING_DATASOURCE_URL}" | sed -E 's#^jdbc:mysql://([^:/?]+).*$#\1#')"
  DB_PORT="$(echo "${SPRING_DATASOURCE_URL}" | sed -E 's#^jdbc:mysql://[^:/?]+:([0-9]+).*$#\1#')"
  DB_NAME="$(echo "${SPRING_DATASOURCE_URL}" | sed -E 's#^.*/([^/?]+).*$#\1#')"
  if [ -n "${DB_HOST}" ] && [ -n "${DB_PORT}" ] && [ -n "${DB_NAME}" ]; then
    mysql -h"${DB_HOST}" -P"${DB_PORT}" -u"${SPRING_DATASOURCE_USERNAME}" -p"${SPRING_DATASOURCE_PASSWORD}" -D "${DB_NAME}" \
      -e "UPDATE flyway_schema_v4_pro SET success=1 WHERE success=0;" >/dev/null 2>&1 || true
  fi
fi

OLD_PID="$(ss -ltnp | awk '/8081/ && /java/ {print $NF}' | sed -E 's/.*pid=([0-9]+).*/\1/' | head -n 1 || true)"
if [ -n "${OLD_PID}" ]; then
  kill "${OLD_PID}" || true
  sleep 2
fi

cd "${APP_DIR}"
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
echo "BACKUP_TS=${TS}"
echo "HEALTH:"
curl -sS http://127.0.0.1:8081/actuator/health || true
echo
EOF

sed -i '' "s#__APP_DIR__#${APP_DIR}#g" /tmp/vaultpi-remote-deploy.sh

echo "==> 5/7 上传并执行远程部署脚本"
sshpass -p "${SERVER_PASS}" scp -P "${SERVER_PORT}" -o StrictHostKeyChecking=no \
  /tmp/vaultpi-remote-deploy.sh "${SERVER_USER}@${SERVER_HOST}:${REMOTE_SCRIPT}"
sshpass -p "${SERVER_PASS}" ssh -p "${SERVER_PORT}" -T -o StrictHostKeyChecking=no \
  "${SERVER_USER}@${SERVER_HOST}" "bash ${REMOTE_SCRIPT}"

echo "==> 6/7 线上站点检查"
curl -sS -I https://vault314.com | sed -n '1,5p'
curl -sS -I https://admin.vault314.com | sed -n '1,5p'

echo "==> 7/7 完成"
echo "发布成功。可执行回滚脚本：scripts/rollback-server.sh <BACKUP_TS>"
