# Vault π 后端

- **JDK 17**（必须，Spring Boot 3.x）；**MySQL 8.0**（必须 8.0，不可用 5.7）；**Redis**。
- 若系统默认是 Java 8，请先安装 JDK 17，并设置 `JAVA_HOME` 再编译运行。

## 启动方式

```bash
cd backend
# 若默认已是 JDK 17：
mvn clean spring-boot:run

# 若需指定 JDK 17（示例，路径按本机实际修改）：
export JAVA_HOME=/path/to/jdk17
mvn clean spring-boot:run
```

- 端口默认 **8081**。
- 出现「接口不存在」或 No static resource 时，多为后端未用最新代码重启，请先停止旧进程再执行上述命令。

## 改代码后全自动重启（推荐）

一条命令即可：**保存代码 → 自动编译 → DevTools 自动重启**，无需任何手动操作。

```bash
cd backend
npm install          # 首次需要，安装监听与并发脚本
npm run dev          # 启动后端 + 监听 src 变更，改完保存即自动重启
```

- 终端会同时显示「监听」与「Spring Boot」两路日志（蓝/绿前缀）。
- 修改任意 `src/**/*.java` 或 `src/main/resources/**` 并保存后，会自动执行 `mvn compile`，DevTools 检测到 classpath 变化后会自动重启后端。
- 退出：在该终端按一次 `Ctrl+C` 即可。

若不想用 Node，也可沿用：先 `mvn spring-boot:run`，改完代码后另开终端执行 `mvn compile`，同样会触发 DevTools 自动重启。
