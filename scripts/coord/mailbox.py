#!/usr/bin/env python3
"""File mailbox reader for the coordinator (GOV-1023-QUA-01): the Stop gate (scripts/hooks/stop_status_gate.py) writes a file per 等调度 / 完成点 report
into `<psa home>/mailbox/`; the coordinator fetches, acknowledges and checks for unanswered ones here.

  mailbox.py list --unacked        unacknowledged reports, oldest first (name, age, session, status, sent or not)
  mailbox.py show <file>           print one report
  mailbox.py ack <file>            move it to mailbox/acked/ (a name or a path inside the mailbox)
  mailbox.py stale [--minutes=20]  unacknowledged for longer than that; exit 1 when there are any (for a timer to alarm on)
  mailbox.py --self-test
The directory is `$MODERNIZE_PSA_HOME/mailbox` or `~/.modernize/psa/mailbox`; `--dir` overrides it.
"""
import argparse
import os
import re
import sys
import tempfile
import time


def mailbox_dir(override=None):
    if override:
        return override
    return os.path.join(os.environ.get("MODERNIZE_PSA_HOME") or os.path.expanduser("~/.modernize/psa"), "mailbox")


def unacked(directory):
    """[(mtime, name)] oldest first; temp files of a write in progress and the acked/ folder are not entries."""
    if not os.path.isdir(directory):
        return []
    items = []
    for f in os.listdir(directory):
        if f.endswith(".md") and not f.startswith("."):
            try:
                items.append((os.path.getmtime(os.path.join(directory, f)), f))
            except OSError:
                pass  # acknowledged (moved away) between the listing and now
    return sorted(items)


def field(path, key):
    try:
        with open(path, encoding="utf-8") as stream:
            for _, line in zip(range(12), stream):
                match = re.match(rf"^{key}:\s*(.*)$", line)
                if match:
                    return match.group(1).strip()
    except OSError:
        pass
    return "?"


def describe(directory, mtime, name, now):
    path = os.path.join(directory, name)
    return f"{name}  {int((now - mtime) / 60)} 分钟前  {field(path, '状态')}  已发调度：{field(path, '已发调度')}"


def resolve(directory, target):
    name = os.path.basename(target)
    path = os.path.join(directory, name)
    if os.path.dirname(os.path.abspath(target)) not in (os.path.abspath(directory), os.path.abspath(".")) and os.sep in target:
        raise SystemExit(f"mailbox: {target} 不在信箱 {directory} 里")
    if not os.path.isfile(path):
        raise SystemExit(f"mailbox: 找不到 {name}（可能已回执）")
    return path


def command(argv, now=None, out=print):
    now = time.time() if now is None else now
    parser = argparse.ArgumentParser(prog="mailbox.py")
    parser.add_argument("--dir")
    sub = parser.add_subparsers(dest="cmd", required=True)
    listing = sub.add_parser("list")
    listing.add_argument("--unacked", action="store_true", required=True)
    sub.add_parser("show").add_argument("file")
    sub.add_parser("ack").add_argument("file")
    stale = sub.add_parser("stale")
    stale.add_argument("--minutes", type=float, default=20)
    args = parser.parse_args(argv)
    directory = mailbox_dir(args.dir)
    if args.cmd == "list":
        items = unacked(directory)
        for mtime, name in items:
            out(describe(directory, mtime, name, now))
        if not items:
            out("信箱无未回执报告")
        return 0
    if args.cmd == "show":
        with open(resolve(directory, args.file), encoding="utf-8") as stream:
            out(stream.read().rstrip("\n"))
        return 0
    if args.cmd == "ack":
        path = resolve(directory, args.file)
        os.makedirs(os.path.join(directory, "acked"), exist_ok=True)
        os.replace(path, os.path.join(directory, "acked", os.path.basename(path)))
        out(f"已回执 {os.path.basename(path)}")
        return 0
    old = [(m, n) for m, n in unacked(directory) if now - m > args.minutes * 60]
    for mtime, name in old:
        out(describe(directory, mtime, name, now))
    if old:
        out(f"共 {len(old)} 条超过 {args.minutes:g} 分钟未回执")
        return 1
    out(f"无超过 {args.minutes:g} 分钟未回执的报告")
    return 0


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        directory = os.path.join(tmp, "mailbox")
        os.makedirs(directory)
        lines = []

        def run(*argv, now=10000.0):
            lines.clear()
            code = command(["--dir", directory, *argv], now, lines.append)
            return code, "\n".join(lines)

        assert run("list", "--unacked")[1] == "信箱无未回执报告" and run("stale")[0] == 0, "empty mailbox"
        for name, age, sent in (("20261023-100000-one-a.md", 3000, "是"), ("20261023-100500-one-b.md", 60, "否（信箱兜底）")):
            path = os.path.join(directory, name)
            open(path, "w", encoding="utf-8").write(f"---\n会话: x\n状态: 等调度\n已发调度: {sent}\n---\n正文 {name}\n")
            os.utime(path, (10000 - age, 10000 - age))
        open(os.path.join(directory, ".tmp-half.md"), "w").write("half")
        os.makedirs(os.path.join(directory, "acked"))
        code, text = run("list", "--unacked")
        assert code == 0 and "one-a" in text and "one-b" in text and ".tmp-" not in text and text.index("one-a") < text.index("one-b"), text
        assert "50 分钟前" in text and "等调度" in text and "信箱兜底" in text, text
        code, text = run("stale")
        assert code == 1 and "one-a" in text and "one-b" not in text and "共 1 条" in text, "only the old one is stale"
        assert run("stale", "--minutes=0.5")[0] == 1 and "one-b" in run("stale", "--minutes=0.5")[1]
        assert "正文 20261023-100000-one-a.md" in run("show", "20261023-100000-one-a.md")[1]
        assert run("ack", "20261023-100000-one-a.md")[0] == 0
        assert os.path.isfile(os.path.join(directory, "acked", "20261023-100000-one-a.md")) and not os.path.exists(os.path.join(directory, "20261023-100000-one-a.md"))
        code, text = run("stale")
        assert code == 0 and "one-a" not in text, "an acked report is no longer stale"
        assert "one-a" not in run("list", "--unacked")[1] and "one-b" in run("list", "--unacked")[1]
        for bad in (("ack", "20261023-100000-one-a.md"), ("ack", "/etc/hosts"), ("ack", "nope.md")):
            try:
                run(*bad)
            except SystemExit as error:
                assert error.code and "mailbox" in str(error.code), "a missing, already acked or foreign file fails loudly"
            else:
                raise AssertionError(f"{bad} must fail")
    print("mailbox self-test: ok")
    return 0


if __name__ == "__main__":
    if "--self-test" in sys.argv:
        sys.exit(self_test())
    sys.exit(command(sys.argv[1:]))
