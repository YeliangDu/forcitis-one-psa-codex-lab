#!/usr/bin/env python3
"""取证单入口（二乙 #6 O22a；定稿 2.6、1.12）：跑本目录的一张取证单，输出落 0700 目录，文件名与文件头带过期日期；不要口令。

用法（只用标准库；固定由 /usr/bin/python3 -I -S 运行，须保持 3.9 兼容）：
  /usr/bin/python3 -I -S tools/evidence/evidence.py --session=<会话> <取证单>
      跑本目录的 sheet_<取证单>.py 的 collect(session, output)（会话名与输出文件路径由入口传入，取证单不读环境；返回若干行文本），在 / 下运行，写 ~/.modernize/psa/ops/evidence/<取证单>-<会话>-<UTC 时刻>-expires-<YYYYMMDD>.txt，
      只在标准输出打印这个路径，不打印内容（明细不进会话）。过期天数读仓库 policy.json 的 retention_days.evidence，缺项或不是正整数即停；到期由 janitor 按名里的 expires- 删。
  /usr/bin/python3 -I -S tools/evidence/evidence.py --entry
      打印本入口在 scripts/gates/prod_targets.json trusted_entries.entries 里的登记行（JSON）；登记行只由它生成，自测核对表里那一条与它逐字段相等。
命令行不收目录；家目录取自账号库（pwd），不读 HOME。门的可信条件（整棵 tools/evidence/ 树与 origin/main 逐字节一致、取证单写法限制）见 prod_write_gate.py 与 README.md；只运行目录里名字逐字等于 sheet_<名>.py 的文件，不写 __pycache__。
无口令：取证单运行前标准输入接到 /dev/null，getpass 被换成直接失败；凭据只能来自专用只读账号的本机配置（底线 4），不经命令行、不经交互。已知未覆盖：取证单或它起的子进程自己打开 /dev/tty 要口令，入口拦不住，靠首轮＋终审（README）。
退出码：0 完成；2 启动方式不对、参数不对、取证单缺失或出错、policy 缺项、输出目录不安全。
"""
import datetime
import getpass
import importlib.util
import json
import os
import pwd
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
INTERPRETER = ["/usr/bin/python3", "-I", "-S"]
NAME = re.compile(r"[a-z0-9][a-z0-9_]{0,39}")
SESSION = re.compile(r"[a-z0-9][a-z0-9-]{0,39}")


def entry_row():  # this entry's trusted_entries row: the only place it is written
    return {"id": "evidence", "script": "tools/evidence/evidence.py", "interpreter": INTERPRETER, "forms": [["--session={name}", "{name}"]], "trust_dirs": []}


def home():  # from the account database, never the HOME variable
    return pwd.getpwuid(os.getuid()).pw_dir


def stop(text):
    print(f"取证单入口：{text}", file=sys.stderr)
    sys.exit(2)


def retention_days():
    try:
        days = json.loads(open(os.path.join(REPO, "policy.json"), encoding="utf-8").read())["retention_days"]["evidence"]
    except (OSError, ValueError, KeyError, TypeError) as e:
        stop(f"读不到 policy.json 的 retention_days.evidence（{type(e).__name__}），停下")
    if not isinstance(days, int) or isinstance(days, bool) or days < 1:
        stop("policy.json 的 retention_days.evidence 不是正整数，停下")
    return days


def no_password(*_args, **_kwargs):  # stop() exits (SystemExit): the sheet's `except Exception` cannot swallow it
    stop("取证单不得要口令：凭据只来自专用只读账号的本机配置")


def collect(sheet, session, output):  # the sheet's lines from its collect(session, output); it runs in /, stdin /dev/null, getpass failing
    path = os.path.join(HERE, f"sheet_{sheet}.py")
    if f"sheet_{sheet}.py" not in os.listdir(HERE) or os.path.islink(path) or not os.path.isfile(path):  # the name exactly as listed: a case-insensitive disk would open Sheet_x.PY too
        stop(f"没有取证单 sheet_{sheet}.py")
    null = os.open(os.devnull, os.O_RDONLY)
    os.dup2(null, 0)
    os.close(null)
    getpass.getpass, sys.dont_write_bytecode = no_password, True  # no __pycache__: the gate trusts the tree only as on origin/main
    os.chdir("/")  # no repository, not writable: nothing read from the working directory (.pdbrc and the like) takes part
    try:
        spec = importlib.util.spec_from_file_location(f"sheet_{sheet}", path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        lines = [str(line) for line in module.collect(session, output)]
    except Exception as e:  # noqa: BLE001 — any failure of the sheet stops the run, nothing is written
        stop(f"取证单 {sheet} 出错：{type(e).__name__}（异常文本可能带取证内容，不打印）")
    if any("\n" in line or "\r" in line for line in lines):
        stop(f"取证单 {sheet} 返回的行含换行")
    return lines


def out_dir():  # ~/.modernize/psa/ops/evidence, 0700; none of ~/.modernize, psa, ops, evidence a symlink
    base = os.path.join(home(), ".modernize", "psa")
    path = os.path.join(base, "ops", "evidence")
    for part in (os.path.dirname(base), base, os.path.join(base, "ops"), path):
        if os.path.islink(part):
            stop(f"输出目录 {part} 是符号链接，停下")
    os.makedirs(path, mode=0o700, exist_ok=True)
    os.chmod(path, 0o700)
    return path


def run(session, sheet):
    days, now = retention_days(), datetime.datetime.now(datetime.timezone.utc)
    expires = (now + datetime.timedelta(days=days)).date()
    name = f"{sheet}-{session}-{now:%Y%m%dT%H%M%SZ}-expires-{expires:%Y%m%d}.txt"
    path = os.path.join(out_dir(), name)
    lines = collect(sheet, session, path)
    head = [f"过期：{expires:%Y-%m-%d}（retention_days.evidence = {days} 天，到期由 janitor 删除）", f"取证单：{sheet}　会话：{session}　生成：{now:%Y-%m-%dT%H:%M:%SZ}", ""]
    try:
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    except OSError as e:  # already there (same second) or not writable: never overwritten
        stop(f"写不了输出文件 {name}（{type(e).__name__}），停下")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write("\n".join(head + lines) + "\n")
    print(path)


def main(argv):
    if argv == ["--entry"]:
        print(json.dumps(entry_row(), ensure_ascii=False))
        return
    if not (sys.flags.isolated and sys.flags.no_site):
        stop("须由 /usr/bin/python3 -I -S 运行（登记的写法）")
    if len(argv) != 2 or not argv[0].startswith("--session="):
        stop("用法：--session=<会话> <取证单>，或 --entry")
    session, sheet = argv[0][len("--session="):], argv[1]
    if not SESSION.fullmatch(session) or not NAME.fullmatch(sheet):
        stop("会话名只许小写字母、数字、连字符；取证单名只许小写字母、数字、下划线（各 ≤40 字）")
    run(session, sheet)


if __name__ == "__main__":
    main(sys.argv[1:])
