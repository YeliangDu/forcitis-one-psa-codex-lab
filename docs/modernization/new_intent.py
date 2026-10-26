#!/usr/bin/env python3
"""新增一条用户决定片段 intent/<日期>-<HHMMSS>-<会话名>-<短名>.md，然后运行 build_decisions.py 校验并重新生成现行决定页（INTENT.md、速查表已停止生成，定稿 1.11）。

用法：python3 docs/modernization/new_intent.py --session=<会话名> "<用户原话>" "<落点>" [--date=YYYY-MM-DD] [--supersedes=<被取代的 intent 文件名>[,<文件名>...]] [--rules=RULE-272,RULE-273] [--adrs=ADR-0044] [--sheet=D5] [--items=D5-01,D5-02]
本决定推翻或取代了此前哪些决定，就在 --supersedes 里写出（现行决定页据此不再列它，build_decisions.py）；文件名须是 intent/ 下已有的片段。
可选的 --rules / --adrs / --sheet / --items 把决定影响的规则号、ADR、确认页单子与题号记入片段（规则号须在规则索引中存在），规则索引与 ADR 治理据此反查「被哪些决定改过」。
原话与落点各为一行。
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


def create(folder, date, session, slug, text, builder="build_decisions.py"):
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
    known = {"session", "date", "supersedes", "rules", "adrs", "sheet", "items"}
    stray = [a for a in sys.argv[1:] if a.startswith("--") and a[2:].split("=", 1)[0] not in known or a.startswith("--") and "=" not in a]
    if stray:
        sys.exit(f"不认识或缺少值的选项：{'、'.join(stray)}\n{__doc__}")
    olds = [x for x in opts.get("supersedes", "").split(",") if x]
    for old in olds:
        if not os.path.isfile(os.path.join(BASE, "intent", old)):
            sys.exit(f"--supersedes：intent/{old} 不存在")
    tags = "\n## 取代\n\n" + "".join(f"- {o}\n" for o in olds) if olds else ""
    for opt, name, pattern in (("rules", "规则", r"(?:[A-Z]+-)?RULE-\d+(?:、(?:[A-Z]+-)?RULE-\d+)*"), ("adrs", "影响的 ADR", r"ADR-\d{4}(?:、ADR-\d{4})*"),
                               ("sheet", "单子", r"[A-Za-z0-9_-]+"), ("items", "题号", r"[A-Za-z0-9_-]+(?:、[A-Za-z0-9_-]+)*")):
        if opt in opts:
            value = opts[opt].replace(",", "、")
            if not re.fullmatch(pattern, value):
                sys.exit(f"--{opt} 的值不合规，应形如 {pattern}：{opts[opt]}")
            parts = value.split("、")
            if opt == "rules":
                import importlib.util
                spec = importlib.util.spec_from_file_location("build_rule_index", os.path.join(BASE, "rules", "build_rule_index.py"))
                index = importlib.util.module_from_spec(spec); spec.loader.exec_module(index)
                cards = index.load()["cards"]
                missing = [r for r in parts if index.norm(r) not in cards]
                if missing:
                    sys.exit(f"--rules 里的规则号不存在于规则索引：{'、'.join(missing)}")
                parts = [index.norm(r) for r in parts]
                value = "、".join(cards[r]["id"] for r in parts)
            if len(parts) != len(set(parts)):
                sys.exit(f"--{opt} 有重复项：{opts[opt]}")
            tags += f"\n## {name}\n\n{value}\n"
    create("intent", opts.get("date", datetime.date.today().isoformat()), opts["session"], short_name(quote),
           f"## 原话\n\n{quote}\n\n## 落点\n\n{landing}\n{tags}")
