#!/usr/bin/env python3
"""手表模拟器（docs/03 第七节）。

模拟 N 块手表按《智能手表开发协议》连后端的 TCP 9001：先发 AP00 登录；收到测量指令先回确认，
再发对应的数据包；定时发 AP03 心跳（电量缓慢下降，步数累加并在上限处回绕）。
数值大体正常，偶尔偏高偏低；可以用 send 命令让某块表制造场景（高心率、SOS、跌倒、没戴、掉线重连……）。

手表与人的绑定：第 i 块表（IMEI 8699 开头）绑定定位文件生成器的第 i 个假人
（python3 tools/positioning-sim/sim.py people --json，卡编码 62082300920390001 起）；
超出假人数量的表登记但不绑定。另有 --unregistered 块没登记的表（IMEI 8698 开头）。

测量指令的回应（docs/10 第 3 项，真实手表到阶段 7 再核对）：
  BPXL → APXL 确认 + AP49 心率
  BPXY → APXY 确认 + APHT 心率,收缩压,舒张压
  BPXZ → APXZ 确认 + APHP 只填血氧，其余留空（协议允许"无值则留空"）
  BPXT → APXT 确认 + AP50 体温,电量（电量固定报 0，和真实手表一样，后端不采用）
  没戴时：AP49/APHT 发 0，APHP 发未佩戴占位包 0,0,0,95,0.0,0.0，AP50 发 0.0

用法（在 health-v2/ 下运行）：
  python3 tools/watch-simulator/sim.py devices              列出 IMEI 与卡编码的对应
  python3 tools/watch-simulator/sim.py bind                 把这份对应写进 DEVICE 表（经 docker 里的 sqlplus）
  python3 tools/watch-simulator/sim.py swap 1 2             对调第 1、2 块表绑定的人（演示在线改绑定）
  python3 tools/watch-simulator/sim.py run                  运行（默认 200 块表，一次只能运行一个）
  python3 tools/watch-simulator/sim.py send 3 sos           让第 3 块表发 SOS（别的动作见 send -h）
  python3 tools/watch-simulator/sim.py once --imei X 包...  单独连一次，发几个包，打印服务器的回复
"""

import argparse
import asyncio
import json
import os
import random
import signal
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

HV2_ROOT = Path(__file__).resolve().parents[2]
RUNTIME = HV2_ROOT / "runtime" / "watch-sim"
CMD_DIR = RUNTIME / "cmd"
PID_FILE = RUNTIME / "sim.pid"
STATS_FILE = RUNTIME / "stats.json"
STATE_FILE = RUNTIME / "state.json"
POSITIONING_SIM = HV2_ROOT / "tools" / "positioning-sim" / "sim.py"
ORACLE_CONTAINER = "local-oracle-free"


# ---------------------------------------------------------------- 手表与绑定

def imei_of(i, registered=True):
    """第 i 块表（从 1 数起）的 IMEI：登记的 8699 开头，未登记的 8698 开头，共 15 位。"""
    return f"{'8699' if registered else '8698'}{i:011d}"


def load_people(count):
    out = subprocess.run([sys.executable, str(POSITIONING_SIM), "--people", str(count), "people", "--json"],
                         check=True, capture_output=True, text=True).stdout
    return json.loads(out)


def device_table(watches, people_count):
    people = load_people(people_count) if people_count > 0 else []
    return [{"no": i, "imei": imei_of(i), "card": people[i - 1]["card"] if i <= len(people) else None,
             "name": people[i - 1]["name"] if i <= len(people) else None}
            for i in range(1, watches + 1)]


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
    env.update({k: v for k, v in os.environ.items() if k.startswith("ORACLE_")})
    return env


def run_sql(sql, test=False):
    """在 docker 容器里用 sqlplus 执行。密码经标准输入传进去，不出现在命令行上。"""
    env = read_env()
    user = env["ORACLE_TEST_USER"] if test else env["ORACLE_USER"]
    password = env["ORACLE_TEST_PASSWORD"] if test else env["ORACLE_PASSWORD"]
    if test and not user.upper().endswith("_TEST"):
        sys.exit("测试用户名必须以 _TEST 结尾")
    service = env.get("ORACLE_SERVICE", "FREEPDB1")
    script = (f'CONNECT {user}/"{password}"@//localhost:1521/{service}\n'
              "WHENEVER SQLERROR EXIT FAILURE ROLLBACK\nSET FEEDBACK OFF\nSET HEADING OFF\n"
              # 和后端一样按北京时间写 LOCALTIMESTAMP（容器本身是 UTC）
              "ALTER SESSION SET TIME_ZONE = 'Asia/Shanghai';\n"
              f"{sql}\nCOMMIT;\nEXIT\n")
    r = subprocess.run(["docker", "exec", "-i", ORACLE_CONTAINER, "sqlplus", "-s", "/nolog"],
                       input=script, capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("执行 SQL 失败：\n" + r.stdout[-2000:] + r.stderr[-2000:])
    return r.stdout


def cmd_devices(args):
    rows = device_table(args.watches, args.people)
    if args.json:
        print(json.dumps(rows, ensure_ascii=False, indent=1))
        return
    for r in rows:
        print(r["no"], r["imei"], r["card"] or "（未绑定）", r["name"] or "")
    print(f"另有 {args.unregistered} 块未登记的表：{imei_of(1, False)} 起" if args.unregistered else "")


def cmd_bind(args):
    rows = device_table(args.watches, args.people)
    # 先把模拟表的绑定都清掉（卡编码唯一，按固定对应重新绑定时不会互相冲突），再逐块写入；人员表里没有的人不绑定
    lines = ["UPDATE DEVICE SET CARD_CODE = NULL, BOUND_AT = NULL WHERE IMEI LIKE '8699%';"]
    for r in rows:
        card = f"'{r['card']}'" if r["card"] else "NULL"
        lines.append(f"""MERGE INTO DEVICE d
USING (SELECT '{r['imei']}' IMEI, (SELECT CARD_CODE FROM POS_PERSON WHERE CARD_CODE = {card}) CARD_CODE FROM DUAL) s
ON (d.IMEI = s.IMEI)
WHEN MATCHED THEN UPDATE SET d.CARD_CODE = s.CARD_CODE, d.STATUS = 1,
     d.BOUND_AT = CASE WHEN s.CARD_CODE IS NULL THEN NULL ELSE LOCALTIMESTAMP(0) END
WHEN NOT MATCHED THEN INSERT (IMEI, CARD_CODE, MODEL, BOUND_AT, STATUS)
     VALUES (s.IMEI, s.CARD_CODE, '模拟手表', CASE WHEN s.CARD_CODE IS NULL THEN NULL ELSE LOCALTIMESTAMP(0) END, 1);""")
    lines.append("SELECT COUNT(*) || ' 块模拟表，' || COUNT(CARD_CODE) || ' 块已绑定' FROM DEVICE WHERE IMEI LIKE '8699%';")
    out = run_sql("\n".join(lines), test=args.test)
    print(out.strip())
    print("后端每 60 秒刷新一次绑定缓存")


def cmd_swap(args):
    a, b = imei_of(args.a), imei_of(args.b)
    run_sql(f"""DECLARE
  ca DEVICE.CARD_CODE%TYPE;
  cb DEVICE.CARD_CODE%TYPE;
BEGIN
  SELECT CARD_CODE INTO ca FROM DEVICE WHERE IMEI = '{a}';
  SELECT CARD_CODE INTO cb FROM DEVICE WHERE IMEI = '{b}';
  UPDATE DEVICE SET CARD_CODE = NULL WHERE IMEI IN ('{a}', '{b}');
  UPDATE DEVICE SET CARD_CODE = cb, BOUND_AT = LOCALTIMESTAMP(0) WHERE IMEI = '{a}';
  UPDATE DEVICE SET CARD_CODE = ca, BOUND_AT = LOCALTIMESTAMP(0) WHERE IMEI = '{b}';
END;
/""", test=args.test)
    print(f"已对调 {a} 和 {b} 绑定的人（{datetime.now():%H:%M:%S}），后端 60 秒内生效")


# ---------------------------------------------------------------- 一块表

class Watch:
    def __init__(self, sim, no, registered):
        self.sim = sim
        self.no = no
        self.imei = imei_of(no, registered)
        self.name = str(no) if registered else f"unreg{no}"
        rnd = random.Random(f"{self.imei}")
        self.rnd = rnd
        # 计步器和电量在模拟器重启后接着用（真实手表不会因为服务器或模拟器重启而归零）
        saved = sim.saved.get(self.imei, {})
        self.battery = saved.get("battery", rnd.randint(60, 100))
        self.steps = saved.get("steps", rnd.randint(0, sim.args.step_wrap))
        self.valid = 0            # 本次运行发出的有效体征条数
        self.walked = 0           # 本次运行走的步数（心跳里计步器的真实增量）
        self.base_hr = rnd.randint(65, 85)
        self.force = {}          # 指定的数值：hr、spo2、temp、sys、dia
        self.unworn = False
        self.low_battery_sent = False
        self.writer = None
        self.connected = False
        self.drop_until = 0

    # ---- 数值

    def hr(self):
        if "hr" in self.force:
            return self.force["hr"]
        if self.rnd.random() < self.sim.args.abnormal_rate:
            return self.rnd.randint(125, 140)
        return max(50, int(self.rnd.gauss(self.base_hr, 6)))

    def spo2(self):
        if "spo2" in self.force:
            return self.force["spo2"]
        return self.rnd.randint(86, 89) if self.rnd.random() < self.sim.args.abnormal_rate else self.rnd.randint(95, 99)

    def temp(self):
        if "temp" in self.force:
            return self.force["temp"]
        return round(self.rnd.uniform(37.5, 38.2) if self.rnd.random() < self.sim.args.abnormal_rate
                     else self.rnd.uniform(36.2, 37.0), 1)

    def pressure(self):
        if "sys" in self.force:
            return self.force["sys"], self.force.get("dia", 85)
        if self.rnd.random() < self.sim.args.abnormal_rate:
            return self.rnd.randint(145, 160), self.rnd.randint(92, 100)
        return self.rnd.randint(108, 132), self.rnd.randint(68, 84)

    def not_worn_now(self):
        return self.unworn or self.rnd.random() < self.sim.args.unworn_rate

    # ---- 收发

    def send(self, packet):
        if self.writer is None or self.writer.is_closing():
            return
        self.writer.write(packet.encode("latin-1"))
        code = packet[2:6]
        self.sim.sent[code] = self.sim.sent.get(code, 0) + 1

    def count_valid(self, metric, n=1):
        self.sim.valid[metric] = self.sim.valid.get(metric, 0) + n
        self.valid += n

    async def on_command(self, frame):
        code = frame[2:6]
        parts = frame[6:-1].split(",")
        serial = parts[2] if len(parts) > 2 else "000000"
        self.sim.received[code] = self.sim.received.get(code, 0) + 1
        if code == "BP33":
            self.send(f"IWAP33,{serial},1#")
        elif code in ("BP86", "BP87"):
            self.send(f"IWAP{code[2:]},{serial}#")
        elif code in ("BPXL", "BPXY", "BPXZ", "BPXT"):
            self.send(f"IWAP{code[2:]},{serial}#")
            await asyncio.sleep(self.rnd.uniform(0.2, 1.0))
            self.measure(code)

    def measure(self, code):
        off = self.not_worn_now()
        if code == "BPXL":
            v = 0 if off else self.hr()
            self.send(f"IWAP49,{v}#")
            if not off:
                self.count_valid("HEART_RATE")
        elif code == "BPXY":
            if off:
                self.send("IWAPHT,0,0,0#")
            else:
                s, d = self.pressure()
                self.send(f"IWAPHT,{self.hr()},{s},{d}#")
                self.count_valid("HEART_RATE")
                self.count_valid("BLOOD_PRESSURE")
        elif code == "BPXZ":
            if off:
                self.send("IWAPHP,0,0,0,95,0.0,0.0#")
            else:
                self.send(f"IWAPHP,,,,{self.spo2()},,,,,,,,,#")
                self.count_valid("SPO2")
        elif code == "BPXT":
            self.send(f"IWAP50,{0.0 if off else self.temp()},0#")
            if not off:
                self.count_valid("TEMPERATURE")

    def heartbeat(self):
        minutes = self.sim.args.heartbeat / 60
        inc = int(self.rnd.randint(0, 80) * minutes)
        self.walked += inc
        self.steps = (self.steps + inc) % (self.sim.args.step_wrap + 1)
        if self.rnd.random() < 1 / 15 * minutes:
            self.battery -= 1
        if self.battery <= 5:
            self.battery = 100            # 充电
            self.low_battery_sent = False
        status = f"060009{self.battery:03d}00102"
        self.send(f"IWAP03,{status},{self.steps:05d},{self.rnd.randint(0, 50)}#")
        if self.battery <= 20 and not self.low_battery_sent:
            self.low_battery_sent = True
            self.alarm("02")

    def alarm(self, code):
        now = datetime.now(timezone.utc)
        self.send(f"IWAP10{now:%y%m%d}A2232.9806N11404.9355E000.1{now:%H%M%S}323.87060009{self.battery:03d}00102,"
                  f"460,0,9520,3671,{code},zh-cn,00,HOME|74-DE-2B-44-88-8C|97#")

    async def run(self):
        args = self.sim.args
        await asyncio.sleep(self.rnd.uniform(0, args.ramp))
        while not self.sim.stopping:
            if time.time() < self.drop_until:
                await asyncio.sleep(0.5)
                continue
            try:
                reader, self.writer = await asyncio.open_connection(args.host, args.port)
            except OSError:
                await asyncio.sleep(3)
                continue
            self.connected = True
            self.send(f"IWAP00{self.imei}#")
            beat = asyncio.create_task(self.beat_loop())
            try:
                buf = b""
                while not self.sim.stopping:
                    data = await reader.read(4096)
                    if not data:
                        break
                    buf += data
                    while b"#" in buf:
                        frame, buf = buf.split(b"#", 1)
                        text = frame.decode("latin-1").strip() + "#"
                        if text.startswith("IWBP") and text[2:6] not in ("BP00", "BP03", "BP49", "BP50", "BPHT",
                                                                           "BPHP", "BP10"):
                            asyncio.create_task(self.on_command(text))
                        else:
                            self.sim.received[text[2:6]] = self.sim.received.get(text[2:6], 0) + 1
            except (ConnectionError, OSError):
                pass
            finally:
                beat.cancel()
                self.connected = False
                if self.writer:
                    self.writer.close()
                self.writer = None
            await asyncio.sleep(3)

    async def beat_loop(self):
        await asyncio.sleep(self.rnd.uniform(1, self.sim.args.heartbeat))
        while True:
            self.heartbeat()
            await asyncio.sleep(self.sim.args.heartbeat)

    def control(self, action, value):
        if action == "sos":
            self.alarm("01")
        elif action == "fall":
            self.alarm("05")
        elif action == "alarm":
            self.alarm(value)
        elif action in ("hr", "spo2", "temp"):
            if value == "off":
                self.force.pop(action, None)
            else:
                self.force[action] = float(value) if action == "temp" else int(value)
        elif action == "bp":
            if value == "off":
                self.force.pop("sys", None)
                self.force.pop("dia", None)
            else:
                s, d = value.split("/")
                self.force["sys"], self.force["dia"] = int(s), int(d)
        elif action == "unworn":
            self.unworn = value == "on"
        elif action == "drop":
            self.drop_until = time.time() + float(value or 5)
            if self.writer:
                self.writer.close()
        elif action == "steps":
            self.steps = int(value)
        elif action == "reboot":
            self.steps = 0
        elif action == "battery":
            self.battery = int(value)
        elif action == "beat":
            self.heartbeat()
        elif action == "measure":
            self.measure({"hr": "BPXL", "bp": "BPXY", "spo2": "BPXZ", "temp": "BPXT"}[value])
        elif action == "raw":
            self.send(value)
        print(f"{datetime.now():%H:%M:%S} 第 {self.name} 块表（{self.imei}）：{action} {value or ''}", flush=True)


# ---------------------------------------------------------------- 模拟器

class Simulator:
    def __init__(self, args):
        self.args = args
        self.saved = json.loads(STATE_FILE.read_text(encoding="utf-8")) if STATE_FILE.exists() else {}
        self.watches = [Watch(self, i, True) for i in range(1, args.watches + 1)]
        self.watches += [Watch(self, i, False) for i in range(1, args.unregistered + 1)]
        self.by_name = {w.name: w for w in self.watches}
        self.by_name.update({w.imei: w for w in self.watches})
        self.sent, self.received, self.valid = {}, {}, {}
        self.stopping = False

    async def control_loop(self):
        CMD_DIR.mkdir(parents=True, exist_ok=True)
        while not self.stopping:
            for f in sorted(CMD_DIR.glob("*.json")):
                try:
                    c = json.loads(f.read_text(encoding="utf-8"))
                finally:
                    f.unlink(missing_ok=True)
                targets = self.watches if c["target"] == "all" else [self.by_name.get(c["target"])]
                for w in targets:
                    if w is None:
                        print(f"没有这块表：{c['target']}", flush=True)
                    else:
                        w.control(c["action"], c.get("value"))
            await asyncio.sleep(0.5)

    async def status_loop(self):
        while not self.stopping:
            await asyncio.sleep(self.args.status)
            online = sum(1 for w in self.watches if w.connected)
            self.save()
            print(f"{datetime.now():%H:%M:%S} 在线 {online}/{len(self.watches)}，已发 {sum(self.sent.values())} 包，"
                  f"有效体征 {sum(self.valid.values())} 条，收到指令 "
                  f"{sum(v for k, v in self.received.items() if k.startswith('BPX'))} 条", flush=True)

    def save(self):
        """写统计（和库里的数据对账用）和计步器状态。"""
        online = sum(1 for w in self.watches if w.connected)
        stats = {"time": datetime.now().isoformat(timespec="seconds"), "connected": online,
                 "sent": self.sent, "received": self.received, "valid_vitals_sent": self.valid,
                 "valid_by_watch": {w.name: w.valid for w in self.watches},
                 "walked_by_watch": {w.name: w.walked for w in self.watches}}
        STATS_FILE.write_text(json.dumps(stats, ensure_ascii=False, indent=1), encoding="utf-8")
        STATE_FILE.write_text(json.dumps({w.imei: {"steps": w.steps, "battery": w.battery} for w in self.watches}),
                              encoding="utf-8")

    async def main(self):
        tasks = [asyncio.create_task(w.run()) for w in self.watches]
        tasks += [asyncio.create_task(self.control_loop()), asyncio.create_task(self.status_loop())]
        await asyncio.gather(*tasks)


def _raise_interrupt(*_):
    raise KeyboardInterrupt


def cmd_run(args):
    RUNTIME.mkdir(parents=True, exist_ok=True)
    if PID_FILE.exists():
        try:
            old = int(PID_FILE.read_text())
            os.kill(old, 0)
            sys.exit(f"手表模拟器已在运行（PID {old}），一次只能运行一个")
        except (ValueError, ProcessLookupError):
            pass
    PID_FILE.write_text(str(os.getpid()))
    for f in CMD_DIR.glob("*.json"):
        f.unlink()
    print(f"启动 {args.watches} 块登记的表 + {args.unregistered} 块未登记的表，连接 {args.host}:{args.port}", flush=True)
    sim = Simulator(args)
    signal.signal(signal.SIGTERM, _raise_interrupt)
    try:
        asyncio.run(sim.main())
    except KeyboardInterrupt:
        pass
    finally:
        sim.save()
        PID_FILE.unlink(missing_ok=True)
        print(f"{datetime.now():%H:%M:%S} 已停止，统计写在 {STATS_FILE.relative_to(HV2_ROOT)}", flush=True)


def cmd_send(args):
    CMD_DIR.mkdir(parents=True, exist_ok=True)
    name = f"{time.time_ns()}.json"
    (CMD_DIR / name).write_text(json.dumps({"target": args.target, "action": args.action, "value": args.value},
                                           ensure_ascii=False), encoding="utf-8")
    if not PID_FILE.exists():
        print("提示：模拟器没在运行，命令会在它启动时被清掉")


def cmd_once(args):
    """单独连一次：登录、依次发包，打印服务器在 --wait 秒内的全部回复。"""
    async def go():
        reader, writer = await asyncio.open_connection(args.host, args.port)
        packets = ([] if args.no_login else [f"IWAP00{args.imei}#"]) + args.packets
        for p in packets:
            writer.write(p.encode("latin-1"))
            await writer.drain()
            print(f">> {p}")
            await asyncio.sleep(0.3)
        end = time.time() + args.wait
        buf = b""
        while time.time() < end:
            try:
                data = await asyncio.wait_for(reader.read(4096), timeout=max(0.1, end - time.time()))
            except asyncio.TimeoutError:
                break
            if not data:
                print("-- 服务器关闭了连接")
                break
            buf += data
            while b"#" in buf:
                frame, buf = buf.split(b"#", 1)
                print(f"<< {frame.decode('latin-1')}#")
        writer.close()
    asyncio.run(go())


def main():
    ap = argparse.ArgumentParser(description="手表模拟器（按智能手表协议连后端 TCP 9001）")
    sub = ap.add_subparsers(dest="cmd", required=True)

    def people_args(p):
        p.add_argument("--watches", type=int, default=200, help="登记的表数量，默认 200")
        p.add_argument("--people", type=int, default=120, help="绑定前多少个假人，默认 120（和定位生成器默认一致）")

    d = sub.add_parser("devices", help="列出 IMEI 与卡编码的对应")
    people_args(d)
    d.add_argument("--unregistered", type=int, default=0)
    d.add_argument("--json", action="store_true")
    b = sub.add_parser("bind", help="把对应写进 DEVICE 表")
    people_args(b)
    b.add_argument("--test", action="store_true", help="写进测试用户（ORACLE_TEST_USER）")
    s = sub.add_parser("swap", help="对调两块表绑定的人")
    s.add_argument("a", type=int)
    s.add_argument("b", type=int)
    s.add_argument("--test", action="store_true")

    r = sub.add_parser("run", help="运行模拟器")
    r.add_argument("--watches", type=int, default=200)
    r.add_argument("--unregistered", type=int, default=0, help="另外模拟几块没登记的表")
    r.add_argument("--host", default="127.0.0.1")
    r.add_argument("--port", type=int, default=int(os.environ.get("WATCH_TCP_PORT", 9001)))
    r.add_argument("--heartbeat", type=float, default=60, help="心跳间隔秒数，默认 60")
    r.add_argument("--step-wrap", type=int, default=65535, help="计步器回绕上限，默认 65535")
    r.add_argument("--abnormal-rate", type=float, default=0.01, help="每次测量出现异常值的概率")
    r.add_argument("--unworn-rate", type=float, default=0.01, help="每次测量按没戴处理的概率")
    r.add_argument("--ramp", type=float, default=10, help="所有表在多少秒内陆续连上")
    r.add_argument("--status", type=float, default=30, help="多少秒打印一次状态")

    c = sub.add_parser("send", help="给运行中的模拟器发命令",
                       description="动作：sos | fall | alarm <代码> | hr <值|off> | spo2 <值|off> | temp <值|off> | "
                                   "bp <收缩/舒张|off> | unworn on|off | drop [秒] | steps <读数> | reboot | "
                                   "battery <值> | beat（立即发心跳）| measure hr|bp|spo2|temp | raw <整包>")
    c.add_argument("target", help="第几块表（1 起）、unreg1 这样的未登记表、IMEI，或 all")
    c.add_argument("action")
    c.add_argument("value", nargs="?")

    o = sub.add_parser("once", help="单独连一次，发几个包，打印回复")
    o.add_argument("--imei", required=True)
    o.add_argument("--host", default="127.0.0.1")
    o.add_argument("--port", type=int, default=int(os.environ.get("WATCH_TCP_PORT", 9001)))
    o.add_argument("--wait", type=float, default=3)
    o.add_argument("--no-login", action="store_true", help="不先发 AP00")
    o.add_argument("packets", nargs="*")

    args = ap.parse_args()
    {"devices": cmd_devices, "bind": cmd_bind, "swap": cmd_swap, "run": cmd_run, "send": cmd_send,
     "once": cmd_once}[args.cmd](args)


if __name__ == "__main__":
    main()
