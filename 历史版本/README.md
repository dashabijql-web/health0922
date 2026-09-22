# 历史版本归档

这个目录只保存已退出运行时的旧界面源码，不参与前端构建、路由注册或后端运行。

## 已归档前端页面

- `前端/HealthShow/src/views/safety-command/legacy-20260601`：安全指挥中心 V1。
- `前端/HealthShow/src/views/safety-command/components`：仅被安全指挥中心 V1 使用的组件。
- `前端/HealthShow/src/views/safety-command/safety-command-interactions.js`：V1 页面的旧弹窗、部门 AI 和全局动作交互层。
- `前端/HealthShow/src/views/health-monitor/dashboard/legacy-20260601`：统一管控 V1。
- `前端/HealthShow/src/views/health-monitor/dashboard/dashboard-pre-redesign-20260720.scss`：主源码目录中遗留的旧样式备份。
- `前端/HealthShow/src/views/health-monitor/miner-portrait-showcase`：矿工专属静态画像。
- `前端/HealthShow/src/views/personnel-management/health-portrait`：早期人员健康画像。
- `数据库/HealthData/src/main/resources/sql/create_health_new_seed.sql`：基于 Windows SQL Server 2019 路径的旧版新库种子生成脚本。
- `展示原型/unity展示专用`：与 Vue/Spring Boot/ESP32 主构建链都无引用关系的独立 Unity 展示原型。归档仅保留源资产和项目配置，不保留可重建的 `Library`、`Builds`、日志和 IDE 生成文件。

旧画像 URL 仍由当前路由做隐藏重定向，查询参数会传递给当前权威的 `employee-profile`。

## 未归档的当前基础设施

以下内容虽包含历史兼容语义，但仍被当前 `health-old` 运行时使用，因此保留在主工程：

- 月分表、`v_health_record` / `v_warning_record` 视图及其迁移脚本。
- 日汇总预聚合表及刷新过程。
- 老库无结构化事件分类的查询兼容。
- 老版 Redis 快照的逐指标时间补齐。

这些不是可独立移走的“旧版备份”，而是当前老库数据链路的组成部分。
