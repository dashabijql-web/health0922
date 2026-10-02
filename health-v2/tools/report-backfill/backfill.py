#!/usr/bin/env python3
"""历史数据回填工具（docs/08 阶段 6）：给测试库造一个已结束月份的数据，用来测试月度汇总。

手表数据的采集时间就是服务器收到的时间，没法通过 TCP 补上个月的数据，所以这里直接写汇总表：
  HEALTH_DAILY_SUMMARY（每人每天每指标）、STEP_DAILY（每人每天步数）、ALERT_EVENT（预警事件）；
定位数据仍按规则生成带过去时间的 RYSS 放进测试后端的收件箱（调用定位文件生成器的 backfill），
由后端照常解析，写出每日出入井 POS_PRESENCE_DAILY。

只能写测试库，有三道保护：
  1. 只用 .env.local 里的测试用户 ORACLE_TEST_USER（必须以 _TEST 结尾、不能和业务用户 ORACLE_USER 相同），
     从来不读业务用户的密码；
  2. 每次执行 SQL，第一句先在数据库里检查当前用户名以 _TEST 结尾，不是就整段回滚退出；
  3. 定位文件只能放进 --inbox 指定的目录，拒绝业务收件箱 runtime/inbox（以及 .env.local 里配的 POSITIONING_INBOX_DIR）。
只能造已经结束的月份（当前月的数据由后端每 5 分钟重算，不能覆盖）。同一个月再跑一次会先删掉那个月的汇总再重写。

人员取测试库里绑定了启用手表的人（先用定位文件生成器 base 和手表模拟器 bind --test 准备好）。
数据是固定种子的伪随机数，同一个月每次造的都一样：大部分人正常，一部分人有时偏高偏低，少数人很少戴表（不够评估），
几个人告警较多；另外有两条没绑定手表的设备事件。

用法（在 health-v2/ 下运行）：
  python3 tools/report-backfill/backfill.py --month 2026-09 --inbox runtime/s6/inbox
  python3 tools/report-backfill/backfill.py --month 2026-08 --only positioning --inbox runtime/s6/inbox
      只补定位（这个月月报里只有第 3 页有数据，其余页"暂无数据"）
  python3 tools/report-backfill/backfill.py --demo-groups
      另外建两个岗位类别做演示：重体力（稳定线 80、不稳定线 60、最少 4 天、风险 5 条，心率上限 130），
      辅助（只建类别和阈值行，月报参数都不配，用默认的）
"""

import argparse
import os
import random
import subprocess
import sys
from datetime import date, datetime, timedelta
from pathlib import Path

HV2_ROOT = Path(__file__).resolve().parents[2]
POSITIONING_SIM = HV2_ROOT / "tools" / "positioning-sim" / "sim.py"
BUSINESS_INBOX = HV2_ROOT / "runtime" / "inbox"
ORACLE_CONTAINER = "local-oracle-free"
SEED = 20261002

# 阈值（和 V005 的 DEFAULT 一样；重体力类别心率上限 130）
DEFAULT_LIMITS = {"HEART_RATE": (50, 120), "SPO2": (90, None), "TEMPERATURE": (35.0, 37.3),
                  "BP_SYS": (90, 140), "BP_DIA": (60, 90)}
HEAVY_HR_HIGH = 130
DEMO_GROUPS = {
    "HEAVY": ("重体力", 1, 4, 5, 80, 60, ["采煤机司机", "支架工", "掘进机司机"]),
    "AUX": ("辅助", 2, None, None, None, None, ["信号工", "安全员"]),
}
# 体征越界事件：指标 → (偏高代码, 偏低代码, 大屏分类)
EVENT_CODES = {"HEART_RATE": ("HR_HIGH", "HR_LOW", "HEART_RATE"), "SPO2": (None, "SPO2_LOW", "SPO2"),
               "TEMPERATURE": ("TEMP_HIGH", "TEMP_LOW", "TEMPERATURE"),
               "BP_SYS": ("BP_SYS_HIGH", "BP_SYS_LOW", "BLOOD_PRESSURE")}


# ---------------------------------------------------------------- 连接（只连测试用户）

def read_env():
    env = {}
    path = HV2_ROOT / ".env.local"
    if not path.exists():
        sys.exit(f"缺少 {path}")
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip()
    env.update({k: v for k, v in os.environ.items() if k.startswith(("ORACLE_", "POSITIONING_"))})
    return env


def test_user(env):
    user = env.get("ORACLE_TEST_USER", "")
    if not user or not env.get("ORACLE_TEST_PASSWORD"):
        sys.exit("没有配置测试用户 ORACLE_TEST_USER / ORACLE_TEST_PASSWORD")
    if not user.upper().endswith("_TEST"):
        sys.exit(f"测试用户名必须以 _TEST 结尾，拒绝写 {user}")
    if user.upper() == env.get("ORACLE_USER", "").upper():
        sys.exit("测试用户和业务用户是同一个，拒绝执行")
    return user


def run_sql(env, sql):
    """在 docker 里用 sqlplus 以测试用户执行。第一句在库里再检查一次用户名，不是测试用户就回滚退出。"""
    user = test_user(env)
    script = (f'CONNECT {user}/"{env["ORACLE_TEST_PASSWORD"]}"@//localhost:1521/{env.get("ORACLE_SERVICE", "FREEPDB1")}\n'
              "WHENEVER SQLERROR EXIT FAILURE ROLLBACK\n"
              "SET FEEDBACK OFF HEADING OFF PAGESIZE 0 LINESIZE 400 TRIMSPOOL ON\n"
              "ALTER SESSION SET TIME_ZONE = 'Asia/Shanghai';\n"
              "BEGIN\n"
              "  IF SYS_CONTEXT('USERENV', 'SESSION_USER') NOT LIKE '%\\_TEST' ESCAPE '\\' THEN\n"
              "    RAISE_APPLICATION_ERROR(-20099, '回填工具只能写测试用户');\n"
              "  END IF;\n"
              "END;\n/\n"
              f"{sql}\nCOMMIT;\nEXIT\n")
    r = subprocess.run(["docker", "exec", "-i", ORACLE_CONTAINER, "sqlplus", "-s", "/nolog"],
                       input=script, capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("执行 SQL 失败：\n" + r.stdout[-3000:] + r.stderr[-2000:])
    return r.stdout


# ---------------------------------------------------------------- 准备

def check_inbox(env, inbox):
    target = Path(inbox).resolve()
    forbidden = {BUSINESS_INBOX.resolve()}
    if env.get("POSITIONING_INBOX_DIR"):
        forbidden.add(Path(env["POSITIONING_INBOX_DIR"]).resolve())
    if target in forbidden:
        sys.exit(f"拒绝往业务收件箱 {target} 补历史数据")
    return target


def month_range(text):
    try:
        first = datetime.strptime(text + "-01", "%Y-%m-%d").date()
    except ValueError:
        sys.exit("--month 写成 yyyy-MM，如 2026-09")
    nxt = (first.replace(day=28) + timedelta(days=4)).replace(day=1)
    if nxt > date.today().replace(day=1):
        sys.exit("只能造已经结束的月份（当前月的数据由后端每 5 分钟重算）")
    return first, nxt


def people(env):
    out = run_sql(env, "SELECT d.CARD_CODE || '|' || d.IMEI || '|' || NVL(k.GROUP_CODE, 'DEFAULT')\n"
                       "  FROM DEVICE d JOIN POS_PERSON p ON p.CARD_CODE = d.CARD_CODE\n"
                       "  LEFT JOIN JOB_KIND_GROUP k ON k.JOB_KIND = p.JOB_KIND\n"
                       " WHERE d.STATUS = 1 ORDER BY d.CARD_CODE;")
    rows = [line.strip().split("|") for line in out.splitlines() if line.count("|") == 2]
    if not rows:
        sys.exit("测试用户里还没有绑定手表的人：先用定位文件生成器 base（放进测试后端的收件箱）、"
                 "再用手表模拟器 bind --test")
    return rows


def demo_groups(env):
    lines = []
    for code, (name, sort, days, risk, stable, unstable, kinds) in DEMO_GROUPS.items():
        n = lambda v: "NULL" if v is None else str(v)  # noqa: E731
        lines.append(f"""MERGE INTO JOB_GROUP g USING (SELECT '{code}' C FROM DUAL) s ON (g.GROUP_CODE = s.C)
WHEN MATCHED THEN UPDATE SET g.GROUP_NAME = '{name}', g.SORT_NO = {sort}, g.MIN_EVAL_DAYS = {n(days)},
     g.RISK_EVENT_COUNT = {n(risk)}
WHEN NOT MATCHED THEN INSERT (GROUP_CODE, GROUP_NAME, SORT_NO, MIN_EVAL_DAYS, RISK_EVENT_COUNT)
     VALUES ('{code}', '{name}', {sort}, {n(days)}, {n(risk)});""")
        for metric in ("HEART_RATE", "TEMPERATURE", "SPO2"):
            high = f"{HEAVY_HR_HIGH}" if code == "HEAVY" and metric == "HEART_RATE" else "d.HIGH_LIMIT"
            lines.append(f"""MERGE INTO ALERT_RULE r
USING (SELECT '{code}' G, d.METRIC, d.LOW_LIMIT, {high} HIGH_LIMIT FROM ALERT_RULE d
        WHERE d.GROUP_CODE = 'DEFAULT' AND d.METRIC = '{metric}') s
ON (r.GROUP_CODE = s.G AND r.METRIC = s.METRIC)
WHEN MATCHED THEN UPDATE SET r.LOW_LIMIT = s.LOW_LIMIT, r.HIGH_LIMIT = s.HIGH_LIMIT,
     r.STABLE_PCT = {n(stable)}, r.UNSTABLE_PCT = {n(unstable)}, r.UPDATED_BY = 'backfill'
WHEN NOT MATCHED THEN INSERT (GROUP_CODE, METRIC, LOW_LIMIT, HIGH_LIMIT, STABLE_PCT, UNSTABLE_PCT, UPDATED_BY)
     VALUES (s.G, s.METRIC, s.LOW_LIMIT, s.HIGH_LIMIT, {n(stable)}, {n(unstable)}, 'backfill');""")
        for kind in kinds:
            lines.append(f"""MERGE INTO JOB_KIND_GROUP k USING (SELECT '{kind}' K FROM DUAL) s ON (k.JOB_KIND = s.K)
WHEN MATCHED THEN UPDATE SET k.GROUP_CODE = '{code}', k.UPDATED_BY = 'backfill', k.UPDATED_AT = LOCALTIMESTAMP(0)
WHEN NOT MATCHED THEN INSERT (JOB_KIND, GROUP_CODE, UPDATED_BY) VALUES ('{kind}', '{code}', 'backfill');""")
    run_sql(env, "\n".join(lines))
    print("演示岗位类别：重体力（采煤机司机、支架工、掘进机司机）、辅助（信号工、安全员）")


# ---------------------------------------------------------------- 造数据

def d(day):
    return f"DATE '{day.isoformat()}'"


def ts(t):
    return f"TIMESTAMP '{t.strftime('%Y-%m-%d %H:%M:%S')}'"


def day_values(rnd, metric, samples, abnormal, group, low_side):
    """一天的 (最大, 最小, 平均)：有异常样本时最大值越过上限（或最小值越过下限），和 NORMAL_COUNT 对得上。"""
    lo, hi = DEFAULT_LIMITS[metric]
    if metric == "HEART_RATE" and group == "HEAVY":
        hi = HEAVY_HR_HIGH
    base = {"HEART_RATE": (62, 100), "SPO2": (94, 99), "TEMPERATURE": (36.1, 36.9),
            "BP_SYS": (105, 132), "BP_DIA": (66, 85)}[metric]
    mn, mx = sorted((rnd.uniform(*base), rnd.uniform(*base)))
    if abnormal:
        if (low_side or hi is None) and lo is not None:
            mn = lo - rnd.uniform(1, 8 if metric != "TEMPERATURE" else 0.8)
        else:
            mx = hi + rnd.uniform(1, 30 if metric != "TEMPERATURE" else 0.9)
    avg = (mn + mx) / 2 + rnd.uniform(-1, 1) * (mx - mn) / 6
    one = 1 if metric == "TEMPERATURE" else 0
    return round(mx, one), round(mn, one), round(avg, 2)


def build(rows, first, nxt):
    rnd = random.Random(f"{SEED}-{first.isoformat()}")
    summary, steps, events = [], [], []
    days = [first + timedelta(days=i) for i in range((nxt - first).days)]
    for no, (card, imei, group) in enumerate(rows):
        pr = random.Random(f"{SEED}-{first.isoformat()}-{card}")
        # 戴表习惯：大部分人九成的日子戴；每 12 个人里有一个很少戴（不够评估）
        wear = 0.12 if no % 12 == 7 else pr.uniform(0.75, 0.97)
        # 某项指标某天"有异常"的概率和那天异常样本的比例：六成多的人很少异常，四分之一有时异常，一成经常异常
        kind = pr.random()
        odd_day, odd_part = ((0.015, (0.05, 0.3)) if kind < 0.65 else (0.12, (0.3, 0.9)) if kind < 0.9
                             else (0.5, (0.4, 0.9)))
        walk = pr.choice([(1500, 5000), (6000, 16000), (6000, 16000), (15000, 28000)])
        for day in days:
            if pr.random() > wear:
                continue
            for metric in ("HEART_RATE", "SPO2", "TEMPERATURE", "BP_SYS", "BP_DIA"):
                samples = pr.randint(60, 240)
                abnormal = round(samples * pr.uniform(*odd_part)) if pr.random() < odd_day else 0
                low_side = pr.random() < 0.3
                mx, mn, avg = day_values(pr, metric, samples, abnormal, group, low_side)
                summary.append(f"SELECT {d(day)}, '{card}', '{metric}', {mx}, {mn}, {avg}, {samples}, "
                               f"{samples - abnormal}, '{group}' FROM DUAL")
                code = EVENT_CODES.get(metric)
                if abnormal and code:
                    high_code, low_code, category = code
                    c = low_code if (low_side or high_code is None) else high_code
                    for _ in range(2 if pr.random() < 0.3 else 1):
                        at = datetime.combine(day, datetime.min.time()) + timedelta(minutes=pr.randint(7 * 60, 20 * 60))
                        val = mn if c == low_code else mx
                        events.append((card, imei, "THRESHOLD", c, category, 2, str(val), group, at,
                                       at + timedelta(minutes=pr.randint(0, 40)), pr.randint(1, 6)))
            steps.append(f"SELECT {d(day)}, '{card}', {pr.randint(*walk)}, {pr.randint(0, 65535)}, "
                         f"{ts(datetime.combine(day, datetime.min.time()) + timedelta(hours=22))}, '{imei}' FROM DUAL")
            # 设备报警：偶尔 SOS、跌倒；低电、脱落是设备事件（月报不统计）
            for code, category, p, sev in (("SOS", "SOS", 0.004, 3), ("FALL", "FALL", 0.006, 3),
                                           ("LOW_BATTERY", "OTHER", 0.05, 1), ("WEAR_OFF", "OTHER", 0.03, 1)):
                if pr.random() < p:
                    at = datetime.combine(day, datetime.min.time()) + timedelta(minutes=pr.randint(6 * 60, 21 * 60))
                    events.append((card, imei, "DEVICE", code, category, sev, "01", None, at, at, 1))
    # 没绑定手表的两条（没有卡编码，按设备号区分）
    for code, category, i in (("SOS", "SOS", 3), ("HR_HIGH", "HEART_RATE", 9)):
        at = datetime.combine(days[min(i, len(days) - 1)], datetime.min.time()) + timedelta(hours=10)
        src = "DEVICE" if category == "SOS" else "THRESHOLD"
        events.append((None, "869900000000199", src, code, category, 3 if src == "DEVICE" else 2,
                       "01" if src == "DEVICE" else "135", None if src == "DEVICE" else "DEFAULT", at, at, 1))
    rnd.shuffle(events)
    return summary, steps, events


def event_select(e):
    card, imei, src, code, category, sev, val, group, first, last, count = e
    q = lambda v: "NULL" if v is None else f"'{v}'"  # noqa: E731
    return (f"SELECT {q(card)}, '{src}', '{code}', '{category}', {sev}, {q(val)}, {q(group)}, {ts(first)}, "
            f"{ts(last)}, {count}, '{imei}' FROM DUAL")


def chunks(selects, head, size=300):
    for i in range(0, len(selects), size):
        yield head + "\n" + "\nUNION ALL ".join(selects[i:i + size]) + ";"


def write_watch_data(env, rows, first, nxt):
    summary, steps, events = build(rows, first, nxt)
    sql = [f"DELETE FROM HEALTH_DAILY_SUMMARY WHERE STAT_DATE >= {d(first)} AND STAT_DATE < {d(nxt)};",
           f"DELETE FROM STEP_DAILY WHERE STAT_DATE >= {d(first)} AND STAT_DATE < {d(nxt)};",
           f"DELETE FROM ALERT_EVENT WHERE OCCURRED_AT >= CAST({d(first)} AS TIMESTAMP) "
           f"AND OCCURRED_AT < CAST({d(nxt)} AS TIMESTAMP);"]
    sql += chunks(summary, "INSERT INTO HEALTH_DAILY_SUMMARY (STAT_DATE, CARD_CODE, METRIC, MAX_V, MIN_V, AVG_V, "
                           "SAMPLE_COUNT, NORMAL_COUNT, RULE_GROUP)")
    sql += chunks(steps, "INSERT INTO STEP_DAILY (STAT_DATE, CARD_CODE, STEPS, LAST_RAW, UPDATED_AT, DEVICE_IMEI)")
    sql += chunks([event_select(e) for e in events],
                  "INSERT INTO ALERT_EVENT (CARD_CODE, SRC, CODE, CATEGORY, SEVERITY, VAL_TEXT, RULE_GROUP, "
                  "OCCURRED_AT, LAST_OCCURRED_AT, OCCUR_COUNT, DEVICE_IMEI)")
    run_sql(env, "\n".join(sql))
    print(f"{first:%Y-%m}：{len(rows)} 人，日汇总 {len(summary)} 行、步数 {len(steps)} 行、预警事件 {len(events)} 条")


def write_positioning(args, first, nxt):
    cmd = [sys.executable, str(POSITIONING_SIM), "--inbox", str(args.inbox), "--people", str(args.people),
           "backfill", "--from", first.isoformat(), "--to", nxt.isoformat(), "--interval", str(args.interval)]
    subprocess.run(cmd, check=True)


def main():
    ap = argparse.ArgumentParser(description="历史数据回填（只写测试库），给月度汇总造一个已结束月份的数据")
    ap.add_argument("--month", help="要造的月份 yyyy-MM（必须已经结束）")
    ap.add_argument("--inbox", help="测试后端的收件箱（不能是业务收件箱 runtime/inbox）")
    ap.add_argument("--only", choices=["watch", "positioning"], help="只造手表汇总或只补定位，默认两样都造")
    ap.add_argument("--people", type=int, default=120, help="定位文件生成器的假人数量，默认 120")
    ap.add_argument("--interval", type=int, default=3600, help="补 RYSS 的间隔秒数，默认 3600")
    ap.add_argument("--demo-groups", action="store_true", help="建两个演示用的岗位类别（重体力、辅助）")
    args = ap.parse_args()
    env = read_env()
    test_user(env)
    if args.demo_groups:
        demo_groups(env)
    if not args.month:
        if not args.demo_groups:
            ap.error("要么给 --month，要么给 --demo-groups")
        return
    first, nxt = month_range(args.month)
    if args.only != "watch":
        if not args.inbox:
            ap.error("补定位数据要用 --inbox 指定测试后端的收件箱（或者 --only watch）")
        args.inbox = check_inbox(env, args.inbox)
    if args.only != "positioning":
        write_watch_data(env, people(env), first, nxt)
    if args.only != "watch":
        write_positioning(args, first, nxt)


if __name__ == "__main__":
    main()
