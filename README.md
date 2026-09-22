# Health 智慧健康管理平台

`health` 是面向矿区职工健康与安全管理的 monorepo，包含 Vue 3 管理端、Spring Boot 后端、ESP32-C3 手表原型和设备模拟器。系统覆盖实时体征监测、风险预警、事件处置、职工健康画像、设备管理和 AI 辅助分析。

## 项目结构

```text
health/
├── HealthShow/                    Vue 3 + Vite 前端
├── HealthData/                    Spring Boot 后端
├── firmware/esp32c3-wifi-watch/  ESP32-C3 手表原型
└── tools/                         启动、探针和运行辅助脚本
```

主要技术栈：

- 前端：Vue 3、Vite 5、Vue Router、Vuex、Element Plus、ECharts
- 后端：Java 21、Spring Boot 3.5、MyBatis-Plus、Sa-Token、Netty
- 后端交付：Maven 构建可执行 Spring Boot JAR，无需外部 Tomcat
- 基础设施：SQL Server、Redis

## 运行依赖

- Java 21
- Maven 3.8+
- Node.js 18+ 与 npm
- Python 3
- Redis
- SQL Server 2022（当前项目使用老库 `health`）

项目当前以 macOS 原生运行为准，SQL Server 由 Docker 容器提供。

## 快速启动

### macOS

先准备名为 `local-mssqlserver2022` 的 SQL Server Docker 容器，然后在不同终端中执行：

```bash
tools/run-redis-mac.sh
tools/run-backend-mac.sh
tools/run-frontend-mac.sh
```

数据库连接建议通过环境变量配置：

```bash
export DB_HOST=127.0.0.1
export DB_PORT=1433
export DB_USERNAME=sa
export DB_PASSWORD='<your-local-password>'
```

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

当前 checkout 的 HTTP 业务、手表协议链路和模拟器共用一个数据库连接池。本地启动脚本通过 `DB_NAME=health` 固定连接老库，用于模拟器、演示数据和非空数据回归。

后端不再提供请求级数据库切换机制。客户端发送的 `X-Health-Data-Source` 或历史 `Health-Data-Source` Cookie 都不会参与数据库选择。

## 协作约定

根目录 [AGENTS.md](AGENTS.md) 是本仓库唯一的详细协作与运行事实源。修改代码前请先阅读其中的数据源路由、认证、手表协议和 Git 操作约定；代码、配置和实际运行结果优先于文档。

本仓库只有一个 Git 根目录，`HealthShow` 和 `HealthData` 都不是独立仓库。新手长期教学和 agent 交接状态统一记录在 [docs/项目新手教学交接状态.md](docs/项目新手教学交接状态.md)，不要再新增其他分散的教学文档。除 `README.md` 和 `AGENTS.md` 外，新增或重命名的 Markdown 文件使用中文文件名；所有 Markdown 尽量使用普通中文、短句和清楚的例子，技术词第一次出现时要解释。

## 新手接手学习手册

如果需要更换 AI agent 或在很久之后继续学习，请先阅读 [项目新手教学计划与 AI 接手状态](docs/项目新手教学交接状态.md)。该文件记录当前阶段、已验证的能力、下一课和每轮教学记录；不要只依赖聊天上下文。

这一节面向刚开始学习 Vue、Java 和数据库的接手者。目标不是把所有文件背下来，而是逐步建立一张可以用来排查问题的地图：知道请求从哪里进入、经过哪些层、数据存在哪里，以及出错时应该去哪里找证据。

### 1. 先记住整体结构

把系统先想成两条流水线：

```text
浏览器 Vue 页面
    -> Vite 开发代理
    -> Spring Boot HTTP 接口（8080）
    -> Controller
    -> Service
    -> Mapper / SQL
    -> SQL Server

智能手表
    -> Netty TCP 接入（9000）
    -> 协议解码
    -> 手表消息处理
    -> Redis 缓冲
    -> 健康记录月表 / 预警记录月表
```

四个目录先这样理解：

- `HealthShow`：管理员在浏览器中看到的 Vue 3 前端。
- `HealthData`：提供 HTTP 接口、数据库访问、认证、预警和手表接入的 Spring Boot 后端。
- `tools`：本地启动、协议探针、模拟器和诊断脚本。
- `firmware/esp32c3-wifi-watch`：ESP32-C3 手表原型，不是理解管理端业务的第一入口。

当前 checkout 有明确的数据库边界：业务、模拟器和手表链路共用启动时配置的唯一数据库。不要在前端增加“切换数据库”的选择器，也不要通过请求头或 Cookie 改变数据库。

### 2. 接手前先建立基线

这个仓库可能带有上一位开发者尚未提交的改动。第一次接手时先在根目录执行：

```bash
git status --short
git branch --show-current
git log -5 --oneline
```

先把现有修改记录下来，确认哪些是当前版本的一部分。不要为了“让目录干净”直接执行 `git reset --hard`、`git checkout -- .` 或批量删除文件。代码、配置和实际运行结果优先于旧文档中的描述。

建议同时确认本机工具版本：

```bash
java -version
mvn -version
node -v
npm -v
python3 --version
docker ps
```

这一阶段的完成标准是：你能说出本项目的前端、后端、数据库、Redis、HTTP 端口和 TCP 端口分别是什么。

### 3. 第一次运行：先观察，再读代码

准备好 Java 21、Maven、Node/npm、Redis 和 SQL Server 后，在 macOS 上分别打开三个终端：

```bash
tools/run-redis-mac.sh
tools/run-backend-mac.sh
tools/run-frontend-mac.sh
```

浏览器访问 `http://localhost:9528/`，后端健康检查使用：

```bash
curl http://127.0.0.1:8080/health/actuator/health
curl -I http://127.0.0.1:9528/
```

健康检查要看顶层 `status` 是否为 `UP`。登录后打开浏览器开发者工具的 Network 面板，观察以下请求：

1. `/auth/login`：登录并取得 Token。
2. `/auth/info`：取得当前用户、角色和权限码。
3. `/heart-rate/overview`：读取一个真实业务页面的数据。

前端开发时请求通常从 `/dev-api` 开始，Vite 会把它代理到后端的 `/health` 上。例如浏览器请求 `/dev-api/heart-rate/overview`，实际后端路径是 `/health/heart-rate/overview`。

### 4. 第一阶段：阅读前端入口和登录流程

按下面顺序阅读，不要先钻进复杂图表和样式：

1. `HealthShow/src/main.ts`：Vue 应用如何创建和挂载。
2. `HealthShow/src/router/index.js`：路由总入口和首页重定向。
3. `HealthShow/src/router/app-routes.mjs`：业务模块和页面路径。
4. `HealthShow/src/permission.js`：登录守卫和权限恢复。
5. `HealthShow/src/store/modules/user.js`：Token、用户信息和菜单权限。
6. `HealthShow/src/utils/request.ts`：Axios 请求、Token 请求头和错误处理。
7. `HealthShow/src/views/login/index.vue`：登录页面如何调用 Store。
8. `HealthShow/src/api/user.js`：登录接口的前端封装。
9. `HealthData/src/main/java/com/xzkj/health/controller/AuthController.java`：后端登录、用户信息和退出接口。

需要弄懂的词：

- `route`：URL 和页面组件的对应关系。
- `store`：多个页面共享的前端状态。
- `Token`：证明当前用户已登录的凭证。
- `interceptor`：每次请求或响应都会经过的统一处理函数。
- `permCode`：菜单和按钮权限码。

阶段完成标准：你可以解释“刷新页面后，前端如何知道用户是否还登录”，也可以解释“为什么接口返回 401 后会回到登录页”。

### 5. 第二阶段：完整读懂一个普通业务功能

建议从“心率分析”开始。它同时包含统计、趋势、分页和实时异常名单，但仍然能按一条主线拆开。

阅读顺序：

1. `HealthShow/src/views/health-monitor/heart-rate/index.vue`
2. `HealthShow/src/views/health-monitor/heart-rate/use-heart-rate-page.ts`
3. `HealthShow/src/api/heart-rate.js`
4. `HealthData/src/main/java/com/xzkj/health/controller/HeartRateController.java`
5. `HealthData/src/main/java/com/xzkj/health/service/impl/HeartRateServiceImpl.java`
6. `HealthData/src/main/java/com/xzkj/health/mapper/HeartRateMapper.java`
7. 相关 DTO、`HealthRecord` 模型和 SQL 查询

先只跟踪一个接口：

```text
页面加载
    -> getHeartRateOverview(startDate, endDate)
    -> GET /heart-rate/overview
    -> HeartRateController
    -> HeartRateServiceImpl
    -> HeartRateMapper
    -> health_record_YYYYMM
    -> Result.data
    -> 页面 KPI 和图表
```

每读一层，都回答这六个问题：

1. 输入参数是什么？
2. 返回值是什么？
3. 谁调用了它？
4. 它调用了谁？
5. 数据实际来自哪里？
6. 出错时如何处理？

不要一开始试图理解页面中的每个 `computed`、动画和 ECharts 配置。先理解“请求、数据、显示”三件事。

阶段完成标准：你可以根据浏览器 Network 面板的一条请求，找到对应的 Vue 调用、Java Controller、Service、Mapper 和数据库查询。

### 6. 第三阶段：理解后端的通用分层

后端文件可以按职责这样看：

- `controller`：接收 HTTP 参数、做基础校验、返回统一响应。
- `service`：组合业务规则、权限、缓存和多个 Mapper 查询。
- `mapper`：执行 MyBatis 或 MyBatis-Plus 查询。
- `model/entity`：对应数据库记录或业务对象。
- `dto`：对外接口的输入和输出结构。
- `config`：数据库、认证、线程池、Netty 和框架配置。
- `scheduler`：定时建月表、刷新汇总等后台任务。

建议补读这些基础文件：

1. `HealthData/src/main/java/com/xzkj/health/HealthApplication.java`
2. `HealthData/src/main/java/com/xzkj/health/common/Result.java`
3. `HealthData/src/main/resources/application.yml`
4. `HealthData/src/main/java/com/xzkj/health/config/MybatisPlusConfig.java`
5. `HealthData/src/main/java/com/xzkj/health/config/SaTokenConfig.java`
6. `HealthData/src/main/java/com/xzkj/health/config/AsyncExecutorConfig.java`

重点理解统一返回格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

其中 `code=200` 是业务成功，不要把它和 HTTP 状态码混为一谈。

### 7. 第四阶段：理解数据库和月表

业务健康记录和预警记录按月份分表，例如：

```text
health_record_202601
warning_record_202601
```

建议按下面顺序学习：

1. `HealthData/src/main/java/com/xzkj/health/model/HealthRecord.java`
2. `HealthData/src/main/java/com/xzkj/health/mapper/HealthRecordMapper.java`
3. `HealthData/src/main/java/com/xzkj/health/util/TableNameUtil.java`
4. `HealthData/src/main/java/com/xzkj/health/util/TableSourceUtil.java`
5. `HealthData/src/main/java/com/xzkj/health/scheduler/MonthlyTableScheduler.java`
6. `HealthData/src/main/resources/sql/monthly_partition_sql.sql`
7. `HealthData/src/main/resources/sql/dashboard_daily_summary.sql`

动态表名只能来自已经校验过的月份表工具，不能把用户输入直接拼到 SQL 中。跨月查询也不能只查当前月份，否则会漏数据。

阶段完成标准：你可以解释某个日期范围会查哪些月表，也能判断一个统计接口是在查原始记录、日汇总表还是缓存。

### 8. 第五阶段：理解 Redis 和异步写入

手表数据不是每收到一条就同步写数据库。建议阅读：

1. `HealthData/src/main/java/com/xzkj/health/service/RedisHealthBufferService.java`
2. `HealthData/src/main/java/com/xzkj/health/service/DeviceDataBufferService.java`
3. `HealthData/src/main/java/com/xzkj/health/service/watch/WatchDataPersistenceService.java`
4. `HealthData/src/main/java/com/xzkj/health/config/AsyncExecutorConfig.java`

理解这条链：

```text
收到体征
    -> 解析并找到设备/员工绑定
    -> 写入 Redis 缓冲或设备缓冲表
    -> 定时批量刷入健康记录月表
    -> 独立执行阈值预警判断
```

Redis 在这里既是缓冲区，也用于实时快照和在线状态。看到 `@Async`、`@Scheduled`、线程池和重试逻辑时，要特别慢下来读，因为这些代码不是普通的同步函数调用。

### 9. 第六阶段：理解手表 TCP 链路

这部分放到普通 HTTP 业务之后。阅读顺序：

1. `HealthData/src/main/java/com/xzkj/health/protocol/WatchMessage.java`
2. `HealthData/src/main/java/com/xzkj/health/protocol/WatchProtocolDecoder.java`
3. `HealthData/src/main/java/com/xzkj/health/handler/WatchDataHandler.java`
4. `HealthData/src/main/java/com/xzkj/health/handler/watch/`
5. `HealthData/src/main/java/com/xzkj/health/service/watch/WatchDeviceContextService.java`
6. `HealthData/src/main/java/com/xzkj/health/service/watch/WatchDataPersistenceService.java`
7. `tools/watch_tcp_simulator_1000.py`

先只追踪一个健康数据包：

```text
TCP 字节流
    -> WatchProtocolDecoder
    -> WatchMessage
    -> WatchDataHandler
    -> WatchHealthProtocolHandler
    -> WatchDeviceContextService
    -> WatchDataPersistenceService
    -> Redis / 健康记录 / 预警
```

先认识这些协议类别即可：

- `AP00`：设备登录。
- `AP03`：设备状态和电量。
- `AP49`、`AP50`、`APHT`、`APHP`：健康数据。
- `AP10`：设备主动报警。
- `BPxx`：服务端发给手表的命令。

不要一开始把所有协议号都背下来，先理解“解码、分发、处理、落库”四步。

### 10. 第七阶段：理解预警和事件处置

预警相关代码建议按这个顺序：

1. `WatchHealthWarningService`
2. `WatchBehaviorAlertService`
3. `RiskWarningService`
4. `TrendWarningService`
5. `CommandCenterIncidentService`
6. `HealthData/src/main/java/com/xzkj/health/controller/CommandCenterController.java`
7. `HealthShow/src/api/command-center.js`
8. `HealthShow/src/views/safety-command/index.vue`

项目中至少要分清三类来源：

- `HEALTH_THRESHOLD`：体征超过阈值。
- `DEVICE_ALARM`：手表主动上报，例如 SOS。
- `TREND_WARNING`：趋势预测产生的预警。

高危不等于 SOS。已读、已确认、已处理、已关闭也不是同一个状态。事件定位必须使用 `warningId + occurredAt`，不能只用裸 `warningId`。

### 12. 推荐的四周学习计划

#### 第 1 周：地图和登录

- 看完 README、AGENTS、package.json、pom.xml、application.yml。
- 成功启动前端和后端。
- 能登录、退出、刷新页面。
- 能解释 Token、路由守卫和 Vite 代理。

#### 第 2 周：一个完整页面

- 完整读懂心率分析或实时监控。
- 用浏览器 Network 面板追踪一个接口。
- 找到 Controller、Service、Mapper 和数据库表。

#### 第 3 周：数据库、Redis 和异步

- 读懂健康记录模型和月表工具。
- 理解 Redis 缓冲、实时快照和定时刷库。
- 能判断问题属于页面、HTTP、Service、SQL 还是缓存。

#### 第 4 周：手表和预警

- 用模拟器理解一条手表数据如何进入系统。
- 读懂健康阈值预警和设备报警的区别。
- 读懂指挥中心事件确认、分派和处理流程。

### 13. 每次阅读代码时的记录模板

可以在自己的笔记中为每个功能记录：

```text
功能名称：
页面入口：
前端 API：
后端接口：
核心 Service：
核心 Mapper：
数据表 / Redis key：
输入参数：
返回字段：
异常情况：
是否异步 / 定时：
```

不要只记录“这个文件做什么”，还要记录“它为什么这样做”。例如实时监控的在线窗口、新鲜度、按人去重和分页口径，都是业务规则，不只是界面显示。

### 14. 初期可以暂时跳过的内容

以下内容不是不重要，而是不适合作为第一入口：

- AI 对话和报告生成。
- 3D 人体和 Three.js 展示。
- 睡眠分析。
- SCTP 生产链路。
- 历史版本目录和遗留页面。
- 所有 Mapper 和所有 DTO 的逐文件通读。

先掌握一条完整链路，再把同类功能进行横向比较，学习速度会快很多。
