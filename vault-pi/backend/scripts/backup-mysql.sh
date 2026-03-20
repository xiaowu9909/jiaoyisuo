#!/usr/bin/env bash
# 数据库每日备份脚本（建议 cron 每日执行，保留 7 天）
# 用法：BACKUP_DIR=/path/to/backups MYSQL_* 环境变量已设置时可直接执行
# 或：export SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=xxx SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/vaultpi?...'
# 从 SPRING_DATASOURCE_URL 解析 host/port/db 或使用下面变量

set -e
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
# 从 JDBC URL 解析或直接设置
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DB:-vaultpi}"
MYSQL_USER="${SPRING_DATASOURCE_USERNAME:-root}"
MYSQL_PASS="${SPRING_DATASOURCE_PASSWORD}"

if [ -z "$MYSQL_PASS" ]; then
  echo "Error: SPRING_DATASOURCE_PASSWORD or MYSQL_PASS not set" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
DATE=$(date +%Y%m%d_%H%M%S)
FILE="$BACKUP_DIR/vaultpi_${DATE}.sql.gz"

mysqldump -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" \
  --single-transaction --routines --triggers "$MYSQL_DB" | gzip -c > "$FILE"
echo "Backup written: $FILE"

# 保留最近 N 天
find "$BACKUP_DIR" -name 'vaultpi_*.sql.gz' -mtime +$RETENTION_DAYS -delete 2>/dev/null || true
