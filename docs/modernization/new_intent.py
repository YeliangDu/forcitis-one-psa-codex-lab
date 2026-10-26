#!/usr/bin/env python3
"""新增一条用户决定片段 intent/<日期>-<HHMMSS>-<会话名>-<短名>.md，然后运行 build_decision_digest.py 校验并重新生成已定决策速查表（INTENT.md 已停止生成，定稿 1.11）。

用法：python3 docs/modernization/new_intent.py --session=<会话名> --category=<类别> --owner=<负责会话> --paths=<路径前缀[,...]|无> --supersedes=<被取代的 intent 文件名[,...]|无> "<用户原话>" "<落点>" [--date=YYYY-MM-DD] [--rules=RULE-272,RULE-273] [--adrs=ADR-0044] [--sheet=D5] [--items=D5-01,D5-02]
必填四项，缺任一即拒并列缺项：--category 取 口径、范围、流程、授权、技术、其他；--owner 负责落地的会话名（须在 roles/ 过渡映射或 policy.json 的 sessions.config 里出现）；--paths 受影响的仓库相对路径前缀，没有写「无」。依次写进片段的「类别」「负责会话」「影响路径」节，旧片段不回填。
本决定推翻或取代了此前哪些决定，就在 --supersedes 里写出（已定决策速查表据此标「被取代」，build_decision_digest.py）；文件名须是 intent/ 下已有的片段，没有写「无」。
可选的 --rules / --adrs / --sheet / --items 把决定影响的规则号、ADR、确认页单子与题号记入片段（规则号须在规则索引中存在），规则索引与 ADR 治理据此反查「被哪些决定改过」。
原话与落点各为一行。文件名由日期、当前时刻与会话名组成，并行会话互不撞名，无需协调序号。
"""
import datetime, glob, json, os, re, subprocess, sys

BASE = os.path.dirname(os.path.abspath(__file__))
CATEGORIES = ("口径", "范围", "流程", "授权", "技术", "其他")


def short_name(text, n=16):
    t = re.sub(r"[`*「」『』（）()\[\]【】<>:：，,。；;、！!？?\"'/\\|·\s]+", "-", text)
    return re.sub(r"-+", "-", t).strip("-")[:n].strip("-") or "x"


def known_owners():
    names = set(json.load(open(os.path.join(BASE, "..", "..", "policy.json"), encoding="utf-8")).get("sessions", {}).get("config", {}))
    for f in glob.glob(os.path.join(BASE, "roles", "*.md")):
        names |= set(re.findall(r"(?<![\w-])one-[a-z0-9]+(?:-[a-z0-9]+)*(?![\w<-])", open(f, encoding="utf-8").read().partition("\n## 过渡映射")[2].split("\n## ")[0]))
    return names


def parse_args(doc, n):
    opts = dict(a[2:].split("=", 1) for a in sys.argv[1:] if a.startswith("--") and "=" in a)
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != n or "session" in opts and not opts["session"]:
        sys.exit(doc)
    return opts, args


def create(folder, date, session, slug, text, builder="build_decision_digest.py"):
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
    known = {"session", "category", "owner", "paths", "date", "supersedes", "rules", "adrs", "sheet", "items"}
    stray = [a for a in sys.argv[1:] if a.startswith("--") and a[2:].split("=", 1)[0] not in known or a.startswith("--") and "=" not in a]
    if stray:
        sys.exit(f"不认识或缺少值的选项：{'、'.join(stray)}\n{__doc__}")
    absent = [f"--{k}" for k in ("session", "category", "owner", "paths", "supersedes") if not opts.get(k)]
    if absent: sys.exit(f"缺必填项：{'、'.join(absent)}（没有被取代的或没有影响路径就写「无」）\n{__doc__}")
    paths = [] if opts["paths"] == "无" else opts["paths"].split(",")
    wrong = [m for ok, m in ((opts["category"] in CATEGORIES, f"--category 须为 {'、'.join(CATEGORIES)} 之一"),
                             (re.fullmatch(r"[a-z0-9-]+", opts["owner"]) and opts["owner"] in known_owners(), "--owner 须是 [a-z0-9-]+，且在 roles/ 过渡映射或 policy.json 的 sessions.config 里出现"),
                             (all(re.fullmatch(r"[^\s/、][^\s、]*", x) and ".." not in x.split("/") for x in paths) and len(paths) == len(set(paths)), "--paths 须是不重复的仓库相对路径前缀（无空白、不以 / 开头、不含 ..）或「无」")) if not ok]
    if wrong: sys.exit("；".join(wrong) + f"：{opts['category']} {opts['owner']} {opts['paths']}")
    olds = [x for x in opts["supersedes"].split(",") if x != "无"]
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
    tags += f"\n## 类别\n\n{opts['category']}\n\n## 负责会话\n\n{opts['owner']}\n\n## 影响路径\n\n{'、'.join(paths) or '无'}\n"
    create("intent", opts.get("date", datetime.date.today().isoformat()), opts["session"], short_name(quote),
           f"## 原话\n\n{quote}\n\n## 落点\n\n{landing}\n{tags}")
