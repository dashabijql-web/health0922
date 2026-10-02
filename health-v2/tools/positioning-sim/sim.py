#!/usr/bin/env python3
"""定位文件生成器（docs/02 第六节）。

按国家煤监局 42 号文附件2 的格式造假数据文件，放进收件箱，和厂家的真实文件走同一条路：
后端每 30 秒扫描收件箱、解析入库。这里不直接写数据库。

假数据是固定的一套（同一个随机种子，每次生成都一样）：假人、区域、基站。
卡编码是 <煤矿编码><9 开头的 5 位数>，和厂家真实卡编码（0、1 开头）不会重复。
阶段 2 的手表模拟器用 `people --json` 拿同一批人来绑定手表。

用法（在 health-v2/ 下运行）：
  python3 tools/positioning-sim/sim.py base          生成区域 RYQY、基站 RYJZ、人员 RYXX
  python3 tools/positioning-sim/sim.py tick          生成一份实时快照 RYSS 和基站状态 JZSS
  python3 tools/positioning-sim/sim.py run           先 base，再每 5 分钟 tick 一次（--interval 改间隔）
  python3 tools/positioning-sim/sim.py bad <类型>     故意造异常文件，类型见 bad -h
  python3 tools/positioning-sim/sim.py people --json 输出假人名单
  python3 tools/positioning-sim/sim.py --inbox <测试收件箱> backfill --from 2026-09-01 --to 2026-10-01
                                                     按过去的时间补一段 RYSS（月报测试数据，只能放进测试用的收件箱）
  python3 tools/positioning-sim/sim.py reset         清掉模拟状态（谁在井下、在哪）
"""

import argparse
import copy
import json
import os
import random
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path

HV2_ROOT = Path(__file__).resolve().parents[2]
RUNTIME = HV2_ROOT / "runtime"
STATE_FILE = RUNTIME / "positioning-sim" / "state.json"
TMP_DIR = RUNTIME / "positioning-sim" / "tmp"

MINE = os.environ.get("POSITIONING_MINE_CODE", "620823009203")
OTHER_MINE = "610000000001"
MINE_NAME = "模拟煤矿"
SEED = 20260923
TIME_FMT = "%Y-%m-%d %H:%M:%S"

# ---------------------------------------------------------------- 假数据

AREA_DEFS = [
    ("井口区域", "副井井口", 0),
    ("重点区域", "-750大巷", 6),
    ("重点区域", "-850大巷", 5),
    ("重点区域", "1301综采工作面", 4),
    ("重点区域", "1302掘进工作面", 3),
    ("其它区域", "东翼轨道巷", 4),
    ("其它区域", "东翼皮带巷", 4),
    ("其它区域", "西翼回风巷", 3),
    ("其它区域", "中央变电所", 2),
    ("其它区域", "水泵房", 2),
    ("其它区域", "井底车场", 3),
    ("限制区域", "采空区密闭", 1),
]

SURNAMES = "王李张刘陈杨黄赵吴周徐孙马朱胡郭何高林罗郑梁谢宋唐许韩冯邓曹彭曾肖田董袁潘于蒋蔡余杜叶程苏魏吕丁任沈"
GIVEN = "伟强磊军勇杰涛明超亮刚平辉鹏华飞鑫波斌宇浩凯健俊帆帅旭宁龙林峰建国志海东红春生文"
DEPTS = ["综采一队", "综采二队", "掘进一队", "掘进二队", "机电队", "运输队", "通风队", "安监科"]
JOBS = {
    "综采一队": ["采煤机司机", "支架工", "输送机司机", "未设置"],
    "综采二队": ["采煤机司机", "支架工", "输送机司机", "未设置"],
    "掘进一队": ["掘进机司机", "锚杆工", "未设置"],
    "掘进二队": ["掘进机司机", "锚杆工", "未设置"],
    "机电队": ["电钳工", "变电所值班员", "未设置"],
    "运输队": ["皮带司机", "绞车司机", "信号工"],
    "通风队": ["瓦斯检查工", "测风工", "安全员"],
    "安监科": ["安全员", "安全检查工"],
}


def area_code(i):
    return f"{MINE}{i + 1:04d}"


def build_mine():
    """固定的一套区域、基站、人员。"""
    rnd = random.Random(SEED)
    areas, stations = [], []
    for i, (kind, name, n_station) in enumerate(AREA_DEFS):
        code = area_code(i)
        areas.append({"code": code, "type": kind, "name": name, "quota": rnd.choice([10, 20, 30, 50])})
        # 井口区域也要有一个基站：出入井时经过它
        for j in range(max(n_station, 1)):
            stations.append({"code": f"{code}{(j + 1) * 10:06d}", "area": code, "name": f"{name}{j + 1}号基站"})
    return areas, stations


def build_people(count):
    rnd = random.Random(SEED + 1)
    people = []
    used = set()
    for i in range(count):
        while True:
            name = rnd.choice(SURNAMES) + "".join(rnd.choice(GIVEN) for _ in range(rnd.choice([1, 2])))
            if name not in used:
                used.add(name)
                break
        dept = DEPTS[i % len(DEPTS)]
        people.append({
            "card": f"{MINE}{90001 + i:05d}",
            "name": name,
            "dept": dept,
            "job": rnd.choice(JOBS[dept]),
            "title": "队长" if i < len(DEPTS) else ("班长" if i % 10 == 3 else "未设置"),
            "leader": 1 if dept == "安监科" and i % 16 == 7 else 0,
            "special": 1 if rnd.random() < 0.3 else 0,
            # 三八制：0 点班、8 点班、16 点班
            "shift": i % 3,
        })
    return people


# ---------------------------------------------------------------- 文件

def inbox_dir(args):
    base = Path(args.inbox) if args.inbox else RUNTIME / "inbox"
    return base / "jxry" / MINE


def header(t, extra=None):
    fields = [MINE, MINE_NAME] + (extra or []) + [t.strftime(TIME_FMT)]
    return ";".join(fields)


def render(head, records, complete=True):
    body = head + "~" + "".join(r + "~" for r in records)
    return body + ("||" if complete else "")


def write_file(args, file_type, t, content, mine_dir=None, exact_path=None, quiet=False):
    """先写到临时目录再改名放进收件箱，后端不会读到写了一半的文件。

    收件箱里已有同名文件（同一秒生成了两份同类型文件）时，文件名里的时间往后推 1 秒，
    不覆盖还没被处理的文件。文件头里的时间不变。
    """
    target_dir = exact_path.parent if exact_path else (mine_dir or inbox_dir(args))
    target_dir.mkdir(parents=True, exist_ok=True)
    TMP_DIR.mkdir(parents=True, exist_ok=True)
    if exact_path:
        name = exact_path.name
    else:
        mine = target_dir.name
        name_time = t
        while (target_dir / (name := f"{mine}_{file_type}_{name_time.strftime('%Y%m%d%H%M%S')}.txt")).exists():
            name_time += timedelta(seconds=1)
    tmp = TMP_DIR / (name + ".part")
    tmp.write_text(content, encoding="utf-8")
    os.replace(tmp, target_dir / name)
    if quiet:
        return target_dir / name
    print(f"写入 {target_dir.relative_to(HV2_ROOT) if target_dir.is_relative_to(HV2_ROOT) else target_dir}/{name}")
    return target_dir / name


def fmt(t):
    return t.strftime(TIME_FMT) if t else ""


# ---------------------------------------------------------------- 模拟状态

def load_state():
    if STATE_FILE.exists():
        return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    return {"people": {}}


def save_state(state):
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(json.dumps(state, ensure_ascii=False, indent=1), encoding="utf-8")


def shift_window(p, now):
    """这个人当前（或最近）一次的下井时段。

    三八制每班 8 小时；每人每天的入井时间在班次开始后 10–50 分钟之间错开，
    在井下约 8 小时 10–40 分钟，所以交接班时两个班会有重叠，井下人数不会突然变成 0。
    """
    def window(day):
        rnd = random.Random(f"{p['card']}-{day}")
        start = day + timedelta(hours=p["shift"] * 8, minutes=rnd.randint(10, 50))
        return start, start + timedelta(hours=8, minutes=rnd.randint(10, 40))

    today = now.replace(hour=0, minute=0, second=0, microsecond=0)
    start, end = window(today)
    return (start, end) if start <= now else window(today - timedelta(days=1))


def step(state, people, stations, now):
    """推进一步：到点的人入井、下班的人出井，井下的人偶尔换基站。返回这一刻的快照记录。"""
    rnd = random.Random(f"{SEED}-{now.isoformat()}")
    underground = [s for s in stations if not s["area"].endswith("0001")]
    wellhead = [s for s in stations if s["area"].endswith("0001")]
    records = []
    for p in people:
        st = state["people"].get(p["card"])
        start, end = shift_window(p, now)
        # 每人每天有 10% 的概率不下井（休息、请假），按日期固定
        absent = random.Random(f"{p['card']}-absent-{start.date()}").random() < 0.1
        working = start <= now < end and not absent

        if working and (st is None or st["flag"] != 1):
            # 入井
            in_time = start
            station = rnd.choice(underground)
            st = {"flag": 1, "in": fmt(in_time), "out": "", "area": station["area"], "area_in": fmt(in_time),
                  "station": station["code"], "station_in": fmt(in_time), "out_seen": 0}
        elif working:
            # 井下：30% 的概率换一个基站，换到别的区域时区域进入时刻也更新
            if rnd.random() < 0.3:
                station = rnd.choice(underground)
                if station["area"] != st["area"]:
                    st["area"], st["area_in"] = station["area"], fmt(now)
                st["station"], st["station_in"] = station["code"], fmt(now)
        elif st is not None and st["flag"] == 1:
            # 出井：刚出井的人在快照里再带两次（厂家样例里快照带着刚出井的人）
            out_time = min(end, now)
            station = rnd.choice(wellhead)
            st.update({"flag": 2, "out": fmt(out_time), "area": station["area"], "station": station["code"],
                       "area_in": fmt(out_time), "station_in": fmt(out_time), "out_seen": 0})
        elif st is not None and st["flag"] == 2:
            st["out_seen"] += 1
            if st["out_seen"] >= 2:
                st = None

        if st is None:
            state["people"].pop(p["card"], None)
            continue
        state["people"][p["card"]] = st
        distance = f"{rnd.uniform(-60, 60):.2f}"
        records.append(";".join([
            p["card"], p["name"], str(st["flag"]), st["in"], st["out"], st["area"], st["area_in"],
            st["station"], st["station_in"], "三八制", distance, "正常", str(p["leader"]), str(p["special"]),
            f"{st['station']}&{st['station_in']}",
        ]))
    return records


# ---------------------------------------------------------------- 命令

def cmd_base(args, now=None):
    now = now or datetime.now().replace(microsecond=0)
    areas, stations = build_mine()
    people = build_people(args.people)
    write_file(args, "RYQY", now, render(header(now), [
        ";".join([a["type"], a["code"], str(a["quota"]), a["name"]]) for a in areas]))
    write_file(args, "RYJZ", now, render(header(now), [
        ";".join([s["code"], s["name"], "0", "0", "0", s["name"]]) for s in stations]))
    ryxx_head = header(now, ["500", "KJ000", "模拟人员定位系统", "模拟厂家", "2030-01-01"])
    write_file(args, "RYXX", now, render(ryxx_head, [
        ";".join([p["card"], p["name"], p["job"], p["title"], p["dept"], "1990-01-01", "",
                  str(p["leader"]), str(p["special"])]) for p in people]))


def cmd_tick(args, now=None):
    now = now or datetime.now().replace(microsecond=0)
    _, stations = build_mine()
    people = build_people(args.people)
    state = load_state()
    records = step(state, people, stations, now)
    save_state(state)
    write_file(args, "RYSS", now, render(header(now), records))
    rnd = random.Random(f"{SEED}-jzss-{now.isoformat()}")
    write_file(args, "JZSS", now, render(header(now), [
        ";".join([s["code"], "1" if rnd.random() < 0.04 else "0", "1", fmt(now)]) for s in stations]))
    in_well = sum(1 for r in records if r.split(";")[2] == "1")
    print(f"井下 {in_well} 人，快照共 {len(records)} 人")


def cmd_run(args):
    cmd_base(args)
    n = 0
    while args.count is None or n < args.count:
        cmd_tick(args)
        n += 1
        if args.count is not None and n >= args.count:
            break
        time.sleep(args.interval)


def cmd_bad(args):
    now = datetime.now().replace(microsecond=0)
    _, stations = build_mine()
    people = build_people(args.people)
    state = load_state()
    good = step(copy.deepcopy(state), people, stations, now) or [
        ";".join([people[0]["card"], people[0]["name"], "1", fmt(now), "", stations[1]["area"], fmt(now),
                  stations[1]["code"], fmt(now), "三八制", "0", "正常", "0", "0", ""])]
    kind = args.kind
    if kind == "half":
        # 写了一半：末尾没有 ||，后端本轮跳过，10 分钟后仍不完整才移到失败目录
        write_file(args, "RYSS", now, render(header(now), good, complete=False))
    elif kind == "fields":
        # 一条记录字段数不对：这条被跳过，文件记为 PARTIAL
        bad = good[0].split(";")[:5]
        write_file(args, "RYSS", now, render(header(now), good + [";".join(bad)]))
    elif kind == "ratio":
        # 一半记录出错：超过 20%，整个文件回滚，移到失败目录
        bad = [r.split(";")[0] + ";坏记录" for r in good]
        write_file(args, "RYSS", now, render(header(now), good + bad))
    elif kind == "old":
        # 旧文件后到：文件头时间比最新的早 10 分钟，记为 STALE，不覆盖新数据
        t = now - timedelta(minutes=10)
        write_file(args, "RYSS", t, render(header(t), good))
    elif kind == "future":
        # 文件头时间在未来：拒收，移到失败目录
        t = now + timedelta(minutes=30)
        write_file(args, "RYSS", t, render(header(t), good))
    elif kind == "dup":
        # 同一文件放两次：等第一份被处理（离开收件箱）后再放一次，第二份记为 DUPLICATE
        content = render(header(now), good)
        path = write_file(args, "RYSS", now, content)
        print("等后端处理第一份（最多 90 秒）……")
        for _ in range(90):
            if not path.exists():
                break
            time.sleep(1)
        else:
            sys.exit("第一份 90 秒内没被处理，后端在运行吗？")
        write_file(args, "RYSS", now, content, exact_path=path)
    elif kind == "rycs":
        # 超时报警：用户决定不用，收到直接删除
        write_file(args, "RYCS", now, render(header(now), [good[0].split(";")[0] + ";测试;;;;;;;"]))
    elif kind == "unknown":
        # 协议里有、但不解析的类型：只备份，记为 UNKNOWN
        write_file(args, "RYQJ", now, render(header(now), ["a;b;c"]))
    elif kind == "other-mine":
        # 别的矿的文件：移到失败目录
        other_dir = inbox_dir(args).parent / OTHER_MINE
        content = render(f"{OTHER_MINE};别的矿;{fmt(now)}", good)
        write_file(args, "RYSS", now, content, mine_dir=other_dir)


def cmd_backfill(args):
    """按过去的时间补一段 RYSS（docs/08 阶段 6：历史数据回填，给月报测试用）。

    从"谁都不在井下"开始，按间隔逐个时刻推进（和 tick 同一套规则），每个时刻一份 RYSS，文件头是那个过去的时间。
    不读写 state.json，不影响正在运行的 tick / run。后端照常解析入库：旧文件后到记 STALE，但每日出入井
    （POS_PRESENCE_DAILY）和人数曲线照常写入（docs/02 第四节）。

    只能放进测试用的收件箱：业务后端读的是默认收件箱 runtime/inbox，往那里放过去的文件会把假数据写进业务库。
    """
    if not args.inbox:
        sys.exit("backfill 必须用 --inbox 指定测试后端的收件箱")
    target = Path(args.inbox).resolve()
    if target == (RUNTIME / "inbox").resolve():
        sys.exit("拒绝往业务收件箱 runtime/inbox 补历史数据（业务后端会把它写进业务库）")
    start = datetime.strptime(args.start, "%Y-%m-%d")
    end = datetime.strptime(args.end, "%Y-%m-%d")
    if not start < end <= datetime.now():
        sys.exit("时间段不对：要求 --from 早于 --to，且 --to 不晚于现在")
    _, stations = build_mine()
    people = build_people(args.people)
    state = {"people": {}}
    t, n = start, 0
    while t < end:
        write_file(args, "RYSS", t, render(header(t), step(state, people, stations, t)), quiet=True)
        n += 1
        t += timedelta(seconds=args.interval)
    print(f"补了 {n} 份 RYSS（{args.start} 到 {args.end}，每 {args.interval} 秒一份），放进 {inbox_dir(args)}")


def cmd_people(args):
    people = build_people(args.people)
    if args.json:
        print(json.dumps([{k: p[k] for k in ("card", "name", "dept", "job")} for p in people],
                         ensure_ascii=False, indent=1))
    else:
        for p in people:
            print(p["card"], p["name"], p["dept"], p["job"])


def cmd_reset(args):
    if STATE_FILE.exists():
        STATE_FILE.unlink()
    print("模拟状态已清空")


def main():
    ap = argparse.ArgumentParser(description="定位文件生成器（按 42 号文附件2 格式造假数据放进收件箱）")
    ap.add_argument("--inbox", help="收件箱目录，默认 health-v2/runtime/inbox")
    ap.add_argument("--people", type=int, default=120, help="假人数量，默认 120")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("base", help="生成 RYQY、RYJZ、RYXX")
    sub.add_parser("tick", help="生成一份 RYSS 和 JZSS")
    run = sub.add_parser("run", help="先 base，再定时 tick")
    run.add_argument("--interval", type=int, default=300, help="间隔秒数，默认 300（厂家每 5 分钟一份）")
    run.add_argument("--count", type=int, help="tick 几次后退出，默认一直运行")
    bad = sub.add_parser("bad", help="造异常文件")
    bad.add_argument("kind", choices=["half", "fields", "ratio", "old", "future", "dup", "rycs", "unknown",
                                      "other-mine"])
    ppl = sub.add_parser("people", help="输出假人名单")
    ppl.add_argument("--json", action="store_true")
    sub.add_parser("reset", help="清空模拟状态")
    bf = sub.add_parser("backfill", help="按过去的时间补一段 RYSS（只能放进测试用的收件箱）")
    bf.add_argument("--from", dest="start", required=True, help="开始日期 yyyy-MM-dd（含）")
    bf.add_argument("--to", dest="end", required=True, help="结束日期 yyyy-MM-dd（不含）")
    bf.add_argument("--interval", type=int, default=3600, help="间隔秒数，默认 3600（三八制下每小时一份足够记下每天谁下过井）")
    args = ap.parse_args()
    {"base": cmd_base, "tick": cmd_tick, "run": cmd_run, "bad": cmd_bad, "people": cmd_people,
     "reset": cmd_reset, "backfill": cmd_backfill}[args.cmd](args)


if __name__ == "__main__":
    main()
