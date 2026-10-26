#!/usr/bin/env python3
"""决定链路一条龙：读用户在确认页的提交 → 对账 → 生成 intent 片段 → 打印收件箱回写所需的 ArtifactData 批量写入。
用法：
  python3 docs/modernization/rules/decide/decide_sync.py <单子> <out_dir> --category=<类别> --paths=<路径前缀|无> --supersedes=<被取代片段|无> [--owner=<负责会话，缺省取登记>] [--session=one-rules] [--inbox-id=<收件箱事项 id>] [--no-intent]
  python3 docs/modernization/rules/decide/decide_sync.py --check <收件箱 out_dir> <单子>=<确认页 out_dir> [<单子>=<确认页 out_dir> ...]
    对账检查（调度报「待答」前先跑）：读收件箱导出与各确认页导出，报出「收件箱仍是 pending，但确认页已提交」的事项，以及确认页提交后又改过、须重新提交的。
    事项对应哪张单子：事项里写 sheet 字段，或事项的 link 等于 README 页面表里该单子的链接；对不上的待定事项不报。有不一致时返回码 1。
前提：已用 ArtifactData list 把该确认页的 answers、answers-submit 存到 <out_dir>。
做三件事：
1. 对账：调用 export_answers 的逻辑，有提交后又改过的题则报错退出；
2. 每个提交编号生成一条 intent 片段（用户原话 = 题号 + 所选项文字 + 说明，落点指向答复文件 rules/decide/answers/<单子>.json），文件名打印出来；
   new_intent 的四个必填项：--owner 取自 ONLINE_PAGES.md 里该单子登记的负责会话（G3；单子未登记、负责会话为「roles/ 未列」或登记表表头变了即拒，不回退默认会话；显式给 --owner 须与登记一致，否则拒），--category、--paths、--supersedes 由调用方手填（页面元数据里没有页类型与影响路径，不编），缺任一即拒并列缺项，原样转给 new_intent（取值见其脚本头）；口头决定不经单子，直接用 new_intent.py，同样必填这四项；只导出不生成 intent 时用 --no-intent；
3. 打印 inbox 事项改为已定所需的 ArtifactData update 内容（status、answer、decidedAt、intent 文件名），由会话用 ArtifactData 执行：
   页面是各自独立的发布件，确认页不能直接写收件箱，所以回写由会话完成，并在报告里附这条命令的输出。
不自动改规则卡、ADR；转写仍由会话执行并复核。
幂等：同一提交编号已有 intent 片段（片段正文含「提交 <编号>」）时不重复生成。原话里的「|」按 new_intent 的要求转义为「\\|」，不改动其他字符；生成失败（返回码非 0 或没有产出片段）即退出，不打印成功。
覆盖检查：存在 sheets/<单子>.json 时，核对提交是否覆盖全部题、所选序号对应的选项文字是否仍与单子一致，不一致报错退出（发布后改过选项会造成序号错位）。"""
import datetime, glob, json, os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import export_answers  # noqa: E402

PAGES_HEADER = ["单子或页面", "用途", "负责会话", "起用日期", "退役日期", "出处"]


def registered_owner(sheet):
    """单子的负责会话，取自 ONLINE_PAGES.md 登记表。列约定：表头固定为 PAGES_HEADER 六列，行以「 | 」分列，单子行第一列是单子编号；
    表头变了、单子未登记或重复、列数不对、负责会话为「roles/ 未列」，一律拒绝，不回退默认会话。"""
    rows = [[c.strip() for c in line.strip().strip("|").split(" | ")] for line in open(os.path.join(HERE, "ONLINE_PAGES.md"), encoding="utf-8") if line.startswith("| ")]
    if not rows or rows[0] != PAGES_HEADER:
        sys.exit(f"ONLINE_PAGES.md 登记表表头不是约定的六列（{' | '.join(PAGES_HEADER)}），不敢解析负责会话；先修登记表或本脚本")
    found = [r for r in rows if r[0] == sheet]
    if len(found) != 1 or len(found[0]) != len(PAGES_HEADER):
        sys.exit(f"单子 {sheet} 没有在 ONLINE_PAGES.md 登记（或重复登记、列数不对），无法确定负责会话；请先登记再生成 intent")
    if found[0][2] in ("", "roles/ 未列"):
        sys.exit(f"单子 {sheet} 在 ONLINE_PAGES.md 的负责会话是「{found[0][2]}」，无法确定负责会话；请先补登记")
    sys.path.insert(0, os.path.join(HERE, "..", ".."))
    import new_intent
    if found[0][2] not in new_intent.known_owners():
        sys.exit(f"单子 {sheet} 登记的负责会话 {found[0][2]} 不在 roles/ 过渡映射或 policy.json 的 sessions.config 里，无法确定负责会话；请先补登记")
    return found[0][2]

if "--check" in sys.argv:
    import glob as _g, re as _re
    rest = [a for a in sys.argv[1:] if a != "--check"]
    if len(rest) < 2 or any("=" not in a for a in rest[1:]):
        sys.exit(__doc__)
    inbox_dir, pages = rest[0], dict(a.split("=", 1) for a in rest[1:])
    urls = {}
    for line in open(os.path.join(HERE, "README.md"), encoding="utf-8"):
        m = _re.match(r"^\| (\w+) .*?(https://claude\.ai/artifact/[\w-]+)", line)
        if m:
            urls[m.group(2)] = m.group(1)
    state = {}
    for sheet_id, d in pages.items():
        try:
            out, stale = export_answers.export(sheet_id, d)
            state[sheet_id] = ("stale", stale) if stale else ("submitted", sorted({v["submission"] for v in out.values()})) if out else ("none", [])
        except Exception as e:  # 导出目录读不到等
            state[sheet_id] = ("error", [str(e)])
    bad = 0
    for f in sorted(_g.glob(os.path.join(inbox_dir, "inbox", "*.json"))):
        doc = export_answers.load(f)
        if (doc.get("status") or "pending") != "pending":
            continue
        sh = doc.get("sheet") or urls.get((doc.get("link") or "").split("?")[0].rstrip("/"))
        if not sh or sh not in state:
            continue
        kind, info = state[sh]
        name = os.path.basename(f)[:-5]
        if kind == "submitted":
            print(f"不一致：收件箱事项 {name} 仍是 pending，确认页 {sh} 已提交（编号 {'、'.join(info)}）→ 跑 decide_sync 回写"); bad += 1
        elif kind == "stale":
            print(f"待重新提交：收件箱事项 {name}；确认页 {sh} 提交后改过：{'、'.join(info)}"); bad += 1
        elif kind == "error":
            print(f"无法核对：收件箱事项 {name}；确认页 {sh} 的导出读取失败：{info[0]}"); bad += 1
    print("检查通过：收件箱与确认页一致" if not bad else f"共 {bad} 处不一致")
    sys.exit(1 if bad else 0)
args = [a for a in sys.argv[1:] if not a.startswith("--")]
opts = dict(a[2:].split("=", 1) if "=" in a else (a[2:], True) for a in sys.argv[1:] if a.startswith("--"))
if len(args) != 2:
    sys.exit(__doc__)
sheet, base = args
if not opts.get("no-intent"):
    absent = [f"--{k}" for k in ("category", "paths", "supersedes") if not (isinstance(opts.get(k), str) and opts[k])]
    if absent:
        sys.exit(f"缺必填项：{'、'.join(absent)}（原样转给 new_intent，取值与写法见其脚本头；--owner 取自 ONLINE_PAGES.md；不生成 intent 时用 --no-intent）")
    owner = registered_owner(sheet)
    if "owner" in opts and opts["owner"] != owner:
        sys.exit(f"--owner={opts['owner']!r} 与 ONLINE_PAGES.md 里单子 {sheet} 登记的负责会话 {owner} 不一致；不给 --owner 即取登记值")
    opts["owner"] = owner
passed = [f"--{k}={opts[k]}" for k in ("category", "owner", "paths", "supersedes") if isinstance(opts.get(k), str) and opts[k]]
out, stale = export_answers.export(sheet, base)
if stale:
    sys.exit(f"对账失败：这些题提交后又改过，请用户重新提交：{'、'.join(stale)}")
if not out:
    sys.exit("没有已提交的答复")
sheet_file = os.path.join(HERE, "sheets", f"{sheet}.json")
if os.path.exists(sheet_file):
    items = {f"{sheet}~{i['no']}": i for i in json.load(open(sheet_file, encoding="utf-8"))["items"]}
    missing = sorted(set(items) - set(out))
    if missing:
        sys.exit(f"提交没有覆盖全部题，缺：{'、'.join(m.split('~', 1)[1] for m in missing)}；请用户补提交")
    for k, v in out.items():
        it = items.get(k)
        if it is None:
            sys.exit(f"{k} 不在单子 {sheet}.json 里")
        if v["choice"] not in ("defer", None) and it["opts"][v["choice"]]["label"] != v["label"]:
            sys.exit(f"{k} 的选项文字与单子不一致（发布后改过选项？）：提交里是「{v['label']}」，单子里是「{it['opts'][v['choice']]['label']}」")
os.makedirs(os.path.join(HERE, "answers"), exist_ok=True)
json.dump(out, open(os.path.join(HERE, "answers", f"{sheet}.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
by_sub = {}
for k, v in sorted(out.items()):
    by_sub.setdefault(v["submission"], []).append((k.split("~", 1)[1], v))
session = opts.get("session", "one-rules")
intents = []
for number, rows in sorted(by_sub.items()):
    lines = "；".join(f"{no}「{v['label']}」" + ("（未作答）" if v.get("unanswered") else "（按推荐预选，未改动）" if v.get("defaulted") else "") + (f"（说明：{v['note']}）" if v.get("note") else "") for no, v in rows)
    quote = f"{sheet} 决策页提交 {number}：{lines}"
    note = f"答复 rules/decide/answers/{sheet}.json；按推荐 {sum(v['recommended'] for _, v in rows)} 题、改选 {sum(not v['recommended'] and v['choice'] not in ('defer', None) for _, v in rows)} 题、暂不定 {sum(v['choice'] == 'defer' for _, v in rows)} 题、未作答 {sum(v['choice'] is None for _, v in rows)} 题"
    intent_dir = os.path.join(HERE, "..", "..", "intent")
    existing = next((f for f in sorted(glob.glob(os.path.join(intent_dir, "*.md"))) if f"提交 {number}：" in open(f, encoding="utf-8").read()), None)
    if existing:
        intents.append((number, os.path.basename(existing)))
    elif not opts.get("no-intent"):
        esc = lambda t: t.replace("|", "\\|")
        r = subprocess.run([sys.executable, os.path.join(HERE, "..", "..", "new_intent.py"), f"--session={session}", *passed, esc(quote), esc(note)], capture_output=True, text=True)
        path = next((l for l in r.stdout.splitlines() if l.startswith("intent/")), "")
        if r.returncode != 0 or not path:
            sys.exit(f"生成 intent 失败（返回码 {r.returncode}）：{(r.stderr or r.stdout)[-300:]}；请检查 docs/modernization/intent/ 下有无半成品片段后再处理，不要手改用户原话")
        intents.append((number, os.path.basename(path)))
    else:
        intents.append((number, ""))
print(f"{sheet}：{len(out)} 题，提交 {len(by_sub)} 次；对账通过；答复已写 answers/{sheet}.json")
for number, name in intents:
    print(f"  提交 {number} → intent {name or '（--no-intent，未生成）'}")
if opts.get("inbox-id"):
    last = max(by_sub, key=lambda n: max(v["submitted"] for _, v in by_sub[n]))   # 按提交时刻，不按编号字符串
    name = dict(intents).get(last, "")
    at = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")
    write = {"action": "update", "collection": "inbox", "doc_id": opts["inbox-id"],
             "data": {"status": "decided", "decidedAt": at, "intent": name,
                      "answer": {"text": f"在确认页提交，编号 {last}", "at": at}}}
    print("收件箱回写（用 ArtifactData 对收件箱页执行，带 if_version）：")
    print(json.dumps(write, ensure_ascii=False, indent=1))
