#!/usr/bin/env bash
# Vault π 一键同步部署包
# 用法：在 vault-pi 目录下执行 ./sync-deploy.sh
# 或在任意处执行：/path/to/vault-pi/sync-deploy.sh
# 改过代码后运行本脚本，会将 后端 JAR、C 端/B 端构建产物、config 同步到 ../部署包

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# 部署包默认在 vault-pi 同级目录下的 部署包
DEPLOY_DIR="${DEPLOY_DIR:-${SCRIPT_DIR}/../部署包}"

echo "========== Vault π 同步部署包 =========="
echo "  项目目录: $SCRIPT_DIR"
echo "  部署目录: $DEPLOY_DIR"
echo ""

if [ ! -d "$DEPLOY_DIR" ]; then
  echo "错误: 部署目录不存在: $DEPLOY_DIR"
  echo "请先创建该目录，或设置环境变量 DEPLOY_DIR 指向部署包路径"
  exit 1
fi

# 1. 后端 JAR
echo "[1/5] 构建后端 JAR..."
(cd "$SCRIPT_DIR/backend" && mvn package -DskipTests -q)
cp "$SCRIPT_DIR/backend/target/vault-pi-backend-1.0.0-SNAPSHOT.jar" "$DEPLOY_DIR/"
echo "      → JAR 已复制"

# 2. C 端前端
echo "[2/5] 构建 C 端 (frontend)..."
(cd "$SCRIPT_DIR/frontend" && npm run build -q)
rm -rf "$DEPLOY_DIR/public/assets" "$DEPLOY_DIR/public/images" 2>/dev/null || true
cp -R "$SCRIPT_DIR/frontend/dist/"* "$DEPLOY_DIR/public/"
echo "      → public/ 已更新"

# 3. B 端管理后台
echo "[3/5] 构建 B 端 (admin)..."
(cd "$SCRIPT_DIR/admin" && npm run build -q)
rm -rf "$DEPLOY_DIR/admin"
mkdir -p "$DEPLOY_DIR/admin"
cp -R "$SCRIPT_DIR/admin/dist/"* "$DEPLOY_DIR/admin/"
echo "      → admin/ 已更新"

# 4. 配置
echo "[4/5] 同步 config..."
cp "$SCRIPT_DIR/backend/src/main/resources/application.yml" "$DEPLOY_DIR/config/application.yml"
cp "$SCRIPT_DIR/backend/src/main/resources/application-prod.yml" "$DEPLOY_DIR/config/application-prod.yml"
cp "$SCRIPT_DIR/backend/src/main/resources/application-dev.yml" "$DEPLOY_DIR/config/application-dev.yml"
echo "      → application*.yml 已更新"

# 5. 记录同步时间
echo "[5/5] 记录同步时间..."
echo "最后同步: $(date '+%Y-%m-%d %H:%M') (sync-deploy.sh)" > "$DEPLOY_DIR/最后同步.txt"

echo ""
echo "========== 同步完成 =========="
echo "  部署包已更新，可直接上传服务器。"
echo ""
