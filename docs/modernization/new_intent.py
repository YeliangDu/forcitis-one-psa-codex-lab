#!/usr/bin/env python3
"""新增一条用户决定片段 intent/<日期>-<HHMMSS>-<会话名>-<短名>.md，然后重新生成 INTENT.md。

用法：python3 docs/modernization/new_intent.py --session=<会话名> "<用户原话>" "<落点>" [--date=YYYY-MM-DD] [--rules=RULE-272,RULE-273] [--adrs=ADR-0044] [--sheet=D5]
可选的 --rules / --adrs / --sheet 把决定影响的规则号、ADR、确认页单子记入片段，规则索引与 ADR 治理据此反查「被哪些决定改过」。
原话与落点各为一行（会成为 INTENT.md 表格的一行），不能含未转义的「|」。
文件名由日期、当前时刻与会话名组成，并行会话互不撞名，无需协调序号。
"""
import datetime, os, re, subprocess, sys

BASE = os.path.dirname(os.path.abspath(__file__))


def short_name(text, n=16):
    t = re.sub(r"[`*「」『』（）()\[\]【】<>:：，,。；;、！!？?\"'/\\|·\s]+", "-", text)
    return re.sub(r"-+", "-", t).strip("-")[:n].strip("-") or "x"


def parse_args(doc, n):
    opts = dict(a[2:].split("=", 1) for a in sys.argv[1:] if a.startswith("--") and "=" in a)
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != n or "session" in opts and not opts["session"]:
        sys.exit(doc)
    return opts, args


def create(folder, date, session, slug, text, builder="build_records.py"):
    now = datetime.datetime.now()
    while True:
        name = f"{date}-{now:%H%M%S}-{short_name(session, 40)}" + (f"-{slug}" if slug else "") + ".md"
        path = os.path.join(BASE, folder, name)
        try:
            open(path, "x", encoding="utf-8").write(text)
            break
        except FileExistsError:
            now += datetime.timedelta(seconds=1)
    print(os.path.relpath(path, BASE))
    subprocess.run([sys.executable, os.path.join(BASE, builder)], check=True)


if __name__ == "__main__":
    opts, (quote, landing) = parse_args(__doc__, 2)
    if "session" not in opts:
        sys.exit(__doc__)
    tags = ""
    for opt, name, pattern in (("rules", "规则", r"RULE-\d+(?:、RULE-\d+)*"), ("adrs", "影响的 ADR", r"ADR-\d{4}(?:、ADR-\d{4})*"), ("sheet", "单子", r"\w+")):
        if opt in opts:
            value = opts[opt].replace(",", "、")
            if not re.fullmatch(pattern, value):
                sys.exit(f"--{opt} 的值不合规，应形如 {pattern}：{opts[opt]}")
            tags += f"\n## {name}\n\n{value}\n"
    create("intent", opts.get("date", datetime.date.today().isoformat()), opts["session"], short_name(quote),
           f"## 原话\n\n{quote}\n\n## 落点\n\n{landing}\n{tags}")
