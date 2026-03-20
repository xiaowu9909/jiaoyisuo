#!/bin/bash
# Vault π 一键启动：一个终端窗口内同时跑 后端(8081) + C端(5174) + B端(5173)
# 双击运行或在终端执行，三个进程输出带前缀 [后端] [C端] [B端]
# 按 Ctrl+C 会同时结束三个进程

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 启动前先杀死占用端口的旧进程，避免“端口已被使用”
kill_port() {
    local port=$1
    local pids
    pids=$(lsof -ti :"$port" 2>/dev/null)
    if [ -n "$pids" ]; then
        echo "  端口 $port 被占用，正在结束进程: $pids"
        echo "$pids" | xargs kill -9 2>/dev/null || true
        sleep 1
    fi
}
echo "========== 启动前清理旧进程 (8081 后端 / 5173 B端 / 5174 C端) =========="
kill_port 8081
kill_port 5173
kill_port 5174
sleep 1
echo "旧进程已清理，即将启动…"
echo ""

# 开发环境后端依赖 Redis：未运行时自动在后台启动
REDIS_STARTED_BY_US=
if command -v redis-cli &>/dev/null; then
    if redis-cli ping 2>/dev/null | grep -q PONG; then
        echo "Redis 已在运行"
    else
        if command -v redis-server &>/dev/null; then
            echo "正在启动 Redis…"
            redis-server --daemonize yes 2>/dev/null || redis-server &
            REDIS_STARTED_BY_US=1
            for _ in 1 2 3 4 5 6 7 8 9 10; do
                sleep 1
                redis-cli ping 2>/dev/null | grep -q PONG && break
            done
            if redis-cli ping 2>/dev/null | grep -q PONG; then
                echo "Redis 已启动"
            else
                echo "警告: Redis 未能就绪，后端接口可能 500"
            fi
        else
            echo "提示: 未找到 redis-server，可执行: brew install redis"
        fi
    fi
else
    echo "提示: 未找到 redis-cli。若未安装 Redis，可执行: brew install redis"
fi
echo ""

# JDK 17
if [ -d "/opt/homebrew/opt/openjdk@17" ] && [ -x "/opt/homebrew/opt/openjdk@17/bin/java" ]; then
    JAVA17_HOME="/opt/homebrew/opt/openjdk@17"
elif [ "$(uname -m)" = "arm64" ] && [ -d "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ]; then
    JAVA17_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
else
    JAVA17_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
fi

if [ -z "$JAVA17_HOME" ]; then
    echo "未检测到 JDK 17，后端将无法启动。可执行: brew install openjdk@17"
    echo "是否仍启动 C 端与 B 端？(y/n)"
    read -r answer 2>/dev/null || answer=n
    case "$answer" in
        [yY]|[yY][eE][sS]) ;;
        *) echo "已取消。" ; exit 0 ;;
    esac
fi

# Ctrl+C 时结束本脚本启动的所有子进程（若本次启动了 Redis 则一并关闭）
trap 'echo ""; echo "正在结束后端、C端、B端…"; kill $(jobs -p) 2>/dev/null; [ -n "$REDIS_STARTED_BY_US" ] && redis-cli shutdown 2>/dev/null; exit' INT TERM

echo "Vault π 单窗口启动"
echo "  后端 http://localhost:8081 | C 端 http://localhost:5174 | B 端 http://localhost:5173/admin"
echo "  按 Ctrl+C 结束全部"
echo ""

export JAVA_HOME="${JAVA17_HOME:-}"

# 三个进程在后台跑，输出加前缀便于区分
(
  if [ -n "$JAVA17_HOME" ]; then
    export PATH="$JAVA17_HOME/bin:$PATH"
    cd "$SCRIPT_DIR/backend" && mvn spring-boot:run -Dspring-boot.run.profiles=dev 2>&1
  else
    echo "[后端] 未配置 JDK 17，跳过"
    sleep 99999
  fi
) | sed 's/^/[后端] /' &

(
  cd "$SCRIPT_DIR/frontend" && npm run dev 2>&1
) | sed 's/^/[C端] /' &

(
  cd "$SCRIPT_DIR/admin" && npm run dev 2>&1
) | sed 's/^/[B端] /' &

wait
