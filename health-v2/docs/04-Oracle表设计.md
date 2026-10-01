# 04 Oracle 表设计

## 一、原则与约定

- **人员卡编码 `CARD_CODE`（17 位）是人员唯一 ID**，所有表用它关联。
- 每份数据记着它依据哪个时间（`SRC_DATA_TIME`），更新时只接受时间更新的数据（规则见 `02`）。
- 厂家字段只读，人工录入的字段（目前只有年龄）单独存放。
- "没有数据"用 `NULL`，不用 `0` 或空串。
- 命名：表名列名全大写，下划线分隔；表名前缀 `SYS_`（系统）、`POS_`（定位）、`HEALTH_`（体征）、`ALERT_`（预警）。
- 字符串 `VARCHAR2(n CHAR)`（按字符计长度，中文不会被截断）；时间 `TIMESTAMP(0)`，日期 `DATE`；自增主键 `NUMBER GENERATED ALWAYS AS IDENTITY`。
- 避开保留字：本文用 `SEVERITY` 代替 `LEVEL`，用 `STAT_DATE` 代替 `DAY`。

## 二、总览

| 模块 | 表 | 说明 |
| --- | --- | --- |
| 系统 | `SYS_USER` | 登录账号 |
| | `SYS_OPERATION_LOG` | 操作日志（只增不改） |
| 定位 | `POS_INGEST_FILE` | 每个定位文件的处理记录 |
| | `POS_INGEST_ERROR` | 文件里解析失败的记录 |
| | `POS_AREA` | 区域 |
| | `POS_STATION` | 基站（编码、厂家名称、运行状态） |
| | `POS_STATION_MARK` | 基站在地图上的位置（人工摆放） |
| | `POS_PERSON` | 人员基本信息 |
| | `POS_PERSON_STATE` | 人员当前位置与出入井状态 |
| | `POS_PRESENCE_DAILY` | 每人每天的入井/出井记录 |
| | `POS_HEADCOUNT_SERIES` | 井下人数随时间变化（上线曲线） |
| 手表 | `DEVICE` | 手表，与人员绑定 |
| 体征 | `HEALTH_RECORD` | 体征流水（**月分区**） |
| | `HEALTH_LATEST` | 每人每指标的最新值 |
| | `HEALTH_DAILY_SUMMARY` | 每人每天每指标的统计 |
| | `STEP_DAILY` | 每人每天步数 |
| | `METRIC_COUNTER` | 累计采集条数 |
| 预警 | `JOB_GROUP` | 岗位类别 |
| | `JOB_KIND_GROUP` | 工种归到哪个岗位类别 |
| | `ALERT_RULE` | 各岗位类别的预警阈值 |
| | `ALERT_EVENT` | 预警事件 |
| 关注 | `WATCH_LIST` | 重点监护、今日关注名单 |

视图：`V_POS_IN_WELL`（当前在井下的人）。

## 三、系统表

### `SYS_USER`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `ID` | 自增主键 | |
| `USERNAME` | VARCHAR2(50 CHAR) 唯一 | 登录名 |
| `PASSWORD_HASH` | VARCHAR2(100 CHAR) | BCrypt 加密后的密码 |
| `DISPLAY_NAME` | VARCHAR2(50 CHAR) | |
| `STATUS` | NUMBER(1) 默认 1 | 1 启用，0 停用 |
| `CREATED_AT` `LAST_LOGIN_AT` | TIMESTAMP(0) | |

初始管理员在迁移脚本 `V002` 里创建：用户名 `admin`，密码 `admin`。脚本里存的是 `admin` 加密后的 BCrypt 哈希，不存明文。这是公开的开发期默认账号，登录页会明文提示（`05`），上线前修改密码（`08` 阶段 7）。

### `SYS_OPERATION_LOG`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `ID` | 自增主键 | |
| `USER_ID` `USERNAME` | | 谁（登录名冗余存一份，账号改名后日志仍可读） |
| `ACTION` | VARCHAR2(30 CHAR) | `STATION_PLACE` `STATION_MOVE` `STATION_RENAME` `STATION_DELETE` `PERSON_AGE_SET` `ALERT_RULE_SET` `JOB_GROUP_SET` `JOB_KIND_GROUP_SET` `WATCH_LIST_ADD` `WATCH_LIST_REMOVE` |
| `TARGET_TYPE` `TARGET_ID` | | 对象类型和编号，如 `STATION` + 基站编码 |
| `BEFORE_JSON` `AFTER_JSON` | CLOB | 改动前后（新增时前者为空，删除时后者为空） |
| `CREATED_AT` | TIMESTAMP(0) | 何时 |

只增不改，用**触发器**保证。

**为什么要保证**：日志记着"谁、何时、把哪个基站从哪挪到哪"。如果有人摆错了基站，想删掉日志掩盖，要让他删不掉、改不了。

**触发器**是写在数据库里的一段规则，数据库在指定时刻自动执行它，不需要程序调用。下面这段的意思是："有人要修改或删除操作日志之前，直接报错，让这次修改或删除作废"：

```sql
CREATE OR REPLACE TRIGGER TRG_OPLOG_IMMUTABLE   -- 创建触发器，名字叫 TRG_OPLOG_IMMUTABLE
BEFORE UPDATE OR DELETE ON SYS_OPERATION_LOG    -- 时刻：修改或删除操作日志之前
BEGIN
  RAISE_APPLICATION_ERROR(-20001, '操作日志不允许修改或删除');  -- 动作：报错
END;
```

效果（在本机 Oracle 上实测）：

```text
> DELETE FROM SYS_OPERATION_LOG WHERE ID = 5;
ORA-20001: 操作日志不允许修改或删除        ← 删除作废，第 5 条还在
> UPDATE SYS_OPERATION_LOG SET USERNAME = 'x';
ORA-20001: 操作日志不允许修改或删除        ← 修改作废
> INSERT INTO SYS_OPERATION_LOG ...;
1 row created.                             ← 新增照常
```

不管是程序还是有人直接连数据库敲命令，都一样被拦下。

门卫有两个漏洞：一次清空整张表（`TRUNCATE`）不经过它；表的所有者还能让它停用。所以分两个账号：

- 建表、迁移用所有者账号 `HEALTH_V2`；
- 上线时，后端改用一个单独的应用账号：对 `SYS_OPERATION_LOG` 只有 `INSERT` 和 `SELECT` 权限，对其他表只有增删改查权限，没有建表、清空表的权限（`08` 阶段 7）。

这样"不可改"对应用成立；对数据库管理员不成立，这是数据库本身的限制。

## 四、定位表

### `POS_INGEST_FILE`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `ID` | 自增主键 | |
| `FILE_NAME` | VARCHAR2(200 CHAR) | |
| `FILE_TYPE` `MINE_CODE` | | 如 `RYSS`；煤矿编码 |
| `HEADER_TIME` | TIMESTAMP(0) | 文件头里的"数据上传时间"（判断新旧的依据） |
| `SHA256` | CHAR(64) | 内容摘要，用来去重；没读取内容时（符号链接、文件名不合法）为空 |
| `SIZE_BYTES` | NUMBER | |
| `STATUS` | VARCHAR2(12 CHAR) | `DONE` `PARTIAL`（部分记录失败）`FAILED` `STALE`（过期未生效）`DUPLICATE` `UNKNOWN`（未识别类型） |
| `RECORD_COUNT` `ERROR_COUNT` | NUMBER | 成功、失败记录数 |
| `ERROR_MSG` | VARCHAR2(500 CHAR) | |
| `RECEIVED_AT` `PROCESSED_AT` | TIMESTAMP(0) | |
| `BACKUP_PATH` | VARCHAR2(500 CHAR) | |

索引（`FILE_NAME`, `SHA256`）用来查重复，不设唯一：重复的文件也要记一行 `DUPLICATE`；索引（`FILE_TYPE`, `STATUS`, `HEADER_TIME`）：`V_POS_IN_WELL` 每次都要找"最新一份生效的 `RYSS`"，带上状态列后只读索引就能取到，不用回表。

### `POS_INGEST_ERROR`

`ID`、`FILE_ID`（外键）、`KIND`（`ERROR` 记录被跳过 / `WARN` 记录已入库但要人工核对，如同一文件里卡编码重复）、`LINE_NO`（第几条记录，文件头之后从 1 数起）、`RAW_TEXT`（原文，超长截断到 1000 字，含姓名，仅供运维排查，页面不展示）、`REASON`。`POS_INGEST_FILE.ERROR_COUNT` 只数 `ERROR`。一个文件最多记 500 条。

### `POS_AREA`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `AREA_CODE` | VARCHAR2(16 CHAR) 主键 | |
| `AREA_TYPE` | VARCHAR2(10 CHAR) | 原值：`井口区域` / `重点区域` / `限制区域` / `其它区域` |
| `AREA_NAME` | VARCHAR2(100 CHAR) | 如"-750大巷" |
| `QUOTA` | NUMBER(6) | 核定人数 |
| `SRC_DATA_TIME` | TIMESTAMP(0) | |

### `POS_STATION`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `STATION_CODE` | VARCHAR2(22 CHAR) 主键 | |
| `AREA_CODE` | VARCHAR2(16 CHAR) **虚拟列** | `SUBSTR(STATION_CODE, 1, 16)`，数据库自动算。类型必须写 `CHAR` 长度，写 `VARCHAR2(16)` 会建表失败 |
| `STATION_NAME` | VARCHAR2(200 CHAR) | 厂家名称（来自 `RYJZ`），可为空 |
| `RUN_STATUS` | NUMBER(1) | 0 通讯正常、1 通讯中断、2 故障、9 未知 |
| `POWER_STATUS` | NUMBER(1) | 0 直流、1 交流、2 电源故障、9 未知 |
| `STATUS_TIME` | TIMESTAMP(0) | 状态的数据时间 |
| `NAME_SRC_TIME` `STATUS_SRC_TIME` | TIMESTAMP(0) | 名称和状态来自不同文件，各自记录文件时间 |

### `POS_STATION_MARK`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `STATION_CODE` | VARCHAR2(22 CHAR) 主键 | 不设外键（允许先摆放后收到文件） |
| `DISPLAY_NAME` | VARCHAR2(200 CHAR) | 用户起的名字，可为空 |
| `X` `Y` | NUMBER(15,3) | 地图坐标（EPSG:4527，见 `06`） |
| `PLACED_BY` `PLACED_AT` `UPDATED_BY` `UPDATED_AT` | | 首次摆放、最后修改的人（登录名）和时间 |
| `VERSION` | NUMBER 默认 0 | 乐观锁：两人同时改，后提交的收到冲突提示 |

基站显示名称优先级：`DISPLAY_NAME` > `STATION_NAME` > "区域名称 + 编码后 6 位"。

### `POS_PERSON`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `CARD_CODE` | VARCHAR2(17 CHAR) 主键 | |
| `PERSON_NAME` | VARCHAR2(50 CHAR) | 姓名（厂家，只读） |
| `JOB_KIND` `JOB_TITLE` | VARCHAR2(50 CHAR) | 工种、职务（厂家，只读；"未设置"存 `NULL`） |
| `DEPT` | VARCHAR2(100 CHAR) | 部门（厂家，只读，原样） |
| `IS_LEADER` `IS_SPECIAL` | NUMBER(1) | 是否矿领导、特种人员 |
| `AGE` | NUMBER(3) | **年龄，人工录入**，为空显示"未录入" |
| `AGE_UPDATED_BY` `AGE_UPDATED_AT` | | 谁、何时录入 |
| `FROM_RYXX` | NUMBER(1) | 1 来自 `RYXX`；0 只是从 `RYSS` 补录的（缺工种、部门） |
| `SRC_DATA_TIME` | TIMESTAMP(0) | 厂家字段依据的文件时间 |
| `CREATED_AT` `UPDATED_AT` | | |

索引：`DEPT`、`JOB_KIND`、`PERSON_NAME`（健康档案按它们筛选）。

### `POS_PERSON_STATE`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `CARD_CODE` | VARCHAR2(17 CHAR) 主键 | |
| `IN_OUT_FLAG` | NUMBER(1) | 0 井口、1 已入井、2 已出井 |
| `IN_TIME` `OUT_TIME` | TIMESTAMP(0) | 入井、出井时刻 |
| `AREA_CODE` `AREA_ENTER_TIME` | | 当前区域及进入时刻 |
| `STATION_CODE` `STATION_ENTER_TIME` | | 当前基站（离他最近的）及进入时刻 |
| `DISTANCE_M` | NUMBER(8,2) | 距基站距离，仅参考 |
| `SHIFT_MODE` | VARCHAR2(20 CHAR) | 如"三八制" |
| `WORK_STATUS` | VARCHAR2(10 CHAR) | `正常` / `求救` |
| `SRC_DATA_TIME` | TIMESTAMP(0) | 依据哪份 `RYSS`（那份文件的上传时间） |

索引：`SRC_DATA_TIME`（视图 `V_POS_IN_WELL` 按它筛选）、`STATION_CODE`。

### 视图 `V_POS_IN_WELL`

"最新一份 `RYSS` 里出入井标志为 1 的人"，全系统统一用它作为井下人数的口径：

```sql
CREATE OR REPLACE VIEW V_POS_IN_WELL AS
SELECT s.*
  FROM POS_PERSON_STATE s
 WHERE s.IN_OUT_FLAG = 1
   AND s.SRC_DATA_TIME = (SELECT MAX(HEADER_TIME) FROM POS_INGEST_FILE
                           WHERE FILE_TYPE = 'RYSS' AND STATUS IN ('DONE','PARTIAL'));
```

### `POS_PRESENCE_DAILY`

主键（`STAT_DATE`, `CARD_CODE`）；`FIRST_IN_TIME`（最早入井时刻）、`LAST_OUT_TIME`（最晚出井时刻）、`LAST_SEEN_TIME`（当天最后一次以"已入井"出现在快照里的快照时间）。用途：佩戴情况导出要知道"前一天下过井的人"。

怎么写入见 `02` 第四节"每日出入井"。**日期按快照算**：`STAT_DATE` 是 `RYSS` 文件头时间的日期。某人在 D 日的任何一份快照里出入井标志为 `1`，就在 D 日有一行。跨零点的夜班因此在两天各有一行，`FIRST_IN_TIME` 可能是前一天的时刻。

### `POS_HEADCOUNT_SERIES`

主键 `SNAPSHOT_TIME`（每份 `RYSS` 的上传时间）；`IN_WELL_COUNT`、`OUT_COUNT`、`TOTAL_COUNT`。按 `SNAPSHOT_TIME` `MERGE` 写入：厂家重发同一时刻的文件时覆盖，不报主键冲突。

## 五、手表与体征表

### `DEVICE`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `IMEI` | VARCHAR2(15 CHAR) 主键 | |
| `CARD_CODE` | VARCHAR2(17 CHAR) 唯一，可空，外键 → `POS_PERSON` | 一块表绑一个人，一个人绑一块表 |
| `MODEL` `BOUND_AT` | | 型号、绑定时间 |
| `LAST_SEEN_AT` | TIMESTAMP(0) | 最后一次收到任何上行包的时间，决定是否在线（`03` 第五节）；只接受更晚的时间 |
| `BATTERY_PCT` `BATTERY_TIME` | | 最新电量及其时间（仅来自心跳 `AP03`） |
| `STATUS` | NUMBER(1) 默认 1 | 1 启用，0 停用（停用的表按未登记处理，`03` 第二节） |

检查约束：`IMEI` 必须是 15 位数字；`BATTERY_PCT` 在 1–100（0 是手表错报，不写入）。

### `HEALTH_RECORD`（月分区）

数据量最大的表。一行 = 一个人在某时刻测到的一个指标。手表每次只传一个指标，一行放全部指标会有大量空值，还得先"拼行"，所以按"一行一指标"存。

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `CARD_CODE` | VARCHAR2(17 CHAR) | |
| `METRIC` | VARCHAR2(20 CHAR) | `HEART_RATE` `SPO2` `TEMPERATURE` `BLOOD_PRESSURE` |
| `VAL1` | NUMBER(6,1) | 主值；血压时是收缩压 |
| `VAL2` | NUMBER(6,1) | 仅血压：舒张压 |
| `COLLECTED_AT` | TIMESTAMP(0) | 采集时间（服务器收到的时间） |
| `DEVICE_IMEI` | VARCHAR2(15 CHAR) | |
| `MSG_ID` | NUMBER(19) | 收到时生成的唯一编号，用来防止重试时重复入库（`03` 第五节） |

```sql
CREATE TABLE HEALTH_RECORD (
  MSG_ID       NUMBER(19)        NOT NULL,
  CARD_CODE    VARCHAR2(17 CHAR) NOT NULL,
  METRIC       VARCHAR2(20 CHAR) NOT NULL,
  VAL1         NUMBER(6,1)       NOT NULL,
  VAL2         NUMBER(6,1),
  COLLECTED_AT TIMESTAMP(0)      NOT NULL,
  DEVICE_IMEI  VARCHAR2(15 CHAR)
)
PARTITION BY RANGE (COLLECTED_AT) INTERVAL (NUMTOYMINTERVAL(1, 'MONTH'))
(PARTITION P_BEFORE VALUES LESS THAN (TIMESTAMP '2026-01-01 00:00:00'));

CREATE INDEX IX_HR_CARD_METRIC_TIME
  ON HEALTH_RECORD (CARD_CODE, METRIC, COLLECTED_AT) LOCAL;

CREATE INDEX IX_HR_TIME
  ON HEALTH_RECORD (COLLECTED_AT) LOCAL;

CREATE UNIQUE INDEX UX_HR_MSG
  ON HEALTH_RECORD (MSG_ID, COLLECTED_AT) LOCAL;
```

`INTERVAL` 让 Oracle 在新的一个月有数据写入时自动建分区；`LOCAL` 索引跟着分区走，删分区时索引一起没。

- `IX_HR_CARD_METRIC_TIME`：查某人某指标的趋势、某人某天有没有数据。
- `IX_HR_TIME`：日汇总每 5 分钟只读"今天"的数据，不用扫整个月分区。
- `UX_HR_MSG`：同一条数据写两次时，第二次被跳过。分区表上的唯一本地索引必须包含分区列，所以带上 `COLLECTED_AT`。写入用 `MERGE`（只插入不存在的），返回的条数就是真正新插入的条数。

**数据量**：约 200 块表、每分钟 1 条 → 每天约 29 万条、每月约 860 万条、每年约 1 亿条，加三个索引每年约 15 GB。

**保留 12 个月**，更早的按月 `DROP PARTITION`。月报用的是日汇总表，删明细不影响历史报告。

**版本**：Oracle 标准版（SE2）没有分区功能。开发用的本机 Free 版支持；给矿上部署时选哪个版本到时再定。不含分区时，改成单表加夜间批量删除旧数据（一亿行规模仍可运行，只是清理更慢），只影响这张表的建表语句和清理任务。

### `HEALTH_LATEST`

主键（`CARD_CODE`, `METRIC`），`VAL1`、`VAL2`、`COLLECTED_AT`、`MSG_ID`、`DEVICE_IMEI`。每人每指标一行，写库时 `MERGE`（只接受时间更新的；采集时间同一秒时 `MSG_ID` 大的算新，所以要存 `MSG_ID`）。大屏、地图弹窗直接查它，不扫流水表。

### `HEALTH_DAILY_SUMMARY`

主键（`STAT_DATE`, `CARD_CODE`, `METRIC`）。

| 列 | 说明 |
| --- | --- |
| `METRIC` | `HEART_RATE` `SPO2` `TEMPERATURE` `BP_SYS` `BP_DIA` |
| `MAX_V` `MIN_V` `AVG_V` | 当天最大、最小、平均 |
| `SAMPLE_COUNT` | 当天有效样本数 |
| `NORMAL_COUNT` | 落在正常范围内的样本数（按这个人所属岗位类别在统计当时的阈值） |
| `RULE_GROUP` | 算 `NORMAL_COUNT` 时用的岗位类别 |

另有 `UPDATED_AT`（这一行最近一次重算的时间）。`AVG_V` 是 NUMBER(7,2)，保留两位小数。

定时任务每 5 分钟按 `IX_HR_TIME` 重算"今天"，每天 0:10 把"昨天"重算定稿（`health` 包的 `HealthDailySummaryJob`，阶段 2 实现）。每次整天重算、`MERGE` 写入，重复执行结果不变。月报和大部分统计都基于这张表。几天合并时，平均值按 `SAMPLE_COUNT` 加权。

算 `NORMAL_COUNT`：每人每项指标用哪行阈值，规则和实时预警一样（`03` 第四节：这个人工种所属类别配了这一项就用它的，没配用 `DEFAULT` 的）；值在 [`LOW_LIMIT`, `HIGH_LIMIT`] 之内算正常，为空的一侧不限；阈值停用（`ENABLED = 0`）时全部算正常。血压拆成 `BP_SYS`、`BP_DIA` 两项分别统计。

耗时（`10` 第 17 项，本机 Docker 里的 Oracle Free 实测，见 `03` 第九节）：重算一天所需时间和当天的流水条数大致成正比，约每千条 1.2 毫秒；1000 块表一天约 180 万条，重算约 2.2 秒，远小于 5 分钟的间隔。

### `STEP_DAILY`

主键（`STAT_DATE`, `CARD_CODE`）；`STEPS`（当天累计步数，算法见 `03`）、`LAST_RAW`（最近一次手表计数器读数）、`UPDATED_AT`（这次读数的收到时间，`TIMESTAMP(3)` 带毫秒：几次读数可能在同一秒内到达，要分得出先后）、`DEVICE_IMEI`（`LAST_RAW` 来自哪块表：Redis 丢了状态或换绑时从这张表取起点，只有同一块表的读数才能接着算增量，`03` 第五节）。写入是覆盖，不是累加；只接受 `UPDATED_AT` 更晚的数据。

### `METRIC_COUNTER`

主键 `METRIC`（四项体征各一行）；`TOTAL_COUNT`。每批写库时，在同一个事务里加上本批**真正新插入**的条数（重复的不算），大屏"累计采集数据"取四行之和。

## 六、预警与关注

### `JOB_GROUP`

主键 `GROUP_CODE`（VARCHAR2(20 CHAR)）；`GROUP_NAME`（如"采掘"）、`SORT_NO`。建表时只有一行 `DEFAULT`（默认），不能删除：触发器 `TRG_JOB_GROUP_KEEP_DEFAULT` 在删除 `DEFAULT` 前报错（`ORA-20002`）。

月报里按岗位不同的参数也放这里（可空，空则用 `DEFAULT` 的值；规则见 `07`）：

| 列 | 说明 | `DEFAULT` 初始值 |
| --- | --- | --- |
| `MIN_EVAL_DAYS` | 一个月里某指标有数据的天数少于它，不参与稳定性评估 | 5 |
| `RISK_EVENT_COUNT` | 一个月里六类告警事件达到它，列为风险职工 | 3 |

### `JOB_KIND_GROUP`

主键 `JOB_KIND`（VARCHAR2(50 CHAR)，和 `POS_PERSON.JOB_KIND` 的原值一致）；`GROUP_CODE`（外键 → `JOB_GROUP`）、`UPDATED_BY`、`UPDATED_AT`。表里没有的工种都算 `DEFAULT`。厂家文件里出现新工种时不会自动归类，归类前按 `DEFAULT` 判断；未归类的工种清单由接口提供，后台管理里显示（`08` 阶段 8）。

### `ALERT_RULE`

主键（`GROUP_CODE`, `METRIC`），`GROUP_CODE` 外键 → `JOB_GROUP`；`METRIC` 取 `HEART_RATE` `SPO2` `TEMPERATURE` `BP_SYS` `BP_DIA`；`LOW_LIMIT`、`HIGH_LIMIT`（可空，空则不判断；都有值时必须下限 < 上限）、`SEVERITY`（1 提示 / 2 一般 / 3 严重，默认 2）、`ENABLED`（1 启用 / 0 停用，默认 1）、`STABLE_PCT`、`UNSTABLE_PCT`（月报稳定性的两条分界线，`DEFAULT` 初始 90 和 70，见 `07`）、`UPDATED_AT`、`UPDATED_BY`。`DEFAULT` 类别五项指标都必须有；其他类别缺哪一行就用 `DEFAULT` 的那一行，某一行里 `STABLE_PCT`、`UNSTABLE_PCT` 为空也用 `DEFAULT` 的。查找顺序和初始值见 `03` 第四节。

### `ALERT_EVENT`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `ID` | 自增主键 | |
| `CARD_CODE` | VARCHAR2(17 CHAR) | 可空：手表未登记或未绑定时为空，此时按 `DEVICE_IMEI` 区分（`03` 第二节） |
| `SRC` | VARCHAR2(10 CHAR) | `THRESHOLD` 体征越界 / `DEVICE` 设备报警 |
| `CODE` | VARCHAR2(20 CHAR) | `HR_HIGH` `HR_LOW` `SPO2_LOW` `TEMP_HIGH` `TEMP_LOW` `BP_SYS_HIGH` `BP_SYS_LOW` `BP_DIA_HIGH` `BP_DIA_LOW` `SOS` `FALL` `LOW_BATTERY` … |
| `CATEGORY` | VARCHAR2(20 CHAR) | 大屏六类：`SOS` `FALL` `HEART_RATE` `BLOOD_PRESSURE` `SPO2` `TEMPERATURE`；其余 `OTHER` |
| `SEVERITY` | NUMBER(1) | 1 提示 / 2 一般 / 3 严重（取值见 `03` 第一节、第四节） |
| `VAL_TEXT` | VARCHAR2(50 CHAR) | 触发时的值，如 `132` 或 `152/98`；设备报警记的是手表的报警代码（如 `01`），页面不显示 |
| `RULE_GROUP` | VARCHAR2(20 CHAR) | 判断体征越界时用的岗位类别；设备报警为空 |
| `OCCURRED_AT` `LAST_OCCURRED_AT` | TIMESTAMP(0) | 第一次、最近一次发生（去重期间更新后者；两者一定在同一天，规则见 `03` 第四节） |
| `OCCUR_COUNT` | NUMBER(6) 默认 1 | 去重期间重复的次数 |
| `DEVICE_IMEI` | VARCHAR2(15 CHAR) | |

索引：`OCCURRED_AT`、（`CARD_CODE`, `OCCURRED_AT`）、（`CATEGORY`, `OCCURRED_AT`）。

检查约束：卡编码和设备号至少有一个；`OCCURRED_AT` 和 `LAST_OCCURRED_AT` 在同一天且后者不早于前者（事件不跨天）。

### `WATCH_LIST`

`ID`、`CARD_CODE`、`LIST_TYPE`（`KEY` 重点监护 / `TODAY` 今日关注）、`NOTE`、`EXPIRE_DATE`（`TODAY` 类型当天有效）、`ADDED_BY`、`ADDED_AT`。唯一（`CARD_CODE`, `LIST_TYPE`）。加入时按这两列 `MERGE`：已有（包括已过期的"今日关注"）就覆盖备注、有效期、加入人和时间，所以第二天可以再次加入同一个人。名单只靠人工加入和移除（`05` 第八节），加入、移除都写操作日志。

## 七、迁移脚本

**迁移脚本**就是建表、改表的 SQL 文件，放在 `backend/src/main/resources/db/migration/`（目录结构见 `01` 第六节）。这些文件在开发到对应阶段时才写：照本文前面各表的列写成 `CREATE TABLE` 语句。本文只给表结构，不放完整脚本。

文件名的意思：

```text
V001__sys.sql
│ │    └── 说明，随便起，"sys" 表示和系统有关
│ └─────── 序号 1（补 0 只为排序整齐）
└───────── V：Flyway 规定的开头字母
```

所以 `V001` 就是"第 1 号脚本"，`V002` 就是"第 2 号脚本"。

**Flyway** 是执行这些脚本的工具。每次运行，它先查数据库里的一张记录表（它自己建的），看哪几号已经执行过，然后把没执行过的按号码从小到大执行，执行完记下来：

| 时间 | 目录里的脚本 | Flyway 执行 | 执行后数据库里有 |
| --- | --- | --- | --- |
| 阶段 0 | 1、2 号 | 1、2 号 | 账号表、操作日志表、一个管理员账号 |
| 阶段 1 | 多了 3 号 | 只执行 3 号 | 再多出定位的表 |
| 阶段 2 | 多了 4、5 号 | 只执行 4、5 号 | 再多出手表、体征、预警的表（已执行） |

到矿上部署时运行一次，它把 1 到 5 号按顺序全部执行，数据库就和开发时一样，不用人记先建哪张表。

| 脚本 | 内容 | 在哪个阶段执行 |
| --- | --- | --- |
| `V001__sys.sql` | `SYS_USER`、`SYS_OPERATION_LOG`（含触发器） | 0 |
| `V002__seed_admin.sql` | 初始管理员 | 0 |
| `V003__positioning.sql` | 全部 `POS_*` 表、视图 | 1 |
| `V004__device_health.sql` | `DEVICE`、`HEALTH_*`、`STEP_DAILY`、`METRIC_COUNTER` | 2 |
| `V005__alert_watch.sql` | `JOB_GROUP`、`JOB_KIND_GROUP`、`ALERT_*`、`WATCH_LIST`、`DEFAULT` 类别和它的初始阈值 | 2 |

两条规矩（Flyway 自己的规定，见它的官方文档 `documentation.red-gate.com/flyway`；阶段 0 第一次执行时实际验证）：

- **编号只能往后加。**已经执行过 5 号，又往目录里放一个 2 号，Flyway 会认为"2 号早该执行过，记录里却没有，出错了"，报错停下。所以编号按开发阶段的先后排，上表每个阶段只执行自己那几个。
- **执行过的脚本不能再改。**Flyway 记着每个脚本的内容摘要，改一个字就报错。要改表，就新写下一个编号的脚本。
