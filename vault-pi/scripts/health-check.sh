#!/usr/bin/env bash
set -euo pipefail

SERVER_HOST="${SERVER_HOST:-107.175.124.214}"
SERVER_PORT="${SERVER_PORT:-22}"
SERVER_USER="${SERVER_USER:-root}"
SERVER_PASS="${SERVER_PASS:-88r0ggf5YL0TKmRbL7}"

echo "== 本地外网检查 =="
curl -sS -I https://vault314.com | sed -n '1,8p'
curl -sS -I https://admin.vault314.com | sed -n '1,8p'

echo
echo "== 服务器内网检查 =="
sshpass -p "${SERVER_PASS}" ssh -p "${SERVER_PORT}" -T -o StrictHostKeyChecking=no \
  "${SERVER_USER}@${SERVER_HOST}" \
  "ss -ltnp | awk '/8081/ {print}'; curl -sS http://127.0.0.1:8081/actuator/health || true; echo; curl -sS 'http://127.0.0.1:8081/api/market/symbol-thumb-one?symbol=ETH-USDT' | sed -n '1,3p'"
