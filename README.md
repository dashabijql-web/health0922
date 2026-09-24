# Health 智慧健康管理平台

`health` 是面向矿区职工健康与安全管理的 monorepo，包含 Vue 3 管理端、Spring Boot 后端和设备模拟器。系统覆盖实时体征监测、风险预警、事件处置、职工健康画像、设备管理和 AI 辅助分析。

## 项目结构

```text
health/
├── HealthShow/                    Vue 3 + Vite 前端
├── HealthData/                    Spring Boot 后端
└── tools/                         手表模拟器、抓包与协议探针脚本、数据库备份
```

主要技术栈：

- 前端：Vue 3、Vite 5、Vue Router、Vuex、Element Plus、ECharts
- 后端：Java 21、Spring Boot 3.5、MyBatis-Plus、Sa-Token、Netty
- 后端交付：Maven 构建可执行 Spring Boot JAR，无需外部 Tomcat
- 基础设施：SQL Server、Redis

## 运行依赖

- Java 21
- Maven 3.8+
- Node.js 18+ 与 pnpm（前端锁文件是 `pnpm-lock.yaml`）
- Python 3
- Redis
- SQL Server 2022（当前项目使用老库 `health`）

项目当前以 macOS 原生运行为准，SQL Server 由 Docker 容器提供。

## 快速启动

### macOS

仓库目前没有一键启动脚本，需要按顺序手动启动：

1. SQL Server：准备名为 `local-mssqlserver2022` 的 Docker 容器，映射到 `1433`。
2. Redis：本机启动，监听 `6379`。
3. 后端：在 `HealthData/` 用 Maven 运行，或在 IDE 里运行 `HealthApplication`。
4. 前端：在 `HealthShow/` 运行 `pnpm dev`。
5. 需要演示数据时，再运行 `tools/watch_tcp_simulator_1000.py`（模拟 1000 块手表，启动前确认没有其他实例）。

数据库连接建议通过环境变量配置：

```bash
export DB_HOST=127.0.0.1
export DB_PORT=1433
export DB_USERNAME=sa
export DB_PASSWORD='<your-local-password>'
```

用 IDE 启动时，也可以激活 `local` profile，它会读取 `HealthData/src/main/resources/application-local.yml`（已加入 `.gitignore`，不会提交）。

## 服务地址

| 服务 | 默认地址 |
| --- | --- |
| 前端 | http://localhost:9528/ |
| 后端 API | http://localhost:8080/health |
| 健康检查 | http://localhost:8080/health/actuator/health |
| Actuator 指标 | http://localhost:8080/health/actuator/metrics |
| 手表 TCP | 127.0.0.1:9000 |
| Redis | 127.0.0.1:6379 |
| SQL Server | 127.0.0.1:1433 |

本地默认登录账号为 `admin / admin123`。生产环境必须使用独立凭证。

启动后至少确认健康检查返回顶层 `"status":"UP"`：

```bash
curl http://127.0.0.1:8080/health/actuator/health
curl -I http://127.0.0.1:9528/
```

## 数据库边界

当前 checkout 的 HTTP 业务、手表协议链路和模拟器共用一个数据库连接池。`application.yml` 里 `DB_NAME` 默认就是 `health`，所以不设置环境变量时固定连接老库，用于模拟器、演示数据和非空数据回归。

后端不再提供请求级数据库切换机制。客户端发送的 `X-Health-Data-Source` 或历史 `Health-Data-Source` Cookie 都不会参与数据库选择。

## 协作约定

根目录 [AGENTS.md](AGENTS.md) 是本仓库唯一的详细协作与运行事实源。修改代码前请先阅读其中的数据源路由、认证、手表协议和 Git 操作约定；代码、配置和实际运行结果优先于文档。

本仓库只有一个 Git 根目录，`HealthShow` 和 `HealthData` 都不是独立仓库。除 `README.md` 和 `AGENTS.md` 外，新增或重命名的 Markdown 文件使用中文文件名；所有 Markdown 尽量使用普通中文、短句和清楚的例子，技术词第一次出现时要解释。

