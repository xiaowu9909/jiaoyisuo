#!/bin/bash
# 使用 JDK 17 启动后端（本机若默认是 JDK 8 会编译失败时可使用）
# ARM Mac 推荐：brew install openjdk@17，路径为 /opt/homebrew/opt/openjdk@17
JAVA17_HOME=""
for candidate in "/opt/homebrew/opt/openjdk@17" \
  "/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home" \
  "/usr/lib/jvm/java-17-openjdk" \
  "$JAVA_HOME"; do
  if [ -n "$candidate" ] && [ -x "$candidate/bin/javac" ]; then
    v=$("$candidate/bin/java" -version 2>&1)
    if echo "$v" | grep -q 'version "17'; then
      JAVA17_HOME="$candidate"
      break
    fi
  fi
done
if [ -z "$JAVA17_HOME" ]; then
  echo "未找到 JDK 17。ARM Mac 可执行: brew install openjdk@17"
  echo "当前 Java: $(java -version 2>&1)"
  exit 1
fi
export JAVA_HOME="$JAVA17_HOME"
cd "$(dirname "$0")"
# 无 MySQL 时可用 dev 配置（H2 内存库）: ./run-with-jdk17.sh -Dspring-boot.run.profiles=dev
exec mvn spring-boot:run "$@"
